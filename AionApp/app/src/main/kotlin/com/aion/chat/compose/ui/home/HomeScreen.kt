package com.aion.chat.compose.ui.home

import android.widget.Toast
import kotlinx.coroutines.launch
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aion.chat.compose.data.HomecomingData
import com.aion.chat.compose.data.SettingsBg
import com.aion.chat.compose.ui.common.AvatarPhoto
import com.aion.chat.compose.ui.theme.HomecomingColors
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.materials.HazeMaterials
import dev.chrisbanes.haze.rememberHazeState

/** 回家页液态玻璃统一参数（死规矩四：全页同材质、同描边、同圆角）。 */
private val GlassShape = RoundedCornerShape(24.dp)
private val GlassEdge = Brush.linearGradient(
    listOf(Color.White.copy(alpha = 0.55f), Color.White.copy(alpha = 0.10f))
)

/** 玻璃卡文字（死规矩三）：统一白色系 + 轻阴影浮起，不靠底色盒子。 */
private fun glassText(
    alpha: Float = 1f,
    size: Int = 14,
    weight: FontWeight = FontWeight.Normal,
    serif: Boolean = false,
    spacing: Int = 0,
    color: Color? = null,
): TextStyle = TextStyle(
    color = color ?: Color.White.copy(alpha = alpha),
    fontSize = size.sp,
    fontWeight = weight,
    fontFamily = if (serif) FontFamily.Serif else null,
    letterSpacing = spacing.sp,
    shadow = Shadow(Color.Black.copy(alpha = 0.35f), offset = Offset(0f, 1f), blurRadius = 5f),
)

