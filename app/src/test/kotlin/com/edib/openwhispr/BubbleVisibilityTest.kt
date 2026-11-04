package com.edib.openwhispr

import org.junit.Assert.*
import org.junit.Test

class BubbleVisibilityTest {
    @Test fun `idle bubble requires a current typing surface`() {
        assertFalse(BubbleVisibility.shouldShow(true, false, false, false))
        assertTrue(BubbleVisibility.shouldShow(true, false, true, false))
        assertTrue(BubbleVisibility.shouldShow(true, false, false, true))
    }
    @Test fun `recording and processing remain reachable after keyboard dismissal`() {
        assertTrue(BubbleVisibility.shouldShow(true, true, false, false))
        assertFalse(BubbleVisibility.shouldShow(false, true, true, true))
    }
}
