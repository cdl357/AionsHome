package com.aion.chat.compose.voice

import java.util.concurrent.atomic.AtomicInteger

/**
 * 身份协议（教程 §4）：call/turn/generation/seq 缺一不可。
 * 所有异步结果（引擎回调、TTS utterance、音频帧）先验身份，再改状态；
 * 旧 generation 的迟到结果永久丢弃——这是"抢话不串台"的前提。
 */
data class CallIdentity(
    val callId: String,
    val turnSequence: Int,
    val generationId: String
) {
    fun requestId(): String = "$callId/t$turnSequence/$generationId"
}

object CallIdentityFactory {
    private val seq = AtomicInteger(0)
    fun newCallId(): String = "call_" + System.currentTimeMillis().toString(36) + "_" + seq.incrementAndGet()
    fun newGeneration(turnSequence: Int): String = "gen_${turnSequence}_a"
}

/** 事件信封身份匹配（教程 belongsToActiveGeneration 的单代简化版：一次只有一个活跃 generation）。 */
fun belongsToActiveGeneration(eventGeneration: String?, active: CallIdentity?): Boolean =
    active != null && eventGeneration == active.generationId
