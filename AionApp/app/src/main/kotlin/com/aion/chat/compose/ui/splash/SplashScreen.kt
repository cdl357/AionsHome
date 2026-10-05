package com.aion.chat.compose.ui.splash

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * 开屏（交接文档 2026-10-05 版）：约 3 秒四拍
 * ①奶白浅绿底+树枝+阳光斑 → ②树枝轻晃、青苹果陆续摇落（落地弹两下再滚一点）→
 * ③黑猫从左侧跑入、眼睛绿光一闪、叼起苹果 → ④走到中央坐定，连笔花体 SY 浮现 → 淡出进回家页。
 * 美术资源到位前，猫/苹果/树枝/SY 全部 Canvas 手绘占位（配色按文档 §1）。
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFFF3F6EC), Color(0xFFE3EFD6))))
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawSplash(frame.toFloat(), size.width, size.height)
        }
    }
}

// ── 时间轴（ms） ──

private const val TOTAL_MS = 3050f
private const val SWAY_T0 = 400f
private const val DROP_T0 = 520f
private const val DROP_GAP = 120f
private const val CAT_T0 = 1200f
private const val EYE_PULSE = 1750f
private const val WALK_T0 = 2000f
private const val SIT_T = 2550f
private const val SY_T0 = 2150f
private const val FADE_T0 = 2880f

// ── 配色（交接文档 §1） ──

private val AppleBody = Color(0xFFA8D84B)
private val AppleHi = Color(0xFFD4ED95)
private val AppleDark = Color(0xFF7FB52E)
private val LeafGreen = Color(0x595B8C3E)
private val LeafGreenSoft = Color(0x335B8C3E)
private val CatBlack = Color(0xFF2B2B2B)
private val EyeGlow = Color(0xFF8FE34A)
private val SunSpot = Color(0xFFFFFDF2)
private val SyInk = Color(0xFF3A4A2E)
private val StemDark = Color(0xFF4A5A38)

private fun DrawScope.drawSplash(t: Float, w: Float, h: Float) {
    val groundY = h * 0.72f
    drawSunSpots(t, w, h)
    drawBranch(t, w, h)
    drawApples(t, w, h, groundY)
    drawCat(t, w, groundY)
    drawSy(t, w, h)
    if (t > FADE_T0) {
        val a = ((t - FADE_T0) / (TOTAL_MS - FADE_T0)).coerceIn(0f, 1f)
        drawRect(Color(0xFFF3F6EC).copy(alpha = a))
    }
}

// ── 阳光斑 ──

