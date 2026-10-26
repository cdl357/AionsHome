package com.aion.chat.compose.data

import android.content.Context
import androidx.compose.runtime.mutableStateOf

/**
 * 样式中心（参考橘瓣样式设置，Yuri：我都要）：
 * - 全部样式状态存 style_settings prefs，rev 戳驱动重组（照 SettingsBg 的模式）
 * - 已接线的项立即生效（如气泡透明度直接写 chat_prefs 的 bubble_alpha）；
 *   其余（颜色/字体/气泡背景图/挂件/圆角）由聊天页与全局玻璃件按 rev 读取接线
 * - 图片资产（气泡背景图/输入框背景/头像挂件）存 filesDir，路径不入仓库
 */
object StyleSettings {

    private const val PREFS = "style_settings"
    private const val KEY = "json"

    private val _rev = mutableStateOf(0)

    /** 读一下这个值即可订阅样式变化（照 SettingsBg.stamp 的惯例）。 */
    val rev: Int get() = _rev.value

    private fun bump(context: Context) {
        _rev.value = _rev.value + 1
    }

    private fun p(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // ── 主题外观 ──

    /** 界面实时渲染（玻璃件实时背景模糊，Haze）。 */
    fun glassBlur(context: Context) = p(context).getBoolean("glass_blur", true)
    fun setGlassBlur(context: Context, v: Boolean) { p(context).edit().putBoolean("glass_blur", v).apply(); bump(context) }

    /** 聊天气泡实时模糊。 */
    fun bubbleBlur(context: Context) = p(context).getBoolean("bubble_blur", false)
    fun setBubbleBlur(context: Context, v: Boolean) { p(context).edit().putBoolean("bubble_blur", v).apply(); bump(context) }

    /** 模糊强度（dp）。 */
    fun blurStrength(context: Context) = p(context).getInt("blur_strength", 4)
    fun setBlurStrength(context: Context, v: Int) { p(context).edit().putInt("blur_strength", v.coerceIn(1, 24)).apply(); bump(context) }

    // ── 透明度 ──

    /** 界面材质不透明度 0.4~1.0（全局玻璃卡）。 */
    fun glassOpacity(context: Context) = p(context).getFloat("glass_opacity", 1f)
    fun setGlassOpacity(context: Context, v: Float) { p(context).edit().putFloat("glass_opacity", v.coerceIn(0.4f, 1f)).apply(); bump(context) }

    /** 聊天气泡透明度 0.3~1.0（镜像 chat_prefs/bubble_alpha，立即可生效）。 */
    fun bubbleOpacity(context: Context) =
        context.getSharedPreferences("chat_prefs", Context.MODE_PRIVATE).getFloat("bubble_alpha", 1f)
    fun setBubbleOpacity(context: Context, v: Float) {
        context.getSharedPreferences("chat_prefs", Context.MODE_PRIVATE)
            .edit().putFloat("bubble_alpha", v.coerceIn(0.3f, 1f)).apply()
        bump(context)
    }

    /** 思维链透明度 0.2~1.0（网关思维链上线后由聊天页读取）。 */
    fun thinkingOpacity(context: Context) = p(context).getFloat("thinking_opacity", 0.6f)
    fun setThinkingOpacity(context: Context, v: Float) { p(context).edit().putFloat("thinking_opacity", v.coerceIn(0.2f, 1f)).apply(); bump(context) }

    // ── 颜色自定义（null = 跟随主题/皮肤） ──

    private fun color(context: Context, key: String): Int? =
        if (p(context).contains(key)) p(context).getInt(key, 0xFF000000.toInt()) else null
    private fun setColor(context: Context, key: String, v: Int?) {
        val e = p(context).edit()
        if (v == null) e.remove(key) else e.putInt(key, v)
        e.apply(); bump(context)
    }

    fun bodyColor(context: Context): Int? = color(context, "c_body")
    fun setBodyColor(context: Context, v: Int?) = setColor(context, "c_body", v)
    fun globalFontColor(context: Context): Int? = color(context, "c_font")
    fun setGlobalFontColor(context: Context, v: Int?) = setColor(context, "c_font", v)
    fun userBubbleColor(context: Context): Int? = color(context, "c_user_bubble")
    fun setUserBubbleColor(context: Context, v: Int?) = setColor(context, "c_user_bubble", v)
    fun aiBubbleColor(context: Context): Int? = color(context, "c_ai_bubble")
    fun setAiBubbleColor(context: Context, v: Int?) = setColor(context, "c_ai_bubble", v)
    fun thinkingBubbleColor(context: Context): Int? = color(context, "c_think_bubble")
    fun setThinkingBubbleColor(context: Context, v: Int?) = setColor(context, "c_think_bubble", v)
    fun chatBgColor(context: Context): Int? = color(context, "c_chat_bg")
    fun setChatBgColor(context: Context, v: Int?) = setColor(context, "c_chat_bg", v)
    fun primaryColor(context: Context): Int? = color(context, "c_primary")
    fun setPrimaryColor(context: Context, v: Int?) = setColor(context, "c_primary", v)
    fun inputBgColor(context: Context): Int? = color(context, "c_input_bg")
    fun setInputBgColor(context: Context, v: Int?) = setColor(context, "c_input_bg", v)

    // ── 消息显示 ──

    fun showUserAvatar(context: Context) = p(context).getBoolean("show_user_avatar", true)
    fun setShowUserAvatar(context: Context, v: Boolean) { p(context).edit().putBoolean("show_user_avatar", v).apply(); bump(context) }
    fun showAiBubble(context: Context) = p(context).getBoolean("show_ai_bubble", true)
    fun setShowAiBubble(context: Context, v: Boolean) { p(context).edit().putBoolean("show_ai_bubble", v).apply(); bump(context) }
    fun showDate(context: Context) = p(context).getBoolean("show_date", true)
    fun setShowDate(context: Context, v: Boolean) { p(context).edit().putBoolean("show_date", v).apply(); bump(context) }
    fun showTokenStats(context: Context) = p(context).getBoolean("show_token_stats", false)
    fun setShowTokenStats(context: Context, v: Boolean) { p(context).edit().putBoolean("show_token_stats", v).apply(); bump(context) }
    fun showThinking(context: Context) = p(context).getBoolean("show_thinking", true)
    fun setShowThinking(context: Context, v: Boolean) { p(context).edit().putBoolean("show_thinking", v).apply(); bump(context) }
    fun autoFoldThinking(context: Context) = p(context).getBoolean("auto_fold_thinking", true)
    fun setAutoFoldThinking(context: Context, v: Boolean) { p(context).edit().putBoolean("auto_fold_thinking", v).apply(); bump(context) }

    /** 0 默认 / 1 衬线体 / 2 等宽字体。 */
    fun fontFamily(context: Context) = p(context).getInt("font_family", 0)
    fun setFontFamily(context: Context, v: Int) { p(context).edit().putInt("font_family", v.coerceIn(0, 2)).apply(); bump(context) }

    /** 聊天字体大小比例 0.8~1.4。 */
    fun fontScale(context: Context) = p(context).getFloat("font_scale", 1f)
    fun setFontScale(context: Context, v: Float) { p(context).edit().putFloat("font_scale", v.coerceIn(0.8f, 1.4f)).apply(); bump(context) }

    /** 思维链字体大小比例 0.8~1.4。 */
    fun thinkingFontScale(context: Context) = p(context).getFloat("think_font_scale", 1f)
    fun setThinkingFontScale(context: Context, v: Float) { p(context).edit().putFloat("think_font_scale", v.coerceIn(0.8f, 1.4f)).apply(); bump(context) }

    // ── 插图素材 ──

    fun bubbleRadius(context: Context) = p(context).getInt("bubble_radius", 16)
    fun setBubbleRadius(context: Context, v: Int) { p(context).edit().putInt("bubble_radius", v.coerceIn(0, 40)).apply(); bump(context) }

    /** 背景图叠加主题色遮罩（关=纯图片）。 */
    fun themeTintOverlay(context: Context) = p(context).getBoolean("theme_tint", false)
    fun setThemeTintOverlay(context: Context, v: Boolean) { p(context).edit().putBoolean("theme_tint", v).apply(); bump(context) }

    private fun file(context: Context, name: String) = java.io.File(context.filesDir, name)

    fun saveImage(context: Context, slot: String, bytes: ByteArray): Boolean = runCatching {
        file(context, "style_$slot").outputStream().use { it.write(bytes) }
        bump(context)
        true
    }.getOrDefault(false)

    fun clearImage(context: Context, slot: String) {
        file(context, "style_$slot").delete()
        bump(context)
    }

    fun hasImage(context: Context, slot: String) = file(context, "style_$slot").exists()
    fun imageFile(context: Context, slot: String) = file(context, "style_$slot")

    /** 槽位常量：用户气泡 / AI气泡 / 输入框 / 用户挂件 / AI挂件。 */
    val SLOT_USER_BUBBLE = "user_bubble.jpg"
    val SLOT_AI_BUBBLE = "ai_bubble.jpg"
    val SLOT_INPUT = "input.jpg"
    val SLOT_USER_PENDANT = "user_pendant.png"
    val SLOT_AI_PENDANT = "ai_pendant.png"
}
