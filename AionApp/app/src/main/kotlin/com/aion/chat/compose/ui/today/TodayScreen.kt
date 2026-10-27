package com.aion.chat.compose.ui.today

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aion.chat.compose.data.TodayStore
import com.aion.chat.compose.ui.theme.HomecomingColors
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Today 页（交接文档功能一）：沈聿淮这段时间在做什么。只读、不发布、不联网。
 * 列表按日期分组（今天/昨天/M月d日），kind 决定图标，detail 可展开，mood 弱化小字。
 */
@Composable
fun TodayScreen(onBack: () -> Unit = {}) {
    val context = LocalContext.current
    val entries = remember { mutableStateOf(listOf<TodayStore.TodayEntry>()) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val loaded = TodayStore.list(context)
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { entries.value = loaded }
        }
    }
    var expandedIds by remember { mutableStateOf(setOf<Long>()) }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.55f))
                    .border(1.dp, Color.White.copy(alpha = 0.7f), CircleShape)
                    .clickable { onBack() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回",
                    tint = HomecomingColors.Ink, modifier = Modifier.size(19.dp)
                )
            }
            Column(modifier = Modifier.weight(1f).padding(start = 10.dp)) {
                Text("Today", fontSize = 18.sp, color = HomecomingColors.Ink, fontWeight = FontWeight.Medium)
                Text("他这段时间在做什么，摊开给你看", fontSize = 10.sp, color = HomecomingColors.InkSoft)
            }
        }

        if (entries.value.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("今天还没写什么。", fontSize = 13.sp, color = HomecomingColors.InkSoft)
            }
            return
        }

        // 按日期分组（列表已时间倒序）：组头 今天/昨天/M月d日
        val today = LocalDate.now()
        var lastGroupKey: String? = null
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 16.dp, end = 16.dp, top = 10.dp, bottom = 116.dp
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            entries.value.forEach { e ->
                val date = Instant.ofEpochMilli(e.timestamp).atZone(ZoneId.systemDefault()).toLocalDate()
                val groupKey = when {
                    date == today -> "今天"
                    date == today.minusDays(1) -> "昨天"
                    else -> date.format(DateTimeFormatter.ofPattern("M月d日"))
                }
                if (groupKey != lastGroupKey) {
                    item(key = "group_" + groupKey) {
                        Text(
                            groupKey,
                            fontSize = 12.sp, color = HomecomingColors.InkSoft,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(top = 10.dp, bottom = 2.dp, start = 4.dp)
                        )
                    }
                    lastGroupKey = groupKey
                }
                item(key = "entry_" + e.id) {
                    TodayRow(
                        entry = e,
                        expanded = e.id in expandedIds,
                        onToggle = {
                            expandedIds = if (e.id in expandedIds) expandedIds - e.id else expandedIds + e.id
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun TodayRow(entry: TodayStore.TodayEntry, expanded: Boolean, onToggle: () -> Unit) {
    val (icon, tint) = kindVisual(entry.kind)
    val clickableMod = if (entry.detail.isNullOrBlank()) Modifier else Modifier.clickable { onToggle() }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.6f))
            .border(1.dp, Color.White.copy(alpha = 0.75f), RoundedCornerShape(14.dp))
            .then(clickableMod)
            .padding(horizontal = 13.dp, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(tint.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = entry.kind, tint = tint, modifier = Modifier.size(16.dp))
            }
            Column(modifier = Modifier.weight(1f).padding(start = 10.dp)) {
                Text(
                    entry.title,
                    fontSize = 13.sp, color = HomecomingColors.Ink,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                entry.mood?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        it, fontSize = 10.sp, color = HomecomingColors.InkSoft,
                        fontStyle = FontStyle.Italic,
                        modifier = Modifier.padding(top = 1.dp)
                    )
                }
            }
            Text(
                timeLabel(entry.timestamp),
                fontSize = 10.sp, color = HomecomingColors.InkSoft
            )
        }
        if (expanded && !entry.detail.isNullOrBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(
                entry.detail!!,
                fontSize = 12.sp, lineHeight = 18.sp, color = HomecomingColors.InkSoft,
                modifier = Modifier.padding(start = 40.dp, top = 2.dp)
            )
        }
    }
}

/** kind → 图标 + 色。 */
private fun kindVisual(kind: String): Pair<ImageVector, Color> = when (kind) {
    TodayStore.Kind.THINKING -> Icons.Outlined.Psychology to Color(0xFF8E6AA8)
    TodayStore.Kind.READING -> Icons.Outlined.MenuBook to Color(0xFF5C7A99)
    TodayStore.Kind.DOING -> Icons.Filled.Build to Color(0xFF6E7E3A)
    TodayStore.Kind.WAITING -> Icons.Filled.Schedule to Color(0xFFC9963C)
    TodayStore.Kind.MISSING -> Icons.Filled.FavoriteBorder to Color(0xFFC97B7B)
    else -> Icons.Outlined.Psychology to HomecomingColors.InkSoft
}

/** 今天显示 HH:mm，更早显示 MM-dd HH:mm（文档规定）。 */
private fun timeLabel(ms: Long): String {
    val z = ZoneId.systemDefault()
    val date = Instant.ofEpochMilli(ms).atZone(z).toLocalDate()
    val time = DateTimeFormatter.ofPattern("HH:mm").format(Instant.ofEpochMilli(ms).atZone(z))
    return if (date == LocalDate.now()) time
    else DateTimeFormatter.ofPattern("MM-dd").format(date) + " " + time
}
