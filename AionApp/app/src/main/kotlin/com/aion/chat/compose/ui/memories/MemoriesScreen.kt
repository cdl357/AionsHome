package com.aion.chat.compose.ui.memories

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aion.chat.compose.data.AppCrashLog
import com.aion.chat.compose.data.HomecomingChatWiring
import com.aion.chat.compose.ui.home.FrostCard
import com.aion.chat.compose.ui.theme.HomecomingColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 记忆库（定稿 §三·补充2 · 方案 C 权限 + 方案 D 浏览）：
 * - Sean 聊天中自动记的重要的事都在这里（homecoming 记忆层，owner=main）
 * - Yuri 能看、能搜、能手动加、能改（纠正记错的）、能删
 * - 分类翻等 keywords 体系建立后再上；先给 搜索 + 时间列表
 */
@Composable
fun MemoriesScreen(onBack: () -> Unit = {}) {
    val context = LocalContext.current

    val memories = remember { mutableStateListOf<com.aion.chat.homecoming.HomecomingMemoryRepository.Memory>() }
    val query = remember { mutableStateOf("") }
    // 分类翻（定稿方案 D）：本地启发式分类——重要日子 / 我们的约定 / 关于你 / 关于我
    val CATEGORIES = listOf("全部", "重要日子", "我们的约定", "关于你", "关于我")
    val category = remember { mutableStateOf("全部") }
    val reloadKey = remember { mutableStateOf(0) }

    fun reload() {
        val w = runCatching { HomecomingChatWiring(context) }
            .onFailure { AppCrashLog.write(context, it) }
            .getOrNull()
        if (w == null) {
            memories.clear()
            return
        }
        try {
            val list = w.memories.recall("main", query.value.trim(), 100)
            memories.clear()
            memories.addAll(list)
        } catch (e: Exception) {
            memories.clear()
        }
    }

    LaunchedEffect(reloadKey.value, query.value) {
        withContext(Dispatchers.IO) { reload() }
    }

    val showEdit = remember { mutableStateOf<com.aion.chat.homecoming.HomecomingMemoryRepository.Memory?>(null) }
    val showAdd = remember { mutableStateOf(false) }

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
                Text("记忆库", fontSize = 22.sp, color = HomecomingColors.Ink, fontWeight = FontWeight.Medium)
                Text(
                    "Sean 自动记的事都在这；你看、搜、加、改、删",
                    fontSize = 11.sp, color = HomecomingColors.InkSoft
                )
            }
        }
        Spacer(Modifier.height(10.dp))

        // ── 搜索框 ──
        OutlinedTextField(
            value = query.value,
            onValueChange = { query.value = it },
            placeholder = { Text("搜一件他记下的事…", fontSize = 13.sp, color = HomecomingColors.InkSoft) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = HomecomingColors.InkSoft) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(10.dp))

        // ── 手动加一条 ──
        FrostCard(onClick = { showAdd.value = true }) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(HomecomingColors.Accent.copy(alpha = 0.16f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text("＋", fontSize = 18.sp, color = HomecomingColors.Accent, fontWeight = FontWeight.Medium)
                }
                Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                    Text("记一件重要的事", fontSize = 14.sp, color = HomecomingColors.Ink)
                    Text("写进去后，Sean 也会记得", fontSize = 11.sp, color = HomecomingColors.InkSoft)
                }
            }
        }
        Spacer(Modifier.height(6.dp))

        // ── 分类翻 ──
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
        ) {
            CATEGORIES.forEach { c ->
                val picked = category.value == c
                Text(
                    c,
                    fontSize = 12.sp,
                    color = if (picked) Color.White else HomecomingColors.Ink,
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (picked) HomecomingColors.Accent else Color.White.copy(alpha = 0.85f))
                        .clickable { category.value = c }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
        Spacer(Modifier.height(6.dp))

        val shown = memories.filter { m ->
            category.value == "全部" || classifyMemory(m) == category.value
        }

        if (shown.isEmpty()) {
            Text(
                if (query.value.isBlank()) "记忆库还是空的——和 Sean 聊聊天，他会把重要的事记下来"
                else "没搜到记着这件事",
                fontSize = 13.sp, color = HomecomingColors.InkSoft,
                modifier = Modifier.fillMaxWidth().padding(vertical = 30.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
        shown.forEach { m ->
            FrostCard(onClick = { showEdit.value = m }) {
                Text(m.content, fontSize = 14.sp, color = HomecomingColors.Ink)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(top = 2.dp)
                ) {
                    Text(
                        fmtTime(m.updatedAt),
                        fontSize = 10.sp, color = HomecomingColors.InkSoft
                    )
                    if (m.keywords.isNotBlank()) {
                        Spacer(Modifier.weight(1f))
                        Text(
                            m.keywords.take(24),
                            fontSize = 10.sp, color = HomecomingColors.Accent,
                            maxLines = 1
                        )
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
        }
    }

    // ── 编辑 / 删除 ──
    showEdit.value?.let { m ->
        val draft = remember(m.id) { mutableStateOf(m.content) }
        AlertDialog(
            onDismissRequest = { showEdit.value = null },
            title = { Text("改这件事", fontSize = 16.sp, color = HomecomingColors.Ink) },
            text = {
                OutlinedTextField(
                    value = draft.value,
                    onValueChange = { draft.value = it },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val text = draft.value.trim()
                    if (text.isEmpty()) return@TextButton
                    scopeEdit(context, m.id, text, m.baseHash) { reloadKey.value++ }
                    showEdit.value = null
                }) { Text("保存", color = HomecomingColors.Accent) }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = {
                    scopeDelete(context, m.id, m.baseHash) { reloadKey.value++ }
                    showEdit.value = null
                }) { Text("删除", color = HomecomingColors.Danger) }
            }
        )
    }

    // ── 手动添加 ──
    if (showAdd.value) {
        val draft = remember(showAdd.value) { mutableStateOf("") }
        val kwDraft = remember(showAdd.value) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAdd.value = false },
            title = { Text("记一件重要的事", fontSize = 16.sp, color = HomecomingColors.Ink) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = draft.value,
                        onValueChange = { draft.value = it },
                        placeholder = { Text("比如：我们的纪念日是 7 月 9 日", fontSize = 13.sp, color = HomecomingColors.InkSoft) },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = kwDraft.value,
                        onValueChange = { kwDraft.value = it },
                        label = { Text("关键词（可选，空格分隔，帮助 Sean 想起）", fontSize = 11.sp) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val text = draft.value.trim()
                    if (text.isEmpty()) return@TextButton
                    scopeAdd(context, text, kwDraft.value.trim()) {
                        reloadKey.value++
                        Toast.makeText(context, "记下了", Toast.LENGTH_SHORT).show()
                    }
                    showAdd.value = false
                }) { Text("记下", color = HomecomingColors.Accent) }
            },
            dismissButton = {
                TextButton(onClick = { showAdd.value = false }) {
                    Text("取消", color = HomecomingColors.InkSoft)
                }
            }
        )
    }
}

