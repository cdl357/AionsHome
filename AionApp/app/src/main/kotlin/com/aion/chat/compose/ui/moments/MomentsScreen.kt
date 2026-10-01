package com.aion.chat.compose.ui.moments

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.PhotoCamera
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
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aion.chat.compose.data.HomecomingChatWiring
import com.aion.chat.compose.data.HomecomingMomentsStore
import com.aion.chat.compose.data.HomecomingRouteConfig
import com.aion.chat.compose.data.HomecomingMomentsStore.Moment
import com.aion.chat.compose.ui.home.GlassAvatar
import com.aion.chat.compose.ui.theme.HomecomingColors
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 页面④：朋友圈。双向动态流（发文字+配图、点赞评论），独立存本地，不进日历。 */
@Composable
fun MomentsScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val main = remember { Handler(Looper.getMainLooper()) }

    val feed = remember { mutableStateListOf<Moment>() }
    val reloadKey = remember { mutableStateOf(0) }
    val coverStamp = remember { mutableStateOf(0L) }
    val routeStamp = HomecomingRouteConfig.stamp(context)

    val wiring = remember(routeStamp) { HomecomingChatWiring(context) }
    val route = remember(routeStamp) { HomecomingRouteConfig.mainRoute(context) }
    val modelKey = remember(routeStamp) { wiring.mainModelKey() }
    val routeReady = route != null

    val coverBitmap = remember(coverStamp.value) {
        runCatching {
            val f = File(context.filesDir, "moments_cover.jpg")
            if (f.exists()) BitmapFactory.decodeFile(f.absolutePath)?.asImageBitmap() else null
        }.getOrNull()
    }

    fun reload() {
        try {
            val list = HomecomingMomentsStore.feed(context)
            main.post {
                feed.clear()
                feed.addAll(list)
            }
        } catch (e: Exception) { /* 安静 */ }
    }

    LaunchedEffect(reloadKey.value) {
        withContext(Dispatchers.IO) { reload() }
    }

    // ── Sean 惰性回应：Yuri 发动态/评论后，线路已配置时让 Sean 回一句 ──
    fun askSeanReply(momentId: Long, momentContent: String, userLine: String) {
        if (!routeReady) return
        scope.launch(Dispatchers.IO) {
            delay(1200L) // 惰性生成：过一会儿才来
            try {
                val trigger = buildString {
                    append("Yuri 的朋友圈动态：『").append(momentContent.take(80)).append("』。")
                    if (userLine.isNotBlank()) {
                        append("Yuri 刚刚评论说：『").append(userLine.take(60)).append("』。")
                    }
                    append("以 Sean 的身份回一句评论：一句话、自然口语、不超过 30 个字。")
                }
                wiring.engine.send(
                    com.aion.chat.homecoming.HomecomingChatEngine.ChatCommand(
                        "req_m_" + System.currentTimeMillis(),
                        "moments_private", "sean", "user", trigger, "main", modelKey, "", ""
                    ),
                    object : com.aion.chat.homecoming.HomecomingChatEngine.Observer {
                        override fun onChunk(chunk: String) {}
                        override fun onComplete(messageId: String, text: String) {
                            val reply = text.trim()
                            if (reply.isNotEmpty()) {
                                HomecomingMomentsStore.addComment(context, momentId, "sean", reply)
                                main.post { reload() }
                            }
                        }
                        override fun onFailure(code: String) { /* 无线路时安静 */ }
                    }
                )
            } catch (e: Exception) { /* 安静 */ }
        }
    }

    // ── 状态 ──
    val showCompose = remember { mutableStateOf(false) }
    val composeText = remember { mutableStateOf("") }
    val composeImages = remember { mutableStateListOf<android.net.Uri>() }
    val commentTarget = remember { mutableStateOf<Long?>(null) }
    val commentDraft = remember { mutableStateOf("") }
    val deleteTarget = remember { mutableStateOf<Long?>(null) }
    var lastPostedId by remember { mutableStateOf(0L) }

    // ── 发布配图选择（最多 4 张） ──
    val composePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(maxItems = 4)
    ) { uris ->
        uris.take(4).forEach { uri ->
            scope.launch(Dispatchers.IO) {
                val saved = runCatching {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        val bytes = input.readBytes()
                        HomecomingMomentsStore.saveMomentImage(context, bytes)
                    }
                }.getOrNull()
                if (saved != null) {
                    main.post {
                        composeImages.add(
                            android.net.Uri.parse("file://" + saved)
                        )
                    }
                }
            }
        }
    }

    // ── 封面更换 ──
    val coverPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        File(context.filesDir, "moments_cover.jpg").outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                }
                main.post { coverStamp.value = System.currentTimeMillis() }
            }
        }
    }

    // ── Sean 回复后刷新 ──
    fun reloadAfterSean() {
        main.post { reload() }
    }
    LaunchedEffect(lastPostedId) {
        if (lastPostedId != 0L) {
            // Sean 的评论由 askSeanReply 异步落库，这里只负责保持最新
        }
    }

    // ── 界面 ──
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(HomecomingColors.IceBlueLight)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                // ── 封面头图（可换） ──
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(190.dp)
                ) {
                    val cover = coverBitmap
                    if (cover != null) {
                        Image(
                            bitmap = cover,
                            contentDescription = "封面",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        listOf(HomecomingColors.IceBlue, HomecomingColors.IceBlueLight)
                                    )
                                )
                        )
                    }
                    Icon(
                        imageVector = Icons.Outlined.PhotoCamera,
                        contentDescription = "换封面",
                        tint = Color.White,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(12.dp)
                            .clickable {
                                coverPicker.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            }
                    )
                    Text(
                        "朋友圈",
                        fontSize = 20.sp,
                        color = Color.White,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(16.dp)
                    )
                }

                // ── 动态流 ──
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                    if (feed.isEmpty()) {
                        Text(
                            "还没有动态，发第一条吧",
                            fontSize = 13.sp,
                            color = HomecomingColors.InkSoft,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                    feed.forEach { m ->
                        MomentCard(
                            moment = m,
                            commentOpen = commentTarget.value == m.id,
                            onToggleLike = {
                                val liked = HomecomingMomentsStore.toggleLike(context, m.id, "user")
                                reload()
                                if (liked && m.author == "user") {
                                    // 自己给自己点赞不需要 Sean 回应
                                }
                            },
                            onComment = {
                                commentTarget.value =
                                    if (commentTarget.value == m.id) null else m.id
                            },
                            onSendComment = { text ->
                                if (HomecomingMomentsStore.addComment(context, m.id, "user", text)) {
                                    commentTarget.value = null
                                    reload()
                                    askSeanReply(m.id, m.content, text)
                                }
                            },
                            onDeleteComment = { cid ->
                                HomecomingMomentsStore.deleteComment(context, cid)
                                reload()
                            },
                            onDeleteMoment = { deleteTarget.value = m.id }
                        )
                        Spacer(Modifier.height(12.dp))
                    }
                }
            }

            // ── 发布按钮（右上角） ──
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 10.dp, end = 14.dp)
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.30f))
                    .clickable { showCompose.value = true },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = "发动态",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }

    // ── 发布抽屉 ──
    if (showCompose.value) {
        AlertDialog(
            onDismissRequest = { showCompose.value = false },
            title = { Text("发动态", fontSize = 16.sp, color = HomecomingColors.Ink) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = composeText.value,
                        onValueChange = { composeText.value = it },
                        placeholder = { Text("这一刻的想法…", color = HomecomingColors.InkSoft) },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (composeImages.isNotEmpty()) {
                        Text(
                            "已配 ${composeImages.size} 张图",
                            fontSize = 11.sp,
                            color = HomecomingColors.InkSoft
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = {
                            composePicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        }) { Text("配图", color = HomecomingColors.Accent) }
                        TextButton(onClick = {
                            val text = composeText.value.trim()
                            if (text.isEmpty() && composeImages.isEmpty()) {
                                Toast.makeText(context, "写点什么或配张图", Toast.LENGTH_SHORT).show()
                                return@TextButton
                            }
                            scope.launch(Dispatchers.IO) {
                                val saved = composeImages.mapNotNull { uri ->
                                    runCatching {
                                        context.contentResolver.openInputStream(uri)?.use { input ->
                                            val bytes = input.readBytes()
                                            HomecomingMomentsStore.saveMomentImage(context, bytes)
                                        }
                                    }.getOrNull()
                                }
                                val newId = HomecomingMomentsStore.addMoment(context, "user", text, saved)
                                main.post {
                                    composeText.value = ""
                                    composeImages.clear()
                                    showCompose.value = false
                                    reload()
                                    if (newId > 0) {
                                        lastPostedId = newId
                                        askSeanReply(newId, text, "")
                                    }
                                }
                            }
                        }) { Text("发布", color = HomecomingColors.Accent) }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {}
        )
    }

    // ── 删除确认 ──
    deleteTarget.value?.let { targetId ->
        AlertDialog(
            onDismissRequest = { deleteTarget.value = null },
            title = { Text("删掉这条动态？", fontSize = 16.sp, color = HomecomingColors.Ink) },
            confirmButton = {
                TextButton(onClick = {
                    HomecomingMomentsStore.deleteMoment(context, targetId)
                    deleteTarget.value = null
                    reload()
                }) { Text("删除", color = HomecomingColors.Danger) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget.value = null }) { Text("取消", color = HomecomingColors.InkSoft) }
            }
        )
    }
}

