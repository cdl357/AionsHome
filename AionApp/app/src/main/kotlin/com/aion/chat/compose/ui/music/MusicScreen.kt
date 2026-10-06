package com.aion.chat.compose.ui.music

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aion.chat.compose.data.MusicClient
import com.aion.chat.compose.data.MusicPlayer
import com.aion.chat.compose.ui.theme.HomecomingColors
import kotlinx.coroutines.launch

/** 一起听歌（Cove 阶段 A：搜索 → 后端代理音频流 → 播放；错误说人话）。 */
@Composable
fun MusicScreen(onBack: () -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val query = remember { mutableStateOf("") }
    val searching = remember { mutableStateOf(false) }
    val results = remember { mutableStateListOf<MusicClient.Song>() }
    val searchError = remember { mutableStateOf<String?>(null) }
    val player by MusicPlayer.state.collectAsState()

    // 歌单
    val playlists = remember { mutableStateListOf<MusicClient.Playlist>() }
    val importUrl = remember { mutableStateOf("") }
    val importing = remember { mutableStateOf(false) }
    val expandedPid = remember { mutableStateOf<Long?>(null) }
    var playlistsLoaded by remember { mutableStateOf(false) }

    fun doSearch() {
        val q = query.value.trim()
        if (q.isEmpty() || searching.value) return
        searching.value = true
        searchError.value = null
        scope.launch {
            val (list, err) = MusicClient.search(q)
            results.clear()
            results.addAll(list)
            searchError.value = err
            searching.value = false
        }
    }

    fun loadPlaylists() {
        scope.launch {
            runCatching { MusicClient.listPlaylists() }.getOrNull()?.let {
                playlists.clear()
                playlists.addAll(it)
            }
        }
    }
    LaunchedEffect(Unit) { loadPlaylists() }

    fun doImport() {
        val raw = importUrl.value.trim()
        if (raw.isEmpty() || importing.value) return
        importing.value = true
        scope.launch {
            val (pl, err) = MusicClient.importPlaylist(raw)
            importing.value = false
            if (pl == null) {
                Toast.makeText(context, err ?: "导入失败", Toast.LENGTH_SHORT).show()
            } else {
                importUrl.value = ""
                loadPlaylists()
                Toast.makeText(context, "歌单《${pl.name}》已入库（${pl.trackCount} 首）", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun doDelete(pid: Long) {
        scope.launch {
            runCatching { MusicClient.deletePlaylist(pid) }
            loadPlaylists()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 112.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "返回",
                tint = HomecomingColors.Ink,
                modifier = Modifier
                    .size(24.dp)
                    .clickable { onBack() }
            )
            Column(modifier = Modifier.weight(1f).padding(start = 10.dp)) {
                Text("一起听歌", fontSize = 22.sp, color = HomecomingColors.Ink, fontWeight = FontWeight.Medium)
                Text("搜一首歌，他和你一起听", fontSize = 11.sp, color = HomecomingColors.InkSoft)
            }
        }
        Spacer(Modifier.height(10.dp))

        // ── 搜索框 ──
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = query.value,
                onValueChange = { query.value = it },
                placeholder = { Text("歌名 / 歌手…", fontSize = 13.sp, color = HomecomingColors.InkSoft) },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            Box(
                modifier = Modifier
                    .padding(start = 8.dp)
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(HomecomingColors.Accent)
                    .clickable { doSearch() },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Search, contentDescription = "搜索", tint = Color.White)
            }
        }
        Spacer(Modifier.height(10.dp))

        // ── 正在播放卡 ──
        val cur = player.song
        if (cur != null) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White.copy(alpha = 0.85f))
                    .padding(14.dp)
            ) {
                Text(
                    cur.name,
                    fontSize = 15.sp, fontWeight = FontWeight.Medium, color = HomecomingColors.Ink,
                    maxLines = 1
                )
                Text(cur.artist, fontSize = 12.sp, color = HomecomingColors.InkSoft, maxLines = 1)
                val errMsg = player.error
                if (errMsg != null) {
                    Text(errMsg, fontSize = 11.sp, color = HomecomingColors.Danger, modifier = Modifier.padding(top = 4.dp))
                }
                if (player.durationSec > 0) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                        Text(fmt(player.positionSec), fontSize = 10.sp, color = HomecomingColors.InkSoft)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 8.dp)
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(HomecomingColors.Accent.copy(alpha = 0.25f))
                        )
                        Text(fmt(player.durationSec), fontSize = 10.sp, color = HomecomingColors.InkSoft)
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(if (player.loading) HomecomingColors.Accent.copy(alpha = 0.4f) else HomecomingColors.Accent)
                            .clickable(enabled = !player.loading) {
                                if (player.loading) return@clickable
                                if (player.song != null) MusicPlayer.toggle()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (player.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = "播放/暂停",
                            tint = Color.White,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                }
                if (player.loading) {
                    Text("正在取音源…", fontSize = 10.sp, color = HomecomingColors.InkSoft, modifier = Modifier.fillMaxWidth().padding(top = 4.dp), textAlign = TextAlign.Center)
                }
            }
            Spacer(Modifier.height(10.dp))
        }

        // ── 我的歌单（导入网易云歌单，整张连播） ──
        Text("我的歌单", fontSize = 13.sp, color = HomecomingColors.InkSoft)
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = importUrl.value,
                onValueChange = { importUrl.value = it },
                placeholder = { Text("贴网易云歌单链接或 ID", fontSize = 12.sp, color = HomecomingColors.InkSoft) },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            Box(
                modifier = Modifier
                    .padding(start = 8.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (importing.value) HomecomingColors.Accent.copy(alpha = 0.4f) else HomecomingColors.Accent)
                    .clickable(enabled = !importing.value) { doImport() }
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Text(if (importing.value) "导入中…" else "导入", fontSize = 13.sp, color = Color.White)
            }
        }
        Spacer(Modifier.height(6.dp))
        playlists.forEach { pl ->
            val expanded = expandedPid.value == pl.id
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.85f))
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f).clickable {
                        expandedPid.value = if (expanded) null else pl.id
                    }) {
                        Text(pl.name, fontSize = 14.sp, color = HomecomingColors.Ink, fontWeight = FontWeight.Medium, maxLines = 1)
                        Text("${pl.trackCount} 首", fontSize = 11.sp, color = HomecomingColors.InkSoft)
                    }
                    Text(
                        "▶ 播放",
                        fontSize = 12.sp, color = HomecomingColors.Accent,
                        modifier = Modifier
                            .clickable { MusicPlayer.playFromList(context, pl.tracks, 0) }
                            .padding(horizontal = 8.dp)
                    )
                    Text(
                        "删除",
                        fontSize = 11.sp, color = HomecomingColors.InkSoft,
                        modifier = Modifier
                            .clickable { doDelete(pl.id) }
                            .padding(start = 6.dp)
                    )
                }
                if (expanded) {
                    pl.tracks.take(30).forEachIndexed { idx, t ->
                        Text(
                            "${idx + 1}. ${t.name} — ${t.artist}",
                            fontSize = 12.sp, color = HomecomingColors.Ink,
                            maxLines = 1,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { MusicPlayer.playFromList(context, pl.tracks, idx) }
                                .padding(vertical = 4.dp, horizontal = 6.dp)
                        )
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
        }
        Spacer(Modifier.height(8.dp))

        // ── 搜索结果 ──
        if (searching.value) {
            Text("搜索中…", fontSize = 12.sp, color = HomecomingColors.InkSoft, modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp), textAlign = TextAlign.Center)
        }
        searchError.value?.let {
            Text(it, fontSize = 12.sp, color = HomecomingColors.Danger, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), textAlign = TextAlign.Center)
        }
        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            items(results, key = { it.id }) { song ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { MusicPlayer.play(context, song) }
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(HomecomingColors.Accent.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.PlayArrow, contentDescription = "播放",
                            tint = HomecomingColors.Accent, modifier = Modifier.size(20.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f).padding(start = 10.dp)) {
                        Text(song.name, fontSize = 14.sp, color = HomecomingColors.Ink, maxLines = 1)
                        Text(
                            song.artist + if (song.fee == 1) " · VIP" else "",
                            fontSize = 11.sp, color = HomecomingColors.InkSoft, maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

private fun fmt(sec: Int): String = "%d:%02d".format(sec / 60, sec % 60)
