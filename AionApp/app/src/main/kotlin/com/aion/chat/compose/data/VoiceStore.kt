package com.aion.chat.compose.data

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * 聊天朗读的克隆声音 v2（ElevenLabs——她的账号里已有克隆音色，不再用 SiliconFlow）：
 * - el_key：ElevenLabs API Key（xi-api-key）
 * - el_voice：选中的 voice_id（从「拉取音色列表」里挑她自己的克隆音色）
 * - synth：POST /v1/text-to-speech/{voice_id} → mp3 → 缓存播放
 * 通话声音是 kimi-room 服务器端合成（ELEVENLABS_VOICE_ID 环境变量），另一条线。
 */
object VoiceStore {
    private const val PREFS = "voice_clone"
    private const val BASE = "https://api.elevenlabs.io"

    private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun key(c: Context): String = prefs(c).getString("el_key", "") ?: ""
    fun voice(c: Context): String = prefs(c).getString("el_voice", "") ?: ""
    fun voiceName(c: Context): String = prefs(c).getString("el_voice_name", "") ?: ""
    fun configured(c: Context): Boolean = key(c).isNotBlank() && voice(c).isNotBlank()

    fun setKey(c: Context, v: String) = prefs(c).edit().putString("el_key", v.trim()).apply()
    fun setVoice(c: Context, id: String, name: String) =
        prefs(c).edit().putString("el_voice", id.trim()).putString("el_voice_name", name).apply()

    /** 拉取账号里的音色列表（含她的克隆音色），返回 (name, voice_id) 列表 */
    fun listVoices(c: Context): Pair<List<Pair<String, String>>?, String?> {
        val k = key(c)
        if (k.isBlank()) return null to "先填 ElevenLabs API Key"
        return runCatching {
            val conn = (URL("$BASE/v1/voices").openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("xi-api-key", k)
                connectTimeout = 15000
                readTimeout = 30000
            }
            val code = conn.responseCode
            val body = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.use { it.readText() } ?: ""
            if (code !in 200..299) return null to "HTTP $code：${body.take(200)}"
            val arr = JSONObject(body).optJSONArray("voices")
                ?: return null to "响应里没有 voices 数组"
            val out = mutableListOf<Pair<String, String>>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val id = o.optString("voice_id")
                val name = o.optString("name", "未命名")
                if (id.isNotBlank()) out.add(name to id)
            }
            out.toList() to null
        }.getOrElse { null to (it.message?.take(200) ?: "拉取失败") }
    }

    /** 合成一段文字 → 缓存 mp3（阻塞 IO 线程调用；模型用 multilingual_v2，中文可用） */
    fun synth(c: Context, text: String): File? {
        val k = key(c); val v = voice(c)
        if (k.isBlank() || v.isBlank()) return null
        return runCatching {
            val body = JSONObject()
                .put("text", text.take(800))
                .put("model_id", "eleven_multilingual_v2")
                .put("voice_settings", JSONObject()
                    .put("stability", 0.55)
                    .put("similarity_boost", 0.8)
                    .put("style", 0.3)
                    .put("use_speaker_boost", true))
            val conn = (URL("$BASE/v1/text-to-speech/$v").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("xi-api-key", k)
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "audio/mpeg")
                doOutput = true
                connectTimeout = 15000
                readTimeout = 90000
            }
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            if (code !in 200..299) return null
            val out = File(c.cacheDir, "tts_" + System.currentTimeMillis() + ".mp3")
            conn.inputStream.use { ins ->
                java.io.FileOutputStream(out).use { it.write(ins.readBytes()) }
            }
            if (out.length() < 512) { runCatching { out.delete() }; return null }
            out
        }.getOrNull()
    }
}
