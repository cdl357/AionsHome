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
 */
object SupabaseClient {

    const val URL = "https://byqqwypdfiwvalozihgs.supabase.co"
    const val ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImJ5cXF3eXBkZml3dmFsb3ppaGdzIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODM2NTQwODAsImV4cCI6MjA5OTIzMDA4MH0.Gacxi6TVGzL3pNn-KdUHkPTYW8dvSpt7A05FpmkZlyc"

    val configured: Boolean get() = URL.isNotBlank() && ANON_KEY.isNotBlank()

    /** GET 请求，返回 JSONArray（空数组或错误返回空）。 */
    suspend fun get(table: String, query: String = ""): JSONArray {
        return withContext(Dispatchers.IO) {
            try {
                val url = "$URL/rest/v1/$table${if (query.isNotBlank()) "?$query" else ""}"
                val conn = URL(url).openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.setRequestProperty("apikey", ANON_KEY)
                conn.setRequestProperty("Authorization", "Bearer $ANON_KEY")
                conn.setRequestProperty("Accept", "application/json")
                conn.connectTimeout = 10_000
                conn.readTimeout = 15_000
                val code = conn.responseCode
                if (code in 200..299) {
                    JSONArray(conn.inputStream.bufferedReader().readText())
                } else {
                    JSONArray()
                }
            } catch (e: Exception) { JSONArray() }
        }
    }

    /** POST（插入一行）。 */
    suspend fun post(table: String, body: JSONObject): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = "$URL/rest/v1/$table"
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("apikey", ANON_KEY)
            conn.setRequestProperty("Authorization", "Bearer $ANON_KEY")
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Prefer", "return=minimal")
            conn.doOutput = true
            conn.connectTimeout = 10_000
            conn.readTimeout = 15_000
            conn.outputStream.write(body.toString().toByteArray(Charsets.UTF_8))
            conn.responseCode in 200..299
        } catch (e: Exception) { false }
    }

    /** PATCH（更新）。 */
    suspend fun patch(table: String, filter: String, body: JSONObject): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = "$URL/rest/v1/$table?$filter"
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.requestMethod = "PATCH"
            conn.setRequestProperty("apikey", ANON_KEY)
            conn.setRequestProperty("Authorization", "Bearer $ANON_KEY")
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Prefer", "return=minimal")
            conn.doOutput = true
            conn.connectTimeout = 10_000
            conn.readTimeout = 15_000
            conn.outputStream.write(body.toString().toByteArray(Charsets.UTF_8))
            conn.responseCode in 200..299
        } catch (e: Exception) { false }
    }
}

/** 朋友圈 + 日记 Supabase 数据层。 */
object SupabaseMomentsStore {

    data class RemoteMoment(
        val id: String, val author: String, val content: String,
        val imageUrl: String?, val createdAt: String
    )

    data class RemoteDiary(
        val id: String, val userId: String, val title: String,
        val content: String, val mood: String, val createdAt: String
    )

    fun mapAuthor(raw: String): String = when {
        raw.contains("沈聿淮") || raw == "sean" || raw == "a_哥哥" || raw.contains("ai_") -> "sean"
        raw.contains("小鑫") || raw == "yuri" || raw == "user" -> "yuri"
        else -> raw
    }

    fun displayName(author: String): String = when (author) {
        "sean" -> "Sean"; "yuri" -> "Yuri"; else -> author
    }

    /** 读朋友圈动态（created_at 倒序）。 */
    suspend fun fetchMoments(): List<RemoteMoment> = withContext(Dispatchers.IO) {
        try {
            val arr = SupabaseClient.get("moments", "select=*&order=created_at.desc&limit=50")
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                RemoteMoment(
                    id = o.optString("id"),
                    author = mapAuthor(o.optString("author", "")),
                    content = o.optString("content", ""),
                    imageUrl = o.optString("image_url", null.takeIf { false } ?: ""),
                    createdAt = o.optString("created_at", "")
                )
            }
        } catch (e: Exception) { emptyList() }
    }

    /** 读哥哥的日记（user_id = ai_哥哥）。 */
    suspend fun fetchSeanDiaries(): List<RemoteDiary> = withContext(Dispatchers.IO) {
        try {
            val arr = SupabaseClient.get(
                "diary_entries",
                "user_id=eq.ai_哥哥&select=*&order=created_at.desc&limit=50"
            )
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                RemoteDiary(
                    id = o.optString("id"), userId = o.optString("user_id", ""),
                    title = o.optString("title", ""), content = o.optString("content", ""),
                    mood = o.optString("mood", ""), createdAt = o.optString("created_at", "")
                )
            }
        } catch (e: Exception) { emptyList() }
    }

    /** 发朋友圈（写入 Supabase）。 */
    suspend fun postMoment(content: String, author: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val body = JSONObject().put("author", author).put("content", content)
            SupabaseClient.post("moments", body)
        } catch (e: Exception) { false }
    }
}

/** SupabaseQuoteSync 使用同一 URL/Key，值从 SupabaseClient 读取。 */
object SupabaseQuoteSync {
    val configured: Boolean get() = SupabaseClient.configured

    suspend fun pull(): String? = withContext(Dispatchers.IO) {
        try {
            val arr = SupabaseClient.get("home_quote", "id=eq.1&select=content")
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
