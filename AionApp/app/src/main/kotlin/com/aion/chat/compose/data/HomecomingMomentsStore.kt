package com.aion.chat.compose.data

import android.content.Context
import org.json.JSONArray
import java.io.File

/**
 * 朋友圈本地数据层（阶段四）：独立存本地，不进「我们」的日历。
 * - moments_local：动态（author: user=Yuri / sean=Sean；attachments=JSON 数组存图片绝对路径）
 * - moment_like_local：点赞（一人一条，重复点=取消）
 * - moment_comment_local：评论链（Sean 的回复由惰性 AI 生成，见 MomentsScreen 接线）
 */
object HomecomingMomentsStore {

    private fun db(context: Context) =
        com.aion.chat.homecoming.HomecomingDatabase(context).writableDatabase

    private fun ensure(db: android.database.sqlite.SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS moments_local(" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "author TEXT NOT NULL, content TEXT NOT NULL DEFAULT '', " +
                "attachments TEXT NOT NULL DEFAULT '[]', " +
                "created_at INTEGER NOT NULL)"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS moment_like_local(" +
                "moment_id INTEGER NOT NULL, author TEXT NOT NULL, " +
                "created_at INTEGER NOT NULL, " +
                "UNIQUE(moment_id, author))"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS moment_comment_local(" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "moment_id INTEGER NOT NULL, author TEXT NOT NULL, " +
                "content TEXT NOT NULL, created_at INTEGER NOT NULL)"
        )
    }

    data class Moment(
        val id: Long,
        val author: String,
        val content: String,
        val attachments: List<String>,
        val createdAt: Long,
        val likes: List<String>,          // author 列表
        val comments: List<Comment>,
        val remoteId: String? = null,     // Supabase 行 id（本地独有动态为 null）
        val localRowId: Long? = null      // 配对的本地行（远端动态的赞/评/配图挂在这行上）
    )

    data class Comment(val id: Long, val author: String, val content: String, val createdAt: Long)

    private fun attachmentsOf(raw: String): List<String> = try {
        val arr = JSONArray(raw)
        (0 until arr.length()).map { arr.getString(it) }
    } catch (e: Exception) { emptyList() }

    fun feed(context: Context): List<Moment> = try {
        val db = db(context); ensure(db)
        val moments = mutableListOf<Moment>()
        val cur = db.rawQuery(
            "SELECT id, author, content, attachments, created_at FROM moments_local " +
                "ORDER BY created_at DESC LIMIT 100", null
        )
        while (cur.moveToNext()) {
            val id = cur.getLong(0)
            val likes = mutableListOf<String>()
            val likeCur = db.rawQuery(
                "SELECT author FROM moment_like_local WHERE moment_id = ? ORDER BY created_at ASC",
                arrayOf(id.toString())
            )
            while (likeCur.moveToNext()) likes.add(likeCur.getString(0))
            likeCur.close()
            val comments = mutableListOf<Comment>()
            val comCur = db.rawQuery(
                "SELECT id, author, content, created_at FROM moment_comment_local " +
                    "WHERE moment_id = ? ORDER BY created_at ASC",
                arrayOf(id.toString())
            )
            while (comCur.moveToNext()) {
                comments.add(
                    Comment(comCur.getLong(0), comCur.getString(1), comCur.getString(2), comCur.getLong(3))
                )
            }
            comCur.close()
            moments.add(
                Moment(
                    id = id, author = cur.getString(1), content = cur.getString(2),
                    attachments = attachmentsOf(cur.getString(3)), createdAt = cur.getLong(4),
                    likes = likes, comments = comments
                )
            )
        }
        cur.close(); db.close()
        moments
    } catch (e: Exception) { emptyList() }

    fun addMoment(context: Context, author: String, content: String, attachments: List<String>): Long = try {
        val db = db(context); ensure(db)
        db.execSQL(
            "INSERT INTO moments_local(author, content, attachments, created_at) VALUES(?,?,?,?)",
            arrayOf(author, content, JSONArray(attachments).toString(), System.currentTimeMillis())
        )
        val cur = db.rawQuery("SELECT last_insert_rowid()", null)
        val id = if (cur.moveToFirst()) cur.getLong(0) else -1L
        cur.close()
        db.close()
        id
    } catch (e: Exception) { -1L }

    fun deleteMoment(context: Context, id: Long): Boolean = try {
        val db = db(context); ensure(db)
        db.execSQL("DELETE FROM moments_local WHERE id = ?", arrayOf(id))
        db.execSQL("DELETE FROM moment_like_local WHERE moment_id = ?", arrayOf(id))
        db.execSQL("DELETE FROM moment_comment_local WHERE moment_id = ?", arrayOf(id))
        db.close(); true
    } catch (e: Exception) { false }

    /** 点赞/取消（author 一人一条）。返回 true=现在已赞。 */
    fun toggleLike(context: Context, momentId: Long, author: String): Boolean = try {
        val db = db(context); ensure(db)
        val liked = db.rawQuery(
            "SELECT id FROM moment_like_local WHERE moment_id = ? AND author = ?",
            arrayOf(momentId.toString(), author)
        ).use { it.moveToFirst() }
        if (liked) {
            db.execSQL(
                "DELETE FROM moment_like_local WHERE moment_id = ? AND author = ?",
                arrayOf(momentId.toString(), author)
            )
            db.close(); false
        } else {
            db.execSQL(
                "INSERT INTO moment_like_local(moment_id, author, created_at) VALUES(?,?,?)",
                arrayOf(momentId, author, System.currentTimeMillis())
            )
            db.close(); true
        }
    } catch (e: Exception) { false }

    fun addComment(context: Context, momentId: Long, author: String, content: String): Boolean = try {
        val db = db(context); ensure(db)
        db.execSQL(
            "INSERT INTO moment_comment_local(moment_id, author, content, created_at) VALUES(?,?,?,?)",
            arrayOf(momentId, author, content, System.currentTimeMillis())
        )
        db.close(); true
    } catch (e: Exception) { false }

    fun deleteComment(context: Context, commentId: Long): Boolean = try {
        val db = db(context); ensure(db)
        db.execSQL("DELETE FROM moment_comment_local WHERE id = ?", arrayOf(commentId))
        db.close(); true
    } catch (e: Exception) { false }

    // ── 动态配图：存 filesDir/moments/<uuid>.jpg ──

    fun momentsDir(context: Context): File =
        File(context.filesDir, "moments").apply { mkdirs() }

    fun saveMomentImage(context: Context, bytes: ByteArray): String {
        val f = File(momentsDir(context), "m_" + System.currentTimeMillis() + "_" +
            (0..999).random() + ".jpg")
        f.writeBytes(bytes)
        return f.absolutePath
    }
}
