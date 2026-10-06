package com.aion.chat.compose.data

import android.media.AudioAttributes
import android.media.MediaPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * 全 App 唯一的播放器（教程：一条播放链路——任何入口都进这一个 Player，绝不叠音）。
 * 状态以 StateFlow 广播；播放事件按教程 §8.3 上报共同状态（play/pause/skip/finish/close）。
 */
object MusicPlayer {

    data class Song(val id: Long, val name: String, val artist: String)

    private fun toSong(s: MusicClient.Song) = Song(s.id, s.name, s.artist)

    data class PlayerState(
        val song: Song? = null,
        val playing: Boolean = false,
        val loading: Boolean = false,
        val positionSec: Int = 0,
        val durationSec: Int = 0,
        val error: String? = null
    )

    private val stateFlow = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = stateFlow

    private var mediaPlayer: MediaPlayer? = null
    private var current: Song? = null
    private var prepared = false
    // 播放队列：最近的音乐卡依时间正序，finish 后自动接下一张（教程：一条播放链路）
    private var queue: List<Song> = emptyList()
    private var scope: CoroutineScope? = null
    private var appContext: android.content.Context? = null

    /** 把最近的音乐卡设为队列（播放卡播放时调用）。 */
    fun setQueueFromCards(context: android.content.Context, currentId: Long) {
        val cards = MusicCardStore.list(context).sortedBy { it.createdAt }
        queue = cards.map { Song(it.songId, it.name, it.artist) }
        // 当前歌之前的排到队尾（循环感）
        val idx = queue.indexOfFirst { it.id == currentId }
        if (idx > 0) queue = queue.drop(idx) + queue.take(idx)
    }

    private fun playNext(context: android.content.Context) {
        val cur = current ?: return
        val idx = queue.indexOfFirst { it.id == cur.id }
        val next = if (idx >= 0 && idx + 1 < queue.size) queue[idx + 1] else queue.firstOrNull()
        if (next != null && next.id != cur.id) {
            play(context, MusicClient.Song(next.id, next.name, next.artist))
        } else {
            emit { it.copy(playing = false) }
        }
    }

    fun init(context: android.content.Context) {
        if (appContext == null) {
            appContext = context.applicationContext
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        }
    }

    private fun emit(transform: (PlayerState) -> PlayerState) {
        stateFlow.value = transform(stateFlow.value)
    }

    /** 播放一首（直接点播）。keepQueue=true 时保留外部设好的队列（歌单连播）。 */
    fun play(context: android.content.Context, song: MusicClient.Song, keepQueue: Boolean = false) {
        init(context)
        if (current?.id == song.id && prepared && mediaPlayer?.isPlaying == true) {
            pause()
            return
        }
        releasePlayer()
        val local = toSong(song)
        if (!keepQueue) setQueueFromCards(context, local.id)
        current = local
        emit { PlayerState(song = current, loading = true) }
        val url = MusicClient.streamUrl(song.id) ?: run {
            emit { it.copy(loading = false, error = "音乐服务暂时无法连接") }
            return
        }
        val mp = MediaPlayer()
        mediaPlayer = mp
        mp.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
        )
        try {
            mp.setDataSource(url)
        } catch (e: Exception) {
            emit { it.copy(loading = false, error = "音源拿不到，可能受版权或地区限制") }
            return
        }
        mp.setOnPreparedListener {
            prepared = true
            it.start()
            emit { it.copy(playing = true, loading = false, durationSec = it.durationSec, error = null) }
            postEvent("play", local)
            startProgressLoop()
        }
        mp.setOnCompletionListener {
            prepared = false
            emit { it.copy(playing = false) }
            postEvent("finish", local)
        }
        mp.setOnErrorListener { _, what, extra ->
            prepared = false
            emit { it.copy(playing = false, loading = false, error = "播放出错（$what/$extra）——这首歌可能拿不到音源") }
            appContext?.let { MusicNotification.cancel(it) }
            true
        }
        mp.prepareAsync()
    }

    fun pause() {
        runCatching {
            if (mediaPlayer?.isPlaying == true) {
                mediaPlayer?.pause()
                emit { it.copy(playing = false, positionSec = mediaPlayer?.currentPosition?.div(1000) ?: it.positionSec) }
                postEvent("pause", current)
                val ctx = appContext
                val s = current
                if (ctx != null && s != null) MusicNotification.show(ctx, s, false)
            }
        }
    }

    fun resume() {
        runCatching {
            if (prepared && mediaPlayer?.isPlaying != true) {
                mediaPlayer?.start()
                emit { it.copy(playing = true) }
                postEvent("resume", current)
                val ctx = appContext
                val s = current
                if (ctx != null && s != null) MusicNotification.show(ctx, s, true)
            }
        }
    }

    fun toggle() {
        if (stateFlow.value.playing) pause() else resume()
    }

    /** 从歌单/队列的第 index 首开始连播。 */
    fun playFromList(context: android.content.Context, songs: List<MusicClient.Song>, index: Int) {
        if (songs.isEmpty()) return
        val i = index.coerceIn(0, songs.size - 1)
        val q = songs.map { toSong(it) }
        queue = q.drop(i) + q.take(i)
        play(context, songs[i], keepQueue = true)
    }

    fun skip() {
        val ctx = appContext
        val s = current
        releasePlayer()
        emit { PlayerState(song = s, playing = false) }
        postEvent("skip", s)
        if (ctx != null && queue.isNotEmpty()) playNext(ctx)
    }

    fun seekTo(sec: Int) {
        runCatching { mediaPlayer?.seekTo(sec * 1000); emit { it.copy(positionSec = sec) } }
    }

    private var progressLoop = false
    private fun startProgressLoop() {
        if (progressLoop) return
        progressLoop = true
        scope?.launch {
            while (progressLoop) {
                val mp = mediaPlayer
                if (mp != null && prepared) {
                    val pos = runCatching { mp.currentPosition / 1000 }.getOrDefault(0)
                    val dur = runCatching { mp.duration / 1000 }.getOrDefault(0)
                    emit { it.copy(positionSec = pos, durationSec = dur) }
                }
                delay(500)
                if (mediaPlayer == null) progressLoop = false
            }
        }
    }

    private fun postEvent(type: String, song: Song?) {
        scope?.launch {
            runCatching {
                MusicClient.postEvent(
                    type, "user",
                    song?.let { MusicClient.Song(it.id, it.name, it.artist) }
                )
            }
        }
    }

    fun shutdown() {
        val s = current
        releasePlayer()
        postEvent("close", s)
        appContext?.let { MusicNotification.cancel(it) }
    }

    private fun releasePlayer() {
        runCatching {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        }
        mediaPlayer = null
        prepared = false
        progressLoop = false
    }
}
