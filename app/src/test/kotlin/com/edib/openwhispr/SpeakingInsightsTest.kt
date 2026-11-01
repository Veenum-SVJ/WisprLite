package com.edib.openwhispr

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class SpeakingInsightsTest {
    private fun event(time: String, words: Long = 10, millis: Long = 5_000) =
        SpeakingEvent(Instant.parse(time).toEpochMilli(), words, millis)

    @Test fun `empty and zero duration have finite zero rate`() {
        val empty = SpeakingInsights.calculate(emptyList())
        assertTrue(empty.days.isEmpty())
        assertEquals(0, empty.currentStreak)
        assertEquals(0, empty.longestStreak)
        assertEquals(0.0, empty.recognizedWpm, 0.0)
        assertEquals(0.0, SpeakingInsights.calculate(listOf(event("2026-01-01T12:00:00Z", millis = 0))).recognizedWpm, 0.0)
    }

    @Test fun `rate uses recognized counts and duration and merges dates`() {
        val result = SpeakingInsights.calculate(listOf(event("2026-01-01T12:00:00Z"), event("2026-01-01T13:00:00Z", 20, 10_000)),
            LocalDate.parse("2026-01-01"), ZoneId.of("UTC"))
        assertEquals(SpeakingDay(LocalDate.parse("2026-01-01"), 2, 30, 15_000), result.days.single())
        assertEquals(120.0, result.recognizedWpm, 0.0)
    }

    @Test fun `streak grants yesterday grace and separates historical runs`() {
        val events = listOf("2026-01-01", "2026-01-02", "2026-01-03", "2026-01-05", "2026-01-06")
            .map { event("${it}T12:00:00Z") }
        val zone = ZoneId.of("UTC")
        assertEquals(2, SpeakingInsights.calculate(events, LocalDate.parse("2026-01-06"), zone).currentStreak)
        assertEquals(2, SpeakingInsights.calculate(events, LocalDate.parse("2026-01-07"), zone).currentStreak)
        assertEquals(0, SpeakingInsights.calculate(events, LocalDate.parse("2026-01-08"), zone).currentStreak)
        assertEquals(3, SpeakingInsights.calculate(events, LocalDate.parse("2026-01-07"), zone).longestStreak)
    }

    @Test fun `DST fall back and timezone changes use local calendar dates`() {
        val events = listOf(event("2026-11-01T03:30:00Z"), event("2026-11-01T05:30:00Z"), event("2026-11-01T06:30:00Z"))
        val ny = SpeakingInsights.calculate(events, LocalDate.parse("2026-11-01"), ZoneId.of("America/New_York"))
        assertEquals(listOf(LocalDate.parse("2026-10-31"), LocalDate.parse("2026-11-01")), ny.days.map { it.date })
        assertEquals(listOf(1L, 2L), ny.days.map { it.count })
        assertEquals(2, ny.currentStreak)
        val utc = SpeakingInsights.calculate(events, LocalDate.parse("2026-11-01"), ZoneId.of("UTC"))
        assertEquals(1, utc.days.size)
        assertEquals(1, utc.currentStreak)
    }

    @Test fun `DST spring forward has consecutive calendar days`() {
        val result = SpeakingInsights.calculate(listOf(event("2026-03-07T17:00:00Z"), event("2026-03-08T16:00:00Z"), event("2026-03-09T16:00:00Z")),
            LocalDate.parse("2026-03-09"), ZoneId.of("America/New_York"))
        assertEquals(3, result.currentStreak)
        assertEquals(3, result.longestStreak)
    }
}
