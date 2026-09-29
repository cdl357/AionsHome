package com.aion.chat.compose.ui.moments

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aion.chat.compose.ui.theme.HomecomingColors

/**
 * 页面④：朋友圈。阶段一骨架：封面 + 空态。
 * 阶段四接线：双向动态流（发文字+配图、点赞评论），数据独立存本地，不进日历。
 */
@Composable
fun MomentsScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        // 封面头图（可换，方案 C）
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .background(
                    Brush.verticalGradient(listOf(HomecomingColors.IceBlue, HomecomingColors.IceBlueLight))
                ),
            contentAlignment = Alignment.BottomStart
        ) {
            Text(
                "朋友圈",
                fontSize = 20.sp,
                color = HomecomingColors.Ink,
                modifier = Modifier.padding(16.dp)
            )
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("还没有动态", fontSize = 14.sp, color = HomecomingColors.InkSoft)
            Text(
                "Yuri 和 Sean 都能发、能互相点赞评论（阶段四接线本地朋友圈存储）",
                fontSize = 11.sp,
                color = HomecomingColors.InkSoft,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
    }
}
