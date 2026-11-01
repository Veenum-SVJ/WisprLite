package com.edib.openwhispr

import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LanguageSelectionIntegrationTest {
    private fun descendants(view: View): List<View> = buildList {
        add(view)
        if (view is ViewGroup) for (i in 0 until view.childCount) addAll(descendants(view.getChildAt(i)))
    }
    @Test fun allLanguageChoicesImmediatelyUpdateTheVisibleSettingsRow() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = context.getSharedPreferences("openwhispr", 0)
        val keys = setOf("language_profile", TranscriptionLanguages.PREF_KEY, "use_local", "welcome_done")
        val original = prefs.all.filterKeys { it in keys }
        try {
            prefs.edit().putString("language_profile", "YORUBA").putBoolean("use_local", false).putBoolean("welcome_done", true).commit()
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    val navigation = activity.javaClass.getDeclaredField("navigation").apply { isAccessible = true }.get(activity) as WisprNavigation
                    navigation.openSettings()
                    activity.window.decorView.findViewWithTag<View>("settings-open-PREFERENCES").performClick()
                    fun displayed(): String {
                        val row = activity.window.decorView.findViewWithTag<View>("settings-language")
                        return row.findViewWithTag<TextView>("setting-subtitle").text.toString()
                    }
                    assertEquals("Auto-detect", displayed())
                    for (profile in listOf(DictationLanguage.ENGLISH, DictationLanguage.AUTO)) {
                        activity.selectLanguageProfile(profile)
                        assertEquals(profile.name, prefs.getString("language_profile", null))
                        assertEquals(profile.languageCode.orEmpty(), TranscriptionLanguages.localWhisperLanguage(prefs))
                        assertEquals("Visible language must follow the actual saved selection", profile.title, displayed())
                    }
                    navigation.refresh()
                    assertEquals("Auto-detect", displayed())
                }
            }
        } finally {
            prefs.edit().apply {
                keys.forEach { remove(it) }
                original.forEach { (key, value) -> when (value) {
                    is String -> putString(key, value)
                    is Boolean -> putBoolean(key, value)
                    is Set<*> -> putStringSet(key, value.map { it.toString() }.toSet())
                } }
            }.commit()
        }
    }
}
