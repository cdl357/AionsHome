package com.aion.chat.compose.voice

/**
 * 两阶段抢话检测（教程 §14）：AI 说话期间麦克风继续工作。
 * 240ms 连续人声 → duck（压低 TTS 音量）；520ms → interrupt（确认抢话，取消旧生成）；
 * 短促误触 160ms 消失 → restore（恢复音量）。
 * 预卷 PCM（教程约 1s）暂不灌回 ASR——系统识别器自带录音，预卷留给后续本地 VAD 版本。
 */
class BargeInDetector(
    private val duckMs: Float = 240f,
    private val interruptMs: Float = 520f,
    private val restoreMs: Float = 160f,
    private val speechRms: Float = 0.020f
) {
    interface Listener {
        fun onDuck()
        fun onInterrupt()
        fun onRestore()
    }

    private var voicedMs = 0f
    private var silentMs = 0f
    private var ducked = false

    fun push(frame: ShortArray, sampleRate: Int, listener: Listener) {
        val ms = frame.size * 1000f / sampleRate
        var sum = 0.0
        for (v in frame) {
            val s = v / 32768f
            sum += s.toDouble() * s
        }
        val rms = kotlin.math.sqrt(sum / frame.size).toFloat()
        if (rms >= speechRms) {
            voicedMs += ms
            silentMs = 0f
            if (!ducked && voicedMs >= duckMs) {
                ducked = true
                listener.onDuck()
            }
            if (voicedMs >= interruptMs) {
                listener.onInterrupt()
            }
        } else {
            silentMs += ms
            if (ducked && silentMs >= restoreMs) {
                ducked = false
                voicedMs = 0f
                silentMs = 0f
                listener.onRestore()
            }
        }
    }

    fun reset() {
        voicedMs = 0f
        silentMs = 0f
        ducked = false
    }
}
