package com.aion.chat.compose.ui.diary

import android.widget.Toast
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
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
import com.aion.chat.compose.ui.theme.HomecomingColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * 日记 · 信件页（参考：小红书 Letters 折叠信）。
 *
 * Z 字折叠长纸条：
 *  - 当前信平铺在中间；读过的在上方仰倒 ~72° 叠成纸堆（底边钉在同一条缝上）；
 *  - 下一封在下方微仰，下半截压进待读纸堆；
 *  - 上滑翻信：当前信绕底边向后仰倒并入上堆，下一封被同一道缝带着立起来补位，
 *    接缝始终粘在一起（下一封顶边 = 上一封底边）。
 * 点开某封 → 全屏信纸展开（撕边、大字日期、邮戳、unfold 动画）。
 */
private const val PILE_TILT = 72f        // 读过信的仰倒角
private const val PILE_TILT_STEP = 2f    // 纸堆每深一层追加的角度
private const val NEXT_TILT = 16f        // 下一封静置时的微仰角
private const val CAMERA_DIST = 36f

private val PaperLight = Color(0xFFFAF5EE)
private val PaperMid = Color(0xFFF5EFE6)
private val PaperDark = Color(0xFFEDE5D8)
private val InkDark = Color(0xFF5C3D2E)
private val InkBrown = Color(0xFF7A5C48)
private val InkSub = Color(0xFFA08878)
private val Accent = Color(0xFF8B6348)
private val StampRed = Color(0xFF8C3C46)
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
        val cached = readDiaryCache(context)
        if (!cached.isNullOrEmpty()) { letters.value = cached; loading.value = false }
        val fresh = withContext(Dispatchers.IO) { loadFoldLetters(context) }
        if (fresh.isNotEmpty()) {
            letters.value = fresh
            writeDiaryCache(context, fresh)
        }
        if (cached.isNullOrEmpty()) loading.value = false
    }

    BackHandler(enabled = openLetter.value != null || editingId.value != null) {
        if (editingId.value != null) editingId.value = null else openLetter.value = null
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 顶栏：返回 + 花体标题 + 封数
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.5f))
                        .border(1.dp, Color.White.copy(alpha = 0.6f), CircleShape)
                        .clickable { onBack() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回",
                        tint = HomecomingColors.Ink, modifier = Modifier.size(19.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Letters",
                        fontSize = 26.sp, color = InkDark,
                        fontFamily = FontFamily.Cursive, fontStyle = FontStyle.Italic)
                    Text("信件 · 慢一点的话",
                        fontSize = 10.sp, color = InkSub, letterSpacing = 3.sp)
                }
                Spacer(Modifier.weight(1f))
                Text("${letters.value.size} LETTERS",
                    fontSize = 10.sp, color = InkSub, letterSpacing = 2.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.White.copy(alpha = 0.4f))
                        .padding(horizontal = 9.dp, vertical = 4.dp))
            }

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when {
                    loading.value -> Text("展开信纸…", fontSize = 12.sp, color = InkSub,
                        modifier = Modifier.align(Alignment.Center))
                    letters.value.isEmpty() -> Text(
                        "还没有信——和他说上一段话，晚安之后就会有第一封",
                        fontSize = 13.sp, color = InkSub, lineHeight = 22.sp,
                        modifier = Modifier.align(Alignment.Center).padding(horizontal = 40.dp))
                    else -> LettersStrip(letters.value,
                        onOpen = { openLetter.value = it },
                        onStampClick = { l -> if (l.cloudId > 0) editingId.value = l.cloudId })
                }
            }
        }

        // 展开读信
        openLetter.value?.let { l ->
            LetterOpenOverlay(
                letter = l,
                onClose = { openLetter.value = null },
                onStampClick = { if (l.cloudId > 0) editingId.value = l.cloudId }
            )
        }

        // 回应弹窗
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
                            Toast.makeText(context, "没贴上，再试一次", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            )
        }
    }
}

// ════════ Z 字折叠信带 ════════

