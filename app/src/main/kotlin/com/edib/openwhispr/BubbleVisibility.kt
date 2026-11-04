package com.edib.openwhispr

/** An active capture stays reachable; idle dictation needs a current typing surface. */
internal object BubbleVisibility {
    fun shouldShow(enabled: Boolean, busy: Boolean, keyboardVisible: Boolean, focusedHardwareEditor: Boolean): Boolean =
        enabled && (busy || keyboardVisible || focusedHardwareEditor)
}
