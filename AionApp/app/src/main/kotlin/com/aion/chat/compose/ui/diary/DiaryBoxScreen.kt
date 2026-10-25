package com.aion.chat.compose.ui.diary

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
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
import com.aion.chat.compose.data.HomecomingDayStore
import com.aion.chat.compose.data.SupabaseMomentsStore
import com.aion.chat.compose.ui.theme.HomecomingColors
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.YearMonth

/**
 * 日记书架（Yuri 设计）：木质书柜 + 玻璃柜门 + 一月一本笔记本。
 * - 点柜门：两扇玻璃门绕外缘向外打开，露出柜内三层搁板
 * - 每月一本笔记本立在搁板上：厚度随篇数变化，六种封面风格轮换
 * - 点笔记本：全屏摊开这本本子——内页材质/横线/丝带跟封面风格配套，点条目读全文
 */
@Composable
fun DiaryBoxScreen(onBack: () -> Unit = {}) {
    val context = LocalContext.current
    val letters = remember { mutableStateOf<List<Letter>>(emptyList()) }
    val loading = remember { mutableStateOf(true) }
    val selectedLetter = remember { mutableStateOf<Letter?>(null) }
    val selectedMonth = remember { mutableStateOf<Pair<String, List<Letter>>?>(null) }
    val scope = rememberCoroutineScope()

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

    // 暖深色墙面（衬托家具）
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
                    Text("点柜门打开 · 一月一本 · 点本子读那个月", fontSize = 11.sp, color = Color(0xFFB9A88E))
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
                    // 书柜尺寸：高 = 宽 * 1.16，宽度按可用高度反推，居中
                    val maxW = maxWidth
                    val maxH = maxHeight - 24.dp
                    var cabW = maxW
                    if (maxW * 1.16f > maxH) cabW = maxH / 1.16f
                    val doorsOpen = remember { Animatable(0f) }
                    val doorProgress = doorsOpen.value

                    CabinetView(
                        cabinetWidth = cabW,
                        doorProgress = doorProgress,
                        months = months,
                        onToggleDoors = {
                            scope.launch {
                                doorsOpen.animateTo(
                                    if (doorsOpen.value > 0.5f) 0f else 1f,
                                    tween(620, easing = FastOutSlowInEasing)
                                )
                            }
                        },
                        onNotebookTap = { key, list ->
                            selectedMonth.value = key to list
                        },
                        modifier = Modifier.align(Alignment.Center)
                    )

                    if (doorProgress < 0.3f) {
                        Text(
                            "点柜门，打开它",
                            fontSize = 11.sp, color = Color(0xFFB9A88E),
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 6.dp)
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

// ── 书柜 ──

private val WoodFrameDark = Color(0xFF4E3826)
private val WoodFrame = Color(0xFF5F4430)
private val WoodFrameMid = Color(0xFF6E4F38)
private val WoodPanel = Color(0xFF7A563E)
private val WoodShelf = Color(0xFF8A684C)
private val GlassDark = Color(0xFF38302A)
private val Brass = Color(0xFFC9A84C)
private val CavityTop = Color(0xFF3A2C1E)
private val CavityBottom = Color(0xFF2A1F14)

@Composable
private fun CabinetView(
    cabinetWidth: Dp,
    doorProgress: Float,
    months: List<Pair<String, List<Letter>>>,
    onToggleDoors: () -> Unit,
    onNotebookTap: (String, List<Letter>) -> Unit,
    modifier: Modifier = Modifier
) {
    val cw = cabinetWidth
    val ch = cw * 1.16f

    Box(modifier = modifier.width(cw).height(ch)) {
        // ── 柜身底色（整体木色） ──
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(3.dp))
                .background(Brush.verticalGradient(listOf(WoodFrameMid, WoodFrameDark)))
        )

        // ── 上柜内腔（搁板 + 笔记本）──
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = ch * 0.075f)
                .size(width = cw * 0.89f, height = ch * 0.50f)
                .clip(RoundedCornerShape(2.dp))
                .background(Brush.verticalGradient(listOf(CavityTop, CavityBottom)))
        ) {
            val cavityW = cw * 0.89f
            val cavityH = ch * 0.50f
            // 三层搁板
            listOf(0.36f, 0.64f, 0.92f).forEach { fy ->
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .offset(y = cavityH * fy)
                        .fillMaxWidth()
                        .height(4.dp)
                        .background(WoodShelf)
                )
            }
            // 笔记本：一月一本，按搁板分配（新→旧），厚度随篇数
            val shelfBooks = List(3) { s -> months.filterIndexed { i, _ -> i % 3 == s } }
            shelfBooks.forEachIndexed { shelf, books ->
                val shelfY = cavityH * listOf(0.36f, 0.64f, 0.92f)[shelf]
                var x = cavityW * 0.05f
                books.forEach { (key, list) ->
                    val st = NOTEBOOK_STYLES[monthIndexOf(key) % NOTEBOOK_STYLES.size]
                    val bookW = 13.dp + 1.2.dp * list.size.coerceAtMost(16)
                    val bookH = cavityH * 0.22f
                    val enabled = doorProgress > 0.6f
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .offset(x = x, y = shelfY - bookH)
                            .size(width = bookW, height = bookH)
                            .graphicsLayer { alpha = 0.55f + 0.45f * doorProgress }
                            .clip(RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
                            .background(Brush.verticalGradient(listOf(st.cover, st.coverDark)))
                            .border(0.8.dp, Color(0x33000000), RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
                            .clickable(enabled = enabled) { onNotebookTap(key, list) }
                    ) {
                        // 书脊饰带 + 月份
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .fillMaxWidth()
                                .height(4.dp)
                                .background(st.ribbon)
                        )
                        Text(
                            key.takeLast(2) + "月",
                            fontSize = 6.sp, color = st.label,
                            maxLines = 1,
                            modifier = Modifier.align(Alignment.Center)
                        )
                        // 书底
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .height(3.dp)
                                .background(st.coverDark)
                        )
                    }
                    x += bookW + 5.dp
                }
            }
            // 关门时的压暗（门开着就亮）
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f * (1f - doorProgress)))
            )
        }

        // ── 两扇玻璃门（绕外缘向外打开） ──
        val doorH = ch * 0.545f
        val doorW = cw * 0.435f
        val doorTop = ch * 0.048f
        // 左门（铰链在左缘）
        DoorPanel(
            width = doorW, height = doorH,
            hingeLeft = true,
            progress = doorProgress,
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(x = cw * 0.03f, y = doorTop)
        )
        // 右门（铰链在右缘）
        DoorPanel(
            width = doorW, height = doorH,
            hingeLeft = false,
            progress = doorProgress,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = -cw * 0.03f, y = doorTop)
        )
        // 门中缝竖框（柜门框架，开门后依然在）
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = doorTop)
                .width(cw * 0.035f)
                .height(doorH)
                .background(WoodFrame)
        )
        // 门框 click 区（点门开/关：左右两门的合成区域）
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = doorTop)
                .fillMaxWidth()
                .height(doorH)
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    enabled = true
                ) { onToggleDoors() }
        )

        // ── 中段檐口 ──
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = ch * 0.595f)
                .fillMaxWidth(1.03f)
                .height(ch * 0.042f)
                .background(Brush.verticalGradient(listOf(WoodFrame, WoodFrameDark)))
        )

        // ── 下柜：两只抽屉 + 两扇板门 ──
        val lowerTop = ch * 0.645f
        // 抽屉
        listOf(0, 1).forEach { i ->
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(x = cw * (0.05f + i * 0.465f), y = lowerTop + ch * 0.012f)
                    .size(width = cw * 0.42f, height = ch * 0.105f)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Brush.verticalGradient(listOf(WoodPanel, WoodFrame)))
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(width = 34.dp, height = 5.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Brass)
                )
            }
        }
        // 下柜板门
        listOf(0, 1).forEach { i ->
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(x = cw * (0.05f + i * 0.465f), y = lowerTop + ch * 0.135f)
                    .size(width = cw * 0.42f, height = ch * 0.20f)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Brush.verticalGradient(listOf(WoodPanel, WoodFrame)))
                    .border(2.dp, WoodFrameDark, RoundedCornerShape(2.dp))
            ) {
                Box(
                    modifier = Modifier
                        .align(if (i == 0) Alignment.CenterEnd else Alignment.CenterStart)
                        .padding(horizontal = 6.dp)
                        .size(9.dp)
                        .clip(CircleShape)
                        .background(Brass)
                )
            }
        }

        // ── 底座 ──
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(ch * 0.045f)
                .background(WoodFrameDark)
        )
    }
}

