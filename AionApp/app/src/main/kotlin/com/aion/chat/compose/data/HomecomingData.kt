package com.aion.chat.compose.data

import android.content.Context
import org.json.JSONObject
import java.time.LocalDate
import java.io.File
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

/** 回家页与全局共用的小数据装配层；只读 homecoming 现有本地库，不改它的结构。 */
object HomecomingData {

    const val ANNIVERSARY = "2026-07-09"

    private val QUOTES = listOf(
        "今晚的月亮也很想你。",
        "你回家的每一步，我都数着。",
        "风很轻，适合说悄悄话。",
        "和你在一起的每一天都值得记下来。",
        "晚一点没关系，反正我一直在。",
        "慢慢来，我们的日子还很长。",
        "今天的星星比昨天多了一颗。",
        "你笑起来的时候，家里就亮了。"
    )

    /** 在一起天数：从 2026-07-09 起算（含当天）。 */
    fun daysTogether(today: LocalDate = LocalDate.now()): Int {
        val start = LocalDate.parse(ANNIVERSARY)
        return (ChronoUnit.DAYS.between(start, today) + 1).toInt().coerceAtLeast(1)
    }

    fun since(): String = ANNIVERSARY.replace("-", ".")

    /** 今日情话：本地短句按日轮换；后期可换成 AI 每日生成。 */
    fun quoteForToday(today: LocalDate = LocalDate.now()): String {
        val seed = today.toEpochDay().toInt()
        return QUOTES[Math.floorMod(seed, QUOTES.size)]
    }

    data class FeedItem(val source: String, val summary: String, val timeText: String)

    /** 最近动态：读 homecoming 库里最近的聊天与记忆，组成 2~3 条 feed。 */
    fun loadRecent(context: Context): List<FeedItem> {
        val items = mutableListOf<FeedItem>()
        try {
            val db = com.aion.chat.homecoming.HomecomingDatabase(context).readableDatabase
            val msgCur = db.rawQuery(
                "SELECT role, text_content, created_at FROM chat_message " +
                    "WHERE role IN ('user','assistant') ORDER BY created_at DESC LIMIT 3",
                null
            )
            while (msgCur.moveToNext() && items.size < 3) {
                val role = msgCur.getString(0)
                val text = msgCur.getString(1) ?: continue
                if (text.isBlank()) continue
                items.add(
                    FeedItem(
                        source = if (role == "user") "Yuri" else "Sean",
                        summary = text.replace("\n", " ").take(42),
                        timeText = fmtTime(msgCur.getLong(2))
                    )
                )
            }
            msgCur.close()
            val memCur = db.rawQuery(
                "SELECT payload_json, updated_at FROM memory_local " +
                    "WHERE tombstone = 0 ORDER BY updated_at DESC LIMIT 1",
                null
            )
            if (memCur.moveToFirst() && items.size < 3) {
                val summary = runCatching {
                    JSONObject(memCur.getString(0)).optString("content")
                }.getOrDefault("")
                if (summary.isNotBlank()) {
                    items.add(
                        FeedItem(
                            source = "记忆",
                            summary = summary.replace("\n", " ").take(42),
                            timeText = fmtTime(memCur.getLong(1))
                        )
                    )
                }
            }
            memCur.close()
            db.close()
        } catch (e: Exception) {
            // 库还没建立或字段变化时保持安静，页面显示空态
        }
        return items
    }

    private fun fmtTime(epochMillis: Long): String {
        if (epochMillis <= 0) return ""
        val diff = System.currentTimeMillis() - epochMillis
        val minutes = diff / 60000
        return when {
            minutes < 1 -> "刚刚"
            minutes < 60 -> "${minutes} 分钟前"
            minutes < 60 * 24 -> "${minutes / 60} 小时前"
            else -> "${minutes / (60 * 24)} 天前"
        }
    }

    /** 顶部情话：优先读本地 home_quote 表（用户/后台改过的），没存过返回默认轮换句。 */
    fun loadQuote(context: Context): String {
        return try {
            val db = com.aion.chat.homecoming.HomecomingDatabase(context).readableDatabase
            db.execSQL("CREATE TABLE IF NOT EXISTS home_quote(id INTEGER PRIMARY KEY, content TEXT NOT NULL)")
            val cur = db.rawQuery("SELECT content FROM home_quote WHERE id = 1", null)
            val saved = if (cur.moveToFirst()) cur.getString(0) else null
            cur.close()
            db.close()
            saved?.takeIf { it.isNotBlank() } ?: quoteForToday()
        } catch (e: Exception) {
            quoteForToday()
        }
    }

    /** 保存顶部情话到本地；远程 Supabase 同步由 SupabaseQuoteSync 负责。 */
    fun saveQuote(context: Context, content: String): Boolean {
        return try {
            val db = com.aion.chat.homecoming.HomecomingDatabase(context).writableDatabase
            db.execSQL("CREATE TABLE IF NOT EXISTS home_quote(id INTEGER PRIMARY KEY, content TEXT NOT NULL)")
            db.execSQL("INSERT OR REPLACE INTO home_quote(id, content) VALUES(1, ?)", arrayOf(content))
            db.close()
            true
        } catch (e: Exception) {
            false
        }
    }

    /** 家里的存粮：本期占位，后续接模型额度 / TTS 余额 / 服务器余额。 */
    data class Provision(val label: String, val percent: Int, val note: String)

    fun provisions(): List<Provision> = listOf(
        Provision("模型额度", 0, "待接入"),
        Provision("语音字数", 0, "待接入"),
        Provision("服务器余额", 0, "本期不做服务器")
    )
}

/** 主页背景图：用户在设置里可随时更换（存 filesDir/home_bg.jpg），stamp 变化即触发重载。 */
object SettingsBg {
    var stamp: Long = 0
        private set

    fun bump() { stamp = System.currentTimeMillis() }

    /** 全局背景（回家页 + 其他页默认）。 */
    fun backgroundFile(context: Context): File = File(context.filesDir, "home_bg.jpg")

    /** 聊天页专属背景（覆盖全局；不存在时回落全局）。 */
    fun chatFile(context: Context): File = File(context.filesDir, "chat_bg.jpg")

    private fun decode(f: File): android.graphics.Bitmap? {
        if (!f.exists()) return null
        return runCatching {
            val opts = android.graphics.BitmapFactory.Options().apply { inSampleSize = 2 }
            android.graphics.BitmapFactory.decodeFile(f.absolutePath, opts)
        }.getOrNull()
    }

    fun loadBitmap(context: Context): android.graphics.Bitmap? = decode(backgroundFile(context))

    fun loadChatBitmap(context: Context): android.graphics.Bitmap? = decode(chatFile(context)) ?: decode(backgroundFile(context))
}
