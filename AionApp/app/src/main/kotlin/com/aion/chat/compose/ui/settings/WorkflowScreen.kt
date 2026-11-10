package com.aion.chat.compose.ui.settings

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.border
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.aion.chat.compose.data.WorkflowStore
import com.aion.chat.compose.data.WorkflowEngine
import com.aion.chat.compose.data.WorkflowJson
import com.aion.chat.compose.data.WorkflowTriggers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 工作流页（照橘瓣 WorkflowsScreen 的信息密度，AionsHome 版）：
 * 列表 = 每个工作流一张卡（名称/触发器/上次运行/启用开关/立即运行/删除），
 * 点卡片展开最近 10 次运行流水。工作流本体由 Sean 在聊天里用 workflow_* 工具创建。
 */
@Composable
fun WorkflowScreen(onBack: () -> Unit = {}) {
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var rev by remember { mutableIntStateOf(0) }
    var expandedId by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf<String?>(null) }

    val rows = remember(rev) { WorkflowStore.listAll(context) }

    Column(
        Modifier
            .fillMaxSize()
            .background(Color(0xFFF2F0EB))
    ) {
        // 顶栏
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
                    tint = Color(0xFF26211C), modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.weight(1f))
        }
        Text(
            "工作流",
            fontSize = 40.sp, color = Color(0xFF26211C), fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Text(
            "让 Sean 在聊天里创建：定时/充电/亮屏/通知/到岗都能触发，动作走他手里的工具。刷新一次可能晚几分钟到（省电的扫描粒度）",
            fontSize = 11.sp, color = Color(0xFF8A8279), lineHeight = 15.sp,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
        )
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
            if (rows.isEmpty()) {
                item {
                    Text(
                        "还没有工作流。直接在聊天里说，比如「每天晚上 11 点提醒我睡觉」或「连上充电器就发早安」，他会自己建。",
                        fontSize = 13.sp, color = Color(0xFF6F675E),
                        modifier = Modifier.padding(top = 40.dp, start = 8.dp, end = 8.dp)
                    )
                }
            }
            items(rows, key = { it.id }) { row ->
                val def = remember(row.id, row.updatedAtMs) {
                    runCatching { org.json.JSONObject(row.definitionJson) }.getOrNull()
                }
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 5.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.White)
                        .clickable { expandedId = if (expandedId == row.id) null else row.id }
                        .padding(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(row.name, fontSize = 15.sp, color = Color(0xFF26211C))
                            val trig = def?.let { triggerSummary(it) } ?: "（定义解析失败）"
                            Text(trig, fontSize = 11.sp, color = Color(0xFF8A8279))
                            val last = row.lastRunAtMs
                            Text(
                                if (last != null)
                                    "上次：" + SimpleDateFormat("MM-dd HH:mm", Locale.US).format(Date(last)) +
                                            " · " + statusLabel(row.lastRunStatus ?: "?") +
                                            (row.lastRunError?.takeIf { it.isNotBlank() }?.let { " · $it" } ?: "")
                                else "从未运行",
                                fontSize = 11.sp,
                                color = if (row.lastRunStatus == "FAILED") Color(0xFFB3564D) else Color(0xFF8A8279)
                            )
                        }
                        Switch(
                            checked = row.enabled,
                            onCheckedChange = { on ->
                                WorkflowStore.setEnabled(context, row.id, on)
                                WorkflowTriggers.sync(context)
                                rev++
                            }
                        )
                    }
                    if (expandedId == row.id) {
                        Spacer(Modifier.size(6.dp))
                        Row {
                            TextButton(onClick = {
                                scope.launch {
                                    val outcome = withContext(Dispatchers.IO) {
                                        runCatching { WorkflowEngine.fire(context, row.id) }.getOrNull()
                                    }
                                    Toast.makeText(
                                        context,
                                        outcome?.let { "${it.status}${it.error?.let { e -> "：$e" } ?: ""}" }
                                            ?: "触发失败",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    rev++
                                }
                            }) { Text("立即运行", fontSize = 12.sp, color = Color(0xFF7A6A55)) }
                            TextButton(onClick = { confirmDelete = row.id }) {
                                Text("删除", fontSize = 12.sp, color = Color(0xFFB3564D))
                            }
                        }
                        // 最近运行流水
                        val runs = remember(row.id, rev) { WorkflowStore.lastRuns(context, row.id, 10) }
                        runs.forEach { r ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                                Text(
                                    SimpleDateFormat("MM-dd HH:mm:ss", Locale.US).format(Date(r.firedAtMs)),
                                    fontSize = 10.sp, color = Color(0xFF8A8279),
                                    modifier = Modifier.padding(end = 8.dp)
                                )
                                Text(
                                    statusLabel(r.status),
                                    fontSize = 10.sp,
                                    color = if (r.status == "SUCCESS") Color(0xFF4C7A4C)
                                    else if (r.status.startsWith("SKIPPED")) Color(0xFF8A8279)
                                    else Color(0xFFB3564D),
                                    modifier = Modifier.padding(end = 8.dp)
                                )
                                Text(
                                    (r.errorMessage ?: "").take(60),
                                    fontSize = 10.sp, color = Color(0xFF8A8279), modifier = Modifier.weight(1f)
                                )
                            }
                        }
                        def?.optJSONArray("actions")?.let { acts ->
                            Text(
                                "动作：" + (0 until acts.length()).joinToString(" → ") {
                                    acts.optJSONObject(it)?.optString("tool") ?: "?"
                                },
                                fontSize = 10.sp, color = Color(0xFF6F675E),
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    confirmDelete?.let { id ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("删除这个工作流？", fontSize = 16.sp) },
            text = { Text("定义和运行历史都会清掉，触发器随之卸下。", fontSize = 13.sp) },
            confirmButton = {
                TextButton(onClick = {
                    WorkflowStore.delete(context, id)
                    com.aion.chat.compose.data.WorkflowEngine.forget(id)
                    WorkflowTriggers.sync(context)
                    confirmDelete = null
                    rev++
                }) { Text("删除", color = Color(0xFFB3564D)) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("取消") } }
        )
    }
}

