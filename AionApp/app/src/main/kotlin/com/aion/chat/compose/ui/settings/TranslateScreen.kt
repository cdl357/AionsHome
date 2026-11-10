package com.aion.chat.compose.ui.settings

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aion.chat.compose.data.TranslateStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 翻译页（队列项）：源/目标语言 chips + 输入/输出 + 复制 + 最近记录。
 * 用当前聊天激活的模型（同一把线路），不带人设不带历史——纯翻译。
 */
@Composable
fun TranslateScreen(onBack: () -> Unit = {}) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var rev by remember { mutableIntStateOf(0) }

    val targets = listOf("中文", "English", "日本語", "한국어", "Français", "Deutsch", "Español", "Русский")
    var target by remember { mutableStateOf("中文") }
    var input by remember { mutableStateOf("") }
    var output by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }
    val history = remember(rev) { TranslateStore.history(context) }

    Column(
        Modifier
            .fillMaxSize()
            .background(Color(0xFFF2F0EB))
    ) {
        // 顶栏
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
            "翻译",
            fontSize = 40.sp, color = Color(0xFF26211C), fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Text(
            "用当前聊天的模型直译，不带人设。源语言自动检测。",
            fontSize = 11.sp, color = Color(0xFF8A8279),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp)
        ) {
            // 目标语言 chips
            Row(
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
            ) {
                targets.take(5).forEach { lang -> langChip(lang, target) { target = lang } }
            }
            Row(
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                targets.drop(5).forEach { lang -> langChip(lang, target) { target = lang } }
            }
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                placeholder = { Text("粘贴或输入要翻译的内容", fontSize = 13.sp, color = Color(0xFFB9B2A8)) },
                minLines = 4, maxLines = 10,
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp, color = Color(0xFF26211C)),
                shape = RoundedCornerShape(14.dp),
                colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFFD8D2C8), unfocusedBorderColor = Color(0xFFE4DFD6),
                    focusedContainerColor = Color.White, unfocusedContainerColor = Color.White
                ),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            Button(
                enabled = !busy && input.isNotBlank(),
                onClick = {
                    busy = true; err = null; output = ""
                    val src = input
                    val tgt = target
                    scope.launch {
                        val (result, error) = withContext(Dispatchers.IO) {
                            TranslateStore.translate(context, src, "", tgt)
                        }
                        busy = false
                        if (result != null) {
                            output = result
                            TranslateStore.add(context, TranslateStore.HistoryItem(
                                System.currentTimeMillis(), src, result, "", tgt))
                            rev++
                        } else {
                            err = error ?: "翻译失败"
                        }
                    }
                },
                shape = RoundedCornerShape(12.dp),
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF7A6A55), contentColor = Color.White
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (busy) {
                    CircularProgressIndicator(
                        color = Color.White, strokeWidth = 2.dp,
                        modifier = Modifier.size(16.dp).padding(2.dp)
                    )
                    Spacer(Modifier.size(8.dp))
                    Text("翻译中…", fontSize = 13.sp)
                } else Text("翻译 → $target", fontSize = 13.sp)
            }
            err?.let {
                Spacer(Modifier.height(6.dp))
                Text(it, fontSize = 12.sp, color = Color(0xFFB3564D))
            }
            if (output.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.White)
                        .padding(12.dp)
                ) {
                    Text(output, fontSize = 14.sp, color = Color(0xFF26211C), lineHeight = 20.sp)
                    Spacer(Modifier.height(8.dp))
                    Row {
                        TextButton(onClick = {
                            clipboard.setText(AnnotatedString(output))
                            Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
                        }) { Text("复制", fontSize = 12.sp, color = Color(0xFF7A6A55)) }
                    }
                }
            }
            if (history.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "最近记录", fontSize = 12.sp, color = Color(0xFF6F675E),
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        "清空", fontSize = 11.sp, color = Color(0xFFB3564D),
                        modifier = Modifier
                            .clickable { TranslateStore.clear(context); rev++ }
                            .padding(4.dp)
                    )
                }
                history.take(10).forEach { h ->
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.White)
                            .clickable {
                                input = h.source
                                output = h.result
                            }
                            .padding(10.dp)
                    ) {
                        Text(
                            h.source.replace("\n", " ").take(60),
                            fontSize = 11.sp, color = Color(0xFF8A8279), maxLines = 1
                        )
                        Text(
                            "→ " + h.result.replace("\n", " ").take(80),
                            fontSize = 12.sp, color = Color(0xFF26211C), maxLines = 2
                        )
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun langChip(lang: String, current: String, onPick: () -> Unit) {
    val picked = lang == current
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (picked) Color(0xFF7A6A55) else Color.White)
            .border(
                1.dp,
                if (picked) Color(0xFF7A6A55) else Color(0xFFE4DFD6),
                RoundedCornerShape(999.dp)
            )
            .clickable { onPick() }
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            lang, fontSize = 12.sp,
            color = if (picked) Color.White else Color(0xFF6F675E)
        )
    }
}
