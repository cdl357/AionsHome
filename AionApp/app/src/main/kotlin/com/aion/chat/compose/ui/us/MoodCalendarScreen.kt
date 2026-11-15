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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
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
import com.aion.chat.compose.data.MoodStore
import java.time.LocalDate
import java.time.YearMonth

/**
 * 情绪日历（v0.3.160，她出的框架四）：
 * 每天点一下心情（😊开心 😐一般 😢难过 😴累），不写字。
 * 月视图看整月分布；Sean 通过 mood_history 看最近的心情。
 */
@Composable
fun MoodCalendarScreen(onBack: () -> Unit = {}) {
    val context = LocalContext.current
    var rev by remember { mutableIntStateOf(0) }
    var viewMonth by remember { mutableStateOf(YearMonth.now()) }
    var pickDate by remember { mutableStateOf<LocalDate?>(null) }

    val today = LocalDate.now()
    val ym = viewMonth
    val firstDay = ym.atDay(1)
    val daysInMonth = ym.lengthOfMonth()
    // 周一=1..周日=7 → 格子偏移（周日起头：日 一 二 … 六）
    val leadingBlanks = (firstDay.dayOfWeek.value % 7)

    val moodFor: (LocalDate) -> Int? = { d ->
        MoodStore.get(context, d.toString())
    }

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
            "情绪日历",
            fontSize = 40.sp, color = Color(0xFF26211C), fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Text(
            "每天点一下今天的心情就行，不用写字。连着几天不开心，Sean 会知道的",
            fontSize = 11.sp, color = Color(0xFF8A8279),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )

        // ── 月历卡 ──
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
                                Box(Modifier.size(40.dp))
                            } else {
                                val mood = moodFor(d)
                                val isToday = d == today
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(
                                            when {
                                                isToday -> Color(0x227A6A55)
                                                mood != null -> Color(0x14F4ACB4)
                                                else -> Color.Transparent
                                            }
                                        )
                                        .border(
                                            1.dp,
                                            if (isToday) Color(0xFF7A6A55) else Color.Transparent,
                                            CircleShape
                                        )
                                        .clickable {
                                            if (d.isBefore(today) || d == today) {
                                                pickDate = d
                                            } else {
                                                // 未来日期不能预点
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            d.dayOfMonth.toString(),
                                            fontSize = 13.sp,
                                            color = if (isToday) Color(0xFF26211C) else Color(0xFF6F675E),
                                            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal
                                        )
                                        Text(
                                            mood?.let { MoodStore.EMOJIS[it] } ?: "",
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "点一个日子 → 选心情。今天点今天，过去了的日子也能补",
                fontSize = 10.sp, color = Color(0xFF8A8279),
                modifier = Modifier.padding(start = 4.dp)
            )
        }

        // ── 点日子弹出的心情选择 ──
        pickDate?.let { d ->
            val current = moodFor(d)
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White)
                    .padding(14.dp)
            ) {
                Text(
                    "${d.monthValue} 月 ${d.dayOfMonth} 日 今晚的心情",
                    fontSize = 13.sp, color = Color(0xFF26211C), fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
                    MoodStore.EMOJIS.forEachIndexed { idx, emoji ->
                        val selected = current == idx
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (selected) Color(0x227A6A55) else Color(0xFFF6F3EE))
                                .border(
                                    1.dp,
                                    if (selected) Color(0xFF7A6A55) else Color.Transparent,
                                    RoundedCornerShape(12.dp)
                                )
                                .clickable {
                                    MoodStore.set(context, d.toString(), idx)
                                    rev++
                                }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(emoji, fontSize = 22.sp)
                            Text(
                                MoodStore.LABELS[idx], fontSize = 10.sp,
                                color = if (selected) Color(0xFF7A6A55) else Color(0xFF8A8279)
                            )
                        }
                    }
                }
            }
        }

        // ── 本月小结 ──
        val counts = IntArray(4)
        for (d in 1..daysInMonth) {
            moodFor(ym.atDay(d))?.let { if (it in 0..3) counts[it]++ }
        }
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color.White.copy(alpha = 0.75f))
                .padding(12.dp)
        ) {
            Text("本月小结", fontSize = 12.sp, color = Color(0xFF26211C), fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(4.dp))
            Text(
                MoodStore.LABELS.mapIndexed { i, l -> l + " " + counts[i] + " 天" }
                    .joinToString(" · "),
                fontSize = 12.sp, color = Color(0xFF6F675E)
            )
        }
    }
}
