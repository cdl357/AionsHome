package com.aion.chat.compose.ui.album

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
 * 相册（Yuri 设计二稿）：软木毛毡板照片墙。
 * - 板：木框软木底（噪点纹理），照片做成拍立得+彩图钉，按种子随机歪斜/错位地钉上去
 * - 点照片 → 大图详情（Sean 私心话 / 日期 / 删除，逻辑不变）
 * - 左上角挂着小猫玩偶：模拟风轻轻摆动；点它 ↔ 切到「整本相册」网格
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
                    if (boardMode.value) "毛毡板 · 钉起来的照片 · 点小猫看整本"
                    else "整本相册 · 点小猫回毛毡板",
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
        Spacer(Modifier.height(10.dp))

        // ── 内容：毛毡板 / 整本网格 ──
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (boardMode.value) {
                CorkBoard(
                    photos = photos.toList(),
                    version = version.value,
                    onPhotoTap = { selected.value = it; showDetail.value = true },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = 12.dp, end = 12.dp, top = 6.dp, bottom = 112.dp
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

            // ── 左上角小猫玩偶（会随风摆；点它切整本相册） ──
            HangingCat(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(x = 10.dp, y = 2.dp)
                    .zIndex(5f),
                onTap = { boardMode.value = !boardMode.value }
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

// ── 软木毛毡板（木框 + 噪点纹理 + 拍立得钉墙） ──

private val CorkTop = Color(0xFFC9AA7D)
private val CorkBottom = Color(0xFFBE9C6E)
private val WoodFrame = Color(0xFF5F4732)
private val PIN_COLORS = listOf(
    Color(0xFFD96A6A), Color(0xFFE8B45A), Color(0xFF7FB3A6),
    Color(0xFFD98AA8), Color(0xFF8AA8D9), Color(0xFFC98A5A)
)

/** 稳定伪随机：同一张照片每次布局一致，不随重排洗牌。 */
private fun seedRand(id: Long, salt: Int): Float {
    var x = id * 2654435761L + salt * 40503L
    x = x xor (x ushr 13)
    x *= 1274126177L
    x = x xor (x ushr 16)
    return ((x and 0x7fffffffL) % 10000L) / 10000f
}

@Composable
private fun CorkBoard(
    photos: List<HomecomingAlbumStore.AlbumPhoto>,
    version: Long,
    onPhotoTap: (HomecomingAlbumStore.AlbumPhoto) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .padding(horizontal = 10.dp)
            .shadow(8.dp, RoundedCornerShape(10.dp))
            .border(7.dp, Brush.verticalGradient(listOf(Color(0xFF6B5138), WoodFrame)), RoundedCornerShape(10.dp))
            .clip(RoundedCornerShape(6.dp))
            .background(Brush.verticalGradient(listOf(CorkTop, CorkBottom)))
    ) {
        // 软木噪点纹理
        Canvas(modifier = Modifier.fillMaxSize()) {
            val rnd = java.util.Random(20261024L)
            repeat(240) {
                val x = rnd.nextFloat() * size.width
                val y = rnd.nextFloat() * size.height
                val r = 1.2f + rnd.nextFloat() * 2.6f
                val dark = rnd.nextBoolean()
                drawCircle(
                    color = if (dark) Color(0x2E8A6A46) else Color(0x33FFF2DC),
                    radius = r,
                    center = androidx.compose.ui.geometry.Offset(x, y)
                )
            }
            // 几道木框内侧高光
            drawRect(
                color = Color(0x26FFFFFF),
                topLeft = androidx.compose.ui.geometry.Offset(0f, 0f),
                size = androidx.compose.ui.geometry.Size(size.width, 3f)
            )
        }

        if (photos.isEmpty()) {
            Text(
                "毛毡板还空着——存几张照片钉上来吧",
                fontSize = 13.sp, color = Color(0xFF6B5138),
                fontFamily = FontFamily.Serif,
                modifier = Modifier.align(Alignment.Center).padding(24.dp)
            )
        }

        LazyVerticalStaggeredGrid(
            columns = StaggeredGridCells.Fixed(2),
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 14.dp, end = 14.dp, top = 16.dp, bottom = 116.dp
            ),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalItemSpacing = 14.dp
        ) {
            items(photos, key = { it.id }) { photo ->
                PinnedPhoto(
                    photo = photo,
                    version = version,
                    onPhotoTap = onPhotoTap
                )
            }
        }
    }
}

/** 拍立得：白边相纸 + 彩色图钉 + 种子随机歪斜错位（每张钉法固定）。 */
@Composable
private fun PinnedPhoto(
    photo: HomecomingAlbumStore.AlbumPhoto,
    version: Long,
    onPhotoTap: (HomecomingAlbumStore.AlbumPhoto) -> Unit
) {
    val context = LocalContext.current
    val bmp = remember(photo.id, version) {
        runCatching { decodeSampled(photo.file(context), 512) }.getOrNull()
    }
    val rotation = (seedRand(photo.id, 1) - 0.5f) * 14f          // ±7°
    val dx = (seedRand(photo.id, 2) - 0.5f) * 14f                 // 左右错位
    val dy = (seedRand(photo.id, 3) - 0.5f) * 10f                 // 上下错位
    val photoAspect = when {
        seedRand(photo.id, 4) < 0.34f -> 1f
        seedRand(photo.id, 4) < 0.67f -> 0.8f
        else -> 1.22f
    }
    val pinColor = PIN_COLORS[(seedRand(photo.id, 5) * PIN_COLORS.size).toInt()
        .coerceIn(0, PIN_COLORS.lastIndex)]

    Box(
        modifier = Modifier
            .graphicsLayer {
                rotationZ = rotation
                translationX = dx * density
                translationY = dy * density
                cameraDistance = 160f * density
            }
            .shadow(5.dp, RoundedCornerShape(2.dp))
            .background(Color(0xFFFDFBF5))
            .border(0.5.dp, Color(0x22000000), RoundedCornerShape(2.dp))
            .clickable { onPhotoTap(photo) }
            .padding(6.dp)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(photoAspect)
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
                if (photo.note.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .background(Color.Black.copy(alpha = 0.35f))
                            .padding(horizontal = 5.dp, vertical = 3.dp)
                    ) {
                        Text(
                            "♥ " + photo.note,
                            fontSize = 9.sp, color = Color.White,
                            maxLines = 1,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
        }
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

/** 左上角挂着的毛绒小猫：钉在板上，模拟风轻轻摆动；点它切整本相册。 */
@Composable
private fun HangingCat(
    modifier: Modifier = Modifier,
    onTap: () -> Unit
) {
    val wind = rememberInfiniteTransition(label = "wind")
    val swing by wind.animateFloat(
        -6f, 6f,
        infiniteRepeatable(tween(3200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "swing"
    )
    val flutter by wind.animateFloat(
        1.6f, -1.6f,
        infiniteRepeatable(tween(2100, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "flutter"
    )
    Box(
        modifier = modifier
            .size(width = 78.dp, height = 96.dp)
            .graphicsLayer {
                rotationZ = swing + flutter * 0.45f
                transformOrigin = TransformOrigin(0.5f, 0.03f)
                cameraDistance = 240f * density
            }
            .clickable(
                indication = null,
                interactionSource = androidx.compose.foundation.interaction.MutableInteractionSource()
            ) { onTap() }
    ) {
        // 钉在板上的小钉子
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .size(10.dp)
                .shadow(1.dp, CircleShape)
                .background(Brush.radialGradient(listOf(Color(0xFFF3E2C7), Color(0xFFB08D5A))), CircleShape)
        )
        Image(
            painter = painterResource(R.drawable.plush_cat),
            contentDescription = "小猫玩偶（点我看整本相册）",
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 7.dp)
                .fillMaxSize()
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
