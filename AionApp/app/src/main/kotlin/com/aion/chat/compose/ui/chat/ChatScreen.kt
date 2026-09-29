package com.aion.chat.compose.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aion.chat.compose.ui.home.GlassAvatar
import com.aion.chat.compose.ui.theme.HomecomingColors

/**
 * 页面③：聊天。阶段一骨架：顶栏（头像+名字+连接状态占位+语音通话图标）+ 输入栏。
 * 阶段三接线：HomecomingChatEngine / HomecomingModelGateway，分条冒泡 + 气泡皮肤库 + 背景图可换。
 */
@Composable
fun ChatScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(listOf(HomecomingColors.IceBlueLight, Color.White))
            )
    ) {
        // 顶部栏
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            GlassAvatar(initial = "S", size = 40)
            Column(modifier = Modifier.weight(1f).padding(start = 10.dp)) {
                Text("Sean", fontSize = 16.sp, color = HomecomingColors.Ink)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .background(HomecomingColors.Ok, CircleShape)
                    )
                    Text("  线路 · 待接入", fontSize = 11.sp, color = HomecomingColors.InkSoft)
                }
            }
            Icon(
                imageVector = Icons.Filled.Phone,
                contentDescription = "语音通话",
                tint = HomecomingColors.Ink,
                modifier = Modifier.size(22.dp)
            )
        }

        // 消息区（阶段三：分条冒泡）
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("消息气泡在这里冒出来", fontSize = 14.sp, color = HomecomingColors.InkSoft)
            Text(
                "分条冒泡 · 气泡皮肤可换 · 接线 HomecomingChatEngine（阶段三）",
                fontSize = 11.sp,
                color = HomecomingColors.InkSoft,
                modifier = Modifier.padding(top = 6.dp)
            )
        }

        // 底部输入栏
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = "",
                onValueChange = {},
                modifier = Modifier.weight(1f),
                placeholder = { Text("和 Sean 说点什么…", fontSize = 14.sp) },
                enabled = false
            )
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(HomecomingColors.WarmPink, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text("➤", color = Color.White)
            }
        }
    }
}
