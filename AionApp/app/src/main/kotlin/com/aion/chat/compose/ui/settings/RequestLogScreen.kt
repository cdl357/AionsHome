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
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aion.chat.compose.data.AppCrashLog
import com.aion.chat.compose.data.RequestLog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 请求日志（照橘瓣的 Logs 页）：app 发出的每次 HTTP——方法/时间/URL/Status/耗时；
 * 崩溃记录也挂在同一个列表底部（方法位显示 CRASH），一键清空。
 */
@Composable
fun RequestLogScreen(onBack: () -> Unit = {}) {
    val context = LocalContext.current
    var rev by remember { mutableIntStateOf(0) }
    var detail by remember { mutableStateOf<String?>(null) }

    val entries = remember(rev) { RequestLog.list(context) }
    val crashes = remember(rev) { parseCrashes(context) }

    Column(
        Modifier
            .fillMaxSize()
            .background(Color(0xFFF2F0EB))
    ) {
        // 顶栏：返回 + 清空
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
            Icon(
                Icons.Outlined.Delete, contentDescription = "清空日志",
                tint = Color(0xFF26211C),
                modifier = Modifier
                    .size(24.dp)
                    .clickable {
                        RequestLog.clear(context)
                        AppCrashLog.clear(context)
                        rev++
                    }
                    .padding(4.dp)
            )
        }
        Text(
            "Logs",
            fontSize = 40.sp, color = Color(0xFF26211C), fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Spacer(Modifier.height(6.dp))

        LazyColumn(Modifier.fillMaxSize()) {
            items(entries, key = { "req_" + it.ts + "_" + it.url }) { e ->
                LogCard(
                    method = e.method,
                    time = fmtTime(e.ts),
                    url = e.url,
                    status = if (e.code in 200..299) "Status: ${e.code}"
                             else if (e.code == 0) "Status: —" else "Status: ${e.code}",
                    ms = "${e.ms}ms",
                    failed = e.code !in 200..299,
                    err = e.err.ifBlank { null },
                    onTap = {
                        detail = "${e.method} ${fmtTime(e.ts)}\n${e.url}\nStatus: ${e.code} · ${e.ms}ms" +
                            (if (e.err.isNotBlank()) "\n\n" + e.err else "")
                    }
                )
            }
            items(crashes, key = { "crash_" + it.first }) { (time, text) ->
                LogCard(
                    method = "CRASH",
                    time = time,
                    url = text.lineSequence().firstOrNull()?.take(120) ?: text.take(120),
                    status = text.lineSequence().drop(1).firstOrNull()?.take(60) ?: "",
                    ms = "",
                    failed = true,
                    err = text,
                    onTap = { detail = "[$time] $text" }
                )
            }
            if (entries.isEmpty() && crashes.isEmpty()) {
                item {
                    Text(
                        "还没有请求记录。\n发条消息 / 刷新朋友圈 / 拉一次模型列表，这里就会有。",
                        fontSize = 13.sp, color = Color(0xFF9A9388), lineHeight = 20.sp,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 60.dp, horizontal = 24.dp)
                    )
                }
            }
            item { Spacer(Modifier.height(80.dp)) }
        }
    }

    // 点卡片看完整内容（错误体/堆栈）
    if (detail != null) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { detail = null },
            containerColor = Color(0xFFF7F5F1),
            shape = RoundedCornerShape(20.dp),
            title = { Text("日志详情", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF26211C)) },
            text = {
                Text(
                    detail ?: "",
                    fontSize = 11.sp, fontFamily = FontFamily.Monospace, lineHeight = 15.sp,
                    color = Color(0xFF4A3F34),
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .height(300.dp)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                        as android.content.ClipboardManager
                    cm.setPrimaryClip(android.content.ClipData.newPlainText("log", detail))
                    detail = null
                }) { Text("复制", color = Color(0xFF7FA6C9)) }
            },
            dismissButton = {
                TextButton(onClick = { detail = null }) { Text("关闭", color = Color(0xFF9A9388)) }
            }
        )
    }
}

@Composable
private fun LogCard(
    method: String,
    time: String,
    url: String,
    status: String,
    ms: String,
    failed: Boolean,
    err: String?,
    onTap: () -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xFFEDEBE6))
            .clickable { onTap() }
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                method,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.sp,
                color = when {
                    method == "CRASH" -> Color(0xFFC96B6B)
                    method == "GET" -> Color(0xFF7FA8D9)
                    else -> Color(0xFF7FA8D9)
                }
            )
            Spacer(Modifier.weight(1f))
            Text(time, fontSize = 11.sp, color = Color(0xFF6B5D4F))
        }
        Spacer(Modifier.height(3.dp))
        Text(
            url,
            fontSize = 13.sp,
            fontFamily = FontFamily.Monospace,
            color = Color(0xFF3A342C),
            lineHeight = 18.sp
        )
        Spacer(Modifier.height(3.dp))
        Row {
            Text(
                status,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                color = if (failed) Color(0xFFC96B6B) else Color(0xFF7FA8D9)
            )
            if (ms.isNotBlank()) {
                Spacer(Modifier.width(14.dp))
                Text(ms, fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = Color(0xFF3A342C))
            }
            if (err != null) {
                Spacer(Modifier.width(14.dp))
                Text(
                    "点看详情",
                    fontSize = 11.sp, color = Color(0xFFB4574E)
                )
            }
        }
    }
}

private fun fmtTime(ts: Long): String =
    SimpleDateFormat("HH:mm:ss", Locale.CHINA).format(Date(ts))

/** 崩溃文件按 "[MM-dd HH:mm:ss] " 前缀切成 (time, text) 列表，最新的在前。 */
private fun parseCrashes(context: android.content.Context): List<Pair<String, String>> =
    runCatching {
        val f = AppCrashLog.file(context)
        if (!f.exists()) return emptyList()
        val rx = Regex("^\\[(\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2})\\] ")
        val out = mutableListOf<Pair<String, String>>()
        var curTime: String? = null
        var cur = StringBuilder()
        f.readText().lines().forEach { line ->
            val m = rx.find(line)
            if (m != null) {
                if (curTime != null) out.add(curTime!! to cur.toString().trimEnd())
                curTime = m.groupValues[1]
                cur = StringBuilder(line.removePrefix(m.value))
            } else if (curTime != null) {
                cur.append('\n').append(line)
            }
        }
        if (curTime != null) out.add(curTime!! to cur.toString().trimEnd())
        out.reversed()
    }.getOrDefault(emptyList())
