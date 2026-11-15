package com.aion.chat

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RadialGradient
import android.graphics.Shader
import com.aion.chat.FloatingBallState
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random
import com.aion.chat.compose.data.HomecomingPokeStore

/**
 * 悬浮球服务（v0.3.156，照她给的博客规格）：
 * - 系统级悬浮球：所有应用上方，可拖动，位置记忆
 * - 单击：冒小爱心 + 眨眼
 * - 双击：切回 App
 * - 三击：戳一戳（心跳 +1，走既有 HomecomingPokeStore）
 * - 长按：工具栏（打开 App / 戳一戳 / 收起球——收起后由通知栏按钮恢复）
 * - 未读回复 → 炸毛状态（毛刺 + 眼睛瞪圆）
 * - 陀螺仪：侧躺手机它也侧躺；摇晃幅度大 → 吵醒（瞪圆 + 颤动）
 * 红线：不碰聊天逻辑；心跳走既有 poke 存储。
 */
class FloatingBallService : Service() {

    companion object {
        private const val CHANNEL = "floating_ball"
        private const val NOTIF_ID = 0x4642
        const val ACTION_SHOW = "com.aion.chat.ball.SHOW"
        const val ACTION_HIDE = "com.aion.chat.ball.HIDE"

        fun start(c: Context) {
            runCatching {
                c.startForegroundService(Intent(c, FloatingBallService::class.java))
            }
        }

        fun stop(c: Context) {
            c.stopService(Intent(c, FloatingBallService::class.java))
        }

        @Volatile var running: Boolean = false
    }

    private lateinit var wm: WindowManager
    private lateinit var ballView: BallView
    private lateinit var ballParams: WindowManager.LayoutParams
    private var heartsView: HeartsOverlayView? = null
    private var heartsParams: WindowManager.LayoutParams? = null
    private var toolbarView: LinearLayout? = null
    private var toolbarParams: WindowManager.LayoutParams? = null

