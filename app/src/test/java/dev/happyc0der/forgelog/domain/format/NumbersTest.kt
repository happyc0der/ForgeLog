package dev.happyc0der.forgelog.domain.format

import org.junit.Assert.assertEquals
import org.junit.Test

class NumbersTest {

    @Test
    fun `whole numbers have no decimal point`() {
        assertEquals("225", plainNumber(225.0))
        assertEquals("0", plainNumber(0.0))
    }

    @Test
    fun `fractions keep what they need and nothing more`() {
        assertEquals("102.5", plainNumber(102.5))
        assertEquals("0.3", plainNumber(0.1 + 0.2))
        assertEquals("5.25", plainNumber(5.25))
    }

    @Test
    fun `large values are never scientific`() {
        assertEquals("12000000", plainNumber(12_000_000.0))
    }

    @Test
    fun `values no one could type still render instead of throwing`() {
        assertEquals("NaN", plainNumber(Double.NaN))
        assertEquals("Infinity", plainNumber(Double.POSITIVE_INFINITY))
        assertEquals("-Infinity", plainNumber(Double.NEGATIVE_INFINITY))
    }
}
