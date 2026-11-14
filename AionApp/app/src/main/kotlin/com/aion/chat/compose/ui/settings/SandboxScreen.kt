package com.aion.chat.compose.ui.settings

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aion.chat.compose.data.WorkspaceTools
import com.aion.chat.compose.data.workspace.AionWorkspace
import com.aion.chat.compose.data.workspace.RootfsInstallProgress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Linux 沙盒页（proot，照橘瓣 workspace）：
 * ①装 rootfs（Alpine 预填清华源，多源回退，进度实时）
 * ②装好后 Sean 自动获得 workspace_* 五件工具（shell/读写文件/列目录/删除）
 * ③试命令：直接跑一条看输出
 * 交互终端页（termux-pty 全键盘仿真）下一轮，先把引擎跑通。
 */
@Composable
fun SandboxScreen(onBack: () -> Unit = {}, onOpenTerminal: () -> Unit = {}) {
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var rev by remember { mutableIntStateOf(0) }
    var installing by remember { mutableStateOf(false) }
    var stageText by remember { mutableStateOf("") }
    var done by remember { mutableStateOf(0L) }
    var total by remember { mutableStateOf(0L) }
    val cancelled = remember { AtomicBoolean(false) }

    var cmd by remember { mutableStateOf("") }
    var cmdOut by remember { mutableStateOf("") }
    var cmdBusy by remember { mutableStateOf(false) }

    val stage = remember(rev) { AionWorkspace.stage(context) }
    val hasRootfs = remember(rev) { AionWorkspace.hasRootfs(context) }

    Column(
        Modifier
            .fillMaxSize()
            .background(Color(0xFFF2F0EB))
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .clickable { onBack() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回",
                    tint = Color(0xFF26211C), modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.weight(1f))
        }
        Text(
            "Linux 沙盒",
            fontSize = 40.sp, color = Color(0xFF26211C), fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Text(
            "手机里跑一个 Alpine Linux（proot，无需 root）。装好后 Sean 就有了真 shell：跑脚本、apk 装软件、处理文件都在沙盒里，不碰系统。",
            fontSize = 11.sp, color = Color(0xFF8A8279), lineHeight = 15.sp,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp)
        ) {
            // 状态卡
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 5.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.White)
                    .padding(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            when {
                                hasRootfs -> "已就绪（Alpine）"
                                stage == "installing" -> "安装中…"
                                stage == "broken" -> "安装失败，可重试"
                                else -> "未安装"
                            },
                            fontSize = 15.sp, color = Color(0xFF26211C)
                        )
                        Text(
                            AionWorkspace.installedUrl(context)?.let { "源：${it.substringBefore("/alpine/")}" }
                                ?: "装好约 300MB 磁盘空间",
                            fontSize = 11.sp, color = Color(0xFF8A8279)
                        )
                    }
                    if (hasRootfs) {
                        Button(
                            onClick = { onOpenTerminal() },
                            shape = RoundedCornerShape(10.dp),
                            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF4C5B68), contentColor = Color.White
                            )
                        ) { Text("打开终端", fontSize = 11.sp) }
                    }
                    when {
                        installing -> {
                            Text(
                                if (total > 0) "${done / 1024 / 1024}/${total / 1024 / 1024}MB" else stageText,
                                fontSize = 11.sp, color = Color(0xFF7A6A55),
                                modifier = Modifier.padding(end = 8.dp)
                            )
                            OutlinedButton(onClick = { cancelled.set(true) }) {
                                Text("停止", fontSize = 11.sp, color = Color(0xFFB3564D))
                            }
                        }
                        hasRootfs -> OutlinedButton(onClick = {
                            scope.launch {
                                withContext(Dispatchers.IO) { AionWorkspace.reset(context) }
                                Toast.makeText(context, "沙盒已清空", Toast.LENGTH_SHORT).show()
                                rev++
                            }
                        }) { Text("清空", fontSize = 11.sp, color = Color(0xFFB3564D)) }
                        else -> Button(
                            enabled = !installing,
                            onClick = {
                                installing = true; cancelled.set(false); done = 0; total = 0
                                scope.launch {
                                    val err = withContext(Dispatchers.IO) {
                                        AionWorkspace.installRootfs(
                                            context, AionWorkspace.ROOTFS_SOURCES,
                                            onProgress = { p: RootfsInstallProgress ->
                                                when (p.stage.name) {
                                                    "DOWNLOADING" -> {
                                                        stageText = "下载中"
                                                        done = p.bytesRead
                                                        total = p.totalBytes ?: 0
                                                    }
                                                    "EXTRACTING" -> {
                                                        stageText = "解包中 ${p.entriesExtracted}"
                                                        total = 0
                                                    }
                                                    else -> stageText = "完成"
                                                }
                                            },
                                            cancelled = { cancelled.get() })
                                    }
                                    installing = false
                                    if (err == null) Toast.makeText(context, "沙盒就绪", Toast.LENGTH_SHORT).show()
                                    else if (err != "cancelled") Toast.makeText(context, "安装失败：$err", Toast.LENGTH_LONG).show()
                                    rev++
                                }
                            },
                            shape = RoundedCornerShape(10.dp),
                            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF7A6A55), contentColor = Color.White
                            )
                        ) { Text("安装", fontSize = 11.sp) }
                    }
                }
                if (installing && total > 0) {
                    Spacer(Modifier.height(6.dp))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color(0xFFE4DFD6))
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth((done.toFloat() / total).coerceIn(0f, 1f))
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(Color(0xFF7A6A55))
                        )
                    }
                }
            }

            // 试命令
            if (hasRootfs) {
                Spacer(Modifier.height(6.dp))
                Text("试命令", fontSize = 13.sp, color = Color(0xFF26211C))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                    OutlinedTextField(
                        value = cmd,
                        onValueChange = { cmd = it },
                        placeholder = { Text("如 uname -a 或 apk add python3", fontSize = 12.sp, color = Color(0xFFB9B2A8)) },
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, color = Color(0xFF26211C)),
                        shape = RoundedCornerShape(12.dp),
                        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFFD8D2C8), unfocusedBorderColor = Color(0xFFE4DFD6),
                            focusedContainerColor = Color.White, unfocusedContainerColor = Color.White
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.size(8.dp))
                    Button(
                        enabled = !cmdBusy && cmd.isNotBlank(),
                        onClick = {
                            val c = cmd
                            cmdBusy = true; cmdOut = ""
                            scope.launch {
                                val r = withContext(Dispatchers.IO) {
                                    runCatching { AionWorkspace.runShell(context, c, 120) }.getOrNull()
                                }
                                cmdBusy = false
                                cmdOut = r?.let { "exit ${it.exitCode}\n${it.stdout}${it.stderr}" } ?: "执行失败"
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF7A6A55), contentColor = Color.White
                        )
                    ) { Text("跑", fontSize = 12.sp) }
                }
                if (cmdBusy) {
                    CircularProgressIndicator(
                        color = Color(0xFF7A6A55), strokeWidth = 2.dp,
                        modifier = Modifier.size(16.dp).padding(2.dp)
                    )
                }
                if (cmdOut.isNotBlank()) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 5.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White)
                            .padding(12.dp)
                    ) {
                        Text(cmdOut.take(4000), fontSize = 12.sp, color = Color(0xFF26211C), lineHeight = 17.sp)
                    }
                }
                Text(
                    "装好后到聊天里直接使唤他：跑脚本、算东西、整理文件，他会调 workspace_shell 和文件工具。",
                    fontSize = 11.sp, color = Color(0xFF8A8279), lineHeight = 15.sp,
                    modifier = Modifier.padding(vertical = 6.dp)
                )
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}
