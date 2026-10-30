package com.edib.openwhispr

import org.junit.Assert.*
import org.junit.Test

class InsightCalculatorTest {
    @Test fun `estimates net typing time saved`() {
        assertEquals(90.0, InsightCalculator.savedSeconds(80, 30000, 40), 0.001)
        assertEquals(0.0, InsightCalculator.savedSeconds(1, 30000, 40), 0.001)
    }
    @Test fun `counts Unicode words without dropping Yoruba marks`() {
        assertEquals(6, InsightCalculator.wordCount("Abeg send ẹ, mo máa check."))
        assertEquals(0, InsightCalculator.wordCount("   \n"))
    }
    @Test fun `rejects invalid typing speed`() {
        assertThrows(IllegalArgumentException::class.java) { InsightCalculator.savedSeconds(80, 30000, 0) }
    }
}
