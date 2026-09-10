package dev.happyc0der.forgelog.data.local.dao

import dev.happyc0der.forgelog.data.local.ForgeLogDatabase
import dev.happyc0der.forgelog.data.local.inMemoryDatabase
import dev.happyc0der.forgelog.data.local.sessionEntity
import dev.happyc0der.forgelog.data.local.sessionExerciseEntity
import dev.happyc0der.forgelog.data.local.setLogEntity
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WorkoutSessionDaoTest {

    private lateinit var database: ForgeLogDatabase
    private lateinit var dao: WorkoutSessionDao

    @Before
    fun setUp() {
        database = inMemoryDatabase()
        dao = database.workoutSessionDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    private suspend fun insertSession(
        name: String,
        startedAt: Long,
        completedAt: Long?,
        status: SessionStatus = SessionStatus.COMPLETED,
    ): Long = dao.insertSession(
        sessionEntity(
            sessionName = name,
            startedAt = startedAt,
            completedAt = completedAt,
            status = status,
        ),
    )

    private suspend fun addSet(sessionId: Long, weight: Double, reps: Int, completed: Boolean = true) {
        val exerciseId = dao.insertSessionExercise(sessionExerciseEntity(sessionId = sessionId))
        dao.upsertSetLog(
            setLogEntity(
                sessionExerciseId = exerciseId,
                weight = weight,
                reps = reps,
                completed = completed,
            ),
        )
    }

    @Test
    fun `last completed session is the most recently completed one, not the most recently started`() =
        runTest {
            // Started later but finished earlier — completedAt is what "last workout" means.
            insertSession("Early finish", startedAt = 5_000L, completedAt = 6_000L)
            insertSession("Late finish", startedAt = 1_000L, completedAt = 9_000L)

            val detail = dao.observeLastCompletedSessionDetail().first()

            assertEquals("Late finish", detail?.session?.sessionName)
        }

    @Test
    fun `last completed ignores in-progress and abandoned sessions`() = runTest {
        insertSession("Done", startedAt = 1_000L, completedAt = 2_000L)
        insertSession("Running", startedAt = 8_000L, completedAt = null, status = SessionStatus.IN_PROGRESS)
        insertSession("Quit", startedAt = 9_000L, completedAt = 9_500L, status = SessionStatus.ABANDONED)

        val detail = dao.observeLastCompletedSessionDetail().first()

        assertEquals("Done", detail?.session?.sessionName)
    }

    @Test
    fun `last completed is null when nothing has been finished`() = runTest {
        insertSession("Running", startedAt = 1L, completedAt = null, status = SessionStatus.IN_PROGRESS)
        assertNull(dao.observeLastCompletedSessionDetail().first())
    }

    @Test
    fun `last completed carries its exercises and sets`() = runTest {
        val sessionId = insertSession("Push Day", startedAt = 1_000L, completedAt = 2_000L)
        addSet(sessionId, weight = 135.0, reps = 5)
        addSet(sessionId, weight = 95.0, reps = 8)

        val detail = dao.observeLastCompletedSessionDetail().first()

        assertEquals(2, detail?.exercises?.size)
        assertEquals(2, detail?.exercises?.sumOf { it.sets.size })
    }

    @Test
    fun `the window is half-open — start is included, end is excluded`() = runTest {
        insertSession("On the start boundary", startedAt = 0L, completedAt = 1_000L)
        insertSession("Inside", startedAt = 0L, completedAt = 1_500L)
        insertSession("On the end boundary", startedAt = 0L, completedAt = 2_000L)

        val names = dao.observeCompletedSessionDetailsBetween(1_000L, 2_000L)
            .first()
            .map { it.session.sessionName }

        // Newest first: 1500 then 1000. The 2000 session belongs to the next window.
        assertEquals(listOf("Inside", "On the start boundary"), names)
    }

    @Test
    fun `adjacent windows cannot double-count a session on their shared boundary`() = runTest {
        insertSession("Boundary", startedAt = 0L, completedAt = 2_000L)

        val first = dao.observeCompletedSessionDetailsBetween(1_000L, 2_000L).first()
        val second = dao.observeCompletedSessionDetailsBetween(2_000L, 3_000L).first()

        assertEquals(0, first.size)
        assertEquals(1, second.size)
    }

    @Test
    fun `the window excludes unfinished and abandoned sessions`() = runTest {
        insertSession("Done", startedAt = 0L, completedAt = 1_500L)
        insertSession("Running", startedAt = 0L, completedAt = null, status = SessionStatus.IN_PROGRESS)
        insertSession("Quit", startedAt = 0L, completedAt = 1_600L, status = SessionStatus.ABANDONED)

        val details = dao.observeCompletedSessionDetailsBetween(1_000L, 2_000L).first()

        assertEquals(1, details.size)
        assertEquals("Done", details.first().session.sessionName)
    }

    @Test
    fun `an empty window returns an empty list rather than failing`() = runTest {
        insertSession("Done", startedAt = 0L, completedAt = 500L)
        assertEquals(emptyList<Any>(), dao.observeCompletedSessionDetailsBetween(1_000L, 2_000L).first())
    }

    @Test
    fun `window results are newest first`() = runTest {
        insertSession("Older", startedAt = 0L, completedAt = 1_100L)
        insertSession("Newer", startedAt = 0L, completedAt = 1_900L)

        val names = dao.observeCompletedSessionDetailsBetween(1_000L, 2_000L)
            .first()
            .map { it.session.sessionName }

        assertEquals(listOf("Newer", "Older"), names)
    }

    @Test
    fun `deleting a session cascades to its exercises and sets`() = runTest {
        val sessionId = insertSession("Push Day", startedAt = 0L, completedAt = 1_000L)
        addSet(sessionId, weight = 100.0, reps = 5)

        dao.deleteSession(sessionId)

        assertNull(dao.getSessionDetail(sessionId))
        assertEquals(0, dao.countSessionsForProgram(1L))
    }
}
