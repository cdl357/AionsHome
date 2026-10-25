package com.aion.chat.compose.ui.diary

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aion.chat.compose.data.HomecomingDayStore
import com.aion.chat.compose.data.SeanDiaryStore
import com.aion.chat.compose.data.SupabaseMomentsStore
import com.aion.chat.compose.data.parseSupabaseTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs

/**
 * 日记 · Z 字折叠的信（交接文档《日记页-Z字折叠信》）：
 * 一长条纸折成 Z 字，中间那封摊平正对屏幕，上面的往屏幕里倒（轴心底边）、
 * 下面的往里倒（轴心顶边）——相邻两封绕同一条折痕，看起来是连着的一张纸。
 * 上下滑动时像手风琴一样连续折叠展开。
 *
 * 数据：sean_diary（他的信 + 回应，新表）为主，旧 diary_entries / 本地日记按天去重兜底。
 * 回应：一篇一句，贴在右上角纸条上（没回应时是邮戳「留一句」）。
 */

// ============ 可调参数（效果不对先调这里，见交接文档 §5） ============

/** 每封信的高度。决定一屏能看到几封。 */
private val LETTER_HEIGHT = 420.dp

/** 最大倾斜角度。越大倒得越狠。60-75 之间比较像那个视频。 */
private const val MAX_TILT_DEG = 68f

/** 透视强度。越小透视越夸张。 */
private const val CAMERA_DISTANCE = 10f

/** 纸叠厚度层数。画在每封信底部，模拟折叠处纸的厚度。 */
private const val THICKNESS_LAYERS = 4

/** 折痕处的暗度。0 不画。 */
private const val CREASE_DARKNESS = 0.16f

// ============ 色值 ============

private val PaperLight = Color(0xFFFBF7EE)
private val PaperMid = Color(0xFFF4EEE1)
private val PaperDark = Color(0xFFE6DECF)
private val PaperEdge = Color(0xFFD8CFBD)
private val InkMain = Color(0xFF5A4A38)
private val InkSub = Color(0xFF9A8A74)
private val ReplyPaper = Color(0xFFFDF0D8)
private val ReplyInk = Color(0xFF8A6A48)
private val StampColor = Color(0xFFC4A882)

// ============ 数据模型 ============

/**
 * 一封（折起来的）信。
 * cloudId>0 = 新表 sean_diary 的行，可贴回应；0 = 旧云端/本地日记，右上角不出邮戳。
 */
data class FoldLetter(
    val key: String,
    val cloudId: Long,
    val dateMs: Long,
    val title: String,
    val meta: String,        // 天气 · 心情（旧信才有）
    val content: String,
    val reply: String,
    val repliedAtMs: Long
)

