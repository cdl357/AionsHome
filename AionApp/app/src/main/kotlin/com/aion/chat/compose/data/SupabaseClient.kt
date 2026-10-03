package com.aion.chat.compose.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Supabase REST 客户端（anon key）。
 * 所有远端数据（朋友圈/日记/情话）统一走这里，不引第三方 SDK。
 * 线路策略：大陆宽带/蜂窝直连 supabase.co（Cloudflare 线路）常被 TLS 拦截，
 * 所以先走自家 VPS 中转（nginx 只转发这个 Supabase 项目），失败再直连兜底。
 */
object SupabaseClient {

    const val URL = "https://byqqwypdfiwvalozihgs.supabase.co"
    const val ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImJ5cXF3eXBkZml3dmFsb3ppaGdzIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODM2NTQwODAsImV4cCI6MjA5OTIzMDA4MH0.Gacxi6TVGzL3pNn-KdUHkPTYW8dvSpt7A05FpmkZlyc"

    /** VPS 中转（134.175.7.196，nginx 18443 → /supabase/）。端口要在云控制台安全组放行。 */
    const val RELAY_URL = "http://134.175.7.196:18443/supabase"

    private val BASES = arrayOf(RELAY_URL, URL)

    val configured: Boolean get() = URL.isNotBlank() && ANON_KEY.isNotBlank()

    /**
     * 依次尝试中转 → 直连。网络异常才换下一条线路；
     * 4xx（RLS 拦截/表不存在/冲突）换线结果一样，直接终止返回 null。
     */
    internal suspend fun request(
        path: String,
        method: String,
        body: ByteArray? = null,
        contentType: String = "application/json",
        parse: ((HttpURLConnection) -> Any?)? = null
    ): Any? = withContext(Dispatchers.IO) {
        for (base in BASES) {
            try {
                val conn = URL("$base/$path").openConnection() as HttpURLConnection
                conn.requestMethod = method
                conn.setRequestProperty("apikey", ANON_KEY)
                conn.setRequestProperty("Authorization", "Bearer $ANON_KEY")
                if (body != null) {
                    conn.setRequestProperty("Content-Type", contentType)
                    conn.setRequestProperty("Prefer", "return=minimal")
                    conn.doOutput = true
                }
                conn.connectTimeout = 8_000
                conn.readTimeout = 20_000
                if (body != null) conn.outputStream.use { it.write(body) }
                val code = conn.responseCode
                if (code in 200..299) {
                    if (parse != null) return@withContext parse(conn) else return@withContext true
                }
                if (code in 400..499) return@withContext null
            } catch (e: Exception) { /* 这条线路不通 → 试下一条 */ }
        }
        null
    }

    /**
     * GET 请求。成功返回 JSONArray（可能为空数组＝表里真没数据）；
     * 失败（网络不通 / HTTP 非 2xx / RLS 拦截）返回 null —— 调用方据此区分「连不上」和「没数据」。
     */
    suspend fun get(table: String, query: String = ""): JSONArray? {
        val r = request(
            "rest/v1/$table" + (if (query.isNotBlank()) "?$query" else ""),
            "GET"
        ) { conn -> JSONArray(conn.inputStream.bufferedReader().readText()) }
        return r as? JSONArray
    }

    /** POST（插入一行）。 */
    suspend fun post(table: String, body: JSONObject): Boolean =
        request("rest/v1/$table", "POST", body.toString().toByteArray(Charsets.UTF_8)) != null

    /** PATCH（更新）。 */
    suspend fun patch(table: String, filter: String, body: JSONObject): Boolean =
        request("rest/v1/$table?$filter", "PATCH", body.toString().toByteArray(Charsets.UTF_8)) != null

    /** DELETE（按过滤条件删行）。 */
    suspend fun delete(table: String, filter: String): Boolean =
        request("rest/v1/$table?$filter", "DELETE") != null
}

/** Supabase Storage（朋友圈配图）。桶 moments 不存在/无权限时返回 null，调用方降级为仅本地保存。 */
object SupabaseStorage {

    suspend fun uploadMomentImage(bytes: ByteArray): String? {
        val name = "m_" + System.currentTimeMillis() + "_" + (0..999).random() + ".jpg"
        val ok = SupabaseClient.request(
            "storage/v1/object/moments/$name", "POST", bytes, "image/jpeg"
        ) != null
        // 表里存直连规范地址；展示端下载时中转/直连两条线路都会试
        return if (ok) SupabaseClient.URL + "/storage/v1/object/public/moments/" + name else null
    }
}

