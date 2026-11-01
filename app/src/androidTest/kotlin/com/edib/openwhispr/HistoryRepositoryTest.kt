package com.edib.openwhispr

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HistoryRepositoryTest {
    private lateinit var repo: HistoryRepository
    @Before fun setup() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase("wisprlite-instrumentation.db")
        repo = HistoryRepository(context, "wisprlite-instrumentation.db")
    }
    @After fun finish() { repo.close() }
    @Test fun unicodeAndExactlyOnceCountersSurviveHistoryDeletion() {
        val record = DictationRecord("one", 1000, "Ẹ káàárọ̀, abeg send am", null, 3000, "Direct")
        repo.saveSuccessful(record)
        repo.close()
        repo = HistoryRepository(InstrumentationRegistry.getInstrumentation().targetContext, "wisprlite-instrumentation.db")
        repo.saveSuccessful(record)
        assertEquals(record, repo.listBefore().single())
        assertEquals(1L, repo.totals().recordings)
        repo.updateCleanup("one", "Ẹ káàárọ̀, abeg send am.")
        assertEquals(1L, repo.totals().recordings)
        repo.delete("one")
        assertTrue(repo.listBefore().isEmpty())
        assertEquals(1L, repo.totals().recordings)
        repo.saveSuccessful(record)
        assertTrue(repo.listBefore().isEmpty())
        repo.resetStatistics()
        assertEquals(0L, repo.totals().recordings)
    }
    @Test fun paginationDoesNotSkipIdenticalTimestampsAndExpiryKeepsCounters() {
        for (id in listOf("a", "b", "c")) repo.saveSuccessful(DictationRecord(id, 1000, id, null, 100, "Direct"))
        val first = repo.listBefore(limit = 2)
        val last = first.last()
        assertEquals(listOf("a"), repo.listBefore(HistoryCursor(last.createdAt, last.id), 2).map { it.id })
        repo.expireText(1001)
        assertTrue(repo.listBefore().isEmpty())
        assertEquals(3L, repo.totals().recordings)
    }

    @Test fun failedTranscriptInsertRollsBackItsStatisticsEvent() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        HistoryDatabase(context, "wisprlite-instrumentation.db").use { helper ->
            helper.writableDatabase.execSQL("CREATE TRIGGER reject_fixture BEFORE INSERT ON transcripts BEGIN SELECT RAISE(ABORT, 'synthetic write failure'); END")
        }
        try {
            repo.saveSuccessful(DictationRecord("failed", 1000, "Ẹ káàárọ̀", null, 100, "Direct"))
            fail("The synthetic database failure must reach the caller")
        } catch (_: android.database.sqlite.SQLiteException) { }
        assertTrue(repo.listBefore().isEmpty())
        assertEquals(InsightTotals(0, 0, 0), repo.totals())
        HistoryDatabase(context, "wisprlite-instrumentation.db").use { helper ->
            helper.writableDatabase.execSQL("DROP TRIGGER reject_fixture")
        }
        val retry = DictationRecord("failed", 1000, "Ẹ káàárọ̀", null, 100, "Direct")
        repo.saveSuccessful(retry)
        assertEquals(retry, repo.listBefore().single())
        assertEquals(1L, repo.totals().recordings)
    }

    @Test fun unsupportedUpgradePreservesExistingSchemaAndUnicodeData() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "wisprlite-instrumentation.db"
        val record = DictationRecord("persisted", 1000, "Ṣọlá, báwo ni?", "Ṣọlá, báwo ni?", 200, "Cleanup")
        repo.saveSuccessful(record)
        repo.close()
        // Version 1 is the only released schema. Future migrations must be explicit;
        // an unsupported upgrade must not reset tables or partially change user_version.
        val production = HistoryDatabase(context, name)
        val future = object : android.database.sqlite.SQLiteOpenHelper(context, name, null, 2) {
            override fun onCreate(db: android.database.sqlite.SQLiteDatabase) = error("Existing fixture database required")
            override fun onUpgrade(db: android.database.sqlite.SQLiteDatabase, oldVersion: Int, newVersion: Int) =
                production.onUpgrade(db, oldVersion, newVersion)
        }
        try {
            try { future.writableDatabase; fail("Unsupported migration must fail closed") }
            catch (_: IllegalStateException) { }
        } finally { future.close(); production.close() }
        repo = HistoryRepository(context, name)
        assertEquals(record, repo.listBefore().single())
        assertEquals(1L, repo.totals().recordings)
        HistoryDatabase(context, name).use { assertEquals(1, it.readableDatabase.version) }
    }
}
