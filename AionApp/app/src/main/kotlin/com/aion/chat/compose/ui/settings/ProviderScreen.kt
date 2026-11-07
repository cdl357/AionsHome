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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.aion.chat.compose.data.ProviderStore
import com.aion.chat.compose.ui.chat.modelSortKey
import com.aion.chat.compose.ui.theme.HomecomingColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 模型提供商（橘瓣 SettingProviderPage/SettingProviderDetailPage 交互 1:1 移植）：
 * ① 列表页：‹ 提供商 +（右上+）+ 搜索 + 两列网格卡（头像圈/⋯菜单/名称/启用·N个模型 chips）
 *    + 添加提供商 = 居中弹窗（预填 名称=OpenAI、Base Url=api.openai.com/v1，取消|添加）
 * ② 详情页：‹ 头像圈 名称 ⇱分享 + 配置|模型 底部双 tab
 *    配置 tab：分段(OpenAI✓/Google/Claude) + 是否启用 + 名称/Key/BaseUrl/API路径 + Response API
 *    + 获取账户余额（可展开）+ 🔗/删除/刷新/保存
 *    模型 tab：模型卡列表（✳圈+模型名+聊天chip+✕移除）+ 添加新模型
 *    → 底部弹层「可用模型 全选(N)」+ 筛选框 + 每行 ➕（橘瓣同款）
 * 所有变更即时落库（ProviderStore.upsert）。
 */
private val PgBg = Color(0xFFF2F3F5)
private val PgCard = Color(0xFFF7F8FA)
private val PgInk = Color(0xFF26211C)
private val PgSub = Color(0xFF8A8378)
private val PgAccent = Color(0xFF7FA6C9)
private val PgAccentBg = Color(0xFFBFD6EC)
private val PgAccentSoft = Color(0xFFD6E5F5)
private val ChipGreenBg = Color(0xFFDFF0DC)
private val ChipGreenFg = Color(0xFF3E7A3A)
private val ChipBlueBg = Color(0xFFDDEBFA)
private val ChipBlueFg = Color(0xFF3E6A9A)

// ────────────────────────── 列表页 ──────────────────────────

@Composable
fun ProviderScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    val rev = ProviderStore.rev
    val providers = remember(rev) { ProviderStore.list(context) }
    val activeId = remember(rev) { ProviderStore.activeId(context) }
    var search by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<ProviderStore.Provider?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }
    var menuFor by remember { mutableStateOf<ProviderStore.Provider?>(null) }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(Modifier.fillMaxSize().background(PgBg)) {
            Column(Modifier.fillMaxSize()) {
                // 顶栏：‹ 提供商 +（右上+）
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
                    Spacer(Modifier.weight(1f))
                    Text("+", fontSize = 26.sp, color = PgInk,
                        modifier = Modifier
                            .clickable { showAddDialog = true }
                            .padding(horizontal = 6.dp))
                }

                // 搜索（圆角 50）
                OutlinedTextField(
                    value = search, onValueChange = { search = it },
                    placeholder = { Text("搜索提供商", fontSize = 14.sp, color = PgSub) },
                    singleLine = true,
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                )
                Spacer(Modifier.height(14.dp))

                val shown = providers.filter {
                    search.isBlank() || it.name.contains(search, true) ||
                        it.models.any { m -> m.model.contains(search, true) }
                }
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = 16.dp, end = 16.dp, bottom = 24.dp
                    ),
                    modifier = Modifier.fillMaxSize()
                ) {
                    shown.forEach { p ->
                        item(key = p.id) {
                            ProviderCardView(
                                name = p.name.ifBlank { "未命名" },
                                enabled = p.enabled,
                                modelCount = p.models.size,
                                isActive = p.id == activeId,
                                onClick = { editing = p },
                                onMenu = { menuFor = p }
                            )
                        }
                    }
                }
            }
        }
    }

    // ⋯ 菜单
    menuFor?.let { p ->
        DropdownMenu(expanded = true, onDismissRequest = { menuFor = null }) {
            DropdownMenuItem(text = { Text("编辑") }, onClick = { menuFor = null; editing = p })
            DropdownMenuItem(
                text = { Text(if (p.id == activeId) "聊天使用中 ✓" else "设为聊天使用") },
                onClick = {
                    ProviderStore.setActive(context, p.id, p.models.firstOrNull()?.key ?: "")
                    menuFor = null
                }
            )
            DropdownMenuItem(
                text = { Text("删除", color = HomecomingColors.Danger) },
                onClick = { ProviderStore.delete(context, p.id); menuFor = null }
            )
        }
    }

    // 添加提供商（居中弹窗，预填 橘瓣同款：名称=OpenAI、Base Url=api.openai.com/v1）
    if (showAddDialog) {
        AddProviderDialog(
            onDismiss = { showAddDialog = false },
            onAdd = { p ->
                ProviderStore.upsert(context, p)
                showAddDialog = false
            }
        )
    }

    // 详情页
    editing?.let { p ->
        ProviderDetailScreen(provider = p, onClose = { editing = null })
    }
}

