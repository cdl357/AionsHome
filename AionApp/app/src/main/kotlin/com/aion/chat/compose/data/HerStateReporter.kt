package com.aion.chat.compose.data

import android.Manifest
import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.BatteryManager
import android.os.Build
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

/**
 * 「告诉他我在干什么」状态上报（交接文档《状态上报-GLM交接》）：
 * - 只记她同意的：电量/充电、位置只到区县、今日屏幕时长、各 app 时长、在听的歌、最后解锁时间
 * - 不记：经纬度、街道门牌、app 内容、通知、截图（app 端就不收集）
 * - 走 murmur:8080 免密中转 → 网关（API_SECRET 只存服务器，绝不进 App/仓库）
 * - 开关默认关；关=取消定时任务；30 分钟一次（WorkManager），手动/额外触发 5 分钟节流
 */
object HerStateReporter {

    private val BASES = arrayOf(
        SupabaseClient.RELAY_URL.removeSuffix("/supabase") + "/xinchao",
        SupabaseClient.RELAY_URL_ALT.removeSuffix("/supabase") + "/xinchao",
        "http://134.175.7.196:8080"
    )

    private const val PREFS = "her_state"
    private const val KEY_ON = "enabled"
    private const val KEY_LAST = "last_sent_at"
    private const val KEY_UNLOCK = "last_unlock_ms"
    private const val WORK_NAME = "her_state_report"

