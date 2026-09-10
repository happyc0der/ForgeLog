package dev.happyc0der.forgelog.ui.common

import dev.happyc0der.forgelog.domain.time.TimeProvider
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

const val ONE_SECOND_MS = 1_000L
const val ONE_MINUTE_MS = 60_000L

/**
 * Emits the current time every [periodMs] for as long as it is collected.
 *
 * Pick the slowest period that still looks correct: a running session clock needs
 * [ONE_SECOND_MS], but a date header or greeting only changes on the hour, so [ONE_MINUTE_MS] is
 * plenty and costs sixty times less work. The flow is cold, so nothing ticks while no screen is
 * watching.
 */
fun ticker(timeProvider: TimeProvider, periodMs: Long = ONE_SECOND_MS): Flow<Long> = flow {
    while (true) {
        emit(timeProvider.nowEpochMs())
        delay(periodMs)
    }
}