/** 一扇玻璃门：木框 + 半透明玻璃 + 斜向反光 + 铜把手。 */
@Composable
private fun DoorPanel(
    width: Dp,
    height: Dp,
    hingeLeft: Boolean,
    progress: Float,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(width = width, height = height)
            .graphicsLayer {
                rotationY = if (hingeLeft) progress * 104f else -progress * 104f
                transformOrigin = TransformOrigin(
                    pivotFractionX = if (hingeLeft) 0f else 1f,
                    pivotFractionY = 0.5f
                )
                cameraDistance = 18f * density
            }
            .clip(RoundedCornerShape(2.dp))
    ) {
        // 门框木框
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(WoodFrame, WoodFrameDark)))
        )
        // 玻璃（半透明，能看见柜内）
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxSize()
                .padding(4.dp)
                .background(Brush.verticalGradient(listOf(GlassDark.copy(alpha = 0.55f), Color(0x66241C14))))
        ) {
            // 斜向反光
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawLine(
                    color = Color(0x22FFFFFF),
                    start = Offset(size.width * 0.25f, 0f),
                    end = Offset(size.width * 0.45f, size.height),
                    strokeWidth = 6f
                )
                drawLine(
                    color = Color(0x16FFFFFF),
                    start = Offset(size.width * 0.45f, 0f),
                    end = Offset(size.width * 0.65f, size.height),
                    strokeWidth = 3f
                )
            }
        }
        // 铜把手（门内侧边）
        Box(
            modifier = Modifier
                .align(if (hingeLeft) Alignment.CenterEnd else Alignment.CenterStart)
                .padding(horizontal = 7.dp)
                .size(width = 4.dp, height = 26.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Brass)
        )
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
                // 摊开的两页
                Row(
                    modifier = Modifier
                        .fillMaxWidth(0.92f)
                        .height(430.dp)
                        .shadow(16.dp, RoundedCornerShape(6.dp))
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) { /* 摊开的本子挡住关闭点击 */ }
                ) {
                    // 左页：月份题头页
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .background(Brush.verticalGradient(listOf(style.page, style.pageEdge)))
                    ) {
                        // 内页横线
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
                            // 左侧页边线
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
                            // 丝带书签
                            Box(
                                modifier = Modifier
                                    .size(width = 10.dp, height = 64.dp)
                                    .background(style.ribbon)
                            )
                        }
                        // 中缝阴影（书页向中缝凹陷）
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
                    // 右页：日记列表（内页滚动）
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
                        // 中缝阴影（左页侧）
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