    // ── 开关 ──

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ON, false)

    /** 开=排 30 分钟周期任务；关=立刻取消，不再上报。 */
    fun setEnabled(context: Context, on: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_ON, on).apply()
        val wm = WorkManager.getInstance(context)
        if (on) {
            val request = PeriodicWorkRequestBuilder<HerStateWorker>(30, TimeUnit.MINUTES)
                .setConstraints(
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
                )
                .build()
            wm.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        } else {
            wm.cancelUniqueWork(WORK_NAME)
        }
    }

    // ── 节流（额外触发用：距上次不足 5 分钟跳过） ──

    fun throttleOk(context: Context, gapMs: Long = 5 * 60_000L): Boolean =
        System.currentTimeMillis() - lastSentAt(context) > gapMs

    fun markSent(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putLong(KEY_LAST, System.currentTimeMillis()).apply()
    }

    private fun lastSentAt(context: Context): Long =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(KEY_LAST, 0L)

    // ── 权限状态 ──

    /** 「使用情况访问」特殊权限（设置里手动开，运行时弹窗申请不到）。 */
    fun hasUsageAccess(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            context.packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun hasCoarseLocation(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    // ── 解锁时间（UnlockReceiver 写入） ──

    fun onUnlock(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putLong(KEY_UNLOCK, System.currentTimeMillis()).apply()
    }

    // ── 采集 + 上报 ──

    /** 采一次快照发上去。manual=true 时跳过节流（开关刚打开/手动点）。返回是否成功。 */
    suspend fun collectAndReport(context: Context, manual: Boolean = false): Boolean =
        withContext(Dispatchers.IO) {
            if (!manual && !throttleOk(context)) return@withContext true
            val body = collect(context)
            val ok = send(body)
            if (ok) markSent(context)
            ok
        }

    /** 所有字段可选：读不到就整个省略，不传 null。 */
    private fun collect(context: Context): JSONObject {
        val body = JSONObject()

        // 电量 + 充电
        runCatching {
            val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            val pct = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            if (pct in 0..100) body.put("battery_pct", pct)
            val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            body.put(
                "is_charging",
                status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
            )
        }

        // 位置：只到区县。粗定位反解，拿不到/没授权就整个省略；绝收集经纬度街道
        runCatching {
            if (hasCoarseLocation(context)) {
                val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
                val loc = lm.getBestProvider(
                    android.location.Criteria().apply { accuracy = android.location.Criteria.ACCURACY_COARSE },
                    true
                )?.let { lm.getLastKnownLocation(it) }
                if (loc != null) {
                    val geocoder = android.location.Geocoder(context, java.util.Locale.CHINA)
                    val addr = geocoder.getFromLocation(loc.latitude, loc.longitude, 1)?.firstOrNull()
                    // 只取市和区县：locality / subAdminArea，别的字段一概不要
                    addr?.locality?.takeIf { it.isNotBlank() }?.let { body.put("city", it) }
                    addr?.subAdminArea?.takeIf { it.isNotBlank() }?.let { body.put("district", it) }
                }
            }
        }

        // 今日屏幕时长 + 各 app 时长（需「使用情况访问」，没开就省略）
        runCatching {
            if (hasUsageAccess(context)) {
                val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
                val cal = java.util.Calendar.getInstance().apply {
                    set(java.util.Calendar.HOUR_OF_DAY, 0)
                    set(java.util.Calendar.MINUTE, 0)
                    set(java.util.Calendar.SECOND, 0)
                    set(java.util.Calendar.MILLISECOND, 0)
                }
                val stats = usm.queryUsageStats(
                    UsageStatsManager.INTERVAL_DAILY, cal.timeInMillis, System.currentTimeMillis()
                ).orEmpty()
                val totalMin = stats.sumOf { it.totalTimeInForeground } / 60_000
                if (totalMin > 0) body.put("screen_min", totalMin.toInt())
                val apps = stats
                    .filter { it.totalTimeInForeground > 60_000 }
                    .sortedByDescending { it.totalTimeInForeground }
                    .take(15)
                    .mapNotNull { s ->
                        runCatching {
                            val pm = context.packageManager
                            val label = pm.getApplicationLabel(pm.getApplicationInfo(s.packageName, 0)).toString()
                            JSONObject().put("name", label).put("min", s.totalTimeInForeground / 60_000)
                        }.getOrNull()
                    }
                if (apps.isNotEmpty()) body.put("app_usage", JSONArray().also { arr -> apps.forEach(arr::put) })
            }
        }

        // 在听什么（App 内播放器；没在放就省略）
        runCatching {
            val st = MusicPlayer.state.value
            if (st.playing && st.song != null) {
                body.put("music_title", st.song?.name ?: "")
                st.song?.artist?.takeIf { it.isNotBlank() }?.let { body.put("music_artist", it) }
            }
        }

        // 最后解锁时间
        runCatching {
            val ms = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(KEY_UNLOCK, 0L)
            if (ms > 0) {
                body.put(
                    "last_unlock_at",
                    java.time.OffsetDateTime.ofInstant(
                        java.time.Instant.ofEpochMilli(ms),
                        java.time.ZoneId.systemDefault()
                    ).toString()
                )
            }
        }

        return body
    }

    private fun send(body: JSONObject): Boolean {
        val bytes = body.toString().toByteArray(Charsets.UTF_8)
        for (base in BASES) {
            try {
                val conn = URL("$base/api/her_state").openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.connectTimeout = 8_000
                conn.readTimeout = 15_000
                conn.doOutput = true
                conn.outputStream.use { it.write(bytes) }
                if (conn.responseCode == 200) return true
            } catch (_: Exception) { /* 换下一条线路 */ }
        }
        return false
    }

    // ── 查看页数据 ──

    data class HerStateRow(
        val timeMs: Long,
        val city: String,
        val district: String,
        val battery: Int?,
        val charging: Boolean?,
        val screenMin: Int?,
        val apps: List<Pair<String, Int>>,
        val music: String
    )

    /** 最近 50 条上报（走同一个中转，只读）。失败返回 null。 */
    suspend fun fetchRecent(): List<HerStateRow>? = withContext(Dispatchers.IO) {
        for (base in BASES) {
            try {
                val conn = URL("$base/api/her_state/recent").openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.connectTimeout = 8_000
                conn.readTimeout = 15_000
                if (conn.responseCode != 200) continue
                val rows = JSONObject(conn.inputStream.bufferedReader().readText()).optJSONArray("rows")
                    ?: return@withContext emptyList<HerStateRow>()
                val out = mutableListOf<HerStateRow>()
                for (i in 0 until rows.length()) {
                    val o = rows.optJSONObject(i) ?: continue
                    val dev = runCatching { JSONObject(o.optString("device_event", "{}")) }.getOrNull() ?: JSONObject()
                    val appsObj = runCatching { JSONObject(o.optString("app_usage", "{}")) }.getOrNull()
                    val apps = mutableListOf<Pair<String, Int>>()
                    appsObj?.optJSONArray("apps")?.let { arr ->
                        for (j in 0 until arr.length()) {
                            val a = arr.optJSONObject(j) ?: continue
                            apps.add((a.optString("name") to a.optInt("min", 0)))
                        }
                    }
                    val music = listOf(
                        o.optString("music_title", ""),
                        o.optString("music_artist", "")
                    ).filter { it.isNotBlank() }.joinToString(" · ")
                    val devMusic = if (music.isBlank())
                        listOf(dev.optString("music_title", ""), dev.optString("music_artist", ""))
                            .filter { it.isNotBlank() }.joinToString(" · ") else music
                    out.add(
                        HerStateRow(
                            timeMs = parseTime(o.optString("timestamp", "")),
                            city = o.optString("location_city", ""),
                            district = o.optString("location_district", ""),
                            battery = if (dev.has("battery_pct")) dev.optInt("battery_pct") else null,
                            charging = if (dev.has("is_charging")) dev.optBoolean("is_charging") else null,
                            screenMin = if (dev.has("screen_min")) dev.optInt("screen_min") else null,
                            apps = apps,
                            music = devMusic
                        )
                    )
                }
                return@withContext out
            } catch (_: Exception) { /* 换线路 */ }
        }
        null
    }

    /** "2026-10-25 23:03:09.445057+00" → 本地毫秒；解析不了给 0。 */
    private fun parseTime(s: String): Long = runCatching {
        val t = s.trim().replace(' ', 'T')
        val fixed = if (t.endsWith("+00")) t + ":00" else t
        java.time.Instant.parse(fixed).toEpochMilli()
    }.getOrElse { 0L }
}

/** 30 分钟周期上报（开关打开时排上）。 */
class HerStateWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val ok = runCatching {
            HerStateReporter.collectAndReport(applicationContext)
        }.getOrDefault(false)
        return if (ok || runAttemptCount >= 3) Result.success() else Result.retry()
    }
}

/** 记下最后一次解锁时间（上报时带上；收不到就省略该字段）。 */
class UnlockReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_USER_PRESENT) {
            runCatching { HerStateReporter.onUnlock(context) }
        }
    }
}