private fun statusLabel(s: String): String = when (s) {
    "SUCCESS" -> "成功"
    "FAILED" -> "失败"
    "SKIPPED_CONDITIONS" -> "条件未过"
    "SKIPPED_COOLDOWN" -> "冷却中"
    "SKIPPED_DAILY_CAP" -> "当日到顶"
    "SKIPPED_DISABLED" -> "已停用"
    else -> s
}

private fun triggerSummary(def: org.json.JSONObject): String {
    val t = def.optJSONObject("trigger") ?: return "?"
    return when (val type = t.optString("type")) {
        "time_cron" -> buildString {
            append("定时")
            t.optString("time_of_day").takeIf { it.isNotEmpty() }?.let {
                append(" 每天 $it")
                val days = t.optJSONArray("days_of_week")
                if (days != null && days.length() > 0) {
                    append("（" + (0 until days.length()).joinToString("、") { weekday(days.optInt(it)) } + "）")
                }
            }
            t.optString("cron").takeIf { it.isNotEmpty() }?.let { append(" cron: $it") }
        }
        "manual" -> "手动触发"
        "power_connected" -> "插上电源"
        "power_disconnected" -> "拔掉电源"
        "battery_below" -> "电量低于 ${t.optInt("threshold_percent")}%"
        "battery_above" -> "电量高于 ${t.optInt("threshold_percent")}%"
        "screen_on" -> "亮屏"
        "screen_off" -> "锁屏"
        "boot_completed" -> "开机完成"
        "headphones_plugged" -> "插入耳机"
        "headphones_unplugged" -> "拔出耳机"
        "wifi_connected" -> "连上 WiFi" + (t.optString("ssid")?.takeIf { it.isNotEmpty() }?.let { " $it" } ?: "")
        "wifi_disconnected" -> "断开 WiFi" + (t.optString("ssid")?.takeIf { it.isNotEmpty() }?.let { " $it" } ?: "")
        "notification_received" -> "收到通知" + (t.optString("package_name")?.takeIf { it.isNotEmpty() }?.let { "（$it）" } ?: "")
        "app_launched" -> "打开 " + (t.optString("package_name"))
        else -> type
    }
}

private fun weekday(iso: Int): String = when (iso) {
    1 -> "一"; 2 -> "二"; 3 -> "三"; 4 -> "四"; 5 -> "五"; 6 -> "六"; 7 -> "日"; else -> "?"
}
