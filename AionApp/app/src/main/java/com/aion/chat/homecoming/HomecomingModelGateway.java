package com.aion.chat.homecoming;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.io.BufferedReader;
import java.io.InputStreamReader;

import com.aion.chat.compose.data.RequestLog;
import okhttp3.Call;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public final class HomecomingModelGateway {
    private final HomecomingRouteVault vault;
    private final Transport transport;
    // 取消表 static：发送路径每次 safeCreate 新实例，停止键可能拿的是旧实例引用——
    // 取消表必须跨实例（v9 审计 P1-4：UI 停了后台流继续跑完落库）
    private static final Map<String, AtomicBoolean> cancellations = new ConcurrentHashMap<>();
    // 自定义请求（他的配置页 → 自定义请求）：额外请求头 + 正文参数，合并进每次请求
    private final Map<String, String> extraHeaders;
    private final org.json.JSONObject extraBody;

    public HomecomingModelGateway(HomecomingRouteVault vault, Transport transport) {
        this(vault, transport, null, null);
    }

    public HomecomingModelGateway(HomecomingRouteVault vault, Transport transport,
                                  Map<String, String> extraHeaders, org.json.JSONObject extraBody) {
        this(vault, transport, extraHeaders, extraBody, true);
    }

    // 提示词缓存（照橘瓣断点策略）：system 末块 + 倒数第二条 user 末块打 cache_control
    private final boolean promptCaching;
    // 缓存 TTL（橘瓣同款可选）：null/"5m" = 默认 5 分钟，"1h" = 1 小时
    private final String cacheTtl;

    public HomecomingModelGateway(HomecomingRouteVault vault, Transport transport,
                                  Map<String, String> extraHeaders, org.json.JSONObject extraBody,
                                  boolean promptCaching) {
        this(vault, transport, extraHeaders, extraBody, promptCaching, null);
    }

    public HomecomingModelGateway(HomecomingRouteVault vault, Transport transport,
                                  Map<String, String> extraHeaders, org.json.JSONObject extraBody,
                                  boolean promptCaching, String cacheTtl) {
        this.vault = vault;
        this.transport = transport;
        this.extraHeaders = extraHeaders == null
                ? java.util.Collections.emptyMap() : extraHeaders;
        this.extraBody = extraBody == null ? new org.json.JSONObject() : extraBody;
        this.promptCaching = promptCaching;
        this.cacheTtl = cacheTtl;
    }

    private org.json.JSONObject cacheControl() {
        org.json.JSONObject cc = new org.json.JSONObject();
        try {
            cc.put("type", "ephemeral");
            if ("1h".equals(cacheTtl)) cc.put("ttl", "1h");
        } catch (Exception ignored) { }
        return cc;
    }

    public void stream(ChatRequest request, StreamObserver observer) throws Exception {
        HomecomingRouteVault.Route route = vault.resolve(request.routeId);
        HomecomingRouteVault.Model model = route.model(request.modelKey);
        if ((!request.imageDataUrl.isEmpty() || !request.extraImageDataUrls.isEmpty())
                && !model.vision) {
            throw new IllegalArgumentException("selected model does not support vision");
        }
        AtomicBoolean cancelled = new AtomicBoolean(false);
        if (cancellations.putIfAbsent(request.requestId, cancelled) != null) {
            throw new IllegalStateException("request is already running");
        }
        // F5：推理型渠道只吐 reasoning_content 不吐 content 时，错误别再误导去查余额
        try {
            runRounds(request, observer, cancelled, route, model, 0, null,
                    new HomecomingModelStreamNormalizer(), new StringBuilder(), new boolean[]{false});
        } finally {
            cancellations.remove(request.requestId, cancelled);
        }
    }

    /** 一轮生成：SSE 抽文本/工具调用；有 tool_calls → 执行 MCP → 结果入历史 → 下一轮（最多 6 轮，照橘子岛骨架）。 */
    private void runRounds(ChatRequest request, StreamObserver observer, AtomicBoolean cancelled,
                           HomecomingRouteVault.Route route, HomecomingRouteVault.Model model,
                           int round, org.json.JSONArray toolHistory,
                           HomecomingModelStreamNormalizer normalizer, StringBuilder complete,
                           boolean[] sawReasoning) throws Exception {
        String reqUrl = null;
        final long reqT0 = android.os.SystemClock.elapsedRealtime();
        // OpenAI 流式工具调用累积：index -> {id, name, arguments}
        final java.util.LinkedHashMap<Integer, String[]> toolAcc = new java.util.LinkedHashMap<>();
        final boolean[] toolFinish = {false};
        try {
            NetworkRequest network = build(request, route, model, toolHistory);
            reqUrl = network.url;
            // 自定义请求（他的配置页）：额外请求头 + 正文参数最后合并，可覆盖默认值
            if (!extraHeaders.isEmpty() || extraBody.length() > 0) {
                network = mergeCustom(network);
            }
            final java.util.concurrent.atomic.AtomicLong cachedTok =
                    new java.util.concurrent.atomic.AtomicLong(-1);
            final java.util.concurrent.atomic.AtomicLong promptTok =
                    new java.util.concurrent.atomic.AtomicLong(-1);
            final java.util.concurrent.atomic.AtomicLong completionTok =
                    new java.util.concurrent.atomic.AtomicLong(-1);
            transport.execute(network, line -> {
                if (cancelled.get()) {
                    return;
                }
                // 缓存命中统计：usage 里读 cached_tokens / cache_read_input_tokens
                if (line.contains("\"usage\"")) {
                    try {
                        String t = line.startsWith("data:") ? line.substring(5).trim() : line;
                        JSONObject u = new JSONObject(t).optJSONObject("usage");
                        if (u != null) {
                            promptTok.set(u.optLong("prompt_tokens", -1));
                            completionTok.set(u.optLong("completion_tokens", -1));
                            long c = -1;
                            JSONObject d = u.optJSONObject("prompt_tokens_details");
                            if (d != null) c = d.optLong("cached_tokens", -1);
                            if (c < 0) c = u.optLong("cache_read_input_tokens", -1);
                            if (c >= 0) cachedTok.set(c);
                        }
                    } catch (Exception ignored) { }
                }
                // 工具调用增量累积（OpenAI 流式格式）
                if (line.contains("\"tool_calls\"") || line.contains("finish_reason")) {
                    try {
                        String t = line.startsWith("data:") ? line.substring(5).trim() : line;
                        if (!"[DONE]".equals(t)) {
                            JSONObject o = new JSONObject(t);
                            JSONArray ch = o.optJSONArray("choices");
                            if (ch != null && ch.length() > 0) {
                                JSONObject c0 = ch.getJSONObject(0);
                                if ("tool_calls".equals(c0.optString("finish_reason"))) toolFinish[0] = true;
                                JSONObject delta = c0.optJSONObject("delta");
                                JSONArray tcs = delta == null ? null : delta.optJSONArray("tool_calls");
                                if (tcs != null) {
                                    for (int k = 0; k < tcs.length(); k++) {
                                        JSONObject tc = tcs.getJSONObject(k);
                                        int idx = tc.optInt("index", 0);
                                        String[] acc = toolAcc.computeIfAbsent(idx, x -> new String[]{"", "", ""});
                                        String idv = tc.optString("id", "");
                                        if (!idv.isEmpty()) acc[0] = idv;
                                        JSONObject fn = tc.optJSONObject("function");
                                        if (fn != null) {
                                            String nv = fn.optString("name", "");
                                            if (!nv.isEmpty()) acc[1] = acc[1] + nv;
                                            String av = fn.optString("arguments", "");
                                            if (!av.isEmpty()) acc[2] = acc[2] + av;
                                        }
                                    }
                                }
                            }
                        }
                    } catch (Exception ignored) { }
                }
                ProviderFrame frame;
                try {
                    frame = parseFrame(route.provider, line);
                } catch (Exception ignored) {
                    return;
                }
                if (frame.reasoningText != null && !frame.reasoningText.isEmpty()) {
                    sawReasoning[0] = true;
                }
                String chunk = normalizer.acceptVisible(frame.visibleText);
                if (!chunk.isEmpty()) {
                    complete.append(chunk);
                    observer.onChunk(chunk);
                }
            }, cancelled::get);
            com.aion.chat.compose.data.RequestLog.add(null, "POST", network.url, 200,
                    android.os.SystemClock.elapsedRealtime() - reqT0,
                    "", cachedTok.get(), promptTok.get(), completionTok.get());
            if (cancelled.get()) {
                observer.onFailure("cancelled");
                return;
            }
            // 工具调用轮：执行 MCP 工具 → 结果入历史 → 下一轮
            if (toolFinish[0] && !toolAcc.isEmpty() && round < 6) {
                org.json.JSONArray hist = toolHistory == null ? new org.json.JSONArray() : toolHistory;
                org.json.JSONArray tcs = new org.json.JSONArray();
                toolAcc.forEach((k, v) -> {
                    try {
                        tcs.put(new JSONObject()
                                .put("id", v[0].isEmpty() ? ("call_" + k) : v[0])
                                .put("type", "function")
                                .put("function", new JSONObject()
                                        .put("name", v[1])
                                        .put("arguments", v[2].isEmpty() ? "{}" : v[2])));
                    } catch (Exception ignored) { }
                });
                hist.put(new JSONObject()
                        .put("role", "assistant")
                        .put("content", JSONObject.NULL)
                        .put("tool_calls", tcs));
                StringBuilder summary = new StringBuilder();
                toolAcc.forEach((k, v) -> {
                    try {
                        String safeName = v[1];
                        String resultText;
                        boolean mcpHasTool = false;
                        for (com.aion.chat.compose.data.McpClient.ToolDef d0 : com.aion.chat.compose.data.McpClient.cachedDefs()) {
                            if (d0.safeName.equals(safeName)) { mcpHasTool = true; break; }
                        }
                        if (com.aion.chat.compose.data.WorkflowTools.handles(safeName)) {
                            // 工作流工具：引擎完整门控在 WorkflowEngine.fire 里（条件/冷却/每日上限照常）
                            resultText = com.aion.chat.compose.data.WorkflowTools.dispatch(safeName,
                                    v[2].isEmpty() ? "{}" : v[2]);
                            RequestLog.add(null, "WF", safeName, 200, 0, "", 0, 0);
                        } else if (!mcpHasTool && com.aion.chat.compose.data.MemoryTools.handles(safeName)
                                && com.aion.chat.compose.data.MemoryTools.enabled()) {
                            String r = com.aion.chat.compose.data.MemoryTools.dispatch(
                                    safeName, v[2].isEmpty() ? "{}" : v[2]);
                            resultText = r.isEmpty() ? "（完成）" : r;
                            RequestLog.add(null, "MEM", safeName, 200, 0, "", 0, 0);
                        } else {
                            com.aion.chat.compose.data.McpClient.CallResult cr =
                                    com.aion.chat.compose.data.McpClient.callBySafeName(
                                            safeName, v[2].isEmpty() ? "{}" : v[2]);
                            resultText = cr.ok ? cr.text : "工具执行失败: " + cr.err;
                            RequestLog.add(null, "MCP", safeName, cr.ok ? 200 : 500,
                                    0, cr.ok ? "" : cr.err, 0, 0);
                        }
                        if (summary.length() < 300) {
                            summary.append(safeName).append(" → ")
                                    .append(resultText, 0, Math.min(80, resultText.length()))
                                    .append('\n');
                        }
                        hist.put(new JSONObject()
                                .put("role", "tool")
                                .put("tool_call_id", v[0].isEmpty() ? ("call_" + k) : v[0])
                                .put("content", resultText));
                    } catch (Exception ignored) { }
                });
                RequestLog.add(null, "MCP", "tool_round_" + (round + 1), 200, 0,
                        summary.toString(), 0, 0);
                runRounds(request, observer, cancelled, route, model,
                        round + 1, hist, normalizer, complete, sawReasoning);
                return;
            }
            String suffix = normalizer.finish();
            if (!suffix.isEmpty()) {
                complete.append(suffix);
                observer.onChunk(suffix);
            }
            if (complete.length() == 0) {
                observer.onFailure(sawReasoning[0]
                        ? "empty_model_reply:reasoning_only"
                        : "empty_model_reply");
            } else {
                observer.onComplete(complete.toString());
            }
        } catch (Exception exception) {
            if (cancelled.get()) {
                observer.onFailure("cancelled");
            } else {
                // 失败码带原因：provider/URL/网络哪一环断的，错误卡上要能看出来
                String why = exception.getMessage();
                com.aion.chat.compose.data.RequestLog.add(null, "POST",
                        reqUrl == null ? request.routeId : reqUrl, 0,
                        android.os.SystemClock.elapsedRealtime() - reqT0,
                        exception.getClass().getSimpleName() + ":" + (why == null ? "" : why));
                observer.onFailure("model_request_failed:"
                        + exception.getClass().getSimpleName()
                        + (why == null ? "" : ":" + why));
            }
        }
    }

    public void cancel(String requestId) {
        AtomicBoolean signal = cancellations.get(requestId);
        if (signal != null) {
            signal.set(true);
        }
    }

    /** 自定义请求合并：extraBody 并进正文（坏 JSON 原样发，别把请求弄死），extraHeaders 覆盖同名默认头。 */
    private NetworkRequest mergeCustom(NetworkRequest r) {
        String bodyJson = r.body;
        try {
            if (extraBody.length() > 0) {
                JSONObject body = new JSONObject(r.body);
                java.util.Iterator<String> it = extraBody.keys();
                while (it.hasNext()) {
                    String k = it.next();
                    body.put(k, extraBody.get(k));
                }
                bodyJson = HomecomingSnapshotStore.canonicalJson(body);
            }
        } catch (Exception ignored) {
            return r;
        }
        Map<String, String> headers = new LinkedHashMap<>(r.headers);
        headers.putAll(extraHeaders);
        return new NetworkRequest(r.url, bodyJson, headers);
    }

    public void cancelAll() {
        for (AtomicBoolean signal : cancellations.values()) {
            signal.set(true);
        }
    }

    private NetworkRequest build(
            ChatRequest request,
            HomecomingRouteVault.Route route,
            HomecomingRouteVault.Model model,
            org.json.JSONArray toolHistory) throws Exception {
        if ("gemini".equals(route.provider)) {
            return buildGemini(route, model, request);
        }
        // 设置页写的是 openai_compatible；custom_openai/siliconflow/未知值一律按
        // OpenAI 兼容处理（橘瓣同款容错：设置只出 OpenAI 兼容中转，不再打枪）
        return buildOpenAi(request, route, model, toolHistory);
    }

    private NetworkRequest buildOpenAi(
            ChatRequest request,
            HomecomingRouteVault.Route route,
            HomecomingRouteVault.Model model,
            org.json.JSONArray toolHistory) throws Exception {
        JSONArray messages = new JSONArray();
        // 缓存断点目标：倒数第二条纯文本 user 消息（橘瓣策略——上轮起点，跨轮稳定命中）
        int cacheTargetIdx = -1;
        if (promptCaching) {
            int seen = 0;
            for (int i = request.messages.size() - 1; i >= 0; i--) {
                ChatMessage m = request.messages.get(i);
                if ("user".equals(m.role) && !m.text.isEmpty()) {
                    seen++;
                    if (seen == 2) { cacheTargetIdx = i; break; }
                }
            }
        }
        int msgIdx = -1;
        for (ChatMessage message : request.messages) {
            msgIdx++;
            JSONObject value = new JSONObject().put("role", message.role);
            String effText = message.text;
            if ("system".equals(message.role)) {
                // 常驻记忆追加（照橘子岛 active_memory）：记忆变了缓存自然失效重写
                String am = com.aion.chat.compose.data.MemoryTools.activeMemorySafe();
                if (!am.isEmpty()) effText = effText + "\n\n[Sean 的长期记忆]\n" + am;
            }
            boolean cachePoint = promptCaching
                    && (msgIdx == cacheTargetIdx || "system".equals(message.role));
            boolean lastUserWithImage = !request.imageDataUrl.isEmpty()
                    && "user".equals(message.role)
                    && message == request.messages.get(request.messages.size() - 1);
            if (cachePoint && !lastUserWithImage) {
                // content 数组格式，末块打 cache_control（橘瓣同款，TTL 可选）
                value.put("content", new JSONArray().put(new JSONObject()
                        .put("type", "text")
                        .put("text", effText)
                        .put("cache_control", cacheControl())));
            }
            else if (lastUserWithImage) {
                // 多图：首图 + extras 逐张 image_url part（v7：中转站与网关对齐）
                JSONArray parts = new JSONArray()
                        .put(new JSONObject()
                                .put("type", "text")
                                .put("text", message.text));
                java.util.ArrayList<String> urls = new java.util.ArrayList<>();
                urls.add(request.imageDataUrl);
                urls.addAll(request.extraImageDataUrls);
                for (String url : urls) {
                    if (url == null || url.isEmpty()) continue;
                    parts.put(new JSONObject()
                            .put("type", "image_url")
                            .put("image_url", new JSONObject()
                                    .put("url", url)));
                }
                value.put("content", parts);
            } else {
                value.put("content", effText);
            }
            messages.put(value);
        }
        // 工具轮历史（assistant tool_calls + tool 结果）追加在消息尾部
        if (toolHistory != null && toolHistory.length() > 0) {
            for (int i = 0; i < toolHistory.length(); i++) {
                messages.put(toolHistory.get(i));
            }
        }
        JSONObject body = new JSONObject()
                .put("model", model.model)
                .put("messages", messages)
                .put("stream", true);
        // MCP 工具注入（橘瓣同款：tools 数组，OpenAI function 格式）
        com.aion.chat.compose.data.McpClient.ToolDef[] mcpTools =
                com.aion.chat.compose.data.McpClient.cachedDefs().toArray(
                        new com.aion.chat.compose.data.McpClient.ToolDef[0]);
        if (mcpTools.length > 0) {
            org.json.JSONArray toolsArr = new org.json.JSONArray();
            for (com.aion.chat.compose.data.McpClient.ToolDef d : mcpTools) {
                org.json.JSONObject fn = new org.json.JSONObject()
                        .put("name", d.safeName)
                        .put("description", d.description);
                try {
                    fn.put("parameters", new org.json.JSONObject(d.schema));
                } catch (Exception ignored) {
                    fn.put("parameters", new org.json.JSONObject()
                            .put("type", "object").put("properties", new org.json.JSONObject()));
                }
                toolsArr.put(new org.json.JSONObject().put("type", "function").put("function", fn));
            }
            body.put("tools", toolsArr);
        }
        // 记忆自维护工具（照橘子岛 MemoryToolProvider）
        if (com.aion.chat.compose.data.MemoryTools.enabled()) {
            org.json.JSONArray mdefs = com.aion.chat.compose.data.MemoryTools.defs();
            org.json.JSONArray toolsArr2;
            if (mcpTools.length > 0) {
                toolsArr2 = body.optJSONArray("tools");
            } else {
                toolsArr2 = new org.json.JSONArray();
                body.put("tools", toolsArr2);
            }
            for (int i = 0; i < mdefs.length(); i++) toolsArr2.put(mdefs.get(i));
        }
        // 工作流工具（照橘瓣 workflow_*：create/list/get/update/delete/set_enabled/run）
        try {
            org.json.JSONArray wdefs = com.aion.chat.compose.data.WorkflowTools.defs();
            org.json.JSONArray toolsArr3 = body.optJSONArray("tools");
            if (toolsArr3 == null) {
                toolsArr3 = new org.json.JSONArray();
                body.put("tools", toolsArr3);
            }
            for (int i = 0; i < wdefs.length(); i++) toolsArr3.put(wdefs.get(i));
        } catch (Throwable ignored) { }
        if (promptCaching) {
            // 让 usage（含缓存命中数）在流末尾回来
            body.put("stream_options", new JSONObject().put("include_usage", true));
        }
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Authorization", "Bearer " + route.apiKey);
        headers.put("Content-Type", "application/json");
        // Base URL 自动补 /v1（设置页「拉取模型」会补，但手填的常不带——发消息的路径必须同款归一）
        String base = trimSlash(route.baseUrl);
        // 自定义路径不二次折叠（v9 审计 P1-3）：base 已带具体路径（如 /v1/messages）时原样使用
        if (base.endsWith("/chat/completions")) {
            return new NetworkRequest(base, HomecomingSnapshotStore.canonicalJson(body), headers);
        }
        if (base.contains("?")) {
            return new NetworkRequest(base, HomecomingSnapshotStore.canonicalJson(body), headers);
        }
        if (base.endsWith("/v1")) {
            return new NetworkRequest(
                    base + "/chat/completions",
                    HomecomingSnapshotStore.canonicalJson(body),
                    headers);
        }
        try {
            java.net.URL u = new java.net.URL(base);
            String path = u.getPath();
            if (path == null || path.isEmpty() || path.equals("/")) {
                return new NetworkRequest(
                        base + "/v1/chat/completions",
                        HomecomingSnapshotStore.canonicalJson(body),
                        headers);
            }
            // 其他自定义路径（如 /v1/messages）：视为完整对话端点，原样使用
            return new NetworkRequest(base, HomecomingSnapshotStore.canonicalJson(body), headers);
        } catch (Exception ignored) {
            base = base + "/v1";
        }
        return new NetworkRequest(
                base + "/chat/completions",
                HomecomingSnapshotStore.canonicalJson(body),
                headers);
    }

    private static NetworkRequest buildGemini(
            HomecomingRouteVault.Route route,
            HomecomingRouteVault.Model model,
            ChatRequest request) throws Exception {
        JSONArray contents = new JSONArray();
        StringBuilder system = new StringBuilder();
        for (ChatMessage message : request.messages) {
            if ("system".equals(message.role)) {
                if (system.length() > 0) {
                    system.append('\n');
                }
                system.append(message.text);
                continue;
            }
            JSONArray parts = new JSONArray().put(new JSONObject().put("text", message.text));
            contents.put(new JSONObject()
                    .put("role", "assistant".equals(message.role) ? "model" : "user")
                    .put("parts", parts));
        }
        JSONObject body = new JSONObject().put("contents", contents);
        if (system.length() > 0) {
            body.put("systemInstruction", new JSONObject()
                    .put("parts", new JSONArray().put(
                            new JSONObject().put("text", system.toString()))));
        }
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("x-goog-api-key", route.apiKey);
        headers.put("Content-Type", "application/json");
        return new NetworkRequest(
                trimSlash(route.baseUrl) + "/models/" + model.model
                        + ":streamGenerateContent?alt=sse",
                HomecomingSnapshotStore.canonicalJson(body),
                headers);
    }

    private static ProviderFrame parseFrame(String provider, String line)
            throws Exception {
        String value = line == null ? "" : line.trim();
        if (value.isEmpty() || !value.startsWith("data:")) {
            return ProviderFrame.EMPTY;
        }
        value = value.substring(5).trim();
        if (value.isEmpty() || "[DONE]".equals(value)) {
            return ProviderFrame.EMPTY;
        }
        JSONObject json = new JSONObject(value);
        if ("gemini".equals(provider)) {
            JSONArray candidates = json.optJSONArray("candidates");
            if (candidates == null || candidates.length() == 0) {
                return ProviderFrame.EMPTY;
            }
            JSONObject candidate = candidates.optJSONObject(0);
            JSONObject content = candidate == null
                    ? null : candidate.optJSONObject("content");
            JSONArray parts = content == null
                    ? null : content.optJSONArray("parts");
            JSONObject part = parts == null || parts.length() == 0
                    ? null : parts.optJSONObject(0);
            return new ProviderFrame(nullableString(part, "text"), "");
        }
        JSONArray choices = json.optJSONArray("choices");
        JSONObject choice = choices == null || choices.length() == 0
                ? null : choices.optJSONObject(0);
        JSONObject delta = choice == null ? null : choice.optJSONObject("delta");
        return new ProviderFrame(
                nullableString(delta, "content"),
                firstNonEmpty(
                        nullableString(delta, "reasoning_content"),
                        nullableString(delta, "reasoning")));
    }

    private static String nullableString(JSONObject value, String key) {
        if (value == null || !value.has(key) || value.isNull(key)) {
            return "";
        }
        return value.optString(key, "");
    }

    private static String firstNonEmpty(String first, String second) {
        return first.isEmpty() ? second : first;
    }

    private static String trimSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    public interface Transport {
        void execute(
                NetworkRequest request,
                ChunkConsumer consumer,
                CancelSignal cancel) throws Exception;
    }

    public interface ChunkConsumer {
        void accept(String line) throws Exception;
    }

    public interface CancelSignal {
        boolean isCancelled();
    }

    public interface StreamObserver {
        void onChunk(String text);
        void onComplete(String text);
        void onFailure(String code);
    }

    public static final class ProviderFrame {
        static final ProviderFrame EMPTY = new ProviderFrame("", "");

        final String visibleText;
        final String reasoningText;

        ProviderFrame(String visibleText, String reasoningText) {
            this.visibleText = visibleText == null ? "" : visibleText;
            this.reasoningText = reasoningText == null ? "" : reasoningText;
        }
    }

    public static final class OkHttpSseTransport implements Transport {
        private static final MediaType JSON =
                MediaType.get("application/json; charset=utf-8");
        private final OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();

        @Override
        public void execute(NetworkRequest network, ChunkConsumer consumer,
                CancelSignal cancel) throws Exception {
            Request.Builder builder = new Request.Builder()
                    .url(network.url)
                    .post(RequestBody.create(network.body, JSON));
            for (Map.Entry<String, String> header : network.headers.entrySet()) {
                builder.header(header.getKey(), header.getValue());
            }
            Call call = client.newCall(builder.build());
            try (Response response = call.execute()) {
                if (!response.isSuccessful() || response.body() == null) {
                    throw new IllegalStateException("model HTTP " + response.code());
                }
                BufferedReader reader = new BufferedReader(new InputStreamReader(
                        response.body().byteStream(), java.nio.charset.StandardCharsets.UTF_8));
                String line;
                while ((line = reader.readLine()) != null) {
                    if (cancel.isCancelled()) {
                        call.cancel();
                        return;
                    }
                    consumer.accept(line);
                }
            }
        }
    }

    public static final class NetworkRequest {
        public final String url;
        public final String body;
        public final Map<String, String> headers;

        NetworkRequest(String url, String body, Map<String, String> headers) {
            this.url = url;
            this.body = body;
            this.headers = Collections.unmodifiableMap(new LinkedHashMap<>(headers));
        }
    }

    public static final class ChatRequest {
        public final String requestId;
        public final String routeId;
        public final String modelKey;
        public final List<ChatMessage> messages;
        public final String imageDataUrl;
        /** 多图（v7：中转站路径与网关对齐）——首图在 imageDataUrl，其余在这里。 */
        public final List<String> extraImageDataUrls;

        public ChatRequest(String requestId, String routeId, String modelKey,
                List<ChatMessage> messages, String imageDataUrl) {
            this(requestId, routeId, modelKey, messages, imageDataUrl,
                    java.util.Collections.<String>emptyList());
        }

        public ChatRequest(String requestId, String routeId, String modelKey,
                List<ChatMessage> messages, String imageDataUrl,
                List<String> extraImageDataUrls) {
            this.requestId = required(requestId, "requestId");
            this.routeId = required(routeId, "routeId");
            this.modelKey = required(modelKey, "modelKey");
            this.messages = Collections.unmodifiableList(new ArrayList<>(messages));
            if (this.messages.isEmpty()) {
                throw new IllegalArgumentException("messages are required");
            }
            this.imageDataUrl = imageDataUrl == null ? "" : imageDataUrl;
            this.extraImageDataUrls = extraImageDataUrls == null
                    ? java.util.Collections.<String>emptyList()
                    : Collections.unmodifiableList(new ArrayList<>(extraImageDataUrls));
        }
    }

    public static final class ChatMessage {
        public final String role;
        public final String text;

        public ChatMessage(String role, String text) {
            this.role = required(role, "role");
            this.text = text == null ? "" : text;
        }

        @Override
        public String toString() {
            return role + ":" + text;
        }
    }

    private static String required(String value, String label) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(label + " is required");
        }
        return value.trim();
    }
}
