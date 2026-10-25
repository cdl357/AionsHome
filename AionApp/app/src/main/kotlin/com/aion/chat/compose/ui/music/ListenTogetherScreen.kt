package com.aion.chat.compose.ui.music

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import com.aion.chat.compose.data.HomecomingChatWiring
import com.aion.chat.compose.data.MusicCardStore
import com.aion.chat.compose.data.MusicClient
import com.aion.chat.compose.data.MusicPlayer
import com.aion.chat.compose.data.SettingsBg
import com.aion.chat.compose.ui.common.AvatarPhoto
import com.aion.chat.compose.ui.theme.HomecomingColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 「爱」（原一起听）：主页「一起听歌」卡的落点，本质就是听歌的地方。
 * - 布局按参考图：顶栏(×/爱/计时+设置) → 双头像连接 → 唱片+歌信息+进度 → 聊天流 → 控制 → 退出
 * - 背景：默认跟全局背景；本页可单独换壁纸（love_bg.jpg），换了就不受全局影响
 * - 右上设置：本页壁纸 + 网易云扫码登录（都从别处收进这里）
 */
@Composable
fun ListenTogetherScreen(onExit: () -> Unit = {}, onOpenMusic: () -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val player by MusicPlayer.state.collectAsState()
    var togetherSec by remember { mutableStateOf(0) }
    val cards = remember { MusicCardStore.list(context).sortedByDescending { it.createdAt }.take(6) }

    // ── 本页背景：自己的壁纸优先，没有就跟全局 ──
    var loveVersion by remember { mutableStateOf(0L) }
    val bgBmp = remember { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(SettingsBg.stamp, loveVersion) {
        bgBmp.value = withContext(Dispatchers.IO) {
            (SettingsBg.loadLoveBitmap(context) ?: SettingsBg.loadBitmap(context))?.asImageBitmap()
        }
    }
    val lovePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            val ok = runCatching {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    SettingsBg.loveFile(context).outputStream().use { output -> input.copyTo(output) }
                } != null
            }.getOrDefault(false)
            if (ok) {
                loveVersion = System.currentTimeMillis()
            }
        }
    }

    // ── 聊天 ──
    val chat = remember { mutableStateListOf<Pair<String, String>>() }
    var input by remember { mutableStateOf("") }
    var seanTyping by remember { mutableStateOf(false) }
    val wiring = remember { HomecomingChatWiring.safeCreate(context) }
    val listState = rememberLazyListState()
    var showSettings by remember { mutableStateOf(false) }
    var showQr by remember { mutableStateOf(false) }
    var ncState by remember { mutableStateOf<MusicClient.NeteaseLoginState?>(null) }
    fun loadNc() {
        scope.launch {
            runCatching { MusicClient.neteaseLoginState() }.getOrNull()?.let { ncState = it }
        }
    }
    LaunchedEffect(Unit) {
        loadNc()
        chat.add("sean" to "来了。这首歌前奏很好听，你听。")
        MusicClient.postEvent("together_join", "user", player.song?.let { MusicClient.Song(it.id, it.name, it.artist) })
        while (true) {
            delay(1000)
            togetherSec++
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            scope.launch {
                runCatching {
                    MusicClient.postEvent("together_leave", "user", player.song?.let { MusicClient.Song(it.id, it.name, it.artist) })
                }
            }
        }
    }
    LaunchedEffect(chat.size, seanTyping) {
        val last = chat.size - 1 + if (seanTyping) 1 else 0
        if (last >= 0) listState.animateScrollToItem(last)
    }

    fun sendToSean(text: String) {
        if (text.isBlank() || seanTyping) return
        chat.add("user" to text)
        input = ""
        val songName = player.song?.name ?: "现在的歌"
        if (wiring == null || !wiring.hasRoute()) {
            chat.add("sean" to "（云线路没就绪，我先听着，你说的我都记着）")
            return
        }
        seanTyping = true
        scope.launch(Dispatchers.IO) {
            var reply: String? = null
            try {
                wiring.engine.send(
                    com.aion.chat.homecoming.HomecomingChatEngine.ChatCommand(
                        "req_together_" + System.currentTimeMillis(),
                        "moments_private", "sean", "user",
                        "你和 Yuri 正在一起听《$songName》。她刚刚说：\"$text\"。" +
                            "以 Sean（沈聿淮）的身份回一句：贴合听歌氛围，20 字以内，温柔、自然、不油腻，不要引号。",
                        "main",
                        wiring.mainModelKey(), "", ""
                    ),
                    object : com.aion.chat.homecoming.HomecomingChatEngine.Observer {
                        override fun onChunk(chunk: String) {}
                        override fun onComplete(messageId: String, text: String) {
                            val t = text.trim().removeSurrounding("\"").trim()
                            if (t.isNotEmpty()) reply = t
                        }
                        override fun onFailure(code: String) {}
                    }
                )
                var waited = 0L
                while (reply == null && waited < 20_000L) { delay(300L); waited += 300L }
            } catch (e: Exception) { /* 安静 */ }
            val r = reply
            withContext(Dispatchers.Main) {
                seanTyping = false
                chat.add("sean" to (r ?: "（刚才没听清，再说一遍？我耳机里全是这首）"))
            }
        }
    }

    val pulse = rememberInfiniteTransition(label = "pulse")
    val pulseScale by pulse.animateFloat(
        1f, 1.3f,
        infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulseScale"
    )
    val vinyl = rememberInfiniteTransition(label = "vinyl")
    val discAngle by vinyl.animateFloat(
        0f, 360f,
        infiniteRepeatable(tween(9000, easing = LinearEasing)),
        label = "discAngle"
    )

    val mm = (togetherSec / 60).toString().padStart(2, '0')
    val ss = (togetherSec % 60).toString().padStart(2, '0')
    val posStr = "%d:%02d".format(player.positionSec / 60, player.positionSec % 60)
    val durStr = "%d:%02d".format(player.durationSec / 60, player.durationSec % 60)

    val GlassBg = Color.White.copy(alpha = 0.55f)
    val GlassBorder = Color.White.copy(alpha = 0.85f)

    Box(modifier = Modifier.fillMaxSize()) {
        // ── 背景：本页壁纸 or 全局；一层薄纱保证玻璃可读 ──
        bgBmp.value?.let { bmp ->
            Image(
                bitmap = bmp, contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.White.copy(alpha = 0.18f), Color.White.copy(alpha = 0.30f))
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .padding(horizontal = 18.dp)
        ) {
            // ── 顶栏：× · 爱 · 计时+设置 ──
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(GlassBg)
                    .border(0.8.dp, GlassBorder, RoundedCornerShape(22.dp))
                    .padding(horizontal = 8.dp, vertical = 7.dp)
            ) {
                Icon(
                    Icons.Filled.Close, contentDescription = "退出",
                    tint = HomecomingColors.Ink,
                    modifier = Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .clickable { onExit() }
                        .padding(2.dp)
                )
                Text(
                    "爱",
                    fontSize = 16.sp, color = HomecomingColors.Ink, fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
                Text("$mm:$ss", fontSize = 12.sp, color = HomecomingColors.InkSoft)
                Spacer(Modifier.width(8.dp))
                Icon(
                    Icons.Filled.Settings, contentDescription = "设置",
                    tint = HomecomingColors.Ink,
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .clickable { showSettings = true }
                        .padding(2.dp)
                )
            }
            Spacer(Modifier.height(10.dp))

            // ── 双头像 + 连接 ──
            Box(modifier = Modifier.fillMaxWidth().height(62.dp), contentAlignment = Alignment.Center) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AvatarPhoto(who = "yuri", initial = "Y", size = 52.dp, onClick = null)
                    Spacer(Modifier.width(34.dp))
                    AvatarPhoto(who = "sean", initial = "S", size = 52.dp, onClick = null)
                }
                Box(modifier = Modifier.align(Alignment.Center)) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .graphicsLayer { scaleX = pulseScale; scaleY = pulseScale }
                            .background(HomecomingColors.Accent.copy(alpha = 0.18f), CircleShape)
                    )
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .background(HomecomingColors.Accent, CircleShape)
                            .border(2.dp, Color.White, CircleShape),
                        contentAlignment = Alignment.Center
                    ) { Text("♪", fontSize = 12.sp, color = Color.White) }
                }
            }
            Spacer(Modifier.height(8.dp))

            // ── 唱片 + 歌信息（玻璃卡） ──
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(GlassBg)
                    .border(0.8.dp, GlassBorder, RoundedCornerShape(16.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Canvas(modifier = Modifier.size(64.dp)) {
                        val r = size.minDimension / 2f
                        val c = Offset(size.width / 2f, size.height / 2f)
                        drawCircle(Brush.radialGradient(listOf(Color(0xFF3E3A40), Color(0xFF1E1C20))), radius = r, center = c)
                        for (i in 1..4) {
                            drawCircle(Color(0x14FFFFFF), radius = r * (0.5f + i * 0.1f), center = c, style = Stroke(1f))
                        }
                        drawCircle(HomecomingColors.Accent, radius = r * 0.3f, center = c)
                        drawCircle(Color.White, radius = r * 0.06f, center = c)
                    }
                    if (player.playing) {
                        Canvas(
                            modifier = Modifier
                                .size(64.dp)
                                .graphicsLayer { rotationZ = discAngle }
                        ) {
                            val r = size.minDimension / 2f
                            drawArc(
                                brush = Brush.sweepGradient(listOf(Color.Transparent, Color(0x28FFFFFF), Color.Transparent)),
                                startAngle = -60f, sweepAngle = 120f, useCenter = false,
                                topLeft = Offset(0f, 0f), size = Size(r * 2, r * 2)
                            )
                        }
                    }
                }
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        player.song?.name ?: "还没选歌",
                        fontSize = 14.sp, color = HomecomingColors.Ink, fontWeight = FontWeight.Medium,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        player.song?.artist ?: "在下面挑一首，或放 Sean 点的",
                        fontSize = 10.sp, color = HomecomingColors.InkSoft,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text(posStr, fontSize = 9.sp, color = HomecomingColors.InkSoft)
                        Spacer(Modifier.width(5.dp))
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(3.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(Color(0x22000000))
                        ) {
                            val frac = if (player.durationSec > 0) player.positionSec.toFloat() / player.durationSec else 0f
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(frac.coerceIn(0f, 1f))
                                    .height(3.dp)
                                    .background(HomecomingColors.Accent, RoundedCornerShape(2.dp))
                            )
                        }
                        Spacer(Modifier.width(5.dp))
                        Text(durStr, fontSize = 9.sp, color = HomecomingColors.InkSoft)
                    }
                }
            }
            Spacer(Modifier.height(10.dp))

            // ── 聊天卡（占满中部） ──
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White.copy(alpha = 0.42f))
                    .border(0.8.dp, GlassBorder, RoundedCornerShape(16.dp))
                    .padding(10.dp)
            ) {
                if (cards.isNotEmpty()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(cards, key = { it.createdAt }) { card ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(Color.White.copy(alpha = 0.7f))
                                    .border(0.8.dp, HomecomingColors.Accent.copy(alpha = 0.4f), RoundedCornerShape(50))
                                    .clickable {
                                        MusicPlayer.play(context, MusicClient.Song(card.songId, card.name, card.artist))
                                    }
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    "▶ " + card.name,
                                    fontSize = 10.sp, color = HomecomingColors.Accent,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.widthIn(max = 110.dp)
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(7.dp))
                }
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(chat.size) { i ->
                        val (who, text) = chat[i]
                        val fromSean = who == "sean"
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = if (fromSean) Arrangement.Start else Arrangement.End
                        ) {
                            if (fromSean) {
                                AvatarPhoto(who = "sean", initial = "S", size = 24.dp, onClick = null)
                                Spacer(Modifier.width(6.dp))
                            }
                            Box(
                                modifier = Modifier
                                    .widthIn(max = 230.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        if (fromSean) Color.White.copy(alpha = 0.75f)
                                        else HomecomingColors.Accent.copy(alpha = 0.16f)
                                    )
                                    .border(0.7.dp, GlassBorder.copy(alpha = 0.7f), RoundedCornerShape(12.dp))
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(text, fontSize = 12.sp, color = HomecomingColors.Ink, lineHeight = 17.sp)
                            }
                            if (!fromSean) {
                                Spacer(Modifier.width(6.dp))
                                AvatarPhoto(who = "yuri", initial = "Y", size = 24.dp, onClick = null)
                            }
                        }
                    }
                    if (seanTyping) {
                        item {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                AvatarPhoto(who = "sean", initial = "S", size = 24.dp, onClick = null)
                                Spacer(Modifier.width(6.dp))
                                Text("Sean 正在打字…", fontSize = 10.sp, color = HomecomingColors.InkSoft)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(7.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        placeholder = { Text("和 Sean 说一句…", fontSize = 12.sp, color = HomecomingColors.InkSoft) },
                        singleLine = true,
                        shape = RoundedCornerShape(22.dp),
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(HomecomingColors.Accent)
                            .clickable { sendToSean(input.trim()) },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.Send, contentDescription = "发送", tint = Color.White, modifier = Modifier.size(17.dp))
                    }
                }
            }
            Spacer(Modifier.height(10.dp))

            // ── 控制条 ──
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(GlassBg)
                        .border(0.8.dp, GlassBorder, CircleShape)
                        .clickable { MusicPlayer.skip() },
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Filled.SkipPrevious, contentDescription = "上一首", tint = HomecomingColors.Ink, modifier = Modifier.size(24.dp)) }
                Spacer(Modifier.width(22.dp))
                Box(
                    modifier = Modifier
                        .size(62.dp)
                        .clip(CircleShape)
                        .background(HomecomingColors.Accent)
                        .border(2.dp, Color.White, CircleShape)
                        .clickable(enabled = player.song != null && !player.loading) {
                            if (!player.loading) MusicPlayer.toggle()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (player.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = "播放/暂停",
                        tint = Color.White,
                        modifier = Modifier.size(34.dp)
                    )
                }
                Spacer(Modifier.width(22.dp))
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(GlassBg)
                        .border(0.8.dp, GlassBorder, CircleShape)
                        .clickable { MusicPlayer.skip() },
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Filled.SkipNext, contentDescription = "下一首", tint = HomecomingColors.Ink, modifier = Modifier.size(24.dp)) }
            }
            Spacer(Modifier.height(10.dp))

            Text(
                "退出",
                fontSize = 12.sp, color = HomecomingColors.Accent,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(GlassBg)
                    .border(0.8.dp, HomecomingColors.Accent.copy(alpha = 0.45f), RoundedCornerShape(20.dp))
                    .clickable { onExit() }
                    .padding(vertical = 9.dp)
            )
            Spacer(Modifier.height(96.dp))
        }

        // ── 设置面板（右上） ──
        if (showSettings) {
            LoveSettingsSheet(
                ncState = ncState,
                hasLoveBg = SettingsBg.loveFile(context).exists(),
                onPickWallpaper = { lovePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                onResetWallpaper = {
                    SettingsBg.loveFile(context).delete()
                    loveVersion = System.currentTimeMillis()
                },
                onQr = { showQr = true },
                onLogout = {
                    scope.launch {
                        runCatching { MusicClient.neteaseLogout() }
                        loadNc()
                    }
                },
                onOpenMusic = onOpenMusic,
                onClose = { showSettings = false }
            )
        }
        if (showQr) {
            NeteaseQrDialog(
                onDismiss = { showQr = false },
                onLoggedIn = { showQr = false; loadNc() }
            )
        }
    }
}

