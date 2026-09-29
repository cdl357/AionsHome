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
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person

/** 底部导航：聊天 | 朋友圈 | 回家(中心凸起) | 我们 | 更多 —— 顺序固定。 */
enum class HomeTab(val route: String, val label: String, val icon: ImageVector) {
    Chat("chat", "聊天", Icons.Filled.MailOutline),
    Moments("moments", "朋友圈", Icons.Filled.FavoriteBorder),
    Home("home", "回家", Icons.Filled.Home),
    Us("us", "我们", Icons.Filled.Person),
    More("more", "更多", Icons.Filled.Menu),
}

@Composable
fun HomecomingApp() {
    val navController = rememberNavController()
    var current by rememberSaveable { mutableStateOf(HomeTab.Home.route) }

    Scaffold(
        containerColor = Color.Transparent,
        bottomBar = {
            HomecomingBottomBar(
                current = current,
                onSelect = { tab ->
                    current = tab.route
                    navController.navigate(tab.route) {
                        popUpTo(HomeTab.Home.route) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            )
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = HomeTab.Home.route,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            composable(HomeTab.Home.route) { HomeScreen() }
            composable(HomeTab.Chat.route) { ChatScreen() }
            composable(HomeTab.Moments.route) { MomentsScreen() }
            composable(HomeTab.Us.route) { UsScreen() }
            composable(HomeTab.More.route) { MoreScreen() }
        }
    }
}

@Composable
private fun HomecomingBottomBar(current: String, onSelect: (HomeTab) -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(Color.White.copy(alpha = 0.9f), HomecomingColors.IceBlueLight.copy(alpha = 0.95f))
                )
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            HomeTab.entries.forEach { tab ->
                if (tab == HomeTab.Home) {
                    CenterHomeButton(selected = current == tab.route, onClick = { onSelect(tab) })
                } else {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .padding(vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = tab.icon,
                            contentDescription = tab.label,
                            tint = if (current == tab.route) HomecomingColors.WarmPink else HomecomingColors.InkSoft,
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = tab.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (current == tab.route) HomecomingColors.WarmPink else HomecomingColors.InkSoft
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CenterHomeButton(selected: Boolean, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.offset(y = (-10).dp)
    ) {
        Box(
            modifier = Modifier
                .size(54.dp)
                .background(
                    Brush.linearGradient(listOf(HomecomingColors.IceBlue, HomecomingColors.WarmPink.copy(alpha = 0.85f))),
                    CircleShape
                )
                .border(3.dp, Color.White, CircleShape)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Home,
                contentDescription = "回家",
                tint = Color.White,
                modifier = Modifier.size(22.dp)
            )
        }
        Text(
            text = "回家",
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) HomecomingColors.WarmPink else HomecomingColors.InkSoft
        )
    }
}
