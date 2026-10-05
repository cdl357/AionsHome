package com.aion.chat.compose.ui.more

import androidx.compose.foundation.background
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Water
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.widget.Toast
import com.aion.chat.compose.ui.home.FrostCard
import com.aion.chat.compose.ui.theme.HomecomingColors

private data class MoreEntry(val icon: androidx.compose.ui.graphics.vector.ImageVector, val name: String, val sub: String)

/**
 * 页面⑤：更多（功能总入口）——按交接文档 §8 只做这些：
 * 相册 / 留言板 / 记忆库 / 语音通话 / 陪伴阅读 / 提醒闹钟 / 设置。其余 AionsHome 功能全部不做。
 */
@Composable
fun MoreScreen(
    onOpenSettings: () -> Unit = {},
    onOpenAlbum: () -> Unit = {},
    onOpenMemories: () -> Unit = {},
    onOpenReading: () -> Unit = {},
    onOpenReminders: () -> Unit = {},
    onOpenCall: () -> Unit = {},
    onOpenHeartTide: () -> Unit = {},
    onOpenBoard: () -> Unit = {}
) {
    val context = LocalContext.current
    val entries = listOf(
        MoreEntry(Icons.Filled.Favorite, "相册", "照片墙 + Sean 存这张时的心里话"),
        MoreEntry(Icons.Filled.MailOutline, "留言板", "双向便利贴 · 点开可对话"),
        MoreEntry(Icons.Filled.Person, "记忆库", "看 · 搜 · 加 · 改 · 删"),
        MoreEntry(Icons.Filled.Call, "语音通话", "打电话给 Sean · 可抢话"),
        MoreEntry(Icons.Filled.Water, "心潮梦境", "他的情绪潮汐与梦"),
        MoreEntry(Icons.Filled.Info, "陪伴阅读", "导入 TXT · Sean 陪你读"),
        MoreEntry(Icons.Filled.Settings, "提醒 / 闹钟", "到点让 AI 主动戳你"),
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 112.dp)
    ) {
        Text("更多", fontSize = 22.sp, color = HomecomingColors.Ink)
        Text("功能总入口", fontSize = 12.sp, color = HomecomingColors.InkSoft)
        Spacer(Modifier.height(14.dp))
        entries.forEach { entry ->
            FrostCard(modifier = Modifier.padding(vertical = 6.dp), onClick = {
                when (entry.name) {
                    "相册" -> onOpenAlbum()
                    "记忆库" -> onOpenMemories()
                    "陪伴阅读" -> onOpenReading()
                    "提醒 / 闹钟" -> onOpenReminders()
                    "语音通话" -> onOpenCall()
                    "心潮梦境" -> onOpenHeartTide()
                    "留言板" -> onOpenBoard()
                    else -> Toast.makeText(context, "${entry.name}：阶段二起逐个接线", Toast.LENGTH_SHORT).show()
                }
            }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(HomecomingColors.IceBlue.copy(alpha = 0.5f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(entry.icon, contentDescription = entry.name, tint = HomecomingColors.Ink)
                    }
                    Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(entry.name, fontSize = 15.sp, color = HomecomingColors.Ink)
                        Text(entry.sub, fontSize = 11.sp, color = HomecomingColors.InkSoft)
                    }
                }
            }
        }

        // 设置：真实入口（换背景图等）
        FrostCard(modifier = Modifier.padding(vertical = 6.dp), onClick = onOpenSettings) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(HomecomingColors.IceBlue.copy(alpha = 0.5f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Settings, contentDescription = "设置", tint = HomecomingColors.Ink)
                }
                Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                    Text("设置", fontSize = 15.sp, color = HomecomingColors.Ink)
                    Text("换背景图 · 云线路 · 模型 · TTS · 主题", fontSize = 11.sp, color = HomecomingColors.InkSoft)
                }
            }
        }
    }
}