    private val handler = Handler(Looper.getMainLooper())
    private var sensors: SensorManager? = null
    private var hidden = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        running = true
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        startForegroundNotification()
        addBall()
        registerSensors()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_HIDE -> { ballView.visibility = View.GONE; hidden = true }
            ACTION_SHOW -> { ballView.visibility = View.VISIBLE; hidden = false }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        running = false
        runCatching { wm.removeView(ballView) }
        heartsView?.let { runCatching { wm.removeView(it) } }
        toolbarView?.let { runCatching { wm.removeView(it) } }
        sensors?.unregisterListener(sensorListener)
        super.onDestroy()
    }

    // ── 前台通知（含 显示/收起 两个按钮） ──
    private fun startForegroundNotification() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "悬浮球", NotificationManager.IMPORTANCE_MIN)
            )
        }
        val showPi = PendingIntent.getService(
            this, 1,
            Intent(this, FloatingBallService::class.java).setAction(ACTION_SHOW),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val hidePi = PendingIntent.getService(
            this, 2,
            Intent(this, FloatingBallService::class.java).setAction(ACTION_HIDE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val n: Notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL)
        } else {
            @Suppress("DEPRECATION") Notification.Builder(this)
        }
            .setSmallIcon(android.R.drawable.sym_def_app_icon)
            .setContentTitle("悬浮球在陪着你")
            .setContentText("戳一下有爱心 · 双击回 App · 长按工具栏")
            .setOngoing(true)
            .addAction(0, "显示", showPi)
            .addAction(0, "收起", hidePi)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIF_ID, n,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, n)
        }
    }

    // ── 球本体 ──
    private fun addBall() {
        val d = resources.displayMetrics.density
        ballView = BallView(this, this)
        ballParams = WindowManager.LayoutParams(
            (64 * d).toInt(), (64 * d).toInt(),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = FloatingBallState.posX
            y = FloatingBallState.posY
        }
        ballView.touchListener = BallTouch()
        wm.addView(ballView, ballParams)
    }

    // ── 手势：拖动 / 单击 / 双击 / 三击 / 长按 ──
    private var downX = 0f; private var downY = 0f
    private var dragged = false
    private var tapCount = 0
    private var downAt = 0L

    inner class BallTouch : View.OnTouchListener {
        private val tapRunner = Runnable { runTapActions(tapCount); tapCount = 0 }
        private val longRunner = Runnable {
            ballView.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
            showToolbar()
            dragged = true   // 长按后松手不再算点击
        }
        override fun onTouch(v: View, e: MotionEvent): Boolean {
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX; downY = e.rawY
                    dragged = false
                    downAt = System.currentTimeMillis()
                    handler.postDelayed(longRunner, 450)
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - downX; val dy = e.rawY - downY
                    if (!dragged && (abs(dx) > 14 || abs(dy) > 14)) {
                        dragged = true
                        handler.removeCallbacks(longRunner)
                    }
                    if (dragged) {
                        ballParams.x = FloatingBallState.posX + dx.toInt()
                        ballParams.y = FloatingBallState.posY + dy.toInt()
                        runCatching { wm.updateViewLayout(ballView, ballParams) }
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    handler.removeCallbacks(longRunner)
                    if (dragged) {
                        if (e.actionMasked == MotionEvent.ACTION_UP) {
                            FloatingBallState.posX = ballParams.x
                            FloatingBallState.posY = ballParams.y
                            FloatingBallState.savePos(this@FloatingBallService)
                        }
                        dragged = false
                        return true
                    }
                    if (e.actionMasked == MotionEvent.ACTION_UP &&
                        System.currentTimeMillis() - downAt < 450
                    ) {
                        tapCount++
                        handler.removeCallbacks(tapRunner)
                        handler.postDelayed(tapRunner, 320)
                    }
                }
            }
            return true
        }

        private fun runTapActions(count: Int) {
            when (count) {
                1 -> {
                    ballView.popHearts()
                    ballView.blinkOnce()
                }
                2 -> openApp()
                3 -> {
                    ballView.popHearts()
                    pokeSean()
                }
            }
        }
    }

    private fun openApp() {
        val i = packageManager.getLaunchIntentForPackage(packageName)
        i?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        if (i != null) runCatching { startActivity(i) }
    }

    private fun pokeSean() {
        runCatching {
            val gained: Int = HomecomingPokeStore.poke(this, "戳了戳", "脸")
            gained
        }
    }

    // ── 爱心动画层（不可触摸，纯视觉） ──
    fun spawnHearts() {
        if (heartsView != null) return
        val d = resources.displayMetrics.density
        heartsView = HeartsOverlayView(this, ballParams.x + 32 * d, ballParams.y + 8 * d)
        heartsParams = WindowManager.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        runCatching {
            wm.addView(heartsView, heartsParams)
            heartsView!!.animateHearts {
                runCatching { wm.removeView(heartsView) }
                heartsView = null
            }
        }.onFailure {
            heartsView = null
        }
    }

    // ── 工具栏（长按唤出） ──
    private fun showToolbar() {
        if (toolbarView != null) { hideToolbar(); return }
        val d = resources.displayMetrics.density
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(Color.argb(215, 255, 255, 255))
                cornerRadius = 14 * d
            }
            setPadding((12 * d).toInt(), (6 * d).toInt(), (12 * d).toInt(), (6 * d).toInt())
        }
        fun item(label: String, onClick: () -> Unit) {
            val t = TextView(this).apply {
                text = label
                textSize = 13f
                setTextColor(Color.rgb(0x1A, 0x32, 0x44))
                setPadding((10 * d).toInt(), 0, (10 * d).toInt(), 0)
                setOnClickListener { onClick(); hideToolbar() }
            }
            bar.addView(t)
        }
        item("打开 App") { openApp() }
        item("戳一戳") { ballView.popHearts(); pokeSean() }
        item("收起球") {
            ballView.visibility = View.GONE
            hidden = true
        }
        toolbarView = bar
        toolbarParams = WindowManager.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = ballParams.x
            y = maxOf(0, ballParams.y + 70 * d.toInt())
        }
        bar.setOnTouchListener { v, e ->
            if (e.actionMasked == MotionEvent.ACTION_OUTSIDE) { hideToolbar(); v.performClick(); true } else false
        }
        runCatching { wm.addView(bar, toolbarParams) }.onFailure {
            toolbarView = null
            Log.w("FloatingBall", "toolbar add failed", it)
        }
        handler.postDelayed({ hideToolbar() }, 6_000)
    }

    private fun hideToolbar() {
        toolbarView?.let { runCatching { wm.removeView(it) } }
        toolbarView = null
    }

    // ── 传感器：陀螺仪侧躺 + 摇晃吵醒 ──
    private fun registerSensors() {
        sensors = getSystemService(SENSOR_SERVICE) as? SensorManager
        val acc = sensors?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return
        sensors?.registerListener(sensorListener, acc, SensorManager.SENSOR_DELAY_GAME)
    }

    private val sensorListener = object : SensorEventListener {
        override fun onSensorChanged(e: SensorEvent) {
            if (e.sensor.type == Sensor.TYPE_ACCELEROMETER) {
                val gx = e.values[0]; val gy = e.values[1]; val gz = e.values[2]
                // 侧躺：重力在 X 轴上的分量 → 球里的眼睛跟着偏
                ballView.leanX = (gx / 9.8f).coerceIn(-1f, 1f)
                ballView.tiltDeg = if (gy != 0f || gz != 0f) {
                    (Math.toDegrees(Math.atan2(gx.toDouble(), sqrt((gy * gy + gz * gz).toDouble())))).toFloat()
                } else 0f
                // 摇晃：合加速度远超 1g → 吵醒
                val mag = sqrt(gx * gx + gy * gy + gz * gz)
                if (mag > 18f) ballView.wakeUp()
                ballView.postInvalidate()
            }
        }
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }
}

