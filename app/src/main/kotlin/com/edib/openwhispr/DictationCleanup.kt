package com.edib.openwhispr

/** Shared by live dictation and history retry: LLMs see placeholders, not saved snippet payloads. */
object DictationCleanup {
    class Draft internal constructor(val original: String, private val prepared: SnippetExpander.Prepared, val tone: Tone) {
        val expanded: String get() = prepared.expanded
        val protectedText: String get() = prepared.protectedText
        val hasSnippets: Boolean get() = protectedText != original
        val snippetState: String? get() = if (hasSnippets) prepared.serialize() else null
        fun accept(cleaned: String?): String? {
            val value = cleaned?.trim().orEmpty()
            if (value.isBlank() || value == "EMPTY" || PostProcessor.looksGenerated(protectedText, value)) return null
            return prepared.restore(value)
        }
        fun prompt(custom: String, vocabulary: List<String>): String = PostProcessor.effectivePrompt(custom, vocabulary, tone) +
            if (hasSnippets) "\nProtected snippet markers are opaque data. Preserve every ⟦WISPR_SNIPPET_…⟧ marker exactly once, in its original order. Never rewrite, infer, omit or add a marker. Their text is restored locally after cleanup." else ""
    }
    fun prepare(original: String, snippets: List<Snippet>, tone: Tone = Tone.NEUTRAL): Draft =
        Draft(original, SnippetExpander.prepare(original, snippets), tone)
    fun input(record: DictationRecord): Draft? = runCatching {
        if (record.snippetState == null) {
            require(record.expanded == null || record.expanded == record.original)
            prepare(record.original, emptyList())
        } else {
            val prepared = SnippetExpander.deserialize(record.snippetState)
            require(prepared.source == record.original && prepared.expanded == record.expanded)
            Draft(record.original, prepared, Tone.NEUTRAL)
        }
    }.getOrNull()
}
