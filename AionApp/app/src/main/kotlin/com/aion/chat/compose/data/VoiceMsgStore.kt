package com.aion.chat.compose.data

import android.content.Context
import android.media.MediaPlayer
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

/**
 * Sean 回复的语音条（v0.3.154）：
 * - 回复完成 → 用 ElevenLabs 克隆音色合成 mp3 → files/voice_msgs/<msgId>.mp3
 * - 注册表 JSON：messageId → {file, durationMs}（内存缓存，写盘落 JSON）
 * - VoiceMsgPlayer：全局单播放器（播新停旧），playingId 是 Compose 状态供气泡切图标
 * - 保留最近 50 条
 */
object VoiceMsgStore {
    private const val PREFS = "voice_msg"
    private const val FILE = "voice_msg_index.json"
    private const val KEEP = 50

    /** 语音条增删的版本号（气泡观察它触发重组） */
    val rev = mutableIntStateOf(0)

    data class Entry(val messageId: String, val file: String, val durationMs: Long)

    @Volatile private var cache: MutableMap<String, Entry>? = null

    private fun file(c: Context) = File(c.filesDir, FILE)
    private fun dir(c: Context) = File(c.filesDir, "voice_msgs").apply { mkdirs() }
    private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun map(c: Context): MutableMap<String, Entry> {
        val m = cache
        if (m != null) return m
        val loaded = runCatching {
            val o = JSONObject(file(c).takeIf { it.isFile }?.readText() ?: "{}")
            val out = mutableMapOf<String, Entry>()
            o.keys().forEach { k ->
                val e = o.getJSONObject(k)
                out[k] = Entry(k, e.optString("file"), e.optLong("durationMs"))
            }
            out
        }.getOrDefault(mutableMapOf())
        cache = loaded
        return loaded
    }

    private fun persist(c: Context) {
        val m = map(c)
        runCatching {
            val o = JSONObject()
            m.forEach { (k, e) -> o.put(k, JSONObject().put("file", e.file).put("durationMs", e.durationMs)) }
            file(c).writeText(o.toString())
        }
    }

    fun get(c: Context, messageId: String): Entry? = map(c)[messageId]

    fun autoEnabled(c: Context): Boolean = prefs(c).getBoolean("auto_voice_msg", false)

    fun setAutoEnabled(c: Context, on: Boolean) = prefs(c).edit().putBoolean("auto_voice_msg", on).apply()

    /** 回复完成后调用：合成 + 注册（阻塞 IO 线程）。返回时长 ms 或 null（未配置/失败） */
    suspend fun attach(c: Context, messageId: String, text: String): Long? = withContext(Dispatchers.IO) {
        if (!autoEnabled(c) || !VoiceStore.configured(c)) return@withContext null
        val clean = text.trim()
        if (clean.isBlank()) return@withContext null
        val f = VoiceStore.synth(c, clean) ?: return@withContext null
        val target = File(dir(c), "$messageId.mp3")
        runCatching { f.renameTo(target) }.onFailure { f.copyTo(target, overwrite = true); f.delete() }
        val dur = runCatching {
            val mp = MediaPlayer()
            mp.setDataSource(target.absolutePath)
            mp.prepare()
            val d = mp.duration.toLong()
            mp.release()
            d
        }.getOrDefault(0L)
        synchronized(this@VoiceMsgStore) {
            map(c)[messageId] = Entry(messageId, target.absolutePath, dur)
            val keys = map(c).keys.toList()
            if (keys.size > KEEP) {
                keys.take(keys.size - KEEP).forEach { old ->
                    runCatching { map(c)[old]?.file?.let { File(it).delete() } }
                    map(c).remove(old)
                }
            }
            persist(c)
        }
        rev.intValue++
        dur
    }
}

/** 全局单播放器：播新停旧；playingId 是 Compose 状态供气泡切图标 */
object VoiceMsgPlayer {
    val playingId = mutableStateOf<String?>(null)
    private var mp: MediaPlayer? = null

    fun toggle(c: Context, messageId: String) {
        if (playingId.value == messageId) { stop(); return }
        stop()
        val e = VoiceMsgStore.get(c, messageId) ?: return
        val f = File(e.file)
        if (!f.isFile) return
        runCatching {
            val p = MediaPlayer()
            p.setDataSource(f.absolutePath)
            p.setOnCompletionListener {
                it.release()
                mp = null
                playingId.value = null
            }
            p.prepare()
            p.start()
            mp = p
            playingId.value = messageId
        }
    }

    fun stop() {
        runCatching { mp?.stop(); mp?.release() }
        mp = null
        playingId.value = null
    }
}
