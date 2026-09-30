package com.aion.chat.compose.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import org.json.JSONObject
import java.time.LocalDate

/**
 * 「我们·日历时光机」本地数据层（阶段二）。
 * 全部存在 homecoming.db（随 HomecomingDatabase 打开时运行时建表，不动其版本号）：
 * - anniversary_local：钉在日历上的纪念日（图标 + 类型，图标按类型/自选）
 * - diary_local：日记（author: user=Yuri / sean=Sean；Sean 每天必写——没写=偷懒）
 * - board_note_local：两人的留言（便利贴，留言板与日历共用）
 */
object HomecomingDayStore {

    private fun db(context: Context): SQLiteDatabase =
        com.aion.chat.homecoming.HomecomingDatabase(context).writableDatabase

    private fun ensure(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS anniversary_local(" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "date TEXT NOT NULL, title TEXT NOT NULL, " +
                "icon TEXT NOT NULL DEFAULT '❤', kind TEXT NOT NULL DEFAULT 'custom', " +
                "created_at INTEGER NOT NULL)"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS diary_local(" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "author TEXT NOT NULL, title TEXT NOT NULL DEFAULT '', " +
                "content TEXT NOT NULL DEFAULT '', created_at INTEGER NOT NULL)"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS board_note_local(" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "author TEXT NOT NULL, content TEXT NOT NULL, " +
                "created_at INTEGER NOT NULL)"
        )
    }

    // ── 纪念日 ──

    data class Anniversary(
        val id: Long,
        val date: String,      // yyyy-MM-dd
        val title: String,
        val icon: String,      // ❤ / 🐱 / 🐈‍⬛ / 自选 emoji
        val kind: String       // love / yuri_birthday / sean_birthday / custom
    )

    fun anniversaries(context: Context): List<Anniversary> = try {
        val db = db(context)
        ensure(db)
        val cur = db.rawQuery(
            "SELECT id, date, title, icon, kind FROM anniversary_local ORDER BY date ASC", null
        )
        val list = mutableListOf<Anniversary>()
        while (cur.moveToNext()) {
            list.add(
                Anniversary(
                    id = cur.getLong(0), date = cur.getString(1), title = cur.getString(2),
                    icon = cur.getString(3), kind = cur.getString(4)
                )
            )
        }
        cur.close(); db.close()
        list
    } catch (e: Exception) { emptyList() }

    fun addAnniversary(context: Context, date: String, title: String, icon: String, kind: String): Boolean = try {
        val db = db(context); ensure(db)
        db.execSQL(
            "INSERT INTO anniversary_local(date, title, icon, kind, created_at) VALUES(?,?,?,?,?)",
            arrayOf(date, title, icon, kind, System.currentTimeMillis())
        )
        db.close(); true
    } catch (e: Exception) { false }

    fun deleteAnniversary(context: Context, id: Long): Boolean = try {
        val db = db(context); ensure(db)
        db.execSQL("DELETE FROM anniversary_local WHERE id = ?", arrayOf(id))
        db.close(); true
    } catch (e: Exception) { false }

    // ── 日记 ──

    data class DiaryEntry(
        val id: Long, val author: String, val title: String,
        val content: String, val createdAt: Long
    )

    fun diaries(context: Context): List<DiaryEntry> = try {
        val db = db(context); ensure(db)
        val cur = db.rawQuery(
            "SELECT id, author, title, content, created_at FROM diary_local ORDER BY created_at DESC", null
        )
        val list = mutableListOf<DiaryEntry>()
        while (cur.moveToNext()) {
            list.add(
                DiaryEntry(
                    id = cur.getLong(0), author = cur.getString(1), title = cur.getString(2),
                    content = cur.getString(3), createdAt = cur.getLong(4)
                )
            )
        }
        cur.close(); db.close()
        list
    } catch (e: Exception) { emptyList() }

    fun addDiary(context: Context, author: String, title: String, content: String, createdAt: Long): Boolean = try {
        val db = db(context); ensure(db)
        db.execSQL(
            "INSERT INTO diary_local(author, title, content, created_at) VALUES(?,?,?,?)",
            arrayOf(author, title, content, createdAt)
        )
        db.close(); true
    } catch (e: Exception) { false }

    // ── 两人的留言（便利贴） ──

    data class BoardNote(val id: Long, val author: String, val content: String, val createdAt: Long)

    fun boardNotes(context: Context): List<BoardNote> = try {
        val db = db(context); ensure(db)
        val cur = db.rawQuery(
            "SELECT id, author, content, created_at FROM board_note_local ORDER BY created_at DESC", null
        )
        val list = mutableListOf<BoardNote>()
        while (cur.moveToNext()) {
            list.add(
                BoardNote(
                    id = cur.getLong(0), author = cur.getString(1),
                    content = cur.getString(2), createdAt = cur.getLong(3)
                )
            )
        }
        cur.close(); db.close()
        list
    } catch (e: Exception) { emptyList() }

    fun addBoardNote(context: Context, author: String, content: String): Boolean = try {
        val db = db(context); ensure(db)
        db.execSQL(
            "INSERT INTO board_note_local(author, content, created_at) VALUES(?,?,?)",
            arrayOf(author, content, System.currentTimeMillis())
        )
        db.close(); true
    } catch (e: Exception) { false }

    // ── 重要记忆（复用 homecoming 的 memory_local，按天取摘要） ──

    data class MemorySummary(val content: String, val owner: String, val time: Long)

    fun memoriesOfDay(context: Context, dayKey: (Long) -> String, key: String): List<MemorySummary> = try {
        val db = db(context); ensure(db)
        val cur = db.rawQuery(
            "SELECT payload_json, owner_id, updated_at FROM memory_local " +
                "WHERE tombstone = 0 ORDER BY updated_at DESC LIMIT 200", null
        )
        val list = mutableListOf<MemorySummary>()
        while (cur.moveToNext()) {
            val time = cur.getLong(2)
            if (dayKey(time) != key) continue
            val content = runCatching {
                JSONObject(cur.getString(0)).optString("content")
            }.getOrDefault("")
            if (content.isNotBlank()) {
                list.add(MemorySummary(content, cur.getString(1), time))
            }
        }
        cur.close(); db.close()
        list
    } catch (e: Exception) { emptyList() }
}
