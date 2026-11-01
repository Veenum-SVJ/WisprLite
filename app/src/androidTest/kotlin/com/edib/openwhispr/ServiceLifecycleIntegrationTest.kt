package com.edib.openwhispr

import android.media.AudioRecord
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Requires own accessibility service enabled and the separate test field foregrounded via ADB. */
@RunWith(AndroidJUnit4::class)
class ServiceLifecycleIntegrationTest {
    @Test fun microphoneInterruptReleasesRecorderAndCrossAppInsertionWorks() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val prefs = context.getSharedPreferences("openwhispr", 0)
        val previous = listOf("use_local", "service_master_enabled").associateWith { key ->
            if (prefs.contains(key)) prefs.getBoolean(key, true) else null
        }
        val deadline = System.currentTimeMillis() + 45_000
        while (WhisperAccessibilityService.instance == null && System.currentTimeMillis() < deadline) Thread.sleep(100)
        val service = requireNotNull(WhisperAccessibilityService.instance) { "Enable/rebind WisprLite accessibility service" }
        fun field(name: String) = service.javaClass.getDeclaredField(name).apply { isAccessible = true }
        val recorderField = field("audioRecord")
        val before = HistoryRepository(context).use { it.totals(0).recordings }
        val audioDirectory = java.io.File(context.filesDir, "recordings")
        val audioBefore = audioDirectory.listFiles().orEmpty().map { it.name }.toSet()
        fun assertNoNewAudio() {
            assertTrue("Cancelled capture must not persist audio", audioDirectory.listFiles().orEmpty().none { it.name !in audioBefore })
        }
        try {
            // This test cancels capture, so it cannot send audio to any provider.
            prefs.edit().putBoolean("use_local", false).putBoolean("service_master_enabled", true).commit()
            instrumentation.runOnMainSync {
                service.javaClass.getDeclaredMethod("startRecording").apply { isAccessible = true }.invoke(service)
                assertEquals(AudioRecord.RECORDSTATE_RECORDING, (recorderField.get(service) as AudioRecord).recordingState)
            }
            Thread.sleep(250)
            instrumentation.runOnMainSync {
                service.onInterrupt()
                assertNull(recorderField.get(service))
                assertNull(field("pcmStream").get(service))
                assertEquals("IDLE", field("state").get(service)?.toString())
            }
            assertNoNewAudio()
            // Pausing must cancel a second live capture through the Settings switch path.
            instrumentation.runOnMainSync {
                service.javaClass.getDeclaredMethod("startRecording").apply { isAccessible = true }.invoke(service)
                assertEquals(AudioRecord.RECORDSTATE_RECORDING, (recorderField.get(service) as AudioRecord).recordingState)
            }
            prefs.edit().putBoolean("service_master_enabled", false).commit()
            service.refreshMasterEnabled()
            instrumentation.waitForIdleSync()
            instrumentation.runOnMainSync {
                assertNull(recorderField.get(service))
                assertNull(field("pcmStream").get(service))
                assertEquals("IDLE", field("state").get(service)?.toString())
            }
            prefs.edit().putBoolean("service_master_enabled", true).commit()
            service.refreshMasterEnabled()
            instrumentation.waitForIdleSync()
            assertEquals(before, HistoryRepository(context).use { it.totals(0).recordings })
            assertNoNewAudio()
            val phrase = "WisprLite insertion: Ṣọlá, how you dey?"
            var injected = false
            var editorReadySince = 0L
            fun readyEditor(node: AccessibilityNodeInfo): Boolean = (node.isEditable && node.isFocused) ||
                (0 until node.childCount).any { index -> node.getChild(index)?.let(::readyEditor) == true }
            val targetDeadline = System.currentTimeMillis() + 45_000
            while (!injected && System.currentTimeMillis() < targetDeadline) {
                instrumentation.runOnMainSync {
                    val roots = service.windows.mapNotNull { it.root }.filter { it.packageName?.toString() == "com.veenum.wisprlite.test" }
                    // A WebView root can appear before its contenteditable and input
                    // connection are ready. Paste may then acknowledge without inserting.
                    val ready = roots.any(::readyEditor)
                    if (!ready) editorReadySince = 0L
                    else if (editorReadySince == 0L) editorReadySince = System.currentTimeMillis()
                    if (ready && System.currentTimeMillis() - editorReadySince >= 500L) {
                        injected = service.javaClass.getDeclaredMethod("injectText", String::class.java, String::class.java, Long::class.javaPrimitiveType)
                            .apply { isAccessible = true }.invoke(service, phrase, null, 0L) as Boolean
                    }
                }
                if (!injected) Thread.sleep(200)
            }
            assertTrue("No insertion into the separate test app", injected)
            fun contains(node: AccessibilityNodeInfo): Boolean = node.text?.toString()?.contains(phrase) == true ||
                (0 until node.childCount).any { index -> node.getChild(index)?.let(::contains) == true }
            val verifyDeadline = System.currentTimeMillis() + 5_000
            var found = false
            while (!found && System.currentTimeMillis() < verifyDeadline) {
                instrumentation.runOnMainSync {
                    found = service.windows.mapNotNull { it.root }.filter { it.packageName?.toString() == "com.veenum.wisprlite.test" }.any(::contains)
                }
                if (!found) Thread.sleep(100)
            }
            if (!found) instrumentation.runOnMainSync {
                fun describe(node: AccessibilityNodeInfo) {
                    println("Owned test editor node: ${node.className}; editable=${node.isEditable}; text=${node.text}")
                    for (index in 0 until node.childCount) node.getChild(index)?.let(::describe)
                }
                service.windows.mapNotNull { it.root }.filter { it.packageName?.toString() == "com.veenum.wisprlite.test" }.forEach(::describe)
            }
            assertTrue("Accessibility action reported success but field text differs", found)
        } finally {
            instrumentation.runOnMainSync { service.onInterrupt() }
            val edit = prefs.edit()
            for ((key, value) in previous) if (value == null) edit.remove(key) else edit.putBoolean(key, value)
            edit.commit()
        }
    }
}
