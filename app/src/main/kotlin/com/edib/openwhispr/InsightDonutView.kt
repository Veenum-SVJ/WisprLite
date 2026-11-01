package com.edib.openwhispr

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import kotlin.math.min

/** Native, static chart; the labelled figures below it provide an accessible equivalent. */
class InsightDonutView(context: Context, private val speakingSeconds: Double, private val savedSeconds: Double) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val density = resources.displayMetrics.density

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        minimumHeight = (156 * density).toInt()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val stroke = 15 * density
        val radius = (min(width.toFloat(), height.toFloat()) - stroke - 16 * density) / 2
        if (radius <= 0) return
        val cx = width / 2f
        val cy = height / 2f
        val oval = RectF(cx - radius, cy - radius, cx + radius, cy + radius)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = stroke
        paint.strokeCap = Paint.Cap.BUTT
        paint.color = ModernUi.color(context, R.color.ink_muted)
        paint.alpha = 35
        canvas.drawOval(oval, paint)
        paint.alpha = 255
        val total = speakingSeconds + savedSeconds
        if (total > 0) {
            val spokenSweep = (speakingSeconds / total * 360).toFloat()
            paint.color = ModernUi.color(context, R.color.ink_muted)
            canvas.drawArc(oval, -90f, spokenSweep, false, paint)
            paint.color = ModernUi.color(context, R.color.primary)
            canvas.drawArc(oval, -90f + spokenSweep, 360f - spokenSweep, false, paint)
        }
        paint.style = Paint.Style.FILL
        paint.color = ModernUi.color(context, R.color.ink)
        paint.textSize = 14 * resources.displayMetrics.scaledDensity
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText(if (total > 0) "Time" else "No data", cx, cy - (paint.ascent() + paint.descent()) / 2, paint)
    }
}
