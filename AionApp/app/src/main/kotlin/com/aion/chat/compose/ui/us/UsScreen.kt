package com.aion.chat.compose.ui.us

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aion.chat.compose.data.HomecomingData
import com.aion.chat.compose.data.HomecomingDayStore
import com.aion.chat.compose.data.HomecomingDayStore.Anniversary
import com.aion.chat.compose.data.HomecomingDayStore.DiaryEntry
import com.aion.chat.compose.ui.home.FrostCard
import com.aion.chat.compose.ui.theme.HomecomingColors
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** 页面②：我们（日历时光机）——整月格子 + 左右滑切月 + 点天抽屉三卡（摘要可展开）+ 纪念日专属图标。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UsScreen() {
    val context = LocalContext.current
    var viewDate by remember { mutableStateOf(LocalDate.now().withDayOfMonth(1)) }
    var selected by remember { mutableStateOf<LocalDate?>(null) }
    var showDrawer by remember { mutableStateOf(false) }
    var reloadKey by remember { mutableStateOf(0) }

    var anniversaries by remember { mutableStateOf(listOf<Anniversary>()) }
    var diaries by remember { mutableStateOf(listOf<HomecomingDayStore.DiaryEntry>()) }
    var boardNotes by remember { mutableStateOf(listOf<HomecomingDayStore.BoardNote>()) }
    var memories by remember { mutableStateOf(listOf<HomecomingDayStore.MemorySummary>()) }
    var diaryCloudError by remember { mutableStateOf<com.aion.chat.compose.data.CloudErrorKind?>(null) }

    LaunchedEffect(reloadKey) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            anniversaries = HomecomingDayStore.anniversaries(context)
            // 第一阶段只读：哥哥的日记（user_id=ai_哥哥）；网络/权限/解析失败分种提示，不影响本地
            val res = runCatching {
                com.aion.chat.compose.data.SupabaseMomentsStore.fetchSeanDiaries()
            }.getOrNull()
            val remoteDiaries = if (res?.error == null) res?.data ?: emptyList() else emptyList()
            diaryCloudError = res?.error
            diaries = HomecomingDayStore.diaries(context) + remoteDiaries.map { d ->
                HomecomingDayStore.DiaryEntry(
                    0, "sean", d.title, d.content,
                    if (d.createdAtMs > 0) d.createdAtMs else System.currentTimeMillis()
                )
            }
            boardNotes = HomecomingDayStore.boardNotes(context)
            memories = HomecomingDayStore.memoriesOfDay(context, ::dayKey, "")
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 112.dp)
    ) {
        Text("我们", fontSize = 22.sp, color = HomecomingColors.Ink)
        Text("日历时光机 · 左右滑月份，点一天回去看看", fontSize = 12.sp, color = HomecomingColors.InkSoft)
        diaryCloudError?.let { kind ->
            Text(
                when (kind) {
                    com.aion.chat.compose.data.CloudErrorKind.NETWORK -> "哥哥的日记：网络连不上云端"
                    com.aion.chat.compose.data.CloudErrorKind.HTTP_401 -> "哥哥的日记：云端 401，权限未放行"
                    com.aion.chat.compose.data.CloudErrorKind.HTTP_403 -> "哥哥的日记：云端 403，RLS 拒绝读取"
                    com.aion.chat.compose.data.CloudErrorKind.HTTP_404 -> "哥哥的日记：云端 404，找不到 diary_entries"
                    com.aion.chat.compose.data.CloudErrorKind.PARSE -> "哥哥的日记：字段解析失败（日志 tag=SupabaseClient）"
                    com.aion.chat.compose.data.CloudErrorKind.OTHER_HTTP -> "哥哥的日记：云端返回异常状态"
                },
                fontSize = 11.sp, color = HomecomingColors.Danger,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        Spacer(Modifier.height(14.dp))

        // ── 日历卡：整月格子 + 左右滑切月 ──
        FrostCard {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerInput(viewDate) {
                        detectHorizontalDragGestures { change, dragAmount ->
                            change.consume()
                            if (kotlin.math.abs(dragAmount) > 40) {
                                viewDate = if (dragAmount < 0) viewDate.plusMonths(1) else viewDate.minusMonths(1)
                            }
                        }
                    }
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("‹", fontSize = 22.sp, color = HomecomingColors.Ink, modifier = Modifier.clickable { viewDate = viewDate.minusMonths(1) })
                    Text("${viewDate.year} 年 ${viewDate.monthValue} 月", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = HomecomingColors.Ink)
                    Text("›", fontSize = 22.sp, color = HomecomingColors.Ink, modifier = Modifier.clickable { viewDate = viewDate.plusMonths(1) })
                }
                Spacer(Modifier.height(10.dp))
                WeekHeader()
                CalendarGrid(
                    year = viewDate.year,
                    month = viewDate.monthValue,
                    selected = selected,
                    today = LocalDate.now(),
                    hasContent = { y, m, d ->
                        hasDiary(diaries, y, m, d) || hasMem(memories, y, m, d) || hasAnniv(anniversaries, y, m, d)
                    },
                    anniversaryOf = { y, m, d -> annivOn(anniversaries, y, m, d) },
                    onDayClick = { date -> selected = date; drawerAction = ""; showDrawer = true }
                )
                Text(
                    "左右滑动翻月 · 有内容的日子带小圆点 · 纪念日有专属图标",
                    fontSize = 11.sp, color = HomecomingColors.InkSoft,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }

        // ── 那天（点了具体日期才出现：预览 + 在这天新建，定稿 §4） ──
        selected?.let { date ->
            Text(
                "那天 · ${date.monthValue} 月 ${date.dayOfMonth} 日" + if (date == LocalDate.now()) "（今天）" else "",
                fontSize = 14.sp, fontWeight = FontWeight.Medium, color = HomecomingColors.Ink,
                modifier = Modifier.padding(top = 12.dp, bottom = 6.dp)
            )
            // 新建入口只属于选中的那天
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = { drawerAction = "diary"; showDrawer = true }) {
                    Text("在这天写日记", color = HomecomingColors.Ink)
                }
                TextButton(onClick = { drawerAction = "anniversary"; showDrawer = true }) {
                    Text("在这天钉纪念日", color = HomecomingColors.Ink)
                }
            }
            val dayAnniv = annivOn(anniversaries, date.year, date.monthValue, date.dayOfMonth)
            if (dayAnniv != null) {
                FrostCard {
                    Text("${dayAnniv.icon} ${dayAnniv.title}", fontSize = 14.sp, color = HomecomingColors.Ink)
                    Text("钉在这天的纪念日", fontSize = 11.sp, color = HomecomingColors.InkSoft)
                }
            }
            FrostCard {
                Text(
                    daySummary(diaries, memories, date),
                    fontSize = 13.sp, color = HomecomingColors.InkSoft
                )
            }
        }

        // ── 今日情话 ──
        Spacer(Modifier.height(6.dp))
        FrostCard {
            Text("今日情话", fontSize = 13.sp, color = HomecomingColors.InkSoft)
            Text(
                HomecomingData.loadQuote(context),
                fontSize = 14.sp, color = HomecomingColors.Ink,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
            )
        }
    }

    // ── 点天抽屉：三张摘要卡（点开展开）+ 新建表单 ──
    if (showDrawer && selected != null) {
        val sheetState = rememberModalBottomSheetState()
        ModalBottomSheet(onDismissRequest = { showDrawer = false }, sheetState = sheetState) {
            DayDrawer(
                date = selected!!,
                diaries = diaries,
                memories = memories,
                boardNotes = boardNotes,
                anniversaries = anniversaries,
                initialAction = drawerAction,
                onSaved = { reloadKey++ }
            )
        }
    }
}

