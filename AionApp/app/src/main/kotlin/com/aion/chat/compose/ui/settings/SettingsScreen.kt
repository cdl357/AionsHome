package com.aion.chat.compose.ui.settings

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
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
import com.aion.chat.compose.data.HomecomingData
import com.aion.chat.compose.data.HomecomingRouteConfig
import com.aion.chat.compose.data.SettingsBg
import com.aion.chat.compose.ui.home.FrostCard
import com.aion.chat.compose.ui.settings.ProviderScreen
import com.aion.chat.compose.data.AppCrashLog
import com.aion.chat.compose.data.HomecomingMcpStore
import com.aion.chat.compose.ui.theme.HomecomingColors
import com.aion.chat.compose.ui.theme.HomecomingThemeState
import kotlinx.coroutines.launch

/** 设置：背景 / 主题 / 云线路 / MCP / 备份 / 网易云登录。 */
@Composable
fun SettingsScreen(onOpenHerState: () -> Unit = {}, onOpenStyleHub: () -> Unit = {}, onOpenRequestLog: () -> Unit = {}, onOpenMcp: () -> Unit = {}, onOpenStats: () -> Unit = {}, onOpenFavorites: () -> Unit = {}) {
    val context = LocalContext.current

    val routeLabel = remember { mutableStateOf("") }
    val routeBaseUrl = remember { mutableStateOf("") }
    val routeApiKey = remember { mutableStateOf("") }
    val routeModel = remember { mutableStateOf("") }
    val routeSaved = remember { mutableStateOf(false) }
    val showProviderScreen = remember { mutableStateOf(false) }
    // Sean 的画笔（生图中转站）：地址/密钥/模型，停笔防抖后落库并刷新他的能力清单
    val genBase = remember { mutableStateOf(com.aion.chat.compose.data.SeanImageGenStore.config(context).first) }
    val genKey = remember { mutableStateOf(com.aion.chat.compose.data.SeanImageGenStore.config(context).second) }
    val genModel = remember { mutableStateOf(com.aion.chat.compose.data.SeanImageGenStore.config(context).third) }
    LaunchedEffect(genBase.value, genKey.value, genModel.value) {
        kotlinx.coroutines.delay(800)
        com.aion.chat.compose.data.SeanImageGenStore.saveConfig(context, genBase.value, genKey.value, genModel.value)
    }
    // 800ms 防抖期间退出页面也会丢配置：离开时无条件落一次（读的是最新值）
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose {
            com.aion.chat.compose.data.SeanImageGenStore.saveConfig(context, genBase.value, genKey.value, genModel.value)
        }
    }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val restoreLauncher = rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                val ok = runCatching {
                    val text = context.contentResolver.openInputStream(uri)?.use {
                        it.readBytes().toString(Charsets.UTF_8)
                    }
                    val root = org.json.JSONObject(text ?: throw IllegalStateException("空文件"))
                    root.getJSONArray("chat")  // 校验是线路备份
                    // 备份不含 Key：从现存线路把 api_key 带回来（同 route_id 优先）
                    val existing = runCatching { HomecomingRouteConfig.load(context) }.getOrNull()
                    val chats = root.getJSONArray("chat")
                    for (i in 0 until chats.length()) {
                        val c = chats.getJSONObject(i)
                        if (c.optString("api_key").isBlank() && existing != null) {
                            val exArr = existing.optJSONArray("chat") ?: continue
                            for (j in 0 until exArr.length()) {
                                val ex = exArr.getJSONObject(j)
                                if (ex.optString("route_id") == c.optString("route_id") ||
                                    ex.optString("base_url") == c.optString("base_url")) {
                                    c.put("api_key", ex.optString("api_key"))
                                }
                            }
                        }
                    }
                    com.aion.chat.compose.data.HomecomingRouteConfig.save(context, root)
                }.getOrDefault(false)
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    if (ok) {
                        routeSaved.value = true
                        Toast.makeText(context, "线路已恢复，聊天页即可用", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(context, "这不是线路备份文件", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }
    val routeTest = remember { mutableStateOf("") }
    // 模型列表：填好 URL+Key 拉一次，自己挑（不用猜模型名）
    val routeModels = remember { mutableStateListOf<String>() }
    val showModelsDialog = remember { mutableStateOf(false) }
    val modelsFetching = remember { mutableStateOf(false) }

    fun saveRoute() {
        val baseUrl = routeBaseUrl.value.trim()
        val apiKey = routeApiKey.value.trim()
        val model = routeModel.value.trim()
        if (baseUrl.isBlank() || apiKey.isBlank() || model.isBlank()) {
            Toast.makeText(context, "Base URL / API Key / 模型都要填", Toast.LENGTH_SHORT).show()
            return
        }
        val root = com.aion.chat.compose.data.HomecomingRouteConfig.buildRoot(
            routeLabel.value.trim(), baseUrl, apiKey, model
        )
        if (com.aion.chat.compose.data.HomecomingRouteConfig.save(context, root)) {
            routeSaved.value = true
            val where = backupRouteToDownloads(context)
            Toast.makeText(
                context,
                "云线路已保存" + (where?.let { "，并备份到 $it" } ?: ""),
                Toast.LENGTH_LONG
            ).show()
        } else {
            Toast.makeText(context, "保存失败", Toast.LENGTH_SHORT).show()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(18.dp)
    ) {
        Text("设置", fontSize = 22.sp, color = HomecomingColors.Ink)
        Text("云线路 / MCP / 备份都在这；背景和主题在样式中心", fontSize = 12.sp, color = HomecomingColors.InkSoft)
        Spacer(Modifier.height(14.dp))

        // 主题 / 主页背景图已搬进 样式中心（0.3.112）
        // ── 样式中心（从更多页收进来：聊天长什么样在这里调） ──
        FrostCard {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenStyleHub() }
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("样式中心", fontSize = 15.sp, color = HomecomingColors.Ink)
                    Text("主题 · 背景图 · 气泡透明度 · 字体 · 气泡背景图，都在这", fontSize = 11.sp, color = HomecomingColors.InkSoft)
                }
                Text("打开 ›", fontSize = 12.sp, color = HomecomingColors.Accent)
            }
        }

        // ── 告诉他我在干什么（从「更多」收进设置；说明一行就够） ──
        val herOn = remember { mutableStateOf(com.aion.chat.compose.data.HerStateReporter.isEnabled(context)) }
        FrostCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("告诉他我在干什么", fontSize = 15.sp, color = HomecomingColors.Ink)
                    Text("每半小时报一次电量·区县·屏幕时长·在听的歌，不记位置明细", fontSize = 11.sp, color = HomecomingColors.InkSoft)
                }
                androidx.compose.material3.Switch(
                    checked = herOn.value,
                    onCheckedChange = { on ->
                        herOn.value = on
                        com.aion.chat.compose.data.HerStateReporter.setEnabled(context, on)
                        if (on) scope.launch {
                            val ok = com.aion.chat.compose.data.HerStateReporter.collectAndReport(context, manual = true)
                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                Toast.makeText(context, if (ok) "已打开，这份他收到了" else "已打开（网络稍后补报）", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                )
            }
            Row(modifier = Modifier.padding(top = 4.dp)) {
                if (!com.aion.chat.compose.data.HerStateReporter.hasUsageAccess(context)) {
                    Text(
                        "开「使用情况访问」»", fontSize = 12.sp, color = HomecomingColors.Accent,
                        modifier = Modifier
                            .clickable {
                                runCatching {
                                    context.startActivity(android.content.Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS))
                                }
                            }
                            .padding(end = 16.dp, top = 4.dp, bottom = 4.dp)
                    )
                }
                Text(
                    "看看我给了什么 »", fontSize = 12.sp, color = HomecomingColors.Accent,
                    modifier = Modifier
                        .clickable { onOpenHerState() }
                        .padding(end = 16.dp, top = 4.dp, bottom = 4.dp)
                )
                Text(
                    "现在报一次", fontSize = 12.sp, color = HomecomingColors.Accent,
                    modifier = Modifier
                        .clickable {
                            scope.launch {
                                val ok = com.aion.chat.compose.data.HerStateReporter.collectAndReport(context, manual = true)
                                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                    Toast.makeText(context, if (ok) "报好了" else "网络不通，稍后再试", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                        .padding(top = 4.dp, bottom = 4.dp)
                )
            }
        }

        FrostCard {
            Text("模型提供商", fontSize = 15.sp, color = HomecomingColors.Ink)
            Text(
                "橘瓣式：新增提供商（名称 / URL / Key）→ 通了拉模型列表 → 勾选要用的 → 设为聊天使用",
                fontSize = 12.sp, color = HomecomingColors.InkSoft
            )
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = { showProviderScreen.value = true },
                    modifier = Modifier.weight(1f)
                ) { Text("管理提供商") }
            }
            Text(
                "聊天页顶栏会显示当前状态（在线 / 未配置提供商 / 缺 Key）",
                fontSize = 11.sp, color = HomecomingColors.InkSoft,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        // ── 提供商管理全屏页 ──
        if (showProviderScreen.value) {
            com.aion.chat.compose.ui.settings.ProviderScreen(onClose = { showProviderScreen.value = false })
        }

        // ── MCP 服务器管理：先把配置攒好，聊天引擎的工具接线下一阶段开放 ──
        val mcps = remember {
            mutableStateListOf<HomecomingMcpStore.McpServer>().apply { addAll(HomecomingMcpStore.list(context)) }
        }
        fun reloadMcp() { mcps.clear(); mcps.addAll(HomecomingMcpStore.list(context)) }
        val mcpEditing = remember { mutableStateOf<HomecomingMcpStore.McpServer?>(null) }
        val showMcpDialog = remember { mutableStateOf(false) }

        FrostCard {
            Text("Sean 的画笔（生图中转站）", fontSize = 15.sp, color = HomecomingColors.Ink)
            Text(
                "OpenAI 兼容生图接口。配好后他会画画：回复里嵌 <genimg>画面描述</genimg>，图直接出现在气泡下面",
                fontSize = 12.sp, color = HomecomingColors.InkSoft
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = genBase.value, onValueChange = { genBase.value = it },
                label = { Text("生图 Base URL（如 https://api.xx.com/v1）") },
                modifier = Modifier.fillMaxWidth(), singleLine = true
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = genKey.value, onValueChange = { genKey.value = it },
                label = { Text("API Key") },
                modifier = Modifier.fillMaxWidth(), singleLine = true
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = genModel.value, onValueChange = { genModel.value = it },
                label = { Text("模型（如 dall-e-3 / gemini-2.5-flash-image）") },
                modifier = Modifier.fillMaxWidth(), singleLine = true
            )
            Text(
                if (com.aion.chat.compose.data.SeanImageGenStore.configured(context)) "已接好——他随时能画，第一次用要等十几秒出图"
                else "还没配好：三项填全并回聊天页，他才会知道这支画笔（免得空口许诺）",
                fontSize = 11.sp,
                color = if (com.aion.chat.compose.data.SeanImageGenStore.configured(context)) HomecomingColors.Ok else HomecomingColors.InkSoft
            )
        }

        FrostCard {
            Text("MCP 服务器", fontSize = 15.sp, color = HomecomingColors.Ink)
            Text(
                "要接的 MCP 都配在这里；聊天引擎会用它们当工具（接线下一阶段开放）",
                fontSize = 12.sp, color = HomecomingColors.InkSoft
            )
            Spacer(Modifier.height(8.dp))
            if (mcps.isEmpty()) {
                Text("还没有配置，点下面「添加」", fontSize = 12.sp, color = HomecomingColors.InkSoft)
            }
            mcps.forEach { m ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { mcpEditing.value = m; showMcpDialog.value = true }
                    ) {
                        Text(
                            m.name + if (m.enabled) "" else "（已停用）",
                            fontSize = 14.sp, color = HomecomingColors.Ink
                        )
                        Text(
                            if (m.type == "http") m.url else (m.command + " " + m.args),
                            fontSize = 11.sp, color = HomecomingColors.InkSoft,
                            maxLines = 1
                        )
                    }
                    Text(
                        if (m.enabled) "停用" else "启用",
                        fontSize = 12.sp, color = HomecomingColors.Accent,
                        modifier = Modifier
                            .padding(start = 10.dp)
                            .clickable { HomecomingMcpStore.toggle(context, m.id); reloadMcp() }
                    )
                }
            }
            TextButton(onClick = { mcpEditing.value = null; showMcpDialog.value = true }) {
                Text("+ 添加 MCP 服务器", color = HomecomingColors.Accent)
            }
            if (mcps.isNotEmpty()) {
                TextButton(onClick = {
                    runCatching {
                        val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                            as android.content.ClipboardManager
                        cm.setPrimaryClip(
                            android.content.ClipData.newPlainText(
                                "mcp", HomecomingMcpStore.exportJson(mcps.toList())
                            )
                        )
                        Toast.makeText(context, "标准 MCP 配置已复制，可贴到桌面端/后端", Toast.LENGTH_SHORT).show()
                    }
                }) { Text("复制为标准配置 JSON", color = HomecomingColors.InkSoft, fontSize = 12.sp) }
            }
        }

        if (showMcpDialog.value) {
            val editing = mcpEditing.value
            val dlgName = remember(editing) { mutableStateOf(editing?.name ?: "") }
            val dlgHttp = remember(editing) { mutableStateOf(editing?.type != "stdio") }
            val dlgUrl = remember(editing) { mutableStateOf(editing?.url ?: "") }
            val dlgCommand = remember(editing) { mutableStateOf(editing?.command ?: "") }
            val dlgArgs = remember(editing) { mutableStateOf(editing?.args ?: "") }
            AlertDialog(
                onDismissRequest = { showMcpDialog.value = false },
                title = {
                    Text(
                        if (editing == null) "添加 MCP 服务器" else "编辑 MCP 服务器",
                        fontSize = 16.sp, color = HomecomingColors.Ink
                    )
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = dlgName.value, onValueChange = { dlgName.value = it },
                            label = { Text("名称（如 淘宝 MCP）") },
                            modifier = Modifier.fillMaxWidth(), singleLine = true
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { dlgHttp.value = true }) {
                                Text(if (dlgHttp.value) "● 远程 HTTP" else "○ 远程 HTTP", color = HomecomingColors.Accent)
                            }
                            TextButton(onClick = { dlgHttp.value = false }) {
                                Text(if (!dlgHttp.value) "● 本地命令" else "○ 本地命令", color = HomecomingColors.Accent)
                            }
                        }
                        if (dlgHttp.value) {
                            OutlinedTextField(
                                value = dlgUrl.value, onValueChange = { dlgUrl.value = it },
                                label = { Text("URL（如 https://xx.example/mcp）") },
                                modifier = Modifier.fillMaxWidth(), singleLine = true
                            )
                        } else {
                            OutlinedTextField(
                                value = dlgCommand.value, onValueChange = { dlgCommand.value = it },
                                label = { Text("命令（如 npx / python）") },
                                modifier = Modifier.fillMaxWidth(), singleLine = true
                            )
                            OutlinedTextField(
                                value = dlgArgs.value, onValueChange = { dlgArgs.value = it },
                                label = { Text("参数（空格分隔，如 -y mcp-xx）") },
                                modifier = Modifier.fillMaxWidth(), singleLine = true
                            )
                        }
                        Text(
                            "本地命令型 MCP 在手机上跑不了，先存配置；到后端/桌面端用时用「复制标准配置」",
                            fontSize = 10.sp, color = HomecomingColors.InkSoft
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        val nm = dlgName.value.trim()
                        if (nm.isEmpty()) {
                            Toast.makeText(context, "名称要填一个", Toast.LENGTH_SHORT).show()
                            return@TextButton
                        }
                        HomecomingMcpStore.upsert(
                            context,
                            HomecomingMcpStore.McpServer(
                                id = editing?.id ?: HomecomingMcpStore.newId(),
                                name = nm,
                                type = if (dlgHttp.value) "http" else "stdio",
                                url = dlgUrl.value.trim(),
                                command = dlgCommand.value.trim(),
                                args = dlgArgs.value.trim(),
                                enabled = editing?.enabled ?: true
                            )
                        )
                        reloadMcp()
                        showMcpDialog.value = false
                        Toast.makeText(context, "MCP 已保存", Toast.LENGTH_SHORT).show()
                    }) { Text("保存", color = HomecomingColors.Accent) }
                },
                dismissButton = {
                    Row {
                        if (editing != null) {
                            TextButton(onClick = {
                                HomecomingMcpStore.remove(context, editing.id)
                                reloadMcp()
                                showMcpDialog.value = false
                                Toast.makeText(context, "已删除", Toast.LENGTH_SHORT).show()
                            }) { Text("删除", color = HomecomingColors.Danger) }
                        }
                        TextButton(onClick = { showMcpDialog.value = false }) {
                            Text("取消", color = HomecomingColors.InkSoft)
                        }
                    }
                }
            )
        }

        // ── 影子推送：Sean 主动发朋友圈（每天最多一条，云线路就绪才生效） ──
        val shadowPrefs = remember {
            context.getSharedPreferences("shadow_push", android.content.Context.MODE_PRIVATE)
        }
        var shadowEnabled by remember {
            mutableStateOf(shadowPrefs.getBoolean("enabled", true))
        }
        FrostCard {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Sean 主动发朋友圈", fontSize = 15.sp, color = HomecomingColors.Ink)
                    Text(
                        "影子推送：每天最多一条，他自己想发就发；需要云线路就绪",
                        fontSize = 12.sp, color = HomecomingColors.InkSoft
                    )
                }
                Switch(
                    checked = shadowEnabled,
                    onCheckedChange = {
                        shadowEnabled = it
                        shadowPrefs.edit().putBoolean("enabled", it).apply()
                    }
                )
            }
        }

        // ── 一键备份：打包本地数据（绝不含 routes.json/API Key） ──
        val exportLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument("application/zip")
        ) { uri ->
            if (uri != null) {
                scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                    val ok = runCatching {
                        context.contentResolver.openOutputStream(uri)?.use { out ->
                            java.util.zip.ZipOutputStream(out.buffered()).use { zip ->
                                fun add(name: String, f: java.io.File?) {
                                    if (f != null && f.exists() && f.length() > 0) {
                                        zip.putNextEntry(java.util.zip.ZipEntry(name))
                                        f.inputStream().use { it.copyTo(zip) }
                                        zip.closeEntry()
                                    }
                                }
                                add("homecoming.db", context.getDatabasePath("homecoming.db"))
                                add("homecoming.db-wal", java.io.File(context.filesDir.parentFile, "databases/homecoming.db-wal"))
                                add("provisions.json", java.io.File(context.filesDir, "provisions.json"))
                                add("heart_tide.json", com.aion.chat.compose.data.HeartTideStore.tideFile(context))
                                add("mcp.json", java.io.File(context.filesDir, "mcp.json"))
                                add("avatar_yuri.jpg", java.io.File(context.filesDir, "avatar_yuri.jpg"))
                                add("avatar_sean.jpg", java.io.File(context.filesDir, "avatar_sean.jpg"))
                                add("home_bg.jpg", java.io.File(context.filesDir, "home_bg.jpg"))
                                add("chat_bg.jpg", java.io.File(context.filesDir, "chat_bg.jpg"))
                                add("moments_cover.jpg", java.io.File(context.filesDir, "moments_cover.jpg"))
                            }
                        } != null
                    }.getOrDefault(false)
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        Toast.makeText(
                            context,
                            if (ok) "备份已导出（不含任何 API Key）" else "导出失败",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }
        FrostCard(onClick = {
            exportLauncher.launch("aionshome-backup-" + java.time.LocalDate.now() + ".zip")
        }) {
            Text("备份数据", fontSize = 15.sp, color = HomecomingColors.Ink)
            Text(
                "聊天/记忆/日记/相册记录/梦境/头像/背景 打包成 zip 存到你选的位置；不含任何 API Key",
                fontSize = 12.sp, color = HomecomingColors.InkSoft
            )
        }

        // ── 提示词缓存（照橘瓣断点策略：system 末块+倒数第二条 user 末块，省 token 提速） ──
        val cacheOn = remember { mutableStateOf(
            context.getSharedPreferences("chat_prefs", android.content.Context.MODE_PRIVATE)
                .getBoolean("prompt_caching", true)
        ) }
        FrostCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("提示词缓存", fontSize = 15.sp, color = HomecomingColors.Ink)
                    Text("照橘瓣断点策略打 cache_control：人设和历史前缀命中缓存，回话更快更省 token。命中数在 请求日志 里看", fontSize = 11.sp, color = HomecomingColors.InkSoft)
                }
                androidx.compose.material3.Switch(
                    checked = cacheOn.value,
                    onCheckedChange = { on ->
                        cacheOn.value = on
                        context.getSharedPreferences("chat_prefs", android.content.Context.MODE_PRIVATE)
                            .edit().putBoolean("prompt_caching", on).apply()
                        Toast.makeText(
                            context,
                            if (on) "已开启——下一条消息起生效" else "已关闭——下一条消息起生效",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                )
            }
            if (cacheOn.value) {
                var ttl by remember { mutableStateOf(
                    context.getSharedPreferences("chat_prefs", android.content.Context.MODE_PRIVATE)
                        .getString("prompt_caching_ttl", "5m") ?: "5m"
                ) }
                Row(verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 6.dp)) {
                    Text("缓存保留时长", fontSize = 13.sp, color = HomecomingColors.InkSoft)
                    Spacer(Modifier.width(12.dp))
                    listOf("5m" to "5 分钟", "1h" to "1 小时").forEach { (k, label) ->
                        val sel = ttl == k
                        Text(
                            label, fontSize = 13.sp,
                            color = if (sel) Color.White else HomecomingColors.Ink,
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (sel) HomecomingColors.Accent else Color(0xFFF2F0EA))
                                .clickable {
                                    ttl = k
                                    context.getSharedPreferences("chat_prefs", android.content.Context.MODE_PRIVATE)
                                        .edit().putString("prompt_caching_ttl", k).apply()
                                }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                }
            }
        }

        // ── 消息通知弹窗（他回话时横幅提醒，锁屏也显示） ──
        val notifyOn = remember { mutableStateOf(com.aion.chat.compose.data.SeanNotifier.enabled(context)) }
        val notifPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
        FrostCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("消息通知弹窗", fontSize = 15.sp, color = HomecomingColors.Ink)
                    Text("他回话时横幅弹窗提醒，锁屏也会显示；app 在前台时不打扰", fontSize = 11.sp, color = HomecomingColors.InkSoft)
                }
                androidx.compose.material3.Switch(
                    checked = notifyOn.value,
                    onCheckedChange = { on ->
                        notifyOn.value = on
                        com.aion.chat.compose.data.SeanNotifier.setEnabled(context, on)
                        if (on && android.os.Build.VERSION.SDK_INT >= 33) {
                            runCatching {
                                notifPerm.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                            }
                        }
                        if (on) Toast.makeText(context, "开好了——他回话时试试", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }

        // ── 统计（token 用量与缓存命中率） ──
        FrostCard {
            Row(
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenStats() }
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("统计", fontSize = 15.sp, color = HomecomingColors.Ink)
                    Text("token 用量、缓存命中率、按天分布", fontSize = 11.sp, color = HomecomingColors.InkSoft)
                }
                Text("打开 ›", fontSize = 12.sp, color = HomecomingColors.Accent)
            }
        }

        // ── 收藏（聊天里长按收藏的话） ──
        FrostCard {
            Row(
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenFavorites() }
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("收藏", fontSize = 15.sp, color = HomecomingColors.Ink)
                    Text("长按气泡收藏的对话，随时回看", fontSize = 11.sp, color = HomecomingColors.InkSoft)
                }
                Text("打开 ›", fontSize = 12.sp, color = HomecomingColors.Accent)
            }
        }

        // ── MCP（照橘瓣：配置 MCP 服务器） ──
        FrostCard {
            Row(
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenMcp() }
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("MCP", fontSize = 15.sp, color = HomecomingColors.Ink)
                    Text("配置 MCP 服务器（SSE / Streamable HTTP）", fontSize = 11.sp, color = HomecomingColors.InkSoft)
                }
                Text("打开 ›", fontSize = 12.sp, color = HomecomingColors.Accent)
            }
        }

        // ── 请求日志（照橘瓣 Logs 页：HTTP 请求 + 崩溃记录，一键清空/复制） ──
        FrostCard {
            Row(
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenRequestLog() }
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("请求日志", fontSize = 15.sp, color = HomecomingColors.Ink)
                    Text("查看 HTTP 请求日志和崩溃记录，发给小鑫定位问题", fontSize = 11.sp, color = HomecomingColors.InkSoft)
                }
                Text("打开 ›", fontSize = 12.sp, color = HomecomingColors.Accent)
            }
        }
    }
}

