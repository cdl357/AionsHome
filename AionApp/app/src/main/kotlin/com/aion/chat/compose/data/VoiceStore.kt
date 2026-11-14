package com.aion.chat.compose.data

import android.content.Context
import android.net.Uri
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * 聊天朗读的克隆声音（v0.3.152，硅基流动 CosyVoice）：
 * - sf_key：SiliconFlow API Key（设置 → 语音 填）
 * - sf_voice：克隆出来的音色 uri（speech:xxx——选一段 ≥10s 的干净人声上传克隆）
 * - speak：合成整段 mp3 到缓存再播（v1 非流式；通话声音走服务器合成，另一条线）
 */
object VoiceStore {
    private const val PREFS = "voice_clone"
    private const val BASE = "https://api.siliconflow.cn/v1"
    private const val MODEL = "FunAudioLLM/CosyVoice2-0.5B"

    private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun key(c: Context): String = prefs(c).getString("sf_key", "") ?: ""
    fun voice(c: Context): String = prefs(c).getString("sf_voice", "") ?: ""
    fun configured(c: Context): Boolean = key(c).isNotBlank() && voice(c).isNotBlank()

    fun setKey(c: Context, v: String) = prefs(c).edit().putString("sf_key", v.trim()).apply()
    fun setVoice(c: Context, v: String) = prefs(c).edit().putString("sf_voice", v.trim()).apply()

    /** 克隆：上传一段参考音频（≥10s 干净人声），成功返回音色 uri */
    fun clone(c: Context, audioUri: Uri, name: String): Pair<String?, String?> {
        val k = key(c)
        if (k.isBlank()) return Pair(null, "先填 SiliconFlow API Key")
        val r: Pair<String?, String?> = runCatching {
            val bytes = c.contentResolver.openInputStream(audioUri)?.use { it.readBytes() }
            if (bytes == null || bytes.size < 16_000) throw IllegalStateException(
                "音频太短/读不出来——录一段 ≥10 秒的干净人声")
            val boundary = "aion" + System.currentTimeMillis()
            val conn = (URL("$BASE/uploads/audio/voice").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Authorization", "Bearer $k")
                setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
                doOutput = true
                connectTimeout = 30000
                readTimeout = 120000
            }
            conn.outputStream.use { os ->
                fun field(name: String, value: String) {
                    os.write("--$boundary\r\n".toByteArray())
                    os.write("Content-Disposition: form-data; name=\"$name\"\r\n\r\n".toByteArray())
                    os.write(value.toByteArray()); os.write("\r\n".toByteArray())
                }
                field("model", MODEL)
                field("customName", "aion_" + name.take(20) + "_" + System.currentTimeMillis() / 1000)
                field("text", "这是我的声音克隆测试。")
                os.write("--$boundary\r\n".toByteArray())
                os.write("Content-Disposition: form-data; name=\"audio\"; filename=\"ref.wav\"\r\n".toByteArray())
                os.write("Content-Type: application/octet-stream\r\n\r\n".toByteArray())
                os.write(bytes); os.write("\r\n".toByteArray())
                os.write("--$boundary--\r\n".toByteArray())
            }
            val code = conn.responseCode
            val body = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.use { it.readText() } ?: ""
            if (code !in 200..299) throw IllegalStateException("HTTP $code：${body.take(200)}")
            val uri = JSONObject(body).optJSONObject("data")?.optString("uri").orEmpty()
            if (uri.isBlank()) throw IllegalStateException("响应里没有音色 uri：${body.take(200)}")
            Pair(uri, null)
        }.getOrElse { Pair(null, it.message?.take(200) ?: "上传失败") }
        return r
    }

    /** 合成一段文字 → 缓存 mp3 文件（阻塞 IO 线程调用） */
    fun synth(c: Context, text: String): File? {
        val k = key(c); val v = voice(c)
        if (k.isBlank() || v.isBlank()) return null
        return runCatching {
            val body = JSONObject()
                .put("model", MODEL)
                .put("input", text.take(500))
                .put("voice", v)
                .put("response_format", "mp3")
            val conn = (URL("$BASE/audio/tts").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Authorization", "Bearer $k")
                setRequestProperty("Content-Type", "application/json")
                doOutput = true
                connectTimeout = 15000
                readTimeout = 60000
            }
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            if (code !in 200..299) return null
            val out = File(c.cacheDir, "tts_" + System.currentTimeMillis() + ".mp3")
            conn.inputStream.use { ins -> FileOutputStreamCompat(out).use { it.write(ins.readBytes()) } }
            if (out.length() < 512) { runCatching { out.delete() }; return null }
            out
        }.getOrNull()
    }

    private object FileOutputStreamCompat {
        operator fun invoke(f: File): java.io.FileOutputStream = java.io.FileOutputStream(f)
    }
}
