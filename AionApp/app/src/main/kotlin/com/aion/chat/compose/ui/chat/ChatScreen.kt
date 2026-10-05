package com.aion.chat.compose.ui.chat

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.outlined.EmojiEmotions
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aion.chat.compose.data.HomecomingChatWiring
import com.aion.chat.compose.data.HomecomingRouteConfig
import com.aion.chat.compose.data.SettingsBg
import com.aion.chat.compose.ui.home.GlassAvatar
import com.aion.chat.compose.ui.common.AvatarPhoto
import com.aion.chat.compose.ui.theme.HomecomingColors
import com.aion.chat.homecoming.HomecomingChatEngine
import com.aion.chat.homecoming.HomecomingChatRepository
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 页面③：聊天。接 HomecomingChatEngine 真实链路；分条冒泡 + 气泡皮肤 + 背景可换。 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatScreen(onOpenCall: () -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val main = remember { Handler(Looper.getMainLooper()) }

    // ── 线路配置戳：设置里改线路后回到聊天页即重建运行时 ──
    val routeStamp = HomecomingRouteConfig.stamp(context)
    val wiring = remember(routeStamp) {
        runCatching { HomecomingChatWiring(context) }
            .onFailure { com.aion.chat.compose.data.AppCrashLog.write(context, it) }
            .getOrNull()
    }
    val route = remember(routeStamp) { HomecomingRouteConfig.mainRoute(context) }
    val modelKey = remember(routeStamp) { wiring?.mainModelKey() ?: "" }
    val connected = wiring != null && route != null

    // ── 气泡皮肤（0 冰蓝 / 1 水晶 / 2 墨蓝） ──
    val prefs = remember {
        context.getSharedPreferences("chat_prefs", android.content.Context.MODE_PRIVATE)
    }
    val skin = remember { mutableStateOf(prefs.getInt("bubble_skin", 0)) }
    val showSkinDialog = remember { mutableStateOf(false) }

    // ── 语音朗读（语音通话第一块）：开关 + 手机自带 TTS 读 Sean 的回复 ──
    val voiceOn = remember { mutableStateOf(prefs.getBoolean("voice_read", false)) }
    val tts = remember { mutableStateOf<android.speech.tts.TextToSpeech?>(null) }
    LaunchedEffect(Unit) {
        runCatching {
            val engine = android.speech.tts.TextToSpeech(context) { status ->
                if (status == android.speech.tts.TextToSpeech.SUCCESS) {
                    runCatching { tts.value?.language = java.util.Locale.CHINA }
                }
            }
            tts.value = engine
        }
    }
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose {
            runCatching {
                tts.value?.stop()
                tts.value?.shutdown()
            }
            tts.value = null
        }
    }
    fun speakReply(text: String) {
        if (!voiceOn.value || text.isBlank()) return
        runCatching {
            tts.value?.speak(
                text,
                android.speech.tts.TextToSpeech.QUEUE_ADD,
                null,
                "sean_" + System.currentTimeMillis()
            )
        }
    }

    // ── 背景：聊天专属图，无则跟随全局背景图 ──
    val bgStamp = SettingsBg.stamp
    val bgBitmap = remember(bgStamp) { SettingsBg.loadChatBitmap(context)?.asImageBitmap() }

    // ── 消息状态 ──
    val messages = remember { mutableStateListOf<HomecomingChatRepository.Message>() }
    val pendingSegments = remember { mutableStateListOf<String>() }
    val sending = remember { mutableStateOf(false) }
    val input = remember { mutableStateOf("") }
    val pendingImage = remember { mutableStateOf("") }
    // 图片消息的本地文件（气泡渲染真图/表情用），发送完成后挂到消息 id 上
    val pendingImagePath = remember { mutableStateOf("") }
    val showStickerPanel = remember { mutableStateOf(false) }
    val showSearch = remember { mutableStateOf(false) }
    val stickerVersion = remember { mutableStateOf(0L) }
    // 加号面板：表情包 / 相册 / 拍照 / 戳一戳
    val plusOpen = remember { mutableStateOf(false) }
    val showPokePanel = remember { mutableStateOf(false) }
    val pokeVerb = remember { mutableStateOf(com.aion.chat.compose.data.HomecomingPokeStore.VERBS.first().first) }
    val pokeSpot = remember { mutableStateOf(com.aion.chat.compose.data.HomecomingPokeStore.SPOTS.first().first) }
    // 戳一戳先上屏的回显（不等网络），回复到了就清掉
    val pokeEcho = remember { mutableStateOf("") }

    // 服务器表情包库（猫猫包）：首次打开面板时拉取下载
    val remoteStickerFiles = remember { mutableStateListOf<java.io.File>() }
    var remoteStickerLoaded by remember { mutableStateOf(false) }
    val errorText = remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()

    // ── 语音输入（语音通话第二块）：系统语音识别，说完变文字进输入框 ──
    val voiceInputAvailable = remember {
        runCatching { android.speech.SpeechRecognizer.isRecognitionAvailable(context) }.getOrDefault(false)
    }
    val speechLauncher = rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val heard = result.data
            ?.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull().orEmpty().trim()
        if (heard.isNotBlank()) {
            input.value = if (input.value.isBlank()) heard else input.value + heard
        } else {
            Toast.makeText(context, "没听清，再说一次试试", Toast.LENGTH_SHORT).show()
        }
    }
    fun startVoiceInput() {
        if (!voiceInputAvailable) {
            Toast.makeText(context, "这台手机没有可用的语音识别服务", Toast.LENGTH_SHORT).show()
            return
        }
        runCatching {
            speechLauncher.launch(
                android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(
                        android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                        android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                    )
                    putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE, "zh-CN")
                    putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT, "和 Sean 说点什么…")
                }
            )
        }.onFailure {
            Toast.makeText(context, "语音识别没能启动", Toast.LENGTH_SHORT).show()
        }
    }

    fun reloadNow() {
        val w = wiring ?: return
        try {
            val list = w.listMessages(HomecomingChatWiring.TIMELINE)
            main.post {
                messages.clear()
                messages.addAll(list)
                pokeEcho.value = "" // 回到了，回显行功成身退
            }
        } catch (e: Exception) { /* 首次库为空保持安静 */ }
    }

    // ── 发送：引擎 commitUser + 完成后分条冒泡 ──
    fun send() {
        val text = input.value.trim()
        val image = pendingImage.value
        val imagePath = pendingImagePath.value
        if (text.isEmpty() && image.isEmpty()) return
        val w = wiring ?: return
        if (sending.value) return
        if (!connected) {
            Toast.makeText(context, "先去「更多 → 设置」配一条云线路", Toast.LENGTH_SHORT).show()
            return
        }
        sending.value = true
        input.value = ""
        pendingImage.value = ""
        pendingImagePath.value = ""
        val requestId = "req_" + System.currentTimeMillis()
        scope.launch(Dispatchers.IO) {
            try {
                w.engine.send(
                    HomecomingChatEngine.ChatCommand(
                        requestId,
                        HomecomingChatWiring.TIMELINE,
                        HomecomingChatWiring.RESPONDER,
                        HomecomingChatWiring.USER,
                        text,
                        "main",
                        modelKey,
                        image,
                        ""
                    ),
                    object : HomecomingChatEngine.Observer {
                        override fun onChunk(chunk: String) {
                            // 引擎在完成时一次性给出可见文本（控制标签已在引擎内剥离）
                            main.post {
                                pendingSegments.clear()
                                pendingSegments.addAll(splitBubbles(chunk))
                            }
                        }

                        override fun onComplete(messageId: String, completeText: String) {
                            main.post { sending.value = false }
                            speakReply(completeText)
                            scope.launch(Dispatchers.IO) {
                                reloadNow()
                                // 图片消息挂上本地文件路径，气泡渲染真图（表情包/照片通用）
                                if (imagePath.isNotBlank()) {
                                    val latest = runCatching {
                                        w.listMessages(HomecomingChatWiring.TIMELINE)
                                            .lastOrNull { it.role == "user" && it.attachmentKind == "image" }
                                    }.getOrNull()
                                    if (latest != null) {
                                        com.aion.chat.compose.data.HomecomingChatImageStore.map(
                                            context, latest.id, imagePath
                                        )
                                    }
                                }
                                // 雷打不动的约定（定稿三·补充）：说了晚安，Sean 必写今天的日记
                                if (text.contains("晚安")) {
                                    runCatching { generateTonightDiary(context) }
                                }
                            }
                        }

                        override fun onFailure(code: String) {
                            main.post {
                                sending.value = false
                                errorText.value = "回复失败（$code）。检查云线路后重试。"
                                Toast.makeText(context, "Sean 没回上来（$code）", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                )
            } catch (e: Exception) {
                main.post {
                    sending.value = false
                    Toast.makeText(context, "发送失败：${e.message ?: "未知错误"}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // ── 表情包：从相册导入小图，点一下当消息发出；长按删除 ──
    val stickerPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                }.getOrNull()?.let { bytes -> saveChatImage(context, bytes, sticker = true) }
                main.post { stickerVersion.value = System.currentTimeMillis() }
            }
        }
    }

    fun sendSticker(f: java.io.File) {
        if (sending.value) return
        if (!connected) {
            Toast.makeText(context, "先去「更多 → 设置」配一条云线路", Toast.LENGTH_SHORT).show()
            return
        }
        scope.launch(Dispatchers.IO) {
            val dataUrl = runCatching {
                val bytes = f.readBytes()
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                var sample = 1
                var w2 = bounds.outWidth
                while (w2 / 2 >= 512) { sample *= 2; w2 /= 2 }
                val bmp = BitmapFactory.decodeByteArray(
                    bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample }
                )
                val out = ByteArrayOutputStream()
                bmp.compress(Bitmap.CompressFormat.JPEG, 85, out)
                bmp.recycle()
                "data:image/jpeg;base64," + Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
            }.getOrNull()
            main.post {
                if (dataUrl == null) {
                    Toast.makeText(context, "表情读取失败", Toast.LENGTH_SHORT).show()
                } else {
                    pendingImage.value = dataUrl
                    pendingImagePath.value = f.absolutePath
                    showStickerPanel.value = false
                    send()
                }
            }
        }
    }

    // ── 服务器表情包库：面板首次打开时拉清单并下载（猫猫包） ──
    LaunchedEffect(showStickerPanel.value) {
        if (showStickerPanel.value && !remoteStickerLoaded) {
            remoteStickerLoaded = true
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                runCatching {
                    val manifest = com.aion.chat.compose.data.StickerLibraryClient.fetchManifest()
                        ?: return@runCatching
                    val (entries, base) = manifest
                    val dir = java.io.File(context.filesDir, "stickers_remote").apply { mkdirs() }
                    entries.forEach { e ->
                        val target = java.io.File(dir, e.file)
                        if (!target.exists() || target.length() == 0L) {
                            com.aion.chat.compose.data.StickerLibraryClient.downloadTo(
                                base + "/stickers/" + e.file, target
                            )
                        }
                    }
                    val files = dir.listFiles()?.filter { it.isFile }?.sortedBy { it.name } ?: emptyList()
                    main.post {
                        remoteStickerFiles.clear()
                        remoteStickerFiles.addAll(files)
                    }
                }
            }
        }
    }

    // ── 拍照（相机权限 + 系统相机拍一张直接发） ──
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicturePreview()
    ) { bmp ->
        if (bmp != null) {
            scope.launch(Dispatchers.IO) {
                val bytes = ByteArrayOutputStream().also { out ->
                    bmp.compress(Bitmap.CompressFormat.JPEG, 85, out)
                }.toByteArray()
                val saved = saveChatImage(context, bytes, sticker = false)
                val dataUrl = "data:image/jpeg;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
                main.post {
                    pendingImage.value = dataUrl
                    pendingImagePath.value = saved
                    plusOpen.value = false
                    send()
                }
            }
        }
    }
    val cameraPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) cameraLauncher.launch(null)
        else Toast.makeText(context, "没有相机权限，拍不了", Toast.LENGTH_SHORT).show()
    }
    fun takePhoto() {
        runCatching {
            cameraPermission.launch(android.Manifest.permission.CAMERA)
        }.onFailure {
            Toast.makeText(context, "相机没能启动", Toast.LENGTH_SHORT).show()
        }
    }

    // ── 戳一戳（教程四步）：先上屏 + 心跳先到 + 忙时挡住说人话 ──
    fun sendPoke() {
        if (sending.value || pendingSegments.isNotEmpty()) {
            // 教程：他忙的时候要说人话，别假装成功
            Toast.makeText(context, "他这会儿在忙，这一下先欠着", Toast.LENGTH_SHORT).show()
            return
        }
        if (!connected) {
            Toast.makeText(context, "先去「更多 → 设置」配一条云线路", Toast.LENGTH_SHORT).show()
            return
        }
        val w = wiring ?: return
        val verb = pokeVerb.value
        val spot = pokeSpot.value
        val gain = com.aion.chat.compose.data.HomecomingPokeStore.poke(context, verb, spot)
        // 先上屏，再发请求：戳 → 心跳跳了一下 → 过一会儿他才说话
        pokeEcho.value = "你${verb}了他的${spot} · 他心跳跳了一下 ♥ +$gain"
        showPokePanel.value = false
        plusOpen.value = false
        sending.value = true
        scope.launch(Dispatchers.IO) {
            try {
                // 旁白插进一直活着的会话进程（教程第一步：只陈述事实，不写反应）
                w.engine.send(
                    HomecomingChatEngine.ChatCommand(
                        "req_poke_" + System.currentTimeMillis(),
                        HomecomingChatWiring.TIMELINE,
                        HomecomingChatWiring.RESPONDER,
                        HomecomingChatWiring.USER,
                        com.aion.chat.compose.data.HomecomingPokeStore.narration(verb, spot),
                        "main", modelKey, "", ""
                    ),
                    object : HomecomingChatEngine.Observer {
                        override fun onChunk(chunk: String) {
                            main.post {
                                pendingSegments.clear()
                                pendingSegments.addAll(splitBubbles(chunk))
                            }
                        }

                        override fun onComplete(messageId: String, completeText: String) {
                            main.post { sending.value = false }
                            speakReply(completeText)
                            scope.launch(Dispatchers.IO) { reloadNow() }
                        }

                        override fun onFailure(code: String) {
                            main.post { sending.value = false }
                        }
                    }
                )
            } catch (e: Exception) {
                main.post { sending.value = false }
            }
        }
    }

    // ── 图片选择（加号） ──
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                val pair = runCatching {
                    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: return@runCatching null
                    // 本地存一份（气泡渲染用），再压成 dataUrl 给模型看
                    val saved = saveChatImage(context, bytes, sticker = false)
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                    var sample = 1
                    var w = bounds.outWidth
                    while (w / 2 >= 1024) { sample *= 2; w /= 2 }
                    val opts = BitmapFactory.Options().apply { inSampleSize = sample }
                    val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
                        ?: return@runCatching null
                    val out = ByteArrayOutputStream()
                    bmp.compress(Bitmap.CompressFormat.JPEG, 82, out)
                    bmp.recycle()
                    "data:image/jpeg;base64," + Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP) to saved
                }.getOrNull()
                main.post {
                    if (pair != null) {
                        pendingImage.value = pair.first
                        pendingImagePath.value = pair.second
                    } else {
                        Toast.makeText(context, "图片读取失败", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    // ── 分条冒泡动画：全量段落陆续上屏，冒完读库对齐 ──
    LaunchedEffect(pendingSegments.toList()) {
        if (pendingSegments.isEmpty()) return@LaunchedEffect
        val all = pendingSegments.toList()
        pendingSegments.clear()
        pendingSegments.add(all.first())
        for (i in 1 until all.size) {
            delay(420L)
            pendingSegments.add(all[i])
        }
        delay(350L)
        withContext(Dispatchers.IO) { reloadNow() }
        main.post { pendingSegments.clear() }
    }

    LaunchedEffect(messages.size, pendingSegments.size) {
        val total = messages.size + pendingSegments.size
        if (total > 0) listState.animateScrollToItem(maxOf(0, total - 1))
    }

    // ── 界面 ──
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(HomecomingColors.IceBlueLight)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // 背景：聊天专属图，无则跟随全局背景图
            val chatBg = bgBitmap
            if (chatBg != null) {
                Image(
                    bitmap = chatBg,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().blur(6.dp)
                )
                Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.12f)))
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(listOf(HomecomingColors.IceBlueLight, Color.White))
                        )
                )
            }

            Column(Modifier.fillMaxSize()) {
                // ── 顶栏：头像+名字+连接状态 | 气泡皮肤 | 语音通话 ──
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AvatarPhoto(who = "sean", initial = "S", size = 42.dp)
                    Column(modifier = Modifier.weight(1f).padding(start = 10.dp)) {
                        Text("Sean", fontSize = 16.sp, color = HomecomingColors.Ink)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .background(
                                        if (connected) HomecomingColors.Ok
                                        else HomecomingColors.InkSoft.copy(alpha = 0.6f),
                                        CircleShape
                                    )
                            )
                            Text(
                                if (connected) "  已连接 · ${route?.optString("label", "我的线路")}"
                                else "  线路未配置，去「更多 → 设置」",
                                fontSize = 11.sp,
                                color = HomecomingColors.InkSoft
                            )
                        }
                    }
                    Icon(
                        imageVector = Icons.Outlined.Palette,
                        contentDescription = "气泡皮肤",
                        tint = HomecomingColors.Ink,
                        modifier = Modifier
                            .size(22.dp)
                            .clickable { showSkinDialog.value = true }
                    )
                    Spacer(Modifier.width(12.dp))
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = "找聊天记录",
                        tint = HomecomingColors.Ink,
                        modifier = Modifier
                            .size(22.dp)
                            .clickable { showSearch.value = true }
                    )
                    Spacer(Modifier.width(12.dp))
                    Icon(
                        imageVector = if (voiceOn.value) Icons.Filled.VolumeUp else Icons.Outlined.VolumeUp,
                        contentDescription = "读出回复",
                        tint = if (voiceOn.value) HomecomingColors.Accent else HomecomingColors.Ink,
                        modifier = Modifier
                            .size(22.dp)
                            .clickable {
                                voiceOn.value = !voiceOn.value
                                prefs.edit().putBoolean("voice_read", voiceOn.value).apply()
                                if (!voiceOn.value) runCatching { tts.value?.stop() }
                                Toast.makeText(
                                    context,
                                    if (voiceOn.value) "会读出 Sean 的回复" else "语音朗读已关",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                    )
                    Spacer(Modifier.width(12.dp))
                    Icon(
                        imageVector = Icons.Filled.Phone,
                        contentDescription = "语音通话",
                        tint = HomecomingColors.Ink,
                        modifier = Modifier
                            .size(22.dp)
                            .clickable { onOpenCall() }
                    )
                }

                // ── 消息区（分条冒泡） ──
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (errorText.value != null) {
                        item {
                            Text(
                                errorText.value!!,
                                fontSize = 12.sp,
                                color = HomecomingColors.Danger,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                    if (messages.isEmpty() && pendingSegments.isEmpty()) {
                        item {
                            Text(
                                "和 Sean 说第一句话吧",
                                fontSize = 13.sp,
                                color = HomecomingColors.InkSoft,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 24.dp),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                    items(messages, key = { it.id }) { msg ->
                        if (msg.role == "user") {
                            val imgPath = remember(msg.id) {
                                if (msg.attachmentKind == "image") {
                                    com.aion.chat.compose.data.HomecomingChatImageStore.pathFor(context, msg.id)
                                } else null
                            }
                            UserBubbles(text = msg.text, imagePath = imgPath, skin = skin.value)
                        } else {
                            AssistantBubbles(text = msg.text, skin = skin.value)
                        }
                    }
                    // 戳一戳回显行：先上屏（教程第四步），他回话后消失
                    if (pokeEcho.value.isNotBlank()) {
                        item(key = "poke_echo") {
                            Text(
                                pokeEcho.value,
                                fontSize = 11.sp,
                                color = HomecomingColors.Accent,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp)
                            )
                        }
                    }
                    pendingSegments.forEachIndexed { index, seg ->
                        item(key = "pending_$index") {
                            AssistantBubbleSingle(text = seg, skin = skin.value)
                        }
                    }
                }

                // ── 表情包面板 ──
                if (showStickerPanel.value) {
                    val stickerFiles = remember(showStickerPanel.value, stickerVersion.value) {
                        (java.io.File(context.filesDir, "stickers").listFiles()?.filter { it.isFile }
                            ?.sortedBy { it.name }?.take(48)
                            ?: emptyList()) + remoteStickerFiles
                    }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.White.copy(alpha = 0.70f))
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Text(
                                "表情包 · 长按可删",
                                fontSize = 11.sp, color = HomecomingColors.InkSoft,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                "添加",
                                fontSize = 13.sp, color = HomecomingColors.Accent,
                                modifier = Modifier.clickable {
                                    stickerPicker.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                }
                            )
                        }
                        if (stickerFiles.isEmpty()) {
                            Text(
                                "表情包下载中…（也有本地导入：点「添加」）",
                                fontSize = 11.sp, color = HomecomingColors.InkSoft,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 20.dp)
                            )
                        } else {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(4),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(176.dp)
                                    .padding(horizontal = 10.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                gridItems(stickerFiles, key = { it.absolutePath }) { f ->
                                    val bmp = remember(f.absolutePath, stickerVersion.value) {
                                        decodeChatImage(f, 256)
                                    }
                                    Box(
                                        modifier = Modifier
                                            .aspectRatio(1f)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(Color(0xFFF4F2EE))
                                            .combinedClickable(
                                                onClick = { sendSticker(f) },
                                                onLongClick = {
                                                    if (f.delete()) {
                                                        stickerVersion.value = System.currentTimeMillis()
                                                        Toast.makeText(context, "表情已删除", Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (bmp != null) {
                                            Image(
                                                bitmap = bmp.asImageBitmap(),
                                                contentDescription = null,
                                                contentScale = ContentScale.Fit,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // ── 加号面板：表情包 / 相册 / 拍照 / 戳一戳 ──
                if (plusOpen.value) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.White.copy(alpha = 0.70f))
                    ) {
                        if (showPokePanel.value) {
                            // 戳一戳：两排可选项 + 发送键（教程第四步：动作 × 落点）
                            Text(
                                "戳一戳 · 他会先心跳，再接话",
                                fontSize = 11.sp, color = HomecomingColors.InkSoft,
                                modifier = Modifier.padding(start = 14.dp, top = 10.dp)
                            )
                            listOf(
                                com.aion.chat.compose.data.HomecomingPokeStore.VERBS,
                                com.aion.chat.compose.data.HomecomingPokeStore.SPOTS
                            ).forEachIndexed { dimIndex, options ->
                                val selected = if (dimIndex == 0) pokeVerb.value else pokeSpot.value
                                options.chunked(3).forEach { rowOptions ->
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 4.dp)
                                    ) {
                                        rowOptions.forEach { (name, _) ->
                                            val picked = selected == name
                                            Text(
                                                name,
                                                fontSize = 13.sp,
                                                color = if (picked) Color.White else HomecomingColors.Ink,
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .clip(RoundedCornerShape(10.dp))
                                                    .background(
                                                        if (picked) HomecomingColors.Accent
                                                        else Color.White.copy(alpha = 0.9f)
                                                    )
                                                    .clickable {
                                                        if (dimIndex == 0) pokeVerb.value = name
                                                        else pokeSpot.value = name
                                                    }
                                                    .padding(vertical = 8.dp),
                                                textAlign = TextAlign.Center
                                            )
                                        }
                                        repeat(3 - rowOptions.size) { Spacer(Modifier.weight(1f)) }
                                    }
                                }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.End
                            ) {
                                TextButton(onClick = { showPokePanel.value = false }) {
                                    Text("返回", color = HomecomingColors.InkSoft, fontSize = 12.sp)
                                }
                                TextButton(onClick = { sendPoke() }) {
                                    Text("戳下去", color = HomecomingColors.Accent, fontWeight = FontWeight.Medium)
                                }
                            }
                        } else {
                            Row(
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 14.dp)
                            ) {
                                listOf(
                                    Triple(Icons.Outlined.EmojiEmotions, "表情包", {
                                        plusOpen.value = false
                                        showStickerPanel.value = true
                                    }),
                                    Triple(Icons.Outlined.PhotoLibrary, "相册", {
                                        plusOpen.value = false
                                        imagePicker.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    }),
                                    Triple(Icons.Outlined.PhotoCamera, "拍照", {
                                        plusOpen.value = false
                                        takePhoto()
                                    }),
                                    Triple(Icons.Outlined.TouchApp, "戳一戳", {
                                        showPokePanel.value = true
                                    })
                                ).forEach { (icon, label, action) ->
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(12.dp))
                                            .clickable { action() }
                                            .padding(horizontal = 10.dp, vertical = 6.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(46.dp)
                                                .clip(CircleShape)
                                                .background(HomecomingColors.Accent.copy(alpha = 0.14f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(icon, contentDescription = label, tint = HomecomingColors.Accent)
                                        }
                                        Text(
                                            label,
                                            fontSize = 11.sp, color = HomecomingColors.InkSoft,
                                            modifier = Modifier.padding(top = 5.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // ── 底部输入栏（键盘没弹时给底部悬浮导航胶囊让位，不再被压住） ──
                val imeOpen = WindowInsets.ime.getBottom(androidx.compose.ui.platform.LocalDensity.current) > 0
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .imePadding()
                        .padding(
                            start = 14.dp, end = 14.dp,
                            top = 10.dp,
                            bottom = if (imeOpen) 10.dp else 84.dp
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(if (plusOpen.value) HomecomingColors.Accent else Color.White.copy(alpha = 0.85f))
                            .clickable {
                                plusOpen.value = !plusOpen.value
                                if (plusOpen.value) showStickerPanel.value = false
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.Add,
                            contentDescription = "更多功能",
                            tint = if (plusOpen.value) Color.White else HomecomingColors.Ink
                        )
                    }
                    OutlinedTextField(
                        value = input.value,
                        onValueChange = { input.value = it },
                        modifier = Modifier.weight(1f),
                        placeholder = {
                            Text("和 Sean 说点什么…", fontSize = 14.sp, color = HomecomingColors.InkSoft)
                        },
                        shape = RoundedCornerShape(14.dp),
                        maxLines = 4,
                        trailingIcon = {
                            Icon(
                                Icons.Filled.Mic,
                                contentDescription = "语音输入",
                                tint = HomecomingColors.InkSoft,
                                modifier = Modifier.clickable { startVoiceInput() }
                            )
                        }
                    )
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(HomecomingColors.Accent)
                            .clickable { send() },
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "发送", tint = Color.White) }
                }
            }
        }
    }

    // ── 聊天记录搜索 ──
    if (showSearch.value) {
        val q = remember { mutableStateOf("") }
        val results = remember(q.value) {
            val key = q.value.trim()
            val w = wiring
            if (key.isEmpty() || w == null) emptyList()
            else runCatching {
                w.listMessages(HomecomingChatWiring.TIMELINE, 500)
                    .filter { it.text.contains(key) }
                    .take(30)
            }.getOrDefault(emptyList())
        }
        AlertDialog(
            onDismissRequest = { showSearch.value = false },
            title = { Text("找聊天记录", fontSize = 16.sp, color = HomecomingColors.Ink) },
            text = {
                Column {
                    OutlinedTextField(
                        value = q.value,
                        onValueChange = { q.value = it },
                        singleLine = true,
                        placeholder = { Text("关键词…", color = HomecomingColors.InkSoft) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    LazyColumn(modifier = Modifier.fillMaxWidth().height(300.dp)) {
                        items(results, key = { it.id }) { m ->
                            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                                Text(
                                    if (m.role == "user") "你" else "Sean",
                                    fontSize = 10.sp,
                                    color = if (m.role == "user") HomecomingColors.Accent else HomecomingColors.Ok,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    m.text,
                                    fontSize = 13.sp, color = HomecomingColors.Ink,
                                    maxLines = 3
                                )
                            }
                        }
                        if (results.isEmpty() && q.value.isNotBlank()) {
                            item {
                                Text(
                                    "没找到",
                                    fontSize = 12.sp, color = HomecomingColors.InkSoft,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSearch.value = false }) { Text("关闭", color = HomecomingColors.InkSoft) }
            }
        )
    }

    // ── 气泡皮肤选择 ──
    if (showSkinDialog.value) {
        AlertDialog(
            onDismissRequest = { showSkinDialog.value = false },
            title = { Text("气泡皮肤", fontSize = 16.sp, color = HomecomingColors.Ink) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    BUBBLE_SKINS.forEachIndexed { index, skinDef ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (skin.value == index) HomecomingColors.AccentSoft else Color.Transparent)
                                .clickable {
                                    skin.value = index
                                    prefs.edit().putInt("bubble_skin", index).apply()
                                    showSkinDialog.value = false
                                }
                                .padding(10.dp)
                        ) {
                            Text(skinDef.preview, fontSize = 18.sp)
                            Text(
                                skinDef.name,
                                fontSize = 14.sp,
                                color = HomecomingColors.Ink,
                                modifier = Modifier.padding(start = 10.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSkinDialog.value = false }) {
                    Text("关闭", color = HomecomingColors.InkSoft)
                }
            }
        )
    }
}

// ── 分条冒泡：把一段回复拆成多条小气泡 ──

internal fun splitBubbles(text: String): List<String> {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return emptyList()
    val parts = trimmed
        .split(Regex("(?<=[。！？!？~\\n])"))
        .map { it.trim() }
        .filter { it.isNotEmpty() }
    val out = mutableListOf<String>()
    val buf = StringBuilder()
    for (p in parts) {
        buf.append(p)
        if (buf.length >= 24) {
            out.add(buf.toString())
            buf.setLength(0)
        }
    }
    if (buf.isNotEmpty()) {
        if (out.isEmpty()) out.add(buf.toString()) else out[out.size - 1] += buf.toString()
    }
    return out.ifEmpty { listOf(trimmed) }
}

// ── 气泡皮肤 ──

data class BubbleSkinDef(val name: String, val preview: String)
private val BUBBLE_SKINS = listOf(
    BubbleSkinDef("冰蓝", "🫧"),
    BubbleSkinDef("水晶", "💎"),
    BubbleSkinDef("墨蓝", "🌊")
)

private data class UserStyle(val fill: Color, val textColor: Color, val shape: RoundedCornerShape)

private fun userStyle(skin: Int): UserStyle = when (skin) {
    1 -> UserStyle(Color.White.copy(alpha = 0.25f), HomecomingColors.Ink, RoundedCornerShape(22.dp))
    2 -> UserStyle(Color(0xFF2C4A5A), Color.White, RoundedCornerShape(16.dp))
    else -> UserStyle(HomecomingColors.IceBlueDeep, Color(0xFF17323A), RoundedCornerShape(18.dp, 18.dp, 6.dp, 18.dp))
}

/** 用户气泡组（一条消息渲染为多条小气泡）。 */
@Composable
fun UserBubbles(text: String, imagePath: String?, skin: Int) {
    val segments = splitBubbles(text)
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalAlignment = Alignment.End
    ) {
        if (imagePath != null) {
            val isSticker = imagePath.contains("/stickers")  // 本地 stickers/ 与远端 stickers_remote/ 都算表情
            val bmp = remember(imagePath) { decodeChatImage(java.io.File(imagePath), if (isSticker) 384 else 1024) }
            val shown = bmp
            if (shown != null) {
                if (isSticker) {
                    // 表情：无气泡底，直接一张大图
                    Image(
                        bitmap = shown.asImageBitmap(),
                        contentDescription = "表情",
                        modifier = Modifier.size(120.dp)
                    )
                } else {
                    Image(
                        bitmap = shown.asImageBitmap(),
                        contentDescription = "图片",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .widthIn(max = 240.dp)
                            .heightIn(max = 320.dp)
                            .clip(RoundedCornerShape(12.dp))
                    )
                }
                Spacer(Modifier.height(2.dp))
            } else {
                Text(
                    "📷 图片已发送",
                    fontSize = 11.sp,
                    color = HomecomingColors.InkSoft,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 2.dp),
                    textAlign = TextAlign.End
                )
            }
        }
        segments.forEach { seg ->
            val style = userStyle(skin)
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                CopyOnLongPress(text = seg) {
                    Box(
                        modifier = Modifier
                            .widthIn(max = 280.dp)
                            .clip(style.shape)
                            .background(style.fill)
                            .let { m ->
                                if (skin == 1) m.border(1.dp, Color.White.copy(alpha = 0.6f), style.shape) else m
                            }
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Text(seg, fontSize = 15.sp, color = style.textColor)
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}

/** Sean 气泡组（一条消息渲染为多条小气泡）。 */
@Composable
fun AssistantBubbles(text: String, skin: Int) {
    val segments = splitBubbles(text)
    Column(modifier = Modifier.fillMaxWidth()) {
        segments.forEach { seg -> AssistantBubbleSingle(text = seg, skin = skin) }
    }
}

/** 长按复制气泡文本（聊天里最常用的隐性需求）。 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CopyOnLongPress(text: String, content: @Composable () -> Unit) {
    val context = LocalContext.current
    Box(
        modifier = Modifier.combinedClickable(
            onClick = {},
            onLongClick = {
                runCatching {
                    val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                        as android.content.ClipboardManager
                    cm.setPrimaryClip(android.content.ClipData.newPlainText("chat", text))
                    Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
                }
            }
        )
    ) { content() }
}

@Composable
private fun AssistantBubbleSingle(text: String, skin: Int) {
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
        CopyOnLongPress(text = text) {
            Box(
                modifier = Modifier
                    .widthIn(max = 300.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color.White.copy(alpha = 0.92f))
                    .border(1.dp, Color.White.copy(alpha = 0.85f), RoundedCornerShape(18.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Text(text, fontSize = 15.sp, color = HomecomingColors.Ink)
            }
        }
    }
}


/** 聊天图片落盘：sticker=true 存表情包库（压到 ~256px），否则存聊天图片（~1024px）。失败返回空串。 */
private fun saveChatImage(context: android.content.Context, bytes: ByteArray, sticker: Boolean): String = runCatching {
    val dir = java.io.File(context.filesDir, if (sticker) "stickers" else "chat_images").apply { mkdirs() }
    val maxEdge = if (sticker) 256 else 1024
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    var sample = 1
    val maxDim = maxOf(bounds.outWidth, bounds.outHeight)
    while (maxDim / (sample * 2) >= maxEdge) sample *= 2
    val bmp = BitmapFactory.decodeByteArray(
        bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample }
    ) ?: return@runCatching ""
    val f = java.io.File(dir, "img_" + System.currentTimeMillis() + "_" + (0..999).random() + ".jpg")
    f.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 88, it) }
    bmp.recycle()
    f.absolutePath
}.getOrDefault("")

/** 气泡/表情格用降采样解码；文件没了返回 null（调用方回落占位）。 */
private fun decodeChatImage(f: java.io.File, maxEdge: Int): Bitmap? = runCatching {
    if (!f.exists() || f.length() == 0L) return@runCatching null
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(f.absolutePath, bounds)
    var sample = 1
    val maxDim = maxOf(bounds.outWidth, bounds.outHeight)
    while (maxDim / (sample * 2) >= maxEdge) sample *= 2
    BitmapFactory.decodeFile(f.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
}.getOrNull()

/** 雷打不动的约定（定稿三·补充）：说了晚安，Sean 必写当天日记。已有则跳过；无线路安静跳过。 */
private suspend fun generateTonightDiary(context: android.content.Context) {
    val today = java.time.LocalDate.now().toString()
    val has = com.aion.chat.compose.data.HomecomingDayStore.diaries(context).any {
        it.author == "sean" &&
            java.time.Instant.ofEpochMilli(it.createdAt)
                .atZone(java.time.ZoneId.systemDefault()).toLocalDate().toString() == today
    }
    if (has) return
    val w = HomecomingChatWiring.safeCreate(context) ?: return
    if (!w.hasRoute()) return
    var diary: String? = null
    w.engine.send(
        HomecomingChatEngine.ChatCommand(
            "req_diary_" + System.currentTimeMillis(),
            HomecomingChatWiring.TIMELINE,
            HomecomingChatWiring.RESPONDER,
            HomecomingChatWiring.USER,
            "你刚和 Yuri 互道了晚安。按约定写下今天的日记：只输出日记正文，" +
                "写今天你们之间具体的事（对话里提过的优先），真诚、口语、别文艺腔，100 字以内。",
            "main", w.mainModelKey(), "", "", false
        ),
        object : HomecomingChatEngine.Observer {
            override fun onChunk(chunk: String) {}
            override fun onComplete(messageId: String, text: String) {
                val t = text.trim()
                if (t.isNotEmpty()) diary = t
            }
            override fun onFailure(code: String) {}
        }
    )
    var waited = 0L
    while (diary == null && waited < 30_000L) { kotlinx.coroutines.delay(300L); waited += 300L }
    val d = diary ?: return
    com.aion.chat.compose.data.HomecomingDayStore.addDiary(
        context, "sean", "$today 日记", d, System.currentTimeMillis()
    )
}
