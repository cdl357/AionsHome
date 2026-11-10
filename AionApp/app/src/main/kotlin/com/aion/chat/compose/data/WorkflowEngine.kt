package com.aion.chat.compose.data

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.PowerManager
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * 工作流引擎（照橘瓣 WorkflowEngine 的生命周期，扁平版）：
 * fire(id) → 启用检查 → 冷却门（锚点=最近真实触发）→ 每日上限 → 条件 AND → 顺序执行动作
 * （每个动作 = 现有工具注册表里的一次调用，带独立超时）→ 记录流水。
 *
 * 与橘瓣一致的三条安全线：
 * - workflow_* 工具不能作为动作（防链式递归），JSON 层已拒
 * - 每个工作流一把锁，两次并发触发（WiFi 抖动等）不会竞争每日计数
 * - 动作超时独立计（1..600s），超时即整次失败，已产出的输出保留进历史
 */
object WorkflowEngine {
    private const val TAG = "WorkflowEngine"

    data class FireOutcome(val status: String, val error: String?, val summary: String)

    private val locks = ConcurrentHashMap<String, ReentrantLock>()
    private fun lockFor(id: String) = locks.computeIfAbsent(id) { ReentrantLock() }

    fun forget(id: String) { locks.remove(id) }

    /**
     * 触发入口：触发器/手动/Run now 全走这里。在 IO 线程调用（内部 runBlocking 跑动作超时）。
     * totalBudgetMs > 0 时整次触发的动作总耗时封顶（v10 P2：聊天回合里的 workflow_run 不能被
     * N 个 ×600s 动作卡到小时级；后台触发不给预算=不限）。
     */
    fun fire(context: Context, id: String, totalBudgetMs: Long = 0L): FireOutcome = lockFor(id).withLock {
        val app = context.applicationContext
        val started = System.currentTimeMillis()
        val firedAt = started
        try {
            kotlinx.coroutines.runBlocking { fireLocked(app, id, firedAt, started, totalBudgetMs) }
        } catch (t: Throwable) {
            Log.w(TAG, "fire $id crashed", t)
            val outcome = FireOutcome("FAILED", "engine_crash: ${t.message?.take(160)}", "")
            WorkflowStore.recordFire(app, id, firedAt, outcome.status, 0, outcome.error)
            outcome
        }
    }

