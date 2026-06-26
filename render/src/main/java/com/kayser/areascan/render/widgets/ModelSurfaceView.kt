package com.kayser.areascan.render.widgets

import android.content.Context
import android.opengl.GLSurfaceView
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.MotionEvent
import com.kayser.areascan.render.opengl.ModelSurfaceRenderer

/**
 * Yüzey Tarama 3D görünümünün ana yüzeyi.
 * İsim, Thuban Lodestar'daki `com.lugatek.thubanlodestar.widgets.opengl.ModelSurfaceView`
 * sınıfıyla aynı tutuldu (decompile analizinde bulunan layout referansıyla uyumlu olması için).
 *
 * İki çalışma modu var:
 *  - Serbest kamera modu (varsayılan): tek-dokunuş rotasyon, çift-dokunuş (pinch) zoom.
 *  - Tarama modu (`setScanningMode(true)`): kamera tam yukarıdan bakar şekilde kilitlenir,
 *    rotasyon/zoom devre dışı kalır; ekrana basılı tutma (long-press) ile dünya düzlemindeki
 *    (X,Z) konumu hesaplanır ve [onCellLongPress] callback'i üzerinden bildirilir.
 */
class ModelSurfaceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : GLSurfaceView(context, attrs) {

    val modelRenderer = ModelSurfaceRenderer()

    private var lastX = 0f
    private var lastY = 0f
    private var lastPinchDistance = -1f

    private var scanningMode = false
    private val longPressHandler = Handler(Looper.getMainLooper())
    private var longPressTriggered = false
    private var downX = 0f
    private var downY = 0f

    /**
     * Tarama modunda basılı tutma tetiklendiğinde çağrılır.
     * worldX/worldZ: grid düzlemindeki (Y=0) dünya koordinatı (metre).
     * Callback ana (UI) thread'inde çağrılır.
     */
    var onCellLongPress: ((worldX: Float, worldZ: Float) -> Unit)? = null

    /** Basılı tutma sırasında (henüz tetiklenmeden) ilerleme göstermek isteyen UI için. */
    var onLongPressProgress: ((progress: Float) -> Unit)? = null
    var onLongPressCancelled: (() -> Unit)? = null

    private val longPressRunnable = Runnable {
        longPressTriggered = true
        val world = modelRenderer.screenToWorldOnGroundPlane(downX, downY)
        if (world != null) {
            onCellLongPress?.invoke(world[0], world[1])
        }
    }

    init {
        setEGLContextClientVersion(2)
        setRenderer(modelRenderer)
        renderMode = RENDERMODE_CONTINUOUSLY
    }

    /** Tarama moduna geçer/çıkar. Tarama modunda kamera otomatik olarak top-down kilitlenir. */
    fun setScanningMode(enabled: Boolean) {
        scanningMode = enabled
        if (enabled) {
            modelRenderer.camera.lockTopDown()
        } else {
            modelRenderer.camera.unlockOrbit()
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (scanningMode) {
            handleScanningTouch(event)
        } else {
            when (event.pointerCount) {
                1 -> handleSingleTouch(event)
                2 -> handlePinch(event)
            }
        }
        return true
    }

    private fun handleScanningTouch(event: MotionEvent) {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                longPressTriggered = false
                modelRenderer.highlightedCell = modelRenderer.screenToWorldOnGroundPlane(downX, downY)
                longPressHandler.postDelayed(longPressRunnable, LONG_PRESS_DURATION_MS)
                reportProgressTicks()
            }
            MotionEvent.ACTION_MOVE -> {
                // Parmak belirgin şekilde kayarsa (örn. kazara sürükleme) basılı tutmayı iptal et.
                val movedDistance = kotlin.math.hypot(event.x - downX, event.y - downY)
                if (movedDistance > MOVE_CANCEL_THRESHOLD_PX) {
                    cancelScanLongPress()
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (!longPressTriggered) {
                    cancelScanLongPress()
                    modelRenderer.highlightedCell = null
                }
            }
        }
    }

    private fun cancelScanLongPress() {
        longPressHandler.removeCallbacks(longPressRunnable)
        if (!longPressTriggered) {
            onLongPressCancelled?.invoke()
        }
    }

    /** Basılı tutma ilerlemesini periyodik olarak UI'a bildirir (örn. dairesel progress göstergesi için). */
    private fun reportProgressTicks() {
        val startTime = System.currentTimeMillis()
        val tick = object : Runnable {
            override fun run() {
                if (longPressTriggered) return
                val elapsed = System.currentTimeMillis() - startTime
                val progress = (elapsed.toFloat() / LONG_PRESS_DURATION_MS).coerceIn(0f, 1f)
                onLongPressProgress?.invoke(progress)
                if (progress < 1f) {
                    longPressHandler.postDelayed(this, PROGRESS_TICK_MS)
                }
            }
        }
        longPressHandler.postDelayed(tick, PROGRESS_TICK_MS)
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
        private const val LONG_PRESS_DURATION_MS = 1200L
        private const val PROGRESS_TICK_MS = 50L
        private const val MOVE_CANCEL_THRESHOLD_PX = 20f
    }
}
