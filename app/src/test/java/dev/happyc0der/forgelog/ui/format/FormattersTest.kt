package dev.happyc0der.forgelog.ui.format

import dev.happyc0der.forgelog.domain.model.ExerciseUnit
import dev.happyc0der.forgelog.domain.workout.KG_TO_LB
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class FormattersTest {

    private val zone = ZoneId.of("Asia/Kolkata")

    private fun epochOf(year: Int, month: Int, day: Int, hour: Int = 12): Long =
        LocalDateTime.of(year, month, day, hour, 0).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun `volume renders in pounds unchanged`() {
        assertEquals("500 lb", Formatters.volume(500.0, ExerciseUnit.LB))
    }

    @Test
    fun `volume converts to kilograms for a kg user`() {
        // 1000 lb is about 453.6 kg.
        assertEquals("454 kg", Formatters.volume(1000.0, ExerciseUnit.KG))
        // A round-trip of a kg figure comes back to itself.
        assertEquals("200 kg", Formatters.volume(200.0 * KG_TO_LB, ExerciseUnit.KG))
    }

    @Test
    fun `large volumes are abbreviated, small ones keep a decimal`() {
        assertEquals("12.5k lb", Formatters.volume(12_500.0, ExerciseUnit.LB))
        assertEquals("45.0 lb", Formatters.volume(45.0, ExerciseUnit.LB))
        assertEquals("0 lb", Formatters.volume(0.0, ExerciseUnit.LB))
    }

    @Test
    fun `bodyweight and timed units do not pretend to be pounds`() {
        // Non-loaded units never reach the formatter with a load, but if they do the suffix is lb
        // because VolumeCalculator only ever produces pounds.
        assertEquals("0 lb", Formatters.volume(0.0, ExerciseUnit.BODYWEIGHT))
    }

    @Test
    fun `compact duration covers minutes, hours and round hours`() {
        assertEquals("48m", Formatters.compactDuration(48 * 60_000L))
        assertEquals("1h 12m", Formatters.compactDuration(72 * 60_000L))
        assertEquals("2h", Formatters.compactDuration(120 * 60_000L))
        assertEquals("< 1m", Formatters.compactDuration(30_000L))
    }

    @Test
    fun `a null duration stays null so the UI can show it as missing`() {
        assertNull(Formatters.compactDuration(null))
    }

    @Test
    fun `timed seconds are null when no timed work happened`() {
        assertNull(Formatters.timedSeconds(0))
        assertNull(Formatters.timedSeconds(-5))
        assertEquals("2m", Formatters.timedSeconds(120))
    }

    @Test
    fun `relative date names today and yesterday`() {
        val today = LocalDate.of(2026, 3, 10)
        assertEquals("Today", Formatters.relativeDate(epochOf(2026, 3, 10), today, zone))
        assertEquals("Yesterday", Formatters.relativeDate(epochOf(2026, 3, 9), today, zone))
    }

    @Test
    fun `relative date omits the year within the same year and includes it otherwise`() {
        val today = LocalDate.of(2026, 3, 10)
        val sameYear = Formatters.relativeDate(epochOf(2026, 1, 5), today, zone)
        val priorYear = Formatters.relativeDate(epochOf(2025, 12, 30), today, zone)
        assertEquals(false, sameYear.contains("2026"))
        assertEquals(true, priorYear.contains("2025"))
    }

    @Test
    fun `relative date uses the given zone, not UTC`() {
        // 00:30 on the 10th in Kolkata is still the 9th in UTC; the label must follow the zone.
        val today = LocalDate.of(2026, 3, 10)
        assertEquals("Today", Formatters.relativeDate(epochOf(2026, 3, 10, hour = 0), today, zone))
    }

    @Test
    fun `time of day is rendered in the given zone`() {
        assertEquals("18:00", Formatters.timeOfDay(epochOf(2026, 3, 10, hour = 18), zone))
    }
}
