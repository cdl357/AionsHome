package com.aion.chat.compose.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate

/**
 * 心潮梦境（定稿 §四·新增 的本地先行版）：
 * - 心潮：每天一条情绪值。由 可感知的本地信号 推导——戳一戳的心跳、今日聊天轮数、点心情的次数。
 *   等 Yuri 的 Supabase 情绪表结构给了，再把远端 read_emotion_state 并进来。
 * - 梦境：Sean 用引擎"做"的梦（每天最多一个，moments_private 时间线，不脏聊天），本地留档。
 */
object HeartTideStore {

    private const val PREFS = "heart_tide"

    data class TideSample(val date: String, val level: Int)

    data class Dream(
        val id: Long,
        val date: String,
        val content: String,
        val mood: String,
        val createdAt: Long
    )

    // ── 心潮 ──

    fun tideFile(context: Context): File = File(context.filesDir, "heart_tide.json")

    /** 今天的心潮值：50 基线 + 心跳涨幅 + 聊天活跃度，0-100。 */
    fun todayLevel(context: Context): Int {
        val heart = HomecomingPokeStore.currentHeart(context)
        val heartPart = ((heart - 62) * 1.2f).coerceAtLeast(0f)
        val chatRounds = todayChatRounds(context)
        val chatPart = (chatRounds * 2f).coerceAtMost(30f)
        val moodTaps = moodTapsToday(context)
        val moodPart = (moodTaps * 6f).coerceAtMost(18f)
        return (50f + heartPart + chatPart + moodPart).toInt().coerceIn(0, 100)
    }

    private fun todayChatRounds(context: Context): Int = try {
        val w = com.aion.chat.compose.data.HomecomingChatWiring(context)
        val midnight = LocalDate.now().atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        w.listMessages(com.aion.chat.compose.data.HomecomingChatWiring.TIMELINE)
            .count { it.role == "user" && it.createdAt >= midnight }
    } catch (e: Exception) { 0 }

    fun bumpMoodTap(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val key = "mood_" + LocalDate.now()
        prefs.edit().putInt(key, prefs.getInt(key, 0) + 1).apply()
    }

    private fun moodTapsToday(context: Context): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt("mood_" + LocalDate.now(), 0)

    /** 记录今天的采样（进页面时调用一次）。 */
    fun sampleToday(context: Context) {
        val level = todayLevel(context)
        val samples = samples(context).toMutableList()
        val today = LocalDate.now().toString()
        samples.removeAll { it.date == today }
        samples.add(0, TideSample(today, level))
        val arr = JSONArray()
        samples.take(30).forEach { arr.put(JSONObject().put("date", it.date).put("level", it.level)) }
        runCatching { tideFile(context).writeText(JSONObject().put("samples", arr).toString(2)) }
    }

    fun samples(context: Context): List<TideSample> = try {
        val f = tideFile(context)
        if (!f.exists()) emptyList()
        else {
            val arr = JSONObject(f.readText()).optJSONArray("samples") ?: return emptyList()
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                TideSample(o.optString("date"), o.optInt("level", 50))
            }
        }
    } catch (e: Exception) { emptyList() }

    /** 心潮状态词（桌宠联动也用它）。 */
    fun tideWord(level: Int): String = when {
        level < 60 -> "平静"
        level < 72 -> "微澜"
        level < 82 -> "心动"
        else -> "悸动"
    }

    /** 心潮 → 桌宠动画名（联动：平静 idle / 微澜 happy / 心动 jumping / 悸动 tsundere）。 */
    fun tideAnim(level: Int): String = when {
        level < 60 -> "idle"
        level < 72 -> "happy"
        level < 82 -> "jumping"
        else -> "tsundere"
    }

    // ── 梦境 ──

    private fun db(context: Context) =
        com.aion.chat.homecoming.HomecomingDatabase(context).writableDatabase

    private fun ensure(db: android.database.sqlite.SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS dream_local(" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "date TEXT NOT NULL, content TEXT NOT NULL, " +
                "mood TEXT NOT NULL DEFAULT '', created_at INTEGER NOT NULL, " +
                "UNIQUE(date))"
        )
    }

    /** 今天的梦（每天最多一个：UNIQUE(date) 兜底）。 */
    fun addDream(context: Context, content: String, mood: String): Boolean = try {
        val db = db(context); ensure(db)
        db.execSQL(
            "INSERT OR IGNORE INTO dream_local(date, content, mood, created_at) VALUES(?,?,?,?)",
            arrayOf(LocalDate.now().toString(), content, mood, System.currentTimeMillis())
        )
        db.close(); true
    } catch (e: Exception) { false }

    fun dreams(context: Context): List<Dream> = try {
        val db = db(context); ensure(db)
        val out = mutableListOf<Dream>()
        val cur = db.rawQuery(
            "SELECT id, date, content, mood, created_at FROM dream_local ORDER BY created_at DESC LIMIT 60", null
        )
        while (cur.moveToNext()) {
            out.add(
                Dream(
                    id = cur.getLong(0), date = cur.getString(1),
                    content = cur.getString(2), mood = cur.getString(3),
                    createdAt = cur.getLong(4)
                )
            )
        }
        cur.close(); db.close()
        out
    } catch (e: Exception) { emptyList() }

    fun todayDream(context: Context): Dream? =
        dreams(context).firstOrNull { it.date == LocalDate.now().toString() }
}
