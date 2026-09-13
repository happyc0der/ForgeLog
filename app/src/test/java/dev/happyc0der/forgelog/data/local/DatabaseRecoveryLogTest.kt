package dev.happyc0der.forgelog.data.local

import androidx.test.core.app.ApplicationProvider
import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The record of an unreadable database, which has to outlive the process that wrote it. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DatabaseRecoveryLogTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun log() = SharedPreferencesDatabaseRecoveryLog(context)

    @Before
    fun setUp() {
        log().markReported()
    }

    @Test
    fun nothingIsReportedUntilSomethingHappens() {
        assertNull(log().unreported())
    }

    @Test
    fun aRecordSurvivesANewInstance() {
        log().record(UnreadableDatabase(preservedFileName = "kept.db", atEpochMs = 1_700_000_000_000L))

        // A different instance, as the next launch would build.
        val read = log().unreported()

        assertEquals("kept.db", read?.preservedFileName)
        assertEquals(1_700_000_000_000L, read?.atEpochMs)
    }

    @Test
    fun aLossWithNoPreservedCopyIsStillARecord() {
        log().record(UnreadableDatabase(preservedFileName = null, atEpochMs = 42L))

        val read = log().unreported()

        assertNotNull("a loss with no copy went unrecorded", read)
        assertNull(read?.preservedFileName)
    }

    /**
     * A phone whose clock has not been set yet starts at the epoch, and that is a first boot, which
     * is exactly when a database might be unreadable. Whether something was recorded cannot be
     * decided by whether the time in it looks plausible.
     */
    @Test
    fun aLossAtTheEpochIsStillReported() {
        log().record(UnreadableDatabase(preservedFileName = "kept.db", atEpochMs = 0L))

        assertNotNull("a loss recorded with an unset clock was dropped", log().unreported())
    }

    @Test
    fun markingItReportedClearsItForGood() {
        log().record(UnreadableDatabase(preservedFileName = "kept.db", atEpochMs = 5L))
        log().markReported()

        assertNull(log().unreported())
    }

    @Test
    fun aSecondLossReplacesTheFirst() {
        log().record(UnreadableDatabase(preservedFileName = "first.db", atEpochMs = 1L))
        log().record(UnreadableDatabase(preservedFileName = "second.db", atEpochMs = 2L))

        assertEquals("second.db", log().unreported()?.preservedFileName)
    }

    /** A later loss with no copy must not leave the earlier copy's name behind. */
    @Test
    fun aLaterLossWithNoCopyDoesNotInheritAnOldFileName() {
        log().record(UnreadableDatabase(preservedFileName = "first.db", atEpochMs = 1L))
        log().record(UnreadableDatabase(preservedFileName = null, atEpochMs = 2L))

        assertNull("the new record claimed a file it did not keep", log().unreported()?.preservedFileName)
    }
}
