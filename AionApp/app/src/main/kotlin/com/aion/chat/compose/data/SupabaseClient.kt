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

    // 从 BuildConfig 读取，不再硬编码
    val URL: String = BuildConfig.SUPABASE_URL
    val ANON_KEY: String = BuildConfig.SUPABASE_ANON_KEY

    /** VPS 中转主线路：8783 端口（安全组已放行，外部实测 200）。 */
    const val RELAY_URL = "http://134.175.7.196:8783/supabase"

    /** 备用中转：18443（个别网络环境可能放行）。 */
    const val RELAY_URL_ALT = "http://134.175.7.196:18443/supabase"

    internal val BASES = arrayOf(RELAY_URL, RELAY_URL_ALT, URL)

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
            Log.w(SupabaseClient.TAG, "[DNS] $method /$path base=$base")
            lastError = CloudErrorKind.NETWORK
        } catch (e: SocketTimeoutException) {
            Log.w(SupabaseClient.TAG, "[TIMEOUT] $method /$path base=$base")
            lastError = CloudErrorKind.NETWORK
        } catch (e: Exception) {
            Log.w(SupabaseClient.TAG, "[NET] $method /$path base=$base msg=${e.message}")
            lastError = CloudErrorKind.NETWORK
        }
    }
    CloudReply(error = lastError ?: CloudErrorKind.NETWORK)
}

/** 日记 + 今日情话仓库。列表只读 Yuri 自己的；新增固定 author='Yuri'。 */
object SupabaseDiary {

    suspend fun list(): CloudResult<List<DiaryEntry>> {
        val reply = supabaseRequest(
            "rest/v1/diary_entries?author=eq.Yuri&order=date.desc&limit=100",
            "GET"
        ) { conn ->
            val json = conn.inputStream.bufferedReader().readText()
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                DiaryEntry(
                    id = obj.optInt("id", -1),
                    author = obj.optString("author", ""),
                    date = obj.optString("date", ""),
                    content = obj.optString("content", "")
                )
            }
        }
        @Suppress("UNCHECKED_CAST")
        return CloudResult(reply.payload as? List<DiaryEntry>, reply.error)
    }

    suspend fun insert(date: String, content: String): Boolean {
        val body = JSONObject()
            .put("author", "Yuri")
            .put("date", date)
            .put("content", content)
        val reply = supabaseRequest(
            "rest/v1/diary_entries", "POST", body.toString().toByteArray(Charsets.UTF_8)
        )
        return reply.error == null
    }
}

data class DiaryEntry(val id: Int, val author: String, val date: String, val content: String)

/** 朋友圈。列表只看 Yuri 自己的；评论是全表读（moment_id 索引）；点赞暂未实现。 */
object SupabaseMoments {

    suspend fun list(): CloudResult<List<MomentEntry>> {
        val reply = supabaseRequest(
            "rest/v1/moments?author=eq.Yuri&order=created_at.desc&limit=100",
            "GET"
        ) { conn ->
            val json = conn.inputStream.bufferedReader().readText()
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                val imgs = obj.optJSONArray("images")
                val imgList = if (imgs != null) {
                    (0 until imgs.length()).map { j -> imgs.getString(j) }
                } else emptyList()
                MomentEntry(
                    id = obj.optInt("id", -1),
                    author = obj.optString("author", ""),
                    content = obj.optString("content", ""),
                    createdAt = obj.optString("created_at", ""),
                    images = imgList
                )
            }
        }
        @Suppress("UNCHECKED_CAST")
        return CloudResult(reply.payload as? List<MomentEntry>, reply.error)
    }

    suspend fun listComments(momentId: Int): CloudResult<List<MomentComment>> {
        val reply = supabaseRequest(
            "rest/v1/moment_comments?moment_id=eq.$momentId&order=created_at.asc",
            "GET"
        ) { conn ->
            val json = conn.inputStream.bufferedReader().readText()
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                MomentComment(
                    id = obj.optInt("id", -1),
                    momentId = obj.optInt("moment_id", -1),
                    author = obj.optString("author", ""),
                    content = obj.optString("content", ""),
                    createdAt = obj.optString("created_at", "")
                )
            }
        }
        @Suppress("UNCHECKED_CAST")
        return CloudResult(reply.payload as? List<MomentComment>, reply.error)
    }

    suspend fun postComment(momentId: Int, author: String, content: String): Boolean {
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

data class MomentEntry(
    val id: Int,
    val author: String,
    val content: String,
    val createdAt: String,
    val images: List<String>
)

data class MomentComment(
    val id: Int,
    val momentId: Int,
    val author: String,
    val content: String,
    val createdAt: String
)

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
