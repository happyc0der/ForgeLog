package dev.happyc0der.forgelog.ui.format

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.happyc0der.forgelog.R
import dev.happyc0der.forgelog.domain.format.plainNumber as domainPlainNumber
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

    /**
     * A single load held in pounds, converted for display — for values the domain normalised, such
     * as a personal record or an estimated 1RM.
     *
     * Distinct from [weight], which formats a load already expressed in [unit] and so must not
     * convert. Mixing the two shows pounds under a kilogram label.
     */
    fun load(loadLb: Double, unit: ExerciseUnit): String = weight(
        value = if (unit == ExerciseUnit.KG) loadLb / KG_TO_LB else loadLb,
        unit = unit,
    )

    /** A single load already expressed in [unit]. Formats only; never converts. */
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

    /** A distance in metres, without a Double's trailing `.0`. */
    fun distanceMeters(value: Double): String = plainNumber(value) + "m"

    /** See [dev.happyc0der.forgelog.domain.format.plainNumber]. */
    fun plainNumber(value: Double): String = domainPlainNumber(value)

    /**
     * Seconds spent on timed work, shown only when a session actually had any.
     *
     * Under a minute keeps its seconds: a 45-second plank reported as "< 1m" tells the user
     * nothing, and timed work is exactly where that precision matters.
     */
    fun timedSeconds(seconds: Int): String? = when {
        seconds <= 0 -> null
        seconds < 60 -> seconds(seconds)
        else -> compactDuration(seconds * 1_000L)
    }

    fun fullDate(date: LocalDate): String =
        date.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.getDefault()))

    /**
     * "Today" / "Yesterday" / a date, for history and last-workout labels.
     *
     * The two words are passed in rather than written here: this object has no resources, and
     * hardcoding them put untranslatable English on four screens. Screens call the composable
     * [relativeDate] wrapper instead of this directly.
     */
    fun relativeDate(
        epochMs: Long,
        today: LocalDate,
        zone: ZoneId,
        todayLabel: String,
        yesterdayLabel: String,
    ): String {
        val date = LocalDate.ofInstant(Instant.ofEpochMilli(epochMs), zone)
        return when (date) {
            today -> todayLabel
            today.minusDays(1) -> yesterdayLabel
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

/** [Formatters.relativeDate] with its two words resolved from resources. */
@Composable
fun relativeDate(epochMs: Long, today: LocalDate, zone: ZoneId): String = Formatters.relativeDate(
    epochMs = epochMs,
    today = today,
    zone = zone,
    todayLabel = stringResource(R.string.date_today),
    yesterdayLabel = stringResource(R.string.date_yesterday),
)
