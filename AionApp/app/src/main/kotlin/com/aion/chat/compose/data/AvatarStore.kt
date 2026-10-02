package com.aion.chat.compose.data

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.File

/**
 * 头像全局存储（布局定稿：真实图片，点头像随时能换）。
 * Yuri/Sean 各一张，存 filesDir/avatar_yuri.jpg / avatar_sean.jpg。
 * 回家页/朋友圈/聊天页共用同一份——首页换了，处处跟着变。
 */
object AvatarStore {

    data class Stamps(val yuri: Long, val sean: Long)

    var stamp: Stamps = Stamps(0, 0)
        private set

    fun bump(who: String) {
        stamp = if (who == "yuri") Stamps(System.currentTimeMillis(), stamp.sean)
                else Stamps(stamp.yuri, System.currentTimeMillis())
    }

    fun yuriFile(context: Context): File = File(context.filesDir, "avatar_yuri.jpg")
    fun seanFile(context: Context): File = File(context.filesDir, "avatar_sean.jpg")

    fun fileFor(context: Context, who: String): File =
        if (who == "yuri") yuriFile(context) else seanFile(context)

    fun save(context: Context, who: String, bytes: ByteArray): Boolean = try {
        fileFor(context, who).outputStream().use { it.write(bytes) }
        bump(who)
        true
    } catch (e: Exception) { false }

    fun load(context: Context, who: String): ImageBitmap? {
        val f = fileFor(context, who)
        if (!f.exists()) return null
        return runCatching {
            val opts = BitmapFactory.Options().apply { inSampleSize = 2 }
            BitmapFactory.decodeFile(f.absolutePath, opts)?.asImageBitmap()
        }.getOrNull()
    }

    fun clear(context: Context, who: String) {
        fileFor(context, who).delete()
        bump(who)
    }
}
