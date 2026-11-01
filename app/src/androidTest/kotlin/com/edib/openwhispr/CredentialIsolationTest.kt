package com.edib.openwhispr

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import android.content.Context

@RunWith(AndroidJUnit4::class)
class CredentialIsolationTest {
    @Test fun explicitLanguageProfileOverridesStaleLegacySelection() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext.createDeviceProtectedStorageContext()
        val prefs = context.getSharedPreferences("language-profile-test", Context.MODE_PRIVATE)
        try {
            prefs.edit().putStringSet(TranscriptionLanguages.PREF_KEY, setOf("zh"))
                .putString("language_profile", "YORUBA").commit()
            assertEquals(emptySet<String>(), TranscriptionLanguages.saved(prefs))
            assertEquals("", TranscriptionLanguages.localWhisperLanguage(prefs))
            prefs.edit().putString("language_profile", "PIDGIN").commit()
            assertEquals(emptySet<String>(), TranscriptionLanguages.saved(prefs))
            assertEquals("", TranscriptionLanguages.localWhisperLanguage(prefs))
            prefs.edit().putString("language_profile", "AUTO").commit()
            assertTrue(TranscriptionLanguages.saved(prefs).isEmpty())
            assertEquals("", TranscriptionLanguages.localWhisperLanguage(prefs))
            prefs.edit().remove("language_profile").commit()
            assertEquals(setOf("en"), TranscriptionLanguages.saved(prefs))
        } finally { prefs.edit().clear().commit() }
    }
    @Test fun encryptedBackupRoundTripRestoresIndependentKeysOnDevice() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext.createDeviceProtectedStorageContext()
        val file = java.io.File(context.cacheDir, "wisprlite-backup-test.owb")
        val passphrase = "synthetic backup test passphrase"
        val snapshot = SettingsBackup.Snapshot(
            mapOf("language_profile" to "YORUBA", "dictionary" to "Ṣọlá\nẸ káàárọ̀", "typing_wpm" to 40),
            mapOf("transcription_api_key" to "synthetic-transcription", "groq_api_key" to "synthetic-groq", "cleanup_api_key" to "synthetic-cleanup"))
        try {
            file.writeText(SettingsBackup.encrypt(snapshot, passphrase))
            assertFalse(file.readText().contains("synthetic-transcription"))
            assertFalse(file.readText().contains("Ṣọlá"))
            assertThrows(SettingsBackup.BackupException::class.java) { SettingsBackup.decrypt(file.readText(), "incorrect passphrase") }
            val restored = SettingsBackup.decrypt(file.readText(), passphrase)
            assertEquals(snapshot, restored)
            SecureKeyStorage.saveTranscriptionApiKey(context, restored.secrets.getValue("transcription_api_key"))
            SecureKeyStorage.saveGroqApiKey(context, restored.secrets.getValue("groq_api_key"))
            SecureKeyStorage.saveCleanupApiKey(context, restored.secrets.getValue("cleanup_api_key"))
            assertEquals("synthetic-transcription", SecureKeyStorage.transcriptionApiKey(context))
            assertEquals("synthetic-groq", SecureKeyStorage.groqApiKey(context))
            assertEquals("synthetic-cleanup", SecureKeyStorage.cleanupApiKey(context))
        } finally {
            file.delete()
            SecureKeyStorage.saveTranscriptionApiKey(context, "")
            SecureKeyStorage.saveGroqApiKey(context, "")
            SecureKeyStorage.saveCleanupApiKey(context, "")
            context.getSharedPreferences("openwhispr", Context.MODE_PRIVATE).edit().clear().commit()
        }
    }
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
