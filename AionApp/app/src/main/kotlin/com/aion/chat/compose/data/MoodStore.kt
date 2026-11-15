package com.aion.chat.compose.data

import android.content.Context
import androidx.compose.runtime.mutableIntStateOf
import org.json.JSONObject
import java.io.File

/**
 * 情绪日历（v0.3.160，她出的框架四）：
 * 每天点一下心情（0=开心😊 1=一般😐 2=难过😢 3=累😴），不写字。
 * - files/mood_calendar.json：{"2026-11-15": {"mood": 0, "ts": ...}}
 * - 月视图看整月分布；Sean 通过 mood_history 看最近的心情（连着几天不开心他要知道）
 */
object MoodStore {
    private const val FILE = "mood_calendar.json"

    val rev = mutableIntStateOf(0)

    val EMOJIS = listOf("😊", "😐", "😢", "😴")
    val LABELS = listOf("开心", "一般", "难过", "累")

    data class MoodDay(val date: String, val mood: Int, val ts: Long)

    private fun file(c: Context) = File(c.filesDir, FILE)

    private fun parse(c: Context): MutableMap<String, MoodDay> = runCatching {
        val o = JSONObject(file(c).takeIf { it.isFile }?.readText() ?: "{}")
        val out = mutableMapOf<String, MoodDay>()
        o.keys().forEach { k ->
            val v = o.optJSONObject(k) ?: return@forEach
            out[k] = MoodDay(k, v.optInt("mood"), v.optLong("ts"))
        }
        out
    }.getOrDefault(mutableMapOf())

    private fun persist(c: Context, m: Map<String, MoodDay>) {
        runCatching {
            val o = JSONObject()
            m.forEach { (k, v) -> o.put(k, JSONObject().put("mood", v.mood).put("ts", v.ts)) }
            file(c).writeText(o.toString())
        }
        rev.intValue++
    }

    fun get(c: Context, date: String): Int? = parse(c)[date]?.mood

    /** 点一下就写（同一天再点=覆盖） */
    fun set(c: Context, date: String, mood: Int) {
        if (mood !in 0..3) return
        val m = parse(c)
        m[date] = MoodDay(date, mood, System.currentTimeMillis())
        persist(c, m)
    }

    /** 最近 N 天（含今天），按日期升序；没记录的天 mood = null */
    fun recent(c: Context, days: Int = 14): List<MoodDay?> {
        val m = parse(c)
        val fmt = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
        val out = mutableListOf<MoodDay?>()
        val cal = java.util.Calendar.getInstance()
        for (i in days - 1 downTo 0) {
            cal.timeInMillis = System.currentTimeMillis()
            cal.add(java.util.Calendar.DAY_OF_MONTH, -i)
            val key = fmt.format(cal.time)
            out.add(m[key])
        }
        return out
    }
}
