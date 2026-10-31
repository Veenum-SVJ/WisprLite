package com.edib.openwhispr

import android.view.View
import android.view.ViewGroup
import android.widget.TextView
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
                }
            }
        } finally {
            if (hadWelcome) prefs.edit().putBoolean("welcome_done", welcome).commit()
            else prefs.edit().remove("welcome_done").commit()
        }
    }
}
