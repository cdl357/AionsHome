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
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
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
 * 相册四稿（Yuri 反馈迭代）：可自由布置的毛毡板。
 * - 板顶一排挂钉（像参考图那样挂玩偶）；黑猫/白猫挂在钉上随风摆，黑猫=入口，白猫装饰
 * - 照片（最近 10 张）与 9 只拼豆猫默认散乱分布（不围圈），全部支持【长按拖动】自由布置，
 *   位置持久化（album_board prefs）；装饰永远压在照片上层
 * - 点照片 → 大图详情（不变）；点黑猫 ↔ 整本相册网格
 */
@Composable
fun AlbumScreen(onBack: () -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val photos = remember { mutableStateListOf<HomecomingAlbumStore.AlbumPhoto>() }
    val version = remember { mutableStateOf(0L) }
    val boardMode = remember { mutableStateOf(true) }

    // ── 拖动布置：key -> (xf, yf)，持久化到 prefs ──
    val boardPrefs = remember {
        context.getSharedPreferences("album_board", android.content.Context.MODE_PRIVATE)
    }
    val boardPositions = remember { mutableStateMapOf<String, Pair<Float, Float>>() }
    val draggingKey = remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        photos.clear()
        photos.addAll(HomecomingAlbumStore.list(context))
        version.value++
        // ── 云端相册导入（album_photos）：没导过的行 → 解码 → 存本地相册；幂等 ──
        withContext(Dispatchers.IO) {
            runCatching {
                val imported = boardPrefs.getStringSet("cloud_imported", emptySet()).orEmpty().toMutableSet()
                val res = com.aion.chat.compose.data.SupabaseMomentsStore.fetchAlbumPhotos()
                val rows = res.data.orEmpty()
                var added = 0
                rows.forEach { cp ->
                    if (cp.id in imported) return@forEach
                    val bytes = decodeCloudImage(cp.image) ?: return@forEach
                    val date = cp.date.take(10).ifBlank { java.time.LocalDate.now().toString() }
                    HomecomingAlbumStore.addPhoto(context, bytes, cp.note, date)
                    imported.add(cp.id)
                    added++
                }
                if (added > 0) {
                    boardPrefs.edit().putStringSet("cloud_imported", imported).apply()
                }
            }
        }
        photos.clear()
        photos.addAll(HomecomingAlbumStore.list(context))
        version.value++
        // 载入已保存的布置
        runCatching {
            val s = boardPrefs.getString("pos", null) ?: return@runCatching
            val obj = org.json.JSONObject(s)
            obj.keys().forEach { k ->
                val arr = obj.optJSONArray(k) ?: return@forEach
                boardPositions[k] = Pair(arr.getDouble(0).toFloat(), arr.getDouble(1).toFloat())
            }
        }
    }

    fun persistBoard() {
        runCatching {
            val obj = org.json.JSONObject()
            boardPositions.forEach { (k, v) ->
                obj.put(k, org.json.JSONArray().put(v.first).put(v.second))
            }
            boardPrefs.edit().putString("pos", obj.toString()).apply()
        }
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
                withContext(Dispatchers.Main) {
                    photos.clear()
                    photos.addAll(HomecomingAlbumStore.list(context))
                    version.value++
                }
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
                    if (boardMode.value) "毛毡板 · 长按照片和小猫可以挪 · 点黑猫看整本"
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
                    Spacer(Modifier.height(18.dp))
                    FeltBoard(
                        photos = photos.toList(),
                        version = version.value,
                        positions = boardPositions,
                        draggingKey = draggingKey,
                        onPersist = { persistBoard() },
                        onPhotoTap = { selected.value = it; showDetail.value = true },
                        onToggleAlbum = { boardMode.value = !boardMode.value },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(16.dp))
                    TinBox(
                        photos = photos.drop(10),
                        version = version.value,
                        onPhotoTap = { selected.value = it; showDetail.value = true },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "钉着最近的 " + minOf(photos.size, 10) + " 张（长按可挪） · 没上墙的在下面铁盒里",
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
                                photos.clear()
                                photos.addAll(HomecomingAlbumStore.list(context))
                                version.value++
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
                                        photos.clear()
                                        photos.addAll(HomecomingAlbumStore.list(context))
                                        version.value++
                                        Toast.makeText(context, "Sean 写好了", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        }) { Text("Sean 写一句", color = HomecomingColors.Accent) }
                        TextButton(onClick = {
                            scope.launch(Dispatchers.IO) {
                                HomecomingAlbumStore.remove(context, photo.id)
                                withContext(Dispatchers.Main) {
                                    photos.clear()
                                    photos.addAll(HomecomingAlbumStore.list(context))
                                    version.value++
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

// ── 奶白毛毡板（可布置） ──

private val FeltTop = Color(0xFFF8F2E4)
private val FeltBottom = Color(0xFFEFE5CF)
private val FrameLight = Color(0xFFCFC0A4)
private val FrameDark = Color(0xFFAE9C7E)
private val PIN_COLORS = listOf(
    Color(0xFFD96A6A), Color(0xFFE8B45A), Color(0xFF7FB3A6),
    Color(0xFFD98AA8), Color(0xFF8AA8D9), Color(0xFFC98A5A)
)

/** 稳定伪随机：同一张照片每次布局一致。 */
private fun seedRand(id: Long, salt: Int): Float {
    var x = id * 2654435761L + salt * 40503L
    x = x xor (x ushr 13)
    x *= 1274126177L
    x = x xor (x ushr 16)
    return ((x and 0x7fffffffL) % 10000L) / 10000f
}

/** 拼豆猫默认散点（散乱分布，不围圈；被拖过就以保存的位置为准）。 */
private val DECO_DEFAULT = listOf(
    0.06f to 0.52f, 0.30f to 0.14f, 0.72f to 0.28f,
    0.52f to 0.44f, 0.14f to 0.78f, 0.66f to 0.66f,
    0.88f to 0.55f, 0.38f to 0.82f, 0.46f to 0.08f
)
private val DECO_SIZE = listOf(40, 38, 40, 42, 46, 40, 38, 36, 42)
private val DECO_RES = listOf(
    R.drawable.deco_cat_0, R.drawable.deco_cat_1, R.drawable.deco_cat_2,
    R.drawable.deco_cat_3, R.drawable.deco_cat_4, R.drawable.deco_cat_5,
    R.drawable.deco_cat_6, R.drawable.deco_cat_7, R.drawable.deco_cat_8
)
/** 照片默认散点（10 个，可拖走）。 */
private val PHOTO_DEFAULT = listOf(
    0.05f to 0.06f, 0.33f to 0.09f, 0.61f to 0.06f,
    0.05f to 0.36f, 0.35f to 0.33f, 0.63f to 0.30f,
    0.06f to 0.64f, 0.34f to 0.61f, 0.62f to 0.56f, 0.67f to 0.72f
)

@Composable
private fun FeltBoard(
    photos: List<HomecomingAlbumStore.AlbumPhoto>,
    version: Long,
    positions: MutableMap<String, Pair<Float, Float>>,
    draggingKey: MutableState<String?>,
    onPersist: () -> Unit,
    onPhotoTap: (HomecomingAlbumStore.AlbumPhoto) -> Unit,
    onToggleAlbum: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .padding(horizontal = 12.dp)
            .height(430.dp)
            .shadow(12.dp, RoundedCornerShape(10.dp))
            .border(8.dp, Brush.verticalGradient(listOf(FrameLight, FrameDark)), RoundedCornerShape(10.dp))
            .clip(RoundedCornerShape(6.dp))
            .background(Brush.verticalGradient(listOf(FeltTop, FeltBottom)))
    ) {
        // 毛毡纤维 + 顶部挂钉横杆 + 一排钉子（参考图：上面一行钉子挂玩偶）
        Canvas(modifier = Modifier.fillMaxSize()) {
            val rnd = java.util.Random(20261024L)
            repeat(220) {
                drawCircle(
                    color = if (rnd.nextBoolean()) Color(0x14B9A98C) else Color(0x1FFFFFFF),
                    radius = 0.8f + rnd.nextFloat() * 1.8f,
                    center = Offset(rnd.nextFloat() * size.width, rnd.nextFloat() * size.height)
                )
            }
            // 两段式：上段奶咖挂带（52dp）+ 接缝阴影；下段是奶白毛毡（背景渐变）
            val bandH = 52.dp.toPx()
            drawRect(
                brush = Brush.verticalGradient(listOf(Color(0xFFD8CCB1), Color(0xFFC7B89A))),
                topLeft = Offset(0f, 0f),
                size = androidx.compose.ui.geometry.Size(size.width, bandH)
            )
            drawRect(
                brush = Brush.verticalGradient(listOf(Color(0x2E7A6648), Color(0x00806A4A))),
                topLeft = Offset(0f, bandH - 4.dp.toPx()),
                size = androidx.compose.ui.geometry.Size(size.width, 4.dp.toPx() + 6.dp.toPx())
            )
        }

        BoxWithConstraints(Modifier.fillMaxSize()) {
            val W = maxWidth
            val H = maxHeight
            // ── 照片（最近 10 张，可长按拖动；在下层） ──
            photos.take(10).forEachIndexed { i, photo ->
                val key = "photo_" + photo.id
                val d = PHOTO_DEFAULT[i % PHOTO_DEFAULT.size]
                Movable(
                    key = key,
                    boardW = W, boardH = H,
                    defaultXf = d.first, defaultYf = d.second,
                    positions = positions,
                    draggingKey = draggingKey,
                    baseZ = 1f,
                    onPersist = onPersist
                ) {
                    PinnedCard(
                        photo = photo,
                        version = version,
                        width = W * (0.16f + 0.035f * seedRand(photo.id, 7)),
                        aspect = when {
                            seedRand(photo.id, 4) < 0.34f -> 1f
                            seedRand(photo.id, 4) < 0.67f -> 0.8f
                            else -> 1.22f
                        },
                        rot = (seedRand(photo.id, 1) - 0.5f) * 12f,
                        pinColor = PIN_COLORS[i % PIN_COLORS.size],
                        onTap = onPhotoTap
                    )
                }
            }
            // ── 拼豆猫装饰（散乱；压在照片上层） ──
            DECO_RES.forEachIndexed { i, res ->
                val d = DECO_DEFAULT[i]
                Movable(
                    key = "deco_$i",
                    boardW = W, boardH = H,
                    defaultXf = d.first, defaultYf = d.second,
                    positions = positions,
                    draggingKey = draggingKey,
                    baseZ = 2f,
                    onPersist = onPersist
                ) {
                    Image(
                        painter = painterResource(res),
                        contentDescription = null,
                        modifier = Modifier
                            .size(DECO_SIZE[i].dp)
                            .graphicsLayer {
                                rotationZ = (seedRand(i.toLong(), 9) - 0.5f) * 14f
                                cameraDistance = 160f * density
                            }
                    )
                }
            }
            // ── 两只毛绒挂件（挂在顶部钉子上；黑猫=入口，白猫=装饰；最上层） ──
            Movable(
                key = "plush_black",
                boardW = W, boardH = H,
                defaultXf = 0.078f, defaultYf = 0.055f,
                positions = positions,
                draggingKey = draggingKey,
                baseZ = 3f,
                onPersist = onPersist
            ) {
                HangingPlush(
                    res = R.drawable.plush_black,
                    width = 46.dp,
                    still = draggingKey.value == "plush_black",
                    onTap = onToggleAlbum
                )
            }
            Movable(
                key = "plush_white",
                boardW = W, boardH = H,
                defaultXf = 0.792f, defaultYf = 0.055f,
                positions = positions,
                draggingKey = draggingKey,
                baseZ = 3f,
                onPersist = onPersist
            ) {
                HangingPlush(
                    res = R.drawable.plush_white,
                    width = 50.dp,
                    still = draggingKey.value == "plush_white",
                    onTap = null
                )
            }
        }
    }
}

/** 可长按拖动的板面元素：位置存 positions（持久化），拖动中放大 + 置顶。 */
@Composable
private fun Movable(
    key: String,
    boardW: Dp,
    boardH: Dp,
    defaultXf: Float,
    defaultYf: Float,
    positions: MutableMap<String, Pair<Float, Float>>,
    draggingKey: MutableState<String?>,
    baseZ: Float,
    onPersist: () -> Unit,
    content: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit
) {
    val pos = positions[key] ?: (defaultXf to defaultYf)
    val dragging = draggingKey.value == key
    Box(
        modifier = Modifier
            .offset(x = boardW * pos.first, y = boardH * pos.second)
            .zIndex(if (dragging) 9f else baseZ)
            .graphicsLayer {
                if (dragging) {
                    scaleX = 1.06f; scaleY = 1.06f
                    shadowElevation = 14f
                }
            }
            .pointerInput(key, boardW, boardH) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { draggingKey.value = key },
                    onDrag = { change, amt ->
                        change.consume()
                        val cur = positions[key] ?: (defaultXf to defaultYf)
                        val nx = (cur.first + amt.x / boardW.toPx()).coerceIn(-0.04f, 0.96f)
                        val ny = (cur.second + amt.y / boardH.toPx()).coerceIn(0f, 0.9f)
                        positions[key] = nx to ny
                    },
                    onDragEnd = { onPersist(); draggingKey.value = null },
                    onDragCancel = { onPersist(); draggingKey.value = null }
                )
            },
        content = content
    )
}

/** 拍立得照片卡（可点开大图）。 */
@Composable
private fun PinnedCard(
    photo: HomecomingAlbumStore.AlbumPhoto,
    version: Long,
    width: Dp,
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
            .width(width)
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
        Spacer(Modifier.height(8.dp))
    }
}

/** 挂在钉子上的毛绒挂件：随风摆；still=true（拖动中）时停稳。 */
@Composable
private fun HangingPlush(
    res: Int,
    width: Dp,
    still: Boolean,
    onTap: (() -> Unit)? = null
) {
    val wind = rememberInfiniteTransition(label = "wind")
    val damp by animateFloatAsState(if (still) 0f else 1f, tween(300), label = "damp")
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
    val h = width * (340f / 175f)
    Box(
        modifier = Modifier
            .size(width = width, height = h)
            .graphicsLayer {
                rotationZ = (swing + flutter * 0.4f) * damp
                transformOrigin = TransformOrigin(0.5f, 0.02f)
                cameraDistance = 240f * density
            }
            .clickable(
                indication = null,
                interactionSource = MutableInteractionSource()
            ) {
                if (onTap != null) onTap()
            }
    ) {
        // 挂钉（在绳圈后面，做出钉在毛毡上的感觉）
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = (-2).dp)
                .size(11.dp)
                .shadow(1.dp, CircleShape)
                .background(Brush.radialGradient(listOf(Color(0xFFF0E8D8), Color(0xFF9A8E7E))), CircleShape)
        )
        Image(
            painter = painterResource(res),
            contentDescription = if (onTap != null) "小猫玩偶（点我看整本相册）" else "小猫玩偶",
            modifier = Modifier.fillMaxSize()
        )
    }
}

