package dev.happyc0der.forgelog.workout

import dev.happyc0der.forgelog.domain.time.TimeProvider
import dev.happyc0der.forgelog.domain.workout.RestTimer
import dev.happyc0der.forgelog.domain.workout.RestTimerState
import dev.happyc0der.forgelog.ui.common.ticker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.transformWhile
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What happens when rest runs out. In the app it buzzes and/or beeps; in tests it is counted. */
fun interface RestAlert {
    suspend fun restFinished()
}

/** A running rest, and whose it is. */
data class ActiveRest(
    val sessionId: Long,
    val state: RestTimerState,
    /** The set whose tick started it, if a tick did: unticking that set stops it. */
    val startedBySetId: Long?,
)

/**
 * The rest countdown, held for the whole app rather than by the logger screen.
 *
 * It lived in the logger's ViewModel, which Android destroys the moment the user leaves the
 * logger. So checking History mid-rest cancelled the rest-end alert outright -- on the test device
 * a 30-second rest ran out on the Home screen and nothing buzzed -- and on returning, the countdown
 * was rebuilt from the database at its planned length, forgetting any +15 or pause. Held here, it
 * runs and alerts wherever the user is in the app; the logger only shows it and adjusts it.
 *
 * One rest at a time, belonging to one session. It is still memory, not storage: after the process
 * is killed the logger rebuilds it from the last completed set, as before.
 */
class RestTimerController(
    scope: CoroutineScope,
    private val timeProvider: TimeProvider,
    private val alert: RestAlert,
) {
    private val active = MutableStateFlow<ActiveRest?>(null)

    /** The countdown for [sessionId]; null when there is none. */
    fun observe(sessionId: Long): Flow<RestTimerState?> = active
        .map { rest -> rest?.takeIf { it.sessionId == sessionId }?.state }
        .distinctUntilChanged()

    fun current(sessionId: Long): ActiveRest? = active.value?.takeIf { it.sessionId == sessionId }

    fun start(sessionId: Long, targetSeconds: Int, anchorEpochMs: Long, startedBySetId: Long?) {
        active.value = ActiveRest(
            sessionId = sessionId,
            state = RestTimer.start(targetSeconds = targetSeconds, anchorEpochMs = anchorEpochMs),
            startedBySetId = startedBySetId,
        )
    }

    /**
     * Puts back a countdown rebuilt after the process was killed -- unless this session already
     * has one, running, paused or skipped, which is always more current than a reconstruction.
     */
    fun restore(sessionId: Long, state: RestTimerState, startedBySetId: Long) {
        active.update { current ->
            if (current?.sessionId == sessionId) current else ActiveRest(sessionId, state, startedBySetId)
        }
    }

    /** Pause, resume, adjust or skip -- applied only if [sessionId]'s rest is the one running. */
    fun update(sessionId: Long, transform: (RestTimerState) -> RestTimerState) {
        active.update { current ->
            if (current == null || current.sessionId != sessionId) {
                current
            } else {
                current.copy(state = transform(current.state))
            }
        }
    }

    fun stopIfStartedBy(sessionId: Long, setId: Long) {
        active.update { current ->
            if (current?.sessionId == sessionId && current.startedBySetId == setId) null else current
        }
    }

    /** The session is over; its rest goes with it. */
    fun clear(sessionId: Long) {
        active.update { current -> if (current?.sessionId == sessionId) null else current }
    }

    init {
        /*
         * Announces the end of rest exactly once per timer.
         *
         * The ticker runs only while a timer is active and still counting: transformWhile stops it
         * on the tick that reaches zero, so nothing wakes once a second between rests.
         * distinctUntilChanged keeps it to one alert per timer.
         */
        @OptIn(ExperimentalCoroutinesApi::class)
        scope.launch {
            active
                .map { it?.state }
                .distinctUntilChanged()
                .flatMapLatest { state ->
                    if (state == null || !state.isActive) {
                        flowOf(false)
                    } else {
                        ticker(timeProvider)
                            .map { now -> RestTimer.hasFinished(state, now) }
                            .transformWhile { finished ->
                                emit(finished)
                                !finished
                            }
                    }
                }
                .distinctUntilChanged()
                .collect { finished ->
                    if (!finished) return@collect
                    // An alert that fails -- no vibrator, a muted stream -- must not end the watch.
                    try {
                        alert.restFinished()
                    } catch (cancellation: CancellationException) {
                        throw cancellation
                    } catch (_: Exception) {
                    }
                }
        }
    }
}
