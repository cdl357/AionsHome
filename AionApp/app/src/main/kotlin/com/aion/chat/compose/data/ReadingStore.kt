package com.aion.chat.compose.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * 陪伴阅读数据层（定稿 §12 近期项）：
 * - 书：filesDir/reading/books/<id>.txt（Yuri 从手机里导入的 TXT）
 * - 书架元信息：reading/meta.json（书名/加入时间/读到哪段）
 * - Sean 的伴读评论：SQLite reading_comment_local（按段落内容 hash 挂，书不乱位）
 */
object ReadingStore {

    data class Book(
        val id: String,
        val title: String,
        val addedAt: Long,
        val lastPara: Int          // 上次读到的段落序号（-1 = 没读过）
    )

    data class Comment(
        val id: Long,
        val bookId: String,
        val paraKey: String,
        val author: String,
        val content: String,
        val createdAt: Long
    )

    // ── 目录 ──

    fun root(context: Context): File = File(context.filesDir, "reading").apply { mkdirs() }
    fun booksDir(context: Context): File = File(root(context), "books").apply { mkdirs() }
    private fun metaFile(context: Context): File = File(root(context), "meta.json")

    // ── 书架 ──

    fun listBooks(context: Context): List<Book> {
        val f = metaFile(context)
        if (!f.exists()) return emptyList()
        return try {
            val arr = JSONObject(f.readText()).optJSONArray("books") ?: return emptyList()
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Book(
                    id = o.optString("id"),
                    title = o.optString("title"),
                    addedAt = o.optLong("added_at", 0L),
                    lastPara = o.optInt("last_para", -1)
                )
            }
        } catch (e: Exception) { emptyList() }
    }

    private fun saveBooks(context: Context, books: List<Book>) {
        val arr = JSONArray()
        books.forEach {
            arr.put(
                JSONObject()
                    .put("id", it.id).put("title", it.title)
                    .put("added_at", it.addedAt).put("last_para", it.lastPara)
            )
        }
        metaFile(context).writeText(JSONObject().put("books", arr).toString(2))
    }

    /** 导入 TXT：返回书 id（失败返回 null）。 */
    fun importBook(context: Context, text: String, title: String): String? = try {
        val id = "bk_" + System.currentTimeMillis().toString(36)
        File(booksDir(context), id + ".txt").writeText(text)
        val books = listBooks(context).toMutableList()
        books.add(0, Book(id, title.ifBlank { "未命名" }, System.currentTimeMillis(), -1))
        saveBooks(context, books)
        id
    } catch (e: Exception) { null }

    fun readBook(context: Context, bookId: String): List<String> = try {
        val f = File(booksDir(context), bookId + ".txt")
        if (!f.exists()) emptyList()
        else f.readText().split('\n').map { it.trim() }.filter { it.isNotEmpty() }
    } catch (e: Exception) { emptyList() }

    fun saveProgress(context: Context, bookId: String, lastPara: Int) {
        try {
            val books = listBooks(context).map { if (it.id == bookId) it.copy(lastPara = lastPara) else it }
            saveBooks(context, books)
        } catch (e: Exception) { /* 进度丢了不打扰 */ }
    }

    fun deleteBook(context: Context, bookId: String): Boolean = try {
        File(booksDir(context), bookId + ".txt").delete()
        saveBooks(context, listBooks(context).filter { it.id != bookId })
        val db = com.aion.chat.homecoming.HomecomingDatabase(context).writableDatabase
        db.execSQL("DELETE FROM reading_comment_local WHERE book_id = ?", arrayOf(bookId))
        db.close()
        true
    } catch (e: Exception) { false }

    // ── Sean 的伴读评论 ──

    private fun ensure(db: android.database.sqlite.SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS reading_comment_local(" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "book_id TEXT NOT NULL, para_key TEXT NOT NULL, " +
                "author TEXT NOT NULL, content TEXT NOT NULL, created_at INTEGER NOT NULL)"
        )
    }

    fun comments(context: Context, bookId: String, paraKey: String): List<Comment> = try {
        val db = com.aion.chat.homecoming.HomecomingDatabase(context).writableDatabase
        ensure(db)
        val out = mutableListOf<Comment>()
        val cur = db.rawQuery(
            "SELECT id, author, content, created_at FROM reading_comment_local " +
                "WHERE book_id = ? AND para_key = ? ORDER BY created_at ASC",
            arrayOf(bookId, paraKey)
        )
        while (cur.moveToNext()) {
            out.add(Comment(cur.getLong(0), bookId, paraKey, cur.getString(1), cur.getString(2), cur.getLong(3)))
        }
        cur.close(); db.close()
        out
    } catch (e: Exception) { emptyList() }

    fun addComment(context: Context, bookId: String, paraKey: String, author: String, content: String): Boolean = try {
        val db = com.aion.chat.homecoming.HomecomingDatabase(context).writableDatabase
        ensure(db)
        db.execSQL(
            "INSERT INTO reading_comment_local(book_id, para_key, author, content, created_at) VALUES(?,?,?,?,?)",
            arrayOf(bookId, paraKey, author, content, System.currentTimeMillis())
        )
        db.close(); true
    } catch (e: Exception) { false }

    fun paraKey(text: String): String =
        Integer.toHexString(text.hashCode()) + "_" + text.length
}