private fun DrawScope.drawSunSpots(t: Float, w: Float, h: Float) {
    val fade = (t / 500f).coerceIn(0f, 1f)
    val spots = listOf(
        Triple(0.18f, 0.14f, 46f), Triple(0.42f, 0.08f, 30f), Triple(0.72f, 0.20f, 52f),
        Triple(0.10f, 0.34f, 26f), Triple(0.60f, 0.30f, 38f), Triple(0.88f, 0.38f, 28f),
        Triple(0.30f, 0.52f, 34f), Triple(0.78f, 0.62f, 30f)
    )
    spots.forEachIndexed { i, (fx, fy, r) ->
        val dx = sin(t / 1700f + i * 1.7f) * w * 0.015f
        val dy = cos(t / 2100f + i * 2.1f) * h * 0.012f
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

// ── 树枝 ──

private fun swayAngle(t: Float): Float {
    if (t < SWAY_T0 || t > SWAY_T0 + 450f) return 0f
    val p = (t - SWAY_T0) / 450f
    return sin(p * PI.toFloat() * 2f) * 4.5f * (1f - p)
}

private fun DrawScope.drawBranch(t: Float, w: Float, h: Float) {
    val anchor = Offset(w * 0.97f, -h * 0.02f)
    rotate(degrees = swayAngle(t), pivot = anchor) {
        val tip = Offset(w * 0.60f, h * 0.16f)
        val trunk = Path().apply {
            moveTo(anchor.x, anchor.y)
            cubicTo(w * 0.88f, h * 0.05f, w * 0.74f, h * 0.09f, tip.x, tip.y)
        }
        drawPath(trunk, StemDark.copy(alpha = 0.75f), style = Stroke(w * 0.012f))
        val b1 = Path().apply {
            moveTo(w * 0.86f, h * 0.055f); quadraticBezierTo(w * 0.80f, h * 0.02f, w * 0.72f, h * 0.05f)
        }
        val b2 = Path().apply {
            moveTo(w * 0.76f, h * 0.08f); quadraticBezierTo(w * 0.70f, h * 0.11f, w * 0.66f, h * 0.135f)
        }
        drawPath(b1, StemDark.copy(alpha = 0.6f), style = Stroke(w * 0.008f))
        drawPath(b2, StemDark.copy(alpha = 0.6f), style = Stroke(w * 0.008f))
        val leaves = listOf(
            Offset(w * 0.84f, h * 0.035f), Offset(w * 0.76f, h * 0.045f), Offset(w * 0.70f, h * 0.06f),
            Offset(w * 0.64f, h * 0.115f), Offset(w * 0.72f, h * 0.09f), Offset(w * 0.80f, h * 0.075f),
            Offset(w * 0.67f, h * 0.145f)
        )
        leaves.forEachIndexed { i, c ->
            val r = w * (0.030f + 0.008f * (i % 3))
            drawCircle(LeafGreen, radius = r, center = c)
            drawCircle(LeafGreenSoft, radius = r * 1.5f, center = Offset(c.x + r, c.y - r * 0.4f))
        }
        if (t < DROP_T0 + DROP_GAP * 2 + 500f) {
            hangingApple(t, w, Offset(w * 0.90f, h * 0.075f))
            hangingApple(t, w, Offset(w * 0.83f, h * 0.088f))
        }
    }
}

private fun DrawScope.hangingApple(t: Float, w: Float, at: Offset) {
    val sway = sin(t / 300f) * w * 0.002f
    drawApple(at.x + sway, at.y, w * 0.022f, 0f, 1f)
}

// ── 青苹果 ──

private data class AppleDrop(val xF: Float, val t0: Float, val rot: Float, val rollDir: Float)

private val DROPS = listOf(
    AppleDrop(0.66f, DROP_T0, 16f, 1f),
    AppleDrop(0.71f, DROP_T0 + DROP_GAP, -19f, -1f),
    AppleDrop(0.76f, DROP_T0 + DROP_GAP * 2, 12f, 1f),
    AppleDrop(0.68f, DROP_T0 + DROP_GAP * 3, -14f, -1f),
    AppleDrop(0.73f, DROP_T0 + DROP_GAP * 4, 20f, 1f)
)
private const val FALL_MS = 480f
private const val BOUNCE_MS = 200f

private fun DrawScope.drawApples(t: Float, w: Float, h: Float, groundY: Float) {
    val r = w * 0.024f
    DROPS.forEachIndexed { idx, d ->
        val taken = idx == 2 && t > EYE_PULSE + 180f
        if (taken) return@forEachIndexed
        val x0 = w * d.xF
        if (t < d.t0) {
            if (t > SWAY_T0 - 200f) drawApple(x0, groundY - h * 0.52f, r, 0f, 1f)
            return@forEachIndexed
        }
        val ft = t - d.t0
        var y: Float
        var rot: Float
        var x = x0
        when {
            ft < FALL_MS -> {
                val p = ft / FALL_MS
                val startY = groundY - h * 0.52f
                y = startY + (groundY - r - startY) * p * p
                rot = d.rot * p
            }
            ft < FALL_MS + BOUNCE_MS -> {
                val p = (ft - FALL_MS) / BOUNCE_MS
                y = groundY - r - abs(sin(p * PI.toFloat())) * h * 0.045f * (1 - p)
                rot = d.rot
            }
            ft < FALL_MS + BOUNCE_MS * 2 + 90f -> {
                val p = (ft - FALL_MS - BOUNCE_MS) / (BOUNCE_MS + 90f)
                y = groundY - r - abs(sin(p * PI.toFloat())) * h * 0.016f * (1 - p)
                rot = d.rot
                x = x0 + d.rollDir * w * 0.008f * p
            }
            else -> {
                val p = ((ft - FALL_MS - BOUNCE_MS * 2 - 90f) / 400f).coerceIn(0f, 1f)
                y = groundY - r
                x = x0 + d.rollDir * w * (0.008f + 0.012f * p)
                rot = d.rot
            }
        }
        drawApple(x, y, r, rot, 1f)
    }
}

private fun DrawScope.drawApple(x: Float, y: Float, r: Float, rot: Float, alpha: Float) {
    translate(x, y) {
        rotate(degrees = rot) {
            drawCircle(AppleBody.copy(alpha = alpha), radius = r)
            drawArc(
                AppleDark.copy(alpha = 0.55f * alpha), startAngle = -20f, sweepAngle = 110f, useCenter = true,
                topLeft = Offset(-r, -r), size = Size(r * 2f, r * 2f)
            )
            drawCircle(AppleHi.copy(alpha = 0.9f * alpha), radius = r * 0.32f, center = Offset(-r * 0.35f, -r * 0.38f))
            drawLine(StemDark, Offset(0f, -r * 0.9f), Offset(r * 0.18f, -r * 1.5f), strokeWidth = r * 0.18f)
            drawCircle(LeafGreen, radius = r * 0.30f, center = Offset(r * 0.38f, -r * 1.35f))
        }
    }
}

// ── 黑猫 ──

private fun DrawScope.drawCat(t: Float, w: Float, groundY: Float) {
    if (t < CAT_T0) return
    val unit = w * 0.012f

    val runP = ((t - CAT_T0) / (WALK_T0 - CAT_T0)).coerceIn(0f, 1f)
    val ease = 1f - (1f - runP) * (1f - runP)
    val xRun = -unit * 14f + (w * 0.56f + unit * 14f) * ease
    val walking = t in WALK_T0..SIT_T
    val walkP = ((t - WALK_T0) / (SIT_T - WALK_T0)).coerceIn(0f, 1f)
    val x = if (t < WALK_T0) xRun else xRun + (w * 0.5f - xRun) * walkP
    val sitting = t >= SIT_T

    val bob = if (!sitting) abs(sin(t / 90f)) * unit * 0.9f else 0f
    val baseY = groundY - bob
    val mouthTaken = t > EYE_PULSE + 180f

    translate(x, baseY) {
        if (!sitting) {
            val legSwing = if (!walking || walkP < 0.99f) sin(t / 70f) * unit * 0.9f else 0f
            val tail = Path().apply {
                moveTo(-unit * 3.2f, -unit * 2.2f)
                cubicTo(-unit * 5.2f, -unit * 2.8f, -unit * 6.2f, -unit * 1.6f, -unit * 6.6f + sin(t / 110f) * unit * 0.5f, -unit * 2.6f)
            }
            drawPath(tail, CatBlack, style = Stroke(unit * 0.55f))
            drawOval(CatBlack, topLeft = Offset(-unit * 3.4f, -unit * 4.0f), size = Size(unit * 6.8f, unit * 3.4f))
            drawCircle(CatBlack, unit * 1.45f, Offset(unit * 3.3f, -unit * 3.1f))
            drawEar(Offset(unit * 2.7f, -unit * 4.2f), unit, -18f)
            drawEar(Offset(unit * 3.9f, -unit * 4.2f), unit, 14f)
            drawLine(CatBlack, Offset(-unit * 2.2f, -unit * 1.4f), Offset(-unit * 2.2f + legSwing, 0f), strokeWidth = unit * 0.42f)
            drawLine(CatBlack, Offset(-unit * 0.8f, -unit * 1.5f), Offset(-unit * 0.8f - legSwing, 0f), strokeWidth = unit * 0.42f)
            drawLine(CatBlack, Offset(unit * 1.2f, -unit * 1.5f), Offset(unit * 1.2f - legSwing, 0f), strokeWidth = unit * 0.42f)
            drawLine(CatBlack, Offset(unit * 2.6f, -unit * 1.4f), Offset(unit * 2.6f + legSwing, 0f), strokeWidth = unit * 0.42f)
            val glow = if (t in EYE_PULSE..EYE_PULSE + 200f) {
                sin((t - EYE_PULSE) / 200f * PI.toFloat()) * 0.9f + 0.1f
            } else 0.12f
            drawCircle(EyeGlow.copy(alpha = glow), unit * 0.30f, Offset(unit * 3.1f, -unit * 3.2f))
            drawCircle(EyeGlow.copy(alpha = glow), unit * 0.30f, Offset(unit * 3.8f, -unit * 3.2f))
            if (mouthTaken) drawApple(unit * 4.4f, -unit * 2.4f, unit * 0.95f, 8f, 1f)
        } else {
            val settle = ((t - SIT_T) / 350f).coerceIn(0f, 1f)
            val tail = Path().apply {
                moveTo(unit * 1.6f, -unit * 0.4f)
                cubicTo(unit * 3.4f, -unit * 0.2f, unit * 3.6f, -unit * 1.6f, unit * 2.2f + sin(t / 500f) * unit * 0.3f, -unit * 1.4f)
            }
            drawPath(tail, CatBlack, style = Stroke(unit * 0.5f))
            val squash = 1f + (1f - settle) * 0.06f
            drawOval(CatBlack, topLeft = Offset(-unit * 2.1f * squash, -unit * 5.2f / squash), size = Size(unit * 4.2f * squash, unit * 5.4f / squash))
            drawCircle(CatBlack, unit * 1.55f, Offset(0f, -unit * 5.0f))
            drawEar(Offset(-unit * 0.75f, -unit * 6.15f), unit, -16f)
            drawEar(Offset(unit * 0.75f, -unit * 6.15f), unit, 16f)
            val eyeY = -unit * 5.1f
            val arc = Path().apply {
                moveTo(-unit * 0.55f, eyeY); quadraticBezierTo(-unit * 0.30f, eyeY - unit * 0.35f, -unit * 0.05f, eyeY)
            }
            val arc2 = Path().apply {
                moveTo(unit * 0.05f, eyeY); quadraticBezierTo(unit * 0.30f, eyeY - unit * 0.35f, unit * 0.55f, eyeY)
            }
            drawPath(arc, EyeGlow, style = Stroke(unit * 0.16f))
            drawPath(arc2, EyeGlow, style = Stroke(unit * 0.16f))
            drawApple(unit * 1.5f, -unit * 4.3f, unit * 0.95f, 10f, 1f)
        }
    }
}

private fun DrawScope.drawEar(at: Offset, unit: Float, tilt: Float) {
    val ear = Path().apply {
        moveTo(at.x - unit * 0.55f, at.y + unit * 0.25f)
        lineTo(at.x + tilt / 18f * unit, at.y - unit * 0.75f)
        lineTo(at.x + unit * 0.55f, at.y + unit * 0.25f)
        close()
    }
    drawPath(ear, CatBlack)
    drawCircle(EyeGlow.copy(alpha = 0.25f), unit * 0.14f, Offset(at.x, at.y - unit * 0.1f))
}

// ── 连笔花体 SY（手写连笔矢量路径：S 尾沿基线滑入 Y，长尾回勾小环） ──

private fun syPath(scale: Float, cx: Float, cy: Float): Path {
    // 设计空间 120x96，原点居中；连笔 S→Y 一笔缠一笔，长尾回勾小环压在 S 下方
    val p = Path()
    fun ox(x: Float) = cx + (x - 60f) * scale
    fun oy(y: Float) = cy + (y - 48f) * scale
    fun m(x: Float, y: Float) { p.moveTo(ox(x), oy(y)) }
    fun c(x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float) {
        p.cubicTo(ox(x1), oy(y1), ox(x2), oy(y2), ox(x3), oy(y3))
    }
    m(70f, 16f)
    c(52f, 4f, 28f, 10f, 24f, 26f)
    c(20f, 42f, 38f, 46f, 50f, 54f)
    c(62f, 62f, 64f, 74f, 52f, 82f)
    c(40f, 90f, 52f, 94f, 68f, 88f)
    c(78f, 82f, 84f, 70f, 88f, 58f)
    m(108f, 20f)
    c(102f, 34f, 95f, 48f, 88f, 58f)
    c(84f, 72f, 86f, 86f, 74f, 92f)
    c(64f, 96f, 50f, 92f, 30f, 96f)
    return p
}

private fun DrawScope.drawSy(t: Float, w: Float, h: Float) {
    if (t < SY_T0) return
    val p = ((t - SY_T0) / 650f).coerceIn(0f, 1f)
    val alpha = p
    val scale = 0.9f + 0.1f * p
    val rise = (1f - p) * h * 0.03f
    val cx = w / 2f
    val cy = h * 0.40f + rise
    val scaleF = min(w, h) / 420f * scale

    rotate(degrees = -4f, pivot = Offset(cx, cy)) {
        drawPath(
            syPath(scaleF, cx + scaleF * 2f, cy + scaleF * 2.5f),
            SyInk.copy(alpha = 0.18f * alpha),
            style = Stroke(w * 0.011f * scale)
        )
        drawPath(
            syPath(scaleF, cx, cy),
            SyInk.copy(alpha = alpha),
            style = Stroke(w * 0.012f * scale)
        )
    }
}