/** 单条动态（微信式：头像+名字+文字+图，底下点赞评论灰盒）。 */
@Composable
private fun MomentCard(
    moment: Moment,
    commentOpen: Boolean,
    onToggleLike: () -> Unit,
    onComment: () -> Unit,
    onSendComment: (String) -> Unit,
    onDeleteComment: (Long) -> Unit,
    onDeleteMoment: () -> Unit
) {
    val name = when (moment.author) { "user" -> "Yuri"; "sean" -> "Sean"; else -> moment.author }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = 0.55f))
            .border(1.dp, Color.White.copy(alpha = 0.7f), RoundedCornerShape(18.dp))
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.Top) {
            GlassAvatar(
                initial = if (moment.author == "user") "Y" else "S",
                size = 44
            )
            Column(modifier = Modifier.weight(1f).padding(start = 10.dp)) {
                Text(
                    name,
                    fontSize = 14.sp, fontWeight = FontWeight.Medium, color = HomecomingColors.Ink
                )
                if (moment.content.isNotBlank()) {
                    Text(
                        moment.content,
                        fontSize = 15.sp, color = HomecomingColors.Ink,
                        modifier = Modifier.padding(top = 3.dp)
                    )
                }
                if (moment.attachments.isNotEmpty()) {
                    Column(modifier = Modifier.padding(top = 8.dp)) {
                        moment.attachments.take(3).forEach { path ->
                            val bmp = remember(path) {
                                runCatching {
                                    val opts = BitmapFactory.Options().apply { inSampleSize = 2 }
                                    BitmapFactory.decodeFile(path, opts)
                                }.getOrNull()
                            }
                            if (bmp != null) {
                                Image(
                                    bitmap = bmp.asImageBitmap(),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(150.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .padding(vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    Text(relTime(moment.createdAt), fontSize = 11.sp, color = HomecomingColors.InkSoft)
                    Spacer(Modifier.weight(1f))
                    if (moment.author == "user") {
                        Text(
                            "删除",
                            fontSize = 11.sp, color = HomecomingColors.InkSoft,
                            modifier = Modifier
                                .padding(end = 10.dp)
                                .clickable { onDeleteMoment() }
                        )
                    }
                    Icon(
                        imageVector = Icons.Filled.FavoriteBorder,
                        contentDescription = "赞",
                        tint = HomecomingColors.InkSoft,
                        modifier = Modifier
                            .size(18.dp)
                            .clickable { onToggleLike() }
                    )
                    Spacer(Modifier.width(10.dp))
                    Icon(
                        imageVector = Icons.Outlined.ChatBubbleOutline,
                        contentDescription = "评论",
                        tint = HomecomingColors.InkSoft,
                        modifier = Modifier
                            .size(18.dp)
                            .clickable { onComment() }
                    )
                }
                if (moment.likes.isNotEmpty() || moment.comments.isNotEmpty() || commentOpen) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.White.copy(alpha = 0.55f))
                            .padding(8.dp)
                    ) {
                        if (moment.likes.isNotEmpty()) {
                            Text(
                                "♥ " + moment.likes.joinToString("、") { l ->
                                    when (l) { "user" -> "Yuri"; "sean" -> "Sean"; else -> l }
                                },
                                fontSize = 12.sp, color = HomecomingColors.Ink
                            )
                        }
                        moment.comments.forEach { c ->
                            Row(modifier = Modifier.padding(top = 3.dp)) {
                                Text(
                                    buildString {
                                        append(
                                            when (c.author) {
                                                "user" -> "Yuri"; "sean" -> "Sean"; else -> c.author
                                            }
                                        )
                                        append("：")
                                        append(c.content)
                                    },
                                    fontSize = 13.sp, color = HomecomingColors.Ink,
                                    modifier = Modifier.weight(1f)
                                )
                                if (c.author == "user") {
                                    Text(
                                        "✕",
                                        fontSize = 11.sp, color = HomecomingColors.InkSoft,
                                        modifier = Modifier.clickable { onDeleteComment(c.id) }
                                    )
                                }
                            }
                        }
                        if (commentOpen) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 6.dp)
                            ) {
                                val draft = remember(moment.id) { mutableStateOf("") }
                                OutlinedTextField(
                                    value = draft.value,
                                    onValueChange = { draft.value = it },
                                    placeholder = {
                                        Text("搭一句…", fontSize = 12.sp, color = HomecomingColors.InkSoft)
                                    },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true
                                )
                                TextButton(onClick = {
                                    if (draft.value.isNotBlank()) onSendComment(draft.value.trim())
                                }) { Text("发送", color = HomecomingColors.Accent) }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun relTime(ts: Long): String {
    if (ts <= 0) return ""
    val diff = System.currentTimeMillis() - ts
    val m = diff / 60000
    return when {
        m < 1 -> "刚刚"
        m < 60 -> "$m 分钟前"
        m < 1440 -> "${m / 60} 小时前"
        m < 43200 -> "${m / 1440} 天前"
        else -> java.text.SimpleDateFormat("M月d日", java.util.Locale.CHINA).format(java.util.Date(ts))
    }
}
