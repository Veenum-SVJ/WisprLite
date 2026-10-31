package com.edib.openwhispr

import org.junit.Assert.*
import org.junit.Test

class BubbleGestureTest {
    @Test fun tapAndSecondTapRemainTaps() {
        val gesture = BubbleGesture(10f)
        gesture.down(true); assertEquals(BubbleGesture.Release.TAP, gesture.up())
        gesture.down(false); assertFalse(gesture.hold()); assertEquals(BubbleGesture.Release.TAP, gesture.up())
    }
    @Test fun holdStopsOnReleaseAndCannotBecomeDrag() {
        val gesture = BubbleGesture(10f)
        gesture.down(true); assertTrue(gesture.hold()); assertFalse(gesture.move(100f))
        assertEquals(BubbleGesture.Release.STOP_HOLD, gesture.up())
        assertFalse(gesture.hold()); assertEquals(BubbleGesture.Release.NONE, gesture.up())
    }
    @Test fun draggingSuppressesHoldAndTap() {
        val gesture = BubbleGesture(10f)
        gesture.down(true); assertFalse(gesture.move(5f)); assertTrue(gesture.move(20f))
        assertFalse(gesture.hold()); assertTrue(gesture.move(0f))
        assertEquals(BubbleGesture.Release.SAVE_POSITION, gesture.up())
    }
    @Test fun cancellationNeverBecomesTranscriptionOrTap() {
        val gesture = BubbleGesture(10f)
        gesture.down(true); gesture.hold(); assertTrue(gesture.cancel())
        assertEquals(BubbleGesture.Release.NONE, gesture.up()); assertFalse(gesture.hold())
        gesture.down(true); assertFalse(gesture.cancel()); assertEquals(BubbleGesture.Release.NONE, gesture.up())
    }
}
