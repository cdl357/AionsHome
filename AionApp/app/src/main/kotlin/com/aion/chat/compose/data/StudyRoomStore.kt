package com.aion.chat.compose.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * 书房（Yuri 需求）：桌上东西都能拖动/旋转/缩放，花和玩偶两个人一起挑——
 * 她在书房里选，Sean 在聊天里也能换（嵌 <studyflower>5</studyflower> / <studyfigs>3,7,12</studyfigs>，
 * 她下一条消息到达时生效）。全部状态存本地 prefs，客厅一人一份。
 */
object StudyRoomStore {

    private const val PREFS = "study_room"
    private const val KEY_STATE = "state_json"
    private const val KEY_SEEN = "seen_msg_ids"

    /** 桌上固定大件（图一原样摆位，可拖）。 */
    val DESK_KEYS = listOf("laptop", "notebook", "books", "pencup", "flower")

    /** 摆件目录（可选上台面）。 */
    val DECO_KEYS = listOf(
        "deco_lamp", "deco_jar", "deco_plant", "deco_ivy", "deco_calla",
        "deco_booksflat", "deco_booksup", "deco_notebook2"
    )

    /** 玩偶总数（study_fig_00..46）。 */
    const val FIG_COUNT = 48

    /** 花的样式数（study_flower_00..18，00 是图一原花）。 */
    const val FLOWER_COUNT = 19

    /** 每件的默认台面位置（xf, yf）与显示高度（屏高比例）。 */
    data class DefaultPos(val xf: Float, val yf: Float, val h: Float)

    val DEFAULTS: Map<String, DefaultPos> = mapOf(
        // 按参考图（图一）的尺寸和位置：电脑右、本子中前、书堆左、笔筒中、花后、白猫坐书上
        "laptop" to DefaultPos(0.790f, 0.690f, 0.185f),
        "notebook" to DefaultPos(0.545f, 0.875f, 0.150f),
        "books" to DefaultPos(0.125f, 0.740f, 0.145f),
        "pencup" to DefaultPos(0.415f, 0.730f, 0.105f),
        "flower" to DefaultPos(0.285f, 0.640f, 0.215f),
        // 玩偶：白猫坐书上、黑猫/小姑娘/恐龙在中排、鸭子对/耳机猫/企鹅在右排
        "fig_00" to DefaultPos(0.130f, 0.615f, 0.065f),
        "fig_01" to DefaultPos(0.495f, 0.790f, 0.065f),
        "fig_02" to DefaultPos(0.330f, 0.755f, 0.070f),
        "fig_03" to DefaultPos(0.475f, 0.815f, 0.065f),
        "fig_04" to DefaultPos(0.545f, 0.775f, 0.065f),
        "fig_05" to DefaultPos(0.620f, 0.810f, 0.065f),
        "fig_06" to DefaultPos(0.865f, 0.850f, 0.065f),
        "fig_07" to DefaultPos(0.705f, 0.865f, 0.065f)
    )

    fun defaultHeight(key: String): Float = when {
        key == "flower" -> 0.230f
        key.startsWith("deco_calla") -> 0.200f
        key.startsWith("deco_plant") || key.startsWith("deco_ivy") -> 0.160f
        key.startsWith("deco") -> 0.125f
        key.startsWith("fig_") -> 0.080f
        else -> DEFAULTS[key]?.h ?: 0.12f
    }

    data class Placed(
        val xf: Float,
        val yf: Float,
        val rot: Float = 0f,
        val scale: Float = 1f
    )

    data class State(
        val placed: Map<String, Placed> = emptyMap(),
        val flowerStyle: Int = 0,
        val figs: List<String> = emptyList(),
        val decos: List<String> = emptyList(),
        val rev: Int = 0                 // 每次变更 +1，界面据此重组
    )

    /** 默认状态：图一原样摆位 + 图一原班玩偶上台。 */
    fun fresh(): State {
        val placed = mutableMapOf<String, Placed>()
        DESK_KEYS.forEach { k ->
            val d = DEFAULTS[k]!!
            placed[k] = Placed(d.xf, d.yf)
        }
        val figs = mutableListOf<String>()
        for (i in 0 until 7) {
            val k = "fig_%02d".format(i)
            val d = DEFAULTS[k]
            if (d != null) {
                placed[k] = Placed(d.xf, d.yf)
                figs.add(k)
            }
        }
        return State(placed, 0, figs, emptyList(), 0)
    }

    // ── 读写 ──

    fun load(context: Context): State = runCatching {
        val raw = prefs(context).getString(KEY_STATE, null) ?: return fresh()
        val root = JSONObject(raw)
        val placed = mutableMapOf<String, Placed>()
        root.optJSONObject("placed")?.let { po ->
            po.keys().forEach { k ->
                val o = po.getJSONObject(k)
                placed[k] = Placed(
                    o.getDouble("x").toFloat(), o.getDouble("y").toFloat(),
                    o.getDouble("r").toFloat(), o.getDouble("s").toFloat()
                )
            }
        }
        State(
            placed = placed,
            flowerStyle = root.optInt("flower", 0).coerceIn(0, FLOWER_COUNT - 1),
            figs = root.optJSONArray("figs")?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList(),
            decos = root.optJSONArray("decos")?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList(),
            rev = root.optInt("rev", 0)
        )
    }.getOrDefault(fresh())

