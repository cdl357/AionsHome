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

    /** VPS 中转主线路：8783 端口（安全组已放行，外部实测 200）。 */
    const val RELAY_URL = "http://134.175.7.196:8783/supabase"

    /** 备用中转：18443（个别网络环境可能放行）。 */
    const val RELAY_URL_ALT = "http://134.175.7.196:18443/supabase"

    internal val BASES = arrayOf(RELAY_URL, RELAY_URL_ALT, URL)

    /** 最近一次成功的线路：下次直接从它开始，别每条请求都从第一条死线等满超时（同步慢的主因）。 */
    @JvmStatic @Volatile
    internal var lastGoodBase: String? = null

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
data class CloudResult<T>(val data: T?, val error: CloudErrorKind?, val detail: String? = null)

/** 一次请求的内部结论。detail = 最后一条线路的具体失败原因（给界面亮出来，别只有个 NETWORK）。 */
internal class CloudReply(
    val payload: Any? = null,
    val error: CloudErrorKind? = null,
    val detail: String? = null
)

/** 底层请求（中转 → 直连）。网络错误才换线路；4xx 语义性失败换线结果一样，直接终止。 */
private fun hostOf(base: String): String =
    runCatching { java.net.URL(base).host + ":" + java.net.URL(base).port }.getOrDefault(base.take(30))

