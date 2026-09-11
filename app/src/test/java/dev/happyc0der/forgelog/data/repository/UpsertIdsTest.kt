package dev.happyc0der.forgelog.data.repository

import dev.happyc0der.forgelog.data.local.exerciseEntity
import dev.happyc0der.forgelog.data.mapper.toDomain
import dev.happyc0der.forgelog.domain.model.ExerciseUnit
import dev.happyc0der.forgelog.domain.model.SessionStartExercise
import dev.happyc0der.forgelog.domain.model.SetLog
import dev.happyc0der.forgelog.domain.model.WorkoutProgram
import dev.happyc0der.forgelog.testing.MainDispatcherRule
import dev.happyc0der.forgelog.testing.TestEnvironment
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * An upsert says which row it wrote, whether it inserted or updated it.
 *
 * Room's own upsert answers -1 for an update. Nothing relied on the answer for an update yet, but
 * an importer writing an activity it has seen before is exactly that case.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UpsertIdsTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var env: TestEnvironment

    @Before
    fun setUp() {
        env = TestEnvironment(mainDispatcherRule.dispatcher)
    }

    @After
    fun tearDown() = env.tearDown()

    @Test
    fun `updating an exercise returns its id`() = runTest {
        val id = env.database.exerciseDao().upsert(exerciseEntity(name = "Bench Press"))
        val exercise = env.exerciseRepository.getExercise(id)!!
        assertEquals(id, env.exerciseRepository.upsert(exercise.copy(name = "Barbell bench press")))
    }

    @Test
    fun `updating a program returns its id`() = runTest {
        val id = env.programRepository.upsertProgram(
            WorkoutProgram(name = "PPL", color = "#A855F7", createdAt = 0L, updatedAt = 0L),
        )
        val program = env.programRepository.getProgram(id)!!
        assertEquals(id, env.programRepository.upsertProgram(program.copy(name = "Strength block")))
    }

    @Test
    fun `updating a session, its exercise and a set returns their ids`() = runTest {
        val benchId = env.database.exerciseDao().upsert(exerciseEntity(name = "Bench Press"))
        val bench = env.database.exerciseDao().getExercise(benchId)!!.toDomain()
        val sessionId = env.sessionRepository.startSession(
            programId = null,
            programDayId = null,
            sessionName = "Push",
            exercises = listOf(SessionStartExercise(exercise = bench)),
        )
        val detail = env.sessionRepository.getSessionDetail(sessionId)!!
        assertEquals(sessionId, env.sessionRepository.upsertSession(detail.session.copy(sessionName = "Push A")))

        val entry = detail.exercises.single().exercise
        assertEquals(entry.id, env.sessionRepository.upsertSessionExercise(entry.copy(exerciseNotes = "Felt good")))

        val setId = env.sessionRepository.upsertSetLog(
            SetLog(sessionExerciseId = entry.id, setNumber = 1, reps = 5, weight = 100.0, weightUnit = ExerciseUnit.LB),
        )
        val set = env.sessionRepository.getSessionDetail(sessionId)!!.exercises.single().sets.single()
        assertEquals(setId, env.sessionRepository.upsertSetLog(set.copy(reps = 6)))
    }
}
