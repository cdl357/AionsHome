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
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aion.chat.compose.data.AvatarStore
import com.aion.chat.compose.data.HomecomingChatWiring
import com.aion.chat.compose.data.HomecomingMomentsStore
import com.aion.chat.compose.data.HomecomingMomentsStore.Moment
import com.aion.chat.compose.ui.common.AvatarPhoto
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.graphics.Brush
import com.aion.chat.compose.data.HomecomingRouteConfig
import com.aion.chat.homecoming.HomecomingChatEngine
import kotlinx.coroutines.delay
import com.aion.chat.compose.ui.theme.HomecomingColors
import java.io.ByteArrayOutputStream
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 页面④：朋友圈。微信流式——封面 + Yuri 头像压沿 + 动态列表 + 发布。双向、配图、赞/评。 */
@Composable
fun MomentsScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val main = remember { Handler(Looper.getMainLooper()) }

    val feed = remember { mutableStateListOf<Moment>() }
    val reloadKey = remember { mutableStateOf(0) }
    val coverVersion = remember { mutableStateOf(0L) }

    val routeStamp = remember { HomecomingRouteConfig.stamp(context) }
    val wiring = remember(routeStamp) { HomecomingChatWiring.safeCreate(context) }
    val route = remember(routeStamp) { HomecomingRouteConfig.mainRoute(context) }
    val modelKey = remember(routeStamp) { wiring?.mainModelKey() ?: "" }
    val routeReady = wiring != null && route != null

    val coverBitmap = remember(coverVersion.value) {
        val f = File(context.filesDir, "moments_cover.jpg")
        runCatching { decodeSampled(f) }.getOrNull()?.asImageBitmap()
    }

    /**
     * 远端动态 + 本地赞/评/配图合并：
     * - 时间用 Supabase 的 created_at（不再全显示"刚刚"）
     * - Yuri 的赞、Sean 的惰性评论挂在本地表（key=远端 id 的 hash），刷新后仍在
     * - 本地独有动态（旧版本发的 / 云端未连上的兜底）与远端按 内容 去重后按时间排
     */
    fun reload() {
        scope.launch(Dispatchers.IO) {
            val remote = runCatching {
                com.aion.chat.compose.data.SupabaseMomentsStore.fetchMoments()
            }.getOrDefault(emptyList())
            val local = runCatching { HomecomingMomentsStore.feed(context) }.getOrDefault(emptyList())
            val merged = mutableListOf<Moment>()
            val remoteKeys = mutableSetOf<String>()
            remote.forEach { rm ->
                remoteKeys.add(rm.author + "|" + rm.content)
                val hid = rm.id.hashCode().toLong()
                val paired = local.firstOrNull { it.id == hid }
                merged.add(
                    Moment(
                        id = hid, remoteId = rm.id,
                        author = rm.author, content = rm.content,
                        attachments = listOfNotNull(rm.imageUrl) + (paired?.attachments ?: emptyList()),
                        createdAt = rm.createdAtMs,
                        likes = paired?.likes ?: emptyList(),
                        comments = paired?.comments ?: emptyList(),
                        localRowId = paired?.id
                    )
                )
            }
            val localOnly = local
                .filter { it.author + "|" + it.content !in remoteKeys }
                .map { it.copy(remoteId = null, localRowId = it.id) }
            val all = (merged + localOnly).sortedByDescending { it.createdAt }
            main.post {
                feed.clear()
                feed.addAll(all)
            }
        }
    }

    LaunchedEffect(reloadKey.value) { reload() }

    // ── 发布弹窗状态 ──
    val showCompose = remember { mutableStateOf(false) }
    val composeText = remember { mutableStateOf("") }
    val composeImages = remember { mutableStateListOf<android.net.Uri>() }
    val composePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(maxItems = 4)
    ) { uris ->
        composeImages.clear()
        composeImages.addAll(uris.take(4))
    }

    // ── 评论状态 ──
    val commentTarget = remember { mutableStateOf<Long?>(null) }
    val commentDraft = remember { mutableStateOf("") }

    fun askSeanReply(momentId: Long, momentContent: String, userLine: String) {
        val w = wiring ?: return
        if (!routeReady) return
        scope.launch(Dispatchers.IO) {
            delay(1500L)
            try {
                var reply: String? = null
                w.engine.send(
                    HomecomingChatEngine.ChatCommand(
                        "req_mm_" + System.currentTimeMillis(),
                        "moments_private", "sean", "user",
                        "Yuri 的朋友圈动态：『${momentContent.take(80)}』。" +
                            (if (userLine.isNotBlank()) "Yuri 刚刚评论说：『${userLine.take(60)}』。" else "") +
                            "以 Sean 的身份回一句评论：一句话、自然口语、不超过 30 个字。",
                        "main", modelKey, "", ""
                    ),
                    object : com.aion.chat.homecoming.HomecomingChatEngine.Observer {
                        override fun onChunk(chunk: String) {}
                        override fun onComplete(messageId: String, text: String) {
                            val r = text.trim()
                            if (r.isNotEmpty()) {
                                HomecomingMomentsStore.addComment(context, momentId, "sean", r)
                                main.post { reload() }
                            }
                        }
                        override fun onFailure(code: String) { /* 安静 */ }
                    }
                )
            } catch (e: Exception) { /* 安静 */ }
        }
    }

    // ── 配图选择（发布弹窗内用） ──
    fun launchComposeImagePicker() {
        composePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
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
                main.post { coverVersion.value = System.currentTimeMillis() }
            }
        }
    }

    // ── 白底 + 封面 + 动态列表 ──
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .verticalScroll(rememberScrollState())
    ) {
        // ── 封面头图 ──
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
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
                                listOf(Color(0xFFB8D4D8), Color(0xFFD4E8EA))
                            )
                        )
                )
            }
            // 相机图标（换封面）
            Icon(
                imageVector = Icons.Outlined.PhotoCamera,
                contentDescription = "换封面",
                tint = Color.White,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(14.dp)
                    .size(26.dp)
                    .clickable {
                        coverPicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    }
            )
            // Yuri 头像压封面下沿（微信式）
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp)
                    .offset(y = 28.dp)
            ) {
                AvatarPhoto(
                    who = "yuri", initial = "Y",
                    size = 68.dp, strokeWidth = 3.dp,
                    onClick = null
                )
            }
        }

        Spacer(Modifier.height(40.dp)) // 给压沿头像留空间

        // ── 动态列表 ──
        if (feed.isEmpty()) {
            Text(
                "还没有动态，发第一条吧",
                fontSize = 13.sp,
                color = Color(0xFF999999),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 30.dp),
                textAlign = TextAlign.Center
            )
        }
        feed.forEach { moment ->
            MomentCard(
                moment = moment,
                commentOpen = commentTarget.value == moment.id,
                onToggleLike = {
                    val liked = HomecomingMomentsStore.toggleLike(context, moment.id, "user")
                    reload()
                    if (liked) askSeanReply(moment.id, moment.content, "")
                },
                onComment = {
                    commentTarget.value =
                        if (commentTarget.value == moment.id) null else moment.id
                },
                onSendComment = { text ->
                    if (HomecomingMomentsStore.addComment(context, moment.id, "user", text)) {
                        commentTarget.value = null
                        reload()
                        askSeanReply(moment.id, moment.content, text)
                    }
                },
                onDeleteComment = { cid ->
                    HomecomingMomentsStore.deleteComment(context, cid)
                    reload()
                },
                onDeleteMoment = {
                    scope.launch(Dispatchers.IO) {
                        if (moment.remoteId != null) {
                            runCatching {
                                com.aion.chat.compose.data.SupabaseClient.delete(
                                    "moments", "id=eq." + moment.remoteId
                                )
                            }
                        }
                        HomecomingMomentsStore.deleteMoment(context, moment.localRowId ?: moment.id)
                        main.post { reload() }
                    }
                }
            )
            // 分割线
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp)
                    .height(0.5.dp)
                    .background(Color(0xFFEEEEEE))
            )
        }
        Spacer(Modifier.height(60.dp))
    }

    // ── 发布弹窗 ──
    if (showCompose.value) {
        AlertDialog(
            onDismissRequest = { showCompose.value = false },
            title = { Text("发动态", fontSize = 16.sp, color = Color(0xFF333333)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = composeText.value,
                        onValueChange = { composeText.value = it },
                        placeholder = { Text("这一刻的想法…", color = Color(0xFF999999)) },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (composeImages.isNotEmpty()) {
                        Text(
                            "已配 ${composeImages.size} 张图",
                            fontSize = 11.sp, color = Color(0xFF999999)
                        )
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = {
                            composePicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        }) { Text("配图", color = Color(0xFF576B95)) }
                        TextButton(onClick = {
                            val text = composeText.value.trim()
                            if (text.isEmpty() && composeImages.isEmpty()) {
                                return@TextButton
                            }
                            scope.launch(Dispatchers.IO) {
                                val bytesList = composeImages.mapNotNull { uri ->
                                    runCatching {
                                        context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                                    }.getOrNull()
                                }
                                // 第一张传 Storage（桶没建/没权限时返回 null，自动降级为仅本地）
                                val remoteUrl = bytesList.firstOrNull()?.let {
                                    com.aion.chat.compose.data.SupabaseStorage.uploadMomentImage(it)
                                }
                                // 本地留档：赞/评/配图都挂本地表，断网也有得看
                                val localPaths = bytesList.mapNotNull { bytes ->
                                    runCatching { HomecomingMomentsStore.saveMomentImage(context, bytes) }.getOrNull()
                                }
                                HomecomingMomentsStore.addMoment(context, "yuri", text, localPaths)
                                val posted = com.aion.chat.compose.data.SupabaseMomentsStore.postMoment(text, "yuri", remoteUrl ?: "")
                                main.post {
                                    composeText.value = ""
                                    composeImages.clear()
                                    showCompose.value = false
                                    reload()
                                    Toast.makeText(
                                        context,
                                        if (posted) "已发布" else "已存本地（云端未连上）",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        }) { Text("发布", color = Color(0xFF576B95)) }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {}
        )
    }
}

/** 微信朋友圈式单条动态：头像左 + 名字/文字/图 + 底下时间/赞/评论灰盒。 */
@Composable
fun MomentCard(
    moment: Moment,
    commentOpen: Boolean,
    onToggleLike: () -> Unit,
    onComment: () -> Unit,
    onSendComment: (String) -> Unit,
    onDeleteComment: (Long) -> Unit,
    onDeleteMoment: () -> Unit
) {
    val name = when (moment.author) { "user" -> "Yuri"; "sean" -> "Sean"; else -> moment.author }
    val avatarInitial = if (moment.author == "user") "Y" else "S"

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Row {
            // 头像（真实照片，全 App 共用；未设置时首字母占位）
            AvatarPhoto(
                who = if (moment.author == "user") "yuri" else "sean",
                initial = avatarInitial,
                size = 44.dp, strokeWidth = 1.dp
            )
            Spacer(Modifier.width(10.dp))
            // 右列
            Column(modifier = Modifier.weight(1f)) {
                Text(name, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Color(0xFF576B95))
                if (moment.content.isNotBlank()) {
                    Text(
                        moment.content, fontSize = 15.sp, color = Color(0xFF333333),
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                // 图片（本地路径或 http 地址都能显示；大图降采样，远端图落盘缓存）
                if (moment.attachments.isNotEmpty()) {
                    Column(modifier = Modifier.padding(top = 8.dp)) {
                        moment.attachments.take(3).forEach { ref ->
                            MomentImage(
                                ref = ref,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(180.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .padding(vertical = 2.dp)
                            )
                        }
                    }
                }
                // 时间 + 操作
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    Text(relTime(moment.createdAt), fontSize = 12.sp, color = Color(0xFF999999))
                    Spacer(Modifier.weight(1f))
                    if (moment.author == "user") {
                        Text(
                            "删除",
                            fontSize = 12.sp, color = Color(0xFF576B95),
                            modifier = Modifier
                                .padding(end = 12.dp)
                                .clickable { onDeleteMoment() }
                        )
                    }
                    Text("赞", fontSize = 13.sp, color = Color(0xFF576B95),
                        modifier = Modifier.clickable { onToggleLike() })
                    Spacer(Modifier.width(14.dp))
                    Text("评论", fontSize = 13.sp, color = Color(0xFF576B95),
                        modifier = Modifier.clickable { onComment() })
                }
                // 赞+评论灰盒
                if (moment.likes.isNotEmpty() || moment.comments.isNotEmpty() || commentOpen) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp)
                            .background(Color(0xFFF7F7F7))
                            .padding(8.dp)
                    ) {
                        if (moment.likes.isNotEmpty()) {
                            Text(
                                "♥ " + moment.likes.joinToString("、") { l ->
                                    when (l) { "user" -> "Yuri"; "sean" -> "Sean"; else -> l }
                                },
                                fontSize = 13.sp, color = Color(0xFF576B95)
                            )
                        }
                        moment.comments.forEach { c ->
                            Row(modifier = Modifier.padding(top = 2.dp)) {
                                Text(
                                    buildString {
                                        append(when (c.author) {
                                            "user" -> "Yuri"; "sean" -> "Sean"; else -> c.author
                                        })
                                        append("：")
                                        append(c.content)
                                    },
                                    fontSize = 14.sp, color = Color(0xFF333333),
                                    modifier = Modifier.weight(1f)
                                )
                                if (c.author == "user") {
                                    Text(
                                        "✕", fontSize = 11.sp, color = Color(0xFF999999),
                                        modifier = Modifier.clickable { onDeleteComment(c.id) }
                                    )
                                }
                            }
                        }
                        if (commentOpen) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                            ) {
                                val draft = remember(moment.id) { mutableStateOf("") }
                                OutlinedTextField(
                                    value = draft.value,
                                    onValueChange = { draft.value = it },
                                    placeholder = { Text("评论…", fontSize = 13.sp, color = Color(0xFF999999)) },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true
                                )
                                TextButton(onClick = {
                                    if (draft.value.isNotBlank()) onSendComment(draft.value.trim())
                                }) { Text("发送", color = Color(0xFF576B95)) }
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

/** 朋友圈配图：本地路径直接解码；http(s) 先落盘缓存再解码；加载中灰底占位。 */
@Composable
fun MomentImage(ref: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val bmp = remember(ref) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(ref) {
        bmp.value = withContext(Dispatchers.IO) { loadMomentBitmap(context, ref) }
    }
    val loaded = bmp.value
    if (loaded != null) {
        Image(
            bitmap = loaded.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier
        )
    } else {
        Box(modifier.background(Color(0xFFF2F2F2)))
    }
}

private fun loadMomentBitmap(context: android.content.Context, ref: String): Bitmap? = runCatching {
    if (ref.startsWith("http")) {
        val cacheDir = File(File(context.filesDir, "moments"), "cache").apply { mkdirs() }
        val cache = File(cacheDir, "%08x.jpg".format(ref.hashCode()))
        if (!cache.exists() || cache.length() == 0L) {
            val conn = java.net.URL(ref).openConnection() as java.net.HttpURLConnection
            conn.connectTimeout = 8000
            conn.readTimeout = 20000
            conn.instanceFollowRedirects = true
            if (conn.responseCode !in 200..299) return@runCatching null
            conn.inputStream.use { input -> cache.outputStream().use { input.copyTo(it) } }
        }
        decodeSampled(cache)
    } else {
        decodeSampled(File(ref))
    }
}.getOrNull()

/** 大图降采样：长边压到 ~1280px 内，避免整图解码 OOM。 */
private fun decodeSampled(f: File): Bitmap? {
    if (!f.exists() || f.length() == 0L) return null
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(f.absolutePath, bounds)
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= 1280 || bounds.outHeight / (sample * 2) >= 1280) sample *= 2
    return BitmapFactory.decodeFile(f.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
}