/** Supabase 返回的 ISO 时间（2026-10-02T15:04:05.123+00:00 / Z / 空格分隔都兼容）转毫秒；失败返回 0。 */
fun parseSupabaseTime(iso: String): Long = try {
    val cleaned = iso.trim().replace(Regex("\\.\\d+"), "").replace(" ", "T")
    when {
        cleaned.endsWith("Z") -> {
            val f = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US)
            f.timeZone = java.util.TimeZone.getTimeZone("UTC")
            f.parse(cleaned)?.time ?: 0L
        }
        Regex("[+-]\\d{2}:?\\d{2}$").containsMatchIn(cleaned) ->
            java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", java.util.Locale.US)
                .parse(cleaned)?.time ?: 0L
        else -> {
            val f = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US)
            f.timeZone = java.util.TimeZone.getTimeZone("UTC")
            f.parse(cleaned)?.time ?: 0L
        }
    }
} catch (e: Exception) { 0L }

/** 朋友圈 + 日记 Supabase 数据层。 */
object SupabaseMomentsStore {

    data class RemoteMoment(
        val id: String, val author: String, val content: String,
        val imageUrl: String?, val createdAt: String, val createdAtMs: Long
    )

    data class RemoteDiary(
        val id: String, val userId: String, val title: String,
        val content: String, val mood: String, val createdAt: String, val createdAtMs: Long
    )

    fun mapAuthor(raw: String): String = when {
        raw.contains("沈聿淮") || raw == "sean" || raw == "a_哥哥" || raw.contains("ai_") -> "sean"
        raw.contains("小鑫") || raw == "yuri" || raw == "user" -> "yuri"
        else -> raw
    }

    fun displayName(author: String): String = when (author) {
        "sean" -> "Sean"; "yuri" -> "Yuri"; else -> author
    }

    /** 读朋友圈动态（created_at 倒序）。null＝云端连不上（网络/权限），空列表＝云端确实没数据。 */
    suspend fun fetchMoments(): List<RemoteMoment>? = withContext(Dispatchers.IO) {
        val arr = SupabaseClient.get("moments", "select=*&order=created_at.desc&limit=50")
            ?: return@withContext null
        try {
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                val created = o.optString("created_at", "")
                RemoteMoment(
                    id = o.optString("id"),
                    author = mapAuthor(o.optString("author", "")),
                    content = o.optString("content", ""),
                    imageUrl = o.optString("image_url", "").takeIf { it.isNotBlank() },
                    createdAt = created,
                    createdAtMs = parseSupabaseTime(created)
                )
            }
        } catch (e: Exception) { emptyList() }
    }

    /** 读哥哥的日记（user_id = ai_哥哥）。null＝云端连不上。 */
    suspend fun fetchSeanDiaries(): List<RemoteDiary>? = withContext(Dispatchers.IO) {
        val arr = SupabaseClient.get(
            "diary_entries",
            "user_id=eq.ai_哥哥&select=*&order=created_at.desc&limit=50"
        ) ?: return@withContext null
        try {
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                val created = o.optString("created_at", "")
                RemoteDiary(
                    id = o.optString("id"), userId = o.optString("user_id", ""),
                    title = o.optString("title", ""), content = o.optString("content", ""),
                    mood = o.optString("mood", ""), createdAt = created,
                    createdAtMs = parseSupabaseTime(created)
                )
            }
        } catch (e: Exception) { emptyList() }
    }

    /** 发朋友圈（写入 Supabase；imageUrl 为 Storage 公网地址，未上传成功时不填）。 */
    suspend fun postMoment(content: String, author: String, imageUrl: String = ""): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val body = JSONObject().put("author", author).put("content", content)
                if (imageUrl.isNotBlank()) body.put("image_url", imageUrl)
                SupabaseClient.post("moments", body)
            } catch (e: Exception) { false }
        }
}

/** SupabaseQuoteSync 使用同一 URL/Key，值从 SupabaseClient 读取。 */
object SupabaseQuoteSync {
    val configured: Boolean get() = SupabaseClient.configured

    suspend fun pull(): String? = withContext(Dispatchers.IO) {
        try {
            val arr = SupabaseClient.get("home_quote", "id=eq.1&select=content") ?: return@withContext null
            if (arr.length() > 0) arr.getJSONObject(0).optString("content", "") else null
        } catch (e: Exception) { null }
    }

    suspend fun push(content: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val body = JSONObject().put("id", 1).put("content", content)
            SupabaseClient.post("home_quote", body)
        } catch (e: Exception) { false }
    }
}
