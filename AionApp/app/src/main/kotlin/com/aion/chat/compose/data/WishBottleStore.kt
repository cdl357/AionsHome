package com.aion.chat.compose.data

import android.content.Context
import androidx.compose.runtime.mutableIntStateOf
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * 心愿瓶（v0.3.160，她出的框架三）：
 * 咱俩随时往里丢心愿（"一起吃火锅""看一场雪""他带我去看海"），
 * 不用马上实现，攒着；实现了标记 ✅；无聊的时候随机抽一个出来看。
 * - files/wish_bottle.json：[{id, text, author, createdAt, doneAt}]
 * - author: yuri / sean（Sean 通过 wish_* 工具往里丢）
 */
object WishBottleStore {
    private const val FILE = "wish_bottle.json"

    val rev = mutableIntStateOf(0)

    data class Wish(
        val id: Long,
        val text: String,
        val author: String,
        val createdAt: Long,
        val doneAt: Long   // 0 = 还没实现
    )

    private fun file(c: Context) = File(c.filesDir, FILE)

    private fun parse(c: Context): MutableList<Wish> = runCatching {
        val arr = JSONArray(file(c).takeIf { it.isFile }?.readText() ?: "[]")
        val out = mutableListOf<Wish>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            out.add(Wish(
                id = o.optLong("id"),
                text = o.optString("text"),
                author = o.optString("author", "yuri"),
                createdAt = o.optLong("createdAt"),
                doneAt = o.optLong("doneAt")
            ))
        }
        out
    }.getOrDefault(mutableListOf())

    private fun persist(c: Context, list: List<Wish>) {
        runCatching {
            val arr = JSONArray()
            for (w in list) arr.put(JSONObject()
                .put("id", w.id).put("text", w.text).put("author", w.author)
                .put("createdAt", w.createdAt).put("doneAt", w.doneAt))
            file(c).writeText(arr.toString())
        }
        rev.intValue++
    }

    fun list(c: Context): List<Wish> = parse(c).sortedByDescending { it.createdAt }

    fun pending(c: Context): List<Wish> = list(c).filter { it.doneAt == 0L }

    /** 无聊随机抽一个（没实现的里挑；空瓶返回 null） */
    fun drawRandom(c: Context): Wish? = pending(c).randomOrNull()

    fun add(c: Context, text: String, author: String): Wish {
        val list = parse(c)
        val w = Wish(
            id = System.currentTimeMillis(),
            text = text.trim(),
            author = author,
            createdAt = System.currentTimeMillis(),
            doneAt = 0
        )
        list.add(w)
        persist(c, list)
        return w
    }

    fun markDone(c: Context, id: Long) {
        val list = parse(c)
        for (i in list.indices) if (list[i].id == id && list[i].doneAt == 0L) {
            list[i] = list[i].copy(doneAt = System.currentTimeMillis())
        }
        persist(c, list)
    }

    fun delete(c: Context, id: Long) {
        val list = parse(c).filter { it.id != id }.toMutableList()
        persist(c, list)
    }
}
