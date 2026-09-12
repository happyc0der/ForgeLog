package dev.happyc0der.forgelog.ui.exercise

import android.app.Application
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import dev.happyc0der.forgelog.data.local.dayEntity
import dev.happyc0der.forgelog.data.local.exerciseEntity
import dev.happyc0der.forgelog.data.local.programEntity
import dev.happyc0der.forgelog.data.local.programExerciseEntity
import dev.happyc0der.forgelog.data.local.sessionEntity
import dev.happyc0der.forgelog.data.local.sessionExerciseEntity
import dev.happyc0der.forgelog.testing.MainDispatcherRule
import dev.happyc0der.forgelog.testing.TestEnvironment
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Deleting an exercise from the library. The confirmation used to claim "It is not used in any
 * logged session" before anything had checked, and said nothing about the program days the
 * exercise would be taken out of.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExerciseLibraryViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var env: TestEnvironment
    private val created = mutableListOf<ExerciseLibraryViewModel>()

    @Before
    fun setUp() {
        env = TestEnvironment(mainDispatcherRule.dispatcher)
    }

    @After
    fun tearDown() {
        created.forEach { it.viewModelScope.cancel() }
        created.clear()
        env.tearDown()
    }

    private fun viewModel() = ExerciseLibraryViewModel(
        application = ApplicationProvider.getApplicationContext<Application>(),
        exerciseRepository = env.exerciseRepository,
    ).also(created::add)

    private suspend fun exercise(name: String) = env.exerciseRepository.getExercise(
        env.database.exerciseDao().upsert(exerciseEntity(name = name)),
    )!!

    /*
     * The empty state picks its advice from this count. Zero means the library really has nothing
     * to show and the answer is to add a lift; anything else means the archive filter is holding
     * one back, and telling the user to add one gets them a second copy of a lift they own.
     */

    @Test
    fun `an empty library hides nothing`() = runTest {
        val vm = viewModel()
        vm.uiState.test {
            var current = awaitItem()
            while (current.isLoading) current = awaitItem()
            assertTrue(current.exercises.isEmpty())
            assertEquals(0, current.hiddenArchivedCount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `an all-archived library reports what the filter is holding back`() = runTest {
        val squat = exercise("Squat")
        env.exerciseRepository.setArchived(squat.id, true)
        advanceUntilIdle()

        val vm = viewModel()
        vm.uiState.test {
            var current = awaitItem()
            while (current.isLoading || current.hiddenArchivedCount == 0) current = awaitItem()
            assertTrue(current.exercises.isEmpty())
            assertEquals(1, current.hiddenArchivedCount)

            vm.onToggleArchived()
            while (current.exercises.isEmpty()) current = awaitItem()
            assertEquals(0, current.hiddenArchivedCount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a search only counts the archived lifts it would have matched`() = runTest {
        val squat = exercise("Squat")
        val curl = exercise("Bicep Curl")
        env.exerciseRepository.setArchived(squat.id, true)
        env.exerciseRepository.setArchived(curl.id, true)
        advanceUntilIdle()

        val vm = viewModel()
        vm.onQueryChange("squat")
        vm.uiState.test {
            var current = awaitItem()
            while (current.isLoading || current.query != "squat") current = awaitItem()
            // Both are archived; only one of them was ever going to match this search.
            assertEquals(1, current.hiddenArchivedCount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a search that matches nothing at all blames the search, not the filter`() = runTest {
        val squat = exercise("Squat")
        env.exerciseRepository.setArchived(squat.id, true)
        advanceUntilIdle()

        val vm = viewModel()
        vm.onQueryChange("deadlift")
        vm.uiState.test {
            var current = awaitItem()
            while (current.isLoading || current.query != "deadlift") current = awaitItem()
            assertTrue(current.exercises.isEmpty())
            assertEquals(0, current.hiddenArchivedCount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `an exercise with history is refused before anything is confirmed`() = runTest {
        val squat = exercise("Squat")
        val dao = env.database.workoutSessionDao()
        val sessionId = dao.insertSession(sessionEntity(sessionName = "Legs", startedAt = 1_000L, completedAt = 2_000L))
        dao.insertSessionExercise(sessionExerciseEntity(sessionId = sessionId, exerciseId = squat.id, displayNameSnapshot = "Squat"))
        val vm = viewModel()

        vm.events.test {
            vm.requestDelete(squat)
            advanceUntilIdle()
            assertTrue(awaitItem() is ExerciseLibraryEvent.Message)
            cancelAndIgnoreRemainingEvents()
        }

        assertNull("no dialog for a delete that cannot happen", vm.pendingDelete.value)
        assertNotNull(env.exerciseRepository.getExercise(squat.id))
    }

    @Test
    fun `the confirmation says how many program days lose the exercise`() = runTest {
        val squat = exercise("Squat")
        val programDao = env.database.programDao()
        val programId = programDao.insertProgram(programEntity(name = "Strength"))
        repeat(2) { index ->
            val dayId = programDao.insertDay(dayEntity(programId = programId, name = "Day $index", dayOrder = index))
            programDao.insertProgramExercise(programExerciseEntity(programDayId = dayId, exerciseId = squat.id))
        }
        val vm = viewModel()

        vm.requestDelete(squat)
        advanceUntilIdle()
        assertEquals(2, vm.pendingDelete.value?.programDays)

        vm.confirmDelete()
        advanceUntilIdle()
        assertNull(vm.pendingDelete.value)
        assertNull(env.exerciseRepository.getExercise(squat.id))
        assertEquals(0, programDao.countDaysUsingExercise(squat.id))
    }

    @Test
    fun `cancelling leaves the exercise where it was`() = runTest {
        val squat = exercise("Squat")
        val vm = viewModel()

        vm.requestDelete(squat)
        advanceUntilIdle()
        assertEquals(0, vm.pendingDelete.value?.programDays)
        vm.cancelDelete()
        advanceUntilIdle()

        assertNull(vm.pendingDelete.value)
        assertNotNull(env.exerciseRepository.getExercise(squat.id))
    }
}
