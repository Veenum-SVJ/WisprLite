package com.edib.openwhispr

import android.app.AlertDialog
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ScrollView
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.material.switchmaterial.SwitchMaterial
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Screen fixtures use a separate database/preferences; never insert into personal history. */
@RunWith(AndroidJUnit4::class)
class MainScreensIntegrationTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val database = "main-screen-ui-fixture.db"
    private fun descendants(view: View): List<View> = buildList {
        add(view); if (view is ViewGroup) for (i in 0 until view.childCount) addAll(descendants(view.getChildAt(i)))
    }
    private fun host(block: (ActivityScenario<MainActivity>) -> Unit) {
        val prefs = context.getSharedPreferences("openwhispr", 0)
        val before = if (prefs.contains("welcome_done")) prefs.getBoolean("welcome_done", false) else null
        prefs.edit().putBoolean("welcome_done", true).commit()
        context.deleteDatabase(database)
        context.getSharedPreferences("main-screen-ui-fixture", 0).edit().clear().commit()
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    (activity.javaClass.getDeclaredField("navigation").apply { isAccessible = true }.get(activity) as WisprNavigation).close()
                }
                block(scenario)
            }
        } finally {
            prefs.edit().apply { if (before == null) remove("welcome_done") else putBoolean("welcome_done", before) }.commit()
            context.deleteDatabase(database)
            context.getSharedPreferences("main-screen-ui-fixture", 0).edit().clear().commit()
        }
    }
    private fun awaitUi(scenario: ActivityScenario<MainActivity>, check: (MainActivity) -> Boolean) {
        val deadline = android.os.SystemClock.uptimeMillis() + 5_000
        while (android.os.SystemClock.uptimeMillis() < deadline) {
            var ready = false; scenario.onActivity { ready = check(it) }
            if (ready) return
            Thread.sleep(50)
        }
        fail("Screen fixture did not reach the expected state")
    }
    private fun dialog(screen: DictionaryScreen): AlertDialog = screen.javaClass.getDeclaredField("dialog")
        .apply { isAccessible = true }.get(screen) as AlertDialog

    @Test fun searchPaginatesLiteralExpandedTextAndPreservesQueryAcrossRefreshAndRebuild() = host { scenario ->
        HistoryRepository(context, database).use { repo ->
            repeat(45) { n -> repo.saveSuccessful(DictationRecord("fixture-$n", n.toLong() + 1,
                "recognized fixture $n", null, 1000, "Direct", "literal 100%_done\\ok " + "long saved text ".repeat(40))) }
            repo.saveSuccessful(DictationRecord("unrelated", 1000, "unrelated", null, 1000, "Direct"))
        }
        val state = Bundle(); lateinit var screen: HomeHistoryScreen
        scenario.onActivity { activity ->
            screen = HomeHistoryScreen(activity, state, {}, database)
            activity.setContentView(ScrollView(activity).apply { addView(screen.build()) })
            activity.window.decorView.findViewWithTag<EditText>("history-search").setText("100%_done\\ok")
        }
        try {
            awaitUi(scenario) { activity ->
                val views = descendants(activity.window.decorView)
                views.count { it.tag?.toString()?.startsWith("transcript-card-") == true } == 40 &&
                    views.filterIsInstance<TextView>().filter { it.tag == "transcript-text" }.all { it.maxLines == Int.MAX_VALUE }
            }
            scenario.onActivity { activity ->
                val views = descendants(activity.window.decorView)
                assertTrue(views.filterIsInstance<TextView>().filter { it.tag == "transcript-text" }.all { it.maxLines == Int.MAX_VALUE })
                views.filterIsInstance<TextView>().single { it.text == "Load more" }.performClick()
            }
            awaitUi(scenario) { activity -> descendants(activity.window.decorView).count { it.tag?.toString()?.startsWith("transcript-card-") == true } == 45 }
            scenario.onActivity { activity ->
                screen.refresh()
                assertEquals("100%_done\\ok", activity.window.decorView.findViewWithTag<EditText>("history-search").text.toString())
                screen.close(); screen = HomeHistoryScreen(activity, Bundle(state), {}, database)
                activity.setContentView(ScrollView(activity).apply { addView(screen.build()) })
                assertEquals("100%_done\\ok", activity.window.decorView.findViewWithTag<EditText>("history-search").text.toString())
                descendants(activity.window.decorView).single { it.contentDescription == "Clear transcript search" }.performClick()
                assertEquals("", activity.window.decorView.findViewWithTag<EditText>("history-search").text.toString())
            }
            awaitUi(scenario) { it.window.decorView.findViewWithTag<View>("transcript-card-unrelated") != null }
        } finally { scenario.onActivity { screen.close() } }
    }

    @Test fun snippetCrudRetainsDraftAndRejectsNormalizedDuplicate() = host { scenario ->
        val prefs = context.getSharedPreferences("main-screen-ui-fixture", 0)
        val state = Bundle().apply { putBoolean("snippets", true) }
        lateinit var screen: DictionaryScreen
        fun open(activity: MainActivity) {
            screen = DictionaryScreen(activity, state, prefs, database)
            activity.setContentView(ScrollView(activity).apply { addView(screen.build()) })
        }
        scenario.onActivity { activity ->
            open(activity)
            descendants(activity.window.decorView).filterIsInstance<TextView>().single { it.text == "Add snippet" }.performClick()
            val editor = dialog(screen)
            editor.window!!.decorView.findViewWithTag<EditText>("entry-title").setText("Send address")
            editor.window!!.decorView.findViewWithTag<EditText>("entry-body").setText("Ẹ káàárọ̀\nhttps://example.org/100%_done")
            screen.close(); open(activity)
            assertEquals("Send address", dialog(screen).window!!.decorView.findViewWithTag<EditText>("entry-title").text.toString())
            dialog(screen).getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        }
        try {
            awaitUi(scenario) { descendants(it.window.decorView).any { v -> v.contentDescription == "Edit Send address" } }
            assertEquals("Ẹ káàárọ̀\nhttps://example.org/100%_done", SnippetRepository(context, database).use { it.all().single().replacement })
            scenario.onActivity { activity ->
                descendants(activity.window.decorView).filterIsInstance<TextView>().single { it.text == "Add snippet" }.performClick()
                dialog(screen).window!!.decorView.findViewWithTag<EditText>("entry-title").setText(" SEND   ADDRESS ")
                dialog(screen).window!!.decorView.findViewWithTag<EditText>("entry-body").setText("duplicate")
                dialog(screen).getButton(AlertDialog.BUTTON_POSITIVE).performClick()
            }
            Thread.sleep(300)
            assertEquals(1, SnippetRepository(context, database).use { it.all().size })
            scenario.onActivity { activity ->
                assertTrue(dialog(screen).isShowing)
                dialog(screen).getButton(AlertDialog.BUTTON_NEGATIVE).performClick()
                descendants(activity.window.decorView).single { it.contentDescription == "Edit Send address" }.performClick()
                descendants(dialog(screen).window!!.decorView).filterIsInstance<SwitchMaterial>().single().isChecked = false
                dialog(screen).getButton(AlertDialog.BUTTON_POSITIVE).performClick()
            }
            awaitUi(scenario) { descendants(it.window.decorView).filterIsInstance<TextView>().any { v -> v.text.startsWith("Disabled · ") } }
            assertFalse(SnippetRepository(context, database).use { it.all().single().enabled })
            scenario.onActivity { activity ->
                descendants(activity.window.decorView).single { it.contentDescription == "Delete Send address" }.performClick()
            }
            scenario.onActivity { dialog(screen).getButton(AlertDialog.BUTTON_POSITIVE).performClick() }
            awaitUi(scenario) { descendants(it.window.decorView).filterIsInstance<TextView>().any { v -> v.text.startsWith("No snippets yet") } }
            assertTrue(SnippetRepository(context, database).use { it.all().isEmpty() })
        } finally { scenario.onActivity { screen.close() } }
    }

    @Test fun vocabularyEditorPreservesExistingAliasesAndSupportsConfirmedDelete() = host { scenario ->
        val prefs = context.getSharedPreferences("main-screen-ui-fixture", 0)
        Dictionary.save(prefs, listOf("Ṣọlá | Shola, Sola", "Ẹ káàárọ̀ | e kaaro"))
        lateinit var screen: DictionaryScreen
        scenario.onActivity { activity ->
            screen = DictionaryScreen(activity, Bundle(), prefs, database)
            activity.setContentView(ScrollView(activity).apply { addView(screen.build()) })
        }
        try {
            awaitUi(scenario) { descendants(it.window.decorView).any { v -> v.contentDescription == "Edit Ṣọlá" } }
            scenario.onActivity { activity ->
                descendants(activity.window.decorView).single { it.contentDescription == "Edit Ṣọlá" }.performClick()
                assertEquals("Shola, Sola", dialog(screen).window!!.decorView.findViewWithTag<EditText>("entry-body").text.toString())
                dialog(screen).window!!.decorView.findViewWithTag<EditText>("entry-body").setText("Shola, Sola, Scholar")
                dialog(screen).getButton(AlertDialog.BUTTON_POSITIVE).performClick()
                descendants(activity.window.decorView).filterIsInstance<TextView>().single { it.text == "Add word" }.performClick()
                dialog(screen).window!!.decorView.findViewWithTag<EditText>("entry-title").setText("WisprLite")
                dialog(screen).window!!.decorView.findViewWithTag<EditText>("entry-body").setText("whisper light")
                dialog(screen).getButton(AlertDialog.BUTTON_POSITIVE).performClick()
            }
            awaitUi(scenario) { descendants(it.window.decorView).any { v -> v.contentDescription == "Delete WisprLite" } }
            assertEquals(setOf("Ṣọlá | Shola, Sola, Scholar", "Ẹ káàárọ̀ | e kaaro", "WisprLite | whisper light"), Dictionary.load(prefs).toSet())
            scenario.onActivity { activity ->
                descendants(activity.window.decorView).single { it.contentDescription == "Delete WisprLite" }.performClick()
                dialog(screen).getButton(AlertDialog.BUTTON_POSITIVE).performClick()
            }
            awaitUi(scenario) { activity -> descendants(activity.window.decorView).none { it.contentDescription == "Delete WisprLite" } }
            assertEquals(2, Dictionary.load(prefs).size)
            assertEquals(listOf("Shola", "Sola", "Scholar"), Dictionary.parseEntries(prefs.getString(Dictionary.PREF_KEY, null)).single { it.canonical == "Ṣọlá" }.aliases)
        } finally { scenario.onActivity { screen.close() } }
    }
}