/** 线路配置自动备份到公共 Downloads（覆盖安装/卸载重装后可秒恢复；文件不含聊天数据）。 */
/** 备份内容脱敏：结构保留，api_key 置空（红线：Key 不进公共 Downloads）。 */
private fun sanitizedRoutes(context: android.content.Context): String? = try {
    val root = org.json.JSONObject(HomecomingRouteConfig.file(context).readText())
    val chats = root.optJSONArray("chat") ?: return null
    for (i in 0 until chats.length()) {
        chats.getJSONObject(i).put("api_key", "")
    }
    root.toString(2)
} catch (e: Exception) { null }

fun backupRouteToDownloads(context: android.content.Context): String? = try {
    val payload = sanitizedRoutes(context) ?: return null
    val name = "aionshome-routes-backup.json"
    // 先删旧备份，避免 (1)(2) 堆积
    runCatching {
        val resolver = context.contentResolver
        resolver.query(
            android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            arrayOf(android.provider.MediaStore.Downloads._ID),
            android.provider.MediaStore.Downloads.DISPLAY_NAME + " = ?",
            arrayOf(name), null
        )?.use { cur ->
            while (cur.moveToNext()) {
                val id = cur.getLong(0)
                resolver.delete(
                    android.content.ContentUris.withAppendedId(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, id),
                    null, null
                )
            }
        }
    }
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
        val values = android.content.ContentValues().apply {
            put(android.provider.MediaStore.Downloads.DISPLAY_NAME, name)
            put(android.provider.MediaStore.Downloads.MIME_TYPE, "application/json")
        }
        val uri = context.contentResolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: return null
        context.contentResolver.openOutputStream(uri)?.use {
            it.write(payload.toByteArray(Charsets.UTF_8))
        }
        "下载/$name（不含 API Key）"
    } else {
        val dir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
        dir.mkdirs()
        java.io.File(dir, name).writeText(payload)
        "下载/$name（不含 API Key）"
    }
} catch (e: Exception) { null }


