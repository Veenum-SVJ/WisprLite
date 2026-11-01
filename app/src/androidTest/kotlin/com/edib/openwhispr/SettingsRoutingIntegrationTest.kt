package com.edib.openwhispr

import android.view.View
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsRoutingIntegrationTest {
    @Test fun toneSwitchPersistsWithoutEnablingCleanupAndShowsCategoryDefaults() {
        val prefs = InstrumentationRegistry.getInstrumentation().targetContext.getSharedPreferences("openwhispr", 0)
        val keys = listOf("welcome_done", "use_post_processing", ToneRules.KEY)
        val previous = prefs.all.filterKeys { it in keys }
        prefs.edit().putBoolean("welcome_done", true).putBoolean("use_post_processing", false).remove(ToneRules.KEY).commit()
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario -> scenario.onActivity { activity ->
                val ui = ToneStyleSettings(activity); ui.refresh()
                val toggle = ui.view.findViewWithTag<com.google.android.material.materialswitch.MaterialSwitch>("tone-enabled")
                assertFalse(toggle.isChecked); toggle.isChecked = true
                assertTrue(ToneRules(prefs).configuration().enabled)
                assertFalse("App tones must never turn on cloud cleanup", prefs.getBoolean("use_post_processing", true))
                ToneCategory.entries.forEach { category ->
                    val row = ui.view.findViewWithTag<View>("tone-category-${category.name}")
                    assertTrue(row.findViewWithTag<TextView>("setting-subtitle").text.toString().contains(category.defaultTone.title))
                }
                assertTrue(ui.view.findViewWithTag<View>("tone-add-app").isClickable)
            } }
        } finally {
            prefs.edit().apply { keys.forEach { remove(it) }; previous.forEach { (key, value) -> when(value) { is String -> putString(key, value); is Boolean -> putBoolean(key, value) } } }.commit()
        }
    }
    @Test fun detailSurvivesRefreshAndRecreationThenBackReturnsToIndex() {
        val prefs = InstrumentationRegistry.getInstrumentation().targetContext.getSharedPreferences("openwhispr", 0)
        val welcome = prefs.all["welcome_done"]
        prefs.edit().putBoolean("welcome_done", true).commit()
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    val navigation = activity.javaClass.getDeclaredField("navigation").apply { isAccessible = true }.get(activity) as WisprNavigation
                    navigation.openSettings()
                    activity.window.decorView.findViewWithTag<View>("settings-open-PREFERENCES").performClick()
                    activity.refreshSettings(); navigation.refresh()
                    assertEquals("Preferences", activity.window.decorView.findViewWithTag<TextView>("settings-detail-title").text.toString())
                }
                scenario.recreate()
                scenario.onActivity { activity ->
                    val root = activity.window.decorView
                    assertEquals("Preferences", root.findViewWithTag<TextView>("settings-detail-title").text.toString())
                    assertEquals(View.GONE, root.findViewWithTag<View>("settings-index").visibility)
                    activity.onBackPressedDispatcher.onBackPressed()
                    assertEquals(View.VISIBLE, root.findViewWithTag<View>("settings-index").visibility)
                    root.findViewWithTag<View>("settings-readiness").performClick()
                    assertEquals("System", root.findViewWithTag<TextView>("settings-detail-title").text.toString())
                }
            }
        } finally { prefs.edit().apply { if (welcome == null) remove("welcome_done") else putBoolean("welcome_done", welcome as Boolean) }.commit() }
    }
    @Test fun promptResetPreservesProviderAndToneConfiguration() {
        val prefs = InstrumentationRegistry.getInstrumentation().targetContext.getSharedPreferences("openwhispr", 0)
        val keys = listOf("custom_instructions", "transcription_model", "welcome_done", ToneRules.KEY)
        val previous = prefs.all.filterKeys { it in keys }
        prefs.edit().putBoolean("welcome_done", true).putString("custom_instructions", "Prefer concise punctuation").putString("transcription_model", "saved-speech-model").commit()
        val tone = prefs.getString(ToneRules.KEY, null)
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario -> scenario.onActivity { activity ->
                val extras = activity.javaClass.getDeclaredField("settingsExtras").apply { isAccessible = true }.get(activity) as SettingsExtras
                extras.resetCustomRefinements()
                assertFalse(prefs.contains("custom_instructions"))
                assertEquals("saved-speech-model", prefs.getString("transcription_model", null))
                assertEquals(tone, prefs.getString(ToneRules.KEY, null))
                assertTrue(PostProcessor.DEFAULT_PROMPT.isNotBlank())
            } }
        } finally {
            prefs.edit().apply { keys.forEach { remove(it) }; previous.forEach { (key, value) -> when(value) { is String -> putString(key, value); is Boolean -> putBoolean(key, value) } } }.commit()
        }
    }
}