@Composable
private fun ProviderCardView(
    name: String, enabled: Boolean, modelCount: Int, isActive: Boolean,
    onClick: () -> Unit, onMenu: () -> Unit
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
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(PgAccentBg),
                contentAlignment = Alignment.Center
            ) {
                Text(name.take(1), fontSize = 18.sp, color = PgInk, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.weight(1f))
            Box {
                Text("⋯", fontSize = 18.sp, color = PgInk,
                    modifier = Modifier.clickable { onMenu() }.padding(4.dp))
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(name, fontSize = 17.sp, color = PgInk, fontWeight = FontWeight.SemiBold, maxLines = 1)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (isActive) Chip("聊天使用", ChipGreenBg, ChipGreenFg)
            Chip(if (enabled) "启用" else "停用", ChipGreenBg, ChipGreenFg)
            Chip("$modelCount 个模型", ChipBlueBg, ChipBlueFg)
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

// ────────────────────────── 添加提供商（居中弹窗，预填橘瓣同款） ──────────────────────────

@Composable
private fun AddProviderDialog(
    onDismiss: () -> Unit,
    onAdd: (ProviderStore.Provider) -> Unit
) {
    val ctxAdd = LocalContext.current
    var name by remember { mutableStateOf("sean") }
    var apiKey by remember { mutableStateOf("") }
    // 预填她的真实中转（重装重加不再"URL 变了"）；初始全选——粘贴=整体替换，不会追加拼坏
    val herRelay = com.aion.chat.compose.data.SupabaseClient.RELAY_URL
        .removeSuffix("/supabase").replace(":8783", ":41337") + "/v1"
    var baseUrl by remember {
        mutableStateOf(androidx.compose.ui.text.input.TextFieldValue(
            herRelay,
            selection = androidx.compose.ui.text.TextRange(0, herRelay.length)
        ))
    }
    var apiPath by remember { mutableStateOf("/chat/completions") }
    var useResponseApi by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(PgCard)
                .padding(16.dp)
        ) {
            Text("添加提供商", fontSize = 16.sp, color = PgInk, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(12.dp))
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
                            .background(if (i == 0) PgAccentSoft else Color.Transparent)
                            .clickable {
                                if (i != 0) android.widget.Toast.makeText(
                                    ctxAdd, "Google / Claude 原生协议在后面的版本",
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                            }
                            .padding(vertical = 9.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            (if (i == 0) "✓ " else "") + label,
                            fontSize = 13.sp,
                            color = if (i == 0) PgInk else PgSub,
                            fontWeight = if (i == 0) FontWeight.SemiBold else FontWeight.Normal
                        )
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("是否启用", fontSize = 13.sp, color = PgInk, modifier = Modifier.weight(1f))
                Checkbox(checked = true, onCheckedChange = null,
                    colors = CheckboxDefaults.colors(checkedColor = PgAccent))
            }
            OutlinedTextField(
                value = name, onValueChange = { name = it },
                label = { Text("名称") },
                singleLine = true, modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = apiKey, onValueChange = { apiKey = it },
                label = { Text("API Key") },
                singleLine = true, modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = baseUrl, onValueChange = { baseUrl = it },
                label = { Text("API Base Url") },
                isError = baseUrl.text.isNotBlank() &&
                    !baseUrl.text.trim().startsWith("http://") &&
                    !baseUrl.text.trim().startsWith("https://"),
                singleLine = true, modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            // 橘瓣同款：勾了 Response API 就没有 API 路径一栏
            if (!useResponseApi) {
                OutlinedTextField(
                    value = apiPath, onValueChange = { apiPath = it },
                    label = { Text("API 路径") },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
            }
            Spacer(Modifier.height(6.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Response API", fontSize = 13.sp, color = PgInk, modifier = Modifier.weight(1f))
                Checkbox(checked = useResponseApi, onCheckedChange = { useResponseApi = it },
                    colors = CheckboxDefaults.colors(checkedColor = PgAccent))
            }
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = onDismiss) { Text("取消", color = PgSub) }
                Spacer(Modifier.width(8.dp))
                TextButton(
                    onClick = {
                        val u = baseUrl.text.trim()
                        if (!u.startsWith("http://") && !u.startsWith("https://")) {
                            android.widget.Toast.makeText(
                                ctxAdd,
                                "Base Url 要以 http:// 或 https:// 开头（如 https://api.openai.com/v1）",
                                android.widget.Toast.LENGTH_LONG
                            ).show()
                            return@TextButton
                        }
                        onAdd(
                            ProviderStore.Provider(
                                id = "p_" + System.currentTimeMillis(),
                                name = name.trim().ifBlank { "sean" },
                                baseUrl = u, apiKey = apiKey.trim(),
                                enabled = true, models = emptyList(),
                                apiPath = apiPath.trim().ifBlank { "/chat/completions" },
                                useResponseApi = useResponseApi
                            )
                        )
                    }
                ) { Text("添加", color = PgAccent, fontWeight = FontWeight.SemiBold) }
            }
        }
    }
}

// ────────────────────────── 详情页（配置 | 模型） ──────────────────────────

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun ProviderDetailScreen(
    provider: ProviderStore.Provider,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(provider.name) }
    var apiKey by remember { mutableStateOf(provider.apiKey) }
    var baseUrl by remember { mutableStateOf(provider.baseUrl) }
    var enabled by remember { mutableStateOf(provider.enabled) }
    var models by remember { mutableStateOf(provider.models) }
    var apiPath by remember { mutableStateOf(provider.apiPath) }
    var useResponseApi by remember { mutableStateOf(provider.useResponseApi) }
    var fetchBalance by remember { mutableStateOf(provider.fetchBalance) }
    var balanceOpen by remember { mutableStateOf(false) }
    var tab by remember { mutableStateOf(0) }   // 0=配置 1=模型

    var testing by remember { mutableStateOf(false) }
    var testMsg by remember { mutableStateOf("") }
    var showModelSheet by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    fun persistFields(): Boolean {
        if (name.isBlank() || baseUrl.isBlank() || apiKey.isBlank()) {
            android.widget.Toast.makeText(
                context,
                when {
                    name.isBlank() -> "名称必填"
                    baseUrl.isBlank() -> "API Base Url 必填"
                    else -> "API Key 必填"
                },
                android.widget.Toast.LENGTH_SHORT
            ).show()
            return false
        }
        val p = provider.copy(
            name = name.trim(), baseUrl = baseUrl.trim(), apiKey = apiKey.trim(),
            enabled = enabled, models = models,
            apiPath = apiPath.trim().ifBlank { "/chat/completions" },
            useResponseApi = useResponseApi, fetchBalance = fetchBalance
        )
        ProviderStore.upsert(context, p)
        return true
    }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(Modifier.fillMaxSize().background(PgBg)) {
            Column(Modifier.fillMaxSize()) {
                // 顶栏：‹ + 头像圈 + 名称（实时） + ⇱ 分享
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp)
                ) {
                    Text("‹", fontSize = 26.sp, color = PgSub,
                        modifier = Modifier
                            .clickable(
                                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                indication = null
                            ) { onClose() }
                            .padding(horizontal = 6.dp))
                    Spacer(Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(PgAccentBg),
                        contentAlignment = Alignment.Center
                    ) {
                        Text((if (name.isBlank()) "S" else name.take(1)),
                            fontSize = 15.sp, color = PgInk, fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(if (name.isBlank()) "新增提供商" else name,
                        fontSize = 19.sp, color = PgInk, fontWeight = FontWeight.SemiBold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f))
                    Text("⇱", fontSize = 19.sp, color = PgInk,
                        modifier = Modifier
                            .clickable {
                                runCatching {
                                    val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                                        as android.content.ClipboardManager
                                    cm.setPrimaryClip(
                                        android.content.ClipData.newPlainText(
                                            "provider",
                                            "name=$name\nbase_url=$baseUrl\napi_path=$apiPath"
                                        )
                                    )
                                    android.widget.Toast.makeText(context, "已复制配置", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            }
                            .padding(4.dp))
                }

                // 内容区（底部双 tab 切换）
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    if (tab == 0) {
                        // ── 配置 tab ──
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color.White)
                                .padding(16.dp)
                        ) {
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
                                            .background(if (i == 0) PgAccentSoft else Color.Transparent)
                                            .clickable {
                                                if (i != 0) android.widget.Toast.makeText(
                                                    context, "Google / Claude 原生协议在后面的版本",
                                                    android.widget.Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                            .padding(vertical = 10.dp),
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
                                singleLine = true,
                                trailingIcon = {
                                    if (apiKey.isNotEmpty()) Text("✕",
                                        fontSize = 13.sp, color = PgSub,
                                        modifier = Modifier.clickable { apiKey = "" }.padding(4.dp))
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(10.dp))
                            OutlinedTextField(
                                value = baseUrl, onValueChange = { baseUrl = it },
                                label = { Text("API Base Url") },
                                isError = baseUrl.isNotBlank() &&
                                    !baseUrl.trim().startsWith("http://") &&
                                    !baseUrl.trim().startsWith("https://"),
                                singleLine = true,
                                trailingIcon = {
                                    if (baseUrl.isNotEmpty()) Text("✕",
                                        fontSize = 13.sp, color = PgSub,
                                        modifier = Modifier.clickable { baseUrl = "" }.padding(4.dp))
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(10.dp))
                            // 橘瓣同款：勾了 Response API 就没有 API 路径一栏
                            if (!useResponseApi) {
                                OutlinedTextField(
                                    value = apiPath, onValueChange = { apiPath = it },
                                    label = { Text("API 路径") },
                                    singleLine = true, modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    "拼接规则：Base Url（少了 /v1 自动补）+ API 路径",
                                    fontSize = 11.sp, color = PgSub
                                )
                                Spacer(Modifier.height(12.dp))
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Response API", fontSize = 14.sp, color = PgInk, modifier = Modifier.weight(1f))
                                Checkbox(
                                    checked = useResponseApi,
                                    onCheckedChange = {
                                        useResponseApi = it
                                        if (it) android.widget.Toast.makeText(
                                            context, "Response API 在后面的版本，先存着",
                                            android.widget.Toast.LENGTH_SHORT
                                        ).show()
                                    },
                                    colors = CheckboxDefaults.colors(checkedColor = PgAccent)
                                )
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { balanceOpen = !balanceOpen }
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text("获取账户余额", fontSize = 14.sp, color = PgInk)
                                    if (balanceOpen) {
                                        Text(
                                            "中转站余额查询（new-api 系）在后面的版本；勾选后保存记住偏好",
                                            fontSize = 11.sp, color = PgSub
                                        )
                                    }
                                }
                                Text(if (balanceOpen) "⌃" else "⌄", fontSize = 14.sp, color = PgSub)
                                Checkbox(
                                    checked = fetchBalance,
                                    onCheckedChange = { fetchBalance = it },
                                    colors = CheckboxDefaults.colors(checkedColor = PgAccent)
                                )
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        // 底部操作行：🔗 / 删除 / 刷新 / 保存
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
                        ) {
                            Text("🔗", fontSize = 18.sp,
                                modifier = Modifier.clickable {
                                    runCatching {
                                        val u = baseUrl.trim()
                                        if (u.isNotBlank()) {
                                            context.startActivity(
                                                android.content.Intent(
                                                    android.content.Intent.ACTION_VIEW,
                                                    android.net.Uri.parse(u)
                                                )
                                            )
                                        }
                                    }
                                })
                            Text(
                                if (confirmDelete) "确认删除？" else "删除",
                                fontSize = 14.sp, color = HomecomingColors.Danger, fontWeight = FontWeight.Medium,
                                modifier = Modifier.clickable {
                                    if (!confirmDelete) {
                                        confirmDelete = true
                                    } else {
                                        ProviderStore.delete(context, provider.id)
                                        onClose()
                                    }
                                }
                            )
                            Text(
                                if (testing) "测试中" else "刷新",
                                fontSize = 14.sp, color = PgAccent, fontWeight = FontWeight.Medium,
                                modifier = Modifier.clickable {
                                    if (testing) return@clickable
                                    testing = true; testMsg = ""
                                    scope.launch(Dispatchers.IO) {
                                        val (ids, msg) = ProviderStore.fetchModelIds(baseUrl.trim(), apiKey.trim())
                                        withContext(Dispatchers.Main) {
                                            testing = false
                                            testMsg = msg
                                            if (ids.isNotEmpty()) {
                                                val added = ids.map { ProviderStore.Model(it, it) }
                                                models = (models + added).distinctBy { it.key }
                                                ProviderStore.upsert(context, provider.copy(models = models))
                                                android.widget.Toast.makeText(
                                                    context,
                                                    "拉到 ${ids.size} 个模型，已并入（切「模型」tab 查看/移除）",
                                                    android.widget.Toast.LENGTH_LONG
                                                ).show()
                                            }
                                        }
                                    }
                                }
                            )
                            Button(
                                enabled = !testing,
                                onClick = {
                                    if (persistFields()) android.widget.Toast.makeText(context, "已保存", android.widget.Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.width(110.dp)
                            ) { Text(if (testing) "测试中…" else "保存", color = Color(0xFF2B3440)) }
                        }
                        if (testMsg.isNotBlank()) {
                            Text(testMsg, fontSize = 11.sp, color = PgSub,
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
                        }
                    } else {
                        // ── 模型 tab ──
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp)
                        ) {
                            models.sortedBy { modelSortKey(it.model) }.forEach { m ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(Color.White)
                                        .padding(horizontal = 12.dp, vertical = 10.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(PgAccentBg),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("✳", fontSize = 15.sp, color = PgInk)
                                    }
                                    Spacer(Modifier.width(10.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(m.model, fontSize = 14.sp, color = PgInk, maxLines = 1)
                                        Spacer(Modifier.height(3.dp))
                                        Chip("聊天", ChipBlueBg, ChipBlueFg)
                                    }
                                    Text("✕", fontSize = 15.sp, color = PgSub,
                                        modifier = Modifier
                                            .clickable {
                                                models = models.filter { it.key != m.key }
                                                ProviderStore.upsert(context, provider.copy(models = models))
                                            }
                                            .padding(6.dp))
                                }
                                Spacer(Modifier.height(8.dp))
                            }
                            if (models.isEmpty()) {
                                Text(
                                    "暂无模型\n点击下方按钮添加模型",
                                    fontSize = 12.sp, color = PgSub, textAlign = TextAlign.Center,
                                    lineHeight = 20.sp,
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 30.dp)
                                )
                            }
                            Spacer(Modifier.height(10.dp))
                            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(50))
                                        .background(PgAccentBg)
                                        .clickable { showModelSheet = true }
                                        .padding(horizontal = 18.dp, vertical = 10.dp)
                                ) {
                                    Text("🧰", fontSize = 14.sp)
                                    Spacer(Modifier.width(6.dp))
                                    Text("+ 添加新模型", fontSize = 14.sp, color = PgInk, fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                    }
                }

                // ── 底部双 tab：配置 | 模型 ──
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFEDEEF0))
                        .padding(vertical = 6.dp)
                ) {
                    listOf("配置" to 0, "模型" to 1).forEach { (label, idx) ->
                        val sel = tab == idx
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(50))
                                .background(if (sel) PgAccentSoft else Color.Transparent)
                                .clickable { tab = idx }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                label, fontSize = 13.sp,
                                color = if (sel) PgInk else PgSub,
                                fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal
                            )
                        }
                    }
                }
            }
        }
    }

    // ── 添加新模型：底部弹层（橘瓣同款：可用模型 全选(N) + 筛选 + 每行 ➕） ──
    if (showModelSheet) {
        var fetched by remember(showModelSheet) { mutableStateOf<List<String>>(emptyList()) }
        var fetchErr by remember(showModelSheet) { mutableStateOf("") }
        var fetchTick by remember(showModelSheet) { mutableStateOf(0) }
        var filter by remember(showModelSheet) { mutableStateOf("") }
        LaunchedEffect(showModelSheet, fetchTick) {
            val (list, msg) = withContext(Dispatchers.IO) {
                ProviderStore.fetchModelIds(baseUrl.trim(), apiKey.trim())
            }
            fetched = list.sortedBy { modelSortKey(it) }
            fetchErr = if (list.isEmpty()) msg else ""
        }
        ModalBottomSheet(
            onDismissRequest = { showModelSheet = false },
            containerColor = Color.White
        ) {
            Column(Modifier.fillMaxWidth().imePadding()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                ) {
                    Text("可用模型", fontSize = 15.sp, color = PgInk, fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f))
                    Text(
                        "全选(${fetched.size})",
                        fontSize = 13.sp, color = PgAccent,
                        modifier = Modifier.clickable {
                            val added = fetched.map { ProviderStore.Model(it, it) }
                            models = (models + added).distinctBy { it.key }
                            ProviderStore.upsert(context, provider.copy(models = models))
                        }
                    )
                }
                Spacer(Modifier.height(8.dp))
                if (fetchErr.isNotBlank() && fetched.isEmpty()) {
                    Text(
                        "拉取失败：$fetchErr",
                        fontSize = 12.sp, color = HomecomingColors.Danger,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    if (listOf("api.openai.com", "api.anthropic.com", "generativelanguage.googleapis.com")
                            .any { fetchErr.contains(it) }
                    ) {
                        Text(
                            "这是官方域名，国内直连不通。去 配置 页把 Base Url 换成你的中转站地址（保存后回来重新拉取）",
                            fontSize = 12.sp, color = Color(0xFF8A6D3B), lineHeight = 17.sp,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                        )
                    }
                    Text(
                        "重试", fontSize = 14.sp, color = PgAccent, fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { fetchTick++ }
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                } else {
                    LazyColumn(
                        Modifier.fillMaxWidth().heightIn(max = 420.dp)
                    ) {
                        val addedKeys = models.map { it.key }.toSet()
                        val shown = fetched.filter { filter.isBlank() || it.contains(filter, true) }
                        items(shown) { id ->
                            val already = id in addedKeys
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(26.dp)
                                        .clip(CircleShape)
                                        .background(PgAccentBg),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("✳", fontSize = 12.sp, color = PgInk)
                                }
                                Spacer(Modifier.width(10.dp))
                                Text(id, fontSize = 13.sp, color = PgInk, maxLines = 1,
                                    modifier = Modifier.weight(1f))
                                Text(
                                    if (already) "✓" else "＋",
                                    fontSize = 16.sp,
                                    color = if (already) Color(0xFF3E7A3A) else PgAccent,
                                    modifier = Modifier
                                        .clickable {
                                            if (!already) {
                                                val m = ProviderStore.Model(id, id)
                                                models = models + m
                                                ProviderStore.upsert(
                                                    context,
                                                    provider.copy(models = models)
                                                )
                                            }
                                        }
                                        .padding(6.dp)
                                )
                            }
                        }
                    }
                    OutlinedTextField(
                        value = filter, onValueChange = { filter = it },
                        placeholder = { Text("输入模型名称筛选", fontSize = 13.sp, color = PgSub) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
                Spacer(Modifier.height(10.dp))
            }
        }
    }
}
