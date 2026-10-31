package com.edib.openwhispr

import android.content.ClipboardManager
import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HistoryDetailIntegrationTest {
    private fun descendants(view: View): List<View> = buildList {
        add(view)
        if (view is ViewGroup) for (index in 0 until view.childCount) addAll(descendants(view.getChildAt(index)))
    }
    @Test fun inlineActionsCopyFullOriginalAndCleanedWithoutDialogOrHistoryChanges() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val before = HistoryRepository(context).use { it.totals(0) }
        val original = "Ẹ káàárọ̀. I will send the document tomorrow. ".repeat(600)
        val cleaned = "Ẹ káàárọ̀, Ṣọlá. I will send the document tomorrow."
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val prefs = context.getSharedPreferences("openwhispr", 0)
        val hadWelcome = prefs.contains("welcome_done")
        val welcome = prefs.getBoolean("welcome_done", false)
        prefs.edit().putBoolean("welcome_done", true).commit()
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                var previousClip: android.content.ClipData? = null
                lateinit var card: View
                try {
                    scenario.onActivity { activity ->
                        previousClip = clipboard.primaryClip
                        val nav = requireNotNull(activity.javaClass.getDeclaredField("navigation").apply { isAccessible = true }.get(activity))
                        card = nav.javaClass.getDeclaredMethod("transcriptCard", DictationRecord::class.java)
                            .apply { isAccessible = true }.invoke(nav,
                                DictationRecord("ui-inline-only", 1, original, cleaned, 5000, "AI Cleanup")) as View
                        val content = nav.javaClass.getDeclaredField("content").apply { isAccessible = true }.get(nav) as LinearLayout
                        content.addView(card, 0)
                    }
                    instrumentation.waitForIdleSync()
                    scenario.onActivity { activity ->
                        fun action(description: String) = descendants(card).single { it.contentDescription == description }
                        val words = card.findViewWithTag<TextView>("transcript-text")
                        assertEquals(cleaned, words.text.toString())
                        action("Copy transcript").performClick()
                        assertEquals(cleaned, clipboard.primaryClip?.getItemAt(0)?.text?.toString())
                        action("Show original transcript").performClick()
                        assertEquals(5, words.maxLines)
                        action("Copy transcript").performClick()
                        assertEquals("Copy must use the complete original, not its preview", original, clipboard.primaryClip?.getItemAt(0)?.text?.toString())
                        card.performClick()
                        assertEquals(Int.MAX_VALUE, words.maxLines)
                        assertEquals(original, words.text.toString())
                        assertTrue("Inline expansion must keep focus in the main window", activity.hasWindowFocus())
                        assertNotNull(action("Delete transcript"))
                        assertNotNull(action("Clean up transcript"))
                        var parent = card.parent
                        var scrollable = false
                        while (parent != null) { if (parent is ScrollView) scrollable = true; parent = parent.parent }
                        assertTrue("Expanded text must remain in the page scroll container", scrollable)
                    }
                } finally {
                    if (previousClip == null) clipboard.clearPrimaryClip() else clipboard.setPrimaryClip(previousClip!!)
                }
            }
            assertEquals(before, HistoryRepository(context).use { it.totals(0) })
            assertFalse(HistoryRepository(context).use { it.listBefore().any { record -> record.id == "ui-inline-only" } })
        } finally {
            if (hadWelcome) prefs.edit().putBoolean("welcome_done", welcome).commit()
            else prefs.edit().remove("welcome_done").commit()
        }
    }
}
