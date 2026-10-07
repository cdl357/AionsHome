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
import androidx.compose.ui.graphics.asImageBitmap
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aion.chat.compose.data.MusicClient
import com.aion.chat.compose.data.MusicPlayer
import com.aion.chat.compose.ui.theme.HomecomingColors
import kotlinx.coroutines.delay
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

    // 网易云账号（扫码登录，Cookie 只存服务器）
    var ncState by remember { mutableStateOf<MusicClient.NeteaseLoginState?>(null) }
    var showQr by remember { mutableStateOf(false) }

    fun loadNcState() {
        scope.launch {
            runCatching { MusicClient.neteaseLoginState() }.getOrNull()?.let { ncState = it }
        }
    }
    LaunchedEffect(Unit) { loadNcState() }

    // 今日私选（懒加载：切到标签才拉，教程 §19）
    var tab by remember { mutableStateOf("search") }
    val dailyCard = remember { mutableStateOf<MusicClient.DailyCard?>(null) }
    val dailyLoading = remember { mutableStateOf(false) }
    var dailyLoaded by remember { mutableStateOf(false) }

    fun loadDaily(refresh: Boolean) {
        if (dailyLoading.value) return
        dailyLoading.value = true
        scope.launch {
            val (card, err) = MusicClient.dailyDiscovery(refresh)
            dailyCard.value = card
            if (card == null && err != null) Toast.makeText(context, err, Toast.LENGTH_SHORT).show()
            dailyLoading.value = false
        }
    }

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

        // ── 网易云账号（扫码登录：Cookie 只存服务器；登录后 VIP/高音质概率更高） ──
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                "网易云账号：" + (ncState?.let { if (it.loggedIn && it.nickname.isNotBlank()) "已登录 · ${it.nickname}" else "未登录" } ?: "…"),
                fontSize = 12.sp, color = HomecomingColors.InkSoft,
                modifier = Modifier.weight(1f)
            )
            if (ncState?.loggedIn == true) {
                Text(
                    "退出登录",
                    fontSize = 12.sp, color = HomecomingColors.Danger,
                    modifier = Modifier.clickable {
                        scope.launch {
                            runCatching { MusicClient.neteaseLogout() }
                            loadNcState()
                            Toast.makeText(context, "已退出网易云登录", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            } else {
                Text(
                    "扫码登录",
                    fontSize = 12.sp, color = HomecomingColors.Accent,
                    modifier = Modifier.clickable { showQr = true }
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        if (showQr) {
            NeteaseQrDialog(
                onDismiss = { showQr = false },
                onLoggedIn = { nickname ->
                    showQr = false
                    loadNcState()
                    Toast.makeText(context, "网易云已登录：" + nickname, Toast.LENGTH_SHORT).show()
                }
            )
        }

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

        // ── 标签：搜歌 | 今日私选 ──
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            listOf("search" to "搜歌", "daily" to "今日私选").forEach { (k, label) ->
                val picked = tab == k
                Text(
                    label,
                    fontSize = 13.sp,
                    color = if (picked) Color.White else HomecomingColors.Ink,
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (picked) HomecomingColors.Accent else Color.White.copy(alpha = 0.85f))
                        .clickable {
                            tab = k
                            if (k == "daily" && !dailyLoaded) {
                                dailyLoaded = true
                                loadDaily(false)
                            }
                        }
                        .padding(horizontal = 14.dp, vertical = 7.dp)
                )
            }
        }
        Spacer(Modifier.height(8.dp))

        // ── 今日私选 ──
        if (tab == "daily") {
            val card = dailyCard.value
            when {
                dailyLoading.value -> Text(
                    "正在往外找歌…（要验证一堆候选，稍等）",
                    fontSize = 12.sp, color = HomecomingColors.InkSoft,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp), textAlign = TextAlign.Center
                )
                card == null -> Text(
                    "今天的私选还没出来，点下面的按钮再试",
                    fontSize = 12.sp, color = HomecomingColors.InkSoft,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp), textAlign = TextAlign.Center
                )
                else -> {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(card.subtitle, fontSize = 12.sp, color = HomecomingColors.InkSoft)
                            Text(card.note, fontSize = 11.sp, color = HomecomingColors.InkSoft, modifier = Modifier.padding(top = 2.dp))
                        }
                        Text(
                            "播放全部",
                            fontSize = 12.sp, color = HomecomingColors.Accent,
                            modifier = Modifier.clickable {
                                val songs = card.songs.map { MusicClient.Song(it.id, it.name, it.artist) }
                                MusicPlayer.playFromList(context, songs, 0)
                            }.padding(start = 8.dp)
                        )
                        Text(
                            "换一批",
                            fontSize = 12.sp, color = HomecomingColors.Accent,
                            modifier = Modifier.clickable { loadDaily(true) }.padding(start = 10.dp)
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    card.songs.forEachIndexed { idx, ds ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.White.copy(alpha = 0.85f))
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    "${idx + 1}. ${ds.name}",
                                    fontSize = 14.sp, color = HomecomingColors.Ink, fontWeight = FontWeight.Medium,
                                    maxLines = 1, modifier = Modifier.weight(1f)
                                )
                                Text(
                                    "▶",
                                    fontSize = 14.sp, color = HomecomingColors.Accent,
                                    modifier = Modifier.clickable {
                                        val songs = card.songs.map { MusicClient.Song(it.id, it.name, it.artist) }
                                        MusicPlayer.playFromList(context, songs, idx)
                                    }.padding(horizontal = 8.dp)
                                )
                            }
                            Text("${ds.artist}", fontSize = 11.sp, color = HomecomingColors.InkSoft, maxLines = 1)
                            Text(ds.reason, fontSize = 11.sp, color = HomecomingColors.InkSoft, modifier = Modifier.padding(top = 2.dp))
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                                Text("[${ds.tag}]", fontSize = 10.sp, color = HomecomingColors.Accent)
                                Spacer(Modifier.weight(1f))
                                listOf("喜欢" to "like", "多推这种" to "more_like_this", "少推这种" to "less_like_this").forEach { (label, action) ->
                                    Text(
                                        label,
                                        fontSize = 10.sp, color = HomecomingColors.InkSoft,
                                        modifier = Modifier
                                            .clickable {
                                                scope.launch {
                                                    runCatching { MusicClient.postFeedback(ds.id, action) }
                                                    Toast.makeText(context, "记下了", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                            .padding(start = 8.dp)
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                    }
                }
            }
        } else {
        // ── 我的歌单（导入网易云歌单，整张连播） ──
        }

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


/** 网易云扫码登录弹窗：二维码 + 轮询状态（等待扫码/确认/成功/过期），Cookie 只留服务器。 */
@Composable
private fun NeteaseQrDialog(onDismiss: () -> Unit, onLoggedIn: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var qrBase64 by remember { mutableStateOf<String?>(null) }
    var unikey by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("生成二维码…") }
    var done by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val start = MusicClient.neteaseQrStart()
        if (start == null) {
            status = "登录服务暂时连不上"
            return@LaunchedEffect
        }
        unikey = start.first
        qrBase64 = start.second
        status = "用网易云音乐 App 扫一扫"
        while (!done) {
            delay(2500)
            val check = MusicClient.neteaseQrCheck(unikey) ?: continue
            when (check.code) {
                803 -> {
                    status = "登录成功！"
                    done = true
                    onLoggedIn(check.nickname)
                }
                802 -> status = "手机上确认一下…"
                800 -> {
                    status = "二维码过期了，关掉重开一次"
                    done = true
                }
                else -> status = "等待扫码…"
            }
        }
    }

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF101B1F))
                .clickable { onDismiss() }
                .padding(30.dp)
        ) {
            Spacer(Modifier.height(30.dp))
            Text("登录网易云音乐", fontSize = 20.sp, color = Color.White, fontWeight = FontWeight.Medium)
            Text(status, fontSize = 14.sp, color = Color(0xFF8FE34A), modifier = Modifier.padding(top = 10.dp))
            Spacer(Modifier.height(20.dp))
            val qrB64 = qrBase64
            if (qrB64 != null) {
                val bmp = remember(qrB64) {
                    runCatching {
                        val bytes = android.util.Base64.decode(qrB64, android.util.Base64.DEFAULT)
                        android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    }.getOrNull()
                }
                if (bmp != null) {
                    androidx.compose.foundation.Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "登录二维码",
                        modifier = Modifier.size(240.dp)
                    )
                }
            } else {
                Box(
                    modifier = Modifier.size(240.dp),
                    contentAlignment = Alignment.Center
                ) { Text("…", color = Color.White) }
            }
            Spacer(Modifier.height(16.dp))
            Text(
                "Cookie 只会保存在你的服务器上，不会进手机缓存或聊天记录",
                fontSize = 10.sp, color = Color(0xFF6E8A92),
                textAlign = TextAlign.Center
            )
        }
    }
}
