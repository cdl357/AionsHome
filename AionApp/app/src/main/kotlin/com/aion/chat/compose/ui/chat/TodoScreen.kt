package com.aion.chat.compose.ui.chat

import android.widget.Toast
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Delete
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aion.chat.compose.data.TodoStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 待办清单（v0.3.158，照她拍的橘子岛功能）：
 * 双栏列表（未完成在上）+ 输入框 + 划掉完成 + 删除。
 * Sean 通过 todo_* 工具读写同一份数据。
 */
@Composable
fun TodoScreen(onBack: () -> Unit = {}) {
    val context = LocalContext.current
    var rev by remember { mutableIntStateOf(0) }
    var input by remember { mutableStateOf("") }

    val todos = remember(rev) { TodoStore.list(context) }
    val open = todos.filter { !it.done }
    val done = todos.filter { it.done }

    Column(
        Modifier
            .fillMaxSize()
            .background(Color(0xFFFCFAF6))
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
            TextButton(onClick = {
                TodoStore.clearDone(context)
                rev++
            }) { Text("清空已完成", fontSize = 12.sp, color = Color(0xFFB3564D)) }
        }
        Text(
            "待办",
            fontSize = 40.sp, color = Color(0xFF26211C), fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Text(
            "和 Sean 说「记一下，明天要…」他就会帮你加进来",
            fontSize = 11.sp, color = Color(0xFF8A8279),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )
        Spacer(Modifier.height(6.dp))

        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
            item {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    placeholder = { Text("加一件要做的事…", fontSize = 13.sp, color = Color(0xFFB9B2A8)) },
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFFD8D2C8), unfocusedBorderColor = Color(0xFFE4DFD6),
                        focusedContainerColor = Color.White, unfocusedContainerColor = Color.White
                    ),
                    trailingIcon = {
                        Text(
                            "＋", fontSize = 18.sp, color = Color(0xFF7A6A55),
                            modifier = Modifier
                                .clickable {
                                    if (input.isNotBlank()) {
                                        TodoStore.add(context, input)
                                        input = ""
                                        rev++
                                    }
                                }
                                .padding(horizontal = 8.dp)
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
            }
            items(open, key = { it.id }) { t ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White)
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .border(1.5.dp, Color(0xFF7A6A55), RoundedCornerShape(6.dp))
                            .clickable {
                                TodoStore.toggle(context, t.id)
                                rev++
                            },
                        contentAlignment = Alignment.Center
                    ) { }
                    Text(
                        t.text, fontSize = 14.sp, color = Color(0xFF26211C),
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 10.dp)
                    )
                    Text(
                        SimpleDateFormat("MM-dd", Locale.US).format(Date(t.createdAt)),
                        fontSize = 10.sp, color = Color(0xFFB9B2A8)
                    )
                    Icon(
                        Icons.Outlined.Delete, contentDescription = "删除",
                        tint = Color(0xFFB9B2A8), modifier = Modifier
                            .padding(start = 8.dp)
                            .clickable { TodoStore.delete(context, t.id); rev++ }
                            .size(16.dp)
                    )
                }
            }
            if (done.isNotEmpty()) {
                item {
                    Text(
                        "已完成", fontSize = 12.sp, color = Color(0xFF8A8279),
                        modifier = Modifier.padding(top = 12.dp, start = 4.dp)
                    )
                }
            }
            items(done, key = { "done" + it.id }) { t ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = 0.6f))
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF7A6A55))
                            .clickable {
                                TodoStore.toggle(context, t.id)
                                rev++
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.Check, contentDescription = null,
                            tint = Color.White, modifier = Modifier.size(14.dp)
                        )
                    }
                    Text(
                        t.text, fontSize = 13.sp, color = Color(0xFFB9B2A8),
                        textDecoration = TextDecoration.LineThrough,
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 10.dp)
                    )
                    Icon(
                        Icons.Outlined.Delete, contentDescription = "删除",
                        tint = Color(0xFFB9B2A8), modifier = Modifier
                            .padding(start = 8.dp)
                            .clickable { TodoStore.delete(context, t.id); rev++ }
                            .size(16.dp)
                    )
                }
            }
            if (open.isEmpty() && done.isEmpty()) {
                item {
                    Text(
                        "还没有待办。跟 Sean 说「记一下，周五要交报告」试试。",
                        fontSize = 13.sp, color = Color(0xFF6F675E),
                        modifier = Modifier.padding(top = 40.dp, start = 8.dp)
                    )
                }
            }
        }
    }
}
