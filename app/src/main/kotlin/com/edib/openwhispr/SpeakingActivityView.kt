package com.edib.openwhispr

import android.app.AlertDialog
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/** Twelve local-calendar weeks. Event counts survive transcript deletion. */
internal class SpeakingActivityView(context: Context, private val days: List<SpeakingDay>,
    private val today: LocalDate = LocalDate.now()) : View(context) {
    private val recentStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusWeeks(11)
    private var start = recentStart
    private val byDate = days.associateBy { it.date }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = ReferenceTypography.face(context) }
    private val cell = ModernUi.dp(context, 30).toFloat()
    private val left = ModernUi.dp(context, 30).toFloat()
    private val top = ModernUi.dp(context, 30).toFloat()
    private var touched: LocalDate? = null
    val visibleDates: List<LocalDate> get() = (0L..83L).map { start.plusDays(it) }.filter { !it.isAfter(today) }
    val rangeLabel: String get() = "$start – ${visibleDates.last()}"
    fun earlierWeeks() { start = start.minusWeeks(12); touched = null; invalidate() }
    fun recentWeeks() { start = recentStart; touched = null; invalidate() }
    init {
        minimumWidth = (left + cell * 12).toInt(); minimumHeight = (top + cell * 7 + 8).toInt()
        contentDescription = "Speaking activity for the recent twelve weeks. Numbers show recordings per day. Use Day list for accessible dates and details."
        isClickable = true
        setOnClickListener { touched?.let(::showDay) }
    }
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(resolveSize(minimumWidth, widthMeasureSpec), resolveSize(minimumHeight, heightMeasureSpec))
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        paint.textSize = ModernUi.dp(context, 10).toFloat(); paint.textAlign = Paint.Align.CENTER
        val peak = visibleDates.maxOfOrNull { byDate[it]?.count ?: 0L }?.coerceAtLeast(1) ?: 1
        for (row in 0..6) {
            paint.color = ModernUi.color(context, R.color.ink_muted)
            canvas.drawText(listOf("M", "T", "W", "T", "F", "S", "S")[row], left / 2, top + row * cell + cell * .65f, paint)
        }
        for (week in 0..11) {
            paint.color = ModernUi.color(context, R.color.ink_muted)
            canvas.drawText(start.plusWeeks(week.toLong()).format(DateTimeFormatter.ofPattern("d/M")), left + week * cell + cell / 2, top - 10, paint)
            for (row in 0..6) {
                val date = start.plusDays(week * 7L + row)
                if (date.isAfter(today)) continue
                val count = byDate[date]?.count ?: 0L
                val blue = ModernUi.color(context, R.color.primary)
                val fill = if (count == 0L) ModernUi.color(context, R.color.panel_muted) else
                    androidx.core.graphics.ColorUtils.blendARGB(ModernUi.color(context, R.color.panel_muted), blue,
                        (.28 + .72 * count / peak).toFloat().coerceIn(.28f, 1f))
                paint.color = fill
                val x = left + week * cell; val y = top + row * cell
                canvas.drawRoundRect(RectF(x + 2, y + 2, x + cell - 2, y + cell - 2), 5f, 5f, paint)
                paint.alpha = 255
                paint.color = if (androidx.core.graphics.ColorUtils.calculateLuminance(fill) > .179)
                    android.graphics.Color.BLACK else android.graphics.Color.WHITE
                canvas.drawText(if (count > 99) "99+" else count.toString(), x + cell / 2, y + cell * .65f, paint)
            }
        }
    }
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_UP) {
            val column = ((event.x - left) / cell).toInt(); val row = ((event.y - top) / cell).toInt()
            touched = if (event.x >= left && event.y >= top && column in 0..11 && row in 0..6)
                start.plusDays(column * 7L + row).takeIf { !it.isAfter(today) } else null
            performClick(); return true
        }
        return super.onTouchEvent(event)
    }
    override fun performClick(): Boolean = super.performClick()
    fun showDay(date: LocalDate) {
        val day = byDate[date] ?: SpeakingDay(date, 0, 0, 0)
        AlertDialog.Builder(context).setTitle(date.format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy")))
            .setMessage("${day.count} recordings\n${day.words} recognized words\n${formatDuration(day.duration)} speaking")
            .setPositiveButton("Close", null).show()
    }
    fun showDayList() {
        val dates = visibleDates.reversed()
        AlertDialog.Builder(context).setTitle("Speaking activity · $rangeLabel")
            .setItems(dates.map { date -> "$date · ${byDate[date]?.count ?: 0} recordings · ${byDate[date]?.words ?: 0} words" }.toTypedArray()) { _, index -> showDay(dates[index]) }
            .setNegativeButton("Close", null).show()
    }
}

