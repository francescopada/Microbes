package com.retro.microbes

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.service.wallpaper.WallpaperService
import android.view.MotionEvent
import android.view.SurfaceHolder
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

class MicrobesWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine = MicrobesEngine()

    inner class MicrobesEngine : Engine() {
        private val handler = Handler(Looper.getMainLooper())
        private var isVisible = false
        private var screenWidth = 0f
        private var screenHeight = 0f

        private val microbes = mutableListOf<Microbe>()
        private val nutrients = mutableListOf<Nutrient>()

        private var touchX: Float? = null
        private var touchY: Float? = null

        private val bgPaint = Paint()

        private val frameCallback = object : Runnable {
            override fun run() {
                drawFrame()
                if (isVisible) {
                    handler.postDelayed(this, 16)
                }
            }
        }

        override fun onCreate(surfaceHolder: SurfaceHolder?) {
            super.onCreate(surfaceHolder)
            setTouchEventsEnabled(true)
        }

        override fun onVisibilityChanged(visible: Boolean) {
            this.isVisible = visible
            if (visible) {
                handler.post(frameCallback)
            } else {
                handler.removeCallbacks(frameCallback)
            }
        }

        override fun onSurfaceChanged(holder: SurfaceHolder?, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            screenWidth = width.toFloat()
            screenHeight = height.toFloat()

            val radius = hypot(screenWidth / 2f, screenHeight / 2f)
            if(radius > 0) {
                bgPaint.shader = RadialGradient(
                    screenWidth / 2f, screenHeight / 2f, radius,
                    Color.rgb(18, 38, 70),
                    Color.rgb(4, 7, 16),
                    Shader.TileMode.CLAMP
                )
            }

            microbes.clear()
            val microbeCount = 14
            for (i in 0 until microbeCount) {
                val depth = Random.nextFloat() * 0.6f + 0.4f
                microbes.add(
                    Microbe(
                        x = Random.nextFloat() * screenWidth,
                        y = Random.nextFloat() * screenHeight,
                        baseRadius = (Random.nextFloat() * 26f + 24f) * depth,
                        depth = depth,
                        colorIndex = i % Microbe.PALETTE.size
                    )
                )
            }
            microbes.sortBy { it.depth }
        }

        override fun onTouchEvent(event: MotionEvent) {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                    touchX = event.x
                    touchY = event.y
                    if (nutrients.size < 30) {
                        nutrients.add(Nutrient(event.x, event.y))
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    touchX = null
                    touchY = null
                }
            }
        }

        private fun drawFrame() {
            val holder = surfaceHolder ?: return
            val canvas: Canvas = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                holder.lockHardwareCanvas() ?: return
            } else {
                holder.lockCanvas() ?: return
            }

            try {
                canvas.drawRect(0f, 0f, screenWidth, screenHeight, bgPaint)

                val nutrientIterator = nutrients.iterator()
                while (nutrientIterator.hasNext()) {
                    val n = nutrientIterator.next()
                    n.update()
                    n.draw(canvas)
                    if (n.isDepleted()) nutrientIterator.remove()
                }

                for (microbe in microbes) {
                    microbe.update(screenWidth, screenHeight, touchX, touchY, nutrients)
                    microbe.draw(canvas)
                }
            } finally {
                holder.unlockCanvasAndPost(canvas)
            }
        }

        override fun onDestroy() {
            super.onDestroy()
            handler.removeCallbacks(frameCallback)
        }
    }
}

class Microbe(
    var x: Float,
    var y: Float,
    val baseRadius: Float,
    val depth: Float,
    colorIndex: Int
) {
    private var vx = (Random.nextFloat() - 0.5f) * 1.5f * depth
    private var vy = (Random.nextFloat() - 0.5f) * 1.5f * depth
    private var wobblePhase = Random.nextFloat() * 10f
    private val wobbleSpeed = Random.nextFloat() * 0.04f + 0.03f
    private val path = Path()

    private val baseColor = PALETTE[colorIndex]
    private val membranePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f * depth
        color = Color.argb((220 * depth).toInt(), Color.red(baseColor), Color.green(baseColor), Color.blue(baseColor))
    }
    private val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb((110 * depth).toInt(), Color.red(baseColor), Color.green(baseColor), Color.blue(baseColor))
    }
    private val nucleusPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb((180 * depth).toInt(), 230, 245, 255)
    }

    fun update(width: Float, height: Float, tx: Float?, ty: Float?, nutrients: List<Nutrient>) {
        wobblePhase += wobbleSpeed

        var targetX = tx
        var targetY = ty
        if (targetX == null && nutrients.isNotEmpty()) {
            val nearest = nutrients.minByOrNull { hypot(it.x - x, it.y - y) }
            if (nearest != null) {
                targetX = nearest.x
                targetY = nearest.y
            }
        }

        if (targetX != null && targetY != null) {
            val dx = targetX - x
            val dy = targetY - y
            val dist = hypot(dx, dy)
            if (dist > 15f) {
                vx += (dx / dist) * 0.08f * depth
                vy += (dy / dist) * 0.08f * depth
            }
        } else {
            vx += (Random.nextFloat() - 0.5f) * 0.15f
            vy += (Random.nextFloat() - 0.5f) * 0.15f
        }

        val speed = hypot(vx, vy)
        val maxSpeed = 3.5f * depth
        if (speed > maxSpeed) {
            vx = (vx / speed) * maxSpeed
            vy = (vy / speed) * maxSpeed
        }

        x += vx
        y += vy

        val padding = baseRadius * 1.5f
        if (x < padding) { x = padding; vx = -vx * 0.8f }
        if (x > width - padding) { x = width - padding; vx = -vx * 0.8f }
        if (y < padding) { y = padding; vy = -vy * 0.8f }
        if (y > height - padding) { y = height - padding; vy = -vy * 0.8f }
    }

    fun draw(canvas: Canvas) {
        path.reset()
        val segments = 12
        for (i in 0 until segments) {
            val angle = (i.toFloat() / segments) * (2f * PI.toFloat())
            val deformation = sin(angle * 4f + wobblePhase) * (baseRadius * 0.16f) +
                              cos(angle * 2f - wobblePhase * 0.8f) * (baseRadius * 0.08f)
            val r = baseRadius + deformation
            val px = x + cos(angle) * r
            val py = y + sin(angle) * r
            if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        path.close()

        canvas.drawPath(path, bodyPaint)
        canvas.drawPath(path, membranePaint)

        val nucleusLagX = x - vx * 3f
        val nucleusLagY = y - vy * 3f
        canvas.drawCircle(nucleusLagX, nucleusLagY, baseRadius * 0.28f, nucleusPaint)
    }

    companion object {
        val PALETTE = intArrayOf(
            Color.rgb(0, 240, 180),
            Color.rgb(0, 190, 255),
            Color.rgb(180, 110, 255),
            Color.rgb(255, 110, 180),
            Color.rgb(80, 255, 140)
        )
    }
}
class Nutrient(val x: Float, val y: Float) {
    private var life = 1.0f
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.rgb(255, 230, 130)
    }

    fun update() { life -= 0.015f }
    fun isDepleted(): Boolean = life <= 0f

    fun draw(canvas: Canvas) {
        paint.alpha = (life * 255).toInt().coerceIn(0, 255)
        canvas.drawCircle(x, y, 4.5f * life, paint)
    }
}
