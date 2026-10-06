package com.aion.chat.compose.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * 聊天里的音乐播放卡（AI 点歌的真产物，教程 §13：点歌必须生成真实可播放卡片）。
 * 本地留档最近 20 张，按时间正序渲染在聊天末尾；点卡片直接用全局唯一播放器播放。
 */
object MusicCardStore {

    data class Card(val songId: Long, val name: String, val artist: String, val createdAt: Long)

    private fun file(context: Context): File = File(context.filesDir, "music_cards.json")

    fun list(context: Context): List<Card> = try {
        val f = file(context)
        if (!f.exists()) emptyList()
        else {
            val arr = JSONObject(f.readText()).optJSONArray("cards") ?: return emptyList()
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Card(o.optLong("songId"), o.optString("name"), o.optString("artist"), o.optLong("createdAt"))
            }
        }
    } catch (e: Exception) { emptyList() }

    fun add(context: Context, song: MusicClient.Song): Boolean = try {
        val cards = list(context).toMutableList()
        // 连续同曲不重复加
        if (cards.lastOrNull()?.songId != song.id) {
            cards.add(Card(song.id, song.name, song.artist, System.currentTimeMillis()))
        }
        val capped = cards.takeLast(20)
        val arr = JSONArray()
        capped.forEach { arr.put(JSONObject().put("songId", it.songId).put("name", it.name).put("artist", it.artist).put("createdAt", it.createdAt)) }
        file(context).writeText(JSONObject().put("cards", arr).toString(2))
        true
    } catch (e: Exception) { false }
}
