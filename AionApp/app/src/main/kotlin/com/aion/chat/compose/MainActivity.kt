package com.aion.chat.compose

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.aion.chat.compose.data.AppCrashLog
import com.aion.chat.compose.ui.HomecomingApp
import com.aion.chat.compose.ui.theme.HomecomingTheme
import com.aion.chat.compose.ui.theme.HomecomingThemeState

/** 新版「回家」前端入口：单 Activity + Compose Navigation。 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 全局崩溃捕获：写入本地日志，设置页可查看（下一轮修复的依据）
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            AppCrashLog.write(this, "未捕获崩溃 @ ${thread.name}: ${throwable.javaClass.name}")
            AppCrashLog.write(this, android.util.Log.getStackTraceString(throwable).take(1200))
            previous?.uncaughtException(thread, throwable)
        }
        setContent {
            HomecomingThemeState.load(this)
            HomecomingTheme {
                HomecomingApp()
            }
        }
    }
}
