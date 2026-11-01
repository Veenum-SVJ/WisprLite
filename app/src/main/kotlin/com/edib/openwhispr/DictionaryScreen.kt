package com.edib.openwhispr

import android.app.AlertDialog
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.switchmaterial.SwitchMaterial
import java.util.UUID
import java.util.concurrent.Executors

/** Vocabulary preferences keep their existing representation; snippets use the shared database. */
internal class DictionaryScreen(
    private val activity: AppCompatActivity,
    private val state: Bundle,
    private val prefs: SharedPreferences = activity.getSharedPreferences("openwhispr", Context.MODE_PRIVATE),
    private val databaseName: String = "wisprlite.db"
) : AutoCloseable {
    private val worker = Executors.newSingleThreadExecutor()
    private val root = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
    private val list = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
    private var closed = false
    private var generation = 0
    private var dialog: AlertDialog? = null
    private val snippets get() = state.getBoolean("snippets")
    private fun dp(n: Int) = ModernUi.dp(activity, n)

    fun build(): View {
        val tabs = LinearLayout(activity)
        for ((label, tab) in listOf("Dictionary" to false, "Snippets" to true)) {
            tabs.addView(screenButton(activity, label) {
                state.putBoolean("snippets", tab); rebuild()
            }.apply {
                tag = "dictionary-tab-$label"; isSelected = snippets == tab
                if (snippets == tab) backgroundTintList = android.content.res.ColorStateList.valueOf(ModernUi.color(activity, R.color.primary_container))
            }, LinearLayout.LayoutParams(0, -2, 1f))
        }
        root.addView(tabs)
        root.addView(ModernUi.label(activity, if (snippets)
            "Say a trigger to insert its saved text. Choose uncommon phrases to avoid accidental expansion."
            else "Names and phrases help recognition. Aliases are hints; this does not train a model or guarantee recognition.", 13f, muted = true).apply { setPadding(0, dp(12), 0, dp(12)) })
        val search = screenInput(activity, if (snippets) "Search snippets" else "Search words and aliases").apply {
            tag = "dictionary-search"; setSingleLine(); setText(state.getString("search", ""))
            onChange { state.putString("search", it); load() }
        }
        root.addView(search)
        root.addView(LinearLayout(activity).apply {
            addView(screenButton(activity, if (snippets) "Add snippet" else "Add word") { edit(null, null) }, LinearLayout.LayoutParams(0, -2, 1f))
            addView(screenButton(activity, if (state.getBoolean("descending")) "Sort Z–A" else "Sort A–Z") {
                state.putBoolean("descending", !state.getBoolean("descending")); rebuild()
            })
        })
        root.addView(list)
        load()
        if (state.getBoolean("editing")) edit(state.getString("edit_word"), state.getString("edit_id"), restore = true)
        return root
    }

    private fun rebuild() {
        dialog?.dismiss(); dialog = null; root.removeAllViews(); build()
    }

    private fun load() {
        val token = ++generation
        val tab = snippets
        val filter = state.getString("search", "").orEmpty()
        val descending = state.getBoolean("descending")
        worker.execute {
            try {
                val words = if (tab) emptyList() else Dictionary.parseEntries(prefs.getString(Dictionary.PREF_KEY, null))
                    .filter { (listOf(it.canonical) + it.aliases).any { word -> word.contains(filter, true) } }
                    .sortedBy { it.canonical.lowercase() }.let { if (descending) it.reversed() else it }
                val entries = if (!tab) emptyList() else SnippetRepository(activity, databaseName).use { it.all() }
                    .filter { it.trigger.contains(filter, true) || it.replacement.contains(filter, true) }
                    .sortedBy { it.trigger.lowercase() }.let { if (descending) it.reversed() else it }
                activity.runOnUiThread {
                    if (closed || generation != token) return@runOnUiThread
                    list.removeAllViews()
                    if (words.isEmpty() && entries.isEmpty()) list.addView(ModernUi.label(activity,
                        if (filter.isNotBlank()) "No matching entries. Clear the search to see all entries."
                        else if (tab) "No snippets yet. Add a trigger and the text it should insert."
                        else "No words yet. Add a name or phrase you use often.", 14f, muted = true).apply { setPadding(0, dp(20), 0, dp(20)) })
                    words.forEach { word ->
                        addRow(word.canonical, word.aliases.joinToString(", "), "word-${word.canonical}",
                            { edit(word.canonical, null) }, { confirmDelete(word.canonical) { deleteWord(word.canonical) } })
                    }
                    entries.forEach { entry ->
                        addRow(entry.trigger, (if (entry.enabled) "Enabled · " else "Disabled · ") + entry.replacement,
                            "snippet-${entry.id}", { edit(null, entry.id, snippet = entry) },
                            { confirmDelete("snippet") { write({ it.delete(entry.id) }) } },
                            { AlertDialog.Builder(activity).setTitle("Snippet preview").setMessage(entry.replacement).setPositiveButton("Close", null).show() })
                    }
                }
            } catch (_: Exception) {
                activity.runOnUiThread { if (!closed && generation == token) {
                    list.removeAllViews(); list.addView(screenButton(activity, "Couldn’t load entries · Retry") { load() })
                } }
            }
        }
    }

    private fun addRow(title: String, subtitle: String, key: String, edit: () -> Unit, delete: () -> Unit, preview: (() -> Unit)? = null) {
        list.addView(LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL; tag = key; background = ModernUi.surface(activity)
            setPadding(dp(14), dp(12), dp(14), dp(4))
            addView(ModernUi.label(activity, title, 16f, strong = true))
            if (subtitle.isNotEmpty()) addView(ModernUi.label(activity, subtitle, 13f, muted = true).apply { maxLines = 3; ellipsize = android.text.TextUtils.TruncateAt.END })
            addView(LinearLayout(activity).apply {
                addView(screenButton(activity, "Edit", edit).apply { contentDescription = "Edit $title" })
                if (preview != null) addView(screenButton(activity, "Preview", preview).apply { contentDescription = "Preview $title" })
                addView(screenButton(activity, "Delete", delete).apply { contentDescription = "Delete $title" })
            })
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
    }

    private fun confirmDelete(label: String, action: () -> Unit) {
        dialog = AlertDialog.Builder(activity).setTitle("Delete $label?").setMessage("This removes the saved entry.")
            .setNegativeButton("Cancel", null).setPositiveButton("Delete") { _, _ -> action() }.create()
        dialog?.show()
    }

    private fun deleteWord(canonical: String) {
        Dictionary.save(prefs, Dictionary.parseEntries(prefs.getString(Dictionary.PREF_KEY, null)).filterNot { it.canonical == canonical }.map { it.asLine() })
        WhisperAccessibilityService.instance?.reloadModel(); load()
    }

    private fun write(action: (SnippetRepository) -> Unit, done: () -> Unit = {}, failed: () -> Unit = {}) {
        worker.execute {
            val ok = runCatching { SnippetRepository(activity, databaseName).use(action) }.isSuccess
            activity.runOnUiThread {
                if (closed) return@runOnUiThread
                if (ok) { done(); load() } else {
                    failed()
                    Toast.makeText(activity, "Couldn’t save. Use a unique trigger and non-empty saved text, then retry.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun edit(word: String?, id: String?, restore: Boolean = false, snippet: Snippet? = null) {
        if (!restore) {
            val entry = Dictionary.parseEntries(prefs.getString(Dictionary.PREF_KEY, null)).find { it.canonical == word }
            state.putString("edit_word", word); state.putString("edit_id", id)
            state.putString("draft_title", snippet?.trigger ?: entry?.canonical.orEmpty())
            state.putString("draft_body", snippet?.replacement ?: entry?.aliases?.joinToString(", ").orEmpty())
            state.putBoolean("draft_enabled", snippet?.enabled ?: true)
        }
        state.putBoolean("editing", true)
        val isSnippet = snippets
        val fields = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(8), dp(20), 0) }
        val first = screenInput(activity, if (isSnippet) "Spoken trigger" else "Canonical word or phrase").apply {
            tag = "entry-title"; setSingleLine(); setText(state.getString("draft_title", ""))
            onChange { state.putString("draft_title", it) }
        }
        val second = screenInput(activity, if (isSnippet) "Saved text" else "Aliases, separated by commas").apply {
            tag = "entry-body"; minLines = if (isSnippet) 4 else 2; maxLines = 8; isVerticalScrollBarEnabled = true; gravity = android.view.Gravity.TOP
            setText(state.getString("draft_body", "")); onChange { state.putString("draft_body", it) }
        }
        fields.addView(first); fields.addView(second)
        val enabled = SwitchMaterial(activity).apply {
            text = "Enabled"; contentDescription = "Enable this snippet"; isChecked = state.getBoolean("draft_enabled", true)
            setOnCheckedChangeListener { _, value -> state.putBoolean("draft_enabled", value) }
        }
        if (isSnippet) fields.addView(enabled)
        val editor = AlertDialog.Builder(activity).setTitle(if (isSnippet) "Snippet" else "Dictionary word")
            .setView(ScrollView(activity).apply { isFillViewport = true; addView(fields) }).setNegativeButton("Cancel") { _, _ -> state.putBoolean("editing", false) }
            .setPositiveButton("Save", null).create()
        dialog = editor
        editor.setOnCancelListener { state.putBoolean("editing", false) }
        editor.show()
        run {
            editor.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val title = first.text.toString().trim()
                val body = second.text.toString()
                if (title.isBlank() || (!isSnippet && (title.contains('|') || title.contains('\n'))) || (isSnippet && body.isBlank())) {
                    first.error = if (isSnippet) "Enter a trigger and saved text" else "Enter one word or phrase without |"; return@setOnClickListener
                }
                if (!isSnippet && (body.contains('|') || body.contains('\n'))) {
                    second.error = "Separate aliases with commas; use one line without |"; return@setOnClickListener
                }
                fun finish() { if (dialog === editor) { state.putBoolean("editing", false); editor.dismiss(); dialog = null } }
                if (isSnippet) {
                    val candidate = Snippet(id ?: state.getString("edit_id") ?: UUID.randomUUID().toString(), title, body, enabled.isChecked)
                    // Reusing the draft ID makes a save retried after rotation idempotent.
                    state.putString("edit_id", candidate.id)
                    editor.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false
                    editor.getButton(AlertDialog.BUTTON_NEGATIVE).isEnabled = false
                    editor.setCancelable(false)
                    write(action = { repo -> SnippetValidation.validateAll(repo.all().filterNot { it.id == candidate.id } + candidate); repo.save(candidate) },
                        done = { finish() }, failed = {
                            if (dialog === editor) {
                                editor.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = true
                                editor.getButton(AlertDialog.BUTTON_NEGATIVE).isEnabled = true
                                editor.setCancelable(true)
                            }
                        })
                } else {
                    val existing = Dictionary.parseEntries(prefs.getString(Dictionary.PREF_KEY, null))
                    if (existing.any { it.canonical != word && it.canonical.equals(title, true) }) {
                        first.error = "This word already exists. Edit that entry instead."; return@setOnClickListener
                    }
                    val replacement = Dictionary.Entry(title, body.split(',').map(String::trim).filter { it.isNotBlank() })
                    Dictionary.save(prefs, existing.filterNot { it.canonical == word }.map { it.asLine() } + replacement.asLine())
                    WhisperAccessibilityService.instance?.reloadModel(); finish(); load()
                }
            }
        }
    }

    override fun close() { closed = true; generation++; dialog?.dismiss(); dialog = null; worker.shutdownNow() }
}
