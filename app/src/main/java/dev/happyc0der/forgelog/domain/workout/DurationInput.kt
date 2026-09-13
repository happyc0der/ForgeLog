package dev.happyc0der.forgelog.domain.workout

import dev.happyc0der.forgelog.domain.model.StoredNumbers
import kotlin.math.roundToInt
import java.util.Locale

enum class DurationInputUnit {
    SECONDS,
    MINUTES,
}

object DurationInput {
    /**
     * A value this large is a typo, not a set. Clamped rather than rejected so a stray digit caps
     * out instead of silently discarding the whole entry.
     *
     * The ceiling is what can be stored and read back again, not what fits in an `Int`. Sixty times
     * the six digits a minutes field accepts fits an `Int` easily, and the backup importer refuses
     * it — so the set saved, the export wrote it out, and the restore turned down the whole file.
     * See [StoredNumbers].
     */
    private val STORABLE = StoredNumbers.STORABLE_WHOLE
    private val STORABLE_AS_DOUBLE = STORABLE.first.toDouble()..STORABLE.last.toDouble()

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
            DurationInputUnit.SECONDS -> trimmed.toIntOrNull()?.coerceIn(STORABLE)
            // "NaN" and "Infinity" both parse as doubles, and roundToInt throws on NaN rather
            // than saturating. A field the user can paste into must reject them as unparseable.
            DurationInputUnit.MINUTES -> trimmed.toDoubleOrNull()
                ?.takeIf { it.isFinite() }
                ?.let { minutes -> (minutes * 60.0).coerceIn(STORABLE_AS_DOUBLE).roundToInt() }
        }
    }

    fun isParseable(display: String, unit: DurationInputUnit): Boolean {
        val trimmed = display.trim()
        if (trimmed.isEmpty()) return true
        return parseSeconds(trimmed, unit) != null
    }
}
