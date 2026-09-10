package dev.happyc0der.forgelog.domain.time

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.ZoneId

class WeekBoundaryTest {

    private val kolkata = ZoneId.of("Asia/Kolkata")
    private val newYork = ZoneId.of("America/New_York")

    /** 2026-01-01T09:30 in Asia/Kolkata — a Thursday. */
    private val newYearMorningIst = 1767240000000L

    @Test
    fun `startOfDay returns local midnight, not UTC midnight`() {
        // IST is UTC+5:30, so the local day starts 5h30m before the UTC day.
        assertEquals(1767205800000L, WeekBoundary.startOfDay(newYearMorningIst, kolkata))
    }

    @Test
    fun `dayRange is half-open around the local day`() {
        val range = WeekBoundary.dayRange(newYearMorningIst, kolkata)
        assertEquals(1767205800000L, range.start)
        assertEquals(1767205800000L + 24 * 3_600_000L, range.endExclusive)
        assertTrue(range.start in range)
        assertTrue(newYearMorningIst in range)
        assertFalse(range.endExclusive in range)
    }

    @Test
    fun `week start day is honoured`() {
        // 2026-01-01 is a Thursday, so a Monday week starts 2025-12-29 and a Sunday week 2025-12-28.
        assertEquals(
            1766946600000L,
            WeekBoundary.weekRange(newYearMorningIst, kolkata, DayOfWeek.MONDAY).start,
        )
        assertEquals(
            1766860200000L,
            WeekBoundary.weekRange(newYearMorningIst, kolkata, DayOfWeek.SUNDAY).start,
        )
    }

    @Test
    fun `week spanning a year boundary does not reset to January`() {
        val range = WeekBoundary.weekRange(newYearMorningIst, kolkata, DayOfWeek.MONDAY)
        // Starts in 2025, ends in 2026, and still covers exactly seven local days.
        assertEquals(1766946600000L, range.start)
        assertEquals(1766946600000L + 7 * 24 * 3_600_000L, range.endExclusive)
        assertTrue(newYearMorningIst in range)
    }

    @Test
    fun `spring-forward week is 167 hours, not 168`() {
        // US DST begins Sunday 2026-03-08; the Monday week containing it loses an hour.
        val afterSpringForward = 1772955000000L // 2026-03-08T03:30 America/New_York
        val range = WeekBoundary.weekRange(afterSpringForward, newYork, DayOfWeek.MONDAY)
        assertEquals(1772427600000L, range.start)
        assertEquals(1773028800000L, range.endExclusive)
        assertEquals(167L, (range.endExclusive - range.start) / 3_600_000L)
    }

    @Test
    fun `fall-back week is 169 hours`() {
        val duringFallBack = 1793000000000L // inside the week of Monday 2026-10-26
        val range = WeekBoundary.weekRange(duringFallBack, newYork, DayOfWeek.MONDAY)
        assertEquals(1792987200000L, range.start)
        assertEquals(1793595600000L, range.endExclusive)
        assertEquals(169L, (range.endExclusive - range.start) / 3_600_000L)
    }

    @Test
    fun `an instant on the first day of the week belongs to that week`() {
        val mondayStart = 1766946600000L
        val range = WeekBoundary.weekRange(mondayStart, kolkata, DayOfWeek.MONDAY)
        assertEquals(mondayStart, range.start)
        assertTrue(mondayStart in range)
    }

    @Test
    fun `an instant one millisecond before the week start belongs to the previous week`() {
        val justBefore = 1766946600000L - 1
        val range = WeekBoundary.weekRange(justBefore, kolkata, DayOfWeek.MONDAY)
        assertEquals(1766946600000L - 7 * 24 * 3_600_000L, range.start)
        assertEquals(1766946600000L, range.endExclusive)
    }

    @Test
    fun `weekRangeOffset walks back whole weeks`() {
        val current = WeekBoundary.weekRange(newYearMorningIst, kolkata, DayOfWeek.MONDAY)
        val previous = WeekBoundary.weekRangeOffset(newYearMorningIst, kolkata, 1, DayOfWeek.MONDAY)
        assertEquals(current.start, previous.endExclusive)
        assertEquals(0, WeekBoundary.weekRangeOffset(newYearMorningIst, kolkata, 0).start - current.start)
    }

    @Test
    fun `daysOfWeek tiles the week with seven contiguous ranges`() {
        val week = WeekBoundary.weekRange(newYearMorningIst, kolkata, DayOfWeek.MONDAY)
        val days = WeekBoundary.daysOfWeek(newYearMorningIst, kolkata, DayOfWeek.MONDAY)
        assertEquals(7, days.size)
        assertEquals(week.start, days.first().start)
        assertEquals(week.endExclusive, days.last().endExclusive)
        days.zipWithNext { a, b -> assertEquals(a.endExclusive, b.start) }
    }

    @Test
    fun `daysOfWeek across a DST change still tiles without gaps`() {
        val afterSpringForward = 1772955000000L
        val week = WeekBoundary.weekRange(afterSpringForward, newYork, DayOfWeek.MONDAY)
        val days = WeekBoundary.daysOfWeek(afterSpringForward, newYork, DayOfWeek.MONDAY)
        assertEquals(7, days.size)
        assertEquals(week.start, days.first().start)
        assertEquals(week.endExclusive, days.last().endExclusive)
        days.zipWithNext { a, b -> assertEquals(a.endExclusive, b.start) }
        // One of those days is 23 hours long — that is the point of not adding 86_400_000.
        assertTrue(days.any { (it.endExclusive - it.start) == 23 * 3_600_000L })
    }
}
