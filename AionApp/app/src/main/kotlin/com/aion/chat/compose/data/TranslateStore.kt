package com.aion.chat.compose.data

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * 翻译页（队列项）：复用当前激活提供商的模型做一次性翻译。
 * - 不进聊天树、不落对话历史、不带人设上下文（纯翻译引擎语义）
 * - 历史存 files/translate_history.json，最近 50 条
 * - 请求：OpenAI 兼容 chat/completions 非流式（URL 折叠规则与 ProviderStore 一致）
 */
object TranslateStore {

    data class HistoryItem(
        val ts: Long,
        val source: String,
        val result: String,
        val sourceLang: String,
        val targetLang: String,
    )

    private const val MAX_HISTORY = 50

    private fun file(c: Context) = File(c.filesDir, "translate_history.json")

    fun history(c: Context): List<HistoryItem> = runCatching {
        val arr = JSONArray(file(c).takeIf { it.isFile }?.readText() ?: "[]")
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            HistoryItem(
                ts = o.optLong("ts"), source = o.optString("source"),
                result = o.optString("result"),
                sourceLang = o.optString("from"), targetLang = o.optString("to")
            )
        }
    }.getOrDefault(emptyList())

    fun add(c: Context, item: HistoryItem) {
        runCatching {
            val arr = JSONArray(file(c).takeIf { it.isFile }?.readText() ?: "[]")
            val next = JSONArray().put(item.toJson())
            for (i in 0 until arr.length().coerceAtMost(MAX_HISTORY - 1)) next.put(arr.get(i))
            file(c).writeText(next.toString())
        }
    }

    fun clear(c: Context) { runCatching { file(c).delete() } }

    private fun HistoryItem.toJson() = JSONObject()
        .put("ts", ts).put("source", source).put("result", result)
        .put("from", sourceLang).put("to", targetLang)

    // ── 翻译请求 ──

    /** 调当前激活模型翻译；返回 (译文, null) 或 (null, 错误) */
    fun translate(
        c: Context, text: String, sourceLang: String, targetLang: String,
    ): Pair<String?, String?> {
        val act = ProviderStore.active(c)
            ?: return null to "没有可用线路——先去设置里配好提供商"
        val (provider, model) = act
        val url = ProviderStore.foldProviderUrl(provider.baseUrl, provider.apiPath)
        val body = JSONObject()
            .put("model", model.model)
            .put("stream", false)
            .put("temperature", 0.2)
            .put("messages", org.json.JSONArray()
                .put(JSONObject().put("role", "system").put("content", systemPrompt(sourceLang, targetLang)))
                .put(JSONObject().put("role", "user").put("content", text)))
        return runCatching {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Authorization", "Bearer ${provider.apiKey}")
                setRequestProperty("Content-Type", "application/json")
                connectTimeout = 30000
                readTimeout = 120000
                doOutput = true
            }
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val text2 = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.use { it.readText() } ?: ""
            if (code !in 200..299) {
                return null to "HTTP $code：${text2.take(200)}"
            }
            val content = JSONObject(text2)
                .optJSONArray("choices")?.optJSONObject(0)
                ?.optJSONObject("message")?.optString("content").orEmpty()
            if (content.isBlank()) null to "返回为空"
            else content.trim() to null
        }.getOrElse {
            Log.w("Translate", "request failed", it)
            null to (it.message?.take(160) ?: "请求失败")
        }
    }

    private fun systemPrompt(sourceLang: String, targetLang: String): String = buildString {
        append("你是翻译引擎。把用户输入从")
        append(if (sourceLang.isBlank()) "自动检测的源语言" else sourceLang)
        append("翻译成")
        append(targetLang)
        append("。规则：只输出译文本身，不要任何解释、注音或前后缀；")
        append("保留原文的换行和列表结构；专有名词、代码、URL 保持原样；")
        append("语气自然贴切，不要直译腔。")
    }
}
