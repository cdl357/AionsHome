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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aion.chat.compose.ui.home.FrostCard
import com.aion.chat.compose.ui.theme.HomecomingColors
import java.time.LocalDate
import java.time.YearMonth

/**
 * 页面②：我们（日历时光机）。
 * 阶段一：整月格子 + 切月 + 点天弹底部抽屉（三张卡占位 + 新建入口说明）。
 * 纪念日专属图标（爱心/黑白猫/自定义）与内容接线在阶段二。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UsScreen() {
    var viewMonth by remember { mutableStateOf(YearMonth.now()) }
    var selected by remember { mutableStateOf<LocalDate?>(null) }
    val sheetState = rememberModalBottomSheetState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(HomecomingColors.IceBlueLight, Color.White)
                )
            )
            .padding(18.dp)
    ) {
        Text("我们", fontSize = 22.sp, color = HomecomingColors.Ink)
        Text("日历时光机 · 左右滑月份，点一天回去看看", fontSize = 12.sp, color = HomecomingColors.InkSoft)
        Spacer(Modifier.height(14.dp))

        FrostCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("‹", fontSize = 22.sp, modifier = Modifier.clickable { viewMonth = viewMonth.minusMonths(1) })
                Text(
                    text = "${viewMonth.year} 年 ${viewMonth.monthValue} 月",
                    fontSize = 16.sp,
                    color = HomecomingColors.Ink
                )
                Text("›", fontSize = 22.sp, modifier = Modifier.clickable { viewMonth = viewMonth.plusMonths(1) })
            }
            Spacer(Modifier.height(10.dp))
            WeekHeader()
            val firstDow = viewMonth.atDay(1).dayOfWeek.value % 7 // 周日=0
            val daysInMonth = viewMonth.lengthOfMonth()
            val cells = firstDow + daysInMonth
            val rows = (cells + 6) / 7
            val today = LocalDate.now()
            for (r in 0 until rows) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    for (c in 0 until 7) {
                        val idx = r * 7 + c
                        val day = idx - firstDow + 1
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(40.dp)
                                .padding(2.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (day in 1..daysInMonth) {
                                val date = viewMonth.atDay(day)
                                val isToday = date == today
                                val isSelected = date == selected
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(CircleShape)
                                        .background(
                                            when {
                                                isSelected -> HomecomingColors.Accent.copy(alpha = 0.35f)
                                                isToday -> HomecomingColors.IceBlue.copy(alpha = 0.55f)
                                                else -> Color.Transparent
                                            }
                                        )
                                        .clickable { selected = date },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        day.toString(),
                                        fontSize = 13.sp,
                                        color = HomecomingColors.Ink,
                                        fontFamily = if (isToday) FontFamily.Serif else FontFamily.Default
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "有日记 / 留言 / 纪念日的日子会标小圆点（内容接线在阶段二）",
                fontSize = 11.sp,
                color = HomecomingColors.InkSoft
            )
        }
    }

    if (selected != null) {
        ModalBottomSheet(onDismissRequest = { selected = null }, sheetState = sheetState) {
            DayDrawer(selected!!)
        }
    }
}

@Composable
private fun WeekHeader() {
    Row(modifier = Modifier.fillMaxWidth()) {
        listOf("日", "一", "二", "三", "四", "五", "六").forEach {
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Text(it, fontSize = 11.sp, color = HomecomingColors.InkSoft)
            }
        }
    }
}

/** 点开某天：底部抽屉按顺序三张卡（摘要 → 点开展开，阶段二接线）。 */
@Composable
private fun DayDrawer(date: LocalDate) {
    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
        Text(
            "${date.monthValue} 月 ${date.dayOfMonth} 日",
            fontSize = 18.sp,
            color = HomecomingColors.Ink
        )
        Spacer(Modifier.height(12.dp))
        listOf("① 那天 Sean 的日记", "② 那天两人的留言", "③ 那天的重要记忆摘要").forEach { title ->
            FrostCard(modifier = Modifier.padding(vertical = 6.dp)) {
                Text(title, fontSize = 14.sp, color = HomecomingColors.Ink)
                Text("点开卡片展开完整内容（内容接线在阶段二）", fontSize = 11.sp, color = HomecomingColors.InkSoft)
            }
        }
        FrostCard {
            Text("新建", fontSize = 14.sp, color = HomecomingColors.Ink)
            Text(
                "给这天加纪念日（可选爱心 / 白猫 / 黑猫 / 自定义图标，格子变色）或补写内容 —— 随后端表开放",
                fontSize = 11.sp,
                color = HomecomingColors.InkSoft,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}
