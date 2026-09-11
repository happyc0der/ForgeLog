package dev.happyc0der.forgelog.ui.programs

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import dev.happyc0der.forgelog.data.local.dayEntity
import dev.happyc0der.forgelog.data.local.exerciseEntity
import dev.happyc0der.forgelog.data.local.programEntity
import dev.happyc0der.forgelog.data.local.programExerciseEntity
import dev.happyc0der.forgelog.testing.MainDispatcherRule
import dev.happyc0der.forgelog.testing.TestEnvironment
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Reordering used to write to the database on every intermediate step of a drag, each write computed
 * from a snapshot the previous one had already invalidated. These pin the new contract: dragging
 * changes a draft, and exactly one write happens on drop.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ReorderPersistenceTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var env: TestEnvironment
    private val createdDetail = mutableListOf<ProgramDetailViewModel>()
    private val createdBuilder = mutableListOf<ProgramDayBuilderViewModel>()

    private var programId = 0L
    private var dayIds = listOf<Long>()

    @Before
    fun setUp() = runTest {
        env = TestEnvironment(mainDispatcherRule.dispatcher)
        val dao = env.database.programDao()
        programId = dao.insertProgram(programEntity(name = "PPL"))
        dayIds = listOf("Push", "Pull", "Legs", "Upper", "Lower").mapIndexed { index, name ->
            dao.insertDay(dayEntity(programId = programId, name = name, dayOrder = index))
        }
    }

    @After
    fun tearDown() {
        createdDetail.forEach { it.viewModelScope.cancel() }
        createdBuilder.forEach { it.viewModelScope.cancel() }
        createdDetail.clear()
        createdBuilder.clear()
        env.tearDown()
    }

    private fun detailViewModel() = ProgramDetailViewModel(
        savedStateHandle = SavedStateHandle(mapOf("programId" to programId)),
        application = ApplicationProvider.getApplicationContext<Application>(),
        programRepository = env.programRepository,
    ).also(createdDetail::add)

    private suspend fun storedDayNames(): List<String> =
        env.programRepository.getProgramDetail(programId)!!.days.map { it.day.name }

    @Test
    fun `dragging does not touch the database until the drop`() = runTest {
        val vm = detailViewModel()
        vm.uiState.test {
            var state = awaitItem()
            while (state.days.size != 5) state = awaitItem()

            // A drag across three positions, one step at a time, as the gesture reports it.
            vm.moveDay(0, 1)
            vm.moveDay(1, 2)
            vm.moveDay(2, 3)
            advanceUntilIdle()

            // Nothing committed yet.
            assertEquals(listOf("Push", "Pull", "Legs", "Upper", "Lower"), storedDayNames())
            // But the screen already shows the new order.
            while (state.draftOrder == null) state = awaitItem()
            assertEquals(
                listOf("Pull", "Legs", "Upper", "Push", "Lower"),
                state.days.map { it.name }.toList(),
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the drop commits the final order exactly once`() = runTest {
        val vm = detailViewModel()
        vm.uiState.test {
            var state = awaitItem()
            while (state.days.size != 5) state = awaitItem()

            vm.moveDay(0, 1)
            vm.moveDay(1, 2)
            vm.moveDay(2, 3)
            vm.persistDayOrder()
            advanceUntilIdle()

            assertEquals(listOf("Pull", "Legs", "Upper", "Push", "Lower"), storedDayNames())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the draft clears after the drop so the stored order takes over`() = runTest {
        val vm = detailViewModel()
        vm.uiState.test {
            var state = awaitItem()
            while (state.days.size != 5) state = awaitItem()

            vm.moveDay(0, 4)
            vm.persistDayOrder()
            advanceUntilIdle()
            // Wait for the committed order *and* the draft being released.
            while (state.draftOrder != null || state.days.firstOrNull()?.name != "Pull") {
                state = awaitItem()
            }

            assertEquals(listOf("Pull", "Legs", "Upper", "Lower", "Push"), state.days.map { it.name })
            assertNull(state.draftOrder)
            cancelAndIgnoreRemainingEvents()
        }
    }

    /**
     * A refused write must take the drag back with it. The draft used to survive, so the list went
     * on showing an order the database had rejected -- next to the message saying it had.
     */
    @Test
    fun `a refused reorder puts the list back`() = runTest {
        val vm = ProgramDetailViewModel(
            savedStateHandle = SavedStateHandle(mapOf("programId" to programId)),
            application = ApplicationProvider.getApplicationContext<Application>(),
            programRepository = RefusingReorder(env.programRepository),
        ).also(createdDetail::add)

        vm.uiState.test {
            var state = awaitItem()
            while (state.days.size != 5) state = awaitItem()

            vm.moveDay(0, 4)
            // The draft has to be in force before the refusal, or the wait below proves nothing.
            while (state.draftOrder == null) state = awaitItem()

            vm.persistDayOrder()
            advanceUntilIdle()
            while (state.draftOrder != null) state = awaitItem()

            assertNull(state.draftOrder)
            assertEquals(listOf("Push", "Pull", "Legs", "Upper", "Lower"), state.days.map { it.name })
            assertEquals(listOf("Push", "Pull", "Legs", "Upper", "Lower"), storedDayNames())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `persisting without a drag is a no-op`() = runTest {
        val vm = detailViewModel()
        vm.uiState.test {
            var state = awaitItem()
            while (state.days.size != 5) state = awaitItem()

            vm.persistDayOrder()
            advanceUntilIdle()

            assertEquals(listOf("Push", "Pull", "Legs", "Upper", "Lower"), storedDayNames())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `an out-of-range move is ignored rather than crashing`() = runTest {
        val vm = detailViewModel()
        vm.uiState.test {
            var state = awaitItem()
            while (state.days.size != 5) state = awaitItem()

            vm.moveDay(0, 99)
            vm.moveDay(-1, 0)
            vm.persistDayOrder()
            advanceUntilIdle()

            assertEquals(listOf("Push", "Pull", "Legs", "Upper", "Lower"), storedDayNames())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `exercise reordering follows the same contract`() = runTest {
        val exerciseDao = env.database.exerciseDao()
        val programDao = env.database.programDao()
        val dayId = dayIds.first()
        listOf("Bench", "Incline", "Dip").forEachIndexed { index, name ->
            val exerciseId = exerciseDao.upsert(exerciseEntity(name = name))
            programDao.insertProgramExercise(
                programExerciseEntity(programDayId = dayId, exerciseId = exerciseId, exerciseOrder = index),
            )
        }

        val vm = ProgramDayBuilderViewModel(
            savedStateHandle = SavedStateHandle(mapOf("dayId" to dayId)),
            application = ApplicationProvider.getApplicationContext<Application>(),
            programRepository = env.programRepository,
            exerciseRepository = env.exerciseRepository,
            settingsRepository = env.settingsRepository,
        ).also(createdBuilder::add)

        vm.uiState.test {
            var state = awaitItem()
            while (state.exercises.size != 3) state = awaitItem()

            vm.moveExercise(0, 2)
            advanceUntilIdle()
            // Uncommitted: storage still has the original order.
            assertEquals(
                listOf("Bench", "Incline", "Dip"),
                env.programRepository.getDayDetail(dayId)!!.exercises.map { it.exercise.name },
            )

            vm.persistExerciseOrder()
            advanceUntilIdle()
            assertEquals(
                listOf("Incline", "Dip", "Bench"),
                env.programRepository.getDayDetail(dayId)!!.exercises.map { it.exercise.name },
            )
            cancelAndIgnoreRemainingEvents()
        }
    }
}

/** A repository that refuses to reorder, standing in for a write the database will not take. */
private class RefusingReorder(
    private val delegate: dev.happyc0der.forgelog.domain.repository.ProgramRepository,
) : dev.happyc0der.forgelog.domain.repository.ProgramRepository by delegate {
    override suspend fun reorderDays(orderedDayIds: List<Long>): Unit = throw java.io.IOException("disk full")
}
