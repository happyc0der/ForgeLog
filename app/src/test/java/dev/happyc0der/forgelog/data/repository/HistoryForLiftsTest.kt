package dev.happyc0der.forgelog.data.repository

import dev.happyc0der.forgelog.data.local.exerciseEntity
import dev.happyc0der.forgelog.data.local.sessionEntity
import dev.happyc0der.forgelog.data.local.sessionExerciseEntity
import dev.happyc0der.forgelog.domain.model.SessionStatus
import dev.happyc0der.forgelog.testing.MainDispatcherRule
import dev.happyc0der.forgelog.testing.TestEnvironment
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * "Last time" and records are about particular lifts, so they are looked up by lift: the latest
 * sessions of any kind can be taken up entirely by others, imported swims most of all.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HistoryForLiftsTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var env: TestEnvironment
    private var benchId = 0L
    private var swimId = 0L

    @Before
    fun setUp() = runTest {
        env = TestEnvironment(mainDispatcherRule.dispatcher)
        benchId = env.database.exerciseDao().upsert(exerciseEntity(name = "Bench Press"))
        swimId = env.database.exerciseDao().upsert(exerciseEntity(name = "Swim"))
    }

    @After
    fun tearDown() = env.tearDown()

    private suspend fun session(name: String, at: Long, exerciseId: Long, status: SessionStatus = SessionStatus.COMPLETED): Long {
        val dao = env.database.workoutSessionDao()
        val id = dao.insertSession(
            sessionEntity(sessionName = name, startedAt = at, completedAt = at + 1_000L, status = status),
        )
        dao.insertSessionExercise(sessionExerciseEntity(sessionId = id, exerciseId = exerciseId))
        return id
    }

    @Test
    fun `the last bench session is found behind any number of swims`() = runTest {
        val bench = session("Push", at = 1_000L, exerciseId = benchId)
        (1..60).forEach { day -> session("Swim $day", at = 10_000L + day * 1_000L, exerciseId = swimId) }

        val found = env.sessionRepository.getCompletedDetailsWithExercises(listOf(benchId))
        assertEquals(listOf(bench), found.map { it.session.id })
    }

    @Test
    fun `newest first, limited, and without the excluded or unfinished sessions`() = runTest {
        val older = session("Push 1", at = 1_000L, exerciseId = benchId)
        val newer = session("Push 2", at = 5_000L, exerciseId = benchId)
        val current = session("Push 3", at = 9_000L, exerciseId = benchId)
        session("Quit", at = 7_000L, exerciseId = benchId, status = SessionStatus.ABANDONED)

        val found = env.sessionRepository.getCompletedDetailsWithExercises(
            exerciseIds = listOf(benchId),
            excludeSessionId = current,
        )
        assertEquals(listOf(newer, older), found.map { it.session.id })

        val latestOnly = env.sessionRepository.getCompletedDetailsWithExercises(listOf(benchId), limit = 1)
        assertEquals(listOf(current), latestOnly.map { it.session.id })
    }

    @Test
    fun `no lifts asked about, no sessions`() = runTest {
        session("Push", at = 1_000L, exerciseId = benchId)
        assertEquals(emptyList<Any>(), env.sessionRepository.getCompletedDetailsWithExercises(emptyList()))
    }
}
