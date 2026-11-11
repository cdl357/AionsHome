package com.aion.chat.compose.ui.settings

import android.widget.Toast
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
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
import com.aion.chat.compose.data.LocalLlamaEngine
import com.aion.chat.compose.data.LocalModelStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 本地模型页（队列项）：llama.cpp JNI。
 * ①模型库：三个 Qwen2.5 小杯（hf-mirror 直链，断点续传）
 * ②试聊台：加载后直接和本地模型对话（Qwen 模板，多轮，流式）
 * 聊天页接入 Sean 是下一步——先把引擎和下载链路跑通。
 */
@Composable
fun LocalModelScreen(onBack: () -> Unit = {}) {
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var rev by remember { mutableIntStateOf(0) }
    var dlKey by remember { mutableStateOf<String?>(null) }
    var dlDone by remember { mutableStateOf(0L) }
    var dlTotal by remember { mutableStateOf(1L) }
    val cancelled = remember { AtomicBoolean(false) }

    var session by remember { mutableStateOf(listOf<Pair<String, String>>()) }
    var chatIn by remember { mutableStateOf("") }
    var chatOut by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

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
            "本地模型",
            fontSize = 40.sp, color = Color(0xFF26211C), fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Text(
            "跑在手机里的 Qwen2.5，断网也能聊。不动聊天记录、不联网，只吃电和内存。先下载一个，然后在这里试聊。",
            fontSize = 11.sp, color = Color(0xFF8A8279), lineHeight = 15.sp,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp)
        ) {
            LocalModelStore.CATALOG.forEach { m ->
                val downloaded = LocalModelStore.isDownloaded(context, m)
                val loaded = LocalLlamaEngine.loadedFile() == LocalModelStore.localFile(context, m).absolutePath
                val dlProg = LocalModelStore.progress(context, m.key)
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 5.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.White)
                        .padding(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(m.label, fontSize = 15.sp, color = Color(0xFF26211C))
                            Text(
                                "${m.sizeBytes / 1024 / 1024}MB · ${m.note}" +
                                        if (loaded) " · 已加载" else if (downloaded) " · 已下载" else "",
                                fontSize = 11.sp, color = Color(0xFF8A8279)
                            )
                        }
                        when {
                            dlKey == m.key -> {
                                Text(
                                    "${dlDone / 1024 / 1024}/${dlTotal / 1024 / 1024}MB",
                                    fontSize = 11.sp, color = Color(0xFF7A6A55),
                                    modifier = Modifier.padding(end = 8.dp)
                                )
                                OutlinedButton(onClick = {
                                    cancelled.set(true)
                                }) { Text("停止", fontSize = 11.sp, color = Color(0xFFB3564D)) }
                            }
                            downloaded -> Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                if (!loaded) {
                                    Button(
                                        onClick = {
                                            busy = true
                                            scope.launch {
                                                val ok = withContext(Dispatchers.IO) {
                                                    runCatching { LocalLlamaEngine.load(context, m) }.getOrDefault(false)
                                                }
                                                busy = false
                                                if (!ok) Toast.makeText(
                                                    context,
                                                    "加载失败：${LocalLlamaEngine.lastError}",
                                                    Toast.LENGTH_LONG
                                                ).show()
                                                rev++
                                            }
                                        },
                                        enabled = !busy,
                                        shape = RoundedCornerShape(10.dp),
                                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                            containerColor = Color(0xFF7A6A55), contentColor = Color.White
                                        )
                                    ) { Text("加载", fontSize = 11.sp) }
                                }
                                OutlinedButton(onClick = {
                                    if (loaded) {
                                        LocalLlamaEngine.release()
                                        rev++
                                    } else {
                                        scope.launch {
                                            withContext(Dispatchers.IO) { LocalModelStore.delete(context, m) }
                                            rev++
                                        }
                                    }
                                }) {
                                    Text(if (loaded) "卸载" else "删除", fontSize = 11.sp, color = Color(0xFFB3564D))
                                }
                            }
                            else -> Button(
                                onClick = {
                                    dlKey = m.key
                                    cancelled.set(false)
                                    dlTotal = m.sizeBytes
                                    scope.launch {
                                        val err = withContext(Dispatchers.IO) {
                                            LocalModelStore.download(context, m,
                                                onProgress = { d, t ->
                                                    dlDone = d; dlTotal = t
                                                },
                                                cancelled = { cancelled.get() })
                                        }
                                        dlKey = null
                                        if (err == null || err == "cancelled") {
                                            if (err == null) Toast.makeText(context, "下载完成", Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(context, "下载失败：$err", Toast.LENGTH_LONG).show()
                                        }
                                        rev++
                                    }
                                },
                                enabled = dlKey == null,
                                shape = RoundedCornerShape(10.dp),
                                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF7A6A55), contentColor = Color.White
                                )
                            ) { Text("下载", fontSize = 11.sp) }
                        }
                    }
                    if (dlKey == m.key) {
                        Spacer(Modifier.height(6.dp))
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(Color(0xFFE4DFD6))
                        ) {
                            Box(
                                Modifier
                                    .fillMaxWidth(
                                        if (dlTotal > 0) (dlDone.toFloat() / dlTotal).coerceIn(0f, 1f) else 0f
                                    )
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(Color(0xFF7A6A55))
                            )
                        }
                    }
                }
            }

            // ── 聊天页开关 ──
            val anyDownloaded = LocalModelStore.CATALOG.any { LocalModelStore.isDownloaded(context, it) }
            if (anyDownloaded) {
                Spacer(Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.White)
                        .padding(12.dp)
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("聊天里用本地模型", fontSize = 14.sp, color = Color(0xFF26211C))
                        Text(
                            "开=发消息走本地（断网也能聊，回得慢些、看不见图片）；关=恢复在线模型。发下一条消息时生效",
                            fontSize = 11.sp, color = Color(0xFF8A8279), lineHeight = 15.sp
                        )
                    }
                    androidx.compose.material3.Switch(
                        checked = LocalModelStore.useInChat(context),
                        onCheckedChange = { on ->
                            LocalModelStore.setUseInChat(context, on)
                            Toast.makeText(
                                context,
                                if (on) "已切换到本地模型" else "已恢复在线模型",
                                Toast.LENGTH_SHORT
                            ).show()
                            rev++
                        }
                    )
                }
            }

            // ── 试聊台 ──
            val anyLoaded = LocalLlamaEngine.loadedFile() != null
            if (anyLoaded) {
                Spacer(Modifier.height(10.dp))
                Text("试聊台（当前本地模型）", fontSize = 13.sp, color = Color(0xFF26211C))
                if (chatOut.isNotBlank()) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 5.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White)
                            .padding(12.dp)
                    ) {
                        Text(chatOut, fontSize = 13.sp, color = Color(0xFF26211C), lineHeight = 19.sp)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { session = emptyList(); chatOut = "" }) {
                        Text("清空会话", fontSize = 11.sp, color = Color(0xFF7A6A55))
                    }
                    Text(
                        "多轮 ${session.size}",
                        fontSize = 10.sp, color = Color(0xFFB9B2A8),
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                    OutlinedTextField(
                        value = chatIn,
                        onValueChange = { chatIn = it },
                        placeholder = { Text("跟本地模型说点什么", fontSize = 12.sp, color = Color(0xFFB9B2A8)) },
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, color = Color(0xFF26211C)),
                        shape = RoundedCornerShape(12.dp),
                        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFFD8D2C8), unfocusedBorderColor = Color(0xFFE4DFD6),
                            focusedContainerColor = Color.White, unfocusedContainerColor = Color.White
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.size(8.dp))
                    Button(
                        enabled = !busy && chatIn.isNotBlank(),
                        onClick = {
                            val userMsg = chatIn
                            val history = session
                            chatIn = ""
                            busy = true
                            chatOut = ""
                            scope.launch {
                                val sb = StringBuilder()
                                val ok = withContext(Dispatchers.IO) {
                                    val prompt = StringBuilder()
                                    prompt.append("<|im_start|>system\n你是一个手机上的本地助手，回答简洁、口语化。<|im_end|>\n")
                                    history.forEach { (u, a) ->
                                        prompt.append("<|im_start|>user\n").append(u).append("<|im_end|>\n")
                                        prompt.append("<|im_start|>assistant\n").append(a).append("<|im_end|>\n")
                                    }
                                    prompt.append("<|im_start|>user\n").append(userMsg).append("<|im_end|>\n")
                                    prompt.append("<|im_start|>assistant\n")
                                    val n = LocalLlamaEngine.generate(
                                        prompt.toString(), maxTokens = 512, temp = 0.7f
                                    ) { piece ->
                                        sb.append(piece)
                                        chatOut = sb.toString()   // Compose snapshot 写入任意线程安全
                                        true
                                    }
                                    n >= 0
                                }
                                busy = false
                                if (ok) {
                                    session = history + (userMsg to sb.toString())
                                } else {
                                    Toast.makeText(
                                        context,
                                        "生成失败：${LocalLlamaEngine.lastError}",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF7A6A55), contentColor = Color.White
                        )
                    ) { Text(if (busy) "思考中" else "发送", fontSize = 12.sp) }
                }
                if (busy) {
                    CircularProgressIndicator(
                        color = Color(0xFF7A6A55), strokeWidth = 2.dp,
                        modifier = Modifier.size(16.dp).padding(2.dp)
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}
