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
     * [dev.happyc0der.forgelog.data.backup.EnteredValuesSurviveABackupTest].
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

    /**
     * Whatever it is handed, it returns nothing or something storable — and never throws.
     *
     * The clamp is the contract: [DurationInput.parseSeconds] is the last thing between a typed
     * entry and a column the backup importer will refuse, so "no result" and "a result inside the
     * bound" are the only two answers allowed. The alphabet is what a numeric field can actually
     * contain plus the things that have caught this code before — a comma from a European keyboard,
     * a lone dot mid-typing, the words that parse as doubles and then throw on rounding.
     */
    @Test
    fun anythingAtAllIsEitherRefusedOrStorable() {
        val alphabet = "0123456789.,-+eE ".toCharArray()
        val words = listOf("", " ", ".", ",", "-", "NaN", "Infinity", "-Infinity", "1e400", "999999.99")
        val cases = mutableListOf<String>()
        cases += words
        var seed = 1
        repeat(20_000) {
            seed = seed * 1_103_515_245 + 12_345
            val length = (seed ushr 16) % 7
            cases += buildString {
                var n = seed
                repeat(length) {
                    n = n * 1_103_515_245 + 12_345
                    append(alphabet[((n ushr 16) % alphabet.size + alphabet.size) % alphabet.size])
                }
            }
        }

        DurationInputUnit.entries.forEach { unit ->
            cases.forEach { text ->
                val parsed = runCatching { DurationInput.parseSeconds(text, unit) }
                assertTrue("parseSeconds threw on ${text.quoted()} as $unit: $parsed", parsed.isSuccess)
                val seconds = parsed.getOrNull()
                if (seconds != null) {
                    assertTrue(
                        "${text.quoted()} as $unit gave $seconds, which no backup could carry",
                        seconds in StoredNumbers.STORABLE_WHOLE,
                    )
                }
                // isParseable has to agree, or a field shows text it will not store.
                val parseable = runCatching { DurationInput.isParseable(text, unit) }
                assertTrue("isParseable threw on ${text.quoted()}", parseable.isSuccess)
                if (text.isNotBlank()) {
                    assertEquals(
                        "isParseable disagrees with parseSeconds on ${text.quoted()} as $unit",
                        seconds != null,
                        parseable.getOrNull(),
                    )
                }
            }
        }
    }

    private fun String.quoted(): String = "\"" + this + "\""
}
