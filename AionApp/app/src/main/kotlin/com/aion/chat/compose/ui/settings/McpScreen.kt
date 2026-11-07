package com.aion.chat.compose.ui.settings

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aion.chat.compose.data.McpClient
import com.aion.chat.compose.data.McpStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * MCP 配置页（照橘瓣）：服务器列表（状态点 + 协议徽章 + 启停 + 编辑 + 删除），
 * 右上角 导入 / 新增。连接 = 真 MCP 协议握手（initialize → tools/list）。
 */
private val McpBg = Color(0xFFF2F0EB)
private val McpCard = Color(0xFFEDEBE6)
private val McpInk = Color(0xFF26211C)
private val McpSub = Color(0xFF8A8378)
private val McpGreen = Color(0xFF4CAF50)
private val McpRed = Color(0xFFE06060)
private val McpBlue = Color(0xFF7FA6C9)

@Composable
fun McpScreen(onBack: () -> Unit = {}) {
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var rev by remember { mutableStateOf(0) }
    val servers = remember(rev) { McpStore.list(context) }
    // 探测结果：serverId -> (ok, tools, err)；正在探测的 id 集合
    var probeState by remember { mutableStateOf(mapOf<String, McpClient.Probe>()) }
    var probing by remember { mutableStateOf(setOf<String>()) }
    var editing by remember { mutableStateOf<McpStore.Server?>(null) }
    var showAdd by remember { mutableStateOf(false) }
    var showImport by remember { mutableStateOf(false) }

    fun probe(id: String, url: String, type: String) {
        probing = probing + id
        scope.launch(Dispatchers.IO) {
            val r = McpClient.probe(McpStore.Server(id, "", url, type, true))
            withContext(Dispatchers.Main) {
                probeState = probeState + (id to r)
                probing = probing - id
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(McpBg)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .clickable { onBack() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回",
                    tint = McpInk, modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.weight(1f))
            Text(
                "导入", fontSize = 14.sp, color = McpInk,
                modifier = Modifier
                    .clickable { showImport = true }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            )
            Text(
                "＋", fontSize = 24.sp, color = McpInk, fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .clickable { showAdd = true }
                    .padding(horizontal = 10.dp)
            )
        }
        Text(
            "MCP",
            fontSize = 40.sp, color = McpInk, fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Spacer(Modifier.height(10.dp))

        LazyColumn(Modifier.fillMaxSize()) {
            items(servers, key = { it.id }) { s ->
                val st = probeState[s.id]
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(McpCard)
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                ) {
                    // 图标（连接中转圈）
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(Color.White),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            if (probing.contains(s.id)) "…" else "M",
                            fontSize = 15.sp, color = McpInk, fontWeight = FontWeight.SemiBold
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                s.name, fontSize = 16.sp, color = McpInk,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(Modifier.width(6.dp))
                            if (s.enabled) {
                                Box(
                                    modifier = Modifier
                                        .size(9.dp)
                                        .clip(CircleShape)
                                        .background(
                                            when {
                                                probing.contains(s.id) -> McpSub
                                                st == null -> McpSub
                                                st.ok -> McpGreen
                                                else -> McpRed
                                            }
                                        )
                                )
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                if (s.type == "sse") "SSE" else "Streamable HTTP",
                                fontSize = 11.sp, color = Color(0xFF3A5F40),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(Color(0xFFCFE8CB))
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                            st?.let { p ->
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    when {
                                        p.ok && p.tools.isEmpty() -> "已连接 · 无工具"
                                        p.ok -> "已连接 · ${p.tools.size} 个工具"
                                        else -> p.err.take(26)
                                    },
                                    fontSize = 10.sp, color = if (p.ok) McpGreen else McpRed, lineHeight = 13.sp
                                )
                            }
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    // 启停 ✕（✓）
                    Text(
                        if (s.enabled) "✓" else "✕",
                        fontSize = 16.sp, color = if (s.enabled) McpGreen else McpSub,
                        modifier = Modifier
                            .clickable {
                                McpStore.setEnabled(context, s.id, !s.enabled)
                                rev++
                            }
                            .padding(6.dp)
                    )
                    // 设置（编辑）
                    Text(
                        "⚙", fontSize = 17.sp, color = McpInk,
                        modifier = Modifier
                            .clickable { editing = s }
                            .padding(6.dp)
                    )
                    // 删除
                    Text(
                        "🗑", fontSize = 15.sp, color = McpRed,
                        modifier = Modifier
                            .clickable {
                                McpStore.delete(context, s.id)
                                rev++
                            }
                            .padding(6.dp)
                    )
                }
            }
            if (servers.isEmpty()) {
                item {
                    Text(
                        "还没有 MCP 服务器。\n右上角 ＋ 新增（填地址选协议），或 导入 粘贴 JSON 配置。",
                        fontSize = 13.sp, color = McpSub, lineHeight = 20.sp,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 60.dp, horizontal = 24.dp)
                    )
                }
            }
            item {
                Spacer(Modifier.height(50.dp))
                Text(
                    "保存后自动连接测试（initialize → tools/list）。\n聊天里调用 MCP 工具在后面的版本。",
                    fontSize = 10.sp, color = McpSub, lineHeight = 15.sp,
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
            }
        }
    }

    // 新增 / 编辑弹窗
    if (showAdd || editing != null) {
        val initial = editing
        McpEditDialog(
            initial = initial,
            onDismiss = { showAdd = false; editing = null },
            onSave = { name, url, type ->
                val s = McpStore.Server(
                    id = initial?.id ?: ("mcp_" + System.currentTimeMillis()),
                    name = name, url = url, type = type,
                    enabled = initial?.enabled ?: true
                )
                McpStore.upsert(context, s)
                showAdd = false
                editing = null
                rev++
                if (s.enabled) probe(s.id, s.url, s.type)
            }
        )
    }

    // 导入弹窗
    if (showImport) {
        ImportDialog(
            onDismiss = { showImport = false },
            onImport = { json ->
                val n = runCatching { McpStore.import(context, json) }.getOrDefault(0)
                showImport = false
                rev++
                n
            }
        )
    }
}

@Composable
private fun McpEditDialog(
    initial: McpStore.Server?,
    onDismiss: () -> Unit,
    onSave: (name: String, url: String, type: String) -> Unit
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var url by remember { mutableStateOf(initial?.url ?: "") }
    var type by remember { mutableStateOf(initial?.type ?: "http") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFFF7F5F1),
        shape = RoundedCornerShape(20.dp),
        title = {
            Text(
                if (initial == null) "新增 MCP 服务器" else "编辑 MCP 服务器",
                fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF26211C)
            )
        },
        text = {
            Column {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("名称", fontSize = 13.sp, color = McpSub) },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = url, onValueChange = { url = it },
                    label = { Text("地址（如 https://…/mcp）", fontSize = 13.sp, color = McpSub) },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                Text("协议", fontSize = 13.sp, color = McpSub)
                Row {
                    listOf("http" to "Streamable HTTP", "sse" to "SSE").forEach { (k, label) ->
                        val sel = type == k
                        Text(
                            label,
                            fontSize = 13.sp,
                            color = if (sel) Color.White else Color(0xFF4E4638),
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (sel) McpBlue else Color.White)
                                .clickable { type = k }
                                .padding(horizontal = 12.dp, vertical = 7.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isNotBlank() && url.startsWith("http")) onSave(name.trim(), url.trim(), type)
            }) { Text("保存并连接", color = McpBlue, fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消", color = McpSub) }
        }
    )
}

@Composable
private fun ImportDialog(
    onDismiss: () -> Unit,
    onImport: (String) -> Int
) {
    var json by remember { mutableStateOf("") }
    var tip by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFFF7F5F1),
        shape = RoundedCornerShape(20.dp),
        title = { Text("导入 MCP 配置", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF26211C)) },
        text = {
            Column {
                Text(
                    "粘贴 JSON（单个对象或数组，字段 name / url / type）",
                    fontSize = 12.sp, color = McpSub, lineHeight = 17.sp
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = json, onValueChange = { json = it },
                    minLines = 4, maxLines = 8,
                    modifier = Modifier.fillMaxWidth()
                )
                if (tip.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(tip, fontSize = 12.sp, color = Color(0xFFB4574E))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val n = onImport(json)
                if (n == 0) tip = "没解析出有效的服务器（要 name + url）"
            }) { Text("导入", color = McpBlue, fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消", color = McpSub) }
        }
    )
}
