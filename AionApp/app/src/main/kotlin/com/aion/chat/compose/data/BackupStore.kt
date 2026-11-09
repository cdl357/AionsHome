package com.aion.chat.compose.data

import android.content.Context
import android.content.Intent
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * 本地自动备份（照她拍板的"自动备份"队列项）：
 * - 内容：聊天数据库 homecoming.db(+wal/shm) + 全部 shared_prefs + files/ 下的配置与记忆小文件
 *   （routes.json / mcp.json / active_memory.md / memory_db/ / 收藏 / 统计 / 背景图头像等）
 * - 不进备份的重媒体目录：相册、聊天图片视频、贴纸库、音乐缓存（太大，ZIP 失去意义）
 * - 存放：App 私有目录 backups/（别的 App 读不到；卸载会没，想留档用「导出到 Downloads」）
 * - 周期：WorkManager 12 小时一次，低电量不跑，保留最近 7 份
 * - ZIP 内部路径规范：databases/<name> / shared_prefs/<name> / files/<相对路径>（还原按前缀回位）
 */
object BackupStore {
    private const val PREFS = "backup_settings"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_LAST_AT = "last_backup_at"
    private const val WORK_NAME = "aion_auto_backup"
    private const val KEEP = 7

    /** 重媒体目录（files/ 下的相对名），备份跳过 */
    private val MEDIA_DIRS = setOf(
        "album", "chat_images", "chat_videos", "stickers", "stickers_remote",
        "stickers_supabase", "sean_assets", "moments", "cache", "widget_assets", "code_cache"
    )

    private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isEnabled(c: Context): Boolean = prefs(c).getBoolean(KEY_ENABLED, true)

    fun setEnabled(c: Context, on: Boolean) {
        prefs(c).edit().putBoolean(KEY_ENABLED, on).apply()
        if (on) schedule(c) else runCatching { WorkManager.getInstance(c).cancelUniqueWork(WORK_NAME) }
    }

    fun lastBackupAt(c: Context): Long = prefs(c).getLong(KEY_LAST_AT, 0L)

    fun backupDir(c: Context): File {
        val ext = c.getExternalFilesDir(null)
        return File(if (ext != null) ext else c.filesDir, "backups")
    }

    fun list(c: Context): List<File> =
        backupDir(c).listFiles { f -> f.isFile && f.name.endsWith(".zip") }
            ?.sortedByDescending { it.name } ?: emptyList()

    /** 打一份备份，返回 ZIP 文件；失败抛异常（UI 捕获提示） */
    fun create(c: Context): File {
        val dir = backupDir(c)
        dir.mkdirs()
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val out = File(dir, "aion-backup-$stamp.zip")
        ZipOutputStream(BufferedOutputStream(FileOutputStream(out))).use { zip ->
            // 1) 聊天数据库（含 wal/shm，一起还原才不丢尾部事务）
            val dbDir = c.getDatabasePath("x").parentFile
            dbDir?.listFiles()
                ?.filter { it.name.startsWith("homecoming.db") }
                ?.forEach { putFile(zip, it, "databases/${it.name}") }
            // 2) shared_prefs 全量（providers/routes/mcp/style 等运行配置；App 私有本地文件，可含密钥）
            File(c.applicationInfo.dataDir, "shared_prefs").listFiles()
                ?.forEach { putFile(zip, it, "shared_prefs/${it.name}") }
            // 3) files/ 递归（跳过重媒体）
            walk(c.filesDir, "") { f, rel -> putFile(zip, f, "files/$rel") }
        }
        prefs(c).edit().putLong(KEY_LAST_AT, System.currentTimeMillis()).apply()
        prune(dir)
        return out
    }

