package com.aion.chat.compose.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * MCP 客户端（照橘瓣 McpManager 的成熟度 + 橘子岛的简洁）：
 * - 协议：Streamable HTTP / SSE legacy，JSON-RPC（initialize → tools/call）
 * - 工具注册表：enabled 服务器的工具（安全名→服务器/真名映射），供聊天网关注入 tools
 * - 会话缓存：Streamable HTTP 的 Mcp-Session-Id 按服务器缓存，过期自动重握手
 */
object McpClient {

    data class Probe(val ok: Boolean, val tools: List<String>, val err: String)

    data class ToolDef(
        @JvmField val serverId: String,
        @JvmField val serverName: String,
        @JvmField val name: String,       // MCP 真名
        @JvmField val safeName: String,   // OpenAI function 名（合法字符）
        @JvmField val description: String,
        @JvmField val schema: String      // inputSchema JSON
    )

    data class CallResult(@JvmField val ok: Boolean, @JvmField val text: String, @JvmField val err: String)

    private const val PROTOCOL = "2025-03-26"
    private const val DEADLINE_MS = 20_000

    // 工具注册表（异步刷新，聊天网关同步读）
    @Volatile
    private var registry: List<ToolDef> = emptyList()

    // serverId -> Mcp-Session-Id（Streamable HTTP）
    private val sessions = java.util.concurrent.ConcurrentHashMap<String, String>()

    @Volatile
    private var ctxRef: Context? = null

    @Volatile
    var lastRefreshErr: String = ""
        private set

    fun initContext(context: Context) {
        if (ctxRef == null) ctxRef = context.applicationContext
    }

    private fun appCtxSafe(): Context? = ctxRef

    @JvmStatic
    fun cachedDefs(): List<ToolDef> = registry

    /** 刷新工具注册表（网络操作，IO 线程调用） */
    @JvmStatic
    fun refreshTools(context: Context) {
        initContext(context)
        val out = mutableListOf<ToolDef>()
        var firstErr = ""
        McpStore.list(context).filter { it.enabled }.forEach { srv ->
            val r = handshakeList(srv)
            if (r.first) {
                r.second.forEach { t ->
                    out.add(
                        ToolDef(
                            serverId = srv.id,
                            serverName = srv.name,
                            name = t.first,
                            safeName = sanitizeName(srv.name + "_" + t.first),
                            description = t.second,
                            schema = t.third
                        )
                    )
                }
            } else if (firstErr.isEmpty()) firstErr = srv.name + ": " + r.third
        }
        registry = out
        lastRefreshErr = firstErr
    }

    /** 按安全名调用工具（聊天网关的工具回环入口） */
    @JvmStatic
    fun callBySafeName(safeName: String, argsJson: String): CallResult {
        val def = registry.firstOrNull { it.safeName == safeName }
            ?: return CallResult(false, "", "工具不存在: $safeName")
        val srv = McpStore.list(ctxRef ?: return CallResult(false, "", "上下文未就绪"))
            .firstOrNull { it.id == def.serverId }
            ?: return CallResult(false, "", "服务器已移除: ${def.serverName}")
        return callTool(srv, def.name, argsJson)
    }

    private fun sanitizeName(raw: String): String =
        raw.replace(Regex("[^a-zA-Z0-9_-]"), "_").take(64)

    // ── 握手 + 工具清单（probe 和 refresh 共用） ──

    /** 返回 (ok, [(name, description, schema)], err) */
    private fun handshakeList(srv: McpStore.Server): Triple<Boolean, List<Triple<String, String, String>>, String> {
        return if (srv.type == "sse") handshakeListSse(srv.url) else handshakeListHttp(srv.url, srv.id)
    }

    private fun handshakeListHttp(url: String, serverId: String): Triple<Boolean, List<Triple<String, String, String>>, String> {
        var sid: String? = sessions[serverId]
        // 先试缓存会话直接 tools/list，失败（会话过期）则完整握手重试一次
        for (attempt in 0..1) {
            if (attempt == 1) sid = null
            val init: RpcReply?
            var effectiveSid = sid
            if (sid == null) {
                val t0 = System.currentTimeMillis()
                val r1 = postRpc(url, initReq(), null)
                RequestLog.add(null, "POST", url, r1.code, System.currentTimeMillis() - t0, r1.err)
                if (r1.code !in 200..299) return Triple(false, emptyList(), "HTTP ${r1.code} ${r1.err.take(80)}")
                sessions[serverId] = r1.sessionId ?: ""
                postRpc(url, notifyReq(), r1.sessionId)
                init = r1
                effectiveSid = r1.sessionId
            } else init = null
            val t2 = System.currentTimeMillis()
            val r2 = postRpc(url, toolsListReq(), effectiveSid)
            RequestLog.add(null, "POST", url, r2.code, System.currentTimeMillis() - t2, r2.err)
            if (r2.code in 200..299) {
                if (r2.sessionId != null) sessions[serverId] = r2.sessionId
                val tools = parseToolsDetailed(extractJson(r2.body))
                if (tools.isNotEmpty() || attempt == 1 || init != null)
                    return Triple(true, tools, if (tools.isEmpty()) "连接成功但没有工具" else "")
                // 空结果且是缓存会话 → 完整握手重试一次
            } else if (attempt == 1) {
                return Triple(false, emptyList(), "HTTP ${r2.code} ${r2.err.take(80)}")
            }
        }
        return Triple(false, emptyList(), "会话握手失败")
    }

