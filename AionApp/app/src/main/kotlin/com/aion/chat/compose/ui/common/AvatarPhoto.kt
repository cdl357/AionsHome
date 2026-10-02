package com.aion.chat.compose.ui.common

import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.asImageBitmap
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File

/**
 * 头像（布局定稿：真实图片，点一下随时能换；未设置时首字母占位）。
 * 全 App 共用同一份文件——回家页/朋友圈/聊天页传同一 who 就自动同步。
 */
@Composable
fun AvatarPhoto(
    who: String,               // "yuri" | "sean"
    initial: String,
    size: Dp = 56.dp,
    strokeWidth: Dp = 2.dp,
    onClick: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val file = remember(who) {
        File(context.filesDir, if (who == "yuri") "avatar_yuri.jpg" else "avatar_sean.jpg")
    }
    val version = remember(who) { mutableStateOf(file.lastModified()) }

    // 文件存在则加载位图（降采样防 OOM）
    val bitmap: androidx.compose.ui.graphics.ImageBitmap? = remember(version.value) {
        if (!file.exists()) return@remember null
        runCatching {
            val opts = BitmapFactory.Options().apply { inSampleSize = 2 }
            BitmapFactory.decodeFile(file.absolutePath, opts)?.asImageBitmap()
        }.getOrNull()
    }

    val borderColor = if (who == "yuri") Color(0xFFE8A0AC) else Color(0xFF9AB8C4)
    val placeholder = if (who == "yuri") Color(0xFFF5E0E4) else Color(0xFFDCEEF2)

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            runCatching {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                if (bytes != null) file.writeBytes(bytes)
            }
            version.value = file.lastModified()
        }
    }

    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(placeholder)
            .border(strokeWidth, borderColor.copy(alpha = 0.55f), CircleShape)
            .let { m -> if (onClick != null) m.clickable {
                launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            } else m },
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = initial,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(size)
            )
        } else {
            Text(initial, fontSize = (size.value * 0.4f).sp, color = borderColor,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Serif)
        }
    }
}
