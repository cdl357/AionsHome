package com.aion.chat.compose.ui.diary

import android.widget.Toast
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aion.chat.compose.data.HomecomingDayStore
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 我的日记（和 Sean 的信不一样）：这页是 Yuri 写给自己的。
 * 从书房点摊开的本子进来；只存这台手机（diary_local, author=user）。
 */
@Composable
fun DiaryWriteScreen(onBack: () -> Unit = {}) {
    val context = LocalContext.current
    val moodList = listOf("平静", "开心", "难过", "累累", "生气", "想你", "甜甜")
    var mood by remember { mutableStateOf("平静") }
    var title by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
    var entries by remember { mutableStateOf(emptyList<HomecomingDayStore.DiaryEntry>()) }
    var reload by remember { mutableStateOf(0) }
    var expandedId by remember { mutableStateOf<Long?>(null) }

    androidx.compose.runtime.LaunchedEffect(reload) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            entries = HomecomingDayStore.diaries(context).filter { it.author == "user" }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
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
                    tint = Color(0xFF4E4638), modifier = Modifier.size(19.dp)
                )
            }
            Column(modifier = Modifier.weight(1f).padding(start = 10.dp)) {
                Text("我的日记", fontSize = 18.sp, color = Color(0xFF4E4638), fontWeight = FontWeight.Medium)
                Text(
                    Instant.now().atZone(ZoneId.systemDefault())
                        .format(DateTimeFormatter.ofPattern("M月d日 EEEE")) + " · 写给你自己的，只存在这台手机上",
                    fontSize = 10.sp, color = Color(0xFF9A8F7E)
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        // ── 心情 ──
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            moodList.forEach { m ->
                val sel = m == mood
                Text(
                    m,
                    fontSize = 12.sp,
                    color = if (sel) Color.White else Color(0xFF6B6152),
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (sel) Color(0xFF8AA84E) else Color.White.copy(alpha = 0.7f))
                        .clickable { mood = m }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }

        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = title,
            onValueChange = { if (it.length <= 30) title = it },
            placeholder = { Text("今天想记成一句话的话…（可空）", fontSize = 12.sp, color = Color(0xFFB0A794)) },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = content,
            onValueChange = { content = it },
            placeholder = { Text("今天怎么样，随便写点什么。", fontSize = 13.sp, color = Color(0xFFB0A794)) },
            minLines = 7,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Text(
                "${content.length} 字",
                fontSize = 10.sp, color = Color(0xFFB0A794),
                modifier = Modifier.padding(top = 4.dp, end = 12.dp)
            )
            TextButton(
                onClick = {
                    if (content.isBlank()) {
                        Toast.makeText(context, "一个字都没写呢", Toast.LENGTH_SHORT).show()
                        return@TextButton
                    }
                    val t = (if (mood.isNotBlank()) "【$mood】" else "") + title.trim()
                    val ok = HomecomingDayStore.addDiary(
                        context, "user", t, content.trim(), System.currentTimeMillis()
                    )
                    if (ok) {
                        content = ""; title = ""
                        reload++
                        Toast.makeText(context, "写好了，收进抽屉了", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "没存上，再试一次", Toast.LENGTH_SHORT).show()
                    }
                }
            ) {
                Text("收进抽屉", color = Color(0xFF6E7E3A), fontWeight = FontWeight.Medium)
            }
        }

        // ── 旧日记 ──
        if (entries.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text(
                "以前的 " + entries.size + " 篇",
                fontSize = 13.sp, color = Color(0xFF4E4638), fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(6.dp))
            entries.forEach { e ->
                val open = expandedId == e.id
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = 0.6f))
                        .border(1.dp, Color.White.copy(alpha = 0.8f), RoundedCornerShape(12.dp))
                        .clickable { expandedId = if (open) null else e.id }
                        .padding(horizontal = 13.dp, vertical = 10.dp)
                ) {
                    Text(
                        Instant.ofEpochMilli(e.createdAt).atZone(ZoneId.systemDefault())
                            .format(DateTimeFormatter.ofPattern("yyyy.M.d")) +
                            if (e.title.isNotBlank()) " · " + e.title else "",
                        fontSize = 12.sp, color = Color(0xFF4E4638), fontWeight = FontWeight.Medium,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        e.content,
                        fontSize = 11.sp, lineHeight = 16.sp, color = Color(0xFF6B6152),
                        maxLines = if (open) 40 else 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.height(7.dp))
            }
        }
        Spacer(Modifier.height(110.dp))
    }
}
