package com.aion.chat.compose.data

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * 自建网关 SSE 事件流（交接文档《前端改造交接-GLM.md》）。
 *
 * 铁律（文档 3.2）：result 才是本轮结束的唯一信号；未知 type 记日志忽略不崩；
 * text_delta 是增量，assistant_done 是全量校正（不一致以它为准）。
 * 纪律（文档 5.x）：断开不取消生成（跑在 App 级 scope，切后台不断流）；流式过程中随时可落库；
 * 单 chatId 单并发由聊天页 busy 态保证；空回复不当沉默。
 */

// ── 事件（sealed + 未知兜底） ──

sealed class GatewayEvent {
    data class Init(val sessionId: String) : GatewayEvent()
    data class TextDelta(val text: String) : GatewayEvent()
    data class ThinkingDelta(val text: String) : GatewayEvent()
    data class ToolStart(val toolName: String) : GatewayEvent()
    data class ToolDone(val toolName: String, val ok: Boolean) : GatewayEvent()
    data class AssistantDone(val content: String) : GatewayEvent()
    data class Retry(val error: String, val attempt: Int) : GatewayEvent()
    data class Error(val message: String) : GatewayEvent()
    data object Compact : GatewayEvent()
    data object Result : GatewayEvent()
    data class Unknown(val type: String) : GatewayEvent()

    companion object {
        fun parse(frame: String): GatewayEvent {
            val o = runCatching { JSONObject(frame) }.getOrElse { return Unknown("bad_json") }
            return when (o.optString("type")) {
                "init" -> Init(o.optString("sessionId"))
                "text_delta" -> TextDelta(o.optString("text"))
                "thinking_delta" -> ThinkingDelta(o.optString("text"))
                "tool_start" -> ToolStart(o.optString("toolName"))
                "tool_done" -> ToolDone(o.optString("toolName"), o.optBoolean("ok", true))
                "assistant_done" -> AssistantDone(o.optString("content"))
                "compact" -> Compact
                "retry" -> Retry(o.optString("error"), o.optInt("attempt", 0))
                "result" -> Result
                "error" -> Error(o.optString("message", "网关开小差了"))
                else -> Unknown(o.optString("type"))
            }
        }
    }
}

object GatewayChat {

    // TODO(沈聿淮): 网关端口未定，联调时改这里（文档：base url 抽成常量，不散落）
    const val BASE = "http://134.175.7.196:8790"

    private const val TAG = "GatewayChat"

    /** App 级作用域：用户锁屏/切后台/聊天页离开都不中断读流（纪律 5.1）。 */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        // SSE 长连接：读不设超时，靠 result/断流本身结束
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val activeCalls = ConcurrentHashMap<String, okhttp3.Call>()

    interface Observer {
        /** thinking_delta 增量。 */
        fun onThinking(delta: String) {}
        /** 工具状态：done=null 开始，true/false 结束。 */
        fun onTool(toolName: String, done: Boolean?) {}
        /** text_delta 增量（追加渲染，别再做字符级延迟）。 */
        fun onChunk(delta: String) {}
        /** 温和提示：compact「他整理了一次记忆」/ retry「被限流了在重试」。 */
        fun onNotice(text: String) {}
        /** 本轮正文（assistant_done 校正后；可能为空=丢件）与思维链全文。 */
        fun onComplete(fullText: String, thinking: String) {}
        fun onFailure(code: String) {}
    }