/** 抽屉打开时的初始动作（"diary"=直接弹出写日记，"anniversary"=弹出钉纪念日，空=只看）。 */
internal var drawerAction: String = ""

// ── 日期工具 ──

private val zone: ZoneId = ZoneId.systemDefault()

private fun dayKey(ts: Long): String {
    val t = Instant.ofEpochMilli(ts).atZone(zone)
    return "${t.year}-${t.monthValue}-${t.dayOfMonth}"
}

private fun dateKey(y: Int, m: Int, d: Int): String = "$y-$m-$d"

private fun dayKeyOfDate(date: LocalDate): String = "${date.year}-${date.monthValue}-${date.dayOfMonth}"

private fun hasDiary(diaries: List<DiaryEntry>, y: Int, m: Int, d: Int): Boolean =
    diaries.any { dayKey(it.createdAt) == dateKey(y, m, d) }

private fun hasMem(memories: List<HomecomingDayStore.MemorySummary>, y: Int, m: Int, d: Int): Boolean =
    memories.any { dayKey(it.time) == dateKey(y, m, d) }

private fun hasAnniv(anniversaries: List<Anniversary>, y: Int, m: Int, d: Int): Boolean =
    anniversaries.any { it.date == "%04d-%02d-%02d".format(y, m, d) }