@Composable
fun DiaryBoxScreen(onBack: () -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val letters = remember { mutableStateOf<List<FoldLetter>>(emptyList()) }
    val loading = remember { mutableStateOf(true) }
    val listState = rememberLazyListState()
    var editingId by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(Unit) {
        loading.value = true
        letters.value = loadFoldLetters(context)
        loading.value = false
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF2A2520))   // 深色底，让纸浮出来
    ) {
        when {
            loading.value -> Text(
                "展开信纸…", fontSize = 12.sp, color = InkSub,
                modifier = Modifier.align(Alignment.Center)
            )
            letters.value.isEmpty() -> Text(
                "还没有信——和他说上一段话，晚安之后就会有第一封",
                fontSize = 13.sp, color = InkSub, lineHeight = 22.sp,
                modifier = Modifier.align(Alignment.Center).padding(horizontal = 40.dp)
            )
            else -> LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 0.dp),
                // 必须是 0：纸是连着的一条，不是分开的卡片
                verticalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                itemsIndexed(letters.value, key = { _, it -> it.key }) { index, letter ->
                    FoldedLetter(
                        letter = letter,
                        index = index,
                        listState = listState,
                        onStampClick = { if (letter.cloudId > 0) editingId = letter.cloudId }
                    )
                }
            }
        }

        // 返回（浮在左上角，不打断纸条）
        Box(
            modifier = Modifier
                .padding(start = 14.dp, top = 16.dp)
                .size(34.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.10f))
                .clickable { onBack() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "返回",
                tint = PaperEdge,
                modifier = Modifier.size(20.dp)
            )
        }

        // 回应输入弹窗
        editingId?.let { id ->
            val target = letters.value.firstOrNull { it.cloudId == id }
            ReplyEditor(
                initial = target?.reply ?: "",
                onDismiss = { editingId = null },
                onConfirm = { text ->
                    editingId = null
                    // 先贴上（乐观更新），网络异步落库
                    letters.value = letters.value.map {
                        if (it.cloudId == id)
                            it.copy(reply = text.trim(), repliedAtMs = System.currentTimeMillis())
                        else it
                    }
                    scope.launch {
                        val ok = SeanDiaryStore.saveReply(id, text.trim())
                        if (!ok) withContext(Dispatchers.Main) {
                            Toast.makeText(context, "没贴上，网络一会儿再点一次重贴", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
            )
        }
    }
}

// ============ 单封信：折叠的核心 ============

@Composable
private fun FoldedLetter(
    letter: FoldLetter,
    index: Int,
    listState: LazyListState,
    onStampClick: () -> Unit,
) {
    val density = LocalDensity.current
    val letterHeightPx = with(density) { LETTER_HEIGHT.toPx() }

    // progress：-1 = 视口上方一屏（完全倒进去） 0 = 视口中间（摊平） +1 = 下方一屏
    val progress by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val item = layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }
                ?: return@derivedStateOf if (index < listState.firstVisibleItemIndex) -1f else 1f
            val viewportCenter =
                (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2f
            val itemCenter = item.offset + item.size / 2f
            ((itemCenter - viewportCenter) / letterHeightPx).coerceIn(-1.4f, 1.4f)
        }
    }

    val tilt = (progress * MAX_TILT_DEG).coerceIn(-MAX_TILT_DEG, MAX_TILT_DEG)

    // 关键：轴心跟着倾斜方向变——上方（往里倒）轴心在底边，下方轴心在顶边。
    // 相邻两封绕同一条折痕，看起来才是连着的一张纸。
    val pivotY = if (tilt < 0f) 1f else 0f

    Box(
        Modifier
            .fillMaxWidth()
            .height(LETTER_HEIGHT)
            .graphicsLayer {
                rotationX = tilt
                transformOrigin = TransformOrigin(0.5f, pivotY)
                cameraDistance = CAMERA_DISTANCE * density.density
                // 倒得越狠稍微缩一点，加强纵深
                val shrink = 1f - abs(progress) * 0.06f
                scaleX = shrink
                scaleY = shrink
            }
    ) {
        LetterPaper(
            letter = letter,
            tiltAbs = abs(progress).coerceIn(0f, 1f),
            onStampClick = onStampClick,
        )
    }
}

// ============ 纸面：底色 + 厚度 + 折痕 + 内容 ============

@Composable
private fun LetterPaper(
    letter: FoldLetter,
    tiltAbs: Float,          // 0 = 摊平，1 = 完全倒下
    onStampClick: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .drawBehind {
                // ① 纸面渐变：摊平时亮，倒下去时整体压暗（背光）
                val darken = tiltAbs * 0.55f
                val top = lerpColor(PaperLight, PaperDark, darken)
                val mid = lerpColor(PaperMid, PaperDark, darken)
                val bottom = lerpColor(PaperMid, PaperDark, darken * 1.2f)
                drawRect(
                    brush = Brush.verticalGradient(
                        0f to top,
                        0.45f to mid,
                        1f to bottom,
                    )
                )

                // ② 底部折痕：一道横向暗带，暗示这里要折过去
                val creaseH = 14.dp.toPx()
                drawRect(
                    brush = Brush.verticalGradient(
                        0f to Color.Transparent,
                        1f to Color.Black.copy(alpha = CREASE_DARKNESS),
                    ),
                    topLeft = Offset(0f, size.height - creaseH),
                    size = Size(size.width, creaseH),
                )

                // ③ 纸叠厚度：折痕下方几层，颜色递深
                for (i in 1..THICKNESS_LAYERS) {
                    val layerH = 1.5.dp.toPx()
                    val y = size.height - creaseH + (i - 1) * layerH
                    drawRect(
                        color = lerpColor(PaperEdge, Color(0xFFBFB4A0), i / THICKNESS_LAYERS.toFloat()),
                        topLeft = Offset(0f, y),
                        size = Size(size.width, layerH),
                    )
                }
            }
    ) {
        // 内容：倒下去时淡出，省得看到挤变形的字
        val contentAlpha = (1f - tiltAbs * 1.5f).coerceIn(0f, 1f)

        Column(
            Modifier
                .fillMaxSize()
                .padding(start = 26.dp, end = 26.dp, top = 24.dp, bottom = 34.dp)
                .graphicsLayer { alpha = contentAlpha }
        ) {
            // 顶部行：日期（左） + 邮戳/回应（右上角）
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column {
                    Text(
                        text = letter.dateMs.fmt("M月d日"),
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Light,
                        color = InkMain,
                    )
                    Text(
                        text = letter.dateMs.fmt("yyyy · EEEE"),
                        fontSize = 10.sp,
                        color = InkSub,
                    )
                    if (letter.meta.isNotBlank()) {
                        Text(letter.meta, fontSize = 10.sp, color = InkSub.copy(alpha = 0.8f))
                    }
                }

                StampOrReply(
                    reply = letter.reply,
                    repliedAtMs = letter.repliedAtMs,
                    enabled = letter.cloudId > 0,
                    onClick = onStampClick,
                )
            }

            Spacer(Modifier.height(18.dp))

            Box(
                Modifier
                    .width(36.dp)
                    .height(1.dp)
                    .background(InkSub.copy(alpha = 0.4f))
            )

            Spacer(Modifier.height(16.dp))

            if (letter.title.isNotBlank()) {
                Text(
                    text = letter.title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = InkMain,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
            }

            Text(
                text = letter.content,
                fontSize = 13.sp,
                lineHeight = 25.sp,
                color = InkMain,
                maxLines = 9,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(Modifier.weight(1f))

            Text(
                text = "— 沈聿淮",
                fontSize = 11.sp,
                fontStyle = FontStyle.Italic,
                color = InkSub,
                modifier = Modifier.align(Alignment.End),
            )
        }
    }
}

