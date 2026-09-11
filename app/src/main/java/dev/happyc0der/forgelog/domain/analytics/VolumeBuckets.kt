package dev.happyc0der.forgelog.domain.analytics

import dev.happyc0der.forgelog.domain.time.WeekBoundary
import java.time.DayOfWeek
import java.time.ZoneId

/** How wide one bar of the volume chart is. */
enum class VolumeBucket {
    DAY,
    WEEK,
    MONTH,
}

/** The bars of the volume chart: what each one covers, and how wide they are. */
data class VolumeBars(
    val bucket: VolumeBucket,
    val ranges: List<WeekBoundary.Range>,
)

/**
 * Chooses the bar width for a span of training, so the chart always covers the span it is shown for.
 *
 * The chart used to be seven day-wide bars of the calendar week containing the span's start,
 * whatever the span was. With an arbitrary range chosen -- a month, a quarter -- it silently showed
 * the first week of it and nothing else: training in the rest of the range counted towards no bar,
 * while empty days of that first week were drawn as if the range ended there.
 *
 * The width grows with the span instead, because a month of day-wide bars is thirty labels across a
 * phone and reads as nothing at all.
 */
object VolumeBuckets {

    /** Up to this many days is still legible as one bar per day. */
    const val MAX_DAYS = 14

    /** Beyond this many week-wide bars, a month is the narrower-reading choice. */
    const val MAX_WEEKS = 26

    fun of(range: WeekBoundary.Range, zone: ZoneId, weekStart: DayOfWeek): VolumeBars {
        val days = WeekBoundary.daysCovering(range, zone)
        if (days.size <= MAX_DAYS) return VolumeBars(VolumeBucket.DAY, days)
        val weeks = WeekBoundary.weeksCovering(range, zone, weekStart)
        if (weeks.size <= MAX_WEEKS) return VolumeBars(VolumeBucket.WEEK, weeks)
        return VolumeBars(VolumeBucket.MONTH, WeekBoundary.monthsCovering(range, zone))
    }
}