    private suspend fun fireLocked(
        app: Context, id: String, firedAt: Long, started: Long, totalBudgetMs: Long,
    ): FireOutcome {
        val row = WorkflowStore.getById(app, id)
            ?: return FireOutcome("FAILED", "workflow_not_found", "")
        if (!row.enabled) {
            return persist(app, id, firedAt, started, "SKIPPED_DISABLED", null, "")
        }
        val def = try { JSONObject(row.definitionJson) } catch (e: Exception) {
            return persist(app, id, firedAt, started, "FAILED", "definition_corrupt: ${e.message?.take(120)}", "")
        }

        // 冷却门：锚点=运行流水里的真实触发 与 按 name 的持久锚（v10 P1：删了重建同频控不重置）取大者
        val cooldown = def.optInt("cooldown_seconds", 0)
        if (cooldown > 0) {
            val last = listOfNotNull(
                WorkflowStore.lastActualFireAtMs(app, id), nameAnchor(app, row.name)
            ).maxOrNull()
            if (last != null && firedAt < last + cooldown * 1000L) {
                return persist(app, id, firedAt, started, "SKIPPED_COOLDOWN", null, "")
            }
        }
        // 每日上限
        val cap = if (def.has("max_runs_per_day")) def.optInt("max_runs_per_day", -1) else -1
        if (cap > 0) {
            val today = java.time.LocalDate.now().toString()
            val used = if (row.runsTodayDate == today) row.runsTodayCount else 0
            if (used >= cap) {
                return persist(app, id, firedAt, started, "SKIPPED_DAILY_CAP", null, "")
            }
        }
        // 条件（AND + invert）
        val conditions = def.optJSONArray("conditions")
        if (conditions != null && conditions.length() > 0) {
            val ctx = snapshot(app)
            for (i in 0 until conditions.length()) {
                val c = conditions.optJSONObject(i) ?: continue
                val passed = try { ConditionEval.evaluate(c, ctx) } catch (t: Throwable) {
                    Log.w(TAG, "condition $i eval failed", t); false
                }
                val inverted = c.optBoolean("invert", false)
                if (passed == inverted) {
                    return persist(app, id, firedAt, started, "SKIPPED_CONDITIONS",
                        "condition[$i] ${c.optString("type")} ${if (inverted) "invert命中" else "未通过"}", "")
                }
            }
        }
        // 顺序执行动作（总预算内逐个扣减：聊天回合触发时封顶，后台触发不限）
        val actions = def.optJSONArray("actions") ?: org.json.JSONArray()
        val outputs = StringBuilder()
        for (i in 0 until actions.length()) {
            val a = actions.optJSONObject(i) ?: continue
            val tool = a.optString("tool")
            val args = a.optJSONObject("args")?.toString() ?: "{}"
            var timeoutS = a.optInt("timeout_seconds", 60).coerceIn(1, WorkflowJson.MAX_TIMEOUT_S)
            if (totalBudgetMs > 0) {
                val remainingMs = totalBudgetMs - (System.currentTimeMillis() - started)
                if (remainingMs <= 0) {
                    return persist(app, id, firedAt, started, "FAILED",
                        "预算用尽（${totalBudgetMs / 1000}s），动作 $i 未执行", outputs.toString())
                }
                timeoutS = minOf(timeoutS, (remainingMs / 1000L).toInt().coerceAtLeast(1))
            }
            if (tool.startsWith("workflow_")) {
                return persist(app, id, firedAt, started, "FAILED", "action $i: workflow_* 禁止作为动作", outputs.toString())
            }
            val text = try {
                val out = withTimeoutOrNull(timeoutS * 1000L) {
                    withContext(Dispatchers.IO) { dispatchTool(app, tool, args) }
                }
                out ?: run {
                    return persist(app, id, firedAt, started, "FAILED",
                        "action $i ($tool) 超过 ${timeoutS}s", outputs.toString())
                }
            } catch (t: Throwable) {
                return persist(app, id, firedAt, started, "FAILED",
                    "action $i: ${t.javaClass.simpleName}: ${t.message.orEmpty().take(300)}", outputs.toString())
            }
            if (outputs.length < 4000) outputs.append("[$i] ").append(tool).append(": ")
                .append(text.take(200)).append('\n')
        }
        return persist(app, id, firedAt, started, "SUCCESS", null, outputs.toString().take(2000))
    }

    private fun persist(
        app: Context, id: String, firedAt: Long, started: Long,
        status: String, error: String?, summary: String,
    ): FireOutcome {
        val duration = System.currentTimeMillis() - started
        runCatching { WorkflowStore.recordFire(app, id, firedAt, status, duration, error) }
        // 真实触发（SUCCESS/FAILED）时刷新按 name 的持久锚——删除工作流清不掉它（v10 P1）
        if (status == "SUCCESS" || status == "FAILED") {
            runCatching {
                app.getSharedPreferences("workflow_trigger", Context.MODE_PRIVATE)
                    .edit()
                    .putLong("wflast_" + Integer.toHexString(app.let { wfName(app, id) }.hashCode()), firedAt)
                    .apply()
            }
        }
        return FireOutcome(status, error, summary)
    }

    private fun wfName(app: Context, id: String): String =
        WorkflowStore.getById(app, id)?.name ?: id

    private fun nameAnchor(app: Context, name: String): Long? {
        val v = app.getSharedPreferences("workflow_trigger", Context.MODE_PRIVATE)
            .getLong("wflast_" + Integer.toHexString(name.hashCode()), -1L)
        return if (v > 0) v else null
    }

