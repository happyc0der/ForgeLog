package dev.happyc0der.forgelog.data.repository

import dev.happyc0der.forgelog.data.local.dayEntity
import dev.happyc0der.forgelog.data.local.exerciseEntity
import dev.happyc0der.forgelog.data.local.programEntity
import dev.happyc0der.forgelog.data.local.sessionEntity
import dev.happyc0der.forgelog.data.local.sessionExerciseEntity
import dev.happyc0der.forgelog.data.local.setLogEntity
import dev.happyc0der.forgelog.domain.model.SessionStatus
import dev.happyc0der.forgelog.testing.MainDispatcherRule
import dev.happyc0der.forgelog.testing.TestEnvironment
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Covers the two ways a logged session gets reused: repeating it as a new session, and promoting it
 * into a reusable program day.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SessionReuseTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var env: TestEnvironment

    private var programId = 0L
    private var dayId = 0L
    private var benchId = 0L
    private var ohpId = 0L

    @Before
    fun setUp() = runTest {
        env = TestEnvironment(mainDispatcherRule.dispatcher)
        env.time.now = 10_000L
        val programDao = env.database.programDao()
        val exerciseDao = env.database.exerciseDao()
        programId = programDao.insertProgram(programEntity(name = "PPL Strength"))
        dayId = programDao.insertDay(dayEntity(programId = programId, name = "Push Day"))
        benchId = exerciseDao.upsert(exerciseEntity(name = "Bench Press"))
        ohpId = exerciseDao.upsert(exerciseEntity(name = "Overhead Press"))
    }

    @After
    fun tearDown() {
        env.tearDown()
    }

    private suspend fun loggedSession(withFeelingAndNotes: Boolean = false): Long {
        val dao = env.database.workoutSessionDao()
        val sessionId = dao.insertSession(
            sessionEntity(
                sessionName = "PPL Strength · Push Day",
                programId = programId,
                programDayId = dayId,
                startedAt = 1_000L,
                completedAt = 5_000L,
                status = SessionStatus.COMPLETED,
                overallFeeling = if (withFeelingAndNotes) 4 else null,
            ),
        )
        listOf(benchId to "Bench Press", ohpId to "Overhead Press")
            .forEachIndexed { index, (exerciseId, name) ->
                val sessionExerciseId = dao.insertSessionExercise(
                    sessionExerciseEntity(
                        sessionId = sessionId,
                        exerciseId = exerciseId,
                        displayNameSnapshot = name,
                        exerciseOrder = index,
                        exerciseNotes = if (withFeelingAndNotes) "Felt strong" else null,
                        feeling = if (withFeelingAndNotes) 5 else null,
                    ),
                )
                dao.upsertSetLog(setLogEntity(sessionExerciseId = sessionExerciseId, reps = 5, weight = 135.0))
            }
        return sessionId
    }

    @Test
    fun `repeating a session copies its exercises in order`() = runTest {
        val sourceId = loggedSession()
        env.time.now = 99_000L

        val newId = env.sessionRepository.repeatSession(sourceId)

        assertNotEquals(sourceId, newId)
        val repeated = env.sessionRepository.getSessionDetail(newId!!)!!
        assertEquals(
            listOf("Bench Press", "Overhead Press"),
            repeated.exercises.map { it.exercise.displayNameSnapshot },
        )
        assertEquals(listOf(0, 1), repeated.exercises.map { it.exercise.exerciseOrder })
    }

    @Test
    fun `a repeated session starts empty and in progress`() = runTest {
        val sourceId = loggedSession()
        env.time.now = 99_000L

        val newId = env.sessionRepository.repeatSession(sourceId)!!
        val repeated = env.sessionRepository.getSessionDetail(newId)!!

        assertEquals(SessionStatus.IN_PROGRESS, repeated.session.status)
        assertNull(repeated.session.completedAt)
        assertEquals(99_000L, repeated.session.startedAt)
        // Set logs are deliberately not carried over — the plan repeats, the work does not.
        assertTrue(repeated.exercises.all { it.sets.isEmpty() })
    }

    @Test
    fun `a repeated session does not inherit the old session's notes or feeling`() = runTest {
        val sourceId = loggedSession(withFeelingAndNotes = true)

        val newId = env.sessionRepository.repeatSession(sourceId)!!
        val repeated = env.sessionRepository.getSessionDetail(newId)!!

        assertNull(repeated.session.overallFeeling)
        assertTrue(repeated.exercises.all { it.exercise.feeling == null })
        assertTrue(repeated.exercises.all { it.exercise.exerciseNotes == null })
    }

    @Test
    fun `a repeated session keeps its program link so previous-session matching still works`() =
        runTest {
            val sourceId = loggedSession()
            val newId = env.sessionRepository.repeatSession(sourceId)!!
            val repeated = env.sessionRepository.getSessionDetail(newId)!!
            assertEquals(programId, repeated.session.programId)
            assertEquals(dayId, repeated.session.programDayId)
        }

    @Test
    fun `a repeated session opens its first exercise`() = runTest {
        val sourceId = loggedSession()
        val newId = env.sessionRepository.repeatSession(sourceId)!!
        val repeated = env.sessionRepository.getSessionDetail(newId)!!
        assertEquals(
            repeated.exercises.first().exercise.id,
            repeated.session.expandedSessionExerciseId,
        )
    }

    @Test
    fun `repeating a session that no longer exists returns null instead of throwing`() = runTest {
        assertNull(env.sessionRepository.repeatSession(9_999L))
    }

    @Test
    fun `the original session is untouched by being repeated`() = runTest {
        val sourceId = loggedSession()
        env.sessionRepository.repeatSession(sourceId)

        val source = env.sessionRepository.getSessionDetail(sourceId)!!
        assertEquals(SessionStatus.COMPLETED, source.session.status)
        assertEquals(2, source.exercises.size)
        assertTrue(source.exercises.all { it.sets.size == 1 })
    }

    @Test
    fun `saving a session as a program day carries its exercises over`() = runTest {
        val sourceId = loggedSession()

        val newDayId = env.programRepository.createDayFromSession(programId, sourceId, "Push Day B")

        val day = env.programRepository.getDayDetail(newDayId)!!
        assertEquals("Push Day B", day.day.name)
        assertEquals(
            listOf("Bench Press", "Overhead Press"),
            day.exercises.map { it.exercise.name },
        )
        assertEquals(listOf(0, 1), day.exercises.map { it.programExercise.exerciseOrder })
    }

    /*
     * It used to copy only which exercises were done, so the new day asked for nothing and every
     * target had to be typed in again -- found by trying it on the test device.
     */
    @Test
    fun `a day saved from a session asks for what was done`() = runTest {
        val sourceId = loggedSession()

        val newDayId = env.programRepository.createDayFromSession(programId, sourceId, "Push Day B")

        val bench = env.programRepository.getDayDetail(newDayId)!!.exercises.first().programExercise
        assertEquals(1, bench.plannedSets)
        assertEquals(5, bench.targetRepMin)
        assertEquals(5, bench.targetRepMax)
        assertEquals(135.0, bench.targetWeight!!, 0.0)
    }

    @Test
    fun `a day created from a session is appended after existing days`() = runTest {
        val sourceId = loggedSession()
        val newDayId = env.programRepository.createDayFromSession(programId, sourceId, "Push Day B")
        val day = env.programRepository.getDayDetail(newDayId)!!
        // Push Day already occupies order 0.
        assertEquals(1, day.day.dayOrder)
    }

    @Test
    fun `exercises deleted from the library since the session are skipped, not invented`() = runTest {
        val dao = env.database.workoutSessionDao()
        val sessionId = dao.insertSession(
            sessionEntity(sessionName = "Ad-hoc", startedAt = 1_000L, completedAt = 2_000L),
        )
        // One exercise still in the library, one whose library row is gone (null id, snapshot only).
        dao.insertSessionExercise(
            sessionExerciseEntity(sessionId = sessionId, exerciseId = benchId, displayNameSnapshot = "Bench Press"),
        )
        dao.insertSessionExercise(
            sessionExerciseEntity(
                sessionId = sessionId,
                exerciseId = null,
                displayNameSnapshot = "Deleted Lift",
                exerciseOrder = 1,
            ),
        )

        val newDayId = env.programRepository.createDayFromSession(programId, sessionId, "Recovered")

        val day = env.programRepository.getDayDetail(newDayId)!!
        assertEquals(listOf("Bench Press"), day.exercises.map { it.exercise.name })
    }

    @Test
    fun `the same exercise logged twice becomes one program entry`() = runTest {
        val dao = env.database.workoutSessionDao()
        val sessionId = dao.insertSession(
            sessionEntity(sessionName = "Double", startedAt = 1_000L, completedAt = 2_000L),
        )
        repeat(2) { index ->
            dao.insertSessionExercise(
                sessionExerciseEntity(
                    sessionId = sessionId,
                    exerciseId = benchId,
                    displayNameSnapshot = "Bench Press",
                    exerciseOrder = index,
                ),
            )
        }

        val newDayId = env.programRepository.createDayFromSession(programId, sessionId, "Deduped")

        assertEquals(1, env.programRepository.getDayDetail(newDayId)!!.exercises.size)
    }

    @Test
    fun `saving into a program that does not exist fails loudly`() = runTest {
        val sourceId = loggedSession()
        assertThrows(IllegalStateException::class.java) {
            runTest { env.programRepository.createDayFromSession(9_999L, sourceId, "Nope") }
        }
    }

    @Test
    fun `saving from a session that does not exist fails loudly`() = runTest {
        assertThrows(IllegalStateException::class.java) {
            runTest { env.programRepository.createDayFromSession(programId, 9_999L, "Nope") }
        }
    }
}
