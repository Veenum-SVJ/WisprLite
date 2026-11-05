package com.edib.openwhispr

import android.app.AlertDialog
import android.content.Context
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatDelegate
import kotlin.concurrent.thread

class SettingsExtras(private val activity: MainActivity, savedState: Bundle? = null) {
    private val prefs = activity.getSharedPreferences("openwhispr", Context.MODE_PRIVATE)
    private val router = SettingsRouter(activity, savedState)
    private val tone = ToneStyleSettings(activity)
    private var languageValue: TextView? = null
    private var themeChoices: com.google.android.material.button.MaterialButtonToggleGroup? = null
    private val providerValues = mutableListOf<TextView>()
    private var storageValue: TextView? = null
    private var diagnostics: TextView? = null
    private var modelList: View? = null
    private val installedVersion = runCatching { activity.packageManager.getPackageInfo(activity.packageName, 0).versionName }.getOrNull() ?: "Unknown"
    private fun providerSummary(): String = runCatching {
        val store = ProviderProfiles(activity); val profiles = store.all().associateBy { it.id }
        "Speech: ${profiles[store.activeSpeechId()]?.name ?: "Manual"}; cleanup: ${profiles[store.activeCleanupId()]?.name ?: "Manual"}"
    }.getOrDefault("Saved profile needs attention")
    private fun dp(value: Int) = (value * activity.resources.displayMetrics.density).toInt()
    private fun column() = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
    private fun note(value: String) = ModernUi.label(activity, value, 14f, muted = true).apply { setPadding(dp(4), dp(10), dp(4), dp(12)) }
    private fun row(label: String, action: () -> Unit) = ModernUi.settingRow(activity, label, action)
    private fun message(value: String) = activity.runOnUiThread { if (!activity.isDestroyed) Toast.makeText(activity, value, Toast.LENGTH_LONG).show() }
    fun saveState(out: Bundle) = router.saveState(out)
    fun handleBack(): Boolean = router.handleBack()
    fun openSystem() = router.open(SettingsDestination.SYSTEM)
    fun openSpeech() = router.open(SettingsDestination.SPEECH)
    fun openLanguageModels() = router.open(SettingsDestination.LANGUAGE)
    fun updateReadiness(summary: String) = router.readiness("$summary · Review System setup")
    internal fun resetCustomRefinements() { prefs.edit().remove("custom_instructions").apply(); activity.refreshSettings() }
    fun refresh() {
        themeChoices?.let { choices -> choices.findViewWithTag<View>("theme-${prefs.getInt("appearance", -1)}")?.let { choices.check(it.id) } }
        languageValue?.text = DictationLanguage.fromSaved(prefs.getString("language_profile", null)).title
        providerValues.forEach { it.text = providerSummary() }
        val mic = androidx.core.content.ContextCompat.checkSelfPermission(activity, android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val service = WhisperAccessibilityService.instance != null
        val enabled = prefs.getBoolean("service_master_enabled", true)
        router.readiness(if (!enabled) "Dictation is paused · Open System setup" else if (mic && service) "Microphone and accessibility ready · Review System setup" else "Setup needs attention · Open System setup")
        diagnostics?.text = "Android ${android.os.Build.VERSION.RELEASE} · microphone ${if (mic) "granted" else "needed"} · accessibility ${if (service) "connected" else "disconnected"} · recognition ${if (prefs.getBoolean("use_local", true)) "local" else "cloud"} · cleanup ${if (prefs.getBoolean("use_post_processing", false)) "on" else "off"}. No keys or transcripts are included."
        tone.refresh()
        refreshStorage()
    }
    private fun refreshStorage() {
        val target = storageValue ?: return
        thread {
            val models = java.io.File(activity.filesDir, "models").walkTopDown().filter { it.isFile }.sumOf { it.length() }
            val audio = java.io.File(activity.filesDir, "recordings").walkTopDown().filter { it.isFile }.sumOf { it.length() }
            val db = activity.databaseList().sumOf { activity.getDatabasePath(it).length() }
            activity.runOnUiThread { if (!activity.isDestroyed) target.text = "Models: ${android.text.format.Formatter.formatFileSize(activity, models)} · Audio: ${android.text.format.Formatter.formatFileSize(activity, audio)} · Databases: ${android.text.format.Formatter.formatFileSize(activity, db)}" }
        }
    }
    fun build(setup: View, engine: View, models: View, cleanupControls: View, bubbleControls: View, backupControls: View, interfaceControls: View, aboutControls: View): View {
        val preferences = column().apply {
            addView(ModernUi.label(activity, "Appearance", 14f, strong = true).apply { setPadding(0, 0, 0, 4) })
            addView(note("Control how OpenWisper looks"))
            addView(LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL; background = ModernUi.surface(activity, radius = 6)
                setPadding(ModernUi.dp(activity, 12), ModernUi.dp(activity, 10), ModernUi.dp(activity, 12), ModernUi.dp(activity, 10))
                addView(ModernUi.label(activity, "Theme", 14f, strong = true))
                addView(ModernUi.label(activity, "Choose light, dark, or match your system", 12f, muted = true))
                val choices = com.google.android.material.button.MaterialButtonToggleGroup(activity).apply {
                    isSingleSelection = true; isSelectionRequired = true
                    background = ModernUi.surface(activity, muted = true, radius = 6)
                    layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = ModernUi.dp(activity, 8) }
                }
                themeChoices = choices
                val selected = prefs.getInt("appearance", -1)
                val values = mutableMapOf<Int, Int>()
                for ((label, mode) in listOf("Light" to 1, "Dark" to 2, "Auto" to -1)) {
                    val item = screenButton(activity, label) {}.apply {
                        id = View.generateViewId(); tag = "theme-$mode"; isCheckable = true
                        contentDescription = "Theme: $label"
                        icon = androidx.core.content.ContextCompat.getDrawable(activity, when(mode) {
                            1 -> R.drawable.ic_ui_sun; 2 -> R.drawable.ic_ui_moon; else -> R.drawable.ic_ui_monitor
                        })
                        iconSize = ModernUi.dp(activity, 14); iconPadding = ModernUi.dp(activity, 5)
                        iconTint = android.content.res.ColorStateList.valueOf(ModernUi.color(activity, R.color.ink_muted))
                        backgroundTintList = android.content.res.ColorStateList(arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                            intArrayOf(ModernUi.color(activity, R.color.panel), android.graphics.Color.TRANSPARENT))
                        strokeColor = android.content.res.ColorStateList(arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                            intArrayOf(ModernUi.color(activity, R.color.panel_outline), android.graphics.Color.TRANSPARENT))
                    }
                    values[item.id] = mode
                    choices.addView(item, LinearLayout.LayoutParams(0, -2, 1f))
                    if (mode == selected) choices.check(item.id)
                }
                choices.addOnButtonCheckedListener { _, id, checked ->
                    if (checked) values[id]?.let { mode ->
                        if (prefs.getInt("appearance", -1) != mode) {
                            prefs.edit().putInt("appearance", mode).apply()
                            AppCompatDelegate.setDefaultNightMode(mode)
                        }
                    }
                }
                addView(choices)
            })
            val language = row("Language · ${DictationLanguage.fromSaved(prefs.getString("language_profile", null)).title}") { activity.showLanguageProfilePicker() }.apply { tag = "settings-language" }
            languageValue = language.findViewWithTag("setting-subtitle"); addView(language)
            addView(note("English selects English recognition where supported. Auto-detect uses the languages supported by your chosen speech model. English-only models remain English-only."))
            addView(interfaceControls)
            for ((key, label, default) in listOf(Triple("recording_sound", "Recording stop sound", true), Triple("recording_vibration", "Recording vibration", false))) {
                addView(com.google.android.material.materialswitch.MaterialSwitch(activity).apply {
                    text = label; isChecked = prefs.getBoolean(key, default)
                    setOnCheckedChangeListener { _, checked -> prefs.edit().putBoolean(key, checked).apply() }
                })
            }
            addView(bubbleControls)
            addView(note("Drag the bubble along either edge or across to switch sides. Tap opens the waveform capsule; tap again to finish. Hold stays circular with a live waveform, double ring and HOLD indicator; release to finish. Processing animates the waveform inside the circle, including when Android transition animations are off."))
            addView(row("Typing speed · ${prefs.getInt("typing_wpm", 40)} words/minute") {
                val input = EditText(activity).apply { inputType = InputType.TYPE_CLASS_NUMBER; setText(prefs.getInt("typing_wpm", 40).toString()) }
                val dialog = AlertDialog.Builder(activity).setTitle("Your typing speed").setMessage("Words you normally type per minute (1–300). Insights uses this only to estimate time saved. It does not change dictation or insertion speed.").setView(input).setPositiveButton("Save", null).setNegativeButton("Cancel", null).create()
                dialog.setOnShowListener { dialog.getButton(-1).setOnClickListener {
                    val value = input.text.toString().toIntOrNull()
                    if (value == null || value !in 1..300) input.error = "Enter 1–300" else { prefs.edit().putInt("typing_wpm", value).apply(); dialog.dismiss() }
                } }; dialog.show()
            })
            addView(note("Typing speed is measured in words per minute, not seconds. For example, 40 words/minute means typing 40 words in one minute. It only affects the estimated typing time saved in Insights."))
        }
        fun providers(): View = row("Saved providers · ${providerSummary()}") { ProviderProfilesUi(activity).show() }.also { providerValues += it.findViewWithTag<TextView>("setting-subtitle") }
        val speech = column().apply {
            addView(engine); addView(providers()); addView(row("Transcription provider · HTTPS URL, speech model and key") { provider() })
            addView(note("Use cloud recognition without downloading a local model. Compatible custom endpoints use the existing OpenAI-compatible adapters; provider availability and language support vary."))
            addView(ModernUi.label(activity, "Local models", 18f, strong = true).apply { tag = "settings-local-models" })
            models.visibility = View.VISIBLE; modelList = models; addView(models)
        }
        val language = column().apply {
            addView(note("Direct preserves recognized speech. AI Cleanup sends transcript text to the independently selected cleanup provider. Tone rules apply only when cleanup is enabled."))
            addView(cleanupControls); addView(providers())
            addView(row("View base cleanup prompt · Read-only faithful cleanup rules") {
                AlertDialog.Builder(activity).setTitle("Base cleanup prompt (read-only)").setView(ScrollView(activity).apply { addView(note(PostProcessor.DEFAULT_PROMPT).apply { setTextIsSelectable(true) }) }).setPositiveButton("Close", null).show()
            }.apply { tag = "settings-view-prompt" })
            addView(row("Reset custom refinements · Return to the default cleanup prompt") { confirm("Remove custom refinements? The fixed cleanup prompt, dictionary and provider keys remain.") {
                resetCustomRefinements(); message("Custom refinements reset")
            } }.apply { tag = "settings-reset-prompt" })
        }
        val privacy = column().apply {
            addView(note("Text history, dictionary, snippets and usage statistics are stored on this phone. Local speech recognition processes audio on-device. Cloud recognition sends recorded audio to your selected speech provider. Optional AI Cleanup sends transcript text, selected tone and custom refinements to your cleanup provider, including when speech recognition is local. App assignments and installed app lists are never sent."))
            val audioLabel = note("")
            fun audioSummary() { val days = prefs.getInt("audio_retention_days", 0); audioLabel.text = if (days == 0) "Audio saving is off (default). Recordings are discarded after recognition." else "Audio is saved locally for $days days." }
            audioSummary(); addView(audioLabel)
            addView(row("Keep audio · Choose retention") {
                choose("Audio retention", listOf("Off — discard audio", "1 day", "7 days", "30 days"), listOf(0,1,7,30), "audio_retention_days", 0) { RetentionWorker.runSoon(activity); audioSummary(); refreshStorage() }
            })
            val retention = row("Text history retention · ${textRetention()}") { }
            retention.setOnClickListener { choose("Keep text history", listOf("Until I delete it", "7 days", "30 days", "90 days"), listOf(0,7,30,90), "text_retention_days", 0) {
                retention.findViewWithTag<TextView>("setting-subtitle").text = textRetention(); RetentionWorker.runSoon(activity)
            } }; addView(retention)
            storageValue = note("Calculating storage…"); addView(storageValue)
            addView(backupControls)
            addView(row("Clear text history") { confirm("Delete all saved text? Usage statistics remain.") { thread { HistoryRepository(activity).use { it.clearHistory() }; message("History cleared"); refreshStorage() } } })
            addView(row("Reset statistics") { confirm("Reset usage statistics? Text history remains.") { thread { HistoryRepository(activity).use { it.resetStatistics() }; message("Statistics reset") } } })
        }
        val system = column().apply {
            addView(setup)
            if (android.os.Build.MANUFACTURER.trim().lowercase(java.util.Locale.ROOT) in setOf("xiaomi", "redmi", "poco")) {
                addView(ModernUi.settingRow(activity, activity.getString(R.string.background_autostart_row), {
                    AlertDialog.Builder(activity).setTitle(R.string.background_autostart_title).setMessage(R.string.background_autostart_help)
                        .setPositiveButton(R.string.background_autostart_open) { _, _ -> openBackgroundStartupSettings() }.setNegativeButton("Close", null).show()
                }, R.drawable.ic_ui_background_startup).apply { tag = "startup-help" })
            }
            addView(note("Installed version: $installedVersion"))
            diagnostics = note("").apply { tag = "settings-diagnostics" }; addView(diagnostics)
            addView(AppUpdatesUi(activity).view)
            addView(aboutControls)
        }
        router.page(SettingsDestination.PREFERENCES, preferences); router.page(SettingsDestination.TONE, tone.view)
        router.page(SettingsDestination.SPEECH, speech); router.page(SettingsDestination.LANGUAGE, language)
        router.page(SettingsDestination.PRIVACY, privacy); router.page(SettingsDestination.SYSTEM, system)
        refresh(); return router.view
    }
    private fun textRetention(): String = prefs.getInt("text_retention_days", 0).let { if (it == 0) "Until I delete it" else "$it days" }
    fun toggleLocalModels() {
        router.open(SettingsDestination.SPEECH)
        modelList?.post { modelList?.let { it.requestRectangleOnScreen(android.graphics.Rect(0, 0, it.width, dp(80)), false) } }
    }
    private fun confirm(label: String, action: () -> Unit) {
        AlertDialog.Builder(activity).setMessage(label).setPositiveButton("Confirm") { _, _ -> action() }.setNegativeButton("Cancel", null).show()
    }
    private fun openBackgroundStartupSettings() {
        val intents = listOf(
            android.content.Intent().setClassName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity").putExtra("package_name", activity.packageName),
            android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:${activity.packageName}")))
        for (intent in intents) {
            try { activity.startActivity(intent); return }
            catch (_: android.content.ActivityNotFoundException) { }
            catch (_: SecurityException) { }
        }
        message(activity.getString(R.string.background_autostart_unavailable))
    }
    private fun choose(title: String, labels: List<String>, values: List<Int>, key: String, default: Int, action: () -> Unit) {
        AlertDialog.Builder(activity).setTitle(title).setSingleChoiceItems(labels.toTypedArray(), values.indexOf(prefs.getInt(key, default))) { dialog, index ->
            prefs.edit().putInt(key, values[index]).apply(); dialog.dismiss(); action()
        }.setNegativeButton("Cancel", null).show()
    }
    private fun provider() {
        val fields = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; setPadding(32,16,32,16) }
        fun field(label: String, value: String, secret: Boolean = false): EditText {
            fields.addView(TextView(activity).apply { text = label })
            return EditText(activity).apply {
                setText(value); isSingleLine = true
                inputType = if (secret) InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD else InputType.TYPE_CLASS_TEXT
                isSaveEnabled = !secret
                if (secret) importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO
                fields.addView(this)
            }
        }
        val url = field("HTTPS API base URL", prefs.getString("transcription_base_url", "https://api.groq.com/openai/v1").orEmpty())
        val model = field("Speech model name", prefs.getString("transcription_model", "whisper-large-v3").orEmpty())
        val key = field("API key", SecureKeyStorage.transcriptionApiKey(activity), true)
        fields.addView(ModernUi.label(activity,
            "Use an HTTPS OpenAI-compatible speech-to-text service and a speech model. Example: Groq base URL https://api.groq.com/openai/v1 with model whisper-large-v3-turbo. Enter the key issued by the same provider.", 13f, muted = true).apply {
            setPadding(dp(4), dp(12), dp(4), dp(12))
        })
        fields.addView(row("Test connection (queries model list)") {
            val config = ProviderConfig(url.text.toString(), model.text.toString())
            try {
                val endpoint = config.transcriptionUrl().toString().removeSuffix("/audio/transcriptions") + "/models"
                val apiKey = key.text.toString().trim()
                require(apiKey.isNotBlank())
                thread {
                    try {
                        okhttp3.OkHttpClient().newCall(okhttp3.Request.Builder().url(endpoint).header("Authorization", "Bearer $apiKey").build()).execute().use {
                            message(if (it.isSuccessful) "Provider reached. Test a dictation to verify this speech model." else "Provider returned HTTP ${it.code}. Some providers do not expose model lists.")
                        }
                    } catch (_: Exception) { message("Couldn’t connect. Check the URL and network.") }
                }
            } catch (_: Exception) { message("Enter a valid HTTPS URL, model, and key") }
        })
        val dialog = AlertDialog.Builder(activity).setTitle("Transcription provider").setView(ScrollView(activity).apply { addView(fields) })
            .setPositiveButton("Save", null).setNegativeButton("Cancel", null).create()
        dialog.setOnShowListener { dialog.getButton(-1).setOnClickListener {
            try {
                ProviderConfig(url.text.toString(), model.text.toString()).transcriptionUrl()
                ProviderProfiles.locked {
                    SecureKeyStorage.saveTranscriptionApiKey(activity, key.text.toString().trim())
                    prefs.edit().putString("transcription_base_url", url.text.toString().trim()).putString("transcription_model", model.text.toString().trim())
                        .putString("transcription_api_format", SpeechApiFormat.MULTIPART.name).remove(ProviderProfiles.SPEECH_ID).commit()
                }
                dialog.dismiss()
                activity.recreate()
            } catch (_: Exception) { url.error = "Enter a valid HTTPS URL and model" }
        } }; dialog.show()
    }
}

