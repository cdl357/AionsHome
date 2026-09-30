package com.aion.chat.compose.ui.home

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aion.chat.compose.data.HomecomingData
import com.aion.chat.compose.ui.theme.HomecomingColors
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.materials.HazeMaterials
import dev.chrisbanes.haze.rememberHazeState

/** 回家页液态玻璃统一参数（死规矩四：全页同材质、同描边、同圆角）。 */
private val GlassShape = RoundedCornerShape(24.dp)
private val GlassEdge = Brush.linearGradient(
    listOf(Color.White.copy(alpha = 0.55f), Color.White.copy(alpha = 0.10f))
)

/** 玻璃卡文字（死规矩三）：统一白色系 + 轻阴影浮起，不靠底色盒子。 */
private fun glassText(
    alpha: Float = 1f,
    size: Int = 14,
    weight: FontWeight = FontWeight.Normal,
    serif: Boolean = false,
    spacing: Int = 0,
    color: Color? = null,
): TextStyle = TextStyle(
    color = color ?: Color.White.copy(alpha = alpha),
    fontSize = size.sp,
    fontWeight = weight,
    fontFamily = if (serif) FontFamily.Serif else null,
    letterSpacing = spacing.sp,
    shadow = Shadow(Color.Black.copy(alpha = 0.35f), offset = Offset(0f, 1f), blurRadius = 5f),
)

