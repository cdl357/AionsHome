package com.aion.chat.compose.ui.diary

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
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
 * 日记手账（Yuri 参考视频：翻页月历手账）。
 * - 一个月一页卡：英文月份名（彩色手写感）+ 回忆清单（＊条目，点开读信）+ 小日历（有日记的日期圈出来）+ 当月最新一封信
 * - 翻页：卡片绕顶边向上翻走（rotationX + 淡出），露出下一页；下滑/「上一个月」翻回来
 * - 数据：云端哥哥日记 + 本地日记合并，同日去重（云端优先），按月分组
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
                Text("一月一页回忆 · 往上翻看下一个月", fontSize = 11.sp, color = HomecomingColors.InkSoft)
            }
        }
        Spacer(Modifier.height(10.dp))

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
                val cardH = (maxHeight - 46.dp).coerceIn(470.dp, 660.dp)
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

// ── 翻页月历 ──

private val MONTH_NAMES = listOf(
    "January", "February", "March", "April", "May", "June",
    "July", "August", "September", "October", "November", "December"
)
private val MONTH_COLORS = listOf(
    Color(0xFF8A7BC8), Color(0xFFD98A8A), Color(0xFF7FB08A), Color(0xFFD9A66A),
    Color(0xFF7FA8D9), Color(0xFFB98AC9), Color(0xFF6FA8A8), Color(0xFFC98A5A),
    Color(0xFF8A9BD9), Color(0xFFC97B8A), Color(0xFF8AA87F), Color(0xFFA87FB0)
)
private val CardPaper = Color(0xFFF7F3E8)
private val CardPaperDark = Color(0xFFEDE7D6)
private val InkSoft2 = Color(0xFF8A8070)

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

    Column(modifier = modifier.fillMaxWidth()) {
        Box(modifier = Modifier.fillMaxWidth().height(cardHeight)) {
            // 已翻过去的页：顶部露出的卡边
            val peek = minOf(2, page.value)
            repeat(peek) { k ->
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .offset(y = (-(10 * (k + 1))).dp)
                        .fillMaxWidth(0.94f - k * 0.015f)
                        .height(22.dp)
                        .zIndex(2f)
                        .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                        .background(if (k == 0) CardPaperDark else CardPaperDark.copy(alpha = 0.8f))
                )
            }
            // 下一页（压在当前页下面，翻走后露出）
            val nextIdx = page.value + 1
            if (nextIdx < monthKeys.size) {
                MonthCard(
                    letters = months[monthKeys[nextIdx]].orEmpty(),
                    monthKey = monthKeys[nextIdx],
                    compact = true,
                    onLetterTap = onLetterTap,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth(0.94f)
                        .height(cardHeight - 14.dp)
                        .zIndex(0f)
                        .graphicsLayer {
                            scaleX = 0.97f; scaleY = 0.97f
                            alpha = 0.92f
                            cameraDistance = 300f * density
                        }
                )
            }
            // 当前页（可翻走）
            MonthCard(
                letters = months[monthKeys[page.value]].orEmpty(),
                monthKey = monthKeys[page.value],
                compact = false,
                onLetterTap = onLetterTap,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth(0.94f)
                    .height(cardHeight)
                    .zIndex(1f)
                    .graphicsLayer {
                        rotationX = -96f * flip.value
                        transformOrigin = TransformOrigin(0.5f, 0f)
                        alpha = 1f - 0.5f * flip.value
                        translationY = -30f * flip.value * density
                        cameraDistance = 420f * density
                        shadowElevation = 8f * (1f - flip.value)
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
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            val canPrev = page.value > 0 && !animating.value
            Text(
                "‹ 上一个月",
                fontSize = 13.sp,
                color = if (canPrev) HomecomingColors.Accent else HomecomingColors.InkSoft.copy(alpha = 0.4f),
                modifier = Modifier
                    .clickable(enabled = canPrev) { goPrev() }
                    .padding(6.dp)
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
                "下一个月 ›",
                fontSize = 13.sp,
                color = if (canNext) HomecomingColors.Accent else HomecomingColors.InkSoft.copy(alpha = 0.4f),
                modifier = Modifier
                    .clickable(enabled = canNext) { goNext() }
                    .padding(6.dp)
            )
        }
    }
}

