package com.aion.chat.compose.data

import android.content.Context
import org.json.JSONArray
import java.io.File

/**
 * 相册本地数据层（定稿 §三·补充2 · 方案 C：照片墙 + 故事）。
 * - album_photo_local：照片（filename 在 filesDir/album/ 下）+「Sean 存这张时在想什么」+ 拍摄日期
 * - 私心话：存照片时可写，事后能补能改；线路就绪时可让 Sean 现场写一句
 */
object HomecomingAlbumStore {

    private fun db(context: Context) =
        com.aion.chat.homecoming.HomecomingDatabase(context).writableDatabase

    private fun ensure(db: android.database.sqlite.SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS album_photo_local(" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "filename TEXT NOT NULL, " +
                "note TEXT NOT NULL DEFAULT '', " +
                "taken_on TEXT NOT NULL DEFAULT '', " +
                "created_at INTEGER NOT NULL)"
        )
    }

    data class AlbumPhoto(
        val id: Long,
        val filename: String,
        val note: String,
        val takenOn: String,
        val createdAt: Long
    ) {
        fun file(context: Context): File = File(albumDir(context), filename)
    }

    fun albumDir(context: Context): File =
        File(context.filesDir, "album").apply { mkdirs() }

    fun list(context: Context): List<AlbumPhoto> = try {
        val db = db(context); ensure(db)
        val out = mutableListOf<AlbumPhoto>()
        val cur = db.rawQuery(
            "SELECT id, filename, note, taken_on, created_at FROM album_photo_local " +
                "ORDER BY created_at DESC LIMIT 500", null
        )
        while (cur.moveToNext()) {
            out.add(
                AlbumPhoto(
                    id = cur.getLong(0), filename = cur.getString(1),
                    note = cur.getString(2), takenOn = cur.getString(3),
                    createdAt = cur.getLong(4)
                )
            )
        }
        cur.close(); db.close()
        out
    } catch (e: Exception) { emptyList() }

    fun addPhoto(context: Context, bytes: ByteArray, note: String, takenOn: String): Long = try {
        val name = "a_" + System.currentTimeMillis() + "_" + (0..999).random() + ".jpg"
        File(albumDir(context), name).writeBytes(bytes)
        val db = db(context); ensure(db)
        db.execSQL(
            "INSERT INTO album_photo_local(filename, note, taken_on, created_at) VALUES(?,?,?,?)",
            arrayOf(name, note, takenOn, System.currentTimeMillis())
        )
        val cur = db.rawQuery("SELECT last_insert_rowid()", null)
        val id = if (cur.moveToFirst()) cur.getLong(0) else -1L
        cur.close(); db.close()
        id
    } catch (e: Exception) { -1L }

    fun update(context: Context, id: Long, note: String?, takenOn: String?): Boolean = try {
        val db = db(context); ensure(db)
        if (note != null) db.execSQL("UPDATE album_photo_local SET note = ? WHERE id = ?", arrayOf(note, id))
        if (takenOn != null) db.execSQL("UPDATE album_photo_local SET taken_on = ? WHERE id = ?", arrayOf(takenOn, id))
        db.close(); true
    } catch (e: Exception) { false }

    fun remove(context: Context, id: Long): Boolean = try {
        val db = db(context); ensure(db)
        val cur = db.rawQuery("SELECT filename FROM album_photo_local WHERE id = ?", arrayOf(id.toString()))
        if (cur.moveToFirst()) {
            File(albumDir(context), cur.getString(0)).delete()
        }
        cur.close()
        db.execSQL("DELETE FROM album_photo_local WHERE id = ?", arrayOf(id))
        db.close(); true
    } catch (e: Exception) { false }

    /** 备注里图片占位（photo wall 摘要用）：私心话为空显示这个。 */
    fun noteOrPlaceholder(note: String): String =
        note.ifBlank { "他还没写下这张的心里话" }

    /** 兼容导出（暂不接云端，留 API）。 */
    fun exportJson(context: Context): String {
        val arr = JSONArray()
        list(context).forEach {
            arr.put(
                org.json.JSONObject()
                    .put("note", it.note).put("taken_on", it.takenOn)
                    .put("created_at", it.createdAt)
            )
        }
        return arr.toString()
    }
}
