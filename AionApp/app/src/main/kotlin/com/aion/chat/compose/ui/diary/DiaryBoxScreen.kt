package com.aion.chat.compose.ui.diary

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import com.aion.chat.compose.data.HomecomingDayStore
import com.aion.chat.compose.data.SupabaseMomentsStore
import com.aion.chat.compose.ui.theme.HomecomingColors
import java.time.Instant

/**
 * 日记信盒三稿（Yuri 参考视频：会摇曳的磨砂亚克力文件盒 + 轻交互 + 「样」换肤）。
 * - 关盒：整体轻摇（rotationZ/Y 摇曳 + 珍珠绳反向摆），信模糊藏在磨砂后
 * - 点盒：盖子弹簧翻全开，三封信错落升起变清晰可点；下方按「新信/旧信/老信」分层展开
 * - 右上「样」：挑一版（光：胭脂/奶油/粉蓝/粉金 · 绳：珍珠絃/弹力绳），点了马上换并记住
 * - 心形挂饰：点它直接读最新一封
 * 数据：云端哥哥日记 + 本地日记合并，同日去重（云端优先）。
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun DiaryBoxScreen(onBack: () -> Unit = {}) {
    val context = LocalContext.current
    val letters = remember { mutableStateOf<List<Letter>>(emptyList()) }
    val loading = remember { mutableStateOf(true) }
    val boxOpen = remember { mutableStateOf(false) }
    val selected = remember { mutableStateOf<Letter?>(null) }
    val showSkin = remember { mutableStateOf(false) }
    val prefs = remember {
        context.getSharedPreferences("diarybox_prefs", android.content.Context.MODE_PRIVATE)
    }
    val skinLight = remember { mutableStateOf(prefs.getInt("light", 0)) }
    val skinRope = remember { mutableStateOf(prefs.getInt("rope", 0)) }
    val skin = BOX_LIGHTS[skinLight.value.coerceIn(0, BOX_LIGHTS.lastIndex)]

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
            // 「样」换肤入口（参照视频右上角）
            Text(
                "样",
                fontSize = 17.sp, color = skin.accent,
                fontFamily = FontFamily.Serif,
                modifier = Modifier
                    .clickable { showSkin.value = true }
                    .padding(8.dp)
            )
        }
        Spacer(Modifier.height(14.dp))

        // ── 磨砂亚克力文件盒（摇曳 + 开合） ──
        AcrylicFileBox(
            letterCount = letters.value.size,
            previewLetters = letters.value.take(3),
            open = boxOpen.value,
            onToggle = { boxOpen.value = !boxOpen.value },
            onLetterTap = { selected.value = it },
            skin = skin,
            rope = skinRope.value,
            newestLetter = letters.value.firstOrNull(),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(6.dp))

        Text(
            when {
                loading.value -> "开盒中…"
                letters.value.isEmpty() -> "盒子里还没有信——和他说上一段话，晚安之后就会有一封"
                boxOpen.value -> "点信读信 · 点盒子合上"
                else -> "点一下盒子打开 · 右上「样」可挑一版"
            },
            fontSize = 12.sp, color = HomecomingColors.InkSoft,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(14.dp))

        // ── 分层文件架（开盒后展开：新信 / 旧信 / 老信） ──
        AnimatedVisibility(
            visible = boxOpen.value && !loading.value && letters.value.isNotEmpty(),
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                boxTiers(letters.value).forEach { (label, list) ->
                    TierBand(label = label, count = list.size, skin = skin)
                    Spacer(Modifier.height(8.dp))
                    list.forEachIndexed { idx, letter ->
                        EnvelopeCard(
                            letter = letter,
                            tilt = when (idx % 3) { 0 -> -1.2f; 1 -> 0.8f; else -> 1.6f },
                            accent = skin.accent,
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { selected.value = letter }
                        )
                        Spacer(Modifier.height(10.dp))
                    }
                    Spacer(Modifier.height(10.dp))
                }
            }
        }
    }

    // ── 「样」换肤面板 ──
    if (showSkin.value) {
        SkinSheet(
            light = skinLight,
            rope = skinRope,
            prefs = prefs,
            onClose = { showSkin.value = false }
        )
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

/** 按时间分层：新信（本月）/ 旧信（今年更早）/ 老信（往年），只返回非空层。 */
private fun boxTiers(all: List<Letter>): List<Pair<String, List<Letter>>> {
    val now = java.time.LocalDate.now().toString()
    val grouped = all.groupBy { l ->
        when {
            l.date.take(7) == now.take(7) -> "新信"
            l.date.take(4) == now.take(4) -> "旧信"
            else -> "老信"
        }
    }
    return listOf("新信", "旧信", "老信").mapNotNull { k -> grouped[k]?.let { k to it } }
}

