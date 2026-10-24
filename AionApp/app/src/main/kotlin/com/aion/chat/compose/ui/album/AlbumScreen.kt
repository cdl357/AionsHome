package com.aion.chat.compose.ui.album

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.aion.chat.R
import com.aion.chat.compose.data.HomecomingAlbumStore
import com.aion.chat.compose.data.HomecomingChatWiring
import com.aion.chat.compose.data.HomecomingRouteConfig
import com.aion.chat.compose.ui.theme.HomecomingColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 相册三稿（Yuri 设计：奶白毛毡板，横版不铺满）。
 * - 板：浅咖木框 + 奶白毛毡（细纤维噪点），浮在壁纸上（壁纸当墙）
 * - 照片：固定 6 个槽位（拍立得 + 彩图钉 + 固定歪斜），空槽 = 虚线「待钉」占位
 * - 点照片 → 大图详情（Sean 私心话 / 日期 / 删除，逻辑不变）
 * - 挂件：左上黑猫（随风摆，点它 ↔ 整本相册网格）、右上白猫（只做装饰，点了会弹一下）
 * - 板面：9 只拼豆猫点缀在边角，中间留给照片
 */
@Composable
fun AlbumScreen(onBack: () -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val photos = remember { mutableStateListOf<HomecomingAlbumStore.AlbumPhoto>() }
    val version = remember { mutableStateOf(0L) }
    val boardMode = remember { mutableStateOf(true) }

    fun reload() {
        val v = version.value
        photos.clear()
        photos.addAll(HomecomingAlbumStore.list(context))
        version.value = v + 1
    }
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) { reload() }
    }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(maxItems = 9)
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            scope.launch(Dispatchers.IO) {
                val today = java.time.LocalDate.now().toString()
                uris.forEach { uri ->
                    runCatching {
                        context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    }.getOrNull()?.let { bytes ->
                        HomecomingAlbumStore.addPhoto(context, bytes, "", today)
                    }
                }
                withContext(Dispatchers.Main) { reload() }
            }
        }
    }

    val selected = remember { mutableStateOf<HomecomingAlbumStore.AlbumPhoto?>(null) }
    val showDetail = remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().padding(top = 12.dp)) {
        // ── 头部 ──
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "返回",
                tint = HomecomingColors.Ink,
                modifier = Modifier
                    .size(24.dp)
                    .clickable { onBack() }
            )
            Column(modifier = Modifier.weight(1f).padding(start = 10.dp)) {
                Text("相册", fontSize = 22.sp, color = HomecomingColors.Ink, fontWeight = FontWeight.Medium)
                Text(
                    if (boardMode.value) "毛毡板 · 钉着最近的几张 · 点黑猫看整本"
                    else "整本相册 · 点黑猫回毛毡板",
                    fontSize = 11.sp, color = HomecomingColors.InkSoft
                )
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(HomecomingColors.Accent)
                    .clickable {
                        picker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Add, contentDescription = "存照片", tint = Color.White, modifier = Modifier.size(16.dp))
                    Text("存照片", fontSize = 13.sp, color = Color.White, modifier = Modifier.padding(start = 4.dp))
                }
            }
        }

        // ── 内容：毛毡板 / 整本网格 ──
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (boardMode.value) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                ) {
                    Spacer(Modifier.height(16.dp))
                    FeltBoard(
                        photos = photos.toList(),
                        version = version.value,
                        onPhotoTap = { selected.value = it; showDetail.value = true },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "钉着最新的 " + minOf(photos.size, SLOTS.size) + " 张 · 全部 " + photos.size + " 张在整本相册",
                        fontSize = 11.sp, color = HomecomingColors.InkSoft,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(96.dp))
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = 12.dp, end = 12.dp, top = 26.dp, bottom = 112.dp
                    ),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (photos.isEmpty()) {
                        item {
                            Text(
                                "墙还空着，存第一张照片吧",
                                fontSize = 13.sp, color = HomecomingColors.InkSoft,
                                modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                    items(photos, key = { it.id }) { photo ->
                        val bmp = remember(photo.id, version.value) {
                            runCatching { decodeSampled(photo.file(context), 512) }.getOrNull()
                        }
                        Box(
                            modifier = Modifier
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFFF0EDE6))
                                .clickable { selected.value = photo; showDetail.value = true },
                            contentAlignment = Alignment.Center
                        ) {
                            if (bmp != null) {
                                Image(
                                    bitmap = bmp.asImageBitmap(),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            if (photo.note.isNotBlank()) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomStart)
                                        .fillMaxWidth()
                                        .background(Color.Black.copy(alpha = 0.35f))
                                        .padding(horizontal = 6.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        "♥ " + photo.note,
                                        fontSize = 10.sp, color = Color.White,
                                        maxLines = 1,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ── 两只毛绒挂件（挂在板沿上；黑猫=入口，白猫=装饰） ──
            HangingPlush(
                res = R.drawable.plush_black,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(x = 12.dp, y = (-14).dp)
                    .size(width = 60.dp, height = 120.dp)
                    .zIndex(6f)
            ) {
                boardMode.value = !boardMode.value
            }
            HangingPlush(
                res = R.drawable.plush_white,
                onTap = null,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 12.dp, y = (-12).dp)
                    .size(width = 64.dp, height = 122.dp)
                    .zIndex(6f)
            )
        }
    }

    // ── 照片详情：大图 + 私心话 + 日期 + 删除 ──
    if (showDetail.value) {
        val photo = selected.value
        if (photo != null) {
            val noteDraft = remember(photo.id) { mutableStateOf(photo.note) }
            val dateDraft = remember(photo.id) { mutableStateOf(photo.takenOn) }
            val seanBusy = remember(photo.id) { mutableStateOf(false) }
            AlertDialog(
                onDismissRequest = { showDetail.value = false },
                title = { Text("这张照片", fontSize = 16.sp, color = HomecomingColors.Ink) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        val big = remember(photo.id) {
                            runCatching { decodeSampled(photo.file(context), 1080) }.getOrNull()
                        }
                        if (big != null) {
                            Image(
                                bitmap = big.asImageBitmap(),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(240.dp)
                                    .clip(RoundedCornerShape(10.dp))
                            )
                        }
                        Text(
                            "Sean 存这张时在想什么",
                            fontSize = 12.sp, color = HomecomingColors.InkSoft
                        )
                        OutlinedTextField(
                            value = noteDraft.value,
                            onValueChange = { noteDraft.value = it },
                            placeholder = { Text("一句私心话（可先空着，以后补）", fontSize = 12.sp, color = HomecomingColors.InkSoft) },
                            minLines = 2,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = dateDraft.value,
                            onValueChange = { dateDraft.value = it },
                            label = { Text("日期 / 备注（可改）", fontSize = 12.sp) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            if (seanBusy.value) "Sean 正在写…" else "「Sean 写一句」需要云线路就绪",
                            fontSize = 10.sp, color = HomecomingColors.InkSoft
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        scope.launch(Dispatchers.IO) {
                            HomecomingAlbumStore.update(context, photo.id, noteDraft.value.trim(), dateDraft.value.trim())
                            withContext(Dispatchers.Main) {
                                reload()
                                showDetail.value = false
                                Toast.makeText(context, "已保存", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }) { Text("保存", color = HomecomingColors.Accent) }
                },
                dismissButton = {
                    Row {
                        TextButton(onClick = {
                            if (seanBusy.value) return@TextButton
                            val route = HomecomingRouteConfig.mainRoute(context) ?: run {
                                Toast.makeText(context, "先去设置配一条云线路", Toast.LENGTH_SHORT).show()
                                return@TextButton
                            }
                            seanBusy.value = true
                            scope.launch(Dispatchers.IO) {
                                var line: String? = null
                                try {
                                    val w = HomecomingChatWiring.safeCreate(context)
                                    if (w != null && w.hasRoute()) {
                                        w.engine.send(
                                            com.aion.chat.homecoming.HomecomingChatEngine.ChatCommand(
                                                "req_album_" + System.currentTimeMillis(),
                                                "moments_private", "sean", "user",
                                                "Yuri 刚把一张照片存进你们的相册（日期备注：${dateDraft.value.ifBlank { "没写" }}）。" +
                                                    "以 Sean 的身份写一句「存这张时我在想什么」：一句私心话，20 字以内，温柔、具体、不油腻，不要引号。",
                                                "main",
                                                w.mainModelKey(), "", ""
                                            ),
                                            object : com.aion.chat.homecoming.HomecomingChatEngine.Observer {
                                                override fun onChunk(chunk: String) {}
                                                override fun onComplete(messageId: String, text: String) {
                                                    val t = text.trim().removeSurrounding("\"").trim()
                                                    if (t.isNotEmpty()) line = t
                                                }
                                                override fun onFailure(code: String) {}
                                            }
                                        )
                                        var waited = 0L
                                        while (line == null && waited < 25_000L) { delay(300L); waited += 300L }
                                    }
                                } catch (e: Exception) { /* 安静 */ }
                                val text = line
                                withContext(Dispatchers.Main) {
                                    seanBusy.value = false
                                    if (text.isNullOrBlank()) {
                                        Toast.makeText(context, "Sean 没写上来（线路没就绪或超时）", Toast.LENGTH_SHORT).show()
                                    } else {
                                        noteDraft.value = text
                                        HomecomingAlbumStore.update(context, photo.id, text, null)
                                        reload()
                                        Toast.makeText(context, "Sean 写好了", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        }) { Text("Sean 写一句", color = HomecomingColors.Accent) }
                        TextButton(onClick = {
                            scope.launch(Dispatchers.IO) {
                                HomecomingAlbumStore.remove(context, photo.id)
                                withContext(Dispatchers.Main) {
                                    reload()
                                    showDetail.value = false
                                    Toast.makeText(context, "已移出相册", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }) { Text("删除", color = HomecomingColors.Danger) }
                        TextButton(onClick = { showDetail.value = false }) {
                            Text("关闭", color = HomecomingColors.InkSoft)
                        }
                    }
                }
            )
        }
    }
}

// ── 奶白毛毡板 ──

private val FeltTop = Color(0xFFF8F2E4)
private val FeltBottom = Color(0xFFEFE5CF)
private val FrameLight = Color(0xFFCFC0A4)
private val FrameDark = Color(0xFFAE9C7E)
private val PIN_COLORS = listOf(
    Color(0xFFD96A6A), Color(0xFFE8B45A), Color(0xFF7FB3A6),
    Color(0xFFD98AA8), Color(0xFF8AA8D9), Color(0xFFC98A5A)
)

/** 照片槽位：位置按板宽比例，手工调过——边角留给拼豆猫。 */
private data class Slot(val xf: Float, val y: Int, val wf: Float, val aspect: Float, val rot: Float)
private val SLOTS = listOf(
    Slot(0.115f, 116, 0.225f, 1.00f, -3.5f),
    Slot(0.365f, 190, 0.205f, 0.78f, 4.0f),
    Slot(0.375f, 58, 0.280f, 1.35f, -2.0f),
    Slot(0.665f, 166, 0.215f, 1.00f, 3.0f),
    Slot(0.615f, 44, 0.180f, 0.80f, 2.5f),
    Slot(0.790f, 246, 0.190f, 0.84f, -4.5f)
)

private data class Deco(val res: Int, val xf: Float, val y: Int, val size: Int, val rot: Float)
private val DECO_RES = listOf(
    R.drawable.deco_cat_0, R.drawable.deco_cat_1, R.drawable.deco_cat_2,
    R.drawable.deco_cat_3, R.drawable.deco_cat_4, R.drawable.deco_cat_5,
    R.drawable.deco_cat_6, R.drawable.deco_cat_7, R.drawable.deco_cat_8
)
private val DECO = listOf(
    Deco(8, 0.205f, 52, 46, -5f),   // 黑睡月亮
    Deco(1, 0.300f, 14, 40, 3f),    // 白坐蓝结
    Deco(2, 0.545f, 8, 42, -4f),    // 黑坐星
    Deco(3, 0.005f, 150, 44, 4f),   // 白睡云
    Deco(4, 0.455f, 290, 52, -3f),  // 黑猫纸箱
    Deco(5, 0.850f, 130, 44, 5f),   // 白躺蓝结
    Deco(6, 0.665f, 302, 44, -4f),  // 黑坐月牙结
    Deco(7, 0.245f, 296, 40, 3f),   // 白坐项圈
    Deco(0, 0.015f, 288, 44, -2f)   // 趴黑猫
)

@Composable
private fun FeltBoard(
    photos: List<HomecomingAlbumStore.AlbumPhoto>,
    version: Long,
    onPhotoTap: (HomecomingAlbumStore.AlbumPhoto) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .padding(horizontal = 12.dp)
            .height(384.dp)
            .shadow(12.dp, RoundedCornerShape(10.dp))
            .border(8.dp, Brush.verticalGradient(listOf(FrameLight, FrameDark)), RoundedCornerShape(10.dp))
            .clip(RoundedCornerShape(6.dp))
            .background(Brush.verticalGradient(listOf(FeltTop, FeltBottom)))
    ) {
        // 毛毡纤维噪点
        Canvas(modifier = Modifier.fillMaxSize()) {
            val rnd = java.util.Random(20261024L)
            repeat(220) {
                val x = rnd.nextFloat() * size.width
                val y = rnd.nextFloat() * size.height
                val r = 0.8f + rnd.nextFloat() * 1.8f
                drawCircle(
                    color = if (rnd.nextBoolean()) Color(0x14B9A98C) else Color(0x1FFFFFFF),
                    radius = r,
                    center = Offset(x, y)
                )
            }
        }
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val W = maxWidth
            SLOTS.forEachIndexed { i, s ->
                Box(
                    modifier = Modifier
                        .offset(x = W * s.xf, y = s.y.dp)
                        .width(W * s.wf)
                ) {
                    val photo = photos.getOrNull(i)
                    if (photo != null) {
                        SlotPhoto(
                            photo = photo,
                            version = version,
                            aspect = s.aspect,
                            rot = s.rot,
                            pinColor = PIN_COLORS[i % PIN_COLORS.size],
                            onTap = onPhotoTap
                        )
                    } else {
                        EmptySlot(aspect = s.aspect, rot = s.rot)
                    }
                }
            }
            DECO.forEach { d ->
                Image(
                    painter = painterResource(DECO_RES[d.res]),
                    contentDescription = null,
                    modifier = Modifier
                        .offset(x = W * d.xf, y = d.y.dp)
                        .size(d.size.dp)
                        .graphicsLayer {
                            rotationZ = d.rot
                            cameraDistance = 160f * density
                        }
                )
            }
        }
    }
}

/** 槽位上的拍立得：白边相纸 + 彩图钉 + 固定歪斜。 */
@Composable
private fun SlotPhoto(
    photo: HomecomingAlbumStore.AlbumPhoto,
    version: Long,
    aspect: Float,
    rot: Float,
    pinColor: Color,
    onTap: (HomecomingAlbumStore.AlbumPhoto) -> Unit
) {
    val context = LocalContext.current
    val bmp = remember(photo.id, version) {
        runCatching { decodeSampled(photo.file(context), 512) }.getOrNull()
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                rotationZ = rot
                cameraDistance = 160f * density
            }
            .shadow(5.dp, RoundedCornerShape(2.dp))
            .background(Color(0xFFFDFBF5))
            .border(0.5.dp, Color(0x22000000), RoundedCornerShape(2.dp))
            .clickable { onTap(photo) }
            .padding(5.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(aspect)
                .clip(RoundedCornerShape(1.dp))
                .background(Color(0xFFEDE9E0))
        ) {
            if (bmp != null) {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        // 图钉
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = (-5).dp)
                .size(13.dp)
                .shadow(2.dp, CircleShape)
                .background(Brush.radialGradient(listOf(pinColor.copy(alpha = 0.85f), pinColor)), CircleShape)
                .border(0.5.dp, Color(0x44000000), CircleShape)
        )
    }
}

/** 空槽位：虚线「待钉」——预留的放照片位置。 */
@Composable
private fun EmptySlot(aspect: Float, rot: Float) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                rotationZ = rot
                cameraDistance = 160f * density
            }
    ) {
        Canvas(modifier = Modifier.fillMaxWidth().aspectRatio(aspect)) {
            val inset = 1.dp.toPx()
            drawRoundRect(
                color = Color(0x77A99778),
                topLeft = Offset(inset, inset),
                size = Size(size.width - inset * 2, size.height - inset * 2),
                cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx()),
                style = Stroke(
                    width = 2.5f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 9f))
                )
            )
        }
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .clip(RoundedCornerShape(50))
                .background(Color(0x66FFFFFF))
                .padding(horizontal = 10.dp, vertical = 4.dp)
        ) {
            Text("待钉", fontSize = 10.sp, color = Color(0xFF9A8A72), fontFamily = FontFamily.Serif)
        }
    }
}

/** 毛绒挂件：钉在板沿随风摆。onTap=null 时只做装饰（点了弹一下）。 */
@Composable
private fun HangingPlush(
    res: Int,
    modifier: Modifier = Modifier,
    onTap: (() -> Unit)? = null
) {
    val wind = rememberInfiniteTransition(label = "wind")
    val swing by wind.animateFloat(
        -6f, 6f,
        infiniteRepeatable(tween(if (onTap != null) 3200 else 2600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "swing"
    )
    val flutter by wind.animateFloat(
        1.6f, -1.6f,
        infiniteRepeatable(tween(2100, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "flutter"
    )
    val scope = rememberCoroutineScope()
    val bounce = remember { Animatable(1f) }
    Box(
        modifier = modifier
            .graphicsLayer {
                rotationZ = swing + flutter * 0.4f
                transformOrigin = TransformOrigin(0.5f, 0.02f)
                cameraDistance = 240f * density
                scaleX = bounce.value
                scaleY = bounce.value
            }
            .clickable(
                indication = null,
                interactionSource = MutableInteractionSource()
            ) {
                if (onTap != null) {
                    onTap()
                } else {
                    scope.launch {
                        bounce.snapTo(0.85f)
                        bounce.animateTo(1f, spring(dampingRatio = 0.32f, stiffness = Spring.StiffnessMedium))
                    }
                }
            }
    ) {
        Image(
            painter = painterResource(res),
            contentDescription = if (onTap != null) "小猫玩偶（点我看整本相册）" else "小猫玩偶",
            modifier = Modifier.fillMaxSize()
        )
    }
}

/** 缩略图/大图通用降采样解码：长边压到 maxEdge 附近，防 OOM。 */
private fun decodeSampled(f: File, maxEdge: Int): Bitmap? {
    if (!f.exists() || f.length() == 0L) return null
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(f.absolutePath, bounds)
    var sample = 1
    val maxDim = maxOf(bounds.outWidth, bounds.outHeight)
    while (maxDim / (sample * 2) >= maxEdge) sample *= 2
    return BitmapFactory.decodeFile(f.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
}
