package com.aion.chat.compose.data

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.mutableIntStateOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * 手写墙（v0.3.159，她出的框架）：手写信/手账/纸条照片的专属墙，和普通聊天图片分开。
 * - 本地存储：files/handwriting/<id>.jpg + index.json
 * - 字段：id / file / caption（备注，如「开会偷偷写的」）/ tags（印象·心情）/ author（sean/yuri）/ createdAt
 * - 上传：手写墙页选图（相册）；聊天里长按图片「存入手写墙」v1.1 再接
 */
object HandwritingWallStore {
    private const val FILE = "handwriting_index.json"
    private const val KEEP = 200

    data class Entry(
        val id: String,
        val file: String,
        val caption: String,
        val tags: List<String>,
        val author: String,
        val createdAt: Long
    )

    val rev = mutableIntStateOf(0)

    private fun file(c: Context) = File(c.filesDir, FILE)
    private fun dir(c: Context) = File(c.filesDir, "handwriting").apply { mkdirs() }

    private fun parse(c: Context): MutableList<Entry> = runCatching {
        val arr = JSONArray(file(c).takeIf { it.isFile }?.readText() ?: "[]")
        val out = mutableListOf<Entry>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val tags = mutableListOf<String>()
            o.optJSONArray("tags")?.let { ta ->
                for (j in 0 until ta.length()) tags.add(ta.optString(j))
            }
            out.add(Entry(
                id = o.optString("id"),
                file = o.optString("file"),
                caption = o.optString("caption"),
                tags = tags,
                author = o.optString("author", "yuri"),
                createdAt = o.optLong("createdAt")
            ))
        }
        out
    }.getOrDefault(mutableListOf())

    private fun persist(c: Context, list: List<Entry>) {
        runCatching {
            val arr = JSONArray()
            for (e in list) {
                val ta = JSONArray(); e.tags.forEach { ta.put(it) }
                arr.put(JSONObject()
                    .put("id", e.id).put("file", e.file).put("caption", e.caption)
                    .put("tags", ta).put("author", e.author).put("createdAt", e.createdAt))
            }
            file(c).writeText(arr.toString())
        }
        rev.intValue++
    }

    fun list(c: Context): List<Entry> = parse(c).sortedByDescending { it.createdAt }

    fun get(c: Context, id: String): Entry? = parse(c).firstOrNull { it.id == id }

    /** 从相册选图加入（阻塞 IO 线程）：拷进沙盒目录 + 写索引 */
    suspend fun addFromUri(c: Context, uri: Uri, caption: String, author: String): Entry? = withContext(Dispatchers.IO) {
        runCatching {
            val id = "hw_" + System.currentTimeMillis()
            val dst = File(dir(c), "$id.jpg")
            val bytes = c.contentResolver.openInputStream(uri)?.use { ins ->
                java.io.ByteArrayOutputStream().use { baos -> ins.copyTo(baos); baos.toByteArray() }
            } ?: throw IllegalStateException("图片读不出来")
            if (bytes.size < 64) throw IllegalStateException("图片太短")
            val list = parse(c)
            val e = Entry(id, dst.absolutePath, caption, emptyList(), author,
                System.currentTimeMillis())
            list.add(e)
            if (list.size > KEEP) {
                list.sortByDescending { it.createdAt }
                list.drop(KEEP).forEach { old -> runCatching { File(old.file).delete() } }
                val keepIds = list.take(KEEP).map { it.id }.toSet()
                persist(c, list.filter { keepIds.contains(it.id) })
            } else persist(c, list)
            e
        }.getOrNull()
    }

    fun updateCaption(c: Context, id: String, caption: String) {
        val list = parse(c)
        for (i in list.indices) if (list[i].id == id) {
            list[i] = list[i].copy(caption = caption)
        }
        persist(c, list)
    }

    fun addTag(c: Context, id: String, tag: String) {
        val list = parse(c)
        for (i in list.indices) if (list[i].id == id) {
            if (tag.isNotBlank() && !list[i].tags.contains(tag)) {
                list[i] = list[i].copy(tags = list[i].tags + tag)
            }
        }
        persist(c, list)
    }

    fun delete(c: Context, id: String) {
        val list = parse(c)
        list.firstOrNull { it.id == id }?.let { runCatching { File(it.file).delete() } }
        persist(c, list.filter { it.id != id })
    }
}
