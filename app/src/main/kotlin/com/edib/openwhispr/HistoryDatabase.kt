package com.edib.openwhispr

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/** Text and counters remain entirely in app-private storage. */
class HistoryDatabase(context: Context, databaseName: String = "wisprlite.db") : SQLiteOpenHelper(context.applicationContext, databaseName, null, 2) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE transcripts (id TEXT PRIMARY KEY, created_at INTEGER NOT NULL, original TEXT NOT NULL, cleaned TEXT, speaking_ms INTEGER NOT NULL, mode TEXT NOT NULL, expanded TEXT, snippet_state TEXT)")
        db.execSQL("CREATE INDEX transcript_date ON transcripts(created_at DESC, id DESC)")
        db.execSQL("CREATE TABLE events (id TEXT PRIMARY KEY, created_at INTEGER NOT NULL, words INTEGER NOT NULL, speaking_ms INTEGER NOT NULL)")
        db.execSQL("CREATE INDEX event_date ON events(created_at)")
        createSnippets(db)
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        check(oldVersion == newVersion || oldVersion == 1 && newVersion == 2) { "Unsupported history schema upgrade" }
        if (oldVersion == newVersion) return
        // SQLiteOpenHelper already wraps upgrades in a transaction. The nested transaction
        // also preserves atomicity when the migration is invoked directly.
        db.beginTransaction()
        try {
            db.execSQL("ALTER TABLE transcripts ADD COLUMN expanded TEXT")
            db.execSQL("ALTER TABLE transcripts ADD COLUMN snippet_state TEXT")
            createSnippets(db)
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    private fun createSnippets(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE snippets (id TEXT PRIMARY KEY NOT NULL, trigger TEXT NOT NULL, replacement TEXT NOT NULL, enabled INTEGER NOT NULL, normalized_trigger TEXT NOT NULL UNIQUE)")
    }
}

data class DictationRecord(val id: String, val createdAt: Long, val original: String,
    val cleaned: String?, val speakingMillis: Long, val mode: String, val expanded: String? = null,
    val snippetState: String? = null) {
    val delivered: String get() = cleaned ?: expanded ?: original
}
data class InsightTotals(val recordings: Long, val words: Long, val speakingMillis: Long)
data class HistoryCursor(val timestamp: Long, val id: String)

class HistoryRepository(context: Context, databaseName: String = "wisprlite.db") : java.io.Closeable {
    private val helper = HistoryDatabase(context, databaseName)

    fun saveSuccessful(record: DictationRecord) {
        require(record.original.isNotBlank() && record.speakingMillis >= 0)
        val db = helper.writableDatabase
        db.beginTransaction()
        try {
            val existing = db.rawQuery("SELECT 1 FROM transcripts WHERE id=?", arrayOf(record.id)).use { it.moveToFirst() }
            if (existing) { db.setTransactionSuccessful(); return }
            val event = ContentValues().apply {
                put("id", record.id); put("created_at", record.createdAt)
                put("words", InsightCalculator.wordCount(record.original)); put("speaking_ms", record.speakingMillis)
            }
            if (db.insertWithOnConflict("events", null, event, SQLiteDatabase.CONFLICT_IGNORE) != -1L) {
                db.insertOrThrow("transcripts", null, ContentValues().apply {
                    put("id", record.id); put("created_at", record.createdAt); put("original", record.original)
                    put("cleaned", record.cleaned); put("speaking_ms", record.speakingMillis); put("mode", record.mode)
                    put("expanded", record.expanded)
                    put("snippet_state", record.snippetState)
                })
            }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    fun updateCleanup(id: String, text: String) {
        helper.writableDatabase.update("transcripts", ContentValues().apply { put("cleaned", text) }, "id=?", arrayOf(id))
    }

    fun listBefore(cursor: HistoryCursor? = null, limit: Int = 40, search: String = ""): List<DictationRecord> {
        require(limit in 1..100)
        val clauses = mutableListOf<String>()
        val args = mutableListOf<String>()
        if (cursor != null) {
            clauses += "(created_at < ? OR (created_at = ? AND id < ?))"
            args += listOf(cursor.timestamp.toString(), cursor.timestamp.toString(), cursor.id)
        }
        if (search.isNotEmpty()) {
            clauses += "(original LIKE ? ESCAPE '\\' OR expanded LIKE ? ESCAPE '\\' OR cleaned LIKE ? ESCAPE '\\')"
            val literal = "%" + search.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%"
            repeat(3) { args += literal }
        }
        return helper.readableDatabase.query("transcripts", arrayOf("id", "created_at", "original", "cleaned", "speaking_ms", "mode", "expanded", "snippet_state"),
            clauses.takeIf { it.isNotEmpty() }?.joinToString(" AND "), args.toTypedArray(), null, null,
            "created_at DESC, id DESC", limit.toString()).use { c ->
            buildList {
                while (c.moveToNext()) add(DictationRecord(c.getString(0), c.getLong(1), c.getString(2),
                    if (c.isNull(3)) null else c.getString(3), c.getLong(4), c.getString(5),
                    if (c.isNull(6)) null else c.getString(6), if (c.isNull(7)) null else c.getString(7)))
            }
        }
    }

    fun delete(id: String) { helper.writableDatabase.delete("transcripts", "id=?", arrayOf(id)) }
    fun clearHistory() { helper.writableDatabase.delete("transcripts", null, null) }
    fun expireText(before: Long) { helper.writableDatabase.delete("transcripts", "created_at < ?", arrayOf(before.toString())) }
    fun totals(since: Long = 0): InsightTotals = helper.readableDatabase.rawQuery(
        "SELECT COUNT(*), COALESCE(SUM(words),0), COALESCE(SUM(speaking_ms),0) FROM events WHERE created_at >= ?",
        arrayOf(since.toString())).use { c -> c.moveToFirst(); InsightTotals(c.getLong(0), c.getLong(1), c.getLong(2)) }
    fun resetStatistics() { helper.writableDatabase.delete("events", null, null) }
    fun events(): List<SpeakingEvent> = helper.readableDatabase.rawQuery(
        "SELECT created_at, words, speaking_ms FROM events ORDER BY created_at, id", null).use { c ->
        buildList { while (c.moveToNext()) add(SpeakingEvent(c.getLong(0), c.getLong(1), c.getLong(2))) }
    }
    override fun close() = helper.close()
}
