package com.edib.openwhispr

import android.animation.ValueAnimator
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.os.SystemClock
import kotlin.math.cos
import kotlin.math.sin

/** Circular dotted mark. Recording reflects measured audio; only processing loops. */
internal class DottedVoiceDrawable : Drawable(), Runnable {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    var recording = false
        set(value) { field = value; invalidateSelf() }
    var voiceLevel = 0f
        set(value) { field = value.coerceIn(0f, 1f); invalidateSelf() }
    var processing = false
        set(value) {
            field = value
            unscheduleSelf(this)
            invalidateSelf()
        }
    override fun draw(canvas: Canvas) {
        val save = canvas.save()
        canvas.translate(bounds.left.toFloat(), bounds.top.toFloat())
        canvas.scale(bounds.width() / 24f, bounds.height() / 24f)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 0.9f
        canvas.drawCircle(12f, 12f, 11.25f, paint)
        paint.style = Paint.Style.FILL
        if (processing) {
            val moving = ValueAnimator.areAnimatorsEnabled()
            val phase = if (moving) (SystemClock.uptimeMillis() / 120 % 8).toInt() else 0
            for (index in 0 until 8) {
                val angle = index * Math.PI / 4 - Math.PI / 2
                canvas.drawCircle(12f + cos(angle).toFloat() * 6.5f, 12f + sin(angle).toFloat() * 6.5f,
                    if (index == phase) 1.4f else 0.9f, paint)
            }
            if (moving && isVisible) scheduleSelf(this, SystemClock.uptimeMillis() + 80)
        } else {
            val pattern = intArrayOf(2, 4, 5, 3, 2)
            for (column in pattern.indices) {
                val rows = if (recording) 1 + (voiceLevel * (pattern[column] - 1)).toInt() else pattern[column]
                for (row in 0 until rows) canvas.drawCircle(4.4f + column * 3.8f,
                    12f + (row - (rows - 1) / 2f) * 3.4f, 1.05f, paint)
            }
        }
        canvas.restoreToCount(save)
    }
    override fun run() { if (processing && isVisible) invalidateSelf() }
    override fun setVisible(visible: Boolean, restart: Boolean): Boolean {
        if (!visible) unscheduleSelf(this)
        return super.setVisible(visible, restart)
    }
    override fun setAlpha(alpha: Int) { paint.alpha = alpha; invalidateSelf() }
    override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter; invalidateSelf() }
    @Deprecated("Android Drawable API")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
