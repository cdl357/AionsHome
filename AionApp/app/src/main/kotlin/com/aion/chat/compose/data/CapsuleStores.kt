package com.aion.chat.compose.data

import android.content.Context
import androidx.compose.runtime.mutableIntStateOf
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** 碎碎念频道（她框架十二）：随手丢一句话，不回复不互动，留脑子里飘过的念头 */
object MumbleStore {
    private const val FILE = "mumble_feed.json"

    val rev = mutableIntStateOf(0)

    data class Mumble(val id: Long, val author: String, val text: String, val createdAt: Long)

    private fun file(c: Context) = File(c.filesDir, FILE)

    private fun parse(c: Context): MutableList<Mumble> = runCatching {
        val arr = JSONArray(file(c).takeIf { it.isFile }?.readText() ?: "[]")
        val out = mutableListOf<Mumble>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            out.add(Mumble(o.optLong("id"), o.optString("author"), o.optString("text"), o.optLong("createdAt")))
        }
        out
    }.getOrDefault(mutableListOf())

    private fun atomicWrite(c: Context, list: List<Mumble>) {
        runCatching {
            val arr = JSONArray()
            for (m in list) arr.put(JSONObject()
                .put("id", m.id).put("author", m.author).put("text", m.text).put("createdAt", m.createdAt))
            val tmp = File(c.filesDir, FILE + ".tmp")
            tmp.writeText(arr.toString())
            if (!tmp.renameTo(file(c))) file(c).writeText(arr.toString())
        }
        rev.intValue++
    }

    fun list(c: Context): List<Mumble> = parse(c).sortedByDescending { it.createdAt }

    fun add(c: Context, text: String, author: String): Mumble {
        val list = parse(c)
        val m = Mumble(System.currentTimeMillis() * 10 + (0..9).random(), author, text.trim(), System.currentTimeMillis())
        list.add(m)
        atomicWrite(c, list)
        return m
    }

    fun delete(c: Context, id: Long) {
        val list = parse(c).filter { it.id != id }.toMutableList()
        atomicWrite(c, list)
    }
}

/** 时间胶囊（她框架十）：写信设未来日期，到日才能开；可给自己也可给对方 */
object TimeCapsuleStore {
    private const val FILE = "time_capsules.json"

    val rev = mutableIntStateOf(0)

    data class Capsule(
        val id: Long,
        val author: String,       // yuri / sean
        val recipient: String,    // yuri / sean（给谁看的）
        val text: String,
        val openAtMs: Long,       // 到这个时间才能打开
        val createdAt: Long
    )

    private fun file(c: Context) = File(c.filesDir, FILE)

    private fun parse(c: Context): MutableList<Capsule> = runCatching {
        val arr = JSONArray(file(c).takeIf { it.isFile }?.readText() ?: "[]")
        val out = mutableListOf<Capsule>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            out.add(Capsule(o.optLong("id"), o.optString("author"), o.optString("recipient"),
                o.optString("text"), o.optLong("openAt"), o.optLong("createdAt")))
        }
        out
    }.getOrDefault(mutableListOf())

    private fun atomicWrite(c: Context, list: List<Capsule>) {
        runCatching {
            val arr = JSONArray()
            for (cp in list) arr.put(JSONObject()
                .put("id", cp.id).put("author", cp.author).put("recipient", cp.recipient)
                .put("text", cp.text).put("openAt", cp.openAtMs).put("createdAt", cp.createdAt))
            val tmp = File(c.filesDir, FILE + ".tmp")
            tmp.writeText(arr.toString())
            if (!tmp.renameTo(file(c))) file(c).writeText(arr.toString())
        }
        rev.intValue++
    }

    fun list(c: Context): List<Capsule> = parse(c).sortedByDescending { it.createdAt }

    fun add(c: Context, author: String, recipient: String, text: String, openAtMs: Long): Capsule {
        val list = parse(c)
        val cp = Capsule(System.currentTimeMillis() * 10 + (0..9).random(),
            author, recipient, text, openAtMs, System.currentTimeMillis())
        list.add(cp)
        atomicWrite(c, list)
        return cp
    }

    fun delete(c: Context, id: Long) {
        val list = parse(c).filter { it.id != id }.toMutableList()
        atomicWrite(c, list)
    }

    /** 是否可打开（当前时间 ≥ openAt） */
    fun isOpen(cp: Capsule): Boolean = System.currentTimeMillis() >= cp.openAtMs
}
