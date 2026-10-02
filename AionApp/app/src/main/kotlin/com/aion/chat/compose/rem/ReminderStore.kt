package com.aion.chat.compose.rem

import android.content.Context
import com.aion.chat.homecoming.HomecomingDatabase

/** 提醒本地存储（homecoming.db 运行时表，不动版本号）。 */
object ReminderStore {

    data class Reminder(val id: Long, val triggerAt: Long, val content: String)

    private fun db(context: Context) =
        com.aion.chat.homecoming.HomecomingDatabase(context).writableDatabase

    private fun ensure(db: android.database.sqlite.SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS reminder_local(" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "trigger_at INTEGER NOT NULL, content TEXT NOT NULL, " +
                "created_at INTEGER NOT NULL, status TEXT NOT NULL DEFAULT 'active')"
        )
    }

    fun list(context: Context): List<Reminder> = try {
        val db = db(context); ensure(db)
        val cur = db.rawQuery(
            "SELECT id, trigger_at, content FROM reminder_local " +
                "WHERE status = 'active' ORDER BY trigger_at ASC", null
        )
        val list = mutableListOf<Reminder>()
        while (cur.moveToNext()) {
            list.add(Reminder(cur.getLong(0), cur.getLong(1), cur.getString(2)))
        }
        cur.close(); db.close()
        list
    } catch (e: Exception) { emptyList() }

    fun add(context: Context, triggerAt: Long, content: String): Long = try {
        val db = db(context); ensure(db)
        db.execSQL(
            "INSERT INTO reminder_local(trigger_at, content, created_at, status) VALUES(?,?,?,'active')",
            arrayOf(triggerAt, content, System.currentTimeMillis())
        )
        val cur = db.rawQuery("SELECT last_insert_rowid()", null)
        val id = if (cur.moveToFirst()) cur.getLong(0) else -1L
        cur.close(); db.close()
        id
    } catch (e: Exception) { -1L }

    fun remove(context: Context, id: Long): Boolean = try {
        val db = db(context); ensure(db)
        db.execSQL(
            "UPDATE reminder_local SET status = 'deleted' WHERE id = ?",
            arrayOf(id)
        )
        db.close(); true
    } catch (e: Exception) { false }
}
