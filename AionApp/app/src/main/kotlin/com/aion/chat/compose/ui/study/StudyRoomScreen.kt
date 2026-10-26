package com.aion.chat.compose.ui.study

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateRotation
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aion.chat.compose.data.StudyRoomStore
import kotlin.math.abs

/**
 * 书房（图二做房间，按图一原样摆桌）：桌上每件东西都能拖动、双指旋转缩放；
 * 点摊开的本子 → 写她自己的日记；点花 → 选花（19 种，Sean 在聊天里也能换）；
 * 点玩偶/摆件 → 选上台面的组合。布局全量持久化。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudyRoomScreen(onBack: () -> Unit = {}, onOpenDiaryWrite: () -> Unit = {}) {
    val context = LocalContext.current
    var state by remember { mutableStateOf(StudyRoomStore.load(context)) }
    var showFlower by remember { mutableStateOf(false) }
    var showFigs by remember { mutableStateOf(false) }
    var showDecos by remember { mutableStateOf(false) }
    var lastDragged by remember { mutableStateOf<String?>(null) }

    val figIds = remember { (0 until StudyRoomStore.FIG_COUNT).map { "fig_%02d".format(it) } }

    val placedOrder = remember(state.rev, lastDragged) {
        buildList {
            StudyRoomStore.DESK_KEYS.forEach { add(it) }
            state.decos.forEach { if (it !in this) add(it) }
            state.figs.forEach { if (it !in this) add(it) }
            lastDragged?.let { k -> if (k in this) { remove(k); add(k) } }
        }.filter { it in state.placed }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // ── 房间（图二原封不动） ──
        val bg = remember {
            BitmapFactory.decodeResource(context.resources, resId(context, "study_bg"))
        }
        Image(
            bitmap = bg.asImageBitmap(),
            contentDescription = "书房",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        // ── 顶栏（玻璃） ──
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, end = 14.dp, top = 12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.55f))
                    .border(1.dp, Color.White.copy(alpha = 0.7f), CircleShape)
                    .clickable { onBack() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回",
                    tint = Color(0xFF4E4638), modifier = Modifier.size(19.dp)
                )
            }
            Text(
                "书房",
                fontSize = 17.sp, color = Color(0xFF4E4638), fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .padding(start = 10.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.5f))
                    .padding(horizontal = 12.dp, vertical = 5.dp)
            )
            Spacer(Modifier.weight(1f))
            Text(
                "拖动挪位置 · 双指旋转缩放\n点本子写日记 · 点花换花 · 点玩偶换摆件",
                fontSize = 9.sp, lineHeight = 13.sp, color = Color(0xFF6B6152),
                textAlign = TextAlign.End,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.White.copy(alpha = 0.42f))
                    .padding(horizontal = 9.dp, vertical = 5.dp)
            )
        }

        // ── 桌上每一件（单层 BoxWithConstraints 统一换算） ──
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val density = LocalDensity.current
            val maxWpx = with(density) { maxWidth.toPx() }
            val maxHpx = with(density) { maxHeight.toPx() }

            placedOrder.forEach { key ->
                val placed = state.placed[key] ?: return@forEach
                val res = resId(context, "study_" + key)
                if (res == 0) return@forEach
                val bmp = remember(key) { BitmapFactory.decodeResource(context.resources, res) }
                val ratio = bmp.width.toFloat() / bmp.height
                val baseH = StudyRoomStore.defaultHeight(key)
                val hPx = maxHpx * baseH * placed.scale
                val wPx = hPx * ratio
                val xPx = maxWpx * placed.xf
                val yPx = maxHpx * placed.yf

                var panAcc by remember(key) { mutableStateOf(0f) }
                var zoomAcc by remember(key) { mutableStateOf(1f) }
                var rotAcc by remember(key) { mutableStateOf(0f) }

                Box(
                    modifier = Modifier
                        .offset { IntOffset((xPx - wPx / 2).toInt(), (yPx - hPx).toInt()) }
                        .size(
                            with(density) { wPx.toDp() },
                            with(density) { hPx.toDp() }
                        )
                        .graphicsLayer {
                            rotationZ = placed.rot
                            transformOrigin = TransformOrigin(0.5f, 1f)
                        }
                        .pointerInput(key) {
                            awaitEachGesture {
                                val down = awaitFirstDown()
                                down.consume()
                                var moved = false
                                var zoomAcc = 1f
                                var rotAcc = 0f
                                var panDist = 0f
                                while (true) {
                                    val event = awaitPointerEvent()
                                    if (event.changes.any { it.isConsumed }) break
                                    val z = event.calculateZoom()
                                    val r = event.calculateRotation()
                                    val p = event.calculatePan()
                                    zoomAcc *= z
                                    rotAcc += abs(r)
                                    panDist += p.getDistance()
                                    if (panDist > 14f || abs(zoomAcc - 1f) > 0.06f || rotAcc > 5f) moved = true
                                    val s = state.placed[key]
                                    if (s != null) {
                                        val nx = (s.xf + p.x / maxWpx).coerceIn(0.02f, 0.98f)
                                        val ny = (s.yf + p.y / maxHpx).coerceIn(0.50f, 0.97f)
                                        val nr = s.rot + r
                                        val ns = (s.scale * z).coerceIn(0.5f, 1.8f)
                                        state = state.copy(
                                            placed = state.placed + (key to StudyRoomStore.Placed(nx, ny, nr, ns))
                                        )
                                    }
                                    event.changes.forEach { if (it.positionChanged()) it.consume() }
                                    if (!event.changes.any { it.pressed }) break
                                }
                                if (!moved) {
                                    when {
                                        key == "notebook" -> onOpenDiaryWrite()
                                        key == "flower" -> showFlower = true
                                        key.startsWith("fig_") -> showFigs = true
                                        key.startsWith("deco_") -> showDecos = true
                                    }
                                } else {
                                    val s = state.placed[key]
                                    if (s != null) {
                                        StudyRoomStore.place(context, key, s.xf, s.yf, s.rot, s.scale)
                                        lastDragged = key
                                    }
                                }
                            }
                        }
                ) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = key,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                }
            }
        }
    }

    // ── 选花 ──
    if (showFlower) {
        ModalBottomSheet(onDismissRequest = { showFlower = false }, containerColor = Color(0xFFFBF9F4)) {
            Column(Modifier.padding(horizontal = 18.dp, vertical = 6.dp)) {
                Text("桌上的花", fontSize = 16.sp, color = Color(0xFF4E4638), fontWeight = FontWeight.Medium)
                Text(
                    "你挑一种；Sean 在聊天里让你换，他也能换",
                    fontSize = 11.sp, color = Color(0xFF9A8F7E)
                )
                Spacer(Modifier.height(8.dp))
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.fillMaxWidth().height(430.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp)
                ) {
                    items((0 until StudyRoomStore.FLOWER_COUNT).toList()) { i ->
                        val sel = state.flowerStyle == i
                        val bmp = remember(i) {
                            BitmapFactory.decodeResource(
                                context.resources, resId(context, "study_flower_%02d".format(i))
                            )
                        }
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (sel) Color(0x1A8AA84E) else Color.White)
                                .border(
                                    1.5.dp,
                                    if (sel) Color(0xFF8AA84E) else Color(0x22000000),
                                    RoundedCornerShape(12.dp)
                                )
                                .clickable {
                                    state = StudyRoomStore.setFlower(context, i)
                                    showFlower = false
                                }
                                .padding(6.dp)
                        ) {
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = null,
                                modifier = Modifier.fillMaxWidth().height(92.dp),
                                contentScale = ContentScale.Fit
                            )
                        }
                    }
                }
            }
        }
    }

    // ── 选玩偶 ──
    if (showFigs) {
        PickerSheet(
            title = "桌上的玩偶",
            sub = "点选要摆在桌上的（可多选）；Sean 让你换，他也能换",
            ids = figIds,
            selected = state.figs.toSet(),
            resName = { "study_$it" },
            onApply = {
                state = StudyRoomStore.setFigs(context, it)
                showFigs = false
            },
            onDismiss = { showFigs = false }
        )
    }

    // ── 选摆件 ──
    if (showDecos) {
        PickerSheet(
            title = "桌上的摆件",
            sub = "小灯、玻璃罐、绿植……想摆什么上台面自己挑",
            ids = StudyRoomStore.DECO_KEYS.toList(),
            selected = state.decos.toSet(),
            resName = { "study_$it" },
            onApply = {
                state = StudyRoomStore.setDecos(context, it)
                showDecos = false
            },
            onDismiss = { showDecos = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PickerSheet(
    title: String,
    sub: String,
    ids: List<String>,
    selected: Set<String>,
    resName: (String) -> String,
    onApply: (List<String>) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val picked = remember(selected) { selected.toMutableSet() }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Color(0xFFFBF9F4)) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 6.dp)) {
            Text(title, fontSize = 16.sp, color = Color(0xFF4E4638), fontWeight = FontWeight.Medium)
            Text(sub, fontSize = 11.sp, color = Color(0xFF9A8F7E))
            Spacer(Modifier.height(8.dp))
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                modifier = Modifier.fillMaxWidth().height(360.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(ids) { id ->
                    val sel = id in picked
                    val bmp = remember(id) {
                        BitmapFactory.decodeResource(context.resources, resId(context, resName(id)))
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (sel) Color(0x1A8AA84E) else Color.White)
                            .border(
                                1.5.dp,
                                if (sel) Color(0xFF8AA84E) else Color(0x22000000),
                                RoundedCornerShape(12.dp)
                            )
                            .clickable {
                                if (sel) picked.remove(id) else picked.add(id)
                            }
                            .padding(5.dp)
                    ) {
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier.fillMaxWidth().height(64.dp),
                            contentScale = ContentScale.Fit
                        )
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("算了", color = Color(0xFF9A8F7E)) }
                TextButton(onClick = { onApply(picked.toList()) }) {
                    Text("摆上去（${picked.size}）", color = Color(0xFF6E7E3A), fontWeight = FontWeight.Medium)
                }
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}

private val resCache = mutableMapOf<String, Int>()

internal fun resId(context: android.content.Context, name: String): Int =
    resCache.getOrPut(name) {
        context.resources.getIdentifier(name, "drawable", context.packageName)
    }
