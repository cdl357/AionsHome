package com.aion.chat.compose.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat

/**
 * Sean 回话的消息通知：横幅弹窗（heads-up）+ 锁屏显示。
 * - 在 app 前台时不打扰（appInForeground 由 MainActivity onResume/onPause 维护）
 * - 锁屏可见：channel lockscreenVisibility = PUBLIC
 * - 开关存 chat_prefs "reply_notify"（默认开）
 */
object SeanNotifier {

    const val CHANNEL = "sean_messages"
    private const val NOTIF_ID = 4601

    @Volatile
    var appInForeground: Boolean = false

    fun enabled(context: Context): Boolean =
        context.getSharedPreferences("chat_prefs", Context.MODE_PRIVATE)
            .getBoolean("reply_notify", true)

    fun setEnabled(context: Context, on: Boolean) {
        context.getSharedPreferences("chat_prefs", Context.MODE_PRIVATE)
            .edit().putBoolean("reply_notify", on).apply()
        if (on) ensureChannel(context)
    }

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL, "Sean 的消息", NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "他回话时的横幅弹窗，锁屏也会显示"
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
        }
        manager.createNotificationChannel(channel)
    }

    /** 他回话完成时调用：app 在后台才弹。 */
    fun maybeNotifyReply(context: Context, title: String, text: String) {
        if (!enabled(context)) return
        if (appInForeground) return
        if (text.isBlank()) return
        runCatching {
            ensureChannel(context)
            if (Build.VERSION.SDK_INT >= 33) {
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                if (!nm.areNotificationsEnabled()) return
            }
            val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            } ?: return
            val pi = PendingIntent.getActivity(
                context, NOTIF_ID, launch,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            val builder = NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(context.applicationInfo.icon)
                .setContentTitle(title)
                .setContentText(text.take(180))
                .setStyle(NotificationCompat.BigTextStyle().bigText(text.take(400)))
                .setContentIntent(pi)
                .setAutoCancel(true)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.notify(NOTIF_ID, builder.build())
        }
    }
}
