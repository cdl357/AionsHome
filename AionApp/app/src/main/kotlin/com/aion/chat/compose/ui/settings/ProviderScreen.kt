package com.aion.chat.compose.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.aion.chat.compose.ui.theme.HomecomingColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 模型提供商（橘瓣式，Yuri 定稿）：
 * 列表（头像圈+名称+启用/N个模型）→ 点卡片进编辑（名称/Key/BaseUrl/启用 + 拉模型列表多选添加 + 测试/删除/保存/设为聊天使用）。
 */
private val PgBg = Color(0xFFF2F3F5)
private val PgCard = Color(0xFFF7F8FA)
private val PgInk = Color(0xFF26211C)
private val PgSub = Color(0xFF8A8378)
private val PgAccent = Color(0xFF7FA6C9)

@Composable
fun ProviderScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val rev = com.aion.chat.compose.data.ProviderStore.rev
    val providers = remember(rev) { com.aion.chat.compose.data.ProviderStore.list(context) }
    val activeId = remember(rev) { com.aion.chat.compose.data.ProviderStore.activeId(context) }
    var editing by remember { mutableStateOf<com.aion.chat.compose.data.ProviderStore.Provider?>(null) }
    var creating by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(Modifier.fillMaxSize().background(PgBg)) {
            Column(Modifier.fillMaxSize()) {
                // ── 顶栏 ──
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp)
                ) {
                    Text("‹", fontSize = 26.sp, color = PgSub,
                        modifier = Modifier
                            .clickable(
                                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                indication = null
                            ) { onClose() }
                            .padding(horizontal = 8.dp))
                    Text("提供商", fontSize = 24.sp, color = PgInk, fontWeight = FontWeight.SemiBold)
                }

                // ── 提供商网格 ──
                if (providers.isEmpty()) {
                    Text(
                        "还没有提供商\n点下面「新增提供商」：填名称、URL、Key，通了拉模型，勾选要用的",
                        fontSize = 13.sp, color = PgSub, textAlign = TextAlign.Center,
                        lineHeight = 22.sp,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 60.dp, horizontal = 30.dp)
                    )
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)
                    ) {
                        providers.forEach { p ->
                            item(key = p.id) {
                                ProviderCard(
                                    name = p.name.ifBlank { "未命名" },
                                    enabled = p.enabled,
                                    modelCount = p.models.size,
                                    isActive = p.id == activeId,
                                    onClick = { editing = p }
                                )
                            }
                        }
                        item(key = "__add") {
                            androidx.compose.foundation.layout.Box(
                                modifier = Modifier
                                    .height(96.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Color.White)
                                    .border(1.dp, PgAccent.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                                    .clickable { creating = true },
                                contentAlignment = Alignment.Center
                            ) {
                                Text("+ 新增提供商", fontSize = 14.sp, color = PgAccent, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
            }
        }
    }

    // ── 编辑 / 新增 ──
    if (creating || editing != null) {
        val isNew = creating
        val base = editing ?: com.aion.chat.compose.data.ProviderStore.Provider(
            id = "p_" + System.currentTimeMillis(), name = "", baseUrl = "", apiKey = "",
            enabled = true, models = emptyList()
        )
        ProviderEditor(
            initial = base,
            isNew = isNew,
            onClose = { creating = false; editing = null },
            onDeleted = { creating = false; editing = null }
        )
    }
}

@Composable
private fun ProviderCard(
    name: String, enabled: Boolean, modelCount: Int, isActive: Boolean, onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White)
            .clickable { onClick() }
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(PgAccent.copy(alpha = 0.28f)),
                contentAlignment = Alignment.Center
            ) {
                Text(name.take(1), fontSize = 17.sp, color = PgInk, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        Text(name, fontSize = 16.sp, color = PgInk, fontWeight = FontWeight.SemiBold, maxLines = 1)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (isActive) Chip("聊天使用", Color(0xFFDCF2C6), Color(0xFF3D4A30))
            Chip(if (enabled) "启用" else "停用", Color(0xFFDFF0DC), Color(0xFF3E7A3A))
            Chip("$modelCount 个模型", Color(0xFFDDEBFA), Color(0xFF3E6A9A))
        }
    }
}

@Composable
private fun Chip(text: String, bg: Color, fg: Color) {
    Text(
        text, fontSize = 11.sp, color = fg,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .padding(horizontal = 8.dp, vertical = 3.dp)
    )
}

// ════════ 编辑 / 新增提供商 ════════

