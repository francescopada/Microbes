package com.retro.microbes

import android.content.Context
import android.opengl.GLSurfaceView
import android.service.wallpaper.WallpaperService
import android.view.MotionEvent
import android.view.SurfaceHolder

class MicrobesWallpaperService : WallpaperService() {
    override fun onCreateEngine(): Engine = GLEngine()

    inner class GLEngine : Engine() {
        private val renderer = MicrobesRenderer(resources.displayMetrics.density)
        private var view: WallpaperGLView? = null

        // GLSurfaceView che disegna sulla surface del wallpaper
        inner class WallpaperGLView(c: Context) : GLSurfaceView(c) {
            override fun getHolder(): SurfaceHolder = surfaceHolder
            fun destroy() = super.onDetachedFromWindow()
        }

        override fun onCreate(surfaceHolder: SurfaceHolder?) {
            super.onCreate(surfaceHolder)
            setTouchEventsEnabled(true)
            view = WallpaperGLView(this@MicrobesWallpaperService).apply {
                setEGLContextClientVersion(2)
                setEGLConfigChooser(8, 8, 8, 8, 0, 0)
                setRenderer(renderer)
                renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
            }
        }

        override fun onVisibilityChanged(visible: Boolean) {
            super.onVisibilityChanged(visible)
            if (visible) view?.onResume() else view?.onPause()
        }

        override fun onOffsetsChanged(
            xOffset: Float, yOffset: Float, xOffsetStep: Float,
            yOffsetStep: Float, xPixelOffset: Int, yPixelOffset: Int
        ) {
            renderer.xOffset = xOffset
        }

        override fun onTouchEvent(event: MotionEvent) {
            val a = event.actionMasked
            if (a == MotionEvent.ACTION_DOWN || a == MotionEvent.ACTION_POINTER_DOWN) {
                val i = event.actionIndex
                renderer.touches.add(floatArrayOf(event.getX(i), event.getY(i), 1f))
            } else if (a == MotionEvent.ACTION_MOVE) {
                for (i in 0 until event.pointerCount) {
                    renderer.touches.add(floatArrayOf(event.getX(i), event.getY(i), 0f))
                }
            }
        }

        override fun onDestroy() {
            view?.destroy()
            view = null
            super.onDestroy()
        }
    }
}
