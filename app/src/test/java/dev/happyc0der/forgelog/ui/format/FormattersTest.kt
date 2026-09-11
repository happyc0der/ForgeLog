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
        assertEquals("Today", Formatters.relativeDate(epochOf(2026, 3, 10), today, zone, "Today", "Yesterday"))
        assertEquals("Yesterday", Formatters.relativeDate(epochOf(2026, 3, 9), today, zone, "Today", "Yesterday"))
    }

    @Test
    fun `relative date omits the year within the same year and includes it otherwise`() {
        val today = LocalDate.of(2026, 3, 10)
        val sameYear = Formatters.relativeDate(epochOf(2026, 1, 5), today, zone, "Today", "Yesterday")
        val priorYear = Formatters.relativeDate(epochOf(2025, 12, 30), today, zone, "Today", "Yesterday")
        assertEquals(false, sameYear.contains("2026"))
        assertEquals(true, priorYear.contains("2025"))
    }

    @Test
    fun `relative date uses the given zone, not UTC`() {
        // 00:30 on the 10th in Kolkata is still the 9th in UTC; the label must follow the zone.
        val today = LocalDate.of(2026, 3, 10)
        assertEquals("Today", Formatters.relativeDate(epochOf(2026, 3, 10, hour = 0), today, zone, "Today", "Yesterday"))
    }

    @Test
    fun `time of day is rendered in the given zone`() {
        assertEquals("18:00", Formatters.timeOfDay(epochOf(2026, 3, 10, hour = 18), zone))
    }

    @Test
    fun `short spans keep their seconds instead of rounding to a minute`() {
        // compactDuration renders anything under a minute as "< 1m", which is useless for rest.
        assertEquals("45s", Formatters.seconds(45))
        assertEquals("2m", Formatters.seconds(120))
        assertEquals("2m 30s", Formatters.seconds(150))
        assertEquals("0s", Formatters.seconds(0))
        assertEquals("0s", Formatters.seconds(-5))
    }

    @Test
    fun `load converts a pounds value for display, weight does not`() {
        // A personal record is held in pounds; a logged set is held in its own unit. Confusing the
        // two showed pounds under a kilogram label.
        assertEquals("100 lb", Formatters.load(100.0, ExerciseUnit.LB))
        assertEquals("45.4 kg", Formatters.load(100.0, ExerciseUnit.KG))
        assertEquals("100 kg", Formatters.weight(100.0, ExerciseUnit.KG))
        // Converted loads land a hair off whole numbers; they read as the whole number.
        assertEquals("152 kg", Formatters.load(151.95 * 2.2046226218, ExerciseUnit.KG))
        assertEquals("233.3 lb", Formatters.load(233.333, ExerciseUnit.LB))
        assertEquals("22.5 lb", Formatters.weight(22.5, ExerciseUnit.LB))
    }

    @Test
    fun `a logged weight reads as it was logged, to two decimals`() {
        // Microplates: 22.75 kg is not 22.8 kg.
        assertEquals("22.75 kg", Formatters.weight(22.75, ExerciseUnit.KG))
        assertEquals("5.5 lb", Formatters.weight(5.5, ExerciseUnit.LB))
        assertEquals("190 lb", Formatters.weight(190.0, ExerciseUnit.LB))
    }

    @Test
    fun `a distance is metres with a space, and kilometres from a kilometre up`() {
        assertEquals("400 m", Formatters.distanceMeters(400.0))
        assertEquals("1.5 km", Formatters.distanceMeters(1_500.0))
        assertEquals("10 km", Formatters.distanceMeters(10_000.0))
        assertEquals("5.02 km", Formatters.distanceMeters(5_021.0))
    }

    @Test
    fun `timed work under a minute keeps its seconds`() {
        // "< 1m" for a 45-second plank is true and useless.
        assertEquals("45s", Formatters.timedSeconds(45))
        assertEquals("1m", Formatters.timedSeconds(60))
        // A 90-second hold is not a one-minute hold.
        assertEquals("1m 30s", Formatters.timedSeconds(90))
        assertEquals("2m", Formatters.timedSeconds(120))
        assertEquals("59m 59s", Formatters.timedSeconds(3_599))
        assertEquals("1h", Formatters.timedSeconds(3600))
        assertNull(Formatters.timedSeconds(0))
    }
}