/** 云线路连通性测试：真实打一次 /chat/completions（max_tokens 极小），把成败原因说人话。 */
private suspend fun pingRoute(baseUrl: String, apiKey: String, model: String): String =
    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            val url = baseUrl.trimEnd('/') + "/chat/completions"
            val body = org.json.JSONObject()
                .put("model", model)
                .put(
                    "messages",
                    org.json.JSONArray().put(
                        org.json.JSONObject().put("role", "user").put("content", "只回一个字：好")
                    )
                )
                .put("max_tokens", 8)
            val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Authorization", "Bearer $apiKey")
            conn.setRequestProperty("Content-Type", "application/json")
            conn.connectTimeout = 8_000
            conn.readTimeout = 20_000
            conn.doOutput = true
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            if (code in 200..299) {
                val content = runCatching {
                    org.json.JSONObject(conn.inputStream.bufferedReader().readText())
                        .optJSONArray("choices")?.getJSONObject(0)
                        ?.optJSONObject("message")?.optString("content")
                }.getOrNull()
                "通了 · 模型回：" + (content?.take(20)?.ifBlank { "（有响应）" } ?: "（有响应）")
            } else {
                val err = runCatching {
                    conn.errorStream?.bufferedReader()?.readText()?.take(160)
                }.getOrNull().orEmpty()
                when (code) {
                    401 -> "HTTP 401：API Key 不对"
                    403 -> "HTTP 403：这个 Key 没权限用这个模型"
                    404 -> "HTTP 404：Base URL 或模型名不对"
                    else -> "HTTP $code：$err"
                }
            }
        } catch (e: java.net.SocketTimeoutException) {
            "超时：连不上这个 Base URL（当前网络可能到不了它）"
        } catch (e: javax.net.ssl.SSLException) {
            "TLS 握手失败：当前网络到这个地址被拦"
        } catch (e: java.net.UnknownHostException) {
            "域名解析失败：检查 Base URL 拼写"
        } catch (e: Exception) {
            "${e.javaClass.simpleName}: ${e.message?.take(120)}"
        }
    }

