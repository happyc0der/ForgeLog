package dev.happyc0der.forgelog.domain.home

import java.time.Instant
import java.time.ZoneId

/**
 * Time-of-day bucket for the Home greeting. A pure enum rather than a string so the wording stays
 * in `strings.xml` and this stays testable without Android.
 */
enum class DayPart {
    MORNING,
    AFTERNOON,
    EVENING,
    NIGHT,
}

object Greeting {

    /**
     * Boundaries are inclusive of their start hour: night runs 22:00–04:59, morning 05:00–11:59,
     * afternoon 12:00–16:59, evening 17:00–21:59. Night wraps midnight, which is why this is not a
     * simple ascending comparison.
     */
    fun dayPart(epochMs: Long, zone: ZoneId): DayPart {
        // atZone, not LocalTime.ofInstant: that needs API 31, and the app runs from 26.
        val hour = Instant.ofEpochMilli(epochMs).atZone(zone).hour
        return when (hour) {
            in 5..11 -> DayPart.MORNING
            in 12..16 -> DayPart.AFTERNOON
            in 17..21 -> DayPart.EVENING
            else -> DayPart.NIGHT
        }
    }
}
