package com.aion.chat.compose.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * 相册本地数据层（图库改版）：照片墙 + 图册（相册集）+ 分类。
 * - album_photo_local：照片（filename 在 filesDir/album/ 下）+ 私心话 + 拍摄日期 + 所属图册 + 分类
 * - 图册注册表：prefs("album_gallery")，支持空图册（新建图库 0 张照片）
 */
object HomecomingAlbumStore {

    private fun db(context: Context) =
        com.aion.chat.homecoming.HomecomingDatabase.sharedWritable(context)

    private fun ensure(db: android.database.sqlite.SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS album_photo_local(" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "filename TEXT NOT NULL, " +
                "note TEXT NOT NULL DEFAULT '', " +
                "taken_on TEXT NOT NULL DEFAULT '', " +
                "created_at INTEGER NOT NULL)"
        )
        // 图库改版新增列（已存在则忽略）
        runCatching { db.execSQL("ALTER TABLE album_photo_local ADD COLUMN album TEXT NOT NULL DEFAULT ''") }
        runCatching { db.execSQL("ALTER TABLE album_photo_local ADD COLUMN kind TEXT NOT NULL DEFAULT '其他'") }
    }

    /** 分类（照橘瓣截图：头像/壁纸/截图/表情包/其他） */
    val KINDS = listOf("头像", "壁纸", "截图", "表情包", "其他")

    data class AlbumPhoto(
        val id: Long,
        val filename: String,
        val note: String,
        val takenOn: String,
        val createdAt: Long,
        val album: String = "",
        val kind: String = "其他"
    ) {
        fun file(context: Context): File = File(albumDir(context), filename)
    }

    data class Album(val name: String, val kind: String)

    fun albumDir(context: Context): File =
        File(context.filesDir, "album").apply { mkdirs() }

    fun list(context: Context): List<AlbumPhoto> = try {
        val db = db(context); ensure(db)
        val out = mutableListOf<AlbumPhoto>()
        val cur = db.rawQuery(
            "SELECT id, filename, note, taken_on, created_at, album, kind FROM album_photo_local " +
                "ORDER BY created_at DESC LIMIT 2000", null
        )
        while (cur.moveToNext()) {
            out.add(
                AlbumPhoto(
                    id = cur.getLong(0), filename = cur.getString(1),
                    note = cur.getString(2), takenOn = cur.getString(3),
                    createdAt = cur.getLong(4),
                    album = cur.getString(5), kind = cur.getString(6)
                )
            )
        }
        cur.close()
        out
    } catch (e: Exception) { emptyList() }

    fun addPhoto(context: Context, bytes: ByteArray, note: String, takenOn: String): Long =
        addPhoto(context, bytes, note, takenOn, "", "其他")

    fun addPhoto(context: Context, bytes: ByteArray, note: String, takenOn: String, album: String, kind: String): Long = try {
        val name = "a_" + System.currentTimeMillis() + "_" + (0..999).random() + ".jpg"
        File(albumDir(context), name).writeBytes(bytes)
        val db = db(context); ensure(db)
        db.execSQL(
            "INSERT INTO album_photo_local(filename, note, taken_on, created_at, album, kind) VALUES(?,?,?,?,?,?)",
            arrayOf(name, note, takenOn, System.currentTimeMillis(), album, kind)
        )
        val cur = db.rawQuery("SELECT last_insert_rowid()", null)
        val id = if (cur.moveToFirst()) cur.getLong(0) else -1L
        cur.close()
        id
    } catch (e: Exception) { -1L }

    fun update(context: Context, id: Long, note: String?, takenOn: String?): Boolean = try {
        val db = db(context); ensure(db)
        if (note != null) db.execSQL("UPDATE album_photo_local SET note = ? WHERE id = ?", arrayOf(note, id))
        if (takenOn != null) db.execSQL("UPDATE album_photo_local SET taken_on = ? WHERE id = ?", arrayOf(takenOn, id))
        true
    } catch (e: Exception) { false }

    fun remove(context: Context, id: Long): Boolean = try {
        val db = db(context); ensure(db)
        val cur = db.rawQuery("SELECT filename FROM album_photo_local WHERE id = ?", arrayOf(id.toString()))
        if (cur.moveToFirst()) {
            File(albumDir(context), cur.getString(0)).delete()
        }
        cur.close()
        db.execSQL("DELETE FROM album_photo_local WHERE id = ?", arrayOf(id))
        true
    } catch (e: Exception) { false }

    // ── 图册（相册集）注册表 ──

    private fun regPrefs(context: Context) =
        context.getSharedPreferences("album_gallery", Context.MODE_PRIVATE)

    fun albums(context: Context): List<Album> = runCatching {
        val arr = JSONArray(regPrefs(context).getString("albums", "[]"))
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Album(o.optString("name"), o.optString("kind", "其他"))
        }
    }.getOrDefault(emptyList())

    fun createAlbum(context: Context, name: String, kind: String): Boolean {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return false
        if (albums(context).any { it.name == trimmed }) return false
        saveAlbums(context, albums(context) + Album(trimmed, kind))
        return true
    }

    fun renameAlbum(context: Context, old: String, new: String): Boolean {
        val trimmed = new.trim()
        if (trimmed.isEmpty() || albums(context).any { it.name == trimmed }) return false
        val db = db(context); ensure(db)
        db.execSQL("UPDATE album_photo_local SET album = ? WHERE album = ?", arrayOf(trimmed, old))
        saveAlbums(context, albums(context).map { if (it.name == old) it.copy(name = trimmed) else it })
        return true
    }

    fun deleteAlbum(context: Context, name: String) {
        // 照片不删，只解除归属（保留在 全部图片）
        val db = db(context); ensure(db)
        db.execSQL("UPDATE album_photo_local SET album = '' WHERE album = ?", arrayOf(name))
        saveAlbums(context, albums(context).filter { it.name != name })
    }

    private fun saveAlbums(context: Context, list: List<Album>) {
        val arr = JSONArray()
        list.forEach { a -> arr.put(JSONObject().put("name", a.name).put("kind", a.kind)) }
        regPrefs(context).edit().putString("albums", arr.toString()).apply()
    }

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
