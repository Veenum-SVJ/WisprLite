package com.edib.openwhispr

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.text.Normalizer
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Explicit live-provider trial. No personal history, dictionary or audio is read. */
@RunWith(AndroidJUnit4::class)
class ConfiguredCloudProviderIntegrationTest {
    @Before fun requireExplicitCloudTrial() {
        require(InstrumentationRegistry.getArguments().getString("confirmedCloudTrial") == "synthetic_groq") {
            "Use test-device.ps1 -IncludeGroqCloudFixture to authorize synthetic live-provider requests"
        }
    }
    @Test fun configuredGroqTranscribesSyntheticEnglish() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val selected = ProviderProfiles(context).speechSelection()
        assertEquals("Select the configured Groq speech profile", "api.groq.com", selected.config.transcriptionUrl().host)
        assertTrue("Configure the provider key in the app", selected.key.isNotBlank())
        val fixture = File(context.filesDir, "english-test.wav")
        assertTrue("Provision the known synthetic English recording", fixture.isFile)
        val completed = CountDownLatch(1)
        val response = AtomicReference<TranscriberClient.Result>()
        val started = System.nanoTime()
        val call = TranscriberClient.transcribe(fixture.readBytes(), selected.key, emptyList(), setOf("en"),
            selected.config.copy(recognitionHint = "The attached recording is an English dictation.")) {
            response.set(it); completed.countDown()
        }
        try {
            assertTrue("Configured speech provider timed out", completed.await(60, TimeUnit.SECONDS))
            val result = requireNotNull(response.get())
            assertTrue("Configured speech request failed; provider details are withheld", result.error == null)
            val text = requireNotNull(result.text).lowercase(Locale.ROOT)
            for (word in listOf("english", "document", "tomorrow")) assertTrue("Synthetic transcript is missing $word", word in text)
            println("LIVE_GROQ_SPEECH_VERIFIED: synthetic English, ${TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)}ms")
        } finally { call?.cancel() }
    }

    @Test fun configuredGroqCleanupPreservesSyntheticMixedLanguageText() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val selected = ProviderProfiles(context).cleanupSelection()
        assertEquals("Select the configured Groq cleanup profile", "api.groq.com", selected.config.chatCompletionsUrl().host)
        assertTrue("Configure the cleanup provider key in the app", selected.key.isNotBlank())
        val original = "Ẹ káàárọ̀ Ṣọlá, abeg send the document tomorrow."
        val completed = CountDownLatch(1)
        val response = AtomicReference<PostProcessor.Result>()
        val started = System.nanoTime()
        val call = PostProcessor.process(original, PostProcessor.effectivePrompt("", emptyList()), selected.key, selected.config) {
            response.set(it); completed.countDown()
        }
        try {
            assertTrue("Configured cleanup provider timed out", completed.await(60, TimeUnit.SECONDS))
            val result = requireNotNull(response.get())
            assertTrue("Configured cleanup request failed; provider details are withheld", result.error == null)
            val cleaned = requireNotNull(result.text)
            assertFalse("Cleanup rewrote the synthetic input", PostProcessor.looksGenerated(original, cleaned))
            val normalized = Normalizer.normalize(cleaned, Normalizer.Form.NFC).lowercase(Locale.ROOT)
            for (word in listOf("ẹ", "káàárọ̀", "ṣọlá", "abeg", "document", "tomorrow")) {
                assertTrue("Cleanup did not preserve the expected mixed-language word", word in normalized)
            }
            println("LIVE_GROQ_CLEANUP_VERIFIED: synthetic mixed-language preservation, ${TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)}ms")
        } finally { call?.cancel() }
    }
}
