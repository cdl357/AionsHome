package com.aion.chat.compose.data

import android.content.Context
import com.aion.chat.compose.data.workspace.AionWorkspace
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject

/**
 * Linux 沙盒（proot workspace）的 LLM 工具（照橘瓣 workspace_* 工具族，AionsHome 精简版）：
 * shell / read_file / write_file / list_directory / delete_file。
 * - 前置条件：rootfs 已安装（设置→Linux 沙盒）
 * - workspace_shell 是真 shell：进程内单并发锁、超时 1..600s、输出截断
 * - 文件工具一律走引擎的 WorkspaceManager（路径越界/配额防护已内置）
 */
object WorkspaceTools {

    private val lock = Any()

    private fun prefs(c: Context) = c.getSharedPreferences("workspace_state", Context.MODE_PRIVATE)

    /** 沙盒 shell 开关（用户可关；关了 workspace_shell 不进工具面，读写文件仍可用） */
    @JvmStatic
    fun shellEnabled(c: Context): Boolean = prefs(c).getBoolean("shell_enabled", true)

    @JvmStatic
    fun setShellEnabled(c: Context, on: Boolean) {
        prefs(c).edit().putBoolean("shell_enabled", on).apply()
    }

    @Volatile private var ctxRef: Context? = null

    /** Wiring 启动时注入（与 WorkflowTools.initContext 同款） */
    @JvmStatic
    fun initContext(context: Context) {
        if (ctxRef == null) ctxRef = context.applicationContext
    }

    @JvmStatic
    fun handles(name: String): Boolean = name.startsWith("workspace_")

    /** 引擎已就绪才把工具面暴露给模型 */
    @JvmStatic
    fun available(): Boolean {
        val c = ctxRef ?: return false
        return AionWorkspace.hasRootfs(c)
    }

    private fun err(code: String, detail: String): String =
        JSONObject().put("ok", false).put("error", code).put("detail", detail).toString()

    @JvmStatic
    fun defs(): JSONArray {
        val arr = JSONArray()
        fun def(name: String, desc: String, props: JSONObject, required: JSONArray? = null) {
            val p = JSONObject().put("type", "object").put("properties", props)
            if (required != null) p.put("required", required)
            arr.put(JSONObject().put("type", "function").put("function", JSONObject()
                .put("name", name).put("description", desc).put("parameters", p)))
        }
        val ctx0 = ctxRef
        if (ctx0 != null && shellEnabled(ctx0)) def("workspace_shell",
            "在 App 内置的 Alpine Linux 沙盒（proot）里执行 shell 命令，返回 stdout/stderr/exit_code。" +
                    "沙盒有 busybox sh、apk 包管理（可 apk add）。边界如实说明：proot 是路径翻译不是安全容器，" +
                    "进程/网络与宿主 App 同权限，沙盒目录内的文件改动互不影响，但不要在里面处理不可信来源的内容。" +
                    "命令超时 1..600 秒（默认 60）。",
            JSONObject()
                .put("command", JSONObject().put("type", "string").put("description", "要执行的 shell 命令"))
                .put("timeout_seconds", JSONObject().put("type", "integer").put("description", "超时秒数，默认 60，上限 600")),
            JSONArray().put("command"))
        def("workspace_read_file",
            "读沙盒 files 区的文件（UTF-8 文本，单次上限 512KB）。path 相对 files 根目录（如 notes/todo.txt）；" +
                    "rootfs（linux 区）文件请用 workspace_shell cat 读。",
            JSONObject().put("path", JSONObject().put("type", "string")),
            JSONArray().put("path"))
        def("workspace_write_file",
            "写沙盒内文件（UTF-8 文本，单次上限 2MB，自动建目录）。",
            JSONObject()
                .put("path", JSONObject().put("type", "string"))
                .put("content", JSONObject().put("type", "string")),
            JSONArray().put("path").put("content"))
        def("workspace_list_directory",
            "列沙盒目录（单次最多 500 项）。",
            JSONObject().put("path", JSONObject().put("type", "string").put("description", "默认根目录")))
        def("workspace_delete_file",
            "删沙盒内文件或目录（目录需 recursive=true）。",
            JSONObject()
                .put("path", JSONObject().put("type", "string"))
                .put("recursive", JSONObject().put("type", "boolean")),
            JSONArray().put("path"))
        return arr
    }

    @JvmStatic
    fun dispatch(name: String, argsJson: String): String {
        val app = ctxRef ?: return err("not_initialized", "沙盒工具未初始化")
        if (!AionWorkspace.hasRootfs(app)) {
            return err("no_rootfs", "沙盒还没装 rootfs——去 设置→Linux 沙盒 先安装")
        }
        val args = try { JSONObject(if (argsJson.isBlank()) "{}" else argsJson) } catch (e: Exception) { JSONObject() }
        val mgr = AionWorkspace.manager(app)
        val root = mgr.filesDir(AionWorkspace.DEFAULT_ROOT)
        return when (name) {
            "workspace_shell" -> {
                val cmd = args.optString("command")
                if (cmd.isBlank()) return err("missing_command", "command 必填")
                val timeout = args.optInt("timeout_seconds", 60).coerceIn(1, 600)
                val result = synchronized(lock) {
                    runCatching { AionWorkspace.runShell(app, cmd, timeout) }.getOrNull()
                }
                when (result) {
                    null -> err("timeout", "命令超过 ${timeout + 30}s 总预算被终止")
                    else -> JSONObject()
                        .put("ok", result.exitCode == 0 && !result.timedOut)
                        .put("exit_code", result.exitCode)
                        .put("timed_out", result.timedOut)
                        .put("stdout", if (result.stdout.isBlank()) JSONObject.NULL else result.stdout.take(8000))
                        .put("stderr", if (result.stderr.isBlank()) JSONObject.NULL else result.stderr.take(4000))
                        .toString()
                }
            }
            "workspace_read_file" -> {
                val path = args.optString("path")
                try {
                    JSONObject().put("ok", true).put("content", mgr.readText(AionWorkspace.DEFAULT_ROOT, path)).toString()
                } catch (e: Exception) { err("read_failed", e.message?.take(200) ?: "读取失败") }
            }
            "workspace_write_file" -> {
                val path = args.optString("path"); val content = args.optString("content")
                if (path.isBlank()) return err("missing_path", "path 必填")
                try {
                    mgr.writeText(AionWorkspace.DEFAULT_ROOT, path, content)
                    JSONObject().put("ok", true).put("path", path).toString()
                } catch (e: Exception) { err("write_failed", e.message?.take(200) ?: "写入失败") }
            }
            "workspace_list_directory" -> {
                val path = args.optString("path")
                try {
                    val items = mgr.listFiles(AionWorkspace.DEFAULT_ROOT, path)
                    JSONArray().apply { for (i in items) put(JSONObject()
                        .put("path", i.path).put("name", i.name).put("dir", i.isDirectory).put("size", i.sizeBytes)) }.toString()
                } catch (e: Exception) { err("list_failed", e.message?.take(200) ?: "列目录失败") }
            }
            "workspace_delete_file" -> {
                val path = args.optString("path")
                if (path.isBlank()) return err("missing_path", "path 必填")
                try {
                    val ok = mgr.deleteFile(AionWorkspace.DEFAULT_ROOT, path, args.optBoolean("recursive", false))
                    if (ok) JSONObject().put("ok", true).toString() else err("delete_failed", "删除失败")
                } catch (e: Exception) { err("delete_failed", e.message?.take(200) ?: "删除失败") }
            }
            else -> err("unknown_tool", name)
        }
    }
}
