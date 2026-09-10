package dev.happyc0der.forgelog.data.repository

import dev.happyc0der.forgelog.data.local.dayEntity
import dev.happyc0der.forgelog.data.local.exerciseEntity
import dev.happyc0der.forgelog.data.local.programEntity
import dev.happyc0der.forgelog.data.local.programExerciseEntity
import dev.happyc0der.forgelog.domain.model.hasTargets
import dev.happyc0der.forgelog.domain.model.Exercise
import dev.happyc0der.forgelog.domain.model.ExerciseCategory
import dev.happyc0der.forgelog.domain.model.ExerciseUnit
import dev.happyc0der.forgelog.domain.model.SessionStartExercise
import dev.happyc0der.forgelog.testing.MainDispatcherRule
import dev.happyc0der.forgelog.testing.TestEnvironment
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Planned targets used to be dropped on the floor at session start — everything configured in the
 * day builder was discarded the moment a workout began. These pin that they arrive, that they are
 * snapshots, and that the program is never written back to.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SessionTargetsTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var env: TestEnvironment
    private var programId = 0L
    private var dayId = 0L
    private var benchId = 0L

    @Before
    fun setUp() = runTest {
        env = TestEnvironment(mainDispatcherRule.dispatcher)
        env.time.now = 1_000L
        programId = env.database.programDao().insertProgram(programEntity(name = "PPL"))
        dayId = env.database.programDao().insertDay(dayEntity(programId = programId, name = "Push Day"))
        benchId = env.database.exerciseDao().upsert(exerciseEntity(name = "Bench Press"))
        env.database.programDao().insertProgramExercise(
            programExerciseEntity(
                programDayId = dayId,
                exerciseId = benchId,
                plannedSets = 4,
                targetRepMin = 6,
                targetRepMax = 8,
                targetWeight = 185.0,
                targetRestSeconds = 150,
            ),
        )
    }

    @After
    fun tearDown() = env.tearDown()

    private fun bench() = Exercise(
        id = benchId,
        name = "Bench Press",
        category = ExerciseCategory.PUSH,
        defaultUnit = ExerciseUnit.LB,
        howToUrl = null,
        defaultPointers = null,
        isArchived = false,
        createdAt = 0L,
        updatedAt = 0L,
    )

    @Test
    fun `startSession snapshots every target onto the session exercise`() = runTest {
        val sessionId = env.sessionRepository.startSession(
            programId = programId,
            programDayId = dayId,
            sessionName = "PPL · Push Day",
            exercises = listOf(
                SessionStartExercise(
                    exercise = bench(),
                    plannedSets = 4,
                    targetRepMin = 6,
                    targetRepMax = 8,
                    targetWeight = 185.0,
                    targetRestSeconds = 150,
                ),
            ),
        )

        val logged = env.sessionRepository.getSessionDetail(sessionId)!!.exercises.single().exercise
        assertEquals(4, logged.plannedSets)
        assertEquals(6, logged.targetRepMin)
        assertEquals(8, logged.targetRepMax)
        assertEquals(185.0, logged.targetWeight ?: 0.0, 0.001)
        assertEquals(150, logged.targetRestSeconds)
        assertTrue(logged.hasTargets)
    }

    @Test
    fun `an exercise with no plan records no targets rather than zeros`() = runTest {
        val sessionId = env.sessionRepository.startSession(
            programId = null,
            programDayId = null,
            sessionName = "Ad-hoc",
            exercises = listOf(SessionStartExercise(exercise = bench())),
        )

        val logged = env.sessionRepository.getSessionDetail(sessionId)!!.exercises.single().exercise
        assertNull(logged.plannedSets)
        assertNull(logged.targetRepMin)
        assertNull(logged.targetWeight)
        assertNull(logged.targetRestSeconds)
        assertFalse(logged.hasTargets)
    }

    @Test
    fun `targets adjusted for one session do not change the program`() = runTest {
        // The planner let the user drop to 165 for today only.
        env.sessionRepository.startSession(
            programId = programId,
            programDayId = dayId,
            sessionName = "PPL · Push Day",
            exercises = listOf(
                SessionStartExercise(
                    exercise = bench(),
                    plannedSets = 3,
                    targetWeight = 165.0,
                    targetRestSeconds = 90,
                ),
            ),
        )

        val template = env.programRepository.getDayDetail(dayId)!!.exercises.single().programExercise
        assertEquals(4, template.plannedSets)
        assertEquals(185.0, template.targetWeight ?: 0.0, 0.001)
        assertEquals(150, template.targetRestSeconds)
    }

    @Test
    fun `editing the program later does not rewrite a session already logged`() = runTest {
        val sessionId = env.sessionRepository.startSession(
            programId = programId,
            programDayId = dayId,
            sessionName = "PPL · Push Day",
            exercises = listOf(
                SessionStartExercise(exercise = bench(), targetWeight = 185.0, plannedSets = 4),
            ),
        )

        val template = env.programRepository.getDayDetail(dayId)!!.exercises.single().programExercise
        env.programRepository.upsertProgramExercise(
            template.copy(targetWeight = 225.0, plannedSets = 6),
        )

        val logged = env.sessionRepository.getSessionDetail(sessionId)!!.exercises.single().exercise
        // The snapshot is what the session was aiming for at the time, and stays that way.
        assertEquals(185.0, logged.targetWeight ?: 0.0, 0.001)
        assertEquals(4, logged.plannedSets)
    }

    @Test
    fun `a session created by hand defaults to manual provenance`() = runTest {
        val sessionId = env.sessionRepository.startSession(
            programId = null,
            programDayId = null,
            sessionName = "Ad-hoc",
            exercises = listOf(SessionStartExercise(exercise = bench())),
        )
        val session = env.sessionRepository.getSession(sessionId)!!
        assertEquals(dev.happyc0der.forgelog.domain.model.SessionSource.MANUAL, session.source)
        assertNull(session.externalId)
        assertNull(session.externalSource)
    }
}
