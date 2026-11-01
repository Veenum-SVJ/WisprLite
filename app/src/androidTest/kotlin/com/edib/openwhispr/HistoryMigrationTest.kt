package com.edib.openwhispr

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HistoryMigrationTest {
    private val name = "wisprlite-migration-fixture.db"
    private lateinit var context: Context

    @Before fun setup() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(name)
    }

    @After fun cleanup() { context.deleteDatabase(name) }

    private fun createV1() {
        SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(name).apply { parentFile?.mkdirs() }, null).use { db ->
            db.execSQL("CREATE TABLE transcripts (id TEXT PRIMARY KEY, created_at INTEGER NOT NULL, original TEXT NOT NULL, cleaned TEXT, speaking_ms INTEGER NOT NULL, mode TEXT NOT NULL)")
            db.execSQL("CREATE INDEX transcript_date ON transcripts(created_at DESC, id DESC)")
            db.execSQL("CREATE TABLE events (id TEXT PRIMARY KEY, created_at INTEGER NOT NULL, words INTEGER NOT NULL, speaking_ms INTEGER NOT NULL)")
            db.execSQL("CREATE INDEX event_date ON events(created_at)")
            db.execSQL("INSERT INTO transcripts VALUES (?, ?, ?, ?, ?, ?)", arrayOf("old", 1234L, "Ẹ káàárọ̀", "Ẹ káàárọ̀!", 500L, "Cleanup"))
            db.execSQL("INSERT INTO events VALUES (?, ?, ?, ?)", arrayOf("old", 1234L, 2L, 500L))
            // A retained event whose transcript was deleted must also survive migration.
            db.execSQL("INSERT INTO events VALUES (?, ?, ?, ?)", arrayOf("deleted-text", 2345L, 3L, 600L))
            db.version = 1
        }
    }

    @Test fun realV1UpgradePreservesRowsCountersAndSupportsNewData() {
        createV1()
        HistoryRepository(context, name).use { repo ->
            assertEquals(DictationRecord("old", 1234, "Ẹ káàárọ̀", "Ẹ káàárọ̀!", 500, "Cleanup"), repo.listBefore().single())
            assertEquals(InsightTotals(2, 5, 1100), repo.totals())
            assertEquals(listOf(SpeakingEvent(1234, 2, 500), SpeakingEvent(2345, 3, 600)), repo.events())
            val capture = SnippetExpander.prepare("my address", listOf(Snippet("capture", "my address", "Exact\nSaved Text")))
            val expanded = DictationRecord("new", 3456, "my address", null, 700, "Direct", capture.expanded, capture.serialize())
            repo.saveSuccessful(expanded)
            assertEquals(expanded, repo.listBefore().first())
        }
        SnippetRepository(context, name).use { repo ->
            val snippet = Snippet("snippet", "my address", "Exact\nSaved Text")
            repo.save(snippet)
            assertEquals(listOf(snippet), repo.all())
        }
        HistoryDatabase(context, name).use { assertEquals(2, it.readableDatabase.version) }
    }

    @Test fun failedMigrationRollsBackAddedColumnAndVersion() {
        createV1()
        SQLiteDatabase.openDatabase(context.getDatabasePath(name).path, null, SQLiteDatabase.OPEN_READWRITE).use {
            // Force the second migration statement to fail after ALTER TABLE.
            it.execSQL("CREATE TABLE snippets (collision TEXT)")
        }
        HistoryDatabase(context, name).use { helper ->
            try { helper.writableDatabase; fail("Migration must fail on an incompatible schema") }
            catch (_: android.database.sqlite.SQLiteException) { }
        }
        SQLiteDatabase.openDatabase(context.getDatabasePath(name).path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
            assertEquals(1, db.version)
            val columns = db.rawQuery("PRAGMA table_info(transcripts)", null).use { c ->
                buildList { while (c.moveToNext()) add(c.getString(1)) }
            }
            assertFalse(columns.contains("expanded"))
            assertFalse(columns.contains("snippet_state"))
            assertEquals(2L, db.rawQuery("SELECT COUNT(*) FROM events", null).use { it.moveToFirst(); it.getLong(0) })
        }
    }
}
