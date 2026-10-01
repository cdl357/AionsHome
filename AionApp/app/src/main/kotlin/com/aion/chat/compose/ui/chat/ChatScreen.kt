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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.outlined.Palette
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
import com.aion.chat.compose.ui.theme.HomecomingColors
import com.aion.chat.homecoming.HomecomingChatEngine
import com.aion.chat.homecoming.HomecomingChatRepository
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 页面③：聊天。接 HomecomingChatEngine 真实链路；分条冒泡 + 气泡皮肤 + 背景可换。 */
@Composable
fun ChatScreen() {
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

    // ── 背景：聊天专属图，无则跟随全局背景图 ──
    val bgStamp = SettingsBg.stamp
    val bgBitmap = remember(bgStamp) { SettingsBg.loadChatBitmap(context)?.asImageBitmap() }

    // ── 消息状态 ──
    val messages = remember { mutableStateListOf<HomecomingChatRepository.Message>() }
    val pendingSegments = remember { mutableStateListOf<String>() }
    val sending = remember { mutableStateOf(false) }
    val input = remember { mutableStateOf("") }
    val pendingImage = remember { mutableStateOf("") }
    val errorText = remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()

    fun reloadNow() {
        val w = wiring ?: return
        try {
            val list = w.listMessages(HomecomingChatWiring.TIMELINE)
            main.post {
                messages.clear()
                messages.addAll(list)
            }
        } catch (e: Exception) { /* 首次库为空保持安静 */ }
    }

    // ── 发送：引擎 commitUser + 完成后分条冒泡 ──
    fun send() {
        val text = input.value.trim()
        val image = pendingImage.value
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
                            scope.launch(Dispatchers.IO) { reloadNow() }
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

    // ── 图片选择（加号） ──
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                val dataUrl = runCatching {
                    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: return@runCatching null
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
                    "data:image/jpeg;base64," + Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
                }.getOrNull()
                main.post {
                    if (dataUrl != null) pendingImage.value = dataUrl
                    else Toast.makeText(context, "图片读取失败", Toast.LENGTH_SHORT).show()
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
                    GlassAvatar(initial = "S", size = 42)
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
                        imageVector = Icons.Filled.Phone,
                        contentDescription = "语音通话",
                        tint = HomecomingColors.Ink,
                        modifier = Modifier
                            .size(22.dp)
                            .clickable { Toast.makeText(context, "语音通话在阶段五接线", Toast.LENGTH_SHORT).show() }
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
                            UserBubbles(text = msg.text, hasImage = msg.attachmentKind == "image", skin = skin.value)
                        } else {
                            AssistantBubbles(text = msg.text, skin = skin.value)
                        }
                    }
                    pendingSegments.forEachIndexed { index, seg ->
                        item(key = "pending_$index") {
                            AssistantBubbleSingle(text = seg, skin = skin.value)
                        }
                    }
                }

                // ── 底部输入栏 ──
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .imePadding()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.85f))
                            .clickable {
                                imagePicker.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Filled.Add, contentDescription = "发图片", tint = HomecomingColors.Ink) }
                    OutlinedTextField(
                        value = input.value,
                        onValueChange = { input.value = it },
                        modifier = Modifier.weight(1f),
                        placeholder = {
                            Text("和 Sean 说点什么…", fontSize = 14.sp, color = HomecomingColors.InkSoft)
                        },
                        shape = RoundedCornerShape(14.dp),
                        maxLines = 4
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
fun UserBubbles(text: String, hasImage: Boolean, skin: Int) {
    val segments = splitBubbles(text)
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalAlignment = Alignment.End
    ) {
        if (hasImage) {
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
        segments.forEach { seg ->
            val style = userStyle(skin)
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
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

@Composable
private fun AssistantBubbleSingle(text: String, skin: Int) {
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
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

