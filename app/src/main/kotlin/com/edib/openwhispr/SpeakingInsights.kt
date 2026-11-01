package com.edib.openwhispr

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class SpeakingEvent(val createdAt: Long, val words: Long, val speakingMillis: Long)
data class SpeakingDay(val date: LocalDate, val count: Long, val words: Long, val duration: Long) {
    val speakingMillis: Long get() = duration
}
data class SpeakingSummary(val days: List<SpeakingDay>, val currentStreak: Int,
    val longestStreak: Int, val recognizedWpm: Double)

object SpeakingInsights {
    fun calculate(events: List<SpeakingEvent>, today: LocalDate = LocalDate.now(),
        zone: ZoneId = ZoneId.systemDefault()): SpeakingSummary {
        require(events.all { it.words >= 0 && it.speakingMillis >= 0 }) { "Speaking counters must be nonnegative" }
        val days = events.groupBy { Instant.ofEpochMilli(it.createdAt).atZone(zone).toLocalDate() }
            .toSortedMap().map { (date, dayEvents) ->
                SpeakingDay(date, dayEvents.size.toLong(), dayEvents.sumOf { it.words }, dayEvents.sumOf { it.speakingMillis })
            }
        val activeDates = days.map { it.date }.toSet()
        var current = 0
        var date = if (today in activeDates) today else today.minusDays(1)
        while (date in activeDates) { current++; date = date.minusDays(1) }
        var longest = 0
        var run = 0
        var previous: LocalDate? = null
        for (day in days) {
            run = if (previous?.plusDays(1) == day.date) run + 1 else 1
            longest = maxOf(longest, run)
            previous = day.date
        }
        val duration = events.sumOf { it.speakingMillis }
        val words = events.sumOf { it.words }
        return SpeakingSummary(days, current, longest,
            if (duration == 0L) 0.0 else words.toDouble() * 60_000.0 / duration)
    }
}
