package com.aion.chat.compose.ui.diary

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.aion.chat.R
import com.aion.chat.compose.data.HomecomingDayStore
import com.aion.chat.compose.data.SupabaseMomentsStore
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.YearMonth

/**
 * 日记书架（Yuri 实拍素材版）：你的书柜图做柜体。
 * - 点柜图 → 「门开」动画：整张柜图淡出、露出后面的笔记本层（暗腔+搁板+一月一本）
 * - 笔记本厚度随篇数、六种封面风格轮换，开门后可点
 * - 点笔记本 → 全屏摊开（内页风格配套），点条目读全文
 */
@Composable
fun DiaryBoxScreen(onBack: () -> Unit = {}) {
    val context = LocalContext.current
    val letters = remember { mutableStateOf<List<Letter>>(emptyList()) }
    val loading = remember { mutableStateOf(true) }
    val selectedLetter = remember { mutableStateOf<Letter?>(null) }
    val selectedMonth = remember { mutableStateOf<Pair<String, List<Letter>>?>(null) }

    LaunchedEffect(Unit) {
        loading.value = true
        letters.value = loadLetters(context)
        loading.value = false
    }

    val monthKeys = remember(letters.value) {
        letters.value.map { it.date.take(7) }.distinct().sortedDescending()
    }
    val months = remember(letters.value) {
        monthKeys.map { it to letters.value.filter { l -> l.date.take(7) == it } }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF221A12))) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val rnd = java.util.Random(11L)
            repeat(160) {
                drawCircle(
                    color = Color(0x0DFFFFFF),
                    radius = 0.7f + rnd.nextFloat() * 1.0f,
                    center = Offset(rnd.nextFloat() * size.width, rnd.nextFloat() * size.height)
                )
            }
        }

        Column(modifier = Modifier.fillMaxSize()) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 14.dp)) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回",
                    tint = Color(0xFFE8DCC8),
                    modifier = Modifier
                        .size(24.dp)
                        .clickable { onBack() }
                )
                Column(modifier = Modifier.weight(1f).padding(start = 10.dp)) {
                    Text("日记书架", fontSize = 22.sp, color = Color(0xFFE8DCC8), fontWeight = FontWeight.Medium)
                    Text("点书柜打开 · 一月一本 · 点本子读那个月", fontSize = 11.sp, color = Color(0xFFB9A88E))
                }
            }

            BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
                if (loading.value) {
                    Text(
                        "擦拭柜子中…",
                        fontSize = 12.sp, color = Color(0xFFB9A88E),
                        modifier = Modifier.align(Alignment.Center)
                    )
                } else {
                    // 柜图 720x1221 → 高/宽 1.696
                    val maxW = maxWidth - 24.dp
                    val maxH = maxHeight - 20.dp
                    var cabW = maxW
                    if (maxW * (1221f / 720f) > maxH) cabW = maxH * (720f / 1221f)
                    val cabH = cabW * (1221f / 720f)
                    val scope = rememberCoroutineScope()
                    val doorsOpen = remember { Animatable(0f) }
                    val open = doorsOpen.value > 0.6f

                    fun toggleDoors() {
                        scope.launch {
                            doorsOpen.animateTo(
                                if (doorsOpen.value > 0.5f) 0f else 1f,
                                tween(620, easing = FastOutSlowInEasing)
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(width = cabW, height = cabH)
                    ) {
                        // ── 柜内层（门后面）：暗腔 + 搁板 + 笔记本 ──
                        // 玻璃门内腔在素材图中的位置：x 4%~96%，y 9%~52%
                        val cavityX = cabW * 0.04f
                        val cavityY = cabH * 0.09f
                        val cavityW = cabW * 0.92f
                        val cavityH = cabH * 0.43f
                        Box(
                            modifier = Modifier
                                .offset(x = cavityX, y = cavityY)
                                .size(width = cavityW, height = cavityH)
                                .clip(RoundedCornerShape(2.dp))
                                .background(
                                    Brush.verticalGradient(
                                        listOf(Color(0xFF33261A), Color(0xFF241A10))
                                    )
                                )
                        ) {
                            val shelfYs = listOf(0.335f, 0.615f, 0.895f)
                            shelfYs.forEach { fy ->
                                Box(
                                    modifier = Modifier
                                        .offset(y = cavityH * fy)
                                        .fillMaxWidth()
                                        .height(cavityH * 0.035f)
                                        .background(Color(0xFF8A684C))
                                )
                            }
                            val shelfBooks = List(3) { s -> months.filterIndexed { i, _ -> i % 3 == s } }
                            shelfBooks.forEachIndexed { shelf, books ->
                                val shelfY = cavityH * shelfYs[shelf]
                                var x = cavityW * 0.05f
                                books.forEach { (key, list) ->
                                    val st = NOTEBOOK_STYLES[monthIndexOf(key) % NOTEBOOK_STYLES.size]
                                    val bookW = 12.dp + 1.1.dp * list.size.coerceAtMost(16)
                                    val bookH = cavityH * 0.225f
                                    Box(
                                        modifier = Modifier
                                            .offset(x = x, y = shelfY - bookH)
                                            .size(width = bookW, height = bookH)
                                            .graphicsLayer { alpha = 0.55f + 0.45f * doorsOpen.value }
                                            .clip(RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
                                            .background(Brush.verticalGradient(listOf(st.cover, st.coverDark)))
                                            .border(0.8.dp, Color(0x33000000), RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
                                            .clickable(enabled = open) { selectedMonth.value = key to list }
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.TopCenter)
                                                .fillMaxWidth()
                                                .height(3.dp)
                                                .background(st.ribbon)
                                        )
                                        Text(
                                            key.takeLast(2) + "月",
                                            fontSize = 6.sp, color = st.label,
                                            maxLines = 1,
                                            modifier = Modifier.align(Alignment.Center)
                                        )
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.BottomCenter)
                                                .fillMaxWidth()
                                                .height(2.5.dp)
                                                .background(st.coverDark)
                                        )
                                    }
                                    x += bookW + cavityW * 0.016f
                                }
                            }
                            // 关门时柜内压暗
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.55f * (1f - doorsOpen.value)))
                            )
                        }

                        // ── 柜体图（整张，含玻璃门）—— 关门态主体 ──
                        Image(
                            painter = painterResource(R.drawable.shelf_closed),
                            contentDescription = "书柜",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    alpha = 1f - doorsOpen.value * 0.92f
                                }
                        )
                        // 开门后柜体轮廓还在（木框描边）
                        if (doorsOpen.value > 0.05f) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer { alpha = doorsOpen.value }
                                    .border(6.dp, Color(0xFF5F4430), RoundedCornerShape(4.dp))
                            )
                        }

                        // ── 点击区：点柜开门/关门；开门后点笔记本（子元素先消费） ──
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clickable(
                                    indication = null,
                                    interactionSource = remember { MutableInteractionSource() }
                                ) { toggleDoors() }
                        )
                    }

                    if (doorsOpen.value < 0.3f) {
                        Text(
                            "点书柜，打开它",
                            fontSize = 11.sp, color = Color(0xFFB9A88E),
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 4.dp)
                        )
                    }
                }
            }
        }
    }

    // ── 摊开的本子 ──
    selectedMonth.value?.let { (key, list) ->
        val style = NOTEBOOK_STYLES[monthIndexOf(key) % NOTEBOOK_STYLES.size]
        NotebookSpreadDialog(
            monthKey = key,
            letters = list,
            style = style,
            onClose = { selectedMonth.value = null },
            onLetterTap = { selectedLetter.value = it }
        )
    }

    // ── 全文阅读 ──
    selectedLetter.value?.let { letter ->
        LetterReaderDialog(letter = letter, onClose = { selectedLetter.value = null })
    }
}

