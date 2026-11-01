package com.edib.openwhispr

import org.junit.Assert.*
import org.junit.Test

class ProviderConfigTest {
    @Test fun `audio chat routes to chat endpoint without inferring protocol from URL`() {
        assertEquals("https://example.com/v1/chat/completions", ProviderConfig("https://example.com/v1/", "speech", apiFormat = SpeechApiFormat.AUDIO_CHAT).transcriptionUrl().toString())
        assertEquals("https://example.com/v1/chat/completions", ProviderConfig("https://example.com/v1/chat/completions/", "speech", apiFormat = SpeechApiFormat.AUDIO_CHAT).transcriptionUrl().toString())
        assertEquals("https://example.com/v1/chat/completions", ProviderConfig("https://example.com/v1/audio/transcriptions", "speech", apiFormat = SpeechApiFormat.AUDIO_CHAT).transcriptionUrl().toString())
        assertEquals("https://example.com/v1/audio/transcriptions", ProviderConfig("https://example.com/v1/chat/completions", "speech").transcriptionUrl().toString())
    }
    @Test fun `saved speech API format is explicit and defaults to multipart`() {
        assertEquals(SpeechApiFormat.MULTIPART, SpeechApiFormat.fromSaved(null))
        assertEquals(SpeechApiFormat.MULTIPART, SpeechApiFormat.fromSaved("unknown"))
        assertEquals(SpeechApiFormat.AUDIO_CHAT, SpeechApiFormat.fromSaved("AUDIO_CHAT"))
        assertEquals(SpeechApiFormat.MULTIPART, ProviderConfig().apiFormat)
    }
    @Test fun `audio chat retains HTTPS endpoint safety checks`() {
        listOf("http://example.com/v1", "https://user:pass@example.com", "https://example.com?key=secret", "https://example.com/#fragment").forEach { url ->
            assertThrows(IllegalArgumentException::class.java) { ProviderConfig(url, "speech", apiFormat = SpeechApiFormat.AUDIO_CHAT).transcriptionUrl() }
        }
    }
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
        assertEquals("en", DictationLanguage.ENGLISH.languageCode)
        assertEquals(listOf(DictationLanguage.AUTO, DictationLanguage.ENGLISH), DictationLanguage.entries.toList())
        assertEquals(DictationLanguage.AUTO, DictationLanguage.fromSaved("YORUBA"))
        assertEquals(DictationLanguage.AUTO, DictationLanguage.fromSaved("PIDGIN"))
        assertEquals(DictationLanguage.ENGLISH, DictationLanguage.fromSaved(null))
        assertTrue(DictationLanguage.AUTO.acceptsModel(setOf("fr", "de")))
        assertEquals("", DictationLanguage.AUTO.recognitionHint)
    }
}
