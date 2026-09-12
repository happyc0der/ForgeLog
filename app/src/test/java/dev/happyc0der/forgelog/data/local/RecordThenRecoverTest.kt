package dev.happyc0der.forgelog.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * The corruption handler must not be the reason the app cannot start.
 *
 * It runs when the database is already unreadable, and everything it does before handing over --
 * copying the file aside, noting the loss in preferences -- touches a disk that may well be the
 * reason the database is unreadable. If any of that throws, the platform never replaces the file,
 * Room cannot open, the app cannot start, and the user can never reach the backup that would have
 * saved them. Losing the notice is a bad outcome; losing the app is the one this exists to prevent.
 */
class RecordThenRecoverTest {

    @Test
    fun recoveryRunsAfterRecording() {
        val order = mutableListOf<String>()

        recordThenRecover(
            record = { order += "record" },
            recover = { order += "recover" },
        )

        assertEquals(listOf("record", "recover"), order)
    }

    @Test
    fun recoveryStillRunsWhenRecordingThrows() {
        var recovered = false

        recordThenRecover(
            record = { throw IOException("no space left on device") },
            recover = { recovered = true },
        )

        assertTrue("the database would never have been replaced", recovered)
    }

    /** Including the kinds that are not ordinary exceptions. */
    @Test
    fun recoveryStillRunsWhenRecordingFailsBadly() {
        var recovered = false

        recordThenRecover(
            record = { throw OutOfMemoryError("preserving a large database") },
            recover = { recovered = true },
        )

        assertTrue("an Error while recording stopped the app from starting", recovered)
    }

    /** A failure to recover is not swallowed: there is nothing sensible left to do with it. */
    @Test
    fun aFailureToRecoverIsNotHidden() {
        var threw = false
        try {
            recordThenRecover(record = {}, recover = { throw IllegalStateException("boom") })
        } catch (expected: IllegalStateException) {
            threw = true
        }
        assertTrue("a failure to recover was swallowed", threw)
    }
}
