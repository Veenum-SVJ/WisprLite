package com.edib.openwhispr

enum class DictationLanguage(val title: String, val languageCode: String?) {
    AUTO("Mixed / Auto", null),
    ENGLISH("English", "en"),
    YORUBA("Yorùbá", "yo"),
    // Whisper has no Pidgin code. Use its English decoder as a recognition hint
    // instead of allowing unrelated language detection on short Pidgin speech.
    PIDGIN("Nigerian Pidgin", "en");

    val recognitionHint: String get() = when (this) {
        PIDGIN -> "Nigerian Pidgin: Abeg, I dey come. Na so e be. Make we go."
        AUTO -> "English, Yorùbá, Nigerian Pidgin."
        else -> ""
    }
    fun acceptsModel(languages: Set<String>): Boolean = when (this) {
        ENGLISH -> "en" in languages
        YORUBA -> "yo" in languages
        AUTO, PIDGIN -> languages.containsAll(setOf("en", "yo"))
    }

    companion object {
        fun fromSaved(value: String?): DictationLanguage = entries.firstOrNull { it.name == value } ?: AUTO
    }
}
