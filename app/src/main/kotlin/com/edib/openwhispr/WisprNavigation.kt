package com.edib.openwhispr

import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.*
import java.text.DateFormat
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date
import java.util.concurrent.Executors

/** Small native navigation shell: no web runtime or additional UI framework. */
class WisprNavigation(private val activity: MainActivity, private val settings: View, savedState: android.os.Bundle? = null) {
    private val worker = Executors.newSingleThreadExecutor()
    private val prefs = activity.getSharedPreferences("openwhispr", Context.MODE_PRIVATE)
    private val frame = FrameLayout(activity)
    private val body = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
    private val title = TextView(activity).apply { textSize = 24f }
    private val content = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
    private var destination = savedState?.getString("destination") ?: "Home"
    private var dictionaryDraft: String? = savedState?.getString("dictionary_draft")
    private var generation = 0
    private var cursor: HistoryCursor? = null
    private var lastDay = ""
    private fun dp(value: Int) = (value * activity.resources.displayMetrics.density).toInt()
    private fun text(value: String, size: Float = 16f) = TextView(activity).apply {
        text = value; textSize = size; setPadding(dp(12), dp(12), dp(12), dp(12))
    }
    private fun button(label: String, action: () -> Unit) = com.google.android.material.button.MaterialButton(activity).apply {
        text = label; isAllCaps = false; setOnClickListener { action() }
    }
    private fun <T> query(action: (HistoryRepository) -> T, result: (T) -> Unit) {
        val token = generation
        worker.execute {
            try {
                val value = HistoryRepository(activity).use(action)
                activity.runOnUiThread { if (!activity.isDestroyed && token == generation) result(value) }
            } catch (_: Exception) {
                activity.runOnUiThread { if (token == generation) content.addView(text("Couldn’t load this view. Open it again to retry.")) }
            }
        }
    }
    fun build(): View {
        val header = LinearLayout(activity).apply {
            gravity = Gravity.CENTER_VERTICAL; setPadding(dp(12), dp(24), dp(16), dp(8))
            addView(button("☰") { drawer() }.apply { contentDescription = "Open navigation" }, LinearLayout.LayoutParams(dp(64), dp(56)))
            addView(title)
        }
        body.addView(header)
        body.addView(ScrollView(activity).apply { addView(content) }, LinearLayout.LayoutParams(-1, 0, 1f))
        frame.addView(body, FrameLayout.LayoutParams(-1, -1))
        show(destination)
        return frame
    }
    fun refresh() { show(destination) }
    fun openSettings() { show("Settings") }
    fun saveState(state: android.os.Bundle) {
        state.putString("destination", destination)
        state.putString("dictionary_draft", dictionaryDraft)
    }
    fun close() { generation++; worker.shutdown() }
    private fun drawer() {
        val cover = FrameLayout(activity).apply { setBackgroundColor(0x66000000) }
        cover.setOnClickListener { frame.removeView(cover) }
        val panel = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(40), dp(16), dp(20))
            val background = android.util.TypedValue()
            activity.theme.resolveAttribute(android.R.attr.colorBackground, background, true)
            setBackgroundColor(background.data)
            isClickable = true
        }
        panel.addView(text("WisprLite", 26f))
        for (name in listOf("Home", "Insights", "Dictionary")) panel.addView(button(name) { frame.removeView(cover); show(name) })
        panel.addView(View(activity), LinearLayout.LayoutParams(1, 0, 1f))
        panel.addView(button("Settings") { frame.removeView(cover); show("Settings") })
        cover.addView(panel, FrameLayout.LayoutParams(dp(280), -1, Gravity.START))
        frame.addView(cover, FrameLayout.LayoutParams(-1, -1))
    }
    private fun show(name: String) {
        destination = name; generation++; title.text = name
        (settings.parent as? android.view.ViewGroup)?.removeView(settings)
        content.removeAllViews()
        when (name) {
            "Home" -> {
                cursor = null; lastDay = ""
                content.addView(text("Your words, ready to use", 20f))
                content.addView(button(DictationLanguage.fromSaved(prefs.getString("language_profile", null)).title) { show("Settings") })
                content.addView(button(if (prefs.getBoolean("use_post_processing", false)) "AI Cleanup" else "Direct transcription") {
                    prefs.edit().putBoolean("use_post_processing", !prefs.getBoolean("use_post_processing", false)).apply(); show("Home")
                })
                content.addView(text("Use the floating microphone while typing in another app. Setup and provider options are in Settings."))
                page()
            }
            "Insights" -> insights()
            "Dictionary" -> dictionary()
            "Settings" -> content.addView(settings)
        }
    }
    private fun page() {
        query({ it.listBefore(cursor) }) { records ->
            if (records.isEmpty() && cursor == null) content.addView(text("No transcripts yet. Your successful dictations will appear here."))
            for (record in records) {
                val day = java.time.Instant.ofEpochMilli(record.createdAt).atZone(ZoneId.systemDefault()).toLocalDate()
                val label = when (day) { LocalDate.now() -> "Today"; LocalDate.now().minusDays(1) -> "Yesterday"; else -> day.toString() }
                if (label != lastDay) { content.addView(text(label, 18f)); lastDay = label }
                val preview = (record.cleaned ?: record.original).take(220)
                content.addView(button("${DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(record.createdAt))} · ${record.mode}\n$preview") { detail(record) })
            }
            records.lastOrNull()?.let { cursor = HistoryCursor(it.createdAt, it.id) }
            if (records.size == 40) {
                lateinit var more: Button
                more = button("Load more") { content.removeView(more); page() }
                content.addView(more)
            }
        }
    }
    private fun detail(record: DictationRecord) {
        var dialog: AlertDialog? = null
        val layout = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16),0,dp(16),0) }
        layout.addView(text("Original\n${record.original}"))
        record.cleaned?.let { layout.addView(text("Cleaned\n$it")) }
        layout.addView(button("Copy original") { copy(record.original) })
        record.cleaned?.let { cleaned -> layout.addView(button("Copy cleaned") { copy(cleaned) }) }
        layout.addView(button("Retry AI cleanup") { dialog?.dismiss(); retryCleanup(record) })
        dialog = AlertDialog.Builder(activity).setTitle("Transcript").setView(ScrollView(activity).apply { addView(layout) })
            .setNegativeButton("Close", null).setNeutralButton("Delete") { _, _ ->
                query({ it.delete(record.id) }) { show("Home") }
            }.create()
        dialog.show()
    }
    private fun retryCleanup(record: DictationRecord) {
        val config = CleanupProviderConfig.fromPreferences(prefs.getString("cleanup_provider", null),
            prefs.getString("cleanup_base_url", null), prefs.getString("cleanup_model", null))
        val key = if (config.provider == CleanupProviderConfig.Provider.GROQ) SecureKeyStorage.groqApiKey(activity) else SecureKeyStorage.cleanupApiKey(activity)
        if (key.isBlank()) { Toast.makeText(activity, "Configure a cleanup API key in Settings", Toast.LENGTH_LONG).show(); return }
        Toast.makeText(activity, "Sending transcript text for cleanup…", Toast.LENGTH_SHORT).show()
        val prompt = PostProcessor.effectivePrompt(prefs.getString("custom_instructions", "").orEmpty(), Dictionary.load(prefs))
        PostProcessor.process(record.original, prompt, key, config) { result ->
            val cleaned = result.text?.trim()
            activity.runOnUiThread {
                if (activity.isDestroyed) return@runOnUiThread
                if (cleaned.isNullOrBlank() || cleaned == "EMPTY" || PostProcessor.looksGenerated(record.original, cleaned)) {
                    Toast.makeText(activity, "Cleanup failed or changed your words. Original text is preserved.", Toast.LENGTH_LONG).show()
                } else query({ it.updateCleanup(record.id, cleaned) }) { show("Home"); detail(record.copy(cleaned = cleaned)) }
            }
        }
    }
    private fun copy(value: String) {
        val clip = ClipData.newPlainText("WisprLite transcript", value)
        if (android.os.Build.VERSION.SDK_INT >= 33) clip.description.extras = android.os.PersistableBundle().apply { putBoolean("android.content.extra.IS_SENSITIVE", true) }
        (activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(clip)
        Toast.makeText(activity, "Copied", Toast.LENGTH_SHORT).show()
    }
    private fun insights() {
        val periods = listOf("Today" to LocalDate.now(), "Last 7 days" to LocalDate.now().minusDays(6), "All time" to null)
        for ((label, date) in periods) query({ it.totals(date?.atStartOfDay(ZoneId.systemDefault())?.toInstant()?.toEpochMilli() ?: 0) }) { totals ->
            val saved = InsightCalculator.savedSeconds(totals.words.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(), totals.speakingMillis, prefs.getInt("typing_wpm", 40).coerceAtLeast(1))
            content.addView(text("$label\n${totals.words} words · ${totals.recordings} recordings\n${totals.speakingMillis / 60000} min speaking · ${saved.toInt() / 60} min estimated saved", 18f))
        }
    }
    private fun dictionary() {
        content.addView(text("Help recognition with names and phrases. One per line; optional aliases after |. This does not train a model."))
        val input = EditText(activity).apply {
            hint = "Canonical phrase | pronunciation, alias"; minLines = 8; gravity = Gravity.TOP
            setText(dictionaryDraft ?: Dictionary.load(prefs).joinToString("\n"))
            addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) { }
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { dictionaryDraft = s?.toString() }
                override fun afterTextChanged(s: android.text.Editable?) { }
            })
        }
        content.addView(input)
        content.addView(button("Save dictionary") {
            Dictionary.save(prefs, input.text.toString().lineSequence().toList())
            WhisperAccessibilityService.instance?.reloadModel()
            Toast.makeText(activity, "Dictionary saved", Toast.LENGTH_SHORT).show()
        })
    }
}
