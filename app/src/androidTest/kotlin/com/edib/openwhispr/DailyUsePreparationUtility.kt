package com.edib.openwhispr

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Explicit user-requested handoff cleanup. Never included in normal regression runs. */
@RunWith(AndroidJUnit4::class)
class DailyUsePreparationUtility {
    @Test fun prepareEnglishAndRemoveOwnedTestArtifacts() {
        require(InstrumentationRegistry.getArguments().getString("confirmedSetup") == "english_daily_use_cleanup")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = context.getSharedPreferences("openwhispr", 0)
        val profilesBefore = ProviderProfiles(context).all()
        val installedBefore = MODEL_CATALOG.filter { ModelDownloader.isInstalled(context, it) }.map { it.archive }
        val removed = mutableListOf<String>()
        fun removeOwned(root: File, name: String) {
            val target = File(root, name).canonicalFile
            check(target.parentFile == root.canonicalFile)
            if (target.exists()) { check(target.deleteRecursively()); removed += name }
        }
        for (name in listOf("appearance-test", "bubble-preview", "model-picker-test", "public-corpus-eval", "lyngual-eval-pinned")) {
            removeOwned(context.cacheDir, name)
        }
        for (name in listOf("mixed-voice-trial-language.json", "wisprlite-backup-test.owb")) removeOwned(context.cacheDir, name)
        context.cacheDir.listFiles().orEmpty().filter {
            it.name.matches(Regex("(?:foreground-ready-|offline-ready-)[0-9a-f]{32}\\.tmp")) ||
                it.name.matches(Regex("(?:retention-test-|download-space-test-)[0-9a-f-]{36}"))
        }.forEach { removeOwned(context.cacheDir, it.name) }
        for (name in listOf("english-test.wav", "speech-model-test.tar.bz2", "speech-model-small-test.tar.bz2") +
            MODEL_CATALOG.map { "${it.archive}-test.tar.bz2" }) removeOwned(context.filesDir, name)
        context.deleteDatabase("wisprlite-instrumentation.db")
        val protected = context.createDeviceProtectedStorageContext()
        for (name in listOf("openwhispr", "openwhispr_secure", "language-profile-test")) protected.deleteSharedPreferences(name)
        removeOwned(protected.cacheDir, "wisprlite-backup-test.owb")
        // Remove only the known fixed-phrase voice trial, never a broad history range.
        var trialRows = 0
        HistoryDatabase(context).use { helper ->
            val db = helper.writableDatabase
            val timestamp = java.time.Instant.parse("2026-11-01T12:48:00.858Z").toEpochMilli()
            val ids = db.rawQuery("SELECT id FROM transcripts WHERE created_at=? AND speaking_ms=5520 AND length(original)=45 AND original LIKE ?",
                arrayOf(timestamp.toString(), "%please send the document tomorrow%")).use { cursor ->
                buildList { while (cursor.moveToNext()) add(cursor.getString(0)) }
            }
            db.beginTransaction()
            try {
                ids.forEach { id -> db.delete("transcripts", "id=?", arrayOf(id)); db.delete("events", "id=?", arrayOf(id)); trialRows++ }
                db.setTransactionSuccessful()
            } finally { db.endTransaction() }
        }
        val edit = prefs.edit().putString("language_profile", "ENGLISH")
            .putStringSet(TranscriptionLanguages.PREF_KEY, setOf("en")).putBoolean("service_master_enabled", true)
        val englishModels = MODEL_CATALOG.filter { it.archive in installedBefore && "en" in it.languages }
        val speech = ProviderProfiles(context).speechSelection()
        if (speech.key.isBlank() && englishModels.isNotEmpty()) edit.putBoolean("use_local", true)
        if (prefs.getString("model_name", null) !in englishModels.map { it.archive } && englishModels.isNotEmpty()) {
            edit.putString("model_name", englishModels.first().archive)
        }
        if (ProviderProfiles(context).cleanupSelection().key.isBlank()) edit.putBoolean("use_post_processing", false)
        check(edit.commit())
        assertEquals(profilesBefore, ProviderProfiles(context).all())
        assertEquals(installedBefore, MODEL_CATALOG.filter { ModelDownloader.isInstalled(context, it) }.map { it.archive })
        val report = JSONObject().put("removed_fixture_names", JSONArray(removed)).put("removed_fixed_trial_rows", trialRows)
            .put("language", "ENGLISH").put("installed_models", JSONArray(installedBefore))
            .put("saved_provider_count", profilesBefore.size).put("local_mode", prefs.getBoolean("use_local", true))
            .put("selected_model", prefs.getString("model_name", null)).put("profiles_and_models_preserved", true)
        File(context.cacheDir, "daily-use-preparation.json").writeText(report.toString(), Charsets.UTF_8)
        println("DAILY_USE_PREPARATION_PASS: English selected; owned fixtures removed; profiles/models preserved")
    }
}
