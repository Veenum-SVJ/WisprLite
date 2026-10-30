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
        context.deleteDatabase("wisprlite.db")
        repo = HistoryRepository(context)
    }
    @After fun finish() { repo.close() }
    @Test fun unicodeAndExactlyOnceCountersSurviveHistoryDeletion() {
        val record = DictationRecord("one", 1000, "Ẹ káàárọ̀, abeg send am", null, 3000, "Direct")
        repo.saveSuccessful(record); repo.saveSuccessful(record)
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
}
