package com.aion.chat.homecoming;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public final class HomecomingChatRepository {
    private final Backend backend;
    private final String epochId;
    private final String deviceId;

    public HomecomingChatRepository(
            HomecomingDatabase database, String epochId, String deviceId) {
        this(new SQLiteBackend(database), epochId, deviceId);
    }

    HomecomingChatRepository(Backend backend, String epochId, String deviceId) {
        this.backend = backend;
        this.epochId = required(epochId, "epochId");
        this.deviceId = required(deviceId, "deviceId");
    }

    public Message commitUserMessage(
            String requestId, String timelineId, String senderId, String text,
            String attachmentKind, long createdAt) {
        return commit(requestId, timelineId, "user", senderId, text,
                attachmentKind, "", createdAt, false);
    }

    public Message commitAssistantMessage(
            String requestId, String timelineId, String senderId,
            String completeText, long createdAt) {
        return commit(requestId, timelineId, "assistant", senderId,
                completeText, "", "", createdAt, true);
    }

    public Message commitSystemMessage(
            String resultRequestId,
            String timelineId,
            String text,
            long createdAt) {
        return commit(
                resultRequestId,
                timelineId,
                "system",
                "system",
                text,
                "",
                "",
                createdAt,
                false);
    }

    public void failRequest(String requestId, String failureCode) {
        backend.fail(required(requestId, "requestId"),
                required(failureCode, "failureCode"));
    }

    /** 重试前把 failed 状态拉回 pending（复用 requestId 的重试不被永久阻断）。 */
    public void resetRequest(String requestId) {
        backend.reset(required(requestId, "requestId"));
    }

    /** 长按删除单条消息（本地删除；连带清掉图片/视频挂载映射）。 */
    public void deleteMessage(String id) {
        backend.deleteMessage(required(id, "id"));
    }

    public void beginAssistantRequest(
            String requestId, String timelineId, long createdAt) {
        validateTimeline(timelineId);
        backend.begin(
                required(requestId, "requestId"), timelineId, createdAt);
    }

    /** 把旧 epoch 的消息并回当前 epoch（当前时间线为空时才生效）——历史不因换代而"消失" */
    public void healEpoch(String timelineId) {
        validateTimeline(timelineId);
        backend.healEpoch(timelineId, epochId);
    }

    /** 消息树：重新生成准备（上下文截断 + 新回复挂父）；finishRegenerate 收尾 */
    public void prepareRegenerate(String parentId, long cutoffMs) {
        backend.prepareRegenerate(parentId, cutoffMs);
    }

    public void finishRegenerate() {
        backend.finishRegenerate();
    }

    /** 消息树：父消息（重新生成的上下文基准） */
    public Message parentOf(String messageId) {
        return backend.parentOf(required(messageId, "messageId"));
    }

    /** 消息树：版本兄弟数（含自身） */
    public int siblingCountOf(String messageId) {
        return backend.siblingCountOf(required(messageId, "messageId"));
    }

    /** 消息树：当前版本在兄弟中的序号（1 起；无兄弟=1） */
    public int siblingIndexOf(String messageId) {
        return backend.siblingIndexOf(required(messageId, "messageId"));
    }

    /** 消息树：版本切换（±1），返回切换后的消息 id；无相邻版本返回 null */
    public String switchSibling(String messageId, int delta) {
        return backend.switchSibling(required(messageId, "messageId"), delta);
    }

    /** 消息树 v1：parent 链一次性回填（幂等，Wiring 初始化时调用一次）。 */
    public static void backfillParents(android.content.Context context) {
        // v10 P2：Wiring 每次发送都会 safeCreate，回填是全表扫描——进程内只跑一次
        if (backfillDone) return;
        backfillDone = true;
        try {
            android.database.sqlite.SQLiteDatabase db =
                    com.aion.chat.homecoming.HomecomingDatabase.sharedWritable(context);
            try {
                db.execSQL("ALTER TABLE chat_message ADD COLUMN parent_id TEXT NOT NULL DEFAULT ''");
            } catch (RuntimeException ignored) { }
            java.util.List<String> timelines = new java.util.ArrayList<>();
            try (android.database.Cursor c = db.rawQuery(
                    "SELECT DISTINCT timeline_id FROM chat_message", null)) {
                while (c.moveToNext()) timelines.add(c.getString(0));
            }
            for (String tl : timelines) {
                java.util.List<String> ids = new java.util.ArrayList<>();
                try (android.database.Cursor c = db.rawQuery(
                        "SELECT id FROM chat_message WHERE timeline_id=? ORDER BY created_at ASC, rowid ASC",
                        new String[]{tl})) {
                    while (c.moveToNext()) ids.add(c.getString(0));
                }
                String prev = "";
                for (String id : ids) {
                    db.execSQL("UPDATE chat_message SET parent_id=? WHERE id=? AND parent_id=''",
                            new Object[]{prev, id});
                    prev = id;
                }
            }
        } catch (Exception ignored) { }
    }

    /** 工作流条件 last_chat_ago：最近一条消息时间戳（无记录=0；主时间线） */
    public static long lastChatAtMs(android.content.Context context) {
        try {
            android.database.sqlite.SQLiteDatabase db =
                    com.aion.chat.homecoming.HomecomingDatabase.sharedReadable(context);
            try (android.database.Cursor c = db.rawQuery(
                    "SELECT MAX(created_at) FROM chat_message WHERE timeline_id=?",
                    new String[]{"main_private"})) {
                return (c.moveToFirst() && !c.isNull(0)) ? c.getLong(0) : 0L;
            }
        } catch (Exception ignored) {
            return 0L;
        }
    }

    public List<Message> listMessages(
            String timelineId, long beforeCreatedAt, int limit) {
        validateTimeline(timelineId);
        if (limit < 1 || limit > 500) {
            throw new IllegalArgumentException("limit must be 1..500");
        }
        return backend.list(epochId, timelineId, beforeCreatedAt, limit);
    }

    public void recordDeferredControls(
            String requestId,
            List<HomecomingControlParser.ControlEvent> controls,
            long createdAt) {
        for (int index = 0; index < controls.size(); index++) {
            HomecomingControlParser.ControlEvent control = controls.get(index);
            String entityId = required(requestId, "requestId")
                    + ":" + index + ":" + control.type;
            HomecomingOperationJournal.Operation operation =
                    HomecomingOperationJournal.create(
                            epochId, deviceId, "deferred_control", entityId,
                            "create", "", control.rawTag, createdAt);
            backend.recordControl(entityId, operation);
        }
    }

    private Message commit(
            String requestId, String timelineId, String role, String senderId,
            String text, String attachmentKind, String transcript,
            long createdAt, boolean assistant) {
        validateTimeline(timelineId);
        String normalizedText = text == null ? "" : text.trim();
        if (normalizedText.isEmpty() && (attachmentKind == null || attachmentKind.isEmpty())) {
            throw new IllegalArgumentException("text or attachment is required");
        }
        // 消息树挂父：重新生成=指定父；常规=当前链尾（该时间线最新一条）
        String parentId = backend.nextCommitParentId();
        if (parentId == null || parentId.isEmpty()) {
            parentId = backend.lastMessageIdInTimeline(epochId, timelineId);
        }
        Message candidate = new Message(
                UUID.randomUUID().toString(),
                required(requestId, "requestId"),
                epochId,
                timelineId,
                role,
                required(senderId, "senderId"),
                normalizedText,
                attachmentKind == null ? "" : attachmentKind,
                transcript == null ? "" : transcript,
                createdAt,
                "committed",
                parentId);
        HomecomingOperationJournal.Operation operation =
                HomecomingOperationJournal.create(
                        epochId,
                        deviceId,
                        "message",
                        candidate.id,
                        "create",
                        "",
                        payload(candidate),
                        createdAt);
        return backend.commit(candidate, operation, assistant);
    }

    /** backfillParents 进程内一次性标记（v10 P2：Wiring 每次发送 safeCreate 都会调它，全表扫描不能反复跑） */
    private static volatile boolean backfillDone = false;

    private static String payload(Message message) {
        try {
            return HomecomingSnapshotStore.canonicalJson(new JSONObject()
                    .put("id", message.id)
                    .put("request_id", message.requestId)
                    .put("epoch_id", message.epochId)
                    .put("timeline_id", message.timelineId)
                    .put("role", message.role)
                    .put("sender_id", message.senderId)
                    .put("text", message.text)
                    .put("attachment_kind", message.attachmentKind)
                    .put("attachment_transcript", message.attachmentTranscript)
                    .put("created_at", message.createdAt));
        } catch (Exception exception) {
            throw new IllegalStateException("could not encode message operation", exception);
        }
    }

    private static void validateTimeline(String timelineId) {
        if (!"main_private".equals(timelineId)
                && !"companion_private".equals(timelineId)
                && !"group".equals(timelineId)) {
            throw new IllegalArgumentException("unsupported timeline");
        }
    }

    public interface Backend {
        Message commit(
                Message candidate,
                HomecomingOperationJournal.Operation operation,
                boolean assistant);
        void fail(String requestId, String code);
        void reset(String requestId);
        void deleteMessage(String id);
        default void begin(String requestId, String timelineId, long createdAt) {
        }
        List<Message> list(
                String epochId, String timelineId, long beforeCreatedAt, int limit);
        default void recordControl(
                String entityId, HomecomingOperationJournal.Operation operation) {
        }
        /** epoch 自愈：当前时间线为空而历史 epoch 有消息时并回（mode 位丢失换代过的补救） */
        default void healEpoch(String timelineId, String currentEpoch) {
        }
        /** 重新生成：上下文截断 + 新回复挂父（引擎在 finally 里 finish） */
        default void prepareRegenerate(String parentId, long cutoffMs) {
        }
        default void finishRegenerate() {
        }
        /** 消息树：下一条 assistant 的挂父（重新生成期间由 prepareRegenerate 设置） */
        String nextCommitParentId();
        /** 消息树：该时间线当前最后一条消息的 id（空时间线返回 ""） */
        String lastMessageIdInTimeline(String epochId, String timelineId);
        /** 消息树：版本兄弟数量（无兄弟=1） */
        default int siblingCountOf(String messageId) {
            return 1;
        }
        /** 消息树：当前版本序号（1 起） */
        default int siblingIndexOf(String messageId) {
            return 1;
        }
        /** 消息树：版本切换（delta ±1），返回切换后的消息 id（失败 null） */
        default String switchSibling(String messageId, int delta) {
            return null;
        }
        /** 消息树：取父消息（无父返回 null） */
        default Message parentOf(String messageId) {
            return null;
        }
    }

    public static final class SQLiteBackend implements Backend {
        private final HomecomingDatabase helper;
        // 重新生成状态：list 截断点 + 下一条 assistant 的挂父（prepare 设置，finish 清除）
        private volatile long regenCutoffMs = 0L;
        private volatile String nextCommitParent = null;

        SQLiteBackend(HomecomingDatabase helper) {
            this.helper = helper;
        }

        @Override
        public void prepareRegenerate(String parentId, long cutoffMs) {
            this.nextCommitParent = parentId;
            this.regenCutoffMs = cutoffMs;
        }

        @Override
        public void finishRegenerate() {
            this.nextCommitParent = null;
            this.regenCutoffMs = 0L;
        }

        @Override
        public String nextCommitParentId() {
            return nextCommitParent;
        }

        @Override
        public String lastMessageIdInTimeline(String epochId, String timelineId) {
            SQLiteDatabase database = helper.getReadableDatabase();
            try (Cursor cursor = database.rawQuery(
                    "SELECT id FROM chat_message WHERE epoch_id=? AND timeline_id=? "
                            + "ORDER BY created_at DESC, rowid DESC LIMIT 1",
                    new String[]{epochId, timelineId})) {
                return cursor.moveToFirst() ? cursor.getString(0) : "";
            }
        }

        @Override
        public Message parentOf(String messageId) {
            SQLiteDatabase database = helper.getReadableDatabase();
            try (Cursor cursor = database.rawQuery(
                    "SELECT parent_id FROM chat_message WHERE id=?",
                    new String[]{messageId})) {
                if (!cursor.moveToFirst()) return null;
                String parentId = cursor.getString(0);
                if (parentId == null || parentId.isEmpty()) return null;
                return messageById(database, parentId);
            }
        }

        @Override
        public int siblingCountOf(String messageId) {
            SQLiteDatabase database = helper.getReadableDatabase();
            String parent = parentMessageId(database, messageId);
            if (parent == null || parent.isEmpty()) return 1;
            try (Cursor cursor = database.rawQuery(
                    "SELECT COUNT(*) FROM chat_message WHERE parent_id=?",
                    new String[]{parent})) {
                return cursor.moveToFirst() ? cursor.getInt(0) : 1;
            }
        }

        @Override
        public int siblingIndexOf(String messageId) {
            SQLiteDatabase database = helper.getReadableDatabase();
            String parent = parentMessageId(database, messageId);
            if (parent == null || parent.isEmpty()) return 1;
            java.util.List<String> ids = new java.util.ArrayList<>();
            try (Cursor cursor = database.rawQuery(
                    "SELECT id FROM chat_message WHERE parent_id=? ORDER BY created_at ASC, rowid ASC",
                    new String[]{parent})) {
                while (cursor.moveToNext()) ids.add(cursor.getString(0));
            }
            int idx = ids.indexOf(messageId);
            return idx >= 0 ? idx + 1 : 1;
        }

        @Override
        public String switchSibling(String messageId, int delta) {
            SQLiteDatabase database = helper.getWritableDatabase();
            String parent = parentMessageId(database, messageId);
            if (parent == null || parent.isEmpty()) return null;
            java.util.List<String> ids = new java.util.ArrayList<>();
            try (Cursor cursor = database.rawQuery(
                    "SELECT id FROM chat_message WHERE parent_id=? ORDER BY created_at ASC, rowid ASC",
                    new String[]{parent})) {
                while (cursor.moveToNext()) ids.add(cursor.getString(0));
            }
            int idx = ids.indexOf(messageId);
            int target = idx + delta;
            if (target < 0 || target >= ids.size()) return null;
            String targetId = ids.get(target);
            // 分支选择持久化：snapshot_meta key=branch:<parent> value=<child>
            database.execSQL(
                    "INSERT INTO snapshot_meta(key, value) VALUES(?, ?) "
                            + "ON CONFLICT(key) DO UPDATE SET value=excluded.value",
                    new Object[]{"branch:" + parent, targetId});
            return targetId;
        }

        private String parentMessageId(SQLiteDatabase database, String messageId) {
            try (Cursor cursor = database.rawQuery(
                    "SELECT parent_id FROM chat_message WHERE id=?",
                    new String[]{messageId})) {
                return cursor.moveToFirst() ? cursor.getString(0) : null;
            }
        }

        private Message messageById(SQLiteDatabase database, String messageId) {
            try (Cursor cursor = database.rawQuery(
                    "SELECT id,request_id,epoch_id,timeline_id,role,sender_id,text_content,"
                            + "attachment_kind,attachment_transcript,created_at,delivery_state,parent_id "
                            + "FROM chat_message WHERE id=?",
                    new String[]{messageId})) {
                return cursor.moveToFirst() ? readMessage(cursor) : null;
            }
        }

        @Override
        public Message commit(
                Message candidate,
                HomecomingOperationJournal.Operation operation,
                boolean assistant) {
            SQLiteDatabase database = helper.getWritableDatabase();
            database.beginTransaction();
            try {
                Message existing = find(database, candidate.requestId, candidate.role);
                if (existing != null) {
                    database.setTransactionSuccessful();
                    return existing;
                }
                String state = requestState(database, candidate.requestId);
                if (assistant && "failed".equals(state)) {
                    throw new IllegalStateException("request already failed");
                }
                upsertRequest(database, candidate, assistant ? "complete" : "pending");
                insertMessage(database, candidate);
                insertOperation(database, operation);
                database.setTransactionSuccessful();
                return candidate;
            } finally {
                database.endTransaction();
            }
        }

        @Override
        public void reset(String requestId) {
            SQLiteDatabase database = helper.getWritableDatabase();
            ContentValues values = new ContentValues();
            values.put("state", "pending");
            values.put("failure_code", "");
            values.put("updated_at", System.currentTimeMillis());
            database.update(
                    "chat_request", values, "request_id=?", new String[]{requestId});
        }

        @Override
        public void deleteMessage(String id) {
            SQLiteDatabase database = helper.getWritableDatabase();
            database.beginTransaction();
            try {
                database.execSQL("DELETE FROM chat_message WHERE id=?", new Object[]{id});
                try {
                    database.execSQL("DELETE FROM chat_image_local WHERE message_id=?", new Object[]{id});
                } catch (RuntimeException ignored) {
                    // 挂载表可能还没建过（没发过图），不挡删除
                }
                database.setTransactionSuccessful();
            } finally {
                database.endTransaction();
            }
        }

        @Override
        public void fail(String requestId, String code) {
            SQLiteDatabase database = helper.getWritableDatabase();
            ContentValues values = new ContentValues();
            values.put("state", "failed");
            values.put("failure_code", code);
            values.put("updated_at", System.currentTimeMillis());
            int changed = database.update(
                    "chat_request", values, "request_id=?", new String[]{requestId});
            if (changed == 0) {
                throw new IllegalStateException("unknown request");
            }
        }

        @Override
        public void begin(String requestId, String timelineId, long createdAt) {
            SQLiteDatabase database = helper.getWritableDatabase();
            ContentValues values = new ContentValues();
            values.put("request_id", requestId);
            values.put("timeline_id", timelineId);
            values.put("state", "pending");
            values.put("created_at", createdAt);
            values.put("updated_at", createdAt);
            database.insertWithOnConflict(
                    "chat_request", null, values, SQLiteDatabase.CONFLICT_IGNORE);
        }

        @Override
        public List<Message> list(
                String epochId, String timelineId, long beforeCreatedAt, int limit) {
            SQLiteDatabase database = helper.getReadableDatabase();
            ArrayList<Message> rows = new ArrayList<>();
            long effectiveBefore = regenCutoffMs > 0
                    ? Math.min(beforeCreatedAt, regenCutoffMs) : beforeCreatedAt;
            try (Cursor cursor = database.rawQuery(
                    "SELECT id,request_id,epoch_id,timeline_id,role,sender_id,text_content,"
                            + "attachment_kind,attachment_transcript,created_at,delivery_state,parent_id "
                            + "FROM chat_message "
                            + "WHERE epoch_id=? AND timeline_id=? AND created_at<? "
                            + "ORDER BY created_at DESC,id DESC LIMIT ?",
                    new String[]{epochId, timelineId, String.valueOf(effectiveBefore),
                            String.valueOf(2000)})) {
                while (cursor.moveToNext()) {
                    rows.add(readMessage(cursor));
                }
            }
            Collections.reverse(rows);
            // 消息树 v1：沿 parent 链走激活分支（用户选中的版本优先，未选=最新），孤儿行兜底
            java.util.Map<String, String> branches = loadBranchSelections(database);
            java.util.List<Message> path = walkActivePath(rows, branches);
            java.util.List<Message> window = new ArrayList<>();
            for (Message m : path) {
                if (m.createdAt < effectiveBefore) window.add(m);
            }
            int from = Math.max(0, window.size() - limit);
            ArrayList<Message> out = new ArrayList<>(window.subList(from, window.size()));
            Collections.reverse(out);
            return Collections.unmodifiableList(out);
        }

        /** 沿 parent 链走激活分支：每个父节点取选中的子节点（无记录=最新），孤儿行兜底追加。 */
        /** 分支选择表：branch:<parent> -> <child>（switchSibling 写入） */
        private static java.util.Map<String, String> loadBranchSelections(SQLiteDatabase database) {
            java.util.Map<String, String> out = new java.util.HashMap<>();
            try (Cursor cursor = database.rawQuery(
                    "SELECT key, value FROM snapshot_meta WHERE key LIKE 'branch:%'", null)) {
                while (cursor.moveToNext()) {
                    out.put(cursor.getString(0).substring(7), cursor.getString(1));
                }
            } catch (Exception ignored) { }
            return out;
        }

        private static java.util.List<Message> walkActivePath(
                java.util.List<Message> rows,
                java.util.Map<String, String> branchSelection) {
            java.util.LinkedHashMap<String, java.util.List<Message>> kids = new java.util.LinkedHashMap<>();
            java.util.List<Message> roots = new java.util.ArrayList<>();
            for (Message m : rows) {
                String p = m.parentId == null || m.parentId.isEmpty() ? "__root__" : m.parentId;
                kids.computeIfAbsent(p, k -> new ArrayList<>()).add(m);
            }
            for (java.util.List<Message> l : kids.values()) {
                l.sort(java.util.Comparator.comparingLong(m -> m.createdAt));
            }
            roots.addAll(kids.getOrDefault("__root__", new ArrayList<>()));
            roots.sort(java.util.Comparator.comparingLong(m -> m.createdAt));
            java.util.List<Message> out = new ArrayList<>();
            if (roots.isEmpty()) return out;
            Message cur = roots.get(0);
            out.add(cur);
            int guard = 0;
            while (guard++ < 5000) {
                java.util.List<Message> k = kids.get(cur.id);
                if (k == null || k.isEmpty()) break;
                // 用户选中的版本优先；未选择=最新（重新生成的新版本自动成为默认）
                String chosen = branchSelection.get(cur.id);
                Message next = null;
                if (chosen != null) {
                    for (Message m : k) {
                        if (m.id.equals(chosen)) { next = m; break; }
                    }
                }
                if (next == null) next = k.get(k.size() - 1);
                if (out.contains(next)) break;
                out.add(next);
                cur = next;
            }
            // 孤儿兜底：父为空或父在库里真不存在的行（链断裂/游离/旧数据），按时间追加——永不丢消息。
            // v10 P2 修正：父在整棵树里存在但不在激活路径上的行（含"孙代"——父挂在被隐藏的分支上）
            // 一律隐藏：它们属于未激活分支，switchSibling 切过去时会整链回来；拼回末尾反而是错的。
            java.util.Set<String> inPath = new java.util.HashSet<>();
            for (Message m : out) inPath.add(m.id);
            java.util.Set<String> allIds = new java.util.HashSet<>();
            for (Message m : rows) allIds.add(m.id);
            java.util.List<Message> rest = new ArrayList<>();
            for (Message m : rows) {
                if (inPath.contains(m.id)) continue;
                boolean parentKnown = m.parentId != null && !m.parentId.isEmpty()
                        && allIds.contains(m.parentId);
                if (parentKnown) {
                    continue; // 父在树里：属于某个分支（激活的子版本或未激活分支的后代）——隐藏
                }
                rest.add(m);
            }
            rest.sort(java.util.Comparator.comparingLong(m -> m.createdAt));
            out.addAll(rest);
            return out;
        }

        @Override
        public void healEpoch(String timelineId, String currentEpoch) {
            SQLiteDatabase database = helper.getWritableDatabase();
            try (Cursor cursor = database.rawQuery(
                    "SELECT COUNT(*) FROM chat_message WHERE epoch_id=? AND timeline_id=?",
                    new String[]{currentEpoch, timelineId})) {
                if (cursor.moveToFirst() && cursor.getLong(0) > 0) {
                    return; // 当前 epoch 有消息，不动
                }
            }
            // 历史 epoch 的消息并回当前 epoch（消息一直在库里，之前只是被换代过滤掉了）
            database.execSQL(
                    "UPDATE chat_message SET epoch_id=? WHERE timeline_id=? AND epoch_id<>?",
                    new Object[]{currentEpoch, timelineId, currentEpoch});
        }

        @Override
        public void recordControl(
                String entityId, HomecomingOperationJournal.Operation operation) {
            SQLiteDatabase database = helper.getWritableDatabase();
            try (Cursor cursor = database.rawQuery(
                    "SELECT 1 FROM operation_journal "
                            + "WHERE entity_type='deferred_control' AND entity_id=? LIMIT 1",
                    new String[]{entityId})) {
                if (cursor.moveToFirst()) {
                    return;
                }
            }
            database.beginTransaction();
            try {
                insertOperation(database, operation);
                database.setTransactionSuccessful();
            } finally {
                database.endTransaction();
            }
        }

        private static void upsertRequest(
                SQLiteDatabase database, Message message, String state) {
            ContentValues values = new ContentValues();
            values.put("request_id", message.requestId);
            values.put("timeline_id", message.timelineId);
            values.put("state", state);
            values.put("created_at", message.createdAt);
            values.put("updated_at", message.createdAt);
            database.insertWithOnConflict(
                    "chat_request", null, values, SQLiteDatabase.CONFLICT_IGNORE);
            ContentValues update = new ContentValues();
            update.put("state", state);
            update.put("updated_at", message.createdAt);
            database.update("chat_request", update, "request_id=?",
                    new String[]{message.requestId});
        }

        private static void insertMessage(SQLiteDatabase database, Message message) {
            ContentValues values = new ContentValues();
            values.put("id", message.id);
            values.put("request_id", message.requestId);
            values.put("epoch_id", message.epochId);
            values.put("timeline_id", message.timelineId);
            values.put("role", message.role);
            values.put("sender_id", message.senderId);
            values.put("text_content", message.text);
            values.put("attachment_kind", message.attachmentKind);
            values.put("attachment_transcript", message.attachmentTranscript);
            values.put("created_at", message.createdAt);
            values.put("delivery_state", message.deliveryState);
            values.put("parent_id", message.parentId);
            database.insertOrThrow("chat_message", null, values);
        }

        private static void insertOperation(
                SQLiteDatabase database, HomecomingOperationJournal.Operation operation) {
            long sequence = 1L;
            try (Cursor cursor = database.rawQuery(
                    "SELECT COALESCE(MAX(device_seq),0)+1 FROM operation_journal", null)) {
                if (cursor.moveToFirst()) {
                    sequence = cursor.getLong(0);
                }
            }
            ContentValues values = new ContentValues();
            values.put("op_id", operation.opId);
            values.put("epoch_id", operation.epochId);
            values.put("device_id", operation.deviceId);
            values.put("device_seq", sequence);
            values.put("entity_type", operation.entityType);
            values.put("entity_id", operation.entityId);
            values.put("action", operation.action);
            values.put("base_revision", operation.baseRevision);
            values.put("payload_json", operation.payloadJson);
            values.put("created_at", operation.createdAt);
            database.insertOrThrow("operation_journal", null, values);
        }

        private static Message find(SQLiteDatabase database, String requestId, String role) {
            try (Cursor cursor = database.rawQuery(
                    "SELECT id,request_id,epoch_id,timeline_id,role,sender_id,text_content,"
                            + "attachment_kind,attachment_transcript,created_at,delivery_state,parent_id "
                            + "FROM chat_message WHERE request_id=? AND role=?",
                    new String[]{requestId, role})) {
                return cursor.moveToFirst() ? readMessage(cursor) : null;
            }
        }

        private static String requestState(SQLiteDatabase database, String requestId) {
            try (Cursor cursor = database.rawQuery(
                    "SELECT state FROM chat_request WHERE request_id=?",
                    new String[]{requestId})) {
                return cursor.moveToFirst() ? cursor.getString(0) : "";
            }
        }

        private static Message readMessage(Cursor cursor) {
            return new Message(
                    cursor.getString(0), cursor.getString(1), cursor.getString(2),
                    cursor.getString(3), cursor.getString(4), cursor.getString(5),
                    cursor.getString(6), cursor.getString(7), cursor.getString(8),
                    cursor.getLong(9), cursor.getString(10), cursor.getString(11));
        }
    }

    public static final class Message {
        public final String id;
        public final String requestId;
        public final String epochId;
        public final String timelineId;
        public final String role;
        public final String senderId;
        public final String text;
        public final String attachmentKind;
        public final String attachmentTranscript;
        public final long createdAt;
        public final String deliveryState;
        public final String parentId;

        public Message(String id, String requestId, String epochId, String timelineId, String role,
                String senderId, String text, String attachmentKind,
                String attachmentTranscript, long createdAt, String deliveryState) {
            this(id, requestId, epochId, timelineId, role, senderId, text,
                    attachmentKind, attachmentTranscript, createdAt, deliveryState, "");
        }

        public Message(String id, String requestId, String epochId, String timelineId, String role,
                String senderId, String text, String attachmentKind,
                String attachmentTranscript, long createdAt, String deliveryState, String parentId) {
            this.id = id;
            this.requestId = requestId;
            this.epochId = epochId;
            this.timelineId = timelineId;
            this.role = role;
            this.senderId = senderId;
            this.text = text;
            this.attachmentKind = attachmentKind;
            this.attachmentTranscript = attachmentTranscript;
            this.createdAt = createdAt;
            this.deliveryState = deliveryState;
            this.parentId = parentId == null ? "" : parentId;
        }

        Message(String id, String requestId, String timelineId, String role,
                String senderId, String text, String attachmentKind,
                String attachmentTranscript, long createdAt, String deliveryState) {
            this(id, requestId, "legacy", timelineId, role, senderId, text,
                    attachmentKind, attachmentTranscript, createdAt, deliveryState);
        }
    }

    private static String required(String value, String label) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(label + " is required");
        }
        return value.trim();
    }
}
