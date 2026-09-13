package dev.happyc0der.forgelog.ui.programs

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import dev.happyc0der.forgelog.data.local.dayEntity
import dev.happyc0der.forgelog.data.local.programEntity
import dev.happyc0der.forgelog.testing.MainDispatcherRule
import dev.happyc0der.forgelog.testing.TestEnvironment
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ProgramDetailViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var env: TestEnvironment
    private val created = mutableListOf<ProgramDetailViewModel>()
    private var programId = 0L

    @Before
    fun setUp() = runTest {
        env = TestEnvironment(mainDispatcherRule.dispatcher)
        programId = env.database.programDao().insertProgram(programEntity(name = "PPL"))
        env.database.programDao().insertDay(dayEntity(programId = programId, name = "Push Day"))
    }

    @After
    fun tearDown() {
        created.forEach { it.viewModelScope.cancel() }
        created.clear()
        env.tearDown()
    }

    private fun viewModel() = ProgramDetailViewModel(
        savedStateHandle = SavedStateHandle(mapOf("programId" to programId)),
        application = ApplicationProvider.getApplicationContext<Application>(),
        programRepository = env.programRepository,
    ).also(created::add)

    private suspend fun dayNames(): List<String> =
        env.programRepository.observeDays(programId).first().map { it.name }

    /*
     * Creating a day is guarded against a second tap, the way the exercise editor is.
     *
     * The dialog's confirm handler calls the ViewModel and then closes the dialog, both synchronously,
     * so there is no enabled check to rely on and two taps can land in one frame before any
     * recomposition removes it. That made two days, not one -- and since creating a day also asks the
     * screen to open it, two navigation events as well, so the user landed on one day with an
     * identical one left behind it.
     */

    @Test
    fun `tapping create twice in the same frame makes one day`() = runTest {
        val vm = viewModel()

        vm.createDay("Pull Day")
        vm.createDay("Pull Day")
        advanceUntilIdle()

        assertEquals(listOf("Push Day", "Pull Day"), dayNames())
    }

    @Test
    fun `a second day can be created once the first is done`() = runTest {
        val vm = viewModel()

        vm.createDay("Pull Day")
        advanceUntilIdle()
        vm.createDay("Leg Day")
        advanceUntilIdle()

        assertEquals(listOf("Push Day", "Pull Day", "Leg Day"), dayNames())
    }

    /** Duplicating is the same button shape, from a row's menu rather than a dialog. */
    @Test
    fun `tapping duplicate twice in the same frame makes one copy`() = runTest {
        val vm = viewModel()
        val dayId = env.programRepository.observeDays(programId).first().first().id

        vm.duplicateDay(dayId)
        vm.duplicateDay(dayId)
        advanceUntilIdle()

        assertEquals(2, dayNames().size)
    }

    /**
     * But duplicating a *different* day is not a double tap and must not be dropped.
     *
     * This is why the guard is by id rather than a single in-flight job: two menu taps on two rows,
     * close together, are two things the user asked for.
     */
    @Test
    fun `duplicating two different days in the same frame copies both`() = runTest {
        env.database.programDao().insertDay(dayEntity(programId = programId, name = "Pull Day"))
        val vm = viewModel()
        val days = env.programRepository.observeDays(programId).first()
        assertEquals(2, days.size)

        vm.duplicateDay(days[0].id)
        vm.duplicateDay(days[1].id)
        advanceUntilIdle()

        assertEquals("one of the two duplicates was dropped", 4, dayNames().size)
    }
}
