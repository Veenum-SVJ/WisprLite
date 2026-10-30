package com.edib.openwhispr

object InsightCalculator {
    fun wordCount(text: String): Int = Regex("[\\p{L}\\p{N}][\\p{L}\\p{M}\\p{N}'’-]*").findAll(text).count()

    fun savedSeconds(words: Int, speakingMillis: Long, typingWordsPerMinute: Int): Double {
        require(typingWordsPerMinute > 0) { "Typing speed must be positive" }
        require(words >= 0 && speakingMillis >= 0) { "Counts and duration must not be negative" }
        return (words * 60.0 / typingWordsPerMinute - speakingMillis / 1000.0).coerceAtLeast(0.0)
    }
}