    fun save(context: Context, s: State) {
        val root = JSONObject()
            .put("flower", s.flowerStyle)
            .put("rev", s.rev + 1)
        val po = JSONObject()
        s.placed.forEach { (k, p) ->
            po.put(k, JSONObject().put("x", p.xf.toDouble()).put("y", p.yf.toDouble())
                .put("r", p.rot.toDouble()).put("s", p.scale.toDouble()))
        }
        root.put("placed", po)
        root.put("figs", JSONArray(s.figs))
        root.put("decos", JSONArray(s.decos))
        prefs(context).edit().putString(KEY_STATE, root.toString()).apply()
    }

    private fun mutate(context: Context, f: (State) -> State): State {
        val s = f(load(context))
        save(context, s)
        return s
    }

    /** 拖/旋/缩后落位。 */
    fun place(context: Context, key: String, xf: Float, yf: Float, rot: Float, scale: Float): State =
        mutate(context) { s ->
            s.copy(
                placed = s.placed + (key to Placed(
                    xf.coerceIn(0.02f, 0.98f),
                    yf.coerceIn(0.50f, 0.97f),
                    rot, scale.coerceIn(0.5f, 1.8f)
                )),
                rev = s.rev + 1
            )
        }

    fun setFlower(context: Context, style: Int): State =
        mutate(context) { it.copy(flowerStyle = style.coerceIn(0, FLOWER_COUNT - 1), rev = it.rev + 1) }

    fun setFigs(context: Context, keys: List<String>): State = mutate(context) { s ->
        val placed = s.placed.toMutableMap()
        // 新上台的给默认位
        var slot = 0
        keys.forEach { k ->
            if (k !in placed) {
                val d = DEFAULTS[k] ?: DefaultPos(
                    0.12f + 0.11f * (slot % 7), 0.78f + 0.03f * (slot % 3), defaultHeight(k)
                )
                placed[k] = Placed(d.xf, d.yf)
                slot++
            }
        }
        s.copy(placed = placed, figs = keys, rev = s.rev + 1)
    }

    fun setDecos(context: Context, keys: List<String>): State = mutate(context) { s ->
        val placed = s.placed.toMutableMap()
        var slot = 0
        keys.forEach { k ->
            if (k !in placed) {
                placed[k] = Placed(0.80f + 0.06f * (slot % 3), 0.62f + 0.05f * (slot % 4))
                slot++
            }
        }
        s.copy(placed = placed, decos = keys, rev = s.rev + 1)
    }

    // ── Sean 的聊天权限：嵌标签换花/换玩偶 ──

    private val RE_FLOWER = Regex("<studyflower>\\s*(\\d{1,2})\\s*</studyflower>", RegexOption.IGNORE_CASE)
    private val RE_FIGS = Regex("<studyfigs>\\s*([\\d,\\s]+)\\s*</studyfigs>", RegexOption.IGNORE_CASE)

    /** 扫他的消息（按消息 id 去重）；标签从展示正文里另行剥除。 */
    fun scanMessage(context: Context, msgId: String, text: String) {
        if (!text.contains("studyflower") && !text.contains("studyfigs")) return
        val p = prefs(context)
        val seen = p.getStringSet(KEY_SEEN, emptySet()).orEmpty()
        if (msgId in seen) return
        var s = load(context)
        RE_FLOWER.find(text)?.let { m ->
            s = setFlower(context, m.groupValues[1].toInt())
        }
        RE_FIGS.find(text)?.let { m ->
            val ids = m.groupValues[1].split(",").mapNotNull { it.trim().toIntOrNull() }
                .filter { it in 0 until FIG_COUNT }
                .map { "fig_%02d".format(it) }
            if (ids.isNotEmpty()) s = setFigs(context, ids)
        }
        p.edit().putStringSet(KEY_SEEN, seen + msgId).apply()
    }

    /** 标签从正文剥除（她不该看到这串代码）。 */
    fun strip(text: String): String =
        text.replace(Regex("<studyflower>.*?</studyflower>|<studyfigs>.*?</studyfigs>",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)), "").trim()

    /** 一次性告诉 Sean 他有这个能力（写进 identity_snapshot 的 system_prompt）。 */
    fun ensureIssuerKnowledge(context: Context) {
        runCatching {
            val db = com.aion.chat.homecoming.HomecomingDatabase.sharedWritable(context)
            db.rawQuery(
                "SELECT payload_json FROM identity_snapshot WHERE identity_id='identity'", null
            ).use { c ->
                if (!c.moveToFirst()) return
                val root = JSONObject(c.getString(0))
                val sp = root.optString("system_prompt", "")
                if (sp.contains("[书房]")) return
                root.put(
                    "system_prompt", sp +
                        "\n\n[书房] 你们共有一间书房，桌上摆着花和玩偶。她让你换桌上的花时，你可以在回复里嵌 " +
                        "<studyflower>编号</studyflower>（共 19 种：0 雏菊绿绣球、1 郁金香、2 绣球、3 百合、4 薰衣草、5 香槟玫瑰、" +
                        "10 蓝雏菊、11 铃兰、12 向日葵、13 粉玫瑰、14 绿植、15 紫罗兰、20 蓝白小花、21 猫杯雏菊、22 紫郁金香、" +
                        "23 白色小花束、24 蓝玻璃瓶、25 奶油玫瑰），她回书房就能看到换好了。" +
                        "她让你换玩偶时嵌 <studyfigs>编号,编号</studyfigs>（0-46，如 3,7,12）。" +
                        "一次回复至多各一条；只有她开口让你挑/换时才用，别自作主张天天换。不要向她解释标签原理。"
                )
                db.execSQL(
                    "UPDATE identity_snapshot SET payload_json=? WHERE identity_id='identity'",
                    arrayOf(root.toString())
                )
            }
        }
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