@Composable
private fun LettersStrip(
    letters: List<FoldLetter>,
    onOpen: (FoldLetter) -> Unit,
    onStampClick: (FoldLetter) -> Unit,
) {
    val scope = rememberCoroutineScope()
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val totalH = maxHeight
        val cardH = totalH * 0.40f          // 一封信的高度
        val seamY = totalH * 0.26f          // 当前信顶边（= 已读堆底缝）
        val stripGap = 6.dp                 // 纸堆每层间距
        val cardHpx = with(androidx.compose.ui.platform.LocalDensity.current) { cardH.toPx() }

        // 连续位置：整数 = 当前信下标，小数 = 翻页进度
        val pos = remember { Animatable(0f) }
        val n = letters.size
        val clampPos = { v: Float -> v.coerceIn(-0.08f, n - 1 + 0.08f) }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(n) {
                    detectVerticalDragGestures(
                        onDragEnd = {
                            scope.launch {
                                pos.animateTo(
                                    pos.value.roundToInt().coerceIn(0, n - 1).toFloat(),
                                    spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioNoBouncy)
                                )
                            }
                        },
                        onDragCancel = {
                            scope.launch {
                                pos.animateTo(
                                    pos.value.roundToInt().coerceIn(0, n - 1).toFloat(),
                                    spring(stiffness = Spring.StiffnessMediumLow)
                                )
                            }
                        },
                        onVerticalDrag = { change, dragAmount ->
                            change.consume()
                            scope.launch { pos.snapTo(clampPos(pos.value - dragAmount / cardHpx)) }
                        }
                    )
                }
        ) {
            val p = pos.value
            val flipFrac = p - kotlin.math.floor(p)  // 0=静置；>0 表示有一封正在向上翻走

            // ── 信卡：rel 大的（纸条下方）先画，rel 小的（上方/纸堆）后画压上来 ──
            val order = letters.indices
                .map { it to (it - p) }
                .filter { (_, rel) -> rel in -4.5f..2.5f }
                .sortedByDescending { (_, rel) -> rel }
            order.forEach { (i, rel) ->
                key(letters[i].key) {
                    StripCard(
                        letter = letters[i],
                        rel = rel,
                        isNewest = i == 0,
                        cardH = cardH,
                        seamY = seamY,
                        stripGap = stripGap,
                        onOpen = { onOpen(letters[i]) },
                        onStampClick = { onStampClick(letters[i]) }
                    )
                }
            }

            // ── 折痕阴影：跟着正在翻走那封的底边走（从 seamY+cardH 升到 seamY） ──
            val hingeY = seamY + cardH * (1f - flipFrac)
            Box(
                Modifier
                    .align(Alignment.TopStart)
                    .offset { IntOffset(0, hingeY.roundToPx()) }
                    .fillMaxWidth()
                    .height(7.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color(0x40000000), Color(0x4D000000), Color.Transparent)
                        )
                    )
            )

            // ── 顶部已读纸堆的纸边（最上层信之后画，压在最底） ──
            val readDepth = kotlin.math.min(kotlin.math.max(p.roundToInt(), 0), 4)
            repeat(readDepth) { k ->
                Box(
                    Modifier
                        .align(Alignment.TopStart)
                        .offset { IntOffset(0, (seamY - 10.dp - stripGap * (k + 1)).roundToPx()) }
                        .fillMaxWidth(1f - k * 0.012f)
                        .height(5.dp)
                        .graphicsLayer { alpha = 1f - k * 0.16f }
                        .background(PaperMid)
                )
            }

            // ── 底部待读纸堆：从下一封中段开始紧紧叠住 ──
            val unreadCount = n - 1 - kotlin.math.max(p.roundToInt(), 0)
            val pileBaseY = seamY + cardH + cardH * 0.22f
            repeat(unreadCount.coerceIn(0, 3)) { k ->
                Box(
                    Modifier
                        .align(Alignment.TopStart)
                        .offset { IntOffset(0, (pileBaseY + stripGap * k).roundToPx()) }
                        .fillMaxWidth(1f - k * 0.012f)
                        .height(5.dp)
                        .graphicsLayer { alpha = 1f - k * 0.16f }
                        .background(PaperMid)
                )
            }
        }
    }
}

// ════════ 带上的一封信 ════════

