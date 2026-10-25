package com.aion.chat.compose.ui.more

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Drafts
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Water
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.widget.Toast
import com.aion.chat.compose.ui.home.FrostCard
import com.aion.chat.compose.ui.theme.HomecomingColors

private data class MoreEntry(val icon: androidx.compose.ui.graphics.vector.ImageVector, val name: String, val sub: String)

/**
 * 页面⑤：更多（功能总入口）——按交接文档 §8 只做这些：
 * 相册 / 记忆库 / 语音通话 / 陪伴阅读 / 提醒闹钟 / 设置。其余 AionsHome 功能全部不做。
 */
@Composable
fun MoreScreen(
    onOpenSettings: () -> Unit = {},
    onOpenDiaryBox: () -> Unit = {},
    onOpenMemories: () -> Unit = {},
    onOpenReading: () -> Unit = {},
    onOpenReminders: () -> Unit = {},
    onOpenCall: () -> Unit = {},
    onOpenHeartTide: () -> Unit = {},
    onOpenHerState: () -> Unit = {}
) {
    val context = LocalContext.current
    val entries = listOf(
        MoreEntry(Icons.Filled.Drafts, "日记信盒", "翻页手账 · 一月一页回忆"),
        MoreEntry(Icons.Filled.Person, "记忆库", "看 · 搜 · 加 · 改 · 删"),
        MoreEntry(Icons.Filled.Call, "语音通话", "打电话给 Sean · 可抢话"),
        MoreEntry(Icons.Filled.Water, "心潮梦境", "他的情绪潮汐与梦"),
        MoreEntry(Icons.Filled.Info, "陪伴阅读", "导入 TXT · Sean 陪你读"),
        MoreEntry(Icons.Filled.Settings, "提醒 / 闹钟", "到点让 AI 主动戳你"),
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 112.dp)
    ) {
        Text("更多", fontSize = 22.sp, color = HomecomingColors.Ink)
        Text("功能总入口", fontSize = 12.sp, color = HomecomingColors.InkSoft)
        Spacer(Modifier.height(14.dp))
        entries.forEach { entry ->
            FrostCard(modifier = Modifier.padding(vertical = 6.dp), onClick = {
                when (entry.name) {
                    "日记信盒" -> onOpenDiaryBox()
                    "记忆库" -> onOpenMemories()
                    "陪伴阅读" -> onOpenReading()
                    "提醒 / 闹钟" -> onOpenReminders()
                    "语音通话" -> onOpenCall()
                    "心潮梦境" -> onOpenHeartTide()
                    else -> Toast.makeText(context, "${entry.name}：阶段二起逐个接线", Toast.LENGTH_SHORT).show()
                }
            }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(HomecomingColors.IceBlue.copy(alpha = 0.5f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(entry.icon, contentDescription = entry.name, tint = HomecomingColors.Ink)
                    }
                    Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(entry.name, fontSize = 15.sp, color = HomecomingColors.Ink)
                        Text(entry.sub, fontSize = 11.sp, color = HomecomingColors.InkSoft)
                    }
                }
            }
        }

        // ── 「告诉他我在干什么」状态上报（默认关；她随时能看到自己给了什么、随时收回） ──
        val herOn = remember { mutableStateOf(com.aion.chat.compose.data.HerStateReporter.isEnabled(context)) }
        val scope = androidx.compose.runtime.rememberCoroutineScope()
        val locPerm = androidx.activity.compose.rememberLauncherForActivityResult(
            androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
        ) { }
        FrostCard(modifier = Modifier.padding(vertical = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("告诉他我在干什么", fontSize = 15.sp, color = HomecomingColors.Ink)
                    Text("每 30 分钟给他一份状态快照（写日记做梦用）", fontSize = 11.sp, color = HomecomingColors.InkSoft)
                }
                androidx.compose.material3.Switch(
                    checked = herOn.value,
                    onCheckedChange = { on ->
                        herOn.value = on
                        com.aion.chat.compose.data.HerStateReporter.setEnabled(context, on)
                        if (on) scope.launch {
                            // 打开即报一次，让他马上「看见」
                            val ok = com.aion.chat.compose.data.HerStateReporter.collectAndReport(context, manual = true)
                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                Toast.makeText(
                                    context,
                                    if (ok) "已打开，这份他收到了" else "已打开（网络稍后补报）",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "记：电量/充电 · 位置只到区县（如 会昌县） · 今日屏幕时长 · 各 app 用了多久 · 在听的歌 · 最后解锁时间\n" +
                    "不记：经纬度 · 街道门牌 · app 里的内容 · 通知 · 截图。关掉就立刻停。",
                fontSize = 11.sp, color = HomecomingColors.InkSoft, lineHeight = 17.sp
            )
            Spacer(Modifier.height(8.dp))
            Row {
                if (!com.aion.chat.compose.data.HerStateReporter.hasUsageAccess(context)) {
                    Text(
                        "去开「使用情况访问」»",
                        fontSize = 12.sp, color = HomecomingColors.Accent,
                        modifier = Modifier
                            .clickable {
                                runCatching {
                                    context.startActivity(
                                        android.content.Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS)
                                    )
                                }
                            }
                            .padding(end = 16.dp, top = 4.dp, bottom = 4.dp)
                    )
                }
                if (!com.aion.chat.compose.data.HerStateReporter.hasCoarseLocation(context)) {
                    Text(
                        "给她大概位置（区县）»",
                        fontSize = 12.sp, color = HomecomingColors.Accent,
                        modifier = Modifier
                            .clickable {
                                locPerm.launch(android.Manifest.permission.ACCESS_COARSE_LOCATION)
                            }
                            .padding(end = 16.dp, top = 4.dp, bottom = 4.dp)
                    )
                }
                Text(
                    "现在报一次",
                    fontSize = 12.sp, color = HomecomingColors.Accent,
                    modifier = Modifier
                        .clickable {
                            scope.launch {
                                val ok = com.aion.chat.compose.data.HerStateReporter.collectAndReport(context, manual = true)
                                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                    Toast.makeText(context, if (ok) "报好了" else "网络不通，稍后再试", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                        .padding(end = 16.dp, top = 4.dp, bottom = 4.dp)
                )
                Text(
                    "看看我给了什么 »",
                    fontSize = 12.sp, color = HomecomingColors.Accent,
                    modifier = Modifier
                        .clickable { onOpenHerState() }
                        .padding(top = 4.dp, bottom = 4.dp)
                )
            }
        }

        // 设置：真实入口（换背景图等）
        FrostCard(modifier = Modifier.padding(vertical = 6.dp), onClick = onOpenSettings) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(HomecomingColors.IceBlue.copy(alpha = 0.5f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Settings, contentDescription = "设置", tint = HomecomingColors.Ink)
                }
                Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                    Text("设置", fontSize = 15.sp, color = HomecomingColors.Ink)
                    Text("换背景图 · 云线路 · 模型 · TTS · 主题", fontSize = 11.sp, color = HomecomingColors.InkSoft)
                }
            }
        }
    }
}
