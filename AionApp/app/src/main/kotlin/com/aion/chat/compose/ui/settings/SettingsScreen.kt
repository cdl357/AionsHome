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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aion.chat.compose.data.HomecomingData
import com.aion.chat.compose.data.HomecomingRouteConfig
import com.aion.chat.compose.data.SettingsBg
import com.aion.chat.compose.ui.home.FrostCard
import com.aion.chat.compose.ui.theme.HomecomingColors

/** 设置：换背景图先行；云线路 / 模型 / TTS / 主题在阶段五接入。 */
@Composable
fun SettingsScreen() {
    val context = LocalContext.current

    val routeLabel = remember { mutableStateOf("") }
    val routeBaseUrl = remember { mutableStateOf("") }
    val routeApiKey = remember { mutableStateOf("") }
    val routeModel = remember { mutableStateOf("") }
    val routeSaved = remember { mutableStateOf(false) }

    fun saveRoute() {
        val baseUrl = routeBaseUrl.value.trim()
        val apiKey = routeApiKey.value.trim()
        val model = routeModel.value.trim()
        if (baseUrl.isBlank() || apiKey.isBlank() || model.isBlank()) {
            Toast.makeText(context, "Base URL / API Key / 模型都要填", Toast.LENGTH_SHORT).show()
            return
        }
        val root = com.aion.chat.compose.data.HomecomingRouteConfig.buildRoot(
            routeLabel.value.trim(), baseUrl, apiKey, model
        )
        if (com.aion.chat.compose.data.HomecomingRouteConfig.save(context, root)) {
            routeSaved.value = true
            Toast.makeText(context, "云线路已保存，聊天页即可用", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "保存失败", Toast.LENGTH_SHORT).show()
        }
    }

    val chatPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri != null) {
            val ok = runCatching {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    SettingsBg.chatFile(context).outputStream().use { output ->
                        input.copyTo(output)
                    }
                } != null
            }.getOrDefault(false)
            if (ok) {
                SettingsBg.bump()
                Toast.makeText(context, "聊天背景已换好", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "没读出这张图，换一张试试", Toast.LENGTH_SHORT).show()
            }
        }
    }

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
            Text("云线路（聊天用）", fontSize = 15.sp, color = HomecomingColors.Ink)
            Text(
                "OpenAI 兼容接口：填 Base URL / API Key / 模型",
                fontSize = 12.sp,
                color = HomecomingColors.InkSoft
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = routeLabel.value, onValueChange = { routeLabel.value = it },
                label = { Text("线路名称（可不填）") },
                modifier = Modifier.fillMaxWidth(), singleLine = true
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = routeBaseUrl.value, onValueChange = { routeBaseUrl.value = it },
                label = { Text("Base URL（如 https://api.xx.com/v1）") },
                modifier = Modifier.fillMaxWidth(), singleLine = true
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = routeApiKey.value, onValueChange = { routeApiKey.value = it },
                label = { Text("API Key") },
                modifier = Modifier.fillMaxWidth(), singleLine = true
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = routeModel.value, onValueChange = { routeModel.value = it },
                label = { Text("模型（如 gpt-4o-mini）") },
                modifier = Modifier.fillMaxWidth(), singleLine = true
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = { saveRoute() }) { Text("保存线路", color = HomecomingColors.Accent) }
            }
            if (routeSaved.value) {
                Text("已保存。回「聊天」页即可开聊。", fontSize = 11.sp, color = HomecomingColors.Ok)
            }
        }

        FrostCard {
            Text("聊天背景图（可选覆盖）", fontSize = 15.sp, color = HomecomingColors.Ink)
            Text(
                "只给聊天页换一张；不选则跟随上面的全局背景",
                fontSize = 12.sp,
                color = HomecomingColors.InkSoft
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = {
                        chatPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("选一张照片") }
                OutlinedButton(
                    onClick = {
                        val f = SettingsBg.chatFile(context)
                        if (f.exists() && f.delete()) {
                            SettingsBg.bump()
                            Toast.makeText(context, "聊天页已跟随全局背景", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "聊天页现在用的就是全局背景", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("跟随全局") }
            }
        }

        FrostCard {
            Text("云线路 · 模型 · TTS · 主题", fontSize = 15.sp, color = HomecomingColors.Ink)
            Text("阶段五接入", fontSize = 12.sp, color = HomecomingColors.InkSoft, modifier = Modifier.padding(top = 4.dp))
        }
    }
}
