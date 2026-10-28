package com.aion.chat.compose.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * 即时指令（nest-instant-command 的 Android 版）：
 * - Sean 在回复里嵌 <countdown>{"title":"去喝水","minutes":1}</countdown>（minutes 可省=不计时）
 * - App 解析后藏起标签，变成屏幕右缘的计时药丸（见 ui/command/CommandPill.kt）
 * - 她左滑=完成（记录用时），长按=取消（不告诉他）
 * - 她下一条消息发出前，把【指令完成反馈】拼进上下文，Sean 就知道用时多少
 */
object CommandStore {

    private const val PREFS = "instant_commands"
    private const val KEY_CMDS = "cmds"
    private const val KEY_PROCESSED = "processed_msg_ids"
    private const val KEY_PILL_Y = "pill_y_fraction"

    const val STATE_PENDING = "pending"
    const val STATE_DONE = "done"        // 完成，等待随下一条消息反馈
    const val STATE_CANCELLED = "cancelled"

    data class Cmd(
        val id: String,
        val title: String,
        val issuedMs: Long,
        val presetSec: Int,      // 0 = 不限时
        val state: String,
        val doneMs: Long = 0L
    )

    // ── 标签解析 ──

    // <clinit> 里编译正则曾在一台设备上抛 ExceptionInInitializerError（炸掉整个主页）。
    // lazy + 兜底：把任何初始化风险挪出静态初始化器，最坏情况退化为宽松匹配。
    private val RE: Regex by lazy {
        runCatching {
            Regex("<countdown>\\s*(\\{.*?})\\s*</countdown>", RegexOption.DOT_MATCHES_ALL)
        }.getOrElse { Regex("<countdown>(.*?)</countdown>") }
    }

    /** 解析正文里的指令标签（title + minutes）。 */
    fun parse(text: String): List<Pair<String, Int>> {
        val out = mutableListOf<Pair<String, Int>>()
        RE.findAll(text).forEach { m ->
            runCatching {
                val o = JSONObject(m.groupValues[1])
                val title = o.optString("title").trim()
                if (title.isNotEmpty()) {
                    val minutes = o.optDouble("minutes", 0.0)
                    out.add(title to (minutes * 60).toInt().coerceAtLeast(0))
                }
            }
        }
        return out
    }

    /** 把标签从展示正文里藏掉。 */
    fun strip(text: String): String =
        RE.replace(text, "").replace(Regex("\\s{3,}"), "  ").trim()

    /** 扫一条他的消息：新消息里的指令入库（按消息 id 去重，重启也不会重复）。 */
    fun scanMessage(context: Context, msgId: String, text: String) {
        if (!text.contains("<countdown")) return
        val prefs = prefs(context)
        val processed = prefs.getStringSet(KEY_PROCESSED, emptySet()).orEmpty()
        if (msgId in processed) return
        val items = parse(text)
        val cmds = loadCmds(context).toMutableList()
        items.forEach { (title, sec) ->
            cmds.add(
                Cmd(
                    id = UUID.randomUUID().toString().take(12),
                    title = title.take(30),
                    issuedMs = System.currentTimeMillis(),
                    presetSec = sec,
                    state = STATE_PENDING
                )
            )
        }
        save(context, cmds)
        prefs.edit().putStringSet(KEY_PROCESSED, processed + msgId).apply()
    }

    // ── 药丸状态 ──

    fun pending(context: Context): List<Cmd> =
        loadCmds(context).filter { it.state == STATE_PENDING }

    fun hasFeedbackReady(context: Context): Boolean =
        loadCmds(context).any { it.state == STATE_DONE }

    fun complete(context: Context, id: String) {
        mutate(context, id) { c -> c.copy(state = STATE_DONE, doneMs = System.currentTimeMillis()) }
    }

    fun cancel(context: Context, id: String) {
        mutate(context, id) { c -> c.copy(state = STATE_CANCELLED) }
    }

    private fun mutate(context: Context, id: String, f: (Cmd) -> Cmd) {
        val cmds = loadCmds(context).map { if (it.id == id) f(it) else it }
        save(context, cmds)
    }

