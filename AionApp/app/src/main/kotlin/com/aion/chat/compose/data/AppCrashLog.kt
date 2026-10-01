package com.aion.chat.compose.data

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

/** 崩溃与初始化错误日志：写入 filesDir/crash_log.txt，设置页可查看，便于远程反馈。 */
object AppCrashLog {

    private const val FILE = "crash_log.txt"
    private const val MAX_BYTES = 64 * 1024

    fun file(context: Context): File = File(context.filesDir, FILE)

    fun write(context: Context, text: String) {
        try {
            val f = file(context)
            val stamp = SimpleDateFormat("MM-dd HH:mm:ss", Locale.CHINA)
                .format(System.currentTimeMillis())
            val line = "[$stamp] $text\n"
            val old = if (f.exists() && f.length() > MAX_BYTES) "" else f.readText()
            f.writeText(old + line)
        } catch (e: Exception) { /* 日志失败不影响主流程 */ }
    }

    fun write(context: Context, t: Throwable) {
        write(context, t.javaClass.simpleName + ": " + (t.message ?: "(无消息)"))
    }

    fun last(context: Context, maxChars: Int = 600): String? = try {
        val f = file(context)
        if (!f.exists()) null
        else f.readText().takeLast(maxChars).ifBlank { null }
    } catch (e: Exception) { null }

    fun clear(context: Context) {
        try { file(context).delete() } catch (e: Exception) {}
    }
}
