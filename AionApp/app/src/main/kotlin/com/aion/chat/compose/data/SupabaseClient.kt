package com.aion.chat.compose.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException

/**
 * Supabase REST 客户端（anon key），不引第三方 SDK。
 *
 * 线路策略：大陆宽带/蜂窝直连 supabase.co（Cloudflare 线路）常被 TLS 拦截，
 * 所以先走自家 VPS 中转（nginx 只转发这个 Supabase 项目），失败再直连兜底。
 *
 * 错误分类（不混淆）：NETWORK（超时/DNS/TLS）/ HTTP_401 / HTTP_403 / HTTP_404 /
 * OTHER_HTTP / PARSE（字段解析失败）。每一类都打进 logcat（tag=SupabaseClient）。
 */
object SupabaseClient {

    internal const val TAG = "SupabaseClient"

    const val URL = "https://byqqwypdfiwvalozihgs.supabase.co"
    const val ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImJ5cXF3eXBkZml3dmFsb3ppaGdzIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODM2NTQwODAsImV4cCI6MjA5OTIzMDA4MH0.Gacxi6TVGzL3pNn-KdUHkPTYW8dvSpt7A05FpmkZlyc"

    /** VPS 中转（nginx 18443 → /supabase/，只转发本项目）。端口需在云控制台安全组放行。 */
    const val RELAY_URL = "http://134.175.7.196:18443/supabase"

    internal val BASES = arrayOf(RELAY_URL, URL)

    val configured: Boolean get() = URL.isNotBlank() && ANON_KEY.isNotBlank()
}

/** 云端错误种类：界面据此区分「网络不通」和「权限没放行」，不再混成一句"连不上"。 */
enum class CloudErrorKind {
    NETWORK,      // 连接超时 / DNS / TLS 被拦
    HTTP_401,     // 未授权（通常是 RLS 没给 anon 放行 SELECT）
    HTTP_403,     // 拒绝访问（RLS 策略拒绝）
    HTTP_404,     // 表/路径不存在
    OTHER_HTTP,   // 其他 HTTP 状态
    PARSE         // JSON 字段解析失败（表结构和代码假设不一致）
}

/** 读取结果：error==null 时 data 一定非 null（空列表=云端确实没数据，不是错误）。 */
data class CloudResult<T>(val data: T?, val error: CloudErrorKind?)

/** 一次请求的内部结论。 */
internal class CloudReply(
    val payload: Any? = null,
    val error: CloudErrorKind? = null
)

/** 底层请求（中转 → 直连）。网络错误才换线路；4xx 语义性失败换线结果一样，直接终止。 */
internal suspend fun supabaseRequest(
    path: String,
    method: String,
    body: ByteArray? = null,
    contentType: String = "application/json",
    parse: ((HttpURLConnection) -> Any?)? = null
): CloudReply = withContext(Dispatchers.IO) {
    var lastError: CloudErrorKind? = null
    for (base in SupabaseClient.BASES) {
        try {
            val conn = URL("$base/$path").openConnection() as HttpURLConnection
            conn.requestMethod = method
            conn.setRequestProperty("apikey", SupabaseClient.ANON_KEY)
            conn.setRequestProperty("Authorization", "Bearer " + SupabaseClient.ANON_KEY)
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
                Log.i(SupabaseClient.TAG, "[OK] $method /$path ($code)")
                if (parse != null) {
                    return@withContext try {
                        CloudReply(parse(conn))
                    } catch (e: Exception) {
                        Log.w(SupabaseClient.TAG, "[PARSE] $method /$path 解析失败: ${e.message}")
                        CloudReply(error = CloudErrorKind.PARSE)
                    }
                }
                return@withContext CloudReply(true)
            }
            val kind = when (code) {
                401 -> CloudErrorKind.HTTP_401
                403 -> CloudErrorKind.HTTP_403
                404 -> CloudErrorKind.HTTP_404
                else -> CloudErrorKind.OTHER_HTTP
            }
            Log.w(SupabaseClient.TAG, "[HTTP $code] $method /$path -> $kind" +
                " body=" + runCatching { conn.errorStream?.bufferedReader()?.readText()?.take(200) }.getOrNull())
            // 权限/表结构问题是线路无关的，换线也一样
            return@withContext CloudReply(error = kind)
        } catch (e: UnknownHostException) {
            Log.w(SupabaseClient.TAG, "[NET/DNS] $method $base/$path 域名解析失败: ${e.message}")
            lastError = CloudErrorKind.NETWORK
        } catch (e: SocketTimeoutException) {
            Log.w(SupabaseClient.TAG, "[NET/TIMEOUT] $method $base/$path 连接或读取超时")
            lastError = CloudErrorKind.NETWORK
        } catch (e: javax.net.ssl.SSLException) {
            Log.w(SupabaseClient.TAG, "[NET/TLS] $method $base/$path TLS 握手被拦: ${e.message?.take(120)}")
            lastError = CloudErrorKind.NETWORK
        } catch (e: Exception) {
            Log.w(SupabaseClient.TAG, "[NET] $method $base/$path ${e.javaClass.simpleName}: ${e.message?.take(120)}")
            lastError = CloudErrorKind.NETWORK
        }
    }
    CloudReply(error = lastError ?: CloudErrorKind.NETWORK)
}

