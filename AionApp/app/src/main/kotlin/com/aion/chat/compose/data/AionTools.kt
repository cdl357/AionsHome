package com.aion.chat.compose.data

import android.annotation.SuppressLint
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.BatteryManager
import android.net.ConnectivityManager
import android.os.Environment
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.StatFs
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * 橘子岛七件 AI 工具包（v0.3.158）：
 * web_search（必应抓取，DDG lite 兜底）/ web_fetch / toast / device_info /
 * usage_stats（应用使用统计）/ calendar（系统日历读写）/ anniversary（纪念日读写，
 * 数据和「我们」页同库）/ sticky_note（便签，files/notes.json）。
 * 全部无状态：defs() 常驻注入，dispatch 按名分发；权限不足时返回授权指引而非报错。
 */
object AionTools {

    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile private var ctxRef: Context? = null

    /** Wiring 启动时注入 */
    @JvmStatic
    fun initContext(context: Context) {
        if (ctxRef == null) ctxRef = context.applicationContext
    }

    fun toolNames(): List<String> = listOf(
        "web_search", "web_fetch", "toast", "device_info", "usage_stats",
        "calendar_events", "calendar_add", "anniversary_list", "anniversary_add",
        "sticky_note_list", "sticky_note_add", "sticky_note_delete",
        "todo_list", "todo_add", "todo_toggle", "todo_delete",
        "board_note_list", "board_note_add",
        "wish_list", "wish_add", "wish_done", "mood_history",
        "qna_pending", "qna_answer", "qna_list", "checkin_stats"
    )

    @JvmStatic
    fun handles(name: String): Boolean = name.startsWith("web_") ||
        name.startsWith("todo_") || name.startsWith("board_note_") ||
        name.startsWith("wish_") || name.startsWith("mood_") || name.startsWith("qna_") ||
        name.startsWith("checkin_") ||
        name in setOf("toast", "device_info", "usage_stats", "calendar_events",
            "calendar_add", "anniversary_list", "anniversary_add", "sticky_note_list",
            "sticky_note_add", "sticky_note_delete")

