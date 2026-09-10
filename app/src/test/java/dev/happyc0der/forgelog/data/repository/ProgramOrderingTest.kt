package dev.happyc0der.forgelog.data.repository

import dev.happyc0der.forgelog.data.local.dayEntity
import dev.happyc0der.forgelog.data.local.exerciseEntity
import dev.happyc0der.forgelog.data.local.programEntity
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
 * Sort positions used to come from `COUNT(*)`, which is only the next free position while no row
 * has ever been deleted. Deleting leaves a gap and nothing renumbers the survivors, so the count
 * under-reports and the next append lands on a position already in use — after which `ORDER BY`
 * returns the tie however it likes and the two rows swap places between reads.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ProgramOrderingTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var env: TestEnvironment
    private var programId = 0L
    private var dayId = 0L

    @Before
    fun setUp() = runTest {
        env = TestEnvironment(mainDispatcherRule.dispatcher)
        programId = env.database.programDao().insertProgram(programEntity(name = "PPL"))
        dayId = env.database.programDao().insertDay(dayEntity(programId = programId, name = "Push"))
    }

    @After
    fun tearDown() = env.tearDown()

    private suspend fun addExercise(name: String): Long {
        val exerciseId = env.database.exerciseDao().upsert(exerciseEntity(name = name))
        env.programRepository.appendProgramExercise(dayId, exerciseId)
        return exerciseId
    }

    private suspend fun exerciseOrders(): List<Int> = env.programRepository.getDayDetail(dayId)!!
        .exercises
        .map { it.programExercise.exerciseOrder }

    @Test
    fun `appending after a deletion does not reuse an occupied position`() = runTest {
        addExercise("Bench")
        addExercise("Incline")
        addExercise("Fly")
        assertEquals(listOf(0, 1, 2), exerciseOrders())

        val first = env.programRepository.getDayDetail(dayId)!!.exercises.first().programExercise
        env.programRepository.deleteProgramExercise(first.id)
        // Two rows remain, at positions 1 and 2 — a count would say the next free position is 2.
        addExercise("Dip")

        val orders = exerciseOrders()
        assertEquals("positions must stay unique", orders.size, orders.distinct().size)
        assertEquals(listOf(1, 2, 3), orders)
    }

    @Test
    fun `appending a day after a deletion does not reuse an occupied position`() = runTest {
        env.programRepository.upsertDay(
            dev.happyc0der.forgelog.domain.model.ProgramDay(
                programId = programId,
                name = "Pull",
                dayOrder = 1,
            ),
        )
        val pull = env.programRepository.getProgramDetail(programId)!!.days.last().day
        env.programRepository.deleteDay(dayId)

        // "Push" is gone; "Pull" still sits at position 1, so a count would say 1 is free.
        val duplicated = env.programRepository.duplicateDay(pull.id)

        val orders = env.programRepository.getProgramDetail(programId)!!.days.map { it.day.dayOrder }
        assertEquals("positions must stay unique", orders.size, orders.distinct().size)
        assertEquals(listOf(1, 2), orders)
        assertEquals(2, env.programRepository.getDayDetail(duplicated)!!.day.dayOrder)
    }
}
