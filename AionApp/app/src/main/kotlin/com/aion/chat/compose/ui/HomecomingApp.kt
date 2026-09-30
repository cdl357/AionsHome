package com.aion.chat.compose.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.aion.chat.compose.ui.chat.ChatScreen
import com.aion.chat.compose.ui.home.HomeScreen
import com.aion.chat.compose.ui.more.MoreScreen
import com.aion.chat.compose.ui.moments.MomentsScreen
import com.aion.chat.compose.ui.theme.HomecomingColors
import com.aion.chat.compose.ui.us.UsScreen
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.MailOutline
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Person

private val NavBlack = Color(0xFF202020)
private val GlassEdge = Brush.linearGradient(
    listOf(Color.White.copy(alpha = 0.70f), Color.White.copy(alpha = 0.15f))
)

/** 底部导航：聊天 | 朋友圈 | 回家(中心) | 我们 | 更多 —— 顺序固定。 */
enum class HomeTab(val route: String, val label: String, val icon: ImageVector) {
    Chat("chat", "聊天", Icons.Outlined.MailOutline),
    Moments("moments", "朋友圈", Icons.Outlined.FavoriteBorder),
    Home("home", "回家", Icons.Outlined.Home),
    Us("us", "我们", Icons.Outlined.Person),
    More("more", "更多", Icons.Outlined.Menu),
}

@Composable
fun HomecomingApp() {
    val navController = rememberNavController()
    var current by rememberSaveable { mutableStateOf(HomeTab.Home.route) }
    val onSelect: (HomeTab) -> Unit = { tab ->
        current = tab.route
        navController.navigate(tab.route) {
            popUpTo(HomeTab.Home.route) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        bottomBar = {
            HomecomingBottomBar(current = current, onSelect = onSelect)
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = HomeTab.Home.route,
            modifier = Modifier.fillMaxSize()
        ) {
            // 回家页背景全屏铺到底（导航胶囊浮在背景上），其余页正常避开导航
            composable(HomeTab.Home.route) {
                Box(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) { HomeScreen() }
            }
            composable(HomeTab.Chat.route) {
                Box(Modifier.fillMaxSize().padding(padding)) { ChatScreen() }
            }
            composable(HomeTab.Moments.route) {
                Box(Modifier.fillMaxSize().padding(padding)) { MomentsScreen() }
            }
            composable(HomeTab.Us.route) {
                Box(Modifier.fillMaxSize().padding(padding)) { UsScreen() }
            }
            composable(HomeTab.More.route) {
                Box(Modifier.fillMaxSize().padding(padding)) { MoreScreen() }
            }
        }
    }
}

@Composable
private fun HomecomingBottomBar(current: String, onSelect: (HomeTab) -> Unit) {
    // 悬浮透明胶囊：能看穿背景，只有边缘高光描边；图标黑色线性
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(50))
                .background(Color.White.copy(alpha = 0.12f))
                .border(1.dp, GlassEdge, RoundedCornerShape(50))
                .padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            HomeTab.entries.forEach { tab ->
                val selected = current == tab.route
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (selected) Color.White.copy(alpha = 0.38f) else Color.Transparent)
                        .clickable { onSelect(tab) }
                        .padding(horizontal = 13.dp, vertical = 7.dp)
                ) {
                    Icon(
                        imageVector = tab.icon,
                        contentDescription = tab.label,
                        tint = NavBlack,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = tab.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = NavBlack,
                        modifier = Modifier.padding(start = 5.dp)
                    )
                }
            }
        }
    }
}
