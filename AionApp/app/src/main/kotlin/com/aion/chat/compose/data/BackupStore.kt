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
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss-SSS", Locale.US).format(Date())
        val out = File(dir, "aion-backup-$stamp.zip")
        ZipOutputStream(BufferedOutputStream(FileOutputStream(out))).use { zip ->
            // 1) 数据库全量（homecoming.db + workflow.db 等，含 wal/shm，一起还原才不丢尾部事务）
            val dbDir = c.getDatabasePath("x").parentFile
            dbDir?.listFiles()
                ?.filter { it.name.endsWith(".db") || it.name.endsWith(".db-wal") || it.name.endsWith(".db-shm") }
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

    /**
     * 还原 ZIP（v10 P2 原子化）：先把全部条目解到 cache staging，校验齐全后才回位——
     * 顺序 shared_prefs → files → databases（最后写）；任何一步前失败，真数据原封不动。
     * 完成后必须重启进程生效。
     */
    fun restore(c: Context, zip: File): Int {
        val stage = File(c.cacheDir, "restore_stage_" + System.nanoTime())
        var count = 0
        try {
            // ① 全量解包到 staging（含防穿越/重媒体过滤）
            ZipInputStream(FileInputStream(zip).buffered()).use { zin ->
                while (true) {
                    val entry = zin.nextEntry ?: break
                    if (entry.isDirectory) continue
                    val name = entry.name
                    if (name.contains("..")) continue
                    val rel = when {
                        name.startsWith("shared_prefs/") -> name
                        name.startsWith("files/") -> {
                            val rest = name.substringAfter('/')
                            val top = rest.substringBefore('/')
                            if (MEDIA_DIRS.contains(top) || MEDIA_DIRS.contains(rest)) {
                                zin.closeEntry(); continue
                            }
                            name
                        }
                        name.startsWith("databases/") -> name
                        else -> { zin.closeEntry(); continue }
                    }
                    val f = File(stage, rel)
                    f.parentFile?.mkdirs()
                    copyStream(zin, f)
                    count++
                    zin.closeEntry()
                }
            }
            // ② 校验：聊天库必须在，否则视为废包，真数据不动
            val stagedDb = File(stage, "databases/homecoming.db")
            if (!stagedDb.isFile || stagedDb.length() == 0L) {
                throw IllegalStateException("backup_missing_chat_db")
            }
            // ③ shared_prefs 回位
            File(stage, "shared_prefs").listFiles()?.forEach { f ->
                val dst = File(File(c.applicationInfo.dataDir, "shared_prefs"), f.name)
                dst.parentFile?.mkdirs()
                FileInputStream(f).use { copyStream(it, dst) }
            }
            // ④ files/ 回位
            val stagedFiles = File(stage, "files")
            stagedFiles.walkTopDown().filter { it.isFile }.forEach { f ->
                val rel = f.relativeTo(stagedFiles).invariantSeparatorsPath
                val dst = File(c.filesDir, rel)
                dst.parentFile?.mkdirs()
                FileInputStream(f).use { copyStream(it, dst) }
            }
            // ⑤ 数据库最后落位：清旧三件套再覆盖（顺序固定，不靠 ZIP 条目运气）
            File(stage, "databases").listFiles()?.forEach { f ->
                val dst = File(c.getDatabasePath("x").parentFile, f.name)
                listOf("-wal", "-shm").forEach { sfx ->
                    runCatching { File(dst.parentFile, dst.name + sfx).delete() }
                }
                dst.parentFile?.mkdirs()
                FileInputStream(f).use { copyStream(it, dst) }
            }
            return count
        } finally {
            stage.deleteRecursively()
        }
    }

    /** 导出时不许出私有目录的条目（v10 P1：公共 Downloads 谁都能读，密钥绝不出门） */
    private val EXPORT_BLOCKLIST = setOf(
        "shared_prefs/chat_providers.xml",   // 提供商 api_key 明文
        "shared_prefs/sean_ext.xml",
        "shared_prefs/mcp_store.xml",
        "files/routes.json",                 // 路由 api_key 明文
        "files/routes.enc",
        "files/mcp.json"                     // MCP 服务器配置（可含 header token）
    )

    /** 导出一份 ZIP 到公共 Downloads（流式重打包，剥离上面的敏感条目） */
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
                val zin = ZipInputStream(FileInputStream(zip).buffered())
                val zout = ZipOutputStream(BufferedOutputStream(os))
                while (true) {
                    val e = zin.nextEntry ?: break
                    if (e.name in EXPORT_BLOCKLIST) { zin.closeEntry(); continue }
                    zout.putNextEntry(ZipEntry(e.name))
                    zin.copyTo(zout)
                    zin.closeEntry(); zout.closeEntry()
                }
                zout.finish(); zout.flush()
            }
            "Downloads/${zip.name}（已剥离密钥）"
        } else {
            // pre-Q：公共 Downloads 要运行时存储权限，改存 App 私有外部目录（用户可用文件管理器取）
            val dst = File(File(c.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS), "backups"), zip.name)
            dst.parentFile?.mkdirs()
            val zout = ZipOutputStream(BufferedOutputStream(FileOutputStream(dst)))
            val zin = ZipInputStream(FileInputStream(zip).buffered())
            while (true) {
                val e = zin.nextEntry ?: break
                if (e.name in EXPORT_BLOCKLIST) { zin.closeEntry(); continue }
                zout.putNextEntry(ZipEntry(e.name))
                zin.copyTo(zout)
                zin.closeEntry(); zout.closeEntry()
            }
            zout.finish(); zout.close()
            dst.absolutePath + "（已剥离密钥）"
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