private fun annivOn(anniversaries: List<Anniversary>, y: Int, m: Int, d: Int): Anniversary? =
    anniversaries.firstOrNull { it.date == "%04d-%02d-%02d".format(y, m, d) }

private fun daySummary(diaries: List<DiaryEntry>, memories: List<HomecomingDayStore.MemorySummary>, date: LocalDate): String {
    val key = dayKeyOfDate(date)
    val d1 = diaries.any { dayKey(it.createdAt) == key }
    val m1 = memories.any { dayKey(it.time) == key }
    return when {
        d1 && m1 -> "这天有日记，也有记下来的事。"
        d1 -> "这天有日记。"
        m1 -> "这天有记下来的事。"
        else -> "这天很安静，没留下什么。"
    }
}

private fun authorName(author: String): String = when (author) {
    "user" -> "Yuri"; "sean", "aion" -> "Sean"; else -> author
}

// ── 日历格子 ──

private val DOW = listOf("日", "一", "二", "三", "四", "五", "六")

@Composable
fun WeekHeader() {
    Row(modifier = Modifier.fillMaxWidth()) {
        DOW.forEach {
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Text(it, fontSize = 11.sp, color = HomecomingColors.InkSoft)
            }
        }
    }
}

@Composable
fun CalendarGrid(
    year: Int,
    month: Int,
    selected: LocalDate?,
    today: LocalDate,
    hasContent: (Int, Int, Int) -> Boolean,
    anniversaryOf: (Int, Int, Int) -> Anniversary?,
    onDayClick: (LocalDate) -> Unit
) {
    val firstDow = LocalDate.of(year, month, 1).dayOfWeek.value % 7
    val daysInMonth = LocalDate.of(year, month, 1).lengthOfMonth()
    val total = ((firstDow + daysInMonth + 6) / 7) * 7
    Column(modifier = Modifier.fillMaxWidth()) {
        for (r in 0 until total / 7) {
            Row(modifier = Modifier.fillMaxWidth()) {
                for (c in 0 until 7) {
                    val idx = r * 7 + c
                    val day = idx - firstDow + 1
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp)
                            .padding(2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (day in 1..daysInMonth) {
                            val date = LocalDate.of(year, month, day)
                            val isToday = date == today
                            val isSelected = date == selected
                            val anniv = anniversaryOf(year, month, day)
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(CircleShape)
                                    .background(
                                        when {
                                            anniv != null -> HomecomingColors.AccentSoft
                                            isSelected -> HomecomingColors.IceBlue.copy(alpha = 0.65f)
                                            else -> Color.Transparent
                                        }
                                    )
                                    .clickable { onDayClick(date) },
                                contentAlignment = Alignment.Center
                            ) {
                                if (anniv != null) {
                                    Text(anniv.icon, fontSize = 17.sp)
                                } else {
                                    Text(
                                        day.toString(),
                                        fontSize = 13.sp,
                                        color = HomecomingColors.Ink,
                                        fontWeight = if (isSelected || isToday) FontWeight.Medium else FontWeight.Normal
                                    )
                                }
                                if (hasContent(year, month, day) && anniv == null) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.BottomCenter)
                                            .padding(bottom = 3.dp)
                                            .size(4.dp)
                                            .background(HomecomingColors.Accent, CircleShape)
                                    )
                                }
                            }
                        } else {
                            val d = if (day < 1) {
                                LocalDate.of(year, month, 1).minusDays((1 - day).toLong()).dayOfMonth
                            } else {
                                LocalDate.of(year, month, 1).plusMonths(1).plusDays((day - daysInMonth - 1).toLong()).dayOfMonth
                            }
                            Text(
                                d.toString(),
                                fontSize = 12.sp,
                                color = HomecomingColors.InkSoft.copy(alpha = 0.4f)
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── 抽屉：三张摘要卡（点开展开）+ 新建表单 ──

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayDrawer(
    date: LocalDate,
    diaries: List<DiaryEntry>,
    memories: List<HomecomingDayStore.MemorySummary>,
    boardNotes: List<HomecomingDayStore.BoardNote>,
    anniversaries: List<Anniversary>,
    initialAction: String,
    onSaved: () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState()
    var expanded by remember { mutableStateOf(setOf<String>()) }
    var showDiaryForm by remember { mutableStateOf(initialAction == "diary") }
    var showAnnivForm by remember { mutableStateOf(initialAction == "anniversary") }
    var diaryTitle by remember { mutableStateOf("") }
    var diaryContent by remember { mutableStateOf("") }
    var annivTitle by remember { mutableStateOf("") }
    var annivIcon by remember { mutableStateOf("❤") }

    val key = dayKeyOfDate(date)
    val seanDiary = diaries.filter { it.author == "sean" && dayKey(it.createdAt) == key }
    val dayNotes = boardNotes.filter { dayKey(it.createdAt) == key }
    val dayMems = memories.filter { dayKey(it.time) == key }

    fun toggle(k: String) {
        expanded = if (expanded.contains(k)) expanded - k else expanded + k
    }

    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)) {
        Text(
            "那天 · ${date.monthValue} 月 ${date.dayOfMonth} 日" + if (date == LocalDate.now()) "（今天）" else "",
            fontSize = 18.sp, color = HomecomingColors.Ink
        )
        Spacer(Modifier.height(12.dp))

        // ① Sean 的日记 —— 约定：每天晚安后必写，没写 = Sean 偷懒
        DaySummaryCard(
            k = "diary",
            label = "① Sean 的日记",
            summary = if (seanDiary.isNotEmpty())
                (seanDiary[0].title.ifBlank { seanDiary[0].content }).take(60)
            else "Sean 偷懒了，这天的日记还没写。"
        ) {
            if (seanDiary.isEmpty()) {
                Text("(去戳戳他把日记补上)", fontSize = 12.sp, color = HomecomingColors.InkSoft)
            } else {
                seanDiary.forEach { d ->
                    if (d.title.isNotBlank()) {
                        Text(d.title, fontSize = 14.sp, color = HomecomingColors.Ink, fontWeight = FontWeight.Medium)
                    }
                    Text(d.content.ifBlank { "（无正文）" }, fontSize = 13.sp, color = HomecomingColors.InkSoft)
                    Spacer(Modifier.height(6.dp))
                }
            }
        }

        // ② 两人的留言
        DaySummaryCard(
            k = "notes",
            label = "② 两人的留言",
            summary = if (dayNotes.isNotEmpty())
                dayNotes.joinToString("；") { n -> "${authorName(n.author)}：${n.content.take(20)}" }
            else "这天还没有留言。"
        ) {
            if (dayNotes.isEmpty()) {
                Text("去「更多 → 留言板」贴第一张便利贴吧。", fontSize = 12.sp, color = HomecomingColors.InkSoft)
            } else {
                dayNotes.forEach { n ->
                    Text("${authorName(n.author)}：${n.content}", fontSize = 13.sp, color = HomecomingColors.InkSoft)
                    Spacer(Modifier.height(4.dp))
                }
            }
        }

        // ③ 重要记忆摘要
        DaySummaryCard(
            k = "mem",
            label = "③ 两人的重要记忆摘要",
            summary = if (dayMems.isNotEmpty())
                dayMems.joinToString("；") { m -> m.content.take(20) }
            else "这天没有留下重要记忆。"
        ) {
            if (dayMems.isEmpty()) {
                Text("聊过天的日子都会有记忆，慢慢会有的。", fontSize = 12.sp, color = HomecomingColors.InkSoft)
            } else {
                dayMems.forEach { m ->
                    Text("· ${m.content}", fontSize = 13.sp, color = HomecomingColors.InkSoft)
                    Spacer(Modifier.height(4.dp))
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            TextButton(onClick = { showDiaryForm = !showDiaryForm; showAnnivForm = false }) {
                Text("给这天写日记", color = HomecomingColors.Ink)
            }
            TextButton(onClick = { showAnnivForm = !showAnnivForm; showDiaryForm = false }) {
                Text("钉纪念日", color = HomecomingColors.Ink)
            }
        }

        if (showDiaryForm) {
            FrostCard {
                OutlinedTextField(
                    value = diaryTitle, onValueChange = { diaryTitle = it },
                    label = { Text("标题（可不填）") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = diaryContent, onValueChange = { diaryContent = it },
                    label = { Text("写点什么…") },
                    modifier = Modifier.fillMaxWidth(), minLines = 3
                )
                Spacer(Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { showDiaryForm = false }) { Text("取消", color = HomecomingColors.InkSoft) }
                    TextButton(onClick = {
                        if (diaryContent.isNotBlank()) {
                            val noon = date.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
                            if (HomecomingDayStore.addDiary(context, "user", diaryTitle.trim(), diaryContent.trim(), noon)) {
                                Toast.makeText(context, "日记记下了", Toast.LENGTH_SHORT).show()
                                diaryTitle = ""; diaryContent = ""; showDiaryForm = false; onSaved()
                            } else Toast.makeText(context, "保存失败", Toast.LENGTH_SHORT).show()
                        }
                    }) { Text("保存", color = HomecomingColors.Accent) }
                }
            }
        }

        if (showAnnivForm) {
            FrostCard {
                Text("纪念日图标（选一个）", fontSize = 12.sp, color = HomecomingColors.InkSoft)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf("❤", "🐱", "🐈‍⬛", "✨", "💐", "🌙").forEach { icon ->
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(
                                    if (annivIcon == icon) HomecomingColors.AccentSoft
                                    else HomecomingColors.IceBlue.copy(alpha = 0.35f)
                                )
                                .clickable { annivIcon = icon },
                            contentAlignment = Alignment.Center
                        ) { Text(icon, fontSize = 16.sp) }
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = annivTitle, onValueChange = { annivTitle = it },
                    label = { Text("纪念日名称（如：第一次见面）") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true
                )
                Spacer(Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { showAnnivForm = false }) { Text("取消", color = HomecomingColors.InkSoft) }
                    TextButton(onClick = {
                        if (annivTitle.isNotBlank()) {
                            val d = "%04d-%02d-%02d".format(date.year, date.monthValue, date.dayOfMonth)
                            val kind = when (annivIcon) {
                                "❤" -> "love"; "🐱" -> "yuri_birthday"; "🐈‍⬛" -> "sean_birthday"; else -> "custom"
                            }
                            if (HomecomingDayStore.addAnniversary(context, d, annivTitle.trim(), annivIcon, kind)) {
                                Toast.makeText(context, "纪念日钉好了", Toast.LENGTH_SHORT).show()
                                annivTitle = ""; showAnnivForm = false; onSaved()
                            } else Toast.makeText(context, "保存失败", Toast.LENGTH_SHORT).show()
                        }
                    }) { Text("钉上", color = HomecomingColors.Accent) }
                }
            }
        }

        Spacer(Modifier.height(28.dp))
    }
}

/** 摘要卡：卡上是摘要，点文字展开/收起完整内容。 */
@Composable
fun DaySummaryCard(
    k: String,
    label: String,
    summary: String,
    body: @Composable () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    FrostCard {
        Text(label, fontSize = 12.sp, color = HomecomingColors.InkSoft)
        Spacer(Modifier.height(4.dp))
        Text(
            summary,
            fontSize = 14.sp,
            color = HomecomingColors.Ink,
            maxLines = if (expanded) Int.MAX_VALUE else 2,
            overflow = TextOverflow.Ellipsis
        )
        if (expanded) body
        Text(
            if (expanded) "收起来" else "点开看完整内容",
            fontSize = 11.sp, color = HomecomingColors.Accent,
            modifier = Modifier
                .padding(top = 6.dp)
                .clickable { expanded = !expanded }
        )
    }
}
