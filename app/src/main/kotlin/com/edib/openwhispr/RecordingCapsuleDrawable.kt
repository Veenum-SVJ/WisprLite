package com.edib.openwhispr

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import kotlin.math.min
import kotlin.math.roundToInt

/** Compact recording-only dotted waveform; actual microphone levels drive its height. */
internal class RecordingCapsuleDrawable : Drawable() {
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply { color=Color.WHITE }
    var voiceLevel=0f
        set(value) { field=value.coerceIn(0f,1f); invalidateSelf() }
    private val peaks=intArrayOf(2,3,5,8,5,3,7,10,8,4,6,9,6,3,5,3,2)
    override fun draw(canvas: Canvas) {
        val cell=min(bounds.width()/20f,bounds.height()/12f)
        val radius=(cell*.24f).coerceAtLeast(.5f)
        val x0=bounds.exactCenterX()-(peaks.size-1)*cell/2
        peaks.forEachIndexed { column,peak ->
            val rows=2+(voiceLevel*(peak-2)).roundToInt()
            for(row in 0 until rows) canvas.drawCircle(x0+column*cell,
                bounds.exactCenterY()+(row-(rows-1)/2f)*cell,radius,paint)
        }
    }
    override fun setAlpha(alpha: Int) { paint.alpha=alpha; invalidateSelf() }
    override fun setColorFilter(filter: ColorFilter?) { paint.colorFilter=filter; invalidateSelf() }
    @Deprecated("Android Drawable API") override fun getOpacity(): Int=PixelFormat.TRANSLUCENT
}
