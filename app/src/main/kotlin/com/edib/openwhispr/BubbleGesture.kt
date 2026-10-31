package com.edib.openwhispr

/** Distinguishes a tap, a press-to-talk session and a drag without sending audio on cancellation. */
internal class BubbleGesture(private val dragThreshold: Float) {
    enum class Release { TAP, STOP_HOLD, SAVE_POSITION, NONE }
    private var pressed = false
    private var dragging = false
    private var holding = false
    private var canHold = false
    fun down(idle: Boolean) { pressed = true; dragging = false; holding = false; canHold = idle }
    fun hold(): Boolean {
        if (!pressed || dragging || holding || !canHold) return false
        holding = true
        return true
    }
    fun move(distance: Float): Boolean {
        if (!pressed || holding) return false
        if (distance >= dragThreshold) dragging = true
        return dragging
    }
    fun up(): Release {
        val result = when { !pressed -> Release.NONE; holding -> Release.STOP_HOLD; dragging -> Release.SAVE_POSITION; else -> Release.TAP }
        pressed = false
        return result
    }
    fun cancel(): Boolean { val wasHolding = pressed && holding; pressed = false; return wasHolding }
}
