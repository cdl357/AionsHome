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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aion.chat.compose.data.HomecomingData
import com.aion.chat.compose.data.HomecomingRouteConfig
import com.aion.chat.compose.data.SettingsBg
import com.aion.chat.compose.ui.home.FrostCard
import com.aion.chat.compose.data.AppCrashLog
import com.aion.chat.compose.data.HomecomingMcpStore
import com.aion.chat.compose.ui.theme.HomecomingColors
import com.aion.chat.compose.ui.theme.HomecomingThemeState

/** 设置：换背景图先行；云线路 / 模型 / TTS / 主题在阶段五接入。 */
@Composable
fun SettingsScreen() {
    val context = LocalContext.current

    val routeLabel = remember { mutableStateOf("") }
    val routeBaseUrl = remember { mutableStateOf("") }
    val routeApiKey = remember { mutableStateOf("") }
    val routeModel = remember { mutableStateOf("") }
    val routeSaved = remember { mutableStateOf(false) }

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
            Toast.makeText(context, "云线路已保存，聊天页即可用", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "保存失败", Toast.LENGTH_SHORT).show()
        }
    }

    val chatPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri != null) {
            val ok = runCatching {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    SettingsBg.chatFile(context).outputStream().use { output ->
                        input.copyTo(output)
                    }
                } != null
            }.getOrDefault(false)
            if (ok) {
                SettingsBg.bump()
                Toast.makeText(context, "聊天背景已换好", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "没读出这张图，换一张试试", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri != null) {
            val ok = runCatching {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    SettingsBg.backgroundFile(context).outputStream().use { output ->
                        input.copyTo(output)
                    }
                } != null
            }.getOrDefault(false)
            if (ok) {
                SettingsBg.bump()
                Toast.makeText(context, "背景已换好，回「回家」页看看", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "没读出这张图，换一张试试", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(18.dp)
    ) {
        Text("设置", fontSize = 22.sp, color = HomecomingColors.Ink)
        Text("先从换背景开始，其余在阶段五接入", fontSize = 12.sp, color = HomecomingColors.InkSoft)
        Spacer(Modifier.height(14.dp))

        // ── 主题（布局定稿·方案 C）：五预设 + 单主色调色盘 ──
        val themeState = remember { androidx.compose.runtime.mutableStateOf(HomecomingThemeState.PRESETS[1]) }
        val customHue = remember { androidx.compose.runtime.mutableStateOf(200f) }
        FrostCard {
            Text("主题", fontSize = 15.sp, color = HomecomingColors.Ink)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HomecomingThemeState.PRESETS.forEach { p ->
                    Text(
                        p.name,
                        fontSize = 13.sp,
                        color = if (themeState.value.key == p.key) Color.White else HomecomingColors.Ink,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(p.accent)
                            .clickable {
                                themeState.value = p
                                HomecomingThemeState.applyPreset(context, p.key)
                            }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Text("调色盘：只调一个主色，其余自动配", fontSize = 12.sp, color = HomecomingColors.InkSoft)
            androidx.compose.material3.Slider(
                value = customHue.value,
                onValueChange = { hue ->
                    customHue.value = hue
                    HomecomingThemeState.applyCustomHue(context, hue)
                },
                valueRange = 0f..360f
            )
            Text(
                "暖橙区会自动压饱和（无橘色）",
                fontSize = 10.sp, color = HomecomingColors.InkSoft
            )
        }

        FrostCard {
            Text("主页背景图", fontSize = 15.sp, color = HomecomingColors.Ink)
            Text(
                "从手机相册选一张，回家页立即换上；选竖图效果最好",
                fontSize = 12.sp,
                color = HomecomingColors.InkSoft
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = {
                        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("选一张照片") }
                OutlinedButton(
                    onClick = {
                        val f = SettingsBg.backgroundFile(context)
                        if (f.exists() && f.delete()) {
                            SettingsBg.bump()
                            Toast.makeText(context, "已恢复默认背景", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "现在用的就是默认背景", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("恢复默认") }
            }
        }

        FrostCard {
            Text("云线路（聊天用）", fontSize = 15.sp, color = HomecomingColors.Ink)
            Text(
                "OpenAI 兼容接口：填 Base URL / API Key / 模型",
                fontSize = 12.sp,
                color = HomecomingColors.InkSoft
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = routeLabel.value, onValueChange = { routeLabel.value = it },
                label = { Text("线路名称（可不填）") },
                modifier = Modifier.fillMaxWidth(), singleLine = true
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = routeBaseUrl.value, onValueChange = { routeBaseUrl.value = it },
                label = { Text("Base URL（如 https://api.xx.com/v1）") },
                modifier = Modifier.fillMaxWidth(), singleLine = true
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = routeApiKey.value, onValueChange = { routeApiKey.value = it },
                label = { Text("API Key") },
                modifier = Modifier.fillMaxWidth(), singleLine = true
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = routeModel.value, onValueChange = { routeModel.value = it },
                label = { Text("模型（如 gpt-4o-mini）") },
                modifier = Modifier.fillMaxWidth(), singleLine = true
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = { saveRoute() }) { Text("保存线路", color = HomecomingColors.Accent) }
            }
            if (routeSaved.value) {
                Text("已保存。回「聊天」页即可开聊。", fontSize = 11.sp, color = HomecomingColors.Ok)
            }
        }

        // ── MCP 服务器管理：先把配置攒好，聊天引擎的工具接线下一阶段开放 ──
        val mcps = remember {
            mutableStateListOf<HomecomingMcpStore.McpServer>().apply { addAll(HomecomingMcpStore.list(context)) }
        }
        fun reloadMcp() { mcps.clear(); mcps.addAll(HomecomingMcpStore.list(context)) }
        val mcpEditing = remember { mutableStateOf<HomecomingMcpStore.McpServer?>(null) }
        val showMcpDialog = remember { mutableStateOf(false) }

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

        FrostCard {
            Text("聊天背景图（可选覆盖）", fontSize = 15.sp, color = HomecomingColors.Ink)
            Text(
                "只给聊天页换一张；不选则跟随上面的全局背景",
                fontSize = 12.sp,
                color = HomecomingColors.InkSoft
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = {
                        chatPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("选一张照片") }
                OutlinedButton(
                    onClick = {
                        val f = SettingsBg.chatFile(context)
                        if (f.exists() && f.delete()) {
                            SettingsBg.bump()
                            Toast.makeText(context, "聊天页已跟随全局背景", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "聊天页现在用的就是全局背景", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("跟随全局") }
            }
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

        val lastCrash = remember { AppCrashLog.last(context) }

        // ── 版本与崩溃日志（便于反馈问题） ──
        FrostCard {
            Text("版本 v0.3", fontSize = 15.sp, color = HomecomingColors.Ink)
            Text("当前安装的回家 App 版本", fontSize = 11.sp, color = HomecomingColors.InkSoft, modifier = Modifier.padding(top = 4.dp))
        }

        if (lastCrash != null) {
            FrostCard {
                Text("上次崩溃日志", fontSize = 15.sp, color = HomecomingColors.Danger)
                Text(lastCrash, fontSize = 10.sp, color = HomecomingColors.InkSoft, modifier = Modifier.padding(top = 6.dp))
                Text("把这段发给 Sean 就能定位问题", fontSize = 10.sp, color = HomecomingColors.InkSoft, modifier = Modifier.padding(top = 4.dp))
            }
        }
        FrostCard {
            Text("云线路 · 模型 · TTS · 主题", fontSize = 15.sp, color = HomecomingColors.Ink)
            Text("阶段五接入", fontSize = 12.sp, color = HomecomingColors.InkSoft, modifier = Modifier.padding(top = 4.dp))
        }
    }
}
