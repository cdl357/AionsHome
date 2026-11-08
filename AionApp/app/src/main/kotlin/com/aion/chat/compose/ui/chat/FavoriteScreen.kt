package com.aion.chat.compose.ui.chat

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aion.chat.compose.data.FavoriteStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 收藏页（照橘瓣 FavoritePage）：他说的/你说的值得留的话，都在这。 */
private val FvBg = Color(0xFFFCFAF6)
private val FvCard = Color(0xFFFFFFFF)
private val FvInk = Color(0xFF33322E)
private val FvSub = Color(0xFF9A968C)
private val FvAccent = Color(0xFFC9705F)

@Composable
fun FavoriteScreen(onBack: () -> Unit = {}) {
    val context = LocalContext.current
    var rev by remember { mutableIntStateOf(0) }
    var detail by remember { mutableStateOf<FavoriteStore.Item?>(null) }
    val items = remember(rev) { FavoriteStore.list(context) }

    Column(
        Modifier
            .fillMaxSize()
            .background(FvBg)
            .statusBarsPadding()
    ) {
        Row(verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Box(
                Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .clickable { onBack() },
                contentAlignment = Alignment.Center
            ) { Text("‹", fontSize = 22.sp, color = FvInk) }
            Spacer(Modifier.width(10.dp))
            Column {
                Text("收藏", fontSize = 19.sp, color = FvInk, fontWeight = FontWeight.SemiBold)
                Text("长按聊天气泡 → 收藏，值得留的话都在这", fontSize = 10.sp, color = FvSub)
            }
        }

        LazyColumn(Modifier.fillMaxSize()) {
            items(items, key = { it.id }) { item ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 5.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(FvCard)
                        .clickable { detail = item }
                        .padding(14.dp)
                ) {
                    Text(
                        item.text,
                        fontSize = 14.sp, color = FvInk, lineHeight = 21.sp,
                        maxLines = 6
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            SimpleDateFormat("yyyy年M月d日 HH:mm", Locale.CHINA).format(Date(item.createdAt)),
                            fontSize = 10.sp, color = FvSub
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            "复制", fontSize = 11.sp, color = FvAccent,
                            modifier = Modifier.clickable {
                                val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                                    as android.content.ClipboardManager
                                cm.setPrimaryClip(android.content.ClipData.newPlainText("fav", item.text))
                                android.widget.Toast.makeText(context, "已复制", android.widget.Toast.LENGTH_SHORT).show()
                            }.padding(horizontal = 6.dp)
                        )
                        Text(
                            "删除", fontSize = 11.sp, color = FvSub,
                            modifier = Modifier
                                .clickable { FavoriteStore.remove(context, item.id); rev++ }
                                .padding(horizontal = 6.dp)
                        )
                    }
                }
            }
            if (items.isEmpty()) {
                item {
                    Text(
                        "还没有收藏。\n\n长按聊天里想留住的气泡 → 收藏。",
                        fontSize = 13.sp, color = FvSub, lineHeight = 20.sp,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 80.dp, horizontal = 24.dp)
                    )
                }
            }
            item { Spacer(Modifier.height(60.dp)) }
        }
    }

    detail?.let { item ->
        AlertDialog(
            onDismissRequest = { detail = null },
            containerColor = FvCard,
            shape = RoundedCornerShape(20.dp),
            title = {
                Text(
                    SimpleDateFormat("yyyy年M月d日 HH:mm", Locale.CHINA).format(Date(item.createdAt)),
                    fontSize = 12.sp, color = FvSub, fontWeight = FontWeight.Medium
                )
            },
            text = {
                Text(
                    item.text,
                    fontSize = 14.sp, color = FvInk, lineHeight = 22.sp,
                    fontFamily = FontFamily.Serif,
                    modifier = Modifier
                        .height(300.dp)
                        .verticalScroll(androidx.compose.foundation.rememberScrollState())
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                        as android.content.ClipboardManager
                    cm.setPrimaryClip(android.content.ClipData.newPlainText("fav", item.text))
                    android.widget.Toast.makeText(context, "已复制全文", android.widget.Toast.LENGTH_SHORT).show()
                    detail = null
                }) { Text("复制全文", color = FvAccent) }
            },
            dismissButton = { TextButton(onClick = { detail = null }) { Text("关闭", color = FvSub) } }
        )
    }
}
