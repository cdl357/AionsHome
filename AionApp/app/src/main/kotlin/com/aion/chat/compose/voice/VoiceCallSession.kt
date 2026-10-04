package com.aion.chat.compose.voice

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.AudioRecord
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import com.aion.chat.compose.data.AppCrashLog
import com.aion.chat.compose.data.HomecomingChatWiring
import com.aion.chat.homecoming.HomecomingChatEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** 会话状态机（教程 §5）：抢话可从 thinking/speaking 回到 listening；挂断先完成告别再释放资源。 */
enum class VoicePhase { STANDBY, GREETING, LISTENING, THINKING, SPEAKING, CLOSING, NOROUTE }

data class VoiceUiState(
    val phase: VoicePhase = VoicePhase.STANDBY,
    val message: String = "",
    val heard: String = "",
    val reply: String = ""
)

/**
 * 通话运行时（教程 §2 四条轨 + Identity Protocol 的 Android 落地）：
 * - Listen：speaking/thinking 期间 AudioRecord 持续监听 → BargeInDetector 两阶段 duck/interrupt
 * - Recognize：listening 阶段系统识别器（自适应停句用其静音参数 + 硬上限）
 * - Respond：homecoming 引擎（主聊天同模型同记忆），首句优先切分
 * - Speak：OrderedTtsQueue 有序可撤销播放
 * - Identity：call/turn/generation 贯穿引擎回调与 TTS 回调，旧代结果永久丢弃
 */
class VoiceCallSession(private val context: Context, private val onEnded: () -> Unit) {

    companion object {
        private const val TAG = "VoiceLatency"
        private const val SAMPLE_RATE = 16000
        private const val FRAME_MS = 20
        private const val HARD_TURN_CAP_MS = 60_000
        private const val SPEAK_COOLDOWN_MS = 260L
        private const val FAREWELL_DEADLINE_MS = 15_000L
    }

    private val callId = CallIdentityFactory.newCallId()
    private var turnSequence = 0
    @Volatile private var active: CallIdentity? = null

    private val stateFlow = MutableStateFlow(VoiceUiState())
    val state: StateFlow<VoiceUiState> = stateFlow

    private val main = Handler(Looper.getMainLooper())
    private var wiring: HomecomingChatWiring? = null
    private var queue: OrderedTtsQueue? = null
    private var recognizer: SpeechRecognizer? = null
    private var closed = false

    private var segmenter = TtsSegmenter()
    private var turnStartAt = 0L
    private var firstTextAt = 0L
    private var firstAudioAt = 0L
    private var asrDoneAt = 0L

    // 麦克风监听轨（抢话检测）
    private var audioRecord: AudioRecord? = null
    private var micThread: Thread? = null
    @Volatile private var micRunning = false
    private val bargeIn = BargeInDetector()
    private var audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var duckedVolume: Int? = null
    private var focusRequest: AudioFocusRequest? = null

    private fun setState(phase: VoicePhase, message: String, heard: String? = null, reply: String? = null) {
        main.post {
            val cur = stateFlow.value
            stateFlow.value = VoiceUiState(
                phase = phase,
                message = message,
                heard = heard ?: cur.heard,
                reply = reply ?: cur.reply
            )
        }
    }

    // ── 生命周期 ──

    fun start() {
        if (stateFlow.value.phase != VoicePhase.STANDBY) return
        wiring = runCatching { HomecomingChatWiring(context) }
            .onFailure { AppCrashLog.write(context, it) }
            .getOrNull()
        val routeReady = wiring?.hasRoute() == true
        queue = OrderedTtsQueue(context) { onTtsReady() }
        queue?.setListeners(
            onFirstAudio = { gen ->
                if (firstAudioAt == 0L && gen == active?.generationId) {
                    firstAudioAt = System.currentTimeMillis()
                    logLatency()
                }
            },
            onGenerationDone = { gen -> onGenerationDone(gen) }
        )
        initRecognizer()
        requestAudioFocus()
        if (routeReady) {
            setState(VoicePhase.GREETING, "接通中…")
            greet()
        } else {
            setState(VoicePhase.NOROUTE, "线路未配置，去设置配一条再打")
        }
    }

