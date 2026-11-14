package com.aion.chat.compose.data.workspace

import android.content.Context
import android.os.Build
import java.io.File

/**
 * AionsHome 的 proot 沙盒封装（引擎层照搬橘瓣 workspace 模块，v1 单沙盒）：
 * - root 固定 filesDir/workspace/default；安装状态存 prefs
 * - Shell = ProotShellRunner（引擎自带 rootfs 就绪检查 + DNS 补丁）
 * - AI 工具（WorkspaceTools）与试命令都走 runShell
 */
object AionWorkspace {
    private const val PREFS = "workspace_state"
    private const val KEY_STAGE = "install_stage"      // none/installing/ready/broken
    private const val KEY_URL = "installed_url"
    const val DEFAULT_ROOT = "default"

    // 预填源（清华实测可达；阿里备用）
    // v0.3.151：USTC 主源（实测对手机 UA 友好）→ 官方 dl-cdn → tuna 兜底；tuna/aliyun 对 Dalvik UA 会 403
    val ROOTFS_SOURCES = listOf(
        "https://mirrors.ustc.edu.cn/alpine/v3.21/releases/aarch64/alpine-minirootfs-3.21.2-aarch64.tar.gz",
        "https://dl-cdn.alpinelinux.org/alpine/v3.21/releases/aarch64/alpine-minirootfs-3.21.2-aarch64.tar.gz",
        "https://mirrors.tuna.tsinghua.edu.cn/alpine/v3.21/releases/aarch64/alpine-minirootfs-3.21.2-aarch64.tar.gz",
        "https://mirrors.aliyun.com/alpine/v3.21/releases/aarch64/alpine-minirootfs-3.21.2-aarch64.tar.gz",
    )

    /** 每个源的官方 sha256（同一发布文件各镜像同哈希） */
    val ROOTFS_SHA256 = mapOf(
        "https://mirrors.ustc.edu.cn/alpine/v3.21/releases/aarch64/alpine-minirootfs-3.21.2-aarch64.tar.gz"
                to "1281d44664bf636e0dbdd7641cce4b2fea92b97acce3d42a246257f056478f5c",
        "https://dl-cdn.alpinelinux.org/alpine/v3.21/releases/aarch64/alpine-minirootfs-3.21.2-aarch64.tar.gz"
                to "1281d44664bf636e0dbdd7641cce4b2fea92b97acce3d42a246257f056478f5c",
        "https://mirrors.tuna.tsinghua.edu.cn/alpine/v3.21/releases/aarch64/alpine-minirootfs-3.21.2-aarch64.tar.gz"
                to "1281d44664bf636e0dbdd7641cce4b2fea92b97acce3d42a246257f056478f5c",
        "https://mirrors.aliyun.com/alpine/v3.21/releases/aarch64/alpine-minirootfs-3.21.2-aarch64.tar.gz"
                to "1281d44664bf636e0dbdd7641cce4b2fea92b97acce3d42a246257f056478f5c",
    )

    private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun baseDir(c: Context): File = File(c.filesDir, "workspace")

    fun manager(c: Context): WorkspaceManager =
        WorkspaceManager(baseDir(c))

    fun rootfsDir(c: Context): File = manager(c).linuxDir(DEFAULT_ROOT)

    fun hasRootfs(c: Context): Boolean = manager(c).hasRootfs(DEFAULT_ROOT)

    fun stage(c: Context): String = prefs(c).getString(KEY_STAGE, "none") ?: "none"

    fun installedUrl(c: Context): String? = prefs(c).getString(KEY_URL, null)

    private fun setStage(c: Context, s: String, url: String? = null) {
        prefs(c).edit().putString(KEY_STAGE, s).apply()
        if (url != null) prefs(c).edit().putString(KEY_URL, url).apply()
    }

    /**
     * 安装 rootfs（阻塞调用线程；支持逐源回退）。
     * 返回 null=成功；非 null=错误。
     */
    fun installRootfs(
        c: Context, urls: List<String>,
        onProgress: (RootfsInstallProgress) -> Unit,
        cancelled: () -> Boolean,
    ): String? {
        // v11 P2：安装前磁盘检查——rootfs 解包后 ~700MB，留足余量
        if (baseDir(c).usableSpace < 1024L * 1024 * 1024) {
            return "磁盘剩余不足 1GB，先清理空间再安装"
        }
        val installer = RootfsInstaller(manager(c))
        var lastErr = "安装失败"
        for (url in urls) {
            setStage(c, "installing", url)
            val err = runCatching {
                installer.install(DEFAULT_ROOT, url, { p ->
                    onProgress(p)
                    if (cancelled()) throw InterruptedException("cancelled")
                }, ROOTFS_SHA256[url])
                null
            }.getOrElse {
                if (it is InterruptedException && cancelled()) "cancelled"
                else it.message?.take(200) ?: "安装异常"
            }
            if (err == null && manager(c).hasRootfs(DEFAULT_ROOT)) {
                runCatching { RootfsPatcher().patch(rootfsDir(c)) }
                setStage(c, "ready", url)
                return null
            }
            if (err == "cancelled") { setStage(c, if (hasRootfs(c)) "ready" else "none", url); return "cancelled" }
            // v11 P2：换源重试前清临时目录——不同源的字节流不能续传拼接（防投毒拼包）
            runCatching { manager(c).tempDir(DEFAULT_ROOT).deleteRecursively() }
            lastErr = err ?: "安装失败"
        }
        setStage(c, if (hasRootfs(c)) "ready" else "broken", null)
        return lastErr
    }

    /** 在沙盒里跑一条命令（阻塞 IO 线程调用） */
    fun runShell(c: Context, command: String, timeoutSeconds: Int = 60): WorkspaceCommandResult {
        // v11 P2：磁盘保护——剩余不足 500MB 拒绝执行（沙盒里 dd 一条就能撑爆存储）
        if (baseDir(c).usableSpace < 500L * 1024 * 1024) {
            return WorkspaceCommandResult(127, "",
                "磁盘剩余不足 500MB，拒绝执行——清一下空间或清空沙盒再试")
        }
        // 与工具层同一把锁：试命令/工作流/聊天工具并发不再互相踩
        return synchronized(this@AionWorkspace) {
            val mgr = manager(c)
        val runner = ProotShellRunner(nativeLibraryDir(c))
        return runner.execute(
            WorkspaceShellContext(
                root = DEFAULT_ROOT,
                command = command,
                cwd = "/",
                filesDir = mgr.filesDir(DEFAULT_ROOT),
                linuxDir = mgr.linuxDir(DEFAULT_ROOT),
                tempDir = mgr.tempDir(DEFAULT_ROOT),
                workingDir = mgr.workspaceDir(DEFAULT_ROOT),
                timeoutMillis = timeoutSeconds.coerceIn(1, 600) * 1000L,
            )
        )
        }
    }

    fun reset(c: Context) {
        manager(c).deleteWorkspace(DEFAULT_ROOT)
        setStage(c, "none", null)
    }

    private fun nativeLibraryDir(c: Context): File {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD) {
            File(c.applicationInfo.nativeLibraryDir)
        } else {
            File("/data/data/" + c.packageName + "/lib")
        }
    }
}
