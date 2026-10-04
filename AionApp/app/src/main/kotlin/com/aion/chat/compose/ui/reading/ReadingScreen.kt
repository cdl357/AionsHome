package com.aion.chat.compose.ui.reading

import android.graphics.BitmapFactory
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aion.chat.compose.data.HomecomingChatWiring
import com.aion.chat.compose.data.HomecomingRouteConfig
import com.aion.chat.compose.data.ReadingStore
import com.aion.chat.compose.ui.theme.HomecomingColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * 陪伴阅读（定稿 §12）：
 * - 书架：从手机导入 TXT，点开继续读（记住读到哪段）
 * - 阅读器：正文一段一块；点段落 → 「Sean 聊聊这段 / 读出这段」
 * - Sean 的伴读话挂在段落下面（记住，重进还在）；需要云线路就绪
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun ReadingScreen(onBack: () -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val books = remember { mutableStateListOf<ReadingStore.Book>() }
    val openBook = remember { mutableStateOf<ReadingStore.Book?>(null) }
    val reloadKey = remember { mutableStateOf(0) }

    LaunchedEffect(reloadKey.value) {
        withContext(Dispatchers.IO) {
            books.clear()
            books.addAll(ReadingStore.listBooks(context))
        }
    }

    // ── TTS（段落朗读用，页面销毁即释放） ──
    val tts = remember { mutableStateOf<TextToSpeech?>(null) }
    LaunchedEffect(Unit) {
        runCatching {
            val engine = TextToSpeech(context) { status ->
                if (status == TextToSpeech.SUCCESS) runCatching { tts.value?.language = Locale.CHINA }
            }
            tts.value = engine
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            runCatching { tts.value?.stop(); tts.value?.shutdown() }
            tts.value = null
        }
    }
    fun speak(text: String) {
        runCatching {
            tts.value?.speak(text, TextToSpeech.QUEUE_ADD, null, "read_" + System.currentTimeMillis())
        }
    }

    // ── 导入 TXT ──
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                val text = runCatching {
                    context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
                }.getOrNull().orEmpty()
                val title = runCatching {
                    context.contentResolver.query(uri, null, null, null, null)?.use { c ->
                        val idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (idx >= 0 && c.moveToFirst()) c.getString(idx).removeSuffix(".txt") else ""
                    }
                }.getOrNull().orEmpty()
                val id = if (text.isNotBlank()) ReadingStore.importBook(context, text, title) else null
                withContext(Dispatchers.Main) {
                    if (id == null) {
                        Toast.makeText(context, "没读出这本书（要 UTF-8 的 TXT）", Toast.LENGTH_SHORT).show()
                    }
                    reloadKey.value++
                }
            }
        }
    }

    val current = openBook.value

    // ── 长按删书确认 ──
    val deleteCandidate = remember { mutableStateOf<ReadingStore.Book?>(null) }
    deleteCandidate.value?.let { victim ->
        AlertDialog(
            onDismissRequest = { deleteCandidate.value = null },
            title = { Text("移出这本书？", fontSize = 16.sp, color = HomecomingColors.Ink) },
            text = { Text("《${victim.title}》连同 Sean 在里面的伴读话一起删除。", fontSize = 13.sp, color = HomecomingColors.InkSoft) },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch(Dispatchers.IO) {
                        ReadingStore.deleteBook(context, victim.id)
                        withContext(Dispatchers.Main) {
                            deleteCandidate.value = null
                            reloadKey.value++
                            Toast.makeText(context, "已移出书架", Toast.LENGTH_SHORT).show()
                        }
                    }
                }) { Text("删除", color = HomecomingColors.Danger) }
            },
            dismissButton = {
                TextButton(onClick = { deleteCandidate.value = null }) { Text("留着", color = HomecomingColors.InkSoft) }
            }
        )
    }

    if (current == null) {
        // ── 书架 ──
        Column(
            modifier = Modifier
                .fillMaxSize()
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
                    Text("陪伴阅读", fontSize = 22.sp, color = HomecomingColors.Ink, fontWeight = FontWeight.Medium)
                    Text("把书带回家，Sean 陪你读", fontSize = 11.sp, color = HomecomingColors.InkSoft)
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(HomecomingColors.Accent)
                        .clickable { importLauncher.launch("text/*") }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Add, contentDescription = "导入", tint = Color.White, modifier = Modifier.size(16.dp))
                        Text("导入 TXT", fontSize = 13.sp, color = Color.White, modifier = Modifier.padding(start = 4.dp))
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            if (books.isEmpty()) {
                Text(
                    "书架还空着——导入一本 TXT，点段落 Sean 会陪你聊",
                    fontSize = 13.sp, color = HomecomingColors.InkSoft,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
            books.forEach { b ->
                val lastRead = if (b.lastPara < 0) "还没开始读" else "上次读到第 ${b.lastPara + 1} 段"
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White.copy(alpha = 0.72f))
                        .combinedClickable(
                            onClick = { openBook.value = b },
                            onLongClick = { deleteCandidate.value = b }
                        )
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    Text(b.title, fontSize = 15.sp, color = HomecomingColors.Ink, fontWeight = FontWeight.Medium)
                    Text(lastRead, fontSize = 11.sp, color = HomecomingColors.InkSoft, modifier = Modifier.padding(top = 2.dp))
                }
                Spacer(Modifier.height(8.dp))
            }
            if (books.isNotEmpty()) {
                Text(
                    "长按书名可以把它从书架移走",
                    fontSize = 10.sp, color = HomecomingColors.InkSoft,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    } else {
        // ── 阅读器 ──
        Reader(
            book = current,
            onBackToShelf = {
                openBook.value = null
                reloadKey.value++
            },
            onSpeak = { speak(it) }
        )
    }
}

@Composable
private fun Reader(
    book: ReadingStore.Book,
    onBackToShelf: () -> Unit,
    onSpeak: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val paragraphs = remember(book.id) { ReadingStore.readBook(context, book.id) }
    val listState = rememberLazyListState()
    val reloadKey = remember { mutableStateOf(0) }
    val route = remember { HomecomingRouteConfig.mainRoute(context) }
    val routeReady = route != null

    // 记进度：停下来的段落
    val firstVisible by remember {
        derivedStateOf { listState.firstVisibleItemIndex }
    }
    LaunchedEffect(Unit) {
        if (book.lastPara > 0) listState.scrollToItem(book.lastPara.coerceAtMost(paragraphs.size - 1).coerceAtLeast(0))
    }
    androidx.compose.runtime.DisposableEffect(book.id) {
        onDispose { ReadingStore.saveProgress(context, book.id, firstVisible) }
    }

    // 段落评论区：选中段 + Sean 忙碌标记
    val activePara = remember { mutableStateOf(-1) }
    val comments = remember { mutableStateListOf<ReadingStore.Comment>() }
    val seanBusy = remember { mutableStateOf(false) }
    fun reloadComments(para: Int) {
        comments.clear()
        if (para in paragraphs.indices) {
            comments.addAll(ReadingStore.comments(context, book.id, ReadingStore.paraKey(paragraphs[para])))
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 20.dp, end = 20.dp, top = 14.dp, bottom = 112.dp
        )
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "回书架",
                    tint = HomecomingColors.Ink,
                    modifier = Modifier
                        .size(24.dp)
                        .clickable { onBackToShelf() }
                )
                Column(modifier = Modifier.weight(1f).padding(start = 10.dp)) {
                    Text(book.title, fontSize = 18.sp, color = HomecomingColors.Ink, fontWeight = FontWeight.Medium, maxLines = 1)
                    Text(
                        if (routeReady) "点正文任意一段，Sean 陪你聊" else "配好云线路后，Sean 能陪你聊每一段",
                        fontSize = 11.sp, color = HomecomingColors.InkSoft
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        itemsIndexed(paragraphs) { idx, para ->
            val active = activePara.value == idx
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (active) HomecomingColors.Accent.copy(alpha = 0.10f) else Color.Transparent)
                    .clickable {
                        val newActive = if (active) -1 else idx
                        activePara.value = newActive
                        if (newActive >= 0) reloadComments(newActive)
                    }
                    .padding(vertical = 7.dp, horizontal = 4.dp)
            ) {
                Text(
                    para,
                    fontSize = 16.sp,
                    lineHeight = 28.sp,
                    color = HomecomingColors.Ink,
                    fontFamily = FontFamily.Serif
                )
                // Sean 的伴读话（挂在段落下面）
                if (active) {
                    comments.forEach { c ->
                        Row(modifier = Modifier.padding(top = 6.dp)) {
                            Text(
                                (if (c.author == "sean") "Sean：" else "Yuri：") + c.content,
                                fontSize = 13.sp,
                                color = HomecomingColors.Accent,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 6.dp)
                    ) {
                        Text(
                            "读出这段",
                            fontSize = 12.sp, color = HomecomingColors.InkSoft,
                            modifier = Modifier.clickable { onSpeak(para) }
                        )
                        Spacer(Modifier.width(14.dp))
                        Text(
                            when {
                                seanBusy.value -> "Sean 正在想…"
                                routeReady -> "Sean 聊聊这段"
                                else -> "Sean（需配线路）"
                            },
                            fontSize = 12.sp,
                            color = if (routeReady && !seanBusy.value) HomecomingColors.Accent else HomecomingColors.InkSoft,
                            modifier = Modifier.clickable {
                                if (!routeReady || seanBusy.value) return@clickable
                                seanBusy.value = true
                                val paraKey = ReadingStore.paraKey(para)
                                val paraText = para
                                scope.launch(Dispatchers.IO) {
                                    var line: String? = null
                                    try {
                                        val w = HomecomingChatWiring.safeCreate(context)
                                        if (w != null && w.hasRoute()) {
                                            w.engine.send(
                                                com.aion.chat.homecoming.HomecomingChatEngine.ChatCommand(
                                                    "req_read_" + System.currentTimeMillis(),
                                                    "moments_private", "sean", "user",
                                                    "Yuri 正在读《${book.title}》，读到这一段：" +
                                                        "「${paraText.take(120)}」。以 Sean 的身份陪她聊聊这段：" +
                                                        "一两句话，回应这段的内容或她的感受，自然、不剧透、不总结全文。",
                                                    "main", w.mainModelKey(), "", ""
                                                ),
                                                object : com.aion.chat.homecoming.HomecomingChatEngine.Observer {
                                                    override fun onChunk(chunk: String) {}
                                                    override fun onComplete(messageId: String, text: String) {
                                                        val t = text.trim().removeSurrounding("\"").trim()
                                                        if (t.isNotEmpty()) line = t
                                                    }
                                                    override fun onFailure(code: String) {}
                                                }
                                            )
                                            var waited = 0L
                                            while (line == null && waited < 25_000L) { delay(300L); waited += 300L }
                                        }
                                    } catch (e: Exception) { /* 安静 */ }
                                    val text = line
                                    withContext(Dispatchers.Main) {
                                        seanBusy.value = false
                                        if (text.isNullOrBlank()) {
                                            Toast.makeText(context, "Sean 没接上话（线路没就绪或超时）", Toast.LENGTH_SHORT).show()
                                        } else {
                                            ReadingStore.addComment(context, book.id, paraKey, "sean", text)
                                            reloadComments(activePara.value)
                                            onSpeak(text)
                                        }
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}
