package com.aion.chat.compose.data.workspace

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.ConnectivityManager
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.inputmethod.InputMethodManager
import com.termux.terminal.TerminalEmulator
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import com.termux.view.TerminalView
import com.termux.view.TerminalViewClient
import java.io.File

/**
 * 交互式 PTY 终端会话（照橘瓣 WorkspaceTerminalSession，AionsHome 适配）：
 * - proot argv 与 ProotShellRunner.buildCommand 同构（v11 起只 bind /dev，/proc /sys 不给）
 * - files 区挂到 /workspace，终端里 cd /workspace 就能看到 AI 工具读写的同一批文件
 * - DNS 补丁用系统当前活动网络的 DNS（比引擎默认更贴她的实际网络）
 */
object WorkspaceTerminal {

    private const val WORKSPACE_DIR = "/workspace"

    fun rootfsReady(c: Context): Boolean = AionWorkspace.hasRootfs(c)

    fun prepare(c: Context) {
        val app = c.applicationContext
        val ws = File(File(app.filesDir, "workspace"), AionWorkspace.DEFAULT_ROOT)
        File(ws, "files").mkdirs()
        File(ws, "tmp").mkdirs()
        RootfsPatcher().patch(
            File(ws, "linux"),
            RootfsPatchOptions(nameservers = app.activeDnsServers())
        )
    }

    fun createSession(c: Context, client: TerminalSessionClient): TerminalSession {
        val app = c.applicationContext
        val ws = File(File(app.filesDir, "workspace"), AionWorkspace.DEFAULT_ROOT)
        val filesDir = File(ws, "files")
        val linuxDir = File(ws, "linux")
        val tempDir = File(ws, "tmp")
        File(ws, "files").mkdirs()
        File(ws, "tmp").mkdirs()
        val nativeLibraryDir = File(app.applicationInfo.nativeLibraryDir)
        val proot = File(nativeLibraryDir, "libproot_exec.so")
        val loader = File(nativeLibraryDir, "libproot_loader.so")

        val args = mutableListOf(
            "--root-id",
            "--link2symlink",
            "--kill-on-exit",
            "-r", linuxDir.absolutePath,
            "-w", WORKSPACE_DIR,
            "-b", "${filesDir.absolutePath}:$WORKSPACE_DIR",
        )
        // v11 P1-3 同款收紧：只 bind /dev
        if (File("/dev").exists()) {
            args += "-b"
            args += "/dev"
        }
        args += listOf(
            "/usr/bin/env", "-i",
            "HOME=/root",
            "PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin",
            "TERM=xterm-256color",
            "LANG=C.UTF-8", "LC_ALL=C.UTF-8",
            "USER=root",
            "SHELL=/bin/bash",
            "/bin/bash",
        )
        val env = arrayOf(
            "PROOT_LOADER=${loader.absolutePath}",
            "PROOT_TMP_DIR=${tempDir.absolutePath}",
            "TMPDIR=${tempDir.absolutePath}",
        )
        return TerminalSession(
            proot.absolutePath,
            filesDir.absolutePath,
            args.toTypedArray(),
            env,
            2_000,
            client,
        )
    }

    fun activeDnsServers(c: Context): List<String> {
        val cm = c.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return emptyList()
        val network = cm.activeNetwork ?: return emptyList()
        return cm.getLinkProperties(network)?.dnsServers?.mapNotNull { it.hostAddress }.orEmpty()
    }