// ── 皮肤（光 × 绳） ──

data class BoxSkin(
    val name: String,
    val top: Color, val mid: Color, val bottom: Color, val edge: Color,
    val accent: Color, val soft: Color
)

private val BOX_LIGHTS = listOf(
    BoxSkin("胭脂", Color(0xB8FDEFF4), Color(0xA6F9E2EB), Color(0x99F4D5E2), Color(0xD9FFFFFF), Color(0xFFA85A72), Color(0xFFE3B4C4)),
    BoxSkin("奶油", Color(0xC2FFF9EE), Color(0xB0F9EEDB), Color(0x99F2E2CB), Color(0xD9FFFFFF), Color(0xFF8A6A48), Color(0xFFE4CDA9)),
    BoxSkin("粉蓝", Color(0xB8ECF5FD), Color(0xA6DEEEF9), Color(0x99CBE3F3), Color(0xD9FFFFFF), Color(0xFF5586A6), Color(0xFFB7D6E6)),
    BoxSkin("粉金", Color(0xB8FDF1E6), Color(0xA6F9E6D4), Color(0x99F2DCC2), Color(0xE6FFF7EE), Color(0xFFB07E4E), Color(0xFFEACBA6))
)

private val PaperCream = Color(0xFFFBF6EA)
private val PaperFlap = Color(0xFFF1E7D2)
private val InkBrown = Color(0xFF5A4632)

// ── 磨砂亚克力文件盒（摇曳 + 开盖 + 信升起 + 心形挂饰） ──

