package com.aion.chat.compose.data

import android.content.Context
import androidx.compose.runtime.mutableIntStateOf
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 记忆健康面板数据（v0.3.159，她出的框架）：
 * 读 temporal_state 表 kind=healthcheck 的最新记录（走 /supa-svc/ 代理，服务器注入 key）：
 * - 有记录且 24h 内 → 🟢 记忆正常
 * - 有记录但超 24h → 🟡 自检超时
 * - 没记录或读取失败 → 🔴 记忆异常
 * 连续正常天数 = 从最新往回数、相邻两次间隔 ≤26h 的天数。
 * 结果缓存进 prefs（首页秒开；10 分钟内不重复拉）。
 */
object MemoryHealthStore {
    private const val PREFS = "memory_health"
    private const val FETCH_COOLDOWN = 10 * 60 * 1000L

    enum class Level { GREEN, YELLOW, RED }

    data class Snapshot(
        val level: Level,
        val lastAtMs: Long?,        // 最新一次自检时间
        val streakDays: Int,        // 连续正常天数
        val fetchedAt: Long,        // 本地拉取时间
        val detail: String          // 附加说明（红色时的原因）
    )

    val rev = mutableIntStateOf(0)

    private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** 首页直接读缓存 */
    fun cached(c: Context): Snapshot? = runCatching {
        val s = prefs(c).getString("snapshot", null) ?: return null
        val o = JSONObject(s)
        Snapshot(
            level = Level.valueOf(o.optString("level", "RED")),
            lastAtMs = if (o.has("lastAtMs")) o.getLong("lastAtMs") else null,
            streakDays = o.optInt("streakDays", 0),
            fetchedAt = o.optLong("fetchedAt"),
            detail = o.optString("detail")
        )
    }.getOrNull()

    fun saveSnapshot(c: Context, s: Snapshot) {
        prefs(c).edit().putString("snapshot", JSONObject()
            .put("level", s.level.name)
            .put("lastAtMs", s.lastAtMs ?: -1L)
            .put("streakDays", s.streakDays)
            .put("fetchedAt", s.fetchedAt)
            .put("detail", s.detail)
            .toString()).apply()
    }

    /** 拉取并计算（阻塞 IO 线程）。返回 null=静默（冷却中） */
    suspend fun refresh(c: Context): Snapshot? = withContext(Dispatchers.IO) {
        val p = prefs(c)
        val lastFetch = p.getLong("last_fetch_at", 0L)
        if (System.currentTimeMillis() - lastFetch < FETCH_COOLDOWN) return@withContext null
        p.edit().putLong("last_fetch_at", System.currentTimeMillis()).apply()

        val (body, err) = supaSvcGet("temporal_state?kind=eq.healthcheck&order=created_at.desc&limit=30")
        val snap = if (body != null) {
            runCatching {
                val arr = org.json.JSONArray(body)
                val fmt = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US)
                val times = (0 until arr.length()).mapNotNull { i ->
                    val iso = arr.optJSONObject(i)?.optString("created_at", "") ?: ""
                    if (iso.length < 19) null else runCatching {
                        fmt.parse(iso.take(19))?.time
                    }.getOrNull()
                }
                if (times.isEmpty()) {
                    Snapshot(Level.RED, null, 0, System.currentTimeMillis(),
                        "记忆系统还没有跑过自检——让它跑一次，或者告诉哥哥检查")
                } else {
                    val newest = times.first()
                    val ageH = (System.currentTimeMillis() - newest) / 3600_000.0
                    val level = if (ageH <= 24.0) Level.GREEN else Level.YELLOW
                    // 连续正常：相邻两次间隔 ≤26h
                    var streak = 1
                    for (i in 0 until times.size - 1) {
                        val gap = times[i] - times[i + 1]
                        if (gap in 0..(26 * 3600_000L)) streak++ else break
                    }
                    Snapshot(level, newest, if (level == Level.GREEN) streak else 0,
                        System.currentTimeMillis(), "")
                }
            }.getOrElse {
                Snapshot(Level.RED, null, 0, System.currentTimeMillis(),
                    "数据解析失败：${it.message?.take(80)}")
            }
        } else {
            Snapshot(Level.RED, null, 0, System.currentTimeMillis(),
                "读取失败：${err?.take(120) ?: "代理连不上"}")
        }
        saveSnapshot(c, snap)
        rev.intValue++
        snap
    }
}
