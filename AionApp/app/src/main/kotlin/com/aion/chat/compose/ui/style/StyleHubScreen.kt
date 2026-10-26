package com.aion.chat.compose.ui.style

import android.graphics.BitmapFactory
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.aion.chat.compose.data.StyleSettings
import com.aion.chat.compose.ui.theme.HomecomingColors
import kotlin.math.roundToInt

/**
 * 样式中心（参考橘瓣样式设置，Yuri：我都要）：
 * 主题外观 / 透明度设置 / 颜色自定义 / 消息显示 / 插图素材，五大分区全部落库。
 * 气泡透明度即时生效（写 chat_prefs）；其余项由聊天页/全局玻璃件按 rev 接线读取。
 */
@Composable
fun StyleHubScreen(onBack: () -> Unit = {}) {
    val context = LocalContext.current
    val rev = StyleSettings.rev   // 订阅：任何样式变更触发整页刷新
    var pickColorFor by remember { mutableStateOf<((Int?) -> Unit)?>(null) }
    var colorDialogTitle by remember { mutableStateOf("") }
    var colorDialogInitial by remember { mutableStateOf<Int?>(null) }

    val ink = Color(0xFF4E4638)
    val soft = Color(0xFF9A8F7E)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.55f))
                    .border(1.dp, Color.White.copy(alpha = 0.7f), CircleShape)
                    .clickable { onBack() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回",
                    tint = ink, modifier = Modifier.size(19.dp)
                )
            }
            Column(modifier = Modifier.weight(1f).padding(start = 10.dp)) {
                Text("样式中心", fontSize = 19.sp, color = ink, fontWeight = FontWeight.Medium)
                Text("聊天长什么样，这里说了算", fontSize = 10.sp, color = soft)
            }
        }

        // ══════ 主题外观 ══════
        SectionTitle("主题外观")
        Card {
            SwitchRow("界面实时渲染", "为玻璃界面启用实时背景渲染", StyleSettings.glassBlur(context)) {
                StyleSettings.setGlassBlur(context, it)
            }
            SwitchRow("聊天气泡实时模糊", "为普通聊天气泡实时渲染背景模糊", StyleSettings.bubbleBlur(context)) {
                StyleSettings.setBubbleBlur(context, it)
            }
            SliderRow("模糊强度", StyleSettings.blurStrength(context).toFloat(), 1f, 24f) { v ->
                StyleSettings.setBlurStrength(context, v.roundToInt())
            }
            Text(
                "${StyleSettings.blurStrength(context)} dp",
                fontSize = 11.sp, color = soft, modifier = Modifier.padding(start = 14.dp, bottom = 8.dp)
            )
        }

        // ══════ 透明度设置 ══════
        SectionTitle("透明度设置")
        Card {
            SliderRow("界面材质不透明度", StyleSettings.glassOpacity(context) * 100, 40f, 100f) { v ->
                StyleSettings.setGlassOpacity(context, v / 100f)
            }
            Percent(StyleSettings.glassOpacity(context))
            SliderRow("聊天气泡透明度", StyleSettings.bubbleOpacity(context) * 100, 30f, 100f) { v ->
                StyleSettings.setBubbleOpacity(context, v / 100f)
            }
            Percent(StyleSettings.bubbleOpacity(context))
            SliderRow("思维链透明度", StyleSettings.thinkingOpacity(context) * 100, 20f, 100f) { v ->
                StyleSettings.setThinkingOpacity(context, v / 100f)
            }
            Percent(StyleSettings.thinkingOpacity(context))
        }

        // ══════ 颜色自定义 ══════
        SectionTitle("颜色自定义")
        Card {
            ColorRow("聊天正文颜色", StyleSettings.bodyColor(context), onOpen = { i -> colorDialogTitle = "聊天正文颜色"; colorDialogInitial = i; pickColorFor = { v -> StyleSettings.setBodyColor(context, v) } }, onReset = { StyleSettings.setBodyColor(context, null) })
            ColorRow("全局字体颜色", StyleSettings.globalFontColor(context), onOpen = { i -> colorDialogTitle = "全局字体颜色"; colorDialogInitial = i; pickColorFor = { v -> StyleSettings.setGlobalFontColor(context, v) } }, onReset = { StyleSettings.setGlobalFontColor(context, null) })
            ColorRow("用户气泡颜色", StyleSettings.userBubbleColor(context), onOpen = { i -> colorDialogTitle = "用户气泡颜色"; colorDialogInitial = i; pickColorFor = { v -> StyleSettings.setUserBubbleColor(context, v) } }, onReset = { StyleSettings.setUserBubbleColor(context, null) })
            ColorRow("AI气泡颜色", StyleSettings.aiBubbleColor(context), onOpen = { i -> colorDialogTitle = "AI气泡颜色"; colorDialogInitial = i; pickColorFor = { v -> StyleSettings.setAiBubbleColor(context, v) } }, onReset = { StyleSettings.setAiBubbleColor(context, null) })
            ColorRow("思维链气泡颜色", StyleSettings.thinkingBubbleColor(context), onOpen = { i -> colorDialogTitle = "思维链气泡颜色"; colorDialogInitial = i; pickColorFor = { v -> StyleSettings.setThinkingBubbleColor(context, v) } }, onReset = { StyleSettings.setThinkingBubbleColor(context, null) })
            ColorRow("聊天背景色", StyleSettings.chatBgColor(context), onOpen = { i -> colorDialogTitle = "聊天背景色"; colorDialogInitial = i; pickColorFor = { v -> StyleSettings.setChatBgColor(context, v) } }, onReset = { StyleSettings.setChatBgColor(context, null) })
            ColorRow("主色调（按钮/链接）", StyleSettings.primaryColor(context), onOpen = { i -> colorDialogTitle = "主色调（按钮/链接）"; colorDialogInitial = i; pickColorFor = { v -> StyleSettings.setPrimaryColor(context, v) } }, onReset = { StyleSettings.setPrimaryColor(context, null) })
            ColorRow("输入框背景颜色", StyleSettings.inputBgColor(context), onOpen = { i -> colorDialogTitle = "输入框背景颜色"; colorDialogInitial = i; pickColorFor = { v -> StyleSettings.setInputBgColor(context, v) } }, onReset = { StyleSettings.setInputBgColor(context, null) })
        }

        // ══════ 消息显示 ══════
        SectionTitle("消息显示")
        Card {
            SwitchRow("显示用户头像", "是否在聊天消息中显示用户头像", StyleSettings.showUserAvatar(context)) {
                StyleSettings.setShowUserAvatar(context, it)
            }
            SwitchRow("显示助手消息气泡", "是否为助手消息显示气泡背景", StyleSettings.showAiBubble(context)) {
                StyleSettings.setShowAiBubble(context, it)
            }
            SwitchRow("显示昵称下方日期", "在昵称下方显示消息日期", StyleSettings.showDate(context)) {
                StyleSettings.setShowDate(context, it)
            }
            SwitchRow("显示Token和上下文统计", "显示token用量和消息数量", StyleSettings.showTokenStats(context)) {
                StyleSettings.setShowTokenStats(context, it)
            }
            SwitchRow("显示思考内容", "是否在生成过程中默认展开并显示思考过程", StyleSettings.showThinking(context)) {
                StyleSettings.setShowThinking(context, it)
            }
            SwitchRow("自动折叠思考", "思考完成自动折叠思考内容", StyleSettings.autoFoldThinking(context)) {
                StyleSettings.setAutoFoldThinking(context, it)
            }
            Spacer(Modifier.height(8.dp))
            Text("聊天字体", fontSize = 14.sp, color = ink)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 6.dp, bottom = 4.dp)
            ) {
                listOf("默认" to 0, "衬线体" to 1, "等宽字体" to 2).forEach { (name, idx) ->
                    val sel = StyleSettings.fontFamily(context) == idx
                    Text(
                        name,
                        fontSize = 13.sp,
                        color = if (sel) Color.White else ink,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(if (sel) HomecomingColors.Accent else Color.White.copy(alpha = 0.8f))
                            .clickable { StyleSettings.setFontFamily(context, idx) }
                            .padding(horizontal = 16.dp, vertical = 7.dp)
                    )
                }
            }
            SliderRow("聊天字体大小", StyleSettings.fontScale(context) * 100, 80f, 140f) { v ->
                StyleSettings.setFontScale(context, v / 100f)
            }
            Percent(StyleSettings.fontScale(context))
            // 实时预览句（橘瓣同款）
            Text(
                "凌晨四点，看到海棠花未眠。我常常这样，在无人的夜里独自醒着。",
                fontSize = (13 * StyleSettings.fontScale(context)).sp,
                lineHeight = (21 * StyleSettings.fontScale(context)).sp,
                color = ink,
                modifier = Modifier.padding(start = 14.dp, bottom = 8.dp, end = 14.dp)
            )
            SliderRow("思维链字体大小", StyleSettings.thinkingFontScale(context) * 100, 80f, 140f) { v ->
                StyleSettings.setThinkingFontScale(context, v / 100f)
            }
            Percent(StyleSettings.thinkingFontScale(context))
        }

        // ══════ 插图素材 ══════
        SectionTitle("插图素材")
        Card {
            ImageRow("用户气泡背景图", "选择一张图片作为用户气泡背景", StyleSettings.SLOT_USER_BUBBLE)
            ImageRow("AI气泡背景图", "选择一张图片作为AI气泡背景", StyleSettings.SLOT_AI_BUBBLE)
            ImageRow("输入框背景图", "选择一张图片作为输入框区域背景", StyleSettings.SLOT_INPUT)
            SliderRow("气泡圆角", StyleSettings.bubbleRadius(context).toFloat(), 0f, 40f) { v ->
                StyleSettings.setBubbleRadius(context, v.roundToInt())
            }
            Text(
                "${StyleSettings.bubbleRadius(context)} dp（0=直角）",
                fontSize = 11.sp, color = soft, modifier = Modifier.padding(start = 14.dp, bottom = 8.dp)
            )
            SwitchRow("背景图叠加主题色遮罩", "关=纯图片，开=图片+主题色遮罩", StyleSettings.themeTintOverlay(context)) {
                StyleSettings.setThemeTintOverlay(context, it)
            }
            ImageRow("用户头像挂件", "选择一张图片作为头像装饰框", StyleSettings.SLOT_USER_PENDANT)
            ImageRow("AI头像挂件", "选择一张图片作为AI头像装饰框", StyleSettings.SLOT_AI_PENDANT)
        }

        Text(
            "颜色 / 字体 / 圆角 / 气泡背景图会在下一次进入聊天页时生效；气泡透明度即时生效。" +
                "深色模式与自定义字体文件在后面的版本。",
            fontSize = 10.sp, color = soft, lineHeight = 15.sp,
            modifier = Modifier.padding(vertical = 14.dp)
        )
        Spacer(Modifier.height(100.dp))
    }

    // 取色弹窗
    if (pickColorFor != null) {
        ColorPickerDialog(
            title = colorDialogTitle,
            initial = colorDialogInitial,
            onPick = { v ->
                pickColorFor?.invoke(v)
                pickColorFor = null
            },
            onDismiss = { pickColorFor = null }
        )
    }
    // 抑制未用警告的占位（rev 已通过读取触发重组）
    @Suppress("UNUSED_EXPRESSION")
    rev
}