    fun close() {
        if (closed) return
        closed = true
        active = null
        stopMic()
        runCatching { recognizer?.destroy() }
        recognizer = null
        queue?.shutdown()
        abandonAudioFocus()
    }

    private fun onTtsReady() { /* TTS 就绪，greeting 已在队列等待合成 */ }

    private fun greet() {
        val w = wiring ?: return listen()
        turnSequence += 1
        val id = CallIdentity(callId, turnSequence, CallIdentityFactory.newGeneration(turnSequence))
        active = id
        queue?.beginGeneration(id.generationId)
        segmenter = TtsSegmenter()
        turnStartAt = System.currentTimeMillis()
        w.engine.send(
            HomecomingChatEngine.ChatCommand(
                id.requestId(),
                HomecomingChatWiring.TIMELINE,
                HomecomingChatWiring.RESPONDER,
                HomecomingChatWiring.USER,
                "（电话刚刚接通）你是 Sean。先自然开口：一句话，像刚接起电话的感觉，30 字以内。",
                "main",
                w.mainModelKey(), "", "",
                false // 问候不落用户消息
            ),
            engineObserver(id)
        )
    }

    // ── Recognize 轨 ──

    private fun initRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) return
        recognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: android.os.Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}

                override fun onError(error: Int) {
                    if (closed) return
                    // 冷却后继续听（避免尾音/回声残留）；若这期间已进入别的阶段则不抢
                    main.postDelayed({
                        if (!closed && stateFlow.value.phase == VoicePhase.LISTENING) listen()
                    }, 400L)
                }

                override fun onResults(results: android.os.Bundle?) {
                    if (closed) return
                    val text = results
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()?.trim().orEmpty()
                    asrDoneAt = System.currentTimeMillis()
                    if (text.isBlank()) {
                        main.postDelayed({
                            if (!closed && stateFlow.value.phase == VoicePhase.LISTENING) listen()
                        }, 400L)
                    } else {
                        startTurn(text)
                    }
                }

                override fun onPartialResults(partialResults: android.os.Bundle?) {
                    val p = partialResults
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()?.trim()
                    if (!p.isNullOrBlank()) {
                        val cur = stateFlow.value
                        main.post { stateFlow.value = cur.copy(heard = p) }
                    }
                }

                override fun onEvent(eventType: Int, params: android.os.Bundle?) {}
            })
        }
    }

    /** 进入监听：识别器开录。 */
    fun listen() {
        if (closed) return
        stopMic()
        setState(VoicePhase.LISTENING, "我在听。点说话键可重来")
        val sr = recognizer ?: return
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            // 自适应停句的起步参数（系统识别器自己的端点 + 我们的上限）
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1350L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 900L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 8000L)
        }
        runCatching { sr.startListening(intent) }
    }

    /** 手动触发（UI 说话键在 LISTENING 时的兜底）。 */
    fun restartListening() {
        if (stateFlow.value.phase == VoicePhase.LISTENING) listen()
    }

    // ── Respond 轨 ──

    private fun startTurn(text: String) {
        if (closed) return
        if (text.length <= 12 && isFarewell(text)) {
            closeGracefully(text)
            return
        }
        turnSequence += 1
        val id = CallIdentity(callId, turnSequence, CallIdentityFactory.newGeneration(turnSequence))
        active = id
        queue?.beginGeneration(id.generationId)
        segmenter = TtsSegmenter()
        turnStartAt = System.currentTimeMillis()
        firstTextAt = 0L
        firstAudioAt = 0L
        setState(VoicePhase.THINKING, "Sean 在想…", heard = text, reply = "")
        startMic()
        val w = wiring ?: return
        w.engine.send(
            HomecomingChatEngine.ChatCommand(
                id.requestId(),
                HomecomingChatWiring.TIMELINE,
                HomecomingChatWiring.RESPONDER,
                HomecomingChatWiring.USER,
                text + "（这是语音通话里说的话，像打电话一样自然回应，不要提语音/文字）",
                "main",
                w.mainModelKey(), "", ""
            ),
            engineObserver(id)
        )
    }

    private fun engineObserver(id: CallIdentity): HomecomingChatEngine.Observer {
        return object : HomecomingChatEngine.Observer {
            override fun onChunk(chunk: String) {
                // 身份协议：旧 generation 的迟到结果永久丢弃
                if (closed || !belongsToActiveGeneration(id.generationId, active)) return
                if (firstTextAt == 0L) firstTextAt = System.currentTimeMillis()
                // 首句优先切分：一到可说片段就送合成
                segmenter.feed(chunk).forEach { seg -> speakSegment(id, seg) }
            }

            override fun onComplete(messageId: String, completeText: String) {
                if (closed || !belongsToActiveGeneration(id.generationId, active)) return
                val rest = segmenter.flushRemaining()
                if (rest.isNotBlank()) speakSegment(id, rest)
                if (completeText.isNotBlank()) {
                    val cur = stateFlow.value
                    main.post { stateFlow.value = cur.copy(reply = completeText.trim()) }
                }
            }

            override fun onFailure(code: String) {
                if (closed || !belongsToActiveGeneration(id.generationId, active)) return
                speakSegment(id, "线路刚才卡了一下，你再说一遍？")
            }
        }
    }

    // ── Speak 轨 ──

    private fun speakSegment(id: CallIdentity, text: String) {
        val q = queue ?: return
        if (stateFlow.value.phase == VoicePhase.THINKING || stateFlow.value.phase == VoicePhase.GREETING) {
            setState(VoicePhase.SPEAKING, "Sean 在说…")
        }
        startMic() // speaking 期间持续监听（教程：AI 说话期间麦克风仍在工作）
        q.speak(id.generationId, text)
    }

    private fun onGenerationDone(gen: String) {
        if (closed) return
        if (gen != active?.generationId) return
        val phase = stateFlow.value.phase
        if (phase == VoicePhase.CLOSING) {
            // 自然挂断：告别在正确 generation 播完后才挂断（教程 §16）
            main.postDelayed({ finishCall() }, 300L)
            return
        }
        restoreVolume()
        stopMic()
        // 有序播放排空 → 回到 listening（冷却 260ms 避开播放尾音）
        main.postDelayed({ listen() }, SPEAK_COOLDOWN_MS)
    }

    // ── Listen 轨（抢话） ──

    private fun startMic() {
        if (micRunning || closed) return
        val minBuf = AudioRecord.getMinBufferSize(
            SAMPLE_RATE, android.media.AudioFormat.CHANNEL_IN_MONO,
            android.media.AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBuf <= 0) return
        val record = runCatching {
            AudioRecord(
                android.media.MediaRecorder.AudioSource.VOICE_COMMUNICATION, // 通话录音模式，系统 AEC 有机会工作（教程 §15.4）
                SAMPLE_RATE,
                android.media.AudioFormat.CHANNEL_IN_MONO,
                android.media.AudioFormat.ENCODING_PCM_16BIT,
                maxOf(minBuf, SAMPLE_RATE * 2)
            )
        }.getOrNull() ?: return
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            return
        }
        audioRecord = record
        micRunning = true
        bargeIn.reset()
        val frameSize = SAMPLE_RATE * FRAME_MS / 1000
        val buffer = ShortArray(frameSize)
        val listener = object : BargeInDetector.Listener {
            override fun onDuck() { duckVolume() }
            override fun onInterrupt() { main.post { interruptTurn("用户抢话") } }
            override fun onRestore() { restoreVolume() }
        }
        micThread = Thread {
            record.startRecording()
            while (micRunning && !closed) {
                val n = runCatching { record.read(buffer, 0, frameSize) }.getOrDefault(0)
                if (n > 0) bargeIn.push(buffer.copyOf(n), SAMPLE_RATE, listener)
            }
            runCatching { record.stop() }
            record.release()
        }.apply {
            priority = Thread.NORM_PRIORITY + 1
            start()
        }
    }

    private fun stopMic() {
        micRunning = false
        micThread = null
        audioRecord = null
        restoreVolume()
    }

    /** 抢话/挂断语义分离（教程 §14）：这只结束当前回复，会话还在。 */
    fun interruptTurn(reason: String) {
        val old = active ?: return
        Log.i(TAG, "{\"event\":\"barge_in\",\"reason\":\"$reason\",\"generation\":\"${old.generationId}\"}")
        queue?.cancelAll(old.generationId)
        active = null // 旧代身份作废，迟到结果全部丢弃
        restoreVolume()
        stopMic()
        if (!closed) main.postDelayed({ listen() }, 200L)
    }

    // ── 自然挂断（教程 §16：先告别，等队列排空，再挂断） ──

    private fun isFarewell(text: String): Boolean =
        listOf("挂了", "挂断", "拜拜", "再见", "先这样", "不聊了").any { text.contains(it) }

    private fun closeGracefully(userText: String) {
        setState(VoicePhase.CLOSING, "Sean 说再见…", heard = userText)
        turnSequence += 1
        val id = CallIdentity(callId, turnSequence, CallIdentityFactory.newGeneration(turnSequence))
        active = id
        queue?.beginGeneration(id.generationId)
        segmenter = TtsSegmenter()
        val w = wiring ?: run { finishCall(); return }
        w.engine.send(
            HomecomingChatEngine.ChatCommand(
                id.requestId(),
                HomecomingChatWiring.TIMELINE,
                HomecomingChatWiring.RESPONDER,
                HomecomingChatWiring.USER,
                userText + "（Yuri 在语音电话里说要走。你是 Sean，回一句告别：温柔简短，20 字以内。）",
                "main",
                w.mainModelKey(), "", ""
            ),
            engineObserver(id)
        )
        // 硬截止：告别播不出来的话也不能永远卡着
        main.postDelayed({
            if (stateFlow.value.phase == VoicePhase.CLOSING) finishCall()
        }, FAREWELL_DEADLINE_MS)
    }

    private fun finishCall() {
        close()
        onEnded()
    }

    /** UI 挂断键：立刻结束整场会话（与自然抢话不同语义，教程 §14）。 */
    fun hangUpNow() {
        Log.i(TAG, "{\"event\":\"hangup\",\"call_id\":\"$callId\",\"mode\":\"manual\"}")
        finishCall()
    }

    // ── duck / restore（音量仲裁） ──

    private fun duckVolume() {
        if (duckedVolume != null) return
        runCatching {
            val cur = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            duckedVolume = cur
            audioManager.setStreamVolume(
                AudioManager.STREAM_MUSIC, maxOf(1, cur / 3), 0
            )
        }
    }

    private fun restoreVolume() {
        val v = duckedVolume ?: return
        duckedVolume = null
        runCatching { audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, v, 0) }
    }

    private fun requestAudioFocus() {
        runCatching {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .build()
            focusRequest = req
            am.requestAudioFocus(req)
        }
    }

    private fun abandonAudioFocus() {
        runCatching {
            focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
            focusRequest = null
        }
    }

    // ── 分阶段延迟日志（教程 §3/§22：不只看总耗时） ──

    private fun logLatency() {
        if (turnStartAt == 0L) return
        val gen = active?.generationId ?: ""
        Log.i(
            TAG,
            "{\"event\":\"voice_latency\",\"call_id\":\"$callId\",\"generation\":\"$gen\"," +
                "\"asr_ms\":${if (asrDoneAt > 0) asrDoneAt - turnStartAt else -1}," +
                "\"model_first_text_ms\":${if (firstTextAt > 0) firstTextAt - turnStartAt else -1}," +
                "\"tts_first_audio_ms\":${if (firstAudioAt > 0) firstAudioAt - turnStartAt else -1}," +
                "\"first_sound_ms\":${if (firstAudioAt > 0) firstAudioAt - turnStartAt else -1}}"
        )
    }
}
