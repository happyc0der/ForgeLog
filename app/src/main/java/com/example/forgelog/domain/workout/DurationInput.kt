package com.example.forgelog.domain.workout

import kotlin.math.roundToInt
import java.util.Locale

enum class DurationInputUnit {
    SECONDS,
    MINUTES,
}

object DurationInput {
    const val SECOND_ADJUST_STEP_SECONDS = 15
    const val MINUTE_ADJUST_STEP_SECONDS = 60

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
            DurationInputUnit.MINUTES -> trimmed.toDoubleOrNull()?.let { minutes ->
                (minutes * 60.0).roundToInt()
            }
        }
    }

    fun isParseable(display: String, unit: DurationInputUnit): Boolean {
        val trimmed = display.trim()
        if (trimmed.isEmpty()) return true
        return parseSeconds(trimmed, unit) != null
    }

    fun adjustStepSeconds(unit: DurationInputUnit): Int = when (unit) {
        DurationInputUnit.SECONDS -> SECOND_ADJUST_STEP_SECONDS
        DurationInputUnit.MINUTES -> MINUTE_ADJUST_STEP_SECONDS
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
