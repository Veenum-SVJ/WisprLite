package com.edib.openwhispr

import org.junit.Assert.*
import org.junit.Test

class ProviderProfileTest {
    private val groq = ProviderProfile("a".repeat(32), "Groq", "https://api.groq.com/openai/v1", "whisper-large-v3", "openai/gpt-oss-120b")
    @Test fun metadataRoundTripPreservesSeparateModelsAndFormats() {
        val google = ProviderProfile("b".repeat(32), "Google", "https://generativelanguage.googleapis.com/v1beta/openai", "gemini-3.8-flash", "gemini-3.8-flash", SpeechApiFormat.AUDIO_CHAT)
        assertEquals(listOf(groq, google), ProviderProfileCodec.decode(ProviderProfileCodec.encode(listOf(groq, google))))
    }
    @Test fun duplicateIdentifiersAndUnknownFormatsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { ProviderProfileCodec.encode(listOf(groq, groq.copy(name = "Other"))) }
        val metadata = ProviderProfileCodec.encode(listOf(groq)).replace("MULTIPART", "UNKNOWN")
        assertThrows(IllegalArgumentException::class.java) { ProviderProfileCodec.decode(metadata) }
    }
    @Test fun unsafeRoutesAndEmptyCapabilitiesAreRejected() {
        for (url in listOf("http://example.com", "https://key@example.com", "https://example.com?key=secret", "https://example.com#secret")) {
            assertThrows(IllegalArgumentException::class.java) { groq.copy(baseUrl = url).validate() }
        }
        assertThrows(IllegalArgumentException::class.java) { groq.copy(speechModel = "", cleanupModel = "").validate() }
        assertThrows(IllegalArgumentException::class.java) { groq.copy(id = "../key").validate() }
    }
    @Test fun cleanupOnlyAndSpeechOnlyProfilesKeepTheirCapabilities() {
        groq.copy(speechModel = "").validate()
        groq.copy(cleanupModel = "").validate()
        assertThrows(IllegalArgumentException::class.java) { groq.copy(name = " ").validate() }
    }
}