/** 把 PostgREST 返回的 text[]（JSON 数组）安全转 List<String>；兼容 "{a,b}" 字符串形态。 */
internal fun jsonStringArray(raw: Any?): List<String> = when (raw) {
    is JSONArray -> (0 until raw.length()).map { raw.optString(it) }.filter { it.isNotBlank() }
    is String -> raw.removePrefix("{").removeSuffix("}")
        .split(",").map { it.trim().trim('"') }.filter { it.isNotBlank() }
    else -> emptyList()
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

/**
 * 朋友圈 + 日记 Supabase 数据层（第一阶段：只读同步 + 发布走 images 数组）。
 * - moments 真实字段：id/author/content/context_note/reply_status/liked/reply_content/
 *   replied_at/yuri_liked/created_at/reply_due_at/images(text[])。没有 image_url。
 * - 日记只读 user_id=ai_哥哥（哥哥的），不混其他人的。
 */
object SupabaseMomentsStore {

    data class RemoteMoment(
        val id: String,
        val author: String,
        val content: String,
        val imageUrls: List<String>,
        val createdAt: String,
        val createdAtMs: Long,
        val liked: Boolean,          // 哥哥点过赞（旧版字段语义）
        val yuriLiked: Boolean,      // Yuri 点过赞
        val replyStatus: String,     // 回应状态
        val replyContent: String     // 哥哥的回应内容（有就当 Sean 评论展示）
    )

    data class RemoteDiary(
        val id: String,
        val userId: String,
        val title: String,
        val content: String,
        val weather: String,
        val mood: String,
        val tags: String,
        val isPrivate: Boolean,
        val createdAt: String,
        val createdAtMs: Long
    )

    data class RemoteComment(
        val id: String,
        val momentId: String,
        val author: String,
        val content: String,
        val createdAtMs: Long
    )

    fun mapAuthor(raw: String): String = when {
        raw.contains("沈聿淮") || raw == "sean" || raw == "a_哥哥" || raw.contains("ai_") -> "sean"
        raw.contains("小鑫") || raw == "yuri" || raw == "user" -> "yuri"
        else -> raw
    }

    fun displayName(author: String): String = when (author) {
        "sean" -> "Sean"; "yuri" -> "Yuri"; else -> author
    }

    /** 读朋友圈动态。查询列按真实表结构写死，不 select *。 */
    suspend fun fetchMoments(): CloudResult<List<RemoteMoment>> {
        val reply = supabaseRequest(
            "rest/v1/moments",
            "GET",
            parse = { conn ->
                JSONArray(conn.inputStream.bufferedReader().readText())
            }
        )
        if (reply.error != null) return CloudResult(null, reply.error)
        val arr = reply.payload as? JSONArray
            ?: return CloudResult(null, CloudErrorKind.PARSE)
        if (arr.length() == 0) {
            Log.i(SupabaseClient.TAG, "[EMPTY] moments 返回空数组（云端没数据）")
            return CloudResult(emptyList(), null)
        }
        val out = mutableListOf<RemoteMoment>()
        for (i in 0 until arr.length()) {
            try {
                val o = arr.getJSONObject(i)
                val created = o.optString("created_at", "")
                out.add(
                    RemoteMoment(
                        id = o.optString("id"),
                        author = mapAuthor(o.optString("author", "")),
                        content = o.optString("content", ""),
                        imageUrls = jsonStringArray(o.opt("images")),
                        createdAt = created,
                        createdAtMs = parseSupabaseTime(created),
                        liked = o.optBoolean("liked", false),
                        yuriLiked = o.optBoolean("yuri_liked", false),
                        replyStatus = o.optString("reply_status", ""),
                        replyContent = o.optString("reply_content", "")
                    )
                )
            } catch (e: Exception) {
                Log.w(SupabaseClient.TAG, "[PARSE] moments 第 $i 行字段解析失败: ${e.message}")
            }
        }
        return CloudResult(out, null)
    }

    /** 读哥哥的日记（user_id=eq.ai_哥哥），查询列按真实表结构写死。 */
    suspend fun fetchSeanDiaries(): CloudResult<List<RemoteDiary>> {
        val reply = supabaseRequest(
            "rest/v1/diary_entries?user_id=" + java.net.URLEncoder.encode("eq.ai_哥哥", "UTF-8") +
                "&select=id,user_id,title,content,weather,mood,tags,private,created_at" +
                "&order=created_at.desc&limit=50",
            "GET",
            parse = { conn -> JSONArray(conn.inputStream.bufferedReader().readText()) }
        )
        if (reply.error != null) return CloudResult(null, reply.error)
        val arr = reply.payload as? JSONArray
            ?: return CloudResult(null, CloudErrorKind.PARSE)
        if (arr.length() == 0) {
            Log.i(SupabaseClient.TAG, "[EMPTY] diary_entries(ai_哥哥) 返回空数组（云端没数据）")
            return CloudResult(emptyList(), null)
        }
        val out = mutableListOf<RemoteDiary>()
        for (i in 0 until arr.length()) {
            try {
                val o = arr.getJSONObject(i)
                val created = o.optString("created_at", "")
                out.add(
                    RemoteDiary(
                        id = o.optString("id"),
                        userId = o.optString("user_id", ""),
                        title = o.optString("title", ""),
                        content = o.optString("content", ""),
                        weather = o.optString("weather", ""),
                        mood = o.optString("mood", ""),
                        tags = jsonStringArray(o.opt("tags")).joinToString("、").ifBlank { o.optString("tags", "") },
                        isPrivate = o.optBoolean("private", false),
                        createdAt = created,
                        createdAtMs = parseSupabaseTime(created)
                    )
                )
            } catch (e: Exception) {
                Log.w(SupabaseClient.TAG, "[PARSE] diary_entries 第 $i 行字段解析失败: ${e.message}")
            }
        }
        return CloudResult(out, null)
    }

    /**
     * 读朋友圈评论（独立表 moment_comments，不假设 moments 自带）。
     * 列结构没有交接文档，防御式解析：author 兼容 user_id 列、content 兼容 text 列。
     * 失败时返回 CloudResult(error)——调用方对评论失败应静默降级，不影响动态主列表。
     */
    suspend fun fetchMomentComments(): CloudResult<List<RemoteComment>> {
        val reply = supabaseRequest(
            "rest/v1/moment_comments?select=*&order=created_at.asc&limit=200",
            "GET",
            parse = { conn -> JSONArray(conn.inputStream.bufferedReader().readText()) }
        )
        if (reply.error != null) return CloudResult(null, reply.error)
        val arr = reply.payload as? JSONArray
            ?: return CloudResult(null, CloudErrorKind.PARSE)
        if (arr.length() == 0) {
            Log.i(SupabaseClient.TAG, "[EMPTY] moment_comments 返回空数组（云端没评论）")
            return CloudResult(emptyList(), null)
        }
        val out = mutableListOf<RemoteComment>()
        for (i in 0 until arr.length()) {
            try {
                val o = arr.getJSONObject(i)
                val created = o.optString("created_at", "")
                out.add(
                    RemoteComment(
                        id = o.optString("id"),
                        momentId = o.optString("moment_id", o.optString("momentid", "")),
                        author = mapAuthor(
                            o.optString("author", o.optString("user_id", ""))
                        ),
                        content = o.optString("content", o.optString("text", "")),
                        createdAtMs = parseSupabaseTime(created)
                    )
                )
            } catch (e: Exception) {
                Log.w(SupabaseClient.TAG, "[PARSE] moment_comments 第 $i 行字段解析失败: ${e.message}")
            }
        }
        return CloudResult(out, null)
    }

    /**
     * 发朋友圈（只写 Yuri 自己的动态）。图片列固定写 images 数组（text[]），没有 image_url。
     * imageUrls 是 Storage 公网地址；上传不成功就传空数组（配图只在本地留档）。
     */
    suspend fun postMoment(content: String, author: String, imageUrls: List<String> = emptyList()): Boolean {
        val body = JSONObject().put("author", author).put("content", content)
        if (imageUrls.isNotEmpty()) body.put("images", JSONArray(imageUrls))
        val reply = supabaseRequest(
            "rest/v1/moments", "POST", body.toString().toByteArray(Charsets.UTF_8)
        )
        return reply.error == null
    }
}

/** Supabase Storage（朋友圈配图）。桶 moments 不存在/无权限时返回 null，调用方降级为仅本地保存。 */
object SupabaseStorage {

    suspend fun uploadMomentImage(bytes: ByteArray): String? {
        val name = "m_" + System.currentTimeMillis() + "_" + (0..999).random() + ".jpg"
        val reply = supabaseRequest(
            "storage/v1/object/moments/$name", "POST", bytes, "image/jpeg"
        )
        // 表里存直连规范地址；展示端下载时中转/直连两条线路都会试
        return if (reply.error == null) {
            SupabaseClient.URL + "/storage/v1/object/public/moments/" + name
        } else null
    }
}

/**
 * 情话远程同步——【暂缓】：当前数据库没有 home_quote 表，发起请求必然 404。
 * 保留接口占位但不发网络请求：pull 恒 null（回落本地情话），push 恒 false（只存本地）。
 * 等表建好再把实现换回真实请求。
 */
object SupabaseQuoteSync {
    val configured: Boolean get() = false

    suspend fun pull(): String? = null

    suspend fun push(content: String): Boolean = false
}
