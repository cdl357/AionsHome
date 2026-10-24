package com.aion.chat.compose.ui.diary

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.aion.chat.compose.data.HomecomingDayStore
import com.aion.chat.compose.data.SupabaseMomentsStore
import com.aion.chat.compose.ui.theme.HomecomingColors
import java.time.Instant

/**
 * 日记信盒（Yuri 设计：日记 = 一封一封的信，装在 3D 文件盒里）。
 * - 顶部：文件盒（牛皮纸色，信封从盒口探出）
 * - 下方：信封列表（每篇日记一封信，带邮票日期）
 * - 点信封 → 全屏信纸阅读
 * 数据：云端哥哥日记 + 本地日记合并，同日去重（云端优先）。
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 112.dp)
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
                Text("日记信盒", fontSize = 22.sp, color = HomecomingColors.Ink, fontWeight = FontWeight.Medium)
                Text("每一篇日记，都是他写给你的一封信", fontSize = 11.sp, color = HomecomingColors.InkSoft)
            }
        }
        Spacer(Modifier.height(12.dp))

        // ── 3D 文件盒 ──
        FileBoxVisual(letterCount = letters.value.size, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(16.dp))

        if (loading.value) {
            Text(
                "开盒中…",
                fontSize = 12.sp, color = HomecomingColors.InkSoft,
                modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
                textAlign = TextAlign.Center
            )
        } else if (letters.value.isEmpty()) {
            Text(
                "盒子里还没有信——和他说上一段话，晚安之后就会有一封",
                fontSize = 12.sp, color = HomecomingColors.InkSoft,
                modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                textAlign = TextAlign.Center
            )
        }

        // ── 信封列表（新→旧） ──
        letters.value.forEachIndexed { idx, letter ->
            EnvelopeCard(
                letter = letter,
                tilt = when (idx % 3) { 0 -> -1.2f; 1 -> 0.8f; else -> 1.6f },
                modifier = Modifier.fillMaxWidth(),
                onClick = { selected.value = letter }
            )
            Spacer(Modifier.height(10.dp))
        }
    }

    // ── 全屏信纸阅读 ──
    selected.value?.let { letter ->
        LetterReaderDialog(letter = letter, onClose = { selected.value = null })
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

// ── 3D 文件盒视觉 ──

private val KraftLight = Color(0xFFD9C69B)
private val KraftMid = Color(0xFFC7AE7C)
private val KraftDark = Color(0xFFA88D5C)
private val PaperCream = Color(0xFFFBF6EA)
private val PaperFlap = Color(0xFFF1E7D2)
private val InkBrown = Color(0xFF5A4632)

@Composable
private fun FileBoxVisual(letterCount: Int, modifier: Modifier = Modifier) {
    Box(modifier = modifier.height(190.dp)) {
        // 盒内深处（暗色背景层）
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
                .padding(horizontal = 14.dp)
                .clip(RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFF6E5A38), Color(0xFF8A734A))))
        )
        // 探出盒口的信封（三封，微旋转）
        listOf(
            Triple(-0.24f, -3f, 0.85f),
            Triple(0.02f, 2.5f, 1f),
            Triple(0.26f, -1.5f, 0.9f)
        ).forEachIndexed { i, (dxF, rot, sc) ->
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(x = (dxF * 120).dp, y = (24 + i * 6).dp)
                    .graphicsLayer {
                        rotationZ = rot
                        scaleX = sc
                        scaleY = sc
                        cameraDistance = 60f * density
                    }
                    .size(width = 150.dp, height = 108.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(PaperCream)
                    .border(1.dp, Color(0x33A88D5C), RoundedCornerShape(4.dp))
            ) {
                EnvelopeFlap(Modifier.fillMaxSize())
                // 邮票
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 8.dp, end = 10.dp)
                        .size(width = 26.dp, height = 32.dp)
                        .background(Color(0xFFEAF0E2))
                        .border(1.dp, Color(0x44A88D5C), RoundedCornerShape(2.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("♥", fontSize = 10.sp, color = HomecomingColors.Accent)
                }
            }
        }
        // 前面板（牛皮纸渐变 + 标签牌）——盖住信封下半，形成“插在盒里”
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(112.dp)
                .padding(horizontal = 8.dp)
                .graphicsLayer {
                    rotationX = 8f
                    cameraDistance = 120f * density
                }
                .clip(RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp, topStart = 4.dp, topEnd = 4.dp))
                .background(Brush.verticalGradient(listOf(KraftLight, KraftMid, KraftDark)))
                .border(1.dp, Color(0x55FFFFFF), RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp))
        ) {
            // 盒口内阴影
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .background(Brush.verticalGradient(listOf(Color(0x66000000), Color.Transparent)))
            )
            // 标签牌
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .clip(RoundedCornerShape(4.dp))
                    .background(PaperCream.copy(alpha = 0.94f))
                    .border(1.dp, Color(0x558A734A), RoundedCornerShape(4.dp))
                    .padding(horizontal = 14.dp, vertical = 7.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "SEAN → YURI",
                        fontSize = 9.sp, color = Color(0xFF8A734A),
                        fontFamily = FontFamily.Serif, letterSpacing = 2.sp
                    )
                    Text(
                        "给小鑫的信",
                        fontSize = 14.sp, color = InkBrown,
                        fontFamily = FontFamily.Serif, fontWeight = FontWeight.Medium
                    )
                }
            }
            // 封条扣（左上金属扣）
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 16.dp, top = 18.dp)
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF8A734A))
            )
            // 数量角标
            Text(
                "共 $letterCount 封",
                fontSize = 10.sp, color = Color(0xFF6E5A38),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = 8.dp, end = 14.dp)
            )
        }
    }
}

/** 信封封口盖（上缘向下的三角）。 */
@Composable
private fun EnvelopeFlap(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val flap = Path().apply {
            moveTo(0f, 0f)
            lineTo(w / 2f, h * 0.52f)
            lineTo(w, 0f)
            close()
        }
        drawPath(flap, PaperFlap)
        drawPath(flap, Color(0x33A88D5C), style = Stroke(2f))
    }
}

