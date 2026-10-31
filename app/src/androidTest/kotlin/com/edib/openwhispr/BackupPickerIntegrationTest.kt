package com.edib.openwhispr

import android.app.UiAutomation
import android.os.SystemClock
import android.view.KeyEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.os.Bundle
import androidx.activity.result.ActivityResultLauncher
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Uses Android's actual document picker; cancellation must discard export secrets. */
@RunWith(AndroidJUnit4::class)
class BackupPickerIntegrationTest {
    @Test fun exportedDocumentCanBeSelectedAndRestoredThroughAndroidPicker() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val prefs = context.getSharedPreferences("openwhispr", 0)
        val marker = "backup_picker_test_${java.util.UUID.randomUUID()}"
        val filename = "$marker.owb"
        println("Synthetic backup document: $filename")
        val expected = "Ẹ káàárọ̀, Ṣọlá. How you dey?"
        val automation = instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
        val hadWelcome = prefs.contains("welcome_done")
        val welcome = prefs.getBoolean("welcome_done", false)
        prefs.edit().putBoolean("welcome_done", true).commit()
        fun nodes(node: AccessibilityNodeInfo): List<AccessibilityNodeInfo> = buildList {
            add(node)
            for (index in 0 until node.childCount) node.getChild(index)?.let { addAll(nodes(it)) }
        }
        fun waitFor(description: String, action: () -> Boolean) {
            val deadline = SystemClock.uptimeMillis() + 15_000
            while (SystemClock.uptimeMillis() < deadline) {
                if (action()) return
                SystemClock.sleep(100)
            }
            fail("Timed out: $description")
        }
        fun click(text: String) = waitFor("click $text") {
            val root = automation.rootInActiveWindow ?: return@waitFor false
            val node = nodes(root).firstOrNull { it.text?.toString()?.equals(text, true) == true } ?: return@waitFor false
            var clickable: AccessibilityNodeInfo? = node
            while (clickable != null && !clickable.isClickable) clickable = clickable.parent
            clickable?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true
        }
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    fun field(name: String) = activity.javaClass.getDeclaredField(name).apply { isAccessible = true }
                    field("backupPassphrase").set(activity, "synthetic picker passphrase")
                    field("pendingExport").set(activity, SettingsBackup.Snapshot(mapOf(marker to expected), emptyMap()))
                    @Suppress("UNCHECKED_CAST")
                    val launcher = field("createBackupLauncher").get(activity) as ActivityResultLauncher<String>
                    launcher.launch(filename)
                }
                click("Save")
                waitFor("export callback") {
                    automation.rootInActiveWindow?.packageName?.toString() == context.packageName
                }
                // Encryption runs off the UI thread; the picker will list the completed document.
                SystemClock.sleep(1000)
                scenario.onActivity { activity ->
                    val field = activity.javaClass.getDeclaredField("openBackupLauncher").apply { isAccessible = true }
                    @Suppress("UNCHECKED_CAST")
                    val launcher = field.get(activity) as ActivityResultLauncher<Array<String>>
                    launcher.launch(arrayOf("*/*"))
                }
                click(filename)
                waitFor("restore passphrase field") {
                    val root = automation.rootInActiveWindow ?: return@waitFor false
                    if (root.packageName?.toString() != context.packageName) return@waitFor false
                    val input = nodes(root).firstOrNull { it.className?.toString() == "android.widget.EditText" } ?: return@waitFor false
                    input.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, Bundle().apply {
                        putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "synthetic picker passphrase")
                    })
                }
                click("Continue")
                waitFor("restore selection") {
                    automation.rootInActiveWindow?.let { root -> nodes(root).any { it.text?.toString() == marker } } == true
                }
                click("Continue")
                waitFor("restored Unicode preference") { prefs.getString(marker, null) == expected }
                assertEquals(expected, prefs.getString(marker, null))
            }
        } finally {
            prefs.edit().remove(marker).apply {
                if (hadWelcome) putBoolean("welcome_done", welcome) else remove("welcome_done")
            }.commit()
        }
    }
    @Test fun cancellingDocumentPickerDiscardsPendingPassphraseAndSnapshot() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val prefs = context.getSharedPreferences("openwhispr", 0)
        val hadWelcome = prefs.contains("welcome_done")
        val welcome = prefs.getBoolean("welcome_done", false)
        prefs.edit().putBoolean("welcome_done", true).commit()
        val automation = instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    fun field(name: String) = activity.javaClass.getDeclaredField(name).apply { isAccessible = true }
                    field("backupPassphrase").set(activity, "synthetic picker passphrase")
                    field("pendingExport").set(activity, SettingsBackup.Snapshot(emptyMap(), mapOf("cleanup_api_key" to "synthetic-only")))
                    @Suppress("UNCHECKED_CAST")
                    val launcher = field("createBackupLauncher").get(activity) as ActivityResultLauncher<String>
                    launcher.launch("wisprlite-cancel-test.owb")
                }
                val deadline = SystemClock.uptimeMillis() + 10_000
                var pickerVisible = false
                while (!pickerVisible && SystemClock.uptimeMillis() < deadline) {
                    pickerVisible = automation.rootInActiveWindow?.packageName?.toString()?.contains("documentsui") == true
                    if (!pickerVisible) SystemClock.sleep(100)
                }
                assertTrue("Android document picker did not open", pickerVisible)
                // On MIUI the first Back can dismiss the filename keyboard only.
                // Keep backing out only while the document picker is foregrounded.
                for (attempt in 0 until 3) {
                    if (automation.rootInActiveWindow?.packageName?.toString()?.contains("documentsui") != true) break
                    val now = SystemClock.uptimeMillis()
                    automation.injectInputEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK, 0), true)
                    automation.injectInputEvent(KeyEvent(now, now + 1, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK, 0), true)
                    SystemClock.sleep(500)
                }
                val returnDeadline = SystemClock.uptimeMillis() + 10_000
                while (automation.rootInActiveWindow?.packageName?.toString() != context.packageName &&
                    SystemClock.uptimeMillis() < returnDeadline) SystemClock.sleep(100)
                assertEquals("Picker cancellation did not return to WisprLite", context.packageName,
                    automation.rootInActiveWindow?.packageName?.toString())
                instrumentation.waitForIdleSync()
                scenario.onActivity { activity ->
                    for (name in listOf("backupPassphrase", "pendingExport")) {
                        val field = activity.javaClass.getDeclaredField(name).apply { isAccessible = true }
                        assertNull("Cancelled export retained $name", field.get(activity))
                    }
                }
            }
        } finally {
            if (hadWelcome) prefs.edit().putBoolean("welcome_done", welcome).commit()
            else prefs.edit().remove("welcome_done").commit()
        }
    }
}
