package com.edib.openwhispr

import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import java.text.DateFormat
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

internal class HomeHistoryScreen(private val activity: AppCompatActivity, private val state: Bundle,
    private val openSettings: () -> Unit, private val databaseName: String = "wisprlite.db") : AutoCloseable {
    private val worker = Executors.newSingleThreadScheduledExecutor()
    private val prefs = activity.getSharedPreferences("openwhispr", Context.MODE_PRIVATE)
    private val root = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
    private val content = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
    private val cleanupPending = mutableSetOf<String>()
    private var generation = 0
    private var closed = false
    private var pending: ScheduledFuture<*>? = null
    private var cursor: HistoryCursor? = null
    private var lastDay: LocalDate? = null
    private var group: LinearLayout? = null
    private var updateControls: () -> Unit = {}
    private fun dp(n: Int) = ModernUi.dp(activity, n)
    private fun button(label: String, action: () -> Unit) = screenButton(activity, label, action)
    private fun text(value: String) = ModernUi.label(activity, value, 14f, muted = true).apply { setPadding(0, dp(18), 0, dp(18)) }
    private fun <T> query(action: (HistoryRepository) -> T, result: (T) -> Unit) {
        val token = generation
        worker.execute {
            val outcome = runCatching { HistoryRepository(activity, databaseName).use(action) }
            activity.runOnUiThread {
                if (closed || activity.isDestroyed || token != generation) return@runOnUiThread
                outcome.fold(result) { content.addView(button("Couldn’t load history · Retry") { refresh() }) }
            }
        }
    }
    fun build(): View {
        root.addView(LinearLayout(activity).apply {
            setPadding(0, 0, 0, dp(8))
            val mode = button("") { }
            fun updateMode() { mode.text = if (!prefs.getBoolean("use_post_processing", false)) "Direct transcription" else if (cleanupConfigured()) "AI Cleanup" else "Cleanup · needs key" }
            updateMode()
            mode.setOnClickListener {
                val enabled = prefs.getBoolean("use_post_processing", false)
                if (!enabled && !cleanupConfigured()) AlertDialog.Builder(activity).setTitle("Set up AI cleanup")
                    .setMessage("Add a cleanup provider and API key in Settings. Direct transcription is available without cleanup.")
                    .setPositiveButton("Open Settings") { _, _ -> openSettings() }.setNegativeButton("Cancel", null).show()
                else { prefs.edit().putBoolean("use_post_processing", !enabled).apply(); updateMode() }
            }
            addView(mode, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(6) })
            val tone = button("") { }
            fun updateTone() { tone.text = ToneRules(prefs).nextOverride()?.title ?: "Automatic / app style" }
            updateTone(); tone.contentDescription = "Tone for next dictation"
            tone.setOnClickListener {
                val rules = ToneRules(prefs); val entries = Tone.entries
                val labels = (listOf("Automatic / app style") + entries.map { it.title }).toTypedArray()
                val chosen = rules.nextOverride()?.let { entries.indexOf(it) + 1 } ?: 0
                AlertDialog.Builder(activity).setTitle("Next dictation tone")
                    .setSingleChoiceItems(labels, chosen) { dialog, index ->
                        rules.overrideNext(if (index == 0) null else entries[index - 1]); updateTone(); dialog.dismiss()
                        if (!prefs.getBoolean("use_post_processing", false)) Toast.makeText(activity,
                            "Direct mode does not use AI tone. The choice applies to the next dictation; Cleanup remains off.", Toast.LENGTH_LONG).show()
                    }.setNegativeButton("Cancel", null).show()
            }
            addView(tone, LinearLayout.LayoutParams(0, -2, 1f))
            updateControls = { updateMode(); updateTone() }
        })
        val searchRow = LinearLayout(activity).apply { gravity = Gravity.CENTER_VERTICAL }
        val search = screenInput(activity, "Search transcripts").apply {
            tag = "history-search"; setSingleLine(); setText(state.getString("search", ""))
            background = ModernUi.surface(activity, muted = true, radius = 18)
            val searchIcon = androidx.core.content.ContextCompat.getDrawable(activity, R.drawable.ic_ui_search)?.mutate()
            searchIcon?.setTint(ModernUi.color(activity, R.color.ink_muted))
            searchIcon?.setBounds(0, 0, dp(16), dp(16))
            setCompoundDrawablesRelative(searchIcon, null, null, null); compoundDrawablePadding = dp(8)
        }
        searchRow.addView(search, LinearLayout.LayoutParams(0, -2, 1f))
        val clear = button("") { search.setText("") }.apply {
            contentDescription = "Clear transcript search"
            icon = androidx.core.content.ContextCompat.getDrawable(activity, R.drawable.ic_ui_close)
            iconSize = dp(16); iconPadding = 0; minWidth = 0
            strokeWidth = 0; backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.TRANSPARENT)
            iconTint = android.content.res.ColorStateList.valueOf(ModernUi.color(activity, R.color.ink_muted))
            visibility = if (search.text.isNullOrEmpty()) View.GONE else View.VISIBLE
        }
        searchRow.addView(clear, LinearLayout.LayoutParams(dp(48), dp(48)))
        root.addView(searchRow); root.addView(content)
        search.onChange { value ->
            state.putString("search", value); clear.visibility = if (value.isEmpty()) View.GONE else View.VISIBLE
            generation++; pending?.cancel(false)
            val token = generation
            pending = worker.schedule({ activity.runOnUiThread { if (!closed && token == generation) refresh() } }, 250, TimeUnit.MILLISECONDS)
        }
        refresh(); return root
    }
    fun refresh() {
        updateControls()
        generation++; pending?.cancel(false); cursor = null; lastDay = null; group = null
        content.removeAllViews(); page()
    }
    private fun page() {
        val search = state.getString("search", "").orEmpty()
        val pageCursor = cursor
        query({ it.listBefore(pageCursor, search = search) }) { records ->
            if (records.isEmpty() && pageCursor == null) content.addView(text(if (search.isBlank())
                "No transcripts yet. Dictate with the floating microphone in another app. Your successful dictations appear here."
                else "No matching transcripts. Clear search to see all history."))
            for (record in records) {
                val day = java.time.Instant.ofEpochMilli(record.createdAt).atZone(ZoneId.systemDefault()).toLocalDate()
                if (day != lastDay) {
                    val label = when (day) { LocalDate.now() -> "Today"; LocalDate.now().minusDays(1) -> "Yesterday"; else -> day.format(java.time.format.DateTimeFormatter.ofPattern("MMM d, yyyy", java.util.Locale.getDefault())) }
                    content.addView(ModernUi.label(activity, label, 13f, muted = true).apply { setPadding(0, dp(22), 0, dp(10)) })
                    group = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; background = ModernUi.surface(activity) }
                    content.addView(group); lastDay = day
                }
                val rows = group!!
                if (rows.childCount > 0) rows.addView(View(activity).apply { setBackgroundColor(ModernUi.color(activity, R.color.panel_outline)) }, LinearLayout.LayoutParams(-1, dp(1)))
                rows.addView(transcriptCard(record))
            }
            records.lastOrNull()?.let { cursor = HistoryCursor(it.createdAt, it.id) }
            if (records.size == 40) {
                lateinit var more: Button
                more = button("Load more") { content.removeView(more); page() }; content.addView(more)
            }
        }
    }
    private fun transcriptCard(record: DictationRecord): View {
        val query = state.getString("search", "").orEmpty().trim()
        var showingOriginal = query.isNotBlank() && !record.delivered.contains(query, true) && record.original.contains(query, true)
        var matchingExpanded = query.isNotBlank() && !record.delivered.contains(query, true) && !record.original.contains(query, true) && record.expanded?.contains(query, true) == true
        var expanded = query.isNotBlank()
        fun currentText() = if (showingOriginal) record.original else if (matchingExpanded) record.expanded.orEmpty() else record.delivered
        val row = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            tag = "transcript-card-${record.id}"
            setPadding(dp(14), dp(8), dp(14), dp(12))
        }
        val words = ModernUi.label(activity, currentText().take(220), 15f).apply {
            tag = "transcript-text"; maxLines = 5; ellipsize = android.text.TextUtils.TruncateAt.END
            setPadding(dp(2), dp(6), dp(2), 0)
        }
        val more = button("Read more") { }.apply {
            minHeight = dp(48); textSize = 12f; cornerRadius = dp(0); strokeWidth = 0
            setPadding(dp(2), 0, dp(8), 0)
            backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.TRANSPARENT)
            setTextColor(ModernUi.color(activity, R.color.primary))
            elevation = 0f; stateListAnimator = null
            visibility = View.GONE
        }
        fun truncated(): Boolean = currentText().length > 220 || words.layout?.let { it.lineCount > 0 && it.getEllipsisCount(it.lineCount - 1) > 0 } == true
        fun refreshExpansion() {
            words.text = if (expanded) currentText() else currentText().take(220)
            words.maxLines = if (expanded) Int.MAX_VALUE else 5
            words.ellipsize = if (expanded) null else android.text.TextUtils.TruncateAt.END
            more.text = if (expanded) "Show less" else "Read more"
            more.visibility = if (expanded || truncated()) View.VISIBLE else View.GONE
        }
        more.setOnClickListener { expanded = !expanded; refreshExpansion() }
        row.isFocusable = true
        row.setOnClickListener { if (expanded || truncated()) { expanded = !expanded; refreshExpansion() } }
        val header = LinearLayout(activity).apply { gravity = Gravity.CENTER_VERTICAL }
        val metadata = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            addView(ModernUi.label(activity, DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(record.createdAt)), 12f, muted = true))
        }
        val status = ModernUi.label(activity, if (matchingExpanded) "Expanded · match" else if (showingOriginal) "Original · match" else if (record.cleaned != null) "AI cleaned" else if (record.expanded != null) "Expanded" else if (record.mode == "AI Cleanup") "Original" else "Direct", 12f, muted = true)
        status.setPadding(dp(8), 0, dp(4), 0)
        if (record.delivered != record.original || matchingExpanded) {
            status.minimumHeight = dp(48)
            status.gravity = Gravity.CENTER_VERTICAL
            status.contentDescription = if (showingOriginal) "Show delivered transcript" else "Show original transcript"
            status.setTextColor(ModernUi.color(activity, R.color.primary))
            status.isFocusable = true
            status.setOnClickListener {
                matchingExpanded = false
                showingOriginal = !showingOriginal
                status.text = if (showingOriginal) "Original" else if (record.cleaned != null) "AI cleaned" else "Expanded"
                status.contentDescription = if (showingOriginal) "Show delivered transcript" else "Show original transcript"
                expanded = false; refreshExpansion()
            }
        }
        metadata.addView(status)
        header.addView(metadata, LinearLayout.LayoutParams(0, -2, 1f))
        fun action(description: String, iconResource: Int, action: () -> Unit) = com.google.android.material.button.MaterialButton(activity).apply {
            contentDescription = description
            icon = androidx.core.content.ContextCompat.getDrawable(activity, iconResource)
            iconTint = android.content.res.ColorStateList.valueOf(ModernUi.color(activity, R.color.ink_muted))
            iconSize = dp(16); iconPadding = 0
            iconGravity = com.google.android.material.button.MaterialButton.ICON_GRAVITY_TEXT_START
            backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.TRANSPARENT)
            cornerRadius = dp(24); elevation = 0f; stateListAnimator = null
            minWidth = 0; setPadding(dp(12), 0, dp(12), 0)
            androidx.appcompat.widget.TooltipCompat.setTooltipText(this, description)
            setOnClickListener { action() }
        }
        header.addView(action("Copy transcript", R.drawable.ic_ui_copy) { copy(currentText()) }, LinearLayout.LayoutParams(dp(48), dp(48)))
        lateinit var cleanupAction: com.google.android.material.button.MaterialButton
        cleanupAction = action("Clean up transcript", R.drawable.ic_ui_cleanup) {
            cleanupAction.isEnabled = false; cleanupAction.alpha = 0.38f
            retryCleanup(record) { cleanupAction.isEnabled = true; cleanupAction.alpha = 1f }
        }
        cleanupAction.isEnabled = record.id !in cleanupPending
        cleanupAction.alpha = if (cleanupAction.isEnabled) 1f else 0.38f
        header.addView(cleanupAction, LinearLayout.LayoutParams(dp(48), dp(48)))
        header.addView(action("Delete transcript", R.drawable.ic_ui_delete) { query({ it.delete(record.id) }) { refresh() } }, LinearLayout.LayoutParams(dp(48), dp(48)))
        row.addView(header)
        row.addView(words)
        row.addView(more, LinearLayout.LayoutParams(-2, -2))
        refreshExpansion()
        words.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            more.visibility = if (expanded || truncated()) View.VISIBLE else View.GONE
        }
        return row
    }
    private fun retryCleanup(record: DictationRecord, finished: () -> Unit = {}) {
        val draft = DictationCleanup.input(record)
        if (draft == null) {
            finished(); Toast.makeText(activity, "Saved expansion could not be verified. Copy the captured delivered text; no cleanup request was sent.", Toast.LENGTH_LONG).show(); return
        }
        val selection = try { ProviderProfiles(activity).cleanupSelection() } catch (_: Exception) {
            finished(); Toast.makeText(activity, "Check your selected cleanup provider in Settings", Toast.LENGTH_LONG).show(); return
        }
        val config = selection.config
        val key = selection.key
        if (key.isBlank()) { finished(); Toast.makeText(activity, "Configure a cleanup API key in Settings", Toast.LENGTH_LONG).show(); return }
        if (!cleanupPending.add(record.id)) { finished(); return }
        Toast.makeText(activity, "Sending transcript text for cleanup…", Toast.LENGTH_SHORT).show()
        val prompt = draft.prompt(prefs.getString("custom_instructions", "").orEmpty(), Dictionary.load(prefs))
        PostProcessor.process(draft.protectedText, prompt, key, config) { result ->
            val cleaned = draft.accept(result.text)
            activity.runOnUiThread {
                cleanupPending.remove(record.id); finished()
                if (activity.isDestroyed || closed) return@runOnUiThread
                if (cleaned == null) {
                    Toast.makeText(activity, "Cleanup failed or changed your words. Captured text is preserved.", Toast.LENGTH_LONG).show()
                } else query({ it.updateCleanup(record.id, cleaned) }) { refresh() }
            }
        }
    }
    private fun cleanupConfigured(): Boolean {
        return runCatching { ProviderProfiles(activity).cleanupSelection().key.isNotBlank() }.getOrDefault(false)
    }
    private fun copy(value: String) {
        val clip = ClipData.newPlainText("OpenWisper transcript", value)
        (activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(clip)
        Toast.makeText(activity, "Copied", Toast.LENGTH_SHORT).show()
    }

    override fun close() { closed = true; generation++; pending?.cancel(true); worker.shutdownNow() }
}
