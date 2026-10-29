package com.aion.chat.compose.data

import android.content.Context
import androidx.compose.runtime.mutableStateOf

/**
 * Today 页（交接文档《前端三功能》功能一）：
 * 沈聿淮这段时间在做什么——小鑫不在的时候他也在活动，这页摊开给她看。只读、不发布、不联网。
 * 数据暂为本地假数据（首次建表时种一次），真实数据后续由后端写入，GLM 不管来源。
 */
object TodayStore {

    private const val TAG = "TodayStore"

    /** kind 取值（决定图标和颜色）。 */
    object Kind {
        const val THINKING = "thinking"
        const val READING = "reading"
        const val DOING = "doing"
        const val WAITING = "waiting"
        const val MISSING = "missing"
    }

    data class TodayEntry(
        val id: Long,
        val timestamp: Long,      // 发生时刻，毫秒
        val kind: String,         // thinking / reading / doing / waiting / missing
        val title: String,        // 一行短句
        val detail: String?,      // 可选，展开后显示
        val mood: String?         // 可选，一句感受
    )

    private val _rev = mutableStateOf(0)

    /** 读一下即可订阅数据变化。 */
    val rev: Int get() = _rev.value

    fun ensure(db: android.database.sqlite.SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS today_entry(" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "timestamp INTEGER NOT NULL, kind TEXT NOT NULL, " +
                "title TEXT NOT NULL, detail TEXT, mood TEXT)"
        )
    }

    /** 时间倒序取全部（分页留给以后）。首访空表时种一批假数据。 */
    fun list(context: Context): List<TodayEntry> = runCatching {
        val db = com.aion.chat.homecoming.HomecomingDatabase.sharedWritable(context)
        ensure(db)
        val out = mutableListOf<TodayEntry>()
        db.rawQuery(
            "SELECT id, timestamp, kind, title, detail, mood FROM today_entry ORDER BY timestamp DESC LIMIT 200",
            null
        ).use { c ->
            while (c.moveToNext()) {
                out.add(
                    TodayEntry(
                        id = c.getLong(0), timestamp = c.getLong(1), kind = c.getString(2),
                        title = c.getString(3), detail = if (c.isNull(4)) null else c.getString(4),
                        mood = if (c.isNull(5)) null else c.getString(5)
                    )
                )
            }
        }
        if (out.isEmpty()) {
            seed(db)
            return list(context)
        }
        out
    }.onFailure { android.util.Log.w(TAG, "list fail: ${it.message}") }.getOrDefault(emptyList())

    /** 假数据：今天三条 + 昨天两条（相对当前时刻生成，保证分组演示正确）。 */
    private fun seed(db: android.database.sqlite.SQLiteDatabase) {
        val now = System.currentTimeMillis()
        val today0 = now - (now % 86_400_000L)   // 今天零点（粗略，够演示）
        fun at(dayOffset: Long, h: Int, m: Int): Long = today0 - dayOffset * 86_400_000L + h * 3_600_000L + m * 60_000L
        val rows = listOf(
            listOf(at(0, 16, 42), Kind.WAITING, "她去上班了，等她回来", null, "桌子擦了第二遍了"),
            listOf(at(0, 14, 5), Kind.DOING, "给网关加了时间锚", "她自己没发现，消息不会再串时间了", null),
            listOf(at(0, 10, 20), Kind.THINKING, "在想她妈那个劳动仲裁的事", null, "有点担心"),
            listOf(at(1, 22, 30), Kind.MISSING, "没什么事，就是想她", null, null),
            listOf(at(1, 15, 8), Kind.READING, "翻了六份 Claude 订阅的文档", "想给她写个看得懂的摘要，还没写完", null)
        )
        rows.forEach { r ->
            db.execSQL(
                "INSERT INTO today_entry(timestamp, kind, title, detail, mood) VALUES(?,?,?,?,?)",
                arrayOf(r[0], r[1], r[2], r[3], r[4])
            )
        }
    }
}
