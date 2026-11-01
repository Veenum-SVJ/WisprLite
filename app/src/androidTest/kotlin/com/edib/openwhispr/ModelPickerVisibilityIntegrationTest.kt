package com.edib.openwhispr

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ModelPickerVisibilityIntegrationTest {
    private fun descendants(view: View): List<View> = buildList {
        add(view)
        if (view is ViewGroup) for (i in 0 until view.childCount) addAll(descendants(view.getChildAt(i)))
    }
    @Test fun modelsAndOfficialBadgesStayVisibleInCloudAndLocalModes() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val prefs = context.getSharedPreferences("openwhispr", 0)
        val previous = listOf("use_local", "welcome_done", "settings_expanded_storage", "settings_models_expanded").associateWith { if (prefs.contains(it)) prefs.getBoolean(it, true) else null }
        val selectedModel = prefs.getString("model_name", null)
        val installed = MODEL_CATALOG.filter { ModelDownloader.isInstalled(context, it) }.map { it.archive }.toSet()
        try {
            prefs.edit().putBoolean("use_local", false).putBoolean("welcome_done", true)
                .putBoolean("settings_expanded_storage", false).putBoolean("settings_models_expanded", false).commit()
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    val navigation = activity.javaClass.getDeclaredField("navigation").apply { isAccessible = true }.get(activity) as WisprNavigation
                    navigation.openSettings()
                    val root = activity.window.decorView
                    assertEquals(View.VISIBLE, root.findViewWithTag<View>("settings-index").visibility)
                    root.findViewWithTag<View>("settings-open-SYSTEM").performClick()
                    val systemLabels = descendants(root.findViewWithTag<View>("settings-detail")).filterIsInstance<TextView>().map { it.text.toString() }
                    assertTrue(activity.getString(R.string.setup) in systemLabels)
                    assertTrue("Service" in systemLabels)
                    activity.onBackPressedDispatcher.onBackPressed()
                    activity.openLocalModels()
                    val container = activity.javaClass.getDeclaredField("modelContainer").apply { isAccessible = true }.get(activity) as View
                    assertEquals(View.VISIBLE, container.visibility)
                    assertEquals("There must be a single Local models entry", 1, descendants(root).filterIsInstance<TextView>().count { it.text.toString() == "Local models" })
                    activity.javaClass.getDeclaredMethod("refresh").apply { isAccessible = true }.invoke(activity)
                    assertEquals("Refresh preserves the speech detail page", "Speech-to-Text", root.findViewWithTag<TextView>("settings-detail-title").text.toString())
                }
                instrumentation.waitForIdleSync()
                scenario.onActivity { activity ->
                    val container = activity.javaClass.getDeclaredField("modelContainer").apply { isAccessible = true }.get(activity) as View
                    val location = IntArray(2).also { container.getLocationOnScreen(it) }
                    assertTrue("Local models shortcut must reveal the model list", location[1] >= 0 && location[1] < activity.window.decorView.height)
                }
                for (local in listOf(false, true)) {
                    prefs.edit().putBoolean("use_local", local).commit()
                    scenario.onActivity { activity ->
                        activity.javaClass.getDeclaredMethod("refresh").apply { isAccessible = true }.invoke(activity)
                        val container = activity.javaClass.getDeclaredField("modelContainer").apply { isAccessible = true }.get(activity) as View
                        assertEquals("Cloud mode must not hide model management", View.VISIBLE, container.visibility)
                        val children = descendants(container)
                        val names = children.filterIsInstance<TextView>().map { it.text.toString() }
                        MODEL_CATALOG.forEach { assertTrue("Missing model ${it.name}", it.name in names) }
                        MODEL_CATALOG.filter { it.archive !in installed }.forEach { model ->
                            val download = children.single { it.contentDescription?.toString() == activity.getString(R.string.download_model, model.name) }
                            assertTrue("Download icon needs an action", download.hasOnClickListeners())
                            assertTrue("Download icon needs to be enabled", download.isEnabled)
                        }
                        val badges = children.filterIsInstance<ImageView>().filter { it.contentDescription?.toString()?.endsWith("model family") == true }
                        assertEquals(MODEL_CATALOG.size, badges.size)
                        assertTrue(badges.all { it.drawable != null && it.visibility == View.VISIBLE })
                        for (model in MODEL_CATALOG.filter { it.archive in installed }) {
                            val delete = children.single { it.contentDescription?.toString() == activity.getString(R.string.delete_model, model.name) }
                            assertEquals("Every installed model, including the selected model, must be deletable in either mode", View.VISIBLE, delete.visibility)
                        }
                        if (!local) {
                            assertTrue(container.width > 0 && container.height > 0)
                            val image = Bitmap.createBitmap(container.width, container.height, Bitmap.Config.ARGB_8888)
                            try {
                                container.draw(Canvas(image))
                                val output = File(context.cacheDir, "model-picker-test").apply { mkdirs() }
                                File(output, "models-cloud.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
                            } finally { image.recycle() }
                        }
                    }
                }
                scenario.onActivity { activity ->
                    val navigation = activity.javaClass.getDeclaredField("navigation").apply { isAccessible = true }.get(activity) as WisprNavigation
                    navigation.javaClass.getDeclaredMethod("show", String::class.java).apply { isAccessible = true }.invoke(navigation, "Insights")
                }
                val deadline = android.os.SystemClock.uptimeMillis() + 5000
                var chartCount = 0
                while (chartCount != 1 && android.os.SystemClock.uptimeMillis() < deadline) {
                    instrumentation.waitForIdleSync()
                    scenario.onActivity { chartCount = descendants(it.window.decorView).filterIsInstance<SpeakingActivityView>().size }
                    if (chartCount != 1) Thread.sleep(50)
                }
                assertEquals("Recent speaking activity needs a heatmap", 1, chartCount)
                scenario.onActivity { activity ->
                    val root = activity.window.decorView
                    val labels = descendants(root).filterIsInstance<TextView>().map { it.text.toString() }
                    assertEquals(1, labels.count { it.startsWith("Estimated typing time saved") })
                    val image = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
                    try {
                        root.draw(Canvas(image))
                        File(context.cacheDir, "model-picker-test/insights.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    } finally { image.recycle() }
                }
            }
            assertEquals(selectedModel, prefs.getString("model_name", null))
            assertEquals(installed, MODEL_CATALOG.filter { ModelDownloader.isInstalled(context, it) }.map { it.archive }.toSet())
        } finally {
            prefs.edit().apply { previous.forEach { (key, value) -> if (value == null) remove(key) else putBoolean(key, value) } }.commit()
        }
    }
}
