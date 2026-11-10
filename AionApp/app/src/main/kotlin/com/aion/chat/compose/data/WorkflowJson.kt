package com.aion.chat.compose.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * 工作流定义 JSON 解析+校验（照橘瓣 WorkflowJson 的 schema，AionsHome 版用 org.json）。
 *
 * 校验失败抛 IllegalArgumentException，消息面向 LLM 自修复（workflow_create 会把它原样带回）。
 * 本版本支持的触发器/条件是橘瓣全集中"不依赖 Play 服务/无障碍常驻时长统计"的子集：
 * - 触发器：time_cron / manual / power_connected / power_disconnected / battery_below /
 *   battery_above / screen_on / screen_off / boot_completed / headphones_plugged /
 *   headphones_unplugged / wifi_connected / wifi_disconnected / notification_received /
 *   app_launched
 * - 条件：time_between / day_of_week_in / wifi_ssid_is / wifi_ssid_in / battery_above /
 *   battery_below / is_charging / is_not_charging / screen_is_on / screen_is_off /
 *   last_chat_ago（全部支持 invert）
 */
object WorkflowJson {

    val TRIGGERS = setOf(
        "time_cron", "manual", "power_connected", "power_disconnected",
        "battery_below", "battery_above", "screen_on", "screen_off",
        "boot_completed", "headphones_plugged", "headphones_unplugged",
        "wifi_connected", "wifi_disconnected", "notification_received", "app_launched",
    )

    val CONDITIONS = setOf(
        "time_between", "day_of_week_in", "wifi_ssid_is", "wifi_ssid_in",
        "battery_above", "battery_below", "is_charging", "is_not_charging",
        "screen_is_on", "screen_is_off", "last_chat_ago",
    )

    const val MAX_NAME = 80
    const val MAX_DESC = 500
    const val MAX_ACTIONS = 32
    const val MAX_TIMEOUT_S = 600
    const val MAX_COOLDOWN_S = 24 * 60 * 60

