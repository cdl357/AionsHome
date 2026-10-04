package com.aion.chat.compose.ui.album

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aion.chat.compose.data.HomecomingAlbumStore
import com.aion.chat.compose.data.HomecomingChatWiring
import com.aion.chat.compose.data.HomecomingRouteConfig
import com.aion.chat.compose.ui.theme.HomecomingColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** 相册（定稿 §三·补充2 · 方案 C）：照片墙 + 点开大图 +「Sean 存这张时在想什么」+ 日期备注可改。 */
@Composable
fun AlbumScreen(onBack: () -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val photos = remember { mutableStateListOf<HomecomingAlbumStore.AlbumPhoto>() }
    val version = remember { mutableStateOf(0L) }

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

    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 12.dp, end = 12.dp, top = 14.dp, bottom = 112.dp
        ),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
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
                        "照片墙 · 每张背后是 Sean 存这张时的心里话",
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
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Text(
                "点一张照片，看/写「Sean 存这张时在想什么」；私心话随时能补能改",
                fontSize = 11.sp, color = HomecomingColors.InkSoft,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        if (photos.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    "墙还空着，存第一张照片吧",
                    fontSize = 13.sp, color = HomecomingColors.InkSoft,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 40.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
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
