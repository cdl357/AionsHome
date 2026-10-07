package com.aion.chat.compose.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL

/**
 * 服务器表情包库（/root/catstickers* 的猫猫包，经 VPS 中转 /stickers/ 公开）。
 * App 端：拉清单 → 逐张下载到 filesDir/stickers_remote/ → 与本地表情包合并展示。
 */
object StickerLibraryClient {

    private val BASES = arrayOf(
        SupabaseClient.RELAY_URL.removeSuffix("/supabase"),
        SupabaseClient.RELAY_URL_ALT.removeSuffix("/supabase")
    )

    data class Entry(val name: String, val file: String)

    /** 拉清单。返回 (清单, 可用的 base)；读不到返回 null。 */
    suspend fun fetchManifest(): Pair<List<Entry>, String>? = withContext(Dispatchers.IO) {
        for (base in BASES) {
            try {
                val conn = URL("$base/stickers/manifest.json").openConnection() as HttpURLConnection
                conn.connectTimeout = 8_000
                conn.readTimeout = 15_000
                if (conn.responseCode in 200..299) {
                    val o = JSONObject(conn.inputStream.bufferedReader().readText())
                    val arr = o.optJSONArray("stickers") ?: continue
                    val list = (0 until arr.length()).map { i ->
                        val e = arr.getJSONObject(i)
                        Entry(e.optString("name"), e.optString("file"))
                    }.filter { it.file.isNotBlank() }
                    if (list.isNotEmpty()) return@withContext list to base
                }
            } catch (e: Exception) { /* 这条线路不通 → 下一条 */ }
        }
        null
    }

    /** 下载一张表情到目标文件。 */
    suspend fun downloadTo(url: String, target: File): Boolean = withContext(Dispatchers.IO) {
        try {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 8_000
            conn.readTimeout = 20_000
            if (conn.responseCode !in 200..299) return@withContext false
            conn.inputStream.use { input -> target.outputStream().use { input.copyTo(it) } }
            target.length() > 0
        } catch (e: Exception) { false }
    }
}


// ── Supabase 表情包库（stickers 表：亲亲/抱抱/星星眼…，你们自己的那批） ──

data class SupabaseSticker(val name: String, val url: String)

/** stickers 表匿名可读；图片走中转（/supabase/ 代理 storage）。 */
suspend fun fetchSupabaseStickers(): List<SupabaseSticker>? = withContext(Dispatchers.IO) {
    val direct = "https://byqqwypdfiwvalozihgs.supabase.co/rest/v1/stickers?select=*&order=created_at.asc"
    val viaRelay = SupabaseClient.RELAY_URL.removeSuffix("/supabase") + "/supabase/rest/v1/stickers?select=*&order=created_at.asc"
    for (u in listOf(viaRelay, direct)) {
        try {
            val conn = URL(u).openConnection() as HttpURLConnection
            conn.connectTimeout = 8_000
            conn.readTimeout = 15_000
            conn.setRequestProperty("apikey", SupabaseClient.ANON_KEY)
            conn.setRequestProperty("Authorization", "Bearer " + SupabaseClient.ANON_KEY)
            if (conn.responseCode !in 200..299) continue
            val arr = JSONArray(conn.inputStream.bufferedReader().readText())
            val out = (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                SupabaseSticker(o.optString("name"), o.optString("url"))
            }.filter { it.url.isNotBlank() }
            if (out.isNotEmpty()) return@withContext out
        } catch (e: Exception) { }
    }
    null
}

/** 把 Supabase storage URL 映射为中转地址（优先）或原地址。 */
fun supabaseStorageCandidates(url: String): List<String> {
    val prefix = SupabaseClient.URL + "/storage/v1/"
    return if (url.startsWith(prefix)) {
        listOf(url, SupabaseClient.RELAY_URL.removeSuffix("/supabase") + "/supabase/storage/v1/" + url.removePrefix(prefix))
    } else listOf(url)
}

/** 下载一张表情（自动尝试中转/直连）。 */
suspend fun downloadSticker(url: String, target: File): Boolean = withContext(Dispatchers.IO) {
    for (u in supabaseStorageCandidates(url)) {
        try {
            val conn = URL(u).openConnection() as HttpURLConnection
            conn.connectTimeout = 8_000
            conn.readTimeout = 20_000
            if (conn.responseCode in 200..299) {
                conn.inputStream.use { input -> target.outputStream().use { input.copyTo(it) } }
                if (target.length() > 0) return@withContext true
            }
        } catch (e: Exception) { }
    }
    false
}


// ── 云端便签（Supabase bulletin_notes：沈聿淮的叮嘱，匿名可读） ──

data class BulletinNote(val author: String, val content: String, val createdAtMs: Long)

suspend fun fetchBulletinNotes(): List<com.aion.chat.compose.data.HomecomingDayStore.BoardNote>? =
    withContext(Dispatchers.IO) {
        val path = "/rest/v1/bulletin_notes?select=*&order=created_at.desc&limit=50"
        val direct = "https://byqqwypdfiwvalozihgs.supabase.co" + path
        val viaRelay = SupabaseClient.RELAY_URL.removeSuffix("/supabase") + "/supabase" + path
        for (u in listOf(viaRelay, direct)) {
            try {
                val conn = URL(u).openConnection() as HttpURLConnection
                conn.connectTimeout = 8_000
                conn.readTimeout = 15_000
                conn.setRequestProperty("apikey", SupabaseClient.ANON_KEY)
                conn.setRequestProperty("Authorization", "Bearer " + SupabaseClient.ANON_KEY)
                if (conn.responseCode !in 200..299) continue
                val arr = org.json.JSONArray(conn.inputStream.bufferedReader().readText())
                val out = (0 until arr.length()).map { i ->
                    val o = arr.getJSONObject(i)
                    val authorRaw = o.optString("author", "")
                    val ts = parseSupabaseTime(o.optString("created_at", ""))
                    com.aion.chat.compose.data.HomecomingDayStore.BoardNote(
                        id = -(ts / 1000).coerceAtLeast(1L),   // 负 id：云端行，避免与本地自增冲突
                        author = if (authorRaw.contains("沈") || authorRaw.contains("聿")) "sean" else authorRaw,
                        content = o.optString("content", ""),
                        createdAt = ts
                    )
                }
                if (out.isNotEmpty()) return@withContext out
            } catch (e: Exception) { }
        }
        null
    }
