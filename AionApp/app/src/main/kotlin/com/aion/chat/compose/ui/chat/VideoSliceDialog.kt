package com.aion.chat.compose.ui.chat

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlin.math.roundToInt

/**
 * 视频切片弹窗（照橘子岛 VideoSliceDialog）：
 * 选完视频先选抽帧参数——按帧数（2~20，按时长推荐默认）/ 按间隔（1~30s），
 * 确认后按参数抽帧发给模型看。
 */
data class VideoSliceResult(val frameCount: Int, val intervalMs: Long)

object VideoSliceDefaults {
    fun defaultFrameCount(durationMs: Long): Int {
        val seconds = durationMs / 1000
        return when {
            seconds < 10 -> 3
            seconds < 30 -> 5
            seconds < 60 -> 8
            else -> maxOf(2, minOf(20, (seconds / 5).toInt()))
        }
    }
}

@Composable
fun VideoSliceDialog(
    videoPath: String,
    durationMs: Long,
    onConfirm: (VideoSliceResult) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val seconds = durationMs / 1000
    val defaultFrames = remember(durationMs) { VideoSliceDefaults.defaultFrameCount(durationMs) }

    var useFrameCountMode by remember { mutableStateOf(true) }
    var frameCount by remember(durationMs) { mutableIntStateOf(defaultFrames) }
    var intervalSec by remember(durationMs) {
        mutableIntStateOf(maxOf(1, (seconds / defaultFrames).toInt()))
    }

    val effectiveFrameCount = if (useFrameCountMode) frameCount
        else maxOf(2, (seconds / intervalSec).toInt())
    val effectiveIntervalMs = if (useFrameCountMode) {
        if (frameCount > 1) durationMs / frameCount else 0L
    } else intervalSec * 1000L

    val thumbnail = remember(videoPath) {
        runCatching {
            val f = java.io.File(videoPath)
            if (!f.exists()) return@runCatching null
            val r = android.media.MediaMetadataRetriever()
            return@runCatching try {
                r.setDataSource(f.absolutePath)
                r.frameAtTime?.let {
                    BitmapHelper.scale(it, 512).also { b -> if (b !== it) it.recycle() }
                }
            } finally { r.release() }
        }.getOrNull()
    }
    val durationFormatted = "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"

    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(Color.White)
                .padding(20.dp)
        ) {
            Text("视频切片", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF26211C))
            Spacer(Modifier.height(4.dp))
            Text("时长 $durationFormatted", fontSize = 13.sp, color = Color(0xFF9A9388))

            Spacer(Modifier.height(14.dp))

            thumbnail?.let {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .border(1.dp, Color(0xFFEDE7DC), RoundedCornerShape(10.dp))
                ) {
                    Image(
                        bitmap = it.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Spacer(Modifier.height(12.dp))
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ModeChip(true, useFrameCountMode, { useFrameCountMode = true }, Modifier.weight(1f))
                ModeChip(false, useFrameCountMode, { useFrameCountMode = false }, Modifier.weight(1f))
            }

            Spacer(Modifier.height(12.dp))

            if (useFrameCountMode) {
                Text(
                    "抽取 $effectiveFrameCount 帧",
                    fontSize = 14.sp, color = Color(0xFF7FA6C9), fontWeight = FontWeight.Medium
                )
                Slider(
                    value = frameCount.toFloat(),
                    onValueChange = { frameCount = it.roundToInt().coerceIn(2, 20) },
                    valueRange = 2f..20f,
                    steps = 17
                )
                Text(
                    "帧间隔约 " + formatMs(effectiveIntervalMs),
                    fontSize = 11.sp, color = Color(0xFF9A9388)
                )
            } else {
                Text(
                    "每 ${intervalSec} 秒一帧（共 $effectiveFrameCount 帧）",
                    fontSize = 14.sp, color = Color(0xFF7FA6C9), fontWeight = FontWeight.Medium
                )
                Slider(
                    value = intervalSec.toFloat(),
                    onValueChange = { intervalSec = it.roundToInt().coerceIn(1, maxOf(1, minOf(30, seconds.toInt()))) },
                    valueRange = 1f..maxOf(2f, minOf(30f, seconds.toFloat())),
                    steps = maxOf(0, minOf(29, seconds.toInt() - 1))
                )
                Text(
                    "共 $effectiveFrameCount 帧", fontSize = 11.sp, color = Color(0xFF9A9388)
                )
            }

            Spacer(Modifier.height(16.dp))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("取消", color = Color(0xFF9A9388)) }
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = {
                    onConfirm(VideoSliceResult(effectiveFrameCount, effectiveIntervalMs))
                }) { Text("抽取 $effectiveFrameCount 帧", color = Color(0xFF7FA6C9), fontWeight = FontWeight.SemiBold) }
            }
        }
    }
}

private fun formatMs(ms: Long): String {
    val f = ms / 1000f
    return if (f < 1) "${(f * 1000).roundToInt()}ms" else "${f.roundToInt()}s"
}

@Composable
private fun ModeChip(
    isCountMode: Boolean,
    useFrameCountMode: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val selected = useFrameCountMode == isCountMode
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) Color(0xFF7FA6C9) else Color(0xFFF2F0EA))
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            if (isCountMode) "按帧数" else "按间隔",
            fontSize = 13.sp,
            color = if (selected) Color.White else Color(0xFF4E4638),
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

/** 抽帧位图缩放（复用 ChatScreen 的长边压缩逻辑，独立小工具防循环依赖）。 */
private object BitmapHelper {
    fun scale(src: android.graphics.Bitmap, maxEdge: Int): android.graphics.Bitmap {
        val w = src.width; val h = src.height
        val longEdge = maxOf(w, h)
        if (longEdge <= maxEdge) return src
        val ratio = maxEdge.toFloat() / longEdge
        return android.graphics.Bitmap.createScaledBitmap(
            src, (w * ratio).toInt().coerceAtLeast(1), (h * ratio).toInt().coerceAtLeast(1), true
        )
    }
}


