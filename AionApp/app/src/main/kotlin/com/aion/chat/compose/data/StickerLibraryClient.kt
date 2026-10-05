package com.aion.chat.compose.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
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