internal suspend fun supabaseRequest(
    path: String,
    method: String,
    body: ByteArray? = null,
    contentType: String = "application/json",
    parse: ((HttpURLConnection) -> Any?)? = null,
    directFirst: Boolean = false
): CloudReply = withContext(Dispatchers.IO) {
    var lastError: CloudErrorKind? = null
    var lastDetail: String? = null
    val goodBase = SupabaseClient.lastGoodBase
    // directFirst：直连 supabase.co 提为第一顺位（她手机上橘瓣同款路径一直可达），
    // 中转线路降为兜底——不再让两个必挂的中转先耗 20 秒
    val ordered = if (directFirst) {
        (listOf(SupabaseClient.URL) + SupabaseClient.BASES.filter { it != SupabaseClient.URL }).distinct()
    } else {
        (listOfNotNull(goodBase) + SupabaseClient.BASES).distinct()
    }
    for (base in ordered) {
        val t0 = android.os.SystemClock.elapsedRealtime()
        val reqUrl = "$base/$path"
        try {
            val conn = URL(reqUrl).openConnection() as HttpURLConnection
            conn.requestMethod = method
            // 红线：App 内无 service_role key（memories 走服务器只读代理 /supa-svc/memories）
            val key = SupabaseClient.ANON_KEY
            conn.setRequestProperty("apikey", key)
            conn.setRequestProperty("Authorization", "Bearer " + key)
            if (body != null) {
                conn.setRequestProperty("Content-Type", contentType)
                conn.setRequestProperty("Prefer", "return=minimal")
                conn.doOutput = true
            }
            conn.connectTimeout = 10_000
            conn.readTimeout = 20_000
            if (body != null) conn.outputStream.use { it.write(body) }
            val code = conn.responseCode
            if (code in 200..299) {
                SupabaseClient.lastGoodBase = base
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
            val bodyText = runCatching { conn.errorStream?.bufferedReader()?.readText()?.take(200) }.getOrNull()
            Log.w(SupabaseClient.TAG, "[HTTP $code] $method /$path -> $kind body=$bodyText")
            RequestLog.add(null, method, reqUrl, code, android.os.SystemClock.elapsedRealtime() - t0, bodyText?.take(120) ?: "")
            lastDetail = "HTTP $code @ " + hostOf(base) + (bodyText?.take(60)?.let { " · $it" } ?: "")
            // 401/403 是线路无关的语义性失败（权限/表结构），换线也一样；4xx/5xx 都换线再试
            if (code in 401..403) return@withContext CloudReply(error = kind)
            lastError = kind
            continue
        } catch (e: UnknownHostException) {
            Log.w(SupabaseClient.TAG, "[NET/DNS] $method $base/$path 域名解析失败: ${e.message}")
            RequestLog.add(null, method, reqUrl, 0, android.os.SystemClock.elapsedRealtime() - t0, "DNS解析失败")
            lastError = CloudErrorKind.NETWORK
            lastDetail = hostOf(base) + " 域名解析失败"
        } catch (e: SocketTimeoutException) {
            Log.w(SupabaseClient.TAG, "[NET/TIMEOUT] $method $base/$path 连接或读取超时")
            RequestLog.add(null, method, reqUrl, 0, android.os.SystemClock.elapsedRealtime() - t0, "超时")
            lastError = CloudErrorKind.NETWORK
            lastDetail = hostOf(base) + " 超时（这条线路到不了）"
        } catch (e: javax.net.ssl.SSLException) {
            Log.w(SupabaseClient.TAG, "[NET/TLS] $method $base/$path TLS 握手被拦: ${e.message?.take(120)}")
            RequestLog.add(null, method, reqUrl, 0, android.os.SystemClock.elapsedRealtime() - t0, "TLS被拦")
            lastError = CloudErrorKind.NETWORK
            lastDetail = hostOf(base) + " TLS 被拦"
        } catch (e: Exception) {
            Log.w(SupabaseClient.TAG, "[NET] $method $base/$path ${e.javaClass.simpleName}: ${e.message?.take(120)}")
            RequestLog.add(null, method, reqUrl, 0, android.os.SystemClock.elapsedRealtime() - t0, e.javaClass.simpleName)
            lastError = CloudErrorKind.NETWORK
            lastDetail = hostOf(base) + " " + e.javaClass.simpleName
        }
    }
    CloudReply(error = lastError ?: CloudErrorKind.NETWORK, detail = lastDetail)
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
data class CloudMemory(
    val id: Long,
    val title: String,
    val content: String,
    val category: String,
    val tags: String,
    val createdAt: String,
    val importanceScore: Int,
    val recallCount: Int
)

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

    /** 她的云端记忆库（memories 表，service_role 全线路读取） */
    suspend fun fetchCloudMemories(): CloudResult<List<CloudMemory>> {
        // 走服务器只读代理（nginx 注入 service key，GET-only）——App 侧零特权密钥
        val reply = supabaseRequest(
            "supa-svc/memories?order=created_at.desc&limit=300",
            "GET",
            parse = { conn ->
                JSONArray(conn.inputStream.bufferedReader().readText())
            }
        )
        if (reply.error != null) return CloudResult(null, reply.error, reply.detail)
        val arr = reply.payload as? JSONArray
            ?: return CloudResult(null, CloudErrorKind.PARSE)
        val out = mutableListOf<CloudMemory>()
        for (i in 0 until arr.length()) {
            try {
                val o = arr.getJSONObject(i)
                out.add(
                    CloudMemory(
                        id = o.optLong("id"),
                        title = o.optString("title", ""),
                        content = o.optString("content", ""),
                        category = o.optString("category", ""),
                        tags = if (o.isNull("tags")) "" else o.optString("tags", ""),
                        createdAt = o.optString("created_at", ""),
                        importanceScore = o.optInt("importance_score", 0),
                        recallCount = o.optInt("recall_count", 0)
                    )
                )
            } catch (e: Exception) { }
        }
        return CloudResult(out, null)
    }

    fun mapAuthor(raw: String): String = when {
        raw.contains("沈聿淮") || raw == "sean" || raw == "a_哥哥" || raw.contains("ai_") -> "sean"
        raw.contains("小鑫") || raw == "yuri" || raw == "user" -> "yuri"
        else -> raw
    }

    fun displayName(author: String): String = when (author) {
        "sean" -> "Sean"; "yuri" -> "Yuri"; else -> author
    }

    // ── 橘瓣兼容模式：与她手机上一直正常的橘瓣朋友圈请求完全同参数 ──
    private const val ORANGE_URL = "https://byqqwypdfiwvalozihgs.supabase.co/rest/v1"
    private const val ORANGE_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImJ5cXF3eXBkZml3dmFsb3ppaGdzIiwicm9sZSI6InNlcnZpY2Vfcm9sZSIsImlhdCI6MTc4MzY1NDA4MCwiZXhwIjoyMDk5MjMwMDgwfQ.LIbE9DFsLSRhOig5bUUfUP4r7t1ykdNy8L0gZM_xtGw"

    private val IMG_REGEX = Regex("<!--imgs:(.*?)-->", RegexOption.DOT_MATCHES_ALL)

    private fun stripRawImages(raw: String): String = IMG_REGEX.replace(raw, "").trim()

    private fun parseInlineImages(raw: String): List<String> =
        IMG_REGEX.find(raw)?.groupValues?.get(1)
            ?.split("|")?.map { it.trim() }?.filter { it.isNotBlank() }
            ?: emptyList()

    private fun orangeConn(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("apikey", ORANGE_KEY)
            setRequestProperty("Authorization", "Bearer $ORANGE_KEY")
            setRequestProperty("Accept", "application/json")
            connectTimeout = 20000
            readTimeout = 20000
        }

    /** 服务器只读代理（v10 P0 改线第一顺位）：App 侧零 key，nginx 注入 service key。轮换后无需改 App */
    private fun fetchMomentsViaProxy(): List<RemoteMoment>? = runCatching {
        val conn = (URL("http://134.175.7.196:8783/supa-svc/moments?order=created_at.desc&limit=50")
            .openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("Accept", "application/json")
            connectTimeout = 20000
            readTimeout = 20000
        }
        val body = conn.inputStream.bufferedReader().use { it.readText() }
        parseMomentsArray(body)
    }.getOrNull()

    /** 橘瓣同款直连（v10 后降为兜底：key 轮换后此路失效，代理接管）：失败返回 null 交回线路链 */
    private fun fetchMomentsOrange(): List<RemoteMoment>? = runCatching {
        val body = orangeConn("$ORANGE_URL/moments?order=created_at.desc&limit=50")
            .inputStream.bufferedReader().use { it.readText() }
        parseMomentsArray(body)
    }.getOrNull()

    private fun parseMomentsArray(body: String): List<RemoteMoment> {
        val arr = JSONArray(body)
        return (0 until arr.length()).mapNotNull { i ->
            runCatching {
                val o = arr.getJSONObject(i)
                val rawContent = o.optString("content", "")
                RemoteMoment(
                    id = o.getString("id"),
                    author = mapAuthor(o.optString("author", "sean")),
                    content = stripRawImages(rawContent),
                    imageUrls = jsonStringArray(o.opt("images")).ifEmpty { parseInlineImages(rawContent) },
                    createdAt = o.optString("created_at", ""),
                    createdAtMs = parseSupabaseTime(o.optString("created_at", "")),
                    liked = o.optBoolean("liked", false),
                    yuriLiked = o.optBoolean("yuri_liked", false),
                    replyStatus = o.optString("reply_status", "done"),
                    replyContent = if (o.isNull("reply_content")) "" else o.optString("reply_content")
                )
            }.getOrNull()
        }.filter { it.content.isNotBlank() }
    }

    /** 读朋友圈动态。查询列按真实表结构写死，不 select *。 */
    suspend fun fetchMoments(): CloudResult<List<RemoteMoment>> {
        // ① 服务器只读代理（App 零 key；v10 P0 改线第一顺位）
        fetchMomentsViaProxy()?.let { list ->
            RequestLog.add(null, "GET", "supa-svc://moments", 200, 0, "", 0, 0)
            return CloudResult(list, null)
        }
        RequestLog.add(null, "GET", "supa-svc://moments", 0, 0, "代理失败，降橘瓣直连")
        // ② 橘瓣兼容直连（key 轮换后此路自动失效，代理接管）
        fetchMomentsOrange()?.let { orangeList ->
            RequestLog.add(null, "GET", "orange-direct://moments", 200, 0, "", 0, 0)
            return CloudResult(orangeList, null)
        }
        RequestLog.add(null, "GET", "orange-direct://moments", 0, 0, "橘瓣直连失败，走原线路")
        // ② 原有线路链（中转 → 直连 anon）
        val reply = supabaseRequest(
            "rest/v1/moments",
            "GET",
            parse = { conn ->
                JSONArray(conn.inputStream.bufferedReader().readText())
            }
        )
        if (reply.error != null) return CloudResult(null, reply.error, reply.detail)
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
        if (reply.error != null) return CloudResult(null, reply.error, reply.detail)
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

    /** 读相册表（album_photos）：老表列名没交接，select=* + 多候选列名防御式解析。 */
    suspend fun fetchAlbumPhotos(): CloudResult<List<CloudAlbumPhoto>> {
        val reply = supabaseRequest(
            "rest/v1/album_photos?select=*&order=created_at.asc&limit=300",
            "GET",
            parse = { conn -> JSONArray(conn.inputStream.bufferedReader().readText()) }
        )
        if (reply.error != null) return CloudResult(null, reply.error, reply.detail)
        val arr = reply.payload as? JSONArray
            ?: return CloudResult(null, CloudErrorKind.PARSE)
        val out = mutableListOf<CloudAlbumPhoto>()
        for (i in 0 until arr.length()) {
            try {
                val o = arr.getJSONObject(i)
                val id = o.optString("id", "row_" + i)
                // 真实列：storage_name = Storage 对象路径；caption/impression = Sean 的话；taken_at = 拍摄时间
                val image = listOf("storage_name", "storage_url", "image", "image_url", "url")
                    .firstNotNullOfOrNull { c -> o.optString(c, "").takeIf { it.isNotBlank() && it != "null" } } ?: ""
                val note = listOf("caption", "impression", "description", "note", "memo")
                    .firstNotNullOfOrNull { c -> o.optString(c, "").takeIf { it.isNotBlank() && it != "null" } } ?: ""
                val date = listOf("taken_at", "created_at", "date")
                    .firstNotNullOfOrNull { c -> o.optString(c, "").takeIf { it.isNotBlank() && it != "null" } } ?: ""
                if (image.isNotBlank()) {
                    out.add(CloudAlbumPhoto(id, image, note, date))
                }
            } catch (e: Exception) {
                Log.w(SupabaseClient.TAG, "[PARSE] album_photos 第 $i 行字段解析失败: ${e.message}")
            }
        }
        return CloudResult(out, null)
    }

    /** 云端相册行（image 可能是 base64 / dataURL / http 链接，导入时统一解码）。 */
    data class CloudAlbumPhoto(
        val id: String,
        val image: String,
        val note: String,
        val date: String
    )

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
        if (reply.error != null) return CloudResult(null, reply.error, reply.detail)
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
     * 发评论到 moment_comments（Yuri 自己的评论）。
     * 列结构无文档，只发最可能的三个字段；失败（列名不符/权限）静默降级——本地一定有。
     */
    suspend fun postComment(momentId: String, author: String, content: String): Boolean {
        val body = JSONObject()
            .put("moment_id", momentId)
            .put("author", author)
            .put("content", content)
        val reply = supabaseRequest(
            "rest/v1/moment_comments", "POST", body.toString().toByteArray(Charsets.UTF_8)
        )
        if (reply.error != null) {
            Log.w(SupabaseClient.TAG, "[COMMENT] 评论上云未成（${reply.error}），已存本地")
        }
        return reply.error == null
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

/** sean_diary 一行（Z字折叠信日记页用）：他的信 + 小鑫的回应。 */
data class SeanDiaryRow(
    val id: Long,
    val thoughts: String,
    val content: String,
    val createdAtIso: String,
    val createdAtMs: Long,
    val replyContent: String,
    val repliedAtMs: Long
)

object SeanDiaryStore {

    /** 读他的信（含回应字段），新→旧。表空不算错误（Sean 还没写）。 */
    suspend fun fetchAll(): CloudResult<List<SeanDiaryRow>> {
        val reply = supabaseRequest(
            "rest/v1/sean_diary?select=id,thoughts,content,created_at,reply_content,replied_at" +
                "&order=created_at.desc&limit=100",
            "GET",
            parse = { conn -> JSONArray(conn.inputStream.bufferedReader().readText()) }
        )
        if (reply.error != null) return CloudResult(null, reply.error, reply.detail)
        val arr = reply.payload as? JSONArray ?: return CloudResult(null, CloudErrorKind.PARSE)
        val out = mutableListOf<SeanDiaryRow>()
        for (i in 0 until arr.length()) {
            try {
                val o = arr.getJSONObject(i)
                val repliedIso = o.optString("replied_at", "")
                out.add(
                    SeanDiaryRow(
                        id = o.optLong("id", 0L),
                        thoughts = o.optString("thoughts", ""),
                        content = o.optString("content", ""),
                        createdAtIso = o.optString("created_at", ""),
                        createdAtMs = parseSupabaseTime(o.optString("created_at", "")),
                        replyContent = o.optString("reply_content", ""),
                        repliedAtMs = if (repliedIso.isBlank()) 0L else parseSupabaseTime(repliedIso)
                    )
                )
            } catch (e: Exception) {
                android.util.Log.w(SupabaseClient.TAG, "[PARSE] sean_diary 第 $i 行解析失败: ${e.message}")
            }
        }
        return CloudResult(out, null)
    }

    /**
     * 贴回应（一篇一句）：写 reply_content / replied_at，并把 reply_seen 重置 false，
     * 让他那边能「撞见」。RLS 已放行（PATCH 实测 204）。
     */
    suspend fun saveReply(diaryId: Long, text: String): Boolean {
        val nowIso = java.time.Instant.now().toString()
        val body = org.json.JSONObject()
            .put("reply_content", text)
            .put("replied_at", nowIso)
            .put("reply_seen", false)
            .toString().toByteArray(Charsets.UTF_8)
        val reply = supabaseRequest(
            "rest/v1/sean_diary?id=eq." + diaryId,
            "PATCH",
            body = body
        )
        return reply.error == null
    }
}
