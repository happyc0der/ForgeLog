package dev.happyc0der.forgelog.ui.input

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Found by typing on the test device: the numeric keypad has "." and "," beside the digits even for
 * an integer field. "9." in the reps field saved reps as null while still displaying "9.", and a
 * comma decimal wiped the weight the same way.
 */
class NumericInputTest {

    @Test
    fun `an integer field keeps digits and refuses anything else`() {
        assertEquals("12", NumericInput.accept("12", decimal = false))
        assertEquals("", NumericInput.accept("", decimal = false))
        assertNull("the stray tap that lost reps", NumericInput.accept("9.", decimal = false))
        assertNull(NumericInput.accept("9..", decimal = false))
        assertNull(NumericInput.accept("9,", decimal = false))
        assertNull(NumericInput.accept("-3", decimal = false))
        assertNull(NumericInput.accept("1 2", decimal = false))
    }

    @Test
    fun `a decimal field reads a comma as a decimal point`() {
        // A locale that writes 62,5 must not lose the weight.
        assertEquals("62.5", NumericInput.accept("62,5", decimal = true))
        assertEquals("62.5", NumericInput.accept("62.5", decimal = true))
    }

    @Test
    fun `a decimal field allows one separator and refuses a second`() {
        assertNull(NumericInput.accept("62.5.", decimal = true))
        assertNull(NumericInput.accept("62,5,", decimal = true))
        assertNull("a comma after a point is still a second separator", NumericInput.accept("6.2,5", decimal = true))
    }

    @Test
    fun `mid-typing states are not errors`() {
        assertEquals("62.", NumericInput.accept("62.", decimal = true))
        assertEquals(".", NumericInput.accept(".", decimal = true))
        assertEquals(".5", NumericInput.accept(".5", decimal = true))
        assertEquals("", NumericInput.accept("", decimal = true))
    }

    @Test
    fun `a decimal field still refuses letters and signs`() {
        assertNull(NumericInput.accept("62a", decimal = true))
        assertNull(NumericInput.accept("-62", decimal = true))
        assertNull(NumericInput.accept("NaN", decimal = true))
    }

    @Test
    fun `numbers too long to store are refused rather than silently lost`() {
        // Past Int.MAX_VALUE, toIntOrNull gives null: the field showed a number and stored none.
        assertNull(NumericInput.accept("99999999999", decimal = false))
        assertEquals("999999", NumericInput.accept("999999", decimal = false))
        assertNull(NumericInput.accept("1000000", decimal = false))
        assertNull(NumericInput.accept("1234567.5", decimal = true))
    }

    @Test
    fun `decimals stop at two places`() {
        assertEquals("22.25", NumericInput.accept("22.25", decimal = true))
        assertEquals("1.27", NumericInput.accept("1.27", decimal = true))
        assertNull(NumericInput.accept("22.255", decimal = true))
        assertEquals(".", NumericInput.accept(".", decimal = true))
    }
}
