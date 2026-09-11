package dev.happyc0der.forgelog.ui.programs

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import dev.happyc0der.forgelog.data.local.dayEntity
import dev.happyc0der.forgelog.data.local.exerciseEntity
import dev.happyc0der.forgelog.data.local.programEntity
import dev.happyc0der.forgelog.domain.model.ExerciseCategory
import dev.happyc0der.forgelog.domain.model.ExerciseUnit
import dev.happyc0der.forgelog.testing.MainDispatcherRule
import dev.happyc0der.forgelog.testing.TestEnvironment
import dev.happyc0der.forgelog.ui.exercise.ExerciseFormState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ProgramDayBuilderViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var env: TestEnvironment
    private val created = mutableListOf<ProgramDayBuilderViewModel>()
    private var dayId = 0L
    private var benchId = 0L
    private var squatId = 0L

    @Before
    fun setUp() = runTest {
        env = TestEnvironment(mainDispatcherRule.dispatcher)
        val programDao = env.database.programDao()
        val programId = programDao.insertProgram(programEntity(name = "PPL"))
        dayId = programDao.insertDay(dayEntity(programId = programId, name = "Push Day"))
        benchId = env.database.exerciseDao().upsert(exerciseEntity(name = "Bench Press"))
        squatId = env.database.exerciseDao().upsert(exerciseEntity(name = "Squat"))
    }

    @After
    fun tearDown() {
        created.forEach { it.viewModelScope.cancel() }
        created.clear()
        env.tearDown()
    }

    private fun viewModel() = ProgramDayBuilderViewModel(
        savedStateHandle = SavedStateHandle(mapOf("dayId" to dayId)),
        application = ApplicationProvider.getApplicationContext<Application>(),
        programRepository = env.programRepository,
        exerciseRepository = env.exerciseRepository,
        settingsRepository = env.settingsRepository,
    ).also(created::add)

    @Test
    fun `a new exercise starts in the default weight unit from Settings`() = runTest {
        env.settingsRepository.setDefaultWeightUnit(ExerciseUnit.KG)
        val vm = viewModel()
        vm.defaultWeightUnit.test {
            var unit = awaitItem()
            while (unit != ExerciseUnit.KG) unit = awaitItem()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `two adds in quick succession get distinct orders`() = runTest {
        val vm = viewModel()
        // The bug this pins: both adds used to read the exercise count from uiState, which had not
        // caught up, so they persisted the same exerciseOrder.
        vm.addExercise(benchId)
        vm.addExercise(squatId)
        advanceUntilIdle()

        val orders = env.programRepository.getDayDetail(dayId)!!
            .exercises
            .map { it.programExercise.exerciseOrder }
        assertEquals(listOf(0, 1), orders)
    }

    @Test
    fun `an inline-created exercise is added after the ones already there`() = runTest {
        val vm = viewModel()
        vm.addExercise(benchId)
        advanceUntilIdle()

        val accepted = vm.createExerciseAndAdd(
            ExerciseFormState(
                name = "Cable Fly",
                category = ExerciseCategory.PUSH,
                defaultUnit = ExerciseUnit.LB,
            ),
        )
        advanceUntilIdle()

        assertTrue(accepted)
        val detail = env.programRepository.getDayDetail(dayId)!!
        assertEquals(listOf("Bench Press", "Cable Fly"), detail.exercises.map { it.exercise.name })
        assertEquals(listOf(0, 1), detail.exercises.map { it.programExercise.exerciseOrder })
    }

    @Test
    fun `an inline create with a blank name is refused before anything is written`() = runTest {
        val vm = viewModel()
        val accepted = vm.createExerciseAndAdd(ExerciseFormState(name = "   "))
        advanceUntilIdle()
        assertTrue(!accepted)
        assertEquals(0, env.programRepository.getDayDetail(dayId)!!.exercises.size)
    }

    @Test
    fun `an inline create with a malformed URL is refused`() = runTest {
        val vm = viewModel()
        val accepted = vm.createExerciseAndAdd(
            ExerciseFormState(name = "Dip", howToUrl = "not a url"),
        )
        advanceUntilIdle()
        assertTrue(!accepted)
        assertEquals(0, env.programRepository.getDayDetail(dayId)!!.exercises.size)
    }

    @Test
    fun `day notes can be written, trimmed, and cleared`() = runTest {
        val vm = viewModel()
        vm.uiState.test {
            var state = awaitItem()
            while (state.detail == null) state = awaitItem()
            // The column existed from the first schema with nothing able to write it.
            assertNull(state.detail?.day?.notes)

            vm.setDayNotes("  warm up the hips properly  ")
            while (state.detail?.day?.notes == null) state = awaitItem()
            assertEquals("warm up the hips properly", state.detail?.day?.notes)

            vm.setDayNotes("   ")
            while (state.detail?.day?.notes != null) state = awaitItem()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `targets configured here are stored on the program exercise`() = runTest {
        val vm = viewModel()
        vm.addExercise(benchId)
        advanceUntilIdle()

        val stored = env.programRepository.getDayDetail(dayId)!!.exercises.single().programExercise
        vm.saveProgramExercise(
            stored.copy(
                plannedSets = 4,
                targetRepMin = 6,
                targetRepMax = 8,
                targetWeight = 185.0,
                targetRestSeconds = 150,
                notes = "work up",
            ),
        )
        advanceUntilIdle()

        val updated = env.programRepository.getDayDetail(dayId)!!.exercises.single().programExercise
        assertEquals(4, updated.plannedSets)
        assertEquals(6, updated.targetRepMin)
        assertEquals(8, updated.targetRepMax)
        assertEquals(185.0, updated.targetWeight ?: 0.0, 0.001)
        assertEquals(150, updated.targetRestSeconds)
        assertEquals("work up", updated.notes)
    }
}
