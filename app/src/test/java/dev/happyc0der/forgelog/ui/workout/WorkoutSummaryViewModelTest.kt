package dev.happyc0der.forgelog.ui.workout

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import dev.happyc0der.forgelog.data.local.exerciseEntity
import dev.happyc0der.forgelog.domain.analytics.RecordKind
import dev.happyc0der.forgelog.domain.model.Exercise
import dev.happyc0der.forgelog.domain.model.ExerciseCategory
import dev.happyc0der.forgelog.domain.model.ExerciseUnit
import dev.happyc0der.forgelog.domain.model.SessionStartExercise
import dev.happyc0der.forgelog.domain.model.SetType
import dev.happyc0der.forgelog.testing.MainDispatcherRule
import dev.happyc0der.forgelog.testing.TestEnvironment
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The completion summary is what a finished workout leads to, so it has to describe the session as
 * actually saved — it reads back from the database rather than being handed totals by the logger.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WorkoutSummaryViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var env: TestEnvironment
    private val created = mutableListOf<WorkoutSummaryViewModel>()
    private var benchId = 0L

    @Before
    fun setUp() = runTest {
        env = TestEnvironment(mainDispatcherRule.dispatcher)
        env.time.now = 1_000_000L
        benchId = env.database.exerciseDao().upsert(exerciseEntity(name = "Bench Press"))
    }

    @After
    fun tearDown() {
        created.forEach { it.viewModelScope.cancel() }
        created.clear()
        env.tearDown()
    }

    private fun viewModel(sessionId: Long) = WorkoutSummaryViewModel(
        savedStateHandle = SavedStateHandle(mapOf("sessionId" to sessionId)),
        application = ApplicationProvider.getApplicationContext<Application>(),
        workoutSessionRepository = env.sessionRepository,
        settingsRepository = env.settingsRepository,
    ).also(created::add)

    private fun bench() = Exercise(
        id = benchId,
        name = "Bench Press",
        category = ExerciseCategory.PUSH,
        defaultUnit = ExerciseUnit.LB,
        howToUrl = null,
        defaultPointers = null,
        isArchived = false,
        createdAt = 0L,
        updatedAt = 0L,
    )

    /** Logs one session of [weights] and finishes it, returning the session id. */
    private suspend fun loggedSession(
        weights: List<Double>,
        reps: Int = 5,
        startedAt: Long,
        setType: SetType = SetType.WORKING,
    ): Long {
        env.time.now = startedAt
        val sessionId = env.sessionRepository.startSession(
            programId = null,
            programDayId = null,
            sessionName = "Push",
            exercises = listOf(SessionStartExercise(exercise = bench())),
        )
        val sessionExerciseId = env.sessionRepository.getSessionDetail(sessionId)!!
            .exercises.single().exercise.id
        weights.forEachIndexed { index, weight ->
            env.sessionRepository.upsertSetLog(
                dev.happyc0der.forgelog.domain.model.SetLog(
                    sessionExerciseId = sessionExerciseId,
                    setNumber = index + 1,
                    setType = setType,
                    reps = reps,
                    weight = weight,
                    weightUnit = ExerciseUnit.LB,
                    completed = true,
                    completedAt = startedAt + (index + 1) * 60_000L,
                ),
            )
        }
        env.time.now = startedAt + 3_600_000L
        env.sessionRepository.completeSession(sessionId)
        return sessionId
    }

    @Test
    fun `the summary totals what was actually logged`() = runTest {
        val sessionId = loggedSession(weights = listOf(185.0, 185.0, 195.0), startedAt = 1_000_000L)

        viewModel(sessionId).uiState.test {
            val state = awaitUntil { it.summary != null }
            val summary = state.summary!!
            assertEquals(3, summary.totalSets)
            assertEquals(1, summary.exerciseCount)
            assertEquals(3_600_000L, summary.durationMs)
            // 5 × (185 + 185 + 195)
            assertEquals(2_825.0, summary.loadLb, 0.001)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `beating a previous session is reported as a record`() = runTest {
        loggedSession(weights = listOf(185.0), startedAt = 1_000_000L)
        val today = loggedSession(weights = listOf(205.0), startedAt = 90_000_000L)

        viewModel(today).uiState.test {
            val state = awaitUntil { it.summary != null }
            val heaviest = state.records.firstOrNull { it.kind == RecordKind.HEAVIEST_WEIGHT }
            assertNotNull("expected a heaviest-weight record, got ${state.records}", heaviest)
            assertEquals(205.0, heaviest!!.candidate.weightLb ?: 0.0, 0.001)
            assertEquals(185.0, heaviest.previousBest?.weightLb ?: 0.0, 0.001)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a session that beats nothing reports no records`() = runTest {
        loggedSession(weights = listOf(225.0), startedAt = 1_000_000L)
        val today = loggedSession(weights = listOf(185.0), startedAt = 90_000_000L)

        viewModel(today).uiState.test {
            val state = awaitUntil { it.summary != null }
            assertTrue("expected no records, got ${state.records}", state.records.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the session being summarised is not compared against itself`() = runTest {
        // The only session ever logged. Judged against a history that included it, its own sets
        // would tie every record and nothing would be reported.
        val only = loggedSession(weights = listOf(185.0), startedAt = 1_000_000L)

        viewModel(only).uiState.test {
            val state = awaitUntil { it.summary != null }
            assertTrue(state.records.any { it.kind == RecordKind.HEAVIEST_WEIGHT })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the breakdown names each exercise and its top set`() = runTest {
        val sessionId = loggedSession(weights = listOf(185.0, 205.0), startedAt = 1_000_000L)

        viewModel(sessionId).uiState.test {
            val state = awaitUntil { it.summary != null }
            val exercise = state.exercises.single()
            assertEquals("Bench Press", exercise.name)
            assertEquals(2, exercise.completedSets)
            // The heaviest set, not the first or the last.
            assertEquals("5 × 205 lb", exercise.topSetLabel)
            cancelAndIgnoreRemainingEvents()
        }
    }

    /** Logs one finished session of [sets], each built from its set number. */
    private suspend fun loggedSessionOf(
        startedAt: Long,
        sets: (sessionExerciseId: Long) -> List<dev.happyc0der.forgelog.domain.model.SetLog>,
    ): Long {
        env.time.now = startedAt
        val sessionId = env.sessionRepository.startSession(
            programId = null,
            programDayId = null,
            sessionName = "Core",
            exercises = listOf(SessionStartExercise(exercise = bench())),
        )
        val sessionExerciseId = env.sessionRepository.getSessionDetail(sessionId)!!
            .exercises.single().exercise.id
        sets(sessionExerciseId).forEach { env.sessionRepository.upsertSetLog(it) }
        env.time.now = startedAt + 3_600_000L
        env.sessionRepository.completeSession(sessionId)
        return sessionId
    }

    private fun unloadedSet(
        sessionExerciseId: Long,
        setNumber: Int,
        reps: Int? = null,
        durationSeconds: Int? = null,
    ) = dev.happyc0der.forgelog.domain.model.SetLog(
        sessionExerciseId = sessionExerciseId,
        setNumber = setNumber,
        reps = reps,
        durationSeconds = durationSeconds,
        weightUnit = ExerciseUnit.LB,
        completed = true,
        completedAt = 1_000_000L + setNumber * 60_000L,
    )

    /*
     * Found finishing a workout on the test device: Plank summarised as "—" and "—". The breakdown
     * knew only weight × reps, so every timed or bodyweight exercise said nothing at all.
     */
    @Test
    fun `a timed exercise is summarised by its longest hold and total time`() = runTest {
        val sessionId = loggedSessionOf(startedAt = 1_000_000L) { id ->
            listOf(
                unloadedSet(id, setNumber = 1, durationSeconds = 45),
                unloadedSet(id, setNumber = 2, durationSeconds = 60),
                unloadedSet(id, setNumber = 3, durationSeconds = 30),
            )
        }

        viewModel(sessionId).uiState.test {
            val exercise = awaitUntil { it.summary != null }.exercises.single()
            assertEquals("1m", exercise.topSetLabel)
            assertEquals(0.0, exercise.volumeLb, 0.0)
            assertEquals("2m 15s", exercise.unloadedTotalLabel)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `bodyweight work is summarised by its most reps and total reps`() = runTest {
        val sessionId = loggedSessionOf(startedAt = 1_000_000L) { id ->
            listOf(unloadedSet(id, setNumber = 1, reps = 12), unloadedSet(id, setNumber = 2, reps = 9))
        }

        viewModel(sessionId).uiState.test {
            val exercise = awaitUntil { it.summary != null }.exercises.single()
            assertEquals("12 reps", exercise.topSetLabel)
            assertEquals("21 reps", exercise.unloadedTotalLabel)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the heaviest set is judged in one unit`() = runTest {
        val sessionId = loggedSessionOf(startedAt = 1_000_000L) { id ->
            listOf(
                unloadedSet(id, setNumber = 1, reps = 5).copy(weight = 200.0, weightUnit = ExerciseUnit.LB),
                unloadedSet(id, setNumber = 2, reps = 5).copy(weight = 100.0, weightUnit = ExerciseUnit.KG),
            )
        }

        viewModel(sessionId).uiState.test {
            val exercise = awaitUntil { it.summary != null }.exercises.single()
            // 100 kg is about 220 lb, so it is the heavier set despite the smaller number.
            assertEquals("5 × 100 kg", exercise.topSetLabel)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `warmups do not claim records`() = runTest {
        val sessionId = loggedSession(
            weights = listOf(315.0),
            startedAt = 1_000_000L,
            setType = SetType.WARMUP,
        )

        viewModel(sessionId).uiState.test {
            val state = awaitUntil { it.summary != null }
            assertTrue(state.records.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a missing session shows an error rather than an empty summary`() = runTest {
        viewModel(9_999L).uiState.test {
            val state = awaitUntil { !it.isLoading }
            assertNotNull(state.errorMessage)
            cancelAndIgnoreRemainingEvents()
        }
    }

    /*
     * There is deliberately no "database failure" test here.
     *
     * TestEnvironment.breakDatabase closes the database, and a Room flow built on a closed database
     * completes silently — it neither emits nor throws — so `catch` never runs and this ViewModel's
     * reportErrors guard cannot be provoked through that seam. Other ViewModels' equivalent tests
     * work because their DAO call sits inside a flatMapLatest that does throw. Writing one here
     * would assert something the setup cannot actually produce.
     */
}

private suspend fun <T> app.cash.turbine.ReceiveTurbine<T>.awaitUntil(predicate: (T) -> Boolean): T {
    repeat(40) {
        val item = awaitItem()
        if (predicate(item)) return item
    }
    error("no emission matched after 40 items")
}
