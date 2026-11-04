package com.aion.chat.compose.ui.diary

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.aion.chat.compose.data.HomecomingDayStore
import com.aion.chat.compose.data.SeanDiaryStore
import com.aion.chat.compose.data.SupabaseMomentsStore
import com.aion.chat.compose.data.parseSupabaseTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/**
 * 日记 · 信件页（定稿 = diary-letters-preview.html，逐项 1:1 移植）。
 *
 * 页面：暖纸渐变底（168° #efe7dd→#e9dfd3→#e2d5c6）+ 顶部白光晕 + 花体水印；
 * 顶栏：‹ 返回（无底）+ 右上 N LETTERS + 大花体 Letters + 居中「信件 · 慢一点的话」；
 * 信带：卡片高 28% 屏高、缝位 27%、左右 9%，未读横躺 47° / 已读 72°(+2°/层)，
 *       纸堆深层内容淡化为纸边、逐层收窄；折痕影 + 已读纸边条；
 * 卡纸：175° 渐变 #fbf7ef→#f6efe3→#efe5d5 + 左上白高光 + 5px 纸纹 + 撕纸顶边，
 *       大日期(衬线红 #7d3b33)/花体 from 沈聿淮/NEW 章(12°)/邮戳(SEAN·K)/虚线/标题/两行摘录/再读一遍章(-2°)；
 * 点开：暗纱 rgba(58,44,38,.35) 上浮出信纸（34/22/40 内距，unfold 自 -58° 展平）。
 * 数据/回应功能沿用原有（sean_diary + 旧云端 + 本地 + 缓存；留一句 回应章）。
 */
private const val PILE_TILT = 72f
private const val PILE_TILT_STEP = 2f
private const val NEXT_TILT = 47f
private const val GAP_DP = 5f                       // HTML gap = 5

// ── HTML 定稿色板 ──
private val PageTop = Color(0xFFEFE7DD)             // 168° 渐变三段
private val PageMid = Color(0xFFE9DFD3)
private val PageBottom = Color(0xFFE2D5C6)
private val WatermarkInk = Color(0xFF7A5444)        // 花体水印
private val HeadScript = Color(0xFF6F5140)          // Letters 大标题
private val HeadBack = Color(0xFF8A7466)            // ‹
private val HeadCount = Color(0xFF9B8574)           // N LETTERS
private val HeadSub = Color(0xFFA08A79)             // 信件 · 慢一点的话
private val PaperTop = Color(0xFFFBF7EF)            // 卡纸 175° 渐变
private val Paper55 = Color(0xFFF6EFE3)
private val PaperBot = Color(0xFFEFE5D5)
private val TextureInk = Color(0xFFA08269)          // 纸纹线（alpha .045）
private val InsetShadow = Color(0x2950332B)         // 卡纸内顶影 rgba(80,51,43,.16)
private val CardEdge = Color(0xFF50332B)            // 卡投影/背光基色 rgba(80,51,43,.*)
private val DateRed = Color(0xFF7D3B33)             // 大日期 / NEW / 再读一遍
private val MonBrown = Color(0xFF6B4A3C)            // 月份
private val FromInk = Color(0xFF4A332A)             // from 沈聿淮
private val TimeInk = Color(0xFF98816F)             // 时间行
private val TitleInk = Color(0xFF3F2C24)            // 标题
private val ExcerptInk = Color(0xFF5D4437)          // 摘录
private val StampInk = Color(0xFF7A5444)            // 邮戳
private val DashInk = Color(0xFF7A5444)             // 虚线（alpha .45）
private val BarColor = Color(0xFFEFE5D6)            // 已读纸边条
private val SheetScrim = Color(0xFF3A2C26)          // 展开页暗纱 rgba(58,44,38,.35)
private val SheetTop = Color(0xFFFDFAF3)            // 信纸渐变
private val Sheet60 = Color(0xFFF6EFE3)
private val SheetBot = Color(0xFFEFE5D5)
private val BodyInk = Color(0xFF4C382E)             // 正文
private val SinkFade = Color(0xFFE2D5C6)            // 下沉渐隐
private val ReplyPaper = Color(0xFFFDF0D8)
private val ReplyInk = Color(0xFF8A6A48)

data class FoldLetter(
    val key: String, val cloudId: Long, val dateMs: Long,
    val title: String, val meta: String, val content: String,
    val reply: String, val repliedAtMs: Long
)