@Composable
private fun StripCard(
    letter: FoldLetter,
    rel: Float,               // 相对位置：0=当前信，负=已读（上方），1≈下一封
    isNewest: Boolean,
    cardH: androidx.compose.ui.unit.Dp,
    seamY: androidx.compose.ui.unit.Dp,
    stripGap: androidx.compose.ui.unit.Dp,
    onOpen: () -> Unit,
    onStampClick: () -> Unit,
) {
    // 几何：三种状态
    //  rel <= -1  → 已读纸堆：底边钉在缝上，仰倒 ~72°，每层往上错一点
    //  -1 < rel<=0 → 正在翻走/当前：底边从 seamY+cardH 升到 seamY，角度 0→72°
    //  0 < rel<=1 → 下一封：顶边粘在上一封底边上，微仰 16°→0° 被拉直立
    val pivotBottom = rel <= 0f
    val angle: Float
    val anchorY: androidx.compose.ui.unit.Dp   // 枢轴边的 Y（底边或顶边）
    val shade: Float                            // 背光强度 0..1
    when {
        rel <= -1f -> {
            val depth = (-rel - 1f).coerceAtMost(3f)
            angle = (PILE_TILT + depth * PILE_TILT_STEP).coerceAtMost(80f)
            anchorY = seamY - stripGap * depth - 2.dp
            shade = 0.45f + depth * 0.10f
        }
        rel <= 0f -> {
            angle = -rel * PILE_TILT
            anchorY = seamY + cardH * (1f + rel)   // 底边沿缝上升
            shade = -rel * 0.45f
        }
        else -> {
            angle = rel * NEXT_TILT
            anchorY = seamY + cardH * rel          // 顶边 = 上一封底边
            shade = rel * 0.15f
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(cardH)
            .graphicsLayer {
                transformOrigin = if (pivotBottom) TransformOrigin(0.5f, 1f) else TransformOrigin(0.5f, 0f)
                translationY = if (pivotBottom) (anchorY - cardH).toPx() else anchorY.toPx()
                rotationX = -angle                   // Android 里正值是把底边推向远处；取负才是顶边向后仰
                cameraDistance = CAMERA_DIST * density
            }
    ) {
        // 纸面（撕边）
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    val w = size.width
                    val edge = 5.dp.toPx()
                    val paper = Path().apply {
                        moveTo(0f, edge)
                        var x = 0f
                        while (x < w) {
                            val j = sin(x / w * 25f + 2f) * 3f + sin(x / w * 8f) * 2f
                            lineTo(x, edge + j.coerceIn(0f, edge))
                            x += 14f
                        }
                        lineTo(w, size.height)
                        lineTo(0f, size.height)
                        close()
                    }
                    drawPath(paper, lerpPaper(PaperLight, PaperDark, shade * 0.5f))
                    drawPath(paper, Color(0xFFDDD5C8), style = Stroke(1.2f))
                }
                .pointerInput(letter.key) {
                    detectTapGestures { onOpen() }
                }
                .padding(horizontal = 30.dp)
                .padding(top = 16.dp, bottom = 10.dp)
        ) {
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                    // 大日期 + 月份
                    Column {
                        Text(letter.dateMs.fmt("d"),
                            fontSize = 40.sp, fontWeight = FontWeight.Bold,
                            color = StampRed, fontFamily = FontFamily.Serif)
                        Text(letter.dateMs.fmt("MMM", Locale("en")).uppercase(),
                            fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.sp,
                            color = InkBrown, fontFamily = FontFamily.Serif)
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.padding(top = 8.dp)) {
                        Text("from 沈聿淮",
                            fontSize = 22.sp, color = InkDark,
                            fontFamily = FontFamily.Cursive, fontStyle = FontStyle.Italic)
                        Text(letter.dateMs.fmt("MM.dd · HH:mm"),
                            fontSize = 11.sp, color = InkSub, letterSpacing = 1.sp)
                    }
                    Spacer(Modifier.weight(1f))
                    // 邮戳区：最新一封多个 NEW 章
                    Column(horizontalAlignment = Alignment.End) {
                        if (isNewest) {
                            Text("NEW",
                                fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp,
                                color = StampRed.copy(alpha = 0.7f),
                                modifier = Modifier
                                    .graphicsLayer { rotationZ = 12f }
                                    .border(1.2.dp, StampRed.copy(alpha = 0.55f), RoundedCornerShape(2.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp))
                            Spacer(Modifier.height(4.dp))
                        }
                        Postmark()
                    }
                }
                Spacer(Modifier.height(10.dp))
                DashedLine()
                Spacer(Modifier.height(10.dp))
                if (letter.title.isNotBlank()) {
                    Text(letter.title, fontSize = 16.sp, fontWeight = FontWeight.Medium,
                        color = InkDark, modifier = Modifier.padding(bottom = 6.dp))
                }
                Text(letter.content.replace("\n", " ").trim(),
                    fontSize = 13.sp, lineHeight = 21.sp, color = InkBrown,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.weight(1f))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    StampOrReply(letter.reply, letter.repliedAtMs, letter.cloudId > 0, onStampClick)
                    Spacer(Modifier.weight(1f))
                    // 「再读一遍」印章只在平铺的这封上清晰可点
                    Text("再 读 一 遍 →",
                        fontSize = 11.sp, letterSpacing = 2.sp,
                        color = StampRed.copy(alpha = if (rel in -0.4f..0.4f) 0.85f else 0.25f),
                        modifier = Modifier
                            .graphicsLayer { rotationZ = -2f }
                            .border(1.4.dp, StampRed.copy(alpha = if (rel in -0.4f..0.4f) 0.6f else 0.2f), RoundedCornerShape(2.dp))
                            .padding(horizontal = 10.dp, vertical = 5.dp))
                }
            }
        }

        // 背光（仰倒时纸面变暗）+ 下一封下半截渐隐进纸堆
        if (shade > 0.01f) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color(0xFF50332B).copy(alpha = shade * 0.55f),
                            0.5f to Color(0xFF50332B).copy(alpha = shade * 0.15f),
                            1f to Color.Transparent
                        )
                    )
            )
        }
        if (rel > 0.3f) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0.55f to Color.Transparent,
                            1f to PaperMid.copy(alpha = ((rel - 0.3f) * 0.5f).coerceIn(0f, 0.45f))
                        )
                    )
            )
        }
    }
}