@Composable
private fun AcrylicFileBox(
    letterCount: Int,
    previewLetters: List<Letter>,
    open: Boolean,
    onToggle: () -> Unit,
    onLetterTap: (Letter) -> Unit,
    skin: BoxSkin,
    rope: Int,
    newestLetter: Letter?,
    modifier: Modifier = Modifier
) {
    // 摇曳：像立在桌上的空盒子被风轻轻吹（开盒后停稳）
    val sway = rememberInfiniteTransition(label = "sway")
    val swayZ by sway.animateFloat(
        -1.3f, 1.3f,
        infiniteRepeatable(tween(3200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "swayZ"
    )
    val swayY by sway.animateFloat(
        -2.6f, 2.6f,
        infiniteRepeatable(tween(3200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "swayY"
    )
    val floatY by sway.animateFloat(
        0f, -5f,
        infiniteRepeatable(tween(2600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "floatY"
    )
    val damp by animateFloatAsState(if (open) 0f else 1f, tween(700), label = "damp")
    // 盖子角度（铰链在盖底，全开翻到背后）
    val lidAngle by animateFloatAsState(
        targetValue = if (open) -158f else 0f,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow),
        label = "lid"
    )

    Box(
        modifier = modifier
            .height(376.dp)
            .graphicsLayer {
                rotationZ = swayZ * damp
                rotationY = swayY * damp
                translationY = floatY * damp * density
                cameraDistance = 520f * density
                transformOrigin = TransformOrigin(0.5f, 0.85f)
            },
        contentAlignment = Alignment.TopCenter
    ) {
        // 落地影子
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 4.dp)
                .width(230.dp)
                .height(12.dp)
                .blur(6.dp)
                .clip(RoundedCornerShape(50))
                .background(Color(0x245A4632))
        )

        // ── 盒身 ──
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 16.dp)
                .fillMaxWidth()
                .height(212.dp)
        ) {
            // 盒内的信（关盒：模糊半透；开盒：升起变清晰）
            previewLetters.forEachIndexed { i, letter ->
                val rise by animateFloatAsState(
                    targetValue = if (open) 1f else 0f,
                    animationSpec = tween(540, delayMillis = if (open) i * 130 else 0, easing = FastOutSlowInEasing),
                    label = "rise$i"
                )
                val xOffset = when (i) { 0 -> -40f; 1 -> 4f; else -> 44f }
                val riseBase = 96f - i * 26f
                MiniEnvelope(
                    letter = letter,
                    accent = skin.accent,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .zIndex(if (open) 3f else 0f)
                        .graphicsLayer {
                            translationY = -(riseBase * rise)
                            translationX = xOffset * (1f - rise) * 0.3f
                            rotationZ = when (i) { 0 -> -7f; 1 -> 2f; else -> -3f } * (0.4f + 0.6f * rise)
                            alpha = 0.55f + 0.45f * rise
                            cameraDistance = 200f * density
                        }
                        .blur(((1f - rise) * 4f).dp),
                    onClick = { if (open) onLetterTap(letter) }
                )
            }

            // 前板（磨砂亚克力）
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxSize()
                    .zIndex(1f)
                    .clip(RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp, topStart = 12.dp, topEnd = 12.dp))
                    .background(Brush.verticalGradient(listOf(skin.top, skin.mid, skin.bottom)))
                    .border(1.5.dp, skin.edge, RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp, topStart = 12.dp, topEnd = 12.dp))
                    .clickable(onClick = onToggle)
            ) {
                // 盒口内阴影
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(14.dp)
                        .background(Brush.verticalGradient(listOf(Color(0x2E8A5A6B), Color.Transparent)))
                )
                // 标签牌
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .clip(RoundedCornerShape(6.dp))
                        .background(PaperCream.copy(alpha = 0.92f))
                        .border(1.dp, skin.accent.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "SEAN → YURI",
                            fontSize = 9.sp, color = skin.accent,
                            fontFamily = FontFamily.Serif, letterSpacing = 2.sp
                        )
                        Text(
                            "给小鑫的信",
                            fontSize = 15.sp, color = InkBrown,
                            fontFamily = FontFamily.Serif, fontWeight = FontWeight.Medium
                        )
                    }
                }
                // 签名（斜体衬线，像盒身上的烫印）
                Text(
                    "Sean",
                    fontSize = 17.sp, color = skin.accent.copy(alpha = 0.55f),
                    fontFamily = FontFamily.Serif, fontStyle = FontStyle.Italic,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 18.dp, bottom = 30.dp)
                )
                // 绳：珍珠絃（会摆） / 弹力绳
                if (rope == 0) {
                    Canvas(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 18.dp, bottom = 14.dp)
                            .size(width = 74.dp, height = 64.dp)
                            .graphicsLayer {
                                rotationZ = -swayZ * 1.9f * damp
                                transformOrigin = TransformOrigin(0.92f, 0.08f)
                            }
                    ) {
                        // 绳圈
                        val string = Path().apply {
                            moveTo(size.width * 0.95f, size.height * 0.15f)
                            cubicTo(
                                size.width * 0.62f, size.height * 0.02f,
                                size.width * 0.42f, size.height * 0.55f,
                                size.width * 0.56f, size.height * 0.72f
                            )
                        }
                        drawPath(string, skin.soft, style = Stroke(6f))
                        // 纽扣（双圈 + 四孔）
                        val btn = Offset(size.width * 0.52f, size.height * 0.74f)
                        val r = size.minDimension * 0.21f
                        drawCircle(skin.soft.copy(alpha = 0.6f), radius = r, center = btn)
                        drawCircle(skin.accent, radius = r, center = btn, style = Stroke(4f))
                        val hr = r * 0.32f
                        listOf(-1, 1).forEach { sx ->
                            listOf(-1, 1).forEach { sy ->
                                drawCircle(
                                    skin.accent, radius = hr * 0.5f,
                                    center = Offset(btn.x + sx * hr, btn.y + sy * hr)
                                )
                            }
                        }
                        // 珍珠
                        drawCircle(Color(0xFFFFFFFF), radius = 5f, center = Offset(size.width * 0.95f, size.height * 0.15f))
                        drawCircle(Color(0xFFFFFFFF), radius = 4f, center = Offset(size.width * 0.80f, size.height * 0.08f))
                    }
                } else {
                    // 弹力绳：绕盒一圈的竖带
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .fillMaxSize()
                            .width(30.dp)
                            .background(skin.soft.copy(alpha = 0.42f))
                            .border(1.dp, skin.edge)
                    )
                }
                // 数量
                Text(
                    "共 $letterCount 封",
                    fontSize = 10.sp, color = skin.accent,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 18.dp, bottom = 10.dp)
                )
            }
        }

        // ── 盒盖 ──
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(horizontal = 10.dp)
                .fillMaxWidth()
                .offset(y = 58.dp)
                .zIndex(if (open) 1f else 2f)
                .graphicsLayer {
                    rotationX = lidAngle
                    transformOrigin = TransformOrigin(0.5f, 1f)
                    cameraDistance = 380f * density
                }
                .height(150.dp)
                .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 10.dp, bottomEnd = 10.dp))
                .background(Brush.verticalGradient(listOf(skin.top.copy(alpha = 0.92f), skin.mid)))
                .border(1.5.dp, skin.edge, RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 10.dp, bottomEnd = 10.dp))
                .clickable(onClick = onToggle)
        ) {
            // 提手（椭圆凹槽）
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 12.dp)
                    .width(96.dp)
                    .height(18.dp)
                    .clip(RoundedCornerShape(50))
                    .background(skin.soft.copy(alpha = 0.55f))
                    .border(1.5.dp, skin.edge, RoundedCornerShape(50))
            )
            // 顶部小标签：信 / 记
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(x = 18.dp, y = (-7).dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(skin.soft)
                    .padding(horizontal = 9.dp, vertical = 3.dp)
            ) { Text("信", fontSize = 11.sp, color = Color.White, fontFamily = FontFamily.Serif) }
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = (-18).dp, y = (-7).dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(skin.soft)
                    .padding(horizontal = 9.dp, vertical = 3.dp)
            ) { Text("记", fontSize = 11.sp, color = Color.White, fontFamily = FontFamily.Serif) }
            Text(
                "Sean 的信都在这里",
                fontSize = 10.sp, color = skin.accent.copy(alpha = 0.8f),
                fontFamily = FontFamily.Serif,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 10.dp)
            )
        }

        // ── 心形挂饰（点它读最新一封） ──
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .offset(x = 2.dp, y = 34.dp)
                .zIndex(4f)
                .graphicsLayer { rotationZ = swayZ * 0.8f * damp }
                .clickable { newestLetter?.let(onLetterTap) }
        ) {
            Text(
                "💗",
                fontSize = 30.sp,
                modifier = Modifier.shadow(6.dp, RoundedCornerShape(50))
            )
        }
    }
}

