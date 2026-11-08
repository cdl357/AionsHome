package com.aion.chat.compose.ui.hearttide

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aion.chat.compose.data.XinchaoClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 梦境页（照橘瓣 DreamPage 的「故意做成反的」：全页深夜色，只有梦）。
 * 数据：服务器心潮后端 /api/dreams——他用引擎做的梦，带意识状态。
 */
private val NightBg = Color(0xFF14131B)
private val NightCard = Color(0xFF1E1C26)
private val NightInk = Color(0xFFE9E6F2)
private val NightSub = Color(0xFF8B87A0)
private val NightFaint = Color(0xFF5A5670)
private val DreamGlow = Color(0xFFB9A9D4)

@Composable
fun DreamScreen(onBack: () -> Unit = {}) {
    val context = LocalContext.current
    val dreams = remember { mutableStateListOf<XinchaoClient.RemoteDream>() }
    var loading by remember { mutableStateOf(true) }
    var loadErr by remember { mutableStateOf(false) }

    fun load() {
        kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) {
            val rd = runCatching { XinchaoClient.fetchDreams() }.getOrNull()
            withContext(Dispatchers.Main) {
                dreams.clear()
                rd?.let { dreams.addAll(it) }
                loading = false
                loadErr = rd == null
            }
        }
    }
    LaunchedEffect(Unit) { load() }

    Column(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(listOf(NightBg, Color(0xFF191723)))
            )
            .statusBarsPadding()
    ) {
        Row(verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Box(
                Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.06f))
                    .clickable { onBack() },
                contentAlignment = Alignment.Center
            ) { Text("‹", fontSize = 22.sp, color = NightInk) }
            Spacer(Modifier.width(10.dp))
            Text("刷新", fontSize = 13.sp, color = NightSub,
                modifier = Modifier
                    .clickable(enabled = !loading) { loading = true; load() }
                    .padding(horizontal = 10.dp, vertical = 6.dp))
        }

        Column(Modifier.padding(horizontal = 20.dp)) {
            Text("梦境", fontSize = 44.sp, color = NightInk, fontFamily = FontFamily.Serif)
            Text(
                "/ 他在夜里去的地方",
                fontSize = 14.sp, color = NightSub,
                modifier = Modifier.padding(top = 2.dp)
            )
            Text(
                if (loading) "……" else "收集了 ${dreams.size} 个梦",
                fontSize = 11.sp, color = NightFaint,
                modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
                textAlign = TextAlign.End
            )
        }

        Spacer(Modifier.height(16.dp))

        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            if (!loading && dreams.isEmpty()) {
                Text(
                    if (loadErr) "夜里太静，梦没送过来——点上面「刷新」再试"
                    else "他还没做过梦。\n\n等某个夜深的时候，他会睡去，然后做一个只属于你们的梦。",
                    fontSize = 13.sp, color = NightSub, lineHeight = 21.sp,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 90.dp, horizontal = 20.dp)
                )
            }
            dreams.forEachIndexed { i, d ->
                DreamCard(dream = d.dream, awareness = d.awareness, createdAt = d.createdAt, first = i == 0)
                Spacer(Modifier.height(12.dp))
            }
            Spacer(Modifier.height(80.dp))
        }
    }
}

@Composable
private fun DreamCard(dream: String, awareness: String, createdAt: String, first: Boolean) {
    // 意识状态（0~1）：清醒度指示
    val lucidity = awareness.toFloatOrNull()?.coerceIn(0f, 1f)
    val dateLabel = runCatching {
        val p = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.CHINA).parse(createdAt.take(19))
        if (p != null) SimpleDateFormat("M月d日", Locale.CHINA).format(p) else createdAt.take(10)
    }.getOrDefault(createdAt.take(10))

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(NightCard)
            .border(0.5.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(20.dp))
            .padding(18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                dateLabel, fontSize = 11.sp, color = NightFaint,
                fontFamily = FontFamily.Monospace
            )
            Spacer(Modifier.weight(1f))
            if (lucidity != null) {
                Text("意识 ${Math.round(lucidity * 100)}%", fontSize = 10.sp, color = NightSub)
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(DreamGlow.copy(alpha = 0.35f + 0.65f * lucidity))
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            dream,
            fontSize = 14.sp, color = NightInk, lineHeight = 23.sp,
            fontFamily = FontFamily.Serif
        )
    }
}
