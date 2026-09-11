package dev.happyc0der.forgelog.ui.input

import dev.happyc0der.forgelog.domain.workout.DurationInputUnit
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Decimal minutes are for typing, not reading: 76 seconds in minutes mode used to sit in the field
 * as "1.27", which reads as one minute twenty-seven.
 */
class FieldTextTest {

    @Test
    fun `a minutes field at rest reads as minutes and seconds`() {
        assertEquals("1m 16s", fieldText("76", DurationInputUnit.MINUTES, editing = false))
        assertEquals("2m", fieldText("120", DurationInputUnit.MINUTES, editing = false))
        assertEquals("45s", fieldText("45", DurationInputUnit.MINUTES, editing = false))
    }

    @Test
    fun `a minutes field being edited shows decimal minutes`() {
        assertEquals("1.27", fieldText("76", DurationInputUnit.MINUTES, editing = true))
        assertEquals("1.5", fieldText("90", DurationInputUnit.MINUTES, editing = true))
        assertEquals("2", fieldText("120", DurationInputUnit.MINUTES, editing = true))
    }

    @Test
    fun `a seconds field is the plain number either way`() {
        assertEquals("76", fieldText("76", DurationInputUnit.SECONDS, editing = false))
        assertEquals("76", fieldText("76", DurationInputUnit.SECONDS, editing = true))
    }

    @Test
    fun `an empty duration stays empty rather than reading as zero`() {
        assertEquals("", fieldText("", DurationInputUnit.MINUTES, editing = false))
        assertEquals("", fieldText("", DurationInputUnit.MINUTES, editing = true))
    }

    @Test
    fun `a field that is not a duration shows its text untouched`() {
        assertEquals("8", fieldText("8", unit = null, editing = false))
    }
}
