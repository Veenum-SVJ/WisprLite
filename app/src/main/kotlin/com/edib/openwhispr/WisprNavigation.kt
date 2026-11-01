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
    private val title = ModernUi.label(activity, "Home", 28f, strong = true).apply { setPadding(dp(20), dp(14), dp(20), dp(10)) }
    private val content = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
    private var destination = savedState?.getString("destination") ?: "Home"
    private var dictionaryDraft: String? = savedState?.getString("dictionary_draft")
    private var generation = 0
    private var cursor: HistoryCursor? = null
    private var lastDay = ""
    private var drawerOverlay: View? = null
    private val cleanupPending = mutableSetOf<String>()
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
        content.setPadding(dp(20), dp(4), dp(20), dp(24))
        activity.onBackPressedDispatcher.addCallback(activity, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (closeDrawer()) return
                if (destination != "Home") { show("Home"); return }
                isEnabled = false
                activity.onBackPressedDispatcher.onBackPressed()
            }
        })
        val header = LinearLayout(activity).apply {
            gravity = Gravity.CENTER_VERTICAL; setPadding(dp(20), dp(8), dp(12), dp(8))
            addView(ModernUi.logo(activity))
            addView(ModernUi.label(activity, "WisprLite", 19f, strong = true),
                LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(10) })
            addView(com.google.android.material.button.MaterialButton(activity).apply {
                contentDescription = "Open navigation"
                backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.TRANSPARENT)
                elevation = 0f; stateListAnimator = null
                icon = androidx.core.content.ContextCompat.getDrawable(activity, R.drawable.ic_ui_menu)
                iconTint = android.content.res.ColorStateList.valueOf(ModernUi.color(activity, R.color.ink))
                iconPadding = 0; iconGravity = com.google.android.material.button.MaterialButton.ICON_GRAVITY_TEXT_START
                setPadding(dp(12), 0, dp(12), 0); minWidth = 0
                setOnClickListener { drawer() }
            }, LinearLayout.LayoutParams(dp(48), dp(48)))
        }
        body.addView(header)
        body.addView(title)
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
            tag = "navigation-panel"
            orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(40), dp(16), dp(20))
            val background = android.util.TypedValue()
            activity.theme.resolveAttribute(android.R.attr.colorBackground, background, true)
            setBackgroundColor(background.data)
            isClickable = true
        }
        panel.addView(LinearLayout(activity).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(ModernUi.logo(activity, 36))
            addView(ModernUi.label(activity, "WisprLite", 22f, strong = true), LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(12) })
            setPadding(dp(8), 0, dp(8), dp(28))
        })
        val icons = mapOf("Home" to R.drawable.ic_ui_home, "Insights" to R.drawable.ic_ui_insights,
            "Dictionary" to R.drawable.ic_ui_dictionary, "Settings" to R.drawable.ic_ui_settings)
        fun navigationRow(name: String) = com.google.android.material.button.MaterialButton(activity).apply {
            text = name; isAllCaps = false; textSize = 16f
            elevation = 0f; stateListAnimator = null
            gravity = Gravity.START or Gravity.CENTER_VERTICAL; cornerRadius = dp(12)
            icon = androidx.core.content.ContextCompat.getDrawable(activity, icons.getValue(name))
            iconPadding = dp(14); iconSize = dp(22)
            setPadding(dp(16), dp(10), dp(16), dp(10))
            setTextColor(ModernUi.color(activity, if (destination == name) R.color.primary else R.color.ink))
            iconTint = android.content.res.ColorStateList.valueOf(ModernUi.color(activity, R.color.primary))
            backgroundTintList = android.content.res.ColorStateList.valueOf(if (destination == name)
                ModernUi.color(activity, R.color.primary_container) else android.graphics.Color.TRANSPARENT)
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(6) }
            minHeight = dp(52)
            setOnClickListener { closeDrawer(); show(name) }
        }
        for (name in listOf("Home", "Insights", "Dictionary")) panel.addView(navigationRow(name))
        panel.addView(View(activity), LinearLayout.LayoutParams(1, 0, 1f))
        panel.addView(navigationRow("Settings"))
        cover.addView(panel, FrameLayout.LayoutParams(dp(280), -1, Gravity.END))
        frame.addView(cover, FrameLayout.LayoutParams(-1, -1))
        if (android.animation.ValueAnimator.areAnimatorsEnabled()) {
            panel.translationX = dp(280).toFloat()
            panel.animate().translationX(0f).setDuration(220)
                .setInterpolator(android.view.animation.DecelerateInterpolator()).start()
        }
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
                    text = label; textSize = 13f; isAllCaps = false; minHeight = dp(48)
                    cornerRadius = dp(12); insetTop = 0; insetBottom = 0
                    setPadding(dp(10), dp(8), dp(10), dp(8))
                    backgroundTintList = android.content.res.ColorStateList.valueOf(ModernUi.color(activity, R.color.panel))
                    strokeColor = android.content.res.ColorStateList.valueOf(ModernUi.color(activity, R.color.panel_outline))
                    setOnClickListener { action() }
                }
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
                }, LinearLayout.LayoutParams(-2, -2))
                controls.gravity = Gravity.END
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
                if (label != lastDay) { content.addView(ModernUi.label(activity, label, 14f, strong = true, muted = true).apply { setPadding(0, dp(22), 0, dp(12)) }); lastDay = label }
                val row = transcriptCard(record)
                content.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })
            }
            records.lastOrNull()?.let { cursor = HistoryCursor(it.createdAt, it.id) }
            if (records.size == 40) {
                lateinit var more: Button
                more = button("Load more") { content.removeView(more); page() }
                content.addView(more)
            }
        }
    }
    private fun transcriptCard(record: DictationRecord): View {
        var showingOriginal = record.cleaned == null
        var expanded = false
        fun currentText() = if (showingOriginal) record.original else record.cleaned.orEmpty()
        val row = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            tag = "transcript-card-${record.id}"
            background = ModernUi.surface(activity)
            setPadding(dp(14), dp(8), dp(14), dp(12))
        }
        val words = ModernUi.label(activity, currentText().take(220), 16f).apply {
            tag = "transcript-text"; maxLines = 5; ellipsize = android.text.TextUtils.TruncateAt.END
            setPadding(dp(2), dp(6), dp(2), 0)
        }
        val more = button("Read more") { }.apply {
            minHeight = dp(48); textSize = 12f; cornerRadius = dp(10)
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
            orientation = LinearLayout.VERTICAL
            addView(ModernUi.label(activity, DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(record.createdAt)), 12f, muted = true))
        }
        val status = ModernUi.label(activity, if (record.cleaned != null) "AI cleaned" else if (record.mode == "AI Cleanup") "Original" else "Direct", 12f, muted = true)
        if (record.cleaned != null) {
            status.minimumHeight = dp(48)
            status.gravity = Gravity.CENTER_VERTICAL
            status.contentDescription = "Show original transcript"
            status.setTextColor(ModernUi.color(activity, R.color.primary))
            status.isFocusable = true
            status.setOnClickListener {
                showingOriginal = !showingOriginal
                status.text = if (showingOriginal) "Original" else "AI cleaned"
                status.contentDescription = if (showingOriginal) "Show cleaned transcript" else "Show original transcript"
                expanded = false; refreshExpansion()
            }
        }
        metadata.addView(status)
        header.addView(metadata, LinearLayout.LayoutParams(0, -2, 1f))
        fun action(description: String, iconResource: Int, action: () -> Unit) = com.google.android.material.button.MaterialButton(activity).apply {
            contentDescription = description
            icon = androidx.core.content.ContextCompat.getDrawable(activity, iconResource)
            iconTint = android.content.res.ColorStateList.valueOf(ModernUi.color(activity, R.color.ink_muted))
            iconSize = dp(20); iconPadding = 0
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
        header.addView(action("Delete transcript", R.drawable.ic_ui_delete) { query({ it.delete(record.id) }) { show("Home") } }, LinearLayout.LayoutParams(dp(48), dp(48)))
        row.addView(header)
        row.addView(words)
        row.addView(more, LinearLayout.LayoutParams(-2, -2))
        words.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            more.visibility = if (expanded || truncated()) View.VISIBLE else View.GONE
        }
        return row
    }
    private fun retryCleanup(record: DictationRecord, finished: () -> Unit = {}) {
        val selection = try { ProviderProfiles(activity).cleanupSelection() } catch (_: Exception) {
            finished(); Toast.makeText(activity, "Check your selected cleanup provider in Settings", Toast.LENGTH_LONG).show(); return
        }
        val config = selection.config
        val key = selection.key
        if (key.isBlank()) { finished(); Toast.makeText(activity, "Configure a cleanup API key in Settings", Toast.LENGTH_LONG).show(); return }
        if (!cleanupPending.add(record.id)) { finished(); return }
        Toast.makeText(activity, "Sending transcript text for cleanup…", Toast.LENGTH_SHORT).show()
        val prompt = PostProcessor.effectivePrompt(prefs.getString("custom_instructions", "").orEmpty(), Dictionary.load(prefs))
        PostProcessor.process(record.original, prompt, key, config) { result ->
            val cleaned = result.text?.trim()
            activity.runOnUiThread {
                cleanupPending.remove(record.id); finished()
                if (activity.isDestroyed) return@runOnUiThread
                if (cleaned.isNullOrBlank() || cleaned == "EMPTY" || PostProcessor.looksGenerated(record.original, cleaned)) {
                    Toast.makeText(activity, "Cleanup failed or changed your words. Original text is preserved.", Toast.LENGTH_LONG).show()
                } else query({ it.updateCleanup(record.id, cleaned) }) { if (destination == "Home") show("Home") }
            }
        }
    }
    private fun cleanupConfigured(): Boolean {
        return runCatching { ProviderProfiles(activity).cleanupSelection().key.isNotBlank() }.getOrDefault(false)
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
            val panel = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL; background = ModernUi.surface(activity)
                tag = "insights_$label"; setPadding(dp(18), dp(16), dp(18), dp(16))
                addView(ModernUi.label(activity, label, 14f, strong = true, muted = true))
                addView(InsightDonutView(activity, totals.speakingMillis / 1000.0, saved), LinearLayout.LayoutParams(-1, dp(156)))
                addView(LinearLayout(activity).apply {
                    gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(10), 0, dp(14))
                    addView(LinearLayout(activity).apply {
                        orientation = LinearLayout.VERTICAL
                        addView(ModernUi.label(activity, "${totals.words}", 34f, strong = true))
                        addView(ModernUi.label(activity, "words", 13f, muted = true))
                    }, LinearLayout.LayoutParams(0, -2, 1f))
                    addView(ModernUi.label(activity, "${totals.recordings} recordings", 15f, strong = true))
                })
                fun duration(seconds: Long): String = if (seconds < 60) "$seconds sec" else "${seconds / 60} min ${seconds % 60} sec"
                addView(ModernUi.label(activity, "Speaking · ${duration(totals.speakingMillis / 1000)}", 14f, strong = true))
                addView(ModernUi.label(activity, "Estimated time saved · ${duration(saved.toLong())}", 14f).apply {
                    setTextColor(ModernUi.color(activity, R.color.primary))
                })
                addView(ModernUi.label(activity, if (totals.recordings == 0L) "Your chart fills as you dictate."
                    else "Teal: estimated time saved. Grey: speaking time. Based on ${prefs.getInt("typing_wpm", 40).coerceAtLeast(1)} words/min typing; excludes processing time.", 12f, muted = true))
            }
            content.addView(panel, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(14) })
        }
    }
    private fun dictionary() {
        content.addView(text("Help recognition with names and phrases. One per line; optional aliases after |. This does not train a model."))
        val input = EditText(activity).apply {
            hint = "Canonical phrase | pronunciation, alias"; minLines = 8; gravity = Gravity.TOP
            background = ModernUi.surface(activity); setPadding(dp(16), dp(14), dp(16), dp(14))
            setTextColor(ModernUi.color(activity, R.color.ink)); setHintTextColor(ModernUi.color(activity, R.color.ink_muted))
            textSize = 16f
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
