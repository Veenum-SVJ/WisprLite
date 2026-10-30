package com.edib.openwhispr

enum class DictationLanguage(val title: String, val languageCode: String?) {
    AUTO("Mixed / Auto", null),
    ENGLISH("English", "en"),
    YORUBA("Yorùbá", "yo"),
    PIDGIN("Nigerian Pidgin", null);

    companion object {
        fun fromSaved(value: String?): DictationLanguage = entries.firstOrNull { it.name == value } ?: AUTO
    }
}
