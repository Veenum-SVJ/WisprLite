package com.edib.openwhispr

import java.net.URI

/** OpenAI-compatible API root; endpoints and credentials are never logged. */
data class ProviderConfig(
    val baseUrl: String = "https://api.groq.com/openai/v1",
    val model: String = "whisper-large-v3",
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
        val endpoint = if (path.endsWith("/audio/transcriptions")) path else "$path/audio/transcriptions"
        return URI("https://${uri.rawAuthority}$endpoint")
    }
}
