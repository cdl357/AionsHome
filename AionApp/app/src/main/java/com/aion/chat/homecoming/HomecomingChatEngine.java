package com.aion.chat.homecoming;

import java.util.List;

public final class HomecomingChatEngine {
    private final RepositoryPort repository;
    private final ContextPort context;
    private final GatewayPort gateway;
    private final Clock clock;
    private final ControlPort controls;

    HomecomingChatEngine(
            RepositoryPort repository,
            ContextPort context,
            GatewayPort gateway,
            Clock clock) {
        this(repository, context, gateway, clock,
                (command, events, now) ->
                        repository.recordControls(command.requestId, events));
    }

    HomecomingChatEngine(
            RepositoryPort repository,
            ContextPort context,
            GatewayPort gateway,
            Clock clock,
            ControlPort controls) {
        this.repository = repository;
        this.context = context;
        this.gateway = gateway;
        this.clock = clock;
        this.controls = controls;
    }

    public HomecomingChatEngine(
            HomecomingChatRepository repository,
            HomecomingContextBuilder context,
            HomecomingModelGateway gateway) {
        this(repository, context, gateway,
                (HomecomingScheduleCommandHandler) null);
    }

    /** 本地模型等非 HTTP 网关走这里：持久化/控制解析/UI 观察者全复用（v10 队列·本地模型接入） */
    public HomecomingChatEngine(
            HomecomingChatRepository repository,
            HomecomingContextBuilder context,
            GatewayPort gateway) {
        this(repository, context, gateway,
                (HomecomingScheduleCommandHandler) null, null);
    }

    public HomecomingChatEngine(
            HomecomingChatRepository repository,
            HomecomingContextBuilder context,
            HomecomingModelGateway gateway,
            HomecomingScheduleCommandHandler scheduleCommands) {
        this(repository, context, gateway, scheduleCommands, null);
    }

    public HomecomingChatEngine(
            HomecomingChatRepository repository,
            HomecomingContextBuilder context,
            HomecomingModelGateway gateway,
            HomecomingControlDispatcher dispatcher) {
        this(repository, context, gateway, null, dispatcher);
    }

    private HomecomingChatEngine(
            HomecomingChatRepository repository,
            HomecomingContextBuilder context,
            GatewayPort gateway,
            HomecomingScheduleCommandHandler scheduleCommands,
            HomecomingControlDispatcher dispatcher) {
        this(
                new RepositoryPort() {
                    @Override public void commitUser(ChatCommand command, long now) {
                        repository.commitUserMessage(
                                command.requestId, command.timelineId, command.userSenderId,
                                command.text,
                                command.imageDataUrl.isEmpty() ? "" : "image",
                                now);
                    }
                    @Override public void prepareAssistant(
                            ChatCommand command, long now) {
                        repository.beginAssistantRequest(
                                command.requestId, command.timelineId, now);
                    }
                    @Override public String commitAssistant(
                            ChatCommand command, String text, long now) {
                        return repository.commitAssistantMessage(
                                command.requestId, command.timelineId,
                                command.responderOwner, text, now).id;
                    }
                    @Override public void fail(String requestId, String code) {
                        repository.failRequest(requestId, code);
                    }
                    @Override public void recordControls(
                            String requestId,
                            List<HomecomingControlParser.ControlEvent> controls) {
                        repository.recordDeferredControls(
                                requestId, controls, System.currentTimeMillis());
                    }
                },
                context::build,
                gateway,
                System::currentTimeMillis,
                dispatcher != null
                        ? dispatcher
                        : scheduleCommands == null
                        ? (command, controls, now) ->
                                repository.recordDeferredControls(
                                        command.requestId, controls, now)
                        : (command, controls, now) -> {
                            for (int index = 0; index < controls.size(); index++) {
                                HomecomingControlParser.ControlEvent control =
                                        controls.get(index);
                                HomecomingScheduleCommandHandler.ApplyResult result =
                                        scheduleCommands.apply(
                                                command.requestId,
                                                index,
                                                control,
                                                command.responderOwner,
                                                command.timelineId,
                                                now);
                                if ("deferred".equals(result.status)) {
                                    repository.recordDeferredControls(
                                            command.requestId,
                                            java.util.Collections.singletonList(control),
                                            now);
                                }
                            }
                        });
    }

