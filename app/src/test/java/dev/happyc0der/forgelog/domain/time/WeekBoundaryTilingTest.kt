package dev.happyc0der.forgelog.domain.time

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * The bucket ranges tile the timeline, in zones chosen to break naive date arithmetic.
 *
 * Every figure on Home and in Analytics is a sum over one of these ranges, so a gap between two of
 * them loses a workout from the totals and an overlap counts one twice -- and either would show up
 * only for users in the zone that caused it, which is the worst kind of bug to ship. The dates here
 * are real transitions:
 *
 *  - Lord Howe shifts by thirty minutes rather than an hour.
 *  - Sao Paulo used to spring forward at midnight, so that day had no 00:00 at all.
 *  - Santiago is southern-hemisphere, so its transitions fall in the opposite months.
 *  - Apia skipped 30 December 2011 entirely, crossing the date line.
 *  - Kathmandu sits at +05:45, and Kolkata at +05:30, so neither aligns to an hour.
 */
class WeekBoundaryTilingTest {

    private val zones = listOf(
        "Australia/Lord_Howe",
        "America/Sao_Paulo",
        "America/Santiago",
        "Pacific/Apia",
        "Asia/Kathmandu",
        "Asia/Kolkata",
        "America/New_York",
        "Europe/London",
        "UTC",
    ).map(ZoneId::of)

    /** Noon on a handful of real transition days, plus ordinary ones, in each zone. */
    private fun probes(zone: ZoneId): List<Long> = listOf(
        // Northern spring forward and fall back.
        ZonedDateTime.of(2026, 3, 8, 12, 0, 0, 0, zone),
        ZonedDateTime.of(2026, 11, 1, 12, 0, 0, 0, zone),
        // Southern transitions.
        ZonedDateTime.of(2026, 4, 5, 12, 0, 0, 0, zone),
        ZonedDateTime.of(2026, 10, 4, 12, 0, 0, 0, zone),
        // Lord Howe's half-hour shifts, and Sao Paulo's midnight one.
        ZonedDateTime.of(2026, 10, 25, 12, 0, 0, 0, zone),
        ZonedDateTime.of(2018, 11, 4, 12, 0, 0, 0, zone),
        // Apia's skipped day, and the turn of a year.
        ZonedDateTime.of(2011, 12, 29, 12, 0, 0, 0, zone),
        ZonedDateTime.of(2026, 1, 1, 12, 0, 0, 0, zone),
        // A leap day, and an ordinary Wednesday.
        ZonedDateTime.of(2024, 2, 29, 12, 0, 0, 0, zone),
        ZonedDateTime.of(2026, 9, 9, 12, 0, 0, 0, zone),
    ).map { it.toInstant().toEpochMilli() }

    @Test
    fun theDaysOfAWeekTileItExactly() {
        for (zone in zones) {
            for (weekStart in DayOfWeek.entries) {
                for (probe in probes(zone)) {
                    val week = WeekBoundary.weekRange(probe, zone, weekStart)
                    val days = WeekBoundary.daysOfWeek(probe, zone, weekStart)

                    assertEquals("$zone $weekStart: seven days", 7, days.size)
                    assertEquals("$zone $weekStart: starts with the week", week.start, days.first().start)
                    assertEquals("$zone $weekStart: ends with the week", week.endExclusive, days.last().endExclusive)
                    days.zipWithNext { a, b ->
                        assertEquals("$zone $weekStart: no gap or overlap", a.endExclusive, b.start)
                    }
                    days.forEach { day ->
                        // Not "never empty": see aDayThatNeverHappenedIsEmptyRatherThanWrong.
                        assertTrue(
                            "$zone $weekStart: a day cannot run backwards",
                            day.endExclusive >= day.start,
                        )
                    }
                }
            }
        }
    }

    /**
     * Samoa skipped 30 December 2011 outright, moving across the date line, so that week has six
     * days in it. The seventh keeps its slot as a zero-length range rather than being dropped or
     * given someone else's hours: nothing can fall inside it, so nothing is lost or double counted,
     * and the week still tiles exactly. It shows up as one empty bar on a chart nobody in Samoa
     * will scroll back to.
     */
    @Test
    fun aDayThatNeverHappenedIsEmptyRatherThanWrong() {
        val apia = ZoneId.of("Pacific/Apia")
        val during = ZonedDateTime.of(2011, 12, 29, 12, 0, 0, 0, apia).toInstant().toEpochMilli()

        val days = WeekBoundary.daysOfWeek(during, apia, DayOfWeek.MONDAY)
        val week = WeekBoundary.weekRange(during, apia, DayOfWeek.MONDAY)

        assertEquals("one day of that week did not happen", 1, days.count { it.endExclusive == it.start })
        assertEquals("the other six did", 6, days.count { it.endExclusive > it.start })
        // The week is still whole: every hour of it belongs to exactly one day.
        assertEquals(week.start, days.first().start)
        assertEquals(week.endExclusive, days.last().endExclusive)
        days.zipWithNext { a, b -> assertEquals(a.endExclusive, b.start) }
    }