@Composable
fun DiaryBoxScreen(onBack: () -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val letters = remember { mutableStateOf<List<FoldLetter>>(emptyList()) }
    val loading = remember { mutableStateOf(true) }
    val openLetter = remember { mutableStateOf<FoldLetter?>(null) }
    val editingId = remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(Unit) {
        loading.value = true
        // 缓存读写都是磁盘 IO：全挪后台（原来在主线程直读直写）
        val cached = withContext(Dispatchers.IO) { readDiaryCache(context) }
        if (!cached.isNullOrEmpty()) { letters.value = cached; loading.value = false }
        val fresh = withContext(Dispatchers.IO) { loadFoldLetters(context) }
        if (fresh.isNotEmpty()) {
            letters.value = fresh
            withContext(Dispatchers.IO) { writeDiaryCache(context, fresh) }
        }
        if (cached.isNullOrEmpty()) loading.value = false
    }

    BackHandler(enabled = openLetter.value != null || editingId.value != null) {
        if (editingId.value != null) editingId.value = null else openLetter.value = null
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val totalH = maxHeight

        // ── 页面底：暖纸渐变 + 顶部白光晕（radial 120%×60% at 50% 0%） ──
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(0f to PageTop, 0.42f to PageMid, 1f to PageBottom)
                )
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        0f to Color.White.copy(alpha = 0.75f),
                        1f to Color.White.copy(alpha = 0f),
                        center = Offset(maxWidth.value / 2f, 0f),
                        radius = maxWidth.value * 1.2f
                    )
                )
        )

        // ── 花体水印：top 14%，rotate -4° ──
        Text(
            "To an ordinary soul",
            fontSize = 40.sp,
            color = WatermarkInk.copy(alpha = 0.14f),
            fontFamily = FontFamily.Cursive,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = totalH * 0.13f)
                .graphicsLayer { rotationZ = -4f }
        )

        // ── 信带舞台（满屏，顶栏文字浮在其上，触控穿透非点击区） ──
        Box(modifier = Modifier.fillMaxSize()) {
            when {
                loading.value -> Text("展开信纸…", fontSize = 12.sp, color = HeadSub,
                    modifier = Modifier.align(Alignment.Center))
                letters.value.isEmpty() -> Text(
                    "还没有信——和他说上一段话，晚安之后就会有第一封",
                    fontSize = 13.sp, color = HeadSub, lineHeight = 22.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center).padding(horizontal = 40.dp))
                else -> LettersStrip(
                    letters = letters.value,
                    onOpen = { openLetter.value = it },
                    onStampClick = { l -> if (l.cloudId > 0) editingId.value = l.cloudId }
                )
            }
        }

        // ── 顶栏（浮层；除返回键外不拦截触摸） ──
        Column(Modifier.fillMaxWidth().padding(start = 22.dp, end = 22.dp, top = 18.dp)) {
            Row(Modifier.fillMaxWidth()) {
                Text(
                    "‹", fontSize = 24.sp, color = HeadBack,
                    modifier = Modifier
                        .offset(y = (-4).dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onBack() }
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "${letters.value.size} LETTERS",
                    fontSize = 11.sp, letterSpacing = 3.sp, color = HeadCount,
                    modifier = Modifier.padding(top = 26.dp)
                )
            }
            Text(
                "Letters",
                fontSize = 46.sp, color = HeadScript, fontFamily = FontFamily.Cursive,
                modifier = Modifier.offset(y = (-8).dp)
            )
            Text(
                "信件 · 慢一点的话",
                fontSize = 12.sp, letterSpacing = 6.sp, color = HeadSub,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().offset(y = (-14).dp)
            )
        }

        // ── 底部提示 ──
        Text(
            "上滑翻看旧信 · 点一下展开",
            fontSize = 11.sp, letterSpacing = 3.sp, color = HeadSub,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 14.dp)
        )

        // ── 展开读信 ──
        openLetter.value?.let { l ->
            LetterOpenOverlay(
                letter = l,
                onClose = { openLetter.value = null },
                onStampClick = { if (l.cloudId > 0) editingId.value = l.cloudId }
            )
        }

        // ── 回应弹窗 ──
        editingId.value?.let { id ->
            val target = letters.value.firstOrNull { it.cloudId == id }
            ReplyEditor(
                initial = target?.reply ?: "",
                onDismiss = { editingId.value = null },
                onConfirm = fun(text: String) {
                    editingId.value = null
                    val prev = letters.value.firstOrNull { it.cloudId == id } ?: return
                    letters.value = letters.value.map {
                        if (it.cloudId == id) it.copy(reply = text.trim(), repliedAtMs = System.currentTimeMillis()) else it
                    }
                    scope.launch {
                        val ok = SeanDiaryStore.saveReply(id, text.trim())
                        if (!ok) withContext(Dispatchers.Main) {
                            letters.value = letters.value.map {
                                if (it.cloudId == id) it.copy(reply = prev.reply, repliedAtMs = prev.repliedAtMs) else it
                            }
                            android.widget.Toast.makeText(context, "没贴上，再试一次", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            )
        }
    }
}

// ════════ Z 字折叠信带（几何 = HTML render()） ════════

@Composable
private fun LettersStrip(
    letters: List<FoldLetter>,
    onOpen: (FoldLetter) -> Unit,
    onStampClick: (FoldLetter) -> Unit,
) {
    val scope = rememberCoroutineScope()
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val totalH = maxHeight
        val cardH = totalH * 0.28f
        val seamY = totalH * 0.27f
        val stripGap = GAP_DP.dp
        val cardHpx = with(androidx.compose.ui.platform.LocalDensity.current) { cardH.toPx() }

        val pos = remember { Animatable(0f) }
        val n = letters.size
        val clampPos = { v: Float -> v.coerceIn(-0.08f, n - 1 + 0.08f) }
        // 吸附动画与拖拽共用一把 Animatable：动画没停就 snapTo 会排队卡 0.5s——拖动先取消动画
        var animJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = maxWidth * 0.09f)
                .pointerInput(n) {
                    detectVerticalDragGestures(
                        onDragEnd = {
                            animJob = scope.launch {
                                pos.animateTo(
                                    pos.value.roundToInt().coerceIn(0, n - 1).toFloat(),
                                    spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioNoBouncy)
                                )
                            }
                        },
                        onDragCancel = {
                            animJob = scope.launch {
                                pos.animateTo(
                                    pos.value.roundToInt().coerceIn(0, n - 1).toFloat(),
                                    spring(stiffness = Spring.StiffnessMediumLow)
                                )
                            }
                        },
                        onVerticalDrag = { change, dragAmount ->
                            change.consume()
                            animJob?.cancel()
                            scope.launch { pos.snapTo(clampPos(pos.value - dragAmount / cardHpx)) }
                        }
                    )
                }
        ) {
            val p = pos.value
            val flipFrac = p - kotlin.math.floor(p)

            // ── 信卡：rel 大的（纸条下方）先画，rel 小的（上方/纸堆）后画压上来 ──
            val order = letters.indices
                .map { it to (it - p) }
                .filter { (_, rel) -> rel in -4.5f..5.5f }
                .sortedByDescending { (_, rel) -> rel }
            order.forEach { (i, rel) ->
                key(letters[i].key) {
                    StripCard(
                        letter = letters[i],
                        rel = rel,
                        seed = i + 1,
                        isNewest = i == 0,
                        cardH = cardH,
                        seamY = seamY,
                        stripGap = stripGap,
                        onOpen = { onOpen(letters[i]) },
                        onStampClick = { onStampClick(letters[i]) }
                    )
                }
            }

            // ── 折痕影（HTML #hinge，6px，跟正在翻走那封的底边走） ──
            val hingeY = seamY + cardH * (1f - flipFrac)
            Box(
                Modifier
                    .align(Alignment.TopStart)
                    .offset { IntOffset(0, hingeY.roundToPx()) }
                    .fillMaxWidth()
                    .height(6.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color(0x47000000), Color(0x52000000), Color.Transparent)
                        )
                    )
            )

            // ── 已读纸堆纸边条（HTML .bar：高 4px，色 #efe5d6，宽 82%×(1-k*0.012)） ──
            val readDepth = kotlin.math.min(kotlin.math.max(p.roundToInt(), 0), 4)
            repeat(readDepth) { k ->
                Box(
                    Modifier
                        .align(Alignment.TopStart)
                        .offset { IntOffset(0, (seamY - 8.dp - stripGap * (k + 1)).roundToPx()) }
                        .fillMaxWidth(1f - k * 0.012f)
                        .height(4.dp)
                        .graphicsLayer { alpha = 1f - k * 0.16f }
                        .background(BarColor)
                )
            }
        }
    }
}

