package com.edib.openwhispr

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/** Text and counters remain entirely in app-private storage. */
class HistoryDatabase(context: Context) : SQLiteOpenHelper(context.applicationContext, "wisprlite.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE transcripts (id TEXT PRIMARY KEY, created_at INTEGER NOT NULL, original TEXT NOT NULL, cleaned TEXT, speaking_ms INTEGER NOT NULL, mode TEXT NOT NULL)")
        db.execSQL("CREATE INDEX transcript_date ON transcripts(created_at DESC, id DESC)")
        db.execSQL("CREATE TABLE events (id TEXT PRIMARY KEY, created_at INTEGER NOT NULL, words INTEGER NOT NULL, speaking_ms INTEGER NOT NULL)")
        db.execSQL("CREATE INDEX event_date ON events(created_at)")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        check(oldVersion == newVersion) { "Unsupported history schema upgrade" }
    }
}

data class DictationRecord(val id: String, val createdAt: Long, val original: String,
    val cleaned: String?, val speakingMillis: Long, val mode: String)
data class InsightTotals(val recordings: Long, val words: Long, val speakingMillis: Long)
data class HistoryCursor(val timestamp: Long, val id: String)

class HistoryRepository(context: Context) : java.io.Closeable {
    private val helper = HistoryDatabase(context)

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
                })
            }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    fun updateCleanup(id: String, text: String) {
        helper.writableDatabase.update("transcripts", ContentValues().apply { put("cleaned", text) }, "id=?", arrayOf(id))
    }

    fun listBefore(cursor: HistoryCursor? = null, limit: Int = 40): List<DictationRecord> {
        require(limit in 1..100)
        val where = if (cursor == null) null else "created_at < ? OR (created_at = ? AND id < ?)"
        val args = cursor?.let { arrayOf(it.timestamp.toString(), it.timestamp.toString(), it.id) }
        return helper.readableDatabase.query("transcripts", null, where, args, null, null,
            "created_at DESC, id DESC", limit.toString()).use { c ->
            buildList {
                while (c.moveToNext()) add(DictationRecord(c.getString(0), c.getLong(1), c.getString(2),
                    if (c.isNull(3)) null else c.getString(3), c.getLong(4), c.getString(5)))
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
    override fun close() = helper.close()
}
