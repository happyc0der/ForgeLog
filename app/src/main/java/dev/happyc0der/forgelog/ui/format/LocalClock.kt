package dev.happyc0der.forgelog.ui.format

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import dev.happyc0der.forgelog.domain.time.SystemTimeProvider
import dev.happyc0der.forgelog.domain.time.SystemZoneProvider
import dev.happyc0der.forgelog.domain.time.TimeProvider
import dev.happyc0der.forgelog.domain.time.ZoneProvider
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * The app's injected clock and zone, for screens.
 *
 * Screens read the zone with `ZoneId.systemDefault()` and "today" with `LocalDate.now()`, around
 * the [TimeProvider] and [ZoneProvider] every ViewModel is given, so the dates on a screen could not
 * be pinned in a test and could disagree with the ViewModel beneath it. MainActivity provides the
 * injected ones; the defaults, the system's own, are what a screen rendered on its own gets.
 */
val LocalTimeProvider = staticCompositionLocalOf<TimeProvider> { SystemTimeProvider() }
val LocalZoneProvider = staticCompositionLocalOf<ZoneProvider> { SystemZoneProvider() }

/** The device zone, through [LocalZoneProvider]. */
@Composable
@ReadOnlyComposable
fun currentZone(): ZoneId = LocalZoneProvider.current.zone()

/** Today in [zone], by [LocalTimeProvider]. */
@Composable
@ReadOnlyComposable
fun today(zone: ZoneId): LocalDate =
    Instant.ofEpochMilli(LocalTimeProvider.current.nowEpochMs()).atZone(zone).toLocalDate()