/** 一个月的手账卡。 */
@Composable
private fun MonthCard(
    letters: List<Letter>,
    monthKey: String,
    compact: Boolean,
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
            .background(Brush.verticalGradient(listOf(CardPaper, CardPaperDark)))
    ) {
        // 纸面细点
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
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp)) {
            // ── 月份名 ──
            Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.fillMaxWidth()) {
                Text(
                    MONTH_NAMES[monthIdx],
                    fontSize = 26.sp, color = monthColor,
                    fontFamily = FontFamily.Serif, fontStyle = FontStyle.Italic,
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    year,
                    fontSize = 12.sp, color = InkSoft2,
                    fontFamily = FontFamily.Serif,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
            Spacer(Modifier.height(6.dp))

            // ── 回忆清单（点一条读一封） ──
            val shown = if (compact) letters.take(3) else letters.take(5)
            shown.forEach { letter ->
                Text(
                    "＊ " + letter.content.replace("\n", " ").trim().take(16),
                    fontSize = 13.sp, color = Color(0xFF4A4034),
                    lineHeight = 20.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            indication = null,
                            interactionSource = MutableInteractionSource()
                        ) { onLetterTap(letter) }
                        .padding(vertical = 3.dp)
                )
            }
            if (letters.size > shown.size) {
                Text(
                    "＊ …还有 " + (letters.size - shown.size) + " 条都在小日历里",
                    fontSize = 12.sp, color = InkSoft2,
                    modifier = Modifier.padding(vertical = 3.dp)
                )
            }
            if (letters.isEmpty()) {
                Text(
                    "＊ 这个月还没有写日记",
                    fontSize = 13.sp, color = InkSoft2,
                    modifier = Modifier.padding(vertical = 3.dp)
                )
            }
            Spacer(Modifier.height(4.dp))

            // ── 小日历（有信的日子圈出来） ──
            MiniCalendar(monthKey = monthKey, marked = letters.map { it.date.take(10).takeLast(2).toIntOrNull() ?: 0 }.toSet(), accent = monthColor)
            Spacer(Modifier.height(6.dp))

            // ── 当月最新一封信（信纸小卡） ──
            letters.firstOrNull()?.let { latest ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFFFDFBF3))
                        .border(1.dp, Color(0x33A85A72), RoundedCornerShape(10.dp))
                        .clickable { onLetterTap(latest) }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "♥ " + latest.date,
                                fontSize = 10.sp, color = Color(0xFFA85A72),
                                fontFamily = FontFamily.Serif
                            )
                            Spacer(Modifier.weight(1f))
                            if (letters.size > 1) {
                                Text(
                                    "本月 " + letters.size + " 封",
                                    fontSize = 10.sp, color = InkSoft2
                                )
                            }
                        }
                        Text(
                            latest.content.replace("\n", " ").take(64) + if (latest.content.length > 64) "…" else "",
                            fontSize = 12.sp, color = Color(0xFF3A3020),
                            lineHeight = 18.sp,
                            maxLines = if (compact) 2 else 3,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

/** 小月历：日 一 二 三 四 五 六；有信的日期圈出来。 */
@Composable
private fun MiniCalendar(monthKey: String, marked: Set<Int>, accent: Color) {
    val ym = runCatching { java.time.YearMonth.parse(monthKey) }.getOrNull() ?: return
    val firstDow = ym.atDay(1).dayOfWeek.value % 7 // 周日=0
    val days = ym.lengthOfMonth()
    Column(Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth()) {
            listOf("日", "一", "二", "三", "四", "五", "六").forEach { w ->
                Text(
                    w, fontSize = 9.sp, color = InkSoft2,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        var cell = 1 - firstDow
        while (cell <= days) {
            Row(modifier = Modifier.fillMaxWidth().padding(top = 2.dp)) {
                repeat(7) {
                    val d = cell; cell++
                    Box(
                        modifier = Modifier.weight(1f).height(20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (d in 1..days) {
                            val markedDay = d in marked
                            Box(
                                modifier = Modifier
                                    .size(17.dp)
                                    .let { m ->
                                        if (markedDay) m.border(1.4.dp, accent, CircleShape) else m
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    d.toString(),
                                    fontSize = 9.sp,
                                    color = if (markedDay) accent else Color(0xFF7A7264),
                                    fontWeight = if (markedDay) FontWeight.Bold else FontWeight.Normal,
                                    fontFamily = FontFamily.Serif
                                )
                            }
                        }
                    }
                }
            }
        }
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
    // 云端（Supabase 哥哥的日记，历史全量；失败静默降级为空）
    val remote = runCatching { SupabaseMomentsStore.fetchSeanDiaries() }
        .getOrNull()
        ?.takeIf { it.error == null }
        ?.data
        .orEmpty()
        .map { d ->
            val (date, ms) = resolveDate(d.createdAt, d.createdAtMs)
            Letter(date, d.title, d.content, d.weather, d.mood, ms)
        }
    // 本地（App 内晚安自动写的）
    val local = HomecomingDayStore.diaries(context)
        .filter { it.author == "sean" }
        .map { d ->
            val (date, _) = resolveDate("", d.createdAt)
            Letter(date, d.title, d.content, "", "", d.createdAt)
        }
    // 同日去重：云端优先（历史正本），本地只保留云端没有的日期
    val remoteDates = remote.map { it.date }.toSet()
    val localOnly = local.filter { it.date !in remoteDates }
    return (remote + localOnly).sortedByDescending { it.createdAt }
}

/** 云端 ISO 时间 / 本地毫秒 →（本地日期字符串，毫秒）。 */
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