@Composable
private fun ProviderEditor(
    initial: com.aion.chat.compose.data.ProviderStore.Provider,
    isNew: Boolean,
    onClose: () -> Unit,
    onDeleted: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(initial.name) }
    var apiKey by remember { mutableStateOf(initial.apiKey) }
    var baseUrl by remember { mutableStateOf(initial.baseUrl) }
    var enabled by remember { mutableStateOf(initial.enabled) }
    var models by remember { mutableStateOf(initial.models) }
    var isDefault by remember { mutableStateOf(initial.id == com.aion.chat.compose.data.ProviderStore.activeId(context)) }

    var testing by remember { mutableStateOf(false) }
    var testMsg by remember { mutableStateOf("") }
    var showModelPicker by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    fun persist(setAsActive: Boolean) {
        val p = initial.copy(
            name = name.trim(), baseUrl = baseUrl.trim(), apiKey = apiKey.trim(),
            enabled = enabled, models = models
        )
        val all = com.aion.chat.compose.data.ProviderStore.list(context)
            .filter { it.id != p.id } + p
        val activeIdNow = com.aion.chat.compose.data.ProviderStore.activeId(context)
        val activeModelNow = com.aion.chat.compose.data.ProviderStore.activeModelKey(context)
        val keepActive = if (setAsActive) p.id else activeIdNow
        val keepModel = if (setAsActive) {
            models.firstOrNull()?.key ?: ""
        } else if (activeIdNow == p.id) {
            models.firstOrNull { it.key == activeModelNow }?.key ?: models.firstOrNull()?.key ?: ""
        } else activeModelNow
        com.aion.chat.compose.data.ProviderStore.saveAll(context, all, keepActive, keepModel)
    }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(Modifier.fillMaxSize().background(PgBg)) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp)
                ) {
                    Text("‹", fontSize = 26.sp, color = PgSub,
                        modifier = Modifier
                            .clickable(
                                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                indication = null
                            ) { onClose() }
                            .padding(horizontal = 8.dp))
                    Spacer(Modifier.width(4.dp))
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(PgAccent.copy(alpha = 0.28f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text((if (name.isBlank()) "S" else name.take(1)),
                            fontSize = 15.sp, color = PgInk, fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(if (isNew) "新增提供商" else name.ifBlank { "提供商" },
                        fontSize = 20.sp, color = PgInk, fontWeight = FontWeight.SemiBold)
                }

                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.White)
                        .padding(16.dp)
                ) {
                    // 协议分段（定稿视觉：OpenAI / Google / Claude；当前仅 OpenAI 兼容可用）
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(50))
                            .background(PgBg)
                    ) {
                        listOf("OpenAI", "Google", "Claude").forEachIndexed { i, label ->
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(50))
                                    .background(if (i == 0) PgAccent.copy(alpha = 0.25f) else Color.Transparent)
                                    .clickable {
                                        if (i != 0) android.widget.Toast.makeText(
                                            context, "Google / Claude 原生协议在后面的版本", android.widget.Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                    .padding(vertical = 9.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    (if (i == 0) "✓ " else "") + label,
                                    fontSize = 14.sp,
                                    color = if (i == 0) PgInk else PgSub,
                                    fontWeight = if (i == 0) FontWeight.SemiBold else FontWeight.Normal
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("是否启用", fontSize = 14.sp, color = PgInk, modifier = Modifier.weight(1f))
                        Switch(checked = enabled, onCheckedChange = { enabled = it })
                    }
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = name, onValueChange = { name = it },
                        label = { Text("名称") },
                        singleLine = true, modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = apiKey, onValueChange = { apiKey = it },
                        label = { Text("API Key") },
                        singleLine = true, modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = baseUrl, onValueChange = { baseUrl = it },
                        label = { Text("API Base Url（少了 /v1 会自动补）") },
                        singleLine = true, modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "接口路径 /chat/completions 自动拼接；「测试」会真的拉一次模型列表",
                        fontSize = 11.sp, color = PgSub
                    )
                }

                // ── 模型 ──
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.White)
                        .padding(16.dp)
                ) {
                    Text("模型（${models.size} 个）", fontSize = 14.sp, color = PgInk, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(8.dp))
                    models.forEach { m ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
                        ) {
                            Text("· ${m.model}", fontSize = 13.sp, color = PgInk, maxLines = 1,
                                modifier = Modifier.weight(1f))
                            Text(
                                "移除", fontSize = 12.sp, color = HomecomingColors.Danger,
                                modifier = Modifier
                                    .clickable { models = models.filter { it.key != m.key } }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                    if (models.isEmpty()) {
                        Text("还没有模型：点「拉取模型列表」→ 勾选要用的 → 添加所选",
                            fontSize = 12.sp, color = PgSub)
                    }
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = {
                            val b = baseUrl.trim()
                            val k = apiKey.trim()
                            if (b.isBlank() || k.isBlank()) {
                                android.widget.Toast.makeText(context, "先填 API Base Url 和 API Key", android.widget.Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            testing = true
                            scope.launch(Dispatchers.IO) {
                                val (ids, msg) = com.aion.chat.compose.data.ProviderStore.fetchModelIds(b, k)
                                withContext(Dispatchers.Main) {
                                    testing = false
                                    testMsg = msg
                                    if (ids.isNotEmpty()) showModelPicker = true
                                    else android.widget.Toast.makeText(context, "拉取失败：$msg", android.widget.Toast.LENGTH_LONG).show()
                                }
                            }
                        },
                        enabled = !testing
                    ) { Text(if (testing) "拉取中…" else "拉取模型列表") }
                    if (testMsg.isNotBlank()) {
                        Text(testMsg, fontSize = 11.sp, color = PgSub, modifier = Modifier.padding(top = 6.dp))
                    }
                }

                // ── 操作行 ──
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Button(
                        onClick = {
                            if (name.isBlank() || baseUrl.isBlank() || apiKey.isBlank() || models.isEmpty()) {
                                android.widget.Toast.makeText(
                                    context,
                                    when {
                                        models.isEmpty() -> "先拉取并添加至少一个模型"
                                        else -> "名称 / Key / Base Url 都要填"
                                    },
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                                return@Button
                            }
                            persist(setAsActive = false)
                            android.widget.Toast.makeText(context, "已保存", android.widget.Toast.LENGTH_SHORT).show()
                            onClose()
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("保存") }
                    Button(
                        onClick = {
                            if (name.isBlank() || baseUrl.isBlank() || apiKey.isBlank() || models.isEmpty()) {
                                android.widget.Toast.makeText(
                                    context,
                                    when {
                                        models.isEmpty() -> "先拉取并添加至少一个模型"
                                        else -> "名称 / Key / Base Url 都要填"
                                    },
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                                return@Button
                            }
                            persist(setAsActive = true)
                            android.widget.Toast.makeText(context, "已设为聊天使用", android.widget.Toast.LENGTH_SHORT).show()
                            onClose()
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("设为聊天使用") }
                }
                if (!isNew) {
                    Text(
                        if (confirmDelete) "真的删除这个提供商？（不可恢复）" else "删除这个提供商",
                        fontSize = 13.sp, color = HomecomingColors.Danger,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (!confirmDelete) {
                                    confirmDelete = true
                                } else {
                                    com.aion.chat.compose.data.ProviderStore.delete(context, initial.id)
                                    onDeleted()
                                }
                            }
                            .padding(vertical = 10.dp)
                    )
                }
                Spacer(Modifier.height(30.dp))
            }
        }
    }

    // ── 模型多选弹窗 ──
    if (showModelPicker) {
        var fetched by remember(showModelPicker) {
            mutableStateOf<List<String>>(emptyList())
        }
        var picked by remember(showModelPicker) { mutableStateOf(setOf<String>()) }
        LaunchedEffect(showModelPicker) {
            fetched = withContext(Dispatchers.IO) {
                com.aion.chat.compose.data.ProviderStore.fetchModelIds(baseUrl.trim(), apiKey.trim()).first
            }
        }
        Dialog(onDismissRequest = { showModelPicker = false }) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White)
                    .padding(16.dp)
            ) {
                Text("拉到 ${fetched.size} 个模型 · 勾选添加", fontSize = 15.sp, color = PgInk, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(10.dp))
                if (fetched.isEmpty()) {
                    Text("列表是空的——这个 Key 可能没有可用模型", fontSize = 12.sp, color = PgSub)
                } else {
                    LazyColumn(Modifier.fillMaxWidth().height(360.dp)) {
                        fetched.forEach { id ->
                            item(key = id) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            picked = if (id in picked) picked - id else picked + id
                                        }
                                        .padding(vertical = 8.dp)
                                ) {
                                    Text(
                                        if (id in picked) "☑" else "☐",
                                        fontSize = 16.sp, color = PgAccent
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(id, fontSize = 13.sp, color = PgInk, maxLines = 1)
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = { showModelPicker = false }) { Text("取消", color = PgSub) }
                    Spacer(Modifier.width(8.dp))
                    TextButton(
                        enabled = picked.isNotEmpty(),
                        onClick = {
                            val added = picked.map { com.aion.chat.compose.data.ProviderStore.Model(it, it) }
                            models = (models + added).distinctBy { it.key }
                            showModelPicker = false
                        }
                    ) { Text("添加所选（${picked.size}）", color = PgAccent) }
                }
            }
        }
    }
}
