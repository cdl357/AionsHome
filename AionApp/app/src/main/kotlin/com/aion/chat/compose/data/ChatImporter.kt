package com.aion.chat.compose.data

import android.content.Context

/**
 * 橘瓣聊天记录导入器：
 * 解析橘瓣（RikkaHub）导出的 Markdown（chat-export-*.md）：
 * ```
 * # 对话标题
 * *Exported on ...*
 * **User**:
 * 文本
 * **Assistant**:
 * 文本（含 > 思考引用 / **Tool** 行 / ![Image] 行，跳过）
 * ```
 * 导入为当前聊天时间线的历史消息（按顺序落库，时间用导入时刻顺延 1s/条 保序）。
 */
object ChatImporter {

    data class ParsedMessage(val role: String, val text: String)
    data class ImportResult(val title: String, val messages: List<ParsedMessage>)

    fun parse(markdown: String): ImportResult {
        var title = ""
        val messages = mutableListOf<ParsedMessage>()
        var role: String? = null
        val buf = StringBuilder()

        fun flush() {
            val text = buf.toString().trim()
            buf.setLength(0)
            if (role != null && text.isNotEmpty()) {
                messages.add(ParsedMessage(role!!, text))
            }
        }

        markdown.lines().forEach { raw ->
            val line = raw.trimEnd()
            val t = line.trim()
            when {
                t.startsWith("# ") -> { flush(); title = t.removePrefix("# ").trim() }
                t.startsWith("*Exported on") -> { flush() }
                t.startsWith("**User**") -> { flush(); role = "user" }
                t.startsWith("**Assistant**") -> { flush(); role = "assistant" }
                t.startsWith("**Tool**") -> { /* 工具行跳过 */ }
                t.startsWith("![Image](") -> { /* 图片行跳过 */ }
                t.startsWith("> ") || t == ">" -> { /* 思考引用行跳过 */ }
                t.startsWith("*") && t.endsWith("*") && t.contains("Exported") -> { flush() }
                else -> {
                    if (role != null) {
                        buf.append(line).append('\n')
                    } else if (t.isNotEmpty() && title.isEmpty()) {
                        // 标记前的散行（无标题导出的容错）：当 user 开头
                        role = "user"
                        buf.append(line).append('\n')
                    }
                }
            }
        }
        flush()
        return ImportResult(title, messages)
    }

    /**
     * 落库到当前聊天时间线。返回成功导入条数（-1 = 异常）。
     * created_at 用导入时刻往回顺延（每条 1s），保证顺序与可读性。
     */
    fun importToCurrentChat(context: Context, markdown: String): Int {
        val parsed = parse(markdown)
        if (parsed.messages.isEmpty()) return 0
        val prefs = context.getSharedPreferences("homecoming_prefs", Context.MODE_PRIVATE)
        val epoch = prefs.getString("epoch", "legacy") ?: "legacy"
        val db = com.aion.chat.homecoming.HomecomingDatabase.sharedWritable(context)
        return try {
            val now = System.currentTimeMillis()
            var ok = 0
            db.beginTransaction()
            parsed.messages.forEachIndexed { i, m ->
                val createdAt = now - (parsed.messages.size - i) * 1000L
                db.execSQL(
                    "INSERT INTO chat_message(id, request_id, epoch_id, timeline_id, role, sender_id, " +
                        "text_content, attachment_kind, attachment_transcript, created_at, delivery_state) " +
                        "VALUES(?,?,?,?,?,?,?,?,?,?,?)",
                    arrayOf(
                        "imp_" + now + "_" + i,
                        "imp_req_" + now + "_" + i,
                        epoch,
                        "main_private",
                        m.role,
                        if (m.role == "assistant") "main" else "user",
                        m.text,
                        "",
                        "",
                        createdAt,
                        "sent"
                    )
                )
                ok++
            }
            db.setTransactionSuccessful()
            ok
        } catch (e: Exception) {
            -1
        } finally {
            runCatching { db.endTransaction() }
        }
    }
}
