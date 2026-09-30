package com.aion.chat.compose.ui.settings

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
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
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aion.chat.compose.data.HomecomingData
import com.aion.chat.compose.data.SettingsBg
import com.aion.chat.compose.ui.home.FrostCard
import com.aion.chat.compose.ui.theme.HomecomingColors

/** 设置：换背景图先行；云线路 / 模型 / TTS / 主题在阶段五接入。 */
@Composable
fun SettingsScreen() {
    val context = LocalContext.current

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri != null) {
            val ok = runCatching {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    SettingsBg.backgroundFile(context).outputStream().use { output ->
                        input.copyTo(output)
                    }
                } != null
            }.getOrDefault(false)
            if (ok) {
                SettingsBg.bump()
                Toast.makeText(context, "背景已换好，回「回家」页看看", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "没读出这张图，换一张试试", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(18.dp)
    ) {
        Text("设置", fontSize = 22.sp, color = HomecomingColors.Ink)
        Text("先从换背景开始，其余在阶段五接入", fontSize = 12.sp, color = HomecomingColors.InkSoft)
        Spacer(Modifier.height(14.dp))

        FrostCard {
            Text("主页背景图", fontSize = 15.sp, color = HomecomingColors.Ink)
            Text(
                "从手机相册选一张，回家页立即换上；选竖图效果最好",
                fontSize = 12.sp,
                color = HomecomingColors.InkSoft
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = {
                        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("选一张照片") }
                OutlinedButton(
                    onClick = {
                        val f = SettingsBg.backgroundFile(context)
                        if (f.exists() && f.delete()) {
                            SettingsBg.bump()
                            Toast.makeText(context, "已恢复默认背景", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "现在用的就是默认背景", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("恢复默认") }
            }
        }

        FrostCard {
            Text("云线路 · 模型 · TTS · 主题", fontSize = 15.sp, color = HomecomingColors.Ink)
            Text("阶段五接入", fontSize = 12.sp, color = HomecomingColors.InkSoft, modifier = Modifier.padding(top = 4.dp))
        }
    }
}
