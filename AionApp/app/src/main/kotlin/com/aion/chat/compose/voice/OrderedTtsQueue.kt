package com.aion.chat.compose.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

/**
 * 有序 TTS 播放轨（教程 §12）：
 * - 首句优先切分（first_min=4 / soft_min=8 / hard_min=14，不切坏数字和英文）
 * - 每个 TTS 片段带 generation 身份 + 严格递增 seq
 * - 播放权可撤销：cancel(generation) 之后，旧 generation 的任何回调/片段全部丢弃
 * - 正式回答永远拥有最高优先级：cancel 即刻 stop，绝不为播完旧片段延迟新回答
 */
class OrderedTtsQueue(context: Context, private val onReady: () -> Unit) {

    private val seqCounter = AtomicInteger(0)
    private var tts: TextToSpeech? = null
    private var ready = false

    /** 每个 generation 的已入队/已完成计数，用于"说完了"判定。 */
    private val enqueued = HashMap<String, Int>()
    private val completed = HashMap<String, Int>()
    private var onGenerationDone: ((String) -> Unit)? = null
    private var onFirstAudio: ((String) -> Unit)? = null
    private var cancelledGenerations = HashSet<String>()

    init {
        tts = TextToSpeech(context) { status ->
            ready = status == TextToSpeech.SUCCESS
            if (ready) {
                runCatching { tts?.language = Locale.CHINA }
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        val gen = utteranceId?.substringBefore('|') ?: return
                        onFirstAudio?.invoke(gen)
                    }

                    override fun onDone(utteranceId: String?) {
                        val gen = utteranceId?.substringBefore('|') ?: return
                        if (gen in cancelledGenerations) return
                        val done = synchronized(completed) {
                            completed[gen] = (completed[gen] ?: 0) + 1
                            val e = enqueued[gen]
                            val c = completed[gen]
                            e != null && c != null && c >= e
                        }
                        if (done) onGenerationDone?.invoke(gen)
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        onDone(utteranceId)
                    }
                })
            }
            onReady()
        }
    }

    fun isReady(): Boolean = ready

    fun setListeners(onFirstAudio: (String) -> Unit, onGenerationDone: (String) -> Unit) {
        this.onFirstAudio = onFirstAudio
        this.onGenerationDone = onGenerationDone
    }

    /** 新 generation 开始：清零该代计数并解除可能的取消标记。 */
    fun beginGeneration(generationId: String) {
        synchronized(completed) {
            enqueued[generationId] = 0
            completed[generationId] = 0
            cancelledGenerations.remove(generationId)
        }
    }

    /** 入队一个片段（有序：调用方按 seq 顺序喂）。返回 utteranceId。 */
    fun speak(generationId: String, text: String): String {
        val seq = seqCounter.incrementAndGet()
        synchronized(completed) { enqueued[generationId] = (enqueued[generationId] ?: 0) + 1 }
        val utteranceId = "$generationId|$seq"
        runCatching {
            tts?.speak(text, TextToSpeech.QUEUE_ADD, null, utteranceId)
        }
        return utteranceId
    }

    /** 抢话/挂断：撤销当前一切播放权。旧 generation 的迟到回调永久丢弃。 */
    fun cancelAll(generationId: String) {
        synchronized(completed) { cancelledGenerations.add(generationId) }
        runCatching { tts?.stop() }
    }

    fun shutdown() {
        runCatching { tts?.stop(); tts?.shutdown() }
        tts = null
        ready = false
    }
}

/**
 * 首句优先切分器（教程 §12.1 TTSStreamer 的同步版）：
 * feed(累积文本) → 返回可合成的片段；切点优先级 句末 > 逗顿 > 硬长度；
 * 不把数字/英文单词/URL 切坏。onComplete 时 flushRemaining() 收尾。
 */
class TtsSegmenter(private val firstMin: Int = 4, private val softMin: Int = 8, private val hardMin: Int = 14) {

    private val buffer = StringBuilder()

    fun feed(cumulativeText: String): List<String> {
        val out = mutableListOf<String>()
        val delta = if (cumulativeText.length > buffer.length) cumulativeText.substring(buffer.length) else ""
        if (delta.isEmpty()) return out
        buffer.append(delta)
        while (true) {
            val cut = findCut() ?: break
            val seg = buffer.substring(0, cut).trim()
            buffer.delete(0, cut)
            if (seg.isNotEmpty()) out.add(seg)
        }
        return out
    }

    fun flushRemaining(): String {
        val rest = buffer.toString().trim()
        buffer.setLength(0)
        return rest
    }

    private fun findCut(): Int? {
        val s = buffer.toString()
        if (s.length < firstMin) return null
        // 1. 句末标点
        for (i in firstMin..s.length) {
            if (i - 1 < s.length && s[i - 1] in "。？！?!\n") return i
        }
        // 2. 逗顿等自然停顿
        for (i in softMin..s.length) {
            if (i - 1 < s.length && s[i - 1] in "，、；：,; ") return i
        }
        // 3. 硬长度：到 hardMin 后找安全边界（不在 ASCII 数字/字母中间切）
        if (s.length >= hardMin) {
            var cut = hardMin
            while (cut < s.length && isAsciiWordChar(s[cut - 1]) && isAsciiWordChar(s[cut])) cut++
            if (cut >= s.length) return null // 还在单词中间且没到头，等更多文本
            return cut
        }
        return null
    }

    private fun isAsciiWordChar(c: Char): Boolean =
        c in 'a'..'z' || c in 'A'..'Z' || c in '0'..'9'
}
