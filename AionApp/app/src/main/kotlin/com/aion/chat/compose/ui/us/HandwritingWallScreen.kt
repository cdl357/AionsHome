package com.aion.chat.compose.ui.us

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.graphicsLayer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.aion.chat.compose.data.HandwritingWallStore
import androidx.compose.ui.text.style.TextDecoration

/**
 * 手写墙 / 纸条箱（v0.3.159，她出的框架）：手写信、手账、纸条照片的专属墙。
 * 两列卡片墙：图 + 备注 + 日期 + 作者徽章 + 印象/心情标签；长按改备注/加标签/删除。
 * 顶部 + 选图（相册）。最新在最前。
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun HandwritingWallScreen(onBack: () -> Unit = {}) {
    val context = LocalContext.current
    val wallScope = androidx.compose.runtime.rememberCoroutineScope()
    var rev by remember { mutableIntStateOf(0) }
    var pickCaption by remember { mutableStateOf<String?>(null) }
    var pendingUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var editing by remember { mutableStateOf<HandwritingWallStore.Entry?>(null) }

    val pickImage = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            pendingUri = uri
            pickCaption = ""
        }
    }

    val entries = remember(rev) { HandwritingWallStore.list(context) }

    // 选图后的备注弹窗
    pendingUri?.let { uri ->
        var draft by remember(uri) { mutableStateOf("") }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { pendingUri = null },
            title = { Text("贴到手写墙", fontSize = 16.sp) },
            text = {
                Column {
                    Text("备注一句话（比如「11.15 开会偷偷写的」）", fontSize = 12.sp, color = Color(0xFF8A8279))
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        placeholder = { Text("备注", fontSize = 12.sp) },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    wallScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                        val added = runCatching {
                            HandwritingWallStore.addFromUri(context, uri, draft.trim(), "yuri")
                        }.getOrNull()
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                            pendingUri = null
                            Toast.makeText(
                                context,
                                if (added != null) "贴好了" else "存图失败",
                                Toast.LENGTH_SHORT
                            ).show()
                            rev++
                        }
                    }
                }) { Text("贴上", color = Color(0xFF7A6A55)) }
            },
            dismissButton = { TextButton(onClick = { pendingUri = null }) { Text("取消") } }
        )
    }

    // 长按编辑弹窗
    editing?.let { e ->
        var captionDraft by remember(e.id) { mutableStateOf(e.caption) }
        var tagDraft by remember(e.id) { mutableStateOf("") }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text("这张纸条", fontSize = 16.sp) },
            text = {
                Column {
                    OutlinedTextField(
                        value = captionDraft,
                        onValueChange = { captionDraft = it },
                        label = { Text("备注", fontSize = 12.sp) },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = tagDraft,
                            onValueChange = { tagDraft = it },
                            label = { Text("印象/心情标签", fontSize = 12.sp) },
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = {
                            if (tagDraft.isNotBlank()) {
                                HandwritingWallStore.addTag(context, e.id, tagDraft.trim())
                                tagDraft = ""
                                rev++
                            }
                        }) { Text("+标签") }
                    }
                    if (e.tags.isNotEmpty()) {
                        Text(
                            "标签：" + e.tags.joinToString(" · "),
                            fontSize = 11.sp, color = Color(0xFF8A8279),
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    HandwritingWallStore.updateCaption(context, e.id, captionDraft.trim())
                    editing = null
                    rev++
                }) { Text("保存", color = Color(0xFF7A6A55)) }
            },
            dismissButton = {
                Row {
                    var confirmDel by remember(e.id) { mutableStateOf(false) }
                    TextButton(onClick = {
                        if (!confirmDel) { confirmDel = true } else {
                            HandwritingWallStore.delete(context, e.id)
                            editing = null
                            rev++
                        }
                    }) { Text(if (confirmDel) "确认删除" else "删除", color = Color(0xFFB3564D)) }
                    TextButton(onClick = { editing = null }) { Text("取消") }
                }
            }
        )
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Color(0xFFF2F0EB))
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .clickable { onBack() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回",
                    tint = Color(0xFF26211C), modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.weight(1f))
            Button(
                onClick = {
                    pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                shape = RoundedCornerShape(10.dp),
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF7A6A55), contentColor = Color.White
                )
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Text("贴一张", fontSize = 12.sp, modifier = Modifier.padding(start = 4.dp))
            }
        }
        Text(
            "手写墙",
            fontSize = 40.sp, color = Color(0xFF26211C), fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Text(
            "手写信、手账、纸条照片都收在这——和普通聊天图片分开。长按可改备注、加印象标签",
            fontSize = 11.sp, color = Color(0xFF8A8279), lineHeight = 15.sp,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )
        Spacer(Modifier.height(8.dp))

        if (entries.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(60.dp))
                Text("墙上还空着。", fontSize = 16.sp, color = Color(0xFF26211C))
                Spacer(Modifier.height(8.dp))
                Text(
                    "把那些偷偷写的纸条拍下来贴上来——\n右上角「贴一张」开始。",
                    fontSize = 13.sp, color = Color(0xFF8A8279),
                    textAlign = TextAlign.Center, lineHeight = 20.sp
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                Modifier.fillMaxSize().padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 20.dp)
            ) {
                items(entries, key = { it.id }) { e ->
                    val bmpState = androidx.compose.runtime.produceState<android.graphics.Bitmap?>(
                        null, e.id
                    ) {
                        withContext(kotlinx.coroutines.Dispatchers.IO) {
                            // v0.3.162 P1-3：降采样解码（长边 512 内），不再主线程全尺寸解原图
                            value = runCatching {
                                if (!java.io.File(e.file).exists()) return@runCatching null
                                val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                                android.graphics.BitmapFactory.decodeFile(e.file, bounds)
                                var sample = 1
                                while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 512) sample *= 2
                                android.graphics.BitmapFactory.decodeFile(
                                    e.file, android.graphics.BitmapFactory.Options().apply { inSampleSize = sample }
                                )
                            }.getOrNull()
                        }
                    }
                    val bmp = bmpState.value
                    Column(
                        Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color.White)
                            .border(0.8.dp, Color(0x22405A66), RoundedCornerShape(14.dp))
                            .combinedClickable(
                                onClick = {},
                                onLongClick = { editing = e }
                            )
                            .padding(8.dp)
                    ) {
                        bmp?.let {
                            Image(
                                bitmap = it.asImageBitmap(),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(150.dp)
                                    .clip(RoundedCornerShape(10.dp))
                            )
                        } ?: Box(
                            Modifier
                                .fillMaxWidth()
                                .height(150.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFFEFEAE2)),
                            contentAlignment = Alignment.Center
                        ) { Text("图不见了", fontSize = 11.sp, color = Color(0xFFB9B2A8)) }
                        if (e.caption.isNotBlank()) {
                            Text(
                                e.caption, fontSize = 12.sp, color = Color(0xFF26211C),
                                maxLines = 2, lineHeight = 16.sp,
                                modifier = Modifier.padding(top = 6.dp)
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            Text(
                                if (e.author == "sean") "🤍 Sean" else "🩵 小鑫",
                                fontSize = 10.sp, color = Color(0xFF8A8279)
                            )
                            Spacer(Modifier.weight(1f))
                            Text(
                                SimpleDateFormat("MM-dd", java.util.Locale.US)
                                    .format(java.util.Date(e.createdAt)),
                                fontSize = 10.sp, color = Color(0xFFB9B2A8)
                            )
                        }
                        if (e.tags.isNotEmpty()) {
                            Text(
                                "🏷 " + e.tags.joinToString(" · "),
                                fontSize = 10.sp, color = Color(0xFF7A6A55),
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