// ════════ 带上的一封信（视觉 = HTML .card/.paper） ════════

@Composable
private fun BoxScope.StripCard(
    letter: FoldLetter,
    rel: Float,
    seed: Int,
    isNewest: Boolean,
    cardH: androidx.compose.ui.unit.Dp,
    seamY: androidx.compose.ui.unit.Dp,
    stripGap: androidx.compose.ui.unit.Dp,
    onOpen: () -> Unit,
    onStampClick: () -> Unit,
) {
    val pivotBottom = rel <= 0f
    val angle: Float
    val anchorY: androidx.compose.ui.unit.Dp
    val shade: Float
    when {
        rel <= -1f -> {
            val depth = (-rel - 1f).coerceAtMost(3f)
            angle = (PILE_TILT + depth * PILE_TILT_STEP).coerceAtMost(80f)
            anchorY = seamY - stripGap * depth - 2.dp
            shade = 0.30f + depth * 0.07f   // 压低：仰倒卡的背光不再发黑
        }
        rel <= 0f -> {
            angle = -rel * PILE_TILT
            anchorY = seamY + cardH * (1f + rel)
            shade = -rel * 0.45f
        }
        rel <= 1f -> {
            angle = rel * NEXT_TILT
            anchorY = seamY + cardH * rel
            shade = rel * 0.15f
        }
        else -> {
            val depth = (rel - 1f).coerceAtMost(4f)
            angle = NEXT_TILT + depth * 1.5f
            anchorY = seamY + cardH + cardH * 0.07f * depth
            shade = 0.15f + depth * 0.08f
        }
    }

    val stackNarrow = if (rel > 1f) (rel - 1f).coerceAtMost(4f) * 0.016f else 0f
    Box(
        modifier = Modifier
            .align(Alignment.TopCenter)
            .fillMaxWidth(1f - stackNarrow)
            .height(cardH)
            // 不用 elevation 阴影：Compose 阴影在 72° 旋转图层上会糊成黑块（Yuri 实测），
            // 层叠感交给 撕边描边 + 折痕影 + 已读纸边条
            .graphicsLayer {
                transformOrigin = if (pivotBottom) TransformOrigin(0.5f, 1f) else TransformOrigin(0.5f, 0f)
                translationY = if (pivotBottom) (anchorY - cardH).toPx() else anchorY.toPx()
                rotationX = -angle
                cameraDistance = size.height * 8f
            }
    ) {
        // ── 纸面：撕边裁形 + 渐变 + 高光 + 纸纹 + 内顶影（HTML .paper 全套） ──
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    val w = size.width
                    val h = size.height
                    val paper = tornPath(seed, w, h)
                    // 175° 纸底渐变
                    drawPath(paper, Brush.verticalGradient(0f to PaperTop, 0.55f to Paper55, 1f to PaperBot,
                        startY = 0f, endY = h))
                    // 左上白高光 radial(90% 50% at 30% 20%) 白 .6 → 0
                    clipPath(paper) {
                        drawRect(
                            Brush.radialGradient(
                                0f to Color.White.copy(alpha = 0.6f), 1f to Color.Transparent,
                                center = Offset(w * 0.30f, h * 0.20f), radius = w * 0.90f
                            )
                        )
                        // 纸纹：每 5px 一条 1px 线，alpha .045
                        var y = 5f
                        val line = TextureInk.copy(alpha = 0.045f)
                        while (y < h) {
                            drawRect(line, topLeft = Offset(0f, y), size = androidx.compose.ui.geometry.Size(w, 1f))
                            y += 5f
                        }
                        // 内顶影 inset 0 3px 3px -1px rgba(80,51,43,.16)
                        drawRect(
                            Brush.verticalGradient(0f to InsetShadow, 1f to Color.Transparent,
                                startY = 0f, endY = 7f),
                            topLeft = Offset(0f, 0f), size = androidx.compose.ui.geometry.Size(w, 7f)
                        )
                    }
                }
                .pointerInput(letter.key) {
                    detectTapGestures { onOpen() }
                }
                .padding(horizontal = cardH * 0.11f)
                .padding(top = cardH * 0.08f, bottom = cardH * 0.05f)
        ) {
            // 纸堆深层的内容淡化成纸边（HTML .deep opacity .16）
            val contentAlpha = (1f - (rel - 1.4f) * 2f).coerceIn(0.16f, 1f)
            Column(Modifier.fillMaxSize().graphicsLayer { alpha = contentAlpha }) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                    // 大日期 + 月份
                    Column {
                        Text(letter.dateMs.fmt("d"),
                            fontSize = (cardH.value * 0.145f).sp, fontWeight = FontWeight.Bold,
                            color = DateRed, fontFamily = FontFamily.Serif)
                        Text(letter.dateMs.fmt("MMM", Locale("en")).uppercase(),
                            fontSize = (cardH.value * 0.050f).sp, fontWeight = FontWeight.Bold,
                            letterSpacing = (cardH.value * 0.016f).sp,
                            color = MonBrown, fontFamily = FontFamily.Serif)
                    }
                    Spacer(Modifier.width(cardH * 0.07f))
                    // 窄屏防溢出：from 列给剩余空间并截断（原来固定宽会把邮戳挤出纸边）
                    Column(Modifier.weight(1f, fill = false).padding(top = cardH * 0.03f)) {
                        Text("from 沈聿淮",
                            fontSize = (cardH.value * 0.110f).sp, color = FromInk,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                            fontFamily = FontFamily.Cursive, fontStyle = FontStyle.Italic)
                        Text(letter.dateMs.fmt("MM.dd · HH:mm"),
                            fontSize = (cardH.value * 0.048f).sp, color = TimeInk, letterSpacing = 1.sp,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Spacer(Modifier.weight(1f))
                    // 邮戳区：最新一封多枚 NEW 章（rotate 12°）
                    Column(horizontalAlignment = Alignment.End) {
                        if (isNewest) {
                            Text("NEW",
                                fontSize = (cardH.value * 0.040f).sp, fontWeight = FontWeight.Bold,
                                letterSpacing = (cardH.value * 0.009f).sp,
                                color = DateRed.copy(alpha = 0.7f),
                                modifier = Modifier
                                    .graphicsLayer { rotationZ = 12f }
                                    .border(1.dp, DateRed.copy(alpha = 0.55f), RoundedCornerShape(2.dp))
                                    .padding(horizontal = cardH * 0.024f, vertical = cardH * 0.008f))
                            Spacer(Modifier.height(cardH * 0.016f))
                        }
                        Postmark(scale = cardH / 210.dp)
                    }
                }
                Spacer(Modifier.height(cardH * 0.05f))
                DashedLine()
                Spacer(Modifier.height(cardH * 0.04f))
                if (letter.title.isNotBlank()) {
                    Text(letter.title, fontSize = (cardH.value * 0.075f).sp, fontWeight = FontWeight.SemiBold,
                        color = TitleInk, modifier = Modifier.padding(bottom = cardH * 0.024f))
                }
                Text(letter.content.replace("\n", " ").trim(),
                    fontSize = (cardH.value * 0.062f).sp, lineHeight = (cardH.value * 0.102f).sp, color = ExcerptInk,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.weight(1f))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    StampOrReply(letter.reply, letter.repliedAtMs, letter.cloudId > 0, onStampClick)
                    Spacer(Modifier.weight(1f))
                    // 「再 读 一 遍 →」章：rotate -2°，平铺时清晰
                    Text("再 读 一 遍 →",
                        fontSize = (cardH.value * 0.050f).sp,
                        letterSpacing = (cardH.value * 0.009f).sp,
                        color = DateRed.copy(alpha = if (rel in -0.4f..0.4f) 0.85f else 0.3f),
                        modifier = Modifier
                            .graphicsLayer { rotationZ = -2f }
                            .border(1.4.dp, DateRed.copy(alpha = if (rel in -0.4f..0.4f) 0.6f else 0.2f), RoundedCornerShape(2.dp))
                            .padding(horizontal = cardH * 0.044f, vertical = cardH * 0.02f))
                }
            }
        }

        // 背光（HTML .shade：180° rgba(80,51,43,.55)→.15@50%→透明，整体 opacity=shade）
        if (shade > 0.01f) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = shade }
                    .background(
                        Brush.verticalGradient(
                            0f to CardEdge.copy(alpha = 0.55f),
                            0.5f to CardEdge.copy(alpha = 0.15f),
                            1f to Color.Transparent
                        )
                    )
            )
        }
        // 下半截渐隐进纸堆（HTML .sinkfade：transparent 40% → rgba(226,213,198,.9) 88%）
        if (rel > 0.5f) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = ((rel - 0.5f) * 0.5f).coerceIn(0f, 0.22f) }
                    .background(
                        Brush.verticalGradient(
                            0.40f to Color.Transparent,
                            0.88f to SinkFade.copy(alpha = 0.9f)
                        )
                    )
            )
        }
    }
}

