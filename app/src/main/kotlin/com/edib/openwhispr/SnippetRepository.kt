package com.edib.openwhispr

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import java.io.Closeable
import java.text.Normalizer

data class Snippet(val id: String, val trigger: String, val replacement: String, val enabled: Boolean = true)

/** Shared by persistence, backup validation and the local matching pipeline. */
object SnippetValidation {
    fun normalizeTrigger(trigger: String): String = normalizeCase(
        trigger.replace(Regex("[\\s\\p{Z}\\u0085]+"), " ").trim())

    /** Java's Unicode case-insensitive equivalence, shared by keys and matching. */
    internal fun normalizeCase(text: String): String {
        val canonical = Normalizer.normalize(text, Normalizer.Form.NFC)
        val folded = StringBuilder()
        var offset = 0
        while (offset < canonical.length) {
            val codePoint = Character.codePointAt(canonical, offset)
            folded.appendCodePoint(Character.toLowerCase(Character.toUpperCase(codePoint)))
            offset += Character.charCount(codePoint)
        }
        return Normalizer.normalize(folded, Normalizer.Form.NFC)
    }

    fun validate(snippet: Snippet) {
        require(snippet.id.isNotBlank()) { "Snippet ID must not be blank" }
        require(normalizeTrigger(snippet.trigger).isNotBlank()) { "Snippet trigger must not be blank" }
        require(snippet.replacement.isNotBlank()) { "Snippet replacement must not be blank" }
    }

    fun validateAll(items: List<Snippet>) {
        items.forEach(::validate)
        require(items.map { it.id }.toSet().size == items.size) { "Duplicate snippet ID" }
        require(items.map { normalizeTrigger(it.trigger) }.toSet().size == items.size) { "Duplicate snippet trigger" }
    }
}

class SnippetRepository(context: Context, databaseName: String = "wisprlite.db") : Closeable {
    private val helper = HistoryDatabase(context, databaseName)

    fun all(): List<Snippet> = helper.readableDatabase.query("snippets",
        arrayOf("id", "trigger", "replacement", "enabled"), null, null, null, null,
        "normalized_trigger, id").use { cursor ->
        buildList {
            while (cursor.moveToNext()) add(Snippet(cursor.getString(0), cursor.getString(1),
                cursor.getString(2), cursor.getInt(3) != 0))
        }
    }

    fun save(snippet: Snippet) {
        SnippetValidation.validate(snippet)
        transaction { db ->
            require(!db.rawQuery("SELECT 1 FROM snippets WHERE normalized_trigger=? AND id<>?",
                arrayOf(SnippetValidation.normalizeTrigger(snippet.trigger), snippet.id)).use { it.moveToFirst() }) {
                "Duplicate snippet trigger"
            }
            val values = values(snippet)
            if (db.update("snippets", values, "id=?", arrayOf(snippet.id)) == 0) {
                db.insertOrThrow("snippets", null, values)
            }
        }
    }

    fun delete(id: String) { helper.writableDatabase.delete("snippets", "id=?", arrayOf(id)) }

    fun replaceAll(items: List<Snippet>) {
        SnippetValidation.validateAll(items)
        transaction { db ->
            db.delete("snippets", null, null)
            items.forEach { db.insertOrThrow("snippets", null, values(it)) }
        }
    }

    private fun values(snippet: Snippet) = ContentValues().apply {
        put("id", snippet.id); put("trigger", snippet.trigger); put("replacement", snippet.replacement)
        put("enabled", if (snippet.enabled) 1 else 0)
        put("normalized_trigger", SnippetValidation.normalizeTrigger(snippet.trigger))
    }

    private fun transaction(block: (SQLiteDatabase) -> Unit) {
        val db = helper.writableDatabase
        db.beginTransaction()
        try { block(db); db.setTransactionSuccessful() } finally { db.endTransaction() }
    }

    override fun close() = helper.close()
}