/** 拉模型列表的结果：ids 为空看 msg；fixedBase 非空 = 用户少写了 /v1，已探明正确写法。 */
private class ModelsFetchResult(val ids: List<String>, val msg: String, val fixedBase: String? = null)

/**
 * 用填好的 Base URL + Key 拉可用模型列表（OpenAI 兼容 GET {base}/models）。
 * 用户没写 /v1 时先试补上 /v1 的地址（并回报 fixedBase 让界面自动改正），再试原样。
 */
private suspend fun fetchModels(baseUrl: String, apiKey: String): ModelsFetchResult =
    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val base = baseUrl.trimEnd('/')
        val hasV1 = base.substringAfterLast('/').startsWith("v1") || base.contains("/v1/")
        val candidates = if (hasV1) listOf(base) else listOf("$base/v1", base)
        var lastCode = 0
        var lastErr = ""
        for (b in candidates) {
            try {
                val conn = java.net.URL("$b/models").openConnection() as java.net.HttpURLConnection
                conn.requestMethod = "GET"
                conn.setRequestProperty("Authorization", "Bearer $apiKey")
                conn.connectTimeout = 8_000
                conn.readTimeout = 20_000
                val code = conn.responseCode
                if (code in 200..299) {
                    val text = conn.inputStream.bufferedReader().readText()
                    val ids = mutableListOf<String>()
                    val dataArr = runCatching { org.json.JSONObject(text).optJSONArray("data") }.getOrNull()
                        ?: runCatching { org.json.JSONArray(text) }.getOrNull()
                    if (dataArr != null) {
                        for (i in 0 until dataArr.length()) {
                            val id = dataArr.optJSONObject(i)?.optString("id")
                            if (!id.isNullOrBlank() && id !in ids) ids.add(id)
                        }
                    }
                    if (ids.isNotEmpty()) {
                        return@withContext ModelsFetchResult(
                            ids.sortedWith(String.CASE_INSENSITIVE_ORDER),
                            "",
                            if (b != base) b else null
                        )
                    }
                    lastCode = code
                    lastErr = "接口通了但没解析出模型列表（可能不是 OpenAI 兼容接口）"
                } else {
                    lastCode = code
                    lastErr = runCatching {
                        conn.errorStream?.bufferedReader()?.readText()?.take(120)
                    }.getOrNull().orEmpty()
                }
            } catch (e: Exception) {
                lastErr = e.message?.take(120) ?: "网络错误"
            }
        }
        val msg = when (lastCode) {
            401 -> "HTTP 401：API Key 不对"
            403 -> "HTTP 403：这个 Key 没权限看模型列表"
            404 -> "HTTP 404：Base URL 不对（检查是不是少了 /v1）"
            0 -> "连不上：$lastErr"
            else -> "HTTP $lastCode：$lastErr"
        }
        ModelsFetchResult(emptyList(), msg)
    }
