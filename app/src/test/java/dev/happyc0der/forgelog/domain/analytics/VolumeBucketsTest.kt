package dev.happyc0der.forgelog.domain.analytics

import dev.happyc0der.forgelog.domain.time.WeekBoundary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId

class VolumeBucketsTest {

    private val zone = ZoneId.of("Asia/Kolkata")

    private fun range(from: String, untilExclusive: String) = WeekBoundary.Range(
        start = LocalDate.parse(from).atStartOfDay(zone).toInstant().toEpochMilli(),
        endExclusive = LocalDate.parse(untilExclusive).atStartOfDay(zone).toInstant().toEpochMilli(),
    )

    private fun bars(from: String, untilExclusive: String) =
        VolumeBuckets.of(range(from, untilExclusive), zone, DayOfWeek.MONDAY)

    @Test
    fun `a week is seven day-wide bars`() {
        val bars = bars("2024-03-04", "2024-03-11")
        assertEquals(VolumeBucket.DAY, bars.bucket)
        assertEquals(7, bars.ranges.size)
        assertEquals(range("2024-03-04", "2024-03-05"), bars.ranges.first())
        assertEquals(range("2024-03-10", "2024-03-11"), bars.ranges.last())
    }

    /** The bars tile the range: no gap between them, and none counted twice. */
    @Test
    fun `bars meet end to end`() {
        listOf(bars("2024-03-04", "2024-03-11"), bars("2024-01-01", "2024-06-01"), bars("2020-01-01", "2024-01-01"))
            .forEach { bars ->
                bars.ranges.zipWithNext { first, second ->
                    assertEquals(first.endExclusive, second.start)
                }
            }
    }

    @Test
    fun `a fortnight is still day-wide`() {
        val bars = bars("2024-03-01", "2024-03-15")
        assertEquals(VolumeBucket.DAY, bars.bucket)
        assertEquals(14, bars.ranges.size)
    }

    /**
     * A month used to be shown as the seven days of the week its first day fell in, so everything
     * after that week counted towards no bar at all.
     */
    @Test
    fun `a month is week-wide bars covering the whole month`() {
        val bars = bars("2024-03-01", "2024-04-01")
        assertEquals(VolumeBucket.WEEK, bars.bucket)
        // Aligned to the Monday before 1 March, through the week containing 31 March.
        assertEquals(range("2024-02-26", "2024-03-04"), bars.ranges.first())
        assertTrue(bars.ranges.last().contains(range("2024-03-31", "2024-04-01").start))
    }

    @Test
    fun `a week-wide chart follows the week-start setting`() {
        val sunday = VolumeBuckets.of(range("2024-03-01", "2024-04-01"), zone, DayOfWeek.SUNDAY)
        assertEquals(VolumeBucket.WEEK, sunday.bucket)
        assertEquals(range("2024-02-25", "2024-03-03"), sunday.ranges.first())
    }

    @Test
    fun `years are month-wide bars`() {
        val bars = bars("2021-01-01", "2024-01-01")
        assertEquals(VolumeBucket.MONTH, bars.bucket)
        assertEquals(36, bars.ranges.size)
        assertEquals(range("2021-01-01", "2021-02-01"), bars.ranges.first())
        assertEquals(range("2023-12-01", "2024-01-01"), bars.ranges.last())
    }

    @Test
    fun `an empty range has no bars`() {
        assertEquals(emptyList<WeekBoundary.Range>(), bars("2024-03-04", "2024-03-04").ranges)
    }

    /** A chart has to stay readable: the bar count is what decides the width. */
    @Test
    fun `no chart has more bars than its widest setting allows`() {
        listOf(
            "2024-03-01" to "2024-03-16",
            "2024-01-01" to "2024-07-01",
            "2015-01-01" to "2025-01-01",
        ).forEach { (from, until) ->
            val bars = bars(from, until)
            val limit = when (bars.bucket) {
                VolumeBucket.DAY -> VolumeBuckets.MAX_DAYS
                VolumeBucket.WEEK -> VolumeBuckets.MAX_WEEKS
                VolumeBucket.MONTH -> Int.MAX_VALUE
            }
            assertTrue("${bars.bucket} had ${bars.ranges.size} bars", bars.ranges.size <= limit)
        }
    }
}
