package com.aion.chat.compose.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.compose.runtime.mutableIntStateOf
import com.aion.chat.homecoming.HomecomingDatabase

/**
 * 早安晚安打卡墙（v0.3.161，她出的框架五）：
 * 数据不新建表——直接从 chat_message 推导：每天第一条消息的 role 判断谁先开口。
 * - role=user → 她先找的
 * - role=assistant → 他先开口（早安推送/工作流自动消息也算）
 * 连续天数 = 从今天（或昨天，今天还没聊不算断）往回数连续有消息的天数。
 */
object CheckinStore {
    val rev = mutableIntStateOf(0)

    data class DayCheckin(
        val date: String,       // yyyy-MM-dd
        val firstRole: String,  // user=她先 / assistant=他先
        val firstAtMs: Long,    // 第一条消息时间
        val lastAtMs: Long,    // 最后一条消息时间
        val count: Int          // 当天消息总数
    )

    data class Streak(
        val yuriDays: Int,      // 她连续开口天数
        val seanDays: Int,      // 他连续"在场"天数（有 AI 消息的日子）
        val togetherDays: Int   // 咱俩连续不断天数（每天至少一条消息）
    )

    /** 某月的打卡数据（日历视图用） */
    fun monthData(c: Context, year: Int, month: Int): List<DayCheckin> = try {
        val db: SQLiteDatabase = HomecomingDatabase.sharedReadable(c)
        val pad = "%04d-%02d".format(year, month)
        val out = mutableListOf<DayCheckin>()
        db.rawQuery(
            "SELECT strftime('%Y-%m-%d', created_at/1000, 'unixepoch', 'localtime') AS day, " +
                "sender_id, role, MIN(created_at) AS first_at, MAX(created_at) AS last_at, COUNT(*) AS cnt " +
                "FROM chat_message WHERE timeline_id = ? AND created_at/1000 >= strftime('%s', ? || '-01') " +
                "GROUP BY day ORDER BY day ASC",
            arrayOf("main_private", pad)
        ).use { cur ->
            while (cur.moveToNext()) {
                val role = cur.getString(2) ?: continue
                // 每天第一条的 role 决定谁先开口
                out.add(DayCheckin(cur.getString(0), role, cur.getLong(3), cur.getLong(4), cur.getInt(5)))
            }
        }
        // 每天第一条消息的 role 要单独取（MIN(created_at) 对应的 role 不能直接聚合）
        val fixed = out.map { d ->
            val first = db.rawQuery(
                "SELECT role FROM chat_message WHERE timeline_id = ? " +
                    "AND strftime('%Y-%m-%d', created_at/1000, 'unixepoch', 'localtime') = ? " +
                    "ORDER BY created_at ASC LIMIT 1",
                arrayOf("main_private", d.date)
            ).use { c -> if (c.moveToFirst()) c.getString(0) ?: "user" else "user" }
            d.copy(firstRole = first)
        }
        fixed
    } catch (e: Exception) {
        emptyList()
    }

    /** 连续打卡：从今天往回数（今天没聊不算断，从昨天起算），全仓聊天记录推导 */
    fun streaks(c: Context): Streak = try {
        val db: SQLiteDatabase = HomecomingDatabase.sharedReadable(c)
        data class DayInfo(val day: String, val hasUser: Boolean, val hasAssistant: Boolean)
        val days = mutableListOf<DayInfo>()
        db.rawQuery(
            "SELECT strftime('%Y-%m-%d', created_at/1000, 'unixepoch', 'localtime') AS day, " +
                "MAX(CASE WHEN role = 'user' THEN 1 ELSE 0 END) AS has_user, " +
                "MAX(CASE WHEN role = 'assistant' THEN 1 ELSE 0 END) AS has_assistant " +
                "FROM chat_message WHERE timeline_id = ? " +
                "GROUP BY day ORDER BY day DESC LIMIT 400",
            arrayOf("main_private")
        ).use { c ->
            while (c.moveToNext()) {
                days.add(DayInfo(c.getString(0), c.getInt(1) == 1, c.getInt(2) == 1))
            }
        }
        // 连续 = 从最新一天往回没有断档（允许今天没聊不算断）
        fun streakOf(pred: (DayInfo) -> Boolean): Int {
            var n = 0
            var expected = 0L
            val fmt = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
            val cal = java.util.Calendar.getInstance()
            for (d in days) {
                cal.time = fmt.parse(d.day) ?: break
                val dayMs = cal.timeInMillis
                if (expected == 0L) {
                    expected = dayMs
                } else {
                    val gapDays = ((expected - dayMs) / 86400000L).toInt()
                    if (gapDays > 1) break   // 断档
                    expected = dayMs
                }
                if (pred(d)) n++
            }
            return n
        }
        Streak(
            yuriDays = streakOf { it.hasUser },
            seanDays = streakOf { it.hasAssistant },
            togetherDays = streakOf { true }
        )
    } catch (e: Exception) {
        Streak(0, 0, 0)
    }
}