    private fun handshakeListSse(endpoint: String): Triple<Boolean, List<Triple<String, String, String>>, String> {
        val deadline = System.currentTimeMillis() + DEADLINE_MS
        val conn = openSse(endpoint) ?: return Triple(false, emptyList(), "连接失败")
        try {
            val reader = BufferedReader(InputStreamReader(conn.inputStream))
            val postUrl = sseWaitEndpoint(reader, deadline)
                ?: return Triple(false, emptyList(), "没等到 endpoint 事件（不是标准 SSE MCP？）")
            post(postUrl, initReq())
            if (sseWaitResponse(reader, deadline, 1) == null)
                return Triple(false, emptyList(), "initialize 没有响应（超时）")
            post(postUrl, notifyReq())
            post(postUrl, toolsListReq())
            val r2 = sseWaitResponse(reader, deadline, 2)
                ?: return Triple(false, emptyList(), "tools/list 没有响应（超时）")
            return Triple(true, parseToolsDetailed(jsonArrayOf(r2)), "")
        } finally {
            runCatching { conn.disconnect() }
        }
    }

    // ── 工具调用 ──

    fun callTool(srv: McpStore.Server, tool: String, argsJson: String): CallResult {
        return try {
            if (srv.type == "sse") callToolSse(srv.url, tool, argsJson) else callToolHttp(srv.url, srv.id, tool, argsJson)
        } catch (e: Exception) {
            CallResult(false, "", "${e.javaClass.simpleName}: ${e.message?.take(120)}")
        }
    }

