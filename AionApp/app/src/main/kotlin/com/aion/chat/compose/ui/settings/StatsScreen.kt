package com.aion.chat.compose.ui.settings

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aion.chat.compose.data.RequestLog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 统计页（照橘瓣 StatsPage）：token 用量可视化——总量/命中率/按天分布。
 * 数据源 = 请求日志（聊天请求的 prompt/缓存/补全 token）。
 */
private val StBg = Color(0xFFFCFAF6)
private val StCard = Color(0xFFFFFFFF)
private val StInk = Color(0xFF33322E)
private val StSub = Color(0xFF9A968C)
private val StAccent = Color(0xFF7FA6C9)
private val StGreen = Color(0xFF4CAF50)
private val StLine = Color(0xFFEDE7DC)

@Composable
fun StatsScreen(onBack: () -> Unit = {}) {
    val context = LocalContext.current
    var rev by remember { mutableIntStateOf(0) }
    val entries = remember(rev) { RequestLog.list(context) }

    val chat = entries.filter { it.method == "POST" && it.promptTokens > 0 }
    val pSum = chat.sumOf { it.promptTokens }
    val cSum = chat.sumOf { it.cachedTokens }
    val oSum = chat.sumOf { it.completionTokens }
    val pct = if (pSum > 0) (cSum * 100 / pSum).toInt() else 0
    val mcpCalls = entries.count { it.method == "MCP" }

    // 最近 7 个有数据的天
    val byDay = chat.groupBy {
        SimpleDateFormat("M月d日", Locale.CHINA).format(Date(it.ts))
    }
    val days = byDay.entries.take(7)

    Column(
        Modifier
            .fillMaxSize()
            .background(StBg)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
    ) {
        Row(verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Box(
                Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(StCard)
                    .clickable { onBack() },
                contentAlignment = Alignment.Center
            ) { Text("‹", fontSize = 22.sp, color = StInk) }
            Spacer(Modifier.width(10.dp))
            Column {
                Text("统计", fontSize = 19.sp, color = StInk, fontWeight = FontWeight.SemiBold)
                Text("token 用量与缓存命中（来自请求日志）", fontSize = 10.sp, color = StSub)
            }
        }

        // ── 汇总卡 ──
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(StCard)
                .padding(16.dp)
        ) {
            Text("总量", fontSize = 15.sp, color = StInk, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(10.dp))
            Row {
                StatCell("聊天请求", "${chat.size}", "次")
                Spacer(Modifier.width(8.dp))
                StatCell("提示 token", "$pSum", "")
                Spacer(Modifier.width(8.dp))
                StatCell("补全 token", "$oSum", "")
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("缓存命中率", fontSize = 13.sp, color = StSub)
                Spacer(Modifier.width(12.dp))
                Text("$pct%", fontSize = 22.sp, color = StGreen, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(8.dp))
                Text("($cSum / $pSum tok)", fontSize = 11.sp, color = StSub)
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "命中的部分按缓存价计费（约 1 折），高命中 = 快 + 省",
                fontSize = 10.sp, color = StSub
            )
        }

        Spacer(Modifier.height(12.dp))

        // ── 按天 ──
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(StCard)
                .padding(16.dp)
        ) {
            Text("按天", fontSize = 15.sp, color = StInk, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(10.dp))
            if (days.isEmpty()) {
                Text("还没有数据。发条消息，第二天这里有柱子。", fontSize = 12.sp, color = StSub)
            }
            days.forEach { (day, list) ->
                val dp = list.sumOf { it.promptTokens }
                val dc = list.sumOf { it.cachedTokens }
                val frac = if (dp > 0) dc.toFloat() / dp else 0f
                Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    Row {
                        Text(day, fontSize = 12.sp, color = StInk)
                        Spacer(Modifier.weight(1f))
                        Text(
                            "${dc}/${dp} tok · ${list.size} 次",
                            fontSize = 10.sp, color = StSub, fontFamily = FontFamily.Monospace
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    // 条：绿 = 缓存命中部分，浅 = 全量
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(Color(0xFFEFF3EC))
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth(frac.coerceIn(0f, 1f))
                                .height(10.dp)
                                .clip(RoundedCornerShape(5.dp))
                                .background(StGreen.copy(alpha = 0.75f))
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // ── 工具 ──
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(StCard)
                .padding(16.dp)
        ) {
            Text("工具", fontSize = 15.sp, color = StInk, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Text(
                "MCP / 记忆工具调用：$mcpCalls 次",
                fontSize = 13.sp, color = StSub
            )
        }

        Spacer(Modifier.height(16.dp))
        Text(
            "数据来自请求日志（保留最近 200 条）。",
            fontSize = 10.sp, color = StSub,
            modifier = Modifier.padding(horizontal = 20.dp)
        )
        Spacer(Modifier.height(60.dp))
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.StatCell(label: String, value: String, unit: String) {
    Column(Modifier.weight(1f)) {
        Text(value + unit, fontSize = 18.sp, color = StInk, fontWeight = FontWeight.SemiBold)
        Text(label, fontSize = 11.sp, color = StSub)
    }
}