/** HTML tornClip(seed)：顶部撕边裁形（底边平直）。百分比坐标 → 像素。 */
private fun tornPath(seed: Int, w: Float, h: Float): Path {
    var rnd = seed * 7919 + 13
    fun rand(): Float { rnd = (rnd * 9301 + 49297) % 233280; return rnd / 233280f }
    val pts = mutableListOf(0f to h, 0f to h * 0.04f)   // '0% 100%','0% 4%'
    var x = 0f
    while (x < w) {
        pts.add(x to h * ((1.6f + rand() * 2.6f) / 100f))
        x += w * (3f + rand() * 5f) / 100f
    }
    pts.add(w to h * 0.04f)
    pts.add(w to h)
    return Path().apply {
        moveTo(pts[0].first, pts[0].second)
        pts.drop(1).forEach { lineTo(it.first, it.second) }
        close()
    }
}

// ════════ 圆形邮戳 + 波浪线（HTML postmarkSVG：SEAN·K 双圈，无旋转） ════════

@Composable
private fun Postmark(scale: Float = 1f) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.width(34.dp * scale).height(20.dp * scale)) {
            val w = size.width; val h = size.height
            for (row in 0..2) {
                val y0 = h * (0.2f + row * 0.3f)
                val path = Path()
                path.moveTo(0f, y0)
                var x = 0f
                while (x < w) {
                    path.quadraticTo(x + w / 12f, y0 - 3.dp.toPx(), x + w / 6f, y0)
                    x += w / 6f
                }
                drawPath(path, StampInk.copy(alpha = 0.55f), style = Stroke(1.1f))
            }
        }
        Spacer(Modifier.width(2.dp))
        Box(
            Modifier
                .size(46.dp * scale)
                .graphicsLayer { alpha = 0.8f }
                .drawBehind {
                    val c = StampInk.copy(alpha = 0.75f)
                    drawCircle(c, size.minDimension / 2f, style = Stroke(1.1f))
                    drawCircle(c, size.minDimension / 2f - 4.dp.toPx(), style = Stroke(1f))
                },
            contentAlignment = Alignment.Center
        ) {
            Text("SEAN·K", fontSize = (8f * scale).sp,
                color = StampInk.copy(alpha = 0.85f), letterSpacing = 1.5.sp,
                fontFamily = FontFamily.Serif)
        }
    }
}

