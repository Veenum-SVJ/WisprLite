package com.edib.openwhispr

import android.content.ClipboardManager
import android.content.Context
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HistoryDetailIntegrationTest {
    @Test fun longYorubaOriginalIsScrollableAndCopiesWithoutChangingHistory() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val before = HistoryRepository(context).use { it.totals(0) }
        val original = "Ẹ káàárọ̀. I will send the document tomorrow. ".repeat(600)
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val prefs = context.getSharedPreferences("openwhispr", 0)
        val hadWelcome = prefs.contains("welcome_done")
        val welcome = prefs.getBoolean("welcome_done", false)
        prefs.edit().putBoolean("welcome_done", true).commit()
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                val previousClip = clipboard.primaryClip
                try {
                    scenario.onActivity { activity ->
                        val nav = activity.javaClass.getDeclaredField("navigation").apply { isAccessible = true }.get(activity)
                        requireNotNull(nav).javaClass.getDeclaredMethod("detail", DictationRecord::class.java)
                            .apply { isAccessible = true }.invoke(nav,
                                DictationRecord("ui-detail-only", 1, original, null, 5000, "Direct"))
                    }
                    instrumentation.waitForIdleSync()
                    val root = requireNotNull(instrumentation.uiAutomation.rootInActiveWindow)
                    assertEquals("com.veenum.wisprlite", root.packageName.toString())
                    fun nodes(node: AccessibilityNodeInfo): List<AccessibilityNodeInfo> = buildList {
                        add(node)
                        for (index in 0 until node.childCount) node.getChild(index)?.let { addAll(nodes(it)) }
                    }
                    val all = nodes(root)
                    assertTrue("Original text must remain complete", all.any { it.text?.toString() == "Original\n$original" })
                    assertTrue("Long details need a scrollable container", all.any { it.isScrollable })
                    val copy = root.findAccessibilityNodeInfosByText("Copy original").singleOrNull { it.text?.toString().equals("Copy original", ignoreCase = true) }
                        ?: error("Copy original is not accessible when the detail opens")
                    assertTrue(copy.performAction(AccessibilityNodeInfo.ACTION_CLICK))
                    instrumentation.waitForIdleSync()
                    assertTrue("Copied text lost Unicode or content", clipboard.primaryClip?.getItemAt(0)?.text?.toString() == original)
                    val close = requireNotNull(instrumentation.uiAutomation.rootInActiveWindow)
                        .findAccessibilityNodeInfosByText("Close").singleOrNull { it.text?.toString().equals("Close", ignoreCase = true) }
                        ?: error("Close action is missing from the detail dialog")
                    assertTrue(close.performAction(AccessibilityNodeInfo.ACTION_CLICK))
                } finally {
                    if (previousClip == null) clipboard.clearPrimaryClip() else clipboard.setPrimaryClip(previousClip)
                }
            }
            assertEquals(before, HistoryRepository(context).use { it.totals(0) })
            assertFalse(HistoryRepository(context).use { it.listBefore().any { record -> record.id == "ui-detail-only" } })
        } finally {
            if (hadWelcome) prefs.edit().putBoolean("welcome_done", welcome).commit()
            else prefs.edit().remove("welcome_done").commit()
        }
    }
}