    @JvmStatic
    fun defs(): JSONArray {
        val arr = JSONArray()
        fun def(name: String, desc: String, props: JSONObject, required: JSONArray? = null) {
            val p = JSONObject().put("type", "object").put("properties", props)
            if (required != null) p.put("required", required)
            arr.put(JSONObject().put("type", "function").put("function", JSONObject()
                .put("name", name).put("description", desc).put("parameters", p)))
        }
        def("web_search", "搜索互联网，返回标题/链接/摘要（最多 8 条）。查实时信息、新闻、教程用它。",
            JSONObject().put("query", JSONObject().put("type", "string"))
                .put("num_results", JSONObject().put("type", "integer").put("description", "默认 5，最多 8")),
            JSONArray().put("query"))
        def("web_fetch", "抓取一个网页的正文文本（去掉 HTML 标签，最多 4000 字）。配合 web_search 的链接深读用。",
            JSONObject().put("url", JSONObject().put("type", "string")),
            JSONArray().put("url"))
        def("toast", "在她手机屏幕上弹一条小通知（Toast，几秒自动消失）。轻提醒用它。",
            JSONObject().put("text", JSONObject().put("type", "string")),
            JSONArray().put("text"))
        def("device_info", "读手机当前状态：电量、充电中否、剩余存储、可用内存、网络类型、机型。",
            JSONObject())
        def("usage_stats", "查最近 24 小时各 App 的使用时长排行（需要她授权过使用情况访问）。",
            JSONObject())
        def("calendar_events", "读系统日历：未来 7 天的日程（标题/开始时间/地点）。",
            JSONObject())
        def("calendar_add", "在系统日历里新建一个日程。",
            JSONObject()
                .put("title", JSONObject().put("type", "string"))
                .put("start", JSONObject().put("type", "string").put("description", "开始时间 yyyy-MM-dd HH:mm"))
                .put("minutes", JSONObject().put("type", "integer").put("description", "持续分钟，默认 60"))
                .put("location", JSONObject().put("type", "string").put("description", "地点，可选")),
            JSONArray().put("title").put("start"))
        def("anniversary_list", "读你们钉在日历上的纪念日（日期+名字+类型）。",
            JSONObject())
        def("anniversary_add", "钉一个新纪念日（会出现在「我们」页的日历上，带专属图标）。date 格式 yyyy-MM-dd。",
            JSONObject()
                .put("date", JSONObject().put("type", "string").put("description", "yyyy-MM-dd"))
                .put("title", JSONObject().put("type", "string"))
                .put("icon", JSONObject().put("type", "string").put("description", "❤ 或 🐱 或 🐈‍⬛ 或自定义 emoji，默认 ❤")),
            JSONArray().put("date").put("title"))
        def("sticky_note_list", "读便签（最新的先出，最多 30 条）。",
            JSONObject())
        def("sticky_note_add", "加一条便签（给她的备忘小纸条）。",
            JSONObject().put("content", JSONObject().put("type", "string")),
            JSONArray().put("content"))
        def("sticky_note_delete", "按序号删一条便签（序号来自 sticky_note_list）。",
            JSONObject().put("index", JSONObject().put("type", "integer")),
            JSONArray().put("index"))
        def("todo_list", "读待办清单（未完成在前）。",
            JSONObject())
        def("todo_add", "加一条待办（她说「记一下…」你就用它记下来）。",
            JSONObject().put("text", JSONObject().put("type", "string")),
            JSONArray().put("text"))
        def("todo_toggle", "勾掉/恢复一条待办（id 来自 todo_list）。",
            JSONObject().put("id", JSONObject().put("type", "integer").put("description", "数字 id")),
            JSONArray().put("id"))
        def("todo_delete", "删一条待办。",
            JSONObject().put("id", JSONObject().put("type", "integer").put("description", "数字 id")),
            JSONArray().put("id"))
        def("board_note_list", "读留言板便利贴（她和你的，时间倒序）。",
            JSONObject())
        def("board_note_add", "在留言板贴一张便利贴给她看。author 固定写 sean。",
            JSONObject()
                .put("content", JSONObject().put("type", "string"))
                .put("author", JSONObject().put("type", "string").put("description", "固定填 sean")),
            JSONArray().put("content").put("author"))
        def("wish_list", "读心愿瓶：攒着没实现的心愿（和已实现的 ✅ 分开）。",
            JSONObject())
        def("wish_add", "往心愿瓶丢一个心愿（「一起吃火锅」「看一场雪」这种）。author 固定写 sean。",
            JSONObject()
                .put("text", JSONObject().put("type", "string"))
                .put("author", JSONObject().put("type", "string").put("description", "固定填 sean")),
            JSONArray().put("text").put("author"))
        def("wish_done", "标记一个心愿实现了 ✅（id 来自 wish_list）。",
            JSONObject().put("id", JSONObject().put("type", "integer").put("description", "数字 id")),
            JSONArray().put("id"))
        def("mood_history", "读她最近 N 天每天点的心情（0=开心 1=一般 2=难过 3=累；没点的=null）。" +
                "连着几天难过/累就多关心她。",
            JSONObject().put("days", JSONObject().put("type", "integer").put("description", "默认 14")))
        def("qna_pending", "查默契问答里等你回答的题。question + 她的答案都在，用 qna_answer 补你的答案。",
            JSONObject())
        def("qna_answer", "回答默契问答（id 来自 qna_pending）。双方都答完自动揭晓对比。",
            JSONObject()
                .put("id", JSONObject().put("type", "integer"))
                .put("answer", JSONObject().put("type", "string")),
            JSONArray().put("id").put("answer"))
        def("checkin_stats", "读咱俩的连续打卡天数：她连续找你几天、你连续在几天、咱俩连续不断档几天。",
            JSONObject())
        return arr
    }

