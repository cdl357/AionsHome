package com.aion.chat.compose.ui.diary

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
import kotlin.math.abs
import kotlin.math.sin

/**
 * 日记 · 信件页（参考图：小红书 Letters 页）。
 * 中间的信平面对着屏幕，上面的往里倒一点、下面的往里倒一点，
 * 缝隙紧密像连着的一张长纸。翻开某封 → 全屏信纸（撕边、大字、邮戳）。
 * 排版靠优雅字体（大号衬线日期、花体 from、虚线分隔、邮戳），不靠 3D 厚度。
 */

private const val MAX_TILT = 14f
private const val CAMERA_DIST = 40f
private val PaperLight = Color(0xFFFAF5EE)
private val PaperMid = Color(0xFFF5EFE6)
private val PaperDark = Color(0xFFEDE5D8)
private val InkDark = Color(0xFF5C3D2E)
private val InkBrown = Color(0xFF7A5C48)
private val InkSub = Color(0xFFA08878)
private val Accent = Color(0xFF8B6348)
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
    val listState = rememberLazyListState()
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
            // 顶栏
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
                Text("日记 · 信",
                    fontSize = 16.sp, color = HomecomingColors.Ink, fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .padding(start = 10.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = 0.45f))
                        .padding(horizontal = 12.dp, vertical = 5.dp))
                Spacer(Modifier.weight(1f))
                Text("${letters.value.size} 封", fontSize = 11.sp, color = HomecomingColors.InkSoft,
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
                    else -> LettersList(letters.value, listState,
                        onOpen = { openLetter.value = it },
                        onStampClick = { l -> if (l.cloudId > 0) editingId.value = l.cloudId })
                }
            }
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

// ════════ 列表 ════════

@Composable
private fun LettersList(
    letters: List<FoldLetter>,
    listState: LazyListState,
    onOpen: (FoldLetter) -> Unit,
    onStampClick: (FoldLetter) -> Unit,
) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 0.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        itemsIndexed(letters, key = { _, it -> it.key }) { index, letter ->
            LetterCard(letter, index, listState, { onOpen(letter) }, { onStampClick(letter) })
        }
    }
}

// ════════ 单封信卡：薄纸 + 微透视 + 排版 ════════

@Composable
private fun LetterCard(
    letter: FoldLetter, index: Int, listState: LazyListState,
    onOpen: () -> Unit, onStampClick: () -> Unit,
) {
    val density = LocalDensity.current
    val cardH = 280.dp
    val cardHpx = with(density) { cardH.toPx() }
    val progress by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val item = info.visibleItemsInfo.firstOrNull { it.index == index }
                ?: return@derivedStateOf if (index < listState.firstVisibleItemIndex) -1f else 1f
            val center = (info.viewportStartOffset + info.viewportEndOffset) / 2f
            ((item.offset + item.size / 2f - center) / cardHpx).coerceIn(-1f, 1f)
        }
    }
    val absP = abs(progress)
    val tilt = progress * MAX_TILT
    val pivotY = if (tilt < 0f) 1f else 0f

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(cardH)
            .graphicsLayer {
                rotationX = -tilt
                transformOrigin = TransformOrigin(0.5f, pivotY)
                cameraDistance = CAMERA_DIST * density.density
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to PaperLight,
                        0.5f to lerpPaper(PaperMid, PaperDark, absP * 0.3f),
                        1f to lerpPaper(PaperMid, PaperDark, absP * 0.35f)
                    )
                )
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) { onOpen() }
                .padding(horizontal = 28.dp, vertical = 14.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column {
                    Text(letter.dateMs.fmt("d"),
                        fontSize = 36.sp, fontWeight = FontWeight.Bold,
                        color = InkDark, fontFamily = FontFamily.Serif)
                    Text(letter.dateMs.fmt("MMM", Locale("en")),
                        fontSize = 12.sp, fontWeight = FontWeight.Bold,
                        color = InkDark, fontFamily = FontFamily.Serif, letterSpacing = 2.sp)
                    Spacer(Modifier.height(4.dp))
                    Text("from 沈聿淮",
                        fontSize = 14.sp, color = InkBrown,
                        fontFamily = FontFamily.Serif, fontStyle = FontStyle.Italic)
                    Text(letter.dateMs.fmt("yyyy-MM-dd · HH:mm"),
                        fontSize = 10.sp, color = InkSub)
                }
                Spacer(Modifier.weight(1f))
                StampOrReply(letter.reply, letter.repliedAtMs, letter.cloudId > 0, onStampClick)
            }
            Spacer(Modifier.height(8.dp))
            DashedLine()
            Spacer(Modifier.height(10.dp))
            Text(letter.content.replace("\n", " ").trim(),
                fontSize = 13.sp, lineHeight = 22.sp, color = InkBrown,
                maxLines = 3, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.weight(1f))
            Text("再读一遍 →", fontSize = 11.sp, color = Accent,
                fontFamily = FontFamily.Serif, modifier = Modifier.align(Alignment.End))
        }
    }
}

// ════════ 展开的信：全屏撕边信纸 ════════

@Composable
private fun LetterOpenOverlay(
    letter: FoldLetter,
    onClose: () -> Unit,
    onStampClick: () -> Unit,
) {
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF5EFE6).copy(alpha = 0.97f))
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) { onClose() }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(top = 48.dp, bottom = 40.dp)
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
                                    fontSize = 44.sp, fontWeight = FontWeight.Bold,
                                    color = InkDark, fontFamily = FontFamily.Serif)
                                Text(letter.dateMs.fmt("MMM", Locale("en")),
                                    fontSize = 14.sp, fontWeight = FontWeight.Bold,
                                    color = InkDark, fontFamily = FontFamily.Serif, letterSpacing = 3.sp)
                            }
                            Spacer(Modifier.weight(1f))
                            Column(horizontalAlignment = Alignment.End) {
                                Text("from 沈聿淮",
                                    fontSize = 18.sp, color = InkBrown,
                                    fontFamily = FontFamily.Serif, fontStyle = FontStyle.Italic)
                                Text(letter.dateMs.fmt("yyyy-MM-dd · HH:mm"),
                                    fontSize = 11.sp, color = InkSub)
                            }
                        }
                        // 邮戳/回应
                        if (letter.cloudId > 0) {
                            Spacer(Modifier.height(8.dp))
                            StampOrReply(letter.reply, letter.repliedAtMs, true, onStampClick)
                        }
                        Spacer(Modifier.height(12.dp))
                        DashedLine()
                        Spacer(Modifier.height(18.dp))
                        if (letter.title.isNotBlank()) {
                            Text(letter.title, fontSize = 15.sp, fontWeight = FontWeight.Medium,
                                color = InkBrown, modifier = Modifier.padding(bottom = 12.dp))
                        }
                        Text(letter.content,
                            fontSize = 15.sp, lineHeight = 30.sp,
                            color = Color(0xFF4A3828), fontFamily = FontFamily.Serif)
                        Spacer(Modifier.height(20.dp))
                        Text("—— 沈聿淮", fontSize = 13.sp, color = InkSub,
                            fontFamily = FontFamily.Serif, fontStyle = FontStyle.Italic,
                            modifier = Modifier.align(Alignment.End))
                    }
                }
            }
            Text("✕", fontSize = 20.sp, color = InkDark,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 48.dp, end = 20.dp)
                    .clickable { onClose() })
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
                .size(52.dp)
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
