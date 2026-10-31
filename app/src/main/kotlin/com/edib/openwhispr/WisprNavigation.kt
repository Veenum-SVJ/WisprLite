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
    private var drawerOverlay: View? = null
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
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(frame) { view, insets ->
            val bars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars() or
                androidx.core.view.WindowInsetsCompat.Type.displayCutout())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        content.setPadding(dp(16), dp(4), dp(16), dp(20))
        activity.onBackPressedDispatcher.addCallback(activity, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (closeDrawer()) return
                if (destination != "Home") { show("Home"); return }
                isEnabled = false
                activity.onBackPressedDispatcher.onBackPressed()
            }
        })
        val header = LinearLayout(activity).apply {
            gravity = Gravity.CENTER_VERTICAL; setPadding(dp(12), dp(8), dp(16), dp(8))
            addView(com.google.android.material.button.MaterialButton(activity).apply {
                text = "☰"; textSize = 22f; contentDescription = "Open navigation"
                backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.TRANSPARENT)
                setTextColor(androidx.core.content.ContextCompat.getColor(activity, R.color.primary))
                setPadding(0, 0, 0, 0); setOnClickListener { drawer() }
            }, LinearLayout.LayoutParams(dp(48), dp(56)))
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
    private fun closeDrawer(): Boolean {
        val overlay = drawerOverlay ?: return false
        frame.removeView(overlay); drawerOverlay = null
        return true
    }
    private fun drawer() {
        closeDrawer()
        val cover = FrameLayout(activity).apply { setBackgroundColor(0x66000000) }
        drawerOverlay = cover
        cover.setOnClickListener { closeDrawer() }
        val panel = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(40), dp(16), dp(20))
            val background = android.util.TypedValue()
            activity.theme.resolveAttribute(android.R.attr.colorBackground, background, true)
            setBackgroundColor(background.data)
            isClickable = true
        }
        panel.addView(text("WisprLite", 26f))
        for (name in listOf("Home", "Insights", "Dictionary")) panel.addView(button(name) { closeDrawer(); show(name) })
        panel.addView(View(activity), LinearLayout.LayoutParams(1, 0, 1f))
        panel.addView(button("Settings") { closeDrawer(); show("Settings") })
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
                val controls = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
                fun control(label: String, action: () -> Unit) = com.google.android.material.button.MaterialButton(activity, null,
                    com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                    text = label; textSize = 14f; isAllCaps = false; minHeight = dp(48)
                    setOnClickListener { action() }
                }
                controls.addView(control(DictationLanguage.fromSaved(prefs.getString("language_profile", null)).title) {
                    activity.showLanguageProfilePicker()
                }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(8) })
                val cleanupEnabled = prefs.getBoolean("use_post_processing", false)
                val cleanupReady = cleanupConfigured()
                val cleanupLabel = if (!cleanupEnabled) "Direct transcription" else if (cleanupReady) "AI Cleanup" else "Cleanup · needs key"
                controls.addView(control(cleanupLabel) {
                    if (!cleanupEnabled && !cleanupReady) {
                        AlertDialog.Builder(activity).setTitle("Set up AI cleanup")
                            .setMessage("Add your cleanup provider and API key in Settings. Direct transcription is available without cleanup.")
                            .setPositiveButton("Open Settings") { _, _ -> show("Settings") }
                            .setNegativeButton("Cancel", null).show()
                    } else {
                        prefs.edit().putBoolean("use_post_processing", !cleanupEnabled).apply(); show("Home")
                    }
                }, LinearLayout.LayoutParams(0, -2, 1f))
                content.addView(controls)
                page()
            }
            "Insights" -> insights()
            "Dictionary" -> dictionary()
            "Settings" -> content.addView(settings)
        }
    }
    private fun page() {
        query({ it.listBefore(cursor) }) { records ->
            if (records.isEmpty() && cursor == null) content.addView(text("No transcripts yet. Use the floating microphone while typing in another app. Your successful dictations will appear here. Setup and provider options are in Settings."))
            for (record in records) {
                val day = java.time.Instant.ofEpochMilli(record.createdAt).atZone(ZoneId.systemDefault()).toLocalDate()
                val label = when (day) { LocalDate.now() -> "Today"; LocalDate.now().minusDays(1) -> "Yesterday"; else -> day.toString() }
                if (label != lastDay) { content.addView(text(label, 18f)); lastDay = label }
                val preview = (record.cleaned ?: record.original).take(220)
                val resultLabel = when {
                    record.cleaned != null -> "AI cleaned"
                    record.mode == "AI Cleanup" -> "Original · cleanup not applied"
                    else -> "Direct"
                }
                val row = com.google.android.material.button.MaterialButton(activity, null,
                    com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                    text = "${DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(record.createdAt))} · $resultLabel\n$preview"
                    isAllCaps = false
                    gravity = Gravity.START or Gravity.CENTER_VERTICAL
                    cornerRadius = dp(12)
                    setPadding(dp(16), dp(12), dp(16), dp(12))
                    setOnClickListener { detail(record) }
                }
                content.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
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
    private fun cleanupConfigured(): Boolean {
        val config = CleanupProviderConfig.fromPreferences(prefs.getString("cleanup_provider", null),
            prefs.getString("cleanup_base_url", null), prefs.getString("cleanup_model", null))
        return (if (config.provider == CleanupProviderConfig.Provider.GROQ) SecureKeyStorage.groqApiKey(activity)
            else SecureKeyStorage.cleanupApiKey(activity)).isNotBlank()
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