internal fun formatDuration(millis: Long): String {
    val seconds = millis / 1000
    return if (seconds < 60) "$seconds sec" else "${seconds / 60} min ${seconds % 60} sec"
}

internal fun speakingInsightsPanel(context: Context, events: List<SpeakingEvent>, typingWpm: Int): View {
    val summary = SpeakingInsights.calculate(events)
    val words = events.sumOf { it.words }; val duration = events.sumOf { it.speakingMillis }
    val dp = { n: Int -> ModernUi.dp(context, n) }
    return LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL; tag = "insights-summary"
        fun tile(value: String, label: String, key: String) = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL; background = ModernUi.surface(context)
            setPadding(dp(14), dp(16), dp(14), dp(16)); tag = key
            addView(ModernUi.label(context, value, 28f, strong = true))
            addView(ModernUi.label(context, label, 13f, muted = true))
        }
        val tiles = listOf(tile(words.toString(), "Recognized words", "insights-words"),
            tile(String.format(Locale.getDefault(), "%.0f", summary.recognizedWpm), "Recognized words/min", "insights-wpm"),
            tile(events.size.toString(), "Recordings", "insights-recordings"),
            tile(summary.currentStreak.toString(), "Day streak · longest ${summary.longestStreak}", "insights-streak"))
        for (pair in tiles.chunked(2)) addView(LinearLayout(context).apply {
            pair.forEach { addView(it, LinearLayout.LayoutParams(0, -1, 1f).apply { setMargins(dp(3), dp(4), dp(3), dp(4)) }) }
        })
        addView(ModernUi.label(context, "Speaking activity", 19f, strong = true).apply { setPadding(0, dp(22), 0, dp(8)) })
        addView(ModernUi.label(context, "Recent 12 weeks · local dates · numbers are recordings", 12f, muted = true))
        val heatmap = SpeakingActivityView(context, summary.days).apply { tag = "speaking-heatmap" }
        val range = ModernUi.label(context, heatmap.rangeLabel, 12f, muted = true).apply { setPadding(0, dp(8), 0, 0) }
        addView(range)
        addView(LinearLayout(context).apply {
            addView(screenButton(context, "Earlier 12 weeks") { heatmap.earlierWeeks(); range.text = heatmap.rangeLabel }, LinearLayout.LayoutParams(0, -2, 1f))
            addView(screenButton(context, "Recent 12 weeks") { heatmap.recentWeeks(); range.text = heatmap.rangeLabel }, LinearLayout.LayoutParams(0, -2, 1f))
        })
        addView(HorizontalScrollView(context).apply { addView(heatmap); isHorizontalScrollBarEnabled = true })
        addView(ModernUi.label(context, "0 = none · lighter blue = fewer · darker blue = more", 12f, muted = true))
        addView(screenButton(context, "Day list") { heatmap.showDayList() }.apply { contentDescription = "Speaking activity accessible day list" })
        if (events.isEmpty()) addView(ModernUi.label(context, "No speaking activity yet. Successful dictations will fill this view.", 14f, muted = true))
        val saved = InsightCalculator.savedSeconds(words.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(), duration, typingWpm.coerceAtLeast(1))
        addView(ModernUi.label(context, "Speaking · ${formatDuration(duration)}", 15f, strong = true).apply { setPadding(0, dp(20), 0, dp(6)) })
        addView(ModernUi.label(context, "Estimated typing time saved · ${formatDuration((saved * 1000).toLong())}", 15f))
        addView(ModernUi.label(context, "Rate estimates recognized words over recorded speaking time. Time saved assumes $typingWpm words/min typing; model and processing time are excluded. Streaks may end today or yesterday. Deleting transcript text keeps activity; Reset statistics clears it.", 12f, muted = true).apply { setPadding(0, dp(10), 0, dp(14)) })
    }
}