// ════════ 圆形邮戳 + 波浪线 ════════

@Composable
private fun Postmark() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        // 三条波浪线
        Canvas(Modifier.width(34.dp).height(20.dp)) {
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
                drawPath(path, InkBrown.copy(alpha = 0.55f), style = Stroke(1.3f))
            }
        }
        Spacer(Modifier.width(2.dp))
        Box(
            Modifier
                .size(54.dp)
                .graphicsLayer { rotationZ = -14f }
                .drawBehind {
                    val c = InkBrown.copy(alpha = 0.55f)
                    drawCircle(c, size.minDimension / 2f, style = Stroke(1.6f))
                    drawCircle(c, size.minDimension / 2f - 4.dp.toPx(),
                        style = Stroke(1f, pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(3f, 3f))))
                },
            contentAlignment = Alignment.Center
        ) {
            Text("SEAN·K", fontSize = 8.sp, fontWeight = FontWeight.Bold,
                color = InkBrown.copy(alpha = 0.7f), letterSpacing = 0.5.sp)
        }
    }
}

// ════════ 展开读信（unfold 动画） ════════

@Composable
private fun LetterOpenOverlay(
    letter: FoldLetter,
    onClose: () -> Unit,
    onStampClick: () -> Unit,
) {
    val unfold = remember { Animatable(0f) }
    LaunchedEffect(letter.key) {
        unfold.snapTo(0f)
        unfold.animateTo(1f, tween(480, easing = FastOutSlowInEasing))
    }
    val t = unfold.value

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF1E9DF).copy(alpha = 0.98f))
        ) {
            // 花体页眉
            Text("Letters",
                fontSize = 34.sp, color = InkDark.copy(alpha = 0.8f),
                fontFamily = FontFamily.Cursive, fontStyle = FontStyle.Italic,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 26.dp))

            // 信纸：从微仰状态展平 + 向下揭开（unfold）
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(top = 86.dp, bottom = 40.dp)
                    .graphicsLayer {
                        alpha = (t * 1.4f).coerceAtMost(1f)
                        transformOrigin = TransformOrigin(0.5f, 0f)
                        rotationX = (1f - t) * -22f
                        translationY = (1f - t) * 46.dp.toPx()
                        cameraDistance = CAMERA_DIST * density
                    }
            ) {
                TornPaper(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 26.dp, vertical = 28.dp)
                    ) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            Column {
                                Text(letter.dateMs.fmt("d"),
                                    fontSize = 48.sp, fontWeight = FontWeight.Bold,
                                    color = StampRed, fontFamily = FontFamily.Serif)
                                Text(letter.dateMs.fmt("MMM", Locale("en")).uppercase(),
                                    fontSize = 14.sp, fontWeight = FontWeight.Bold,
                                    color = InkBrown, fontFamily = FontFamily.Serif, letterSpacing = 3.sp)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("from 沈聿淮",
                                    fontSize = 24.sp, color = InkDark,
                                    fontFamily = FontFamily.Cursive, fontStyle = FontStyle.Italic)
                                Text(letter.dateMs.fmt("yyyy.MM.dd · HH:mm"),
                                    fontSize = 11.sp, color = InkSub)
                                Spacer(Modifier.height(6.dp))
                                Postmark()
                            }
                        }
                        Spacer(Modifier.height(14.dp))
                        DashedLine()
                        Spacer(Modifier.height(18.dp))
                        if (letter.title.isNotBlank()) {
                            Text(letter.title, fontSize = 16.sp, fontWeight = FontWeight.Medium,
                                color = InkDark, modifier = Modifier.padding(bottom = 12.dp))
                        }
                        Text(letter.content,
                            fontSize = 15.sp, lineHeight = 30.sp,
                            color = Color(0xFF4A3828), fontFamily = FontFamily.Serif)
                        Spacer(Modifier.height(20.dp))
                        // 回应 + 落款
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            StampOrReply(letter.reply, letter.repliedAtMs, letter.cloudId > 0, onStampClick)
                            Spacer(Modifier.weight(1f))
                            Text("—— 沈聿淮", fontSize = 13.sp, color = InkSub,
                                fontFamily = FontFamily.Serif, fontStyle = FontStyle.Italic)
                        }
                    }
                }
            }

            Text("✕", fontSize = 20.sp, color = InkDark,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 30.dp, end = 18.dp)
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.5f))
                    .clickable { onClose() }
                    .padding(top = 4.dp))
        }
    }
}

