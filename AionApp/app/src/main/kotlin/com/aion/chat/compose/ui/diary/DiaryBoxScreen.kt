package com.aion.chat.compose.ui.diary

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.aion.chat.compose.data.HomecomingDayStore
import com.aion.chat.compose.data.SupabaseMomentsStore
import com.aion.chat.compose.ui.theme.HomecomingColors
import java.time.Instant
import java.time.YearMonth

/**
 * 日记手账·厚卡片本（按 Yuri 提供的技术文档 v2 实现；替换之前的书本方案）。
 * - 一月一张厚卡片：Canvas 弧面（上下边同弧度下凹，光从右上）+ 5 层厚度截面 + 投影（左下偏）
 * - 上下滑动浏览，层层压叠（18dp），jitter 参差（±0.8°/±3dp，种子固定）
 * - 点卡片原地展开（128→430dp）：显示当月日记列表，点一条读全文（信纸弹窗）
 * - 顶部一行（月份名 + 迷你月历）是被压住时唯一露出的部分
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
        letters.value.map { it.date.take(7) }.distinct().sortedDescending() // 新→旧
    }
    val months = remember(letters.value) {
        letters.value.groupBy { it.date.take(7) }
    }

    // 屏幕背景：暖米色 + 极淡噪点（文档 §六）
    Box(modifier = Modifier.fillMaxSize().background(Color(0xFFF2EDE3))) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val rnd = java.util.Random(25L)
            repeat(200) {
                drawCircle(
                    color = Color(0x0D8A7A5A),
                    radius = 0.7f + rnd.nextFloat() * 1.1f,
                    center = Offset(rnd.nextFloat() * size.width, rnd.nextFloat() * size.height)
                )
            }
        }

        Column(modifier = Modifier.fillMaxSize()) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 14.dp)) {
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
                    Text("厚卡片本 · 上下滑 · 点卡片展开一个月", fontSize = 11.sp, color = HomecomingColors.InkSoft)
                }
            }

            BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
                if (loading.value) {
                    Text(
                        "翻开卡片本中…",
                        fontSize = 12.sp, color = HomecomingColors.InkSoft,
                        modifier = Modifier.align(Alignment.Center)
                    )
                } else if (monthKeys.isEmpty()) {
                    Text(
                        "还没有一张卡片——\n和他说上一段话，晚安之后这个月就会有",
                        fontSize = 13.sp, color = HomecomingColors.InkSoft,
                        lineHeight = 22.sp, textAlign = TextAlign.Center,
                        modifier = Modifier.align(Alignment.Center)
                    )
                } else {
                    var expandedIndex by remember { mutableStateOf<Int?>(null) }
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 24.dp, vertical = 34.dp)
                    ) {
                        monthKeys.forEachIndexed { index, key ->
                            val monthLetters = months[key].orEmpty()
                            val isExpanded = expandedIndex == index
                            val cardHeight by animateDpAsState(
                                targetValue = if (isExpanded) 430.dp else 128.dp,
                                animationSpec = spring(dampingRatio = 0.8f, stiffness = 300f),
                                label = "cardH"
                            )
                            // 参差微扰：种子固定（文档 §三）
                            val jitterRotation = remember(index) {
                                val r = java.util.Random((index * 31).toLong())
                                (r.nextFloat() - 0.5f) * (JITTER_ROTATION * 2f)
                            }
                            val jitterX = remember(index) {
                                val r = java.util.Random((index * 17).toLong())
                                ((r.nextFloat() - 0.5f) * 6).dp
                            }
                            // 包一层：可见高度 = 卡高 - 压叠量；卡片本体超出部分压在下一张上
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(cardHeight - CARD_OVERLAP)
                                    .zIndex(index.toFloat())
                            ) {
                                ThickCurvedCard(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(cardHeight)
                                        .offset(x = jitterX)
                                        .graphicsLayer { rotationZ = jitterRotation }
                                        .clickable { expandedIndex = if (isExpanded) null else index },
                                    cardIndex = index,
                                    monthKey = key,
                                    letters = monthLetters,
                                    expanded = isExpanded,
                                    onLetterTap = { selected.value = it }
                                )
                            }
                        }
                        Spacer(Modifier.height(110.dp))
                    }
                }
            }
        }
    }

    // ── 全文阅读 ──
    selected.value?.let { letter ->
        LetterReaderDialog(letter = letter, onClose = { selected.value = null })
    }
}

// ── 厚卡片（文档 §六 常量 / 色值） ──

private val CARD_CURL_HEIGHT = 8.dp
private val CARD_THICKNESS = 5.dp
private const val THICKNESS_LAYERS = 5
private val CARD_OVERLAP = 18.dp
private val JITTER_ROTATION = 0.8f

private val MONTH_NAMES = listOf(
    "January", "February", "March", "April", "May", "June",
    "July", "August", "September", "October", "November", "December"
)
private val ScreenBg = Color(0xFFF2EDE3)
private val SurfaceMid = Color(0xFFF1EBDE)
private val SurfaceLeft = Color(0xFFFBF8F1)
private val SurfaceLeft2 = Color(0xFFFCFAF4)
private val SurfaceLeftIn = Color(0xFFF5F0E5)
private val SurfaceRightIn = Color(0xFFF6F1E7)
private val SurfaceRight = Color(0xFFFDFBF6)
private val SurfaceRightEdge = Color(0xFFFFFDF8)
private val SectionNear = Color(0xFFF6F1E6)
private val SectionFar = Color(0xFFE8E1D2)
private val MonthNameColor = Color(0xFFC9A876)
private val HighlightColor = Color(0xFF8A7A66)
private val BulletColor = Color(0xFFB5AA98)
private val CalHeaderColor = Color(0xFFAAA093)
private val CalDayColor = Color(0xFF9A8E7C)
private val MarkedGreen = Color(0xFF9DB58A)
private val AnniversaryRed = Color(0xFFD98B8B)
private val CardSideBase = Color(0xFFE8E1D2)

/** 构造“打开状态”卡片的轮廓：上下边中间下凹（控制点 2 倍翘起高度），左右直边。 */
private fun buildCardPath(w: Float, h: Float, curlHeight: Float): Path = Path().apply {
    moveTo(0f, 0f)
    quadraticBezierTo(w / 2f, curlHeight * 2f, w, 0f)
    lineTo(w, h)
    quadraticBezierTo(w / 2f, h + curlHeight * 2f, 0f, h)
    close()
}

