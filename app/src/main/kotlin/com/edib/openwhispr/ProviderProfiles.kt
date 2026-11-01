package com.edib.openwhispr

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Metadata contains no API keys. A profile can be selected independently for each task. */
data class ProviderProfile(
    val id: String,
    val name: String,
    val baseUrl: String,
    val speechModel: String,
    val cleanupModel: String,
    val speechFormat: SpeechApiFormat = SpeechApiFormat.MULTIPART,
) {
    fun validate() {
        SecureKeyStorage.profileKey(id)
        require(name.isNotBlank() && name.length <= 80) { "Enter a provider name of up to 80 characters" }
        require(baseUrl.length <= 2048 && speechModel.length <= 200 && cleanupModel.length <= 200) { "Provider configuration is too long" }
        require(speechModel.isNotBlank() || cleanupModel.isNotBlank()) { "Enter a speech or cleanup model" }
        if (speechModel.isNotBlank()) ProviderConfig(baseUrl, speechModel, apiFormat = speechFormat).transcriptionUrl()
        if (cleanupModel.isNotBlank()) CleanupProviderConfig(CleanupProviderConfig.Provider.OPENAI_COMPATIBLE, baseUrl, cleanupModel).chatCompletionsUrl()
    }
}

internal object ProviderProfileCodec {
    fun encode(profiles: List<ProviderProfile>): String {
        require(profiles.size <= 32 && profiles.map { it.id }.toSet().size == profiles.size)
        return JSONArray().apply {
            profiles.forEach { profile ->
                profile.validate()
                put(JSONObject().put("id", profile.id).put("name", profile.name).put("url", profile.baseUrl)
                    .put("speech", profile.speechModel).put("cleanup", profile.cleanupModel).put("format", profile.speechFormat.name))
            }
        }.toString().also { require(it.length <= 65_536) }
    }
    fun decode(value: String): List<ProviderProfile> {
        require(value.length <= 65_536)
        val array = JSONArray(value)
        require(array.length() <= 32)
        val profiles = (0 until array.length()).map { index ->
            val item = array.getJSONObject(index)
            val format = SpeechApiFormat.valueOf(item.getString("format"))
            ProviderProfile(item.getString("id"), item.getString("name"), item.getString("url"),
                item.getString("speech"), item.getString("cleanup"), format).also { it.validate() }
        }
        require(profiles.map { it.id }.toSet().size == profiles.size)
        return profiles
    }
}

class ProviderProfiles(private val context: Context) {
    private val prefs get() = context.getSharedPreferences("openwhispr", Context.MODE_PRIVATE)
    fun all(): List<ProviderProfile> = ProviderProfileCodec.decode(prefs.getString(METADATA_KEY, "[]").orEmpty())
    fun activeSpeechId(): String? = prefs.getString(SPEECH_ID, null)
    fun activeCleanupId(): String? = prefs.getString(CLEANUP_ID, null)
    fun save(profile: ProviderProfile, key: String) = locked {
        profile.validate()
        val existing = all()
        val metadata = ProviderProfileCodec.encode(existing.filterNot { it.id == profile.id } + profile)
        val oldKey = SecureKeyStorage.profileApiKey(context, profile.id)
        SecureKeyStorage.saveProfileApiKey(context, profile.id, key.trim())
        if (!prefs.edit().putString(METADATA_KEY, metadata).commit()) {
            SecureKeyStorage.saveProfileApiKey(context, profile.id, oldKey)
            error("Couldn’t save provider profile")
        }
        // Saving an edit to a selected profile explicitly updates that profile.
    }
    fun activateSpeech(id: String) = locked {
        val profile = all().single { it.id == id }
        require(profile.speechModel.isNotBlank()) { "This profile has no speech model" }
        val key = SecureKeyStorage.profileApiKey(context, id)
        require(key.isNotBlank()) { "Add this profile’s API key first" }
        check(prefs.edit().putString(SPEECH_ID, id).commit())
        // Choosing credentials does not enable cloud recording or change cleanup.
    }
    fun activateCleanup(id: String) = locked {
        val profile = all().single { it.id == id }
        require(profile.cleanupModel.isNotBlank()) { "This profile has no cleanup model" }
        val key = SecureKeyStorage.profileApiKey(context, id)
        require(key.isNotBlank()) { "Add this profile’s API key first" }
        check(prefs.edit().putString(CLEANUP_ID, id).commit())
    }
    fun delete(id: String) = locked {
        require(id != activeSpeechId() && id != activeCleanupId()) { "Choose another profile or save manual settings before deleting an active profile" }
        check(prefs.edit().putString(METADATA_KEY, ProviderProfileCodec.encode(all().filterNot { it.id == id })).commit())
        SecureKeyStorage.saveProfileApiKey(context, id, "")
    }
    fun exportKeys(): Map<String, String> = all().associate { SecureKeyStorage.profileKey(it.id) to SecureKeyStorage.profileApiKey(context, it.id) }
    data class SpeechSelection(val config: ProviderConfig, val key: String)
    data class CleanupSelection(val config: CleanupProviderConfig, val key: String)
    fun speechSelection(): SpeechSelection = synchronized(lock) {
        val id = activeSpeechId()
        if (id != null) {
            val selected = all().singleOrNull { it.id == id } ?: error("Selected speech profile is missing")
            require(selected.speechModel.isNotBlank()) { "Selected profile has no speech model" }
            SpeechSelection(ProviderConfig(selected.baseUrl, selected.speechModel, apiFormat = selected.speechFormat), SecureKeyStorage.profileApiKey(context, id))
        } else SpeechSelection(ProviderConfig(prefs.getString("transcription_base_url", null) ?: "https://api.groq.com/openai/v1",
            prefs.getString("transcription_model", null) ?: "whisper-large-v3",
            apiFormat = SpeechApiFormat.fromSaved(prefs.getString("transcription_api_format", null))), SecureKeyStorage.transcriptionApiKey(context))
    }
    fun cleanupSelection(): CleanupSelection = synchronized(lock) {
        val id = activeCleanupId()
        if (id != null) {
            val selected = all().singleOrNull { it.id == id } ?: error("Selected cleanup profile is missing")
            require(selected.cleanupModel.isNotBlank()) { "Selected profile has no cleanup model" }
            CleanupSelection(CleanupProviderConfig(CleanupProviderConfig.Provider.OPENAI_COMPATIBLE, selected.baseUrl, selected.cleanupModel), SecureKeyStorage.profileApiKey(context, id))
        } else {
            val config = CleanupProviderConfig.fromPreferences(prefs.getString("cleanup_provider", null), prefs.getString("cleanup_base_url", null), prefs.getString("cleanup_model", null))
            CleanupSelection(config, if (config.provider == CleanupProviderConfig.Provider.GROQ) SecureKeyStorage.groqApiKey(context) else SecureKeyStorage.cleanupApiKey(context))
        }
    }
    companion object {
        const val METADATA_KEY = "provider_profiles"
        const val SPEECH_ID = "speech_provider_profile"
        const val CLEANUP_ID = "cleanup_provider_profile"
        fun newId(): String = java.util.UUID.randomUUID().toString().replace("-", "")
        private val lock = Any()
        internal fun <T> locked(action: () -> T): T = synchronized(lock, action)
    }
}
