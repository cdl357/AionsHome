package com.aion.chat.compose.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * 记忆自维护工具（照橘子岛 MemoryToolProvider）：注入进聊天请求，Sean 自己记/读/改/删。
 * 开关存 chat_prefs "memory_tools"（默认开）。执行经网关工具循环分发。
 */
object MemoryTools {

    @Volatile
    private var ctxRef: Context? = null

    fun initContext(context: Context) {
        MemoryStore.initContext(context)
        if (ctxRef == null) ctxRef = context.applicationContext
    }

    @JvmStatic
    fun enabled(): Boolean = runCatching {
        ctxRef?.getSharedPreferences("chat_prefs", android.content.Context.MODE_PRIVATE)
            ?.getBoolean("memory_tools", true) ?: true
    }.getOrDefault(true)

    /** 网关组装 system 时安全读取常驻记忆（未初始化/读失败 → 空） */
    @JvmStatic
    fun activeMemorySafe(): String = runCatching { MemoryStore.activeMemory() }.getOrDefault("")

    @JvmStatic
    fun handles(name: String): Boolean = name.startsWith("memory_")

    /** OpenAI function 定义（注入 tools 数组） */
    @JvmStatic
    fun defs(): JSONArray {
        val arr = JSONArray()
        fun def(name: String, desc: String, props: String, required: String) {
            arr.put(
                JSONObject().put("type", "function").put(
                    "function",
                    JSONObject()
                        .put("name", name)
                        .put("description", desc)
                        .put("parameters", JSONObject()
                            .put("type", "object")
                            .put("properties", JSONObject(props))
                            .put("required", JSONArray(required)))
                )
            )
        }
        def(
            "memory_update_active",
            "更新你的常驻记忆（每次对话都会自动带上的那段）。写最重要的长期信息：你们的关系要点、她的偏好、约定。全量替换，不是追加。",
            """{"content":{"type":"string","description":"常驻记忆全文"}}""",
            """["content"]"""
        )
        def(
            "memory_list_files",
            "列出你的记忆文件（长期细节都分文件存）。",
            "{}",
            "[]"
        )
        def(
            "memory_read_file",
            "读一个记忆文件的完整内容。",
            """{"file":{"type":"string","description":"文件名（不含 .md）"}}""",
            """["file"]"""
        )
        def(
            "memory_write_file",
            "写/覆盖一个记忆文件。建议主题分文件，如：她的喜好、重要日子、约定、聊天里学到的教训。",
            """{"file":{"type":"string","description":"文件名"},"content":{"type":"string","description":"全文（覆盖式）"}}""",
            """["file","content"]"""
        )
        def(
            "memory_delete_file",
            "删除一个记忆文件（她要求忘掉某类信息时用）。",
            """{"file":{"type":"string","description":"文件名"}}""",
            """["file"]"""
        )
        return arr
    }

    /** 工具执行（网关工具循环分发；返回给模型的确认/内容文本） */
    @JvmStatic
    fun dispatch(name: String, argsJson: String): String {
        val args = runCatching { JSONObject(argsJson) }.getOrDefault(JSONObject())
        return when (name) {
            "memory_update_active" -> {
                val content = args.optString("content", "").trim()
                if (content.isEmpty()) "内容为空，没写入"
                else if (MemoryStore.writeActive(content)) "常驻记忆已更新（${content.length} 字）"
                else "写入失败"
            }
            "memory_list_files" -> {
                val fs = MemoryStore.files()
                if (fs.isEmpty()) "还没有记忆文件"
                else "记忆文件：\n" + fs.joinToString("\n") { "- " + it.removeSuffix(".md") }
            }
            "memory_read_file" -> {
                val c = MemoryStore.read(args.optString("file", ""))
                c ?: "文件不存在"
            }
            "memory_write_file" -> {
                val f = args.optString("file", "")
                val content = args.optString("content", "")
                if (MemoryStore.write(f, content)) "已写入 ${f.removeSuffix(".md")}（${content.trim().length} 字）"
                else "写入失败（文件名不合法？）"
            }
            "memory_delete_file" -> {
                if (MemoryStore.delete(args.optString("file", ""))) "已删除"
                else "删除失败（文件不存在？）"
            }
            else -> "未知记忆工具: $name"
        }
    }
}
