package com.edib.openwhispr

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import android.content.Context

@RunWith(AndroidJUnit4::class)
class CredentialIsolationTest {
    @Test fun customTranscriptionKeyNeverBecomesGroqCleanupKey() {
        // Separate device-protected test preferences: normal app settings remain untouched.
        val context = InstrumentationRegistry.getInstrumentation().targetContext.createDeviceProtectedStorageContext()
        val prefs = context.getSharedPreferences("openwhispr", Context.MODE_PRIVATE)
        try {
            prefs.edit().putString("transcription_base_url", "https://example.com/v1").commit()
            SecureKeyStorage.saveGroqApiKey(context, "groq-test-key")
            SecureKeyStorage.saveTranscriptionApiKey(context, "custom-test-key")
            assertEquals("custom-test-key", SecureKeyStorage.transcriptionApiKey(context))
            assertEquals("groq-test-key", SecureKeyStorage.groqApiKey(context))
            SecureKeyStorage.saveTranscriptionApiKey(context, "")
            assertEquals("", SecureKeyStorage.transcriptionApiKey(context))
            prefs.edit().putString("transcription_base_url", "https://api.groq.com/openai/v1").commit()
            assertEquals("groq-test-key", SecureKeyStorage.transcriptionApiKey(context))
        } finally {
            SecureKeyStorage.saveGroqApiKey(context, "")
            SecureKeyStorage.saveTranscriptionApiKey(context, "")
            prefs.edit().clear().commit()
        }
    }
}