private fun darken(c: Color, f: Float): Color =
    Color(c.red * (1f - f), c.green * (1f - f), c.blue * (1f - f), c.alpha)

@Composable
private fun ThickCurvedCard(
    modifier: Modifier,
    cardIndex: Int,
    monthKey: String,
    letters: List<Letter>,
    expanded: Boolean,
    onLetterTap: (Letter) -> Unit
) {
    Box(modifier = modifier.clipToBounds()) {
        // ── 卡片外壳：投影 + 厚度 + 弧面（Canvas，文档 §二） ──
        Canvas(modifier = Modifier.matchParentSize()) {
            val w = size.width
            val h = size.height
            val curl = CARD_CURL_HEIGHT.toPx()

            // ① 投影：往左下偏，越靠下的卡片越重（多层近似模糊）
            val shadowAlpha = 0.10f + cardIndex * 0.015f
            for (i in 5 downTo 1) {
                val sp = buildCardPath(w, h, curl)
                sp.translate(Offset((-3 - i * 1.1f).dp.toPx(), (5 + i * 1.5f).dp.toPx()))
                drawPath(sp, Color.Black.copy(alpha = shadowAlpha / 3f))
            }

            // ② 厚度侧面：同一条弧形路径往下平移，颜色逐层加深（文档 §二②）
            for (i in THICKNESS_LAYERS downTo 1) {
                val yo = CARD_THICKNESS * (i / THICKNESS_LAYERS.toFloat())
                val sidePath = buildCardPath(w, h, curl)
                sidePath.translate(Offset(0f, yo.toPx()))
                drawPath(sidePath, darken(CardSideBase, 0.08f + (i / THICKNESS_LAYERS.toFloat()) * 0.12f))
            }

            // ③ 卡片表面：弧形轮廓 + 弧面光影（右更亮，光从右上来）
            val cardPath = buildCardPath(w, h, curl)
            val surfaceBrush = Brush.horizontalGradient(
                0.00f to SurfaceLeft,
                0.12f to SurfaceLeft2,
                0.38f to SurfaceLeftIn,
                0.50f to SurfaceMid,
                0.62f to SurfaceRightIn,
                0.88f to SurfaceRight,
                1.00f to SurfaceRightEdge
            )
            drawPath(cardPath, surfaceBrush)

            // 纸张噪点（限制在卡片路径内）
            clipPath(cardPath) {
                val rnd = java.util.Random(cardIndex * 977L)
                repeat(80) {
                    drawCircle(
                        color = Color(0x0A8A7A5A),
                        radius = 0.7f + rnd.nextFloat() * 1.0f,
                        center = Offset(rnd.nextFloat() * w, rnd.nextFloat() * h)
                    )
                }
            }
        }

        // ── 内容：左右避开翘起区（文档 §三） ──
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 16.dp)
        ) {
            MonthCardContent(
                monthKey = monthKey,
                letters = letters,
                expanded = expanded,
                onLetterTap = onLetterTap
            )
        }
    }
}