    /**
     * 发一轮：先落 user 消息（纪律 5.2），再开 SSE 读流。
     * 流中每收一段就增量回调；断了把已收到的部分提交落库（保住已收内容）；
     * result 到达才回调 onComplete 并提交最终正文+思维链。
     */
    fun send(
        chatId: String,
        requestId: String,
        text: String,
        imageDataUrl: String,
        context: android.content.Context,
        repo: com.aion.chat.homecoming.HomecomingChatRepository,
        responderOwnerId: String,
        observer: Observer,
    ) {
        // ① user 先落库（含图片占位标记，图片文件由聊天页按消息 id 另存）
        runCatching {
            repo.commitUserMessage(
                requestId, chatId, "user", text,
                if (imageDataUrl.isNotBlank()) "image" else "", System.currentTimeMillis()
            )
        }
        // ② SSE 流
        scope.launch {
            val body = JSONObject().apply {
                put("chatId", chatId)
                put("text", text)
                if (imageDataUrl.isNotBlank()) {
                    put("images", JSONArray().put(JSONObject()
                        .put("mediaType", imageDataUrl.substringAfter("data:").substringBefore(";base64").ifBlank { "image/jpeg" })
                        .put("data", imageDataUrl.substringAfter(";base64,"))))
                }
            }
            val textSb = StringBuilder()
            val thinkSb = StringBuilder()
            var assistantDone: String? = null
            var finished = false
            val call = client.newCall(
                Request.Builder()
                    .url("$BASE/chat")
                    .header("Accept", "text/event-stream")
                    .post(body.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                    .build()
            )
            activeCalls[chatId] = call
            try {
                call.execute().use { resp ->
                    if (!resp.isSuccessful || resp.body == null) {
                        observer.onFailure("gateway HTTP ${resp.code}")
                        return@launch
                    }
                    val reader = BufferedReader(
                        InputStreamReader(resp.body!!.byteStream(), Charsets.UTF_8)
                    )
                    while (true) {
                        val line = reader.readLine() ?: break   // 断流：走 catch，已收内容保住
                        if (line.isBlank() || !line.startsWith("data:")) continue
                        val ev = GatewayEvent.parse(line.removePrefix("data:").trim())
                        when (ev) {
                            is GatewayEvent.Init -> Log.i(TAG, "session=${ev.sessionId}")
                            is GatewayEvent.TextDelta -> { textSb.append(ev.text); observer.onChunk(ev.text) }
                            is GatewayEvent.ThinkingDelta -> { thinkSb.append(ev.text); observer.onThinking(ev.text) }
                            is GatewayEvent.ToolStart -> observer.onTool(ev.toolName, null)
                            is GatewayEvent.ToolDone -> observer.onTool(ev.toolName, ev.ok)
                            is GatewayEvent.AssistantDone -> {
                                // 全量校正：不一致以 assistant_done 为准
                                assistantDone = ev.content
                                if (textSb.toString() != ev.content) {
                                    textSb.setLength(0); textSb.append(ev.content)
                                    observer.onChunk(ev.content)
                                }
                            }
                            is GatewayEvent.Retry -> observer.onNotice("被限流了，稍等（第 ${ev.attempt} 次）…")
                            GatewayEvent.Compact -> observer.onNotice("他整理了一次记忆")
                            is GatewayEvent.Error -> {
                                finished = true
                                observer.onFailure(ev.message.ifBlank { "gateway_error" })
                            }
                            GatewayEvent.Result -> {
                                finished = true
                                // result 才是唯一结束信号
                                val full = (assistantDone ?: textSb.toString())
                                val thinking = thinkSb.toString()
                                runCatching {
                                    repo.commitAssistantMessage(
                                        requestId, chatId, responderOwnerId,
                                        full, System.currentTimeMillis()
                                    )
                                    if (thinking.isNotBlank()) {
                                        ThinkingStore.save(context, requestId, thinking)
                                    }
                                }
                                observer.onComplete(full, thinking)
                            }
                            is GatewayEvent.Unknown -> Log.i(TAG, "未知事件忽略: ${ev.type}")
                        }
                        if (finished) break
                    }
                    // 服务端流关闭但没发 result：按纪律当丢件处理，已收的部分保住
                    if (!finished) {
                        val partial = (assistantDone ?: textSb.toString())
                        if (partial.isNotBlank()) {
                            runCatching {
                                repo.commitAssistantMessage(
                                    requestId, chatId, responderOwnerId,
                                    partial, System.currentTimeMillis()
                                )
                                if (thinkSb.isNotBlank()) ThinkingStore.save(context, requestId, thinkSb.toString())
                            }
                            observer.onComplete(partial, thinkSb.toString())
                        } else {
                            observer.onFailure("stream_ended_without_result")
                        }
                    }
                }
            } catch (e: Exception) {
                if (finished) return@launch
                // 断开≠取消生成（服务端继续跑）；已收到的部分先保住
                val partial = (assistantDone ?: textSb.toString())
                if (partial.isNotBlank()) {
                    runCatching {
                        repo.commitAssistantMessage(
                            requestId, chatId, responderOwnerId,
                            partial, System.currentTimeMillis()
                        )
                        if (thinkSb.isNotBlank()) ThinkingStore.save(context, requestId, thinkSb.toString())
                    }
                    observer.onComplete(partial, thinkSb.toString())
                } else {
                    observer.onFailure("network:${e.javaClass.simpleName}")
                }
            } finally {
                activeCalls.remove(chatId)
            }
        }
    }

    /** 用户主动暂停：POST /chat/stop + 本地断开读流。 */
    fun stop(chatId: String) {
        activeCalls.remove(chatId)?.cancel()
        scope.launch {
            runCatching {
                client.newCall(
                    Request.Builder()
                        .url("$BASE/chat/stop")
                        .post("""{"chatId":"$chatId"}""".toRequestBody("application/json".toMediaType()))
                        .build()
                ).execute().close()
            }
        }
    }
}

/**
 * 思维链持久化（文档 4.1：刷新/重进还能点开看）。
 * 独立小表挂在同一个 homecoming 库上，不动消息表结构；键=消息的 requestId。
 */
object ThinkingStore {

    private const val TAG = "ThinkingStore"

    fun ensure(db: android.database.sqlite.SQLiteDatabase) {
        runCatching {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS message_thinking(" +
                    "request_id TEXT PRIMARY KEY, thinking TEXT NOT NULL, created_at INTEGER NOT NULL)"
            )
        }
    }

    fun save(context: android.content.Context, requestId: String, thinking: String) {
        runCatching {
            val db = com.aion.chat.homecoming.HomecomingDatabase(context).writableDatabase
            ensure(db)
            db.execSQL(
                "INSERT OR REPLACE INTO message_thinking(request_id, thinking, created_at) VALUES(?,?,?)",
                arrayOf(requestId, thinking, System.currentTimeMillis())
            )
        }.onFailure { Log.w(TAG, "save fail: ${it.message}") }
    }

    fun get(context: android.content.Context, requestId: String): String = runCatching {
        val db = com.aion.chat.homecoming.HomecomingDatabase(context).readableDatabase
        ensure(db)
        db.rawQuery(
            "SELECT thinking FROM message_thinking WHERE request_id=?",
            arrayOf(requestId)
        ).use { c -> if (c.moveToFirst()) c.getString(0) else "" }
    }.getOrDefault("")
}
