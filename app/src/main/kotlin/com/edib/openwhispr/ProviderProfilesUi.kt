package com.edib.openwhispr

import android.app.AlertDialog
import android.text.InputType
import android.view.View
import android.widget.*

/** Small native profile editor; credentials are never included in list labels. */
class ProviderProfilesUi(private val activity: MainActivity) {
    private val store = ProviderProfiles(activity)
    private fun error(action: () -> Unit) {
        try { action() } catch (_: Exception) { Toast.makeText(activity, "Couldn’t update provider. Check its name, HTTPS URL, models and key.", Toast.LENGTH_LONG).show() }
    }
    fun show() = error {
        val profiles = store.all()
        val labels = profiles.map {
            it.name + listOfNotNull(if (it.id == store.activeSpeechId()) "speech" else null,
                if (it.id == store.activeCleanupId()) "cleanup" else null).takeIf { tags -> tags.isNotEmpty() }?.joinToString(", ", " · ").orEmpty()
        }
        AlertDialog.Builder(activity).setTitle("Saved providers")
            .setItems((labels + "Add provider…").toTypedArray()) { _, index ->
                if (index == profiles.size) preset() else actions(profiles[index])
            }.setNegativeButton("Close", null).show()
    }
    private fun preset() {
        AlertDialog.Builder(activity).setTitle("Add provider")
            .setItems(arrayOf("Groq", "Google AI Studio", "Custom")) { _, index ->
                val id = ProviderProfiles.newId()
                edit(when (index) {
                    0 -> ProviderProfile(id, "Groq", "https://api.groq.com/openai/v1", "whisper-large-v3", "openai/gpt-oss-120b")
                    1 -> ProviderProfile(id, "Google AI Studio", "https://generativelanguage.googleapis.com/v1beta/openai", "gemini-3.8-flash", "gemini-3.8-flash", SpeechApiFormat.AUDIO_CHAT)
                    else -> ProviderProfile(id, "", "https://", "", "")
                })
            }.setNegativeButton("Cancel", null).show()
    }
    private fun actions(profile: ProviderProfile) {
        val labels = mutableListOf<String>()
        val actions = mutableListOf<() -> Unit>()
        if (profile.speechModel.isNotBlank()) {
            labels += "Use for transcription"; actions += { error { store.activateSpeech(profile.id); activity.recreate() } }
        }
        if (profile.cleanupModel.isNotBlank()) {
            labels += "Use for AI cleanup"; actions += { error { store.activateCleanup(profile.id); activity.recreate() } }
        }
        labels += "Edit"; actions += { edit(profile) }
        labels += "Delete"; actions += {
            AlertDialog.Builder(activity).setTitle("Delete ${profile.name}?")
                .setMessage("This removes its saved key. Select another provider or save manual settings before deleting an active profile.")
                .setPositiveButton("Delete") { _, _ -> error { store.delete(profile.id); show() } }.setNegativeButton("Cancel", null).show()
        }
        AlertDialog.Builder(activity).setTitle(profile.name).setItems(labels.toTypedArray()) { _, index -> actions[index]() }
            .setNegativeButton("Close", null).show()
    }
    private fun edit(profile: ProviderProfile) {
        val fields = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; setPadding(32, 16, 32, 16) }
        fun field(label: String, value: String, secret: Boolean = false): EditText {
            fields.addView(ModernUi.label(activity, label, 13f, muted = true))
            return EditText(activity).apply {
                contentDescription = label; isSingleLine = true; setText(value)
                inputType = InputType.TYPE_CLASS_TEXT or if (secret) InputType.TYPE_TEXT_VARIATION_PASSWORD else InputType.TYPE_TEXT_VARIATION_NORMAL
                if (secret) { isSaveEnabled = false; importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO }
                fields.addView(this)
            }
        }
        val name = field("Profile name", profile.name)
        val url = field("HTTPS API base URL", profile.baseUrl)
        val speech = field("Speech model (optional)", profile.speechModel)
        val cleanup = field("Cleanup model (optional)", profile.cleanupModel)
        fields.addView(ModernUi.label(activity, "Speech API format", 13f, muted = true))
        val format = Spinner(activity).apply {
            adapter = ArrayAdapter(activity, android.R.layout.simple_spinner_dropdown_item, arrayOf("Whisper multipart", "Audio chat (Google)"))
            setSelection(if (profile.speechFormat == SpeechApiFormat.AUDIO_CHAT) 1 else 0)
            contentDescription = "Speech API format"; fields.addView(this)
        }
        val key = field("Provider API key", SecureKeyStorage.profileApiKey(activity, profile.id), true)
        fields.addView(ModernUi.label(activity, "Leave an unused model blank. Saving keeps the provider available; choose Use to select it. Editing a selected profile updates that selection. Cloud recording and AI cleanup remain separate switches. Availability and language accuracy depend on the model and your account.", 13f, muted = true))
        val dialog = AlertDialog.Builder(activity).setTitle("Provider profile")
            .setView(ScrollView(activity).apply { addView(fields) }).setPositiveButton("Save", null).setNegativeButton("Cancel", null).create()
        dialog.setOnShowListener {
            dialog.getButton(-1).setOnClickListener {
                error {
                    store.save(ProviderProfile(profile.id, name.text.toString().trim(), url.text.toString().trim(), speech.text.toString().trim(),
                        cleanup.text.toString().trim(), if (format.selectedItemPosition == 1) SpeechApiFormat.AUDIO_CHAT else SpeechApiFormat.MULTIPART), key.text.toString())
                    activity.refreshSettingsState()
                    dialog.dismiss(); show()
                }
            }
        }
        dialog.show()
    }
}
