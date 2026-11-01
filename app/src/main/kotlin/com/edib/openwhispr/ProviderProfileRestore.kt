package com.edib.openwhispr

/** Prepare partial restores without orphaning selections or reusing a key at a changed endpoint. */
internal object ProviderProfileRestore {
    fun prepare(current: Map<String, Any?>, incoming: Map<String, Any>, selected: Set<String>, selectedSecrets: Set<String>): Map<String, Any> {
        val changes = incoming.filterKeys { it in selected }.toMutableMap()
        val target = current + changes
        fun speech(values: Map<String, Any?>) = (values["transcription_base_url"] as? String)
            ?.trim()?.trimEnd('/')?.takeIf { it.isNotBlank() } ?: "https://api.groq.com/openai/v1"
        fun cleanup(values: Map<String, Any?>): String = CleanupProviderConfig.fromPreferences(
            values["cleanup_provider"] as? String, values["cleanup_base_url"] as? String,
            values["cleanup_model"] as? String).chatCompletionsUrl().toString()
        fun guard(key: String, before: String, source: String, after: String) {
            require(before == after || key in selectedSecrets) {
                "Select the matching API key when restoring a changed manual provider URL"
            }
            require(key !in selectedSecrets || source == after) {
                "Restore the matching manual provider settings with this API key"
            }
        }
        guard("transcription_api_key", speech(current), speech(incoming), speech(target))
        // The Groq key has a fixed destination. Custom cleanup keys must stay paired
        // with their configured endpoint, even while another provider is selected.
        fun customCleanup(values: Map<String, Any?>) = cleanup(values + ("cleanup_provider" to "openai_compatible"))
        guard("cleanup_api_key", customCleanup(current), customCleanup(incoming), customCleanup(target))
        val existing = ProviderProfileCodec.decode(current[ProviderProfiles.METADATA_KEY] as? String ?: "[]")
        val imported = incoming[ProviderProfiles.METADATA_KEY]?.let { ProviderProfileCodec.decode(it as String) }.orEmpty()
        var profiles = existing
        if (ProviderProfiles.METADATA_KEY in changes) {
            for (profile in imported) {
                val old = existing.singleOrNull { it.id == profile.id }
                require(old == null || old.baseUrl == profile.baseUrl || SecureKeyStorage.profileKey(profile.id) in selectedSecrets) {
                    "Select the matching profile key when restoring a changed provider URL"
                }
            }
            profiles = existing.filterNot { old -> imported.any { it.id == old.id } } + imported
            changes[ProviderProfiles.METADATA_KEY] = ProviderProfileCodec.encode(profiles)
        }
        for (secret in selectedSecrets.filter { it.matches(Regex("provider_profile_[a-f0-9]{32}")) }) {
            val id = secret.removePrefix("provider_profile_")
            val source = imported.singleOrNull { it.id == id }
            val target = profiles.singleOrNull { it.id == id }
            require(source != null && target != null && source.baseUrl == target.baseUrl) {
                "Restore matching profile metadata with this key"
            }
        }
        for (key in listOf(ProviderProfiles.SPEECH_ID, ProviderProfiles.CLEANUP_ID)) {
            val id = (changes[key] ?: current[key]) as? String ?: continue
            val profile = profiles.singleOrNull { it.id == id }
            require(profile != null) { "Restore the saved profile together with its selection" }
            require(if (key == ProviderProfiles.SPEECH_ID) profile.speechModel.isNotBlank() else profile.cleanupModel.isNotBlank()) {
                "The selected profile does not support this task"
            }
        }
        return changes
    }
}
