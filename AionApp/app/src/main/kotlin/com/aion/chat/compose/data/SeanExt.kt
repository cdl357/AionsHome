package com.aion.chat.compose.data

import android.content.Context

/**
 * 他的能力注入开关（他的配置页 → 扩展管理）：关掉后对应段落会在下次刷新时从 system_prompt 移除。
 * key = sean_ext prefs 的布尔值，默认全开。
 */
object SeanExt {
    val ITEMS = listOf(
        "ext_sticker" to "素材库（他能动手换你的壁纸/背景/头像）",
        "ext_paint" to "画笔（生图：他想画给你看就能画）",
        "ext_cmd" to "即时指令（倒计时药丸，推你去做小事）",
        "ext_study" to "书房（桌上的花和玩偶，你们共有的）"
    )

    fun on(context: Context, key: String): Boolean =
        context.getSharedPreferences("sean_ext", Context.MODE_PRIVATE).getBoolean(key, true)

    fun set(context: Context, key: String, value: Boolean) {
        context.getSharedPreferences("sean_ext", Context.MODE_PRIVATE)
            .edit().putBoolean(key, value).apply()
    }
}
