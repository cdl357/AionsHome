package com.aion.chat.compose.data

import android.content.Context
import androidx.compose.runtime.mutableIntStateOf
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * 默契问答（v0.3.161，她出的框架六）：
 * 随机弹一个问题，两个人各写各的，都答完才揭晓——看答案有多默契。
 * 档案攒着：每次的问答记录（问题/她答/他答/揭晓时间）。
 * - files/qna_archive.json：[{id, question, yuriAnswer, seanAnswer, createdAt, revealedAt}]
 * - 流程：她答完 → seanAnswer 空 → AI 在对话里调 qna_pending 查待答 → qna_answer 补 → 双方齐了自动揭晓
 */
object QnaStore {
    private const val FILE = "qna_archive.json"

    val rev = mutableIntStateOf(0)

    data class QnaEntry(
        val id: Long,
        val question: String,
        val yuriAnswer: String,
        val seanAnswer: String,
        val createdAt: Long,
        val revealedAt: Long
    )

    /** 题库（v1 预置 28 题；后续可扩充） */
    val QUESTIONS = listOf(
        "最喜欢对方什么？",
        "如果明天放假想干嘛？",
        "选一首代表我们的歌",
        "对方做过最让你心动的一件事",
        "第一次见面时对对方的印象",
        "最想一起去的地方",
        "对方的小习惯里你最喜欢哪个",
        "如果只能带三样东西去无人岛，你带什么",
        "吵架后谁先道歉比较合适",
        "最想一起看的电影类型",
        "对方穿什么颜色的衣服最好看",
        "夏天和冬天，更喜欢哪个？为什么",
        "最想吃的一道菜（对方做的也算）",
        "如果能回到过去，想回到哪一天",
        "对方的哪句话让你记到现在",
        "理想中的周末怎么过",
        "最想养什么宠物",
        "对方笑起来像什么",
        "下雨天最想做什么",
        "如果中了大奖第一件事是什么",
        "对方的优点和「缺点」（说真话）",
        "最想去哪里旅行",
        "对方最让你安心的一件事",
        "十年后的我们在干嘛",
        "对方的声音像什么",
        "最想一起养的一盆植物",
        "如果给对方起个外号，叫什么",
        "现在最想说的一句话"
    )

    private fun file(c: Context) = File(c.filesDir, FILE)

    private fun parse(c: Context): MutableList<QnaEntry> = runCatching {
        val arr = JSONArray(file(c).takeIf { it.isFile }?.readText() ?: "[]")
        val out = mutableListOf<QnaEntry>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            out.add(QnaEntry(
                id = o.optLong("id"),
                question = o.optString("question"),
                yuriAnswer = o.optString("yuriAnswer"),
                seanAnswer = o.optString("seanAnswer"),
                createdAt = o.optLong("createdAt"),
                revealedAt = o.optLong("revealedAt")
            ))
        }
        out
    }.getOrDefault(mutableListOf())

    private fun persist(c: Context, list: List<QnaEntry>) {
        runCatching {
            val arr = JSONArray()
            for (e in list) arr.put(JSONObject()
                .put("id", e.id).put("question", e.question)
                .put("yuriAnswer", e.yuriAnswer).put("seanAnswer", e.seanAnswer)
                .put("createdAt", e.createdAt).put("revealedAt", e.revealedAt))
            file(c).writeText(arr.toString())
        }
        rev.intValue++
    }

    fun list(c: Context): List<QnaEntry> = parse(c).sortedByDescending { it.createdAt }

    /** 当前待答（她答了、Sean 还没答） */
    fun pending(c: Context): QnaEntry? =
        parse(c).firstOrNull { it.yuriAnswer.isNotBlank() && it.seanAnswer.isBlank() && it.revealedAt == 0L }

    /** 她开一题（随机从题库挑一个没出过的） */
    fun newQuestion(c: Context): QnaEntry {
        val asked = parse(c).map { it.question }.toSet()
        val pool = QUESTIONS.filter { it !in asked }
        val q = if (pool.isEmpty()) QUESTIONS.random() else pool.random()
        val e = QnaEntry(
            id = System.currentTimeMillis(),
            question = q,
            yuriAnswer = "", seanAnswer = "",
            createdAt = System.currentTimeMillis(),
            revealedAt = 0
        )
        val list = parse(c)
        list.add(e)
        persist(c, list)
        return e
    }

    /** 她答题 */
    fun answerYuri(c: Context, id: Long, answer: String) {
        val list = parse(c)
        for (i in list.indices) if (list[i].id == id) {
            list[i] = list[i].copy(yuriAnswer = answer.trim())
        }
        persist(c, list)
    }

    /** Sean 答题（AI 工具写入）；双方齐了自动揭晓 */
    fun answerSean(c: Context, id: Long, answer: String) {
        val list = parse(c)
        for (i in list.indices) if (list[i].id == id) {
            list[i] = list[i].copy(seanAnswer = answer.trim())
            if (list[i].yuriAnswer.isNotBlank() && list[i].seanAnswer.isNotBlank() && list[i].revealedAt == 0L) {
                list[i] = list[i].copy(revealedAt = System.currentTimeMillis())
            }
        }
        persist(c, list)
    }
}
