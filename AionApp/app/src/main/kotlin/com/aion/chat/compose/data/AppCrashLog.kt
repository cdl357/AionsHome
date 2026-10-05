package com.aion.chat.compose.data

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

/** 崩溃与初始化错误日志：写入 filesDir/crash_log.txt，设置页可查看，便于远程反馈。 */
object AppCrashLog {

    private const val FILE = "crash_log.txt"
    private const val MAX_BYTES = 64 * 1024
    private const val PREFS = "crash_flags"
    private const val KEY_CRASHED_LAST = "crashed_last_launch"

    fun file(context: Context): File = File(context.filesDir, FILE)

    fun write(context: Context, text: String) {
        try {
            val f = file(context)
            val stamp = SimpleDateFormat("MM-dd HH:mm:ss", Locale.CHINA)
                .format(System.currentTimeMillis())
            val line = "[$stamp] $text\n"
            val old = if (f.exists() && f.length() > MAX_BYTES) "" else f.readText()
            f.writeText(old + line)
            // 同时镜像到外部应用目录：App 起不来时用文件管理器也能拿到
            runCatching {
                val ext = context.getExternalFilesDir(null)
                if (ext != null) {
                    val extFile = File(ext, "crash_log.txt")
                    extFile.writeText(f.readText())
                }
            }
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

    /** 全量日志（安全模式用）。 */
    fun all(context: Context): String = try {
        file(context).readText()
    } catch (e: Exception) { "" }

    fun clear(context: Context) {
        try { file(context).delete() } catch (e: Exception) {}
    }

    // ── 崩溃标记与自救（安全模式） ──

    /** 上一次运行发生过未捕获崩溃 → 本次启动进安全模式。 */
    fun crashedLastLaunch(context: Context): Boolean =
        try {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(KEY_CRASHED_LAST, false)
        } catch (e: Exception) { false }

    fun markCrashed(context: Context) {
        runCatching {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_CRASHED_LAST, true).apply()
        }
    }

    /** 正常启动后清标记（由用户在安全模式里确认，或正常跑满一次由 MainActivity 清）。 */
    fun clearCrashedFlag(context: Context) {
        runCatching {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_CRASHED_LAST, false).apply()
        }
    }

    /**
     * 把崩溃日志导出到公共 Downloads（无需存储权限，API 29+），
     * 路径：下载/aionshome-crash-<时间>.txt。App 完全起不来时也拿得到崩溃原因。
     */
    fun exportToDownloads(context: Context): String? = try {
        val body = all(context).ifBlank { "(日志为空)" }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val name = "aionshome-crash-" +
                SimpleDateFormat("MMdd-HHmmss", Locale.CHINA).format(System.currentTimeMillis()) + ".txt"
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, name)
                put(MediaStore.Downloads.MIME_TYPE, "text/plain")
            }
            val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: return null
            context.contentResolver.openOutputStream(uri)?.use { it.write(body.toByteArray()) }
            "下载/$name"
        } else {
            val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            dir.mkdirs()
            val name = "aionshome-crash-" +
                SimpleDateFormat("MMdd-HHmmss", Locale.CHINA).format(System.currentTimeMillis()) + ".txt"
            File(dir, name).writeText(body)
            "下载/$name"
        }
    } catch (e: Exception) { null }
}
