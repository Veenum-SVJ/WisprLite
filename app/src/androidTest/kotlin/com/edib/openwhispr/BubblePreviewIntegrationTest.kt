package com.edib.openwhispr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.material.button.MaterialButton
import com.google.android.material.slider.Slider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BubblePreviewIntegrationTest {
    private fun descendants(view: View): List<View> = buildList {
        add(view)
        if (view is ViewGroup) for (index in 0 until view.childCount) addAll(descendants(view.getChildAt(index)))
    }
    @Test fun previewTracksSizeLanguageLivesInSettingsAndDrawerOpensOnRight() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val prefs = context.getSharedPreferences("openwhispr", Context.MODE_PRIVATE)
        val hadWelcome = prefs.contains("welcome_done")
        val welcome = prefs.getBoolean("welcome_done", false)
        val previousSize = prefs.all[BubbleSize.PREFERENCE_KEY]
        val recordingExpanded = prefs.all["settings_expanded_recording"] as? Boolean
        prefs.edit().putBoolean("welcome_done", true).commit()
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    val root = activity.window.decorView
                    val language = DictationLanguage.fromSaved(prefs.getString("language_profile", null)).title
                    assertFalse("Home must not expose a language picker", descendants(root).filterIsInstance<MaterialButton>().any { it.text.toString() == language })
                    descendants(root).single { it.contentDescription == "Open navigation" }.performClick()
                }
                instrumentation.waitForIdleSync()
                val deadline = SystemClock.uptimeMillis() + 5_000
                var settled = false
                while (!settled && SystemClock.uptimeMillis() < deadline) {
                    scenario.onActivity { activity ->
                        val panel = requireNotNull(activity.window.decorView.findViewWithTag<View>("navigation-panel"))
                        settled = panel.width > 0 && panel.translationX <= 1f
                        if (settled) {
                            val parent = panel.parent as View
                            assertTrue("Drawer must be anchored on the right", panel.left > 0)
                            assertEquals(parent.width - parent.paddingRight, panel.right)
                        }
                    }
                    if (!settled) SystemClock.sleep(50)
                }
                assertTrue("Drawer transition did not settle", settled)
                scenario.onActivity { activity ->
                    activity.onBackPressedDispatcher.onBackPressed()
                    val nav = activity.javaClass.getDeclaredField("navigation").apply { isAccessible = true }.get(activity) as WisprNavigation
                    nav.openSettings()
                    root.findViewWithTag<View>("settings-open-PREFERENCES").performClick()
                    assertNotNull("Language belongs to Preferences", root.findViewWithTag<View>("settings-language"))
                }
                instrumentation.waitForIdleSync()
                scenario.onActivity { activity ->
                    val root = activity.window.decorView
                    val slider = descendants(root).filterIsInstance<Slider>().single { it.contentDescription.toString().startsWith(context.getString(R.string.bubble_size_accessibility)) }
                    val preview = requireNotNull(root.findViewWithTag<View>("bubble-size-preview"))
                    val stage = preview.parent as View
                    fun capture(name: String): Bitmap {
                        stage.measure(View.MeasureSpec.makeMeasureSpec(stage.width, View.MeasureSpec.EXACTLY),
                            View.MeasureSpec.makeMeasureSpec(stage.height, View.MeasureSpec.EXACTLY))
                        stage.layout(stage.left, stage.top, stage.right, stage.bottom)
                        return Bitmap.createBitmap(stage.width, stage.height, Bitmap.Config.ARGB_8888).also { bitmap ->
                            stage.draw(Canvas(bitmap))
                            val folder = java.io.File(context.cacheDir, "bubble-preview").apply { mkdirs() }
                            java.io.File(folder, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                        }
                    }
                    val initial = slider.value
                    try {
                        slider.value = BubbleSize.MIN_PERCENT.toFloat()
                        val minimumWidth = preview.layoutParams.width
                        assertEquals(minimumWidth, preview.layoutParams.height)
                        val small = capture("small")
                        try {
                            slider.value = BubbleSize.MAX_PERCENT.toFloat()
                            assertTrue("Live preview must visibly grow with the slider", preview.layoutParams.width > minimumWidth)
                            assertEquals(preview.layoutParams.width, preview.layoutParams.height)
                            assertTrue(slider.contentDescription.toString().contains(BubbleSize.MAX_PERCENT.toString()))
                            val large = capture("large")
                            try {
                                val y = small.height / 2
                                val changed = (0 until small.width).count { x -> small.getPixel(x, y) != large.getPixel(x, y) }
                                assertTrue("Rendered preview must change, not only its stored dimensions", changed > 4)
                            } finally { large.recycle() }
                        } finally { small.recycle() }
                    } finally { slider.value = initial }
                }
            }
            assertEquals("Programmatic preview checks must preserve the user's saved size", previousSize, prefs.all[BubbleSize.PREFERENCE_KEY])
        } finally {
            prefs.edit().apply {
                if (recordingExpanded == null) remove("settings_expanded_recording") else putBoolean("settings_expanded_recording", recordingExpanded)
            }.commit()
            if (hadWelcome) prefs.edit().putBoolean("welcome_done", welcome).commit()
            else prefs.edit().remove("welcome_done").commit()
        }
    }
}
