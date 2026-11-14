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
import androidx.compose.ui.window.Dialog
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.outlined.EmojiEmotions
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aion.chat.compose.data.HomecomingChatWiring
import com.aion.chat.compose.data.MusicClient
import com.aion.chat.compose.data.MusicPlayer
import com.aion.chat.compose.data.HomecomingRouteConfig
import com.aion.chat.compose.data.SettingsBg
import com.aion.chat.compose.ui.home.GlassAvatar
import com.aion.chat.compose.ui.common.AvatarPhoto
import com.aion.chat.compose.ui.theme.HomecomingColors
import com.aion.chat.homecoming.HomecomingChatEngine
import com.aion.chat.homecoming.HomecomingChatRepository
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 页面③：聊天。接 HomecomingChatEngine 真实链路；分条冒泡 + 气泡皮肤 + 背景可换。 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatScreen(onOpenCall: () -> Unit = {}, onOpenSettings: () -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val main = remember { Handler(Looper.getMainLooper()) }

    // ── 线路配置戳：设置里改线路后回到聊天页即重建运行时 ──
    val routeStamp = HomecomingRouteConfig.stamp(context)
    // 加载失败时把真实原因带给界面（“未配置”三个字解决不了任何问题）
    var wiringError by remember(routeStamp) { mutableStateOf<String?>(null) }
    var wiringTrace by remember(routeStamp) { mutableStateOf<String?>(null) }
    var showWiringDiag by remember { mutableStateOf(false) }
    var wiringRetry by remember { mutableStateOf(0) }
    val wiring = remember(routeStamp, wiringRetry) {
        runCatching { HomecomingChatWiring(context) }
            .onFailure {
                com.aion.chat.compose.data.AppCrashLog.write(context, it)
                wiringError = it.javaClass.simpleName +
                    (it.message?.let { m -> ": $m" } ?: "")   // 完整原因，不再截断
                wiringTrace = it.stackTraceToString().take(4000)
            }
            .getOrNull()
    }
    // 点重试后仍失败：自动弹出诊断卡（完整堆栈/复制）——组合外写状态
    LaunchedEffect(wiring, wiringRetry) {
        if (wiring == null && wiringRetry > 0 && wiringError != null) showWiringDiag = true
    }
    val route = remember(routeStamp) { HomecomingRouteConfig.mainRoute(context) }
    val modelKey = remember(routeStamp) { wiring?.mainModelKey() ?: "" }
    // 橘瓣式提供商体系：聊天用哪个提供商/模型由 ProviderStore.active 决定
    val activeProv = remember(routeStamp, com.aion.chat.compose.data.ProviderStore.rev) {
        com.aion.chat.compose.data.ProviderStore.active(context)
    }
    // v11 P2：本地模型开关打开时无需云提供商也能聊（发消息走 LocalModelGateway）
    val localModeOn = runCatching {
        com.aion.chat.compose.data.LocalModelStore.useInChat(context) &&
                com.aion.chat.compose.data.LocalModelStore.chatModel(context)?.let {
                    com.aion.chat.compose.data.LocalModelStore.isDownloaded(context, it)
                } == true
    }.getOrDefault(false)
    val connected = wiring != null && (activeProv != null || localModeOn)

    // ── 气泡皮肤（0 冰蓝 / 1 水晶 / 2 墨蓝 / 3 仿TG） ──
    val prefs = remember {
        context.getSharedPreferences("chat_prefs", android.content.Context.MODE_PRIVATE)
    }
    // 0.3.58 起默认仿TG（Yuri 定稿）；老用户显式选过的皮肤保留
    val skin = remember { mutableStateOf(prefs.getInt("bubble_skin", 3)) }
    // 气泡不透明度（0.3~1.0，默认 1.0=现状；聊天设置抽屉里调，所有气泡统一乘算）
    val bubbleAlpha = remember { mutableStateOf(prefs.getFloat("bubble_alpha", 1f)) }
    val showSkinDialog = remember { mutableStateOf(false) }
    // 聊天设置抽屉（左缘右滑 / 左上角滑杆图标呼出）
    val showChatDrawer = remember { mutableStateOf(false) }
    val showSeanConfig = remember { mutableStateOf(false) }
    val showMcp = remember { mutableStateOf(false) }
    val showMemoryBank = remember { mutableStateOf(false) }
    val showFavorites = remember { mutableStateOf(false) }
    val showStats = remember { mutableStateOf(false) }
    // 聊天背景重载戳（抽屉里换图后立即刷新，不必切页）
    val chatBgVersion = remember { mutableStateOf(0L) }
    val chatDrawerPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            val ok = runCatching {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    SettingsBg.chatFile(context).outputStream().use { output ->
                        input.copyTo(output)
                    }
                } != null
            }.getOrDefault(false)
            if (ok) {
                SettingsBg.bump()
                chatBgVersion.value = System.currentTimeMillis()
                Toast.makeText(context, "聊天背景已换好", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "没读出这张图，换一张试试", Toast.LENGTH_SHORT).show()
            }
        }
    }

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
    val bgBitmap = remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    LaunchedEffect(SettingsBg.stamp, chatBgVersion.value) {
        bgBitmap.value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            SettingsBg.loadChatBitmap(context)?.asImageBitmap()
        }
    }

    // ── 消息状态 ──
    val messages = remember { mutableStateListOf<HomecomingChatRepository.Message>() }
    val pendingSegments = remember { mutableStateListOf<String>() }
    val sending = remember { mutableStateOf(false) }
    // ── 网关 SSE 后端（开关在设置；默认旧中转站） ──
    // F2 真迁移（v9 审计 P1-2）：实验网关 8790 从未部署，遗留开关强制关闭，
    // 老装机不再每次发送白等 10s connect 超时
    val useGateway = remember(routeStamp) {
        context.getSharedPreferences("chat_backend", android.content.Context.MODE_PRIVATE)
            .edit().putBoolean("gateway", false).apply()
        false
    }
    val thinkingLive = remember { mutableStateOf<String?>(null) }
    val toolName = remember { mutableStateOf<String?>(null) }
    val toolDone = remember { mutableStateOf<Boolean?>(null) }
    val noticeText = remember { mutableStateOf<String?>(null) }
    val emptyHint = remember { mutableStateOf(false) }
    var activeRequestId by remember { mutableStateOf("") }
    var stopping by remember { mutableStateOf(false) }
    var pendingReload by androidx.compose.runtime.mutableIntStateOf(0)

    fun stopGeneration() {
        if (!sending.value) return
        stopping = true
        if (useGateway) {
            com.aion.chat.compose.data.GatewayChat.stop(HomecomingChatWiring.TIMELINE)
        } else {
            runCatching { wiring?.engine?.stop(activeRequestId) }
        }
        // 停止/失败后对齐库内真身（乐观气泡撤回或落位）——经计数器触发 reloadNow（声明在后）
        pendingReload++
        sending.value = false
        pendingSegments.clear()
        thinkingLive.value = null; toolName.value = null; noticeText.value = null
        Toast.makeText(context, "停了", Toast.LENGTH_SHORT).show()
    }
    val input = remember { mutableStateOf("") }
    val pendingImage = remember { mutableStateOf("") }
    // 多图发送（参考原作者 image-viewer/多图形式）：一次最多 3 张
    val pendingImages = remember { mutableStateListOf<Pair<String, String>>() }  // (dataUrl, path)
    var viewerPaths by remember { mutableStateOf<List<String>>(emptyList()) }
    var viewerIndex by remember { mutableStateOf(0) }
    // 视频消息：抽帧给他看，气泡可点播
    var pendingVideo by remember { mutableStateOf<VideoDraft?>(null) }
    var sliceVideo by remember { mutableStateOf<SlicePrep?>(null) }   // 待选抽帧参数（橘子岛式切片弹窗）
    var playerVideo by remember { mutableStateOf<String?>(null) }
    // 模型选择弹层（橘瓣 ModelSelector 同款）
    var showModelSheet by remember { mutableStateOf(false) }
    // 图片消息的本地文件（气泡渲染真图/表情用），发送完成后挂到消息 id 上
    val pendingImagePath = remember { mutableStateOf("") }
    val showStickerPanel = remember { mutableStateOf(false) }
    val showSearch = remember { mutableStateOf(false) }
    val stickerVersion = remember { mutableStateOf(0L) }
    // 加号面板：表情包 / 相册 / 拍照
    val plusOpen = remember { mutableStateOf(false) }

    // AI 点歌的播放卡（本地留档，聊天末尾渲染）
    val musicCardsReload = remember { mutableStateOf(0) }
    val musicCards = remember(musicCardsReload.value) {
        mutableStateListOf<com.aion.chat.compose.data.MusicCardStore.Card>()
            .apply { addAll(com.aion.chat.compose.data.MusicCardStore.list(context)) }
    }

    // 服务器表情包库（猫猫包）：首次打开面板时拉取下载
    val remoteStickerFiles = remember { mutableStateListOf<java.io.File>() }
    var remoteStickerLoaded by remember { mutableStateOf(false) }
    val errorText = remember { mutableStateOf<String?>(null) }
    // 橘瓣式发送失败错误卡：原因 + 建议 + 重试，挂在聊天流里可关闭
    val sendError = remember { mutableStateOf<SendError?>(null) }
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
            // 即时指令：扫他的新消息里的 <countdown> 标签（去重过的不会重复入药丸）
            com.aion.chat.compose.data.CommandStore.ensureIssuerKnowledge(context)
            com.aion.chat.compose.data.StudyRoomStore.ensureIssuerKnowledge(context)
            com.aion.chat.compose.data.SeanAssetStore.ensureIssuerKnowledge(context)
            com.aion.chat.compose.data.SeanImageGenStore.ensureIssuerKnowledge(context)
            list.reversed().filter { it.role != "user" }.forEach {
                com.aion.chat.compose.data.CommandStore.scanMessage(context, it.id, it.text)
                com.aion.chat.compose.data.StudyRoomStore.scanMessage(context, it.id, it.text)
                com.aion.chat.compose.data.SeanAssetStore.scanMessage(context, it.id, it.text)
            }
            main.post {
                messages.clear()
                messages.addAll(list)
            }
        } catch (e: Exception) {
            // 读库失败被吞会让时间线看着"全空"——记入崩溃日志，别再无声
            com.aion.chat.compose.data.AppCrashLog.write(context, e)
        }
    }

    // ── 进页面/回页面就加载历史：以前只在发送成功后 reload——重进聊天页必然"全空" ──
    LaunchedEffect(wiring, routeStamp) {
        if (wiring != null) withContext(Dispatchers.IO) { reloadNow() }
    }
    // 停止/失败触发的对齐 reload（reloadNow 声明于前方可直接引用）
    LaunchedEffect(pendingReload) {
        if (pendingReload > 0 && wiring != null) withContext(Dispatchers.IO) { reloadNow() }
    }

    // ── 发送：引擎 commitUser + 完成后分条冒泡 ──
    fun isSongRequest(text: String): Boolean =
        text.contains("歌") && Regex("(放|来|点|听|播|想听)").containsMatchIn(text)

    fun cleanSongQuery(text: String): String =
        text.replace(Regex("(请|帮我|给我|我想|想要|一起|陪我|现在|立刻|马上|然后|适合|一首|首歌|个|下|吧|呗|好吗|可以吗)"), " ")
            .replace(Regex("\\s+"), " ").trim()

    fun sendViaRoute(
        w: HomecomingChatWiring,
        reqId: String,
        txt: String,
        promptText: String,
        img: String,
        imgPath: String,
        extras: List<String>,
        bubblePaths: List<String>,
        regenParent: HomecomingChatRepository.Message? = null
    ) {
        main.post {
            sending.value = true
            stopping = false
            emptyHint.value = false
            activeRequestId = reqId
        }
        // 橘瓣式：路由/模型由提供商体系决定（routes.json 里 active 提供商已镜像为 "main"）
        // v11 P2：本地模型开关打开时无需云提供商（wiring 按开关装 LocalModelGateway）
        val localMode = runCatching {
            com.aion.chat.compose.data.LocalModelStore.useInChat(context) &&
                    com.aion.chat.compose.data.LocalModelStore.chatModel(context)?.let {
                        com.aion.chat.compose.data.LocalModelStore.isDownloaded(context, it)
                    } == true
        }.getOrDefault(false)
        val act = com.aion.chat.compose.data.ProviderStore.active(context)
        if (act == null) {
            if (localMode) {
                // 本地模式：act 可空，走 LocalModelGateway（路由/模型占位，引擎侧不读）
            } else {
                main.post {
                    sending.value = false
                    sendError.value = SendError(
                        title = "还没有可用的提供商",
                        reason = "设置 → 模型提供商 里没有启用中的提供商（或没添加模型）。开着本地模型开关的话，确认 设置 → 本地模型 里已下载并加载过模型",
                        solution = "云线路：去 设置 → 模型提供商 新增并设为聊天使用；本地：去 设置 → 本地模型 下载模型"
                    )
                }
                return
            }
        }
        scope.launch(Dispatchers.IO) {
            try {
                // ── 自愈（F3 双源错位根除）：按最新提供商状态重写 routes.json，
                //    wiring 每次新建（不吃组合期旧快照），按最新 routes.json 装配 ──
                com.aion.chat.compose.data.ProviderStore.resyncRoutes(context)
                val wFresh = HomecomingChatWiring.safeCreate(context) ?: run {
                    main.post {
                        sending.value = false
                        messages.removeAll { it.id == "temp_" + reqId }
                        sendError.value = SendError(
                            title = "线路装配失败",
                            reason = "聊天运行时初始化失败（详见 设置 → 崩溃日志）",
                            solution = "重存一次提供商通常可恢复；反复出现请反馈崩溃日志"
                        )
                    }
                    return@launch
                }
                // failed 状态复位：复用 requestId 的重试不再被 "request already failed" 永久阻断
                runCatching { wFresh.chats.resetRequest(reqId) }
                // 消息树：重新生成时 prepare（上下文截断+挂父），完成/失败后 finish
                if (regenParent != null) {
                    wFresh.chats.prepareRegenerate(regenParent.id, regenParent.createdAt)
                }
                wFresh.engine.send(
                    HomecomingChatEngine.ChatCommand(
                        reqId,
                        HomecomingChatWiring.TIMELINE,
                        HomecomingChatWiring.RESPONDER,
                        HomecomingChatWiring.USER,
                        promptText,
                        act?.first?.id ?: "local",   // 本地模式占位（LocalModelGateway 不读路由）
                        act?.second?.key ?: "local", // v11 P2：本地模式无云提供商也能发
                        img,
                        "",
                        regenParent == null,   // 重新生成不落新用户消息（复用父消息）
                        extras.filter { it.isNotBlank() }   // v7：中转站路径也带多图/视频帧
                    ),
                    object : HomecomingChatEngine.Observer {
                        override fun onChunk(chunk: String) {
                            // 引擎在完成时一次性给出可见文本（控制标签已在引擎内剥离）
                            main.post {
                                pendingSegments.clear()
                                pendingSegments.addAll(splitBubbles(com.aion.chat.compose.data.SeanImageGenStore.strip(com.aion.chat.compose.data.SeanAssetStore.strip(chunk))))
                            }
                        }

                        override fun onComplete(messageId: String, completeText: String) {
                            runCatching { wFresh.chats.finishRegenerate() }
                            main.post {
                                if (stopping) { stopping = false; return@post }
                                sending.value = false
                                // 后台回话 → 横幅弹窗 + 锁屏显示
                                com.aion.chat.compose.data.SeanNotifier.maybeNotifyReply(
                                    context,
                                    com.aion.chat.compose.data.SeanAssetStore.remark(context).ifBlank { "Sean" },
                                    completeText
                                )
                                // F4：空回复不再无声无息（控制标签剥完为空/思维模型只吐思考）
                                if (completeText.isBlank()) {
                                    sendError.value = SendError(
                                        title = "他没说话（回复为空）",
                                        reason = "线路通了、模型也回了，但正文是空的——多半是思维（thinking）模型只输出了思考",
                                        solution = "在提供商的模型列表里换一个非 thinking 模型再试",
                                        retry = { sendViaRoute(w, reqId, txt, promptText, img, imgPath, extras, bubblePaths) }
                                    )
                                    return@post
                                }
                            }
                            if (stopping) return
                            speakReply(completeText)
                            scope.launch(Dispatchers.IO) {
                                reloadNow()
                                // 图片消息挂上本地文件路径，气泡渲染真图（表情包/照片/视频通用）
                                if (bubblePaths.any { it.isNotBlank() }) {
                                    val latest = runCatching {
                                        wFresh.listMessages(HomecomingChatWiring.TIMELINE)
                                            .lastOrNull { it.role == "user" && it.attachmentKind == "image" }
                                    }.getOrNull()
                                    if (latest != null) {
                                        com.aion.chat.compose.data.HomecomingChatImageStore.mapMany(
                                            context, latest.id, bubblePaths.filter { it.isNotBlank() }
                                        )
                                    }
                                }
                                // 雷打不动的约定（定稿三·补充）：说了晚安，Sean 必写今天的日记
                                if (txt.contains("晚安")) {
                                    runCatching { generateTonightDiary(context) }
                                }
                                // 音乐卡在搜歌路径已入库（这里重复 add 会出双卡）
                            }
                        }

                        override fun onFailure(code: String) {
                            runCatching { wFresh.chats.finishRegenerate() }
                            main.post {
                                if (stopping) { stopping = false; return@post }
                                sending.value = false
                                // 失败也要刷：她的消息在引擎里已落库，不上屏会像"没发出去"
                                scope.launch(Dispatchers.IO) { reloadNow() }
                                sendError.value = SendError(
                                    title = "回复失败（$code）",
                                    reason = when {
                                        code.startsWith("local_no_model") ->
                                            "本地模型没下载——去 设置 → 本地模型 下载一个，或关掉「聊天里用本地模型」开关走在线"
                                        code.startsWith("local_load_failed") ->
                                            "模型加载失败：内存吃紧或文件损坏——删掉重下，或换 0.5B 小杯"
                                        code.startsWith("local_generate_failed") ->
                                            "本地生成失败：内存吃紧或上下文超长——清一下会话再试，或换 0.5B 小杯"
                                        code.startsWith("local_") ->
                                            "本地推理异常——重试一次；反复出现把崩溃日志发我"
                                        code == "empty_model_reply:reasoning_only" ->
                                            "这个模型只输出了思考没有正文——换一个非 thinking 模型（如 claude-opus-4-6）再试"
                                        code == "empty_model_reply" ->
                                            "线路通了，但模型回了空——多半是模型名不对或余额/权限问题"
                                        code.startsWith("model_request_failed") -> {
                                            val why = code.substringAfter("model_request_failed:")
                                                .substringAfter(':', "")
                                            when {
                                                code.contains("UnknownHost") -> "域名解析失败——Base URL 拼写或网络 DNS 有问题"
                                                code.contains("ConnectException") -> "连不上端点（连接被拒 / 网络不通）"
                                                code.contains("SocketTimeout") -> "请求超时（线路太慢或不通）"
                                                code.contains("SSL") -> "TLS 握手被拦（证书 / 网络拦截）"
                                                code.contains("already running") -> "上一条还没跑完，等它结束再发"
                                                else -> "请求没发出去" + (if (why.isNotBlank()) "：${why.take(90)}" else "")
                                            }
                                        }
                                        code.contains("401") || code.contains("403") -> "Key 无效或没权限（HTTP 401/403）"
                                        code.contains("404") -> "端点 404——Base URL 或模型名不对"
                                        code.contains("429") -> "限流（HTTP 429），稍等再试"
                                        else -> "线路返回异常（$code）"
                                    },
                                    solution = "去 设置 → 云线路 检查 Base URL / API Key / 模型，保存后立即生效，不用重启",
                                    failedText = txt,
                                    retry = { sendViaRoute(w, reqId, txt, promptText, img, imgPath, extras, bubblePaths) }
                                )
                                Toast.makeText(context, "Sean 没回上来（$code）", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                )
            } catch (e: Exception) {
                main.post {
                    sending.value = false
                    scope.launch(Dispatchers.IO) { reloadNow() }   // 乐观气泡下线，回归库内真身
                    Toast.makeText(context, "发送失败：${e.message ?: "未知错误"}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun send(forcedText: String? = null, promptPrefix: String? = null, preSong: com.aion.chat.compose.data.MusicClient.Song? = null, forceRoute: Boolean = false) {
        val text = (forcedText ?: input.value).trim()
        // 视频优先：抽帧给他看，气泡播本地 mp4
        val pv = pendingVideo
        val image = pv?.frames?.firstOrNull()
            ?: pendingImage.value.ifBlank { pendingImages.firstOrNull()?.first ?: "" }
        val imagePath = pv?.path
            ?: pendingImagePath.value.ifBlank { pendingImages.firstOrNull()?.second ?: "" }
        // 多图全部带给模型（0.3.80 只接了视频帧，纯多图没接——这次真修）：
        // 视频消息 = 帧序列（首帧当主图）+ 所选图片；纯多图 = 首图当主图 + 其余
        val extraImagesForModel = buildList {
            if (pv != null) addAll(pv.frames.drop(1))
            pendingImages.drop(if (pv != null) 0 else 1).forEach { add(it.first) }
        }
        val extraImagePaths = pendingImages.drop(if (pv != null) 0 else 1).map { it.second }
        if (text.isEmpty() && image.isEmpty()) return
        val w = wiring ?: return
        if (sending.value) {
            // 生成中按发送：此刻的意图是「先停下」（文档 4.3）
            stopGeneration()
            return
        }
        if (!connected) {
            Toast.makeText(
                context,
                when {
                    wiring == null -> "引擎装配失败：" +
                        (wiringError ?: "未知原因") + "——点顶栏胶囊重试"
                    else -> "先去 设置 → 模型提供商 新增提供商（URL+Key），拉模型列表选好，设为聊天使用"
                },
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        sending.value = true
        stopping = false   // 跨轮复位：否则停止后无回调时，下一轮 onComplete 被吞、发送键两段式卡死
        input.value = ""
        pendingImage.value = ""
        pendingImagePath.value = ""
        pendingVideo = null
        val imagesForBubble = listOf(imagePath) + extraImagePaths
        pendingImages.clear()
        // ── AI 点歌（教程 §13）：说想听歌 → 搜真歌+验证 → Sean 知道结果 → 聊天里生成真实播放卡 ──
        if (promptPrefix == null && image.isEmpty() && isSongRequest(text)) {
            val rawText = text
            errorText.value = "正在找歌…"
            scope.launch(Dispatchers.IO) {
                val q = cleanSongQuery(rawText).ifBlank { rawText }
                val (songs, _) = runCatching { MusicClient.search(q, 12) }
                    .getOrDefault(emptyList<MusicClient.Song>() to null)
                val verified = songs.firstOrNull { s ->
                    runCatching { MusicClient.checkPlayable(s.id) }.getOrDefault(false)
                }
                main.post {
                    errorText.value = null
                    sending.value = false   // 递归 send 自己重新置 true
                    if (verified == null) {
                        // 失败不伪装：如实回应，请她补充歌手名
                        send(
                            forcedText = rawText,
                            promptPrefix = "[音乐服务没有找到可播放的匹配歌曲。如实告诉 Yuri 这次没找到，请她补充歌手名或换个说法。不要假装已经播放。] "
                        )
                    } else {
                        com.aion.chat.compose.data.MusicCardStore.add(context, verified)
                        musicCardsReload.value++
                        send(
                            forcedText = rawText,
                            promptPrefix = "[已为 Yuri 找到可播放的歌曲：《${verified.name}》- ${verified.artist}，播放卡已经生成在屏幕上。自然地告诉她歌已就绪，邀请她点播放卡。] ",
                            preSong = verified
                        )
                    }
                }
            }
            return
        }

        val requestId = "req_" + System.currentTimeMillis()
        activeRequestId = requestId
        emptyHint.value = false
        // 乐观气泡（总入口，网关路径/中转路径/回落全覆盖）：发送中立刻上屏。
        // key 去重——回落重试再进来不会重复（重复 key 会让 LazyColumn 崩）
        if (messages.none { it.id == "temp_" + requestId }) {
            runCatching {
                messages.add(
                    HomecomingChatRepository.Message(
                        "temp_" + requestId, requestId, "", HomecomingChatWiring.TIMELINE, "user",
                        HomecomingChatWiring.USER, text,
                        if (imagePath.isNotBlank()) "image" else "",
                        "", System.currentTimeMillis(), "sent"
                    )
                )
            }
        }

    /**
     * 中转站发送路径（send() 主体；网关失败回落也走这里）。
     * reqId 复用 → 用户消息按 (requestId,role) 幂等；img/imgPath/extras/bubblePaths 全透传 → 重发不丢图。
     */
        // ── 网关 SSE 后端（开关打开时走这里；旧中转站分支原样保留在后面） ──
        if (useGateway && !forceRoute) {
            val cmdFeedback = com.aion.chat.compose.data.CommandStore.consumeFeedback(context)
            val videoNote = pv?.let {
                "（Yuri 发来一段视频，约 ${it.durationMs / 1000} 秒。images 里是按时间顺序抽的 ${it.frames.size} 帧，请当作看过这段视频来回应她。）"
            }
            val prefix = listOfNotNull(promptPrefix, cmdFeedback?.let { "[$it]" }, videoNote)
                .joinToString("\n").ifBlank { null }
            val prompt = if (prefix != null) prefix + "\n" + text else text
            val acc = StringBuilder()
            com.aion.chat.compose.data.GatewayChat.send(
                HomecomingChatWiring.TIMELINE, requestId, text, prompt, image, extraImagesForModel, context, w.chats,
                HomecomingChatWiring.RESPONDER,
                object : com.aion.chat.compose.data.GatewayChat.Observer {
                    override fun onThinking(delta: String) {
                        main.post { thinkingLive.value = (thinkingLive.value ?: "") + delta }
                    }
                    override fun onTool(toolName0: String, done: Boolean?) {
                        main.post {
                            if (done == null) { toolName.value = toolName0; toolDone.value = null }
                            else if (toolName.value == toolName0) toolDone.value = done
                        }
                    }
                    override fun onChunk(delta: String) {
                        main.post {
                            acc.append(delta)
                            pendingSegments.clear()
                            pendingSegments.addAll(splitBubbles(com.aion.chat.compose.data.SeanImageGenStore.strip(com.aion.chat.compose.data.SeanAssetStore.strip(com.aion.chat.compose.data.StudyRoomStore.strip(com.aion.chat.compose.data.CommandStore.strip(acc.toString()))))))
                        }
                    }
                    override fun onCorrected(full: String) {
                        main.post {
                            acc.setLength(0)
                            acc.append(full)
                            pendingSegments.clear()
                            pendingSegments.addAll(splitBubbles(com.aion.chat.compose.data.SeanImageGenStore.strip(com.aion.chat.compose.data.SeanAssetStore.strip(com.aion.chat.compose.data.StudyRoomStore.strip(com.aion.chat.compose.data.CommandStore.strip(full))))))
                        }
                    }
                    override fun onNotice(t: String) { main.post { noticeText.value = t } }
                    override fun onComplete(fullText: String, thinking: String) {
                        main.post {
                            if (stopping) { stopping = false; return@post }
                            sending.value = false
                            com.aion.chat.compose.data.SeanNotifier.maybeNotifyReply(
                                context,
                                com.aion.chat.compose.data.SeanAssetStore.remark(context).ifBlank { "Sean" },
                                fullText
                            )
                            thinkingLive.value = null; toolName.value = null; noticeText.value = null
                            pendingSegments.clear()
                            if (fullText.isBlank()) {
                                // 空回复≠他没说话（文档 5.5）
                                emptyHint.value = true
                            } else {
                                speakReply(fullText)
                            }
                            scope.launch(Dispatchers.IO) {
                                reloadNow()
                                if (imagePath.isNotBlank()) {
                                    val latest = runCatching {
                                        w.listMessages(HomecomingChatWiring.TIMELINE)
                                            .lastOrNull { it.role == "user" && it.attachmentKind == "image" }
                                    }.getOrNull()
                                    if (latest != null) {
                                        com.aion.chat.compose.data.HomecomingChatImageStore.mapMany(
                                            context, latest.id, imagesForBubble.filter { it.isNotBlank() }
                                        )
                                    }
                                }
                                if (text.contains("晚安")) {
                                    runCatching { generateTonightDiary(context) }
                                }
                                // 音乐卡在搜歌路径已入库（这里重复 add 会出双卡）
                            }
                        }
                    }
                    override fun onFailure(code: String) {
                        main.post {
                            if (stopping) { stopping = false; return@post }
                            // 网关没通（8790 服务端从未部署）→ 不吞消息：回落到中转站路径重发。
                            // P1-1/P1-2 修复：复用同一 requestId（commit 按 (requestId,role) 幂等，
                            // 不出双气泡），并透传 原图/视频帧/多图/前缀——重发不再丢图丢上下文。
                            sending.value = false
                            thinkingLive.value = null; toolName.value = null; noticeText.value = null
                            val note = "网关没连上（$code），已自动改走你的线路重发"
                            errorText.value = note
                            main.postDelayed({ if (errorText.value == note) errorText.value = null }, 6000)
                            sendViaRoute(w, requestId, text, prompt, image, imagePath, extraImagesForModel, imagesForBubble)
                        }
                    }
                }
            )
            return
        }

        // ── 中转站路径（网关失败也回落到这里） ──
        val cmdFeedback = com.aion.chat.compose.data.CommandStore.consumeFeedback(context)
        val prefix = listOfNotNull(promptPrefix, cmdFeedback?.let { "[$it]" })
            .joinToString("\n").ifBlank { null }
        val prompt = if (prefix != null) prefix + "\n" + text else text
        sendViaRoute(w, requestId, text, prompt, image, imagePath, extraImagesForModel, imagesForBubble)
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

    /** 消息树：重新生成这条回复——旧回复的父用户消息作为上下文，新回复挂同一父（成为新版本） */
    fun regenerateMessage(msgId: String) {
        val w = wiring ?: return
        if (sending.value) return
        scope.launch(Dispatchers.IO) {
            try {
                val wf = HomecomingChatWiring.safeCreate(context) ?: return@launch
                val parent = runCatching { wf.chats.parentOf(msgId) }.getOrNull() ?: run {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "这条消息没有可重发的上下文", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }
                val localMode = runCatching {
                    com.aion.chat.compose.data.LocalModelStore.useInChat(context) &&
                            com.aion.chat.compose.data.LocalModelStore.chatModel(context)?.let {
                                com.aion.chat.compose.data.LocalModelStore.isDownloaded(context, it)
                            } == true
                }.getOrDefault(false)
                val act = com.aion.chat.compose.data.ProviderStore.active(context)
                if (!localMode && act == null) return@launch
                com.aion.chat.compose.data.ProviderStore.resyncRoutes(context)
                val reqId = "req_" + System.currentTimeMillis()
                withContext(Dispatchers.Main) {
                    sending.value = true
                    stopping = false
                    emptyHint.value = false
                    activeRequestId = reqId
                }
                sendViaRoute(
                    wf, reqId,
                    parent.text, parent.text,
                    "", "", emptyList(), emptyList(),
                    regenParent = parent
                )
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    sending.value = false
                    Toast.makeText(context, "重新生成失败：${e.message?.take(60)}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    /** 消息树：切换版本（±1），切完刷列表 */
    fun switchVersion(msgId: String, delta: Int) {
        val w = wiring ?: return
        scope.launch(Dispatchers.IO) {
            val ok = runCatching { w.chats.switchSibling(msgId, delta) }.getOrNull()
            if (ok != null) withContext(Dispatchers.IO) { reloadNow() }
        }
    }

    fun sendSticker(f: java.io.File) {
        if (sending.value) {
            // 生成中按发送：此刻的意图是「先停下」（文档 4.3）
            stopGeneration()
            return
        }
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

    // ── 服务器表情包库：面板首次打开时拉取（Supabase 亲亲抱抱那批放最前 + 猫猫包） ──
    val sbStickerFiles = remember { mutableStateListOf<java.io.File>() }
    LaunchedEffect(showStickerPanel.value) {
        if (showStickerPanel.value && !remoteStickerLoaded) {
            remoteStickerLoaded = true
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                runCatching {
                    // 1. Supabase stickers 表（你们自己的表情）
                    val sb = com.aion.chat.compose.data.fetchSupabaseStickers()
                    val sbDir = java.io.File(context.filesDir, "stickers_supabase").apply { mkdirs() }
                    sb?.forEach { e ->
                        val target = java.io.File(sbDir, e.name + "_" + Integer.toHexString(e.url.hashCode()) + ".jpg")
                        if (!target.exists() || target.length() == 0L) {
                            com.aion.chat.compose.data.downloadSticker(e.url, target)
                        }
                    }
                    val sbFiles = sbDir.listFiles()?.filter { it.isFile && it.length() > 0 }?.sortedBy { it.name } ?: emptyList()
                    // 2. 猫猫包
                    val manifest = com.aion.chat.compose.data.StickerLibraryClient.fetchManifest()
                    val catDir = java.io.File(context.filesDir, "stickers_remote").apply { mkdirs() }
                    if (manifest != null) {
                        val (entries, base) = manifest
                        entries.forEach { e ->
                            val target = java.io.File(catDir, e.file)
                            if (!target.exists() || target.length() == 0L) {
                                com.aion.chat.compose.data.StickerLibraryClient.downloadTo(
                                    base + "/stickers/" + e.file, target
                                )
                            }
                        }
                    }
                    val catFiles = catDir.listFiles()?.filter { it.isFile }?.sortedBy { it.name } ?: emptyList()
                    main.post {
                        sbStickerFiles.clear()
                        sbStickerFiles.addAll(sbFiles)
                        remoteStickerFiles.clear()
                        remoteStickerFiles.addAll(catFiles)
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
                    pendingImages.clear()
                    pendingImages.add(dataUrl to saved)
                    plusOpen.value = false
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

    // ── 图片选择（加号）：多选最多 3 张 ──
    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(maxItems = 3)
    ) { uris ->
        if (uris.isNotEmpty()) {
            scope.launch(Dispatchers.IO) {
                val pairs = uris.mapNotNull { uri ->
                    runCatching {
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
                }
                main.post {
                    if (pairs.isNotEmpty()) {
                        pendingImages.clear()
                        pendingImages.addAll(pairs)
                        plusOpen.value = false
                    } else {
                        Toast.makeText(context, "图片读取失败", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    // ── 视频选择：本地存 mp4，抽 6 帧按时间顺序发给他看（任何多模态模型都看得懂帧序列） ──
    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                val prep = runCatching {
                    val dir = java.io.File(context.filesDir, "chat_videos").apply { mkdirs() }
                    val base = "vid_" + System.currentTimeMillis()
                    val dst = java.io.File(dir, "$base.mp4")
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        dst.outputStream().use { input.copyTo(it) }
                    } ?: return@runCatching null
                    if (dst.length() > 200L * 1024 * 1024) { dst.delete(); return@runCatching null }
                    // 只读时长 + 封面：抽帧参数交给「视频切片」弹窗（照橘子岛）
                    val r = android.media.MediaMetadataRetriever()
                    var durMs = 0L
                    var thumbPath = ""
                    try {
                        r.setDataSource(dst.absolutePath)
                        durMs = r.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
                            ?.toLongOrNull() ?: 0L
                        r.getFrameAtTime(durMs * 1000L / 10, android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC)?.let { cover ->
                            val t = java.io.File(dir, "${base}_thumb.jpg")
                            t.outputStream().use { scaledBitmap(cover, 720).compress(Bitmap.CompressFormat.JPEG, 85, it) }
                            cover.recycle()
                            thumbPath = t.absolutePath
                        }
                    } finally {
                        r.release()
                    }
                    if (durMs <= 0L) {
                        dst.delete()
                        java.io.File(dir, "${base}_thumb.jpg").delete()
                        null
                    } else SlicePrep(dst.absolutePath, durMs, thumbPath)
                }.getOrNull()
                main.post {
                    if (prep == null) {
                        Toast.makeText(context, "这个视频读不出来（大小/编码可能不支持）", Toast.LENGTH_SHORT).show()
                    } else {
                        sliceVideo = prep
                        plusOpen.value = false
                    }
                }
            }
        }
    }

    // ── 视频切片弹窗（照橘子岛：按帧数/按间隔，确认后按参数抽帧） ──
    sliceVideo?.let { prep ->
        VideoSliceDialog(
            videoPath = prep.dstPath,
            durationMs = prep.durMs,
            onConfirm = { result ->
                scope.launch(Dispatchers.IO) {
                    val draft = runCatching {
                        val r = android.media.MediaMetadataRetriever()
                        try {
                            r.setDataSource(prep.dstPath)
                            val frames = extractFramesForModel(r, prep.durMs, result.frameCount, result.intervalMs)
                            if (frames.isEmpty()) null
                            else VideoDraft(prep.dstPath, prep.thumbPath, prep.durMs, frames)
                        } finally { r.release() }
                    }.getOrNull()
                    main.post {
                        if (draft == null) {
                            Toast.makeText(context, "抽帧失败，换个参数或换个视频试试", Toast.LENGTH_SHORT).show()
                        } else {
                            pendingVideo = draft
                            sliceVideo = null
                        }
                    }
                }
            },
            onDismiss = { sliceVideo = null }
        )
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
        // 接近底部才自动跟随：她上翻读历史时不再被每条新气泡拽回底
        if (total > 0 && !listState.canScrollForward) listState.animateScrollToItem(maxOf(0, total - 1))
    }

    // ── 界面 ──
    Column(
        modifier = Modifier
            .fillMaxSize()
            // 左缘右滑：从左边缘向右拖出聊天设置抽屉（只观察不消费，不影响列表滚动）
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val startX = down.position.x
                    var opened = false
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.changes.all { !it.pressed }) break
                        val drag = event.changes.first().position.x - startX
                        if (!opened && startX < 60f && drag > 110f) {
                            showChatDrawer.value = true
                            opened = true
                        }
                    }
                }
            }
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // 背景：聊天专属图，无则跟随全局背景图
            val chatBg = bgBitmap.value
            if (chatBg != null) {
                Image(
                    bitmap = chatBg,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().blur(6.dp)
                )
                Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.12f)))
            }
            // 无专属图时不画任何底：透出 HomecomingApp 的全局背景层

            Column(Modifier.fillMaxSize()) {
                // ── 顶栏（skin==3 仿TG：圆返回+居中胶囊+右头像；否则旧顶栏） ──
                if (skin.value == 3) {
                    // 仿TG顶栏（按 Yuri 预览定稿）：左圆钮设置 + 居中名字胶囊 + 右头像
                    val seanRemarkTg = remember(com.aion.chat.compose.data.SeanAssetStore.rev) {
                        com.aion.chat.compose.data.SeanAssetStore.remark(context).ifBlank { "Sean" }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.85f))
                                .border(0.5.dp, Color.Black.copy(alpha = 0.06f), CircleShape)
                                .clickable { showChatDrawer.value = true },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Tune,
                                contentDescription = "聊天设置",
                                tint = Color(0xFF6B5D4F),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        // 点胶囊 = 换模型（橘瓣同款）；装配失败时点 = 重试装配
                        // 不用 shadow：半透明底会透出阴影层，胶囊中间显出一条白/灰带（伪影）
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clip(RoundedCornerShape(99.dp))
                                .background(Color.White.copy(alpha = 0.85f))
                                .border(0.5.dp, Color.Black.copy(alpha = 0.06f), RoundedCornerShape(99.dp))
                                .clickable {
                                    if (wiring == null) wiringRetry++
                                    else showModelSheet = true
                                }
                                .padding(horizontal = 26.dp, vertical = 6.dp)
                        ) {
                            Text(
                                seanRemarkTg,
                                fontSize = 15.sp, color = Color(0xFF4A3F34),
                                fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp
                            )
                            val currentModelName = activeProv?.second?.model?.takeLast(26)
                                ?.substringAfterLast('/') ?: ""
                            Text(
                                when {
                                    connected -> "在线 · " + activeProv!!.first.name + " · " + currentModelName + " ▾"
                                    wiring == null -> if (wiringRetry > 0) "还没修好 · 再点一次试试"
                                                      else "线路异常 · 点重试"
                                    else -> "未配置提供商"
                                },
                                fontSize = 10.sp, maxLines = 1,
                                color = when {
                                    connected -> HomecomingColors.Ok
                                    wiring == null -> HomecomingColors.Accent
                                    else -> HomecomingColors.InkSoft
                                },
                                letterSpacing = 0.5.sp
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        AvatarPhoto(who = "sean", initial = "S", size = 40.dp)
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Tune,
                            contentDescription = "聊天设置",
                            tint = HomecomingColors.Ink,
                            modifier = Modifier
                                .size(22.dp)
                                .clickable { showChatDrawer.value = true }
                        )
                        Spacer(Modifier.width(12.dp))
                        AvatarPhoto(who = "sean", initial = "S", size = 42.dp)
                    Column(modifier = Modifier.weight(1f).padding(start = 10.dp)) {
                        // 他改的备注名（<setname>）实时生效；没改过回落 Sean
                        val seanRemark = remember(com.aion.chat.compose.data.SeanAssetStore.rev) {
                            com.aion.chat.compose.data.SeanAssetStore.remark(context).ifBlank { "Sean" }
                        }
                        Text(seanRemark, fontSize = 16.sp, color = HomecomingColors.Ink)
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
                                if (connected && useGateway) "  已连接 · 网关SSE（实验模式）"
                                else if (connected) "  已连接 · ${route?.optString("label", "我的线路")}"
                                else if (wiringError != null) "  线路加载失败 · 点这里去设置"
                                else if (route == null) "  还没有线路配置 · 点这里去设置"
                                else "  线路未生效 · 点这里去设置",
                                fontSize = 11.sp,
                                color = if (connected) HomecomingColors.InkSoft else HomecomingColors.Accent,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable(
                                        indication = null,
                                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                                    ) { if (!connected) onOpenSettings() }
                                    .padding(vertical = 2.dp)
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
                    if (emptyHint.value) {
                        item(key = "empty_hint") {
                            Text(
                                "刚才那句没传过来，可能丢件了——再说一次试试",
                                fontSize = 12.sp, color = HomecomingColors.InkSoft,
                                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                    itemsIndexed(messages, key = { _, m -> m.id }) { msgIdx, msg ->
                        // 间隔提示（功能三）：相邻消息时间差决定灰字，纯渲染层判断
                        val gapText = if (msgIdx > 0) {
                            gapLabel(messages[msgIdx - 1].createdAt, msg.createdAt)
                        } else null
                        Column(modifier = Modifier.fillMaxWidth()) {
                            gapText?.let { g ->
                                // 仿TG：日期/间隔提示做成居中小胶囊
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        g,
                                        fontSize = if (g.contains("月") && g.contains("周")) 11.sp else 10.sp,
                                        color = Color(0xFFA3907A),
                                        letterSpacing = 1.sp,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(Color.White.copy(alpha = 0.75f))
                                            .padding(horizontal = 14.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                        if (msg.role == "user") {
                            val mediaPaths = remember(msg.id) {
                                if (msg.attachmentKind == "image") {
                                    com.aion.chat.compose.data.HomecomingChatImageStore.pathsFor(context, msg.id)
                                } else emptyList()
                            }
                            // 视频（.mp4）单独渲染成可点播的气泡，图片走原样
                            val videoPaths = mediaPaths.filter { it.endsWith(".mp4", true) }
                            val imgPaths = mediaPaths.filter { !it.endsWith(".mp4", true) }
                            UserBubbles(
                                text = msg.text, imagePaths = imgPaths, videoPaths = videoPaths,
                                skin = skin.value, alpha = bubbleAlpha.value,
                                timeMs = msg.createdAt,
                                // 发送失败的那条不标 ✓✓（红卡与对勾同屏语义矛盾）
                                checkmark = sendError.value?.failedText != msg.text,
                                onPlayVideo = { p -> playerVideo = p },
                                onViewImage = { idx -> viewerPaths = imgPaths; viewerIndex = idx }
                            )
                        } else {
                            // 他发表情包：<sticker>名</sticker> 解析挪 IO（每条消息一次 DB+目录扫描，别在组合期跑）
                            var seanSticker by remember(msg.id) { mutableStateOf<java.io.File?>(null) }
                            LaunchedEffect(msg.id) {
                                seanSticker = kotlinx.coroutines.withContext(Dispatchers.IO) {
                                    com.aion.chat.compose.data.SeanAssetStore.stickerFile(context, msg.text)
                                }
                            }
                            // 他的画笔：<genimg>描述</genimg> → 调中转站出图，图显示在这条消息下面
                            val genPrompt = remember(msg.id) {
                                com.aion.chat.compose.data.SeanImageGenStore.extractPrompt(msg.text)
                            }
                            var genImage by remember(msg.id) { mutableStateOf<com.aion.chat.compose.data.SeanImageGenStore.GenImage?>(null) }
                            LaunchedEffect(msg.id, genPrompt, com.aion.chat.compose.data.SeanImageGenStore.rev) {
                                if (genPrompt == null) return@LaunchedEffect
                                genImage = kotlinx.coroutines.withContext(Dispatchers.IO) {
                                    com.aion.chat.compose.data.SeanImageGenStore.get(context, msg.id)
                                }
                                if (genImage == null) {
                                    com.aion.chat.compose.data.SeanImageGenStore.ensureGenerated(context, msg.id, genPrompt)
                                }
                            }
                            val cleanText = remember(msg.id) {
                                com.aion.chat.compose.data.SeanImageGenStore.strip(
                                    com.aion.chat.compose.data.SeanAssetStore.strip(
                                        com.aion.chat.compose.data.StudyRoomStore.strip(
                                            com.aion.chat.compose.data.CommandStore.strip(msg.text)
                                        )
                                    )
                                )
                            }
                            // 思考链（文档 4.1）：在气泡上方、弱样式、默认折叠、随消息持久化
                            if (com.aion.chat.compose.data.StyleSettings.showThinking(context)) {
                                var think by remember(msg.requestId) { mutableStateOf("") }
                                // 滚走再回来还是展开的（文档功能二：状态要记住）——rememberSaveable+消息键
                                var open by androidx.compose.runtime.saveable.rememberSaveable(msg.requestId) { mutableStateOf(false) }
                                LaunchedEffect(msg.requestId) {
                                    if (msg.requestId.isNotBlank()) {
                                        think = kotlinx.coroutines.withContext(Dispatchers.IO) {
                                            com.aion.chat.compose.data.ThinkingStore.get(context, msg.requestId)
                                        }
                                    }
                                }
                                if (think.isNotBlank()) {
                                    ThinkingFoldRow(
                                        thinking = think,
                                        expanded = open,
                                        onToggle = { open = !open },
                                        fontScale = com.aion.chat.compose.data.StyleSettings.thinkingFontScale(context),
                                        opacity = com.aion.chat.compose.data.StyleSettings.thinkingOpacity(context)
                                    )
                                }
                            }
                            val gi = genImage   // 委托属性不能智能转换：先落局部
                            AssistantBubbles(
                                text = cleanText, skin = skin.value, alpha = bubbleAlpha.value, timeMs = msg.createdAt,
                                stickerPath = seanSticker?.absolutePath,
                                genPath = gi?.path?.takeIf { gi.state == "done" },
                                genBusy = genPrompt != null && (gi == null || gi.state == "pending"),
                                genFailed = genPrompt != null && gi?.state == "failed",
                                messageId = msg.id,
                                onSpeakText = { t ->
                                    // 菜单朗读不受总开关限制：她点名要听哪条就听哪条
                                    runCatching {
                                        tts.value?.speak(
                                            t, android.speech.tts.TextToSpeech.QUEUE_FLUSH, null,
                                            "menu_" + System.currentTimeMillis()
                                        )
                                    }
                                },
                                onDeleteMessage = { id ->
                                    // 本地先摘（省一次全量查询+4 次 ensure+逐条扫描），DB 后台删
                                    main.post { messages.removeAll { it.id == id } }
                                    scope.launch(Dispatchers.IO) {
                                        runCatching { wiring?.chats?.deleteMessage(id) }
                                    }
                                },
                                onRegenerate = { regenerateMessage(msg.id) },
                                versionInfo = remember(msg.id, messages.size) {
                                    runCatching {
                                        val w = wiring
                                        if (w != null) {
                                            val cnt = w.chats.siblingCountOf(msg.id)
                                            if (cnt > 1) w.chats.siblingIndexOf(msg.id) to cnt else null
                                        } else null
                                    }.getOrNull()
                                },
                                onSwitchVersion = { delta -> switchVersion(msg.id, delta) }
                            )
                        }
                    }
                    // 生成中：思维链（实时）+ 工具状态行 + 温和提示（网关后端）
                    if (sending.value) {
                        val tl = thinkingLive.value
                        if (tl != null && com.aion.chat.compose.data.StyleSettings.showThinking(context)) {
                            item(key = "thinking_live") {
                                ThinkingFoldRow(
                                    thinking = tl,
                                    expanded = !com.aion.chat.compose.data.StyleSettings.autoFoldThinking(context),
                                    onToggle = {},
                                    fontScale = com.aion.chat.compose.data.StyleSettings.thinkingFontScale(context),
                                    opacity = com.aion.chat.compose.data.StyleSettings.thinkingOpacity(context),
                                    live = true
                                )
                            }
                        }
                        val tn = toolName.value
                        if (tn != null) {
                            item(key = "tool_row") {
                                Text(
                                    (if (toolDone.value == null) "正在使用 " else "✓ ") + tn + (if (toolDone.value == null) "…" else ""),
                                    fontSize = 11.sp, color = HomecomingColors.InkSoft,
                                    fontStyle = FontStyle.Italic,
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }
                        noticeText.value?.let { t ->
                            item(key = "notice_row") {
                                Text(
                                    t, fontSize = 11.sp, color = HomecomingColors.InkSoft,
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    // AI 点歌的播放卡（真实可播，点卡片用全局唯一播放器）
                    items(musicCards, key = { "music_" + it.createdAt + "_" + it.songId }) { card ->
                        val playerState = MusicPlayer.state.collectAsState().value
                        val playingThis = playerState.song?.id == card.songId && playerState.playing
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(CircleShape)
                                    .background(HomecomingColors.Accent.copy(alpha = 0.16f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Filled.MusicNote,
                                    contentDescription = "歌曲",
                                    tint = HomecomingColors.Accent,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Column(modifier = Modifier.weight(1f).padding(start = 10.dp)) {
                                Text(card.name, fontSize = 14.sp, color = HomecomingColors.Ink, fontWeight = FontWeight.Medium, maxLines = 1)
                                Text(card.artist, fontSize = 11.sp, color = HomecomingColors.InkSoft, maxLines = 1)
                            }
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(HomecomingColors.Accent)
                                    .clickable {
                                        if (playingThis) MusicPlayer.pause()
                                        else MusicPlayer.play(
                                            context,
                                            com.aion.chat.compose.data.MusicClient.Song(card.songId, card.name, card.artist)
                                        )
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (playingThis) Icons.Filled.Pause else Icons.AutoMirrored.Filled.Send,
                                    contentDescription = if (playingThis) "暂停" else "播放",
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                    // 戳一戳回显行：先上屏（教程第四步），他回话后消失
                    pendingSegments.forEachIndexed { index, seg ->
                        item(key = "pending_$index") {
                            AssistantBubbleSingle(text = seg, skin = skin.value, alpha = bubbleAlpha.value, timeText = timeText(System.currentTimeMillis()))
                        }
                    }
                    // ── 发送失败错误卡（照橘瓣 ChatError：标题+原因+建议+重试，可关） ──
                    sendError.value?.let { err ->
                        item(key = "send_error_card") {
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 6.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Color(0xFFFDECEC))
                                    .padding(horizontal = 12.dp, vertical = 10.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("⚠ 发送失败", fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                                        color = HomecomingColors.Danger, modifier = Modifier.weight(1f))
                                    Text("✕", fontSize = 13.sp, color = HomecomingColors.InkSoft,
                                        modifier = Modifier
                                            .clickable { sendError.value = null }
                                            .padding(horizontal = 6.dp))
                                }
                                Text(err.reason, fontSize = 12.sp, color = HomecomingColors.Ink,
                                    modifier = Modifier.padding(top = 4.dp))
                                Text(err.solution, fontSize = 11.sp, color = HomecomingColors.InkSoft,
                                    modifier = Modifier.padding(top = 3.dp))
                                if (err.retry != null) {
                                    Text(
                                        "重试",
                                        fontSize = 13.sp, color = HomecomingColors.Accent,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier
                                            .padding(top = 8.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable {
                                                val r = err.retry
                                                sendError.value = null
                                                r?.invoke()
                                            }
                                            .padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }
                // ── 表情包面板 ──
                if (showStickerPanel.value) {
                    val stickerFiles = remember(showStickerPanel.value, stickerVersion.value, sbStickerFiles.size, remoteStickerFiles.size) {
                        (java.io.File(context.filesDir, "stickers").listFiles()?.filter { it.isFile }
                            ?.sortedBy { it.name }?.take(48)
                            ?: emptyList()) + sbStickerFiles + remoteStickerFiles
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
                                "表情包加载中…（你们的亲亲抱抱 + 猫猫包 + 本地导入）",
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

                // ── 加号面板：表情包 / 相册 / 拍照（戳一戳已迁到主页桌宠） ──
                if (plusOpen.value) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.White.copy(alpha = 0.70f))
                    ) {
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
                                    Triple(Icons.Outlined.Movie, "视频", {
                                        plusOpen.value = false
                                        videoPicker.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                                        )
                                    }),
                                    Triple(Icons.Outlined.PhotoCamera, "拍照", {
                                        plusOpen.value = false
                                        takePhoto()
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

                // ── 待发图片预览（多图）：缩略图 + 移除 ──
                if (pendingImages.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 14.dp, end = 14.dp, bottom = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        pendingImages.forEachIndexed { pi, pair ->
                            Box {
                                val pv = remember(pair.second) {
                                    decodeChatImage(java.io.File(pair.second), 192)
                                }
                                pv?.let {
                                    Image(
                                        bitmap = it.asImageBitmap(),
                                        contentDescription = "待发图片",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(64.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .border(1.dp, HomecomingColors.Accent.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                                    )
                                }
                                Text(
                                    "✕", fontSize = 11.sp, color = Color.White,
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .size(18.dp)
                                        .background(Color.Black.copy(alpha = 0.55f), CircleShape)
                                        .clickable {
                                            val removed = pendingImages.removeAt(pi)
                                            // 拍照流会同时写 pendingImage/pendingImagePath：不同步清，删掉的图照样发
                                            if (pendingImagePath.value == removed.second) {
                                                pendingImage.value = ""
                                                pendingImagePath.value = ""
                                            }
                                        }
                                )
                            }
                        }
                    }
                }
                // 待发视频 chip：封面 + 时长 + 可撤
                pendingVideo?.let { pv ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 14.dp, end = 14.dp, bottom = 6.dp)
                    ) {
                        Box {
                            val thumb = remember(pv.thumbPath) {
                                if (pv.thumbPath.isBlank()) null
                                else decodeChatImage(java.io.File(pv.thumbPath), 192)
                            }
                            if (thumb != null) {
                                Image(
                                    bitmap = thumb.asImageBitmap(),
                                    contentDescription = "待发视频",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(64.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .border(1.dp, HomecomingColors.Accent.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(64.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(HomecomingColors.InkSoft.copy(alpha = 0.2f))
                                )
                            }
                            Icon(
                                Icons.Filled.PlayArrow, contentDescription = null, tint = Color.White,
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .size(22.dp)
                                    .background(Color.Black.copy(alpha = 0.45f), CircleShape)
                            )
                            Text(
                                "%d:%02d".format(pv.durationMs / 60000, (pv.durationMs / 1000) % 60),
                                fontSize = 9.sp, color = Color.White,
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(3.dp)
                                    .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 3.dp)
                            )
                            Text(
                                "✕", fontSize = 11.sp, color = Color.White,
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .size(18.dp)
                                    .background(Color.Black.copy(alpha = 0.55f), CircleShape)
                                    .clickable { pendingVideo = null }
                            )
                        }
                        Text(
                            "视频 · 他会一帧帧看",
                            fontSize = 11.sp, color = HomecomingColors.InkSoft,
                            modifier = Modifier.padding(start = 8.dp).align(Alignment.CenterVertically)
                        )
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
                        )
                        .bubbleBgImage(
                            com.aion.chat.compose.data.StyleSettings.SLOT_INPUT,
                            RoundedCornerShape(22.dp)
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
                        shape = RoundedCornerShape(if (skin.value == 3) 24.dp else 14.dp),
                        colors = if (skin.value == 3) androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color.White.copy(alpha = 0.95f),
                            unfocusedContainerColor = Color.White.copy(alpha = 0.95f),
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent
                        ) else androidx.compose.material3.OutlinedTextFieldDefaults.colors(),
                        maxLines = 4
                    )
                    if (skin.value == 3 && input.value.isBlank() && !sending.value && pendingImages.isEmpty() && pendingVideo == null) {
                        // 仿TG：没打字时右边是麦克风（语音输入），有字才变发送键
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .shadow(2.dp, CircleShape)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.9f))
                                .clickable { startVoiceInput() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Filled.Mic,
                                contentDescription = "语音输入",
                                tint = Color(0xFF8A7A64)
                            )
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(HomecomingColors.Accent)
                                .clickable { send() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (sending.value) Icons.Filled.Stop else Icons.AutoMirrored.Filled.Send,
                                contentDescription = if (sending.value) "停止" else "发送",
                                tint = Color.White
                            )
                        }
                    }
                }
            }

            // ── 回到底部浮动钮（仿TG）：往上翻过消息时浮出，点了滚到最新 ──
            val canScrollForward by remember { derivedStateOf { listState.canScrollForward } }
            if (skin.value == 3 && canScrollForward && !showStickerPanel.value) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 16.dp, bottom = 150.dp)
                        .size(40.dp)
                        .shadow(3.dp, CircleShape)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.92f))
                        .clickable {
                            scope.launch {
                                val total = listState.layoutInfo.totalItemsCount
                                if (total > 0) listState.animateScrollToItem(total - 1)
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.KeyboardArrowDown,
                        contentDescription = "回到底部",
                        tint = Color(0xFF8A7A64)
                    )
                }
            }
        }
    }

    // ── 聊天记录搜索 ──
    if (showSearch.value) {
        val q = remember { mutableStateOf("") }
        val results = remember { mutableStateOf(emptyList<HomecomingChatRepository.Message>()) }
        // 防抖 300ms 再去 IO 线程查：每敲一个字符同步查 500 条会卡主线程
        LaunchedEffect(q.value) {
            val key = q.value.trim()
            if (key.isEmpty()) {
                results.value = emptyList()
                return@LaunchedEffect
            }
            kotlinx.coroutines.delay(300)
            val w = wiring ?: return@LaunchedEffect
            results.value = kotlinx.coroutines.withContext(Dispatchers.IO) {
                runCatching {
                    w.listMessages(HomecomingChatWiring.TIMELINE, 500)
                        .filter { it.text.contains(key) }
                        .take(30)
                }.getOrDefault(emptyList())
            }
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
                        items(results.value, key = { it.id }) { m ->
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
                        if (results.value.isEmpty() && q.value.isNotBlank()) {
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

    // ── 模型选择弹层（橘瓣同款） ──
    if (showModelSheet) {
        ModelPickerSheet(onDismiss = { showModelSheet = false })
    }

    // ── 收藏页（聊天抽屉 → 收藏） ──
    if (showFavorites.value) {
        Box(Modifier.fillMaxSize().background(Color(0xFFFCFAF6))) {
            FavoriteScreen(onBack = { showFavorites.value = false })
        }
    }

    // ── 统计页（抽屉 → 统计） ──
    if (showStats.value) {
        Box(Modifier.fillMaxSize().background(Color(0xFFFCFAF6))) {
            com.aion.chat.compose.ui.settings.StatsScreen(onBack = { showStats.value = false })
        }
    }

    // ── 记忆库（他的配置 → 记忆 → 打开） ──
    if (showMemoryBank.value) {
        Box(Modifier.fillMaxSize().background(Color(0xFFF7F5F1))) {
            MemoryBankScreen(onBack = { showMemoryBank.value = false })
        }
    }

    // ── MCP 配置页（他的配置 → MCP；设置页也有入口） ──
    if (showMcp.value) {
        Box(Modifier.fillMaxSize().background(Color(0xFFF2F0EB))) {
            com.aion.chat.compose.ui.settings.McpScreen(onBack = { showMcp.value = false })
        }
    }

    // ── 他的配置页（对话页左侧进入，橘瓣式） ──
    if (showSeanConfig.value) {
        Box(Modifier.fillMaxSize().background(Color(0xFFF3F1EC))) {
            SeanConfigScreen(
                onBack = { showSeanConfig.value = false },
                onPickModel = {
                    showSeanConfig.value = false
                    showModelSheet = true
                },
                onOpenMcp = {
                    showSeanConfig.value = false
                    showMcp.value = true
                },
                onOpenMemoryBank = {
                    showSeanConfig.value = false
                    showMemoryBank.value = true
                }
            )
        }
    }

    // ── 线路装配诊断卡（胶囊点重试仍失败时自动弹出） ──
    if (showWiringDiag && wiringError != null) {
        WiringDiagDialog(
            error = wiringError ?: "",
            trace = wiringTrace,
            onRetry = { showWiringDiag = false; wiringRetry++ },
            onDismiss = { showWiringDiag = false }
        )
    }

    // ── 图片查看器（审计#4：之前写了 130 行但从未挂载） ──
    if (viewerPaths.isNotEmpty()) {
        ChatImageViewer(
            paths = viewerPaths,
            index = viewerIndex,
            onIndex = { viewerIndex = it },
            onClose = { viewerPaths = emptyList(); viewerIndex = 0 }
        )
    }

    // ── 视频播放（她发的视频，点气泡弹出） ──
    playerVideo?.let { vp ->
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { playerVideo = null },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .clickable { playerVideo = null }
            ) {
                AndroidView(
                    factory = { ctx ->
                        android.widget.VideoView(ctx).apply {
                            setVideoPath(vp)
                            setOnPreparedListener { mp -> mp.isLooping = true; mp.start() }
                        }
                    },
                    modifier = Modifier.align(Alignment.Center).fillMaxSize()
                )
                Text(
                    "✕", color = Color.White, fontSize = 18.sp,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(18.dp)
                        .background(Color.Black.copy(alpha = 0.4f), CircleShape)
                        .padding(6.dp)
                        .clickable { playerVideo = null }
                )
            }
        }
    }

    // ── 聊天设置抽屉：气泡不透明度 + 聊天背景（从主设置搬来） ──
    AnimatedVisibility(
        visible = showChatDrawer.value,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier.fillMaxSize()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.30f))
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) { showChatDrawer.value = false }
        )
    }
    AnimatedVisibility(
        visible = showChatDrawer.value,
        enter = slideInHorizontally(initialOffsetX = { -it }),
        exit = slideOutHorizontally(targetOffsetX = { -it }),
        modifier = Modifier.fillMaxHeight()
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .width(300.dp)
                .shadow(10.dp)
                .background(Color.White.copy(alpha = 0.95f))
                .padding(22.dp)
        ) {
            Text("聊天设置", fontSize = 18.sp, color = HomecomingColors.Ink, fontWeight = FontWeight.Medium)
            Text("只影响聊天页这一页", fontSize = 11.sp, color = HomecomingColors.InkSoft)
            Spacer(Modifier.height(18.dp))

            Text("气泡不透明度", fontSize = 14.sp, color = HomecomingColors.Ink)
            Text("调低透出背景，调高更清晰", fontSize = 11.sp, color = HomecomingColors.InkSoft)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Slider(
                    value = bubbleAlpha.value,
                    onValueChange = { bubbleAlpha.value = it },
                    onValueChangeFinished = {
                        prefs.edit().putFloat("bubble_alpha", bubbleAlpha.value).apply()
                    },
                    valueRange = 0.3f..1f,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    (bubbleAlpha.value * 100).toInt().toString() + "%",
                    fontSize = 12.sp, color = HomecomingColors.Ink,
                    modifier = Modifier.width(42.dp)
                )
            }
            Spacer(Modifier.height(16.dp))

            Text("找聊天记录 / 收藏 / 统计", fontSize = 14.sp, color = HomecomingColors.Ink)
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = {
                        showChatDrawer.value = false
                        showSearch.value = true
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("找记录") }
                Button(
                    onClick = {
                        showChatDrawer.value = false
                        showFavorites.value = true
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("收藏") }
                Button(
                    onClick = {
                        showChatDrawer.value = false
                        showStats.value = true
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("统计") }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = {
                        showChatDrawer.value = false
                        showSkinDialog.value = true
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("气泡皮肤") }
            }
            Spacer(Modifier.height(16.dp))

            // 他的配置（橘瓣式助手配置页入口）
            Button(
                onClick = {
                    showChatDrawer.value = false
                    showSeanConfig.value = true
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("他的配置（人设 · 提示词 · 记忆）") }
            Spacer(Modifier.height(16.dp))

            Text("聊天背景图", fontSize = 14.sp, color = HomecomingColors.Ink)
            Text("只给聊天页换一张；不选则跟随全局背景", fontSize = 11.sp, color = HomecomingColors.InkSoft)
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = {
                        chatDrawerPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("选一张照片") }
                OutlinedButton(
                    onClick = {
                        val f = SettingsBg.chatFile(context)
                        if (f.exists() && f.delete()) {
                            SettingsBg.bump()
                            chatBgVersion.value = System.currentTimeMillis()
                            Toast.makeText(context, "聊天页已跟随全局背景", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "聊天页现在用的就是全局背景", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("跟随全局") }
            }
            Spacer(Modifier.weight(1f))
            Text("提示：从屏幕左边缘向右滑也能打开这里", fontSize = 10.sp, color = HomecomingColors.InkSoft)
        }
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

/** 功能三：相邻消息时间差 → 灰字提示；<30 分钟不显示，跨天显示日期（比间隔更直观）。 */
private fun gapLabel(prevMs: Long, curMs: Long): String? {
    val diff = curMs - prevMs
    if (diff < 30 * 60_000L) return null
    val zone = ZoneId.systemDefault()
    val prevDay = Instant.ofEpochMilli(prevMs).atZone(zone).toLocalDate()
    val curDay = Instant.ofEpochMilli(curMs).atZone(zone).toLocalDate()
    val days = diff / 86_400_000L
    return when {
        days >= 7 -> "隔了 $days 天"
        prevDay != curDay -> curDay.format(DateTimeFormatter.ofPattern("M月d日 E", Locale.CHINA))
        diff >= 2 * 3_600_000L -> "隔了 ${diff / 3_600_000L} 小时"
        else -> "隔了 1 小时"
    }
}

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
    BubbleSkinDef("墨蓝", "🌊"),
    BubbleSkinDef("仿TG", "✈️")
)

private data class UserStyle(val fill: Color, val textColor: Color, val shape: RoundedCornerShape)

private fun userStyle(skin: Int, alpha: Float): UserStyle = when (skin) {
    1 -> UserStyle(Color.White.copy(alpha = (0.25f * alpha).coerceIn(0.12f, 1f)), HomecomingColors.Ink, RoundedCornerShape(22.dp))
    2 -> UserStyle(Color(0xFF2C4A5A).copy(alpha = alpha), Color.White, RoundedCornerShape(16.dp))
    else -> UserStyle(HomecomingColors.IceBlueDeep.copy(alpha = alpha), Color(0xFF17323A), RoundedCornerShape(18.dp, 18.dp, 6.dp, 18.dp))
}

/** 用户气泡组（一条消息渲染为多条小气泡）；多图堆叠卡，点图进查看器。 */
@Composable
fun UserBubbles(
    text: String,
    imagePaths: List<String>,
    videoPaths: List<String> = emptyList(),
    skin: Int,
    alpha: Float = 1f,
    timeMs: Long = 0L,
    checkmark: Boolean = true,
    onPlayVideo: (String) -> Unit = {},
    onViewImage: (Int) -> Unit = {},
) {
    val segments = splitBubbles(text)
    val timeText = if (timeMs > 0) timeText(timeMs) else ""
    // StyleHub：字体大小 / 透明度 / 圆角（她的绿气泡同款接线）
    val ctxU = LocalContext.current
    val fontScaleU = com.aion.chat.compose.data.StyleSettings.fontScale(ctxU)
    val bRadU = com.aion.chat.compose.data.StyleSettings.bubbleRadius(ctxU).coerceIn(0, 40)
    val fontU = styleFontFamily(com.aion.chat.compose.data.StyleSettings.fontFamily(ctxU))
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalAlignment = Alignment.End
    ) {
        // 视频气泡（她发的视频）：封面 + 时长 + 点按播放
        videoPaths.forEach { vp ->
            VideoBubble(path = vp, onPlay = { onPlayVideo(vp) })
        }
        if (imagePaths.isNotEmpty()) {
            // 多图堆叠卡（原作者 chat-image-group 的 Compose 版）：>1 张折叠成扇形堆，点一下展开
            var stackExpanded by remember(imagePaths) { mutableStateOf(imagePaths.size == 1) }
            if (imagePaths.size > 1) {
                Text(
                    if (stackExpanded) "收起图片 ▴" else "共 ${imagePaths.size} 张 · 点开 ▾",
                    fontSize = 10.sp, color = HomecomingColors.InkSoft,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { stackExpanded = !stackExpanded }
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
            if (imagePaths.size > 1 && !stackExpanded) {
                // 折叠：第一张完整显示，后面几张错位叠在后面
                Box(modifier = Modifier.padding(end = 10.dp)) {
                    imagePaths.drop(1).asReversed().forEachIndexed { i, _ ->
                        Box(
                            modifier = Modifier
                                .offset(x = (6 * (i + 1)).dp, y = (4 * (i + 1)).dp)
                                .widthIn(max = 200.dp)
                                .heightIn(max = 140.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color(0xFFD9D4C8).copy(alpha = 0.9f))
                        )
                    }
                    Box {
                        TgChatImage(
                            path = imagePaths.first(), skin = skin,
                            modifier = Modifier
                                .widthIn(max = 220.dp)
                                .heightIn(max = 150.dp)
                                .clip(RoundedCornerShape(14.dp)),
                            onView = { onViewImage(0) }
                        )
                        if (skin == 3) {
                            TgTimePill(
                                timeText,
                                Modifier.align(Alignment.BottomEnd).padding(8.dp)
                            )
                        }
                    }
                }
            } else {
                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    horizontalAlignment = Alignment.End
                ) {
                    imagePaths.forEachIndexed { idx, p ->
                        // 仿TG：图片右下角叠半透明时间胶囊（贴纸不叠）
                        Box {
                            TgChatImage(
                                path = p, skin = skin,
                                modifier = Modifier
                                    .widthIn(max = 240.dp)
                                    .heightIn(max = 300.dp)
                                    .clip(RoundedCornerShape(16.dp)),
                                isSticker = p.contains("/stickers") && imagePaths.size == 1,
                                onView = { onViewImage(idx) }
                            )
                            if (skin == 3 && !p.contains("/stickers")) {
                                TgTimePill(
                                    timeText,
                                    Modifier.align(Alignment.BottomEnd).padding(8.dp)
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(2.dp))
        }
        segments.forEachIndexed { segIdx, seg ->
            if (skin == 3) {
                // 仿TG：浅绿气泡、时间+✓✓ 内嵌右下、右下角小圆角（按预览：小号紧凑）
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                    CopyOnLongPress(text = seg) {
                        val rU = bRadU.dp
                        val shapeU = RoundedCornerShape(rU, rU, (rU / 3.5f).coerceIn(0.dp, 9.dp), rU)
                        Box(
                            modifier = Modifier
                                .widthIn(max = 280.dp)
                                .clip(shapeU)
                                .background(Color(0xFFDCF2C6).copy(alpha = (0.96f * alpha).coerceIn(0.3f, 1f)))
                                .bubbleBgImage(com.aion.chat.compose.data.StyleSettings.SLOT_USER_BUBBLE, shapeU)
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                tgInline(text = seg, time = timeText, check = checkmark, isLast = segIdx == segments.lastIndex, timeColor = Color(0xFF7FA05C)),
                                fontSize = (14 * fontScaleU).sp, color = Color(0xFF3D4A30),
                                lineHeight = (20 * fontScaleU).sp,
                                fontFamily = fontU
                            )
                        }
                    }
                }
            } else {
                val style = userStyle(skin, alpha)
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                    CopyOnLongPress(text = seg) {
                        Box(
                            modifier = Modifier
                                .widthIn(max = 280.dp)
                                .clip(style.shape)
                                .background(style.fill)
                                .let { m ->
                                    if (skin == 1) m.border(1.dp, Color.White.copy(alpha = (0.6f * alpha).coerceIn(0.15f, 1f)), style.shape) else m
                                }
                                .padding(horizontal = 14.dp, vertical = 10.dp)
                        ) {
                            Text(seg, fontSize = 15.sp, color = style.textColor)
                        }
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}

/** 聊天单张图：普通图片，点开进查看器。 */
@Composable
private fun TgChatImage(
    path: String,
    skin: Int,
    modifier: Modifier,
    isSticker: Boolean = false,
    onView: () -> Unit,
) {
    val bmp = remember(path) { decodeChatImage(java.io.File(path), if (isSticker) 384 else 1024) }
    val shown = bmp
    if (shown == null) {
        Text(
            "📷 图片已发送",
            fontSize = 11.sp, color = HomecomingColors.InkSoft,
            modifier = Modifier.fillMaxWidth().padding(bottom = 2.dp),
            textAlign = TextAlign.End
        )
        return
    }
    Image(
        bitmap = shown.asImageBitmap(),
        contentDescription = "聊天图片",
        contentScale = if (isSticker) ContentScale.Fit else ContentScale.Crop,
        modifier = modifier
            .then(if (isSticker) Modifier.size(120.dp) else Modifier)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) { onView() }
    )
}

/** Sean 气泡组（一条消息渲染为多条小气泡）。 */
@Composable
fun AssistantBubbles(
    text: String, skin: Int, alpha: Float = 1f, timeMs: Long = 0L,
    stickerPath: String? = null,
    genPath: String? = null,
    genBusy: Boolean = false,
    genFailed: Boolean = false,
    messageId: String? = null,
    onSpeakText: (String) -> Unit = {},
    onDeleteMessage: (String) -> Unit = {},
    onRegenerate: (() -> Unit)? = null,
    versionInfo: Pair<Int, Int>? = null,
    onSwitchVersion: ((Int) -> Unit)? = null
) {
    val segments = splitBubbles(text)
    val timeText = if (timeMs > 0) timeText(timeMs) else ""
    Column(modifier = Modifier.fillMaxWidth()) {
        // 他发的表情包：无气泡边框大图（照 TG 表情的样式），文字为空就只发表情
        if (stickerPath != null) {
            val f = remember(stickerPath) { java.io.File(stickerPath) }
            val bmp = remember(stickerPath) { f.takeIf { it.exists() }?.let { decodeChatImage(it, 256) } }
            if (bmp != null) {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = "表情包",
                    modifier = Modifier
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                        .heightIn(max = 132.dp)
                        .clip(RoundedCornerShape(10.dp))
                )
            }
        }
        // 他画的图：显示在这条消息下面（点开大图）
        genPath?.let { gp ->
            val f = remember(gp) { java.io.File(gp) }
            val bmp = remember(gp) { f.takeIf { it.exists() }?.let { decodeChatImage(it, 1080) } }
            if (bmp != null) {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = "他画的图",
                    modifier = Modifier
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                        .widthIn(max = 248.dp)
                        .clip(RoundedCornerShape(14.dp))
                )
            }
        }
        if (genBusy) {
            Text(
                "✏️ 他正在画…",
                fontSize = 11.sp, color = HomecomingColors.InkSoft,
                fontStyle = FontStyle.Italic,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
            )
        }
        if (genFailed) {
            Text(
                "（这次没画成——中转站可能没通，稍后再让他画一张）",
                fontSize = 11.sp, color = HomecomingColors.InkSoft,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
            )
        }
        if (segments.isNotEmpty() || stickerPath == null) {
            segments.forEachIndexed { i, seg ->
                AssistantBubbleSingle(
                    text = seg, skin = skin, alpha = alpha, timeText = timeText,
                    isLast = i == segments.lastIndex,
                    fullText = text, messageId = messageId,
                    onSpeakText = onSpeakText, onDeleteMessage = onDeleteMessage,
                    onRegenerate = if (i == segments.lastIndex) onRegenerate else null,
                    versionInfo = if (i == segments.lastIndex) versionInfo else null,
                    onSwitchVersion = onSwitchVersion
                )
            }
        } else if (timeText.isNotEmpty()) {
            // 纯表情消息：时间戳单独一行小字
            Text(
                timeText,
                fontSize = 9.sp, color = HomecomingColors.InkSoft,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 1.dp)
            )
        }
    }
}

/** 长按复制气泡文本（用户气泡保留原行为）。 */
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

/**
 * 长按气泡 → 菜单：朗读 / 复制 / 删除（Yuri 点名：朗读和删除放长按里）。
 * 删除两段确认（第一下变「确认删除？」），避免误删 Sean 的原话。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageLongPressMenu(
    text: String,
    fullText: String,
    messageId: String?,
    isAssistant: Boolean = false,
    onSpeakText: (String) -> Unit,
    onDeleteMessage: (String) -> Unit,
    onRegenerate: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    var showMenu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier.combinedClickable(
            onClick = {},
            onLongClick = { showMenu = true; confirmDelete = false }
        )
    ) { content() }
    if (showMenu) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showMenu = false; confirmDelete = false },
            title = { Text("这条消息", fontSize = 15.sp, color = HomecomingColors.Ink) },
            text = {
                Column {
                    if (fullText.length > 60) {
                        Text(
                            fullText.take(60) + "…",
                            fontSize = 12.sp, color = HomecomingColors.InkSoft,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                    TextButton(
                        onClick = {
                            showMenu = false
                            runCatching { onSpeakText(fullText) }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("朗读", fontSize = 14.sp, color = HomecomingColors.Ink) }
                    TextButton(
                        onClick = {
                            showMenu = false
                            runCatching {
                                val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                                    as android.content.ClipboardManager
                                cm.setPrimaryClip(android.content.ClipData.newPlainText("chat", text))
                                Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("复制", fontSize = 14.sp, color = HomecomingColors.Ink) }
                    TextButton(
                        onClick = {
                            showMenu = false
                            com.aion.chat.compose.data.FavoriteStore.add(context, fullText.ifBlank { text })
                            Toast.makeText(context, "已收藏", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("收藏", fontSize = 14.sp, color = HomecomingColors.Accent) }
                    if (onRegenerate != null && isAssistant && messageId != null) {
                        TextButton(
                            onClick = {
                                showMenu = false
                                runCatching { onRegenerate() }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("重新生成", fontSize = 14.sp, color = HomecomingColors.Accent) }
                    }
                    if (messageId != null) {
                        TextButton(
                            onClick = {
                                if (!confirmDelete) {
                                    confirmDelete = true
                                } else {
                                    showMenu = false
                                    onDeleteMessage(messageId)
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                if (confirmDelete) "确认删除？" else "删除",
                                fontSize = 14.sp,
                                color = HomecomingColors.Danger
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {}
        )
    }
}

@Composable
private fun AssistantBubbleSingle(
    text: String, skin: Int, alpha: Float = 1f,
    timeText: String = "", isLast: Boolean = true,
    fullText: String = "",
    messageId: String? = null,
    onSpeakText: (String) -> Unit = {},
    onDeleteMessage: (String) -> Unit = {},
    onRegenerate: (() -> Unit)? = null,
    versionInfo: Pair<Int, Int>? = null,   // (当前序号, 总数)——多版本时显示 ‹ 2/3 ›
    onSwitchVersion: ((Int) -> Unit)? = null,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
        // StyleHub 四项：聊天字体大小 / 气泡透明度 / 气泡圆角 / 显示助手消息气泡
        val ctxB = LocalContext.current
        val fontScaleB = com.aion.chat.compose.data.StyleSettings.fontScale(ctxB)
        val bRadB = com.aion.chat.compose.data.StyleSettings.bubbleRadius(ctxB).coerceIn(0, 40)
        val showAiBubbleB = com.aion.chat.compose.data.StyleSettings.showAiBubble(ctxB)
        val fontB = styleFontFamily(com.aion.chat.compose.data.StyleSettings.fontFamily(ctxB))
        val tgShape = RoundedCornerShape(bRadB.dp, bRadB.dp, (bRadB / 3.5f).dp.coerceIn(0.dp, 9.dp), bRadB.dp)
        MessageLongPressMenu(
            text = text,
            fullText = fullText.ifBlank { text },
            messageId = messageId,
            isAssistant = true,
            onSpeakText = onSpeakText,
            onDeleteMessage = onDeleteMessage,
            onRegenerate = onRegenerate
        ) {
            if (skin == 3) {
                // 仿TG：白底黑字、时间灰字内嵌右下、左下角小圆角（字体/透明度/圆角跟随 StyleHub；气泡可关）
                Box(
                    modifier = Modifier
                        .widthIn(max = 300.dp)
                        .then(
                            if (showAiBubbleB) Modifier
                                .clip(tgShape)
                                .background(Color.White.copy(alpha = (0.97f * alpha).coerceIn(0.3f, 1f)))
                                .bubbleBgImage(com.aion.chat.compose.data.StyleSettings.SLOT_AI_BUBBLE, tgShape)
                            else Modifier
                        )
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        tgInline(text = text, time = timeText, check = false, isLast = isLast, timeColor = Color(0xFFB3A48E)),
                        fontSize = (14 * fontScaleB).sp, color = Color(0xFF4A4238),
                        lineHeight = (20 * fontScaleB).sp,
                        fontFamily = fontB
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .widthIn(max = 300.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color.White.copy(alpha = (0.92f * alpha).coerceIn(0.15f, 1f)))
                        .border(1.dp, Color.White.copy(alpha = (0.85f * alpha).coerceIn(0.2f, 1f)), RoundedCornerShape(18.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Text(text, fontSize = 15.sp, color = HomecomingColors.Ink)
                }
            }
        }
    }  // Box 结束

    // 版本指示器：‹ 2/3 ›（多版本时显示，点箭头切换）
    if (versionInfo != null && versionInfo.second > 1 && onSwitchVersion != null) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 10.dp, top = 2.dp)
        ) {
            Text(
                "‹", fontSize = 15.sp, color = HomecomingColors.Accent,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clickable { onSwitchVersion(-1) }
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
            Text(
                "${versionInfo.first} / ${versionInfo.second}",
                fontSize = 10.sp, color = HomecomingColors.InkSoft,
                letterSpacing = 1.sp
            )
            Text(
                "›", fontSize = 15.sp, color = HomecomingColors.Accent,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clickable { onSwitchVersion(1) }
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
    }
    }  // Column 结束
}

/** 仿TG：正文 + 右下角小号灰字时间（发出的多一对✓✓），内嵌在最后一个字后面。 */
private fun tgInline(text: String, time: String, check: Boolean, isLast: Boolean, timeColor: Color = Color(0xFF9AA394)) =
    if (time.isBlank() || !isLast) AnnotatedString(text)
    else buildAnnotatedString {
        append(text)
        withStyle(SpanStyle(fontSize = 9.5.sp, color = timeColor, fontWeight = FontWeight.Normal)) {
            append("  " + time + if (check) " ✓✓" else "")
        }
    }

/** 时间胶囊（叠在图片/表情右下角）：深色半透明底、白字。 */
@Composable
private fun TgTimePill(time: String, modifier: Modifier) {
    if (time.isBlank()) return
    Text(
        time,
        fontSize = 10.sp, color = Color.White,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(Color.Black.copy(alpha = 0.45f))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    )
}

private fun timeText(ms: Long): String =
    java.time.Instant.ofEpochMilli(ms).atZone(java.time.ZoneId.systemDefault())
        .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))


// ── 视频消息（她发视频 → 抽帧给他看 + 本地可回放） ──

/** 待选抽帧参数的视频（切片弹窗输入）。 */
private data class SlicePrep(val dstPath: String, val durMs: Long, val thumbPath: String)

/** 按参数抽帧：帧数模式取每段中点；间隔模式按间隔取位（相邻同帧去重）。 */
private fun extractFramesForModel(
    r: android.media.MediaMetadataRetriever,
    durMs: Long,
    count: Int,
    intervalMs: Long
): List<String> {
    val out = mutableListOf<String>()
    val positions: List<Long> = if (intervalMs > 0) {
        val n = maxOf(2, minOf(20, (durMs / intervalMs).toInt()))
        (0 until n).map { i -> (i * intervalMs * 1000L).coerceAtMost(durMs * 1000L - 1) }
    } else {
        // 帧数模式：含首帧（0s 起），v9 审计 P2 修复「少抽一帧」
        (0 until count.coerceIn(2, 20)).map { i -> (durMs * 1000L * i / count).coerceAtMost(durMs * 1000L - 1) }
    }
    positions.forEach { atUs ->
        val f = r.getFrameAtTime(atUs, android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC) ?: return@forEach
        val bos = ByteArrayOutputStream()
        scaledBitmap(f, 768).compress(Bitmap.CompressFormat.JPEG, 78, bos)
        f.recycle()
        out.add("data:image/jpeg;base64," + Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP))
        if (out.size >= 20) return out
    }
    return out.distinct()
}

/** 待发视频：本地 mp4 + 封面 + 给模型看的按序抽帧。 */
private data class VideoDraft(
    val path: String,        // chat_videos/vid_x.mp4（气泡按 .mp4 识别成视频）
    val thumbPath: String,   // 封面 jpg（命名约定：去掉 .mp4 加 _thumb.jpg）
    val durationMs: Long,
    val frames: List<String> // dataUrl，按时间顺序
)

/** 抽帧/封面通用：等比压到长边 maxEdge 内。 */
private fun scaledBitmap(bmp: Bitmap, maxEdge: Int): Bitmap {
    val w = bmp.width
    val h = bmp.height
    if (w <= 0 || h <= 0) return bmp
    val s = maxEdge.toFloat() / maxOf(w, h)
    if (s >= 1f) return bmp
    return Bitmap.createScaledBitmap(bmp, (w * s).toInt().coerceAtLeast(1), (h * s).toInt().coerceAtLeast(1), true)
}

/** 气泡显示用：读视频时长（秒）；读不出回落 0（不显示时长）。 */
private fun videoDurationSec(path: String): Int = runCatching {
    val r = android.media.MediaMetadataRetriever()
    try {
        r.setDataSource(path)
        ((r.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L) / 1000L).toInt()
    } finally {
        r.release()
    }
}.getOrDefault(0)

/** 视频气泡：封面 + 中央播放键 + 右下时长，点按弹出播放。 */
@Composable
private fun VideoBubble(path: String, onPlay: () -> Unit) {
    // 封面解码 + 时长读取都是磁盘/媒体操作：挪 IO（组合期跑会在滚动时掉帧）
    var thumb by remember(path) { mutableStateOf<Bitmap?>(null) }
    var durSec by remember(path) { mutableStateOf(0) }
    LaunchedEffect(path) {
        val pair = kotlinx.coroutines.withContext(Dispatchers.IO) {
            runCatching { decodeChatImage(java.io.File(path.removeSuffix(".mp4") + "_thumb.jpg"), 512) }.getOrNull() to videoDurationSec(path)
        }
        thumb = pair.first; durSec = pair.second
    }
    // 横屏视频不再被裁成竖条：按封面宽高比算高度（限 110~250dp）
    val aspect = thumb?.let { it.width.toFloat() / it.height.toFloat() } ?: (9f / 16f)
    val boxH = (180f / aspect).dp.coerceIn(110.dp, 250.dp)
    Box(
        modifier = Modifier
            .padding(end = 10.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable { onPlay() }
    ) {
        val t = thumb
        if (t != null) {
            Image(
                bitmap = t.asImageBitmap(),
                contentDescription = "视频",
                contentScale = ContentScale.Crop,
                modifier = Modifier.width(180.dp).height(boxH)
            )
        } else {
            Box(
                modifier = Modifier
                    .width(180.dp)
                    .height(boxH)
                    .background(HomecomingColors.InkSoft.copy(alpha = 0.2f))
            )
        }
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(44.dp)
                .background(Color.Black.copy(alpha = 0.45f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.PlayArrow, contentDescription = "播放", tint = Color.White, modifier = Modifier.size(28.dp))
        }
        if (durSec > 0) {
            Text(
                "%d:%02d".format(durSec / 60, durSec % 60),
                fontSize = 10.sp, color = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp)
                    .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 5.dp, vertical = 1.dp)
            )
        }
    }
}

/** StyleHub 字体 chips（0=默认 1=衬线 2=等宽）→ FontFamily。 */
internal fun styleFontFamily(idx: Int): androidx.compose.ui.text.font.FontFamily = when (idx) {
    1 -> androidx.compose.ui.text.font.FontFamily.Serif
    2 -> androidx.compose.ui.text.font.FontFamily.Monospace
    else -> androidx.compose.ui.text.font.FontFamily.Default
}

/** 气泡背景图（StyleHub 插图素材）：返回裁好形状的背景 Modifier；没选图返回原样。 */
@Composable
private fun Modifier.bubbleBgImage(slot: String, shape: androidx.compose.ui.graphics.Shape): Modifier {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val rev = com.aion.chat.compose.data.StyleSettings.rev
    val bmp = remember(slot, rev) {
        runCatching {
            val f = com.aion.chat.compose.data.StyleSettings.imageFile(ctx, slot)
            if (!f.exists()) null else decodeChatImage(f, 512)
        }.getOrNull()
    } ?: return this
    return this.drawWithContentCompat(bmp, shape)
}

/** 位图铺底 + 内容在上（裁形）。 */
private fun Modifier.drawWithContentCompat(
    bmp: Bitmap,
    shape: androidx.compose.ui.graphics.Shape
): Modifier = this.then(
    Modifier
        .clip(shape)
        .drawBehind {
            drawImage(
                bmp.asImageBitmap(),
                dstSize = IntSize(size.width.toInt(), size.height.toInt()),
                alpha = 0.90f
            )
            // 压一层浅白，保证字可读
            drawRect(Color.White.copy(alpha = 0.35f))
        }
)

/**
 * 发送失败错误卡（照橘瓣 ChatService.ChatError 模式）：
 * 标题 + 具体原因 + 建议动作 + 可重试，挂在聊天流里可关闭——不再是一闪而过的 toast。
 */
private data class SendError(
    val title: String,
    val reason: String,
    val solution: String,
    val failedText: String? = null,   // 失败那条的原文（✓✓ 不再标在失败消息上）
    val retry: (() -> Unit)? = null   // 重试 = 带全部原参（图/帧/前缀/requestId）重走中转站路径
)

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

/**
 * 思考链折叠行（文档 4.1）：弱样式、默认折叠、点开看全文；live=true 为生成中的实时区。
 */
@Composable
fun ThinkingFoldRow(
    thinking: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    fontScale: Float,
    opacity: Float,
    live: Boolean = false,
) {
    // 展开态完全由调用方持有（双份状态会在 LazyColumn 复用时错乱——审计发现）
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFFEFECE4).copy(alpha = 0.75f * opacity))
            .clickable { onToggle() }
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(
            (if (live) "他正在想…" else "他在想") + (if (expanded) " ▾" else " ▸"),
            fontSize = 10.sp,
            color = Color(0xFF8A8172),
            fontStyle = FontStyle.Italic,
            fontWeight = FontWeight.Medium
        )
        if (expanded) {
            Spacer(Modifier.height(3.dp))
            Text(
                thinking,
                fontSize = (11 * fontScale).sp,
                lineHeight = (17 * fontScale).sp,
                color = Color(0xFF7A7264),
                fontStyle = FontStyle.Italic
            )
        } else {
            Text(
                thinking.take(46) + if (thinking.length > 46) "…" else "",
                fontSize = (10 * fontScale).sp,
                color = Color(0xFFA49B8A),
                fontStyle = FontStyle.Italic,
                maxLines = 1
            )
        }
    }
}


// ── 图片查看器（参考原作者 image-viewer）：全屏黑底、缩放拖动、保存到相册、多图切换 ──

@Composable
private fun ChatImageViewer(
    paths: List<String>,
    index: Int,
    onIndex: (Int) -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    var scale by remember { mutableStateOf(1f) }
    var offX by remember { mutableStateOf(0f) }
    var offY by remember { mutableStateOf(0f) }
    Dialog(
        onDismissRequest = onClose,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xF508090B))
        ) {
            val path = paths.getOrNull(index) ?: paths.firstOrNull() ?: return@Box
            val bmp = remember(path) { decodeChatImage(java.io.File(path), 2048) }
            bmp?.let {
                Image(
                    bitmap = it.asImageBitmap(),
                    contentDescription = "查看图片",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            translationX = offX
                            translationY = offY
                        }
                        .pointerInput(path) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                scale = (scale * zoom).coerceIn(1f, 6f)
                                if (scale > 1.02f) {
                                    offX += pan.x
                                    offY += pan.y
                                } else {
                                    offX = 0f; offY = 0f
                                }
                            }
                        }
                )
            }
            // 顶栏：关闭 + 序号
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color(0x88000000), Color.Transparent)))
                    .padding(paddingValues = PaddingValues(horizontal = 16.dp, vertical = 14.dp)),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "✕ 关闭", fontSize = 14.sp, color = Color.White,
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .clickable { onClose() }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
                if (paths.size > 1) {
                    Text("${index + 1} / ${paths.size}", fontSize = 13.sp, color = Color.White.copy(alpha = 0.85f))
                }
            }
            // 底栏：上一张 / 保存到相册 / 下一张
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0x99000000))))
                    .padding(bottom = 22.dp, top = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (paths.size > 1) {
                    Text(
                        "‹ 上一张", fontSize = 13.sp, color = Color.White,
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color(0x33303238))
                            .clickable { if (index > 0) { onIndex(index - 1); scale = 1f; offX = 0f; offY = 0f } }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    )
                }
                Text(
                    "保存到相册", fontSize = 13.sp, color = Color.White,
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0x33303238))
                        .clickable {
                            runCatching {
                                val bmpSave = decodeChatImage(java.io.File(path), 2048) ?: throw IllegalStateException("图片读不出来")
                                val values = android.content.ContentValues().apply {
                                    put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, "aionshome_" + System.currentTimeMillis() + ".jpg")
                                    put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                                }
                                val uri = context.contentResolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                                    ?: throw IllegalStateException("相册拒绝写入")
                                context.contentResolver.openOutputStream(uri)?.use { out ->
                                    bmpSave.compress(android.graphics.Bitmap.CompressFormat.JPEG, 95, out)
                                }
                                Toast.makeText(context, "已保存到相册", Toast.LENGTH_SHORT).show()
                            }.onFailure {
                                Toast.makeText(context, "保存失败：${it.message ?: "未知"}", Toast.LENGTH_SHORT).show()
                            }
                        }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                )
                if (paths.size > 1) {
                    Text(
                        "下一张 ›", fontSize = 13.sp, color = Color.White,
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color(0x33303238))
                            .clickable { if (index < paths.size - 1) { onIndex(index + 1); scale = 1f; offX = 0f; offY = 0f } }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    )
                }
            }
            Text(
                "双指缩放 · 拖动查看",
                fontSize = 11.sp, color = Color.White.copy(alpha = 0.5f),
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(top = 240.dp)
            )
        }
    }
}