    private fun callToolHttp(url: String, serverId: String, tool: String, argsJson: String): CallResult {
        val t0 = System.currentTimeMillis()
        var sid: String? = sessions[serverId]
        val callBody = callReq(tool, argsJson, 2)
        var r = postRpc(url, callBody, sid)
        if (r.code !in 200..299 && sid != null) {
            // 会话过期：完整握手重试
            val init = postRpc(url, initReq(), null)
            if (init.code in 200..299) {
                sessions[serverId] = init.sessionId ?: ""
                postRpc(url, notifyReq(), init.sessionId)
                r = postRpc(url, callBody, init.sessionId)
            }
        }
        RequestLog.add(null, "POST", url, r.code, System.currentTimeMillis() - t0,
            if (r.ok) "" else r.err, 0, 0)
        if (!r.ok) return CallResult(false, "", "HTTP ${r.code} ${r.err.take(100)}")
        val arr = extractJson(r.body)
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val result = o.optJSONObject("result")
            if (result != null) return CallResult(true, contentText(result), "")
            val err = o.optJSONObject("error")
            if (err != null) return CallResult(false, "", err.optString("message", "工具执行出错").take(150))
        }
        return CallResult(true, "", "空结果")
    }

    private fun callToolSse(endpoint: String, tool: String, argsJson: String): CallResult {
        val deadline = System.currentTimeMillis() + DEADLINE_MS
        val conn = openSse(endpoint) ?: return CallResult(false, "", "连接失败")
        try {
            val reader = BufferedReader(InputStreamReader(conn.inputStream))
            val postUrl = sseWaitEndpoint(reader, deadline)
                ?: return CallResult(false, "", "没等到 endpoint 事件")
            post(postUrl, initReq())
            if (sseWaitResponse(reader, deadline, 1) == null)
                return CallResult(false, "", "initialize 没有响应（超时）")
            post(postUrl, notifyReq())
            post(postUrl, callReq(tool, argsJson, 2))
            val r2 = sseWaitResponse(reader, deadline, 2)
                ?: return CallResult(false, "", "工具调用没有响应（超时）")
            val result = r2.optJSONObject("result")
            if (result != null) return CallResult(true, contentText(result), "")
            val err = r2.optJSONObject("error")
            return CallResult(false, "", err?.optString("message", "工具执行出错")?.take(150) ?: "未知错误")
        } finally {
            runCatching { conn.disconnect() }
        }
    }

    private fun contentText(result: JSONObject): String {
        val parts = mutableListOf<String>()
        val content = result.optJSONArray("content")
        if (content != null) {
            for (i in 0 until content.length()) {
                val c = content.optJSONObject(i) ?: continue
                when (c.optString("type")) {
                    "text" -> parts.add(c.optString("text"))
                    "image" -> parts.add("[图片结果]")
                    "resource" -> parts.add("[资源] " + (c.optJSONObject("resource")?.optString("uri", "") ?: ""))
                }
            }
        }
        if (parts.isEmpty() && result.optString("text", "").isNotBlank()) {
            parts.add(result.optString("text"))
        }
        val isError = result.optBoolean("isError", false)
        val text = parts.joinToString("\n").trim().take(4000)
        return if (isError) "工具报告错误: $text" else text
    }

    // ── probe（McpScreen 状态点用） ──

    fun probe(server: McpStore.Server): Probe {
        val r = handshakeList(server)
        return if (r.first) Probe(true, r.second.map { it.first }, r.third)
        else Probe(false, emptyList(), r.third)
    }

    // ── 底层组件 ──

    private fun initReq() =
        """{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"$PROTOCOL","capabilities":{},"clientInfo":{"name":"AionsHome","version":"1.0"}}}"""

    private fun notifyReq() = """{"jsonrpc":"2.0","method":"notifications/initialized"}"""

    private fun toolsListReq() = """{"jsonrpc":"2.0","id":2,"method":"tools/list"}"""

    private fun callReq(tool: String, argsJson: String, id: Int): String {
        val args = runCatching { JSONObject(argsJson) }.getOrDefault(JSONObject())
        return JSONObject()
            .put("jsonrpc", "2.0").put("id", id)
            .put("method", "tools/call")
            .put("params", JSONObject().put("name", tool).put("arguments", args))
            .toString()
    }

    private class RpcReply(val code: Int, val sessionId: String?, val body: String, val err: String) {
        val ok: Boolean get() = code in 200..299
    }

    private fun postRpc(url: String, body: String, sessionId: String?): RpcReply {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.connectTimeout = 8_000
            conn.readTimeout = 20_000
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Accept", "application/json, text/event-stream")
            if (!sessionId.isNullOrBlank()) conn.setRequestProperty("Mcp-Session-Id", sessionId)
            conn.outputStream.use { it.write(body.toByteArray()) }
            val code = conn.responseCode
            val sid = conn.getHeaderField("Mcp-Session-Id") ?: sessionId
            val text = if (code in 200..299) {
                conn.inputStream.bufferedReader().readText()
            } else conn.errorStream?.bufferedReader()?.readText()?.take(200) ?: ""
            return RpcReply(code, sid, text, if (code in 200..299) "" else text)
        } finally {
            runCatching { conn.disconnect() }
        }
    }

    private fun post(url: String, body: String) {
        runCatching { postRpc(url, body, null) }
    }

    private fun openSse(endpoint: String): HttpURLConnection? = runCatching {
        val conn = URL(endpoint).openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.connectTimeout = 8_000
        conn.readTimeout = 25_000
        conn.setRequestProperty("Accept", "text/event-stream")
        conn.setRequestProperty("Cache-Control", "no-cache")
        if (conn.responseCode !in 200..299) return null
        conn
    }.getOrNull()

    private fun sseWaitEndpoint(reader: BufferedReader, deadline: Long): String? {
        while (System.currentTimeMillis() < deadline) {
            val ev = readEvent(reader, deadline) ?: return null
            if (ev.first == "endpoint" || ev.second.contains("sessionId=")) return ev.second.trim()
        }
        return null
    }

    private fun sseWaitResponse(reader: BufferedReader, deadline: Long, id: Int): JSONObject? {
        while (System.currentTimeMillis() < deadline) {
            val ev = readEvent(reader, deadline) ?: return null
            val json = runCatching { JSONObject(ev.second) }.getOrNull() ?: continue
            if (json.optInt("id", -1) == id && (json.has("result") || json.has("error"))) return json
        }
        return null
    }

    private fun readEvent(reader: BufferedReader, deadline: Long): Pair<String, String>? {
        var event = "message"
        val data = StringBuilder()
        while (System.currentTimeMillis() < deadline) {
            val line = reader.readLine() ?: return null
            if (line.isEmpty()) {
                if (data.isNotEmpty()) return event to data.toString()
                event = "message"
                continue
            }
            when {
                line.startsWith("event:") -> event = line.removePrefix("event:").trim()
                line.startsWith("data:") -> data.append(line.removePrefix("data:").trim())
            }
        }
        return null
    }

    private fun extractJson(text: String): JSONArray {
        val arr = JSONArray()
        val t = text.trim()
        if (t.startsWith("{")) {
            runCatching { arr.put(JSONObject(t)) }
            return arr
        }
        t.lineSequence().filter { it.startsWith("data:") }.forEach { line ->
            val d = line.removePrefix("data:").trim()
            if (d.isNotEmpty()) runCatching { arr.put(JSONObject(d)) }
        }
        return arr
    }

    private fun jsonArrayOf(o: JSONObject): JSONArray {
        val arr = JSONArray()
        arr.put(o)
        return arr
    }

    private fun parseToolsDetailed(arr: JSONArray): List<Triple<String, String, String>> {
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val result = o.optJSONObject("result") ?: continue
            val tools = result.optJSONArray("tools") ?: continue
            return (0 until tools.length()).mapNotNull { j ->
                val t = tools.optJSONObject(j) ?: return@mapNotNull null
                val name = t.optString("name").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                Triple(name, t.optString("description", ""), t.optJSONObject("inputSchema")?.toString() ?: "{}")
            }
        }
        return emptyList()
    }
}
