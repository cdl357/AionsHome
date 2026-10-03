package com.aion.chat.compose.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * MCP 服务器配置（设置页管理，存 filesDir/mcp.json，应用私有目录，不进代码仓）。
 * 支持两类：
 *  - http   : 远程 MCP（url + headers）
 *  - stdio  : 本地进程 MCP（command + args，仅在后端/桌面侧有意义，App 端只保存配置）
 * 引擎侧的工具调用接线属下一阶段；这里先把配置攒好，导出即可用。
 */
object HomecomingMcpStore {

    data class McpServer(
        val id: String,
        val name: String,
        val type: String,          // "http" | "stdio"
        val url: String,           // http 型
        val command: String,       // stdio 型
        val args: String,          // stdio 型，空格分隔
        val enabled: Boolean = true
    )

    fun file(context: Context): File = File(context.filesDir, "mcp.json")

    fun list(context: Context): List<McpServer> {
        val f = file(context)
        if (!f.exists()) return emptyList()
        return try {
            val arr = JSONObject(f.readText()).optJSONArray("mcp") ?: return emptyList()
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                McpServer(
                    id = o.optString("id"),
                    name = o.optString("name"),
                    type = o.optString("type", "http"),
                    url = o.optString("url", ""),
                    command = o.optString("command", ""),
                    args = o.optString("args", ""),
                    enabled = o.optBoolean("enabled", true)
                )
            }
        } catch (e: Exception) { emptyList() }
    }

    fun save(context: Context, servers: List<McpServer>): Boolean = try {
        val arr = JSONArray()
        servers.forEach { s ->
            arr.put(
                JSONObject()
                    .put("id", s.id)
                    .put("name", s.name)
                    .put("type", s.type)
                    .put("url", s.url)
                    .put("command", s.command)
                    .put("args", s.args)
                    .put("enabled", s.enabled)
            )
        }
        file(context).writeText(JSONObject().put("mcp", arr).toString(2))
        true
    } catch (e: Exception) { false }

    fun upsert(context: Context, server: McpServer): Boolean {
        val cur = list(context).toMutableList()
        val idx = cur.indexOfFirst { it.id == server.id }
        if (idx >= 0) cur[idx] = server else cur.add(server)
        return save(context, cur)
    }

    fun remove(context: Context, id: String): Boolean {
        val cur = list(context).filter { it.id != id }
        return save(context, cur)
    }

    fun toggle(context: Context, id: String): Boolean {
        val cur = list(context)
        val next = cur.map { if (it.id == id) it.copy(enabled = !it.enabled) else it }
        return save(context, next)
    }

    fun newId(): String = "mcp_" + System.currentTimeMillis().toString(36)

    /** 导出成标准 MCP 配置片段（mcpServers JSON，贴到桌面端/后端配置里就能用）。 */
    fun exportJson(servers: List<McpServer>): String {
        val root = JSONObject()
        servers.filter { it.enabled }.forEach { s ->
            val def = JSONObject()
            if (s.type == "http") {
                def.put("url", s.url)
            } else {
                def.put("command", s.command)
                def.put("args", JSONArray(s.args.split(" ").filter { it.isNotBlank() }))
            }
            root.put(s.name, def)
        }
        return JSONObject().put("mcpServers", root).toString(2)
    }
}