// ── 分区组件 ──

@Composable
private fun SectionTitle(t: String) {
    Text(
        t, fontSize = 12.sp, color = Color(0xFFB3CDE8),
        modifier = Modifier.padding(top = 16.dp, bottom = 6.dp, start = 4.dp)
    )
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.75f))
            .border(1.dp, Color.White.copy(alpha = 0.8f), RoundedCornerShape(16.dp))
            .padding(vertical = 4.dp)
    ) { content() }
}

@Composable
private fun SwitchRow(title: String, sub: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp, color = Color(0xFF4E4638))
            Text(sub, fontSize = 11.sp, color = Color(0xFF9A8F7E))
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun SliderRow(title: String, value: Float, from: Float, to: Float, onChange: (Float) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp)) {
        Text(title, fontSize = 14.sp, color = Color(0xFF4E4638))
        Slider(value = value, onValueChange = onChange, valueRange = from..to)
    }
}

@Composable
private fun Percent(v: Float) {
    Text(
        "${v.roundToInt()}%", fontSize = 11.sp, color = Color(0xFF9A8F7E),
        modifier = Modifier.padding(start = 14.dp, bottom = 4.dp)
    )
}

@Composable
private fun ColorRow(title: String, current: Int?, onOpen: (Int?) -> Unit, onReset: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 7.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp, color = Color(0xFF4E4638))
        }
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(Color(current ?: 0xFF4E4638.toInt()))
                .border(1.dp, Color(0x33000000), CircleShape)
        )
        Spacer(Modifier.size(10.dp))
        Text(
            "自定义", fontSize = 12.sp, color = Color(0xFF7FA8D9),
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable { onOpen(current) }
                .padding(horizontal = 6.dp, vertical = 4.dp)
        )
        Text(
            "重置", fontSize = 12.sp, color = Color(0xFF7FA8D9),
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable { onReset() }
                .padding(horizontal = 6.dp, vertical = 4.dp)
        )
    }
}


