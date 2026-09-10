package dev.happyc0der.forgelog.domain.workout

import kotlin.math.roundToInt
import java.util.Locale

enum class DurationInputUnit {
    SECONDS,
    MINUTES,
}

object DurationInput {
    /**
     * A minute value large enough to overflow `Int` is a typo, not a set. Clamped rather than
     * rejected so a stray digit caps out instead of silently discarding the whole entry.
     */
    private val SAFE_SECONDS = Int.MIN_VALUE.toDouble()..Int.MAX_VALUE.toDouble()

    fun toDisplay(seconds: Int?, unit: DurationInputUnit): String {
        if (seconds == null) return ""
        return when (unit) {
            DurationInputUnit.SECONDS -> seconds.toString()
            DurationInputUnit.MINUTES -> {
                if (seconds % 60 == 0) {
                    (seconds / 60).toString()
                } else {
                    val minutes = seconds / 60.0
                    "%.2f".format(Locale.US, minutes).trimEnd('0').trimEnd('.')
                }
            }
        }
    }

    fun parseSeconds(display: String, unit: DurationInputUnit): Int? {
        val trimmed = display.trim()
        if (trimmed.isEmpty()) return null
        return when (unit) {
            DurationInputUnit.SECONDS -> trimmed.toIntOrNull()
            // "NaN" and "Infinity" both parse as doubles, and roundToInt throws on NaN rather
            // than saturating. A field the user can paste into must reject them as unparseable.
            DurationInputUnit.MINUTES -> trimmed.toDoubleOrNull()
                ?.takeIf { it.isFinite() }
                ?.let { minutes -> (minutes * 60.0).coerceIn(SAFE_SECONDS).roundToInt() }
        }
    }

    fun isParseable(display: String, unit: DurationInputUnit): Boolean {
        val trimmed = display.trim()
        if (trimmed.isEmpty()) return true
        return parseSeconds(trimmed, unit) != null
    }

    fun formatWithUnit(seconds: Int?, unit: DurationInputUnit): String? {
        if (seconds == null) return null
        val suffix = when (unit) {
            DurationInputUnit.SECONDS -> "s"
            DurationInputUnit.MINUTES -> "m"
        }
        return "${toDisplay(seconds, unit)}$suffix"
    }
}
