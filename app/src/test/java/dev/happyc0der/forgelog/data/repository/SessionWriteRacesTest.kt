package dev.happyc0der.forgelog.data.repository

import dev.happyc0der.forgelog.data.local.exerciseEntity
import dev.happyc0der.forgelog.domain.model.Exercise
import dev.happyc0der.forgelog.domain.model.ExerciseCategory
import dev.happyc0der.forgelog.domain.model.ExerciseUnit
import dev.happyc0der.forgelog.domain.model.SessionSource
import dev.happyc0der.forgelog.domain.model.SessionStartExercise
import dev.happyc0der.forgelog.domain.model.SessionStatus
import dev.happyc0der.forgelog.testing.MainDispatcherRule
import dev.happyc0der.forgelog.testing.TestEnvironment
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
 * Session-level edits used to be read-modify-write upserts of the whole row.
 *
 * Two of them overlapping meant the second write carried a copy of the row taken before the first,
 * silently undoing it — finishing a workout while a tap expanded an exercise could put the session
 * back to in-progress. They are targeted single-column UPDATEs now, and these tests interleave the
 * operations in the order that used to lose data.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SessionWriteRacesTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var env: TestEnvironment
    private var benchId = 0L

    @Before
    fun setUp() = runTest {
        env = TestEnvironment(mainDispatcherRule.dispatcher)
        env.time.now = 1_000L
        benchId = env.database.exerciseDao().upsert(exerciseEntity(name = "Bench Press"))
    }

    @After
    fun tearDown() = env.tearDown()

    private suspend fun startSession(): Long = env.sessionRepository.startSession(
        programId = null,
        programDayId = null,
        sessionName = "Ad-hoc",
        exercises = listOf(
            SessionStartExercise(
                exercise = Exercise(
                    id = benchId,
                    name = "Bench Press",
                    category = ExerciseCategory.PUSH,
                    defaultUnit = ExerciseUnit.LB,
                    howToUrl = null,
                    defaultPointers = null,
                    isArchived = false,
                    createdAt = 0L,
                    updatedAt = 0L,
                ),
            ),
        ),
    )

    @Test
    fun `expanding an exercise after finishing does not un-finish the session`() = runTest {
        val sessionId = startSession()
        val exerciseId = env.sessionRepository.getSessionDetail(sessionId)!!
            .exercises.single().exercise.id

        env.time.now = 5_000L
        env.sessionRepository.completeSession(sessionId)
        // The tap that used to carry a stale copy of the row back over the completion.
        env.sessionRepository.updateExpandedExercise(sessionId, exerciseId)

        val session = env.sessionRepository.getSession(sessionId)!!
        assertEquals(SessionStatus.COMPLETED, session.status)
        assertEquals(5_000L, session.completedAt)
        assertEquals(exerciseId, session.expandedSessionExerciseId)
    }

    @Test
    fun `a feeling and a note written in quick succession both survive`() = runTest {
        val sessionId = startSession()
        env.sessionRepository.setOverallFeeling(sessionId, 4)
        env.sessionRepository.setOverallNotes(sessionId, "Felt strong")

        val session = env.sessionRepository.getSession(sessionId)!!
        assertEquals(4, session.overallFeeling)
        assertEquals("Felt strong", session.overallNotes)
    }

    @Test
    fun `an abandoned session has no completion time`() = runTest {
        val sessionId = startSession()
        env.time.now = 9_000L
        env.sessionRepository.abandonSession(sessionId)

        val session = env.sessionRepository.getSession(sessionId)!!
        assertEquals(SessionStatus.ABANDONED, session.status)
        // Null is what marks the duration unknown; stamping one made history show a figure for a
        // workout that never finished.
        assertNull(session.completedAt)
    }

    @Test
    fun `provenance survives an edit rather than reverting to manual`() = runTest {
        val sessionId = startSession()
        val imported = env.sessionRepository.getSession(sessionId)!!.copy(
            source = SessionSource.IMPORTED,
            externalSource = "garmin",
            externalId = "activity-42",
        )
        env.sessionRepository.upsertSession(imported)

        // Any ordinary edit used to round-trip through a mapper that dropped these three columns.
        env.sessionRepository.setOverallFeeling(sessionId, 3)
        env.sessionRepository.completeSession(sessionId)

        val reloaded = env.sessionRepository.getSession(sessionId)!!
        assertEquals(SessionSource.IMPORTED, reloaded.source)
        assertEquals("garmin", reloaded.externalSource)
        assertEquals("activity-42", reloaded.externalId)
    }
}