@Composable
private fun ImageRow(title: String, sub: String, slot: String) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    StyleSettings.saveImage(context, slot, input.readBytes())
                }
            }
        }
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        // 当前图预览（有则显示）
        if (StyleSettings.hasImage(context, slot)) {
            val bmp = remember(slot, StyleSettings.rev) {
                runCatching { BitmapFactory.decodeFile(StyleSettings.imageFile(context, slot).absolutePath) }.getOrNull()
            }
            bmp?.let {
                Image(
                    bitmap = it.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .border(1.dp, Color(0x33000000), RoundedCornerShape(8.dp))
                )
                Spacer(Modifier.size(10.dp))
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp, color = Color(0xFF4E4638))
            Text(sub, fontSize = 11.sp, color = Color(0xFF9A8F7E))
        }
        Text(
            "选择图片", fontSize = 12.sp, color = Color(0xFF7FA8D9),
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable {
                    launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
                .padding(horizontal = 6.dp, vertical = 4.dp)
        )
        if (StyleSettings.hasImage(context, slot)) {
            Text(
                "清除", fontSize = 12.sp, color = Color(0xFFC97B7B),
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { StyleSettings.clearImage(context, slot) }
                    .padding(horizontal = 6.dp, vertical = 4.dp)
            )
        }
    }
}

