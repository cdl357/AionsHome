package com.aion.chat.compose.data

import android.content.Context
import android.util.Log
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * 本地模型引擎（llama.cpp JNI 绑定，AionsHome 本地模型队列项）：
 * - 模型文件：filesDir 下的 local_models 目录（gguf，走 LocalModelStore 下载）
 * - 句柄单槽：同一时刻只加载一个模型（手机内存现实），切换模型自动释放旧的
 * - generate 阻塞在调用线程（UI 用专用后台线程），token 流式回调，回调返回 false = 用户停止
 * - prompt 由调用方拼完整（模板+多轮），引擎每轮清 KV——无状态、简单、可预期
 */
object LocalLlamaEngine {
    private const val TAG = "LocalLlama"

    /** 每条生成 token 的回调：返回 false 提前停止 */
    fun interface TokenSink {
        fun onToken(piece: String): Boolean
    }

    private val lock = Any()
    private var handle: Long = 0L
    private var loadedPath: String? = null

    @Volatile var lastError: String = ""
        private set

    fun modelsDir(c: Context): File = File(c.filesDir, "local_models").apply { mkdirs() }

    /** 模型是否可用（文件在 + 尺寸>50MB 防半截） */
    fun isReady(c: Context, model: LocalModelStore.ModelMeta): Boolean {
        val f = File(modelsDir(c), model.file)
        return f.isFile && f.length() > 50L * 1024 * 1024
    }

    fun loadedFile(): String? = synchronized(lock) { loadedPath }

    fun load(c: Context, model: LocalModelStore.ModelMeta, nCtx: Int = 4096): Boolean = synchronized(lock) {
        val path = File(modelsDir(c), model.file).absolutePath
        if (handle != 0L && loadedPath == path) return true
        release()
        runCatching { nativeBackendInit() }
            .onFailure { Log.w(TAG, "backend init", it) }
        handle = runCatching { nativeLoadModel(path.toByteArray(Charsets.UTF_8), nCtx, 4) }
            .getOrElse {
                lastError = it.message ?: "加载失败"
                Log.w(TAG, "load failed", it); 0L
            }
        loadedPath = if (handle != 0L) path else null
        if (handle == 0L && lastError.isBlank()) lastError = "模型加载失败"
        return handle != 0L
    }

    /** 阻塞生成：在 IO 线程调用；返回生成 token 数（-1 失败） */
    fun generate(prompt: String, maxTokens: Int, temp: Float, sink: TokenSink): Int = synchronized(lock) {
        if (handle == 0L) { lastError = "模型未加载"; return -1 }
        lastError = ""
        return runCatching {
            nativeGenerate(handle, prompt.toByteArray(Charsets.UTF_8), maxTokens, temp, sink)
        }.getOrElse {
            Log.w(TAG, "generate crashed", it)
            lastError = it.message ?: "生成异常"
            -1
        }
    }

    fun release() = synchronized(lock) {
        if (handle != 0L) runCatching { nativeFree(handle) }
        handle = 0L
        loadedPath = null
    }

    // ── native ──
    @JvmStatic private external fun nativeBackendInit()
    @JvmStatic private external fun nativeLoadModel(pathBytes: ByteArray, nCtx: Int, nThreads: Int): Long
    @JvmStatic private external fun nativeFree(handle: Long)
    @JvmStatic private external fun nativeGenerate(
        handle: Long, promptBytes: ByteArray, maxTokens: Int, temp: Float, sink: Any): Int

    @JvmStatic private external fun nativeBackendFree()

    // object 的 init 块在首次访问时执行（companion 不能嵌在 standalone object 里）
    init { System.loadLibrary("local-llama") }
}

/**
 * 本地模型库：预置小模型清单（hf-mirror 直链，国内可达）+ 下载（断点续传）+ 管理。
 * 只收 Qwen2.5 Instruct 的 Q4_K_M 小杯——0.5B 拉得动、1.5B 日常、3B 上限，再大手机会烧。
 */
object LocalModelStore {
    private const val PREFS = "local_model"
    private const val KEY_PROGRESS = "dl_"
    private const val KEY_CHAT_MODEL = "chat_model"
    private const val KEY_USE_IN_CHAT = "use_in_chat"

    data class ModelMeta(
        val key: String,
        val label: String,
        val file: String,
        val urls: List<String>,      // 源链：hf-mirror 在前，ModelScope 兜底（hf 的大文件会 302 到海外 CDN，国内可能不通）
        val sizeBytes: Long,
        val note: String,
    )

    private fun hf(repo: String, file: String) =
        "https://hf-mirror.com/Qwen/$repo/resolve/main/$file"

    private fun ms(repo: String, file: String) =
        "https://modelscope.cn/models/Qwen/$repo/resolve/master/$file"

