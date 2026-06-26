package com.kayser.areascan.render.widgets

import android.content.Context
import android.opengl.GLSurfaceView
import android.util.AttributeSet
import android.view.MotionEvent
import com.kayser.areascan.render.opengl.ModelSurfaceRenderer

/**
 * Yüzey Tarama 3D görünümünün ana yüzeyi.
 * İsim, Thuban Lodestar'daki `com.lugatek.thubanlodestar.widgets.opengl.ModelSurfaceView`
 * sınıfıyla aynı tutuldu (decompile analizinde bulunan layout referansıyla uyumlu olması için).
 *
 * Tek-dokunuş: orbit kamera rotasyonu
 * Çift-dokunuş (pinch): zoom
 */
class ModelSurfaceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : GLSurfaceView(context, attrs) {

    val modelRenderer = ModelSurfaceRenderer()

    private var lastX = 0f
    private var lastY = 0f
    private var lastPinchDistance = -1f

    init {
        setEGLContextClientVersion(2)
        setRenderer(modelRenderer)
        renderMode = RENDERMODE_CONTINUOUSLY
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.pointerCount) {
            1 -> handleSingleTouch(event)
            2 -> handlePinch(event)
        }
        return true
    }

    private fun handleSingleTouch(event: MotionEvent) {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                lastX = event.x
                lastY = event.y
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = (event.x - lastX) * ROTATE_SENSITIVITY
                val dy = (event.y - lastY) * ROTATE_SENSITIVITY
                modelRenderer.camera.rotate(dx, dy)
                lastX = event.x
                lastY = event.y
            }
        }
    }

    private fun handlePinch(event: MotionEvent) {
        val dx = event.getX(0) - event.getX(1)
        val dy = event.getY(0) - event.getY(1)
        val distance = kotlin.math.sqrt(dx * dx + dy * dy)

        if (event.action == MotionEvent.ACTION_MOVE && lastPinchDistance > 0f) {
            val factor = distance / lastPinchDistance
            modelRenderer.camera.zoom(factor)
        }
        lastPinchDistance = distance
    }

    fun resetCameraRotation() {
        modelRenderer.camera.resetRotation()
    }

    fun resetCameraZoom() {
        modelRenderer.camera.resetZoom()
    }

    companion object {
        private const val ROTATE_SENSITIVITY = 0.4f
    }
}
