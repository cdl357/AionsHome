package com.aion.chat.compose.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * 摄像头推帧（v0.3.165，照她出的实时视频流方案）：
 * 小鑫手机拍一张 → JPEG → HTTP POST 到 VPS /api/video/push-frame
 * Sean 通过 peek_camera 工具读取 VPS 上的最新帧
 * VPS 端内存缓冲不落盘，关了就没了
 */
object CameraStreamStore {
    private const val BASE = "http://134.175.7.196:8783/api/video"
    private const val FRAME_KEY = "aion_frame_f3wcga9kja7fqm0bhlyl39nx83jfkp4i"

    /** 拍一张 → 压缩 → 推到 VPS（阻塞，需在 IO 线程调用） */
    fun pushFrame(bmp: Bitmap): Boolean {
        return runCatching {
            val baos = ByteArrayOutputStream()
            bmp.compress(Bitmap.CompressFormat.JPEG, 65, baos)
            val jpeg = baos.toByteArray()
            if (jpeg.size < 100) return@runCatching false
            val conn = (URL("$BASE/push-frame").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("X-Frame-Key", FRAME_KEY)
                setRequestProperty("Content-Type", "application/octet-stream")
                doOutput = true
                connectTimeout = 10000
                readTimeout = 10000
            }
            conn.outputStream.use { it.write(jpeg) }
            conn.responseCode in 200..299
        }.getOrDefault(false)
    }

    /** 从 VPS 拉最新一帧 → Bitmap（阻塞 IO） */
    fun peekLatest(): Bitmap? {
        return runCatching {
            val conn = (URL("$BASE/latest-frame").openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10000
                readTimeout = 10000
            }
            if (conn.responseCode != 200) return@runCatching null
            val bytes = conn.inputStream.use { it.readBytes() }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }.getOrNull()
    }

    fun status(): JSONObject {
        return runCatching {
            val conn = (URL("$BASE/stream-status").openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 5000
                readTimeout = 5000
            }
            JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
        }.getOrDefault(JSONObject().put("online", false))
    }

    fun stopStream() {
        runCatching {
            val conn = (URL("$BASE/stop-stream").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("X-Frame-Key", FRAME_KEY)
                doOutput = true
                connectTimeout = 5000
                readTimeout = 5000
            }
            conn.responseCode
        }
    }
}
