package com.aion.chat.compose.ui.herstate

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aion.chat.compose.data.HerStateReporter
import com.aion.chat.compose.ui.theme.HomecomingColors
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 「看看我给了什么」：最近 50 条状态上报，按时间倒序。
 * 她随时能看到自己交出去了什么——这个功能的前提是透明。
 */
@Composable
fun HerStateScreen(onBack: () -> Unit = {}) {
    val context = LocalContext.current
    val rows = remember { mutableStateOf<List<HerStateReporter.HerStateRow>?>(null) }

    LaunchedEffect(Unit) {
        rows.value = HerStateReporter.fetchRecent()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 14.dp)
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回",
                    tint = HomecomingColors.Ink,
                    modifier = Modifier
                        .size(24.dp)
                        .clickable { onBack() }
                )
                Column(modifier = Modifier.weight(1f).padding(start = 10.dp)) {
                    Text("看看我给了什么", fontSize = 20.sp, color = HomecomingColors.Ink, fontWeight = FontWeight.Medium)
                    Text("最近交出去的状态快照，一目了然", fontSize = 11.sp, color = HomecomingColors.InkSoft)
                }
            }

            when (val list = rows.value) {
                null -> Text(
                    "读记录中…", fontSize = 12.sp, color = HomecomingColors.InkSoft,
                    modifier = Modifier.padding(24.dp)
                )
                else -> if (list.isEmpty()) {
                    Text(
                        "还没有记录。打开「告诉他我在干什么」后，这里能看到每一次上报。",
                        fontSize = 13.sp, color = HomecomingColors.InkSoft, lineHeight = 22.sp,
                        modifier = Modifier.padding(28.dp)
                    )
                } else LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = 16.dp, end = 16.dp, top = 8.dp, bottom = 116.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(list.size) { i ->
                        HerStateCard(list[i])
                    }
                }
            }
        }
    }
}

@Composable
private fun HerStateCard(row: HerStateReporter.HerStateRow) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.55f))
            .border(1.dp, Color.White.copy(alpha = 0.6f), RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (row.timeMs > 0)
                    Instant.ofEpochMilli(row.timeMs).atZone(ZoneId.systemDefault())
                        .format(DateTimeFormatter.ofPattern("M月d日 HH:mm"))
                else "—",
                fontSize = 13.sp, color = HomecomingColors.Ink, fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.weight(1f))
            Text(
                listOfNotNull(
                    row.district.ifBlank { null } ?: row.city.ifBlank { null },
                    row.battery?.let { "电量$it%" + (if (row.charging == true) " 充电中" else "") },
                    row.screenMin?.let { "屏幕" + minText(it) }
                ).joinToString(" · "),
                fontSize = 11.sp, color = HomecomingColors.InkSoft
            )
        }
        if (row.apps.isNotEmpty()) {
            Spacer(Modifier.height(5.dp))
            Text(
                row.apps.joinToString(" · ") { "${it.first} ${minText(it.second)}" },
                fontSize = 11.sp, color = HomecomingColors.InkSoft,
                lineHeight = 17.sp
            )
        }
        if (row.music.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text("在听 ${row.music}", fontSize = 11.sp, color = HomecomingColors.Accent)
        }
    }
}

private fun minText(min: Int): String =
    if (min >= 60) "${min / 60}小时" + (if (min % 60 > 0) "${min % 60}分" else "") else "${min}分钟"