    /**
     * 校验并规整一份工作流定义。knownTools = 当前可用的动作工具名集合。
     * 返回规整后的 JSON（保持传入字段 + 默认值补齐），直接作为存储真源。
     */
    fun parse(json: String, knownTools: Set<String>): JSONObject {
        val root = try { JSONObject(json) } catch (e: Exception) {
            throw IllegalArgumentException("definition 不是合法 JSON 对象: ${e.message?.take(120)}")
        }
        val name = root.optString("name").trim()
        require(name.isNotEmpty()) { "缺少 name" }
        require(name.length <= MAX_NAME) { "name 超过 $MAX_NAME 字" }
        root.optNullableString("description")?.let {
            require(it.length <= MAX_DESC) { "description 超过 $MAX_DESC 字" }
        }

        // trigger
        val trigger = root.optJSONObject("trigger")
            ?: throw IllegalArgumentException("缺少 trigger 对象")
        val type = trigger.optString("type")
        require(type in TRIGGERS) {
            "trigger.type='$type' 不支持。可用: $TRIGGERS。" +
                    "geofence_* / bluetooth_* / app_closed / app_foreground_duration 本版本暂未开放"
        }
        when (type) {
            "time_cron" -> {
                val cron = trigger.optNullableString("cron")
                val tod = trigger.optNullableString("time_of_day")
                require(cron != null || tod != null) {
                    "time_cron 需要 cron（5 字段或 \"@every Ns\"，N≥60）或 time_of_day（HH:mm）二选一"
                }
                cron?.let {
                    val isEvery = Regex("^@every \\d+s$").matches(it.trim())
                    require(isEvery || it.trim().split(Regex("\\s+")).size == 5) {
                        "cron 应为 5 字段表达式（周字段 0=周日）或 \"@every Ns\"（N≥60）"
                    }
                }
                tod?.let {
                    require(Regex("^\\d{2}:\\d{2}$").matches(it) && it.substring(0, 2).toInt() < 24
                            && it.substring(3).toInt() < 60) { "time_of_day 格式应为 HH:mm（24 小时制）" }
                }
                val days = trigger.optJSONArray("days_of_week")
                if (days != null) {
                    for (i in 0 until days.length()) {
                        val d = days.optInt(i, -1)
                        require(d in 1..7) { "days_of_week 用 ISO 1..7（1=周一）" }
                    }
                }
            }
            "battery_below", "battery_above" -> {
                val t = trigger.optInt("threshold_percent", -1)
                require(t in 1..100) { "trigger.threshold_percent 需要 1..100" }
            }
            "app_launched" -> {
                require(trigger.optNullableString("package_name") != null) { "app_launched 需要 package_name" }
            }
            "notification_received" -> {
                val has = trigger.has("package_name") || trigger.has("title_contains") ||
                        trigger.has("text_contains") || trigger.has("title_matches") || trigger.has("text_matches")
                require(has) { "notification_received 至少要一个过滤字段（package_name/title_contains/text_contains/title_matches/text_matches）" }
                listOf("title_matches", "text_matches").forEach { k ->
                    trigger.optNullableString(k)?.let { p ->
                        runCatching { Regex(p) }.onFailure {
                            throw IllegalArgumentException("$k 不是合法正则: ${it.message?.take(100)}")
                        }
                    }
                }
            }
        }

        // conditions
        val conditions = root.optJSONArray("conditions") ?: JSONArray()
        for (i in 0 until conditions.length()) {
            val c = conditions.optJSONObject(i) ?: throw IllegalArgumentException("conditions[$i] 必须是对象")
            val ct = c.optString("type")
            require(ct in CONDITIONS) { "conditions[$i].type='$ct' 不支持。可用: $CONDITIONS" }
            when (ct) {
                "time_between" -> {
                    listOf("start", "end").forEach { k ->
                        val v = c.optNullableString(k)
                        require(v != null && Regex("^\\d{2}:\\d{2}$").matches(v)) { "conditions[$i].$k 应为 HH:mm" }
                    }
                }
                "day_of_week_in" -> {
                    val days = c.optJSONArray("days") ?: throw IllegalArgumentException("conditions[$i].days 缺失")
                    require(days.length() > 0) { "conditions[$i].days 不能为空" }
                    for (j in 0 until days.length()) require(days.optInt(j, -1) in 1..7) {
                        "conditions[$i].days 用 ISO 1..7（1=周一）"
                    }
                }
                "battery_above", "battery_below" -> {
                    require(c.optInt("percent", -1) in 1..100) { "conditions[$i].percent 需要 1..100" }
                }
                "wifi_ssid_is" -> require(c.optNullableString("ssid") != null) { "conditions[$i].ssid 缺失" }
                "wifi_ssid_in" -> {
                    val ss = c.optJSONArray("ssids") ?: throw IllegalArgumentException("conditions[$i].ssids 缺失")
                    require(ss.length() > 0) { "conditions[$i].ssids 不能为空" }
                }
                "last_chat_ago" -> require(c.optLong("minutes", -1) > 0) { "conditions[$i].minutes 应为正整数" }
            }
        }

        // actions
        val actions = root.optJSONArray("actions") ?: throw IllegalArgumentException("缺少 actions 数组")
        require(actions.length() in 1..MAX_ACTIONS) { "actions 需要 1..$MAX_ACTIONS 个" }
        for (i in 0 until actions.length()) {
            val a = actions.optJSONObject(i) ?: throw IllegalArgumentException("actions[$i] 必须是对象")
            val tool = a.optString("tool")
            require(tool.isNotEmpty()) { "actions[$i].tool 缺失" }
            require(!tool.startsWith("workflow_")) { "actions[$i]: workflow_* 工具不能作为动作（v1 不支持链式触发）" }
            require(tool in knownTools) {
                "actions[$i].tool='$tool' 不在当前可用工具里。可用: ${knownTools.sorted().take(40)}"
            }
            val t = a.optInt("timeout_seconds", 60)
            require(t in 1..MAX_TIMEOUT_S) { "actions[$i].timeout_seconds 需要 1..$MAX_TIMEOUT_S" }
            if (!a.has("args")) a.put("args", JSONObject())
        }

        // 数值上限 + 频控给底（v10 P1：防注入建高频工作流——冷却至少 60s，每日上限缺省 200）
        val cooldown = root.optInt("cooldown_seconds", 0)
        require(cooldown in 0..MAX_COOLDOWN_S) { "cooldown_seconds 需要 0..$MAX_COOLDOWN_S" }
        root.put("cooldown_seconds", if (cooldown < 60) 60 else cooldown)
        if (root.has("max_runs_per_day")) {
            require(root.optInt("max_runs_per_day", -1) in 1..1000) { "max_runs_per_day 需要 1..1000" }
        } else {
            root.put("max_runs_per_day", 200)
        }
        if (!root.has("enabled")) root.put("enabled", true)
        return root
    }

    /** 取触发器类型的便捷读法 */
    fun triggerType(def: JSONObject): String = def.optJSONObject("trigger")?.optString("type") ?: "unknown"

    fun JSONObject.optNullableString(key: String): String? {
        if (!has(key) || isNull(key)) return null
        val v = optString(key)
        return if (v.isBlank()) null else v
    }
}
