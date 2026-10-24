package com.aion.chat.compose.ui.diary

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import com.aion.chat.compose.data.HomecomingDayStore
import com.aion.chat.compose.data.SupabaseMomentsStore
import com.aion.chat.compose.ui.theme.HomecomingColors
import kotlinx.coroutines.launch
import java.time.Instant

/**
 * 日记手账（Yuri 反馈迭代）：
 * - 一月一页，页上 4 张层叠信纸条（每张都能点着读），铺满整页
 * - 页底「全部日期」胶囊条：当月每一天一颗圆签，点哪天读哪封——所有信都看得到
 * - 顶部翻页堆圈进预留区（不再顶到标题）；底部露边塞到卡片底下（无缝隙，层层叠叠）
 * - 翻页：上滑翻走 / 下滑倒翻
 */
@Composable
fun DiaryBoxScreen(onBack: () -> Unit = {}) {
    val context = LocalContext.current
    val letters = remember { mutableStateOf<List<Letter>>(emptyList()) }
    val loading = remember { mutableStateOf(true) }
    val selected = remember { mutableStateOf<Letter?>(null) }

    LaunchedEffect(Unit) {
        loading.value = true
        letters.value = loadLetters(context)
        loading.value = false
    }

    val monthKeys = remember(letters.value) {
        letters.value.map { it.date.take(7) }.distinct().sorted()
    }
    val months = remember(letters.value) {
        letters.value.groupBy { it.date.take(7) }
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
                Text("一月一页 · 点信纸或日期读那天的信", fontSize = 11.sp, color = HomecomingColors.InkSoft)
            }
        }
        Spacer(Modifier.height(6.dp))

        BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (loading.value) {
                Text(
                    "翻开手账中…",
                    fontSize = 12.sp, color = HomecomingColors.InkSoft,
                    modifier = Modifier.align(Alignment.Center)
                )
            } else if (monthKeys.isEmpty()) {
                Text(
                    "还没有一页手账——\n和他说上一段话，晚安之后这个月就会有回忆",
                    fontSize = 13.sp, color = HomecomingColors.InkSoft,
                    lineHeight = 22.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                val cardH = (maxHeight - 58.dp).coerceIn(470.dp, 660.dp)
                FlipCalendar(
                    monthKeys = monthKeys,
                    months = months,
                    cardHeight = cardH,
                    onLetterTap = { selected.value = it },
                    modifier = Modifier.align(Alignment.TopCenter)
                )
            }
        }
    }

    // ── 全屏信纸阅读 ──
    selected.value?.let { letter ->
        LetterReaderDialog(letter = letter, onClose = { selected.value = null })
    }
}

// ── 翻页手账 ──

private val MONTH_NAMES = listOf(
    "January", "February", "March", "April", "May", "June",
    "July", "August", "September", "October", "November", "December"
)
private val MONTH_COLORS = listOf(
    Color(0xFF8A7BC8), Color(0xFFD98A8A), Color(0xFF7FB08A), Color(0xFFD9A66A),
    Color(0xFF7FA8D9), Color(0xFFB98AC9), Color(0xFF6FA8A8), Color(0xFFC98A5A),
    Color(0xFF8A9BD9), Color(0xFFC97B8A), Color(0xFF8AA87F), Color(0xFFA87FB0)
)
private val PagePaper = Color(0xFFF7F3E8)
private val PagePaperMid = Color(0xFFEFE9D8)
private val PagePaperDark = Color(0xFFE4DCC6)
private val SlipPaper = Color(0xFFFDFBF3)
private val TAPE_COLORS = listOf(
    Color(0x80E8C9D8), Color(0x80BFD8E8), Color(0x80D8E8C0), Color(0x80E8E0B8)
)

