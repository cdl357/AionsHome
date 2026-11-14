package com.aion.chat.compose.ui.settings

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.aion.chat.compose.data.workspace.WorkspaceTerminal
import com.termux.terminal.TerminalSession
import com.termux.view.TerminalView

/**
 * 沙盒交互终端（termux-pty 全键盘仿真）：真实 PTY + ANSI 渲染，
 * 跑的是 proot bash，cd /workspace 即 AI 工具的文件区。会话随页面销毁结束。
 */
@Composable
fun SandboxTerminalScreen(onBack: () -> Unit = {}) {
    val context = LocalContext.current
    var sessionFinished by remember { mutableStateOf(false) }
    var ctrlDown by remember { mutableStateOf(false) }

    val ready = remember { WorkspaceTerminal.rootfsReady(context) }

    // 会话与视图：remember 创建一次，随组合销毁而终结
    val triple = remember {
        runCatching {
            WorkspaceTerminal.prepare(context)
            val sc = WorkspaceTerminal.SessionClient(context) { }
            val vc = WorkspaceTerminal.ViewClient(context)
            val view = TerminalView(context, null)
            sc.terminalView = view
            vc.terminalView = view
            val session = WorkspaceTerminal.createSession(context, sc)
            view.attachSession(session)
            view.setTerminalViewClient(vc)
            view.isFocusableInTouchMode = true
            Triple(session, vc, view)
        }.getOrNull()
    }

    DisposableEffect(Unit) {
        onDispose {
            runCatching {
                triple?.first?.let { s -> if (s.isRunning) s.finishIfRunning() }
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF10161C))
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF232B33))
                    .clickable { onBack() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回",
                    tint = Color(0xFFD9E2EA), modifier = Modifier.size(20.dp)
                )
            }
            Text(
                "Alpine 终端",
                fontSize = 16.sp, color = Color(0xFFD9E2EA), fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(start = 10.dp)
            )
            Spacer(Modifier.weight(1f))
        }

        when {
            !ready -> Text(
                "还没装 rootfs——回上一页先装，装完再进终端。",
                fontSize = 13.sp, color = Color(0xFF8FA5B5), modifier = Modifier.padding(24.dp)
            )
            sessionFinished || triple == null -> Text(
                if (triple == null) "终端启动失败（rootfs 不完整？回上一页重新安装）"
                else "会话已结束（bash 退出了）。返回后重新进入即可开新会话。",
                fontSize = 13.sp, color = Color(0xFF8FA5B5), modifier = Modifier.padding(24.dp)
            )
            else -> {
                val (session, vc, view) = triple
                // 快捷键行
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF1A222A)),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    listOf(
                        "Esc" to "\u001b",
                        "Tab" to "\t",
                        "/" to "/",
                        "-" to "-",
                        "|" to "|",
                    ).forEach { (label, txt) ->
                        Text(
                            label, fontSize = 13.sp, color = Color(0xFFD9E2EA),
                            modifier = Modifier
                                .clickable {
                                    session.writeText(txt)
                                    vc.focusAndShowKeyboard()
                                }
                                .padding(horizontal = 14.dp, vertical = 10.dp)
                        )
                    }
                    Text(
                        if (ctrlDown) "CTRL●" else "CTRL",
                        fontSize = 13.sp,
                        color = if (ctrlDown) Color(0xFF7FD1A8) else Color(0xFFD9E2EA),
                        modifier = Modifier
                            .clickable {
                                ctrlDown = !ctrlDown
                                vc.controlDown = ctrlDown
                            }
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    )
                    Text(
                        "粘贴", fontSize = 13.sp, color = Color(0xFFD9E2EA),
                        modifier = Modifier
                            .clickable {
                                val cb = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                                        as android.content.ClipboardManager
                                val text = cb.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)
                                    ?.coerceToText(context)?.toString() ?: return@clickable
                                session.writeText(text)
                                vc.focusAndShowKeyboard()
                            }
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    )
                }

                // 终端本体
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(6.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF10161C))
                ) {
                    AndroidView(
                        factory = { view },
                        modifier = Modifier.fillMaxSize()
                    )
                }
                DisposableEffect(Unit) {
                    vc.focusAndShowKeyboard()
                    onDispose { }
                }
            }
        }
    }
}

private fun TerminalSession.writeText(text: String) {
    val bytes = text.toByteArray()
    write(bytes, 0, bytes.size)
}
