package com.aion.chat.compose.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * 服务器上「心潮 · Xinchao」后端的客户端（ Yuri 的情绪/梦境老家，端口 8080）。
 * 线路策略与 Supabase 相同：VPS 中转（/xinchao/ → 127.0.0.1:8080）优先，直连兜底。
 * 接口：GET /api/state（情绪驱动+意识状态）、/api/dreams（模型生成的梦）、/api/arc（心语弧线）。
 */
object XinchaoClient {

    private val BASES = arrayOf(
        "http://134.175.7.196:80/xinchao",
        "http://134.175.7.196:443/xinchao",
        SupabaseClient.RELAY_URL.removeSuffix("/supabase") + "/xinchao",
        SupabaseClient.RELAY_URL_ALT.removeSuffix("/supabase") + "/xinchao",
        "http://134.175.7.196:8080"
    )

    data class Drive(val key: String, val zh: String, val v: Float)

    data class RemoteState(
        val consciousness: String,
        val fatigue: Double,
        val drives: List<Drive>
    )

    data class RemoteDream(
        val dream: String,
        val awareness: String,
        val createdAt: String
    )

    data class ArcEntry(
        val text: String,
        val time: String,
        val driveZh: String
    )

    /** GET，中转优先直连兜底；任何一条线路失败（含 4xx——可能只是那条中转配置不对）都换下一条。 */
    private suspend fun getJson(path: String): JSONObject? = withContext(Dispatchers.IO) {
        for (base in BASES) {
            val t0 = android.os.SystemClock.elapsedRealtime()
            try {
                val conn = URL("$base$path").openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.connectTimeout = 8_000
                conn.readTimeout = 20_000
                val code = conn.responseCode
                if (code in 200..299) {
                    RequestLog.add(null, "GET", "$base$path", code, android.os.SystemClock.elapsedRealtime() - t0)
                    return@withContext JSONObject(conn.inputStream.bufferedReader().readText())
                }
                RequestLog.add(null, "GET", "$base$path", code, android.os.SystemClock.elapsedRealtime() - t0)
                android.util.Log.w("Xinchao", "GET $path @ $base -> HTTP $code，换下一条")
            } catch (e: Exception) {
                RequestLog.add(null, "GET", "$base$path", 0, android.os.SystemClock.elapsedRealtime() - t0, e.javaClass.simpleName)
                android.util.Log.w("Xinchao", "GET $path @ $base -> ${e.javaClass.simpleName}，换下一条")
            }
        }
        null
    }

    private suspend fun getArray(path: String): org.json.JSONArray? = withContext(Dispatchers.IO) {
        for (base in BASES) {
            val t0 = android.os.SystemClock.elapsedRealtime()
            try {
                val conn = URL("$base$path").openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.connectTimeout = 8_000
                conn.readTimeout = 20_000
                val code = conn.responseCode
                if (code in 200..299) {
                    RequestLog.add(null, "GET", "$base$path", code, android.os.SystemClock.elapsedRealtime() - t0)
                    return@withContext org.json.JSONArray(conn.inputStream.bufferedReader().readText())
                }
                RequestLog.add(null, "GET", "$base$path", code, android.os.SystemClock.elapsedRealtime() - t0)
                android.util.Log.w("Xinchao", "GET $path @ $base -> HTTP $code，换下一条")
            } catch (e: Exception) {
                RequestLog.add(null, "GET", "$base$path", 0, android.os.SystemClock.elapsedRealtime() - t0, e.javaClass.simpleName)
                android.util.Log.w("Xinchao", "GET $path @ $base -> ${e.javaClass.simpleName}，换下一条")
            }
        }
        null
    }

