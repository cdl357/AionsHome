package com.aion.chat.compose.ui.call

import android.Manifest
import android.content.pm.PackageManager
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.core.content.ContextCompat
import com.aion.chat.compose.ui.common.AvatarPhoto
import com.aion.chat.compose.ui.theme.HomecomingColors
import com.aion.chat.compose.voice.VoiceCallSession
import com.aion.chat.compose.voice.VoicePhase

/**
 * 语音通话界面（教程式）：UI 只做单一状态渲染入口（教程坑 11），
 * 所有状态由 VoiceCallSession 单向广播；按键只发指令，不各自猜状态。
 */
@Composable
fun CallScreen(onHangUp: () -> Unit = {}) {
    val context = LocalContext.current
    var session by remember { mutableStateOf<VoiceCallSession?>(null) }
    var permissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        permissionGranted = granted
        if (granted) session?.start()
    }

    // 会话随界面生灭；挂断/离开都要释放麦克风与 TTS
    DisposableEffect(permissionGranted) {
        if (permissionGranted && session == null) {
            val s = VoiceCallSession(context.applicationContext) { onHangUp() }
            session = s
            s.start()
        }
        onDispose {
            session?.close()
            session = null
        }
    }

    val ui = session?.state?.collectAsState()?.value

    // 通话计时（纯展示）
    var seconds by remember { mutableStateOf(0) }
    LaunchedEffect(ui?.phase) {
        if (ui?.phase != VoicePhase.NOROUTE) {
            while (true) {
                kotlinx.coroutines.delay(1000L)
                seconds += 1
            }
        }
    }

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
                .padding(top = 64.dp)
        ) {
            // 头像 + 呼吸光环（thinking/speaking/greeting 时呼吸）
            Box(contentAlignment = Alignment.Center) {
                if (ui != null && ui.phase in listOf(
                        VoicePhase.THINKING, VoicePhase.SPEAKING, VoicePhase.GREETING
                    )
                ) {
                    val pulse = rememberInfiniteTransition(label = "pulse")
                    val ring by pulse.animateFloat(
                        initialValue = 1f, targetValue = 1.35f,
                        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
                        label = "ring"
                    )
                    val ringA by pulse.animateFloat(
                        initialValue = 0.30f, targetValue = 0.05f,
                        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
                        label = "ringA"
                    )
                    Box(
                        modifier = Modifier
                            .size((168 * ring).dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = ringA))
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
                when (ui?.phase) {
                    VoicePhase.STANDBY -> "接通中…"
                    VoicePhase.GREETING -> "接通中…"
                    VoicePhase.LISTENING -> "我在听。说话就好"
                    VoicePhase.THINKING -> "Sean 在想…（此时开口可打断）"
                    VoicePhase.SPEAKING -> "Sean 在说…（开口可抢话）"
                    VoicePhase.CLOSING -> "再见…"
                    else -> if (permissionGranted) "线路未配置，去设置配一条再打" else "需要麦克风权限"
                },
                fontSize = 13.sp, color = Color.White.copy(alpha = 0.75f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 6.dp, start = 24.dp, end = 24.dp)
            )
            Text(
                String.format("%02d:%02d", seconds / 60, seconds % 60),
                fontSize = 12.sp, color = Color.White.copy(alpha = 0.5f),
                modifier = Modifier.padding(top = 4.dp)
            )
            if (!ui?.heard.isNullOrBlank()) {
                Text(
                    "你：" + ui!!.heard,
                    fontSize = 13.sp, color = Color.White.copy(alpha = 0.85f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp, vertical = 6.dp)
                )
            }
            if (!ui?.reply.isNullOrBlank()) {
                Text(
                    "Sean：" + ui!!.reply,
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
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(56.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 64.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFE5484D))
                    .clickable {
                        session?.hangUpNow() ?: onHangUp()
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.CallEnd, contentDescription = "挂断",
                    tint = Color.White, modifier = Modifier.size(30.dp)
                )
            }
            val canTalk = ui != null && ui.phase in listOf(
                VoicePhase.LISTENING, VoicePhase.SPEAKING, VoicePhase.THINKING
            )
            Box(
                modifier = Modifier
                    .size(76.dp)
                    .clip(CircleShape)
                    .background(
                        if (ui?.phase == VoicePhase.LISTENING) HomecomingColors.Ok
                        else Color.White.copy(alpha = 0.25f)
                    )
                    .border(2.dp, Color.White.copy(alpha = 0.55f), CircleShape)
                    .clickable {
                        when (ui?.phase) {
                            VoicePhase.LISTENING -> session?.restartListening()
                            VoicePhase.SPEAKING, VoicePhase.THINKING -> session?.interruptTurn("手动打断")
                            else -> {}
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Mic, contentDescription = "说话/打断",
                    tint = if (canTalk) Color.White else Color.White.copy(alpha = 0.4f),
                    modifier = Modifier.size(34.dp)
                )
            }
        }
        Text(
            "说完停一停他就会接 · Sean 说话时你开口就是抢话 · 红键挂断",
            fontSize = 10.sp, color = Color.White.copy(alpha = 0.45f),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 34.dp)
        )
    }

    // 权限：没给就申请
    LaunchedEffect(Unit) {
        if (!permissionGranted) {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
}
