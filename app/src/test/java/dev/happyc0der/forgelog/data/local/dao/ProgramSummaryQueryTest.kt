package dev.happyc0der.forgelog.data.local.dao

import dev.happyc0der.forgelog.data.local.ForgeLogDatabase
import dev.happyc0der.forgelog.data.local.dayEntity
import dev.happyc0der.forgelog.data.local.inMemoryDatabase
import dev.happyc0der.forgelog.data.local.programEntity
import dev.happyc0der.forgelog.data.local.sessionEntity
import dev.happyc0der.forgelog.domain.model.SessionStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The program summary is four correlated subqueries in one statement, which is easy to get subtly
 * wrong — a join instead of a subquery would multiply the day count by the session count.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ProgramSummaryQueryTest {

    private lateinit var database: ForgeLogDatabase
    private lateinit var dao: ProgramDao
    private lateinit var sessionDao: WorkoutSessionDao

    @Before
    fun setUp() {
        database = inMemoryDatabase()
        dao = database.programDao()
        sessionDao = database.workoutSessionDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    private suspend fun summaryFor(name: String) =
        dao.observeProgramSummaries(includeArchived = true).first()
            .first { it.program.name == name }

    @Test
    fun `a new program reports no history`() = runTest {
        dao.insertProgram(programEntity(name = "Fresh"))
        val summary = summaryFor("Fresh")
        assertEquals(0, summary.dayCount)
        assertNull(summary.lastPerformedAt)
        assertEquals(0, summary.completedSessionCount)
    }

    @Test
    fun `day count counts days and is not multiplied by sessions`() = runTest {
        val programId = dao.insertProgram(programEntity(name = "PPL"))
        repeat(3) { index ->
            dao.insertDay(dayEntity(programId = programId, name = "Day $index", dayOrder = index))
        }
        // Two completed sessions would inflate dayCount to six under a naive join.
        repeat(2) {
            sessionDao.insertSession(
                sessionEntity(
                    sessionName = "Session",
                    programId = programId,
                    startedAt = 1_000L,
                    completedAt = 2_000L,
                ),
            )
        }

        val summary = summaryFor("PPL")

        assertEquals(3, summary.dayCount)
        assertEquals(2, summary.completedSessionCount)
    }

    @Test
    fun `last performed is the latest completion`() = runTest {
        val programId = dao.insertProgram(programEntity(name = "PPL"))
        sessionDao.insertSession(
            sessionEntity(sessionName = "Old", programId = programId, startedAt = 0L, completedAt = 1_000L),
        )
        sessionDao.insertSession(
            sessionEntity(sessionName = "New", programId = programId, startedAt = 0L, completedAt = 9_000L),
        )

        assertEquals(9_000L, summaryFor("PPL").lastPerformedAt)
    }

    @Test
    fun `unfinished and abandoned sessions count for neither field`() = runTest {
        val programId = dao.insertProgram(programEntity(name = "PPL"))
        sessionDao.insertSession(
            sessionEntity(
                sessionName = "Running",
                programId = programId,
                startedAt = 0L,
                completedAt = null,
                status = SessionStatus.IN_PROGRESS,
            ),
        )
        sessionDao.insertSession(
            sessionEntity(
                sessionName = "Quit",
                programId = programId,
                startedAt = 0L,
                completedAt = 9_999L,
                status = SessionStatus.ABANDONED,
            ),
        )

        val summary = summaryFor("PPL")
        assertNull(summary.lastPerformedAt)
        assertEquals(0, summary.completedSessionCount)
    }

    @Test
    fun `another program's sessions do not leak into this one`() = runTest {
        val mine = dao.insertProgram(programEntity(name = "Mine"))
        val theirs = dao.insertProgram(programEntity(name = "Theirs"))
        sessionDao.insertSession(
            sessionEntity(sessionName = "Theirs", programId = theirs, startedAt = 0L, completedAt = 5_000L),
        )
        dao.insertDay(dayEntity(programId = mine, name = "Day"))

        val mineSummary = summaryFor("Mine")
        assertEquals(1, mineSummary.dayCount)
        assertNull(mineSummary.lastPerformedAt)
        assertEquals(0, mineSummary.completedSessionCount)
        assertEquals(5_000L, summaryFor("Theirs").lastPerformedAt)
    }

    @Test
    fun `an ad-hoc session belongs to no program and is counted nowhere`() = runTest {
        val programId = dao.insertProgram(programEntity(name = "PPL"))
        sessionDao.insertSession(
            sessionEntity(sessionName = "Ad-hoc", programId = null, startedAt = 0L, completedAt = 5_000L),
        )
        val summary = summaryFor("PPL")
        assertNull(summary.lastPerformedAt)
        assertEquals(0, summary.completedSessionCount)
    }

    @Test
    fun `archived programs are excluded unless asked for`() = runTest {
        val programId = dao.insertProgram(programEntity(name = "Archived"))
        dao.setArchived(programId, true, 1_000L)

        assertEquals(
            emptyList<String>(),
            dao.observeProgramSummaries(includeArchived = false).first().map { it.program.name },
        )
        assertEquals(
            listOf("Archived"),
            dao.observeProgramSummaries(includeArchived = true).first().map { it.program.name },
        )
    }
}
