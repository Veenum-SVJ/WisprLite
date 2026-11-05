package com.edib.openwhispr

import android.graphics.*
import android.graphics.drawable.Drawable
import android.os.SystemClock
import kotlin.math.sin

/** One monochrome surface morphs from the logo to the recording capsule. */
internal class DottedVoiceDrawable : Drawable(), Runnable {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND }
    private val surface = RectF()
    private var opacity = 255
    private val tops = floatArrayOf(10.1f, 7.7f, 6.4f, 5.3f, 6.5f, 8f, 11f)
    private val bottoms = floatArrayOf(12f, 14.2f, 15.5f, 16.5f, 14.8f, 14.2f, 12.4f)
    var expansion = 0f
        set(value) { field = value.coerceIn(0f, 1f); invalidateSelf() }
    var dockedLeft = true
        set(value) { field = value; invalidateSelf() }
    var holdRecording = false
        set(value) { field = value; invalidateSelf() }
    var recording = false
        set(value) { field = value; invalidateSelf() }
    var voiceLevel = 0f
        set(value) { field = value.coerceIn(0f, 1f); invalidateSelf() }
    var processing = false
        set(value) { field = value; unscheduleSelf(this); invalidateSelf() }

    override fun draw(canvas: Canvas) {
        val h = bounds.height().toFloat()
        if (h <= 0f) return
        val holding = recording && holdRecording
        val width = if (holding) h else h + (bounds.width() - h) * expansion
        val left = if (dockedLeft) bounds.left.toFloat() else bounds.right - width
        val centerX = left + width / 2f
        val centerY = bounds.exactCenterY()
        val unit = h / 24f
        surface.set(left + unit, bounds.top + unit, left + width - unit, bounds.bottom - unit)
        paint.style = Paint.Style.FILL
        paint.color = Color.rgb(24, 24, 24)
        paint.alpha = (opacity * .88f).toInt()
        canvas.drawRoundRect(surface, h / 2f, h / 2f, paint)
        paint.style = Paint.Style.STROKE
        paint.color = Color.WHITE
        paint.alpha = (opacity * (1f - expansion * if (holdRecording) .05f else .65f)).toInt()
        paint.strokeWidth = unit * .65f
        surface.inset(unit * .8f, unit * .8f)
        canvas.drawRoundRect(surface, h / 2f, h / 2f, paint)
        if (holding) {
            paint.alpha = opacity
            paint.strokeWidth = unit * .5f
            surface.inset(unit * 1.2f, unit * 1.2f)
            canvas.drawRoundRect(surface, h / 2f, h / 2f, paint)
        }

        // Processing is functional progress feedback, independent of Android's
        // transition/animator duration scale. In particular Pixel can have scale=0.
        val moving = processing
        val time = if (moving) SystemClock.uptimeMillis() / 190.0 else 0.0
        paint.alpha = opacity
        paint.strokeWidth = unit * .95f
        // The same seven columns form the idle logo, live speech and loading wave.
        for (column in 0..6) {
            val x = centerX + (column - 3) * (1.6f * unit + expansion * (width - h) / 9f)
            val top = tops[column]
            val bottom = bottoms[column]
            val idleHeight = (bottom - top) * unit
            val liveHeight = (unit * (1.5f + voiceLevel * (7f + 5f * sin(column * 1.7).toFloat()))).coerceAtMost(h * if (holding) .4f else .5f)
            val loadHeight = unit * (3f + 8f * ((sin(time - column * .65) + 1) / 2).toFloat())
            val height = if (processing) idleHeight + (loadHeight - idleHeight) * (1f - expansion)
                else idleHeight + (liveHeight - idleHeight) * expansion
            val idleY = bounds.top + (top + bottom) * .5f * unit
            val y = idleY + (centerY - idleY) * expansion - if (holding) h * .09f * expansion else 0f
            // Preserve the two segmented bars of the supplied waveform while idle.
            val gap = (1f - expansion) * if (processing) 0f else 1.5f * unit
            if ((column == 1 || column == 4) && gap > .1f) {
                val split = if (column == 1) bounds.top + 10.8f * unit else bounds.top + 8.3f * unit
                canvas.drawLine(x, y - height / 2, x, split - gap / 2, paint)
                canvas.drawLine(x, split + gap / 2, x, y + height / 2, paint)
            } else canvas.drawLine(x, y - height / 2, x, y + height / 2, paint)
        }
        paint.alpha = (opacity * (1f - expansion)).toInt()
        canvas.drawLine(centerX, bounds.top + 17.8f * unit, centerX, bounds.top + 19.2f * unit, paint)
        canvas.drawLine(centerX - 2.1f * unit, bounds.top + 19.2f * unit,
            centerX + 2.1f * unit, bounds.top + 19.2f * unit, paint)
        if (holding) {
            paint.alpha = opacity
            paint.style = Paint.Style.FILL
            paint.typeface = Typeface.DEFAULT_BOLD
            paint.textAlign = Paint.Align.CENTER
            paint.textSize = h * .17f
            canvas.drawText("HOLD", centerX, bounds.bottom - h * .22f, paint)
        }
        if (moving && isVisible) scheduleSelf(this, SystemClock.uptimeMillis() + 32)
    }
    override fun run() { if (processing && isVisible) invalidateSelf() }
    override fun setVisible(visible: Boolean, restart: Boolean): Boolean {
        if (!visible) unscheduleSelf(this)
        return super.setVisible(visible, restart)
    }
    override fun setAlpha(alpha: Int) { opacity = alpha; invalidateSelf() }
    override fun setColorFilter(filter: ColorFilter?) { paint.colorFilter = filter; invalidateSelf() }
    @Deprecated("Android Drawable API") override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