    /** 心潮状态（情绪驱动值 + 意识状态 + 疲劳度）。null = 云端读不到。 */
    suspend fun fetchState(): RemoteState? {
        val o = getJson("/api/state") ?: return null
        return try {
            val drivesJson = o.optJSONObject("drives") ?: JSONObject()
            val drives = drivesJson.keys().asSequence().map { key ->
                val d = drivesJson.optJSONObject(key) ?: JSONObject()
                Drive(key, d.optString("z", key), d.optDouble("v", 0.0).toFloat())
            }.sortedByDescending { it.v }.toList()
            RemoteState(
                consciousness = o.optString("consciousness", "unknown"),
                fatigue = o.optDouble("fatigue", 0.0),
                drives = drives
            )
        } catch (e: Exception) { null }
    }

    /** 模型做的梦（新→旧）。 */
    suspend fun fetchDreams(): List<RemoteDream>? {
        val arr = getArray("/api/dreams") ?: return null
        return try {
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                RemoteDream(
                    dream = o.optString("dream", ""),
                    awareness = o.optString("awareness", ""),
                    createdAt = o.optString("createdAt", "")
                )
            }.filter { it.dream.isNotBlank() }
        } catch (e: Exception) { emptyList() }
    }

    /** 心语弧线（情绪弧：一段一段的心里话）。 */
    suspend fun fetchArc(): List<ArcEntry>? {
        val arr = getArray("/api/arc") ?: return null
        return try {
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                ArcEntry(
                    text = o.optString("text", ""),
                    time = o.optString("time", ""),
                    driveZh = o.optString("zh", o.optString("drive", ""))
                )
            }.filter { it.text.isNotBlank() }
        } catch (e: Exception) { emptyList() }
    }
    /**
         * 经服务器心潮后端的只读代理读朋友圈（/api/moments_proxy）。
         * 用途：Supabase RLS 未放行 anon 读 moments 时的兜底——服务端密钥不出服务器，App 端无任何密钥。
         */
    /** Yuri 点赞写回（只置 yuri_liked=true，窄端点）。best-effort，失败不阻塞本地点赞。 */
    /** 相册图片窄代理（服务器持 key 读 storage album 桶，App 不碰 key）。 */
    fun proxyAlbumPhoto(name: String): ByteArray? {
        for (base in BASES) {
            try {
                val conn = URL("$base/api/album_photo?name=" + java.net.URLEncoder.encode(name, "UTF-8"))
                    .openConnection() as HttpURLConnection
                conn.connectTimeout = 8_000
                conn.readTimeout = 30_000
                if (conn.responseCode == 200) return conn.inputStream.readBytes()
                if (conn.responseCode in 400..499) return null
            } catch (e: Exception) { }
        }
        return null
    }

    suspend fun likeMomentViaXinchao(momentId: String, on: Boolean = true): Boolean = withContext(Dispatchers.IO) {
        for (base in BASES) {
            try {
                val conn = URL("$base/api/moments_proxy/like").openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.connectTimeout = 8_000
                conn.readTimeout = 15_000
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json")
                conn.outputStream.use {
                    it.write(org.json.JSONObject().put("id", momentId).put("on", on).toString().toByteArray())
                }
                if (conn.responseCode in 200..299) return@withContext true
            } catch (e: Exception) { }
        }
        false
    }

    suspend fun fetchMomentsViaXinchao(): List<SupabaseMomentsStore.RemoteMoment>? {
        val arr = getArray("/api/moments_proxy") ?: return null
        return try {
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                val created = o.optString("created_at", "")
                SupabaseMomentsStore.RemoteMoment(
                    id = o.optString("id"),
                    author = SupabaseMomentsStore.mapAuthor(o.optString("author", "")),
                    content = o.optString("content", ""),
                    imageUrls = jsonStringArray(o.opt("images")),
                    createdAt = created,
                    createdAtMs = parseSupabaseTime(created),
                    liked = o.optBoolean("liked", false),
                    yuriLiked = o.optBoolean("yuri_liked", false),
                    replyStatus = o.optString("reply_status", ""),
                    replyContent = o.optString("reply_content", "")
                )
            }
        } catch (e: Exception) { null }
    }
}
