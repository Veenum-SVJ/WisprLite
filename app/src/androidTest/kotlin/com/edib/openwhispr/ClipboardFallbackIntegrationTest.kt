package com.edib.openwhispr

import android.content.ClipboardManager
import android.content.Context
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ClipboardFallbackIntegrationTest {
    @Test fun noEditableFieldKeepsUnicodeOnSensitiveClipboardWithoutSavingHistory() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val prefs = context.getSharedPreferences("openwhispr", 0)
        val hadMaster = prefs.contains("service_master_enabled")
        val master = prefs.getBoolean("service_master_enabled", true)
        val hadWelcome = prefs.contains("welcome_done")
        val welcome = prefs.getBoolean("welcome_done", false)
        prefs.edit().putBoolean("welcome_done", true).putBoolean("service_master_enabled", true).commit()
        val before = HistoryRepository(context).use { it.totals().recordings }
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                val deadline = SystemClock.uptimeMillis() + 45_000
                while (WhisperAccessibilityService.instance == null && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(100)
                val service = requireNotNull(WhisperAccessibilityService.instance) { "Own accessibility service must be bound" }
                instrumentation.waitForIdleSync()
                scenario.onActivity { activity ->
                    val clipboard = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val previous = clipboard.primaryClip
                    try {
                        val phrase = "Ẹ káàárọ̀, Ṣọlá. Abeg send am."
                        val injected = service.javaClass.getDeclaredMethod("injectText", String::class.java, String::class.java, Long::class.javaPrimitiveType)
                            .apply { isAccessible = true }.invoke(service, phrase, null, 0L) as Boolean
                        assertFalse("Home has no editable field; injection should use clipboard fallback", injected)
                        val clip = requireNotNull(clipboard.primaryClip)
                        assertEquals(phrase, clip.getItemAt(0).text.toString())
                        assertTrue(clip.description.extras?.getBoolean("android.content.extra.IS_SENSITIVE") == true)
                    } finally {
                        if (previous == null) clipboard.clearPrimaryClip() else clipboard.setPrimaryClip(previous)
                    }
                }
            }
            assertEquals(before, HistoryRepository(context).use { it.totals().recordings })
        } finally {
            prefs.edit().apply {
                if (hadMaster) putBoolean("service_master_enabled", master) else remove("service_master_enabled")
                if (hadWelcome) putBoolean("welcome_done", welcome) else remove("welcome_done")
            }.commit()
        }
    }
}