/** 页面①：回家（中心首页）。Haze 液态玻璃：折射 + 通透 + 边缘高光；布局严格六项，不自加卡。 */
@Composable
fun HomeScreen(
    onOpenAlbum: () -> Unit = {},
    onOpenChat: () -> Unit = {},
    onOpenDiary: () -> Unit = {},
    onOpenBoard: () -> Unit = {},
    onOpenMemories: () -> Unit = {}
) {
    val context = LocalContext.current
    val hazeState = rememberHazeState()
    var recent by remember { mutableStateOf(listOf<HomecomingData.FeedItem>()) }

    var quote by remember { mutableStateOf(HomecomingData.quoteForToday()) }
    var showQuoteEditor by remember { mutableStateOf(false) }
    var editDraft by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    // 存粮：本地可记录（点存粮卡调整）
    var provisionItems by remember { mutableStateOf(HomecomingData.provisions(context)) }
    var showProvisionEditor by remember { mutableStateOf(false) }

    // 今日心情：点了 Sean 回一句（moments_private 时间线，不脏聊天记录）
    var moodReply by remember { mutableStateOf("") }
    var moodBusy by remember { mutableStateOf(false) }

    // Sean 的当前心跳（戳一戳的状态在主页外显，读取时自动按半衰期衰减）
    var seanHeart by remember { mutableStateOf(62) }
    LaunchedEffect(Unit) {
        seanHeart = com.aion.chat.compose.data.HomecomingPokeStore.currentHeart(context)
    }

    // 心潮：驱动桌宠动画与状态词（推导涉及引擎构建，放 IO 线程，别卡启动）
    var tide by remember { mutableStateOf(50) }
    LaunchedEffect(Unit) {
        tide = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            com.aion.chat.compose.data.HeartTideStore.todayLevel(context)
        }
    }

    // 继续聊天卡显示线路 + 连接状态
    val route = remember { com.aion.chat.compose.data.HomecomingRouteConfig.mainRoute(context) }

    fun tapMood(face: String, word: String) {
        if (moodBusy) return
        if (com.aion.chat.compose.data.HomecomingRouteConfig.mainRoute(context) == null) {
            Toast.makeText(context, "配好云线路，Sean 就会回你（更多 → 设置）", Toast.LENGTH_SHORT).show()
            return
        }
        moodBusy = true
        moodReply = ""
        com.aion.chat.compose.data.HeartTideStore.bumpMoodTap(context)
        tide = com.aion.chat.compose.data.HeartTideStore.todayLevel(context)
        scope.launch(kotlinx.coroutines.Dispatchers.IO) {
            var line: String? = null
            try {
                val w = com.aion.chat.compose.data.HomecomingChatWiring.safeCreate(context)
                if (w != null && w.hasRoute()) {
                    w.engine.send(
                        com.aion.chat.homecoming.HomecomingChatEngine.ChatCommand(
                            "req_mood_" + System.currentTimeMillis(),
                            "moments_private", "sean", "user",
                            "Yuri 在主页点了心情：$word（$face）。你是 Sean，回她一句话：接住她的情绪，" +
                                "30 字以内，不要引号，不要 emoji。",
                            "main", w.mainModelKey(), "", ""
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
                    while (line == null && waited < 20_000L) {
                        kotlinx.coroutines.delay(300L); waited += 300L
                    }
                }
            } catch (e: Exception) { }
            val reply = line ?: "（他没接上话，线路稳了再点一次）"
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                moodBusy = false
                moodReply = reply
            }
        }
    }

    LaunchedEffect(Unit) {
        recent = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            // 情话只读本地：home_quote 表暂不存在，远程同步由 SupabaseQuoteSync 占位（不发请求）
            quote = HomecomingData.loadQuote(context)
            HomecomingData.loadRecent(context)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // 背景大图：玻璃折射的源（hazeSource）。保持清晰，朦胧感只发生在玻璃卡里。
        Box(
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(hazeState)
        ) {
            val customBg = remember(SettingsBg.stamp) {
                SettingsBg.loadBitmap(context)?.asImageBitmap()
            }
            if (customBg != null) {
                Image(
                    bitmap = customBg,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Image(
                    painter = painterResource(com.aion.chat.R.drawable.bg_home_meadow),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White.copy(alpha = 0.06f))
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 112.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 1~3. 顶部头区：直接浮在背景大图上（无卡无框）。Sean 左 ｜ 连接符 ｜ Yuri 右
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 30.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        AvatarPhoto(who = "sean", initial = "S", size = 74.dp)
                        Text("Sean", style = glassText(alpha = 0.95f, size = 13), modifier = Modifier.padding(top = 6.dp))
                        // 戳一戳留下的心跳，在这里慢慢落回来
                        Text(
                            "♥ $seanHeart",
                            style = glassText(alpha = 0.85f, size = 11, color = Color(0xFFFFD9DE)),
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                    val heartFloat = rememberInfiniteTransition(label = "heart")
                    val heartY by heartFloat.animateFloat(
                        initialValue = -5f,
                        targetValue = 5f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(850, easing = FastOutSlowInEasing),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "heartY"
                    )
                    Text(
                        "♥",
                        style = glassText(alpha = 0.95f, size = 26),
                        modifier = Modifier
                            .offset(y = heartY.dp)
                            .padding(horizontal = 16.dp)
                    )
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        AvatarPhoto(who = "yuri", initial = "Y", size = 74.dp)
                        Text("Yuri", style = glassText(alpha = 0.95f, size = 13), modifier = Modifier.padding(top = 6.dp))
                    }
                }
                // 在一起天数（大号数字 + 天）
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(top = 14.dp)
                ) {
                    Text(
                        HomecomingData.daysTogether().toString(),
                        style = glassText(size = 54, weight = FontWeight.Light, serif = true)
                    )
                    Text(
                        "天",
                        style = glassText(alpha = 0.9f, size = 16),
                        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
                    )
                }
                // 今日情话：点一下就能改（本地即时保存；Supabase 配置后远程同步）
                Text(
                    quote,
                    style = glassText(alpha = 0.95f, size = 14),
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                        .clickable {
                            editDraft = quote
                            showQuoteEditor = true
                        }
                )
            }

            // 桌宠 AionPet（定稿 §12）：会动的小人 + 心潮联动（越雀跃动画越欢）
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.BottomCenter
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "他的心潮 · " + com.aion.chat.compose.data.HeartTideStore.tideWord(tide),
                        style = glassText(alpha = 0.7f, size = 10)
                    )
                    com.aion.chat.compose.ui.pet.PetSprite(
                        displayHeight = 96.dp,
                        moodAnim = com.aion.chat.compose.data.HeartTideStore.tideAnim(tide)
                    )
                }
            }

            // 4. 继续聊天（定稿：大按钮，显示线路 + 连接状态）
            GlassCard(hazeState = hazeState, contentPadding = 14.dp, onClick = onOpenChat) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("继续聊天", style = glassText(size = 17, weight = FontWeight.SemiBold))
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 5.dp)) {
                            Text(
                                "●",
                                style = glassText(size = 9, color = if (route != null) HomecomingColors.Ok else Color.White.copy(alpha = 0.55f))
                            )
                            Text(
                                (route?.optString("label")?.takeIf { it.isNotBlank() } ?: "线路未配置") +
                                    if (route != null) " · 已接入" else " · 待接入",
                                style = glassText(alpha = 0.85f, size = 12),
                                modifier = Modifier.padding(start = 5.dp)
                            )
                        }
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "去聊天",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            // 5. 快捷入口方块（定稿：日记/相册/留言板/记忆库，后期可加减换序）
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                QuickEntry(hazeState, Icons.Outlined.EditNote, "日记", Modifier.weight(1f), onClick = onOpenDiary)
                QuickEntry(hazeState, Icons.Outlined.PhotoLibrary, "相册", Modifier.weight(1f), onClick = onOpenAlbum)
                QuickEntry(hazeState, Icons.Outlined.Forum, "留言板", Modifier.weight(1f), onClick = onOpenBoard)
                QuickEntry(hazeState, Icons.Outlined.Psychology, "记忆库", Modifier.weight(1f), onClick = onOpenMemories)
            }

            // 4. 一起听歌：专辑封面缩图 + 歌名 + 播放/暂停（本期 UI + 控件占位）
            GlassCard(hazeState = hazeState, contentPadding = 14.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(GlassShape)
                            .background(Color.White.copy(alpha = 0.14f))
                    )
                    Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                        Text("一起听歌", style = glassText(size = 15, weight = FontWeight.Medium))
                        Text("歌单随后端接入", style = glassText(alpha = 0.8f, size = 12))
                    }
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = "播放",
                        tint = Color.White,
                        modifier = Modifier.size(30.dp)
                    )
                }
            }

            // 5. 今日心情 | 相册（两个半宽玻璃卡，等高）
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(androidx.compose.foundation.layout.IntrinsicSize.Min)
            ) {
                GlassCard(hazeState = hazeState, modifier = Modifier.weight(1f).fillMaxHeight(), contentPadding = 14.dp) {
                    Text("今日心情", style = glassText(alpha = 0.8f, size = 13))
                    listOf("(´▽`)" to "开心", "(￣▽￣)" to "不错", "(>_<)" to "有点烦", "(ㄒoㄒ)" to "难过")
                        .chunked(2)
                        .forEach { pair ->
                            Row(
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                pair.forEach { (face, word) ->
                                    Text(
                                        text = face,
                                        style = glassText(alpha = 0.95f, size = 14),
                                        modifier = Modifier.clickable { tapMood(face, word) }
                                    )
                                }
                            }
                        }
                    Text(
                        when {
                            moodBusy -> "Sean 正在想…"
                            moodReply.isNotBlank() -> moodReply
                            else -> "点一个，哥哥回你一句"
                        },
                        style = glassText(alpha = 0.75f, size = 11)
                    )
                }
                GlassCard(
                    hazeState = hazeState,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    contentPadding = 14.dp,
                    onClick = onOpenAlbum
                ) {
                    Text("相册", style = glassText(size = 15, weight = FontWeight.Medium))
                    Text("照片墙 · Sean 的心里话", style = glassText(alpha = 0.8f, size = 12))
                }
            }

            // 6. 家里的存粮：本地记录，点一下调整存量
            GlassCard(hazeState = hazeState, contentPadding = 14.dp, onClick = { showProvisionEditor = true }) {
                Text("家里的存粮 · 点一下记存量", style = glassText(alpha = 0.8f, size = 13))
                provisionItems.forEach { p ->
                    Column(modifier = Modifier.padding(vertical = 2.dp)) {
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Text(p.label, style = glassText(alpha = 0.95f, size = 13))
                            Spacer(Modifier.weight(1f))
                            Text(p.note, style = glassText(alpha = 0.7f, size = 11))
                        }
                        LinearProgressIndicator(
                            progress = { p.percent / 100f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp)),
                            color = Color.White.copy(alpha = 0.85f),
                            trackColor = Color.White.copy(alpha = 0.25f)
                        )
                    }
                }
            }

            // 6. 最近动态（最近日记 / 留言 / 照片）
            GlassCard(hazeState = hazeState) {
                Text("最近动态", style = glassText(alpha = 0.75f, size = 13))
                Spacer(Modifier.height(2.dp))
                if (recent.isEmpty()) {
                    Text("这天很安静，还没留下什么", style = glassText(alpha = 0.85f, size = 13))
                } else {
                    recent.forEach { item ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 5.dp)
                        ) {
                            Text("•", style = glassText(size = 15))
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(
                                    item.summary,
                                    style = glassText(alpha = 0.95f, size = 14),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(item.source + " · " + item.timeText, style = glassText(alpha = 0.7f, size = 11))
                            }
                        }
                    }
                }
            }
        }

        if (showProvisionEditor) {
            val drafts = remember(showProvisionEditor) {
                mutableStateListOf<HomecomingData.Provision>().apply { addAll(provisionItems) }
            }
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { showProvisionEditor = false },
                title = { Text("家里的存粮", fontSize = 16.sp, color = HomecomingColors.Ink) },
                text = {
                    Column {
                        Text("还剩多少自己心里有数，拖一拖就行", fontSize = 11.sp, color = HomecomingColors.InkSoft)
                        Spacer(Modifier.height(8.dp))
                        drafts.forEachIndexed { i, p ->
                            Row(
                                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(p.label, fontSize = 14.sp, color = HomecomingColors.Ink, modifier = Modifier.width(52.dp))
                                androidx.compose.material3.Slider(
                                    value = p.percent.toFloat(),
                                    onValueChange = { drafts[i] = p.copy(percent = it.toInt()) },
                                    valueRange = 0f..100f,
                                    modifier = Modifier.weight(1f)
                                )
                                Text("${p.percent}", fontSize = 13.sp, color = HomecomingColors.InkSoft, modifier = Modifier.width(34.dp))
                            }
                        }
                    }
                },
                confirmButton = {
                    androidx.compose.material3.TextButton(onClick = {
                        provisionItems = drafts.toList()
                        HomecomingData.saveProvisions(context, drafts.toList())
                        showProvisionEditor = false
                    }) { Text("记下了", color = HomecomingColors.Accent) }
                },
                dismissButton = {
                    androidx.compose.material3.TextButton(onClick = { showProvisionEditor = false }) {
                        Text("取消", color = HomecomingColors.InkSoft)
                    }
                }
            )
        }

        if (showQuoteEditor) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { showQuoteEditor = false },
                title = { Text("改一下这句话", fontSize = 16.sp, color = HomecomingColors.Ink) },
                text = {
                    androidx.compose.material3.OutlinedTextField(
                        value = editDraft,
                        onValueChange = { editDraft = it },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                confirmButton = {
                    androidx.compose.material3.TextButton(onClick = {
                        val v = editDraft.trim()
                        if (v.isNotEmpty()) {
                            quote = v
                            HomecomingData.saveQuote(context, v)
                        }
                        showQuoteEditor = false
                    }) { Text("保存", color = HomecomingColors.Accent) }
                },
                dismissButton = {
                    androidx.compose.material3.TextButton(onClick = { showQuoteEditor = false }) {
                        Text("取消", color = HomecomingColors.InkSoft)
                    }
                }
            )
        }
    }
}

