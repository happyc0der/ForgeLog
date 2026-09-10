package dev.happyc0der.forgelog.ui.format

import dev.happyc0der.forgelog.domain.model.ExerciseUnit
import dev.happyc0der.forgelog.domain.workout.KG_TO_LB
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Display helpers shared by every screen that shows a session summary.
 *
 * Volume is always *computed* in pounds — [dev.happyc0der.forgelog.domain.workout.VolumeCalculator]
 * normalises kilograms so mixed-unit sessions sum correctly — and converted here, once, for
 * display. Nothing downstream of this file should be doing unit arithmetic.
 */
object Formatters {

    fun volume(loadLb: Double, unit: ExerciseUnit): String {
        val value = when (unit) {
            ExerciseUnit.KG -> loadLb / KG_TO_LB
            else -> loadLb
        }
        val suffix = if (unit == ExerciseUnit.KG) "kg" else "lb"
        return when {
            value <= 0.0 -> "0 $suffix"
            value >= 10_000 -> String.format(Locale.US, "%.1fk %s", value / 1000.0, suffix)
            value >= 100 -> String.format(Locale.US, "%.0f %s", value, suffix)
            else -> String.format(Locale.US, "%.1f %s", value, suffix)
        }
    }

    /** A single load, as opposed to a session's total volume. */
    fun weight(value: Double, unit: ExerciseUnit): String {
        val suffix = if (unit == ExerciseUnit.KG) "kg" else "lb"
        return if (value % 1.0 == 0.0) {
            String.format(Locale.US, "%.0f %s", value, suffix)
        } else {
            String.format(Locale.US, "%.1f %s", value, suffix)
        }
    }

    /** Compact duration for stat tiles: `1h 12m`, `48m`, `< 1m`. Null renders as an em dash. */
    fun compactDuration(durationMs: Long?): String? {
        if (durationMs == null) return null
        val totalMinutes = durationMs / 60_000L
        return when {
            totalMinutes <= 0L -> "< 1m"
            totalMinutes < 60L -> "${totalMinutes}m"
            totalMinutes % 60L == 0L -> "${totalMinutes / 60L}h"
            else -> "${totalMinutes / 60L}h ${totalMinutes % 60L}m"
        }
    }

    /**
     * A short span in seconds, for rest and duration targets: `45s`, `2m`, `2m 30s`.
     *
     * Distinct from [compactDuration], which rounds to whole minutes and so renders every rest
     * target under a minute as `< 1m`.
     */
    fun seconds(value: Int): String {
        if (value <= 0) return "0s"
        val minutes = value / 60
        val remainder = value % 60
        return when {
            minutes == 0 -> "${remainder}s"
            remainder == 0 -> "${minutes}m"
            else -> "${minutes}m ${remainder}s"
        }
    }

    /** Seconds spent on timed work, shown only when a session actually had any. */
    fun timedSeconds(seconds: Int): String? {
        if (seconds <= 0) return null
        return compactDuration(seconds * 1_000L)
    }

    fun fullDate(date: LocalDate): String =
        date.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.getDefault()))

    /** "Today" / "Yesterday" / a date, for history and last-workout labels. */
    fun relativeDate(epochMs: Long, today: LocalDate, zone: ZoneId): String {
        val date = LocalDate.ofInstant(Instant.ofEpochMilli(epochMs), zone)
        return when (date) {
            today -> "Today"
            today.minusDays(1) -> "Yesterday"
            else -> {
                val pattern = if (date.year == today.year) "d MMM" else "d MMM yyyy"
                date.format(DateTimeFormatter.ofPattern(pattern, Locale.getDefault()))
            }
        }
    }

    fun timeOfDay(epochMs: Long, zone: ZoneId): String =
        DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())
            .format(Instant.ofEpochMilli(epochMs).atZone(zone))
}
