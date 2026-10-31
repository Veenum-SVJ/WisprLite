package com.edib.openwhispr

import org.junit.Assert.*
import org.junit.Test

class ProviderConfigTest {
    @Test fun `normalizes transcription API root and endpoint`() {
        assertEquals("https://example.com/v1/audio/transcriptions", ProviderConfig("https://example.com/v1/", "speech").transcriptionUrl().toString())
        assertEquals("https://example.com/v1/audio/transcriptions", ProviderConfig("https://example.com/v1/audio/transcriptions", "speech").transcriptionUrl().toString())
    }
    @Test fun `rejects insecure and ambiguous endpoints`() {
        listOf("http://example.com/v1", "https://user:pass@example.com", "https://example.com?key=secret", "https://example.com/#fragment").forEach { url ->
            assertThrows(IllegalArgumentException::class.java) { ProviderConfig(url, "speech").transcriptionUrl() }
        }
        assertThrows(IllegalArgumentException::class.java) { ProviderConfig("https://example.com", " ").transcriptionUrl() }
    }
    @Test fun `language profiles only force supported explicit codes`() {
        assertNull(DictationLanguage.AUTO.languageCode)
        assertEquals("en", DictationLanguage.PIDGIN.languageCode)
        assertEquals("en", DictationLanguage.ENGLISH.languageCode)
        assertEquals("yo", DictationLanguage.YORUBA.languageCode)
    }
}