    val CATALOG = listOf(
        ModelMeta(
            "qwen25-05b", "Qwen2.5 0.5B（最快）",
            "qwen2.5-0.5b-instruct-q4_k_m.gguf",
            listOf(
                hf("Qwen2.5-0.5B-Instruct-GGUF", "qwen2.5-0.5b-instruct-q4_k_m.gguf"),
                ms("Qwen2.5-0.5B-Instruct-GGUF", "qwen2.5-0.5b-instruct-q4_k_m.gguf"),
            ),
            411_000_000L, "约 400MB，聊天秒回，适合当随身应答机"
        ),
        ModelMeta(
            "qwen25-15b", "Qwen2.5 1.5B（均衡·推荐）",
            "qwen2.5-1.5b-instruct-q4_k_m.gguf",
            listOf(
                hf("Qwen2.5-1.5B-Instruct-GGUF", "qwen2.5-1.5b-instruct-q4_k_m.gguf"),
                ms("Qwen2.5-1.5B-Instruct-GGUF", "qwen2.5-1.5b-instruct-q4_k_m.gguf"),
            ),
            1_100_000_000L, "约 1.1GB，质量明显上一个档"
        ),
        ModelMeta(
            "qwen25-3b", "Qwen2.5 3B（上限）",
            "qwen2.5-3b-instruct-q4_k_m.gguf",
            listOf(
                hf("Qwen2.5-3B-Instruct-GGUF", "qwen2.5-3b-instruct-q4_k_m.gguf"),
                ms("Qwen2.5-3B-Instruct-GGUF", "qwen2.5-3b-instruct-q4_k_m.gguf"),
            ),
            2_000_000_000L, "约 2GB，回答最像样，但慢、吃内存"
        ),
    )

    fun byKey(key: String): ModelMeta? = CATALOG.firstOrNull { it.key == key }

    private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** 聊天页用哪个本地模型：优先取显式选择，否则用第一个已下载的 */
    fun chatModel(c: Context): ModelMeta? {
        val key = prefs(c).getString(KEY_CHAT_MODEL, null)
        key?.let { byKey(it)?.let { m -> return m } }
        return CATALOG.firstOrNull { isDownloaded(c, it) }
    }

    /** 聊天里用本地模型（替代在线模型发 Sean） */
    fun useInChat(c: Context): Boolean = prefs(c).getBoolean(KEY_USE_IN_CHAT, false)

    fun setUseInChat(c: Context, on: Boolean) {
        prefs(c).edit().putBoolean(KEY_USE_IN_CHAT, on).apply()
        if (!on) LocalLlamaEngine.release()
    }

    fun progress(c: Context, key: String): Long =
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(KEY_PROGRESS + key, -1L)  // -1=没在下

    private fun setProgress(c: Context, key: String, value: Long) {
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putLong(KEY_PROGRESS + key, value).apply()
    }

    fun localFile(c: Context, m: ModelMeta): File = File(LocalLlamaEngine.modelsDir(c), m.file)

    fun isDownloaded(c: Context, m: ModelMeta): Boolean {
        val f = localFile(c, m)
        return f.isFile && f.length() > 50L * 1024 * 1024
    }

    fun delete(c: Context, m: ModelMeta) {
        runCatching { localFile(c, m).delete() }
        LocalLlamaEngine.release()
    }

    /**
     * 下载（阻塞调用线程，支持断点续传）。progress 回调 (已下载字节, 总字节)。
     * 返回 null=成功；非 null=错误信息。逐源尝试（hf-mirror → ModelScope）。
     */
    fun download(
        c: Context, m: ModelMeta,
        onProgress: (Long, Long) -> Unit,
        cancelled: () -> Boolean,
    ): String? {
        var lastErr = "下载失败"
        for (url in m.urls) {
            val err = downloadFrom(c, m, url, onProgress, cancelled)
            if (err == null || err == "cancelled") return err
            lastErr = err
            if (cancelled()) return "cancelled"
        }
        return lastErr
    }

    private fun downloadFrom(
        c: Context, m: ModelMeta, url: String,
        onProgress: (Long, Long) -> Unit,
        cancelled: () -> Boolean,
    ): String? {
        val dst = localFile(c, m)
        dst.parentFile?.mkdirs()
        var downloaded = if (dst.isFile) dst.length() else 0L
        return runCatching {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = true
                connectTimeout = 30000
                readTimeout = 60000
                if (downloaded > 0) setRequestProperty("Range", "bytes=$downloaded-")
            }
            val code = conn.responseCode
            if (code !in 200..299) return "HTTP $code"
            val resumeOk = code == 206
            if (!resumeOk) { downloaded = 0L; runCatching { dst.delete() } }
            val total = downloaded + conn.contentLengthLong
            setProgress(c, m.key, 0L)
            conn.inputStream.use { ins ->
                java.io.FileOutputStream(dst, downloaded > 0).use { out ->
                    val buf = ByteArray(256 * 1024)
                    var sinceReport = 0L
                    while (true) {
                        if (cancelled()) return "cancelled"
                        val n = ins.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        downloaded += n
                        sinceReport += n
                        if (sinceReport >= 2L * 1024 * 1024) {
                            sinceReport = 0
                            setProgress(c, m.key, downloaded)
                            onProgress(downloaded, total)
                        }
                    }
                }
            }
            if (total > 0 && downloaded < total * 95 / 100) return "下载不完整"
            setProgress(c, m.key, -1L)
            onProgress(downloaded, downloaded)
            null
        }.getOrElse {
            Log.w("LocalModel", "download failed: $url", it)
            setProgress(c, m.key, -1L)
            it.message ?: "下载失败"
        }
    }
}
