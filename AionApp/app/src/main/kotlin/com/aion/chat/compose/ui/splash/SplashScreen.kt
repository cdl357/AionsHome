package com.aion.chat.compose.ui.splash

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.aion.chat.R
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sin

/**
 * 开屏（Yuri 素材版）：约 3.2 秒
 * ①奶白浅绿底 + 阳光斑；②右上角苹果树枝被风吹得摇晃（两阵风），苹果一颗一颗被晃落；
 * ③落地弹两下后沿地面滚出去（边滚边转、减速停住）；④淡出进回家页。
 * 猫已按需求移除。素材：splash_branch / splash_apple_a / splash_apple_b。
 */
@Composable
fun SplashScreen(onFinished: () -> Unit) {
    var frame by remember { mutableStateOf(0L) }
    var finished by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val start = withFrameMillis { it }
        while (true) {
            withFrameMillis { now -> frame = now - start }
            if (frame >= TOTAL_MS && !finished) {
                finished = true
                onFinished()
            }
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFFF3F6EC), Color(0xFFE3EFD6))))
    ) {
        val w = maxWidth
        val h = maxHeight
        val t = frame.toFloat()

        // ── 阳光斑 ──
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawSunSpots(t, size.width, size.height)
        }

        // ── 苹果树枝（右上角）：两阵风依次吹过，每阵风吹得枝子晃几下再平息 ──
        val g1 = if (t < 1150f) sin(t / 1150f * 3f * PI.toFloat()) * 3.4f * (1f - t / 1150f) else 0f
        val g2 = if (t in 1500f..2550f)
            sin((t - 1500f) / 1050f * 2.5f * PI.toFloat()) * 2.6f * (1f - (t - 1500f) / 1050f) else 0f
        val sway = g1 + g2 + sin(t / 1900f) * 1.1f
        Image(
            painter = painterResource(R.drawable.splash_branch),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(width = w * 0.38f, height = w * 0.38f * (1070f / 800f))
                .graphicsLayer {
                    rotationZ = sway
                    alpha = ((t - 60f) / 380f).coerceIn(0f, 1f)
                    transformOrigin = TransformOrigin(0.9f, 0.05f)
                }
        )

        // ── 掉落的青苹果：挂在枝上时看不见（藏在树叶里），风把它晃下来才出现 ──
        DROPS.forEachIndexed { idx, d ->
            val applePainter = painterResource(if (idx % 2 == 0) R.drawable.splash_apple_a else R.drawable.splash_apple_b)
            val appleW = w * (if (idx % 2 == 0) 0.075f else 0.088f)
            val appleH = appleW * (if (idx % 2 == 0) (248f / 240f) else (287f / 280f))
            val groundY = h * GROUND_Y
            val r = appleH / 2f
            val x0 = w * d.xF
            val hangY = h * 0.185f

            if (t >= d.t0) {
                val ft = t - d.t0
                var y: androidx.compose.ui.unit.Dp
                var rot: Float
                var x: androidx.compose.ui.unit.Dp = x0
                when {
                    ft < FALL_MS -> {
                        val p = ft / FALL_MS
                        y = hangY + (groundY - r - hangY) * p * p
                        rot = d.rot * p
                    }
                    ft < FALL_MS + BOUNCE_MS -> {
                        val p = (ft - FALL_MS) / BOUNCE_MS
                        y = groundY - r - h * 0.038f * (1 - p) * abs(sin(p * PI.toFloat()))
                        rot = d.rot
                    }
                    ft < FALL_MS + BOUNCE_MS + 110f -> {
                        val p = (ft - FALL_MS - BOUNCE_MS) / 110f
                        y = groundY - r - h * 0.014f * (1 - p) * abs(sin(p * PI.toFloat()))
                        rot = d.rot
                    }
                    else -> {
                        // 落地后沿地面滚出去：位移和旋转一起减速，直到停住
                        val p = ((ft - FALL_MS - BOUNCE_MS - 110f) / ROLL_MS).coerceIn(0f, 1f)
                        val roll = 1f - (1f - p) * (1f - p) * (1f - p)
                        x = x0 + w * 0.10f * roll * d.rollDir
                        y = groundY - r
                        rot = d.rot + 320f * roll * d.rollDir
                    }
                }
                Image(
                    applePainter, null,
                    modifier = Modifier
                        .offset { IntOffset((x - appleW / 2f).roundToPx(), (y - r).roundToPx()) }
                        .size(appleW, appleH)
                        .graphicsLayer { rotationZ = rot }
                )
            }
        }

        // ── 淡出进回家页 ──
        if (t > FADE_T0) {
            val a = ((t - FADE_T0) / (TOTAL_MS - FADE_T0)).coerceIn(0f, 1f)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFFF3F6EC).copy(alpha = a))
            )
        }
    }
}

// ── 时间轴（ms） ──

private const val TOTAL_MS = 3200f
private const val FADE_T0 = 2950f
private const val FALL_MS = 470f
private const val BOUNCE_MS = 190f
private const val ROLL_MS = 850f
private const val GROUND_Y = 0.86f

private data class AppleDrop(val xF: Float, val t0: Float, val rot: Float, val rollDir: Float)

// 掉落时刻跟着两阵风走：第一阵（0~1.1s）掉前 3 颗，第二阵（1.5~2.55s）掉后 4 颗
private val DROPS = listOf(
    AppleDrop(0.58f, 620f, 16f, 1f),
    AppleDrop(0.66f, 840f, -19f, -1f),
    AppleDrop(0.74f, 1050f, 12f, 1f),
    AppleDrop(0.62f, 1560f, -15f, -1f),
    AppleDrop(0.70f, 1830f, 20f, 1f),
    AppleDrop(0.78f, 2130f, -12f, -1f),
    AppleDrop(0.55f, 2400f, 18f, 1f)
)

// ── 阳光斑（保留原版） ──

private val SunSpot = Color(0xFFFFFDF2)

private fun DrawScope.drawSunSpots(t: Float, w: Float, h: Float) {
    val fade = (t / 500f).coerceIn(0f, 1f)
    val spots = listOf(
        Triple(0.18f, 0.14f, 46f), Triple(0.42f, 0.08f, 30f), Triple(0.72f, 0.20f, 52f),
        Triple(0.10f, 0.34f, 26f), Triple(0.60f, 0.30f, 38f), Triple(0.88f, 0.38f, 28f),
        Triple(0.30f, 0.52f, 34f), Triple(0.78f, 0.62f, 30f)
    )
    spots.forEachIndexed { i, (fx, fy, r) ->
        val dx = sin(t / 1700f + i * 1.7f) * w * 0.015f
        val dy = kotlin.math.cos(t / 2100f + i * 2.1f) * h * 0.012f
        val a = (0.5f + 0.5f * sin(t / 1300f + i * 2.6f)) * 0.35f * fade
        val radius = r * min(w, h) / 1080f * 2f
        val center = Offset(w * fx + dx, h * fy + dy)
        drawCircle(
            Brush.radialGradient(
                colors = listOf(SunSpot.copy(alpha = a), SunSpot.copy(alpha = 0f)),
                center = center,
                radius = radius
            ),
            radius = radius,
            center = center
        )
    }
}
