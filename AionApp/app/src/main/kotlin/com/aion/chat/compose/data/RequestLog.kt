package com.aion.chat.compose.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * 请求日志（照橘瓣的 Logs 页）：记下 app 发出的每次 HTTP——方法/URL/状态码/耗时/错误。
 * 存 filesDir/request_log.json，最多 200 条，新的在前。
 * context 惰性采纳：第一个带 context 的 add() 把 applicationContext 存下来（Java 网关无 context 也能记）。
 */
object RequestLog {

    data class Entry(
        val ts: Long,
        val method: String,
        val url: String,
        val code: Int,      // 0 = 没拿到状态码（网络异常/超时）
        val ms: Long,
        val err: String,
        val cachedTokens: Long = 0,  // 提示词缓存命中 token 数（>0 才显示）
        val promptTokens: Long = 0,  // 本次请求的 prompt 总 token
        val completionTokens: Long = 0 // 补全 token
    )

    private const val FILE = "request_log.json"
    private const val MAX = 200
    private val buf = java.util.ArrayDeque<Entry>()
    private var loaded = false

    @Volatile
    private var appCtx: Context? = null

    @JvmStatic
    @JvmOverloads
    @Synchronized
    fun add(context: Context?, method: String, url: String, code: Int, ms: Long, err: String = "", cachedTokens: Long = 0, promptTokens: Long = 0, completionTokens: Long = 0) {
        if (appCtx == null && context != null) appCtx = context.applicationContext
        buf.addFirst(
            Entry(System.currentTimeMillis(), method.uppercase(), url.take(180), code, ms, err.take(200), cachedTokens, promptTokens, completionTokens)
        )
        while (buf.size > MAX) buf.removeLast()
        persistAsync()
    }

    @Synchronized
    fun list(context: Context): List<Entry> {
        if (!loaded) {
            loaded = true
            if (appCtx == null) appCtx = context.applicationContext
            runCatching {
                val f = file(context)
                if (f.exists()) {
                    val arr = JSONArray(f.readText())
                    for (i in 0 until arr.length()) {
                        val o = arr.getJSONObject(i)
                        buf.addLast(
                            Entry(
                                o.optLong("ts"), o.optString("method"), o.optString("url"),
                                o.optInt("code"), o.optLong("ms"), o.optString("err"),
                                o.optLong("cached"), o.optLong("prompt"), o.optLong("completion")
                            )
                        )
                    }
                }
            }
        }
        return buf.toList()
    }

    @Synchronized
    fun clear(context: Context) {
        buf.clear()
        loaded = true
        file(context).delete()
    }

    private fun file(context: Context): File = File(context.filesDir, FILE)

    private fun persistAsync() {
        Thread {
            runCatching {
                val ctx = appCtx ?: return@runCatching
                val arr = JSONArray()
                val snapshot: List<Entry>
                synchronized(this@RequestLog) { snapshot = buf.toList() }
                snapshot.forEach { e ->
                    arr.put(
                        JSONObject()
                            .put("ts", e.ts).put("method", e.method).put("url", e.url)
                            .put("code", e.code).put("ms", e.ms).put("err", e.err)
                            .put("cached", e.cachedTokens).put("prompt", e.promptTokens)
                            .put("completion", e.completionTokens)
                    )
                }
                file(ctx).writeText(arr.toString())
            }
        }.start()
    }
}
