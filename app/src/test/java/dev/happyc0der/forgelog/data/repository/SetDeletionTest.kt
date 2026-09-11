package dev.happyc0der.forgelog.data.repository

import dev.happyc0der.forgelog.data.local.exerciseEntity
import dev.happyc0der.forgelog.domain.model.Exercise
import dev.happyc0der.forgelog.domain.model.ExerciseCategory
import dev.happyc0der.forgelog.domain.model.ExerciseUnit
import dev.happyc0der.forgelog.domain.model.SessionStartExercise
import dev.happyc0der.forgelog.domain.model.SetLog
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

/** A set's number is its position, so deleting one closes the gap it leaves. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SetDeletionTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var env: TestEnvironment
    private var sessionId = 0L
    private var benchEntryId = 0L
    private var rowEntryId = 0L

    @Before
    fun setUp() = runTest {
        env = TestEnvironment(mainDispatcherRule.dispatcher)
        val benchId = env.database.exerciseDao().upsert(exerciseEntity(name = "Bench Press"))
        val rowId = env.database.exerciseDao().upsert(exerciseEntity(name = "Barbell Row"))
        sessionId = env.sessionRepository.startSession(
            programId = null,
            programDayId = null,
            sessionName = "Ad-hoc",
            exercises = listOf(start(benchId, "Bench Press"), start(rowId, "Barbell Row")),
        )
        val entries = env.sessionRepository.getSessionDetail(sessionId)!!.exercises
        benchEntryId = entries.single { it.exercise.displayNameSnapshot == "Bench Press" }.exercise.id
        rowEntryId = entries.single { it.exercise.displayNameSnapshot == "Barbell Row" }.exercise.id
    }

    @After
    fun tearDown() = env.tearDown()

    private fun start(id: Long, name: String) = SessionStartExercise(
        exercise = Exercise(
            id = id,
            name = name,
            category = ExerciseCategory.PUSH,
            defaultUnit = ExerciseUnit.LB,
            howToUrl = null,
            defaultPointers = null,
            isArchived = false,
            createdAt = 0L,
            updatedAt = 0L,
        ),
    )

    private suspend fun addSets(entryId: Long, count: Int): List<Long> = (1..count).map { number ->
        env.sessionRepository.upsertSetLog(
            SetLog(
                sessionExerciseId = entryId,
                setNumber = number,
                reps = number,
                weight = 100.0,
                weightUnit = ExerciseUnit.LB,
            ),
        )
    }

    private suspend fun setsOf(entryId: Long) = env.sessionRepository.getSessionDetail(sessionId)!!
        .exercises.single { it.exercise.id == entryId }.sets.sortedBy { it.setNumber }

    @Test
    fun `deleting a middle set moves the later ones up`() = runTest {
        val ids = addSets(benchEntryId, count = 4)

        env.sessionRepository.deleteSetLog(ids[1])

        val left = setsOf(benchEntryId)
        assertEquals(listOf(1, 2, 3), left.map { it.setNumber })
        // The same sets, in the same order: reps were 1, 3 and 4.
        assertEquals(listOf(1, 3, 4), left.map { it.reps })
    }

    @Test
    fun `deleting the last set renumbers nothing`() = runTest {
        val ids = addSets(benchEntryId, count = 3)

        env.sessionRepository.deleteSetLog(ids[2])

        assertEquals(listOf(1, 2), setsOf(benchEntryId).map { it.setNumber })
    }

    @Test
    fun `another exercise's sets are left alone`() = runTest {
        val benchIds = addSets(benchEntryId, count = 3)
        addSets(rowEntryId, count = 3)

        env.sessionRepository.deleteSetLog(benchIds[0])

        assertEquals(listOf(1, 2), setsOf(benchEntryId).map { it.setNumber })
        assertEquals(listOf(1, 2, 3), setsOf(rowEntryId).map { it.setNumber })
    }

    @Test
    fun `deleting a set that is already gone does nothing`() = runTest {
        val ids = addSets(benchEntryId, count = 2)
        env.sessionRepository.deleteSetLog(ids[0])

        env.sessionRepository.deleteSetLog(ids[0])

        assertEquals(listOf(1), setsOf(benchEntryId).map { it.setNumber })
    }
}
