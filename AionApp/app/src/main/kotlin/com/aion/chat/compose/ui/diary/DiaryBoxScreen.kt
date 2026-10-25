package com.aion.chat.compose.ui.diary

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.aion.chat.compose.data.HomecomingDayStore
import com.aion.chat.compose.data.SupabaseMomentsStore
import com.aion.chat.compose.ui.theme.HomecomingColors
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import kotlin.math.abs

/**
 * 日记手账·摊开的厚书（按 Yuri 提供的技术文档实现）。
 * - 书壳固定：弧面翘起（贝塞尔轮廓+横向光影渐变）+ 书脊凹陷 + 页缘截面厚度 + 桌面投影 + 纸张噪点
 * - 一篇日记 = 一页：HorizontalPager 左右翻，页内上下滚动（长文读书壳不动）
 * - 月份章节页眉：只在该月第一篇显示；右侧月份索引贴（点贴纸跳当月第一篇）
 * - 翻页动效：rotationY 绕书脊侧（页面边缘）转 12° + 轻微缩放，不是卡片特效
 */
@Composable
fun DiaryBoxScreen(onBack: () -> Unit = {}) {
    val context = LocalContext.current
    val letters = remember { mutableStateOf<List<Letter>>(emptyList()) }
    val loading = remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        loading.value = true
        letters.value = loadLetters(context)
        loading.value = false
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 96.dp)
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
                Text("日记手账", fontSize = 22.sp, color = HomecomingColors.Ink, fontWeight = FontWeight.Medium)
                Text("一本摊开的厚书 · 左右翻页读每一天", fontSize = 11.sp, color = HomecomingColors.InkSoft)
            }
        }
        Spacer(Modifier.height(6.dp))

        BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (loading.value) {
                Text(
                    "翻开书中…",
                    fontSize = 12.sp, color = HomecomingColors.InkSoft,
                    modifier = Modifier.align(Alignment.Center)
                )
            } else if (letters.value.isEmpty()) {
                Text(
                    "书还是空的——\n和他说上一段话，晚安之后就会写下一页",
                    fontSize = 13.sp, color = HomecomingColors.InkSoft,
                    lineHeight = 22.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                val bookH = (maxHeight - 46.dp).coerceIn(470.dp, 660.dp)
                OpenBook(
                    letters = letters.value,
                    bookHeight = bookH,
                    modifier = Modifier.align(Alignment.TopCenter)
                )
            }
        }
    }
}

// ── 摊开的厚书 ──

// 文档 §四 色值 & §3.1 参数（抽常量方便调参）
private val DeskShadowColor = Color(0x30000000)
private val PaperEdgeLight = Color(0xFFF0E7D5)
private val PaperEdge = Color(0xFFF4ECDA)
private val PaperMain = Color(0xFFF6EFE0)
private val PaperNearSpine = Color(0xFFEDE4D2)
private val PaperSpine = Color(0xFFE8DFD0)
private val SectionL1 = Color(0xFFE2D9C9)
private val SectionL2 = Color(0xFFD8CFC0)
private val SectionL3 = Color(0xFFD0C7B7)
private val SectionL4 = Color(0xFFC8BFB0)
private val InkMain = Color(0xFF6A5A48)
private val InkDate = Color(0xFF7A6A5A)
private val InkSoft2 = Color(0xFFA89878)
private val InkAuthor = Color(0xFFBFAF9C)

private val MARGIN_H = 20.dp
private val MARGIN_V = 50.dp
private val CURL_DEPTH = 14.dp

private val MONTH_NAMES = listOf(
    "January", "February", "March", "April", "May", "June",
    "July", "August", "September", "October", "November", "December"
)
private val CHN_MONTHS = listOf(
    "一月", "二月", "三月", "四月", "五月", "六月",
    "七月", "八月", "九月", "十月", "十一月", "十二月"
)
private val CHN_WEEKDAYS = arrayOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

