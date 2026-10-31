package com.edib.openwhispr

import android.app.AlertDialog
import android.content.Context
import android.text.InputType
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatDelegate
import kotlin.concurrent.thread

class SettingsExtras(private val activity: MainActivity) {
    private val prefs = activity.getSharedPreferences("openwhispr", Context.MODE_PRIVATE)
    private fun message(value: String) = activity.runOnUiThread { Toast.makeText(activity, value, Toast.LENGTH_LONG).show() }
    private fun dp(value: Int) = (value * activity.resources.displayMetrics.density).toInt()
    private fun heading(label: String) = TextView(activity).apply {
        text = label; textSize = 13f; setTypeface(typeface, android.graphics.Typeface.BOLD)
        setTextColor(androidx.core.content.ContextCompat.getColor(activity, R.color.primary))
        setPadding(dp(2), dp(20), dp(2), dp(10))
    }
    private fun row(label: String, action: () -> Unit) = ModernUi.settingRow(activity, label, action)
    fun build(): View = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        addView(heading("Speech and language"))
        addView(row("Language · ${DictationLanguage.fromSaved(prefs.getString("language_profile", null)).title}") {
            activity.showLanguageProfilePicker()
        })
        addView(row("Transcription provider · URL, model and key") { provider() })
        addView(row("Language help") {
            AlertDialog.Builder(activity).setTitle("Choosing a language")
                .setMessage("Mixed/Auto lets the model detect the language and can misidentify short phrases. Choose Yorùbá for Yoruba speech. Pidgin uses English-based recognition plus cloud vocabulary hints where a separate Pidgin code is unavailable. Recognition and tone-mark accuracy depend on your model.")
                .setPositiveButton("Close", null).show()
        })
        addView(TextView(activity).apply {
            text = "AI cleanup sends transcript text to your selected provider, even with offline recognition."
            textSize = 14f; setPadding(dp(8), dp(4), dp(8), dp(8))
        })
        addView(heading("Recording"))
        for ((key, label, default) in listOf(Triple("recording_sound", "Recording stop sound", true), Triple("recording_vibration", "Recording vibration", false))) {
            addView(com.google.android.material.materialswitch.MaterialSwitch(activity).apply {
                text = label; isChecked = prefs.getBoolean(key, default)
                setPadding(dp(8), dp(6), dp(8), dp(6))
                setOnCheckedChangeListener { _, checked -> prefs.edit().putBoolean(key, checked).apply() }
            })
        }
        addView(heading("Storage and appearance"))
        val storage = TextView(activity).apply { text = "Model storage: calculating…"; textSize = 14f; setPadding(dp(8), dp(4), dp(8), dp(10)) }
        addView(storage)
        thread {
            val bytes = java.io.File(activity.filesDir, "models").walkTopDown().filter { it.isFile }.sumOf { it.length() }
            activity.runOnUiThread { if (!activity.isDestroyed) storage.text = "Model storage: ${android.text.format.Formatter.formatFileSize(activity, bytes)}" }
        }
        addView(row("Appearance · light, dark or system") {
            choose("Appearance", listOf("System", "Light", "Dark"), listOf(-1, 1, 2), "appearance", -1) {
                AppCompatDelegate.setDefaultNightMode(prefs.getInt("appearance", -1))
            }
        })
        val audioDays = prefs.getInt("audio_retention_days", 0)
        addView(row("Keep audio · ${if (audioDays == 0) "Off" else "$audioDays days"}") {
            choose("Audio retention", listOf("Off — discard audio", "1 day", "7 days", "30 days"), listOf(0,1,7,30), "audio_retention_days", 0) {
                RetentionWorker.runSoon(activity); activity.recreate()
            }
        })
        addView(row("Text history retention") {
            choose("Keep text history", listOf("Until I delete it", "7 days", "30 days", "90 days"), listOf(0,7,30,90), "text_retention_days", 0) {
                RetentionWorker.runSoon(activity); activity.recreate()
            }
        })
        addView(row("Typing speed · ${prefs.getInt("typing_wpm", 40)} words/minute") {
            val input = EditText(activity).apply { inputType = InputType.TYPE_CLASS_NUMBER; setText(prefs.getInt("typing_wpm", 40).toString()) }
            val dialog = AlertDialog.Builder(activity).setTitle("Typing speed (1–300 WPM)").setView(input).setPositiveButton("Save", null).setNegativeButton("Cancel", null).create()
            dialog.setOnShowListener { dialog.getButton(-1).setOnClickListener {
                val value = input.text.toString().toIntOrNull()
                if (value == null || value !in 1..300) input.error = "Enter 1–300"
                else { prefs.edit().putInt("typing_wpm", value).apply(); dialog.dismiss(); activity.recreate() }
            } }; dialog.show()
        })
        addView(heading("Manage your data"))
        addView(row("Clear text history") { confirm("Delete all saved text? Statistics remain.") {
            thread { HistoryRepository(activity).use { it.clearHistory() }; message("History cleared") }
        } })
        addView(row("Reset statistics") { confirm("Reset usage statistics? Text history remains.") {
            thread { HistoryRepository(activity).use { it.resetStatistics() }; message("Statistics reset") }
        } })
    }
    private fun confirm(label: String, action: () -> Unit) {
        AlertDialog.Builder(activity).setMessage(label).setPositiveButton("Confirm") { _, _ -> action() }.setNegativeButton("Cancel", null).show()
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
                SecureKeyStorage.saveTranscriptionApiKey(activity, key.text.toString().trim())
                prefs.edit().putString("transcription_base_url", url.text.toString().trim()).putString("transcription_model", model.text.toString().trim()).apply()
                dialog.dismiss()
            } catch (_: Exception) { url.error = "Enter a valid HTTPS URL and model" }
        } }; dialog.show()
    }
}