    public void send(ChatCommand command, Observer observer) {
        final List<HomecomingModelGateway.ChatMessage> messages;
        final long now = clock.now();
        try {
            messages = context.build(
                    command.timelineId,
                    command.responderOwner,
                    command.text,
                    now,
                    command.locationState);
            if (command.commitUser) {
                repository.commitUser(command, now);
            } else {
                repository.prepareAssistant(command, now);
            }
            gateway.stream(new HomecomingModelGateway.ChatRequest(
                    command.requestId,
                    command.routeId,
                    command.modelKey,
                    messages,
                    command.imageDataUrl,
                    command.extraImageDataUrls),
                    new HomecomingModelGateway.StreamObserver() {
                        private boolean deltasForwarded = false;

                        @Override public void onChunk(String text) {
                            // v0.3.151 流式：网关的逐 delta 转发给 UI（UI 每次渲染都剥控制标签，
                            // 标签跨 chunk 的瞬时残影由最终提交修正）。空段不转。
                            if (text != null && !text.isEmpty()) {
                                deltasForwarded = true;
                                observer.onChunk(text);
                            }
                        }

                        @Override public void onComplete(String text) {
                            HomecomingControlParser.Result parsed =
                                    HomecomingControlParser.parse(text);
                            try {
                                String messageId = repository.commitAssistant(
                                        command, parsed.visibleText, clock.now());
                                controls.apply(command, parsed.events, clock.now());
                                // 已经流式转发过 delta 的不再追加全文（否则 UI 会重复拼接）
                                if (!deltasForwarded && !parsed.visibleText.isEmpty()) {
                                    observer.onChunk(parsed.visibleText);
                                }
                                observer.onComplete(
                                        messageId,
                                        parsed.visibleText);
                            } catch (RuntimeException exception) {
                                repository.fail(command.requestId, "assistant_commit_failed");
                                observer.onFailure("assistant_commit_failed");
                            }
                        }

                        @Override public void onFailure(String code) {
                            repository.fail(command.requestId, code);
                            observer.onFailure(code);
                        }
                    });
        } catch (Exception exception) {
            // 失败码带真实异常：哪一步炸的、为什么，错误卡上直接可见（不再吞成同一个词）
            String detail = exception.getClass().getSimpleName()
                    + (exception.getMessage() == null ? "" : ":" + exception.getMessage());
            if (detail.length() > 220) {
                detail = detail.substring(0, 220);
            }
            String code = "request_setup_failed:" + detail;
            try {
                repository.fail(command.requestId, code);
            } catch (RuntimeException ignored) {
                // The user commit may not exist yet.
            }
            observer.onFailure(code);
        }
    }

    public void stop(String requestId) {
        gateway.cancel(requestId);
    }

    public interface RepositoryPort {
        void commitUser(ChatCommand command, long now);
        default void prepareAssistant(ChatCommand command, long now) {
        }
        String commitAssistant(ChatCommand command, String text, long now);
        void fail(String requestId, String code);
        void recordControls(
                String requestId, List<HomecomingControlParser.ControlEvent> controls);
    }

    public interface ContextPort {
        List<HomecomingModelGateway.ChatMessage> build(
                String timelineId, String responderOwner, String trigger,
                long now, String locationState);
    }

    public interface GatewayPort {
        void stream(
                HomecomingModelGateway.ChatRequest request,
                HomecomingModelGateway.StreamObserver observer) throws Exception;
        void cancel(String requestId);
    }

    public interface Clock {
        long now();
    }

    public interface ControlPort {
        void apply(
                ChatCommand command,
                List<HomecomingControlParser.ControlEvent> controls,
                long now);
    }

    public interface Observer {
        void onChunk(String text);
        void onComplete(String messageId, String text);
        void onFailure(String code);
    }

    public static final class ChatCommand {
        public final String requestId;
        public final String timelineId;
        public final String responderOwner;
        public final String userSenderId;
        public final String text;
        public final String routeId;
        public final String modelKey;
        public final String imageDataUrl;
        public final String locationState;
        public final boolean commitUser;
        /** 多图（v7）：首图在 imageDataUrl，其余在这里；网关/中转站路径都会带上。 */
        public final java.util.List<String> extraImageDataUrls;

        public ChatCommand(
                String requestId, String timelineId, String responderOwner,
                String userSenderId, String text, String routeId, String modelKey,
                String imageDataUrl, String locationState) {
            this(requestId, timelineId, responderOwner, userSenderId, text,
                    routeId, modelKey, imageDataUrl, locationState, true);
        }

        public ChatCommand(
                String requestId, String timelineId, String responderOwner,
                String userSenderId, String text, String routeId, String modelKey,
                String imageDataUrl, String locationState, boolean commitUser) {
            this(requestId, timelineId, responderOwner, userSenderId, text,
                    routeId, modelKey, imageDataUrl, locationState, commitUser,
                    java.util.Collections.<String>emptyList());
        }

        public ChatCommand(
                String requestId, String timelineId, String responderOwner,
                String userSenderId, String text, String routeId, String modelKey,
                String imageDataUrl, String locationState, boolean commitUser,
                java.util.List<String> extraImageDataUrls) {
            this.requestId = required(requestId, "requestId");
            this.timelineId = required(timelineId, "timelineId");
            this.responderOwner = required(responderOwner, "responderOwner");
            this.userSenderId = required(userSenderId, "userSenderId");
            this.text = text == null ? "" : text;
            this.routeId = required(routeId, "routeId");
            this.modelKey = required(modelKey, "modelKey");
            this.imageDataUrl = imageDataUrl == null ? "" : imageDataUrl;
            this.locationState = locationState == null ? "" : locationState;
            this.commitUser = commitUser;
            this.extraImageDataUrls = extraImageDataUrls == null
                    ? java.util.Collections.<String>emptyList()
                    : java.util.Collections.unmodifiableList(
                            new java.util.ArrayList<>(extraImageDataUrls));
        }
    }

    private static String required(String value, String label) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(label + " is required");
        }
        return value.trim();
    }
}
