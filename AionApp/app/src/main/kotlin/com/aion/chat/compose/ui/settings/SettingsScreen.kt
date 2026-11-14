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
import androidx.compose.runtime.rememberCoroutineScope
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
import com.aion.chat.compose.data.BackupStore
import com.aion.chat.compose.data.HomecomingMcpStore
import com.aion.chat.compose.data.restartApp
import com.aion.chat.compose.ui.theme.HomecomingColors
import com.aion.chat.compose.ui.theme.HomecomingThemeState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat

/** 设置：背景 / 主题 / 云线路 / MCP / 备份 / 网易云登录。 */
@Composable
fun SettingsScreen(onOpenHerState: () -> Unit = {}, onOpenStyleHub: () -> Unit = {}, onOpenRequestLog: () -> Unit = {}, onOpenMcp: () -> Unit = {}, onOpenWorkflow: () -> Unit = {}, onOpenTranslate: () -> Unit = {}, onOpenLocalModel: () -> Unit = {}, onOpenSandbox: () -> Unit = {}) {
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
        Text(
            "v" + com.aion.chat.BuildConfig.VERSION_NAME + " · 云线路 / MCP / 备份；背景和主题在样式中心",
            fontSize = 12.sp, color = HomecomingColors.InkSoft
        )
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
                    Text("照橘瓣断点策略打 cache_control：人设和历史前缀命中缓存，更快更省 token。每次发消息自动续期；停聊超过时长才过期（橘瓣也是这套 5 分钟/1 小时机制）。命中数在 请求日志 里看", fontSize = 11.sp, color = HomecomingColors.InkSoft, lineHeight = 15.sp)
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

        // ── 导入：橘瓣聊天记录（chat-export-*.md） ──
        val importTip = remember { mutableStateOf("") }
        val importPending = remember { mutableStateOf<String?>(null) }
        val importPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                }.getOrNull()?.let { md ->
                    val parsed = com.aion.chat.compose.data.ChatImporter.parse(md)
                    importPending.value = md
                    importTip.value = if (parsed.messages.isEmpty())
                        "没解析出消息——确认是橘瓣「导出聊天记录」生成的 Markdown"
                    else "解析到 ${parsed.messages.size} 条消息" +
                        (if (parsed.title.isNotBlank()) "（${parsed.title.take(20)}）" else "") +
                        "，点「确认导入」落进当前聊天"
                } ?: run { importTip.value = "读不出这个文件" }
            }
        }
        FrostCard {
            Column {
                Text("导入聊天记录（橘瓣）", fontSize = 15.sp, color = HomecomingColors.Ink)
                Text(
                    "选择橘瓣导出的 chat-export-*.md，消息会按顺序导入当前聊天",
                    fontSize = 11.sp, color = HomecomingColors.InkSoft,
                    modifier = Modifier.padding(top = 2.dp)
                )
                if (importTip.value.isNotBlank()) {
                    Text(importTip.value, fontSize = 12.sp, color = HomecomingColors.Accent,
                        modifier = Modifier.padding(top = 6.dp))
                }
                Row(modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.End) {
                    if (importPending.value != null) {
                        TextButton(onClick = {
                            val n = com.aion.chat.compose.data.ChatImporter.importToCurrentChat(context, importPending.value!!)
                            importPending.value = null
                            importTip.value = if (n >= 0) "已导入 $n 条——回聊天页就能看到" else "导入失败（看崩溃日志）"
                        }) { Text("确认导入", color = HomecomingColors.Accent, fontWeight = FontWeight.SemiBold, fontSize = 13.sp) }
                    }
                    TextButton(onClick = {
                        importPicker.launch(arrayOf("text/markdown", "text/plain", "application/octet-stream"))
                    }) { Text("选择 .md 文件", color = HomecomingColors.Accent, fontWeight = FontWeight.SemiBold, fontSize = 13.sp) }
                }
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

        // ── 工作流（照橘瓣 workflow_*：定时/充电/亮屏/通知/到岗触发，动作走现有工具面） ──
        FrostCard {
            Row(
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenWorkflow() }
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("工作流", fontSize = 15.sp, color = HomecomingColors.Ink)
                    Text("在聊天里让 Sean 建：定时提醒、充电播报、通知响应、回来问候，动作他自己的工具都能干", fontSize = 11.sp, color = HomecomingColors.InkSoft)
                }
                Text("打开 ›", fontSize = 12.sp, color = HomecomingColors.Accent)
            }
        }

        // ── 翻译（队列项：当前模型直译，不带人设不进聊天树） ──
        FrostCard {
            Row(
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenTranslate() }
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("翻译", fontSize = 15.sp, color = HomecomingColors.Ink)
                    Text("粘贴一段文字直接翻，语言 chips 换目标语，历史可点回", fontSize = 11.sp, color = HomecomingColors.InkSoft)
                }
                Text("打开 ›", fontSize = 12.sp, color = HomecomingColors.Accent)
            }
        }

        // ── 本地模型（队列项：llama.cpp JNI，断网也能聊） ──
        FrostCard {
            Row(
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenLocalModel() }
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("本地模型", fontSize = 15.sp, color = HomecomingColors.Ink)
                    Text("Qwen2.5 小杯跑在手机里，断网可用，聊天页开关一键切换", fontSize = 11.sp, color = HomecomingColors.InkSoft)
                }
                Text("打开 ›", fontSize = 12.sp, color = HomecomingColors.Accent)
            }
        }

        // ── 语音（v0.3.153：ElevenLabs——她账号里已有的克隆音色） ──
        FrostCard {
            var elKey by remember { mutableStateOf(com.aion.chat.compose.data.VoiceStore.key(context)) }
            var voiceName by remember { mutableStateOf(com.aion.chat.compose.data.VoiceStore.voiceName(context)) }
            var fetching by remember { mutableStateOf(false) }
            var voices by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
            var showPick by remember { mutableStateOf(false) }
            var fetchErr by remember { mutableStateOf<String?>(null) }
            val voiceScope = rememberCoroutineScope()

            Column {
                Text("语音 · 我的声音", fontSize = 15.sp, color = HomecomingColors.Ink)
                Text(
                    "填 ElevenLabs API Key → 拉取音色 → 选你克隆好的那个。聊天朗读就是你的声音（通话声音在 kimi-room 的 ELEVENLABS_VOICE_ID，两边都改才统一）",
                    fontSize = 11.sp, color = HomecomingColors.InkSoft, lineHeight = 15.sp
                )
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = elKey,
                    onValueChange = {
                        elKey = it
                        com.aion.chat.compose.data.VoiceStore.setKey(context, it)
                    },
                    placeholder = { Text("ElevenLabs API Key", fontSize = 11.sp) },
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp),
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Sean 回复自动带语音条", fontSize = 13.sp, color = HomecomingColors.Ink, modifier = Modifier.weight(1f))
                    androidx.compose.material3.Switch(
                        checked = com.aion.chat.compose.data.VoiceMsgStore.autoEnabled(context),
                        onCheckedChange = { on ->
                            com.aion.chat.compose.data.VoiceMsgStore.setAutoEnabled(context, on)
                        }
                    )
                }
                Text(
                    "开=每条回复自动用你选的音色合成语音条（挂气泡下，点播）。要先选好音色；还没克隆的话开关不会出声",
                    fontSize = 10.sp, color = HomecomingColors.InkSoft, lineHeight = 14.sp
                )
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        enabled = elKey.isNotBlank() && !fetching,
                        onClick = {
                            fetching = true; fetchErr = null
                            voiceScope.launch {
                                val (list, err) = withContext(Dispatchers.IO) {
                                    com.aion.chat.compose.data.VoiceStore.listVoices(context)
                                }
                                fetching = false
                                if (list != null) {
                                    voices = list
                                    showPick = true
                                } else fetchErr = err ?: "拉取失败"
                            }
                        },
                        shape = RoundedCornerShape(10.dp),
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                            containerColor = HomecomingColors.Accent, contentColor = Color.White
                        )
                    ) { Text(if (fetching) "拉取中…" else "拉取音色列表", fontSize = 12.sp) }
                    Spacer(Modifier.width(10.dp))
                    Text(
                        if (voiceName.isBlank()) "还没选音色"
                        else "当前 ✓ " + voiceName,
                        fontSize = 11.sp, color = HomecomingColors.InkSoft
                    )
                }
                fetchErr?.let {
                    Spacer(Modifier.height(4.dp))
                    Text(it, fontSize = 11.sp, color = Color(0xFFB3564D))
                }
                if (showPick && voices.isNotEmpty()) {
                    androidx.compose.material3.AlertDialog(
                        onDismissRequest = { showPick = false },
                        title = { Text("选一个音色", fontSize = 16.sp) },
                        text = {
                            Column(Modifier.verticalScroll(rememberScrollState())) {
                                voices.forEach { (name, id) ->
                                    Text(
                                        name, fontSize = 14.sp, color = HomecomingColors.Ink,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                com.aion.chat.compose.data.VoiceStore.setVoice(context, id, name)
                                                voiceName = name
                                                showPick = false
                                            }
                                            .padding(vertical = 10.dp)
                                    )
                                }
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = { showPick = false }) { Text("取消") }
                        }
                    )
                }
            }
        }

        // ── Linux 沙盒（proot：装好 rootfs Sean 就有真 shell） ──
        FrostCard {
            Row(
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenSandbox() }
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Linux 沙盒", fontSize = 15.sp, color = HomecomingColors.Ink)
                    Text("手机里的 Alpine Linux，Sean 可跑脚本/apk 装软件/处理文件，系统隔离", fontSize = 11.sp, color = HomecomingColors.InkSoft)
                }
                Text("打开 ›", fontSize = 12.sp, color = HomecomingColors.Accent)
            }
        }

        // ── 自动备份（WorkManager 12h 一份 ZIP：聊天库+配置+记忆，保留 7 份） ──
        FrostCard {
            var bkEnabled by remember { mutableStateOf(BackupStore.isEnabled(context)) }
            var bkList by remember { mutableStateOf(BackupStore.list(context)) }
            var bkBusy by remember { mutableStateOf(false) }
            var bkRestore by remember { mutableStateOf<File?>(null) }
            val bkScope = rememberCoroutineScope()
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("自动备份", fontSize = 15.sp, color = HomecomingColors.Ink)
                        Text(
                            "聊天记录+配置+记忆 每 12 小时打包一份，保留最近 7 份（不含图片视频）",
                            fontSize = 11.sp, color = HomecomingColors.InkSoft
                        )
                    }
                    Switch(checked = bkEnabled, onCheckedChange = { on ->
                        bkEnabled = on
                        BackupStore.setEnabled(context, on)
                    })
                }
                val lastAt = BackupStore.lastBackupAt(context)
                Text(
                    if (lastAt > 0L) "上次备份：" + SimpleDateFormat("MM-dd HH:mm", java.util.Locale.US).format(java.util.Date(lastAt))
                    else "还没有备份",
                    fontSize = 11.sp, color = HomecomingColors.InkSoft
                )
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(enabled = !bkBusy, onClick = {
                        bkBusy = true
                        bkScope.launch {
                            val msg = withContext(Dispatchers.IO) {
                                runCatching {
                                    val f = BackupStore.create(context)
                                    "备份完成 ${"%.1f".format(f.length() / 1048576.0)} MB"
                                }.getOrElse { "备份失败：${it.message?.take(50)}" }
                            }
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            bkList = BackupStore.list(context)
                            bkBusy = false
                        }
                    }) { Text("立即备份", fontSize = 12.sp) }
                    OutlinedButton(enabled = bkList.isNotEmpty(), onClick = {
                        val f = bkList.firstOrNull() ?: return@OutlinedButton
                        bkScope.launch {
                            val path = withContext(Dispatchers.IO) { BackupStore.exportToDownloads(context, f) }
                            Toast.makeText(
                                context,
                                if (path != null) "已导出到 $path" else "导出失败",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }) { Text("导出最新到 Downloads", fontSize = 12.sp) }
                }
                bkList.take(7).forEach { f ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { bkRestore = f }
                    ) {
                        Text(
                            f.name.removePrefix("aion-backup-").removeSuffix(".zip"),
                            fontSize = 12.sp, color = HomecomingColors.Ink,
                            modifier = Modifier.weight(1f)
                        )
                        Text("${"%.1f".format(f.length() / 1048576.0)} MB", fontSize = 11.sp, color = HomecomingColors.InkSoft)
                    }
                }
                if (bkList.isNotEmpty()) {
                    Text("点一份可还原（覆盖当前数据，完成后自动重启）", fontSize = 10.sp, color = HomecomingColors.InkSoft)
                }
                bkRestore?.let { f ->
                    AlertDialog(
                        onDismissRequest = { bkRestore = null },
                        title = { Text("还原这份备份？", fontSize = 16.sp) },
                        text = {
                            Text(
                                "将覆盖当前的聊天记录、记忆和配置，完成后自动重启。此操作不可撤销。",
                                fontSize = 13.sp
                            )
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                bkRestore = null
                                bkScope.launch {
                                    val ok = withContext(Dispatchers.IO) {
                                        runCatching { BackupStore.restore(context, f) }.isSuccess
                                    }
                                    if (ok) {
                                        Toast.makeText(context, "还原完成，正在重启…", Toast.LENGTH_SHORT).show()
                                        delay(600)
                                        restartApp(context)
                                    } else {
                                        Toast.makeText(context, "还原失败（看崩溃日志）", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }) { Text("还原", color = HomecomingColors.Accent) }
                        },
                        dismissButton = {
                            TextButton(onClick = { bkRestore = null }) { Text("取消") }
                        }
                    )
                }
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
