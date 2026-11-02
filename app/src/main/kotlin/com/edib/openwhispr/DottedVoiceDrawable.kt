package com.edib.openwhispr

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.graphics.drawable.Drawable
import android.os.SystemClock
import androidx.core.content.ContextCompat

/** User-supplied logo; a small outer arc indicates processing without replacing the mark. */
internal class DottedVoiceDrawable(context: Context) : Drawable(), Runnable {
    private val logo by lazy { requireNotNull(ContextCompat.getDrawable(context, R.drawable.openwisper_logo)).mutate() }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val clip = Path()
    private val arc = RectF()
    var recording = false
        set(value) { field = value; invalidateSelf() }
    var voiceLevel = 0f
        set(value) { field = value.coerceIn(0f, 1f); invalidateSelf() }
    var processing = false
        set(value) { field = value; unscheduleSelf(this); invalidateSelf() }
    override fun onBoundsChange(bounds: Rect) {
        logo.bounds = bounds
        clip.reset(); clip.addOval(RectF(bounds), Path.Direction.CW)
        paint.strokeWidth = bounds.width() * .025f
        arc.set(bounds); arc.inset(paint.strokeWidth, paint.strokeWidth)
    }
    override fun draw(canvas: Canvas) {
        val saved = canvas.save(); canvas.clipPath(clip); logo.draw(canvas); canvas.restoreToCount(saved)
        if (processing) {
            val moving = ValueAnimator.areAnimatorsEnabled()
            val angle = if (moving) (SystemClock.uptimeMillis() / 5 % 360).toFloat() else -90f
            canvas.drawArc(arc, angle, 85f, false, paint)
            if (moving && isVisible) scheduleSelf(this, SystemClock.uptimeMillis() + 80)
        }
    }
    override fun run() { if (processing && isVisible) invalidateSelf() }
    override fun setVisible(visible: Boolean, restart: Boolean): Boolean {
        if (!visible) unscheduleSelf(this)
        return super.setVisible(visible, restart)
    }
    override fun setAlpha(alpha: Int) { logo.alpha = alpha; paint.alpha = alpha; invalidateSelf() }
    override fun setColorFilter(filter: ColorFilter?) { logo.colorFilter = filter; paint.colorFilter = filter; invalidateSelf() }
    @Deprecated("Android Drawable API") override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
