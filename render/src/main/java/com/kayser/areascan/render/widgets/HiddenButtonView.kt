package com.kayser.areascan.render.widgets

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

/**
 * Ekran kenarında ince, yarı saydam bir tutamaç (handle) çizen ve dokunulduğunda
 * bir callback tetikleyen widget. Thuban'daki `HiddenButtonView` referansıyla aynı
 * görsel amaç: NavigationRailView'i gösterip gizlemek için kullanılan dar dokunma alanı.
 */
class HiddenButtonView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    var onToggle: (() -> Unit)? = null

    private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(80, 255, 255, 255)
        style = Paint.Style.FILL
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val handleWidth = width * 0.4f
        val handleHeight = height * 0.25f
        val left = (width - handleWidth) / 2f
        val top = (height - handleHeight) / 2f
        canvas.drawRoundRect(
            left, top, left + handleWidth, top + handleHeight,
            handleWidth / 2f, handleWidth / 2f,
            handlePaint
        )
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_UP) {
            onToggle?.invoke()
            performClick()
        }
        return true
    }

    override fun performClick(): Boolean {
        return super.performClick()
    }
}
