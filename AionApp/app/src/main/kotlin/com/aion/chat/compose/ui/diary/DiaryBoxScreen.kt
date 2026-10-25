package com.aion.chat.compose.ui.diary

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aion.chat.compose.data.HomecomingDayStore
import com.aion.chat.compose.data.SeanDiaryStore
import com.aion.chat.compose.data.SupabaseMomentsStore
import com.aion.chat.compose.data.parseSupabaseTime
import com.aion.chat.compose.ui.theme.HomecomingColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 日记 · 折叠的信（Yuri 定稿需求合并版）：
 * - 背景：页面透明，直接透出全局主页背景
 * - 列表：Z 字折叠的纸条堆（上面的往屏幕里倒轴心在底边、下面的轴心在顶边，相邻绕同一折痕），
 *   每张折叠卡只放那天的概述：日期 + 三行摘要 + 邮戳/回应纸条；卡片高度按屏幕自适应
 * - 点卡片：原地「折信展开」成整封信（缩放+摆正+透视落平+折痕分相淡出+文字浮现），可滚动读全文
 * - 回应：一篇一句，没回应显示邮戳「留一句」，贴上后成歪贴的暖色小纸条带完整时间戳
 *
 * 数据：sean_diary（他的信+回应）为主，旧 diary_entries/本地按天去重兜底。
 */

// ============ 可调参数 ============

private const val MAX_TILT_DEG = 64f
private const val CAMERA_DISTANCE = 10f
private const val THICKNESS_LAYERS = 4
private const val CREASE_DARKNESS = 0.16f

// ============ 色值（暖纸） ============

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

