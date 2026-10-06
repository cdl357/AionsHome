package com.aion.chat.compose.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.aion.chat.R

/**
 * 播放器媒体通知（教程 §28：系统级锁屏控制和媒体通知）。
 * 播放时常驻，含 播放/暂停、跳过、关闭 三个动作；屏幕熄了也能控制。
 */
object MusicNotification {

    const val CHANNEL_ID = "music_playback_channel"
    const val NOTIFY_ID = 50001
    const val ACTION_TOGGLE = "com.aion.chat.music.ACTION_TOGGLE"
    const val ACTION_SKIP = "com.aion.chat.music.ACTION_SKIP"
    const val ACTION_CLOSE = "com.aion.chat.music.ACTION_CLOSE"

    fun ensureChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID, "一起听歌",
            NotificationManagerCompat.IMPORTANCE_LOW   // 常驻控制条，不响不打扰
        )
        NotificationManagerCompat.from(context).createNotificationChannel(channel)
    }

    fun show(context: Context, song: MusicPlayer.Song, playing: Boolean) {
        ensureChannel(context)
        val openApp = PendingIntent.getActivity(
            context, 0,
            Intent(context, com.aion.chat.compose.MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        fun action(action: String, icon: Int, title: String): NotificationCompat.Action {
            val intent = Intent(context, MusicActionReceiver::class.java).setAction(action)
            val pi = PendingIntent.getBroadcast(
                context, action.hashCode(), intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            return NotificationCompat.Action(icon, title, pi)
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(if (playing) "正在听" else "已暂停")
            .setContentText("${song.name} — ${song.artist}")
            .setOngoing(playing)
            .setOnlyAlertOnce(true)
            .setContentIntent(openApp)
            .addAction(action(ACTION_TOGGLE, android.R.drawable.ic_media_pause, if (playing) "暂停" else "播放"))
            .addAction(action(ACTION_SKIP, android.R.drawable.ic_media_next, "下一首"))
            .addAction(action(ACTION_CLOSE, android.R.drawable.ic_menu_close_clear_cancel, "关闭"))
            .build()

        runCatching { NotificationManagerCompat.from(context).notify(NOTIFY_ID, notification) }
    }

    fun cancel(context: Context) {
        runCatching { NotificationManagerCompat.from(context).cancel(NOTIFY_ID) }
    }

    /** 播放动作广播接收器（manifest 注册）。 */
    class MusicActionReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                ACTION_TOGGLE -> MusicPlayer.toggle()
                ACTION_SKIP -> MusicPlayer.skip()
                ACTION_CLOSE -> MusicPlayer.shutdown()
            }
        }
    }
}
