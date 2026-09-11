package dev.happyc0der.forgelog.ui.exercise

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import dev.happyc0der.forgelog.data.local.exerciseEntity
import dev.happyc0der.forgelog.domain.model.ExerciseCategory
import dev.happyc0der.forgelog.domain.model.ExerciseUnit
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

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExerciseEditorViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var env: TestEnvironment
    private val created = mutableListOf<ExerciseEditorViewModel>()
    private var benchId = 0L

    @Before
    fun setUp() = runTest {
        env = TestEnvironment(mainDispatcherRule.dispatcher)
        benchId = env.database.exerciseDao().upsert(exerciseEntity(name = "Bench Press"))
    }

    @After
    fun tearDown() {
        created.forEach { it.viewModelScope.cancel() }
        created.clear()
        env.tearDown()
    }

    private fun viewModel(exerciseId: Long = -1L) = ExerciseEditorViewModel(
        savedStateHandle = SavedStateHandle(mapOf("exerciseId" to exerciseId)),
        application = ApplicationProvider.getApplicationContext<Application>(),
        exerciseRepository = env.exerciseRepository,
        settingsRepository = env.settingsRepository,
    ).also(created::add)

    @Test
    fun `a new exercise starts in the default weight unit from Settings`() = runTest {
        env.settingsRepository.setDefaultWeightUnit(ExerciseUnit.KG)
        val vm = viewModel()
        vm.uiState.test {
            var state = awaitItem()
            while (state.form.defaultUnit != ExerciseUnit.KG) state = awaitItem()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a unit the user picked is not replaced by the Settings default`() = runTest {
        env.settingsRepository.setDefaultWeightUnit(ExerciseUnit.KG)
        val vm = viewModel()
        vm.onUnitChange(ExerciseUnit.BODYWEIGHT)
        advanceUntilIdle()
        assertEquals(ExerciseUnit.BODYWEIGHT, vm.uiState.value.form.defaultUnit)
    }

    @Test
    fun `editing an exercise keeps its own unit whatever Settings says`() = runTest {
        env.settingsRepository.setDefaultWeightUnit(ExerciseUnit.KG)
        val vm = viewModel(exerciseId = benchId)
        advanceUntilIdle()
        assertEquals(ExerciseUnit.LB, vm.uiState.value.form.defaultUnit)
    }

    @Test
    fun `a name already in use is warned about but not refused`() = runTest {
        val vm = viewModel()
        vm.uiState.test {
            var state = awaitItem()
            while (state.isLoading) state = awaitItem()

            vm.onNameChange("Bench Press")
            advanceUntilIdle()
            while (state.form.duplicateNameWarning == null) state = awaitItem()
            assertNotNull(state.form.duplicateNameWarning)
            // Advisory only: the name field is not in an error state.
            assertNull(state.form.nameError)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the duplicate check is case-insensitive`() = runTest {
        val vm = viewModel()
        vm.uiState.test {
            var state = awaitItem()
            while (state.isLoading) state = awaitItem()
            vm.onNameChange("bench press")
            advanceUntilIdle()
            while (state.form.duplicateNameWarning == null) state = awaitItem()
            assertNotNull(state.form.duplicateNameWarning)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `editing an exercise does not warn about its own name`() = runTest {
        val vm = viewModel(exerciseId = benchId)
        vm.uiState.test {
            var state = awaitItem()
            while (state.form.name != "Bench Press") state = awaitItem()

            vm.onNameChange("Bench Press")
            advanceUntilIdle()
            assertNull(state.form.duplicateNameWarning)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a free name produces no warning`() = runTest {
        val vm = viewModel()
        vm.uiState.test {
            var state = awaitItem()
            while (state.isLoading) state = awaitItem()
            vm.onNameChange("Incline Press")
            advanceUntilIdle()
            assertNull(state.form.duplicateNameWarning)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a blank name is refused with an error, not a warning`() = runTest {
        val vm = viewModel()
        vm.uiState.test {
            var state = awaitItem()
            while (state.isLoading) state = awaitItem()

            vm.onNameChange("   ")
            vm.save()
            while (state.form.nameError == null) state = awaitItem()
            assertNotNull(state.form.nameError)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a malformed how-to URL reports a localised message`() = runTest {
        val vm = viewModel()
        vm.uiState.test {
            var state = awaitItem()
            while (state.isLoading) state = awaitItem()

            vm.onNameChange("Dip")
            vm.onHowToUrlChange("not a url at all")
            vm.save()
            while (state.form.howToUrlError == null) state = awaitItem()
            // The domain's own message is hardcoded English; this must come from resources.
            assertEquals("Enter a valid http or https link.", state.form.howToUrlError)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `opening a deleted exercise reports an exercise problem, not a program one`() = runTest {
        val vm = viewModel(exerciseId = 9_999L)
        vm.uiState.test {
            var state = awaitItem()
            while (state.errorMessage == null) state = awaitItem()
            assertTrue(state.errorMessage!!.contains("exercise"))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a valid exercise saves and reports its id`() = runTest {
        val vm = viewModel()
        vm.events.test {
            vm.onNameChange("Cable Fly")
            vm.onCategoryChange(ExerciseCategory.PUSH)
            vm.onUnitChange(ExerciseUnit.LB)
            vm.onHowToUrlChange("example.com/fly")
            vm.onPointersChange("Slight bend in the elbows.")
            vm.save()
            advanceUntilIdle()
            val event = awaitItem()
            assertTrue(event is ExerciseEditorEvent.Saved)
            cancelAndIgnoreRemainingEvents()
        }
        val saved = env.exerciseRepository.findByName("Cable Fly")
        assertNotNull(saved)
        // A scheme-less URL is normalised rather than rejected.
        assertEquals("https://example.com/fly", saved?.howToUrl)
        assertEquals("Slight bend in the elbows.", saved?.defaultPointers)
    }
}
