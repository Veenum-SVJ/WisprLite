package com.edib.openwhispr

import android.content.Context
import android.os.SystemClock
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BubbleVisibilityIntegrationTest {
    @Test fun dismissingKeyboardHidesBubbleDespiteRetainedFocusAndRepeatedEvents() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val prefs = context.getSharedPreferences("openwhispr", 0)
        val previous = listOf("welcome_done", "service_master_enabled").associateWith { prefs.all[it] }
        prefs.edit().putBoolean("welcome_done", true).putBoolean("service_master_enabled", true).commit()
        fun await(message: String, condition: () -> Boolean) {
            val deadline = SystemClock.uptimeMillis() + 6_000
            while (SystemClock.uptimeMillis() < deadline) {
                var ready = false
                instrumentation.runOnMainSync { ready = condition() }
                if (ready) return
                SystemClock.sleep(50)
            }
            fail(message)
        }
        try {
            val serviceDeadline = SystemClock.uptimeMillis() + 45_000
            while (WhisperAccessibilityService.instance == null && SystemClock.uptimeMillis() < serviceDeadline) SystemClock.sleep(100)
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                val service = requireNotNull(WhisperAccessibilityService.instance)
                val overlayField = service.javaClass.getDeclaredField("overlayView").apply { isAccessible = true }
                val refresh = service.javaClass.getDeclaredMethod("refreshAccessibilityFocusSignal").apply { isAccessible = true }
                val overlay = overlayField.get(service) as View
                lateinit var field: EditText
                scenario.onActivity { activity ->
                    field = EditText(activity).apply { hint = "Bubble visibility fixture" }
                    activity.setContentView(field)
                    field.requestFocus()
                    field.post { (activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(field, InputMethodManager.SHOW_IMPLICIT) }
                }
                await("Typing must show the bubble") { refresh.invoke(service); overlay.visibility == View.VISIBLE && overlay.alpha > .1f }
                scenario.onActivity { activity ->
                    (activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(field.windowToken, 0)
                }
                // Flood refreshes more quickly than the old 300ms debounce. It must
                // still finish hiding, even though Android retains editor focus.
                await("Dismissed IME must hide despite retained focus and repeated refreshes") {
                    refresh.invoke(service)
                    overlay.visibility != View.VISIBLE
                }
                scenario.onActivity { assertTrue("Fixture must retain focus to exercise the stale-editor case", field.isFocused) }
                instrumentation.runOnMainSync { service.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME) }
                await("An ordinary non-typing screen must keep the bubble hidden") { refresh.invoke(service); overlay.visibility != View.VISIBLE }
            }
        } finally {
            prefs.edit().apply { previous.forEach { (key, value) -> if (value is Boolean) putBoolean(key, value) else remove(key) } }.commit()
        }
    }
}