// ════════ 展开读信（HTML #overlay/#sheet：暗纱 + 浮层信纸，unfold 自 -58°） ════════

@Composable
private fun LetterOpenOverlay(
    letter: FoldLetter,
    onClose: () -> Unit,
    onStampClick: () -> Unit,
) {
    val unfold = remember { Animatable(0f) }
    LaunchedEffect(letter.key) {
        unfold.snapTo(0f)
        unfold.animateTo(1f, tween(450, easing = FastOutSlowInEasing))
    }
    val t = unfold.value

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(SheetScrim.copy(alpha = 0.35f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onClose() }
        ) {
            // 信纸浮层：inset 34/22/40（HTML #sheet）
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 22.dp, end = 22.dp, top = 34.dp, bottom = 40.dp)
            ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .shadow(18.dp, RectangleShape)
                    .clip(RectangleShape)
                    .background(Brush.verticalGradient(0f to SheetTop, 0.6f to Sheet60, 1f to SheetBot))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { /* 吃掉点纱关闭 */ }
                    .graphicsLayer {
                        alpha = (t * 1.4f).coerceAtMost(1f)
                        transformOrigin = TransformOrigin(0.5f, 0f)
                        rotationX = (1f - t) * -58f
                        translationY = (1f - t) * 30.dp.toPx()
                        cameraDistance = size.height * 2f
                    }
            ) {
                Box(Modifier.fillMaxWidth()) {
                    Text(
                        "×", fontSize = 26.sp, color = HeadBack,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 6.dp, end = 16.dp)
                            .clickable { onClose() }
                    )
                }
                Column(
                    Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(start = 28.dp, end = 28.dp, bottom = 40.dp)
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column {
                            Text(letter.dateMs.fmt("d"),
                                fontSize = 44.sp, fontWeight = FontWeight.Bold,
                                color = DateRed, fontFamily = FontFamily.Serif)
                            Text(letter.dateMs.fmt("MMM", Locale("en")).uppercase(),
                                fontSize = 12.sp, fontWeight = FontWeight.Bold,
                                color = MonBrown, fontFamily = FontFamily.Serif, letterSpacing = 3.sp)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("from 沈聿淮",
                                fontSize = 23.sp, color = FromInk,
                                fontFamily = FontFamily.Cursive, fontStyle = FontStyle.Italic)
                            Text(letter.dateMs.fmt("MM.dd · HH:mm"),
                                fontSize = 11.sp, color = TimeInk)
                            Spacer(Modifier.height(6.dp))
                            Postmark()
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    DashedLine(dashColor = DashInk.copy(alpha = 0.45f), strokePx = 1.5f)
                    Spacer(Modifier.height(10.dp))
                    if (letter.title.isNotBlank()) {
                        Text(letter.title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                            color = TitleInk, modifier = Modifier.padding(bottom = 12.dp))
                    }
                    // 正文：段落 = \n\n 切分（HTML <p>）
                    letter.content.split("\n\n").forEachIndexed { idx, para ->
                        if (para.isBlank()) return@forEachIndexed
                        if (idx > 0) Spacer(Modifier.height(14.dp))
                        Text(para.trim(), fontSize = 14.5.sp, lineHeight = 27.sp, color = BodyInk)
                    }
                    Spacer(Modifier.height(20.dp))
                    // 回应功能保留：留一句 / 他贴的回应
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        StampOrReply(letter.reply, letter.repliedAtMs, letter.cloudId > 0, onStampClick)
                        Spacer(Modifier.weight(1f))
                        Text("—— 沈聿淮", fontSize = 13.sp, color = HeadSub,
                            fontFamily = FontFamily.Serif, fontStyle = FontStyle.Italic)
                    }
                }
            }
            }
        }
    }
}

