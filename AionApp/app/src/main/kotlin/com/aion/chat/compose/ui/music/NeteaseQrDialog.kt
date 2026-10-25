package com.aion.chat.compose.ui.music

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aion.chat.compose.data.MusicClient
import kotlinx.coroutines.delay

/** 网易云扫码登录弹窗：二维码 + 轮询状态（等待扫码/确认/成功/过期），Cookie 只留服务器。 */
@Composable
fun NeteaseQrDialog(onDismiss: () -> Unit, onLoggedIn: (String) -> Unit) {
    var qrBase64 by remember { mutableStateOf<String?>(null) }
    var unikey by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("生成二维码…") }
    var done by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val start = MusicClient.neteaseQrStart()
        if (start == null) {
            status = "登录服务暂时连不上"
            return@LaunchedEffect
        }
        unikey = start.first
        qrBase64 = start.second
        status = "用网易云音乐 App 扫一扫"
        while (!done) {
            delay(2500)
            val check = MusicClient.neteaseQrCheck(unikey) ?: continue
            when (check.code) {
                803 -> {
                    status = "登录成功！"
                    done = true
                    onLoggedIn(check.nickname)
                }
                802 -> status = "手机上确认一下…"
                800 -> {
                    status = "二维码过期了，关掉重开一次"
                    done = true
                }
                else -> status = "等待扫码…"
            }
        }
    }

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF101B1F))
                .clickable { onDismiss() }
                .padding(30.dp)
        ) {
            Spacer(Modifier.height(30.dp))
            Text("登录网易云音乐", fontSize = 20.sp, color = Color.White, fontWeight = FontWeight.Medium)
            Text(status, fontSize = 14.sp, color = Color(0xFF8FE34A), modifier = Modifier.padding(top = 10.dp))
            Spacer(Modifier.height(20.dp))
            val qrB64 = qrBase64
            if (qrB64 != null) {
                val bmp = remember(qrB64) {
                    runCatching {
                        val bytes = android.util.Base64.decode(qrB64, android.util.Base64.DEFAULT)
                        android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    }.getOrNull()
                }
                if (bmp != null) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "登录二维码",
                        modifier = Modifier.size(240.dp)
                    )
                }
            } else {
                Box(
                    modifier = Modifier.size(240.dp),
                    contentAlignment = Alignment.Center
                ) { Text("…", color = Color.White) }
            }
            Spacer(Modifier.height(16.dp))
            Text(
                "Cookie 只会保存在你的服务器上，不会进手机缓存或聊天记录",
                fontSize = 10.sp, color = Color(0xFF6E8A92),
                textAlign = TextAlign.Center
            )
        }
    }
}
