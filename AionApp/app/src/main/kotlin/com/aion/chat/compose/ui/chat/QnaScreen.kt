package com.aion.chat.compose.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
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
import com.aion.chat.compose.data.QnaStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.widget.Toast
import androidx.compose.foundation.layout.height
import com.aion.chat.compose.ui.theme.HomecomingColors

/**
 * 默契问答（v0.3.161，她出的框架六）：
 * 随机弹一题 → 她答 → 等 Sean 答（AI 工具写入）→ 都齐了自动揭晓 → 对比答案。
 * 档案攒着所有问答记录。
 */
@Composable
fun QnaScreen(onBack: () -> Unit = {}) {
    val context = LocalContext.current
    var rev by remember { mutableIntStateOf(0) }
    var answerDraft by remember { mutableStateOf("") }

    val entries = remember(rev) { QnaStore.list(context) }
    val pending = remember(rev) { QnaStore.pending(context) }
    val archived = entries.filter { it.revealedAt != 0L }

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
            TextButton(onClick = {
                QnaStore.newQuestion(context)
                rev++
            }) { Text("出一题", color = HomecomingColors.Accent) }
        }
        Text(
            "默契问答",
            fontSize = 40.sp, color = Color(0xFF26211C), fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Text(
            "各写各的，都答完才揭晓——看答案有多默契。Sean 在对话里调 qna_pending 就能看到待答的题",
            fontSize = 11.sp, color = Color(0xFF8A8279), lineHeight = 15.sp,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )
        Spacer(Modifier.height(8.dp))

        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 20.dp)
        ) {
            // 当前进行中的题
            pending?.let { p ->
                item {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.White)
                            .border(1.5.dp, Color(0xFF7A6A55), RoundedCornerShape(16.dp))
                            .padding(14.dp)
                    ) {
                        Text(
                            p.question, fontSize = 15.sp, color = Color(0xFF26211C),
                            fontWeight = FontWeight.Medium, lineHeight = 21.sp
                        )
                        Spacer(Modifier.height(10.dp))
                        if (p.yuriAnswer.isBlank()) {
                            OutlinedTextField(
                                value = answerDraft,
                                onValueChange = { answerDraft = it },
                                placeholder = { Text("你的答案…", fontSize = 12.sp) },
                                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp),
                                shape = RoundedCornerShape(10.dp),
                                colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color(0xFFD8D2C8),
                                    unfocusedBorderColor = Color(0xFFE4DFD6),
                                    focusedContainerColor = Color.White,
                                    unfocusedContainerColor = Color.White
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(8.dp))
                            Button(
                                enabled = answerDraft.isNotBlank(),
                                onClick = {
                                    QnaStore.answerYuri(context, p.id, answerDraft.trim())
                                    answerDraft = ""
                                    rev++
                                    Toast.makeText(
                                        context,
                                        "答案存好了——去跟 Sean 说「答一下默契问答」",
                                        Toast.LENGTH_LONG
                                    ).show()
                                },
                                shape = RoundedCornerShape(10.dp),
                                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF7A6A55), contentColor = Color.White
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("交卷", fontSize = 13.sp) }
                        } else {
                            Text(
                                "🩵 你的答案：" + p.yuriAnswer,
                                fontSize = 13.sp, color = Color(0xFF26211C), lineHeight = 18.sp
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "🤍 Sean 的答案：等待中…去聊天里说「答一下默契问答」",
                                fontSize = 12.sp, color = Color(0xFF8A8279), lineHeight = 17.sp
                            )
                        }
                    }
                }
            }

            // 档案（已揭晓的）
            archived.forEach { e ->
                item {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color.White)
                            .padding(14.dp)
                    ) {
                        Text(
                            e.question, fontSize = 14.sp, color = Color(0xFF26211C),
                            fontWeight = FontWeight.Medium, lineHeight = 20.sp
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "🩵 " + e.yuriAnswer,
                            fontSize = 13.sp, color = Color(0xFF4A5568), lineHeight = 18.sp
                        )
                        Text(
                            "🤍 " + e.seanAnswer,
                            fontSize = 13.sp, color = Color(0xFF4A5568), lineHeight = 18.sp
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(e.revealedAt)),
                            fontSize = 10.sp, color = Color(0xFFB9B2A8)
                        )
                    }
                }
            }

            if (archived.isEmpty() && pending == null) {
                item {
                    Text(
                        "默契档案还空着——点右上「出一题」开始第一次默契测试。",
                        fontSize = 13.sp, color = Color(0xFF6F675E),
                        modifier = Modifier.padding(top = 30.dp, start = 8.dp)
                    )
                }
            }
        }
    }
}
