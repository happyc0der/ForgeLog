package dev.happyc0der.forgelog.domain.time

import java.time.ZoneId

/**
 * The device time zone, injected rather than read statically.
 *
 * Every timestamp in the database is UTC epoch millis, and every "today", "this week" and
 * calendar-day boundary has to be computed in the user's own zone. Going through this seam
 * instead of [ZoneId.systemDefault] is what makes those calculations testable — a test can
 * pin a zone with a known DST transition and assert the boundaries it produces.
 */
fun interface ZoneProvider {
    fun zone(): ZoneId
}

class SystemZoneProvider : ZoneProvider {
    override fun zone(): ZoneId = ZoneId.systemDefault()
}
