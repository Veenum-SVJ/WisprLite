package com.edib.openwhispr

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProviderProfilesIntegrationTest {
    @Test fun partialMetadataRestorePreservesConfiguredProviderAndKey() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext.createDeviceProtectedStorageContext()
        val prefs = context.getSharedPreferences("openwhispr", 0)
        val a = ProviderProfile("e".repeat(32), "Existing", "https://one.example/v1", "speech", "cleanup")
        val b = a.copy(id = "f".repeat(32), name = "Imported", baseUrl = "https://two.example/v1")
        try {
            prefs.edit().clear().commit()
            val store = ProviderProfiles(context)
            store.save(a, "synthetic-existing-profile-key")
            store.activateSpeech(a.id)
            val incoming = mapOf<String, Any>(ProviderProfiles.METADATA_KEY to ProviderProfileCodec.encode(listOf(b)))
            ProviderProfiles.locked {
                val prepared = ProviderProfileRestore.prepare(prefs.all, incoming, incoming.keys, emptySet())
                prefs.edit().putString(ProviderProfiles.METADATA_KEY, prepared.getValue(ProviderProfiles.METADATA_KEY) as String).commit()
            }
            assertEquals(setOf(a.id, b.id), store.all().map { it.id }.toSet())
            assertEquals(a.baseUrl, store.speechSelection().config.baseUrl)
            assertEquals("synthetic-existing-profile-key", store.speechSelection().key)
            assertThrows(IllegalArgumentException::class.java) { store.activateSpeech(b.id) }
        } finally {
            SecureKeyStorage.saveProfileApiKey(context, a.id, "")
            SecureKeyStorage.saveProfileApiKey(context, b.id, "")
            prefs.edit().clear().commit()
        }
    }
    @Test fun encryptedKeysAndIndependentSelectionsSurviveReopening() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext.createDeviceProtectedStorageContext()
        val prefs = context.getSharedPreferences("openwhispr", Context.MODE_PRIVATE)
        val a = ProviderProfile("a".repeat(32), "Groq test", "https://api.groq.com/openai/v1", "whisper-large-v3", "cleanup-a")
        val b = ProviderProfile("b".repeat(32), "Google test", "https://generativelanguage.googleapis.com/v1beta/openai", "gemini-3.8-flash", "gemini-3.8-flash", SpeechApiFormat.AUDIO_CHAT)
        try {
            prefs.edit().clear().putBoolean("use_local", true).commit()
            val store = ProviderProfiles(context)
            store.save(a, "synthetic-groq-profile-key")
            store.save(b, "synthetic-google-profile-key")
            assertNull(store.activeSpeechId())
            assertNull(store.activeCleanupId())
            store.activateSpeech(a.id)
            store.activateCleanup(b.id)
            val reopened = ProviderProfiles(context)
            assertEquals(a.baseUrl, reopened.speechSelection().config.baseUrl)
            assertEquals("synthetic-groq-profile-key", reopened.speechSelection().key)
            assertEquals(b.cleanupModel, reopened.cleanupSelection().config.model)
            assertEquals("synthetic-google-profile-key", reopened.cleanupSelection().key)
            assertTrue("Selecting credentials must not enable cloud recording", prefs.getBoolean("use_local", false))
            store.activateSpeech(b.id)
            assertEquals(SpeechApiFormat.AUDIO_CHAT, reopened.speechSelection().config.apiFormat)
            assertEquals("synthetic-google-profile-key", reopened.speechSelection().key)
            assertEquals(b.id, reopened.activeCleanupId())
            val metadata = prefs.getString(ProviderProfiles.METADATA_KEY, "").orEmpty()
            val encrypted = context.getSharedPreferences("openwhispr_secure", 0).all.values.joinToString()
            assertFalse(metadata.contains("synthetic-"))
            assertFalse(encrypted.contains("synthetic-"))
            val snapshot = SettingsBackup.Snapshot(mapOf(ProviderProfiles.METADATA_KEY to metadata), reopened.exportKeys())
            val envelope = SettingsBackup.encrypt(snapshot, "synthetic profile backup")
            assertFalse(envelope.contains("synthetic-google-profile-key"))
            assertEquals(snapshot, SettingsBackup.decrypt(envelope, "synthetic profile backup"))
            store.delete(a.id)
            assertEquals("", SecureKeyStorage.profileApiKey(context, a.id))
            assertEquals("synthetic-google-profile-key", reopened.cleanupSelection().key)
            assertThrows(IllegalArgumentException::class.java) { store.delete(b.id) }
        } finally {
            SecureKeyStorage.saveProfileApiKey(context, a.id, "")
            SecureKeyStorage.saveProfileApiKey(context, b.id, "")
            prefs.edit().clear().commit()
        }
    }
    @Test fun missingProfileOrCredentialNeverFallsBackToAnotherProvider() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext.createDeviceProtectedStorageContext()
        val prefs = context.getSharedPreferences("openwhispr", 0)
        val id = "c".repeat(32)
        try {
            prefs.edit().clear().commit()
            SecureKeyStorage.saveGroqApiKey(context, "synthetic-legacy-key")
            val store = ProviderProfiles(context)
            store.save(ProviderProfile(id, "No key", "https://example.com/v1", "speech", ""), "")
            assertThrows(IllegalArgumentException::class.java) { store.activateSpeech(id) }
            prefs.edit().putString(ProviderProfiles.SPEECH_ID, "d".repeat(32)).commit()
            assertThrows(IllegalStateException::class.java) { store.speechSelection() }
        } finally {
            SecureKeyStorage.saveProfileApiKey(context, id, "")
            SecureKeyStorage.saveGroqApiKey(context, "")
            prefs.edit().clear().commit()
        }
    }
}
