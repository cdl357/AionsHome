package com.aion.chat.compose

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.aion.chat.compose.ui.HomecomingApp
import com.aion.chat.compose.ui.theme.HomecomingTheme

/** 新版「回家」前端入口：单 Activity + Compose Navigation。 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            HomecomingTheme {
                HomecomingApp()
            }
        }
    }
}
