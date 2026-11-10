package com.aion.chat.compose.data

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * 工作流触发器（照橘瓣 TriggerRegistry 的"按需注册"电量纪律，扁平版）：
 * - sync()：读启用中的工作流 → 有哪类触发器才注册哪类广播接收器，一个都没有就全注销 + 停掉 cron 扫描
 * - 广播类：电源插拔 / 亮灭屏 / 耳机插拔 / WiFi 状态 / 开机（manifest 静态注册 WorkflowBootReceiver）
 * - 电池跃迁：BATTERY_CHANGED 里拿电量，和上次电量（prefs）比较跨阈值才触发（照橘瓣 transition 语义）
 * - 时间类：WorkManager 15 分钟扫描一次（time_of_day/cron 粒度=分钟，@every 最小 60s）
 * - 通知类：AionNotificationListenerService.onNotificationPosted → onNotification()
 * - 前台 App：AionAccessibilityService.onAccessibilityEvent → onAppLaunch()
 */
object WorkflowTriggers {
    private const val TAG = "WorkflowTriggers"
    private const val PREFS = "workflow_trigger"
    private const val CRON_WORK = "workflow_cron_sweep"
    private const val BATT_KEY = "last_battery_level"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // ── 对外钩子（服务/接收器调用） ──

    @JvmStatic fun onPowerConnected(c: Context) = fireMatches(c, "power_connected")
    @JvmStatic fun onPowerDisconnected(c: Context) = fireMatches(c, "power_disconnected")
    @JvmStatic fun onScreenOn(c: Context) = fireMatches(c, "screen_on")
    @JvmStatic fun onScreenOff(c: Context) = fireMatches(c, "screen_off")
    @JvmStatic fun onHeadphones(c: Context, plugged: Boolean) =
        fireMatches(c, if (plugged) "headphones_plugged" else "headphones_unplugged")
    @JvmStatic fun onBoot(c: Context) { sync(c); fireMatches(c, "boot_completed") }

    fun onWifiStateChanged(c: Context) {
        val app = c.applicationContext
        // WiFi 开关翻转让 SSID 读数不稳，交给条件/触发器自身语义判断：connected=WiFi 已启用
        val wm = app.getSystemService(Context.WIFI_SERVICE) as? android.net.wifi.WifiManager
        val enabled = wm?.isWifiEnabled ?: false
        fireMatches(app, if (enabled) "wifi_connected" else "wifi_disconnected")
    }

    fun onBatteryChanged(c: Context, level: Int) {
        val app = c.applicationContext
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val last = prefs.getInt(BATT_KEY, -1)
        prefs.edit().putInt(BATT_KEY, level).apply()
        if (last == -1 || last == level) return
        scope.launch {
            val workflows = runCatching { WorkflowStore.listAll(app, enabledOnly = true) }.getOrNull() ?: return@launch
            for (w in workflows) {
                val def = parseDef(w.definitionJson) ?: continue
                val t = def.optJSONObject("trigger") ?: continue
                val type = t.optString("type")
                val th = t.optInt("threshold_percent", -1)
                val hit = (type == "battery_below" && last >= th && level < th) ||
                        (type == "battery_above" && last < th && level >= th)
                if (hit && th > 0) WorkflowEngine.fire(app, w.id)
            }
        }
    }

    @JvmStatic
    fun onNotification(c: Context, pkg: String?, title: String?, text: String?) {
        val app = c.applicationContext
        if (pkg.isNullOrBlank() && title.isNullOrBlank() && text.isNullOrBlank()) return
        // 读库+正则全部下 IO（v10 P2：通知回调在主线程）
        scope.launch {
            val workflows = runCatching { WorkflowStore.listAll(app, enabledOnly = true) }.getOrNull() ?: return@launch
            for (w in workflows) {
                val def = parseDef(w.definitionJson) ?: continue
                val t = def.optJSONObject("trigger") ?: continue
                if (t.optString("type") != "notification_received") continue
                val pkgOk = t.optString("package_name", "").takeIf { it.isNotEmpty() }
                    ?.let { pkg?.contains(it, ignoreCase = true) == true } ?: true
                val titleOk = matchesField(t, "title_contains", "title_matches", title)
                val textOk = matchesField(t, "text_contains", "text_matches", text)
                if (pkgOk && titleOk && textOk) WorkflowEngine.fire(app, w.id)
            }
        }
    }

