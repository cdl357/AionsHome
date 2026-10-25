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
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * 开屏（Yuri 素材版）：约 3.2 秒
 * ①奶白浅绿底 + 阳光斑；②右上角苹果树枝轻晃（实拍素材），青苹果从枝上掉落（弹两下再滚一点）；
 * ③黑猫（实拍剪影）从左侧跑入、带着跑动的起伏停到中场；④淡出进回家页。
 * SY 花体已按需求移除。素材：splash_branch / splash_cat_run / splash_apple_a / splash_apple_b。
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

        // ── 苹果树枝（右上角，轻晃；淡入） ──
        val sway = if (t in 260f..760f) {
            sin((t - 260f) / 500f * 2f * PI.toFloat()) * 2.4f * (1f - (t - 260f) / 500f)
        } else sin(t / 1900f) * 1.1f
        Image(
            painter = painterResource(R.drawable.splash_branch),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(width = w * 0.38f, height = w * 0.38f * (1056f / 800f))
                .graphicsLayer {
                    rotationZ = sway
                    alpha = ((t - 60f) / 380f).coerceIn(0f, 1f)
                    transformOrigin = TransformOrigin(0.9f, 0.05f)
                }
        )

        // ── 掉落的青苹果（两颗素材交替，落下→弹→滚） ──
        DROPS.forEachIndexed { idx, d ->
            val applePainter = painterResource(if (idx % 2 == 0) R.drawable.splash_apple_a else R.drawable.splash_apple_b)
            val appleW = w * (if (idx % 2 == 0) 0.075f else 0.088f)
            val appleH = appleW * (if (idx % 2 == 0) (248f / 240f) else (287f / 280f))
            val groundY = h * GROUND_Y
            val r = appleH / 2f
            val x0 = w * d.xF
            val hangY = h * 0.185f

            if (t < d.t0) {
                // 还挂在枝上
                if (t > 240f) {
                    val swayX = w * 0.003f * sin(t / 320f + idx)
                    Image(
                        applePainter, null,
                        modifier = Modifier
                            .offset { IntOffset((x0 + swayX - appleW / 2f).roundToPx(), (hangY - r).roundToPx()) }
                            .size(appleW, appleH)
                    )
                }
            } else {
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
                        y = groundY - r - h * 0.045f * (1 - p) * abs(sin(p * PI.toFloat()))
                        rot = d.rot
                    }
                    ft < FALL_MS + BOUNCE_MS * 2 + 90f -> {
                        val p = (ft - FALL_MS - BOUNCE_MS) / (BOUNCE_MS + 90f)
                        y = groundY - r - h * 0.016f * (1 - p) * abs(sin(p * PI.toFloat()))
                        rot = d.rot
                        x = x0 + w * 0.008f * p * d.rollDir
                    }
                    else -> {
                        y = groundY - r
                        x = x0 + w * (0.008f + 0.012f * ((ft - FALL_MS - BOUNCE_MS * 2 - 90f) / 400f).coerceIn(0f, 1f)) * d.rollDir
                        rot = d.rot
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

        // ── 黑猫：从左侧跑入（带跑动起伏），停在中场继续小跑 ──
        val catW = w * 0.30f
        val catH = catW * (309f / 420f)
        val catGround = h * GROUND_Y
        val runP = ((t - CAT_T0) / RUN_MS).coerceIn(0f, 1f)
        val ease = 1f - (1f - runP) * (1f - runP)
        val catXF = -0.16f + (0.40f + 0.16f) * ease
        val bob = if (t >= CAT_T0) h * 0.009f * abs(sin(t / 72f)) else 0.dp
        val wig = if (t >= CAT_T0) sin(t / 145f) * 2.2f else 0f
        val catAlpha = ((t - CAT_T0) / 180f).coerceIn(0f, 1f)
        if (t >= CAT_T0) {
            Image(
                painter = painterResource(R.drawable.splash_cat_run),
                contentDescription = null,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .offset(
                        x = w * catXF,
                        y = -(h * GROUND_Y - catH) - bob
                    )
                    .size(width = catW, height = catH)
                    .graphicsLayer {
                        rotationZ = wig
                        alpha = catAlpha
                        transformOrigin = TransformOrigin(0.5f, 1f)
                    }
            )
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
private const val CAT_T0 = 480f
private const val RUN_MS = 1150f
private const val DROP_T0 = 620f
private const val DROP_GAP = 230f
private const val FALL_MS = 470f
private const val BOUNCE_MS = 190f
private const val GROUND_Y = 0.86f

private data class AppleDrop(val xF: Float, val t0: Float, val rot: Float, val rollDir: Float)

private val DROPS = listOf(
    AppleDrop(0.58f, DROP_T0, 16f, 1f),
    AppleDrop(0.66f, DROP_T0 + DROP_GAP, -19f, -1f),
    AppleDrop(0.74f, DROP_T0 + DROP_GAP * 2, 12f, 1f),
    AppleDrop(0.62f, DROP_T0 + DROP_GAP * 3.4f, -15f, -1f),
    AppleDrop(0.70f, DROP_T0 + DROP_GAP * 4.6f, 20f, 1f),
    AppleDrop(0.78f, DROP_T0 + DROP_GAP * 5.7f, -12f, -1f),
    AppleDrop(0.55f, DROP_T0 + DROP_GAP * 6.8f, 18f, 1f)
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
