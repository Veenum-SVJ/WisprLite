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
        val previous = listOf("use_local", "welcome_done").associateWith { if (prefs.contains(it)) prefs.getBoolean(it, true) else null }
        val selectedModel = prefs.getString("model_name", null)
        val installed = MODEL_CATALOG.filter { ModelDownloader.isInstalled(context, it) }.map { it.archive }.toSet()
        try {
            prefs.edit().putBoolean("use_local", false).putBoolean("welcome_done", true).commit()
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    val navigation = activity.javaClass.getDeclaredField("navigation").apply { isAccessible = true }.get(activity) as WisprNavigation
                    navigation.openSettings()
                    val shortcut = descendants(activity.window.decorView).filterIsInstance<ViewGroup>().single { view ->
                        view.isClickable && descendants(view).filterIsInstance<TextView>().any { it.text.toString() == "Local models" }
                    }
                    shortcut.performClick()
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
                        val badges = children.filterIsInstance<ImageView>().filter { it.contentDescription?.toString()?.endsWith("model family") == true }
                        assertEquals(MODEL_CATALOG.size, badges.size)
                        assertTrue(badges.all { it.drawable != null && it.visibility == View.VISIBLE })
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
            }
            assertEquals(selectedModel, prefs.getString("model_name", null))
            assertEquals(installed, MODEL_CATALOG.filter { ModelDownloader.isInstalled(context, it) }.map { it.archive }.toSet())
        } finally {
            prefs.edit().apply { previous.forEach { (key, value) -> if (value == null) remove(key) else putBoolean(key, value) } }.commit()
        }
    }
}
