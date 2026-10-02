package com.aion.chat.compose.ui.theme

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import kotlin.math.roundToInt

/**
 * 全局主题（布局定稿·方案 C）：五套现成主题一键换色 + 单主色调色盘自动配。
 * 颜色令牌全部是 Compose 状态——切主题即刻全局生效，无需重启。
 * 持久化：SharedPreferences "theme_prefs"。暖橙区（15°~75°）自动压饱和，落实「无橘色」。
 */
object HomecomingColors {
    var IceBlue by mutableStateOf(Color(0xFFBCECEF))
    var IceBlueLight by mutableStateOf(Color(0xFFCEF4F5))
    var IceBlueDeep by mutableStateOf(Color(0xFF9AD6DC))
    var Accent by mutableStateOf(Color(0xFF5B9AA8))
    var AccentSoft by mutableStateOf(Color(0x335B9AA8))
    var Ink by mutableStateOf(Color(0xFF1A3244))
    var InkSoft by mutableStateOf(Color(0xFF4A6274))
    var Ok by mutableStateOf(Color(0xFF6FBF9A))
    var Danger by mutableStateOf(Color(0xFFD98A8A))

    // 玻璃层常量（白色系，与主题无关）
    const val GLASS_FILL_ALPHA = 0.55f
    const val GLASS_BORDER_ALPHA = 0.90f
}

@Composable
fun HomecomingTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = HomecomingColors.Accent,
            onPrimary = Color.White,
            secondary = HomecomingColors.IceBlueDeep,
            background = HomecomingColors.IceBlueLight,
            surface = Color.White,
            onBackground = HomecomingColors.Ink,
            onSurface = HomecomingColors.Ink,
        ),
        content = content,
    )
}

/** 主题预设与派生（布局定稿：樱粉/雾蓝/墨绿/藕荷/奶茶，无橘色）。 */
object HomecomingThemeState {

    data class Palette(
        val key: String,
        val name: String,
        val accent: Color,
        val iceBlue: Color,
        val iceBlueLight: Color,
        val iceBlueDeep: Color,
        val ink: Color,
        val inkSoft: Color
    )

    val PRESETS = listOf(
        Palette("sakura", "樱粉", Color(0xFFC97B8F), Color(0xFFF2D7DD), Color(0xFFF7E5E9), Color(0xFFE0AEBB), Color(0xFF4A3A40), Color(0xFF8A7880)),
        Palette("wulan", "雾蓝", Color(0xFF5B9AA8), Color(0xFFBCECEF), Color(0xFFCEF4F5), Color(0xFF9AD6DC), Color(0xFF2C4A5A), Color(0xFF5E7386)),
        Palette("molyu", "墨绿", Color(0xFF5F8F7A), Color(0xFFCDE8DC), Color(0xFFDFF2E8), Color(0xFF8FBFA8), Color(0xFF2C4A3E), Color(0xFF638071)),
        Palette("ouhe", "藕荷", Color(0xFF9B8AB8), Color(0xFFE0D9EE), Color(0xFFEAE4F4), Color(0xFFB9A9D4), Color(0xFF453D5C), Color(0xFF7D7492)),
        Palette("naicha", "奶茶", Color(0xFFA98A63), Color(0xFFEAD9C5), Color(0xFFF2E6D8), Color(0xFFC9A87E), Color(0xFF4A3E30), Color(0xFF8A7A66))
    )

    private const val PREFS = "theme_prefs"
    private const val KEY_MODE = "theme_mode"        // preset:<key> | custom
    private const val KEY_HUE = "theme_custom_hue"   // 0..360

    /** 应用启动时恢复上次选择（同步读 SharedPreferences，避免闪烁）。 */
    fun load(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        when (val mode = prefs.getString(KEY_MODE, "preset:wulan")) {
            "custom" -> applyHueInternal(prefs.getFloat(KEY_HUE, 200f))
            else -> {
                val key = (mode ?: "preset:wulan").removePrefix("preset:")
                PRESETS.firstOrNull { it.key == key }?.let { apply(it) }
            }
        }
    }

    fun applyPreset(context: Context, key: String) {
        val p = PRESETS.firstOrNull { it.key == key } ?: return
        apply(p)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_MODE, "preset:$key").apply()
    }

    fun applyCustomHue(context: Context, hue: Float) {
        applyHueInternal(hue)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_MODE, "custom").putFloat(KEY_HUE, hue).apply()
    }

    private fun apply(p: Palette) {
        HomecomingColors.Accent = p.accent
        HomecomingColors.AccentSoft = p.accent.copy(alpha = 0.20f)
        HomecomingColors.IceBlue = p.iceBlue
        HomecomingColors.IceBlueLight = p.iceBlueLight
        HomecomingColors.IceBlueDeep = p.iceBlueDeep
        HomecomingColors.Ink = p.ink
        HomecomingColors.InkSoft = p.inkSoft
    }

    private fun applyHueInternal(hueRaw: Float) {
        val hue = ((hueRaw % 360) + 360) % 360
        // 暖橙区（15°~75°）自动压饱和，落实「无橘色」
        val sat = if (hue in 15f..75f) 0.22f else 0.42f
        fun c(l: Float, s: Float = sat): Color = Color(hslToArgb(hue, s, l))
        fun soft(l: Float, a: Float = 0.20f): Color = c(l).copy(alpha = a)
        HomecomingColors.Accent = c(0.52f)
        HomecomingColors.AccentSoft = soft(0.60f)
        HomecomingColors.IceBlue = c(0.82f, sat * 0.75f)
        HomecomingColors.IceBlueLight = c(0.90f, sat * 0.60f)
        HomecomingColors.IceBlueDeep = c(0.66f, sat * 0.85f)
        HomecomingColors.Ink = c(0.24f, sat * 0.55f)
        HomecomingColors.InkSoft = c(0.42f, sat * 0.45f)
    }

    private fun hslToArgb(h: Float, s: Float, l: Float): Int {
        val c = (1 - kotlin.math.abs(2 * l - 1)) * s
        val x = c * (1 - kotlin.math.abs((h / 60) % 2 - 1))
        val m = l - c / 2
        val rgb = when ((h / 60).toInt() % 6) {
            0 -> Triple(c, x, 0f)
            1 -> Triple(x, c, 0f)
            2 -> Triple(0f, c, x)
            3 -> Triple(0f, x, c)
            4 -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        fun to255(v: Float) = ((v + m) * 255).roundToInt().coerceIn(0, 255)
        return (to255(rgb.first) shl 16) or (to255(rgb.second) shl 8) or to255(rgb.third)
    }
}
