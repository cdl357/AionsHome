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
        SupabaseClient.RELAY_URL.removeSuffix("/supabase") + "/xinchao",
        SupabaseClient.RELAY_URL_ALT.removeSuffix("/supabase") + "/xinchao",
        "http://134.175.7.196:8080"
    )

    data class Drive(val zh: String, val v: Float)

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

    /** GET，中转优先直连兜底；网络异常换线路，4xx 直接终止。 */
    private suspend fun getJson(path: String): JSONObject? = withContext(Dispatchers.IO) {
        for (base in BASES) {
            try {
                val conn = URL("$base$path").openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.connectTimeout = 8_000
                conn.readTimeout = 20_000
                val code = conn.responseCode
                if (code in 200..299) {
                    return@withContext JSONObject(conn.inputStream.bufferedReader().readText())
                }
                if (code in 400..499) return@withContext null
            } catch (e: Exception) { /* 这条线路不通 → 试下一条 */ }
        }
        null
    }

    private suspend fun getArray(path: String): org.json.JSONArray? = withContext(Dispatchers.IO) {
        for (base in BASES) {
            try {
                val conn = URL("$base$path").openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.connectTimeout = 8_000
                conn.readTimeout = 20_000
                val code = conn.responseCode
                if (code in 200..299) {
                    return@withContext org.json.JSONArray(conn.inputStream.bufferedReader().readText())
                }
                if (code in 400..499) return@withContext null
            } catch (e: Exception) { }
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
                Drive(d.optString("z", key), d.optDouble("v", 0.0).toFloat())
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
