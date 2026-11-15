package com.aion.chat.compose.ui.us

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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
import com.aion.chat.compose.data.CheckinStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.time.LocalDate
import java.time.YearMonth

/**
 * 早安晚安打卡墙（v0.3.161，她出的框架五）：
 * 月历上每天两个小点——☀ 她先开口 / 🌙 他先开口。
 * 顶部连续打卡：她连续 X 天 · 他连续 Y 天 · 咱俩连续 Z 天。
 * 点某天看当天首末条时间和总数。
 */
@Composable
fun CheckinScreen(onBack: () -> Unit = {}) {
    var viewMonth by remember { mutableStateOf(YearMonth.now()) }
    var pickDate by remember { mutableStateOf<LocalDate?>(null) }
    val context = LocalContext.current

    val streaks = remember { CheckinStore.streaks(context) }

    Column(
        Modifier
            .fillMaxSize()
            .background(Color(0xFFF2F0EB))
    ) {
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
            "打卡墙",
            fontSize = 40.sp, color = Color(0xFF26211C), fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Text(
            "每天第一条消息谁发的，那天就算谁先开口。断一天就得从头连——看咱俩能连多少天",
            fontSize = 11.sp, color = Color(0xFF8A8279), lineHeight = 15.sp,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )

        // ── 连续天数卡 ──
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White)
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${streaks.yuriDays}", fontSize = 26.sp, color = Color(0xFF7A8BA6), fontWeight = FontWeight.Bold)
                Text("她连续找你", fontSize = 10.sp, color = Color(0xFF8A8279))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${streaks.seanDays}", fontSize = 26.sp, color = Color(0xFF7A6A55), fontWeight = FontWeight.Bold)
                Text("他连续在", fontSize = 10.sp, color = Color(0xFF8A8279))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${streaks.togetherDays}", fontSize = 26.sp, color = Color(0xFF26211C), fontWeight = FontWeight.Bold)
                Text("咱俩不断档", fontSize = 10.sp, color = Color(0xFF8A8279))
            }
        }

        // ── 月历卡 ──
        val ym = viewMonth
        val firstDay = ym.atDay(1)
        val daysInMonth = ym.lengthOfMonth()
        val leadingBlanks = (firstDay.dayOfWeek.value % 7)
        val monthData = remember(ym) { CheckinStore.monthData(context, ym.year, ym.monthValue) }
        val byDay = remember(monthData) { monthData.associateBy { it.date } }

        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Color.White)
                .padding(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("‹", fontSize = 20.sp, color = Color(0xFF576B95),
                    modifier = Modifier
                        .clickable { viewMonth = viewMonth.minusMonths(1) }
                        .padding(horizontal = 10.dp))
                Spacer(Modifier.weight(1f))
                Text(
                    "${ym.year} 年 ${ym.monthValue} 月",
                    fontSize = 16.sp, color = Color(0xFF26211C), fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.weight(1f))
                Text("›", fontSize = 20.sp, color = Color(0xFF576B95),
                    modifier = Modifier
                        .clickable { viewMonth = viewMonth.plusMonths(1) }
                        .padding(horizontal = 10.dp))
            }
            Spacer(Modifier.height(10.dp))
            val week = listOf("日", "一", "二", "三", "四", "五", "六")
            Row(Modifier.fillMaxWidth()) {
                week.forEach {
                    Text(it, fontSize = 12.sp, color = Color(0xFF8A8279),
                        modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
            }
            Spacer(Modifier.height(6.dp))
            val cells = mutableListOf<LocalDate?>()
            repeat(leadingBlanks) { cells.add(null) }
            for (d in 1..daysInMonth) cells.add(ym.atDay(d))
            for (row in cells.chunked(7)) {
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                    for (i in 0 until 7) {
                        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            val d = row.getOrNull(i)
                            if (d == null) {
                                Box(Modifier.size(44.dp))
                            } else {
                                val checkin = byDay[d.toString()]
                                val isToday = d == LocalDate.now()
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(
                                            when {
                                                checkin != null && checkin.firstRole == "user" -> Color(0x227A8BA6)
                                                checkin != null -> Color(0x227A6A55)
                                                else -> Color.Transparent
                                            }
                                        )
                                        .border(
                                            1.dp,
                                            if (isToday) Color(0xFF7A6A55) else Color.Transparent,
                                            CircleShape
                                        )
                                        .clickable { if (checkin != null) pickDate = d }
                                ) {
                                    Text(
                                        d.dayOfMonth.toString(),
                                        fontSize = 12.sp,
                                        color = Color(0xFF26211C),
                                        fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal
                                    )
                                    if (checkin != null) {
                                        Text(
                                            if (checkin.firstRole == "user") "☀" else "🌙",
                                            fontSize = 10.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "☀ 她先开口  ·  🌙 他先开口  ·  点有标记的日子看当天详情",
                fontSize = 10.sp, color = Color(0xFF8A8279),
                modifier = Modifier.padding(start = 4.dp)
            )
        }

        // ── 点某天的详情 ──
        pickDate?.let { d ->
            val info = byDay[d.toString()]
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White)
                    .padding(14.dp)
            ) {
                if (info == null) {
                    Text("那天没聊天", fontSize = 13.sp, color = Color(0xFF8A8279))
                } else {
                    Text(
                        "${d.monthValue} 月 ${d.dayOfMonth} 日 · " +
                                (if (info.firstRole == "user") "她先找的" else "他先开口"),
                        fontSize = 13.sp, color = Color(0xFF26211C), fontWeight = FontWeight.Medium
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "第一条 " + SimpleDateFormat("HH:mm", Locale.US)
                            .format(java.util.Date(info.firstAtMs)) +
                                " · 最后一条 " + SimpleDateFormat("HH:mm", Locale.US)
                            .format(java.util.Date(info.lastAtMs)) +
                                " · 当天聊了 " + info.count + " 条",
                        fontSize = 11.sp, color = Color(0xFF8A8279)
                    )
                }
            }
        }
    }
}
