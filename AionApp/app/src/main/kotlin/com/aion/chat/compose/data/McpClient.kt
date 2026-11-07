package com.aion.chat.compose.data

import java.io.BufferedReader
import java.io.InputStreamReader
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * 极简 MCP 客户端（只为「连接 + 列工具」）：
 * - Streamable HTTP：POST JSON-RPC（响应可能是 JSON 或 SSE 帧），带 Mcp-Session-Id。
 * - SSE（legacy）：GET /sse 拿 endpoint 事件 → POST 消息 → 在流上等对应 id 的响应。
 * 协议版本 2025-03-26（多数服务端兼容）；总超时 20s。
 */
object McpClient {

    data class Probe(val ok: Boolean, val tools: List<String>, val err: String)

    private const val PROTOCOL = "2025-03-26"
    private const val DEADLINE_MS = 20_000

    fun probe(server: McpStore.Server): Probe = try {
        val r = if (server.type == "sse") probeSse(server.url) else probeHttp(server.url)
        if (r.second.isEmpty()) Probe(false, emptyList(), r.third.ifBlank { "没拿到工具" })
        else Probe(true, r.second, r.third)
    } catch (e: Exception) {
        Probe(false, emptyList(), "${e.javaClass.simpleName}: ${e.message?.take(120)}")
    }

    // ── Streamable HTTP ──

    private fun probeHttp(url: String): Triple<Boolean, List<String>, String> {
        val t0 = System.currentTimeMillis()
        var sid: String? = null
        // 1) initialize
        val r1 = postRpc(url, initReq(), sid)
        RequestLog.add(null, "POST", url, r1.code, System.currentTimeMillis() - t0, r1.err)
        if (r1.code !in 200..299) return Triple(false, emptyList(), "HTTP ${r1.code} ${r1.err.take(100)}")
        sid = r1.sessionId
        // 2) notifications/initialized（无 id，202 也算过）
        postRpc(url, notifyReq(), sid)
        // 3) tools/list
        val t2 = System.currentTimeMillis()
        val r2 = postRpc(url, toolsReq(), sid)
        RequestLog.add(null, "POST", url, r2.code, System.currentTimeMillis() - t2, r2.err)
        if (r2.code !in 200..299) return Triple(false, emptyList(), "HTTP ${r2.code} ${r2.err.take(100)}")
        val tools = parseTools(extractJson(r2.body))
        return Triple(tools.isNotEmpty(), tools, if (tools.isEmpty()) "连接成功但没有工具" else "")
    }

    // ── SSE legacy ──

    private fun probeSse(endpoint: String): Triple<Boolean, List<String>, String> {
        val deadline = System.currentTimeMillis() + DEADLINE_MS
        val conn = URL(endpoint).openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.connectTimeout = 8_000
        conn.readTimeout = 25_000
        conn.setRequestProperty("Accept", "text/event-stream")
        conn.setRequestProperty("Cache-Control", "no-cache")
        var code = 0
        try {
            code = conn.responseCode
        } catch (e: Exception) {
            return Triple(false, emptyList(), "${e.javaClass.simpleName}: ${e.message?.take(100)}")
        }
        if (code !in 200..299) {
            return Triple(false, emptyList(), "HTTP $code")
        }
        RequestLog.add(null, "GET", endpoint, code, 0)
        val reader = BufferedReader(InputStreamReader(conn.inputStream))
        try {
            // 1) 等 endpoint 事件
            var postUrl: String? = null
            while (System.currentTimeMillis() < deadline) {
                val ev = readEvent(reader, deadline) ?: break
                if (ev.first == "endpoint" || ev.second.contains("sessionId=")) {
                    postUrl = resolveUrl(endpoint, ev.second.trim())
                    break
                }
            }
            if (postUrl == null) return Triple(false, emptyList(), "没等到 endpoint 事件（不是标准 SSE MCP？）")
            // 2) initialize → 在流上等 id=1
            post(postUrl, initReq())
            val r1 = waitResponse(reader, deadline, 1)
            if (r1 == null) return Triple(false, emptyList(), "initialize 没有响应（超时）")
            // 3) notifications/initialized
            post(postUrl, notifyReq())
            // 4) tools/list → 等 id=2
            post(postUrl, toolsReq())
            val r2 = waitResponse(reader, deadline, 2)
                ?: return Triple(false, emptyList(), "tools/list 没有响应（超时）")
            val tools = parseTools(jsonArrayOf(r2))
            return Triple(tools.isNotEmpty(), tools, if (tools.isEmpty()) "连接成功但没有工具" else "")
        } finally {
            runCatching { conn.disconnect() }
        }
    }