    @JvmStatic
    fun onAppLaunch(c: Context, pkg: String?) {
        val app = c.applicationContext
        if (pkg.isNullOrBlank()) return
        scope.launch {
            val workflows = runCatching { WorkflowStore.listAll(app, enabledOnly = true) }.getOrNull() ?: return@launch
            for (w in workflows) {
                val def = parseDef(w.definitionJson) ?: continue
                val t = def.optJSONObject("trigger") ?: continue
                if (t.optString("type") != "app_launched") continue
                val want = t.optString("package_name")
                if (want.isNotEmpty() && (pkg == want || pkg.endsWith(".$want") || pkg.contains(want))) {
                    WorkflowEngine.fire(app, w.id)
                }
            }
        }
    }

    // ── cron 扫描（WorkManager 15 分钟一档；分钟级对齐靠 fired_key 去重） ──

    class CronWorker(private val ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
        override suspend fun doWork(): Result {
            sweep(applicationContext)
            return Result.success()
        }
    }

    fun sweep(app: Context) {
        val now = java.time.LocalDateTime.now()
        val minuteKey = now.format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmm"))
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val workflows = WorkflowStore.listAll(app, enabledOnly = true)
        for (w in workflows) {
            val def = parseDef(w.definitionJson) ?: continue
            val t = def.optJSONObject("trigger") ?: continue
            if (t.optString("type") != "time_cron") continue
            val key = w.id + ":" + minuteKey
            if (prefs.getString("fired_$key", null) != null) continue
            val due = when {
                t.optString("time_of_day").isNotEmpty() -> {
                    val dayOk = run {
                        val days = t.optJSONArray("days_of_week")
                        if (days == null || days.length() == 0) true
                        else (0 until days.length()).any { days.optInt(it) == now.dayOfWeek.value }
                    }
                    dayOk && t.optString("time_of_day") == now.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))
                }
                t.optString("cron").isNotEmpty() -> cronMatches(t.optString("cron"), now)
                else -> false
            }
            if (due) {
                prefs.edit().putString("fired_$key", "1").apply()
                launchFire(app, w.id)
            }
        }
        // fired_key 会慢慢堆，顺手清 2 天前的（按 key 里嵌入的时间戳比，v10 修：原字符串比方向反了永不清理）
        val cutoff = now.minusDays(2).format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmm"))
        val editor = prefs.edit()
        prefs.all.keys.filter { it.startsWith("fired_") && it.substringAfterLast(':') < cutoff }
            .forEach { editor.remove(it) }
        editor.apply()
    }

    /** 5 字段 cron（分 时 日 月 周）最小实现：星号、步进（星号斜杠n）、数字、区间、枚举 */
    internal fun cronMatches(cron: String, now: java.time.LocalDateTime): Boolean {
        val fields = cron.trim().split(Regex("\\s+"))
        if (fields.size != 5) return false
        // 周字段按标准 cron：0=周日..6=周六（java.time 是 1=周一..7=周日，%7 对齐）
        val values = intArrayOf(now.minute, now.hour, now.dayOfMonth, now.monthValue, now.dayOfWeek.value % 7)
        for (i in 0..4) {
            if (!fieldMatches(fields[i], values[i])) return false
        }
        return true
    }

    private fun fieldMatches(f: String, v: Int): Boolean {
        return f.split(",").any { part ->
            when {
                part == "*" -> true
                part.startsWith("*/") -> part.substring(2).toIntOrNull()?.let { n -> n > 0 && v % n == 0 } ?: false
                part.contains("-") -> {
                    val (a, b) = part.split("-", limit = 2).map { it.toIntOrNull() ?: return false }
                    v in a..b
                }
                else -> part.toIntOrNull() == v
            }
        }
    }

    // ── 注册同步 ──

    private var broadcastRegistered = false
    private var wifiReceiver: BroadcastReceiver? = null

    private val eventReceiver = object : BroadcastReceiver() {
        @SuppressLint("UnsafeProtectedBroadcastReceiver")
        override fun onReceive(context: Context, intent: Intent) {
            val app = context.applicationContext
            when (intent.action) {
                Intent.ACTION_POWER_CONNECTED -> onPowerConnected(app)
                Intent.ACTION_POWER_DISCONNECTED -> onPowerDisconnected(app)
                Intent.ACTION_SCREEN_ON -> onScreenOn(app)
                Intent.ACTION_SCREEN_OFF -> onScreenOff(app)
                Intent.ACTION_HEADSET_PLUG -> {
                    if (Build.VERSION.SDK_INT < 31) onHeadphones(app, intent.getIntExtra("state", 0) == 1)
                }
                Intent.ACTION_BATTERY_CHANGED -> {
                    val level = intent.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1)
                    val scale = intent.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1)
                    if (level >= 0 && scale > 0) onBatteryChanged(app, level * 100 / scale)
                }
            }
        }
    }

    fun sync(context: Context) {
        val app = context.applicationContext
        val enabled = WorkflowStore.listAll(app, enabledOnly = true)
        val needsBroadcast = mutableSetOf<String>()
        var needsWifi = false
        for (w in enabled) {
            val t = parseDef(w.definitionJson)?.optJSONObject("trigger") ?: continue
            when (t.optString("type")) {
                "power_connected", "power_disconnected" -> {
                    needsBroadcast.add(Intent.ACTION_POWER_CONNECTED)
                    needsBroadcast.add(Intent.ACTION_POWER_DISCONNECTED)
                }
                "screen_on", "screen_off" -> {
                    needsBroadcast.add(Intent.ACTION_SCREEN_ON)
                    needsBroadcast.add(Intent.ACTION_SCREEN_OFF)
                }
                "headphones_plugged", "headphones_unplugged" -> needsBroadcast.add(Intent.ACTION_HEADSET_PLUG)
                "battery_below", "battery_above" -> needsBroadcast.add(Intent.ACTION_BATTERY_CHANGED)
                "wifi_connected", "wifi_disconnected" -> needsWifi = true
            }
        }
        // 广播接收器：先卸后挂，避免重复注册炸 IllegalArgumentException
        runCatching { if (broadcastRegistered) app.unregisterReceiver(eventReceiver) }
        broadcastRegistered = if (needsBroadcast.isEmpty()) false else runCatching {
            val filt = IntentFilter().apply { for (a in needsBroadcast) addAction(a) }
            app.registerReceiver(eventReceiver, filt)
            true
        }.getOrDefault(false)
        // WiFi：STATE_CHANGE 广播（读 SSID 需要 manifest 里的 ACCESS_WIFI_STATE；精细 SSID 匹配交给条件层）
        runCatching {
            wifiReceiver?.let { runCatching { app.unregisterReceiver(it) } }
            wifiReceiver = if (needsWifi) {
                val r = object : BroadcastReceiver() {
                    override fun onReceive(context: Context, intent: Intent) = onWifiStateChanged(context)
                }
                app.registerReceiver(r, IntentFilter(android.net.wifi.WifiManager.NETWORK_STATE_CHANGED_ACTION))
                r
            } else null
        }.onFailure { Log.w(TAG, "register wifi receiver failed", it) }
        // cron 扫描：有时间触发器才挂
        runCatching {
            val hasTime = enabled.any {
                parseDef(it.definitionJson)?.optJSONObject("trigger")?.optString("type") == "time_cron"
            }
            val wm = WorkManager.getInstance(app)
            if (hasTime) {
                wm.enqueueUniquePeriodicWork(CRON_WORK, ExistingPeriodicWorkPolicy.KEEP,
                    PeriodicWorkRequestBuilder<CronWorker>(15, TimeUnit.MINUTES).build())
            } else {
                wm.cancelUniqueWork(CRON_WORK)
            }
        }.onFailure { Log.w(TAG, "cron schedule failed", it) }
    }

    // ── 内部 ──

    private fun parseDef(json: String): JSONObject? = runCatching { JSONObject(json) }.getOrNull()

    private fun launchFire(app: Context, id: String) {
        scope.launch {
            val outcome = runCatching { WorkflowEngine.fire(app, id) }.getOrNull() ?: return@launch
            Log.i(TAG, "wf $id → ${outcome.status} ${outcome.error ?: ""}")
        }
    }

    private fun fireMatches(app: Context, type: String) {
        // 匹配+读库全部下 IO（v10 P2：广播回调在主线程，不能在主线程跑 SQL）
        scope.launch {
            val workflows = runCatching { WorkflowStore.listAll(app, enabledOnly = true) }.getOrNull() ?: return@launch
            for (w in workflows) {
                val t = parseDef(w.definitionJson)?.optJSONObject("trigger") ?: continue
                if (t.optString("type") == type) WorkflowEngine.fire(app, w.id)
            }
        }
    }

    private fun matchesField(t: JSONObject, containsKey: String, matchesKey: String, value: String?): Boolean {
        val has = t.has(containsKey) || t.has(matchesKey)
        if (!has) return true
        val v = value.orEmpty()
        val containsOk = t.optString(containsKey, "").takeIf { it.isNotEmpty() }
            ?.let { v.contains(it, ignoreCase = true) } ?: true
        val matchesOk = t.optString(matchesKey, "").takeIf { it.isNotEmpty() }
            ?.let { p -> runCatching { Regex(p).containsMatchIn(v) }.getOrDefault(false) } ?: true
        return containsOk && matchesOk
    }
}

/** 开机重挂触发器（manifest 静态注册；时间扫描器由 WorkManager 自行恢复） */
class WorkflowBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            runCatching { WorkflowTriggers.sync(context.applicationContext) }
        }
    }
}
