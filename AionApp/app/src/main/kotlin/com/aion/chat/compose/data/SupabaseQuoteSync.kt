package com.aion.chat.compose.data

import android.content.Context
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * 顶部情话的远程同步（Supabase home_quote 表，见 AionApp/supabase/home_quote.sql）。
 * 配置好 SUPABASE_URL / SUPABASE_ANON_KEY 后自动启用；未配置时仅存本地 SQLite，一切照常。
 * 令牌/密钥不进代码仓：这两个常量留空提交，发布前在本机填入即可。
 */
object SupabaseQuoteSync {
    private const val SUPABASE_URL = ""
    private const val SUPABASE_ANON_KEY = ""

    val configured: Boolean
        get() = SUPABASE_URL.isNotBlank() && SUPABASE_ANON_KEY.isNotBlank()

    private fun client() = okhttp3.OkHttpClient.Builder()
        .connectTimeout(java.time.Duration.ofSeconds(10))
        .readTimeout(java.time.Duration.ofSeconds(10))
        .build()

    /** 拉取 home_quote(id=1) 的 content；未配置或失败返回 null（调用方回退本地）。 */
    fun pull(): String? {
        if (!configured) return null
        return try {
            val request = okhttp3.Request.Builder()
                .url("$SUPABASE_URL/rest/v1/home_quote?id=eq.1&select=content")
                .get()
                .header("apikey", SUPABASE_ANON_KEY)
                .header("Authorization", "Bearer $SUPABASE_ANON_KEY")
                .build()
            client().newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val arr = org.json.JSONArray(resp.body!!.string())
                if (arr.length() == 0) null else arr.getJSONObject(0).optString("content")
            }
        } catch (e: Exception) {
            null
        }
    }

    /** 推送 content 到 home_quote(id=1)（upsert）；未配置或失败返回 false。 */
    fun push(content: String): Boolean {
        if (!configured) return false
        return try {
            val json = org.json.JSONObject().put("id", 1).put("content", content)
            val request = okhttp3.Request.Builder()
                .url("$SUPABASE_URL/rest/v1/home_quote?on_conflict=id")
                .put(json.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                .header("apikey", SUPABASE_ANON_KEY)
                .header("Authorization", "Bearer $SUPABASE_ANON_KEY")
                .header("Prefer", "resolution=merge-duplicates,return=minimal")
                .build()
            client().newCall(request).execute().use { it.isSuccessful }
        } catch (e: Exception) {
            false
        }
    }
}
