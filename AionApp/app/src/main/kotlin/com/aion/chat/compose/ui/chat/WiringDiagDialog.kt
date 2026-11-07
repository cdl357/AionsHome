package com.aion.chat.compose.ui.chat

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 线路装配诊断卡：胶囊点重试仍失败时自动弹出。
 * 顶栏胶囊只留一句短文案（用户定稿：长异常塞胶囊里太丑），
 * 完整原因和堆栈收在这里——「复制详情」一键带给开发者。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WiringDiagDialog(
    error: String,
    trace: String?,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFFF7F5F1),
        shape = RoundedCornerShape(20.dp),
        title = { Text("线路异常", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF26211C)) },
        text = {
            Column {
                Text(
                    "聊天引擎没能装配起来，点「重试装配」再试一次。" +
                        "如果一直这样，点「复制详情」把下面的内容发给小鑫定位。",
                    fontSize = 13.sp, color = Color(0xFF6B5D4F), lineHeight = 20.sp
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    error,
                    fontSize = 13.sp, color = Color(0xFFB4574E),
                    fontWeight = FontWeight.Medium
                )
                if (!trace.isNullOrBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        trace,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFF8A8378),
                        lineHeight = 12.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .background(Color(0xFFEFECE6), RoundedCornerShape(10.dp))
                            .verticalScroll(rememberScrollState())
                            .padding(8.dp)
                    )
                }
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = {
                        clipboard.setText(AnnotatedString("线路装配失败\n" + error + "\n" + (trace ?: "")))
                        Toast.makeText(context, "已复制，发给小鑫就行", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("复制详情", fontSize = 13.sp, color = Color(0xFF6B5D4F)) }
            }
        },
        confirmButton = {
            TextButton(onClick = onRetry) { Text("重试装配", color = Color(0xFF7FA6C9), fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("关闭", color = Color(0xFF9A9388)) }
        }
    )
}
