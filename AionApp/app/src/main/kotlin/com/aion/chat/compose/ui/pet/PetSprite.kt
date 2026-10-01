package com.aion.chat.compose.ui.pet

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.json.JSONObject

/**
 * AionPet 桌宠精灵（布局定稿 §12 近期项）：逐帧播 192×208 横向精灵条。
 * 帧时长读 manifest.json 的 frameDurationsMs；锚点 bottom-center。
 * 先做「会动的小摆件」：idle 为常态，点一下随机切一个小动作，播完回 idle。
 * 中期：动作由心潮情绪驱动。
 */
data class PetAnim(
    val name: String,
    val src: String,
    val frameCount: Int,
    val frameWidth: Int,
    val frameHeight: Int,
    val durationsMs: List<Long>,
    val loop: Boolean
)

private fun loadManifest(context: android.content.Context): Map<String, PetAnim> {
    val json = context.assets.open("pet/manifest.json").bufferedReader().use { it.readText() }
    val root = JSONObject(json)
    val anims = root.getJSONObject("animations")
    val out = mutableMapOf<String, PetAnim>()
    anims.keys().forEach { key ->
        val a = anims.getJSONObject(key)
        val durations = mutableListOf<Long>()
        val dArr = a.optJSONArray("frameDurationsMs")
        if (dArr != null) for (i in 0 until dArr.length()) durations.add(dArr.getLong(i))
        out[key] = PetAnim(
            name = key,
            src = a.getString("src"),
            frameCount = a.getInt("frameCount"),
            frameWidth = a.getInt("frameWidth"),
            frameHeight = a.getInt("frameHeight"),
            durationsMs = if (durations.isEmpty()) List(a.getInt("frameCount")) { a.getLong("frameDurationMs") } else durations,
            loop = a.optBoolean("loop", true)
        )
    }
    return out
}

private val stripCache = mutableMapOf<String, androidx.compose.ui.graphics.ImageBitmap?>()

private fun loadStrip(context: android.content.Context, src: String): androidx.compose.ui.graphics.ImageBitmap? =
    stripCache.getOrPut(src) {
        runCatching {
            BitmapFactory.decodeStream(context.assets.open("pet/$src"))?.asImageBitmap()
        }.getOrNull()
    }

@Composable
fun PetSprite(
    modifier: Modifier = Modifier,
    displayHeight: Dp = 104.dp,
    onTap: () -> Unit = {}
) {
    val context = LocalContext.current
    val manifest = remember { loadManifest(context) }
    var currentAnim by remember { mutableIntStateOf(manifest.keys.indexOf("idle").coerceAtLeast(0)) }
    var frameIndex by remember { mutableIntStateOf(0) }
    val animNames = remember { manifest.keys.filter { manifest[it]?.loop == true } }

    val anim = manifest[manifest.keys.elementAtOrNull(currentAnim) ?: "idle"]

    // 帧推进：按 manifest 帧时长；非循环动画播完回 idle
    LaunchedEffect(currentAnim) {
        val a = anim ?: return@LaunchedEffect
        while (true) {
            val d = a.durationsMs.getOrElse(frameIndex) { 140L }
            delay(d)
            val next = frameIndex + 1
            if (next >= a.frameCount) {
                if (a.name != "idle") {
                    // 小动作播完回 idle
                    currentAnim = manifest.keys.indexOf("idle").coerceAtLeast(0)
                    frameIndex = 0
                    return@LaunchedEffect
                }
                if (!a.loop) { frameIndex = 0 } else frameIndex = next % a.frameCount
            } else {
                frameIndex = next
            }
        }
    }

    // 常态随机小动作：每 9 秒掷一次，30% 概率切 waving/happy/waiting/review
    LaunchedEffect(Unit) {
        while (true) {
            delay(9000L)
            if (manifest.keys.elementAtOrNull(currentAnim) == "idle" &&
                (0..2).random() == 0
            ) {
                val pick = listOf("waving", "happy", "waiting", "review").random()
                currentAnim = manifest.keys.indexOf(pick).coerceAtLeast(0)
                frameIndex = 0
            }
        }
    }

    val strip = anim?.let { loadStrip(context, it.src) }
    val density = androidx.compose.ui.platform.LocalDensity.current
    val drawH = with(density) { displayHeight.toPx() }
    val drawW = drawH * ((anim?.frameWidth ?: 192) / (anim?.frameHeight ?: 208).toFloat())

    Canvas(
        modifier = modifier
            .height(displayHeight)
            .clickable { onTap() }
    ) {
        val a = anim ?: return@Canvas
        val bmp = strip ?: return@Canvas
        val fw = a.frameWidth.toFloat()
        val fh = a.frameHeight.toFloat()
        val srcRect = Rect(
            left = frameIndex * fw, top = 0f,
            right = (frameIndex + 1) * fw, bottom = fh
        )
        // 锚点 bottom-center：整体按显示高度缩放，绘制区底部对齐
        val drawScale = drawH / fh
        scale(drawScale, drawScale, pivot = Offset(size.width / 2f, size.height)) {
            drawImage(
                image = bmp,
                srcOffset = IntOffset((frameIndex * a.frameWidth), 0),
                srcSize = IntSize(a.frameWidth, a.frameHeight),
                dstOffset = IntOffset(
                    ((size.width - fw) / 2).toInt(),
                    (size.height - fh).toInt()
                ),
                dstSize = IntSize(fw.toInt(), fh.toInt())
            )
        }
    }
}
