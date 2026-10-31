package com.edib.openwhispr

import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatDelegate
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class AppearanceIntegrationTest {
    private fun descendants(view: View): List<View> = buildList {
        add(view)
        if (view is ViewGroup) for (index in 0 until view.childCount) addAll(descendants(view.getChildAt(index)))
    }
    @Test fun lightAndDarkScreensRenderAndRestorePreference() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val prefs = context.getSharedPreferences("openwhispr", 0)
        val hadAppearance = prefs.contains("appearance")
        val appearance = prefs.getInt("appearance", -1)
        val hadWelcome = prefs.contains("welcome_done")
        val welcome = prefs.getBoolean("welcome_done", false)
        val hadMaster = prefs.contains("service_master_enabled")
        val master = prefs.getBoolean("service_master_enabled", true)
        val output = File(context.cacheDir, "appearance-test").apply { mkdirs() }
        prefs.edit().putInt("appearance", 1).putBoolean("welcome_done", true).commit()
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                for ((mode, name) in listOf(1 to "light", 2 to "dark")) {
                    if (mode == 2) { prefs.edit().putInt("appearance", mode).commit(); scenario.recreate() }
                    instrumentation.waitForIdleSync()
                    scenario.onActivity { activity ->
                        val expected = if (mode == 1) Configuration.UI_MODE_NIGHT_NO else Configuration.UI_MODE_NIGHT_YES
                        assertEquals(expected, activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK)
                        val root = activity.window.decorView
                        assertTrue(descendants(root).filterIsInstance<TextView>().any { it.text.toString() == "Home" })
                        fun capture(screen: String) {
                            assertTrue(root.width > 0 && root.height > 0)
                            val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
                            try { root.draw(Canvas(bitmap)); File(output, "$screen-$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } }
                            finally { bitmap.recycle() }
                        }
                        capture("home")
                        descendants(root).single { it.contentDescription == "Open navigation" }.performClick()
                    }
                    instrumentation.waitForIdleSync()
                    scenario.onActivity { activity ->
                        val root = activity.window.decorView
                        val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
                        try { root.draw(Canvas(bitmap)); File(output, "drawer-$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } }
                        finally { bitmap.recycle() }
                        descendants(root).filterIsInstance<TextView>().single { it.text.toString() == "Settings" }.performClick()
                    }
                    instrumentation.waitForIdleSync()
                    scenario.onActivity { activity ->
                        val root = activity.window.decorView
                        val labels = descendants(root).filterIsInstance<TextView>().map { it.text.toString() }
                        assertTrue(labels.any { it.startsWith("Transcription provider") })
                        val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
                        try { root.draw(Canvas(bitmap)); File(output, "settings-$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } }
                        finally { bitmap.recycle() }
                        prefs.edit().putBoolean("service_master_enabled", false).commit()
                        activity.javaClass.getDeclaredMethod("refresh").apply { isAccessible = true }.invoke(activity)
                        assertTrue("Paused dictation must be reflected in the status card", descendants(root).filterIsInstance<TextView>()
                            .any { it.text.toString() == "Dictation is paused" })
                        prefs.edit().putBoolean("service_master_enabled", master).commit()
                        activity.onBackPressedDispatcher.onBackPressed()
                    }
                }
            }
        } finally {
            val edit = prefs.edit()
            if (hadAppearance) edit.putInt("appearance", appearance) else edit.remove("appearance")
            if (hadWelcome) edit.putBoolean("welcome_done", welcome) else edit.remove("welcome_done")
            if (hadMaster) edit.putBoolean("service_master_enabled", master) else edit.remove("service_master_enabled")
            edit.commit()
            instrumentation.runOnMainSync { AppCompatDelegate.setDefaultNightMode(appearance) }
        }
    }
}
