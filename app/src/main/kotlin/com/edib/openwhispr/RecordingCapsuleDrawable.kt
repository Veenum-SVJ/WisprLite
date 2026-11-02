package com.edib.openwhispr

import android.graphics.*
import android.graphics.drawable.Drawable
import kotlin.math.min

/** Rounded waveform bars echo the supplied logo and follow measured microphone levels. */
internal class RecordingCapsuleDrawable : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    var voiceLevel = 0f
        set(value) { field = value.coerceIn(0f, 1f); invalidateSelf() }
    private val peaks = intArrayOf(2,3,5,8,5,3,7,10,8,4,6,9,6,3,5,3,2)
    override fun draw(canvas: Canvas) {
        val step = bounds.width() / (peaks.size + 3f)
        val width = min(step * .5f, bounds.height() * .11f)
        val start = bounds.exactCenterX() - (peaks.size - 1) * step / 2
        peaks.forEachIndexed { column, peak ->
            val height = width + voiceLevel * (bounds.height() * .75f * peak / 10f - width).coerceAtLeast(0f)
            val x = start + column * step
            canvas.drawRoundRect(x - width / 2, bounds.exactCenterY() - height / 2,
                x + width / 2, bounds.exactCenterY() + height / 2, width / 2, width / 2, paint)
        }
    }
    override fun setAlpha(alpha: Int) { paint.alpha = alpha; invalidateSelf() }
    override fun setColorFilter(filter: ColorFilter?) { paint.colorFilter = filter; invalidateSelf() }
    @Deprecated("Android Drawable API") override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