    /** TerminalSession 回调（照橘瓣 WorkspaceTerminalSessionClient） */
    class SessionClient(
        private val context: Context,
        private val onFinished: () -> Unit,
    ) : TerminalSessionClient {
        var terminalView: TerminalView? = null

        override fun onTextChanged(changedSession: TerminalSession) {
            terminalView?.onScreenUpdated()
        }
        override fun onTitleChanged(changedSession: TerminalSession) = Unit
        override fun onSessionFinished(finishedSession: TerminalSession) {
            terminalView?.onScreenUpdated()
            onFinished()
        }
        override fun onCopyTextToClipboard(session: TerminalSession, text: String) {
            val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cb.setPrimaryClip(ClipData.newPlainText("terminal", text))
        }
        override fun onPasteTextFromClipboard(session: TerminalSession) {
            val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val text = cb.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)
                ?.coerceToText(context)?.toString() ?: return
            val bytes = text.toByteArray()
            session.write(bytes, 0, bytes.size)
        }
        override fun onBell(session: TerminalSession) = Unit
        override fun onColorsChanged(session: TerminalSession) { terminalView?.invalidate() }
        override fun onTerminalCursorStateChange(state: Boolean) { terminalView?.invalidate() }
        override fun getTerminalCursorStyle(): Int = TerminalEmulator.DEFAULT_TERMINAL_CURSOR_STYLE
        override fun logError(tag: String, message: String) { Log.e(tag, message) }
        override fun logWarn(tag: String, message: String) { Log.w(tag, message) }
        override fun logInfo(tag: String, message: String) { Log.i(tag, message) }
        override fun logDebug(tag: String, message: String) { Log.d(tag, message) }
        override fun logVerbose(tag: String, message: String) { Log.v(tag, message) }
        override fun logStackTraceWithMessage(tag: String, message: String, e: Exception) { Log.e(tag, message, e) }
        override fun logStackTrace(tag: String, e: Exception) { Log.e(tag, "Terminal error", e) }
    }

    /** TerminalView 回调（照橘瓣 WorkspaceTerminalViewClient） */
    class ViewClient(
        private val context: Context,
    ) : TerminalViewClient {
        var terminalView: TerminalView? = null
        var controlDown: Boolean = false
        var altDown: Boolean = false

        override fun onScale(scale: Float): Float = scale.coerceIn(0.8f, 1.25f)
        override fun onSingleTapUp(e: MotionEvent) { focusAndShowKeyboard() }

        fun focusAndShowKeyboard() {
            val view = terminalView ?: return
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            view.post {
                view.requestFocus()
                imm.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
            }
        }
        override fun shouldBackButtonBeMappedToEscape(): Boolean = false
        override fun shouldEnforceCharBasedInput(): Boolean = true
        override fun shouldUseCtrlSpaceWorkaround(): Boolean = false
        override fun isTerminalViewSelected(): Boolean = true
        override fun copyModeChanged(copyMode: Boolean) = Unit
        override fun onKeyDown(keyCode: Int, e: KeyEvent, session: TerminalSession): Boolean = false
        override fun onKeyUp(keyCode: Int, e: KeyEvent): Boolean = false
        override fun onLongPress(event: MotionEvent): Boolean = false
        override fun readControlKey(): Boolean = controlDown
        override fun readAltKey(): Boolean = altDown
        override fun readShiftKey(): Boolean = false
        override fun readFnKey(): Boolean = false
        override fun onCodePoint(codePoint: Int, ctrlDown: Boolean, session: TerminalSession): Boolean = false
        override fun onEmulatorSet() = Unit
        override fun logError(tag: String, message: String) { Log.e(tag, message) }
        override fun logWarn(tag: String, message: String) { Log.w(tag, message) }
        override fun logInfo(tag: String, message: String) { Log.i(tag, message) }
        override fun logDebug(tag: String, message: String) { Log.d(tag, message) }
        override fun logVerbose(tag: String, message: String) { Log.v(tag, message) }
        override fun logStackTraceWithMessage(tag: String, message: String, e: Exception) { Log.e(tag, message, e) }
        override fun logStackTrace(tag: String, e: Exception) { Log.e(tag, "Terminal view error", e) }
    }
}

/** 系统当前活动网络的 DNS（rootfs 补丁用，比引擎默认贴实际网络） */
private fun Context.activeDnsServers(): List<String> {
    val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        ?: return emptyList()
    val network = cm.activeNetwork ?: return emptyList()
    return cm.getLinkProperties(network)?.dnsServers?.mapNotNull { it.hostAddress }.orEmpty()
}
