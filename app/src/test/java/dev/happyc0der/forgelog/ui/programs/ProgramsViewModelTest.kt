package dev.happyc0der.forgelog.ui.programs

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import dev.happyc0der.forgelog.data.local.dayEntity
import dev.happyc0der.forgelog.data.local.exerciseEntity
import dev.happyc0der.forgelog.data.local.programEntity
import dev.happyc0der.forgelog.data.local.sessionEntity
import dev.happyc0der.forgelog.domain.model.SessionStatus
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
class ProgramsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var env: TestEnvironment
    private val created = mutableListOf<ProgramsViewModel>()

    @Before
    fun setUp() {
        env = TestEnvironment(mainDispatcherRule.dispatcher)
        env.time.now = 10_000L
    }

    @After
    fun tearDown() {
        created.forEach { it.viewModelScope.cancel() }
        created.clear()
        env.tearDown()
    }

    private fun viewModel() = ProgramsViewModel(
        application = ApplicationProvider.getApplicationContext<Application>(),
        programRepository = env.programRepository,
    ).also(created::add)

    @Test
    fun `a program summary reports days, completions and last performed`() = runTest {
        val programDao = env.database.programDao()
        val programId = programDao.insertProgram(programEntity(name = "PPL"))
        programDao.insertDay(dayEntity(programId = programId, name = "Push"))
        programDao.insertDay(dayEntity(programId = programId, name = "Pull", dayOrder = 1))
        env.database.workoutSessionDao().insertSession(
            sessionEntity(sessionName = "Push", programId = programId, startedAt = 1_000L, completedAt = 8_000L),
        )

        val vm = viewModel()
        vm.uiState.test {
            var state = awaitItem()
            while (state.programs.isEmpty()) state = awaitItem()
            val summary = state.programs.single()
            assertEquals(2, summary.dayCount)
            assertEquals(1, summary.completedSessionCount)
            assertEquals(8_000L, summary.lastPerformedAt)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a program never performed reports no date rather than a wrong one`() = runTest {
        env.database.programDao().insertProgram(programEntity(name = "Fresh"))
        val vm = viewModel()
        vm.uiState.test {
            var state = awaitItem()
            while (state.programs.isEmpty()) state = awaitItem()
            assertNull(state.programs.single().lastPerformedAt)
            assertEquals(0, state.programs.single().completedSessionCount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `an abandoned session is not a completion`() = runTest {
        val programId = env.database.programDao().insertProgram(programEntity(name = "PPL"))
        env.database.workoutSessionDao().insertSession(
            sessionEntity(
                sessionName = "Quit",
                programId = programId,
                startedAt = 1_000L,
                completedAt = 2_000L,
                status = SessionStatus.ABANDONED,
            ),
        )
        val vm = viewModel()
        vm.uiState.test {
            var state = awaitItem()
            while (state.programs.isEmpty()) state = awaitItem()
            assertEquals(0, state.programs.single().completedSessionCount)
            assertNull(state.programs.single().lastPerformedAt)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `an all-archived library still lets the archived filter be turned on`() = runTest {
        val programId = env.database.programDao().insertProgram(programEntity(name = "Old"))
        env.programRepository.setArchived(programId, true)
        advanceUntilIdle()

        val vm = viewModel()
        vm.uiState.test {
            var state = awaitItem()
            while (state.isLoading) state = awaitItem()
            // Nothing visible, which is what used to hide the only control that could reveal it.
            assertTrue(state.programs.isEmpty())
            assertTrue(!state.includeArchived)

            vm.onToggleArchived()
            while (state.programs.isEmpty()) state = awaitItem()
            assertEquals("Old", state.programs.single().program.name)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a chosen colour is stored on create and kept on rename`() = runTest {
        val vm = viewModel()
        vm.createProgram("Bright", "", "#22C55E")
        advanceUntilIdle()

        val created = env.programRepository.observeProgramSummaries(includeArchived = true)
        vm.uiState.test {
            var state = awaitItem()
            while (state.programs.isEmpty()) state = awaitItem()
            assertEquals("#22C55E", state.programs.single().program.color)

            vm.renameProgram(state.programs.single().program, "Brighter", "", "#EF4444")
            while (state.programs.single().program.color != "#EF4444") state = awaitItem()
            assertEquals("Brighter", state.programs.single().program.name)
            cancelAndIgnoreRemainingEvents()
        }
        assertNotNull(created)
    }

    @Test
    fun `duplicating a program that was deleted reports a message instead of crashing`() = runTest {
        val vm = viewModel()
        vm.events.test {
            vm.duplicate(9_999L)
            advanceUntilIdle()
            assertTrue(awaitItem() is ProgramsEvent.Message)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a failing database surfaces a reachable error state`() = runTest {
        env.breakDatabase()
        val vm = viewModel()
        vm.uiState.test {
            var state = awaitItem()
            while (state.errorMessage == null) state = awaitItem()
            assertNotNull(state.errorMessage)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
