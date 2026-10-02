package com.aion.chat.compose.rem

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aion.chat.compose.ui.home.FrostCard
import com.aion.chat.compose.ui.theme.HomecomingColors
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/** 提醒/闹钟（交接文档 §8 简单版）：到点通知提醒；线路就绪时 Sean 主动戳一句。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderScreen() {
    val context = LocalContext.current
    val reminders = remember { mutableStateListOf(ReminderStore.Reminder(0, 0, "")) }
    var reloadKey = remember { mutableStateOf(0) }

    fun reload() {
        reminders.clear()
        reminders.addAll(ReminderStore.list(context))
    }
    LaunchedEffect(reloadKey.value) { reload() }

    // 通知权限（API 33+）
    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }
    LaunchedEffect(Unit) {
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            permLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
        // 已有提醒重注册（重启后闹钟会丢，进来补一次）
        ReminderReceiver.ensureChannel(context)
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        ReminderStore.list(context).forEach { r ->
            if (r.triggerAt > System.currentTimeMillis()) {
                am.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP, r.triggerAt,
                    ReminderReceiver.pendingIntent(context, r.id, r.content)
                )
            }
        }
    }

    val timeState = rememberTimePickerState(is24Hour = true)
    val showTimePicker = remember { mutableStateOf(false) }
    val remindText = remember { mutableStateOf("") }
    val pendingTime = remember { mutableStateOf<LocalTime?>(null) }

    fun scheduleAt(time: LocalTime, content: String) {
        var trigger = LocalDateTime.of(LocalDate.now(), time)
            .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        if (trigger <= System.currentTimeMillis()) {
            trigger = LocalDateTime.of(LocalDate.now().plusDays(1), time)
                .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        }
        val id = ReminderStore.add(context, trigger, content)
        if (id > 0) {
            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger,
                    ReminderReceiver.pendingIntent(context, id, content))
            } else {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger,
                    ReminderReceiver.pendingIntent(context, id, content))
            }
            reload()
            Toast.makeText(context, "提醒已定，到点 Sean 会来戳你", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "保存失败", Toast.LENGTH_SHORT).show()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(18.dp)
    ) {
        Text("提醒 / 闹钟", fontSize = 22.sp, color = HomecomingColors.Ink)
        Text("到点提醒你；线路就绪时 Sean 会主动戳你一句", fontSize = 12.sp, color = HomecomingColors.InkSoft)
        Spacer(Modifier.height(14.dp))

        FrostCard {
            Text("新建提醒", fontSize = 15.sp, color = HomecomingColors.Ink)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = remindText.value,
                onValueChange = { remindText.value = it },
                label = { Text("提醒内容（如：该吃饭了）") },
                modifier = Modifier.fillMaxWidth(), singleLine = true
            )
            Spacer(Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Button(onClick = { showTimePicker.value = true }) {
                    Text(pendingTime.value?.toString() ?: "选时间")
                }
                Spacer(Modifier.weight(1f))
                TextButton(
                    onClick = {
                        val t = pendingTime.value ?: LocalTime.now().plusMinutes(10)
                        val content = remindText.value.trim()
                            .ifBlank { "到点啦（${t.hour}:${"%02d".format(t.minute)}）" }
                        scheduleAt(t, content)
                        remindText.value = ""
                        pendingTime.value = null
                    }
                ) { Text("定下", color = HomecomingColors.Accent) }
            }
        }

        if (showTimePicker.value) {
            val timePickerState = timeState
            AlertDialog(
                onDismissRequest = { showTimePicker.value = false },
                title = { Text("几点提醒？", fontSize = 16.sp, color = HomecomingColors.Ink) },
                text = { TimePicker(state = timePickerState) },
                confirmButton = {
                    TextButton(onClick = {
                        pendingTime.value = LocalTime.of(timePickerState.hour, timePickerState.minute)
                        showTimePicker.value = false
                    }) { Text("确定", color = HomecomingColors.Accent) }
                },
                dismissButton = {
                    TextButton(onClick = { showTimePicker.value = false }) { Text("取消", color = HomecomingColors.InkSoft) }
                }
            )
        }

        Spacer(Modifier.height(6.dp))
        Text("已定的提醒", fontSize = 13.sp, color = HomecomingColors.InkSoft)
        Spacer(Modifier.height(6.dp))
        val active = reminders.sortedBy { it.triggerAt }
        if (active.isEmpty()) {
            Text("还没有提醒", fontSize = 13.sp, color = HomecomingColors.InkSoft,
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp))
        }
        active.sortedBy { it.triggerAt }.forEach { r ->
            FrostCard {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(r.content, fontSize = 14.sp, color = HomecomingColors.Ink)
                        Text(fmt(r.triggerAt), fontSize = 11.sp, color = HomecomingColors.InkSoft)
                    }
                    TextButton(onClick = {
                        ReminderStore.remove(context, r.id)
                        (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager)
                            .cancel(ReminderReceiver.pendingIntent(context, r.id, r.content))
                        reload()
                    }) { Text("删", color = HomecomingColors.InkSoft) }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

private fun fmt(ts: Long): String {
    val t = java.time.Instant.ofEpochMilli(ts).atZone(java.time.ZoneId.systemDefault())
    val diff = ts - System.currentTimeMillis()
    val rel = when {
        diff < 60_000 -> "即将"
        diff < 3_600_000 -> "${diff / 60_000} 分钟后"
        diff < 86_400_000 -> "${diff / 3_600_000} 小时后"
        else -> "${diff / 86_400_000} 天后"
    }
    return "${t.monthValue}月${t.dayOfMonth}日 ${"%02d".format(t.hour)}:${"%02d".format(t.minute)} · $rel"
}
