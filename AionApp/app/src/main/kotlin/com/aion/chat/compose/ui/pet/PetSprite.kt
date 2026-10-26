package com.aion.chat.compose.ui.pet

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.json.JSONObject

/**
 * 桌宠精灵（Q 版沈聿淮）：逐帧播 192×208 横向精灵条，锚点 bottom-center。
 * - baseAnim：心潮（PetMind）决定的常态动作，循环
 * - frozen：consciousness=frozen 时停在第 1 帧
 * - oneShot：戳一戳动作，播完一轮回调 onOneShotDone，回到 baseAnim
 * - 素材缺失（missing/love 未到位）回退 idle 显示，绝不回退机械小人
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

private val stripCache = mutableMapOf<String, ImageBitmap?>()

private fun loadStrip(context: android.content.Context, src: String): ImageBitmap? =
    stripCache.getOrPut(src) {
        runCatching {
            BitmapFactory.decodeStream(context.assets.open("pet/$src"))?.asImageBitmap()
        }.getOrNull()
    }

/**
 * idle 条素材的逐帧纵向对齐数据（交接文档规定不重新裁切/不改素材尺寸，故在渲染层补偿）：
 * 素材 6 帧中 0/2/4/5 的可见鞋底止于 y=195，y=196..207 只剩半透明残影贴片（alpha<128）；
 * 帧 1/3 实踩 y=207。逐帧取「实心像素底行」（该行存在 alpha>=0.5 的像素），绘制时据此
 * 裁 src 高度并钉底，等价于把可见内容整体下移补齐踩底，消除 160ms/帧的「踩地↔悬空12px」抖动。
 */
private val idleSolidBottomCache = mutableMapOf<String, IntArray>()

private fun idleSolidBottomRows(bmp: ImageBitmap, a: PetAnim): IntArray =
    idleSolidBottomCache.getOrPut(a.src) {
        val pm = bmp.toPixelMap()
        IntArray(a.frameCount) { f ->
            val x0 = f * a.frameWidth
            (a.frameHeight - 1 downTo 0).firstOrNull { y ->
                (0 until a.frameWidth).any { x -> (pm[x0 + x, y].toArgb() ushr 24) >= 0x80 }
            } ?: -1
        }
    }

@Composable
fun PetSprite(
    modifier: Modifier = Modifier,
    displayHeight: Dp = 104.dp,
    baseAnim: String = "idle",
    frozen: Boolean = false,
    oneShot: String? = null,
    onOneShotDone: () -> Unit = {},
    onTap: () -> Unit = {}
) {
    val context = LocalContext.current
    val manifest = remember { loadManifest(context) }
    val idleAnim = remember { manifest["idle"] }
    var frameIndex by remember { mutableIntStateOf(0) }

    // 目标动作：oneShot 优先；素材缺失（missing/love 未到位）回退 idle 播
    val wanted = oneShot?.takeIf { it.isNotBlank() } ?: baseAnim.takeIf { it.isNotBlank() } ?: "idle"
    val resolved = remember(wanted) {
        val a = manifest[wanted]
        if (a != null && loadStrip(context, a.src) != null) a else idleAnim
    }
    val anim = resolved ?: idleAnim

    // 帧推进：frozen 定在第 1 帧；oneShot 播完一轮回调；baseAnim 循环
    LaunchedEffect(anim?.name, oneShot, frozen) {
        val a = anim ?: return@LaunchedEffect
        // 钳制防越界：换条瞬间 frameIndex 可能超过新条帧数（如 idle 第 6 帧 → waving 4 帧），
        // 否则 drawImage 的 srcOffset 会越界。必须与「删除 wanted 硬清帧」同批生效。
        frameIndex = frameIndex.coerceIn(0, a.frameCount - 1)
        while (true) {
            val d = a.durationsMs.getOrElse(frameIndex) { 140L }
            delay(d)
            if (frozen) { frameIndex = 0; continue }
            val next = frameIndex + 1
            if (next >= a.frameCount) {
                if (oneShot != null && a.name == oneShot) {
                    onOneShotDone()
                    return@LaunchedEffect
                }
                frameIndex = 0
            } else {
                frameIndex = next
            }
        }
    }

    val strip = anim?.let { loadStrip(context, it.src) } ?: idleAnim?.let { loadStrip(context, it.src) }
    // 逐帧纵向对齐补偿仅作用于 idle：其余条（如 happy 帧 3 离地 11px）是疑似跳跃关键帧，不能压平
    val idleBottoms = remember(anim?.src) {
        val a = anim
        if (a != null && a.name == "idle" && strip != null) idleSolidBottomRows(strip, a) else null
    }
    val density = androidx.compose.ui.platform.LocalDensity.current
    val drawH = with(density) { displayHeight.toPx() }

    Canvas(
        modifier = modifier
            .height(displayHeight)
            .clickable { onTap() }
    ) {
        val a = anim ?: return@Canvas
        val bmp = strip ?: return@Canvas
        val fw = a.frameWidth.toFloat()
        val fh = a.frameHeight.toFloat()
        // 帧号钳制：换条瞬间 frameIndex 可能超出新条帧数，防 srcOffset 越界
        val fi = frameIndex.coerceIn(0, a.frameCount - 1)
        // idle 逐帧纵向对齐：按实心像素底行裁 src 高度并钉底，可见鞋底统一踩在绘制区底部
        val srcH = if (a.name == "idle") {
            idleBottoms?.getOrNull(fi)?.takeIf { it in 0 until a.frameHeight }?.plus(1) ?: a.frameHeight
        } else a.frameHeight
        // 锚点 bottom-center：整体按显示高度缩放，绘制区底部对齐
        val drawScale = drawH / fh
        scale(drawScale, drawScale, pivot = Offset(size.width / 2f, size.height)) {
            drawImage(
                image = bmp,
                srcOffset = IntOffset(fi * a.frameWidth, 0),
                srcSize = IntSize(a.frameWidth, srcH),
                dstOffset = IntOffset(
                    ((size.width - fw) / 2).toInt(),
                    (size.height - srcH).toInt()
                ),
                dstSize = IntSize(fw.toInt(), srcH)
            )
        }
    }
}