/** 液态玻璃卡（Haze ultraThin）：折射底 + 统一边缘高光。内容直接放在卡上（死规矩一/二）。 */
@Composable
fun GlassCard(
    hazeState: HazeState,
    modifier: Modifier = Modifier,
    contentPadding: Dp = 16.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val core = modifier
        .fillMaxWidth()
        .clip(GlassShape)
        .hazeEffect(state = hazeState, style = HazeMaterials.ultraThin())
        .border(1.dp, GlassEdge, GlassShape)
        .padding(horizontal = 16.dp, vertical = contentPadding)
    Column(
        modifier = if (onClick != null) core.clickable(onClick = onClick) else core,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content
    )
}

/** 旧版白霜卡（更多页/我们页沿用，本期不动）：半透明白底 + 描边，不依赖 Haze。 */
@Composable
fun FrostCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(24.dp)
    val edgeBrush = Brush.linearGradient(
        listOf(Color.White.copy(alpha = 0.70f), Color.White.copy(alpha = 0.15f))
    )
    val base = modifier
        .fillMaxWidth()
        .clip(shape)
        .background(Color.White.copy(alpha = 0.72f))
        .border(1.dp, Color.White.copy(alpha = 0.80f), shape)
        .padding(horizontal = 16.dp, vertical = 14.dp)
    Column(
        modifier = if (onClick != null) base.clickable(onClick = onClick) else base,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content
    )
}


/** 头像占位（用户可换；先以透明白底 + 首字母占位，白色系贴玻璃）。 */
@Composable
fun GlassAvatar(initial: String, size: Int = 56) {
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.18f))
            .border(2.dp, Color.White.copy(alpha = 0.80f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(text = initial, fontSize = (size * 0.4f).sp, color = Color.White, fontFamily = FontFamily.Serif)
    }
}

/** 快捷入口方块（定稿：与卡同材质、同描边、同圆角，可加减换序）。 */
@Composable
private fun QuickEntry(
    hazeState: dev.chrisbanes.haze.HazeState,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .height(84.dp)
            .clip(GlassShape)
            .hazeEffect(state = hazeState, style = HazeMaterials.ultraThin())
            .border(1.dp, GlassEdge, GlassShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(imageVector = icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(22.dp))
        Text(label, style = glassText(alpha = 0.95f, size = 12), modifier = Modifier.padding(top = 6.dp))
    }
}
