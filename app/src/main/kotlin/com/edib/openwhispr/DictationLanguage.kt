package com.edib.openwhispr

enum class DictationLanguage(val title: String, val languageCode: String?) {
    AUTO("Auto-detect", null),
    ENGLISH("English", "en");

    val recognitionHint: String get() = ""
    fun acceptsModel(languages: Set<String>): Boolean = when (this) {
        ENGLISH -> "en" in languages
        AUTO -> true
    }

    companion object {
        fun fromSaved(value: String?): DictationLanguage = when (value) {
            "AUTO", "YORUBA", "PIDGIN" -> AUTO
            else -> ENGLISH
        }
    }
}
