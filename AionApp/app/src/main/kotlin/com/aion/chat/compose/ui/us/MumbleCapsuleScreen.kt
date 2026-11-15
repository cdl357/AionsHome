package com.aion.chat.compose.ui.us

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aion.chat.compose.data.MumbleStore
import com.aion.chat.compose.data.TimeCapsuleStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.foundation.layout.width
import com.aion.chat.compose.ui.theme.HomecomingColors

/**
 * 碎碎念 + 时间胶囊（v0.3.164，她框架十二/十 合页）：
 * 上半 = 碎碎念（随手丢一句话，不回复不互动）
 * 下半 = 时间胶囊（写信设未来日期，到了那天才能开；可给对方）
 */
@Composable
fun MumbleCapsuleScreen(onBack: () -> Unit = {}) {
    val context = LocalContext.current
    var rev by remember { mutableIntStateOf(0) }
    var mumbleInput by remember { mutableStateOf("") }
    var capsuleText by remember { mutableStateOf("") }
    var capsuleRecipient by remember { mutableStateOf("yuri") }
    var capsuleDays by remember { mutableStateOf("30") }
    var capsuleOpen by remember { mutableStateOf<Long?>(null) }

    val mumbles = remember(rev) { MumbleStore.list(context) }
    val capsules = remember(rev) { TimeCapsuleStore.list(context) }
    val openCapsules = capsules.filter { TimeCapsuleStore.isOpen(it) }
    val sealedCapsules = capsules.filter { !TimeCapsuleStore.isOpen(it) }

    Column(
        Modifier
            .fillMaxSize()
            .background(Color(0xFFF2F0EB))
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .clickable { onBack() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回",
                    tint = Color(0xFF26211C), modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.weight(1f))
        }
        Text(
            "碎碎念",
            fontSize = 36.sp, color = Color(0xFF26211C), fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Text(
            "随手丢一句，不回复不互动，脑子里飘过的念头留这儿",
            fontSize = 11.sp, color = Color(0xFF8A8279),
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Spacer(Modifier.height(8.dp))

        // ── 碎碎念输入 ──
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)
        ) {
            OutlinedTextField(
                value = mumbleInput,
                onValueChange = { mumbleInput = it },
                placeholder = { Text("今天想到了什么…", fontSize = 12.sp, color = Color(0xFFB9B2A8)) },
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp),
                shape = RoundedCornerShape(12.dp),
                colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFFD8D2C8), unfocusedBorderColor = Color(0xFFE4DFD6),
                    focusedContainerColor = Color.White, unfocusedContainerColor = Color.White
                ),
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = {
                if (mumbleInput.isBlank()) return@TextButton
                MumbleStore.add(context, mumbleInput.trim(), "yuri")
                mumbleInput = ""
                rev++
            }) { Text("丢", color = HomecomingColors.Accent, fontWeight = FontWeight.Medium) }
        }
        LazyColumn(
            Modifier
                .fillMaxWidth()
                .height(180.dp)
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 6.dp)
        ) {
            items(mumbles, key = { it.id }) { m ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.White.copy(alpha = 0.8f))
                        .padding(horizontal = 10.dp, vertical = 7.dp)
                ) {
                    Text(
                        if (m.author == "sean") "🤍" else "🩵",
                        fontSize = 11.sp, modifier = Modifier.padding(end = 6.dp)
                    )
                    Text(
                        m.text, fontSize = 13.sp, color = Color(0xFF26211C),
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        SimpleDateFormat("MM-dd", Locale.US).format(Date(m.createdAt)),
                        fontSize = 9.sp, color = Color(0xFFB9B2A8)
                    )
                    if (m.author == "yuri") {
                        Icon(
                            Icons.Filled.Close, contentDescription = "删",
                            tint = Color(0xFFB9B2A8), modifier = Modifier
                                .padding(start = 4.dp)
                                .clickable { MumbleStore.delete(context, m.id); rev++ }
                                .size(14.dp)
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        // ── 时间胶囊 ──
        Text(
            "时间胶囊",
            fontSize = 22.sp, color = Color(0xFF26211C), fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Text(
            "写信设一个未来的日期，到了那天才能打开。可以给自己，也可以给对方",
            fontSize = 11.sp, color = Color(0xFF8A8279),
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Spacer(Modifier.height(6.dp))

        // 已到期的胶囊（自动打开）
        openCapsules.forEach { cp ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFFFFF7E6))
                    .border(1.5.dp, Color(0xFFE0B35C), RoundedCornerShape(16.dp))
                    .padding(14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "🫙 已到开瓶日 · " + SimpleDateFormat("MM-dd", Locale.US).format(Date(cp.createdAt)) + " 写给 " +
                                (if (cp.recipient == "yuri") "小鑫" else "Sean"),
                        fontSize = 11.sp, color = Color(0xFFB98A3C)
                    )
                    Spacer(Modifier.weight(1f))
                    Icon(
                        Icons.Filled.Close, contentDescription = "删",
                        tint = Color(0xFFB98A3C), modifier = Modifier
                            .size(16.dp)
                            .clickable { TimeCapsuleStore.delete(context, cp.id); rev++ }
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    cp.text, fontSize = 14.sp, color = Color(0xFF26211C), lineHeight = 20.sp
                )
            }
        }

        // 新胶囊输入
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color.White)
                .padding(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (capsuleRecipient == "yuri") "给小鑫" else "给 Sean",
                    fontSize = 12.sp, color = Color(0xFF7A6A55), fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.weight(1f))
                Text(
                    if (capsuleRecipient == "yuri") "写给对方 ↺" else "写给自己 ↺",
                    fontSize = 11.sp, color = Color(0xFF576B95),
                    modifier = Modifier.clickable {
                        capsuleRecipient = if (capsuleRecipient == "yuri") "sean" else "yuri"
                    }.padding(4.dp)
                )
            }
            OutlinedTextField(
                value = capsuleText,
                onValueChange = { capsuleText = it },
                placeholder = { Text("想对未来说的话…", fontSize = 12.sp, color = Color(0xFFB9B2A8)) },
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, color = Color(0xFF26211C)),
                minLines = 3,
                shape = RoundedCornerShape(10.dp),
                colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFFD8D2C8), unfocusedBorderColor = Color(0xFFE4DFD6),
                    focusedContainerColor = Color(0xFFFCFAF6), unfocusedContainerColor = Color(0xFFFCFAF6)
                ),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("多少天后打开：", fontSize = 12.sp, color = Color(0xFF6F675E))
                OutlinedTextField(
                    value = capsuleDays,
                    onValueChange = { capsuleDays = it.filter { ch -> ch.isDigit() } },
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center),
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.width(56.dp)
                )
                Text("天", fontSize = 12.sp, color = Color(0xFF6F675E))
                Spacer(Modifier.weight(1f))
                TextButton(onClick = {
                    val days = capsuleDays.toLongOrNull() ?: return@TextButton
                    if (capsuleText.isBlank()) return@TextButton
                    val openAt = System.currentTimeMillis() + days * 86400_000L
                    TimeCapsuleStore.add(context, "yuri", capsuleRecipient, capsuleText.trim(), openAt)
                    capsuleText = ""
                    Toast.makeText(context, "胶囊封好了——${days}天后见", Toast.LENGTH_LONG).show()
                    rev++
                }) { Text("封存 🫙", color = Color(0xFF7A6A55), fontWeight = FontWeight.Medium) }
            }
        }

        // 未到期胶囊列表
        if (sealedCapsules.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            sealedCapsules.forEach { cp ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 3.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.White.copy(alpha = 0.6f))
                        .padding(horizontal = 10.dp, vertical = 7.dp)
                ) {
                    Text(
                        "🫙 " + SimpleDateFormat("MM-dd", Locale.US).format(Date(cp.createdAt)) +
                                " → " + SimpleDateFormat("MM-dd", Locale.US)
                            .format(Date(cp.openAtMs)) + "（" +
                                ((cp.openAtMs - System.currentTimeMillis()) / 86400_000L + 1) + " 天后）",
                        fontSize = 11.sp, color = Color(0xFF8A8279), modifier = Modifier.weight(1f)
                    )
                    Icon(
                        Icons.Filled.Close, contentDescription = "删",
                        tint = Color(0xFFB9B2A8), modifier = Modifier
                            .size(14.dp)
                            .clickable { TimeCapsuleStore.delete(context, cp.id); rev++ }
                    )
                }
            }
        }
        Spacer(Modifier.height(20.dp))
    }
}
