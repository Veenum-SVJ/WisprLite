package com.edib.openwhispr

import kotlin.math.roundToInt

object BubbleSize {
    const val PREFERENCE_KEY = "bubble_size_percent"
    const val MIN_PERCENT = 90
    const val DEFAULT_PERCENT = 100
    const val MAX_PERCENT = 150
    const val STEP_PERCENT = 5

    const val BUTTON_DP = 44
    const val RING_DP = 56
    const val PADDING_DP = 0
    const val MIN_TOUCH_TARGET_DP = 48
    private const val TAP_THRESHOLD_DP = 10
    private const val FEEDBACK_OFFSET_DP = 64

    data class Dimensions(
        val buttonDp: Int,
        val ringDp: Int,
        val paddingDp: Int,
        val tapThresholdDp: Int,
        val feedbackOffsetDp: Int
    )

    data class Position(val x: Int, val y: Int)

    const val X_FRACTION_KEY = "overlay_x_fraction"
    const val Y_FRACTION_KEY = "overlay_y_fraction"

    fun freePosition(xFraction: Float, yFraction: Float, screenWidth: Int, screenHeight: Int, ringSize: Int, margin: Int): Position {
        fun coordinate(fraction: Float, length: Int): Int = margin +
            (maxOf(0, length - ringSize - 2 * margin) * (if (fraction.isFinite()) fraction.coerceIn(0f, 1f) else 0.5f)).roundToInt()
        return clampPosition(coordinate(xFraction, screenWidth), coordinate(yFraction, screenHeight), screenWidth, screenHeight, ringSize, margin)
    }

    fun fraction(coordinate: Int, length: Int, ringSize: Int, margin: Int): Float =
        ((coordinate - margin).toFloat() / maxOf(1, length - ringSize - 2 * margin)).coerceIn(0f, 1f)

    fun preferencePercent(value: Int?): Int =
        if (value != null && value in MIN_PERCENT..MAX_PERCENT &&
            (value - MIN_PERCENT) % STEP_PERCENT == 0
        ) {
            value
        } else {
            DEFAULT_PERCENT
        }

    fun sliderPercent(value: Int): Int {
        val bounded = value.coerceIn(MIN_PERCENT, MAX_PERCENT)
        return MIN_PERCENT + ((bounded - MIN_PERCENT + STEP_PERCENT / 2) / STEP_PERCENT) * STEP_PERCENT
    }

    fun dimensions(percent: Int): Dimensions {
        val scale = percent.coerceIn(MIN_PERCENT, MAX_PERCENT) / 100f
        fun scaled(value: Int) = (value * scale).roundToInt()

        return Dimensions(
            buttonDp = scaled(BUTTON_DP),
            ringDp = maxOf(MIN_TOUCH_TARGET_DP, scaled(RING_DP)),
            paddingDp = scaled(PADDING_DP),
            tapThresholdDp = scaled(TAP_THRESHOLD_DP),
            feedbackOffsetDp = scaled(FEEDBACK_OFFSET_DP)
        )
    }

    fun valueLabel(
        percent: Int,
        standard: String = "standard",
        smaller: String = "smaller",
        larger: String = "larger",
    ): String = when (percent) {
        DEFAULT_PERCENT -> "$percent% · $standard"
        in MIN_PERCENT until DEFAULT_PERCENT -> "$percent% · $smaller"
        else -> "$percent% · $larger"
    }

    fun accessibilityDescription(
        percent: Int,
        standard: String = "standard size",
        smaller: String = "smaller than standard",
        larger: String = "larger than standard",
        percentUnit: String = "percent",
    ): String = when (percent) {
        DEFAULT_PERCENT -> "$percent $percentUnit, $standard"
        in MIN_PERCENT until DEFAULT_PERCENT -> "$percent $percentUnit, $smaller"
        else -> "$percent $percentUnit, $larger"
    }

    fun clampPosition(
        x: Int,
        y: Int,
        screenWidth: Int,
        screenHeight: Int,
        ringSize: Int,
        margin: Int,
        height: Int = ringSize
    ): Position {
        val maxX = maxOf(margin, screenWidth - ringSize - margin)
        val maxY = maxOf(margin, screenHeight - height - margin)
        return Position(x.coerceIn(margin, maxX), y.coerceIn(margin, maxY))
    }

    fun resizedDockedPosition(previous: Position, oldWidth: Int, width: Int, height: Int,
        screenWidth: Int, screenHeight: Int, margin: Int): Position {
        val left = previous.x + oldWidth / 2 <= screenWidth / 2
        return Position(if (left) margin else maxOf(margin, screenWidth - width - margin),
            previous.y.coerceIn(margin, maxOf(margin, screenHeight - height - margin)))
    }

    /** Final placement docks to the nearest side; an exact middle drop chooses left. */
    fun dockedPosition(x: Int, y: Int, screenWidth: Int, screenHeight: Int, width: Int, margin: Int, height: Int = width): Position =
        Position(snappedX(x, width, screenWidth, margin), y.coerceIn(margin, maxOf(margin, screenHeight - height - margin)))

    fun snappedX(x: Int, ringSize: Int, screenWidth: Int, margin: Int): Int {
        val maxX = maxOf(margin, screenWidth - ringSize - margin)
        return if (x + ringSize / 2 > screenWidth / 2) maxX else margin
    }

    const val POSITION_PERCENT_KEY = "overlay_position_percent"
    const val ON_LEFT_KEY = "overlay_on_left"
    const val DEFAULT_POSITION_PERCENT = 50

    fun positionPercent(value: Int?): Int = (value ?: DEFAULT_POSITION_PERCENT).coerceIn(0, 100)

    /** Where the bubble sits for a saved setting: [percent] is the share of the
     * available height (0 = top edge, 100 = bottom edge, margins included), so
     * the whole ring always stays on screen. */
    fun positionFor(
        percent: Int,
        onLeft: Boolean,
        screenWidth: Int,
        screenHeight: Int,
        ringSize: Int,
        margin: Int
    ): Position {
        val maxY = maxOf(margin, screenHeight - ringSize - margin)
        val y = margin + ((maxY - margin) * positionPercent(percent) / 100f).roundToInt()
        val x = if (onLeft) margin else screenWidth - ringSize - margin
        return clampPosition(x, y, screenWidth, screenHeight, ringSize, margin)
    }
}