    /**
     * 工具分发：与聊天轮内同一顺序（Memory → MCP），工作流动作复用完全相同的工具面。
     * 返回结果文本（失败时抛异常，由调用方记为 FAILED）。
     */
    fun dispatchTool(app: Context, tool: String, argsJson: String): String {
        if (MemoryTools.handles(tool) && MemoryTools.enabled()) {
            val r = MemoryTools.dispatch(tool, argsJson)
            RequestLog.add(null, "WF", tool, 200, 0, "", 0, 0)
            return r.ifEmpty { "（完成）" }
        }
        val cr = McpClient.callBySafeName(tool, argsJson)
        RequestLog.add(null, "WF", tool, if (cr.ok) 200 else 500, 0, if (cr.ok) "" else cr.err, 0, 0)
        if (!cr.ok) throw IllegalStateException("tool_failed: ${cr.err.take(200)}")
        return cr.text
    }

    // ── 条件求值上下文（橘瓣 ContextProvider 的惰性快照，扁平版一次性全取，字段都是廉价读） ──

    data class Ctx(
        val nowMs: Long,
        val batteryLevel: Int?,
        val isCharging: Boolean,
        val wifiSsid: String?,
        val screenOn: Boolean,
        val lastChatMs: Long?,
    )

    fun snapshot(app: Context): Ctx {
        val bi = runCatching {
            val i = app.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val level = i?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = i?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            val st = i?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            val chg = st == BatteryManager.BATTERY_STATUS_CHARGING ||
                    st == BatteryManager.BATTERY_STATUS_FULL
            (if (level >= 0 && scale > 0) level * 100 / scale else null) to chg
        }.getOrNull()
        val battery = bi?.first
        val charging = bi?.second ?: false
        val pm = app.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val screen = runCatching { pm?.isInteractive ?: false }.getOrDefault(false)
        val ssid = runCatching {
            val wm = app.getSystemService(Context.WIFI_SERVICE) as? android.net.wifi.WifiManager
            val s = wm?.connectionInfo?.ssid
            if (s == null || s == "<unknown ssid>") null else s.removeSurrounding("\"")
        }.getOrNull()
        val lastChat = runCatching {
            com.aion.chat.homecoming.HomecomingChatRepository.lastChatAtMs(app)
        }.getOrNull()
        return Ctx(System.currentTimeMillis(), battery, charging, ssid, screen,
            if (lastChat != null && lastChat > 0) lastChat else null)
    }

    /** 条件求值（纯函数，JSON 形状与 WorkflowJson 校验一致） */
    object ConditionEval {
        fun evaluate(c: JSONObject, ctx: Ctx): Boolean = when (c.optString("type")) {
            "time_between" -> {
                val now = java.time.LocalTime.now()
                val s = java.time.LocalTime.parse(c.getString("start"))
                val e = java.time.LocalTime.parse(c.getString("end"))
                if (s <= e) now in s..e else now >= s || now <= e   // 跨午夜（照橘瓣）
            }
            "day_of_week_in" -> {
                val days = c.optJSONArray("days")
                val iso = java.time.LocalDate.now().dayOfWeek.value
                (0 until (days?.length() ?: 0)).any { days!!.optInt(it) == iso }
            }
            "wifi_ssid_is" -> ctx.wifiSsid != null && ctx.wifiSsid == c.optString("ssid")
            "wifi_ssid_in" -> {
                val ss = c.optJSONArray("ssids")
                ctx.wifiSsid != null && (0 until (ss?.length() ?: 0)).any { ss!!.optString(it) == ctx.wifiSsid }
            }
            "battery_above" -> (ctx.batteryLevel ?: -1) >= c.optInt("percent", 101)
            "battery_below" -> ctx.batteryLevel != null && ctx.batteryLevel < c.optInt("percent", -1)
            "is_charging" -> ctx.isCharging
            "is_not_charging" -> !ctx.isCharging
            "screen_is_on" -> ctx.screenOn
            "screen_is_off" -> !ctx.screenOn
            "last_chat_ago" -> {
                val last = ctx.lastChatMs
                last == null || (ctx.nowMs - last) >= c.optLong("minutes", 0) * 60_000L
            }
            else -> false
        }
    }
}