// ── 笔记本风格（六种轮换：封面/内页配套） ──

private data class NotebookStyle(
    val name: String,
    val cover: Color,
    val coverDark: Color,
    val ribbon: Color,
    val label: Color,
    val page: Color,
    val pageEdge: Color,
    val pageLine: Color,
    val pageText: Color,
    val accent: Color
)

private val NOTEBOOK_STYLES = listOf(
    NotebookStyle(
        "酒红布面", Color(0xFF7A2E2E), Color(0xFF5C2020), Color(0xFFD9B98A), Color(0xFFF0E0C8),
        Color(0xFFFAF3E3), Color(0xFFEFE6D2), Color(0x33A08C6E), Color(0xFF5A4632), Color(0xFF7A2E2E)
    ),
    NotebookStyle(
        "墨绿皮革", Color(0xFF2F4A3C), Color(0xFF20362B), Color(0xFFC9A84C), Color(0xFFE8DCC0),
        Color(0xFFF3F0E4), Color(0xFFE6E2D2), Color(0x338A9A7A), Color(0xFF44523E), Color(0xFF2F4A3C)
    ),
    NotebookStyle(
        "藏蓝硬壳", Color(0xFF2E3A5C), Color(0xFF1F2A44), Color(0xFFD9C08A), Color(0xFFE4E0D4),
        Color(0xFFF7F4EC), Color(0xFFE9E6DC), Color(0x337A8AA8), Color(0xFF3A4460), Color(0xFF2E3A5C)
    ),
    NotebookStyle(
        "芥黄牛皮", Color(0xFFC9963C), Color(0xFFA57A2C), Color(0xFF5C4632), Color(0xFF4E3C28),
        Color(0xFFFCF6E8), Color(0xFFF2EAD6), Color(0x33C9A84C), Color(0xFF6E5A38), Color(0xFFA57A2C)
    ),
    NotebookStyle(
        "紫梅绒面", Color(0xFF5C3A5E), Color(0xFF432A45), Color(0xFFD9B9DB), Color(0xFFEBDCEC),
        Color(0xFFF9F1F2), Color(0xFFEFE4E6), Color(0x33A87FAA), Color(0xFF584258), Color(0xFF5C3A5E)
    ),
    NotebookStyle(
        "原棕皮面", Color(0xFF6E4A32), Color(0xFF523622), Color(0xFFD9C6A8), Color(0xFFEFE4D0),
        Color(0xFFF6EFE0), Color(0xFFEAE2CE), Color(0x33A08C6E), Color(0xFF5A4632), Color(0xFF6E4A32)
    )
)

