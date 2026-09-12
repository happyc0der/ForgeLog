package dev.happyc0der.forgelog.ui.exercise

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import dev.happyc0der.forgelog.data.local.exerciseEntity
import dev.happyc0der.forgelog.domain.model.Exercise
import dev.happyc0der.forgelog.domain.model.ExerciseCategory
import dev.happyc0der.forgelog.domain.model.ExerciseUnit
import dev.happyc0der.forgelog.domain.repository.ExerciseRepository
import dev.happyc0der.forgelog.testing.MainDispatcherRule
import dev.happyc0der.forgelog.testing.TestEnvironment
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

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

    private fun viewModel(
        exerciseId: Long = -1L,
        repository: ExerciseRepository = env.exerciseRepository,
    ) = ExerciseEditorViewModel(
        savedStateHandle = SavedStateHandle(mapOf("exerciseId" to exerciseId)),
        application = ApplicationProvider.getApplicationContext<Application>(),
        exerciseRepository = repository,
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

    /** Writes fail; everything else is the real repository. */
    private fun failingWrites() = object : ExerciseRepository by env.exerciseRepository {
        override suspend fun upsert(exercise: Exercise): Long = throw IOException("Disk full")
    }

    @Test
    fun `a failed save keeps the form and says so`() = runTest {
        val vm = viewModel(repository = failingWrites())
        vm.events.test {
            vm.onNameChange("Cable Fly")
            vm.save()
            assertEquals(ExerciseEditorEvent.Message("Disk full"), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        advanceUntilIdle()
        val state = vm.uiState.value
        // Still the form, as typed, and ready to try again.
        assertNull(state.errorMessage)
        assertEquals("Cable Fly", state.form.name)
        assertFalse(state.isSaving)
    }

    @Test
    fun `a failed load can be retried`() = runTest {
        var failing = true
        val flaky = object : ExerciseRepository by env.exerciseRepository {
            override suspend fun getExercise(id: Long): Exercise? =
                if (failing) throw IOException("Disk busy") else env.exerciseRepository.getExercise(id)
        }
        val vm = viewModel(exerciseId = benchId, repository = flaky)
        advanceUntilIdle()
        assertEquals("Disk busy", vm.uiState.value.errorMessage)

        failing = false
        vm.retry()
        advanceUntilIdle()
        assertNull(vm.uiState.value.errorMessage)
        assertEquals("Bench Press", vm.uiState.value.form.name)
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

    /*
     * Saving is guarded against a second tap, and the guard has to hold until the screen is gone,
     * not until the write finishes. It used to reopen in between, so an ordinary double tap on a
     * save that felt slow put the same lift in the library twice -- and then history was split
     * across the two of them. Reproduced on a device before this was fixed.
     */

    @Test
    fun `tapping save twice creates the exercise once`() = runTest {
        val vm = viewModel()
        vm.onNameChange("Cable Fly")

        vm.save()
        advanceUntilIdle()
        // The write is done and the screen has not left yet: the window the second tap landed in.
        vm.save()
        advanceUntilIdle()

        assertEquals(
            listOf("Bench Press", "Cable Fly"),
            env.exerciseRepository.observeExercises(includeArchived = true).first().map { it.name },
        )
    }

    @Test
    fun `tapping save twice in the same frame creates the exercise once`() = runTest {
        val vm = viewModel()
        vm.onNameChange("Cable Fly")

        vm.save()
        vm.save()
        advanceUntilIdle()

        assertEquals(
            listOf("Bench Press", "Cable Fly"),
            env.exerciseRepository.observeExercises(includeArchived = true).first().map { it.name },
        )
    }

    @Test
    fun `a save that failed can be tried again`() = runTest {
        val vm = viewModel(repository = FailingOnceExerciseRepository(env.exerciseRepository))
        vm.onNameChange("Cable Fly")

        vm.save()
        advanceUntilIdle()
        // The form is still in front of the user, so the button has to work.
        vm.save()
        advanceUntilIdle()

        assertNotNull(env.exerciseRepository.findByName("Cable Fly"))
    }
}

/** Fails the first upsert and then behaves, to check the guard reopens after a failure. */
private class FailingOnceExerciseRepository(
    private val delegate: ExerciseRepository,
) : ExerciseRepository by delegate {
    private var failed = false

    override suspend fun upsert(exercise: dev.happyc0der.forgelog.domain.model.Exercise): Long {
        if (!failed) {
            failed = true
            throw IllegalStateException("disk full")
        }
        return delegate.upsert(exercise)
    }
}
