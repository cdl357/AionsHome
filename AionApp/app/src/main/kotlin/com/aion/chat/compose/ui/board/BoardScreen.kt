package com.aion.chat.compose.ui.board

import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import kotlinx.coroutines.delay
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aion.chat.compose.data.HomecomingChatWiring
import com.aion.chat.compose.data.HomecomingDayStore
import com.aion.chat.compose.data.HomecomingRouteConfig
import com.aion.chat.compose.ui.home.FrostCard
import com.aion.chat.compose.ui.theme.HomecomingColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 留言板（布局定稿·方案 A）：双向便利贴墙 + 点开看回复、可对话。数据存 board_note_local / board_reply_local。 */
@Composable
fun BoardScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val main = remember { Handler(Looper.getMainLooper()) }

    val notes = remember { mutableStateListOf<HomecomingDayStore.BoardNote>() }
    val repliesMap = remember { mutableStateOf(emptyMap<Long, List<HomecomingDayStore.NoteReply>>()) }
    val openNote = remember { mutableStateOf<Long?>(null) }
    val showCompose = remember { mutableStateOf(false) }
    val composeText = remember { mutableStateOf("") }
    val replyDraft = remember { mutableStateOf("") }

    val routeStamp = HomecomingRouteConfig.stamp(context)
    val wiring = remember(routeStamp) {
        runCatching { HomecomingChatWiring(context) }
            .onFailure { /* 无线路时 Sean 不贴，安静 */ }
            .getOrNull()
    }
    val routeReady = wiring != null && HomecomingRouteConfig.mainRoute(context) != null

    fun reload() {
        try {
            val list = HomecomingDayStore.boardNotes(context)
            val map = mutableMapOf<Long, List<HomecomingDayStore.NoteReply>>()
            list.forEach { n -> map[n.id] = HomecomingDayStore.noteReplies(context, n.id) }
            main.post {
                notes.clear()
                notes.addAll(list)
                repliesMap.value = map
            }
        } catch (e: Exception) { /* 安静 */ }
    }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) { reload() }
    }

    // ── Sean 惰性回应：Yuri 贴便利贴/回复后，线路就绪时 Sean 也来一张/回一句 ──
    fun askSean(trigger: String, onDone: (String) -> Unit) {
        if (!routeReady) return
        scope.launch(Dispatchers.IO) {
            delay(1500L)
            try {
                var reply: String? = null
                wiring.engine.send(
                    com.aion.chat.homecoming.HomecomingChatEngine.ChatCommand(
                        "req_board_" + System.currentTimeMillis(),
                        "moments_private", "sean", "user", trigger, "main", wiring.mainModelKey(), "", ""
                    ),
                    object : com.aion.chat.homecoming.HomecomingChatEngine.Observer {
                        override fun onChunk(chunk: String) {}
                        override fun onComplete(messageId: String, text: String) {
                            reply = text.trim()
                            main.post {
                                if (reply != null) onDone(reply)
                            }
                        }
                        override fun onFailure(code: String) {}
                    }
                )
            } catch (e: Exception) { /* 安静 */ }
        }
    }

    // ── 便利贴墙 ──
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(18.dp)
    ) {
        Text("留言板", fontSize = 22.sp, color = HomecomingColors.Ink)
        Text("一面墙，一人一张便利贴，想说什么就贴上去", fontSize = 12.sp, color = HomecomingColors.InkSoft)
        Spacer(Modifier.height(14.dp))

        if (openNote.value == null) {
            // ── 墙视图 ──
            if (notes.isEmpty()) {
                Text(
                    "墙上还空着。\n点右下角贴上第一张便利贴。",
                    fontSize = 13.sp, color = HomecomingColors.InkSoft,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 30.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
            notes.forEach { n ->
                val bg = when (n.author) {
                    "user" -> Color(0xFFFFF3CE)
                    "sean", "aion" -> Color(0xFFDDEEF4)
                    else -> Color(0xFFF0F0F0)
                }
                val thread = repliesMap.value[n.id].orEmpty()
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 5.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(bg.copy(alpha = 0.92f))
                        .clickable { openNote.value = n.id }
                        .padding(14.dp)
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                authorName(n.author),
                                fontSize = 12.sp, fontWeight = FontWeight.Medium, color = HomecomingColors.InkSoft
                            )
                            Spacer(Modifier.weight(1f))
                            Text(relTime(n.createdAt), fontSize = 10.sp, color = HomecomingColors.InkSoft)
                        }
                        Text(
                            n.content, fontSize = 14.sp, color = HomecomingColors.Ink,
                            maxLines = 3, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                        if (thread.isNotEmpty()) {
                            Text(
                                "♥ ${thread.size} 条来往",
                                fontSize = 10.sp, color = HomecomingColors.Accent,
                                modifier = Modifier.padding(top = 6.dp)
                            )
                        }
                    }
                }
            }
        } else {
            // ── 便利贴详情：原帖 + 回复线程 + 回复输入 ──
            val note = notes.firstOrNull { it.id == openNote.value }
            if (note == null) {
                openNote.value = null
                return
            }
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "‹ 回便利贴墙",
                    fontSize = 14.sp, color = HomecomingColors.Accent,
                    modifier = Modifier.clickable { openNote.value = null }
                )
            }
            Spacer(Modifier.height(10.dp))
            FrostCard {
                Text(authorName(note.author), fontSize = 12.sp, color = HomecomingColors.InkSoft)
                Text(note.content, fontSize = 15.sp, color = HomecomingColors.Ink, modifier = Modifier.padding(top = 6.dp))
                Text(relTime(note.createdAt), fontSize = 10.sp, color = HomecomingColors.InkSoft, modifier = Modifier.padding(top = 8.dp))
            }
            Spacer(Modifier.height(10.dp))
            Text("你来我往", fontSize = 13.sp, color = HomecomingColors.InkSoft)
            Spacer(Modifier.height(6.dp))
            val thread = repliesMap.value[note.id].orEmpty()
            if (thread.isEmpty()) {
                Text("还没有回复，回一句吧。", fontSize = 12.sp, color = HomecomingColors.InkSoft)
            }
            thread.forEach { r ->
                FrostCard {
                    Text(authorName(r.author), fontSize = 12.sp, color = HomecomingColors.InkSoft)
                    Text(r.content, fontSize = 14.sp, color = HomecomingColors.Ink, modifier = Modifier.padding(top = 4.dp))
                    Text(relTime(r.createdAt), fontSize = 10.sp, color = HomecomingColors.InkSoft, modifier = Modifier.padding(top = 4.dp))
                }
                Spacer(Modifier.height(8.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = replyDraft.value,
                    onValueChange = { replyDraft.value = it },
                    placeholder = { Text("回一句…", color = HomecomingColors.InkSoft) },
                    modifier = Modifier.weight(1f), singleLine = true
                )
                TextButton(onClick = {
                    val v = replyDraft.value.trim()
                    if (v.isNotEmpty()) {
                        HomecomingDayStore.addNoteReply(context, note.id, "user", v)
                        replyDraft.value = ""
                        reload()
                        askSean("Yuri 在你的便利贴『${note.content.take(40)}』下回复了：『$v』。以 Sean 的身份回她一句，一句话。") { seanText ->
                            HomecomingDayStore.addNoteReply(context, note.id, "sean", seanText)
                            main.post { reload() }
                        }
                    }
                }) { Text("发送", color = HomecomingColors.Accent) }
            }
        }

        // ── 贴新便利贴 ──
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = composeText.value,
                onValueChange = { composeText.value = it },
                placeholder = { Text("写一句话贴上去…", color = HomecomingColors.InkSoft) },
                modifier = Modifier.weight(1f), singleLine = true
            )
            TextButton(onClick = {
                val v = composeText.value.trim()
                if (v.isEmpty()) return@TextButton
                composeText.value = ""
                scope.launch(Dispatchers.IO) {
                    val newId = HomecomingDayStore.addBoardNote(context, "user", v)
                    main.post {
                        reload()
                        if (newId > 0 && routeReady) {
                            askSean("Yuri 贴了一张新便利贴：『${v.take(50)}』。以 Sean 的身份也贴一张便利贴回应她，一句话。") { seanText ->
                                HomecomingDayStore.addBoardNote(context, "sean", seanText)
                                main.post { reload() }
                            }
                        }
                    }
                }
            }) { Text("贴上", color = HomecomingColors.Accent) }
        }
    }
}

private fun authorName(author: String): String = when (author) {
    "user" -> "Yuri"; "sean", "aion" -> "Sean"; else -> author
}

private fun relTime(ts: Long): String {
    if (ts <= 0) return ""
    val diff = System.currentTimeMillis() - ts
    val m = diff / 60000
    return when {
        m < 1 -> "刚刚"
        m < 60 -> "$m 分钟前"
        m < 1440 -> "${m / 60} 小时前"
        m < 43200 -> "${m / 1440} 天前"
        else -> java.text.SimpleDateFormat("M月d日", java.util.Locale.CHINA).format(java.util.Date(ts))
    }
}
