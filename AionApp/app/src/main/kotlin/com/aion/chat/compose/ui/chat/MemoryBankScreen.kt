package com.aion.chat.compose.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.aion.chat.compose.data.MemoryStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 记忆库（页面照橘瓣 MemoryBankPage）：
 * - 常驻记忆（active_memory）：每次请求自动带上
 * - 记忆文件：Sean 用工具自己记的，这里可视化管理（手动改也行）
 */
private val MbBg = Color(0xFFF7F5F1)
private val MbCard = Color(0xFFFFFFFF)
private val MbInk = Color(0xFF26211C)
private val MbSub = Color(0xFF9A9388)
private val MbAccent = Color(0xFF7FA6C9)
private val MbDanger = Color(0xFFC96B6B)

@Composable
fun MemoryBankScreen(onBack: () -> Unit = {}) {
    val context = LocalContext.current
    var active by remember { mutableStateOf("") }
    var files by remember { mutableStateOf(listOf<String>()) }
    var editing by remember { mutableStateOf<String?>(null) }   // null=不在编辑；""=新建未命名
    var editContent by remember { mutableStateOf("") }
    var showNew by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<String?>(null) }

    fun refresh() {
        kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) {
            val a = MemoryStore.activeMemory()
            val f = MemoryStore.files()
            withContext(Dispatchers.Main) { active = a; files = f }
        }
    }
    LaunchedEffect(Unit) { refresh() }

    Column(
        Modifier
            .fillMaxSize()
            .background(MbBg)
            .statusBarsPadding()
    ) {
        Row(verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Box(
                Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .clickable { onBack() },
                contentAlignment = Alignment.Center
            ) { Text("‹", fontSize = 22.sp, color = MbInk) }
            Spacer(Modifier.width(10.dp))
            Column {
                Text("记忆库", fontSize = 19.sp, color = MbInk, fontWeight = FontWeight.SemiBold)
                Text("他的长期记忆：常驻 + 分文件，他能自己读写，你也能改", fontSize = 10.sp, color = MbSub)
            }
        }

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp)
        ) {
            // ── 常驻记忆 ──
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(MbCard)
                    .padding(14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("常驻记忆", fontSize = 15.sp, color = MbInk, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.weight(1f))
                    Text("${active.length} 字", fontSize = 11.sp, color = MbSub)
                }
                Text(
                    "每次对话自动带上。只放最重要的长期信息，细节分到下面的文件。",
                    fontSize = 11.sp, color = MbSub, lineHeight = 15.sp,
                    modifier = Modifier.padding(top = 2.dp)
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = active,
                    onValueChange = { active = it },
                    minLines = 5, maxLines = 12,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.End) {
                    TextButton(onClick = {
                        kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) {
                            MemoryStore.writeActive(active)
                            withContext(Dispatchers.Main) { refresh() }
                        }
                    }) { Text("保存常驻记忆", color = MbAccent, fontWeight = FontWeight.SemiBold, fontSize = 13.sp) }
                }
            }

            Spacer(Modifier.height(12.dp))

            // ── 记忆文件 ──
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(MbCard)
                    .padding(14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("记忆文件", fontSize = 15.sp, color = MbInk, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.weight(1f))
                    Text("＋ 新建", fontSize = 13.sp, color = MbAccent,
                        modifier = Modifier.clickable { showNew = true })
                }
                if (files.isEmpty()) {
                    Text(
                        "还没有记忆文件。他自己会用工具记，你也可以手动新建。",
                        fontSize = 12.sp, color = MbSub, modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
                files.forEach { f ->
                    val preview = runCatching { MemoryStore.read(f) ?: "" }.getOrDefault("")
                    Row(verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                editing = f
                                editContent = preview
                            }
                            .padding(vertical = 8.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text(f.removeSuffix(".md"), fontSize = 14.sp, color = MbInk)
                            Text(
                                preview.take(60).ifBlank { "（空）" },
                                fontSize = 11.sp, color = MbSub, maxLines = 1
                            )
                        }
                        Text(
                            "删除", fontSize = 12.sp, color = MbDanger,
                            modifier = Modifier
                                .clickable { deleting = f }
                                .padding(6.dp)
                        )
                    }
                }
            }
            Spacer(Modifier.height(60.dp))
        }
    }

    // 编辑器
    editing?.let { fname ->
        var draft by remember(fname) { mutableStateOf(editContent) }
        AlertDialog(
            onDismissRequest = { editing = null },
            containerColor = MbCard,
            shape = RoundedCornerShape(20.dp),
            title = {
                Column {
                    Text(fname.removeSuffix(".md"), fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = MbInk)
                    Text("覆盖式保存；Sean 下次读到的就是这里的内容", fontSize = 10.sp, color = MbSub)
                }
            },
            text = {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    minLines = 6, maxLines = 14,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) {
                        MemoryStore.write(fname, draft)
                        withContext(Dispatchers.Main) { editing = null; refresh() }
                    }
                }) { Text("保存", color = MbAccent, fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("取消", color = MbSub) } }
        )
    }

    // 新建
    if (showNew) {
        var newName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showNew = false },
            containerColor = MbCard,
            shape = RoundedCornerShape(20.dp),
            title = { Text("新建记忆文件", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = MbInk) },
            text = {
                OutlinedTextField(
                    value = newName, onValueChange = { newName = it },
                    label = { Text("文件名（如：她的喜好）", fontSize = 13.sp, color = MbSub) },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) {
                        val ok = MemoryStore.write(newName, "")
                        withContext(Dispatchers.Main) {
                            showNew = false
                            if (ok) { refresh(); editing = newName; editContent = "" }
                        }
                    }
                }) { Text("创建", color = MbAccent, fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = { TextButton(onClick = { showNew = false }) { Text("取消", color = MbSub) } }
        )
    }

    // 删除确认
    deleting?.let { f ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            containerColor = MbCard,
            shape = RoundedCornerShape(20.dp),
            title = { Text("删除「${f.removeSuffix(".md")}」？", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = MbInk) },
            text = { Text("Sean 也会失去这份记忆。", fontSize = 12.sp, color = MbSub) },
            confirmButton = {
                TextButton(onClick = {
                    kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) {
                        MemoryStore.delete(f)
                        withContext(Dispatchers.Main) { deleting = null; refresh() }
                    }
                }) { Text("删除", color = MbDanger, fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("取消", color = MbSub) } }
        )
    }
}
