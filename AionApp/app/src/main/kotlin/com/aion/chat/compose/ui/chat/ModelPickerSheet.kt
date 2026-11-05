package com.aion.chat.compose.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aion.chat.compose.data.ProviderStore
import com.aion.chat.compose.ui.theme.HomecomingColors

/**
 * 模型选择弹层（橘瓣 ChatInput.ModelSelector 同款交互）：
 * 搜索 + 按提供商分组的模型列表，点选即切换（ProviderStore.setActive → routes.json 镜像同步）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelPickerSheet(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val rev = ProviderStore.rev
    val providers = remember(rev) {
        ProviderStore.list(context).filter { it.enabled && it.models.isNotEmpty() }
    }
    val activeId = remember(rev) { ProviderStore.activeId(context) }
    val activeModel = remember(rev) { ProviderStore.activeModelKey(context) }
    var search by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFFF7F8FA)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.72f)
                .imePadding()
                .padding(horizontal = 8.dp)
        ) {
            Text(
                "选择模型",
                fontSize = 16.sp, color = Color(0xFF26211C), fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
            )
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                placeholder = { Text("搜索模型 / 提供商", fontSize = 13.sp, color = Color(0xFF9A9388)) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)
            )
            Spacer(Modifier.height(4.dp))

            LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
                providers.forEach { p ->
                    val models = p.models.filter {
                        search.isBlank() || it.model.contains(search, true) || p.name.contains(search, true)
                    }
                    if (models.isEmpty()) return@forEach
                    // 分组头：提供商
                    item(key = "h_" + p.id) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(26.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF7FA6C9).copy(alpha = 0.25f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    p.name.take(1).ifBlank { "?" },
                                    fontSize = 12.sp, color = Color(0xFF26211C),
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            Text(
                                p.name.ifBlank { "未命名" } + " · ${models.size}",
                                fontSize = 13.sp, color = Color(0xFF6B5D4F),
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                    models.forEach { m ->
                        val active = p.id == activeId && m.key == activeModel
                        item(key = p.id + "_" + m.key) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        if (active) Color(0xFF7FA6C9).copy(alpha = 0.14f)
                                        else Color.Transparent
                                    )
                                    .clickable {
                                        ProviderStore.setActive(context, p.id, m.key)
                                        onDismiss()
                                    }
                                    .padding(horizontal = 14.dp, vertical = 10.dp)
                            ) {
                                Text(
                                    if (active) "●" else "○",
                                    fontSize = 13.sp,
                                    color = if (active) HomecomingColors.Accent else Color(0xFFB9B2A6)
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    m.model,
                                    fontSize = 14.sp,
                                    color = if (active) HomecomingColors.Accent else Color(0xFF26211C),
                                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
                if (providers.isEmpty()) {
                    item(key = "empty") {
                        Text(
                            "还没有启用的提供商\n去 设置 → 模型提供商 新增：填 URL 和 Key，拉模型列表勾选",
                            fontSize = 13.sp, color = Color(0xFF9A9388),
                            lineHeight = 21.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 50.dp)
                        )
                    }
                }
            }
        }
    }
}
