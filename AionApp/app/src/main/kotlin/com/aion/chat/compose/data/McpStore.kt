package com.aion.chat.compose.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * MCP 服务器配置（照橘瓣）：SSE / Streamable HTTP 两种协议。
 * 存 prefs("mcp_store").servers = JSON 数组。
 */
object McpStore {

    data class Server(
        val id: String,
        val name: String,
        val url: String,
        val type: String,      // "sse" | "http"
        val enabled: Boolean
    )

    private const val PREFS = "mcp_store"
    private const val KEY = "servers"

    @Volatile
    var rev: Int = 0
        private set

    private fun bump() { rev++ }

    fun list(context: Context): List<Server> = runCatching {
        val arr = JSONArray(prefs(context).getString(KEY, "[]"))
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Server(
                id = o.optString("id"),
                name = o.optString("name", "未命名"),
                url = o.optString("url"),
                type = if (o.optString("type") == "sse") "sse" else "http",
                enabled = o.optBoolean("enabled", true)
            )
        }
    }.getOrDefault(emptyList())

    fun upsert(context: Context, server: Server) {
        val others = list(context).filter { it.id != server.id }
        save(context, others + server)
    }

    fun delete(context: Context, id: String) {
        save(context, list(context).filter { it.id != id })
    }

    fun setEnabled(context: Context, id: String, enabled: Boolean) {
        save(context, list(context).map { if (it.id == id) it.copy(enabled = enabled) else it })
    }

    /** 粘贴 JSON 导入（橘瓣同款：支持单个对象或数组，字段 name/url/type） */
    fun import(context: Context, json: String): Int {
        val trimmed = json.trim()
        val arr = if (trimmed.startsWith("[")) JSONArray(trimmed) else JSONArray().put(JSONObject(trimmed))
        val incoming = (0 until arr.length()).mapNotNull { i ->
            runCatching {
                val o = arr.getJSONObject(i)
                Server(
                    id = "mcp_" + System.currentTimeMillis() + "_" + i,
                    name = o.optString("name", "导入的服务器"),
                    url = o.optString("url", ""),
                    type = if (o.optString("type", "http").lowercase() == "sse") "sse" else "http",
                    enabled = o.optBoolean("enabled", true)
                )
            }.getOrNull()
        }.filter { it.url.isNotBlank() }
        if (incoming.isEmpty()) return 0
        save(context, list(context) + incoming)
        return incoming.size
    }

    private fun save(context: Context, servers: List<Server>) {
        val arr = JSONArray()
        servers.forEach { s ->
            arr.put(
                JSONObject()
                    .put("id", s.id).put("name", s.name).put("url", s.url)
                    .put("type", s.type).put("enabled", s.enabled)
            )
        }
        prefs(context).edit().putString(KEY, arr.toString()).apply()
        bump()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