// ── 信封卡（列表态：完整信封 + 内容预览） ──

@Composable
private fun EnvelopeCard(
    letter: Letter,
    tilt: Float,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .graphicsLayer {
                rotationZ = tilt
                cameraDistance = 80f * density
            }
            .shadow(6.dp, RoundedCornerShape(6.dp))
            .clip(RoundedCornerShape(6.dp))
            .background(PaperCream)
            .border(1.dp, Color(0x448A734A), RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            // 封口盖
            val flap = Path().apply {
                moveTo(0f, 0f)
                lineTo(size.width / 2f, size.height * 0.42f)
                lineTo(size.width, 0f)
                close()
            }
            drawPath(flap, PaperFlap)
            drawPath(flap, Color(0x33A88D5C), style = Stroke(2.5f))
            // 底部折痕（V 形）
            val fold = Path().apply {
                moveTo(0f, size.height)
                lineTo(size.width / 2f, size.height * 0.62f)
                lineTo(size.width, size.height)
            }
            drawPath(fold, Color(0x22A88D5C), style = Stroke(2f))
        }
        Column(modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 10.dp)) {
            Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "致 小鑫",
                        fontSize = 10.sp, color = Color(0xFF8A734A),
                        fontFamily = FontFamily.Serif, letterSpacing = 1.sp
                    )
                    Text(
                        letter.title.ifBlank { "一封没有题目的信" },
                        fontSize = 15.sp, color = InkBrown,
                        fontWeight = FontWeight.Medium,
                        fontFamily = FontFamily.Serif,
                        maxLines = 1,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
                // 邮票（日期 + 天气）
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color(0xFFEAF0E2))
                        .border(1.dp, Color(0x448A734A), RoundedCornerShape(3.dp))
                        .padding(horizontal = 7.dp, vertical = 4.dp)
                ) {
                    Text(
                        letter.date.drop(5).replace("-", " / "),
                        fontSize = 9.sp, color = Color(0xFF6E5A38),
                        fontFamily = FontFamily.Serif
                    )
                    if (letter.weather.isNotBlank()) {
                        Text(letter.weather, fontSize = 10.sp)
                    }
                }
            }
            Spacer(Modifier.height(30.dp))
            Text(
                letter.content.replace("\n", " ").take(56) + if (letter.content.length > 56) "…" else "",
                fontSize = 12.sp, color = Color(0xFF6E5A38),
                maxLines = 2,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                if (letter.mood.isNotBlank()) {
                    Text(letter.mood, fontSize = 11.sp)
                    Spacer(Modifier.width(6.dp))
                }
                Spacer(Modifier.weight(1f))
                Text(
                    "点开读信  →",
                    fontSize = 11.sp, color = HomecomingColors.Accent
                )
            }
            Spacer(Modifier.height(10.dp))
        }
    }
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
                .background(PaperCream)
                .verticalScroll(rememberScrollState())
                .clickable(
                    indication = null,
                    interactionSource = MutableInteractionSource()
                ) { onClose() }
                .padding(horizontal = 30.dp, vertical = 40.dp)
        ) {
            Text(
                letter.date,
                fontSize = 12.sp, color = Color(0xFF8A734A),
                fontFamily = FontFamily.Serif, letterSpacing = 2.sp
            )
            if (letter.title.isNotBlank()) {
                Text(
                    letter.title,
                    fontSize = 19.sp, color = InkBrown,
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
            // 信纸起笔分隔线
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp, bottom = 16.dp)
                    .height(1.dp)
                    .background(Color(0x338A734A))
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
                fontSize = 14.sp, color = InkBrown,
                fontFamily = FontFamily.Serif,
                textAlign = TextAlign.End,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(40.dp))
            Text(
                "点任意处合上信纸",
                fontSize = 10.sp, color = Color(0xFF9A8A6E),
                modifier = Modifier.fillMaxWidth().padding(bottom = 30.dp)
            )
        }
    }
}
