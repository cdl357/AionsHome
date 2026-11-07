package com.aion.chat.compose.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Sean 的画笔（生图中转站，Yuri 自己接）：
 * - 设置页填 OpenAI 兼容的生图接口（Base URL / API Key / 模型，如 dall-e-3 / gemini-image 系）
 * - 他在回复里嵌 <genimg>画面描述</genimg>，App 调中转站出图，存本地，图显示在这条消息下面
 * - 生成按消息 id 幂等（一次只画一张，重进聊天不重画）；失败留痕，不自动重试
 * - 能力说明走 [画笔] 段落注入 system_prompt，只在配好中转站后才告诉他（免得他空口许诺）
 */
object SeanImageGenStore {

    private const val TAG = "SeanImageGenStore"

    /** state: pending / done / failed */
    data class GenImage(
        val messageId: String, val path: String, val prompt: String,
        val state: String, val createdAt: Long
    )

    private val _rev = mutableStateOf(0)

    /** 读一下即可订阅生成进度变化。 */
    val rev: Int get() = _rev.value

    private fun bump() { _rev.value += 1 }

    private fun prefs(context: Context) =
        context.getSharedPreferences("sean_image_gen", Context.MODE_PRIVATE)

    // ── 中转站配置 ──

    fun saveConfig(context: Context, baseUrl: String, apiKey: String, model: String) {
        prefs(context).edit()
            .putString("base", baseUrl.trim())
            .putString("key", apiKey.trim())
            .putString("model", model.trim())
            .apply()
        bump()
        // identity_snapshot 读写是 DB IO：设置页防抖回调在主线程，别跟着上主线程
        kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            ensureIssuerKnowledge(context)
        }
    }

    fun config(context: Context): Triple<String, String, String> = Triple(
        prefs(context).getString("base", "") ?: "",
        prefs(context).getString("key", "") ?: "",
        prefs(context).getString("model", "") ?: ""
    )

    fun configured(context: Context): Boolean {
        val (b, k, m) = config(context)
        return b.isNotBlank() && k.isNotBlank() && m.isNotBlank()
    }

    // ── 标签 ──

    private val RE_GENIMG = Regex("<genimg>\\s*([\\s\\S]{1,800}?)\\s*</genimg>", RegexOption.IGNORE_CASE)

    fun containsTag(text: String): Boolean = text.lowercase().contains("<genimg>")

    /** 他的画图描述（他可能写中文，转告中转站前原样带上）。 */
    fun extractPrompt(text: String): String? =
        RE_GENIMG.find(text)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }

    /** 标签从展示正文剥除（照 StudyRoomStore 惯例，图单独渲染在这条消息下面）。 */
    fun strip(text: String): String = text
        .replace(RE_GENIMG, "")
        .replace(Regex("</?genimg>", RegexOption.IGNORE_CASE), "")
        .trim()

    // ── 生成记录 ──

    private fun ensure(db: android.database.sqlite.SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS sean_gen_image(" +
                "message_id TEXT PRIMARY KEY, path TEXT, prompt TEXT, " +
                "state TEXT NOT NULL, created_at INTEGER NOT NULL)"
        )
    }

    fun get(context: Context, messageId: String): GenImage? = runCatching {
        val db = com.aion.chat.homecoming.HomecomingDatabase.sharedWritable(context)
        ensure(db)
        db.rawQuery(
            "SELECT message_id, path, prompt, state, created_at FROM sean_gen_image WHERE message_id=?",
            arrayOf(messageId)
        ).use { c ->
            if (!c.moveToFirst()) return null
            val g = GenImage(c.getString(0), c.getString(1) ?: "", c.getString(2) ?: "", c.getString(3), c.getLong(4))
            // 生成中进程被杀 → 行永卡 pending：超 3 分钟判失败（消息从「正在画」变「没画成」）
            if (g.state == "pending" && System.currentTimeMillis() - g.createdAt > 240_000L) {
                upsert(context, messageId, g.prompt, "failed", "")
                g.copy(state = "failed")
            } else g
        }
    }.getOrNull()

    private fun upsert(context: Context, messageId: String, prompt: String, state: String, path: String) {
        runCatching {
            val db = com.aion.chat.homecoming.HomecomingDatabase.sharedWritable(context)
            ensure(db)
            db.execSQL(
                "INSERT OR REPLACE INTO sean_gen_image(message_id, path, prompt, state, created_at) VALUES(?,?,?,?,?)",
                arrayOf(messageId, path, prompt, state, System.currentTimeMillis())
            )
            bump()
        }
    }

    private val inFlight = java.util.Collections.synchronizedSet(mutableSetOf<String>())

    /** 画这张（消息 id 幂等；失败留 failed 痕，不自动重试——重试会每次重组都打中转站）。 */
    fun ensureGenerated(context: Context, messageId: String, prompt: String) {
        if (get(context, messageId) != null) return
        if (!configured(context)) return
        if (!inFlight.add(messageId)) return
        kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                upsert(context, messageId, prompt, "pending", "")
                val bytes = runCatching { callRelay(context, prompt) }.getOrNull()
                if (bytes == null) {
                    upsert(context, messageId, prompt, "failed", "")
                    android.util.Log.w(TAG, "gen failed: $prompt")
                } else {
                    val dir = File(context.filesDir, "chat_images").apply { mkdirs() }
                    val f = File(dir, "gen_" + System.currentTimeMillis() + ".jpg")
                    f.outputStream().use { it.write(bytes) }
                    upsert(context, messageId, prompt, "done", f.absolutePath)
                }
            } finally {
                inFlight.remove(messageId)
                bump()
            }
        }
    }

    /** 调 OpenAI 兼容生图接口：POST {base}/images/generations，收 b64_json 或 url。 */
    private fun callRelay(context: Context, prompt: String): ByteArray {
        val (rawBase, key, model) = config(context)
        var base = rawBase.trim().trimEnd('/')
        if (!base.endsWith("/v1") && !base.endsWith("/images/generations")) base = "$base/v1"
        val body = JSONObject()
            .put("model", model)
            .put("prompt", prompt)
            .put("n", 1)
            .put("size", "1024x1024")
            .put("response_format", "b64_json")
        val conn = java.net.URL("$base/images/generations").openConnection() as java.net.HttpURLConnection
        conn.requestMethod = "POST"
        conn.connectTimeout = 15_000
        conn.readTimeout = 180_000   // 生图慢，给足
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("Authorization", "Bearer $key")
        conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
        val code = conn.responseCode
        val respText = (if (code in 200..299) conn.inputStream else conn.errorStream)
            ?.bufferedReader()?.readText() ?: ""
        if (code !in 200..299) {
            throw IllegalStateException("gen HTTP $code: ${respText.take(200)}")
        }
        val data = JSONObject(respText).optJSONArray("data")
            ?: throw IllegalStateException("gen 响应没有 data")
        val first = data.optJSONObject(0) ?: throw IllegalStateException("gen data 空")
        val b64 = first.optString("b64_json", "")
        if (b64.isNotBlank()) {
            // 有的中转站直接回 data URI 前缀，Base64.decode 会炸：剥掉再解
            val raw = if (b64.contains(";base64,")) b64.substringAfter(";base64,") else b64
            return toJpegIfPng(android.util.Base64.decode(raw, android.util.Base64.DEFAULT))
        }
        // 有些中转站只回 url：下载
        val url = first.optString("url", "")
        if (url.isNotBlank()) {
            val dl = java.net.URL(url).openConnection() as java.net.HttpURLConnection
            dl.connectTimeout = 15_000
            dl.readTimeout = 60_000
            if (dl.responseCode !in 200..299) throw IllegalStateException("gen 图片下载 HTTP ${dl.responseCode}")
            return dl.inputStream.use { it.readBytes() }
        }
        throw IllegalStateException("gen 响应既无 b64_json 也无 url")
    }

    /** PNG 冒名 .jpg 会导致有的渲染器/分享端不认：PNG 魔数才转，JPEG 原样过。 */
    private fun toJpegIfPng(bytes: ByteArray): ByteArray {
        if (bytes.size < 8 || bytes[0] != 0x89.toByte() || bytes[1] != 0x50.toByte()) return bytes
        return runCatching {
            val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return bytes
            val out = ByteArrayOutputStream()
            bmp.compress(Bitmap.CompressFormat.JPEG, 90, out)
            bmp.recycle()
            out.toByteArray()
        }.getOrDefault(bytes)
    }

    // ── 能力说明注入（配好中转站才告诉他） ──

    private fun section(context: Context): String {
        val (_, _, model) = config(context)
        return "\n\n[画笔] 你有一支画笔（生图：$model）。想画给她看时，在回复里嵌 " +
            "<genimg>画面描述</genimg>：描述写具体（主体/氛围/风格，60 词内，中英文都行），" +
            "发出去图会自动画好、显示在这条消息下面。一次回复至多一条；" +
            "她开口让你画、生日节日纪念日、或聊到特别想给她看什么画面时才画，别每条都画。" +
            "画不出来时别编造说画好了。不要向她解释标签原理。"
    }

    private fun sig(context: Context): String {
        val (b, k, m) = config(context)
        return Integer.toHexString((b + "|" + m + "|" + (k.length) + "|ext=" + SeanExt.on(context, "ext_paint")).hashCode())
    }

    /** [画笔] 段落按配置签名刷新（照 [素材库] 的边界约定，段落互不吃）。 */
    fun ensureIssuerKnowledge(context: Context) {
        runCatching {
            val wantSig = sig(context)
            if (prefs(context).getString("prompt_sig", null) == wantSig) return
            val db = com.aion.chat.homecoming.HomecomingDatabase.sharedWritable(context)
            db.rawQuery(
                "SELECT payload_json FROM identity_snapshot WHERE identity_id='identity'", null
            ).use { c ->
                if (!c.moveToFirst()) return
                val root = JSONObject(c.getString(0))
                val sp0 = root.optString("system_prompt", "")
                val cleaned = sp0.replace(Regex("\n\n\\[画笔\\][\\s\\S]*?(?=\n\n\\[|\\z)"), "")
                val next = if (configured(context) && SeanExt.on(context, "ext_paint")) cleaned + section(context) else cleaned
                root.put("system_prompt", next)
                db.execSQL(
                    "UPDATE identity_snapshot SET payload_json=? WHERE identity_id='identity'",
                    arrayOf(root.toString())
                )
                prefs(context).edit().putString("prompt_sig", wantSig).apply()
            }
        }.onFailure { android.util.Log.w(TAG, "ensureIssuerKnowledge fail: ${it.message}") }
    }
}
