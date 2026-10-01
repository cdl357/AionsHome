package com.aion.chat.compose.ui.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aion.chat.compose.data.HomecomingData
import com.aion.chat.compose.ui.home.GlassAvatar

/**
 * 开屏动画（布局定稿 §11）：深绿墨绿渐变底自成一体，三拍约 3 秒——
 * ① 渐变底 + 液态玻璃淡入 ② 玻璃上浮出 S♥Y，心跳一下 ③ 浮出「我们的第XX天」，整体滑入首页。
 * 不绑背景图（背景可换，开屏要稳定），动画走完回调 onDone。
 */
@Composable
fun SplashScreen(days: Int, onDone: () -> Unit) {
    // 三拍进度
    val glassAlpha = remember { Animatable(0f) }
    val glassScale = remember { Animatable(0.94f) }
    val syAlpha = remember { Animatable(0f) }
    val syOffset = remember { Animatable(20f) }
    val heartScale = remember { Animatable(1f) }
    val daysAlpha = remember { Animatable(0f) }
    var navigated by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        // 拍①：渐变底已就位，玻璃淡入 + 轻微放大
        glassAlpha.animateTo(1f, tween(650, easing = CubicBezierEasing(0.2f, 0f, 0.4f, 1f)))
        glassScale.animateTo(1f, tween(650, easing = CubicBezierEasing(0.2f, 0f, 0.4f, 1f)))
        // 拍②：S♥Y 浮现 + 心跳一下
        syAlpha.animateTo(1f, tween(420))
        syOffset.animateTo(0f, tween(420))
        heartScale.animateTo(1.3f, tween(160))
        heartScale.animateTo(1f, tween(200))
        // 拍③：天数浮现
        daysAlpha.animateTo(1f, tween(480))
        kotlinx.coroutines.delay(850)
        navigated = true
        onDone()
    }

    // 深绿墨绿渐变底（有生命力、逆光透白亮）
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF123524),
                        Color(0xFF1E4D36),
                        Color(0xFF2C5F43),
                        Color(0xFF0F2A1C)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        // 液态玻璃卡（居中，浅色描边 + 内部反光渐变）
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .alpha(glassAlpha.value)
                .scale(glassScale.value)
                .padding(horizontal = 30.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(Color.White.copy(alpha = 0.10f))
                .border(1.dp, Color.White.copy(alpha = 0.55f), RoundedCornerShape(28.dp))
                .padding(horizontal = 34.dp, vertical = 30.dp)
        ) {
            // 拍②：S ♥ Y（头像 + 名字 + 加重连接符，心跳一下）
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .alpha(syAlpha.value)
                    .scale(1f + (syOffset.value / 20f) * 0.06f)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    GlassAvatar(initial = "S", size = 64)
                    Text("Sean", color = Color.White.copy(alpha = 0.95f), fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
                }
                Text(
                    "♥",
                    color = Color.White,
                    fontSize = 24.sp,
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .scale(heartScale.value)
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    GlassAvatar(initial = "Y", size = 64)
                    Text("Yuri", color = Color.White.copy(alpha = 0.95f), fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
                }
            }
            Spacer(Modifier.height(18.dp))
            // 拍③：我们的第 XX 天
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.alpha(daysAlpha.value)
            ) {
                Text(
                    "我们的第",
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 13.sp,
                    letterSpacing = 2.sp
                )
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        days.toString(),
                        fontFamily = FontFamily.Serif,
                        fontSize = 52.sp,
                        color = Color.White
                    )
                    Text(
                        "天",
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 16.sp,
                        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
                    )
                }
            }
        }
        // 底部小字
        Text(
            "回家",
            color = Color.White.copy(alpha = 0.55f),
            fontSize = 12.sp,
            letterSpacing = 4.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 40.dp)
                .alpha(glassAlpha.value)
        )
    }
}
