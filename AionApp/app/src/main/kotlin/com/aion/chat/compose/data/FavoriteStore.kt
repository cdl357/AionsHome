package com.aion.chat.compose.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** 收藏（照橘瓣 FavoritePage）：把聊天里想说的话存起来，随时回看。 */
object FavoriteStore {

    data class Item(val id: String, val text: String, val createdAt: Long)

    private const val PREFS = "favorite_store"
    private const val KEY = "items"
    private const val MAX = 200

    @Volatile
    var rev: Int = 0
        private set

    private fun bump() { rev++ }

    fun list(context: Context): List<Item> = runCatching {
        val arr = JSONArray(prefs(context).getString(KEY, "[]"))
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Item(o.optString("id"), o.optString("text"), o.optLong("ts"))
        }
    }.getOrDefault(emptyList())

    fun add(context: Context, text: String) {
        val t = text.trim()
        if (t.isEmpty()) return
        val items = list(context).toMutableList()
        // 去重：同样内容只存最新一条
        items.removeAll { it.text == t }
        items.add(0, Item("fav_" + System.currentTimeMillis(), t.take(2000), System.currentTimeMillis()))
        save(context, items)
    }

    fun remove(context: Context, id: String) {
        save(context, list(context).filter { it.id != id })
    }

    fun clear(context: Context) {
        prefs(context).edit().remove(KEY).apply()
        bump()
    }

    private fun save(context: Context, items: List<Item>) {
        val arr = JSONArray()
        items.take(MAX).forEach { i ->
            arr.put(JSONObject().put("id", i.id).put("text", i.text).put("ts", i.createdAt))
        }
        prefs(context).edit().putString(KEY, arr.toString()).apply()
        bump()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
