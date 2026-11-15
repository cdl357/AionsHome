package com.aion.chat.compose.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat

/**
 * AI 工具扩展：专属电台 + 睡前故事 + 纪念日倒数（v0.3.166）。
 * 接在 AionTools 后面，同一 dispatch 链。
 */
object RadioStoryTools {

    @JvmStatic
    fun handles(name: String): Boolean = name in setOf(
        "radio_add", "radio_list", "radio_remove",
        "bedtime_story", "story_list",
        "anniversary_countdown"
    )

    @JvmStatic
    fun defs(): JSONArray = org.json.JSONArray().apply {
        fun def(name: String, desc: String, props: JSONObject = JSONObject(), required: JSONArray? = null) {
            val p = JSONObject().put("type", "object").put("properties", props)
            if (required != null) p.put("required", required)
            put(JSONObject().put("type", "function").put("function", JSONObject()
                .put("name", name).put("description", desc).put("parameters", p)))
        }
        def("radio_add",
            "往专属电台加一首歌。先用 web_search 或直接告诉歌名和歌手（需要网易云音乐搜到的 song_id），加上备注为什么加这首歌。",
            JSONObject()
                .put("song_id", JSONObject().put("type", "integer").put("description", "网易云音乐 song_id"))
                .put("name", JSONObject().put("type", "string").put("description", "歌名"))
                .put("artist", JSONObject().put("type", "string").put("description", "歌手"))
                .put("note", JSONObject().put("type", "string").put("description", "为什么加这首歌（比如「复习教资时放给他听的」）")),
            JSONArray().put("song_id").put("name").put("artist"))
        def("radio_list", "读专属电台歌单（每首歌带备注和添加人）。",
            JSONObject())
        def("radio_remove", "从专属电台删一首歌。",
            JSONObject().put("id", JSONObject().put("type", "integer")),
            JSONArray().put("id"))
        def("bedtime_story",
            "保存一个睡前短故事到故事库。你自己先写好故事内容，然后通过这个工具存储。" +
            "故事主角是沈猫猫（一只猫）和小鑫（一个人），要温暖、简短（200-300 字）、适合睡前读。",
            JSONObject()
                .put("content", JSONObject().put("type", "string").put("description", "故事内容（你写好的）")),
            JSONArray().put("content"))
        def("story_list", "读最近生成的睡前故事列表。",
            JSONObject())
        def("anniversary_countdown", "读纪念日倒数：哪些纪念日快到了、还有几天。",
            JSONObject())
    }

    @JvmStatic
    fun dispatch(context: Context, name: String, argsJson: String): String {
        val app = context.applicationContext
        val args = try { JSONObject(if (argsJson.isBlank()) "{}" else argsJson) } catch (e: Exception) { JSONObject() }
        return when (name) {
            "radio_add" -> {
                val sid = args.optLong("song_id", -1)
                val name = args.optString("name")
                if (sid < 0 || name.isBlank()) return err("missing_args", "song_id/name 必填")
                val r = RadioStore.add(app, sid, name, args.optString("artist"), "yuri", args.optString("note"))
                JSONObject().put("ok", true).put("song", r.name)
                    .put("note", r.note).toString()
            }
            "radio_list" -> {
                val list = RadioStore.list(app)
                val arr = JSONArray()
                for (r in list) arr.put(JSONObject()
                    .put("song", r.name).put("artist", r.artist)
                    .put("added_by", r.addedBy).put("note", r.note)
                    .put("date", SimpleDateFormat("MM-dd", java.util.Locale.US).format(java.util.Date(r.addedAt))))
                JSONObject().put("count", arr.length()).put("items", arr).toString()
            }
            "radio_remove" -> {
                RadioStore.remove(app, args.optLong("id", -1))
                JSONObject().put("ok", true).toString()
            }
            "bedtime_story" -> {
                val content = args.optString("content")
                if (content.isBlank()) return err("missing_content", "content 必填——你自己写好故事然后存进来")
                val st = StoryStore.add(app, content)
                JSONObject().put("ok", true).put("date",
                    java.text.SimpleDateFormat("MM-dd", java.util.Locale.US)
                        .format(java.util.Date(st.createdAt))).toString()
            }
            "story_list" -> {
                val list = StoryStore.list(app)
                val arr = JSONArray()
                for (st in list) arr.put(JSONObject()
                    .put("content", st.content)
                    .put("date", SimpleDateFormat("MM-dd", java.util.Locale.US).format(java.util.Date(st.createdAt))))
                JSONObject().put("count", arr.length()).put("items", arr).toString()
            }
            "anniversary_countdown" -> {
                val list = HomecomingDayStore.anniversaries(app)
                val now = System.currentTimeMillis()
                val arr = JSONArray()
                for (a in list) {
                    try {
                        val target = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).parse(a.date)?.time ?: continue
                        // 计算下一个周年
                        val daysLeft = java.util.concurrent.TimeUnit.MILLISECONDS.toDays(target - now)
                        arr.put(JSONObject().put("date", a.date).put("title", a.title)
                            .put("days_left", daysLeft).put("icon", a.icon))
                    } catch (_: Exception) {}
                }
                JSONObject().put("items", arr).toString()
            }
            else -> err("unknown_tool", name)
        }
    }

    private fun err(code: String, detail: String): String =
        JSONObject().put("ok", false).put("error", code).put("detail", detail).toString()
}
