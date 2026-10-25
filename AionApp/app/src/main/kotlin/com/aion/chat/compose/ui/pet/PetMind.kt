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
        val intentKey: String,   // 当前最强驱力（戳一戳联动用）
        val cs: String = "awake",// consciousness 原始值（sleeping/drowsy/frozen/awake）
        val intentZh: String = ""// 最强驱力的中文词（主页状态行展示用）
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
        val topDrive = st.drives.maxByOrNull { it.v }
        val top = topDrive?.key ?: "duty"
        val topZh = topDrive?.zh ?: ""
        val cs = st.consciousness.lowercase()
        return when {
            cs == "asleep" || cs == "sleeping" -> Mind("sleepy", false, top, cs, topZh)
            cs == "drowsy" -> Mind("sleepy", false, top, cs, topZh)
            cs == "frozen" -> Mind("idle", true, top, cs, topZh)
            st.fatigue > 0.7 -> Mind("sleepy", false, top, "awake", topZh)
            (drives["possess"] ?: 0f) > 0.6f && (drives["social"] ?: 1f) < 0.4f ||
                (drives["crave"] ?: 0f) > 0.6f && (drives["social"] ?: 1f) < 0.4f
            -> Mind("tsundere", false, top, "awake", topZh)
            else -> Mind(when (top) {
                "possess", "monitor", "crave", "grieve" -> "missing"
                "libido" -> "love"
                "share", "curiosity" -> "happy"
                "social" -> "waving"
                "boredom" -> "sleepy"
                "anger" -> "angry"
                else -> "idle"   // duty / reflection / 未知
            }, false, top, "awake", topZh)
        }
    }

    /** 主页状态行的人话：先说意识（睡着/犯困/定住），再说驱力。 */
    fun label(m: Mind): String = when {
        m.frozen -> "他定住了"
        m.cs == "asleep" || m.cs == "sleeping" -> "他睡着了"
        m.cs == "drowsy" -> "他犯困"
        m.action == "sleepy" -> "他累了，打着盹"
        m.intentZh.isNotBlank() -> "他在" + m.intentZh
        else -> "他醒着"
    }

    /** 他此刻是不是睡/困状态（戳一戳旁白带困意用）。 */
    fun isSleepy(m: Mind): Boolean =
        m.cs == "asleep" || m.cs == "sleeping" || m.cs == "drowsy" || m.action == "sleepy"

    /**
     * 戳一戳的动作（文档 §6 + Yuri 需求：部位联动）：
     * anger→angry；咬一口→轻轻炸毛；腰→躲（tsundere）；耳朵/颈窝→害羞背身；
     * 头发/脸蛋/下巴→happy；手→waving；possess/crave→happy（love 素材未到位前用 happy）。
     */
    fun pokeAction(intentKey: String, spot: String = "", verb: String = ""): String = when {
        intentKey == "anger" -> "angry"
        verb == "咬了一口" -> "angry"
        spot == "腰" -> "tsundere"
        spot == "耳朵" || spot == "颈窝" -> "tsundere"
        spot == "头发" || spot == "脸蛋" || spot == "下巴" -> "happy"
        spot == "手" -> "waving"
        intentKey == "possess" || intentKey == "crave" -> "happy"
        else -> "waving"
    }
}