/** 悬浮球全局状态（未读炸毛/位置记忆） */
object FloatingBallState {
    @Volatile var unread: Boolean = false
    var posX: Int = 40
    var posY: Int = 240

    fun savePos(c: Context) {
        c.getSharedPreferences("floating_ball", Context.MODE_PRIVATE).edit()
            .putInt("pos_x", posX).putInt("pos_y", posY).apply()
    }

    fun load(c: Context) {
        val p = c.getSharedPreferences("floating_ball", Context.MODE_PRIVATE)
        posX = p.getInt("pos_x", 40)
        posY = p.getInt("pos_y", 240)
    }
}

/** 球本体绘制：白绒球 + 两只眼 + 炸毛 + 侧躺；触摸交由服务处理 */
private class BallView(context: Context, private val service: FloatingBallService) : View(context) {

    var touchListener: View.OnTouchListener? = null

    var leanX: Float = 0f
    var tiltDeg: Float = 0f
    private var wakeUntil = 0L
    private var blinkUntil = 0L
    private val handler = Handler(Looper.getMainLooper())
    private val rnd = Random(System.currentTimeMillis())

    private val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = RadialGradient(0f, 0f, 1f,
            Color.argb(255, 252, 250, 246), Color.argb(235, 226, 220, 214), Shader.TileMode.CLAMP)
    }
    private val spikePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(240, 248, 246, 242)
        strokeWidth = 3f
    }
    private val eyePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(235, 58, 53, 53) }
    private val mouthPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(200, 58, 53, 53); style = Paint.Style.STROKE; strokeWidth = 3f
    }
    private val heartPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(220, 229, 148, 148) }

    init {
        FloatingBallState.load(context)
        // 眨眼循环
        handler.post(object : Runnable {
            override fun run() {
                blinkUntil = System.currentTimeMillis() + 140
                postInvalidate()
                handler.postDelayed(this, 2800 + rnd.nextLong(2400))
            }
        })
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        return touchListener?.onTouch(this, e) ?: super.onTouchEvent(e)
    }

    fun popHearts() { service.spawnHearts() }
    fun blinkOnce() { blinkUntil = System.currentTimeMillis() + 140; postInvalidate() }
    fun wakeUp() {
        wakeUntil = System.currentTimeMillis() + 3_000
        val v = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        runCatching { v?.vibrate(VibrationEffect.createOneShot(60, VibrationEffect.DEFAULT_AMPLITUDE)) }
        postInvalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f; val cy = height / 2f
        val r = min(width, height) / 2f * 0.82f
        val unread = FloatingBallState.unread
        val awake = System.currentTimeMillis() < wakeUntil
        val now = System.currentTimeMillis()

        canvas.save()
        canvas.rotate(tiltDeg * 0.3f, cx, cy)

        // 炸毛：一圈毛刺（未读时轻轻颤动）
        if (unread) {
            val quiver = if (awake) 3f else sin(now / 180f) * 2f
            for (i in 0 until 26) {
                val a = (i / 26f) * 2f * Math.PI.toFloat() + quiver * 0.05f
                val x1 = cx + cos(a) * r; val y1 = cy + sin(a) * r
                val x2 = cx + cos(a) * (r + 7 + quiver); val y2 = cy + sin(a) * (r + 7 + quiver)
                canvas.drawLine(x1, y1, x2, y2, spikePaint)
            }
        }

        // 身体（白绒球）
        bodyPaint.shader = RadialGradient(cx - r * 0.25f, cy - r * 0.3f, r * 0.15f,
            intArrayOf(Color.argb(255, 253, 251, 248), Color.argb(240, 228, 222, 216)),
            floatArrayOf(0f, 1f), Shader.TileMode.CLAMP)
        canvas.drawCircle(cx, cy, r, bodyPaint)

        // 眼睛：偏移跟陀螺仪；未读/被吵醒瞪圆，平时竖椭圆，眨眼成线
        val eyeOffX = leanX * r * 0.22f
        val eyeY = cy - r * 0.12f
        val eyeDx = r * 0.26f
        val eyeRw = r * (if (unread || awake) 0.13f else 0.10f)
        val eyeRh = r * (if (unread || awake) 0.20f else 0.16f)
        val blinking = now < blinkUntil
        for (sign in intArrayOf(-1, 1)) {
            val ex = cx + sign * eyeDx + eyeOffX
            if (blinking) {
                canvas.drawLine(ex - eyeRw, eyeY, ex + eyeRw, eyeY, eyePaint)
            } else {
                canvas.drawOval(ex - eyeRw, eyeY - eyeRh, ex + eyeRw, eyeY + eyeRh, eyePaint)
            }
        }
        // 嘴：小弧线（炸毛时嘴巴张开成小圆）
        val mx = cx + eyeOffX * 0.6f; val my = cy + r * 0.22f
        if (unread || awake) {
            canvas.drawCircle(mx, my, r * 0.07f, eyePaint)
        } else {
            canvas.drawLine(mx - r * 0.08f, my, mx + r * 0.08f, my, mouthPaint)
        }
        canvas.restore()
    }
}

