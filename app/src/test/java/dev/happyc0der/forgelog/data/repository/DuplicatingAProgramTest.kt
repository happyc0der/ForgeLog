package dev.happyc0der.forgelog.data.repository

import dev.happyc0der.forgelog.data.local.dayEntity
import dev.happyc0der.forgelog.data.local.exerciseEntity
import dev.happyc0der.forgelog.data.local.programEntity
import dev.happyc0der.forgelog.data.local.programExerciseEntity
import dev.happyc0der.forgelog.data.local.sessionEntity
import dev.happyc0der.forgelog.domain.model.SessionStatus
import dev.happyc0der.forgelog.testing.MainDispatcherRule
import dev.happyc0der.forgelog.testing.TestEnvironment
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * What a duplicated program actually contains.
 *
 * The copy is made by taking each row and changing two fields — the id, and the parent it belongs
 * to — so every target the user set comes across for free. That is the good kind of code and also
 * the kind nothing notices breaking: drop a field from one of those copies during a refactor and
 * the program still duplicates, still has its days, still has the right lifts in the right order.
 * Only the planned weights are gone, and only the person who finds out mid-workout ever knows.
 *
 * Duplicating a program was reached by no test at all — the two that name it cover a double tap and
 * a program deleted from under the copy, neither of which looks at what came out.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DuplicatingAProgramTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var env: TestEnvironment
    private var programId = 0L
    private var benchId = 0L
    private var squatId = 0L

    @Before
    fun setUp() = runTest {
        env = TestEnvironment(mainDispatcherRule.dispatcher)
        env.time.now = 9_000L
        val exercises = env.database.exerciseDao()
        val programs = env.database.programDao()

        benchId = exercises.upsert(exerciseEntity(name = "Bench Press"))
        squatId = exercises.upsert(exerciseEntity(name = "Squat"))
        programId = programs.insertProgram(
            programEntity(name = "PPL", description = "the split", color = "#123456", createdAt = 1L, updatedAt = 2L),
        )
        val push = programs.insertDay(
            dayEntity(programId = programId, name = "Push Day", dayOrder = 0, notes = "heavy day"),
        )
        val legs = programs.insertDay(
            dayEntity(programId = programId, name = "Leg Day", dayOrder = 1, notes = null),
        )
        programs.insertProgramExercise(
            programExerciseEntity(
                programDayId = push,
                exerciseId = benchId,
                exerciseOrder = 0,
                plannedSets = 4,
                targetRepMin = 6,
                targetRepMax = 8,
                targetWeight = 185.0,
                targetRestSeconds = 180,
                defaultPointersOverride = "elbows tucked",
                notes = "pause on the chest",
            ),
        )
        programs.insertProgramExercise(
            programExerciseEntity(
                programDayId = push,
                exerciseId = squatId,
                exerciseOrder = 1,
                plannedSets = 3,
                targetDurationSeconds = 45,
            ),
        )
        programs.insertProgramExercise(
            programExerciseEntity(programDayId = legs, exerciseId = squatId, exerciseOrder = 0, plannedSets = 5),
        )
    }

    @After
    fun tearDown() = env.tearDown()

    private suspend fun copyId(): Long = env.programRepository.duplicateProgram(programId)

    @Test
    fun `the copy is named as a copy, is not archived, and is a different program`() = runTest {
        env.programRepository.setArchived(programId, true)

        val newId = copyId()

        val copy = env.programRepository.getProgram(newId)!!
        assertNotEquals(programId, newId)
        assertEquals("PPL (copy)", copy.name)
        assertFalse("an archived program produced an archived copy", copy.isArchived)
        assertEquals("the split", copy.description)
        assertEquals("#123456", copy.color)
    }

    @Test
    fun `every day comes across, in order, with its name and notes`() = runTest {
        val days = env.programRepository.observeDays(copyId()).first()

        assertEquals(listOf("Push Day", "Leg Day"), days.map { it.name })
        assertEquals(listOf(0, 1), days.map { it.dayOrder })
        assertEquals(listOf("heavy day", null), days.map { it.notes })
    }

    @Test
    fun `every planned target comes across with its exercise`() = runTest {
        val detail = env.programRepository.getProgramDetail(copyId())!!
        val push = detail.days.first { it.day.name == "Push Day" }

        assertEquals(listOf("Bench Press", "Squat"), push.exercises.map { it.exercise.name })
        val bench = push.exercises.first { it.exercise.name == "Bench Press" }.programExercise
        assertEquals(4, bench.plannedSets)
        assertEquals(6, bench.targetRepMin)
        assertEquals(8, bench.targetRepMax)
        assertEquals(185.0, bench.targetWeight ?: 0.0, 0.0)
        assertEquals(180, bench.targetRestSeconds)
        assertEquals("elbows tucked", bench.defaultPointersOverride)
        assertEquals("pause on the chest", bench.notes)

        val squat = push.exercises.first { it.exercise.name == "Squat" }.programExercise
        assertEquals(3, squat.plannedSets)
        assertEquals(45, squat.targetDurationSeconds)
        // The leg day's squat is its own row with its own target, not the push day's.
        val legs = detail.days.first { it.day.name == "Leg Day" }
        assertEquals(5, legs.exercises.single().programExercise.plannedSets)
    }

    @Test
    fun `the copy's rows belong to the copy, not to the original`() = runTest {
        val newId = copyId()

        val original = env.programRepository.getProgramDetail(programId)!!
        val copy = env.programRepository.getProgramDetail(newId)!!

        assertEquals("the original lost or gained a day", 2, original.days.size)
        assertEquals(2, copy.days.size)
        val originalDayIds = original.days.map { it.day.id }.toSet()
        copy.days.forEach { day ->
            assertFalse("a day is shared between the two programs", day.day.id in originalDayIds)
            assertEquals(newId, day.day.programId)
        }
        val originalRowIds = original.days.flatMap { it.exercises.map { e -> e.programExercise.id } }.toSet()
        copy.days.flatMap { it.exercises }.forEach { exercise ->
            assertFalse(
                "a program exercise row is shared between the two programs",
                exercise.programExercise.id in originalRowIds,
            )
        }
    }

    /** Training logged from the original stays with the original. A copy has no history of its own. */
    @Test
    fun `workouts logged from the original are not attached to the copy`() = runTest {
        val dayId = env.programRepository.observeDays(programId).first().first().id
        env.database.workoutSessionDao().insertSession(
            sessionEntity(
                sessionName = "PPL · Push Day",
                startedAt = 1_000,
                completedAt = 2_000,
                status = SessionStatus.COMPLETED,
                programId = programId,
                programDayId = dayId,
            ),
        )

        val newId = copyId()

        val sessions = env.database.backupDao().allSessions()
        assertEquals(1, sessions.size)
        assertEquals("the logged workout followed the copy", programId, sessions.single().programId)
        assertNotNull(env.programRepository.getProgram(newId))
    }
}
