package com.edib.openwhispr

import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.EditText
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StartupNavigationTest {
    private fun descendants(view: View): List<View> = buildList {
        add(view)
        if (view is ViewGroup) for (index in 0 until view.childCount) addAll(descendants(view.getChildAt(index)))
    }
    @Test fun homeStartsAndDrawerExposesRequiredDestinations() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val prefs = context.getSharedPreferences("openwhispr", 0)
        val hadWelcome = prefs.contains("welcome_done")
        val welcome = prefs.getBoolean("welcome_done", false)
        prefs.edit().putBoolean("welcome_done", true).commit()
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    val root = activity.window.decorView
                    assertTrue(descendants(root).filterIsInstance<TextView>().any { it.text.toString() == "Home" })
                    descendants(root).single { it.contentDescription == "Open navigation" }.performClick()
                    val labels = descendants(root).filterIsInstance<TextView>().map { it.text.toString() }
                    for (label in listOf("Home", "Insights", "Dictionary", "Settings")) assertTrue(label, label in labels)
                    activity.onBackPressedDispatcher.onBackPressed()
                    assertFalse(descendants(root).filterIsInstance<TextView>().any { it.text.toString() == "Settings" })
                    descendants(root).single { it.contentDescription == "Open navigation" }.performClick()
                    descendants(root).filterIsInstance<TextView>().single { it.text.toString() == "Dictionary" }.performClick()
                    root.findViewWithTag<EditText>("dictionary-search").setText("Ṣọlá | Shola")
                    root.findViewWithTag<View>("dictionary-tab-Snippets").performClick()
                }
                scenario.recreate()
                scenario.onActivity { activity ->
                    val root = activity.window.decorView
                    assertEquals("Ṣọlá | Shola", root.findViewWithTag<EditText>("dictionary-search").text.toString())
                    assertTrue(root.findViewWithTag<View>("dictionary-tab-Snippets").isSelected)
                    assertTrue(descendants(root).filterIsInstance<TextView>().any { it.text.toString() == "Dictionary" })
                    activity.onBackPressedDispatcher.onBackPressed()
                    assertTrue(descendants(root).filterIsInstance<TextView>().any { it.text.toString() == "Home" })
                    descendants(root).single { it.contentDescription == "Open navigation" }.performClick()
                    descendants(root).filterIsInstance<TextView>().single { it.text.toString() == "Insights" }.performClick()
                }
                val totals = HistoryRepository(context).use { it.totals(0) }
                var insightsLoaded = false
                val deadline = System.currentTimeMillis() + 5_000
                while (!insightsLoaded && System.currentTimeMillis() < deadline) {
                    scenario.onActivity { activity ->
                        val panel = activity.window.decorView.findViewWithTag<View>("insights-summary")
                        val labels = panel?.let { descendants(it).filterIsInstance<TextView>().map { view -> view.text.toString() } }.orEmpty()
                        insightsLoaded = totals.words.toString() in labels && "Recognized words" in labels &&
                            totals.recordings.toString() in labels && "Recordings" in labels
                    }
                    if (!insightsLoaded) Thread.sleep(100)
                }
                assertTrue("Insights did not render the persisted all-time totals", insightsLoaded)
                scenario.onActivity { activity ->
                    val root = activity.window.decorView
                    descendants(root).single { it.contentDescription == "Open navigation" }.performClick()
                    descendants(root).filterIsInstance<TextView>().single { it.text.toString() == "Settings" }.performClick()
                    val labels = descendants(root).filterIsInstance<TextView>().map { it.text.toString() }
                    for (label in listOf("Preferences", "Tone & Style", "Speech-to-Text", "Language Models", "Privacy & Data", "System")) assertTrue(label, label in labels)
                    root.findViewWithTag<View>("settings-open-PRIVACY").performClick()
                    assertTrue(descendants(root).filterIsInstance<TextView>().any { it.text.toString() == "Keep audio" })
                    activity.onBackPressedDispatcher.onBackPressed()
                    assertEquals(View.VISIBLE, root.findViewWithTag<View>("settings-index").visibility)
                    activity.onBackPressedDispatcher.onBackPressed()
                    assertTrue(descendants(root).filterIsInstance<TextView>().any { it.text.toString() == "Home" })
                }
            }
        } finally {
            if (hadWelcome) prefs.edit().putBoolean("welcome_done", welcome).commit()
            else prefs.edit().remove("welcome_done").commit()
        }
    }
}
