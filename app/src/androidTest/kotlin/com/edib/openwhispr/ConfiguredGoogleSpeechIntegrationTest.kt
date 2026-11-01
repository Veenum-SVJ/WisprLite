package com.edib.openwhispr

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Explicit synthetic trial of a saved Google profile; never changes active selections. */
@RunWith(AndroidJUnit4::class)
class ConfiguredGoogleSpeechIntegrationTest {
    @Test fun inspectResponseStructureWithoutLoggingContentOrKeys() {
        require(InstrumentationRegistry.getArguments().getString("confirmedCloudTrial") == "synthetic_google_diagnostic")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val selected = ProviderProfiles.locked {
            val profile = ProviderProfiles(context).all().single { java.net.URI(it.baseUrl).host == "generativelanguage.googleapis.com" }
            ProviderProfiles.SpeechSelection(ProviderConfig(profile.baseUrl, profile.speechModel, apiFormat = profile.speechFormat), SecureKeyStorage.profileApiKey(context, profile.id))
        }
        val request = TranscriberClient.transcriptionRequest(File(context.filesDir, "english-test.wav").readBytes(), selected.key, emptyList(), setOf("en"), selected.config)
        try {
            okhttp3.OkHttpClient.Builder().callTimeout(60, TimeUnit.SECONDS).build().newCall(request).execute().use { response ->
                val json = runCatching { org.json.JSONObject(response.body?.string().orEmpty()) }.getOrNull()
                val choices = json?.optJSONArray("choices")
                val choice = choices?.optJSONObject(0)
                val message = choice?.optJSONObject("message")
                val content = message?.opt("content")
                val kind = when (content) { is String -> "string"; is org.json.JSONArray -> "array"; null, org.json.JSONObject.NULL -> "null"; else -> "other" }
                val finish = choice?.optString("finish_reason").orEmpty().uppercase(Locale.ROOT)
                val safeFinish = finish.takeIf { it in setOf("STOP", "LENGTH", "CONTENT_FILTER", "SAFETY", "RECITATION", "MAX_TOKENS") } ?: "other"
                val status = json?.optJSONObject("error")?.optString("status").orEmpty()
                val safeStatus = status.takeIf { it in setOf("UNAVAILABLE", "INVALID_ARGUMENT", "UNAUTHENTICATED", "PERMISSION_DENIED", "NOT_FOUND", "RESOURCE_EXHAUSTED") } ?: "other"
                println("GOOGLE_RESPONSE_STRUCTURE: http=${response.code}; json=${json != null}; choices=${choices?.length() ?: 0}; content=$kind; chars=${(content as? String)?.length ?: 0}; refusal=${message?.isNull("refusal") == false}; finish=$safeFinish; error=$safeStatus")
                assertTrue("Google returned a non-success status; safe metadata recorded", response.isSuccessful)
            }
        } catch (_: java.io.IOException) { fail("Google request could not complete; no request or credential details logged") }
    }

    @Test fun savedGoogleProfileTranscribesSyntheticEnglish() {
        require(InstrumentationRegistry.getArguments().getString("confirmedCloudTrial") == "synthetic_google")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val selected = ProviderProfiles.locked {
            val profile = ProviderProfiles(context).all().single { java.net.URI(it.baseUrl).host == "generativelanguage.googleapis.com" }
            assertEquals("Google speech requires audio chat format", SpeechApiFormat.AUDIO_CHAT, profile.speechFormat)
            ProviderProfiles.SpeechSelection(ProviderConfig(profile.baseUrl, profile.speechModel, apiFormat = profile.speechFormat), SecureKeyStorage.profileApiKey(context, profile.id))
        }
        assertTrue("Configure the saved Google key in the app", selected.key.isNotBlank())
        val fixture = File(context.filesDir, "english-test.wav")
        assertTrue("Provision the known synthetic speech fixture", fixture.isFile)
        val completed = CountDownLatch(1)
        val response = AtomicReference<TranscriberClient.Result>()
        val started = System.nanoTime()
        val call = TranscriberClient.transcribe(fixture.readBytes(), selected.key, emptyList(), setOf("en"), selected.config) {
            response.set(it); completed.countDown()
        }
        try {
            assertTrue("Google speech request timed out", completed.await(60, TimeUnit.SECONDS))
            val result = requireNotNull(response.get())
            val status = result.error?.takeIf { it.matches(Regex("Transcription provider returned HTTP [0-9]{3}")) } ?: "details withheld"
            assertTrue("Google speech request failed: $status", result.error == null)
            val text = requireNotNull(result.text).lowercase(Locale.ROOT)
            for (word in listOf("english", "document", "tomorrow")) assertTrue("Synthetic transcript is missing $word", word in text)
            println("LIVE_GOOGLE_SPEECH_VERIFIED: synthetic English, ${TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)}ms; active providers unchanged")
        } finally { call?.cancel() }
    }
}