// ════════ 邮戳 / 回应 ════════

@Composable
private fun StampOrReply(
    reply: String, repliedAtMs: Long, enabled: Boolean, onClick: () -> Unit,
) {
    if (!enabled) return
    if (reply.isBlank()) {
        Box(
            Modifier
                .size(46.dp)
                .graphicsLayer { rotationZ = -8f }
                .clickable { onClick() },
            contentAlignment = Alignment.Center
        ) {
            Box(
                Modifier.fillMaxSize().drawBehind {
                    drawCircle(StampInk.copy(alpha = 0.5f), size.minDimension / 2f, style = Stroke(1.5f))
                    drawCircle(StampInk.copy(alpha = 0.3f), size.minDimension / 2f - 4.dp.toPx(), style = Stroke(1f))
                },
                contentAlignment = Alignment.Center
            ) { Text("留一句", fontSize = 9.sp, color = StampInk) }
        }
    } else {
        Column(
            modifier = Modifier
                .widthIn(max = 130.dp)
                .graphicsLayer { rotationZ = 4f }
                .clickable { onClick() }
                .background(ReplyPaper)
                .border(1.dp, Color(0xFFE8D5B8), RoundedCornerShape(2.dp))
                .padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            Text(reply, fontSize = 11.sp, lineHeight = 16.sp, color = ReplyInk,
                maxLines = 3, overflow = TextOverflow.Ellipsis)
            if (repliedAtMs > 0) {
                Spacer(Modifier.height(3.dp))
                Text(repliedAtMs.fmt("yyyy.M.d HH:mm"), fontSize = 8.sp, color = ReplyInk.copy(alpha = 0.5f))
            }
        }
    }
}