// ============ 右上角：邮戳 or 小鑫的回应纸条 ============

@Composable
private fun StampOrReply(
    reply: String,
    repliedAtMs: Long,
    enabled: Boolean,        // 只有 sean_diary 的信可贴回应
    onClick: () -> Unit,
) {
    if (!enabled) return   // 旧信不出邮戳，也不要"暂无回应"
    if (reply.isBlank()) {
        // 没回应：邮戳，点了可以写
        Box(
            Modifier
                .size(54.dp)
                .graphicsLayer { rotationZ = -8f }
                .clickable { onClick() },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .drawBehind {
                        drawCircle(
                            color = StampColor.copy(alpha = 0.55f),
                            radius = size.minDimension / 2f,
                            style = Stroke(1.5.dp.toPx()),
                        )
                        drawCircle(
                            color = StampColor.copy(alpha = 0.35f),
                            radius = size.minDimension / 2f - 4.dp.toPx(),
                            style = Stroke(1.dp.toPx()),
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "留一句",
                    fontSize = 9.sp,
                    color = StampColor,
                )
            }
        }
    } else {
        // 有回应：一张歪着贴的小纸条
        Box(
            Modifier
                .widthIn(max = 150.dp)
                .graphicsLayer { rotationZ = 4f }
                .clickable { onClick() }
                .drawBehind {
                    // 纸条底 + 轻微阴影
                    drawRect(
                        color = Color.Black.copy(alpha = 0.10f),
                        topLeft = Offset(2.dp.toPx(), 3.dp.toPx()),
                        size = size,
                    )
                    drawRect(color = ReplyPaper)
                }
                .padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            Column {
                Text(
                    text = reply,
                    fontSize = 11.sp,
                    lineHeight = 17.sp,
                    color = ReplyInk,
                )
                if (repliedAtMs > 0) {
                    Spacer(Modifier.height(4.dp))
                    // 完整日期，不要"3天前"——要看得出隔了多久回头看的
                    Text(
                        text = repliedAtMs.fmt("yyyy.M.d HH:mm"),
                        fontSize = 8.sp,
                        color = ReplyInk.copy(alpha = 0.55f),
                    )
                }
            }
        }
    }
}

// ============ 回应输入弹窗 ============