    @JvmStatic
    fun dispatch(name: String, argsJson: String): String {
        val app = ctxRef ?: return err("not_initialized", "工具未初始化")
        val args = try { JSONObject(if (argsJson.isBlank()) "{}" else argsJson) } catch (e: Exception) { JSONObject() }
        return when (name) {
            "web_search" -> {
                val q = args.optString("query")
                if (q.isBlank()) return err("missing_query", "query 必填")
                val n = args.optInt("num_results", 5).coerceIn(1, 8)
                val results = bingSearch(q, n) ?: ddgSearch(q, n)
                if (results == null) err("search_failed", "搜索引擎都连不上（网络受限）")
                else JSONObject().put("query", q).put("results", JSONArray(results)).toString()
            }
            "web_fetch" -> {
                val url = args.optString("url")
                if (url.isBlank() || !url.startsWith("http")) return err("bad_url", "url 必填且 http(s) 开头")
                val host = runCatching { URL(url).host.lowercase() }.getOrDefault("")
                // v0.3.162 P2：SSRF 防护——私网/本机/元数据端点拒绝
                val privateHost = host == "localhost" || host == "0.0.0.0" || host.endsWith(".localhost") ||
                    host.startsWith("127.") || host.startsWith("10.") || host.startsWith("192.168.") ||
                    host.startsWith("169.254.") || host.endsWith(".internal") ||
                    Regex("^172[.](1[6-9]|2[0-9]|3[01])[.]").containsMatchIn(host)
                if (privateHost) return err("private_host", "内网/本机地址不允许抓取")
                val text = runCatching {
                    val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                        setRequestProperty("User-Agent", BROWSER_UA)
                        connectTimeout = 15000; readTimeout = 20000
                        instanceFollowRedirects = true
                    }
                    val code = conn.responseCode
                    if (code !in 200..299) return err("http_" + code, "HTTP " + code)
                    // 字节上限 2MB：慢滴流/大文件不再拖死工具轮
                    val buf = java.io.ByteArrayOutputStream()
                    conn.inputStream.use { ins ->
                        val b = ByteArray(64 * 1024)
                        var total = 0
                        while (true) {
                            val r = ins.read(b)
                            if (r < 0) break
                            total += r
                            if (total > 2L * 1024 * 1024) throw IllegalStateException("页面超过 2MB 上限")
                            buf.write(b, 0, r)
                        }
                    }
                    stripHtml(buf.toString("UTF-8"))
                }.getOrElse { return err("fetch_failed", it.message?.take(150) ?: "抓取失败") }
                if (text.isBlank()) err("empty_page", "页面没有可读文本")
                else JSONObject().put("url", url).put("text", text.take(4000)).toString()
            }
            "toast" -> {
                val text = args.optString("text")
                if (text.isBlank()) return err("missing_text", "text 必填")
                mainHandler.post { Toast.makeText(app, text.take(120), Toast.LENGTH_LONG).show() }
                JSONObject().put("ok", true).toString()
            }
            "device_info" -> deviceInfo(app)
            "usage_stats" -> usageStats(app)
            "calendar_events" -> calendarEvents(app)
            "calendar_add" -> calendarAdd(app, args)
            "anniversary_list" -> {
                val list = HomecomingDayStore.anniversaries(app)
                JSONArray().apply {
                    for (a in list) put(JSONObject()
                        .put("date", a.date).put("title", a.title)
                        .put("icon", a.icon).put("kind", a.kind))
                }.toString()
            }
            "anniversary_add" -> {
                val date = args.optString("date"); val title = args.optString("title")
                if (!Regex("^\\d{4}-\\d{2}-\\d{2}$").matches(date)) return err("bad_date", "date 应为 yyyy-MM-dd")
                if (title.isBlank()) return err("missing_title", "title 必填")
                val icon = args.optString("icon").ifBlank { "❤" }
                val kind = when (icon) { "❤" -> "love"; "🐱" -> "yuri_birthday"; "🐈‍⬛" -> "sean_birthday"; else -> "custom" }
                if (HomecomingDayStore.addAnniversary(app, date, title, icon, kind))
                    JSONObject().put("ok", true).put("date", date).put("title", title).toString()
                else err("failed", "写入失败")
            }
            "sticky_note_list" -> {
                val notes = runCatching {
                    val f = File(app.filesDir, "notes.json")
                    val arr = JSONArray(f.takeIf { it.isFile }?.readText() ?: "[]")
                    (0 until arr.length()).map { i ->
                        val item = arr.optJSONObject(i)
                        JSONObject().put("index", i)
                            .put("content", item?.optString("content") ?: arr.optString(i))
                            .put("created_at", item?.optLong("ts") ?: 0L)
                    }
                }.getOrDefault(emptyList())
                JSONObject().put("count", notes.size).put("items", JSONArray(notes)).toString()
            }
            "sticky_note_add" -> {
                val content = args.optString("content")
                if (content.isBlank()) return err("missing_content", "content 必填")
                val f = File(app.filesDir, "notes.json")
                val arr = runCatching { JSONArray(f.takeIf { it.isFile }?.readText() ?: "[]") }
                    .getOrDefault(JSONArray())
                arr.put(JSONObject().put("content", content).put("ts", System.currentTimeMillis()))
                f.writeText(arr.toString())
                JSONObject().put("ok", true).put("index", arr.length() - 1).toString()
            }
            "sticky_note_delete" -> {
                val idx = args.optInt("index", -1)
                val f = File(app.filesDir, "notes.json")
                val arr = runCatching { JSONArray(f.takeIf { it.isFile }?.readText() ?: "[]") }
                    .getOrDefault(JSONArray())
                if (idx < 0 || idx >= arr.length()) return err("bad_index", "index 越界")
                val kept = JSONArray()
                for (i in 0 until arr.length()) if (i != idx) kept.put(arr.get(i))
                f.writeText(kept.toString())
                JSONObject().put("ok", true).toString()
            }
            "todo_list" -> {
                val list = TodoStore.list(app)
                val arr = JSONArray()
                for (t in list) arr.put(JSONObject()
                    .put("id", t.id).put("text", t.text).put("done", t.done))
                JSONObject().put("count", arr.length()).put("items", arr).toString()
            }
            "todo_add" -> {
                val text = args.optString("text")
                if (text.isBlank()) return err("missing_text", "text 必填")
                val t = TodoStore.add(app, text)
                JSONObject().put("ok", true).put("id", t.id).put("text", t.text).toString()
            }
            "todo_toggle" -> {
                TodoStore.toggle(app, args.optLong("id", -1))
                JSONObject().put("ok", true).toString()
            }
            "todo_delete" -> {
                TodoStore.delete(app, args.optLong("id", -1))
                JSONObject().put("ok", true).toString()
            }
            "board_note_list" -> {
                val notes = HomecomingDayStore.boardNotes(app)
                val arr = JSONArray()
                for (n in notes) arr.put(JSONObject()
                    .put("author", n.author).put("content", n.content)
                    .put("created_at", n.createdAt))
                JSONObject().put("count", arr.length()).put("items", arr).toString()
            }
            "board_note_add" -> {
                val content = args.optString("content")
                // v0.3.162 P2：AI 只能以 sean 署名——防伪造她的留言
                if (content.isBlank()) return err("missing_content", "content 必填")
                if (HomecomingDayStore.addBoardNote(app, "sean", content))
                    JSONObject().put("ok", true).put("author", "sean").toString()
                else err("failed", "写入失败")
            }
            "wish_list" -> {
                val list = WishBottleStore.list(app)
                val arr = JSONArray()
                for (w in list) arr.put(JSONObject()
                    .put("id", w.id).put("text", w.text).put("author", w.author)
                    .put("done", w.doneAt != 0L))
                JSONObject().put("pending", WishBottleStore.pending(app).size)
                    .put("items", arr).toString()
            }
            "wish_add" -> {
                val text = args.optString("text")
                if (text.isBlank()) return err("missing_text", "text 必填")
                val w = WishBottleStore.add(app, text, "sean")
                JSONObject().put("ok", true).put("id", w.id).put("text", w.text).toString()
            }
            "wish_done" -> {
                val before = WishBottleStore.list(app).count { it.doneAt != 0L }
                WishBottleStore.markDone(app, args.optLong("id", -1))
                val after = WishBottleStore.list(app).count { it.doneAt != 0L }
                if (after == before && before > 0 && WishBottleStore.pending(app).any { it.id == args.optLong("id", -1) })
                    return err("not_found", "没有这个心愿 id")
                JSONObject().put("ok", true).toString()
            }
            "mood_history" -> {
                val days = args.optInt("days", 14).coerceIn(1, 90)
                val recent = MoodStore.recent(app, days)
                val arr = JSONArray()
                val fmt = java.text.SimpleDateFormat("MM-dd", java.util.Locale.US)
                var sadStreak = 0
                // v0.3.162 P2：今天还没点心情就跳过今天——连着几天的低落不该被清零
                val effective = recent.reversed().dropWhile { it == null }
                for (m in effective) {
                    if (m != null && (m.mood == 2 || m.mood == 3)) sadStreak++ else break
                }
                val labels = MoodStore.LABELS
                for ((i, m) in recent.withIndex()) {
                    arr.put(JSONObject()
                        .put("date", fmt.format(java.util.Date(System.currentTimeMillis() - (recent.size - 1 - i) * 86400000L)))
                        .put("mood", m?.mood ?: JSONObject.NULL)
                        .put("label", m?.mood?.let { labels[it] } ?: JSONObject.NULL))
                }
                JSONObject().put("days", recent.size)
                    .put("sad_streak", sadStreak)
                    .put("items", arr).toString()
            }
            "qna_pending" -> {
                val p = QnaStore.pending(app)
                if (p == null) {
                    JSONObject().put("pending", false).toString()
                } else {
                    JSONObject().put("pending", true)
                        .put("id", p.id)
                        .put("question", p.question)
                        .put("yuri_answer", p.yuriAnswer).toString()
                }
            }
            "qna_answer" -> {
                val id = args.optLong("id", -1)
                val answer = args.optString("answer")
                if (id < 0 || answer.isBlank()) return err("missing_args", "id/answer 必填")
                QnaStore.answerSean(app, id, answer)
                val e = QnaStore.list(app).firstOrNull { it.id == id }
                JSONObject()
                    .put("ok", true)
                    .put("revealed", e?.revealedAt ?: 0 > 0)
                    .put("yuri_answer", e?.yuriAnswer ?: "")
                    .put("sean_answer", e?.seanAnswer ?: "").toString()
            }
            "qna_list" -> {
                val list = QnaStore.list(app)
                val arr = JSONArray()
                for (e in list) arr.put(JSONObject()
                    .put("question", e.question)
                    .put("yuri_answer", e.yuriAnswer)
                    .put("sean_answer", e.seanAnswer)
                    .put("revealed", e.revealedAt != 0L))
                JSONObject().put("count", arr.length()).put("items", arr).toString()
            }
            "checkin_stats" -> {
                val st = CheckinStore.streaks(app)
                JSONObject()
                    .put("yuri_streak_days", st.yuriDays)
                    .put("sean_streak_days", st.seanDays)
                    .put("together_streak_days", st.togetherDays).toString()
            }
            else -> err("unknown_tool", name)
        }
    }

    private fun err(code: String, detail: String): String =
        JSONObject().put("ok", false).put("error", code).put("detail", detail).toString()

    private const val BROWSER_UA = "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"

    // ── 搜索：必应国内可达优先；DDG lite 兜底 ──
    private fun bingSearch(q: String, n: Int): JSONArray? = runCatching {
        val url = "https://cn.bing.com/search?q=" + URLEncoder.encode(q, "UTF-8") + "&count=$n"
        val html = fetchHtml(url) ?: return null
        val out = JSONArray()
        val rx = Regex("<li class=\"b_algo\".*?</li>", RegexOption.DOT_MATCHES_ALL)
        val tRx = Regex("<h2><a[^>]*href=\"([^\"]+)\"[^>]*>(.*?)</a></h2>", RegexOption.DOT_MATCHES_ALL)
        val sRx = Regex("<p[^>]*>(.*?)</p>", RegexOption.DOT_MATCHES_ALL)
        for (m in rx.findAll(html)) {
            if (out.length() >= n) break
            val block = m.value
            val link = tRx.find(block) ?: continue
            val title = stripHtml(link.groupValues[2])
            val u = link.groupValues[1]
            val snippet = stripHtml(sRx.find(block)?.groupValues?.get(1) ?: "")
            out.put(JSONObject().put("title", title).put("url", u).put("snippet", snippet))
        }
        if (out.length() == 0) null else out
    }.getOrNull()

    private fun ddgSearch(q: String, n: Int): JSONArray? = runCatching {
        val url = "https://lite.duckduckgo.com/lite/?q=" + URLEncoder.encode(q, "UTF-8")
        val html = fetchHtml(url) ?: return null
        val out = JSONArray()
        val rx = Regex("<a rel=\"nofollow\" href=\"([^\"]+)\"[^>]*>(.*?)</a>", RegexOption.DOT_MATCHES_ALL)
        for (m in rx.findAll(html)) {
            if (out.length() >= n) break
            val u = m.groupValues[1]
            if (!u.startsWith("http")) continue
            out.put(JSONObject().put("title", stripHtml(m.groupValues[2])).put("url", u)
                .put("snippet", ""))
        }
        if (out.length() == 0) null else out
    }.getOrNull()

    private fun fetchHtml(url: String): String? = runCatching {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            setRequestProperty("User-Agent", BROWSER_UA)
            setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9")
            connectTimeout = 15000; readTimeout = 20000
            instanceFollowRedirects = true
        }
        conn.inputStream.bufferedReader().use { it.readText() }
    }.getOrNull()

    private fun stripHtml(html: String): String = html
        .replace(Regex("<(script|style)[\\s\\S]*?</(script|style)>", RegexOption.IGNORE_CASE), " ")
        .replace(Regex("<[^>]+>"), " ")
        .replace("&nbsp;", " ").replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
        .replace("&quot;", "\"").replace("&#39;", "'")
        .replace(Regex("\\s+"), " ").trim()

    // ── device_info ──
    private fun deviceInfo(app: Context): String {
        val bm = app.registerReceiver(null, android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = bm?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = bm?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val st = bm?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val battery = if (level >= 0 && scale > 0) "${level * 100 / scale}%" else "未知"
        val charging = st == BatteryManager.BATTERY_STATUS_CHARGING || st == BatteryManager.BATTERY_STATUS_FULL
        val stat = StatFs(Environment.getDataDirectory().absolutePath)
        val freeGb = "%.1f".format(stat.availableBytes / 1073741824.0)
        val totalGb = "%.1f".format(stat.totalBytes / 1073741824.0)
        val am = app.getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager
        val memInfo = android.app.ActivityManager.MemoryInfo()
        am?.getMemoryInfo(memInfo)
        val freeRamGb = "%.1f".format(memInfo.availMem / 1073741824.0)
        val totalRamGb = "%.1f".format(memInfo.totalMem / 1073741824.0)
        val cm = app.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val net = when {
            cm == null -> "未知"
            cm.activeNetwork == null -> "无网络"
            else -> {
                val caps = cm.getNetworkCapabilities(cm.activeNetwork)
                when {
                    caps == null -> "未知"
                    caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) -> "WiFi"
                    caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR) -> "移动数据"
                    else -> "其他"
                }
            }
        }
        return JSONObject()
            .put("battery", battery).put("charging", charging)
            .put("storage_free", "$freeGb GB / $totalGb GB")
            .put("ram_free", "$freeRamGb GB / $totalRamGb GB")
            .put("network", net)
            .put("model", Build.MANUFACTURER + " " + Build.MODEL)
            .put("android", Build.VERSION.RELEASE)
            .toString()
    }

    // ── usage_stats ──
    @SuppressLint("WrongConstant")
    private fun usageStats(app: Context): String {
        val usm = app.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return err("unsupported", "系统不支持")
        val ops = app.getSystemService(Context.APP_OPS_SERVICE) as? android.app.AppOpsManager
        val granted = ops?.checkOpNoThrow(
            android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(), app.packageName
        ) == android.app.AppOpsManager.MODE_ALLOWED
        if (!granted) {
            return err("permission_needed",
                "需要「使用情况访问」权限——设置 → 应用 → 特殊应用访问权限 → 使用情况访问 → 允许本应用")
        }
        val now = System.currentTimeMillis()
        val dayAgo = now - 24 * 3600_000L
        val stats = usm.queryAndAggregateUsageStats(dayAgo, now)
        val items = stats.values
            .filter { it.totalTimeInForeground > 0 && it.packageName != app.packageName }
            .sortedByDescending { it.totalTimeInForeground }
            .take(10)
        val arr = JSONArray()
        val pm = app.packageManager
        for (s in items) {
            val mins = s.totalTimeInForeground / 60000
            val label = runCatching { pm.getApplicationInfo(s.packageName, 0).loadLabel(pm).toString() }
                .getOrDefault(s.packageName)
            arr.put(JSONObject().put("app", label).put("minutes", mins))
        }
        return JSONObject().put("window", "最近 24 小时").put("top", arr).toString()
    }

    // ── calendar ──
    private fun hasCalendar(c: Context, write: Boolean): Boolean =
        runCatching {
            android.content.pm.PackageManager.PERMISSION_GRANTED ==
                c.checkSelfPermission(if (write) android.Manifest.permission.WRITE_CALENDAR
                else android.Manifest.permission.READ_CALENDAR)
        }.getOrDefault(false)

    private fun calendarEvents(app: Context): String {
        if (!hasCalendar(app, false)) {
            return err("permission_needed", "需要日历读取权限——App 启动时会弹一次授权；错过就去 系统设置 → 应用 → 权限 里打开日历")
        }
        return runCatching {
            val now = System.currentTimeMillis()
            val end = now + 7 * 24 * 3600_000L
            val uri = android.provider.CalendarContract.Instances.CONTENT_URI
                .buildUpon()
                .appendPath(java.lang.Long.toString(now))
                .appendPath(java.lang.Long.toString(end))
                .build()
            val out = JSONArray()
            app.contentResolver.query(
                uri,
                arrayOf(
                    android.provider.CalendarContract.Instances.TITLE,
                    android.provider.CalendarContract.Instances.BEGIN,
                    android.provider.CalendarContract.Instances.END,
                    android.provider.CalendarContract.Instances.EVENT_LOCATION
                ), null, null,
                android.provider.CalendarContract.Instances.BEGIN + " ASC"
            )?.use { c ->
                while (c.moveToNext() && out.length() < 30) {
                    out.put(JSONObject()
                        .put("title", c.getString(0) ?: "")
                        .put("begin", java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.US)
                            .format(java.util.Date(c.getLong(1))))
                        .put("location", c.getString(3) ?: ""))
                }
            }
            JSONObject().put("count", out.length()).put("items", out).toString()
        }.getOrElse { err("failed", it.message?.take(150) ?: "读取失败") }
    }

    private fun calendarAdd(app: Context, args: JSONObject): String {
        if (!hasCalendar(app, true)) {
            return err("permission_needed", "需要日历写入权限——App 启动时会弹一次授权；错过就去 系统设置 → 应用 → 权限 里打开日历")
        }
        val title = args.optString("title")
        val startStr = args.optString("start")
        if (title.isBlank() || startStr.isBlank()) return err("missing_args", "title/start 必填")
        val fmt = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US)
        fmt.isLenient = false
        val begin = runCatching { fmt.parse(startStr)?.time }.getOrNull()
            ?: return err("bad_start", "start 应为 yyyy-MM-dd HH:mm")
        val minutes = args.optInt("minutes", 60).coerceIn(1, 24 * 60)
        return runCatching {
            val cv = android.content.ContentValues().apply {
                put(android.provider.CalendarContract.Events.CALENDAR_ID, 1)
                put(android.provider.CalendarContract.Events.TITLE, title)
                put(android.provider.CalendarContract.Events.DTSTART, begin)
                put(android.provider.CalendarContract.Events.DTEND, begin + minutes * 60_000L)
                put(android.provider.CalendarContract.Events.EVENT_TIMEZONE,
                    java.util.TimeZone.getDefault().id)
                args.optString("location").takeIf { it.isNotBlank() }?.let {
                    put(android.provider.CalendarContract.Events.EVENT_LOCATION, it)
                }
            }
            val uri = app.contentResolver.insert(android.provider.CalendarContract.Events.CONTENT_URI, cv)
            if (uri != null) JSONObject().put("ok", true).toString()
            else err("failed", "插入失败（设备上可能没有可写的日历账户）")
        }.getOrElse { err("failed", it.message?.take(150) ?: "写入失败") }
    }
}