// ── 分层条（文件架隔层的前沿） ──

@Composable
private fun TierBand(label: String, count: Int, skin: BoxSkin) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp)
            .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
            .background(Brush.verticalGradient(listOf(skin.top, skin.mid)))
            .border(1.dp, skin.edge, RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 10.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(skin.soft)
                .padding(horizontal = 9.dp, vertical = 2.dp)
        ) {
            Text(label, fontSize = 11.sp, color = Color.White, fontFamily = FontFamily.Serif)
        }
        Text(
            "$count 封",
            fontSize = 10.sp, color = skin.accent,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 12.dp)
        )
    }
}

// ── 盒里的迷你信封 ──

@Composable
private fun MiniEnvelope(
    letter: Letter,
    accent: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .size(width = 128.dp, height = 84.dp)
            .shadow(5.dp, RoundedCornerShape(6.dp))
            .clip(RoundedCornerShape(6.dp))
            .background(PaperCream)
            .border(1.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val flap = Path().apply {
                moveTo(0f, 0f)
                lineTo(size.width / 2f, size.height * 0.46f)
                lineTo(size.width, 0f)
                close()
            }
            drawPath(flap, PaperFlap)
            drawPath(flap, accent.copy(alpha = 0.2f), style = Stroke(2f))
        }
        Text(
            letter.date.drop(5).replace("-", " / "),
            fontSize = 9.sp, color = accent,
            fontFamily = FontFamily.Serif,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 6.dp, end = 8.dp)
        )
        Text(
            "♥",
            fontSize = 10.sp, color = accent,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 8.dp, bottom = 6.dp)
        )
    }
}