private fun fmtTime(ts: Long): String = try {
    if (ts <= 0) "" else SimpleDateFormat("M月d日", Locale.CHINA).format(Date(ts))
} catch (e: Exception) { "" }

// ── 写操作走 IO，异常只记日志不打扰页面 ──

private fun scopeAdd(context: android.content.Context, content: String, keywords: String, done: () -> Unit) {
    kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) {
        try {
            val w = HomecomingChatWiring(context)
            w.memories.create("main", content, keywords, System.currentTimeMillis())
        } catch (e: Exception) { AppCrashLog.write(context, e) }
        withContext(Dispatchers.Main) { done() }
    }
}

private fun scopeEdit(context: android.content.Context, id: String, content: String, baseHash: String, done: () -> Unit) {
    kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) {
        try {
            val w = HomecomingChatWiring(context)
            w.memories.update("main", id, content, baseHash, System.currentTimeMillis())
        } catch (e: Exception) { AppCrashLog.write(context, e) }
        withContext(Dispatchers.Main) { done() }
    }
}

private fun scopeDelete(context: android.content.Context, id: String, baseHash: String, done: () -> Unit) {
    kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) {
        try {
            val w = HomecomingChatWiring(context)
            w.memories.delete("main", id, baseHash, System.currentTimeMillis())
        } catch (e: Exception) { AppCrashLog.write(context, e) }
        withContext(Dispatchers.Main) { done() }
    }
}

/** 本地启发式分类（定稿方案 D 的四类）。 */
private fun classifyMemory(m: com.aion.chat.homecoming.HomecomingMemoryRepository.Memory): String {
    val text = (m.content + " " + m.keywords)
    return when {
        Regex("生日|纪念日|周年|每年|\\d+月\\d+日|\\d+号|领证|结婚").containsMatchIn(text) -> "重要日子"
        Regex("约定|说好|答应|一起|陪你去|下周|周末|计划|以后要").containsMatchIn(text) -> "我们的约定"
        Regex("Yuri|小鑫|她|你").containsMatchIn(text) -> "关于你"
        else -> "关于我"
    }
}
