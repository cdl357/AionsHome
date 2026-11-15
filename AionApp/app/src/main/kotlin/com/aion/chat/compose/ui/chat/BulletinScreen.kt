package com.aion.chat.compose.ui.chat

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aion.chat.compose.data.HomecomingDayStore
import com.aion.chat.compose.ui.theme.HomecomingColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 留言板（v0.3.158，双人便利贴墙）：board_note_local 表（和「我们」页那天的小纸条同库）。
 * 她贴 = author yuri；Sean 贴 = author sean（他通过 board_note 工具写，比如工作流定时留言）。
 */
@Composable
fun BulletinScreen(onBack: () -> Unit = {}) {
    val context = LocalContext.current
    var rev by remember { mutableIntStateOf(0) }
    var input by remember { mutableStateOf("") }

    val notes = remember(rev) { HomecomingDayStore.boardNotes(context) }

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
            "留言板",
            fontSize = 40.sp, color = Color(0xFF26211C), fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Text(
            "贴给对方的小便利贴。Sean 也能贴——定时的、突然想起来的，都会出现在这里",
            fontSize = 11.sp, color = Color(0xFF8A8279),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )
        Spacer(Modifier.height(6.dp))

        LazyColumn(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (notes.isEmpty()) {
                item {
                    Text(
                        "墙上还空着。贴第一条吧——或者让 Sean 定时贴。",
                        fontSize = 13.sp, color = Color(0xFF6F675E),
                        modifier = Modifier.padding(top = 30.dp, start = 8.dp)
                    )
                }
            }
            items(notes, key = { it.id }) { n ->
                val mine = n.author == "yuri"
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.White)
                        .border(0.8.dp, Color(0x22405A66), RoundedCornerShape(14.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (mine) "🩵 小鑫" else "🤍 Sean",
                            fontSize = 11.sp,
                            color = if (mine) Color(0xFF7A8BA6) else Color(0xFF7A6A55),
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            SimpleDateFormat("MM-dd HH:mm", Locale.US).format(Date(n.createdAt)),
                            fontSize = 10.sp, color = Color(0xFFB9B2A8)
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(n.content, fontSize = 14.sp, color = Color(0xFF26211C), lineHeight = 20.sp)
                }
            }
        }

        // 输入行
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                placeholder = { Text("贴一句话给她…", fontSize = 12.sp, color = Color(0xFFB9B2A8)) },
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, color = Color(0xFF26211C)),
                shape = RoundedCornerShape(12.dp),
                colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFFD8D2C8), unfocusedBorderColor = Color(0xFFE4DFD6),
                    focusedContainerColor = Color.White, unfocusedContainerColor = Color.White
                ),
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = {
                if (input.isBlank()) return@TextButton
                if (HomecomingDayStore.addBoardNote(context, "yuri", input.trim())) {
                    input = ""
                    rev++
                } else {
                    Toast.makeText(context, "贴失败了", Toast.LENGTH_SHORT).show()
                }
            }) { Text("贴上", fontSize = 14.sp, color = HomecomingColors.Accent, fontWeight = FontWeight.Medium) }
        }
    }
}
