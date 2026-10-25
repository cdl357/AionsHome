package com.aion.chat.compose.ui.pet

import com.aion.chat.compose.data.XinchaoClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 桌宠心潮映射（桌宠交接文档 §5）：
 * - consciousness 盖过一切：asleep/sleeping→sleepy（sleep_prone 无素材）、drowsy→sleepy、frozen→idle 定帧
 * - fatigue > 0.7 → sleepy（盖过驱力，仅次于意识）
 * - tsundere：possess/crave > 0.6 且 social < 0.4（想她但嘴上不说）→ 放在驱力映射之前
 * - intent = 最强驱力键：possess/monitor/crave/grieve→missing、libido→love、share/curiosity→happy、
 *   social→waving、boredom→sleepy、anger→angry、duty/reflection→idle
 * - missing/love 素材未到位：动作名照发，PetSprite 层回退 idle 播（绝不回退机械小人）
 * - 请求失败保持上一个动作；冷启动 idle
 */
object PetMind {

    data class Mind(
        val action: String,      // 播哪套动作（missing/love 由 PetSprite 回退 idle 显示）
        val frozen: Boolean,     // consciousness=frozen：停在第 1 帧
        val intentKey: String    // 当前最强驱力（戳一戳联动用）
    )

    private val _state = MutableStateFlow(Mind("idle", false, "duty"))
    val state: StateFlow<Mind> = _state

    /** 拉一次心潮并更新动作；失败静默保持上一个（文档：不要闪回 idle）。 */
    suspend fun refresh() {
        val st = runCatching { XinchaoClient.fetchState() }.getOrNull() ?: return
        _state.value = map(st)
    }

    fun map(st: XinchaoClient.RemoteState): Mind {
        val drives = st.drives.associate { it.key to it.v }
        val top = st.drives.maxByOrNull { it.v }?.key ?: "duty"
        val cs = st.consciousness.lowercase()
        return when {
            cs == "asleep" || cs == "sleeping" -> Mind("sleepy", false, top)
            cs == "drowsy" -> Mind("sleepy", false, top)
            cs == "frozen" -> Mind("idle", true, top)
            st.fatigue > 0.7 -> Mind("sleepy", false, top)
            (drives["possess"] ?: 0f) > 0.6f && (drives["social"] ?: 1f) < 0.4f ||
                (drives["crave"] ?: 0f) > 0.6f && (drives["social"] ?: 1f) < 0.4f
            -> Mind("tsundere", false, top)
            else -> Mind(when (top) {
                "possess", "monitor", "crave", "grieve" -> "missing"
                "libido" -> "love"
                "share", "curiosity" -> "happy"
                "social" -> "waving"
                "boredom" -> "sleepy"
                "anger" -> "angry"
                else -> "idle"   // duty / reflection / 未知
            }, false, top)
        }
    }

    /** 戳一戳的动作（文档 §6）：anger→angry；possess/crave→love（无素材用 happy）；其他→waving。 */
    fun pokeAction(intentKey: String): String = when (intentKey) {
        "anger" -> "angry"
        "possess", "crave" -> "happy"   // love 素材未到位前用 happy
        else -> "waving"
    }
}