    /** 还原 ZIP：按前缀回位，数据库最后写；完成后必须重启进程生效 */
    fun restore(c: Context, zip: File): Int {
        var count = 0
        val dbFiles = mutableListOf<Pair<File, File>>()   // zip内临时 → 目标
        val staged = mutableListOf<File>()
        ZipInputStream(FileInputStream(zip).buffered()).use { zin ->
            while (true) {
                val entry = zin.nextEntry ?: break
                if (entry.isDirectory) continue
                val name = entry.name
                if (name.contains("..")) continue          // 防穿越
                try {
                    when {
                        name.startsWith("databases/") -> {
                            val tmp = File(c.cacheDir, "restore_" + System.nanoTime())
                            copyStream(zin, tmp); staged.add(tmp)
                            dbFiles.add(tmp to File(c.getDatabasePath("x").parentFile, name.substringAfter("databases/")))
                        }
                        name.startsWith("shared_prefs/") -> {
                            val dst = File(File(c.applicationInfo.dataDir, "shared_prefs"), name.substringAfter('/'))
                            dst.parentFile?.mkdirs()
                            copyStream(zin, dst); count++
                        }
                        name.startsWith("files/") -> {
                            val dst = File(c.filesDir, name.substringAfter('/'))
                            if (MEDIA_DIRS.contains(dst.parentFile?.name)) continue
                            dst.parentFile?.mkdirs()
                            copyStream(zin, dst); count++
                        }
                    }
                } finally {
                    zin.closeEntry()
                }
            }
        }
        // 数据库最后落位：先清掉旧的三件套再覆盖
        for ((tmp, dst) in dbFiles) {
            listOf("-wal", "-shm").forEach { sfx -> runCatching { File(dst.parentFile, dst.name + sfx).delete() } }
            dst.parentFile?.mkdirs()
            FileInputStream(tmp).use { ins -> copyStream(ins, dst) }
            count++
        }
        staged.forEach { runCatching { it.delete() } }
        return count
    }

    /** 导出一份 ZIP 到公共 Downloads（想留长期档/换机用） */
    fun exportToDownloads(c: Context, zip: File): String? = runCatching {
        val resolver = c.contentResolver
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            val values = android.content.ContentValues().apply {
                put(android.provider.MediaStore.Downloads.DISPLAY_NAME, zip.name)
                put(android.provider.MediaStore.Downloads.MIME_TYPE, "application/zip")
            }
            val uri = resolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: return null
            resolver.openOutputStream(uri)?.use { os ->
                FileInputStream(zip).use { it.copyTo(os) }
            }
            "Downloads/${zip.name}"
        } else {
            val dst = File(
                android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS),
                zip.name
            )
            FileInputStream(zip).use { ins -> copyStream(ins, dst) }
            dst.absolutePath
        }
    }.getOrNull()

    /** 注册周期任务：12h 一次，低电量不跑（开关打开时调用；WorkManager 自动跨重启） */
    fun schedule(c: Context) {
        val req = PeriodicWorkRequestBuilder<BackupWorker>(12, TimeUnit.HOURS)
            .setConstraints(
                Constraints.Builder().setRequiresBatteryNotLow(true).build()
            )
            .build()
        WorkManager.getInstance(c).enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, req)
    }

    /** App 冷启动调用：开关开着就确保任务在册（幂等） */
    fun ensureScheduled(c: Context) {
        if (isEnabled(c)) runCatching { schedule(c) }
    }

    private fun prune(dir: File) {
        dir.listFiles { f -> f.isFile && f.name.endsWith(".zip") }
            ?.sortedByDescending { it.name }
            ?.drop(KEEP)
            ?.forEach { runCatching { it.delete() } }
    }

    private fun walk(dir: File, rel: String, on: (File, String) -> Unit) {
        val fs = dir.listFiles() ?: return
        for (f in fs) {
            val r = if (rel.isEmpty()) f.name else "$rel/${f.name}"
            if (f.isDirectory) {
                if (MEDIA_DIRS.contains(f.name)) continue
                walk(f, r, on)
            } else {
                if (f.length() > 20L * 1024 * 1024) continue   // 单文件超 20MB 的不入包（异常大文件兜底）
                on(f, r)
            }
        }
    }

    private fun putFile(zip: ZipOutputStream, f: File, entryName: String) {
        if (!f.isFile) return
        zip.putNextEntry(ZipEntry(entryName))
        FileInputStream(f).use { it.copyTo(zip) }
        zip.closeEntry()
    }

    private fun copyStream(input: java.io.InputStream, dst: File) {
        FileOutputStream(dst).use { input.copyTo(it) }
    }
}

/** WorkManager 周期备份执行体 */
class BackupWorker(private val ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): androidx.work.ListenableWorker.Result {
        return try {
            BackupStore.create(ctx)
            androidx.work.ListenableWorker.Result.success()
        } catch (e: Exception) {
            androidx.work.ListenableWorker.Result.retry()
        }
    }
}

/** 还原后的重启（设置页调用）：清任务栈冷启动，进程一死新旧连接全断 */
fun restartApp(context: Context) {
    val i = context.packageManager.getLaunchIntentForPackage(context.packageName)
    i?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    if (i != null) context.startActivity(i)
    Runtime.getRuntime().exit(0)
}
