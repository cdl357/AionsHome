package com.aion.chat.compose.ui.us

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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aion.chat.compose.data.WishBottleStore
import com.aion.chat.compose.ui.theme.HomecomingColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 心愿瓶（v0.3.160，她出的框架三）：
 * 咱俩随时往里丢心愿，攒着；实现了标 ✅；无聊点「抽一个」随机看一张没实现的。
 * Sean 通过 wish_* 工具也往里丢（author=sean）。
 */
@Composable
fun WishBottleScreen(onBack: () -> Unit = {}) {
    val context = LocalContext.current
    var rev by remember { mutableIntStateOf(0) }
    var input by remember { mutableStateOf("") }
    var drawn by remember { mutableStateOf<WishBottleStore.Wish?>(null) }

    val wishes = remember(rev) { WishBottleStore.list(context) }
    val pending = wishes.filter { it.doneAt == 0L }
    val done = wishes.filter { it.doneAt != 0L }

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
            "心愿瓶",
            fontSize = 40.sp, color = Color(0xFF26211C), fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Text(
            "想一起做的事都丢进来，攒着。哪天实现了标 ✅。无聊的时候抽一个出来看看",
            fontSize = 11.sp, color = Color(0xFF8A8279), lineHeight = 15.sp,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )
        Spacer(Modifier.height(8.dp))

        // 抽一个（随机未实现）
        Box(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White.copy(alpha = 0.75f))
                    .border(0.8.dp, Color.White.copy(alpha = 0.85f), RoundedCornerShape(16.dp))
                    .clickable {
                        val w = WishBottleStore.drawRandom(context)
                        drawn = w
                        if (w == null) Toast.makeText(context, "瓶子空了——先丢几个心愿进去", Toast.LENGTH_SHORT).show()
                    }
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Text("🫙", fontSize = 22.sp)
                Spacer(Modifier.weight(1f))
                Text(
                    if (pending.isEmpty()) "瓶子还空着" else "摇一摇 · 抽一个（还剩 ${pending.size} 个）",
                    fontSize = 13.sp, color = Color(0xFF26211C), fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.weight(1f))
                Text("🫙", fontSize = 22.sp)
            }
        }

        // 抽中的心愿卡（浮在最上面）
        drawn?.let { w ->
            Spacer(Modifier.height(10.dp))
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFFFFF7E6))
                    .border(1.5.dp, Color(0xFFE0B35C), RoundedCornerShape(16.dp))
                    .padding(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("今天抽到了这个", fontSize = 10.sp, color = Color(0xFFB98A3C))
                    Spacer(Modifier.weight(1f))
                    Icon(
                        Icons.Filled.Close, contentDescription = "收起",
                        tint = Color(0xFFB98A3C), modifier = Modifier
                            .size(16.dp)
                            .clickable { drawn = null }
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "「" + w.text + "」",
                    fontSize = 16.sp, color = Color(0xFF26211C), fontWeight = FontWeight.Medium,
                    lineHeight = 22.sp
                )
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "来自 " + if (w.author == "sean") "Sean" else "小鑫",
                        fontSize = 11.sp, color = Color(0xFF8A8279)
                    )
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = {
                        WishBottleStore.markDone(context, w.id)
                        drawn = null
                        rev++
                    }) { Text("✅ 实现了！", color = Color(0xFF7A6A55)) }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // 输入行
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                placeholder = { Text("丢一个心愿进去…", fontSize = 12.sp, color = Color(0xFFB9B2A8)) },
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
                WishBottleStore.add(context, input.trim(), "yuri")
                input = ""
                rev++
            }) { Text("丢进去", color = HomecomingColors.Accent) }
        }

        LazyColumn(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 20.dp)
        ) {
            if (pending.isEmpty()) {
                item {
                    Text(
                        "没有攒着的心愿了——都实现完了 🎉",
                        fontSize = 13.sp, color = Color(0xFF6F675E),
                        modifier = Modifier.padding(top = 20.dp, start = 8.dp)
                    )
                }
            }
            items(pending, key = { it.id }) { w ->
                WishCard(w) {
                    WishBottleStore.markDone(context, w.id)
                    rev++
                }
            }
            if (done.isNotEmpty()) {
                item {
                    Text(
                        "已实现 ✅", fontSize = 12.sp, color = Color(0xFF8A8279),
                        modifier = Modifier.padding(top = 10.dp, start = 4.dp)
                    )
                }
            }
            items(done, key = { "done" + it.id }) { w ->
                WishCard(w, dimmed = true)
            }
        }
    }
}

@Composable
private fun WishCard(w: WishBottleStore.Wish, dimmed: Boolean = false, onDone: () -> Unit = {}) {
    val context = LocalContext.current
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = if (dimmed) 0.6f else 1f))
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "「" + w.text + "」",
                fontSize = 14.sp,
                color = if (dimmed) Color(0xFFB9B2A8) else Color(0xFF26211C),
                textDecoration = if (dimmed) TextDecoration.LineThrough else null,
                modifier = Modifier.weight(1f),
                lineHeight = 20.sp
            )
            if (w.doneAt != 0L) {
                Text("✅", fontSize = 14.sp)
            } else {
                TextButton(onClick = { onDone() }, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                    Text("✅ 实现了", fontSize = 11.sp, color = Color(0xFF7A6A55))
                }
            }
        }
        Text(
            SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(w.createdAt)) +
                    " · 来自 " + if (w.author == "sean") "Sean" else "小鑫",
            fontSize = 10.sp, color = Color(0xFFB9B2A8)
        )
    }
}