/** 卡片内容：月份名 + 迷你月历（被压住时露出的部分）；展开后追加当月日记列表。 */
@Composable
private fun MonthCardContent(
    monthKey: String,
    letters: List<Letter>,
    expanded: Boolean,
    onLetterTap: (Letter) -> Unit
) {
    val ym = runCatching { YearMonth.parse(monthKey) }.getOrNull()
    val monthIdx = (ym?.monthValue ?: 1) - 1

    Column(modifier = Modifier.fillMaxWidth()) {
        // 顶部行：月份名（左） + 迷你月历（右）——被压住时唯一露出的部分
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column {
                Text(
                    MONTH_NAMES[monthIdx],
                    fontSize = 25.sp,
                    fontWeight = FontWeight.Normal,
                    color = MonthNameColor,
                    fontFamily = FontFamily.Serif,
                    fontStyle = FontStyle.Italic
                )
                Text(
                    monthKey.take(4),
                    fontSize = 10.sp, color = Color(0xFFA89878)
                )
                if (letters.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "共 " + letters.size + " 封" + if (expanded) " · 点日期读全文" else "",
                        fontSize = 10.sp, color = BulletColor
                    )
                }
            }
            MiniCalendar(
                yearMonth = ym,
                markedDays = letters.map { it.date.takeLast(2).toIntOrNull() ?: 0 }.toSet()
            )
        }

        Spacer(Modifier.height(8.dp))

        if (!expanded) {
            // 收起态：日记要点
            letters.take(3).forEach { letter ->
                Row(modifier = Modifier.padding(vertical = 2.dp)) {
                    Text("✻ ", fontSize = 11.sp, color = BulletColor)
                    Text(
                        letter.content.replace("\n", " ").trim().take(18),
                        fontSize = 11.sp, lineHeight = 21.sp, color = HighlightColor,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
            }
        } else {
            // 展开态：当月每篇日记（日期 + 开头），点一条读全文
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                letters.forEach { letter ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onLetterTap(letter) }
                            .padding(vertical = 7.dp)
                    ) {
                        Text(
                            "✻ " + letter.date,
                            fontSize = 11.sp, color = MonthNameColor,
                            fontFamily = FontFamily.Serif
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            letter.content.replace("\n", " ").trim().take(24),
                            fontSize = 12.sp, color = HighlightColor,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            "读 →",
                            fontSize = 10.sp, color = HomecomingColors.Accent
                        )
                    }
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(0.7.dp)
                            .background(Color(0x1FA89A78))
                    )
                }
            }
        }
    }
}

/** 迷你月历（文档 §四）：写了日记的日子画青提绿实心圆。 */
@Composable
private fun MiniCalendar(yearMonth: YearMonth?, markedDays: Set<Int>) {
    if (yearMonth == null) return
    val firstDow = yearMonth.atDay(1).dayOfWeek.value % 7 // 周日=0
    val days = yearMonth.lengthOfMonth()
    Column(horizontalAlignment = Alignment.End) {
        Text(
            "日 一 二 三 四 五 六",
            fontSize = 8.sp,
            letterSpacing = 1.5.sp,
            color = CalHeaderColor
        )
        Spacer(Modifier.height(3.dp))
        var cell = 1 - firstDow
        while (cell <= days) {
            Row {
                repeat(7) {
                    val d = cell; cell++
                    Box(
                        Modifier.size(13.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (d in 1..days) {
                            if (d in markedDays) {
                                Box(
                                    Modifier
                                        .size(12.dp)
                                        .background(MarkedGreen, CircleShape)
                                )
                            }
                            Text(
                                d.toString(),
                                fontSize = 8.sp,
                                color = if (d in markedDays) Color.White else CalDayColor
                            )
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

// ── 全文阅读 ──

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