/** 满屏爱心动画层：从球的位置冒出 7 颗小爱心上升渐隐 */
private class HeartsOverlayView(
    context: Context,
    private val originX: Float,
    private val originY: Float,
) : View(context) {

    private val d = context.resources.displayMetrics.density
    private val hearts = List(7) {
        Heart(
            dx = (Random.nextFloat() - 0.5f) * 90 * d,
            drift = (Random.nextFloat() - 0.5f) * 40 * d,
            size = (9 + Random.nextFloat() * 8) * d,
            color = if (Random.nextBoolean()) Color.argb(230, 229, 148, 148)
                    else Color.argb(230, 244, 172, 180)
        )
    }
    private var progress = 0f
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    fun animateHearts(onEnd: () -> Unit) {
        val anim = android.animation.ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1_100
            addUpdateListener {
                progress = it.animatedValue as Float
                postInvalidate()
                if (progress >= 1f) onEnd()
            }
        }
        anim.start()
    }

    private class Heart(val dx: Float, val drift: Float, val size: Float, val color: Int)

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val rise = progress * 90 * d
        for (h in hearts) {
            val x = originX + h.dx + h.drift * progress
            val y = originY - rise - abs(h.dx) * 0.2f
            val s = h.size * (0.7f + progress * 0.5f)
            paint.color = h.color
            paint.alpha = ((1f - progress) * 230).toInt().coerceIn(0, 255)
            // 爱心：两圆一三角
            canvas.drawCircle(x - s * 0.35f, y - s * 0.15f, s * 0.38f, paint)
            canvas.drawCircle(x + s * 0.35f, y - s * 0.15f, s * 0.38f, paint)
            val path = android.graphics.Path()
            path.moveTo(x - s * 0.66f, y - s * 0.05f)
            path.lineTo(x + s * 0.66f, y - s * 0.05f)
            path.lineTo(x, y + s * 0.72f)
            path.close()
            canvas.drawPath(path, paint)
        }
    }
}