/** 「爱」的设置：本页壁纸 + 网易云登录 + 搜索歌单入口。 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun LoveSettingsSheet(
    ncState: MusicClient.NeteaseLoginState?,
    hasLoveBg: Boolean,
    onPickWallpaper: () -> Unit,
    onResetWallpaper: () -> Unit,
    onQr: () -> Unit,
    onLogout: () -> Unit,
    onOpenMusic: () -> Unit,
    onClose: () -> Unit
) {
    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onClose,
        containerColor = Color(0xFFFBF9F4)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(start = 22.dp, end = 22.dp, bottom = 30.dp)) {
            Text("爱的设置", fontSize = 17.sp, color = HomecomingColors.Ink, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(12.dp))

            Text("这个页面的壁纸", fontSize = 13.sp, color = HomecomingColors.Ink)
            Text(
                if (hasLoveBg) "用着单独的壁纸，不影响别的页面" else "现在跟全局背景走，选一张就只换这一页",
                fontSize = 11.sp, color = HomecomingColors.InkSoft
            )
            Row(modifier = Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                androidx.compose.material3.Button(onClick = onPickWallpaper, modifier = Modifier.weight(1f)) {
                    Text("选一张照片")
                }
                androidx.compose.material3.OutlinedButton(
                    onClick = onResetWallpaper,
                    enabled = hasLoveBg,
                    modifier = Modifier.weight(1f)
                ) { Text("恢复跟全局") }
            }
            Spacer(Modifier.height(14.dp))

            Text("网易云账号", fontSize = 13.sp, color = HomecomingColors.Ink)
            Text(
                "扫码登录后 VIP 歌和高音质概率更高；Cookie 只存你的服务器",
                fontSize = 11.sp, color = HomecomingColors.InkSoft
            )
            Row(modifier = Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (ncState?.loggedIn == true) {
                    Text(
                        "已登录 · ${ncState.nickname}",
                        fontSize = 12.sp, color = HomecomingColors.Ink,
                        modifier = Modifier.weight(1f).padding(top = 10.dp)
                    )
                    androidx.compose.material3.OutlinedButton(onClick = onLogout) { Text("退出") }
                } else {
                    androidx.compose.material3.Button(onClick = onQr, modifier = Modifier.weight(1f)) {
                        Text("扫码登录")
                    }
                }
            }
            Spacer(Modifier.height(14.dp))

            Text("搜索和歌单", fontSize = 13.sp, color = HomecomingColors.Ink)
            Text(
                (ncState?.loggedIn == true).let { "" } + "搜歌、导入网易云歌单、每日私选都在这",
                fontSize = 11.sp, color = HomecomingColors.InkSoft
            )
            androidx.compose.material3.OutlinedButton(
                onClick = onOpenMusic,
                modifier = Modifier.padding(top = 6.dp).fillMaxWidth()
            ) { Text("去搜歌 / 歌单 ›") }
        }
    }
}
