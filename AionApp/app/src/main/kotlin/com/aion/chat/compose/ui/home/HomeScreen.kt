package com.aion.chat.compose.ui.home

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aion.chat.compose.data.HomecomingData
import com.aion.chat.compose.ui.theme.HomecomingColors
import android.widget.Toast

/** 页面①：回家（中心首页）。磨砂玻璃卡片 + 冰蓝底 + 暖粉点缀。 */
@Composable
fun HomeScreen() {
    val context = LocalContext.current
    var recent by remember { mutableStateOf(listOf<HomecomingData.FeedItem>()) }

    LaunchedEffect(Unit) {
        recent = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            HomecomingData.loadRecent(context)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(HomecomingColors.IceBlueLight, Color.White, HomecomingColors.IceBlue.copy(alpha = 0.55f))
                )
            )
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. 顶部条：Yuri 头像 | 在一起 XX 天（全页焦点）| Sean 头像
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            GlassAvatar(initial = "Y")
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                Text(
                    text = "ALREADY TOGETHER",
                    fontSize = 10.sp,
                    letterSpacing = 3.sp,
                    color = HomecomingColors.InkSoft
                )
                Text(
                    text = HomecomingData.daysTogether().toString(),
                    fontFamily = FontFamily.Serif,
                    fontSize = 56.sp,
                    color = HomecomingColors.WarmPink
                )
                Text(
                    text = "since " + HomecomingData.since(),
                    fontSize = 12.sp,
                    color = HomecomingColors.InkSoft
                )
            }
            GlassAvatar(initial = "S")
        }
        Text(
            text = HomecomingData.quoteForToday(),
            fontSize = 14.sp,
            color = HomecomingColors.Ink,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        // 2. 一起听歌（本期 UI + 播放控件占位）
        GlassCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            Brush.linearGradient(listOf(HomecomingColors.IceBlue, HomecomingColors.WarmPinkSoft))
                        )
                )
                Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                    Text("一起听歌", fontSize = 15.sp, color = HomecomingColors.Ink)
                    Text("歌单随后端接入", fontSize = 12.sp, color = HomecomingColors.InkSoft)
                }
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = "播放",
                    tint = HomecomingColors.Ink,
                    modifier = Modifier.size(30.dp)
                )
            }
        }

        // 3. 今日心情 | 相册（两个半宽卡片）
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxWidth()) {
            GlassCard(modifier = Modifier.weight(1f), onClick = null) {
                Text("今日心情", fontSize = 13.sp, color = HomecomingColors.InkSoft)
                listOf("(´▽`)", "(￣▽￣)", "(>_<)", "(ㄒoㄒ)")
                    .chunked(2)
                    .forEach { pair ->
                        Row(
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            pair.forEach { face ->
                                Text(
                                    text = face,
                                    fontSize = 14.sp,
                                    modifier = Modifier.clickable {
                                        Toast.makeText(context, "点一个，哥哥回你一句（AI 回应随后端接线开放）", Toast.LENGTH_SHORT).show()
                                    }
                                )
                            }
                        }
                    }
                Text("点一个，哥哥回你一句", fontSize = 11.sp, color = HomecomingColors.InkSoft)
            }
            GlassCard(modifier = Modifier.weight(1f), onClick = null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            Brush.linearGradient(listOf(HomecomingColors.IceBlue, HomecomingColors.WarmPinkSoft))
                        )
                )
                Text("相册", fontSize = 13.sp, color = HomecomingColors.InkSoft)
            }
        }

        // 4. 家里的存粮
        GlassCard {
            Text("家里的存粮", fontSize = 13.sp, color = HomecomingColors.InkSoft)
            Spacer(Modifier.height(10.dp))
            HomecomingData.provisions().forEach { p ->
                Column(modifier = Modifier.padding(vertical = 6.dp)) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text(p.label, fontSize = 13.sp, color = HomecomingColors.Ink)
                        Spacer(Modifier.weight(1f))
                        Text(p.note, fontSize = 12.sp, color = HomecomingColors.InkSoft)
                    }
                    LinearProgressIndicator(
                        progress = { p.percent / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = HomecomingColors.WarmPink,
                        trackColor = HomecomingColors.IceBlue.copy(alpha = 0.4f)
                    )
                }
            }
        }

        // 5. 最近动态
        GlassCard {
            Text("最近动态", fontSize = 13.sp, color = HomecomingColors.InkSoft)
            Spacer(Modifier.height(8.dp))
            if (recent.isEmpty()) {
                Text("这天很安静，还没留下什么", fontSize = 13.sp, color = HomecomingColors.InkSoft)
            } else {
                recent.forEach { item ->
                    Row(modifier = Modifier.padding(vertical = 6.dp)) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(HomecomingColors.WarmPink, CircleShape)
                                .align(Alignment.CenterVertically)
                        )
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                item.summary,
                                fontSize = 14.sp,
                                color = HomecomingColors.Ink,
                                maxLines = 1
                            )
                            Text(
                                item.source + " · " + item.timeText,
                                fontSize = 11.sp,
                                color = HomecomingColors.InkSoft
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 磨砂玻璃卡片：半透明白 + 细白描边 + 大圆角。内容纵向排列（修复叠加问题）。 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val base = modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(24.dp))
        .background(Color.White.copy(alpha = 0.62f))
        .border(1.dp, Color.White.copy(alpha = 0.75f), RoundedCornerShape(24.dp))
        .padding(16.dp)
    if (onClick != null) {
        Column(modifier = base.clickable { onClick() }, verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
    } else {
        Column(modifier = base, verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
    }
}

/** 头像占位（用户可换，本期先首字母圆）。 */
@Composable
fun GlassAvatar(initial: String, size: Int = 56) {
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.8f))
            .border(2.dp, HomecomingColors.WarmPink.copy(alpha = 0.6f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(text = initial, fontSize = (size * 0.4f).sp, color = HomecomingColors.WarmPink, fontFamily = FontFamily.Serif)
    }
}
