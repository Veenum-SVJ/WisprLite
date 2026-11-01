package com.edib.openwhispr

import java.net.URI

enum class SpeechApiFormat {
    MULTIPART, AUDIO_CHAT;

    companion object {
        fun fromSaved(value: String?): SpeechApiFormat = entries.firstOrNull { it.name == value } ?: MULTIPART
    }
}

/** OpenAI-compatible API root; endpoints and credentials are never logged. */
data class ProviderConfig(
    val baseUrl: String = "https://api.groq.com/openai/v1",
    val model: String = "whisper-large-v3",
    val recognitionHint: String = "",
    val apiFormat: SpeechApiFormat = SpeechApiFormat.MULTIPART,
) {
    fun transcriptionUrl(): URI {
        require(model.isNotBlank()) { "Transcription model is required" }
        val uri = try { URI(baseUrl.trim()) } catch (_: Exception) {
            throw IllegalArgumentException("Enter a valid HTTPS API URL")
        }
        require(uri.scheme.equals("https", true) && !uri.host.isNullOrBlank()) { "API URL must use HTTPS" }
        require(uri.rawUserInfo == null && uri.rawQuery == null && uri.rawFragment == null) {
            "API URL cannot contain credentials, query parameters, or a fragment"
        }
        val path = uri.rawPath.orEmpty().trimEnd('/')
            .removeSuffix("/audio/transcriptions").removeSuffix("/chat/completions")
        val suffix = when (apiFormat) {
            SpeechApiFormat.MULTIPART -> "/audio/transcriptions"
            SpeechApiFormat.AUDIO_CHAT -> "/chat/completions"
        }
        val endpoint = "$path$suffix"
        return URI("https://${uri.rawAuthority}$endpoint")
    }
}
