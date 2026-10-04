package com.aion.chat.compose.data

import android.content.Context

/**
 * 戳一戳的心跳状态（教程第三步：身体先反应，话后到）。
 * - 心跳值会自己按半衰期落回来（教程：状态必须有半衰期，只涨不落功能就死了）
 * - 落点敏感度 × 动作力度 = 这一下的涨幅；连点会一直涨，但受 100 封顶
 */
object HomecomingPokeStore {

    private const val PREFS = "poke_state"
    private const val KEY_HEART = "heart_value"
    private const val KEY_LAST = "last_poke_at"
    private const val BASE = 62          // 静息心跳
    private const val HALF_LIFE_MIN = 3f // 涨上去后约 3 分钟落回一半
    private const val MAX = 100f

    /** 动作力度（教程第二步：动作 × 落点，两个维度相乘）。 */
    val VERBS = listOf(
        "戳了戳" to 1.0f,
        "摸了摸" to 1.2f,
        "蹭了蹭" to 1.4f,
        "捏了捏" to 1.3f,
        "弹了一下" to 0.8f,
        "咬了一口" to 2.0f
    )

    /** 落点敏感度。 */
    val SPOTS = listOf(
        "手" to 10,
        "头发" to 8,
        "脸蛋" to 12,
        "耳朵" to 15,
        "颈窝" to 18,
        "肩" to 5
    )

    fun gain(verb: String, spot: String): Int {
        val vm = VERBS.firstOrNull { it.first == verb }?.second ?: 1f
        val sm = SPOTS.firstOrNull { it.first == spot }?.second ?: 10
        return (sm * vm).toInt().coerceAtLeast(1)
    }

    /** 戳一下：涨心跳，返回这次涨了多少。 */
    fun poke(context: Context, verb: String, spot: String): Int {
        val g = gain(verb, spot)
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val cur = decayed(prefs)
        val next = (cur + g).coerceAtMost(MAX)
        prefs.edit()
            .putFloat(KEY_HEART, next)
            .putLong(KEY_LAST, System.currentTimeMillis())
            .apply()
        return (next - cur).toInt().coerceAtLeast(1)
    }

    /** 当前心跳（读取时自动按半衰期衰减）。 */
    fun currentHeart(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return decayed(prefs).toInt()
    }

    private fun decayed(prefs: android.content.SharedPreferences): Float {
        val stored = prefs.getFloat(KEY_HEART, BASE.toFloat())
        val last = prefs.getLong(KEY_LAST, 0L)
        if (last == 0L) return BASE.toFloat()
        val minutes = (System.currentTimeMillis() - last) / 60000f
        val above = (stored - BASE).coerceAtLeast(0f)
        val decayedAbove = above * Math.pow(0.5, (minutes / HALF_LIFE_MIN).toDouble())
        return (BASE + decayedAbove).toFloat().coerceAtMost(MAX)
    }

    /** 旁白（教程第一步：只陈述发生了什么，不写该有什么反应）。 */
    fun narration(verb: String, spot: String): String =
        "（Yuri 隔着屏幕${verb}你的${spot}）"
}