// ════════ 撕边信纸 ════════

@Composable
private fun TornPaper(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier.drawBehind {
            val w = size.width
            val h = size.height
            val edge = 6.dp.toPx()
            val paper = Path().apply {
                moveTo(0f, edge)
                var x = 0f
                while (x < w) {
                    val j = sin(x / w * 25f + 2f) * 3f + sin(x / w * 8f) * 2f
                    lineTo(x, edge + j.coerceIn(0f, edge))
                    x += 14f
                }
                lineTo(w, h - edge)
                x = w
                while (x > 0) {
                    val j = sin(x / w * 20f + 4f) * 3f
                    lineTo(x, h - edge - j.coerceIn(0f, edge))
                    x -= 14f
                }
                close()
            }
            drawPath(paper, PaperLight)
            drawPath(paper, Color(0xFFDDD5C8), style = Stroke(1.5f))
        }
    ) { content() }
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
                    drawCircle(Accent.copy(alpha = 0.5f), size.minDimension / 2f, style = Stroke(1.5f))
                    drawCircle(Accent.copy(alpha = 0.3f), size.minDimension / 2f - 4.dp.toPx(), style = Stroke(1f))
                },
                contentAlignment = Alignment.Center
            ) { Text("留一句", fontSize = 9.sp, color = Accent) }
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
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)).clickable { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier.fillMaxWidth(0.84f).clip(RoundedCornerShape(14.dp))
                .background(ReplyPaper).clickable(enabled = false) {}.padding(18.dp)
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
private fun DashedLine() {
    Canvas(Modifier.fillMaxWidth().height(1.dp)) {
        val dash = 6.dp.toPx(); val gap = 5.dp.toPx()
        var x = 0f
        while (x < size.width) {
            drawLine(InkSub.copy(alpha = 0.35f), Offset(x, 0f), Offset(x + dash, 0f), 1f)
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

private fun lerpPaper(a: Color, b: Color, t: Float): Color {
    val tt = t.coerceIn(0f, 1f)
    return Color(
        a.red + (b.red - a.red) * tt,
        a.green + (b.green - a.green) * tt,
        a.blue + (b.blue - a.blue) * tt,
        a.alpha + (b.alpha - a.alpha) * tt
    )
}

private fun Long.fmt(pattern: String, locale: java.util.Locale? = null): String {
    if (this <= 0) return ""
    val fmt = DateTimeFormatter.ofPattern(pattern)
    return if (locale != null) fmt.withLocale(locale).withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(this))
    else fmt.withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(this))
}