@Composable
private fun OpenBook(
    letters: List<Letter>,
    bookHeight: Dp,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { letters.size })
    val monthList = letters.map { it.date.take(7) }.distinct() // 列表是新→旧，月也新→旧

    Column(modifier = modifier.fillMaxWidth()) {
    Box(modifier = Modifier.fillMaxWidth().height(bookHeight)) {
        // ── 1) 桌面投影：多层圆角矩形叠加，近似软模糊（文档 3.4） ──
        Canvas(modifier = Modifier.matchParentSize()) {
            val steps = 14
            for (i in steps downTo 1) {
                val grow = (i.toFloat() / steps) * 22.dp.toPx()
                drawRoundRect(
                    color = Color.Black.copy(alpha = 0.028f),
                    topLeft = Offset(
                        MARGIN_H.toPx() - 8.dp.toPx() - grow,
                        MARGIN_V.toPx() - 4.dp.toPx() - grow * 0.6f
                    ),
                    size = Size(
                        size.width - (MARGIN_H.toPx() - 8.dp.toPx() - grow) * 2 - (size.width - MARGIN_H.toPx() * 2) * 0f,
                        size.height - (MARGIN_V.toPx() - 4.dp.toPx() - grow * 0.6f) * 2 - (size.height - MARGIN_V.toPx() * 2) * 0f
                    ),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(
                        12.dp.toPx() + grow, 12.dp.toPx() + grow
                    )
                )
            }
        }

        // ── 2) 页面截面厚度：4 层递减窄条（文档 3.2） ──
        val layers = listOf(
            Triple(2.dp, 1.dp, SectionL1),
            Triple(4.dp, 2.dp, SectionL2),
            Triple(5.dp, 3.dp, SectionL3),
            Triple(6.dp, 4.dp, SectionL4)
        )
        layers.reversed().forEach { (verticalOffset, horizontalShrink, color) ->
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .offset(y = verticalOffset)
                    .padding(horizontal = MARGIN_H + CURL_DEPTH + horizontalShrink)
                    .fillMaxWidth()
                    .height(verticalOffset)
                    .background(color, RoundedCornerShape(bottomStart = 2.dp, bottomEnd = 2.dp))
            )
        }

        // ── 3) 书页主体：弧面轮廓 + 弧面光影 + 书脊凹陷 + 纸张噪点（文档 3.1 / 3.3） ──
        Canvas(modifier = Modifier.matchParentSize()) {
            val w = size.width
            val h = size.height
            val marginH = MARGIN_H.toPx()
            val marginV = MARGIN_V.toPx()
            val curl = CURL_DEPTH.toPx()

            // 书页轮廓：左右边缘向内弯（贝塞尔）
            val path = Path().apply {
                moveTo(marginH + curl, marginV)
                quadraticBezierTo(w / 2f, marginV + 3.dp.toPx(), w - marginH - curl, marginV)
                cubicTo(
                    w - marginH, marginV + h * 0.1f,
                    w - marginH, marginV + h * 0.9f,
                    w - marginH - curl, h - marginV
                )
                quadraticBezierTo(w / 2f, h - marginV + 3.dp.toPx(), marginH + curl, h - marginV)
                cubicTo(
                    marginH, h - marginV - h * 0.1f,
                    marginH, marginV + h * 0.1f,
                    marginH + curl, marginV
                )
                close()
            }
            // 弧面光影：边缘亮 → 书脊暗 → 边缘亮
            val paperGradient = Brush.horizontalGradient(
                0.00f to PaperEdgeLight,
                0.06f to PaperEdge,
                0.15f to PaperMain,
                0.42f to PaperNearSpine,
                0.50f to PaperSpine,
                0.58f to PaperNearSpine,
                0.85f to PaperMain,
                0.94f to PaperEdge,
                1.00f to PaperEdgeLight
            )
            drawPath(path, paperGradient)

            // 书脊凹陷：中线两侧窄渐变带
            val spineX = w / 2f
            drawRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(Color.Transparent, Color(0x0A000000), Color(0x12000000)),
                    startX = spineX - 20.dp.toPx(), endX = spineX
                ),
                topLeft = Offset(spineX - 20.dp.toPx(), marginV),
                size = Size(20.dp.toPx(), h - marginV * 2)
            )
            drawRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(Color(0x12000000), Color(0x0A000000), Color.Transparent),
                    startX = spineX, endX = spineX + 20.dp.toPx()
                ),
                topLeft = Offset(spineX, marginV),
                size = Size(20.dp.toPx(), h - marginV * 2)
            )

            // 纸张噪点（限制在书页路径内）
            clipPath(path) {
                val rnd = java.util.Random(20261025L)
                repeat(140) {
                    drawCircle(
                        color = Color(0x0F8A7A5A),
                        radius = 0.7f + rnd.nextFloat() * 1.1f,
                        center = Offset(
                            marginH + rnd.nextFloat() * (w - marginH * 2),
                            marginV + rnd.nextFloat() * (h - marginV * 2)
                        )
                    )
                }
            }
        }

        // ── 4) 内容：一篇日记一页，左右翻（文档 3.5 / 3.7） ──
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxSize()
        ) { pageIndex ->
            val diary = letters[pageIndex]
            val isFirstOfMonth = pageIndex == 0 ||
                letters[pageIndex - 1].date.take(7) != diary.date.take(7)

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        // 翻页动效：绕书脊侧转 12° + 轻缩放（文档 3.7）
                        val pageOffset = (pagerState.currentPage - pageIndex) +
                            pagerState.currentPageOffsetFraction
                        rotationY = pageOffset * 12f
                        val scale = 1f - abs(pageOffset) * 0.06f
                        scaleX = scale
                        scaleY = scale
                        transformOrigin = TransformOrigin(
                            pivotFractionX = if (pageOffset > 0) 0f else 1f,
                            pivotFractionY = 0.5f
                        )
                        cameraDistance = 16f * density
                    }
                    .clipToBounds()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(
                            start = MARGIN_H + CURL_DEPTH + 16.dp,
                            end = MARGIN_H + CURL_DEPTH + 16.dp,
                            top = MARGIN_V + 20.dp,
                            bottom = MARGIN_V + 20.dp
                        )
                        .verticalScroll(rememberScrollState())
                ) {
                    // 月份章节页眉（仅该月第一篇显示）
                    if (isFirstOfMonth) {
                        Text(
                            MONTH_NAMES[monthIdxOf(diary.date)],
                            fontFamily = FontFamily.Serif,
                            fontStyle = FontStyle.Italic,
                            fontSize = 30.sp,
                            fontWeight = FontWeight.Light,
                            color = InkMain
                        )
                        Text(
                            diary.date.take(4),
                            fontSize = 11.sp, color = InkSoft2
                        )
                        Spacer(Modifier.height(18.dp))
                    }

                    // 日期 + 星期
                    val d = runCatching { LocalDate.parse(diary.date) }.getOrNull()
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            if (d != null) "${d.monthValue}月${d.dayOfMonth}日" else diary.date,
                            fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = InkDate
                        )
                        Spacer(Modifier.width(8.dp))
                        if (d != null) {
                            Text(
                                CHN_WEEKDAYS[d.dayOfWeek.value - 1],
                                fontSize = 11.sp, color = InkSoft2
                            )
                        }
                        if (diary.weather.isNotBlank()) {
                            Spacer(Modifier.width(10.dp))
                            Text(diary.weather, fontSize = 11.sp, color = InkSoft2)
                        }
                        if (diary.mood.isNotBlank()) {
                            Spacer(Modifier.width(6.dp))
                            Text(diary.mood, fontSize = 11.sp, color = InkSoft2)
                        }
                    }

                    if (diary.title.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            diary.title,
                            fontSize = 15.sp, fontWeight = FontWeight.Medium, color = InkMain
                        )
                    }

                    // 细分隔线
                    Spacer(Modifier.height(10.dp))
                    Box(
                        Modifier
                            .width(40.dp)
                            .height(1.dp)
                            .background(Color(0x33A08C6E))
                    )
                    Spacer(Modifier.height(14.dp))

                    // 正文（长文靠页内滚动）
                    Text(
                        diary.content,
                        fontSize = 13.sp,
                        lineHeight = 26.sp,
                        letterSpacing = 0.3.sp,
                        color = InkMain
                    )

                    Spacer(Modifier.height(24.dp))
                    Text(
                        "— Sean",
                        fontSize = 11.sp, fontStyle = FontStyle.Italic, color = InkAuthor,
                        modifier = Modifier.align(Alignment.End)
                    )
                    Spacer(Modifier.height(10.dp))
                }

                // ── 右侧月份索引贴（文档 3.6） ──
                Column(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 2.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val currentMonth = diary.date.take(7)
                    monthList.forEach { month ->
                        val isCurrent = month == currentMonth
                        Box(
                            modifier = Modifier
                                .width(if (isCurrent) 26.dp else 20.dp)
                                .height(34.dp)
                                .background(
                                    color = if (isCurrent) Color(0xFF9DBF8A) else Color(0xFFD8CFC0),
                                    shape = RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp)
                                )
                                .clickable {
                                    scope.launch {
                                        pagerState.animateScrollToPage(
                                            letters.indexOfFirst { it.date.take(7) == month }
                                        )
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                CHN_MONTHS[monthIdxOf(month + "-01")].take(1),
                                fontSize = 9.sp,
                                color = if (isCurrent) Color.White else Color(0xFF8A7A6A)
                            )
                        }
                    }
                }
            }
        }

        // ── 页码（书底中央，淡色小字） ──
        Text(
            "${pagerState.currentPage + 1} / ${letters.size}",
            fontSize = 10.sp, color = InkSoft2.copy(alpha = 0.7f),
            fontFamily = FontFamily.Serif,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 12.dp)
        )
    }

        // ── 底部翻页导航 ──
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            val canPrev = pagerState.currentPage > 0
            Text(
                "‹ 上一篇",
                fontSize = 13.sp,
                color = if (canPrev) HomecomingColors.Accent else HomecomingColors.InkSoft.copy(alpha = 0.4f),
                modifier = Modifier
                    .clickable(enabled = canPrev) {
                        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
                    }
                    .padding(6.dp)
            )
            val cur = letters.getOrNull(pagerState.currentPage)
            Text(
                if (cur != null) CHN_MONTHS[monthIdxOf(cur.date)] else "",
                fontSize = 13.sp, color = HomecomingColors.Ink,
                fontFamily = FontFamily.Serif,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )
            val canNext = pagerState.currentPage < letters.size - 1
            Text(
                "下一篇 ›",
                fontSize = 13.sp,
                color = if (canNext) HomecomingColors.Accent else HomecomingColors.InkSoft.copy(alpha = 0.4f),
                modifier = Modifier
                    .clickable(enabled = canNext) {
                        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                    }
                    .padding(6.dp)
            )
        }
    }
}

private fun monthIdxOf(date: String): Int {
    val m = date.length >= 7 && date.contains("-")
    val month = if (m) date.split("-")[1].toIntOrNull() ?: 1 else 1
    return (month - 1).coerceIn(0, 11)
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
