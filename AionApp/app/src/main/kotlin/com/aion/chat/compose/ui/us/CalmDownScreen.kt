package com.aion.chat.compose.ui.us

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
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
import com.aion.chat.compose.data.FavoriteStore
import com.aion.chat.compose.data.HomecomingData
import androidx.compose.foundation.layout.Arrangement

/**
 * 争吵冷静角（v0.3.164，她框架九）：
 * 吵架的时候进来只看好话——收藏里挑的甜言蜜语 + 今日情话。
 * 看完气消一半再回去好好说话。
 */
@Composable
fun CalmDownScreen(onBack: () -> Unit = {}) {
    val context = LocalContext.current
    var rev by remember { mutableIntStateOf(0) }
    var quoteIdx by remember { mutableIntStateOf(0) }
    val favorites = remember(rev) {
        FavoriteStore.list(context).filter { it.text.isNotBlank() }.shuffled().take(10)
    }
    val quote = HomecomingData.quoteForToday()

    Column(
        Modifier
            .fillMaxSize()
            .background(Color(0xFFEDF0F3))
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
            "冷静一下",
            fontSize = 36.sp, color = Color(0xFF26211C), fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Text(
            "先深呼吸三秒，往下翻——都是他对你说过的话。",
            fontSize = 12.sp, color = Color(0xFF8A8279),
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Spacer(Modifier.height(12.dp))

        // 今日情话
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White)
                .border(1.dp, Color(0x22405A66), RoundedCornerShape(16.dp))
                .padding(16.dp)
        ) {
            Text("💬 今日情话", fontSize = 11.sp, color = Color(0xFF576B95))
            Spacer(Modifier.height(6.dp))
            Text(quote, fontSize = 15.sp, color = Color(0xFF26211C), lineHeight = 22.sp)
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = { quoteIdx++ }) {
                Text("换一句 ›", fontSize = 12.sp, color = Color(0xFF576B95))
            }
        }

        Spacer(Modifier.height(12.dp))
        Text(
            "他说的好话", fontSize = 14.sp, color = Color(0xFF26211C), fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Spacer(Modifier.height(6.dp))
        LazyColumn(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 20.dp)
        ) {
            if (favorites.isEmpty()) {
                item {
                    Text(
                        "收藏里还没有他的话——长按聊天里的气泡可以收藏。",
                        fontSize = 13.sp, color = Color(0xFF6F675E),
                        modifier = Modifier.padding(top = 20.dp)
                    )
                }
            }
            items(favorites, key = { it.id }) { f ->
                Text(
                    "「" + f.text + "」",
                    fontSize = 14.sp, color = Color(0xFF26211C), lineHeight = 22.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.White)
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                )
            }
        }

        // 回去好好说话
        Button(
            onClick = { onBack() },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .clip(RoundedCornerShape(12.dp)),
            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                containerColor = Color(0xFF576B95), contentColor = Color.White
            )
        ) { Text("好，回去好好说话 →", fontSize = 14.sp) }
        Spacer(Modifier.height(12.dp))
    }
}
