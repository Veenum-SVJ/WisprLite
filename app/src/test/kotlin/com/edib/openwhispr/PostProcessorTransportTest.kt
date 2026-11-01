package com.edib.openwhispr

import org.junit.Assert.*
import org.junit.Test

class PostProcessorTransportTest {
    @Test fun httpErrorsNeverExposeProviderBodyOrCredentials() {
        val result = PostProcessor.transportResponse(401, """{"error":{"message":"Rejected synthetic-secret-key"}}""")
        assertNull(result.text)
        assertEquals("Cleanup provider returned HTTP 401", result.error)
        assertFalse(result.error.orEmpty().contains("synthetic-secret-key"))
    }
    @Test fun invalidSuccessBodiesNeverExposeParserInput() {
        for (body in listOf("synthetic-secret-key invalid JSON", """{"error":{"message":"synthetic-secret-key"}}""")) {
            val result = PostProcessor.transportResponse(200, body)
            assertNull(result.text)
            assertEquals("Invalid cleanup response", result.error)
        }
    }
    @Test fun validCleanupKeepsUnicodeAndStripsOnlyWrapperTags() {
        val result = PostProcessor.transportResponse(200, """{"choices":[{"message":{"content":"<transcript>Ẹ káàárọ̀, abeg send am.</transcript>"}}]}""")
        assertNull(result.error)
        assertEquals("Ẹ káàárọ̀, abeg send am.", result.text)
    }
}