/** 页面①：回家（中心首页）。Haze 液态玻璃：折射 + 通透 + 边缘高光；布局严格六项，不自加卡。 */
@Composable
fun HomeScreen() {
    val context = LocalContext.current
    val hazeState = rememberHazeState()
    var recent by remember { mutableStateOf(listOf<HomecomingData.FeedItem>()) }

    LaunchedEffect(Unit) {
        recent = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            HomecomingData.loadRecent(context)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // 背景大图：玻璃折射的源（hazeSource）。保持清晰，朦胧感只发生在玻璃卡里。
        Box(
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(hazeState)
        ) {
            Image(
                painter = painterResource(com.aion.chat.R.drawable.bg_home_meadow),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White.copy(alpha = 0.06f))
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 1~3. 顶部卡：Yuri/Sean 头像+名字+连接符号 ｜ 在一起天数 ｜ 今日情话
            GlassCard(hazeState = hazeState, contentPadding = 18.dp) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                        GlassAvatar(initial = "Y")
                        Text("Yuri", style = glassText(alpha = 0.95f, size = 13), modifier = Modifier.padding(top = 6.dp))
                    }
                    Text("∞", style = glassText(alpha = 0.9f, size = 22), modifier = Modifier.padding(horizontal = 10.dp))
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                        GlassAvatar(initial = "S")
                        Text("Sean", style = glassText(alpha = 0.95f, size = 13), modifier = Modifier.padding(top = 6.dp))
                    }
                }
                Text(
                    "ALREADY TOGETHER",
                    style = glassText(alpha = 0.75f, size = 10, weight = FontWeight.Medium, spacing = 3),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(HomecomingData.daysTogether().toString(), style = glassText(size = 52, weight = FontWeight.Light, serif = true))
                    Text("天", style = glassText(alpha = 0.9f, size = 16), modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))
                }
                Text(
                    "since " + HomecomingData.since(),
                    style = glassText(alpha = 0.75f, size = 12),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    HomecomingData.quoteForToday(),
                    style = glassText(alpha = 0.95f, size = 14),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // 4. 一起听歌：专辑封面缩图 + 歌名 + 播放/暂停（本期 UI + 控件占位）
            GlassCard(hazeState = hazeState, contentPadding = 14.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(GlassShape)
                            .background(Color.White.copy(alpha = 0.14f))
                    )
                    Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                        Text("一起听歌", style = glassText(size = 15, weight = FontWeight.Medium))
                        Text("歌单随后端接入", style = glassText(alpha = 0.8f, size = 12))
                    }
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = "播放",
                        tint = Color.White,
                        modifier = Modifier.size(30.dp)
                    )
                }
            }

            // 5. 今日心情 | 相册（两个半宽玻璃卡，等高）
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(androidx.compose.foundation.layout.IntrinsicSize.Min)
            ) {
                GlassCard(hazeState = hazeState, modifier = Modifier.weight(1f).fillMaxHeight(), contentPadding = 14.dp) {
                    Text("今日心情", style = glassText(alpha = 0.8f, size = 13))
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
                                        style = glassText(alpha = 0.95f, size = 14),
                                        modifier = Modifier.clickable {
                                            Toast.makeText(context, "点一个，哥哥回你一句（AI 回应随后端接线开放）", Toast.LENGTH_SHORT).show()
                                        }
                                    )
                                }
                            }
                        }
                    Text("点一个，哥哥回你一句", style = glassText(alpha = 0.75f, size = 11))
                }
                GlassCard(hazeState = hazeState, modifier = Modifier.weight(1f).fillMaxHeight(), contentPadding = 14.dp) {
                    Text("相册", style = glassText(size = 15, weight = FontWeight.Medium))
                    Text("照片墙 · Sean 的心里话", style = glassText(alpha = 0.8f, size = 12))
                }
            }

            // 6. 家里的存粮：三根细进度条（数值随后端接入，接不到显示占位）
            GlassCard(hazeState = hazeState, contentPadding = 14.dp) {
                Text("家里的存粮", style = glassText(alpha = 0.8f, size = 13))
                HomecomingData.provisions().forEach { p ->
                    Column(modifier = Modifier.padding(vertical = 2.dp)) {
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Text(p.label, style = glassText(alpha = 0.95f, size = 13))
                            Spacer(Modifier.weight(1f))
                            Text(p.note, style = glassText(alpha = 0.7f, size = 11))
                        }
                        LinearProgressIndicator(
                            progress = { p.percent / 100f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp)),
                            color = Color.White.copy(alpha = 0.85f),
                            trackColor = Color.White.copy(alpha = 0.25f)
                        )
                    }
                }
            }

            // 6. 最近动态（最近日记 / 留言 / 照片）
            GlassCard(hazeState = hazeState) {
                Text("最近动态", style = glassText(alpha = 0.75f, size = 13))
                Spacer(Modifier.height(2.dp))
                if (recent.isEmpty()) {
                    Text("这天很安静，还没留下什么", style = glassText(alpha = 0.85f, size = 13))
                } else {
                    recent.forEach { item ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 5.dp)
                        ) {
                            Text("•", style = glassText(size = 15))
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(
                                    item.summary,
                                    style = glassText(alpha = 0.95f, size = 14),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(item.source + " · " + item.timeText, style = glassText(alpha = 0.7f, size = 11))
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 液态玻璃卡（Haze ultraThin）：折射底 + 统一边缘高光。内容直接放在卡上（死规矩一/二）。 */
@Composable
fun GlassCard(
    hazeState: HazeState,
    modifier: Modifier = Modifier,
    contentPadding: Dp = 16.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val core = modifier
        .fillMaxWidth()
        .clip(GlassShape)
        .hazeEffect(state = hazeState, style = HazeMaterials.ultraThin())
        .border(1.dp, GlassEdge, GlassShape)
        .padding(horizontal = 16.dp, vertical = contentPadding)
    Column(
        modifier = if (onClick != null) core.clickable(onClick = onClick) else core,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content
    )
}

/** 旧版白霜卡（更多页/我们页沿用，本期不动）：半透明白底 + 描边，不依赖 Haze。 */
@Composable
fun FrostCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(24.dp)
    val edgeBrush = Brush.linearGradient(
        listOf(Color.White.copy(alpha = 0.70f), Color.White.copy(alpha = 0.15f))
    )
    val base = modifier
        .fillMaxWidth()
        .clip(shape)
        .background(Color.White.copy(alpha = 0.12f))
        .border(1.dp, edgeBrush, shape)
        .padding(horizontal = 16.dp, vertical = 14.dp)
    Column(
        modifier = if (onClick != null) base.clickable(onClick = onClick) else base,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content
    )
}


/** 头像占位（用户可换；先以透明白底 + 首字母占位，白色系贴玻璃）。 */
@Composable
fun GlassAvatar(initial: String, size: Int = 56) {
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.18f))
            .border(2.dp, Color.White.copy(alpha = 0.80f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(text = initial, fontSize = (size * 0.4f).sp, color = Color.White, fontFamily = FontFamily.Serif)
    }
}
