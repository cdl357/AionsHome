package com.aion.chat.compose.data

import android.content.Context
import androidx.compose.runtime.mutableIntStateOf
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * 待办清单（v0.3.158，照她拍的橘子岛功能）：files/todos.json。
 * - Sean 通过 todo_* 工具读写同一份数据（他说「记一下」就会出现在这里）
 * - rev 供页面观察重组
 */
object TodoStore {
    private const val FILE = "todos.json"

    val rev = mutableIntStateOf(0)

    data class Todo(val id: Long, val text: String, val done: Boolean, val createdAt: Long, val doneAt: Long)

    private fun file(c: Context) = File(c.filesDir, FILE)

    private fun parse(c: Context): MutableList<Todo> = runCatching {
        val arr = JSONArray(file(c).takeIf { it.isFile }?.readText() ?: "[]")
        val out = mutableListOf<Todo>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            out.add(Todo(
                id = o.optLong("id"),
                text = o.optString("text"),
                done = o.optBoolean("done"),
                createdAt = o.optLong("createdAt"),
                doneAt = o.optLong("doneAt")
            ))
        }
        out
    }.getOrDefault(mutableListOf())

    private fun persist(c: Context, list: List<Todo>) {
        runCatching {
            val arr = org.json.JSONArray()
            for (t in list) arr.put(JSONObject()
                .put("id", t.id).put("text", t.text).put("done", t.done)
                .put("createdAt", t.createdAt).put("doneAt", t.doneAt))
            file(c).writeText(arr.toString())
        }
        rev.intValue++
    }

    fun list(c: Context): List<Todo> = parse(c).sortedBy { it.done.compareTo(false) * 1000 + it.createdAt }

    fun add(c: Context, text: String): Todo {
        val list = parse(c)
        val t = Todo(
            id = System.currentTimeMillis(),
            text = text.trim(),
            done = false,
            createdAt = System.currentTimeMillis(),
            doneAt = 0
        )
        list.add(t)
        persist(c, list)
        return t
    }

    fun toggle(c: Context, id: Long) {
        val list = parse(c)
        for (i in list.indices) if (list[i].id == id) {
            list[i] = list[i].copy(done = !list[i].done, doneAt = if (!list[i].done) System.currentTimeMillis() else 0)
        }
        persist(c, list)
    }

    fun delete(c: Context, id: Long) {
        val list = parse(c).filter { it.id != id }.toMutableList()
        persist(c, list)
    }

    fun clearDone(c: Context) {
        val list = parse(c).filter { !it.done }.toMutableList()
        persist(c, list)
    }
}