@Composable
private fun ReplyEditor(
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by remember { mutableStateOf(initial) }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable { onDismiss() },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .fillMaxWidth(0.84f)
                .clip(RoundedCornerShape(14.dp))
                .background(ReplyPaper)
                .clickable(enabled = false) {}   // 吃掉点击，别穿透关闭
                .padding(18.dp)
        ) {
            Text(
                text = "留一句",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = ReplyInk,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "一篇只留一句。想改就再点一次。",
                fontSize = 10.sp,
                color = ReplyInk.copy(alpha = 0.6f),
            )

            Spacer(Modifier.height(14.dp))

            BasicTextField(
                value = text,
                onValueChange = { if (it.length <= 120) text = it },
                textStyle = TextStyle(
                    fontSize = 13.sp,
                    lineHeight = 22.sp,
                    color = ReplyInk,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 70.dp)
                    .background(Color.White.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                    .padding(10.dp),
            )

            Spacer(Modifier.height(6.dp))
            Text(
                text = "${text.length} / 120",
                fontSize = 9.sp,
                color = ReplyInk.copy(alpha = 0.45f),
                modifier = Modifier.align(Alignment.End),
            )

            Spacer(Modifier.height(14.dp))

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onDismiss) {
                    Text("算了", fontSize = 12.sp, color = ReplyInk.copy(alpha = 0.6f))
                }
                Spacer(Modifier.width(8.dp))
                TextButton(
                    onClick = { if (text.isNotBlank()) onConfirm(text.trim()) },
                    enabled = text.isNotBlank(),
                ) {
                    Text("贴上去", fontSize = 12.sp, color = ReplyInk)
                }
            }
        }
    }
}

// ============ 数据合并：sean_diary 为主，旧云端/本地按天去重兜底 ============

private suspend fun loadFoldLetters(context: android.content.Context): List<FoldLetter> {
    val list = mutableListOf<FoldLetter>()
    val seenDates = mutableSetOf<String>()

    // ① 新表：他的信 + 回应
    val rows = runCatching { SeanDiaryStore.fetchAll() }
        .getOrNull()?.takeIf { it.error == null }?.data.orEmpty()
    rows.forEach { r ->
        list.add(
            FoldLetter(
                key = "sd" + r.id,
                cloudId = r.id,
                dateMs = r.createdAtMs,
                title = "",
                meta = "",
                content = r.content,
                reply = r.replyContent,
                repliedAtMs = r.repliedAtMs
            )
        )
        seenDates.add(dayKey(r.createdAtMs))
    }

    // ② 旧云端 diary_entries（同日已有新表数据就跳过）
    val old = runCatching { SupabaseMomentsStore.fetchSeanDiaries() }
        .getOrNull()?.takeIf { it.error == null }?.data.orEmpty()
    old.forEach { d ->
        val ms = if (d.createdAtMs > 0) d.createdAtMs else parseSupabaseTime(d.createdAt)
        if (ms <= 0) return@forEach
        val k = dayKey(ms)
        if (k in seenDates) return@forEach
        seenDates.add(k)
        list.add(
            FoldLetter(
                key = "de" + d.id,
                cloudId = 0L,
                dateMs = ms,
                title = d.title,
                meta = listOf(d.weather, d.mood).filter { it.isNotBlank() }.joinToString(" · "),
                content = d.content,
                reply = "",
                repliedAtMs = 0L
            )
        )
    }

    // ③ 本地 sean 日记
    runCatching { HomecomingDayStore.diaries(context).filter { it.author == "sean" } }
        .getOrDefault(emptyList()).forEach { d ->
            if (d.createdAt <= 0) return@forEach
            val k = dayKey(d.createdAt)
            if (k in seenDates) return@forEach
            seenDates.add(k)
            list.add(
                FoldLetter(
                    key = "lo" + d.createdAt,
                    cloudId = 0L,
                    dateMs = d.createdAt,
                    title = d.title,
                    meta = "",
                    content = d.content,
                    reply = "",
                    repliedAtMs = 0L
                )
            )
        }

    return list.sortedByDescending { it.dateMs }
}

private fun dayKey(ms: Long): String =
    Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate().toString()

// ============ 工具 ============

private fun lerpColor(a: Color, b: Color, t: Float): Color {
    val tt = t.coerceIn(0f, 1f)
    return Color(
        red = a.red + (b.red - a.red) * tt,
        green = a.green + (b.green - a.green) * tt,
        blue = a.blue + (b.blue - a.blue) * tt,
        alpha = a.alpha + (b.alpha - a.alpha) * tt,
    )
}

private fun Long.fmt(pattern: String): String {
    if (this <= 0) return ""
    return DateTimeFormatter.ofPattern(pattern)
        .withZone(ZoneId.systemDefault())
        .format(Instant.ofEpochMilli(this))
}