data class FoldLetter(
    val key: String,
    val cloudId: Long,      // sean_diary 行 id；0 = 旧信（不可回应）
    val dateMs: Long,
    val title: String,
    val meta: String,
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
    val overlayKey = remember { mutableStateOf<String?>(null) }
    val closeReq = remember { mutableStateOf(false) }
    val unfold = remember { Animatable(0f) }
    val editingId = remember { mutableStateOf<Long?>(null) }
    val cardGeo = remember { mutableMapOf<String, Pair<Offset, IntSize>>() }

    LaunchedEffect(Unit) {
        loading.value = true
        letters.value = loadFoldLetters(context)
        loading.value = false
    }
    LaunchedEffect(overlayKey.value) {
        if (overlayKey.value != null && !closeReq.value) {
            unfold.animateTo(1f, tween(780, easing = FastOutSlowInEasing))
        }
    }
    LaunchedEffect(closeReq.value) {
        if (closeReq.value && overlayKey.value != null) {
            unfold.animateTo(0f, tween(560, easing = FastOutSlowInEasing))
            overlayKey.value = null
            closeReq.value = false
        }
    }
    BackHandler(enabled = overlayKey.value != null || editingId.value != null) {
        if (editingId.value != null) editingId.value = null else closeReq.value = true
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val ease = smooth(unfold.value)
        // 折叠卡高度按屏幕自适应：一屏约见三张，绝不溢出
        val cardH = (maxHeight * 0.30f).coerceIn(210.dp, 320.dp)

        Column(modifier = Modifier.fillMaxSize()) {
            // 顶栏（玻璃胶囊，透出全局背景）
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 14.dp, end = 14.dp, top = 12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.5f))
                        .border(1.dp, Color.White.copy(alpha = 0.6f), CircleShape)
                        .clickable { onBack() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回",
                        tint = HomecomingColors.Ink,
                        modifier = Modifier.size(19.dp)
                    )
                }
                Text(
                    "日记 · 折叠的信",
                    fontSize = 17.sp, color = HomecomingColors.Ink, fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .padding(start = 10.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = 0.45f))
                        .padding(horizontal = 12.dp, vertical = 5.dp)
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "${letters.value.size} 封",
                    fontSize = 11.sp, color = HomecomingColors.InkSoft,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.White.copy(alpha = 0.4f))
                        .padding(horizontal = 9.dp, vertical = 4.dp)
                )
            }

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
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
                        verticalArrangement = Arrangement.spacedBy(0.dp),
                    ) {
                        itemsIndexed(letters.value, key = { _, it -> it.key }) { index, letter ->
                            SummaryFoldCard(
                                letter = letter,
                                index = index,
                                listState = listState,
                                cardH = cardH,
                                onGeometry = { pos, size -> cardGeo[letter.key] = pos to size },
                                onOpen = {
                                    if (overlayKey.value == letter.key) {
                                        closeReq.value = true
                                    } else {
                                        overlayKey.value = letter.key
                                        closeReq.value = false
                                    }
                                },
                                onStampClick = { if (letter.cloudId > 0) editingId.value = letter.cloudId }
                            )
                        }
                    }
                }
            }
        }

        // ── 展开的信：遮罩 + 信纸浮层 ──
        overlayKey.value?.let { key ->
            val letter = letters.value.firstOrNull { it.key == key } ?: return@let
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer { alpha = 0.34f * ease }
                    .background(Color(0x99000000))
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) { closeReq.value = true }
                    .pointerInput(Unit) { detectDragGestures { _, _ -> } }
            )
            LetterOverlay(
                letter = letter,
                geo = cardGeo[key],
                ease = ease,
                maxH = maxHeight - 150.dp,
                onClickCollapse = { closeReq.value = true },
                onStampClick = { if (letter.cloudId > 0) editingId.value = letter.cloudId }
            )
        }

        // 回应输入弹窗
        editingId.value?.let { id ->
            val target = letters.value.firstOrNull { it.cloudId == id }
            ReplyEditor(
                initial = target?.reply ?: "",
                onDismiss = { editingId.value = null },
                onConfirm = { text ->
                    editingId.value = null
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

private fun smooth(t: Float) = t * t * (3f - 2f * t)

// ============ 折叠概述卡：Z 字堆叠的核心 ============

@Composable
private fun SummaryFoldCard(
    letter: FoldLetter,
    index: Int,
    listState: LazyListState,
    cardH: androidx.compose.ui.unit.Dp,
    onGeometry: (Offset, IntSize) -> Unit,
    onOpen: () -> Unit,
    onStampClick: () -> Unit,
) {
    val density = LocalDensity.current
    val cardHpx = with(density) { cardH.toPx() }

    // progress：-1 = 视口上方一屏（完全倒进去） 0 = 视口中间（摊平） +1 = 下方一屏
    val progress by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val item = layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }
                ?: return@derivedStateOf if (index < listState.firstVisibleItemIndex) -1f else 1f
            val viewportCenter = (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2f
            val itemCenter = item.offset + item.size / 2f
            ((itemCenter - viewportCenter) / cardHpx).coerceIn(-1.4f, 1.4f)
        }
    }
    val tilt = (progress * MAX_TILT_DEG).coerceIn(-MAX_TILT_DEG, MAX_TILT_DEG)
    // 上面的轴心在底边、下面的轴心在顶边：相邻两封绕同一折痕，像连着的一张纸
    val pivotY = if (tilt < 0f) 1f else 0f

    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp)
            .height(cardH)
            .graphicsLayer {
                rotationX = tilt
                transformOrigin = TransformOrigin(0.5f, pivotY)
                cameraDistance = CAMERA_DISTANCE * density.density
                val shrink = 1f - abs(progress) * 0.06f
                scaleX = shrink
                scaleY = shrink
            }
            .onGloballyPositioned { onGeometry(it.positionInRoot(), it.size) }
    ) {
        // 纸面：渐变 + 折痕 + 厚度层
        Column(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(3.dp))
                .drawBehind {
                    val darken = abs(progress).coerceIn(0f, 1f) * 0.55f
                    drawRect(
                        Brush.verticalGradient(
                            0f to lerpColor(PaperLight, PaperDark, darken),
                            0.45f to lerpColor(PaperMid, PaperDark, darken),
                            1f to lerpColor(PaperMid, PaperDark, darken * 1.2f)
                        )
                    )
                    val creaseH = 12.dp.toPx()
                    drawRect(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            1f to Color.Black.copy(alpha = CREASE_DARKNESS)
                        ),
                        topLeft = Offset(0f, size.height - creaseH),
                        size = Size(size.width, creaseH)
                    )
                    for (i in 1..THICKNESS_LAYERS) {
                        val layerH = 1.5.dp.toPx()
                        drawRect(
                            color = lerpColor(PaperEdge, Color(0xFFBFB4A0), i / THICKNESS_LAYERS.toFloat()),
                            topLeft = Offset(0f, size.height - creaseH + (i - 1) * layerH),
                            size = Size(size.width, layerH)
                        )
                    }
                }
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) { onOpen() }
                .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 20.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column {
                    Text(
                        letter.dateMs.fmt("M月d日"),
                        fontSize = 22.sp, fontWeight = FontWeight.Light, color = InkMain
                    )
                    Row {
                        Text(letter.dateMs.fmt("yyyy · EEEE"), fontSize = 10.sp, color = InkSub)
                        if (letter.meta.isNotBlank()) {
                            Text(" · " + letter.meta, fontSize = 10.sp, color = InkSub)
                        }
                    }
                }
                StampOrReply(
                    reply = letter.reply,
                    repliedAtMs = letter.repliedAtMs,
                    enabled = letter.cloudId > 0,
                    onClick = onStampClick
                )
            }
            Spacer(Modifier.height(8.dp))
            Box(Modifier.width(30.dp).height(1.dp).background(InkSub.copy(alpha = 0.4f)))
            Spacer(Modifier.height(8.dp))
            Text(
                letter.content.replace("\n", " ").trim(),
                fontSize = 12.sp, lineHeight = 19.sp, color = InkMain,
                maxLines = 3, overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.weight(1f))
            Text(
                "点开这封 ›", fontSize = 10.sp, color = InkSub.copy(alpha = 0.9f),
                fontStyle = FontStyle.Italic,
                modifier = Modifier.align(Alignment.End)
            )
        }
    }
}

