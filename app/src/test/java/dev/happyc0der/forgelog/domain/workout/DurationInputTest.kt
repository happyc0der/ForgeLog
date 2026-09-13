package dev.happyc0der.forgelog.domain.workout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import dev.happyc0der.forgelog.domain.model.StoredNumbers

class DurationInputTest {
    @Test
    fun displaysWholeMinutesWithoutDecimals() {
        assertEquals("2", DurationInput.toDisplay(120, DurationInputUnit.MINUTES))
        assertEquals("90", DurationInput.toDisplay(90, DurationInputUnit.SECONDS))
    }

    @Test
    fun displaysFractionalMinutes() {
        assertEquals("1.5", DurationInput.toDisplay(90, DurationInputUnit.MINUTES))
        assertEquals("0.75", DurationInput.toDisplay(45, DurationInputUnit.MINUTES))
    }

    @Test
    fun parsesMinutesBackToSeconds() {
        assertEquals(90, DurationInput.parseSeconds("1.5", DurationInputUnit.MINUTES))
        assertEquals(120, DurationInput.parseSeconds("2", DurationInputUnit.MINUTES))
        assertEquals(90, DurationInput.parseSeconds("90", DurationInputUnit.SECONDS))
        assertNull(DurationInput.parseSeconds("", DurationInputUnit.MINUTES))
        assertNull(DurationInput.parseSeconds("abc", DurationInputUnit.MINUTES))
    }

    @Test
    fun blankDisplayIsParseable() {
        assertTrue(DurationInput.isParseable("", DurationInputUnit.SECONDS))
        assertTrue(DurationInput.isParseable("1.5", DurationInputUnit.MINUTES))
    }

    /*
     * "NaN" and "Infinity" are accepted by Double.parseDouble, and roundToInt throws on NaN rather
     * than saturating — so a paste into the duration field used to crash the app mid-set. The
     * numeric keyboard is only a hint; a clipboard or a hardware keyboard bypasses it.
     */

    @Test
    fun nonNumericDoublesAreRejectedRatherThanThrowing() {
        listOf("NaN", "Infinity", "-Infinity", "nan").forEach { input ->
            assertNull("$input should not parse", DurationInput.parseSeconds(input, DurationInputUnit.MINUTES))
            assertFalse("$input should not be parseable", DurationInput.isParseable(input, DurationInputUnit.MINUTES))
        }
    }

    /**
     * The clamp is to the largest storable value, not to what fits in an `Int`.
     *
     * It used to be Int.MAX_VALUE, which overflows nothing and stores fine — and is then refused by
     * the backup importer, which turns down the whole file. Sixty times the six digits a minutes
     * field accepts is already past that bound, so an ordinary stray digit was enough: see
     * [dev.happyc0der.forgelog.data.backup.TypedDurationsSurviveABackupTest].
     */
    @Test
    fun anAbsurdlyLargeEntryClampsToWhatCanBeStored() {
        listOf("999999999999", "999999.99", "99999").forEach { typed ->
            assertEquals(
                "$typed minutes clamped somewhere a backup could not be restored from",
                StoredNumbers.MAX_WHOLE,
                DurationInput.parseSeconds(typed, DurationInputUnit.MINUTES),
            )
        }
    }

    /** And just under it, nothing is clamped: 16,666.65 minutes is exactly the ceiling in seconds. */
    @Test
    fun anEntryThatFitsIsLeftAlone() {
        assertEquals(
            StoredNumbers.MAX_WHOLE,
            DurationInput.parseSeconds("16666.65", DurationInputUnit.MINUTES),
        )
        assertEquals(599_940, DurationInput.parseSeconds("9999", DurationInputUnit.MINUTES))
        assertEquals(90, DurationInput.parseSeconds("1.5", DurationInputUnit.MINUTES))
    }
}
