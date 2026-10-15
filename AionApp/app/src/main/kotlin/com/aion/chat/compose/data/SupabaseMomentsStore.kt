package com.aion.chat.compose.data

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * 朋友圈 / 哥哥日记的云端仓库（Supabase REST，anon key）。
 *
 * 这个文件是按调用点重建的：原始实现从未提交到仓库，导致 10-10 之前的构建
 * 直接把 Kotlin + Compose 从 build.gradle 里拆掉才能编过（代价是入口 Activity
 * 没进包、装上必闪退）。重建的目标是恢复编译并保持调用点语义：
 *   - fetchMoments()      朋友圈列表，失败回落心潮只读代理，再失败返回 null
 *   - postMoment()        发动态（Yuri）
 *   - fetchSeanDiaries()  哥哥的日记（只读）
 *   - mapAuthor()         云端作者名 → 本地 "sean" / "yuri"
 * 表结构依据 docs/supabase-rls.sql 与 SupabaseMoments/SupabaseDiary 里的既有映射。
 */
object SupabaseMomentsStore {

    private const val TAG = "SupabaseMoments"

    /** 云端一条动态。imageUrl 是给只展示单图的旧调用点用的便捷属性。 */
    data class RemoteMoment(
        val id: String,
        val author: String,
        val content: String,
        val imageUrls: List<String> = emptyList(),
        val createdAt: String = "",
        val createdAtMs: Long = 0L,
        val liked: Boolean = false,
        val yuriLiked: Boolean = false,
        val replyStatus: String = "",
        val replyContent: String = ""
    ) {
        val imageUrl: String? get() = imageUrls.firstOrNull()
    }

    /** 云端一条日记（哥哥写的，App 端只读）。 */
    data class RemoteDiary(
        val id: String,
        val title: String,
        val content: String,
        val createdAt: String = "",
        val createdAtMs: Long = 0L
    )

    /** 云端作者名 → 本地标识。沈聿/哥哥 记为 sean，其余归 yuri。 */
    fun mapAuthor(raw: String): String {
        val s = raw.trim()
        return when {
            s.isEmpty() -> "yuri"
            s.equals("sean", ignoreCase = true) -> "sean"
            s.contains("沈") || s.contains("聿") || s.contains("哥哥") -> "sean"
            else -> "yuri"
        }
    }

    /**
     * 读朋友圈。直连/中转全失败时走心潮后端的只读代理兜底；
     * 两条路都不通返回 null（调用方据此显示「云端连不上」横幅，不清空本地内容）。
     */
    suspend fun fetchMoments(): List<RemoteMoment>? {
        val reply = supabaseRequest(
            "rest/v1/moments?order=created_at.desc&limit=100",
            "GET"
        ) { conn -> parseMoments(conn) }

        @Suppress("UNCHECKED_CAST")
        val direct = reply.payload as? List<RemoteMoment>
        if (reply.error == null && direct != null) return direct

        Log.w(TAG, "直连读朋友圈失败（${reply.error}），尝试心潮代理")
        return runCatching { XinchaoClient.fetchMomentsViaXinchao() }.getOrNull()
    }

    /** 发一条动态。imageUrl 为空字符串表示没有配图（只在本地留档）。 */
    suspend fun postMoment(content: String, author: String, imageUrl: String = ""): Boolean {
        val body = JSONObject().put("author", author).put("content", content)
        if (imageUrl.isNotBlank()) body.put("images", JSONArray(listOf(imageUrl)))
        val reply = supabaseRequest(
            "rest/v1/moments", "POST", body.toString().toByteArray(Charsets.UTF_8)
        )
        if (reply.error != null) Log.w(TAG, "发动态上云未成（${reply.error}），已存本地")
        return reply.error == null
    }

    /** 读哥哥的日记（user_id=ai_哥哥）。错误分种返回，界面据此区分「没网」和「没放行」。 */
    suspend fun fetchSeanDiaries(): CloudResult<List<RemoteDiary>> {
        val uid = URLEncoder.encode("ai_哥哥", "UTF-8")
        val reply = supabaseRequest(
            "rest/v1/diary_entries?user_id=eq.$uid&order=created_at.desc&limit=100",
            "GET"
        ) { conn ->
            val arr = JSONArray(conn.inputStream.bufferedReader().readText())
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                val created = o.optString("created_at", "")
                RemoteDiary(
                    id = o.optString("id", ""),
                    title = o.optString("title", ""),
                    content = o.optString("content", ""),
                    createdAt = created,
                    createdAtMs = parseSupabaseTime(created)
                )
            }
        }
        @Suppress("UNCHECKED_CAST")
        return CloudResult(reply.payload as? List<RemoteDiary>, reply.error)
    }

    private fun parseMoments(conn: HttpURLConnection): List<RemoteMoment> {
        val arr = JSONArray(conn.inputStream.bufferedReader().readText())
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val created = o.optString("created_at", "")
            RemoteMoment(
                id = o.optString("id", ""),
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
        }
    }
}

/**
 * Supabase 的 timestamptz 字符串 → 毫秒。解析不出来返回 0，
 * 调用方自己决定回落（通常用 System.currentTimeMillis()）。
 */
fun parseSupabaseTime(raw: String): Long {
    val s = raw.trim()
    if (s.isEmpty()) return 0L
    // "2026-10-01T03:15:15.123456+00:00" / "...Z" / 无小数秒 / 无时区
    val normalized = s
        .replace("Z", "+0000")
        .replace(Regex("([+-]\\d{2}):(\\d{2})$"), "$1$2")
        .replace(Regex("(\\.\\d{3})\\d+"), "$1")
    val patterns = arrayOf(
        "yyyy-MM-dd'T'HH:mm:ss.SSSZ",
        "yyyy-MM-dd'T'HH:mm:ssZ",
        "yyyy-MM-dd'T'HH:mm:ss.SSS",
        "yyyy-MM-dd'T'HH:mm:ss",
        "yyyy-MM-dd HH:mm:ss",
        "yyyy-MM-dd"
    )
    for (p in patterns) {
        runCatching {
            val fmt = SimpleDateFormat(p, Locale.US)
            if (!p.endsWith("Z")) fmt.timeZone = TimeZone.getTimeZone("UTC")
            return fmt.parse(normalized)?.time ?: 0L
        }
    }
    return 0L
}

/** JSONArray / 单个字符串 / null → 字符串列表（空白项丢掉）。 */
fun jsonStringArray(value: Any?): List<String> = when (value) {
    null, JSONObject.NULL -> emptyList()
    is JSONArray -> (0 until value.length())
        .map { value.optString(it, "") }
        .filter { it.isNotBlank() }
    is String -> if (value.isBlank()) emptyList() else runCatching {
        val arr = JSONArray(value)
        (0 until arr.length()).map { arr.optString(it, "") }.filter { it.isNotBlank() }
    }.getOrElse { listOf(value) }
    else -> emptyList()
}
