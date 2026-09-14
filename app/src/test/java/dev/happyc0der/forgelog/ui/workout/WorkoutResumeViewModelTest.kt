package dev.happyc0der.forgelog.ui.workout

import androidx.lifecycle.viewModelScope
import app.cash.turbine.test
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
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The question asked once at launch: is there a workout to go back into?
 *
 * Small, and easy to get subtly wrong, which is why it is worth writing down. The gate is spent on
 * the first real answer — including the answer "no workout" — and that is the part that looks like a
 * mistake and is not. Spending it only when there *is* a session would leave the gate open for the
 * rest of the process, so the next workout the user starts would arrive here as a cold start too,
 * and be navigated to a second time on top of the navigation the start itself already does.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WorkoutResumeViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var env: TestEnvironment
    private val created = mutableListOf<WorkoutResumeViewModel>()

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

    private fun viewModel() =
        WorkoutResumeViewModel(workoutSessionRepository = env.sessionRepository).also(created::add)

    private suspend fun startWorkout(name: String): Long =
        env.database.workoutSessionDao().insertSession(
            sessionEntity(
                sessionName = name,
                startedAt = 1_000,
                completedAt = null,
                status = SessionStatus.IN_PROGRESS,
            ),
        )

    @Test
    fun `a workout left in progress is offered`() = runTest {
        val sessionId = startWorkout("PPL · Push Day")
        val vm = viewModel()

        vm.coldStart.test {
            var state = awaitItem()
            while (state !is ColdStartState.Ready) state = awaitItem()
            assertEquals(sessionId, (state as ColdStartState.Ready).session?.id)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `nothing in progress is a ready answer, not a missing one`() = runTest {
        val vm = viewModel()

        vm.coldStart.test {
            var state = awaitItem()
            while (state !is ColdStartState.Ready) state = awaitItem()
            assertNull((state as ColdStartState.Ready).session)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the offer is taken once and not again`() = runTest {
        startWorkout("PPL · Push Day")
        val vm = viewModel()
        advanceUntilIdle()
        val ready = vm.coldStart.value

        assertNotNull("the first ask should hand back the session", vm.consumeIfNeeded(ready))
        assertNull("the same cold start was offered twice", vm.consumeIfNeeded(ready))
    }

    /** Still loading is not an answer, so it must not spend the gate. */
    @Test
    fun `being asked before the answer arrives leaves the offer standing`() = runTest {
        startWorkout("PPL · Push Day")
        val vm = viewModel()

        assertNull(vm.consumeIfNeeded(ColdStartState.Loading))

        advanceUntilIdle()
        assertNotNull(
            "asking too early threw the resume away",
            vm.consumeIfNeeded(vm.coldStart.value),
        )
    }

    /**
     * The subtle one. Launching with nothing in progress spends the gate, so a workout started
     * afterwards is not mistaken for one to resume — the start does its own navigating, and this
     * would send the user to the logger a second time on top of it.
     */
    @Test
    fun `a workout started later is not offered as one to resume`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        // Launch answered "nothing in progress", which is an answer and spends the gate.
        assertNull(vm.consumeIfNeeded(vm.coldStart.value))

        startWorkout("PPL · Push Day")
        advanceUntilIdle()
        val nowRunning = vm.coldStart.value
        assertNotNull((nowRunning as ColdStartState.Ready).session)

        assertNull(
            "a workout started in this process was offered as a cold-start resume",
            vm.consumeIfNeeded(nowRunning),
        )
    }
}
