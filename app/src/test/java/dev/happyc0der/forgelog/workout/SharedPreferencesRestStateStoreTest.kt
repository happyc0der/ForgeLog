package dev.happyc0der.forgelog.workout

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.happyc0der.forgelog.domain.workout.RestTimerState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SharedPreferencesRestStateStoreTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun `a running rest reads back as it was saved`() {
        val rest = ActiveRest(
            sessionId = 7L,
            state = RestTimerState(targetSeconds = 105, anchorEpochMs = 1_000_000L),
            startedBySetId = 42L,
        )
        SharedPreferencesRestStateStore(context).save(rest)

        assertEquals(rest, SharedPreferencesRestStateStore(context).load())
    }

    @Test
    fun `a paused, skipped, set-less rest keeps its nulls`() {
        val rest = ActiveRest(
            sessionId = 7L,
            state = RestTimerState(targetSeconds = 90, anchorEpochMs = null, pausedRemainingSeconds = 40, isDismissed = true),
            startedBySetId = null,
        )
        SharedPreferencesRestStateStore(context).save(rest)

        assertEquals(rest, SharedPreferencesRestStateStore(context).load())
    }

    @Test
    fun `saving nothing forgets it`() {
        val store = SharedPreferencesRestStateStore(context)
        store.save(ActiveRest(7L, RestTimerState(targetSeconds = 90, anchorEpochMs = 1L), 1L))
        store.save(null)

        assertNull(SharedPreferencesRestStateStore(context).load())
    }
}