    /** 取走完成反馈（拼进她下一条消息），取走即清。 */
    fun consumeFeedback(context: Context): String? {
        val cmds = loadCmds(context)
        val done = cmds.filter { it.state == STATE_DONE }
        if (done.isEmpty()) return null
        val lines = done.joinToString("\n") { c ->
            val spent = ((c.doneMs - c.issuedMs) / 1000L).toInt().coerceAtLeast(1)
            val base = "「${c.title}」用时 ${durText(spent)}"
            if (c.presetSec > 0) {
                val diff = spent - c.presetSec
                base + if (diff >= 0) "（比预设 ${c.presetSec}s 慢 ${durText(diff)}）" else "（比预设 ${c.presetSec}s 快 ${durText(-diff)}）"
            } else base
        }
        save(context, cmds.filter { it.state != STATE_DONE })
        return "【指令完成反馈】\n$lines"
    }

    private fun durText(sec: Int): String {
        val m = sec / 60
        val s = sec % 60
        return when {
            m <= 0 -> "${s} 秒"
            s == 0 -> "${m} 分"
            else -> "${m} 分 ${s} 秒"
        }
    }

    // ── 药丸位置（右缘，纵向分数 0~1） ──

    fun pillY(context: Context): Float = prefs(context).getFloat(KEY_PILL_Y, 0.42f)

    fun savePillY(context: Context, fraction: Float) {
        prefs(context).edit().putFloat(KEY_PILL_Y, fraction.coerceIn(0.06f, 0.86f)).apply()
    }

    // ── 让他知道有这个能力（一次性写进 identity_snapshot 的 system_prompt） ──

    private const val KNOWLEDGE =
        "\n\n[即时指令] 你可以在回复里给她下一条带倒计时的即时指令：嵌入 " +
            "<countdown>{\"title\":\"去喝水\",\"minutes\":1}</countdown> " +
            "（minutes 可省略=不计时；一次回复至多一条；只在需要推她去做小事时用，别频繁）。" +
            "标签不会显示给她，会变成她屏幕边缘的计时药丸，倒计时走完会转超时正向计时。" +
            "她完成后左滑，下一轮你会收到用时反馈；长按取消你不会知道。不要向她解释这个标签的原理。"

    fun ensureIssuerKnowledge(context: Context) {
        runCatching {
            val db = com.aion.chat.homecoming.HomecomingDatabase(context).writableDatabase
            db.rawQuery(
                "SELECT payload_json FROM identity_snapshot WHERE identity_id='identity'", null
            ).use { c ->
                if (!c.moveToFirst()) return
                val root = JSONObject(c.getString(0))
                val sp = root.optString("system_prompt", "")
                if (sp.contains("[即时指令]")) return
                root.put("system_prompt", sp + KNOWLEDGE)
                db.execSQL(
                    "UPDATE identity_snapshot SET payload_json=? WHERE identity_id='identity'",
                    arrayOf(root.toString())
                )
            }
        }
    }

    // ── 持久化 ──

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun loadCmds(context: Context): List<Cmd> = runCatching {
        val arr = JSONArray(prefs(context).getString(KEY_CMDS, "[]"))
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Cmd(
                id = o.getString("id"),
                title = o.getString("title"),
                issuedMs = o.getLong("issuedMs"),
                presetSec = o.optInt("presetSec", 0),
                state = o.getString("state"),
                doneMs = o.optLong("doneMs", 0L)
            )
        }
    }.getOrDefault(emptyList())

    private fun save(context: Context, cmds: List<Cmd>) {
        val arr = JSONArray()
        cmds.sortedByDescending { it.issuedMs }.take(40).forEach { c ->
            arr.put(
                JSONObject()
                    .put("id", c.id)
                    .put("title", c.title)
                    .put("issuedMs", c.issuedMs)
                    .put("presetSec", c.presetSec)
                    .put("state", c.state)
                    .put("doneMs", c.doneMs)
            )
        }
        prefs(context).edit().putString(KEY_CMDS, arr.toString()).apply()
    }
}
