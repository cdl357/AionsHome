package com.aion.chat.compose.ui.diary

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
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aion.chat.compose.data.HomecomingDayStore
import com.aion.chat.compose.data.SupabaseMomentsStore
import java.time.Instant
import java.time.LocalDate
import kotlin.math.roundToInt

/**
 * 日记 · 信（Yuri 参考图：粉缎面 + 散落信卡 + 展开的信）。
 * - 列表：粉色缎面底，信卡微微歪斜散落（种子固定），卡片只放概要：大日期 + 月份 + 三行摘要 + 蝴蝶结
 * - 点开：卡片原地展开成整封信——从卡片位置/大小长成整页信纸，折痕阴影分相淡出、卡片摆正、纸面落平，
 *   字随展开浮现；展开后是撕边信纸：大衬线日期 / from Sean + 时间 / 虚线 / 全文 / 邮戳 / 底层信纸探边
 * - 再点一下（或点空白处/系统返回）反向折回
 */
@Composable
fun DiaryBoxScreen(onBack: () -> Unit = {}) {
    val context = LocalContext.current
    val letters = remember { mutableStateOf<List<Letter>>(emptyList()) }
    val loading = remember { mutableStateOf(true) }
    val overlayKey = remember { mutableStateOf<String?>(null) }
    val closeReq = remember { mutableStateOf(false) }
    val unfold = remember { Animatable(0f) }
    // 每张卡的信纸几何（px），点开时用作展开锚点
    val cardGeo = remember { mutableMapOf<String, Pair<Offset, IntSize>>() }

    LaunchedEffect(Unit) {
        loading.value = true
        letters.value = loadLetters(context)
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
    BackHandler(enabled = overlayKey.value != null) { closeReq.value = true }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFFF7E6EB), Color(0xFFF1D3DD), Color(0xFFECC7D5))))
    ) {
        val ease = smooth(unfold.value)

        // ── 粉缎面质感：斜向光泽带 + 高光斑 + 细噪点 ──
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            drawRect(
                Brush.linearGradient(
                    listOf(Color(0x00FFFFFF), Color(0x30FFFFFF), Color(0x00FFFFFF)),
                    start = Offset(w * 0.1f, 0f), end = Offset(w * 0.55f, h)
                )
            )
            drawRect(
                Brush.linearGradient(
                    listOf(Color(0x00000000), Color(0x14C98AA8), Color(0x00000000)),
                    start = Offset(w * 0.7f, 0f), end = Offset(w, h * 0.9f)
                )
            )
            drawCircle(
                Brush.radialGradient(
                    listOf(Color(0x2EFFFFFF), Color(0x00FFFFFF)),
                    center = Offset(w * 0.25f, h * 0.12f), radius = w * 0.55f
                ),
                radius = w * 0.55f, center = Offset(w * 0.25f, h * 0.12f)
            )
            val rnd = java.util.Random(7L)
            repeat(90) {
                drawCircle(
                    color = Color(0x0AC98AA8),
                    radius = 0.6f + rnd.nextFloat() * 1.0f,
                    center = Offset(rnd.nextFloat() * w, rnd.nextFloat() * h)
                )
            }
        }

        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 14.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回",
                    tint = Color(0xFF8A5A6A),
                    modifier = Modifier
                        .size(24.dp)
                        .clickable {
                            if (overlayKey.value != null) closeReq.value = true else onBack()
                        }
                )
                Column(modifier = Modifier.weight(1f).padding(start = 10.dp)) {
                    Text("日记 · 信", fontSize = 22.sp, color = Color(0xFF7A4656), fontWeight = FontWeight.Medium)
                    Text("点开一封，慢慢读", fontSize = 11.sp, color = Color(0xFFB08A98))
                }
                Text(
                    "Letters",
                    fontSize = 18.sp, color = Color(0xFFD9AEBE),
                    fontFamily = FontFamily.Serif, fontStyle = FontStyle.Italic
                )
            }

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when {
                    loading.value -> Text(
                        "拆信中…", fontSize = 12.sp, color = Color(0xFFB08A98),
                        modifier = Modifier.align(Alignment.Center)
                    )
                    letters.value.isEmpty() -> Text(
                        "还没有信——和他说上一段话，晚安之后就会有第一封",
                        fontSize = 13.sp, color = Color(0xFF9A7080),
                        lineHeight = 22.sp, textAlign = TextAlign.Center,
                        modifier = Modifier.align(Alignment.Center).padding(horizontal = 40.dp)
                    )
                    else -> LazyVerticalStaggeredGrid(
                        columns = StaggeredGridCells.Fixed(2),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 116.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalItemSpacing = 14.dp
                    ) {
                        items(letters.value, key = { it.date + it.createdAt }) { letter ->
                            LetterCard(
                                letter = letter,
                                onGeometry = { pos, size -> cardGeo[letter.date] = pos to size },
                                onClick = {
                                    if (overlayKey.value == letter.date) {
                                        closeReq.value = true
                                    } else {
                                        overlayKey.value = letter.date
                                        closeReq.value = false
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }

        // ── 展开的信：遮罩 + 信纸浮层（点空白收起；遮罩挡住列表滚动） ──
        overlayKey.value?.let { key ->
            val letter = letters.value.firstOrNull { it.date == key } ?: return@let
            val geo = cardGeo[key]
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer { alpha = 0.32f * ease }
                    .background(Color(0x66000000))
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) { closeReq.value = true }
                    .pointerInput(Unit) { detectDragGestures { _, _ -> } }
            )
            LetterOverlay(
                letter = letter,
                geo = geo,
                ease = ease,
                fullW = maxWidth - 32.dp,
                maxH = maxHeight - 150.dp,
                onClickCollapse = { closeReq.value = true }
            )
        }
    }
}

private fun smooth(t: Float) = t * t * (3f - 2f * t)

// ── 配色 ──

private val Paper = Color(0xFFFDFBF6)
private val PaperEdge = Color(0x1AA08A78)
private val InkBrown = Color(0xFF6B5240)
private val InkSoft = Color(0xFFA08A78)
private val RoseRed = Color(0xFFA96A6A)
private val RoseDeep = Color(0xFF8E4A4A)
private val SealRed = Color(0xFFC97B7B)
private val SealDark = Color(0xFF9E5454)

/** 稳定伪随机（歪斜角度/错位固定，不随重组变化）。 */
private fun seedRand(id: Long, salt: Int): Float {
    var x = id * 2654435761L + salt * 40503L
    x = x xor (x ushr 13); x *= 1274126177L; x = x xor (x ushr 16)
    return ((x and 0x7fffffffL) % 10000L) / 10000f
}

/**
 * 收起态信卡：蝴蝶结压顶 + 大日期 + 月份 + 三行摘要 + 展开提示。
 * 位置尺寸通过 onGloballyPositioned 上报，供展开浮层作锚点。
 */
@Composable
private fun LetterCard(
    letter: Letter,
    onGeometry: (Offset, IntSize) -> Unit,
    onClick: () -> Unit
) {
    val rot = (seedRand(letter.createdAt, 1) - 0.5f) * 8f        // ±4°
    val dx = 6.dp * (seedRand(letter.createdAt, 2) - 0.5f)
    val date = remember(letter.date) { runCatching { LocalDate.parse(letter.date) }.getOrNull() }

    Box(
        modifier = Modifier
            .padding(top = 8.dp)
            .offset(x = dx)
            .graphicsLayer {
                rotationZ = rot
                cameraDistance = 24f * density
                shadowElevation = 4f
            }
    ) {
        // 蝴蝶结（压在卡片顶边）
        Canvas(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = (-7).dp)
                .size(width = 27.dp, height = 14.dp)
        ) {
            val cx = size.width / 2f
            val cy = size.height * 0.42f
            // 两个圈
            drawOval(
                SealRed, topLeft = Offset(size.width * 0.04f, cy - size.height * 0.34f),
                size = androidx.compose.ui.geometry.Size(size.width * 0.38f, size.height * 0.72f),
                style = Stroke(width = size.height * 0.30f)
            )
            drawOval(
                SealRed, topLeft = Offset(size.width * 0.58f, cy - size.height * 0.34f),
                size = androidx.compose.ui.geometry.Size(size.width * 0.38f, size.height * 0.72f),
                style = Stroke(width = size.height * 0.30f)
            )
            // 结心 + 两条飘带
            drawCircle(SealDark, radius = size.height * 0.24f, center = Offset(cx, cy + 1f))
            drawLine(
                SealRed, Offset(cx - 2f, cy + 2f),
                Offset(cx - size.width * 0.16f, size.height),
                strokeWidth = size.height * 0.20f, cap = StrokeCap.Round
            )
            drawLine(
                SealRed, Offset(cx + 2f, cy + 2f),
                Offset(cx + size.width * 0.18f, size.height),
                strokeWidth = size.height * 0.20f, cap = StrokeCap.Round
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .onGloballyPositioned { onGeometry(it.positionInRoot(), it.size) }
                .clip(RoundedCornerShape(2.dp))
                .background(Paper)
                .border(0.6.dp, PaperEdge, RoundedCornerShape(2.dp))
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) { onClick() }
                .padding(horizontal = 12.dp, vertical = 12.dp)
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Column {
                    Text(
                        date?.dayOfMonth?.toString() ?: letter.date.takeLast(2),
                        fontSize = 30.sp, color = InkBrown,
                        fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold
                    )
                    Text(
                        date?.let { it.month.name.take(3).uppercase() } ?: "",
                        fontSize = 10.sp, color = InkSoft,
                        letterSpacing = 2.sp, fontFamily = FontFamily.Serif
                    )
                    Box(
                        Modifier
                            .padding(top = 3.dp)
                            .width(24.dp)
                            .height(1.dp)
                            .background(InkSoft.copy(alpha = 0.5f))
                    )
                }
                Spacer(Modifier.weight(1f))
            }
            Spacer(Modifier.height(7.dp))
            Text(
                letter.content.replace("\n", " ").trim(),
                fontSize = 11.sp, color = Color(0xFF7A6A5C),
                lineHeight = 17.sp, maxLines = 3, overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(5.dp))
            Text(
                "展开这封 ›", fontSize = 9.sp, color = RoseRed, fontFamily = FontFamily.Serif
            )
        }
    }
}

