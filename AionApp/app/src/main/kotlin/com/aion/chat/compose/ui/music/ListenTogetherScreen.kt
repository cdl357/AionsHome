package com.aion.chat.compose.ui.music

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aion.chat.compose.data.MusicCardStore
import com.aion.chat.compose.data.MusicClient
import com.aion.chat.compose.data.MusicPlayer
import com.aion.chat.compose.ui.common.AvatarPhoto
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 一起听（网易云风格）：Yuri 和 Sean 的听歌房。
 * - 顶部双头像 + 连接徽章（呼吸脉动），连听时长计时
 * - 中间旋转唱片（播放时转）+ 歌名/歌手 + 同步进度条
 * - 控制：上一首 / 播放暂停 / 下一首
 * - 「他给你点的歌」：Sean 的点歌卡横排，点了直接进播放链路
 * - 进入/离开房都会向服务器报 together_join / together_leave（Sean 那边知道你们在一起听）
 */
@Composable
fun ListenTogetherScreen(onExit: () -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val player by MusicPlayer.state.collectAsState()
    var togetherSec by remember { mutableStateOf(0) }
    val cards = remember { MusicCardStore.list(context).sortedByDescending { it.createdAt }.take(6) }

    LaunchedEffect(Unit) {
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

    val pulse = rememberInfiniteTransition(label = "pulse")
    val pulseScale by pulse.animateFloat(
        1f, 1.35f,
        infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulseScale"
    )
    val pulseAlpha by pulse.animateFloat(
        0.55f, 0.1f,
        infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulseAlpha"
    )
    val vinylAngle = rememberInfiniteTransition(label = "vinyl")
    val discAngle by vinylAngle.animateFloat(
        0f, 360f,
        infiniteRepeatable(tween(9000, easing = androidx.compose.animation.core.LinearEasing)),
        label = "discAngle"
    )

    val mm = (togetherSec / 60).toString().padStart(2, '0')
    val ss = (togetherSec % 60).toString().padStart(2, '0')
    val posStr = "${(player.positionSec / 60).toString().padStart(2, '0')}:${(player.positionSec % 60).toString().padStart(2, '0')}"
    val durStr = "${(player.durationSec / 60).toString().padStart(2, '0')}:${(player.durationSec % 60).toString().padStart(2, '0')}"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF2B272C), Color(0xFF171519), Color(0xFF121014))
                )
            )
            .padding(horizontal = 22.dp)
    ) {
        // ── 顶栏 ──
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "退出一起听",
                tint = Color(0xFFB8B0B4),
                modifier = Modifier
                    .size(22.dp)
                    .clickable { onExit() }
            )
            Text(
                "一起听",
                fontSize = 16.sp, color = Color.White, fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )
            Text(
                "$mm:$ss",
                fontSize = 13.sp, color = Color(0xFF9C9298),
                fontFamily = FontFamily.Serif
            )
        }
        Text(
            "已连听（分钟:秒）",
            fontSize = 10.sp, color = Color(0xFF776D73),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(26.dp))

        // ── 双头像 + 连接徽章 ──
        Box(modifier = Modifier.fillMaxWidth().height(84.dp), contentAlignment = Alignment.Center) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AvatarPhoto(who = "yuri", initial = "Y", size = 66.dp, onClick = null)
                Spacer(Modifier.width(46.dp))
                AvatarPhoto(who = "sean", initial = "S", size = 66.dp, onClick = null)
            }
            // 中间连接徽章（耳机 + 呼吸环）
            Box(modifier = Modifier.align(Alignment.Center)) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .graphicsLayerAlpha(pulseScale)
                        .background(Color(0xFFEC4141).copy(alpha = pulseAlpha), CircleShape)
                )
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(Color(0xFFEC4141), CircleShape)
                        .border(2.dp, Color(0xFF171519), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.Headphones,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
        Text(
            "正在一起听 · Sean 也在",
            fontSize = 11.sp, color = Color(0xFFB8B0B4),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
        )
        Spacer(Modifier.height(30.dp))

        // ── 旋转唱片 ──
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.size(210.dp)) {
                val r = size.minDimension / 2f
                val c = Offset(size.width / 2f, size.height / 2f)
                // 盘面
                drawCircle(Brush.radialGradient(listOf(Color(0xFF2E2B30), Color(0xFF161418))), radius = r, center = c)
                // 唱片纹路
                for (i in 1..7) {
                    drawCircle(
                        color = Color(0x14FFFFFF),
                        radius = r * (0.42f + i * 0.075f),
                        center = c,
                        style = Stroke(1.2f)
                    )
                }
                // 中芯
                drawCircle(Brush.radialGradient(listOf(Color(0xFFEC4141), Color(0xFFB02E2E))), radius = r * 0.30f, center = c)
                drawCircle(Color(0xFF171519), radius = r * 0.045f, center = c)
            }
            // 旋转高光弧（播放时转）
            if (player.playing) {
                Canvas(
                    modifier = Modifier
                        .size(210.dp)
                        .graphicsLayerRotate(discAngle)
                ) {
                    val r = size.minDimension / 2f
                    val c = Offset(size.width / 2f, size.height / 2f)
                    drawArc(
                        brush = Brush.sweepGradient(
                            listOf(Color.Transparent, Color(0x30FFFFFF), Color.Transparent)
                        ),
                        startAngle = -60f, sweepAngle = 120f, useCenter = false,
                        topLeft = Offset(c.x - r, c.y - r), size = Size(r * 2, r * 2)
                    )
                }
            }
            // 歌名首字（盘芯）
            Text(
                player.song?.name?.take(1) ?: "♪",
                fontSize = 34.sp, color = Color.White, fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.Center)
            )
        }
        Spacer(Modifier.height(24.dp))

        // ── 歌名/歌手 ──
        Text(
            player.song?.name ?: "还没选歌",
            fontSize = 18.sp, color = Color.White, fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            player.song?.artist ?: "在下面挑一首，或者放 Sean 点的",
            fontSize = 12.sp, color = Color(0xFF9C9298),
            textAlign = TextAlign.Center,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth().padding(top = 5.dp)
        )
        Spacer(Modifier.height(18.dp))

        // ── 进度条 ──
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(posStr, fontSize = 10.sp, color = Color(0xFF776D73))
            Spacer(Modifier.width(8.dp))
            Box(modifier = Modifier.weight(1f).height(3.dp).clip(RoundedCornerShape(2.dp)).background(Color(0xFF3A363B))) {
                val frac = if (player.durationSec > 0) player.positionSec.toFloat() / player.durationSec else 0f
                Box(
                    modifier = Modifier
                        .fillMaxWidth(frac.coerceIn(0f, 1f))
                        .height(3.dp)
                        .background(Color(0xFFEC4141), RoundedCornerShape(2.dp))
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(durStr, fontSize = 10.sp, color = Color(0xFF776D73))
        }
        Spacer(Modifier.height(22.dp))

        // ── 控制条 ──
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF26222A))
                    .clickable { MusicPlayer.skip() },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.SkipPrevious, contentDescription = "上一首", tint = Color.White, modifier = Modifier.size(26.dp))
            }
            Spacer(Modifier.width(28.dp))
            Box(
                modifier = Modifier
                    .size(70.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFEC4141))
                    .clickable(enabled = player.song != null && !player.loading) {
                        if (!player.loading) MusicPlayer.toggle()
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (player.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = "播放/暂停",
                    tint = Color.White,
                    modifier = Modifier.size(38.dp)
                )
            }
            Spacer(Modifier.width(28.dp))
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF26222A))
                    .clickable { MusicPlayer.skip() },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.SkipNext, contentDescription = "下一首", tint = Color.White, modifier = Modifier.size(26.dp))
            }
        }
        Spacer(Modifier.height(26.dp))

        // ── 他给你点的歌 ──
        if (cards.isNotEmpty()) {
            Text(
                "他给你点的歌",
                fontSize = 12.sp, color = Color(0xFFB8B0B4), fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(cards, key = { it.createdAt }) { card ->
                    Column(
                        modifier = Modifier
                            .width(120.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF26222A))
                            .clickable {
                                MusicPlayer.play(context, MusicClient.Song(card.songId, card.name, card.artist))
                            }
                            .padding(10.dp)
                    ) {
                        Text(
                            card.name,
                            fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Medium,
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            card.artist,
                            fontSize = 10.sp, color = Color(0xFF9C9298),
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                        Text(
                            "▶ 播放",
                            fontSize = 10.sp, color = Color(0xFFEC4141),
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                }
            }
        }

        Spacer(Modifier.weight(1f))

        // ── 退出 ──
        Text(
            "退出一起听",
            fontSize = 13.sp, color = Color(0xFFEC4141),
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .border(1.dp, Color(0x55EC4141), RoundedCornerShape(22.dp))
                .clickable { onExit() }
                .padding(vertical = 12.dp)
        )
        Spacer(Modifier.height(110.dp))
    }
}

private fun Modifier.graphicsLayerAlpha(scale: Float): Modifier =
    this.then(Modifier.graphicsLayer { scaleX = scale; scaleY = scale })

private fun Modifier.graphicsLayerRotate(angle: Float): Modifier =
    this.then(Modifier.graphicsLayer { rotationZ = angle })
