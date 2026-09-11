package dev.happyc0der.forgelog.workout

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import dev.happyc0der.forgelog.domain.workout.RestTimerState

/**
 * Where the rest in progress is kept, so it outlives the app's process.
 *
 * The countdown lives in memory. When Android closed the app mid-rest, the logger rebuilt it from
 * the last set and the plan, which was wrong in three ways: a skipped rest came back and buzzed, a
 * paused one came back running, and a +15 was forgotten -- with the alarm moved back to the planned
 * end. Kept here, what the user did to the rest comes back with it.
 */
interface RestStateStore {
    fun load(): ActiveRest?

    /** Null forgets it. */
    fun save(rest: ActiveRest?)
}

/** Keeps nothing: for tests, and anything that does not need a rest to outlive it. */
object NoRestStateStore : RestStateStore {
    override fun load(): ActiveRest? = null
    override fun save(rest: ActiveRest?) = Unit
}

class SharedPreferencesRestStateStore(context: Context) : RestStateStore {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    override fun load(): ActiveRest? {
        if (!prefs.contains(KEY_SESSION_ID)) return null
        // Anything unreadable is simply no saved rest: the logger then rebuilds one as before.
        return runCatching {
            ActiveRest(
                sessionId = prefs.getLong(KEY_SESSION_ID, 0L),
                state = RestTimerState(
                    targetSeconds = prefs.getInt(KEY_TARGET_SECONDS, 0),
                    anchorEpochMs = prefs.longOrNull(KEY_ANCHOR),
                    pausedRemainingSeconds = prefs.intOrNull(KEY_PAUSED_REMAINING),
                    isDismissed = prefs.getBoolean(KEY_DISMISSED, false),
                ),
                startedBySetId = prefs.longOrNull(KEY_STARTED_BY_SET),
            )
        }.getOrNull()
    }

    override fun save(rest: ActiveRest?) {
        prefs.edit {
            clear()
            if (rest != null) {
                putLong(KEY_SESSION_ID, rest.sessionId)
                putInt(KEY_TARGET_SECONDS, rest.state.targetSeconds)
                rest.state.anchorEpochMs?.let { putLong(KEY_ANCHOR, it) }
                rest.state.pausedRemainingSeconds?.let { putInt(KEY_PAUSED_REMAINING, it) }
                putBoolean(KEY_DISMISSED, rest.state.isDismissed)
                rest.startedBySetId?.let { putLong(KEY_STARTED_BY_SET, it) }
            }
        }
    }

    private fun SharedPreferences.longOrNull(key: String): Long? =
        if (contains(key)) getLong(key, 0L) else null

    private fun SharedPreferences.intOrNull(key: String): Int? =
        if (contains(key)) getInt(key, 0) else null

    private companion object {
        const val FILE_NAME = "forgelog_rest"
        const val KEY_SESSION_ID = "sessionId"
        const val KEY_TARGET_SECONDS = "targetSeconds"
        const val KEY_ANCHOR = "anchorEpochMs"
        const val KEY_PAUSED_REMAINING = "pausedRemainingSeconds"
        const val KEY_DISMISSED = "dismissed"
        const val KEY_STARTED_BY_SET = "startedBySetId"
    }
}
