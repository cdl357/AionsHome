package com.aion.chat.compose.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * 全局主题（集中管理，方便后期加樱粉/雾蓝等主题）。
 * 视觉基调：冰蓝 + 白 + 浅灰，暖粉只做点缀；磨砂玻璃卡片近乎无色透明。
 */
object HomecomingColors {
    val IceBlue = Color(0xFFBCECEF)
    val IceBlueLight = Color(0xFFCEF4F5)
    val IceBlueDeep = Color(0xFF9AD6DC)
    val WarmPink = Color(0xFFF0A8BC)
    val WarmPinkSoft = Color(0x33F0A8BC)
    val Ink = Color(0xFF3A4550)
    val InkSoft = Color(0xFF7B8794)
    val Glass = Color.White.copy(alpha = 0.62f)
    val GlassStrong = Color.White.copy(alpha = 0.82f)
    val GlassBorder = Color.White.copy(alpha = 0.75f)
    val Ok = Color(0xFF6FBF9A)
}

private val LightScheme = lightColorScheme(
    primary = HomecomingColors.WarmPink,
    onPrimary = Color.White,
    secondary = HomecomingColors.IceBlueDeep,
    background = HomecomingColors.IceBlueLight,
    surface = Color.White,
    onBackground = HomecomingColors.Ink,
    onSurface = HomecomingColors.Ink,
)

@Composable
fun HomecomingTheme(content: @Composable () -> Unit) {
    // 深浅模式本期以浅色冰蓝为准；暗色主题随后期主题系统一起做
    isSystemInDarkTheme()
    MaterialTheme(
        colorScheme = LightScheme,
        content = content,
    )
}
