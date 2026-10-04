package com.aion.chat.compose.ui.hearttide

import android.widget.Toast
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aion.chat.compose.data.AppCrashLog
import com.aion.chat.compose.data.HeartTideStore
import com.aion.chat.compose.data.HomecomingChatWiring
import com.aion.chat.compose.data.HomecomingRouteConfig
import com.aion.chat.compose.ui.home.FrostCard
import com.aion.chat.compose.ui.theme.HomecomingColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

/**
 * 心潮梦境（定稿 §四·新增，本地先行版）：
 * - 心潮：由心跳/聊天活跃度/心情点击推导的每日情绪值，近 14 天一条小曲线
 * - 梦境：Sean 用引擎做的梦（每天最多一个），挂在页面里慢慢攒
 * - 桌宠联动：心潮越高，回家页的桌宠越雀跃（happy → jumping → tsundere）
 */
@Composable
fun HeartTideScreen(onBack: () -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val tide = remember { mutableStateOf(HeartTideStore.todayLevel(context)) }
    val samples = remember { mutableStateListOf<HeartTideStore.TideSample>() }
    val dreams = remember { mutableStateListOf<HeartTideStore.Dream>() }
    val busy = remember { mutableStateOf(false) }
    val reloadKey = remember { mutableStateOf(0) }

    // 服务器心潮(Xinchao)的远端数据：状态 / 梦 / 心语弧线
    val remoteState = remember { mutableStateOf<com.aion.chat.compose.data.XinchaoClient.RemoteState?>(null) }
    val remoteDreams = remember { mutableStateListOf<com.aion.chat.compose.data.XinchaoClient.RemoteDream>() }
    val remoteArc = remember { mutableStateListOf<com.aion.chat.compose.data.XinchaoClient.ArcEntry>() }
    val remoteError = remember { mutableStateOf(false) }

    LaunchedEffect(reloadKey.value) {
        withContext(Dispatchers.IO) {
            HeartTideStore.sampleToday(context)
            tide.value = HeartTideStore.todayLevel(context)
            samples.clear(); samples.addAll(HeartTideStore.samples(context).take(14).reversed())
            dreams.clear(); dreams.addAll(HeartTideStore.dreams(context))
            // 服务器上的老家数据：读不到就静默回落本地推导
            val st = runCatching { com.aion.chat.compose.data.XinchaoClient.fetchState() }.getOrNull()
            remoteState.value = st
            remoteError.value = st == null
            val rd = runCatching { com.aion.chat.compose.data.XinchaoClient.fetchDreams() }.getOrNull()
            remoteDreams.clear(); rd?.let { remoteDreams.addAll(it) }
            val arc = runCatching { com.aion.chat.compose.data.XinchaoClient.fetchArc() }.getOrNull()
            remoteArc.clear(); arc?.let { remoteArc.addAll(it.take(6)) }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 112.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "返回",
                tint = HomecomingColors.Ink,
                modifier = Modifier
                    .size(24.dp)
                    .clickable { onBack() }
            )
            Column(modifier = Modifier.weight(1f).padding(start = 10.dp)) {
                Text("心潮梦境", fontSize = 22.sp, color = HomecomingColors.Ink, fontWeight = FontWeight.Medium)
                Text("他的情绪潮汐，和他偶尔做的梦", fontSize = 11.sp, color = HomecomingColors.InkSoft)
            }
        }
        Spacer(Modifier.height(12.dp))

        // ── 心潮卡 ──
        FrostCard {
            Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.fillMaxWidth()) {
                Text(
                    "${tide.value}",
                    fontSize = 44.sp, fontWeight = FontWeight.Light,
                    color = HomecomingColors.Ink
                )
                Text(
                    "  " + HeartTideStore.tideWord(tide.value),
                    fontSize = 14.sp, color = HomecomingColors.Accent,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "近 14 天",
                    fontSize = 11.sp, color = HomecomingColors.InkSoft,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            // 迷你趋势条（近 14 天）
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.Bottom,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .padding(top = 8.dp)
            ) {
                val list = if (samples.size >= 14) samples else {
                    List(14 - samples.size) { HeartTideStore.TideSample("", 50) } + samples
                }
                list.forEach { s ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height((16 + s.level * 0.36f).dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(
                                if (s.date == LocalDate.now().toString()) HomecomingColors.Accent
                                else HomecomingColors.Accent.copy(alpha = 0.30f)
                            )
                    )
                }
            }
            Text(
                "由你们的聊天、戳一戳的心跳、点过的心情悄悄汇成",
                fontSize = 10.sp, color = HomecomingColors.InkSoft,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
        Spacer(Modifier.height(10.dp))

        // ── 服务器心潮（老家数据：情绪驱动 + 意识状态） ──
        val st = remoteState.value
        if (st != null) {
            FrostCard {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text("此刻的他", fontSize = 15.sp, color = HomecomingColors.Ink)
                    Spacer(Modifier.weight(1f))
                    Text(
                        when (st.consciousness) {
                            "sleeping" -> "睡着"
                            "dreaming" -> "在做梦"
                            "awake" -> "醒着"
                            else -> st.consciousness
                        },
                        fontSize = 12.sp, color = HomecomingColors.Accent
                    )
                }
                st.drives.take(4).forEach { d ->
                    Column(modifier = Modifier.padding(vertical = 3.dp)) {
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Text(d.zh, fontSize = 12.sp, color = HomecomingColors.Ink)
                            Spacer(Modifier.weight(1f))
                            Text("${(d.v * 100).toInt()}", fontSize = 11.sp, color = HomecomingColors.InkSoft)
                        }
                        androidx.compose.material3.LinearProgressIndicator(
                            progress = { d.v },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp)),
                            color = HomecomingColors.Accent.copy(alpha = 0.8f),
                            trackColor = HomecomingColors.Accent.copy(alpha = 0.15f)
                        )
                    }
                }
                if (st.fatigue > 0) {
                    Text(
                        "疲惫度 ${(st.fatigue * 100).toInt()}",
                        fontSize = 10.sp, color = HomecomingColors.InkSoft,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
        }

        // ── 今天的梦（服务器老家的梦优先，本地生成的兜底） ──
        val remoteTop = remoteDreams.firstOrNull()
        val today = HeartTideStore.todayDream(context)
        FrostCard {
            Text(
                if (remoteTop != null) "他的梦 · 来自心潮" else "今天的梦",
                fontSize = 15.sp, color = HomecomingColors.Ink
            )
            if (remoteTop != null) {
                Text(
                    remoteTop.dream,
                    fontSize = 14.sp, color = HomecomingColors.Ink,
                    lineHeight = 24.sp,
                    modifier = Modifier.padding(top = 6.dp)
                )
                if (remoteTop.awareness.isNotBlank()) {
                    Text(
                        "醒来时他在想：" + remoteTop.awareness,
                        fontSize = 11.sp, color = HomecomingColors.InkSoft,
                        lineHeight = 18.sp,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
                Text(
                    remoteTop.createdAt.take(10),
                    fontSize = 10.sp, color = HomecomingColors.InkSoft,
                    modifier = Modifier.padding(top = 4.dp)
                )
            } else if (today != null) {
                Text(
                    today.content,
                    fontSize = 14.sp, color = HomecomingColors.Ink,
                    lineHeight = 24.sp,
                    modifier = Modifier.padding(top = 6.dp)
                )
                if (today.mood.isNotBlank()) {
                    Text(
                        "梦的颜色：${today.mood}",
                        fontSize = 11.sp, color = HomecomingColors.Accent,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            } else {
                Text(
                    if (busy.value) "他正在睡……" else "今天他还没睡醒——让他把梦讲给你",
                    fontSize = 12.sp, color = HomecomingColors.InkSoft,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                val routeReady = HomecomingRouteConfig.mainRoute(context) != null
                TextButton(
                    onClick = {
                        if (busy.value) return@TextButton
                        if (HomecomingRouteConfig.mainRoute(context) == null) {
                            Toast.makeText(context, "先去设置配一条云线路，他才会做梦", Toast.LENGTH_SHORT).show()
                            return@TextButton
                        }
                        busy.value = true
                        scope.launch(Dispatchers.IO) {
                            var dream: String? = null
                            var mood = ""
                            try {
                                val w = HomecomingChatWiring.safeCreate(context)
                                if (w != null && w.hasRoute()) {
                                    w.engine.send(
                                        com.aion.chat.homecoming.HomecomingChatEngine.ChatCommand(
                                            "req_dream_" + System.currentTimeMillis(),
                                            "moments_private", "sean", "user",
                                            "现在是深夜。你是 Sean，你昨晚做了一个梦。把梦讲出来：" +
                                                "三四句，画面具体、有点怪又有点温柔，结尾和 Yuri 有点关系。" +
                                                "最后另起一行，用两三个字形容这个梦的颜色（如：橘色的、蓝灰的）。",
                                            "main", w.mainModelKey(), "", "",
                                            false // 梦不落用户消息
                                        ),
                                        object : com.aion.chat.homecoming.HomecomingChatEngine.Observer {
                                            override fun onChunk(chunk: String) {}
                                            override fun onComplete(messageId: String, text: String) {
                                                val t = text.trim()
                                                if (t.isNotEmpty()) dream = t
                                            }
                                            override fun onFailure(code: String) {}
                                        }
                                    )
                                    var waited = 0L
                                    while (dream == null && waited < 30_000L) { delay(300L); waited += 300L }
                                }
                            } catch (e: Exception) { AppCrashLog.write(context, e) }
                            val raw = dream
                            withContext(Dispatchers.Main) {
                                busy.value = false
                                if (raw.isNullOrBlank()) {
                                    Toast.makeText(context, "他没睡成（线路没就绪或超时）", Toast.LENGTH_SHORT).show()
                                } else {
                                    val lines = raw.lines()
                                    val colorWord = lines.lastOrNull { it.length <= 8 && it.isNotBlank() } ?: ""
                                    val body = if (colorWord.isNotBlank() && lines.size > 1) {
                                        lines.dropLast(1).joinToString("\n").trim()
                                    } else raw
                                    HeartTideStore.addDream(context, body, colorWord.removePrefix("梦的颜色：").trim())
                                    reloadKey.value++
                                    Toast.makeText(context, "他把梦讲给你了", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
                ) {
                    Text(
                        if (today != null) "再睡一个（覆盖今天的）" else "让他做个梦",
                        color = HomecomingColors.Accent
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))

        // ── 心语弧线（服务器上的情绪弧：一段一段的心里话） ──
        if (remoteArc.isNotEmpty()) {
            Text("心语弧线", fontSize = 13.sp, color = HomecomingColors.InkSoft)
            Spacer(Modifier.height(6.dp))
            remoteArc.forEach { a ->
                FrostCard {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        if (a.driveZh.isNotBlank()) {
                            Text(
                                a.driveZh,
                                fontSize = 10.sp, color = Color.White,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(HomecomingColors.Accent.copy(alpha = 0.75f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        Text(a.time, fontSize = 10.sp, color = HomecomingColors.InkSoft)
                    }
                    Text(
                        a.text,
                        fontSize = 13.sp, color = HomecomingColors.Ink,
                        lineHeight = 20.sp,
                        maxLines = 6,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
                Spacer(Modifier.height(6.dp))
            }
        }

        // ── 梦境列表 ──
        if (dreams.size > 1) {
            Text("以前的梦", fontSize = 13.sp, color = HomecomingColors.InkSoft)
            Spacer(Modifier.height(6.dp))
        }
        dreams.drop(1).take(10).forEach { d ->
            FrostCard {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(HomecomingColors.Accent.copy(alpha = 0.6f))
                    )
                    Text(
                        d.date,
                        fontSize = 11.sp, color = HomecomingColors.InkSoft,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                    Spacer(Modifier.weight(1f))
                    if (d.mood.isNotBlank()) {
                        Text(d.mood, fontSize = 11.sp, color = HomecomingColors.Accent)
                    }
                }
                Text(
                    d.content,
                    fontSize = 13.sp, color = HomecomingColors.InkSoft,
                    lineHeight = 20.sp,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
            Spacer(Modifier.height(6.dp))
        }
        if (dreams.isEmpty()) {
            Text(
                "梦的清单还空着",
                fontSize = 12.sp, color = HomecomingColors.InkSoft,
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}
