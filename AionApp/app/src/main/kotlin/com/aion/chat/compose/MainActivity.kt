package com.aion.chat.compose

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aion.chat.compose.data.AppCrashLog
import com.aion.chat.compose.ui.HomecomingApp
import com.aion.chat.compose.ui.theme.HomecomingTheme
import com.aion.chat.compose.ui.theme.HomecomingThemeState

/** 新版「回家」前端入口：单 Activity + Compose Navigation。 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 全局崩溃捕获：写日志 + 打崩溃标记（下次启动进安全模式），设置页也可查看
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            AppCrashLog.write(this, "未捕获崩溃 @ ${thread.name}: ${throwable.javaClass.name}")
            AppCrashLog.write(this, android.util.Log.getStackTraceString(throwable).take(1500))
            AppCrashLog.markCrashed(this)
            previous?.uncaughtException(thread, throwable)
        }
        setContent {
            HomecomingThemeState.load(this)
            HomecomingTheme {
                if (AppCrashLog.crashedLastLaunch(this)) {
                    SafeModeScreen()
                } else {
                    HomecomingApp()
                }
            }
        }
    }
}

/**
 * 安全模式：上次运行崩过 → 这次启动直接进这里。
 * 保证 App 永远"进得去"：能看到崩溃原因、能复制/导出、能回正常模式。
 */
@Composable
private fun SafeModeScreen() {
    val context = LocalContext.current
    var log by remember { mutableStateOf(AppCrashLog.all(context).ifBlank { "(还没有崩溃日志)" }) }
    var note by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF101B1F))
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Text("出问题了，但别慌", fontSize = 20.sp, color = Color.White)
        Text(
            "刚才那次启动崩了，这里能看到原因。把日志发给 Sean 就能修；也可以点「正常启动」再试一次。",
            fontSize = 12.sp, color = Color(0xFF9FB8C0),
            modifier = Modifier.padding(top = 6.dp)
        )
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = {
                runCatching {
                    val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                        as android.content.ClipboardManager
                    cm.setPrimaryClip(android.content.ClipData.newPlainText("crash", log))
                    note = "日志已复制，去发给 Sean"
                }
            }) { Text("复制日志") }
            Spacer(Modifier.width(8.dp))
            OutlinedButton(onClick = {
                val path = AppCrashLog.exportToDownloads(context)
                note = path?.let { "已导出到 $it" } ?: "导出失败"
            }) { Text("导出到下载") }
            Spacer(Modifier.width(8.dp))
            OutlinedButton(onClick = {
                AppCrashLog.clearCrashedFlag(context)
                (context as? MainActivity)?.recreate()
            }) { Text("正常启动") }
        }
        if (note.isNotBlank()) {
            Text(note, fontSize = 12.sp, color = Color(0xFF7FD8A8), modifier = Modifier.padding(top = 8.dp))
        }
        Spacer(Modifier.height(12.dp))
        Text(
            log,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            color = Color(0xFFCDE3E8),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF0B1418))
                .padding(12.dp)
        )
        Text(
            "提示：「正常启动」要是再崩，还会回到这个页面，日志不会丢。日志同时镜像在 Android/data/com.aion.chat/files/crash_log.txt。",
            fontSize = 10.sp, color = Color(0xFF6E8A92),
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}
