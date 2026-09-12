package dev.happyc0der.forgelog.ui.input

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
}
