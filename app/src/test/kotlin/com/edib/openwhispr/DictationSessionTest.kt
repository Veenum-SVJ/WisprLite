package com.edib.openwhispr

import org.junit.Assert.*
import org.junit.Test

class DictationSessionTest {
    @Test fun `cancelled work cannot start another provider request`() {
        val session = DictationSession()
        val job = session.begin()
        var starts = 0
        session.runIfCurrent(job) { starts++ }
        session.cancel()
        session.runIfCurrent(job) { starts++ }
        assertEquals(1, starts)
    }
    @Test fun `cancelled and superseded jobs cannot deliver results`() {
        val session = DictationSession()
        val old = session.begin()
        assertTrue(session.isCurrent(old))
        session.cancel()
        assertFalse(session.isCurrent(old))
        val next = session.begin()
        assertFalse(session.isCurrent(old))
        assertTrue(session.isCurrent(next))
        session.cancel()
        assertFalse(session.isCurrent(next))
    }
}