@Composable
private fun FlipCalendar(
    monthKeys: List<String>,
    months: Map<String, List<Letter>>,
    cardHeight: Dp,
    onLetterTap: (Letter) -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val page = remember { mutableStateOf(0) }
    val flip = remember { Animatable(0f) }
    val animating = remember { mutableStateOf(false) }
    var dragAcc by remember { mutableStateOf(0f) }

    fun goNext() {
        if (animating.value || page.value >= monthKeys.lastIndex) return
        animating.value = true
        scope.launch {
            flip.animateTo(1f, tween(560, easing = FastOutSlowInEasing))
            page.value++
            flip.snapTo(0f)
            animating.value = false
        }
    }
    fun goPrev() {
        if (animating.value || page.value <= 0) return
        animating.value = true
        scope.launch {
            page.value--
            flip.snapTo(1f)
            flip.animateTo(0f, tween(560, easing = FastOutSlowInEasing))
            animating.value = false
        }
    }

    val topReserve = 30.dp
    val bottomReserve = 22.dp
    val cardTop = 24.dp // 卡顶塞进翻页堆下面 6dp，有被压住的感觉

    Column(modifier = modifier.fillMaxWidth()) {
        Box(modifier = Modifier.fillMaxWidth().height(cardHeight + topReserve + bottomReserve)) {
            // ── 上面：已翻过去的页（3 层卡边，圈在预留区里，不顶标题） ──
            val past = minOf(3, page.value)
            repeat(past) { k ->
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .offset(y = (k * 7).dp)
                        .fillMaxWidth(0.91f - k * 0.02f)
                        .height(22.dp)
                        .zIndex(3f)
                        .shadow(3.dp, RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                        .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                        .background(
                            when (k) {
                                0 -> PagePaperMid
                                1 -> PagePaperDark
                                else -> PagePaperDark.copy(alpha = 0.85f)
                            }
                        )
                )
            }
            // ── 下面：没翻到的页（塞到卡片底下，无缝隙；透出下一月月名） ──
            val future = minOf(2, monthKeys.size - 1 - page.value)
            repeat(future) { k ->
                val nextKey = monthKeys.getOrNull(page.value + 1 + k)
                val nextMonthIdx = (nextKey?.takeLast(2)?.toIntOrNull() ?: 1) - 1
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .offset(y = (k * 8).dp)
                        .fillMaxWidth(0.91f - k * 0.02f)
                        .height(30.dp)
                        .zIndex(0f)
                        .shadow(3.dp, RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp))
                        .clip(RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp))
                        .background(
                            if (k == 0) Brush.verticalGradient(listOf(PagePaper, PagePaperMid))
                            else SolidColor(PagePaperDark.copy(alpha = 0.9f))
                        )
                ) {
                    if (k == 0 && nextKey != null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                        ) {
                            Text(
                                MONTH_NAMES[nextMonthIdx],
                                fontSize = 14.sp,
                                color = MONTH_COLORS[nextMonthIdx % 12].copy(alpha = 0.45f),
                                fontFamily = FontFamily.Serif, fontStyle = FontStyle.Italic
                            )
                            Spacer(Modifier.weight(1f))
                            Box(
                                modifier = Modifier
                                    .size(width = 38.dp, height = 10.dp)
                                    .graphicsLayer { rotationZ = -4f }
                                    .background(TAPE_COLORS[nextMonthIdx % TAPE_COLORS.size])
                            )
                        }
                    }
                }
            }
            // ── 下一页整页（垫在当前页下，翻走时露出） ──
            val nextIdx = page.value + 1
            if (nextIdx < monthKeys.size) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .offset(y = cardTop + 6.dp)
                        .fillMaxWidth(0.95f)
                        .height(cardHeight - 8.dp)
                        .zIndex(0f)
                        .shadow(8.dp, RoundedCornerShape(14.dp))
                        .clip(RoundedCornerShape(14.dp))
                        .background(Brush.verticalGradient(listOf(PagePaper, PagePaperDark))),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Text(
                        MONTH_NAMES.getOrNull(
                            (monthKeys[nextIdx].takeLast(2).toIntOrNull() ?: 1) - 1
                        ).orEmpty(),
                        fontSize = 22.sp,
                        color = MONTH_COLORS[
                            ((monthKeys[nextIdx].takeLast(2).toIntOrNull() ?: 1) - 1).mod(12)
                        ].copy(alpha = 0.5f),
                        fontFamily = FontFamily.Serif, fontStyle = FontStyle.Italic,
                        modifier = Modifier.padding(top = 16.dp)
                    )
                }
            }
            // ── 当前页（可翻走） ──
            MonthPage(
                letters = months[monthKeys[page.value]].orEmpty(),
                monthKey = monthKeys[page.value],
                onLetterTap = onLetterTap,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = cardTop)
                    .fillMaxWidth(0.95f)
                    .height(cardHeight)
                    .zIndex(1f)
                    .graphicsLayer {
                        rotationX = -96f * flip.value
                        transformOrigin = TransformOrigin(0.5f, 0f)
                        alpha = 1f - 0.5f * flip.value
                        translationY = -30f * flip.value * density
                        cameraDistance = 420f * density
                        shadowElevation = 10f * (1f - flip.value)
                    }
                    .pointerInput(monthKeys.size, page.value) {
                        detectVerticalDragGestures(
                            onDragStart = { dragAcc = 0f },
                            onVerticalDrag = { change, amount ->
                                change.consume()
                                dragAcc += amount
                                if (!animating.value) {
                                    if (dragAcc < -110) { dragAcc = 0f; goNext() }
                                    else if (dragAcc > 110) { dragAcc = 0f; goPrev() }
                                }
                            }
                        )
                    }
            )
        }

        Spacer(Modifier.height(10.dp))
        // ── 月份导航 ──
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            val canPrev = page.value > 0 && !animating.value
            Text(
                "‹ 上一月",
                fontSize = 13.sp,
                color = if (canPrev) HomecomingColors.Accent else HomecomingColors.InkSoft.copy(alpha = 0.4f),
                modifier = Modifier.clickable(enabled = canPrev) { goPrev() }.padding(6.dp)
            )
            Text(
                monthKeys.getOrNull(page.value)?.replace("-", " / ") ?: "",
                fontSize = 13.sp, color = HomecomingColors.Ink,
                fontFamily = FontFamily.Serif,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )
            val canNext = page.value < monthKeys.lastIndex && !animating.value
            Text(
                "下一月 ›",
                fontSize = 13.sp,
                color = if (canNext) HomecomingColors.Accent else HomecomingColors.InkSoft.copy(alpha = 0.4f),
                modifier = Modifier.clickable(enabled = canNext) { goNext() }.padding(6.dp)
            )
        }
    }
}

