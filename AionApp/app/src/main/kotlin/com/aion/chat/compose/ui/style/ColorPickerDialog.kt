package com.aion.chat.compose.ui.style

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/**
 * 颜色自定义弹窗（橘瓣式）：预设色板 + 色相滑杆 + 饱和度/明度二维盘，实时预览。
 * initial=null 表示「未自定义」，重置按钮回 null。
 */
@Composable
fun ColorPickerDialog(
    title: String,
    initial: Int?,
    onPick: (Int?) -> Unit,
    onDismiss: () -> Unit,
) {
    // 工作色：initial 为空给一个中性起点
    var hsv by remember {
        val c = initial?.let { Color(it) } ?: Color(0xFF8AA84E)
        val hsvArr = FloatArray(3)
        android.graphics.Color.colorToHSV(
            android.graphics.Color.argb(
                (c.alpha * 255).roundToInt(), (c.red * 255).roundToInt(),
                (c.green * 255).roundToInt(), (c.blue * 255).roundToInt()
            ), hsvArr
        )
        mutableStateOf(hsvArr)
    }
    val cur = Color(android.graphics.Color.HSVToColor(hsv))

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontSize = 16.sp) },
        text = {
            Column {
                // SV 二维盘
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
                        .border(1.dp, Color(0x22000000), androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
                        .pointerInput(Unit) {
                            awaitPointerEventScope {
                                while (true) {
                                    val down = awaitPointerEvent()
                                    val pos = down.changes.firstOrNull()?.position ?: continue
                                    val s = (pos.x / size.width).coerceIn(0f, 1f)
                                    val v = 1f - (pos.y / size.height).coerceIn(0f, 1f)
                                    hsv = floatArrayOf(hsv[0], s, v)
                                    down.changes.forEach { it.consume() }
                                }
                            }
                        }
                ) {
                    val w = size.width
                    val h = size.height
                    // 色相打底
                    drawRect(
                        Brush.horizontalGradient(
                            listOf(
                                Color(android.graphics.Color.HSVToColor(floatArrayOf(0f, 1f, 1f))),
                                Color(android.graphics.Color.HSVToColor(floatArrayOf(60f, 1f, 1f))),
                                Color(android.graphics.Color.HSVToColor(floatArrayOf(120f, 1f, 1f))),
                                Color(android.graphics.Color.HSVToColor(floatArrayOf(180f, 1f, 1f))),
                                Color(android.graphics.Color.HSVToColor(floatArrayOf(240f, 1f, 1f))),
                                Color(android.graphics.Color.HSVToColor(floatArrayOf(300f, 1f, 1f))),
                                Color(android.graphics.Color.HSVToColor(floatArrayOf(360f, 1f, 1f)))
                            )
                        ),
                        size = size
                    )
                    // 白→透明（饱和度）
                    drawRect(Brush.horizontalGradient(listOf(Color.White, Color(0x00FFFFFF))), size = size)
                    // 透明→黑（明度）
                    drawRect(Brush.verticalGradient(listOf(Color(0x00000000), Color.Black)), size = size)
                    // 指示点
                    drawCircle(
                        Color.White, radius = 10f,
                        center = Offset(w * hsv[1], h * (1f - hsv[2]))
                    )
                    drawCircle(
                        Color.Black, radius = 10f, center = Offset(w * hsv[1], h * (1f - hsv[2])),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(3f)
                    )
                }
                Spacer(Modifier.height(10.dp))
                // 色相滑杆
                Text("色相", fontSize = 11.sp, color = Color(0xFF8A7F6E))
                Slider(
                    value = hsv[0],
                    onValueChange = { hsv = floatArrayOf(it, hsv[1], hsv[2]) },
                    valueRange = 0f..360f
                )
                Spacer(Modifier.height(6.dp))
                // 预设色板
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    val presets = listOf(
                        0xFF4E4638.toInt(), 0xFF8AA84E.toInt(), 0xFFE8B4B8.toInt(), 0xFF7FB5C9.toInt(),
                        0xFF5C7A99.toInt(), 0xFFC9A84C.toInt(), 0xFF8E6AA8.toInt(), 0xFFFFFFFF.toInt()
                    )
                    presets.forEach { pc ->
                        Box(
                            modifier = Modifier
                                .size(26.dp)
                                .clip(CircleShape)
                                .background(Color(pc))
                                .border(1.dp, Color(0x33000000), CircleShape)
                                .clickable {
                                    val arr = FloatArray(3)
                                    android.graphics.Color.colorToHSV(pc, arr)
                                    hsv = arr
                                }
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    // 当前色预览
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(cur)
                            .border(2.dp, Color.White, CircleShape)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onPick(cur.toArgbInt()) }) { Text("就用这个") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { onPick(null) }) { Text("重置") }
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        }
    )
}

private fun Color.toArgbInt(): Int =
    android.graphics.Color.argb(
        (alpha * 255).roundToInt(), (red * 255).roundToInt(),
        (green * 255).roundToInt(), (blue * 255).roundToInt()
    )
