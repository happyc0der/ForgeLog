package dev.happyc0der.forgelog.domain.time

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Calendar arithmetic for analytics and history grouping.
 *
 * All inputs and outputs are UTC epoch millis; the [ZoneId] decides where a day or week starts.
 * Ranges are half-open — `start` is inclusive, `endExclusive` is the first millisecond of the
 * next period — so adjacent ranges tile the timeline with no gap and no double counting. That
 * matters on DST days, where a local day can be 23 or 25 hours long and a fixed 7 × 86_400_000
 * week would drift.
 */
object WeekBoundary {

    /** Half-open range of epoch millis, `[start, endExclusive)`. */
    data class Range(val start: Long, val endExclusive: Long) {
        operator fun contains(epochMs: Long): Boolean = epochMs >= start && epochMs < endExclusive
    }

    /** First millisecond of the local calendar day containing [epochMs]. */
    fun startOfDay(epochMs: Long, zone: ZoneId): Long = localDate(epochMs, zone).startMs(zone)

    /** The local calendar day containing [epochMs], as a half-open range. */
    fun dayRange(epochMs: Long, zone: ZoneId): Range {
        val date = localDate(epochMs, zone)
        return Range(date.startMs(zone), date.plusDays(1).startMs(zone))
    }

    /**
     * The calendar week containing [epochMs], starting on [weekStart].
     *
     * [weekStart] is a user preference — ISO weeks begin on Monday, many lifters think in
     * Sunday weeks — so it is never assumed here.
     */
    fun weekRange(epochMs: Long, zone: ZoneId, weekStart: DayOfWeek = DayOfWeek.MONDAY): Range {
        val first = startOfWeekDate(localDate(epochMs, zone), weekStart)
        return Range(first.startMs(zone), first.plusWeeks(1).startMs(zone))
    }

    /** The week [weeksAgo] weeks before the one containing [epochMs]; 1 means the previous week. */
    fun weekRangeOffset(
        epochMs: Long,
        zone: ZoneId,
        weeksAgo: Int,
        weekStart: DayOfWeek = DayOfWeek.MONDAY,
    ): Range {
        val first = startOfWeekDate(localDate(epochMs, zone), weekStart).minusWeeks(weeksAgo.toLong())
        return Range(first.startMs(zone), first.plusWeeks(1).startMs(zone))
    }

    /**
     * The seven local day ranges of the week containing [epochMs], in order from [weekStart].
     * Used for per-day volume bars, where an empty day must still occupy its slot.
     */
    fun daysOfWeek(
        epochMs: Long,
        zone: ZoneId,
        weekStart: DayOfWeek = DayOfWeek.MONDAY,
    ): List<Range> {
        val first = startOfWeekDate(localDate(epochMs, zone), weekStart)
        return (0L until 7L).map { offset ->
            val day = first.plusDays(offset)
            Range(day.startMs(zone), day.plusDays(1).startMs(zone))
        }
    }

    /**
     * The local day ranges covering [range], in order.
     *
     * Unlike [daysOfWeek] this follows the range it is given rather than a calendar week, so a chart
     * of an arbitrary span covers that span.
     */
    fun daysCovering(range: Range, zone: ZoneId): List<Range> =
        buckets(range, zone) { it.plusDays(1) }

    /** The week ranges covering [range], in order, each aligned to [weekStart]. */
    fun weeksCovering(
        range: Range,
        zone: ZoneId,
        weekStart: DayOfWeek = DayOfWeek.MONDAY,
    ): List<Range> = buckets(range, zone, { startOfWeekDate(it, weekStart) }) { it.plusWeeks(1) }

    /** The calendar-month ranges covering [range], in order. */
    fun monthsCovering(range: Range, zone: ZoneId): List<Range> =
        buckets(range, zone, { it.withDayOfMonth(1) }) { it.plusMonths(1) }

    /**
     * Ranges of one [next] step each, tiling [range] from the bucket [align] puts its start in.
     *
     * The last bucket may extend past the range's end: a bucket is a whole day, week or month, and
     * half of one would understate its volume.
     */
    private fun buckets(
        range: Range,
        zone: ZoneId,
        align: (LocalDate) -> LocalDate = { it },
        next: (LocalDate) -> LocalDate,
    ): List<Range> {
        if (range.endExclusive <= range.start) return emptyList()
        val last = localDate(range.endExclusive - 1, zone)
        val result = mutableListOf<Range>()
        var start = align(localDate(range.start, zone))
        while (!start.isAfter(last)) {
            val end = next(start)
            result += Range(start.startMs(zone), end.startMs(zone))
            start = end
        }
        return result
    }

    private fun localDate(epochMs: Long, zone: ZoneId): LocalDate =
        Instant.ofEpochMilli(epochMs).atZone(zone).toLocalDate()

    private fun startOfWeekDate(date: LocalDate, weekStart: DayOfWeek): LocalDate {
        val daysSinceStart = (date.dayOfWeek.value - weekStart.value + 7) % 7
        return date.minusDays(daysSinceStart.toLong())
    }

    /**
     * Start of day is resolved through the zone's own rules rather than by adding hours, so a
     * spring-forward day where 00:00 exists and a zone where it does not both behave correctly.
     */
    private fun LocalDate.startMs(zone: ZoneId): Long =
        atStartOfDay(zone).toInstant().toEpochMilli()
}
