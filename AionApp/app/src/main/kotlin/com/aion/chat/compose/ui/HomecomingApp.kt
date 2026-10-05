package com.aion.chat.compose.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalContext
import com.aion.chat.compose.data.SettingsBg
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.aion.chat.compose.ui.chat.ChatScreen
import com.aion.chat.compose.ui.home.HomeScreen
import com.aion.chat.compose.ui.album.AlbumScreen
import com.aion.chat.compose.ui.board.BoardScreen
import com.aion.chat.compose.ui.call.CallScreen
import com.aion.chat.compose.ui.memories.MemoriesScreen
import com.aion.chat.compose.ui.reading.ReadingScreen
import com.aion.chat.compose.ui.hearttide.HeartTideScreen
import com.aion.chat.compose.rem.ReminderScreen
import com.aion.chat.compose.ui.more.MoreScreen
import com.aion.chat.compose.ui.splash.SplashScreen
import com.aion.chat.compose.ui.settings.SettingsScreen
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
    var current by rememberSaveable { mutableStateOf("splash") }
    val onSelect: (HomeTab) -> Unit = { tab ->
        current = tab.route
        navController.navigate(tab.route) {
            popUpTo(HomeTab.Home.route) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    // ── 全局背景：所有页面浮在同一张图上，设置换一次全 App 生效 ──
    val appContext = androidx.compose.ui.platform.LocalContext.current
    val bgStamp = SettingsBg.stamp
    val bgBitmap = remember(bgStamp) { SettingsBg.loadBitmap(appContext)?.asImageBitmap() }
    // 键盘弹出时收起底部胶囊，别浮在聊天输入框上
    val imeOpen = WindowInsets.ime.getBottom(androidx.compose.ui.platform.LocalDensity.current) > 0

    // App 打开时把桌面小组件刷成最新数据（天数/情话/心跳）
    LaunchedEffect(Unit) {
        runCatching { com.aion.chat.widget.CompanionWidgetProvider.refreshAll(appContext) }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // 背景层
        if (bgBitmap != null) {
            Image(
                bitmap = bgBitmap,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(HomecomingColors.IceBlueLight, Color.White, HomecomingColors.IceBlue.copy(alpha = 0.30f))
                        )
                    )
            )
        }

        Scaffold(
            containerColor = Color.Transparent,
            bottomBar = {
                if (current != "splash" && !imeOpen) {
                    HomecomingBottomBar(current = current, onSelect = onSelect)
                }
            }
        ) { padding ->
            NavHost(
                navController = navController,
                startDestination = "splash",
                modifier = Modifier.fillMaxSize()
            ) {
                composable("splash") {
                    SplashScreen(onFinished = {
                        current = HomeTab.Home.route
                        navController.navigate(HomeTab.Home.route) {
                            popUpTo("splash") { inclusive = true }
                            launchSingleTop = true
                        }
                    })
                }
                composable(HomeTab.Home.route) {
                    Box(Modifier.fillMaxSize()) {
                        HomeScreen(
                            onOpenAlbum = { navController.navigate("album") },
                            onOpenChat = {
                                navController.navigate(HomeTab.Chat.route) {
                                    popUpTo(HomeTab.Home.route) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            onOpenDiary = {
                                navController.navigate(HomeTab.Us.route) {
                                    popUpTo(HomeTab.Home.route) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            onOpenBoard = { navController.navigate("board") },
                            onOpenMemories = { navController.navigate("memories") }
                        )
                    }
                }
                composable(HomeTab.Chat.route) {
                    Box(Modifier.fillMaxSize()) {
                        ChatScreen(onOpenCall = { navController.navigate("call") })
                    }
                }
                composable(HomeTab.Moments.route) {
                    Box(Modifier.fillMaxSize()) { MomentsScreen() }
                }
                composable(HomeTab.Us.route) {
                    Box(Modifier.fillMaxSize()) { UsScreen() }
                }
                composable("settings") { SettingsScreen() }
                composable("album") { AlbumScreen(onBack = { navController.popBackStack() }) }
                composable("board") { BoardScreen() }
                composable("memories") { MemoriesScreen(onBack = { navController.popBackStack() }) }
                composable("reading") { ReadingScreen(onBack = { navController.popBackStack() }) }
                composable("reminders") { ReminderScreen() }
                composable("hearttide") { HeartTideScreen(onBack = { navController.popBackStack() }) }
                composable("call") { CallScreen(onHangUp = { navController.popBackStack() }) }
                composable(HomeTab.More.route) {
                    Box(Modifier.fillMaxSize()) {
                        MoreScreen(
                            onOpenSettings = { navController.navigate("settings") },
                            onOpenAlbum = { navController.navigate("album") },
                            onOpenMemories = { navController.navigate("memories") },
                            onOpenReading = { navController.navigate("reading") },
                            onOpenReminders = { navController.navigate("reminders") },
                            onOpenCall = { navController.navigate("call") },
                            onOpenHeartTide = { navController.navigate("hearttide") },
                            onOpenBoard = { navController.navigate("board") }
                        )
                    }
                }
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
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            HomeTab.entries.forEach { tab ->
                val selected = current == tab.route
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 4.dp)
                        .clip(RoundedCornerShape(50))
                        .background(if (selected) Color.White.copy(alpha = 0.38f) else Color.Transparent)
                        .clickable { onSelect(tab) }
                        .padding(vertical = 6.dp)
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
