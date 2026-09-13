package dev.happyc0der.forgelog.ui.input

import dev.happyc0der.forgelog.domain.model.StoredNumbers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** What NumericInput accepts, held to its own promise: the field cannot show what is not stored. */
class NumericInputPropertyTest {

    private val partials = setOf("", ".")

    /** Every decimal string it accepts either parses, or is a state mid-typing. */
    @Test
    fun anythingAcceptedAsADecimalParses() {
        val alphabet = "0123456789.,eE+-xX १٣１".toCharArray()
        var accepted = 0
        for (seed in 0 until 40_000) {
            var n = seed
            val text = buildString {
                repeat(4) {
                    append(alphabet[n % alphabet.size])
                    n /= alphabet.size
                }
            }
            val kept = NumericInput.accept(text, decimal = true) ?: continue
            accepted++
            if (kept in partials) continue
            assertEquals(
                "accepted \"$text\" as \"$kept\", which does not parse",
                true,
                kept.toDoubleOrNull() != null,
            )
        }
        assertEquals("the alphabet never reached the accepting path", true, accepted > 0)
    }

    /** And every whole-number string it accepts parses to the number it shows. */
    @Test
    fun anythingAcceptedAsAWholeNumberParsesToWhatItShows() {
        val alphabet = "0123456789.,- १٣１".toCharArray()
        for (seed in 0 until 20_000) {
            var n = seed
            val text = buildString {
                repeat(4) {
                    append(alphabet[n % alphabet.size])
                    n /= alphabet.size
                }
            }
            val kept = NumericInput.accept(text, decimal = false) ?: continue
            if (kept.isEmpty()) continue
            val parsed = kept.toIntOrNull()
            assertEquals("accepted \"$kept\", which does not parse", true, parsed != null)
            // Not character-for-character: "007" is a normal thing to be part-way through typing,
            // and it does store the seven it shows. What matters is that the value is the one on
            // screen, which leading zeros do not change.
            assertEquals(
                "accepted \"$kept\" but stored $parsed, so the field shows what is not stored",
                kept.trimStart('0').ifEmpty { "0" },
                parsed.toString(),
            )
        }
    }

    /**
     * Digits from another script are refused rather than half-understood.
     *
     * Char.isDigit is true for Devanagari and Arabic-Indic digits, so they used to be accepted.
     * toIntOrNull then read them as numbers -- a whole-number field showed "१" and stored 1 --
     * while toDoubleOrNull refused them, so a weight typed on a Hindi keyboard was accepted into
     * the field and stored as nothing at all. Both are exactly the silent loss this object exists
     * to prevent.
     */
    @Test
    fun digitsFromAnotherScriptAreRefused() {
        listOf("१", "१२", "٣", "１", "1१").forEach { text ->
            assertNull("accepted $text as a decimal", NumericInput.accept(text, decimal = true))
            assertNull("accepted $text as a whole number", NumericInput.accept(text, decimal = false))
        }
    }

    /**
     * Nothing the field accepts is bigger than what can be stored, in the unit it is typed in.
     *
     * These were two copies of one rule -- six digits here, 999,999 in the backup importer -- and
     * keeping them in step by hand is what failed: a duration typed in minutes is multiplied by
     * sixty on the way to storage, so six digits of minutes landed well past the importer, which
     * then refused the whole backup file. [DurationInput] does the clamping for converted units;
     * this ties the digit caps themselves to the same number, so the three cannot drift apart.
     */
    @Test
    fun theFieldCannotAcceptMoreThanCanBeStored() {
        val biggestWhole = StoredNumbers.MAX_WHOLE.toString()
        assertEquals(biggestWhole, NumericInput.accept(biggestWhole, decimal = false))
        assertNull(
            "a whole number above the stored limit was accepted",
            NumericInput.accept(biggestWhole + "0", decimal = false),
        )

        val biggestMeasurement = StoredNumbers.MAX_MEASUREMENT.toString()
        assertEquals(biggestMeasurement, NumericInput.accept(biggestMeasurement, decimal = true))
        assertNull(
            "a measurement above the stored limit was accepted",
            NumericInput.accept("9" + biggestMeasurement, decimal = true),
        )
        assertNull(
            "a third decimal place was accepted, which the importer rounds away",
            NumericInput.accept("1.234", decimal = true),
        )
    }

    /** And every accepted decimal is within the measurement limit, not merely short enough. */
    @Test
    fun everyAcceptedDecimalIsWithinTheStoredLimit() {
        listOf("0", "0.01", "999999", "999999.9", "999999.99", "1.5", ".5").forEach { text ->
            val accepted = NumericInput.accept(text, decimal = true)
            assertEquals("$text should be accepted", text, accepted)
            val value = accepted!!.toDoubleOrNull() ?: 0.0
            assertTrue(
                "$text parses to $value, past the stored limit",
                value <= StoredNumbers.MAX_MEASUREMENT,
            )
        }
    }
}
