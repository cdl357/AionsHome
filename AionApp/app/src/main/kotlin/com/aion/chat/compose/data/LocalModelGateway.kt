package com.aion.chat.compose.data

import android.content.Context
import android.util.Log
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 本地模型的 GatewayPort 适配器（AionsHome 本地模型队列项·聊天页接入）：
 * - 实现与 HTTP 网关相同的 HomecomingChatEngine.GatewayPort，引擎的
 *   用户消息落库 / 控制解析 / assistant 提交 / 失败标记全部复用，UI 无感知切换
 * - 引擎的 gateway 层 onChunk 是空实现（设计上只在完成后发布整段），本地 token 流
 *   内部累计、结束一次性 onComplete——与现有"发完才显示"的行为一致，无重复风险
 * - prompt：把引擎 ContextPort 已建好的消息列表映到 Qwen2.5 模板；系统消息全保，
 *   历史按字符预算裁剪（nCtx 4096 的现实）
 * - 停止：engine.stop(requestId) → cancel 置位 → sink 返回 false 中断生成
 */
class LocalModelGateway(private val appContext: Context) : com.aion.chat.homecoming.HomecomingChatEngine.GatewayPort {

    companion object {
        // v11 P2：取消表必须 static——网关每次发送都会新建实例，实例级表让停止键空转（v9 同款坑）
        private val cancels = ConcurrentHashMap<String, AtomicBoolean>()
    }

    override fun stream(
        request: com.aion.chat.homecoming.HomecomingModelGateway.ChatRequest,
        observer: com.aion.chat.homecoming.HomecomingModelGateway.StreamObserver,
    ) {
        val cancel = AtomicBoolean(false)
        cancels[request.requestId] = cancel
        try {
            val meta = LocalModelStore.chatModel(appContext)
            if (meta == null || !LocalModelStore.isDownloaded(appContext, meta)) {
                observer.onFailure("local_no_model: 本地模型未下载（设置→本地模型）")
                return
            }
            if (LocalLlamaEngine.loadedFile() != LocalModelStore.localFile(appContext, meta).absolutePath) {
                if (!LocalLlamaEngine.load(appContext, meta)) {
                    observer.onFailure("local_load_failed: ${LocalLlamaEngine.lastError}")
                    return
                }
            }
            val prompt = buildQwenPrompt(request.messages)
            val t0 = System.currentTimeMillis()
            val sb = StringBuilder()
            val n = LocalLlamaEngine.generate(prompt, maxTokens = 1024, temp = 0.7f) { piece ->
                val text = String(piece, Charsets.UTF_8)
                sb.append(text)
                // v0.3.151 流式：本地 token 逐个进 UI 气泡
                observer.onChunk(text)
                !cancel.get()
            }
            val (pt, ct) = LocalLlamaEngine.lastStats
            RequestLog.add(null, "LOCAL", request.modelKey.ifBlank { "local" },
                if (n >= 0) 200 else 500, System.currentTimeMillis() - t0,
                if (n < 0) LocalLlamaEngine.lastError.take(150) else "",
                0, pt.toLong(), ct.toLong())
            val text = sb.toString()
            if ((n < 0 || text.isBlank()) && LocalLlamaEngine.lastError.isNotBlank()) {
                observer.onFailure("local_generate_failed: ${LocalLlamaEngine.lastError.take(160)}")
                return
            }
            observer.onComplete(text.trim())
        } catch (t: Throwable) {
            Log.w("LocalModelGateway", "stream crashed", t)
            observer.onFailure("local_crash: ${t.javaClass.simpleName}: ${t.message?.take(120)}")
        } finally {
            cancels.remove(request.requestId)
        }
    }

    override fun cancel(requestId: String) {
        cancels[requestId]?.set(true)
    }

    /** Qwen2.5 chat 模板：system 全保 + 历史按预算保新裁旧（最后一条=当前输入必保） */
    private fun buildQwenPrompt(messages: List<com.aion.chat.homecoming.HomecomingModelGateway.ChatMessage>): String {
        val budget = 9000   // 字符预算（≈ nCtx 4096 token 内给输出留余地）
        val system = StringBuilder()
        val blocks = mutableListOf<String>()
        for (m in messages) {
            when (m.role) {
                "system" -> system.append("<|im_start|>system\n").append(m.text).append("<|im_end|>\n")
                "assistant" -> blocks.add("<|im_start|>assistant\n${m.text}<|im_end|>\n")
                else -> blocks.add("<|im_start|>user\n${m.text}<|im_end|>\n")
            }
        }
        // 从最新往旧收，预算内尽量多带
        val kept = ArrayDeque<String>()
        var used = 0
        for (b in blocks.reversed()) {
            if (used + b.length > budget && kept.isNotEmpty()) break
            used += b.length
            kept.addFirst(b)
        }
        val sb = StringBuilder(system)
        for (h in kept) sb.append(h)
        sb.append("<|im_start|>assistant\n")
        return sb.toString()
    }
}
