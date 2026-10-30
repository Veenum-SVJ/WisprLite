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
    private fun row(label: String, action: () -> Unit) = Button(activity).apply {
        text = label; isAllCaps = false; setOnClickListener { action() }
    }
    fun build(): View = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        addView(row("Transcription provider · URL, model and key") { provider() })
        addView(row("Language · ${DictationLanguage.fromSaved(prefs.getString("language_profile", null)).title}") {
            val values = DictationLanguage.entries
            AlertDialog.Builder(activity).setTitle("Dictation language")
                .setSingleChoiceItems(values.map { it.title }.toTypedArray(), values.indexOf(DictationLanguage.fromSaved(prefs.getString("language_profile", null)))) { dialog, index ->
                    val selected = values[index]
                    dialog.dismiss(); activity.selectLanguageProfile(selected)
                }.setNegativeButton("Cancel", null).show()
        })
        addView(TextView(activity).apply { text = "Mixed/Auto preserves language switches. Pidgin is a recognition profile; accuracy depends on your model. Cloud cleanup sends transcript text, including with local speech recognition." })
        addView(row("Appearance · light, dark or system") {
            choose("Appearance", listOf("System", "Light", "Dark"), listOf(-1, 1, 2), "appearance", -1) {
                AppCompatDelegate.setDefaultNightMode(prefs.getInt("appearance", -1))
            }
        })
        addView(row("Keep audio · ${prefs.getInt("audio_retention_days", 0)} days (0 = off)") {
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