/** 一个月的手账页：月份纸签 + 4 张层叠信纸条 + 全部日期胶囊条。 */
@Composable
private fun MonthPage(
    letters: List<Letter>,
    monthKey: String,
    onLetterTap: (Letter) -> Unit,
    modifier: Modifier = Modifier
) {
    val year = monthKey.take(4)
    val monthIdx = (monthKey.takeLast(2).toIntOrNull() ?: 1) - 1
    val monthColor = MONTH_COLORS[monthIdx % MONTH_COLORS.size]

    Box(
        modifier = modifier
            .shadow(10.dp, RoundedCornerShape(14.dp))
            .clip(RoundedCornerShape(14.dp))
            .background(Brush.verticalGradient(listOf(PagePaper, PagePaperMid)))
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val rnd = java.util.Random(monthKey.hashCode().toLong())
            repeat(90) {
                drawCircle(
                    color = Color(0x0F8A8070),
                    radius = 0.8f + rnd.nextFloat() * 1.2f,
                    center = Offset(rnd.nextFloat() * size.width, rnd.nextFloat() * size.height)
                )
            }
        }
        Column(Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 12.dp)) {
            // ── 月份纸签 ──
            Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .graphicsLayer { rotationZ = -2.5f }
                        .shadow(3.dp, RoundedCornerShape(4.dp))
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFFFDF9EE))
                        .border(0.7.dp, Color(0x33A89A78), RoundedCornerShape(4.dp))
                        .padding(start = 14.dp, end = 18.dp, top = 5.dp, bottom = 5.dp)
                ) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            MONTH_NAMES[monthIdx],
                            fontSize = 22.sp, color = monthColor,
                            fontFamily = FontFamily.Serif, fontStyle = FontStyle.Italic,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            year,
                            fontSize = 11.sp, color = Color(0xFF8A8070),
                            fontFamily = FontFamily.Serif,
                            modifier = Modifier.padding(bottom = 3.dp)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .offset(y = (-7).dp)
                            .size(width = 46.dp, height = 13.dp)
                            .graphicsLayer { rotationZ = -6f }
                            .background(TAPE_COLORS[monthIdx % TAPE_COLORS.size])
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(
                    "共 " + letters.size + " 封",
                    fontSize = 11.sp, color = Color(0xFF8A8070),
                    fontFamily = FontFamily.Serif
                )
            }
            Spacer(Modifier.height(10.dp))

            // ── 层叠信纸条（4 张，逐张下压铺满；每张露出的上沿都能点） ──
            BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
                if (letters.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(SlipPaper)
                            .border(0.7.dp, Color(0x33A89A78), RoundedCornerShape(8.dp))
                            .padding(18.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "这个月还没有写信——\n晚安之后就会有一张",
                            fontSize = 12.sp, color = Color(0xFF8A8070),
                            lineHeight = 20.sp, textAlign = TextAlign.Center,
                            fontFamily = FontFamily.Serif
                        )
                    }
                }
                val visible = letters.take(4)
                // 步长按可用高度算：条与条压排着铺满整页
                val step = ((maxHeight - 132.dp) / (visible.size - 1).coerceAtLeast(1))
                    .coerceIn(58.dp, 96.dp)
                for (i in visible.indices.reversed()) {
                    val letter = visible[i]
                    LetterSlip(
                        letter = letter,
                        tapeColor = TAPE_COLORS[(monthIdx + i) % TAPE_COLORS.size],
                        rot = when (i) { 0 -> -1.2f; 1 -> 1.6f; 2 -> -0.8f; else -> 1.2f },
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .offset(y = step * i, x = when (i) { 0 -> 0.dp; 1 -> 7.dp; 2 -> (-6).dp; else -> 5.dp })
                            .fillMaxWidth(1f - i * 0.035f)
                            .zIndex(i.toFloat())
                            .clickable(
                                indication = null,
                                interactionSource = MutableInteractionSource()
                            ) { onLetterTap(letter) }
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            // ── 全部日期胶囊条：当月每一天一颗圆签，点哪天读哪封 ──
            if (letters.isNotEmpty()) {
                Text(
                    "全部日期 · 点哪天读哪封",
                    fontSize = 10.sp, color = Color(0xFF9A9080),
                    fontFamily = FontFamily.Serif,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                ) {
                    letters.sortedBy { it.date }.forEach { letter ->
                        val day = letter.date.takeLast(2).toIntOrNull() ?: 0
                        Box(
                            modifier = Modifier
                                .padding(end = 6.dp)
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(monthColor.copy(alpha = 0.14f))
                                .border(1.2.dp, monthColor.copy(alpha = 0.55f), CircleShape)
                                .clickable { onLetterTap(letter) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                day.toString(),
                                fontSize = 11.sp, color = monthColor,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Serif
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 一张信纸条：胶带贴口 + 日期 + 信文摘录。 */
@Composable
private fun LetterSlip(
    letter: Letter,
    tapeColor: Color,
    rot: Float,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .graphicsLayer { rotationZ = rot; cameraDistance = 160f * density }
            .shadow(5.dp, RoundedCornerShape(8.dp))
            .clip(RoundedCornerShape(8.dp))
            .background(SlipPaper)
            .border(0.7.dp, Color(0x2E8A8070), RoundedCornerShape(8.dp))
    ) {
        Column(Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 15.dp, bottom = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "♥ " + letter.date,
                    fontSize = 10.sp, color = Color(0xFFA85A72),
                    fontFamily = FontFamily.Serif
                )
                if (letter.weather.isNotBlank()) {
                    Spacer(Modifier.width(6.dp))
                    Text(letter.weather, fontSize = 10.sp)
                }
                if (letter.mood.isNotBlank()) {
                    Spacer(Modifier.width(4.dp))
                    Text(letter.mood, fontSize = 10.sp)
                }
                Spacer(Modifier.weight(1f))
                Text(
                    "读这封 →",
                    fontSize = 10.sp, color = HomecomingColors.Accent
                )
            }
            Text(
                letter.content.replace("\n", " ").trim().take(96) + if (letter.content.length > 96) "…" else "",
                fontSize = 13.sp, color = Color(0xFF3A3020),
                lineHeight = 20.sp,
                fontFamily = FontFamily.Serif,
                maxLines = 4, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 5.dp)
            )
        }
        // 胶带（压在纸条上口）
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = (-8).dp)
                .size(width = 58.dp, height = 15.dp)
                .graphicsLayer { rotationZ = if (letter.createdAt % 2 == 0L) 5f else -5f }
                .background(tapeColor)
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

// ── 全屏信纸阅读 ──

@Composable
private fun LetterReaderDialog(letter: Letter, onClose: () -> Unit) {
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFFBF6EA))
                .verticalScroll(rememberScrollState())
                .clickable(
                    indication = null,
                    interactionSource = MutableInteractionSource()
                ) { onClose() }
                .padding(horizontal = 30.dp, vertical = 40.dp)
        ) {
            Text(
                letter.date,
                fontSize = 12.sp, color = Color(0xFFA85A72),
                fontFamily = FontFamily.Serif, letterSpacing = 2.sp
            )
            if (letter.title.isNotBlank()) {
                Text(
                    letter.title,
                    fontSize = 19.sp, color = Color(0xFF5A4632),
                    fontWeight = FontWeight.Medium,
                    fontFamily = FontFamily.Serif,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 6.dp)
            ) {
                if (letter.weather.isNotBlank()) Text(letter.weather + "  ", fontSize = 13.sp)
                if (letter.mood.isNotBlank()) Text(letter.mood, fontSize = 13.sp)
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp, bottom = 16.dp)
                    .height(1.dp)
                    .background(Color(0x33A85A72))
            )
            Text(
                letter.content,
                fontSize = 16.sp, lineHeight = 31.sp,
                color = Color(0xFF3A3020),
                fontFamily = FontFamily.Serif
            )
            Spacer(Modifier.height(28.dp))
            Text(
                "—— Sean",
                fontSize = 14.sp, color = Color(0xFF5A4632),
                fontFamily = FontFamily.Serif,
                textAlign = TextAlign.End,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(40.dp))
            Text(
                "点任意处合上信纸",
                fontSize = 10.sp, color = Color(0xFFB99AA6),
                modifier = Modifier.fillMaxWidth().padding(bottom = 30.dp)
            )
        }
    }
}
