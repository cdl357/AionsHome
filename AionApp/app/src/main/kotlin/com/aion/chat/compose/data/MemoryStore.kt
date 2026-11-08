package com.aion.chat.compose.data

import android.content.Context
import java.io.File

/**
 * 记忆文件系统（照橘子岛 MemoryManager）：
 * - active_memory.md：常驻记忆，每次请求追加进 system prompt
 * - memory_db 里的 .md 文件：Sean 自己创建/读写的记忆文件
 * 全部本地文件；路径白名单校验防穿越。线程安全（synchronized）。
 */
object MemoryStore {

    @Volatile
    private var ctxRef: Context? = null

    fun initContext(context: Context) {
        if (ctxRef == null) ctxRef = context.applicationContext
    }

    private fun ctx(): Context = ctxRef ?: throw IllegalStateException("MemoryStore 未初始化")

    private fun dir(): File = File(ctx().filesDir, "memory_db").apply { mkdirs() }

    private fun activeFile(): File = File(ctx().filesDir, "active_memory.md")

    const val MAX_ACTIVE = 4000
    const val MAX_FILE = 8000

    fun activeMemory(): String = runCatching {
        val f = activeFile()
        if (f.exists()) f.readText().take(MAX_ACTIVE) else ""
    }.getOrDefault("")

    fun writeActive(text: String): Boolean = runCatching {
        activeFile().writeText(text.trim().take(MAX_ACTIVE))
        true
    }.getOrDefault(false)

    fun files(): List<String> = runCatching {
        dir().listFiles()?.filter { it.isFile && it.name.endsWith(".md") }
            ?.map { it.name }?.sorted() ?: emptyList()
    }.getOrDefault(emptyList())

    fun read(name: String): String? = runCatching {
        safeFile(name)?.takeIf { it.exists() }?.readText()?.take(MAX_FILE)
    }.getOrNull()

    fun write(name: String, content: String): Boolean = runCatching {
        val f = safeFile(name) ?: return@runCatching false
        f.writeText(content.trim().take(MAX_FILE))
        true
    }.getOrDefault(false)

    fun delete(name: String): Boolean = runCatching {
        safeFile(name)?.takeIf { it.exists() }?.delete() ?: false
    }.getOrDefault(false)

    /** 文件名白名单：字母数字下划线横杠中文，1~40 字；自动补 .md；canonical 防穿越。 */
    fun safeFile(name: String): File? {
        val cleaned = name.trim().removeSuffix(".md")
        if (cleaned.isEmpty() || cleaned.length > 40) return null
        if (!cleaned.matches(Regex("[a-zA-Z0-9_\\-\\u4e00-\\u9fa5]+"))) return null
        val f = File(dir(), "$cleaned.md")
        return if (f.canonicalPath.startsWith(dir().canonicalPath)) f else null
    }
}