    // ── JSON-RPC 组件 ──

    private fun initReq() =
        """{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"$PROTOCOL","capabilities":{},"clientInfo":{"name":"AionsHome","version":"1.0"}}}"""

    private fun notifyReq() =
        """{"jsonrpc":"2.0","method":"notifications/initialized"}"""

    private fun toolsReq() =
        """{"jsonrpc":"2.0","id":2,"method":"tools/list"}"""

    private class RpcReply(val code: Int, val sessionId: String?, val body: String, val err: String)

    private fun postRpc(url: String, body: String, sessionId: String?): RpcReply {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.connectTimeout = 8_000
            conn.readTimeout = 15_000
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Accept", "application/json, text/event-stream")
            if (!sessionId.isNullOrBlank()) conn.setRequestProperty("Mcp-Session-Id", sessionId)
            conn.outputStream.use { it.write(body.toByteArray()) }
            val code = conn.responseCode
            val sid = conn.getHeaderField("Mcp-Session-Id") ?: sessionId
            val text = if (code in 200..299) {
                conn.inputStream.bufferedReader().readText()
            } else {
                conn.errorStream?.bufferedReader()?.readText()?.take(200) ?: ""
            }
            return RpcReply(code, sid, text, if (code in 200..299) "" else text)
        } finally {
            runCatching { conn.disconnect() }
        }
    }

    private fun post(url: String, body: String) {
        runCatching { postRpc(url, body, null) }
    }

    /** SSE 帧读取：返回 (event, data)。跨行 data 拼接。 */
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

    /** 在 SSE 流上等指定 id 的 JSON-RPC 响应。 */
    private fun waitResponse(reader: BufferedReader, deadline: Long, id: Int): JSONObject? {
        while (System.currentTimeMillis() < deadline) {
            val ev = readEvent(reader, deadline) ?: return null
            val json = runCatching { JSONObject(ev.second) }.getOrNull() ?: continue
            if (json.optInt("id", -1) == id && (json.has("result") || json.has("error"))) return json
        }
        return null
    }

    /** 响应体可能是纯 JSON 或 SSE 帧（data: 行），统一抽成 JSON 数组。 */
    private fun extractJson(text: String): org.json.JSONArray {
        val arr = org.json.JSONArray()
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

    private fun jsonArrayOf(o: JSONObject): org.json.JSONArray {
        val arr = org.json.JSONArray()
        arr.put(o)
        return arr
    }

    private fun parseTools(arr: org.json.JSONArray): List<String> {
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val result = o.optJSONObject("result") ?: continue
            val tools = result.optJSONArray("tools") ?: continue
            return (0 until tools.length()).mapNotNull { j ->
                tools.optJSONObject(j)?.optString("name")?.takeIf { it.isNotBlank() }
            }
        }
        return emptyList()
    }

    private fun resolveUrl(base: String, data: String): String {
        if (data.startsWith("http")) return data
        return runCatching {
            val b = URL(base)
            val portPart = when {
                b.port == -1 || b.port == b.defaultPort -> ""
                else -> ":" + b.port
            }
            "${b.protocol}://${b.host}$portPart" + (if (data.startsWith("/")) data else "/$data")
        }.getOrDefault(data)
    }
}