    /** Every other zone and week here has seven days that actually happened. */
    @Test
    fun anOrdinaryWeekHasSevenRealDays() {
        for (zone in zones.filterNot { it.id == "Pacific/Apia" }) {
            for (weekStart in DayOfWeek.entries) {
                for (probe in probes(zone)) {
                    val days = WeekBoundary.daysOfWeek(probe, zone, weekStart)
                    assertEquals(
                        "$zone $weekStart: seven days that happened",
                        7,
                        days.count { it.endExclusive > it.start },
                    )
                }
            }
        }
    }

    @Test
    fun aWeekContainsTheMomentItWasAskedAbout() {
        for (zone in zones) {
            for (weekStart in DayOfWeek.entries) {
                for (probe in probes(zone)) {
                    val week = WeekBoundary.weekRange(probe, zone, weekStart)
                    assertTrue("$zone $weekStart: $probe is in its own week", probe in week)
                    assertTrue(
                        "$zone $weekStart: the day is in its own week",
                        WeekBoundary.dayRange(probe, zone).start in week,
                    )
                }
            }
        }
    }

    @Test
    fun consecutiveWeeksTileWithoutGapOrOverlap() {
        for (zone in zones) {
            for (weekStart in DayOfWeek.entries) {
                for (probe in probes(zone)) {
                    val thisWeek = WeekBoundary.weekRange(probe, zone, weekStart)
                    val lastWeek = WeekBoundary.weekRangeOffset(probe, zone, weeksAgo = 1, weekStart = weekStart)
                    assertEquals(
                        "$zone $weekStart: last week runs up to this one",
                        thisWeek.start,
                        lastWeek.endExclusive,
                    )
                    assertTrue("$zone $weekStart: last week is before this one", lastWeek.start < thisWeek.start)
                }
            }
        }
    }

    /** A range of any length is covered, with no day left out and none counted twice. */
    @Test
    fun daysCoveringARangeTileIt() {
        for (zone in zones) {
            for (probe in probes(zone)) {
                for (days in listOf(1, 2, 7, 31, 365)) {
                    val start = WeekBoundary.startOfDay(probe, zone)
                    val range = WeekBoundary.Range(
                        start = start,
                        endExclusive = WeekBoundary.dayRange(
                            probe + days * 24L * 60L * 60L * 1000L,
                            zone,
                        ).endExclusive,
                    )
                    val buckets = WeekBoundary.daysCovering(range, zone)

                    assertTrue("$zone: $days days produce buckets", buckets.isNotEmpty())
                    assertEquals("$zone: starts where the range does", range.start, buckets.first().start)
                    buckets.zipWithNext { a, b ->
                        assertEquals("$zone: no gap or overlap over $days days", a.endExclusive, b.start)
                    }
                    assertTrue(
                        "$zone: the last bucket reaches the end of the range",
                        buckets.last().endExclusive >= range.endExclusive,
                    )
                }
            }
        }
    }

    /** Weeks and months covering a range tile it too, whatever the zone does in the middle. */
    @Test
    fun weeksAndMonthsCoveringARangeTileIt() {
        for (zone in zones) {
            for (probe in probes(zone)) {
                val range = WeekBoundary.Range(
                    start = WeekBoundary.startOfDay(probe, zone),
                    endExclusive = WeekBoundary.startOfDay(probe + 400L * 24 * 60 * 60 * 1000, zone),
                )
                for (weekStart in DayOfWeek.entries) {
                    val weeks = WeekBoundary.weeksCovering(range, zone, weekStart)
                    weeks.zipWithNext { a, b ->
                        assertEquals("$zone $weekStart: weeks tile", a.endExclusive, b.start)
                    }
                    assertTrue("$zone $weekStart: weeks reach the end", weeks.last().endExclusive >= range.endExclusive)
                }
                val months = WeekBoundary.monthsCovering(range, zone)
                months.zipWithNext { a, b ->
                    assertEquals("$zone: months tile", a.endExclusive, b.start)
                }
                assertTrue("$zone: months reach the end", months.last().endExclusive >= range.endExclusive)
            }
        }
    }
}
