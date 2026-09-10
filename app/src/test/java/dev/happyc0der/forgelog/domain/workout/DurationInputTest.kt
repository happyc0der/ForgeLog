package dev.happyc0der.forgelog.domain.workout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

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

    @Test
    fun adjustStepMatchesSelectedUnit() {
        assertEquals(15, DurationInput.adjustStepSeconds(DurationInputUnit.SECONDS))
        assertEquals(60, DurationInput.adjustStepSeconds(DurationInputUnit.MINUTES))
    }

    @Test
    fun formatWithUnitUsesChosenSuffix() {
        assertEquals("90s", DurationInput.formatWithUnit(90, DurationInputUnit.SECONDS))
        assertEquals("1.5m", DurationInput.formatWithUnit(90, DurationInputUnit.MINUTES))
        assertEquals("2m", DurationInput.formatWithUnit(120, DurationInputUnit.MINUTES))
        assertNull(DurationInput.formatWithUnit(null, DurationInputUnit.MINUTES))
    }
}
