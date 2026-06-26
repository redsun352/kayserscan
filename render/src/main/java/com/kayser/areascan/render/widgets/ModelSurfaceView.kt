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
 * İki çalışma modu var:
 *  - Serbest kamera modu (varsayılan): tek-dokunuş rotasyon, çift-dokunuş (pinch) zoom.
 *  - Tarama modu (`setScanningMode(true)`): kamera tam yukarıdan bakar şekilde kilitlenir,
 *    rotasyon/zoom devre dışı kalır; ekrana kısa dokunuşla (tap) bir hücre SEÇİLİR ve
 *    [onCellSelected] callback'i üzerinden bildirilir, sarı bir daire ile vurgulanır.
 *    Gerçek ölçüm bu sınıfın dışında, ayrı bir "Ölçü Al" butonuyla tetiklenir
 *    (bkz. AreaScanActivity) — basılı tutma burada DEĞİL, butonda olur.
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
    private var downX = 0f
    private var downY = 0f

    /**
     * Tarama modunda kısa dokunuşla (tap) bir hücre seçildiğinde çağrılır.
     * worldX/worldZ: grid düzlemindeki (Y=0) dünya koordinatı (metre), null ise
     * dokunulan nokta düzlemle kesişmedi (örn. ekran dışı bir açı — pratikte top-down'da nadir).
     * Callback ana (UI) thread'inde çağrılır.
     */
    var onCellSelected: ((worldX: Float, worldZ: Float) -> Unit)? = null

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
            modelRenderer.highlightedCell = null
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (scanningMode) {
            handleScanningTap(event)
        } else {
            when (event.pointerCount) {
                1 -> handleSingleTouch(event)
                2 -> handlePinch(event)
            }
        }
        return true
    }

    private fun handleScanningTap(event: MotionEvent) {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
            }
            MotionEvent.ACTION_UP -> {
                // Sadece belirgin bir sürükleme olmadan (gerçek "tap") seçim yapılır;
                // kamera bu modda zaten kilitli olsa da, kazara uzun parmak hareketlerini
                // seçim olarak saymamak için küçük bir tolerans uygulanır.
                val movedDistance = kotlin.math.hypot(event.x - downX, event.y - downY)
                if (movedDistance <= TAP_MOVE_THRESHOLD_PX) {
                    val world = modelRenderer.screenToWorldOnGroundPlane(event.x, event.y)
                    modelRenderer.highlightedCell = world
                    if (world != null) {
                        onCellSelected?.invoke(world[0], world[1])
                    }
                }
            }
        }
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

    /** Seçili hücreyi temizler (örn. ölçüm tamamlandıktan sonra). */
    fun clearSelection() {
        modelRenderer.highlightedCell = null
    }

    companion object {
        private const val ROTATE_SENSITIVITY = 0.4f
        private const val TAP_MOVE_THRESHOLD_PX = 20f
    }
}
