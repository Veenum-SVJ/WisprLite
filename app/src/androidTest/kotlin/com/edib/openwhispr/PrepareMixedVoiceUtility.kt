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
    @Test fun restorePreviousLanguageAfterTrial() {
        require(InstrumentationRegistry.getArguments().getString("confirmedSetup") == "restore_mixed_voice_language")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = context.getSharedPreferences("openwhispr", 0)
        val file = File(context.cacheDir, "mixed-voice-trial-language.json")
        val saved = JSONObject(file.readText(Charsets.UTF_8))
        val previous = saved.optString("profile")
        require(prefs.getString("language_profile", null) in setOf(DictationLanguage.AUTO.name, previous)) {
            "Do not overwrite a different language selected by the user after the trial"
        }
        val edit = prefs.edit()
        if (saved.getBoolean("had_profile")) edit.putString("language_profile", previous) else edit.remove("language_profile")
        if (saved.getBoolean("had_legacy")) {
            val values = saved.getJSONArray("legacy")
            edit.putStringSet(TranscriptionLanguages.PREF_KEY, (0 until values.length()).map { values.getString(it) }.toSet())
        } else edit.remove(TranscriptionLanguages.PREF_KEY)
        assertTrue(edit.commit())
        assertEquals(previous, prefs.getString("language_profile", null))
        assertTrue(file.delete())
        println("MIXED_VOICE_LANGUAGE_RESTORED: provider/model/cleanup settings unchanged")
    }
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
