package dev.happyc0der.forgelog.data.repository

import dev.happyc0der.forgelog.data.local.dayEntity
import dev.happyc0der.forgelog.data.local.exerciseEntity
import dev.happyc0der.forgelog.data.local.programEntity
import dev.happyc0der.forgelog.domain.history.HistoryFilter
import dev.happyc0der.forgelog.domain.model.SessionStartExercise
import dev.happyc0der.forgelog.domain.model.SessionStatus
import dev.happyc0der.forgelog.testing.MainDispatcherRule
import dev.happyc0der.forgelog.testing.TestEnvironment
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
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
 * Tidying the programs while a workout from one of them is still running.
 *
 * Nothing stops it: the logger is a screen you can leave, and Programs is two taps away with its
 * delete on every row. The foreign keys are SET_NULL so the session survives, but the question is
 * what the logger is left holding — it was started from a plan that no longer exists, and everything
 * it shows about that plan has to come from the snapshots taken when the session began.
 *
 * That is what the snapshots are for. This is the test that they are enough on their own.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DeletingAProgramMidSessionTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var env: TestEnvironment
    private var programId = 0L
    private var dayId = 0L
    private var sessionId = 0L

    @Before
    fun setUp() = runTest {
        env = TestEnvironment(mainDispatcherRule.dispatcher)
        env.time.now = 1_700_000_000_000L

        val exerciseId = env.database.exerciseDao().upsert(exerciseEntity(name = "Bench Press"))
        programId = env.database.programDao().insertProgram(programEntity(name = "PPL"))
        dayId = env.database.programDao().insertDay(dayEntity(programId = programId, name = "Push Day"))

        sessionId = env.sessionRepository.startSession(
            programId = programId,
            programDayId = dayId,
            sessionName = "PPL · Push Day",
            exercises = listOf(
                SessionStartExercise(
                    exercise = env.exerciseRepository.getExercise(exerciseId)!!,
                    plannedSets = 4,
                    targetRepMin = 6,
                    targetRepMax = 8,
                    targetWeight = 185.0,
                    targetRestSeconds = 150,
                    pointersOverride = "elbows tucked",
                ),
            ),
        )
    }

    @After
    fun tearDown() = env.tearDown()

    /** Everything the logger reads about the plan, after the plan is gone. */
    private suspend fun assertTheLoggerStillHasItsPlan() {
        val detail = env.sessionRepository.getSessionDetail(sessionId)
        assertNotNull("the session itself was taken with the program", detail)
        assertEquals(SessionStatus.IN_PROGRESS, detail!!.session.status)
        assertEquals("the session lost the name it was started under", "PPL · Push Day", detail.session.sessionName)

        val exercise = detail.exercises.single().exercise
        assertEquals("Bench Press", exercise.displayNameSnapshot)
        assertEquals("elbows tucked", exercise.pointersSnapshot)
        assertEquals(4, exercise.plannedSets)
        assertEquals(6, exercise.targetRepMin)
        assertEquals(8, exercise.targetRepMax)
        assertEquals(185.0, exercise.targetWeight ?: 0.0, 0.0)
        assertEquals(150, exercise.targetRestSeconds)
    }

    @Test
    fun deletingTheProgramLeavesTheWorkoutRunnable() = runTest {
        env.programRepository.deleteProgram(programId)

        assertTheLoggerStillHasItsPlan()
        val session = env.sessionRepository.getSession(sessionId)!!
        assertNull("the session still points at a program that is gone", session.programId)
        assertNull("the session still points at a day that went with it", session.programDayId)
    }

    @Test
    fun deletingJustTheDayLeavesTheWorkoutRunnable() = runTest {
        env.programRepository.deleteDay(dayId)

        assertTheLoggerStillHasItsPlan()
        val session = env.sessionRepository.getSession(sessionId)!!
        assertNull("the session still points at a day that is gone", session.programDayId)
        assertEquals("the program was taken along with its day", programId, session.programId)
    }

    /** And it can still be finished, which is the thing the user is in the middle of. */
    @Test
    fun aWorkoutWhoseProgramIsGoneCanStillBeFinished() = runTest {
        env.programRepository.deleteProgram(programId)

        env.sessionRepository.completeSession(sessionId)

        val session = env.sessionRepository.getSession(sessionId)!!
        assertEquals(SessionStatus.COMPLETED, session.status)
        assertNotNull("finished with no completion time", session.completedAt)
    }

    /** It stays in History too, under the name it was logged as. */
    @Test
    fun aWorkoutWhoseProgramIsGoneStaysInHistory() = runTest {
        env.sessionRepository.completeSession(sessionId)
        env.programRepository.deleteProgram(programId)

        val names = env.sessionRepository
            .observeSessionHistory(HistoryFilter())
            .first()
            .map { it.session.sessionName }

        assertEquals(listOf("PPL · Push Day"), names)
    }
}
