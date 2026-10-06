package com.aion.chat.compose.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * 一起听歌 · 音乐后端客户端（服务器 music-api，经 VPS 中转 /music/）。
 * 只连自己的后端：搜索/音源/共同状态都走它，App 端无任何密钥（教程 §7/§22）。
 */
object MusicClient {

    private val BASES = arrayOf(
        SupabaseClient.RELAY_URL.removeSuffix("/supabase") + "/music",
        SupabaseClient.RELAY_URL_ALT.removeSuffix("/supabase") + "/music"
    )

    @Volatile var lastGoodBase: String? = null

    data class Song(
        val id: Long,
        val name: String,
        val artist: String,
        val album: String = "",
        val durationMs: Long = 0,
        val fee: Int = 0
    )

    private fun bases(): Array<String> =
        lastGoodBase?.let { arrayOf(it, *BASES.filter { b -> b != it }.toTypedArray()) } ?: BASES

    /** 搜索。返回 (歌曲列表, 错误文案)；成功时错误为 null。 */
    suspend fun search(q: String, limit: Int = 20): Pair<List<Song>, String?> = withContext(Dispatchers.IO) {
        val eq = URLEncoder.encode(q, "UTF-8")
        for (base in bases()) {
            try {
                val conn = URL("$base/api/music/search?q=$eq&limit=$limit").openConnection() as HttpURLConnection
                conn.connectTimeout = 8_000
                conn.readTimeout = 20_000
                if (conn.responseCode !in 200..299) continue
                val o = JSONObject(conn.inputStream.bufferedReader().readText())
                if (!o.optBoolean("ok")) continue
                lastGoodBase = base
                val arr = o.optJSONArray("songs") ?: JSONArray()
                val list = (0 until arr.length()).map { i ->
                    val s = arr.getJSONObject(i)
                    Song(
                        id = s.optLong("id"),
                        name = s.optString("name"),
                        artist = s.optString("artist"),
                        album = s.optString("album"),
                        durationMs = s.optLong("duration"),
                        fee = s.optInt("fee")
                    )
                }.filter { it.id > 0 }
                return@withContext list to null
            } catch (e: Exception) { /* 换线路 */ }
        }
        emptyList<Song>() to "音乐服务暂时无法连接"
    }

    /** 音频流地址（后端代理，支持 Range）。 */
    fun streamUrl(songId: Long, smooth: Boolean = false): String? {
        val base = lastGoodBase ?: BASES[0]
        return "$base/api/music/stream/$songId" + if (smooth) "?smooth=true" else ""
    }

    /** 共同状态（当前歌曲/最近事件）。 */
    suspend fun getState(): JSONObject? = withContext(Dispatchers.IO) {
        for (base in bases()) {
            try {
                val conn = URL("$base/api/music/session").openConnection() as HttpURLConnection
                conn.connectTimeout = 8_000
                conn.readTimeout = 15_000
                if (conn.responseCode in 200..299) {
                    lastGoodBase = base
                    return@withContext JSONObject(conn.inputStream.bufferedReader().readText())
                }
            } catch (e: Exception) { }
        }
        null
    }

    /** 上报播放事件（play/pause/skip/finish/close，教程 §8.3）。失败静默——状态下次播放会覆盖。 */
    suspend fun postEvent(eventType: String, actor: String, song: Song?) = withContext(Dispatchers.IO) {
        val base = lastGoodBase ?: BASES[0]
        try {
            val body = JSONObject()
                .put("event_type", eventType)
                .put("actor", actor)
                .put("song", JSONObject().put("id", song?.id ?: 0).put("name", song?.name ?: "").put("artist", song?.artist ?: ""))
            val conn = URL("$base/api/music/session/event").openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.connectTimeout = 8_000
            conn.readTimeout = 15_000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.outputStream.use { it.write(body.toString().toByteArray()) }
            conn.responseCode in 200..299
        } catch (e: Exception) { false }
    }
}
