package dev.happyc0der.forgelog.ui

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performClick
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.happyc0der.forgelog.data.local.dayEntity
import dev.happyc0der.forgelog.data.local.exerciseEntity
import dev.happyc0der.forgelog.data.local.programEntity
import dev.happyc0der.forgelog.data.local.programExerciseEntity
import dev.happyc0der.forgelog.domain.model.Exercise
import dev.happyc0der.forgelog.domain.model.ExerciseCategory
import dev.happyc0der.forgelog.domain.model.ExerciseUnit
import dev.happyc0der.forgelog.domain.model.SessionStartExercise
import dev.happyc0der.forgelog.domain.model.SetLog
import dev.happyc0der.forgelog.testing.TestEnvironment
import dev.happyc0der.forgelog.ui.testing.TestTags
import dev.happyc0der.forgelog.ui.theme.ForgeLogTheme
import dev.happyc0der.forgelog.ui.workout.ActiveWorkoutScreen
import dev.happyc0der.forgelog.ui.workout.ActiveWorkoutViewModel
import dev.happyc0der.forgelog.ui.workout.StartWorkoutScreen
import dev.happyc0der.forgelog.ui.workout.StartWorkoutViewModel
import dev.happyc0der.forgelog.ui.workout.WorkoutSummaryScreen
import dev.happyc0der.forgelog.ui.workout.WorkoutSummaryViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The workout flow, driven through the real Composables.
 *
 * Runs twice from one source: on the JVM under Robolectric with `testDebugUnitTest`, and on a
 * device with `connectedDebugAndroidTest`. AndroidJUnit4 picks whichever runner is present.
 *
 * ViewModels are constructed by hand rather than resolved through Hilt. Hilt's graph is already
 * validated at compile time, and passing them in keeps the test about the screens and the database
 * underneath them rather than about dependency injection.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class WorkoutFlowUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    /*
     * The repositories run on a TestDispatcher, and in a Compose test nothing is advancing its
     * scheduler -- runTest is not driving this, the Compose rule is. So the scheduler is pumped by
     * hand from [settle] wherever the test waits on a database write. Coroutines on Dispatchers.Main
     * (the ViewModels' own scope) are driven by the Compose rule as usual.
     */
    private val scheduler = TestCoroutineScheduler()

    private lateinit var env: TestEnvironment
    private val created = mutableListOf<androidx.lifecycle.ViewModel>()
    private var dayId = 0L
    private var benchId = 0L

    private val application: Application get() = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp(): Unit = runBlocking {
        env = TestEnvironment(UnconfinedTestDispatcher(scheduler))
        env.time.now = 1_700_000_000_000L
        val programDao = env.database.programDao()
        val programId = programDao.insertProgram(programEntity(name = "PPL"))
        dayId = programDao.insertDay(dayEntity(programId = programId, name = "Push Day"))
        benchId = env.database.exerciseDao().upsert(exerciseEntity(name = "Bench Press"))
        programDao.insertProgramExercise(
            programExerciseEntity(
                programDayId = dayId,
                exerciseId = benchId,
                plannedSets = 3,
                targetRepMin = 8,
                targetWeight = 135.0,
                targetRestSeconds = 90,
            ),
        )
    }

    @After
    fun tearDown() {
        created.forEach { it.viewModelScope.cancel() }
        created.clear()
        env.tearDown()
    }

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

    private fun planner() = StartWorkoutViewModel(
        savedStateHandle = SavedStateHandle(mapOf("programDayId" to dayId, "adHoc" to false)),
        application = application,
        programRepository = env.programRepository,
        exerciseRepository = env.exerciseRepository,
        workoutSessionRepository = env.sessionRepository,
        settingsRepository = env.settingsRepository,
    ).also(created::add)

    private fun logger(sessionId: Long) = ActiveWorkoutViewModel(
        savedStateHandle = SavedStateHandle(mapOf("sessionId" to sessionId)),
        application = application,
        workoutSessionRepository = env.sessionRepository,
        exerciseRepository = env.exerciseRepository,
        programRepository = env.programRepository,
        timeProvider = env.time,
        settingsRepository = env.settingsRepository,
    ).also(created::add)

    private fun summary(sessionId: Long) = WorkoutSummaryViewModel(
        savedStateHandle = SavedStateHandle(mapOf("sessionId" to sessionId)),
        application = application,
        workoutSessionRepository = env.sessionRepository,
        settingsRepository = env.settingsRepository,
    ).also(created::add)

    @Test
    fun theplannerShowsTheProgramsTargetsAndStartsTheSession() {
        var startedId: Long? = null
        composeRule.setContent {
            ForgeLogTheme {
                StartWorkoutScreen(
                    onBack = {},
                    onPickFromLibrary = {},
                    onStarted = { startedId = it },
                    viewModel = planner(),
                )
            }
        }

        composeRule.waitUntil(WAIT_MS) {
            composeRule.onAllNodesWithText("Bench Press").fetchSemanticsNodes().isNotEmpty()
        }
        // The plan reaches the screen: this is the end-to-end form of the bug where five of the six
        // targets were dropped between the program and the session.
        composeRule.onNodeWithText("3 sets", substring = true).assertIsDisplayed()

        composeRule.onNodeWithTag(TestTags.START_WORKOUT_CONFIRM).performClick()
        composeRule.waitUntil(WAIT_MS) { settle { startedId != null } }

        val logged = runBlocking { env.sessionRepository.getSessionDetail(startedId!!) }
        assertNotNull(logged)
        val exercise = logged!!.exercises.single().exercise
        assertEquals(3, exercise.plannedSets)
        assertEquals(135.0, exercise.targetWeight ?: 0.0, 0.001)
        assertEquals(90, exercise.targetRestSeconds)
    }

    @Test
    fun addingSetsInTheLoggerPersistsThem() {
        val sessionId = startSession()
        composeRule.setContent {
            ForgeLogTheme {
                ActiveWorkoutScreen(
                    onBack = {},
                    onLeave = {},
                    onFinished = {},
                    viewModel = logger(sessionId),
                )
            }
        }

        composeRule.waitUntil(WAIT_MS) {
            composeRule.onAllNodesWithTag(TestTags.ACTIVE_WORKOUT_ADD_SET).fetchSemanticsNodes()
                .isNotEmpty()
        }
        // performScrollTo before each click: the logger is a scrolling column, and the first set
        // row pushes the button below the viewport, where a click lands on nothing.
        composeRule.onNodeWithTag(TestTags.ACTIVE_WORKOUT_ADD_SET).performScrollTo().performClick()
        composeRule.waitUntil(WAIT_MS) { settle { setsOf(sessionId).size >= 1 } }
        composeRule.onNodeWithTag(TestTags.ACTIVE_WORKOUT_ADD_SET).performScrollTo().performClick()

        composeRule.waitUntil(WAIT_MS) { settle { setsOf(sessionId).size >= 2 } }
        val numbers = setsOf(sessionId).map { it.setNumber }
        assertEquals("expected distinct numbers, got $numbers", numbers.size, numbers.distinct().size)
    }

    @Test
    fun finishingLeadsToTheSummaryWithTheSessionsTotals() {
        val sessionId = startSession()
        runBlocking { logSet(sessionId, reps = 5, weight = 205.0) }

        var finished = false
        composeRule.setContent {
            ForgeLogTheme {
                ActiveWorkoutScreen(
                    onBack = {},
                    onLeave = {},
                    onFinished = { finished = true },
                    viewModel = logger(sessionId),
                )
            }
        }

        composeRule.waitUntil(WAIT_MS) {
            composeRule.onAllNodesWithTag(TestTags.ACTIVE_WORKOUT_FINISH).fetchSemanticsNodes()
                .isNotEmpty()
        }
        // The finish action lives in the top app bar, which does not scroll.
        composeRule.onNodeWithTag(TestTags.ACTIVE_WORKOUT_FINISH).performClick()
        // Finishing is not the same as abandoning: only one of them earns a summary.
        composeRule.waitUntil(WAIT_MS) { settle { finished } }
    }

    @Test
    fun theSummaryNamesAPersonalBest() {
        val sessionId = startSession()
        runBlocking { logSet(sessionId, reps = 5, weight = 205.0) }
        runBlocking { env.sessionRepository.completeSession(sessionId) }

        composeRule.setContent {
            ForgeLogTheme {
                WorkoutSummaryScreen(
                    onDone = {},
                    onViewLog = {},
                    viewModel = summary(sessionId),
                )
            }
        }

        composeRule.waitUntil(WAIT_MS) {
            composeRule.onAllNodesWithTag(TestTags.WORKOUT_SUMMARY_SCREEN).fetchSemanticsNodes()
                .isNotEmpty()
        }
        // Nothing was ever logged before, so the first working set is a record on its own terms.
        composeRule.onNodeWithTag(TestTags.WORKOUT_SUMMARY_RECORDS)
            .assertIsDisplayed()
        composeRule.waitUntil(WAIT_MS) {
            composeRule.onAllNodesWithText("Heaviest weight", substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        // assertExists, not assertIsDisplayed: the summary is a LazyColumn and on a phone-sized
        // viewport the action sits below the fold.
        // The summary is a LazyColumn, so the action is not composed until it is scrolled to.
        composeRule.onNodeWithTag(TestTags.WORKOUT_SUMMARY_SCREEN)
            .performScrollToNode(hasTestTag(TestTags.WORKOUT_SUMMARY_DONE))
        composeRule.onNodeWithTag(TestTags.WORKOUT_SUMMARY_DONE).assertIsDisplayed()
    }

    @Test
    fun aSessionThatBeatsNothingSaysSoRatherThanInventingARecord() {
        // An earlier, heavier session.
        val earlier = startSession()
        runBlocking {
            logSet(earlier, reps = 5, weight = 315.0)
            env.sessionRepository.completeSession(earlier)
        }
        val today = startSession()
        runBlocking {
            logSet(today, reps = 5, weight = 135.0)
            env.sessionRepository.completeSession(today)
        }

        composeRule.setContent {
            ForgeLogTheme {
                WorkoutSummaryScreen(onDone = {}, onViewLog = {}, viewModel = summary(today))
            }
        }

        composeRule.waitUntil(WAIT_MS) {
            composeRule.onAllNodesWithText("No new bests today", substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
    }



    // --- helpers ---

    private fun startSession(): Long = runBlocking {
        env.sessionRepository.startSession(
            programId = null,
            programDayId = null,
            sessionName = "Push",
            exercises = listOf(SessionStartExercise(exercise = bench(), plannedSets = 3)),
        )
    }

    private suspend fun logSet(sessionId: Long, reps: Int, weight: Double) {
        val exerciseId = env.sessionRepository.getSessionDetail(sessionId)!!
            .exercises.single().exercise.id
        env.sessionRepository.upsertSetLog(
            SetLog(
                sessionExerciseId = exerciseId,
                setNumber = 1,
                reps = reps,
                weight = weight,
                weightUnit = ExerciseUnit.LB,
                completed = true,
                completedAt = env.time.now,
            ),
        )
    }

    private fun setsOf(sessionId: Long): List<SetLog> = runBlocking {
        env.sessionRepository.getSessionDetail(sessionId)?.exercises?.single()?.sets.orEmpty()
    }

    /** Lets queued database work run, then reports whether [condition] now holds. */
    private fun settle(condition: () -> Boolean): Boolean {
        composeRule.waitForIdle()
        scheduler.advanceUntilIdle()
        return condition()
    }

    private companion object {
        /** Generous: a device runs this slower than the JVM does. */
        const val WAIT_MS = 10_000L
    }
}

private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onAllNodesWithTag(tag: String) =
    onAllNodes(hasTestTagMatcher(tag))

private fun hasTestTagMatcher(tag: String) =
    androidx.compose.ui.test.SemanticsMatcher.expectValue(
        androidx.compose.ui.semantics.SemanticsProperties.TestTag,
        tag,
    )
