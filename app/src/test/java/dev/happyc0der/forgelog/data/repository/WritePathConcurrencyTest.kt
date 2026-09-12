package dev.happyc0der.forgelog.data.repository

import dev.happyc0der.forgelog.data.local.ForgeLogDatabase
import dev.happyc0der.forgelog.data.local.dayEntity
import dev.happyc0der.forgelog.data.local.exerciseEntity
import dev.happyc0der.forgelog.data.local.inMemoryDatabase
import dev.happyc0der.forgelog.data.local.programEntity
import dev.happyc0der.forgelog.data.local.sessionEntity
import dev.happyc0der.forgelog.data.local.sessionExerciseEntity
import dev.happyc0der.forgelog.domain.model.SessionStatus
import dev.happyc0der.forgelog.domain.workout.setLog
import dev.happyc0der.forgelog.testing.FakeTimeProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The write path with several things happening at once, which nothing tested before.
 *
 * The session row used to be updated by reading it, copying it and writing the whole thing back.
 * Tapping an exercise header while tapping Finish could therefore write back `status = IN_PROGRESS`
 * over a workout that had just been completed -- the read having happened before the finish and the
 * write after it. The fix was to update single columns instead, and the DAO says so in a comment.
 * This is the test that the fix holds rather than the comment claiming it does.
 *
 * Real threads, not a test dispatcher: the point is genuine overlap, so the database gets Room's own
 * executors and the repository gets Dispatchers.IO. Each case runs many rounds, because a race that
 * only shows up sometimes is still a race.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WritePathConcurrencyTest {

    private lateinit var database: ForgeLogDatabase
    private lateinit var repository: WorkoutSessionRepositoryImpl
    private val time = FakeTimeProvider(1_700_000_000_000L)

    private var sessionId = 0L
    private var sessionExerciseId = 0L

    @Before
    fun setUp() = runBlocking {
        database = inMemoryDatabase()
        repository = WorkoutSessionRepositoryImpl(
            database = database,
            workoutSessionDao = database.workoutSessionDao(),
            timeProvider = time,
            ioDispatcher = Dispatchers.IO,
        )
        val exerciseId = database.exerciseDao().upsert(exerciseEntity(name = "Bench Press"))
        val programId = database.programDao().insertProgram(programEntity(name = "PPL"))
        val dayId = database.programDao().insertDay(dayEntity(programId = programId, name = "Push"))
        sessionId = database.workoutSessionDao().insertSession(
            sessionEntity(
                programId = programId,
                programDayId = dayId,
                status = SessionStatus.IN_PROGRESS,
                startedAt = time.now,
                completedAt = null,
            ),
        )
        sessionExerciseId = database.workoutSessionDao().insertSessionExercise(
            sessionExerciseEntity(
                sessionId = sessionId,
                exerciseId = exerciseId,
                displayNameSnapshot = "Bench Press",
            ),
        )
        Unit
    }

    @After
    fun tearDown() {
        database.close()
    }

    /** Back to no sets, between rounds. */
    private suspend fun clearSets() {
        repository.getSessionDetail(sessionId)
            ?.exercises
            ?.flatMap { it.sets }
            ?.forEach { database.workoutSessionDao().deleteSetLog(it.id) }
    }

    /** Whatever else is happening, a finished workout stays finished. */
    @Test
    fun aFinishedWorkoutIsNeverUnfinished() = runBlocking {
        repeat(60) { round ->
            database.workoutSessionDao().updateStatus(
                id = sessionId,
                status = SessionStatus.IN_PROGRESS,
                completedAt = null,
                now = time.now,
            )
            coroutineScope {
                val jobs = buildList {
                    add(async(Dispatchers.IO) { repository.completeSession(sessionId) })
                    repeat(8) { index ->
                        add(
                            async(Dispatchers.IO) {
                                repository.updateExpandedExercise(
                                    sessionId,
                                    if (index % 2 == 0) sessionExerciseId else null,
                                )
                            },
                        )
                    }
                }
                jobs.awaitAll()
            }
            val session = requireNotNull(repository.getSession(sessionId))
            assertEquals(
                "round $round: a finished workout came back in progress",
                SessionStatus.COMPLETED,
                session.status,
            )
            assertNotNull("round $round: finished with no completion time", session.completedAt)
        }
    }

    /**
     * Finishing and abandoning at once leaves one answer, and a self-consistent one.
     *
     * Either outcome is fine -- whoever won, won -- but a completed session must carry a completion
     * time and an abandoned one must not, because the duration shown everywhere is derived from it.
     */
    @Test
    fun finishingAndAbandoningAtOnceLeaveOneCoherentOutcome() = runBlocking {
        repeat(60) { round ->
            database.workoutSessionDao().updateStatus(
                id = sessionId,
                status = SessionStatus.IN_PROGRESS,
                completedAt = null,
                now = time.now,
            )
            coroutineScope {
                listOf(
                    async(Dispatchers.IO) { repository.completeSession(sessionId) },
                    async(Dispatchers.IO) { repository.abandonSession(sessionId) },
                ).awaitAll()
            }
            val session = requireNotNull(repository.getSession(sessionId))
            assertTrue(
                "round $round: ended as ${session.status}, which is neither outcome",
                session.status == SessionStatus.COMPLETED || session.status == SessionStatus.ABANDONED,
            )
            if (session.status == SessionStatus.COMPLETED) {
                assertNotNull("round $round: completed with no completion time", session.completedAt)
            } else {
                assertNull(
                    "round $round: abandoned but carrying a completion time, so it will show a duration",
                    session.completedAt,
                )
            }
        }
    }

    /** Two fields of one row, written at the same time: neither overwrites the other. */
    @Test
    fun twoFieldsWrittenAtOnceBothSurvive() = runBlocking {
        repeat(80) { round ->
            database.workoutSessionDao().updateOverallFeeling(sessionId, null, time.now)
            database.workoutSessionDao().updateOverallNotes(sessionId, null, time.now)
            coroutineScope {
                listOf(
                    async(Dispatchers.IO) { repository.setOverallFeeling(sessionId, 4) },
                    async(Dispatchers.IO) { repository.setOverallNotes(sessionId, "felt strong") },
                ).awaitAll()
            }
            val session = requireNotNull(repository.getSession(sessionId))
            assertEquals("round $round: the note overwrote the feeling", 4, session.overallFeeling)
            assertEquals(
                "round $round: the feeling overwrote the note",
                "felt strong",
                session.overallNotes,
            )
        }
    }

    /** The same, for the per-exercise pair. */
    @Test
    fun anExercisesFeelingAndNotesDoNotOverwriteEachOther() = runBlocking {
        repeat(80) { round ->
            repository.setExerciseFeeling(sessionExerciseId, null)
            repository.setExerciseNotes(sessionExerciseId, null)
            coroutineScope {
                listOf(
                    async(Dispatchers.IO) { repository.setExerciseFeeling(sessionExerciseId, 5) },
                    async(Dispatchers.IO) { repository.setExerciseNotes(sessionExerciseId, "elbow twinge") },
                ).awaitAll()
            }
            val detail = requireNotNull(repository.getSessionDetail(sessionId))
            val exercise = detail.exercises.single().exercise
            assertEquals("round $round: notes overwrote the feeling", 5, exercise.feeling)
            assertEquals("round $round: the feeling overwrote the notes", "elbow twinge", exercise.exerciseNotes)
        }
    }

    /** Sets appended at the same time are numbered once each, with no gap and no duplicate. */
    @Test
    fun setsAppendedAtOnceAreEachNumberedOnce() = runBlocking {
        repeat(25) { round ->
            clearSets()
            val howMany = 10
            coroutineScope {
                (1..howMany).map {
                    async(Dispatchers.IO) {
                        repository.appendSetLog(setLog(id = 0L, sessionExerciseId = sessionExerciseId))
                    }
                }.awaitAll()
            }
            val numbers = requireNotNull(repository.getSessionDetail(sessionId))
                .exercises.single().sets.map { it.setNumber }.sorted()
            assertEquals(
                "round $round: $howMany sets appended at once came back numbered $numbers",
                (1..howMany).toList(),
                numbers,
            )
        }
    }

    /** Appending and deleting at the same time still leaves the numbering a clean run. */
    @Test
    fun appendingWhileDeletingLeavesAContiguousRun() = runBlocking {
        repeat(25) { round ->
            clearSets()
            repeat(6) {
                repository.appendSetLog(setLog(id = 0L, sessionExerciseId = sessionExerciseId))
            }
            val existing = requireNotNull(repository.getSessionDetail(sessionId))
                .exercises.single().sets.map { it.id }

            coroutineScope {
                buildList {
                    existing.take(3).forEach { id ->
                        add(async(Dispatchers.IO) { repository.deleteSetLog(id) })
                    }
                    repeat(3) {
                        add(
                            async(Dispatchers.IO) {
                                repository.appendSetLog(setLog(id = 0L, sessionExerciseId = sessionExerciseId))
                            },
                        )
                    }
                }.awaitAll()
            }

            val numbers = requireNotNull(repository.getSessionDetail(sessionId))
                .exercises.single().sets.map { it.setNumber }.sorted()
            assertEquals(
                "round $round: numbering came back $numbers, which is not a clean run of ${numbers.size}",
                (1..numbers.size).toList(),
                numbers,
            )
        }
    }
}