/** 铁盒子：没上墙的照片做成竖版小卡插在盒里，滑动时有拨动卡片的摆动效果。 */
@Composable
private fun TinBox(
    photos: List<HomecomingAlbumStore.AlbumPhoto>,
    version: Long,
    onPhotoTap: (HomecomingAlbumStore.AlbumPhoto) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    BoxWithConstraints(
        modifier
            .padding(horizontal = 12.dp)
            .height(150.dp)
    ) {
        val W = maxWidth
        Box(
            modifier = Modifier
                .fillMaxSize()
                .shadow(7.dp, RoundedCornerShape(12.dp))
                .clip(RoundedCornerShape(12.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFFEDEBE4), Color(0xFFC9C5BB), Color(0xFFA8A49A))
                    )
                )
                .border(1.dp, Color(0x66FFFFFF), RoundedCornerShape(12.dp))
        ) {
            // 金属拉丝纹
            Canvas(modifier = Modifier.fillMaxSize()) {
                repeat(26) { i ->
                    val y = i * size.height / 26f
                    drawLine(
                        color = Color(0x12FFFFFF),
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = 1f
                    )
                }
            }
            // 开口内阴影
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(14.dp)
                    .background(Brush.verticalGradient(listOf(Color(0x59000000), Color.Transparent)))
            )
            if (photos.isEmpty()) {
                Text(
                    "都在墙上呢 · 存新照片会先钉上毛毡板",
                    fontSize = 11.sp, color = Color(0xFF7A766C),
                    fontFamily = FontFamily.Serif,
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                val scroll = rememberScrollState()
                val cardW = 64.dp
                val overlap = 30.dp
                val stride = with(density) { (cardW - overlap).toPx() }
                val startPad = with(density) { 18.dp.toPx() }
                val viewport = with(density) { W.toPx() }
                val cardWpx = with(density) { cardW.toPx() }
                Row(
                    verticalAlignment = Alignment.Top,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 6.dp)
                        .horizontalScroll(scroll)
                ) {
                    Spacer(Modifier.width(18.dp))
                    photos.forEachIndexed { i, photo ->
                        // 拨动效果：卡片中心离视口中心越远越歪，带 3D 转角
                        val centerPx = startPad + i * stride + cardWpx / 2f - scroll.value
                        val rel = ((centerPx - viewport / 2f) / viewport).coerceIn(-0.6f, 0.6f)
                        Box(modifier = Modifier.width(cardW - overlap)) {
                            TinCard(
                                photo = photo,
                                version = version,
                                width = cardW,
                                rel = rel,
                                onTap = onPhotoTap
                            )
                        }
                    }
                    Spacer(Modifier.width(22.dp))
                }
            }
            // 前壁（盖住卡片下沿 → 卡片插在盒里）
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(26.dp)
                    .background(
                        Brush.verticalGradient(listOf(Color(0xFFDCD8CE), Color(0xFFABA79C)))
                    )
            )
            Text(
                "TIN · 没上墙的照片都在这",
                fontSize = 9.sp, color = Color(0xFF6E6A60),
                fontFamily = FontFamily.Serif,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 12.dp, bottom = 6.dp)
            )
        }
    }
}

