package com.aion.chat.compose.ui.command

import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aion.chat.compose.data.CommandStore
import com.aion.chat.compose.ui.theme.HomecomingColors
import kotlinx.coroutines.delay

/**
 * 即时指令药丸（nest-instant-command 手势规范）：
 * - 右缘悬浮：标题 + 倒计时；超时转陶土色正向计时
 * - 轻点：展开大卡片，再点收回；上下拖：沿右缘移动并记住位置
 * - 左滑超过 64px：完成（记录用时，随下一条消息反馈给他）
 * - 长按约 0.6s：取消（不告诉他）
 * - 时间按墙钟算，切走再回来数字是对的
 */
@Composable
fun CommandPillHost() {
    val context = LocalContext.current
    val view = LocalView.current
    val density = LocalDensity.current
    // 组合期不碰任何可能抛初始化异常的东西：先空列表渲染，1 秒内由协程补上真实数据
    var cmds by remember { mutableStateOf(emptyList<CommandStore.Cmd>()) }
    var expandedId by remember { mutableStateOf<String?>(null) }
    var now by remember { mutableStateOf(System.currentTimeMillis()) }

    // 每秒走一次表（墙钟，后台时间也算）；pending 的读取在这里也是安全的
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            runCatching { cmds = CommandStore.pending(context) }
                .onFailure { com.aion.chat.compose.data.AppCrashLog.write(context, "CommandPill pending 失败: ${it.javaClass.name}: ${it.message ?: ""}") }
            delay(1000)
        }
    }

    if (cmds.isEmpty()) return
    val screenH = view.height.toFloat().coerceAtLeast(1f)

    cmds.take(3).forEachIndexed { idx, cmd ->
        val idxPx = with(density) { (58.dp * idx).toPx() }
        val overtime = cmd.presetSec > 0 && now > cmd.issuedMs + cmd.presetSec * 1000L
        val secs = if (overtime) ((now - cmd.issuedMs) / 1000L - cmd.presetSec).toInt()
        else if (cmd.presetSec > 0) (cmd.presetSec - (now - cmd.issuedMs) / 1000L).toInt()
        else ((now - cmd.issuedMs) / 1000L).toInt()
        val timeText = (if (overtime) "+" else "") + "%d:%02d".format(secs / 60, secs % 60)
        val expanded = expandedId == cmd.id

        var dragging by remember(cmd.id) { mutableStateOf(false) }
        var dragOffX by remember(cmd.id) { mutableStateOf(0f) }
        val shiftX by animateFloatAsState(
            targetValue = if (dragging) dragOffX else 0f,
            animationSpec = tween(180), label = "pillShift"
        )

        fun complete() {
            CommandStore.complete(context, cmd.id)
            cmds = CommandStore.pending(context)
            expandedId = null
            Toast.makeText(context, "记下了，他会知道你用了多久", Toast.LENGTH_SHORT).show()
        }
        fun cancel() {
            CommandStore.cancel(context, cmd.id)
            cmds = CommandStore.pending(context)
            expandedId = null
        }

        // 拖动状态：改 y 位置（存分数）；左滑分量超 64px 记完成
        var yFraction by remember(cmd.id) { mutableStateOf(CommandStore.pillY(context)) }

        Box(
            modifier = Modifier
                .fillMaxSize()
        ) {
            // ── 药丸本体 ──
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .graphicsLayer {
                        translationY = screenH * yFraction + idxPx
                        translationX = shiftX
                    }
                    .pointerInput(cmd.id) {
                        var accY = 0f
                        var horizMode = false
                        detectDragGestures(
                            onDragStart = { dragging = true; accY = 0f; dragOffX = 0f; horizMode = false },
                            onDragEnd = {
                                dragging = false
                                if (horizMode && dragOffX < -with(density) { 64.dp.toPx() }) {
                                    complete()
                                } else if (!horizMode) {
                                    CommandStore.savePillY(context, yFraction)
                                }
                                dragOffX = 0f
                            },
                            onDragCancel = { dragging = false; dragOffX = 0f }
                        ) { change, amt ->
                            change.consume()
                            if (!horizMode && kotlin.math.abs(amt.x) > kotlin.math.abs(amt.y) + 2f) horizMode = true
                            if (horizMode) {
                                dragOffX += amt.x
                            } else {
                                accY += amt.y
                                yFraction = (yFraction + amt.y / screenH).coerceIn(0.06f, 0.86f)
                            }
                        }
                    }
                    .pointerInput(cmd.id + "tap") {
                        detectTapGestures(
                            onTap = { expandedId = if (expanded) null else cmd.id },
                            onLongPress = { cancel() }
                        )
                    }
                    .clip(RoundedCornerShape(50))
                    .background(
                        if (overtime) Color(0xFFB85C38) else Color.White.copy(alpha = 0.88f)
                    )
                    .border(
                        1.dp,
                        if (overtime) Color(0xFF8F4128) else Color.White,
                        RoundedCornerShape(50)
                    )
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .background(
                                if (overtime) Color.White else HomecomingColors.Accent,
                                CircleShape
                            )
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        cmd.title,
                        fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        color = if (overtime) Color.White else HomecomingColors.Ink,
                        modifier = Modifier.width(64.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        timeText,
                        fontSize = 12.sp,
                        color = if (overtime) Color.White else HomecomingColors.Ink,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // ── 展开的大卡片 ──
            if (expanded) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .graphicsLayer { translationY = screenH * yFraction + idxPx }
                        .padding(end = 12.dp)
                        .width(238.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color(0xFFFBF9F4))
                        .border(1.dp, Color(0x33B85C38), RoundedCornerShape(18.dp))
                        .clickable { expandedId = null }
                        .padding(16.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            cmd.title,
                            fontSize = 16.sp, color = HomecomingColors.Ink, fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            timeText,
                            fontSize = 40.sp,
                            color = if (overtime) Color(0xFFB85C38) else HomecomingColors.Ink,
                            fontWeight = FontWeight.Light
                        )
                        Text(
                            if (cmd.presetSec > 0)
                                (if (overtime) "已超时（预设 ${cmd.presetSec}s）" else "预设 ${cmd.presetSec}s")
                            else "不限时，慢慢来",
                            fontSize = 10.sp, color = HomecomingColors.InkSoft
                        )
                        Spacer(Modifier.height(14.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(HomecomingColors.Accent)
                                    .clickable { complete() }
                                    .padding(vertical = 9.dp),
                                contentAlignment = Alignment.Center
                            ) { Text("做好了", fontSize = 13.sp, color = Color.White, fontWeight = FontWeight.Medium) }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFFF0EAE2))
                                    .clickable { cancel() }
                                    .padding(vertical = 9.dp),
                                contentAlignment = Alignment.Center
                            ) { Text("算了", fontSize = 13.sp, color = HomecomingColors.InkSoft) }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "左滑完成 · 长按取消 · 上下拖动挪位置",
                            fontSize = 9.sp, color = HomecomingColors.InkSoft
                        )
                    }
                }
            }
        }
    }
}
