package com.aion.chat.compose.ui.call

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aion.chat.compose.data.HomecomingChatWiring
import com.aion.chat.compose.data.HomecomingRouteConfig
import com.aion.chat.compose.ui.common.AvatarPhoto
import com.aion.chat.compose.ui.theme.HomecomingColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * 语音通话 v1：全屏通话界面。
 * 点一下说话（系统语音识别）→ Sean 回复 → 自动朗读（读完回到待说状态）。
 * 通话内容走正常聊天时间线（main_private），挂断后聊天页能看到这段对话。
 */
@Composable
fun CallScreen(onHangUp: () -> Unit = {}) {
    val context = LocalContext.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()

    val route = remember { HomecomingRouteConfig.mainRoute(context) }
    val routeReady = route != null

    // 状态机：idle（等你说）→ thinking（Sean 在想）→ speaking（Sean 在说）
    var phase by remember { mutableStateOf(if (routeReady) "idle" else "noroute") }
    var lastReply by remember { mutableStateOf("") }
    var lastHeard by remember { mutableStateOf("") }
    var seconds by remember { mutableStateOf(0) }

    // 通话期间保持屏幕常亮
    DisposableEffect(Unit) {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

    // 通话计时
    LaunchedEffect(Unit) {
        while (true) { delay(1000L); seconds += 1 }
    }

    // TTS：读完自动回到待说状态
    val tts = remember { mutableStateOf<TextToSpeech?>(null) }
    LaunchedEffect(Unit) {
        runCatching {
            val engine = TextToSpeech(context) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    runCatching { tts.value?.language = Locale.CHINA }
                    tts.value?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {}
                        override fun onDone(utteranceId: String?) {
                            if (utteranceId == "call_reply") phase = "idle"
                        }
                        @Deprecated("Deprecated in Java")
                        override fun onError(utteranceId: String?) {
                            if (utteranceId == "call_reply") phase = "idle"
                        }
                    })
                }
            }
            tts.value = engine
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            runCatching { tts.value?.stop(); tts.value?.shutdown() }
            tts.value = null
        }
    }

    // 语音识别
    val voiceInputAvailable = remember {
        runCatching { SpeechRecognizer.isRecognitionAvailable(context) }.getOrDefault(false)
    }
    fun makeCall(text: String) {
        if (phase == "thinking" || phase == "speaking") return
        val w = HomecomingChatWiring.safeCreate(context)
        if (w == null || !w.hasRoute()) {
            phase = "noroute"
            return
        }
        phase = "thinking"
        scope.launch(Dispatchers.IO) {
            var reply: String? = null
            try {
                w.engine.send(
                    com.aion.chat.homecoming.HomecomingChatEngine.ChatCommand(
                        "req_call_" + System.currentTimeMillis(),
                        HomecomingChatWiring.TIMELINE,
                        HomecomingChatWiring.RESPONDER,
                        HomecomingChatWiring.USER,
                        text + "（这是语音通话里说的话，像打电话一样自然回应，不要提语音/文字）",
                        "main",
                        w.mainModelKey(), "", ""
                    ),
                    object : com.aion.chat.homecoming.HomecomingChatEngine.Observer {
                        override fun onChunk(chunk: String) {}
                        override fun onComplete(messageId: String, completeText: String) {
                            val t = completeText.trim()
                            if (t.isNotEmpty()) reply = t
                        }
                        override fun onFailure(code: String) {}
                    }
                )
                var waited = 0L
                while (reply == null && waited < 30_000L) { delay(300L); waited += 300L }
            } catch (e: Exception) { }
            val text2 = reply ?: "（线路没接上，挂断重打一次试试）"
            withContext(Dispatchers.Main) {
                lastReply = text2
                phase = "speaking"
                runCatching {
                    tts.value?.speak(text2, TextToSpeech.QUEUE_FLUSH, null, "call_reply")
                }
            }
        }
    }
    val speechLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val heard = result.data
            ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull().orEmpty().trim()
        if (heard.isBlank()) {
            phase = "idle"
            Toast.makeText(context, "没听清，再点一次说话", Toast.LENGTH_SHORT).show()
        } else {
            lastHeard = heard
            makeCall(heard)
        }
    }

    fun startListening() {
        if (phase != "idle") return
        if (!voiceInputAvailable) {
            Toast.makeText(context, "这台手机没有可用的语音识别服务", Toast.LENGTH_SHORT).show()
            return
        }
        phase = "listening"
        runCatching {
            speechLauncher.launch(
                Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN")
                    putExtra(RecognizerIntent.EXTRA_PROMPT, "和 Sean 说点什么…")
                }
            )
        }.onFailure {
            phase = "idle"
            Toast.makeText(context, "语音识别没能启动", Toast.LENGTH_SHORT).show()
        }
    }

    // ── 界面 ──
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF17262B), Color(0xFF223B43), Color(0xFF17262B))
                )
            )
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 68.dp)
        ) {
            // 头像 + 呼吸光环（thinking/speaking 时呼吸）
            Box(contentAlignment = Alignment.Center) {
                if (phase == "thinking" || phase == "speaking") {
                    val pulse = rememberInfiniteTransition(label = "pulse")
                    val ring by pulse.animateFloat(
                        initialValue = 1f, targetValue = 1.35f,
                        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
                        label = "ring"
                    )
                    val alpha by pulse.animateFloat(
                        initialValue = 0.30f, targetValue = 0.05f,
                        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
                        label = "ringA"
                    )
                    Box(
                        modifier = Modifier
                            .size((168 * ring).dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = alpha))
                    )
                }
                AvatarPhoto(who = "sean", initial = "S", size = 132.dp, strokeWidth = 3.dp)
            }
            Text(
                "Sean",
                fontSize = 26.sp, fontWeight = FontWeight.Medium, color = Color.White,
                modifier = Modifier.padding(top = 18.dp)
            )
            Text(
                when (phase) {
                    "idle" -> "通话中 · 点下方说话"
                    "listening" -> "在听你说…"
                    "thinking" -> "Sean 在想…"
                    "speaking" -> "Sean 在说…"
                    else -> "线路未配置，去设置配一条再打"
                },
                fontSize = 13.sp, color = Color.White.copy(alpha = 0.75f),
                modifier = Modifier.padding(top = 6.dp)
            )
            Text(
                String.format("%02d:%02d", seconds / 60, seconds % 60),
                fontSize = 12.sp, color = Color.White.copy(alpha = 0.5f),
                modifier = Modifier.padding(top = 4.dp)
            )
            if (lastHeard.isNotBlank()) {
                Text(
                    "你：" + lastHeard,
                    fontSize = 13.sp, color = Color.White.copy(alpha = 0.85f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp, vertical = 6.dp)
                )
            }
            if (lastReply.isNotBlank()) {
                Text(
                    "Sean：" + lastReply,
                    fontSize = 14.sp, color = Color.White,
                    lineHeight = 22.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp)
                )
            }
        }

        // ── 底部控制 ──
        Row(
            horizontalArrangement = Arrangement.spacedBy(56.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 64.dp)
        ) {
            // 挂断
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFE5484D))
                    .clickable { onHangUp() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.CallEnd, contentDescription = "挂断",
                    tint = Color.White, modifier = Modifier.size(30.dp)
                )
            }
            // 说话
            Box(
                modifier = Modifier
                    .size(76.dp)
                    .clip(CircleShape)
                    .background(if (phase == "idle") HomecomingColors.Ok else Color.White.copy(alpha = 0.25f))
                    .border(2.dp, Color.White.copy(alpha = 0.55f), CircleShape)
                    .clickable {
                        when (phase) {
                            "idle" -> startListening()
                            "speaking" -> runCatching { tts.value?.stop() }.also { phase = "idle" }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Mic, contentDescription = "说话",
                    tint = Color.White, modifier = Modifier.size(34.dp)
                )
            }
        }
        Text(
            "说话键：待机时点一下开始说 · Sean 说话时点一下打断",
            fontSize = 10.sp, color = Color.White.copy(alpha = 0.45f),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 34.dp)
        )
    }
}
