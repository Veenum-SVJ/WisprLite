package com.edib.openwhispr

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Explicit one-time trial setup, excluded from regression suites and the app APK. */
@RunWith(AndroidJUnit4::class)
class PrepareMixedVoiceUtility {
    @Test fun prepareExistingCloudProviderForFixedMixedPhrase() {
        require(InstrumentationRegistry.getArguments().getString("confirmedSetup") == "mixed_voice_trial")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = context.getSharedPreferences("openwhispr", 0)
        assertFalse("Keep the existing cloud mode; do not enable uploads implicitly", prefs.getBoolean("use_local", true))
        val selected = ProviderProfiles(context).speechSelection()
        assertTrue("Configure the selected provider inside WisprLite first", selected.key.isNotBlank())
        val host = selected.config.transcriptionUrl().host
        val original = File(context.cacheDir, "mixed-voice-trial-language.json")
        assertFalse("Preserve an earlier pending trial snapshot rather than overwriting it", original.exists())
        original.writeText(JSONObject()
            .put("had_profile", prefs.contains("language_profile"))
            .put("profile", prefs.getString("language_profile", null) ?: JSONObject.NULL)
            .put("had_legacy", prefs.contains(TranscriptionLanguages.PREF_KEY))
            .put("legacy", JSONArray(prefs.getStringSet(TranscriptionLanguages.PREF_KEY, emptySet()).orEmpty().sorted()))
            .put("provider_host", host).put("model", selected.config.model)
            .put("reference", "Báwo ni? Please send the document tomorrow.").toString(), Charsets.UTF_8)
        assertTrue(prefs.edit().putString("language_profile", DictationLanguage.AUTO.name)
            .putStringSet(TranscriptionLanguages.PREF_KEY, emptySet()).commit())
        assertEquals("", TranscriptionLanguages.localWhisperLanguage(prefs))
        println("MIXED_VOICE_SETUP_READY: only language changed; original language snapshot retained")
    }
}