// ════════ 回应编辑 ════════

@Composable
private fun ReplyEditor(
    initial: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier.fillMaxWidth(0.84f).clip(RoundedCornerShape(14.dp))
                .background(ReplyPaper)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { }
                .padding(18.dp)
        ) {
            Text("留一句", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = ReplyInk)
            Text("一篇只留一句。想改就再点一次。", fontSize = 10.sp, color = ReplyInk.copy(alpha = 0.6f))
            Spacer(Modifier.height(14.dp))
            BasicTextField(
                value = text, onValueChange = { if (it.length <= 120) text = it },
                textStyle = TextStyle(fontSize = 13.sp, lineHeight = 22.sp, color = ReplyInk),
                modifier = Modifier.fillMaxWidth().heightIn(min = 70.dp)
                    .background(Color.White.copy(alpha = 0.5f), RoundedCornerShape(8.dp)).padding(10.dp)
            )
            Spacer(Modifier.height(6.dp))
            Text("${text.length} / 120", fontSize = 9.sp, color = ReplyInk.copy(alpha = 0.45f),
                modifier = Modifier.align(Alignment.End))
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("算了", fontSize = 12.sp, color = ReplyInk.copy(alpha = 0.6f)) }
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = { if (text.isNotBlank()) onConfirm(text.trim()) }, enabled = text.isNotBlank()) {
                    Text("贴上去", fontSize = 12.sp, color = ReplyInk)
                }
            }
        }
    }
}

