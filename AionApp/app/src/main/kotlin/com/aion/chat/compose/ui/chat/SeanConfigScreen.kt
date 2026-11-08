package com.aion.chat.compose.ui.chat

import androidx.compose.foundation.background
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aion.chat.compose.ui.common.AvatarPhoto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Sean 的配置页（对话页左侧进入；结构照截图那版助手配置搬）：
 * 头像+名字+人设预览 → 基础设定（名称/模型）/ 提示词（系统提示）/ 记忆 / 其余项标注后续版本。
 */
private val PgBg = Color(0xFFF3F1EC)
private val PgCard = Color(0xFFF9F8F5)
private val PgInk = Color(0xFF26211C)
private val PgSub = Color(0xFF9A9388)
private val PgAccent = Color(0xFF7FA6C9)

private data class IdParts(
    val userName: String,
    val companionName: String,
    val systemPrompt: String,
    val raw: String?
)

private fun loadIdentity(context: android.content.Context): IdParts =
    runCatching {
        val db = com.aion.chat.homecoming.HomecomingDatabase.sharedWritable(context)
        db.rawQuery(
            "SELECT payload_json FROM identity_snapshot WHERE identity_id='identity'", null
        ).use { c ->
            if (!c.moveToFirst()) return IdParts("小鑫", "Sean", "", null)
            val root = JSONObject(c.getString(0))
            IdParts(
                userName = root.optJSONObject("user")?.optString("name", "小鑫") ?: "小鑫",
                companionName = root.optJSONObject("companions")?.optJSONObject("main")
                    ?.optString("name", "Sean") ?: "Sean",
                systemPrompt = root.optString("system_prompt", ""),
                raw = c.getString(0)
            )
        }
    }.getOrDefault(IdParts("小鑫", "Sean", "", null))

private fun saveIdentity(context: android.content.Context, transform: (JSONObject) -> Unit) {
    runCatching {
        val db = com.aion.chat.homecoming.HomecomingDatabase.sharedWritable(context)
        val root = loadIdentity(context).raw?.let { JSONObject(it) } ?: JSONObject()
        transform(root)
        db.execSQL(
            "UPDATE identity_snapshot SET payload_json=? WHERE identity_id='identity'",
            arrayOf(root.toString())
        )
    }
}