// ── 信封卡（列表态） ──

@Composable
private fun EnvelopeCard(
    letter: Letter,
    tilt: Float,
    accent: Color,
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
            .border(1.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val flap = Path().apply {
                moveTo(0f, 0f)
                lineTo(size.width / 2f, size.height * 0.42f)
                lineTo(size.width, 0f)
                close()
            }
            drawPath(flap, PaperFlap)
            drawPath(flap, accent.copy(alpha = 0.2f), style = Stroke(2.5f))
            val fold = Path().apply {
                moveTo(0f, size.height)
                lineTo(size.width / 2f, size.height * 0.62f)
                lineTo(size.width, size.height)
            }
            drawPath(fold, accent.copy(alpha = 0.13f), style = Stroke(2f))
        }
        Column(modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 10.dp)) {
            Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "致 小鑫",
                        fontSize = 10.sp, color = accent,
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
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color(0xFFF6E3EA))
                        .border(1.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(3.dp))
                        .padding(horizontal = 7.dp, vertical = 4.dp)
                ) {
                    Text(
                        letter.date.drop(5).replace("-", " / "),
                        fontSize = 9.sp, color = accent,
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
                fontSize = 12.sp, color = Color(0xFF6E5A4A),
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
                    fontSize = 11.sp, color = accent
                )
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}

// ── 「样」换肤面板 ──

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun SkinSheet(
    light: MutableState<Int>,
    rope: MutableState<Int>,
    prefs: android.content.SharedPreferences,
    onClose: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onClose, containerColor = PaperCream) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 22.dp, end = 22.dp, bottom = 34.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "挑一版",
                    fontSize = 17.sp, color = InkBrown,
                    fontFamily = FontFamily.Serif, fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.width(10.dp))
                Text("点了马上换，选好的记住", fontSize = 11.sp, color = Color(0xFF9A8A80))
            }
            Spacer(Modifier.height(14.dp))
            Text("光 · 盒子玻璃的光", fontSize = 12.sp, color = Color(0xFF9A8A80))
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BOX_LIGHTS.forEachIndexed { i, s ->
                    PickPill(
                        text = s.name,
                        selected = light.value == i,
                        accent = s.accent,
                        onClick = { light.value = i; prefs.edit().putInt("light", i).apply() }
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            Text("绳 · 合盒的那一根", fontSize = 12.sp, color = Color(0xFF9A8A80))
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("珍珠絃", "弹力绳").forEachIndexed { i, n ->
                    PickPill(
                        text = n,
                        selected = rope.value == i,
                        accent = BOX_LIGHTS[light.value.coerceIn(0, BOX_LIGHTS.lastIndex)].accent,
                        onClick = { rope.value = i; prefs.edit().putInt("rope", i).apply() }
                    )
                }
            }
            Spacer(Modifier.height(18.dp))
            Text("右上角「样」随时回来换", fontSize = 10.sp, color = Color(0xFFB9AA9E))
        }
    }
}

@Composable
private fun PickPill(text: String, selected: Boolean, accent: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) accent else Color.White.copy(alpha = 0.75f))
            .border(1.dp, accent.copy(alpha = 0.45f), RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 15.dp, vertical = 7.dp)
    ) {
        Text(
            text,
            fontSize = 12.sp,
            color = if (selected) Color.White else accent
        )
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
                fontSize = 12.sp, color = Color(0xFFA85A72),
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
                fontSize = 14.sp, color = InkBrown,
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