/**
 * 展开的信：从卡片位置/大小长成整页信纸。
 * - 外层：歪斜摆正 + 轻微透视落平；内层：从卡片尺寸缩放到整页（左上角锚定）
 * - 折痕两道分相淡出 + 展开中底边移动阴影；文字随展开浮现
 */
@Composable
private fun LetterOverlay(
    letter: Letter,
    geo: Pair<Offset, IntSize>?,
    ease: Float,
    fullW: androidx.compose.ui.unit.Dp,
    maxH: androidx.compose.ui.unit.Dp,
    onClickCollapse: () -> Unit
) {
    val dens = LocalDensity.current
    var measuredH by remember { mutableStateOf(0) }
    val pos = geo?.first ?: Offset(120f, 620f)
    val geoSize = geo?.second?.let { IntSize(it.width.coerceAtLeast(1), it.height.coerceAtLeast(1)) }
        ?: IntSize(420, 300)
    val fullWPx = with(dens) { fullW.toPx() }
    val startSx = (geoSize.width / fullWPx).coerceIn(0.15f, 1f)
    val startSy = if (measuredH > 0) (geoSize.height / measuredH.toFloat()).coerceIn(0.04f, 1f) else 1f
    val rot = (seedRand(letter.createdAt, 1) - 0.5f) * 8f
    val date = remember(letter.date) { runCatching { LocalDate.parse(letter.date) }.getOrNull() }
    val textAlpha = ((ease - 0.12f) / 0.55f).coerceIn(0f, 1f)

    Column(
        modifier = Modifier
            .offset { IntOffset(pos.x.roundToInt(), pos.y.roundToInt()) }
            .graphicsLayer {
                rotationZ = rot * (1f - ease)
                rotationX = 7f * (1f - ease)
                transformOrigin = TransformOrigin(0.5f, 1f)
                cameraDistance = 30f * density
            }
    ) {
        Box {
            // 底层信纸（右上探边）
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 5.dp, y = 8.dp)
                    .size(width = fullW * 0.97f, height = 96.dp)
                    .graphicsLayer { rotationZ = 1.8f; alpha = 0.9f * ease }
                    .background(Color(0xFFF4EDE2))
            )
            // 信纸本体
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
                    .clip(RoundedCornerShape(2.dp))
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) { onClickCollapse() }
            ) {
                // 纸面：撕边 + 折痕 + 底边影
                Canvas(modifier = Modifier.matchParentSize()) {
                    val w = size.width
                    val h = size.height.toFloat()
                    val jag = 9f
                    val step = w / 15f
                    val shadow = Path().apply {
                        moveTo(0f, h)
                        var x = 0f; var up = true
                        lineTo(0f, if (up) jag else jag * 0.4f)
                        while (x < w) {
                            lineTo(x + step / 2f, if (up) jag else jag * 0.4f)
                            x += step; up = !up
                        }
                        lineTo(w, h); close()
                    }
                    translate(left = 0f, top = 4.5f) { drawPath(shadow, Color(0x16000000)) }
                    drawPath(shadow, Paper)
                    // 折痕（两道，分相淡出）
                    val c1 = (1f - ease / 0.5f).coerceIn(0f, 1f)
                    val c2 = (1f - (ease - 0.42f) / 0.5f).coerceIn(0f, 1f)
                    listOf(h / 3f to c1, h * 2f / 3f to c2).forEach { (y, a) ->
                        if (a > 0.01f) {
                            drawRect(
                                Brush.verticalGradient(
                                    listOf(Color(0x00000000), Color(0x2E000000).copy(alpha = 0.55f * a), Color(0x00000000)),
                                    startY = y - h * 0.035f, endY = y + h * 0.035f
                                ),
                                topLeft = Offset(0f, y - h * 0.035f),
                                size = androidx.compose.ui.geometry.Size(w, h * 0.07f)
                            )
                            drawLine(
                                Color(0x33000000).copy(alpha = a),
                                Offset(0f, y), Offset(w, y), strokeWidth = 1.2f
                            )
                        }
                    }
                    // 展开过程中的底边影（折着的纸向下摊开）
                    if (ease < 0.97f) {
                        val a = (1f - ease) * 0.22f
                        drawRect(
                            Brush.verticalGradient(
                                listOf(Color(0x00000000), Color(0x52000000).copy(alpha = a)),
                                startY = h - 22f, endY = h
                            ),
                            topLeft = Offset(0f, h - 22f),
                            size = androidx.compose.ui.geometry.Size(w, 22f)
                        )
                    }
                    drawRect(PaperEdge, size = androidx.compose.ui.geometry.Size(w, h), style = Stroke(1f))
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = maxH)
                        .verticalScroll(rememberScrollState())
                        .graphicsLayer { alpha = textAlpha }
                        .padding(top = 18.dp, start = 16.dp, end = 16.dp, bottom = 16.dp)
                ) {
                    Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
                        Column {
                            Text(
                                date?.dayOfMonth?.toString() ?: letter.date.takeLast(2),
                                fontSize = 42.sp, color = InkBrown,
                                fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold
                            )
                            Text(
                                date?.let { it.month.name.take(3).uppercase() } ?: "",
                                fontSize = 10.sp, color = InkSoft,
                                letterSpacing = 3.sp, fontFamily = FontFamily.Serif
                            )
                            Box(
                                Modifier
                                    .padding(top = 4.dp)
                                    .width(28.dp)
                                    .height(1.dp)
                                    .background(InkSoft.copy(alpha = 0.5f))
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        Postmark(letter.date)
                    }
                    Text(
                        "from Sean",
                        fontSize = 14.sp, color = RoseRed,
                        fontFamily = FontFamily.Serif, fontStyle = FontStyle.Italic,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    Text(letter.date.replace("-", "."), fontSize = 10.sp, color = InkSoft)
                    Spacer(Modifier.height(9.dp))
                    Canvas(modifier = Modifier.fillMaxWidth().height(2.dp)) {
                        var x = 0f
                        while (x < size.width) {
                            drawLine(
                                Color(0x425A4C40),
                                Offset(x, size.height / 2f),
                                Offset(x + 7.dp.toPx(), size.height / 2f),
                                strokeWidth = 1.4f
                            )
                            x += 13.dp.toPx()
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    if (letter.title.isNotBlank()) {
                        Text(
                            letter.title, fontSize = 14.sp, color = InkBrown,
                            fontWeight = FontWeight.Medium, fontFamily = FontFamily.Serif,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                    }
                    Text(
                        letter.content,
                        fontSize = 13.sp, color = Color(0xFF5A4C40),
                        lineHeight = 24.sp, fontFamily = FontFamily.Serif
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "—— Sean", fontSize = 11.sp, color = InkSoft,
                        fontFamily = FontFamily.Serif, fontStyle = FontStyle.Italic,
                        textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
        Text(
            "再点一下，把信折回去",
            fontSize = 10.sp, color = Color(0xFF9A7080),
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { alpha = ease }
                .padding(top = 10.dp)
        )
    }
}

/** 圆形邮戳：双环 + 波纹 + 日期。 */
@Composable
private fun Postmark(date: String) {
    Box(modifier = Modifier.size(56.dp)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val c = center
            val r = size.minDimension / 2.35f
            drawCircle(RoseDeep.copy(alpha = 0.8f), radius = r, center = c, style = Stroke(2.4f))
            drawCircle(RoseDeep.copy(alpha = 0.55f), radius = r * 0.76f, center = c, style = Stroke(1.4f))
            repeat(3) { i ->
                val y = c.y + r * 0.45f + i * r * 0.34f
                drawLine(
                    RoseDeep.copy(alpha = 0.5f),
                    Offset(c.x - r * 1.85f, y), Offset(c.x - r * 0.1f, y),
                    strokeWidth = 2f
                )
            }
        }
        Text(
            date.takeLast(5).replace("-", "."),
            fontSize = 8.sp, color = RoseDeep, fontFamily = FontFamily.Serif,
            modifier = Modifier.align(Alignment.Center)
        )
    }
}

// ── 数据 ──

data class Letter(
    val date: String,
    val title: String,
    val content: String,
    val weather: String,
    val mood: String,
    val createdAt: Long
)

private suspend fun loadLetters(context: android.content.Context): List<Letter> {
    val remote = runCatching { SupabaseMomentsStore.fetchSeanDiaries() }
        .getOrNull()
        ?.takeIf { it.error == null }
        ?.data
        .orEmpty()
        .map { d ->
            val (date, ms) = resolveDate(d.createdAt, d.createdAtMs)
            Letter(date, d.title, d.content, d.weather, d.mood, ms)
        }
    val local = HomecomingDayStore.diaries(context)
        .filter { it.author == "sean" }
        .map { d ->
            val (date, _) = resolveDate("", d.createdAt)
            Letter(date, d.title, d.content, "", "", d.createdAt)
        }
    val remoteDates = remote.map { it.date }.toSet()
    val localOnly = local.filter { it.date !in remoteDates }
    return (remote + localOnly).sortedByDescending { it.createdAt }
}

private fun resolveDate(iso: String, ms: Long): Pair<String, Long> {
    val millis = if (ms > 0) ms
    else runCatching { Instant.parse(iso).toEpochMilli() }.getOrElse { 0L }
    val date = if (millis > 0)
        Instant.ofEpochMilli(millis).atZone(java.time.ZoneId.systemDefault()).toLocalDate().toString()
    else iso.take(10)
    return date to millis
}
