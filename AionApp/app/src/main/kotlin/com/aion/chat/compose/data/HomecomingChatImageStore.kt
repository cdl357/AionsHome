package com.aion.chat.compose.data

import android.content.Context

/**
 * 聊天图片的本地展示映射：引擎消息表只存 attachment_kind（不存图片字节），
 * 这张旁表把「消息 id → 本地图片文件」挂上，气泡就能渲染出真实图片/表情包。
 * 引擎层数据结构零改动；文件丢了就回落"图片已发送"占位。
 */
object HomecomingChatImageStore {

    private fun db(context: Context) =
        com.aion.chat.homecoming.HomecomingDatabase(context).writableDatabase

    private fun ensure(db: android.database.sqlite.SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS chat_image_local(" +
                "message_id TEXT PRIMARY KEY, " +
                "path TEXT NOT NULL, " +
                "created_at INTEGER NOT NULL)"
        )
    }

    fun map(context: Context, messageId: String, path: String): Boolean = try {
        val db = db(context); ensure(db)
        db.execSQL(
            "INSERT OR REPLACE INTO chat_image_local(message_id, path, created_at) VALUES(?,?,?)",
            arrayOf(messageId, path, System.currentTimeMillis())
        )
        db.close(); true
    } catch (e: Exception) { false }

    fun pathFor(context: Context, messageId: String): String? = try {
        val db = db(context); ensure(db)
        val cur = db.rawQuery(
            "SELECT path FROM chat_image_local WHERE message_id = ?",
            arrayOf(messageId)
        )
        val path = if (cur.moveToFirst()) cur.getString(0) else null
        cur.close(); db.close()
        path
    } catch (e: Exception) { null }
}
