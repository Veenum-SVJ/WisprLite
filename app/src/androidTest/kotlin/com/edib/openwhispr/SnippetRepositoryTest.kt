package com.edib.openwhispr

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SnippetRepositoryTest {
    private val name = "wisprlite-snippet-fixture.db"
    private lateinit var repo: SnippetRepository

    @Before fun setup() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(name)
        repo = SnippetRepository(context, name)
    }

    @After fun cleanup() {
        repo.close()
        InstrumentationRegistry.getInstrumentation().targetContext.deleteDatabase(name)
    }

    @Test fun unicodeMultilineAndEnabledStatePersistAcrossReopenAndEdit() {
        val item = Snippet("one", "my address", "Ẹ\nhttps://example.org/?x=1&y=2", false)
        repo.save(item)
        repo.close()
        repo = SnippetRepository(InstrumentationRegistry.getInstrumentation().targetContext, name)
        assertEquals(listOf(item), repo.all())
        repo.save(item.copy(trigger = "my location", enabled = true))
        assertEquals(listOf(item.copy(trigger = "my location", enabled = true)), repo.all())
        repo.delete(item.id)
        assertTrue(repo.all().isEmpty())
    }

    @Test fun duplicateTriggersAndInvalidReplacementsPreserveAllExistingData() {
        val first = Snippet("first", "  MY\u00a0address", "first")
        val second = Snippet("second", "email", "second")
        repo.replaceAll(listOf(first, second))
        val before = repo.all()
        assertThrows(IllegalArgumentException::class.java) { repo.save(Snippet("third", "my address", "collision")) }
        assertEquals(before, repo.all())
        assertThrows(IllegalArgumentException::class.java) { repo.save(second.copy(trigger = "my address")) }
        assertEquals(before, repo.all())
        assertThrows(IllegalArgumentException::class.java) { repo.replaceAll(listOf(first, second.copy(trigger = "my address"))) }
        assertThrows(IllegalArgumentException::class.java) { repo.replaceAll(listOf(first, first.copy(trigger = "unique"))) }
        assertThrows(IllegalArgumentException::class.java) { repo.replaceAll(listOf(first.copy(replacement = "\n"))) }
        assertEquals(before, repo.all())
    }

    @Test fun failingReplacementTransactionRestoresPreviousSnippets() {
        val first = Snippet("first", "address", "saved")
        repo.save(first)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        HistoryDatabase(context, name).use { helper ->
            helper.writableDatabase.execSQL("CREATE TRIGGER reject_snippet BEFORE INSERT ON snippets WHEN NEW.id = 'fail' BEGIN SELECT RAISE(ABORT, 'synthetic failure'); END")
        }
        assertThrows(android.database.sqlite.SQLiteException::class.java) {
            repo.replaceAll(listOf(Snippet("ok", "okay", "one"), Snippet("fail", "fails", "two")))
        }
        assertEquals(listOf(first), repo.all())
    }
}