@Composable
fun SeanConfigScreen(
    onBack: () -> Unit,
    onPickModel: () -> Unit,
    onOpenMcp: () -> Unit = {},
    onOpenMemoryBank: () -> Unit = {}
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var id by remember { mutableStateOf(IdParts("小鑫", "Sean", "", null)) }
    var editingName by remember { mutableStateOf<String?>(null) }
    var editingPrompt by remember { mutableStateOf<String?>(null) }
    var savedTip by remember { mutableStateOf(false) }
    var memories by remember { mutableStateOf(listOf<String>()) }
    var extState by remember { mutableStateOf(mapOf<String, Boolean>()) }
    var memToolsOn by remember { mutableStateOf(true) }
    var reqHeaders by remember { mutableStateOf("") }
    var reqBody by remember { mutableStateOf("") }
    var reqTip by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            id = loadIdentity(context)
            editingName = id.companionName
            editingPrompt = id.systemPrompt
            memories = runCatching {
                val w = com.aion.chat.compose.data.HomecomingChatWiring.safeCreate(context)
                w?.memories?.recall("main", "", 50)
                    ?.filter { !it.tombstone }
                    ?.map { it.content }
                    ?: emptyList()
            }.getOrDefault(emptyList())
            extState = com.aion.chat.compose.data.SeanExt.ITEMS.associate { (k, _) ->
                k to com.aion.chat.compose.data.SeanExt.on(context, k)
            }
            memToolsOn = context.getSharedPreferences("chat_prefs", android.content.Context.MODE_PRIVATE)
                .getBoolean("memory_tools", true)
            val cr = com.aion.chat.compose.data.HomecomingChatWiring.loadCustomRequest(context)
            if (cr != null) {
                reqHeaders = org.json.JSONObject(cr.first).toString()
                reqBody = cr.second.toString()
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(PgBg)
            .verticalScroll(rememberScrollState())
    ) {
        // 顶栏
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            Text("‹", fontSize = 26.sp, color = PgInk,
                modifier = Modifier
                    .clickable { onBack() }
                    .padding(horizontal = 10.dp, vertical = 2.dp))
            Text("他的配置", fontSize = 19.sp, color = PgInk, fontWeight = FontWeight.SemiBold)
        }

        // 头像 + 名字
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(Color.White)
            ) {
                AvatarPhoto(who = "sean", initial = "S", size = 96.dp)
            }
            Spacer(Modifier.height(8.dp))
            Text(id.companionName, fontSize = 22.sp, color = PgInk, fontWeight = FontWeight.SemiBold)
            if (id.systemPrompt.isNotBlank()) {
                Text(
                    id.systemPrompt.take(120) + if (id.systemPrompt.length > 120) "…" else "",
                    fontSize = 12.sp, color = PgSub, lineHeight = 18.sp,
                    maxLines = 4, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 28.dp, vertical = 6.dp)
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        // ── 基础设定：名称 / 模型 ──
        SectionCard(title = "基础设定", subtitle = "他的名字、聊天用的模型") {
            OutlinedTextField(
                value = editingName ?: "",
                onValueChange = { editingName = it },
                label = { Text("他的名字", fontSize = 13.sp, color = PgSub) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp)
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onPickModel() }
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Column(Modifier.weight(1f)) {
                    Text("聊天模型", fontSize = 14.sp, color = PgInk)
                    Text("在提供商的模型列表里选（顶栏胶囊也可换）", fontSize = 11.sp, color = PgSub)
                }
                Text("›", fontSize = 20.sp, color = PgSub)
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = {
                    saveIdentity(context) { root ->
                        root.optJSONObject("companions")?.optJSONObject("main")
                            ?.put("name", (editingName ?: "").trim())
                    }
                    savedTip = true
                }) { Text("保存名字", color = PgAccent, fontWeight = FontWeight.SemiBold, fontSize = 13.sp) }
            }
        }

        // ── 提示词：系统提示 ──
        SectionCard(title = "提示词", subtitle = "他的人设与系统提示（保存后下一条消息生效）") {
            OutlinedTextField(
                value = editingPrompt ?: "",
                onValueChange = { editingPrompt = it },
                label = { Text("系统提示", fontSize = 13.sp, color = PgSub) },
                minLines = 4,
                maxLines = 10,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp)
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            ) {
                if (savedTip) {
                    Text("已保存 ✓", fontSize = 12.sp, color = Color(0xFF5B8A5E), modifier = Modifier.weight(1f))
                } else Spacer(Modifier.weight(1f))
                TextButton(onClick = {
                    saveIdentity(context) { root -> root.put("system_prompt", editingPrompt ?: "") }
                    savedTip = true
                }) { Text("保存提示词", color = PgAccent, fontWeight = FontWeight.SemiBold, fontSize = 13.sp) }
            }
        }

        // ── 记忆（自维护开关 + 管理入口） ──
        SectionCard(title = "记忆", subtitle = "他记住的事（${memories.size} 条）") {
            Row(verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("让他自己记", fontSize = 13.sp, color = Color(0xFF26211C))
                    Text("给他记忆工具，重要的他自己记进记忆库", fontSize = 11.sp, color = Color(0xFF9A9388))
                }
                androidx.compose.material3.Switch(
                    checked = memToolsOn,
                    onCheckedChange = { v ->
                        memToolsOn = v
                        context.getSharedPreferences("chat_prefs", android.content.Context.MODE_PRIVATE)
                            .edit().putBoolean("memory_tools", v).apply()
                    }
                )
            }
            Text(
                "打开记忆库 ›", fontSize = 13.sp, color = Color(0xFF7FA6C9),
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clickable { onOpenMemoryBank() }
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )
            if (memories.isEmpty()) {
                Text(
                    "还没有记忆。你们聊的内容会慢慢变成这里的条目。",
                    fontSize = 12.sp, color = PgSub,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }
            memories.take(20).forEach { m ->
                Text(
                    "· " + m.take(90),
                    fontSize = 12.sp, color = PgInk, lineHeight = 17.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 3.dp)
                )
            }
        }

        // ── 扩展管理（真开关：关掉即从他的系统提示里剥掉对应段落） ──
        SectionCard(title = "扩展管理", subtitle = "他的能力开关，关掉下一条消息起生效") {
            com.aion.chat.compose.data.SeanExt.ITEMS.forEach { (key, label) ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp)
                ) {
                    Text(label, fontSize = 13.sp, color = PgInk, lineHeight = 17.sp, modifier = Modifier.weight(1f))
                    androidx.compose.material3.Checkbox(
                        checked = extState[key] ?: true,
                        onCheckedChange = { v ->
                            extState = extState + (key to v)
                            com.aion.chat.compose.data.SeanExt.set(context, key, v)
                        },
                        colors = androidx.compose.material3.CheckboxDefaults.colors(checkedColor = PgAccent)
                    )
                }
            }
        }

        // ── 自定义请求（真功能：额外请求头 + 正文参数，合并进每次聊天请求） ──
        SectionCard(title = "自定义请求", subtitle = "给聊天请求加自定义请求头 / 正文参数（JSON）") {
            OutlinedTextField(
                value = reqHeaders,
                onValueChange = { reqHeaders = it },
                label = { Text("请求头 JSON，如 {\"X-Flag\":\"1\"}", fontSize = 11.sp, color = PgSub) },
                minLines = 2, maxLines = 5,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp)
            )
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = reqBody,
                onValueChange = { reqBody = it },
                label = { Text("正文参数 JSON，如 {\"temperature\":0.9}", fontSize = 11.sp, color = PgSub) },
                minLines = 2, maxLines = 5,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp)
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            ) {
                Text(
                    reqTip,
                    fontSize = 12.sp,
                    color = if (reqTip.startsWith("已保存")) Color(0xFF5B8A5E) else Color(0xFFB4574E),
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = {
                    val err = com.aion.chat.compose.data.HomecomingChatWiring
                        .saveCustomRequest(context, reqHeaders, reqBody)
                    reqTip = err ?: "已保存 ✓ 下一条消息起生效"
                }) { Text("保存", color = PgAccent, fontWeight = FontWeight.SemiBold, fontSize = 13.sp) }
            }
        }

        // ── 后续版本 ──
        listOf(
            "本地工具" to "启用本地工具，如 JavaScript 引擎（需要单独版本）"
        ).forEach { (t, s) ->
            SectionCard(title = t, subtitle = s, comingSoon = true) {}
        }

        Spacer(Modifier.height(30.dp))
    }
}

@Composable
private fun SectionCard(
    title: String,
    subtitle: String,
    comingSoon: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 5.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(PgCard)
            .clickable(enabled = onClick != null) { onClick?.invoke() }
            .padding(vertical = 6.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    title + if (comingSoon) "（后续版本）" else "",
                    fontSize = 15.sp,
                    color = if (comingSoon) PgSub else PgInk,
                    fontWeight = FontWeight.SemiBold
                )
                Text(subtitle, fontSize = 11.sp, color = PgSub, lineHeight = 15.sp)
            }
            if (comingSoon) Text("›", fontSize = 20.sp, color = PgSub.copy(alpha = 0.5f))
        }
        content()
    }
}