private fun monthIndexOf(date: String): Int {
    val month = if (date.length >= 7) date.split("-")[1].toIntOrNull() ?: 1 else 1
    return (month - 1).coerceIn(0, 11)
}

// ── 摊开的本子（全屏：左右两页，内页风格跟封面配套） ──

@Composable
private fun NotebookSpreadDialog(
    monthKey: String,
    letters: List<Letter>,
    style: NotebookStyle,
    onClose: () -> Unit,
    onLetterTap: (Letter) -> Unit
) {
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF1C150E))
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) { onClose() },
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth(0.92f)
                        .height(430.dp)
                        .shadow(16.dp, RoundedCornerShape(6.dp))
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) { }
                ) {
                    // 左页：月份题头
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .background(Brush.verticalGradient(listOf(style.page, style.pageEdge)))
                    ) {
                        Canvas(modifier = Modifier.matchParentSize()) {
                            var y = 44.dp.toPx()
                            while (y < size.height - 20.dp.toPx()) {
                                drawLine(
                                    color = style.pageLine,
                                    start = Offset(14.dp.toPx(), y),
                                    end = Offset(size.width - 12.dp.toPx(), y),
                                    strokeWidth = 1f
                                )
                                y += 27.dp.toPx()
                            }
                            drawLine(
                                color = style.accent.copy(alpha = 0.35f),
                                start = Offset(10.dp.toPx(), 0f),
                                end = Offset(10.dp.toPx(), size.height),
                                strokeWidth = 1.2f
                            )
                        }
                        Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
                            Text(
                                MONTH_NAMES2[monthIndexOf(monthKey)],
                                fontSize = 30.sp, color = style.accent,
                                fontFamily = FontFamily.Serif, fontStyle = FontStyle.Italic,
                                fontWeight = FontWeight.Light
                            )
                            Text(
                                monthKey.take(4) + " · " + style.name,
                                fontSize = 11.sp, color = style.pageText.copy(alpha = 0.7f)
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                "共 " + letters.size + " 封",
                                fontSize = 12.sp, color = style.pageText,
                                fontFamily = FontFamily.Serif
                            )
                            Spacer(Modifier.height(20.dp))
                            Box(
                                modifier = Modifier
                                    .size(width = 10.dp, height = 64.dp)
                                    .background(style.ribbon)
                            )
                        }
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .fillMaxHeight()
                                .width(14.dp)
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(Color.Transparent, Color(0x26000000))
                                    )
                                )
                        )
                    }
                    // 右页：日记列表
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .background(Brush.verticalGradient(listOf(style.page, style.pageEdge)))
                    ) {
                        Canvas(modifier = Modifier.matchParentSize()) {
                            var y = 44.dp.toPx()
                            while (y < size.height - 20.dp.toPx()) {
                                drawLine(
                                    color = style.pageLine,
                                    start = Offset(12.dp.toPx(), y),
                                    end = Offset(size.width - 14.dp.toPx(), y),
                                    strokeWidth = 1f
                                )
                                y += 27.dp.toPx()
                            }
                        }
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(start = 14.dp, end = 16.dp, top = 16.dp, bottom = 14.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            letters.forEach { letter ->
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onLetterTap(letter) }
                                        .padding(vertical = 8.dp)
                                ) {
                                    Text(
                                        "✻ " + letter.date,
                                        fontSize = 11.sp, color = style.accent,
                                        fontFamily = FontFamily.Serif
                                    )
                                    Text(
                                        letter.content.replace("\n", " ").trim().take(30),
                                        fontSize = 12.sp, color = style.pageText,
                                        lineHeight = 18.sp,
                                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(top = 3.dp)
                                    )
                                }
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .height(0.7.dp)
                                        .background(style.pageLine)
                                )
                            }
                        }
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .fillMaxHeight()
                                .width(14.dp)
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(Color(0x26000000), Color.Transparent)
                                    )
                                )
                        )
                    }
                }
                Spacer(Modifier.height(18.dp))
                Text(
                    "点本子外合上 · 点日期读那天的信",
                    fontSize = 11.sp, color = Color(0xFFB9A88E)
                )
            }
        }
    }
}

private val MONTH_NAMES2 = listOf(
    "January", "February", "March", "April", "May", "June",
    "July", "August", "September", "October", "November", "December"
)

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
                    interactionSource = remember { MutableInteractionSource() }
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