// ════════ 共用小组件 ════════

@Composable
private fun DashedLine(dashColor: Color = DashInk.copy(alpha = 0.45f), strokePx: Float = 1.5f) {
    Canvas(Modifier.fillMaxWidth().height(2.dp)) {
        val dash = 6.dp.toPx(); val gap = 5.dp.toPx()
        var x = 0f
        while (x < size.width) {
            drawLine(dashColor, Offset(x, 0f), Offset(x + dash, 0f), strokePx)
            x += dash + gap
        }
    }
}

// ════════ 数据 ════════

private fun readDiaryCache(context: android.content.Context): List<FoldLetter>? = runCatching {
    val f = java.io.File(context.filesDir, "diary_cache.json")
    if (!f.exists()) return null
    val arr = org.json.JSONArray(f.readText())
    (0 until arr.length()).map { i ->
        val o = arr.getJSONObject(i)
        FoldLetter(o.getString("key"), o.getLong("cloudId"), o.getLong("dateMs"),
            o.getString("title"), o.getString("meta"), o.getString("content"),
            o.optString("reply"), o.optLong("repliedAtMs", 0L))
    }.sortedByDescending { it.dateMs }
}.getOrNull()

private fun writeDiaryCache(context: android.content.Context, list: List<FoldLetter>) {
    runCatching {
        val arr = org.json.JSONArray()
        list.forEach { l ->
            arr.put(org.json.JSONObject()
                .put("key", l.key).put("cloudId", l.cloudId).put("dateMs", l.dateMs)
                .put("title", l.title).put("meta", l.meta).put("content", l.content)
                .put("reply", l.reply).put("repliedAtMs", l.repliedAtMs))
        }
        java.io.File(context.filesDir, "diary_cache.json").writeText(arr.toString())
    }
}

private suspend fun loadFoldLetters(context: android.content.Context): List<FoldLetter> {
    val list = mutableListOf<FoldLetter>()
    val seen = mutableSetOf<String>()
    val rows = runCatching { SeanDiaryStore.fetchAll() }
        .getOrNull()?.takeIf { it.error == null }?.data.orEmpty()
    rows.forEach { r ->
        list.add(FoldLetter("sd${r.id}", r.id, r.createdAtMs, "", "", r.content, r.replyContent, r.repliedAtMs))
        seen.add(dayKey(r.createdAtMs))
    }
    val old = runCatching { SupabaseMomentsStore.fetchSeanDiaries() }
        .getOrNull()?.takeIf { it.error == null }?.data.orEmpty()
    old.forEach { d ->
        val ms = if (d.createdAtMs > 0) d.createdAtMs else parseSupabaseTime(d.createdAt)
        if (ms <= 0) return@forEach
        val k = dayKey(ms)
        if (k in seen) return@forEach
        seen.add(k)
        list.add(FoldLetter("de${d.id}", 0L, ms, d.title,
            listOf(d.weather, d.mood).filter { it.isNotBlank() }.joinToString(" · "),
            d.content, "", 0L))
    }
    runCatching { HomecomingDayStore.diaries(context).filter { it.author == "sean" } }
        .getOrDefault(emptyList()).forEach { d ->
            if (d.createdAt <= 0) return@forEach
            val k = dayKey(d.createdAt)
            if (k in seen) return@forEach
            seen.add(k)
            list.add(FoldLetter("lo${d.id}_${d.createdAt}", 0L, d.createdAt, d.title, "", d.content, "", 0L))
        }
    return list.sortedByDescending { it.dateMs }
}

private fun dayKey(ms: Long) =
    Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate().toString()

// ════════ 工具 ════════

private fun Long.fmt(pattern: String, locale: java.util.Locale? = null): String {
    if (this <= 0) return ""
    val fmt = DateTimeFormatter.ofPattern(pattern)
    return if (locale != null) fmt.withLocale(locale).withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(this))
    else fmt.withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(this))
}
