package com.aion.chat.compose.ui.theme

import android.content.Context
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.graphics.Color

/**
 * 分页面的底色 + 透明度（设置里可逐页调）。
 *
 * 背景：全局背景图（SettingsBg.home_bg）铺在 HomecomingApp 最底层，但朋友圈/我们/相册
 * 这些页各自铺了不透明底色把它盖住了，所以「背景通用」看起来失效。这里把每页的那层底色
 * 抽成「颜色 + 透明度」两个可调量：alpha=0 就是完全透出背景图，alpha=1 是纯色不透。
 *
 * 状态用 SnapshotStateMap，改完即刻全局重组，不用重启。
 * 持久化：SharedPreferences "surface_prefs"，key 形如 color_moments / alpha_moments。
 */
object SurfaceStyleState {

    /** 一个可调区域。default* 是改之前各页原本写死的值，保证「不调就跟原来一样」。 */
    data class Scope(
        val key: String,
        val label: String,
        val defaultColor: Color,
        val defaultAlpha: Float
    )

    val SCOPES = listOf(
        Scope("home", "回家页", Color.White, 0.06f),
        Scope("chat", "聊天页", Color.White, 0.12f),
        // 朋友圈原本是整屏纯白（把全局背景图完全盖住），默认改成微白蒙层露出背景
        Scope("moments", "朋友圈", Color.White, 0.10f),
        Scope("us", "我们", Color.White, 0.00f),
        Scope("album", "相册", Color.White, 0.00f),
        Scope("card", "玻璃卡片", Color.White, 0.72f)
    )

    /** 调色盘：浅色为主，跟五套主题搭得上。 */
    val SWATCHES = listOf(
        "白" to Color.White,
        "米" to Color(0xFFFAF7F0),
        "杏" to Color(0xFFF0EDE6),
        "樱" to Color(0xFFF7E5E9),
        "雾" to Color(0xFFCEF4F5),
        "绿" to Color(0xFFDFF2E8),
        "藕" to Color(0xFFEAE4F4),
        "茶" to Color(0xFFF2E6D8),
        "墨" to Color(0xFF2C4A5A),
        "夜" to Color(0xFF17262B)
    )

    private const val PREFS = "surface_prefs"

    private val colors = mutableStateMapOf<String, Int>()
    private val alphas = mutableStateMapOf<String, Float>()

    private fun scope(key: String): Scope? = SCOPES.firstOrNull { it.key == key }

    /** 启动时恢复（同步读，避免首帧闪一下默认色）。 */
    fun load(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        SCOPES.forEach { s ->
            if (prefs.contains("color_" + s.key)) {
                colors[s.key] = prefs.getInt("color_" + s.key, s.defaultColor.value.toInt())
            }
            if (prefs.contains("alpha_" + s.key)) {
                alphas[s.key] = prefs.getFloat("alpha_" + s.key, s.defaultAlpha)
            }
        }
    }

    /** 这一页当前的底色（不含透明度）。 */
    fun baseColor(key: String): Color {
        val s = scope(key) ?: return Color.White
        val stored = colors[key] ?: return s.defaultColor
        return Color(stored.toLong() and 0xFFFFFFFFL)
    }

    /** 这一页当前的透明度，0=全透（直接看见背景图），1=纯色。 */
    fun alpha(key: String): Float {
        val s = scope(key) ?: return 0f
        return alphas[key] ?: s.defaultAlpha
    }

    /** 直接拿去 .background(...) 的颜色：底色 + 透明度。 */
    fun overlay(key: String): Color = baseColor(key).copy(alpha = alpha(key))

    fun setColor(context: Context, key: String, color: Color) {
        val argb = color.value.toInt()
        colors[key] = argb
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putInt("color_" + key, argb).apply()
    }

    fun setAlpha(context: Context, key: String, value: Float) {
        val a = value.coerceIn(0f, 1f)
        alphas[key] = a
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putFloat("alpha_" + key, a).apply()
    }

    /** 还原这一页到出厂值。 */
    fun reset(context: Context, key: String) {
        colors.remove(key)
        alphas.remove(key)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().remove("color_" + key).remove("alpha_" + key).apply()
    }
}
