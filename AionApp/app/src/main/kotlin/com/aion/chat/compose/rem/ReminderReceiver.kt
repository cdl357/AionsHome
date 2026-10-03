package com.aion.chat.compose.rem

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.aion.chat.compose.data.HomecomingChatWiring
import com.aion.chat.homecoming.HomecomingChatEngine
import com.aion.chat.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** 到点触发：本地通知提醒；线路就绪时 Sean 通过引擎主动生成一句戳人的话（第二 条通知）。 */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val text = intent.getStringExtra(EXTRA_TEXT) ?: return
        ensureChannel(context)

        // 1. 提醒通知（必达，不依赖网络）
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("到点啦")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFY_ID, notification)

        // 2. Sean 主动发消息戳用户：线路就绪时生成并落进聊天时间线，再发一条通知
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val poke = withTimeoutOrNull(20_000L) { generateSeanPoke(context, text) }
                if (!poke.isNullOrBlank()) {
                    NotificationManagerCompat.from(context).notify(
                        (System.currentTimeMillis() and 0x7FFFFFFF).toInt(),
                        NotificationCompat.Builder(context, CHANNEL_ID)
                            .setSmallIcon(android.R.drawable.ic_dialog_info)
                            .setContentTitle("Sean")
                            .setContentText(poke)
                            .setStyle(NotificationCompat.BigTextStyle().bigText(poke))
                            .setPriority(NotificationCompat.PRIORITY_HIGH)
                            .setAutoCancel(true)
                            .build()
                    )
                }
            } catch (e: Exception) { /* 静默 */ } finally {
                pending.finish()
            }
        }
    }

    /** 用聊天引擎生成 Sean 的主动戳（无线路/失败返回 null，不伪造）。 */
    private suspend fun generateSeanPoke(context: Context, reminderText: String): String? {
        val wiring = HomecomingChatWiring.safeCreate(context) ?: return null
        if (!wiring.hasRoute()) return null
        var reply: String? = null
        val requestId = "poke_" + System.currentTimeMillis()
        wiring.engine.send(
            HomecomingChatEngine.ChatCommand(
                requestId,
                HomecomingChatWiring.TIMELINE,
                HomecomingChatWiring.RESPONDER,
                HomecomingChatWiring.USER,
                "闹钟响了。你要主动戳 Yuri 一句：提醒内容是『$reminderText』。" +
                    "以 Sean 的口吻主动说一句话戳她（一句话，自然口语，30 字以内）。",
                "main",
                wiring.mainModelKey(),
                "", ""
            ),
            object : HomecomingChatEngine.Observer {
                override fun onChunk(chunk: String) {}
                override fun onComplete(messageId: String, text: String) {
                    reply = text.trim()
                }
                override fun onFailure(code: String) {}
            }
        )
        // 引擎回调在 OkHttp 线程，等 onComplete 落值
        var waited = 0L
        while (reply == null && waited < 15_000L) {
            delay(200L); waited += 200L
        }
        return reply?.takeIf { it.isNotBlank() }
    }

    companion object {
        const val CHANNEL_ID = "reminder_channel"
        const val NOTIFY_ID = 40001
        const val EXTRA_TEXT = "reminder_text"
        const val EXTRA_ID = "reminder_id"

        fun ensureChannel(context: Context) {
            val channel = NotificationChannel(
                CHANNEL_ID, "提醒", NotificationManagerCompat.IMPORTANCE_HIGH
            )
            NotificationManagerCompat.from(context).createNotificationChannel(channel)
        }

        fun pendingIntent(context: Context, id: Long, text: String): PendingIntent {
            ensureChannel(context)
            val intent = Intent(context, ReminderReceiver::class.java)
                .putExtra(EXTRA_ID, id)
                .putExtra(EXTRA_TEXT, text)
            return PendingIntent.getBroadcast(
                context, id.toInt(), intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
    }
}