/** 铁盒里的竖版小卡。 */
@Composable
private fun TinCard(
    photo: HomecomingAlbumStore.AlbumPhoto,
    version: Long,
    width: Dp,
    rel: Float,
    onTap: (HomecomingAlbumStore.AlbumPhoto) -> Unit
) {
    val context = LocalContext.current
    val bmp = remember(photo.id, version) {
        runCatching { decodeSampled(photo.file(context), 384) }.getOrNull()
    }
    Box(
        modifier = Modifier
            .width(width)
            .graphicsLayer {
                rotationZ = rel * 12f
                rotationY = -rel * 18f
                cameraDistance = 220f * density
                shadowElevation = 6f
            }
            .shadow(4.dp, RoundedCornerShape(3.dp))
            .clip(RoundedCornerShape(3.dp))
            .background(Color(0xFFFDFBF5))
            .border(0.7.dp, Color(0x33000000), RoundedCornerShape(3.dp))
            .clickable { onTap(photo) }
            .padding(3.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.72f)
                .clip(RoundedCornerShape(2.dp))
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
    }
}

/** 云端图片字段 → 字节：支持 dataURL / 纯 base64 / http 链接；解不出返回 null。 */
private fun decodeCloudImage(v: String): ByteArray? = runCatching {
    when {
        v.startsWith("data:") -> android.util.Base64.decode(
            v.substringAfter("base64,"), android.util.Base64.DEFAULT
        )
        v.startsWith("http") -> java.net.URL(v).readBytes()
        v.length > 100 && v.none { it.isWhitespace() } -> android.util.Base64.decode(v, android.util.Base64.DEFAULT)
        else -> null
    }
}.getOrNull()

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