// ============ 展开的信：从卡片位置折信展开成整页 ============

@Composable
private fun LetterOverlay(
    letter: FoldLetter,
    geo: Pair<Offset, IntSize>?,
    ease: Float,
    maxH: androidx.compose.ui.unit.Dp,
    onClickCollapse: () -> Unit,
    onStampClick: () -> Unit
) {
    val dens = LocalDensity.current
    var measuredH by remember { mutableStateOf(0) }
    val pos = geo?.first ?: Offset(120f, 620f)
    val geoSize = geo?.second?.let { IntSize(it.width.coerceAtLeast(1), it.height.coerceAtLeast(1)) }
        ?: IntSize(420, 300)
    val fullW = with(dens) { (geoSize.width * 1.18f).toDp() }.coerceAtMost(420.dp)
    val fullWPx = with(dens) { fullW.toPx() }
    val startSx = (geoSize.width / fullWPx).coerceIn(0.3f, 1f)
    val startSy = if (measuredH > 0) (geoSize.height / measuredH.toFloat()).coerceIn(0.05f, 1f) else 1f
    val textAlpha = ((ease - 0.12f) / 0.55f).coerceIn(0f, 1f)

    Column(
        modifier = Modifier
            .offset { IntOffset(pos.x.roundToInt(), pos.y.roundToInt()) }
            .graphicsLayer {
                rotationX = 8f * (1f - ease)
                transformOrigin = TransformOrigin(0.5f, 1f)
                cameraDistance = 30f * density
            }
    ) {
        Box {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .onSizeChanged { measuredH = it.height }
                    .graphicsLayer {
                        if (measuredH == 0) alpha = 0f
                        scaleX = startSx + (1f - startSx) * ease
                        scaleY = startSy + (1f - startSy) * ease
                        transformOrigin = TransformOrigin(0f, 0f)
                    }
                    .clip(RoundedCornerShape(3.dp))
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) { onClickCollapse() }
            ) {
                // 纸面 + 展开中的折痕
                Canvas(modifier = Modifier.matchParentSize()) {
                    drawRect(
                        Brush.verticalGradient(
                            0f to PaperLight, 0.5f to PaperMid, 1f to PaperMid
                        )
                    )
                    val c1 = (1f - ease / 0.5f).coerceIn(0f, 1f)
                    val c2 = (1f - (ease - 0.42f) / 0.5f).coerceIn(0f, 1f)
                    listOf(size.height / 3f to c1, size.height * 2f / 3f to c2).forEach { (y, a) ->
                        if (a > 0.01f) {
                            drawRect(
                                Brush.verticalGradient(
                                    listOf(Color(0x00000000), Color(0x2E000000).copy(alpha = 0.55f * a), Color(0x00000000)),
                                    startY = y - size.height * 0.03f, endY = y + size.height * 0.03f
                                ),
                                topLeft = Offset(0f, y - size.height * 0.03f),
                                size = Size(size.width, size.height * 0.06f)
                            )
                            drawLine(
                                Color(0x33000000).copy(alpha = a),
                                Offset(0f, y), Offset(size.width, y), strokeWidth = 1.2f
                            )
                        }
                    }
                    drawRect(PaperEdge, size = size, style = Stroke(1f))
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = maxH)
                        .verticalScroll(rememberScrollState())
                        .graphicsLayer { alpha = textAlpha }
                        .padding(start = 22.dp, end = 22.dp, top = 20.dp, bottom = 18.dp)
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column {
                            Text(
                                letter.dateMs.fmt("M月d日"),
                                fontSize = 32.sp, fontWeight = FontWeight.Light, color = InkMain
                            )
                            Row {
                                Text(letter.dateMs.fmt("yyyy · EEEE"), fontSize = 10.sp, color = InkSub)
                                if (letter.meta.isNotBlank()) Text(" · " + letter.meta, fontSize = 10.sp, color = InkSub)
                            }
                        }
                        StampOrReply(
                            reply = letter.reply,
                            repliedAtMs = letter.repliedAtMs,
                            enabled = letter.cloudId > 0,
                            onClick = onStampClick
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Canvas(modifier = Modifier.fillMaxWidth().height(2.dp)) {
                        var x = 0f
                        while (x < size.width) {
                            drawLine(
                                Color(0x429A8A74),
                                Offset(x, size.height / 2f),
                                Offset(x + 7.dp.toPx(), size.height / 2f),
                                strokeWidth = 1.4f
                            )
                            x += 13.dp.toPx()
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    if (letter.title.isNotBlank()) {
                        Text(
                            letter.title, fontSize = 14.sp, color = InkMain,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                    }
                    Text(
                        letter.content,
                        fontSize = 13.sp, lineHeight = 24.sp, color = InkMain
                    )
                    Spacer(Modifier.height(14.dp))
                    Text(
                        "— 沈聿淮", fontSize = 11.sp, color = InkSub,
                        fontStyle = FontStyle.Italic,
                        modifier = Modifier.align(Alignment.End)
                    )
                }
            }
        }
        Text(
            "再点一下，把信折回去",
            fontSize = 10.sp, color = Color.White.copy(alpha = 0.85f),
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { alpha = ease }
                .padding(top = 10.dp)
        )
    }
}

// ============ 右上角：邮戳 or 回应纸条 ============

@Composable
private fun StampOrReply(
    reply: String,
    repliedAtMs: Long,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    if (!enabled) return
    if (reply.isBlank()) {
        Box(
            Modifier
                .size(46.dp)
                .graphicsLayer { rotationZ = -8f }
                .clickable { onClick() },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .drawBehind {
                        drawCircle(StampColor.copy(alpha = 0.55f), size.minDimension / 2f, style = Stroke(1.5.dp.toPx()))
                        drawCircle(
                            StampColor.copy(alpha = 0.35f),
                            size.minDimension / 2f - 4.dp.toPx(), style = Stroke(1.dp.toPx())
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text("留一句", fontSize = 9.sp, color = StampColor)
            }
        }
    } else {
        Box(
            Modifier
                .widthIn(max = 130.dp)
                .graphicsLayer { rotationZ = 4f }
                .clickable { onClick() }
                .drawBehind {
                    drawRect(
                        Color.Black.copy(alpha = 0.10f),
                        topLeft = Offset(2.dp.toPx(), 3.dp.toPx()), size = size
                    )
                    drawRect(ReplyPaper)
                }
                .padding(horizontal = 9.dp, vertical = 7.dp)
        ) {
            Column {
                Text(reply, fontSize = 11.sp, lineHeight = 16.sp, color = ReplyInk, maxLines = 3, overflow = TextOverflow.Ellipsis)
                if (repliedAtMs > 0) {
                    Spacer(Modifier.height(3.dp))
                    Text(
                        repliedAtMs.fmt("yyyy.M.d HH:mm"),
                        fontSize = 8.sp, color = ReplyInk.copy(alpha = 0.55f)
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
                .clickable(enabled = false) {}
                .padding(18.dp)
        ) {
            Text("留一句", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = ReplyInk)
            Spacer(Modifier.height(4.dp))
            Text("一篇只留一句。想改就再点一次。", fontSize = 10.sp, color = ReplyInk.copy(alpha = 0.6f))
            Spacer(Modifier.height(14.dp))
            BasicTextField(
                value = text,
                onValueChange = { if (it.length <= 120) text = it },
                textStyle = TextStyle(fontSize = 13.sp, lineHeight = 22.sp, color = ReplyInk),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 70.dp)
                    .background(Color.White.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                    .padding(10.dp),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "${text.length} / 120",
                fontSize = 9.sp, color = ReplyInk.copy(alpha = 0.45f),
                modifier = Modifier.align(Alignment.End)
            )
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
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

// ============ 数据合并 ============

private suspend fun loadFoldLetters(context: android.content.Context): List<FoldLetter> {
    val list = mutableListOf<FoldLetter>()
    val seenDates = mutableSetOf<String>()

    val rows = runCatching { SeanDiaryStore.fetchAll() }
        .getOrNull()?.takeIf { it.error == null }?.data.orEmpty()
    rows.forEach { r ->
        list.add(
            FoldLetter(
                key = "sd" + r.id, cloudId = r.id, dateMs = r.createdAtMs,
                title = "", meta = "", content = r.content,
                reply = r.replyContent, repliedAtMs = r.repliedAtMs
            )
        )
        seenDates.add(dayKey(r.createdAtMs))
    }

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
                key = "de" + d.id, cloudId = 0L, dateMs = ms, title = d.title,
                meta = listOf(d.weather, d.mood).filter { it.isNotBlank() }.joinToString(" · "),
                content = d.content, reply = "", repliedAtMs = 0L
            )
        )
    }

    runCatching { HomecomingDayStore.diaries(context).filter { it.author == "sean" } }
        .getOrDefault(emptyList()).forEach { d ->
            if (d.createdAt <= 0) return@forEach
            val k = dayKey(d.createdAt)
            if (k in seenDates) return@forEach
            seenDates.add(k)
            list.add(
                FoldLetter(
                    key = "lo" + d.createdAt, cloudId = 0L, dateMs = d.createdAt,
                    title = d.title, meta = "", content = d.content, reply = "", repliedAtMs = 0L
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
