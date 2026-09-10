package dev.happyc0der.forgelog.data.local.dao

import dev.happyc0der.forgelog.data.local.ForgeLogDatabase
import dev.happyc0der.forgelog.data.local.dayEntity
import dev.happyc0der.forgelog.data.local.exerciseEntity
import dev.happyc0der.forgelog.data.local.inMemoryDatabase
import dev.happyc0der.forgelog.data.local.programEntity
import dev.happyc0der.forgelog.data.local.sessionEntity
import dev.happyc0der.forgelog.data.local.sessionExerciseEntity
import dev.happyc0der.forgelog.data.local.setLogEntity
import dev.happyc0der.forgelog.domain.history.HistoryFilter
import dev.happyc0der.forgelog.domain.model.SessionStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The history query takes seven optional filters that combine with AND, which is exactly the shape
 * of SQL that looks right and silently returns the wrong rows. Every filter is exercised alone, and
 * then in combination.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SessionHistoryQueryTest {

    private lateinit var database: ForgeLogDatabase
    private lateinit var dao: WorkoutSessionDao

    private var pplId = 0L
    private var otherProgramId = 0L
    private var pushDayId = 0L
    private var pullDayId = 0L
    private var benchId = 0L
    private var rowId = 0L

    @Before
    fun setUp() = runTest {
        database = inMemoryDatabase()
        dao = database.workoutSessionDao()
        val programDao = database.programDao()
        val exerciseDao = database.exerciseDao()

        pplId = programDao.insertProgram(programEntity(name = "PPL Strength"))
        otherProgramId = programDao.insertProgram(programEntity(name = "Upper Lower"))
        pushDayId = programDao.insertDay(dayEntity(programId = pplId, name = "Push Day"))
        pullDayId = programDao.insertDay(dayEntity(programId = pplId, name = "Pull Day", dayOrder = 1))
        benchId = exerciseDao.upsert(exerciseEntity(name = "Bench Press"))
        rowId = exerciseDao.upsert(exerciseEntity(name = "Barbell Row"))

        // Day 1: PPL Push Day, completed, bench press.
        session("PPL Strength · Push Day", 1_000L, 2_000L, SessionStatus.COMPLETED, pplId, pushDayId, benchId, "Bench Press")
        // Day 2: PPL Pull Day, completed, barbell row.
        session("PPL Strength · Pull Day", 3_000L, 4_000L, SessionStatus.COMPLETED, pplId, pullDayId, rowId, "Barbell Row")
        // Day 3: other program, abandoned, bench press.
        session("Upper Lower · Upper", 5_000L, 5_500L, SessionStatus.ABANDONED, otherProgramId, null, benchId, "Bench Press")
        // Day 4: ad-hoc, in progress, no program at all.
        session("Ad-hoc workout", 7_000L, null, SessionStatus.IN_PROGRESS, null, null, null, "Nordic Curl")
    }

    @After
    fun tearDown() {
        database.close()
    }

    private suspend fun session(
        name: String,
        startedAt: Long,
        completedAt: Long?,
        status: SessionStatus,
        programId: Long?,
        programDayId: Long?,
        exerciseId: Long?,
        displayName: String,
    ): Long {
        val sessionId = dao.insertSession(
            sessionEntity(
                sessionName = name,
                startedAt = startedAt,
                completedAt = completedAt,
                status = status,
                programId = programId,
                programDayId = programDayId,
            ),
        )
        val sessionExerciseId = dao.insertSessionExercise(
            sessionExerciseEntity(
                sessionId = sessionId,
                exerciseId = exerciseId,
                displayNameSnapshot = displayName,
            ),
        )
        dao.upsertSetLog(setLogEntity(sessionExerciseId = sessionExerciseId))
        return sessionId
    }

    private suspend fun history(
        query: String = "",
        status: SessionStatus? = null,
        programId: Long? = null,
        programDayId: Long? = null,
        exerciseId: Long? = null,
        from: Long? = null,
        until: Long? = null,
    ): List<String> = dao.observeSessionHistory(
        query = query,
        status = status,
        programId = programId,
        programDayId = programDayId,
        exerciseId = exerciseId,
        fromEpochMs = from,
        untilEpochMs = until,
    ).first().map { it.session.sessionName }

    @Test
    fun `no filters returns everything, newest first`() = runTest {
        assertEquals(
            listOf(
                "Ad-hoc workout",
                "Upper Lower · Upper",
                "PPL Strength · Pull Day",
                "PPL Strength · Push Day",
            ),
            history(),
        )
    }

    @Test
    fun `status narrows to one kind of session`() = runTest {
        assertEquals(2, history(status = SessionStatus.COMPLETED).size)
        assertEquals(listOf("Upper Lower · Upper"), history(status = SessionStatus.ABANDONED))
        assertEquals(listOf("Ad-hoc workout"), history(status = SessionStatus.IN_PROGRESS))
    }

    @Test
    fun `program filter excludes other programs and program-less sessions`() = runTest {
        assertEquals(
            listOf("PPL Strength · Pull Day", "PPL Strength · Push Day"),
            history(programId = pplId),
        )
        assertEquals(listOf("Upper Lower · Upper"), history(programId = otherProgramId))
    }

    @Test
    fun `day filter narrows within a program`() = runTest {
        assertEquals(listOf("PPL Strength · Push Day"), history(programDayId = pushDayId))
        assertEquals(listOf("PPL Strength · Pull Day"), history(programDayId = pullDayId))
    }

    @Test
    fun `exercise filter finds every session containing that exercise`() = runTest {
        assertEquals(
            listOf("Upper Lower · Upper", "PPL Strength · Push Day"),
            history(exerciseId = benchId),
        )
        assertEquals(listOf("PPL Strength · Pull Day"), history(exerciseId = rowId))
    }

    @Test
    fun `date range is inclusive of from and exclusive of until`() = runTest {
        // Sessions start at 1000, 3000, 5000, 7000.
        assertEquals(2, history(from = 3_000L, until = 7_000L).size)
        assertEquals(listOf("PPL Strength · Push Day"), history(from = 1_000L, until = 3_000L))
        assertEquals(emptyList<String>(), history(from = 7_001L))
        assertEquals(4, history(from = 1_000L).size)
    }

    @Test
    fun `text search matches the session name`() = runTest {
        assertEquals(2, history(query = "PPL").size)
        assertEquals(listOf("Ad-hoc workout"), history(query = "Ad-hoc"))
    }

    @Test
    fun `text search matches an exercise name snapshot`() = runTest {
        assertEquals(
            listOf("Upper Lower · Upper", "PPL Strength · Push Day"),
            history(query = "Bench"),
        )
        assertEquals(listOf("Ad-hoc workout"), history(query = "Nordic"))
    }

    @Test
    fun `text search is case-insensitive`() = runTest {
        assertEquals(history(query = "bench"), history(query = "BENCH"))
        assertEquals(2, history(query = "bench").size)
    }

    @Test
    fun `an empty query is not treated as a filter`() = runTest {
        assertEquals(4, history(query = "").size)
    }

    @Test
    fun `filters combine with AND`() = runTest {
        // Bench press appears in two sessions, but only one of them is a completed PPL session.
        assertEquals(
            listOf("PPL Strength · Push Day"),
            history(exerciseId = benchId, status = SessionStatus.COMPLETED, programId = pplId),
        )
        // A combination that matches nothing returns nothing rather than ignoring a filter.
        assertEquals(
            emptyList<String>(),
            history(exerciseId = rowId, programDayId = pushDayId),
        )
    }

    @Test
    fun `sessions are returned with their exercises and sets attached`() = runTest {
        val details = dao.observeSessionHistory("", null, pplId, pushDayId, null, null, null).first()
        assertEquals(1, details.size)
        assertEquals(1, details.first().exercises.size)
        assertEquals("Bench Press", details.first().exercises.first().exercise.displayNameSnapshot)
        assertEquals(1, details.first().exercises.first().sets.size)
    }

    @Test
    fun `logged exercises lists each exercise once, alphabetically`() = runTest {
        val logged = dao.observeLoggedExercises().first()
        // Bench press appears in two sessions but must be offered once; the ad-hoc session's
        // exercise had a null id and so cannot be filtered on.
        assertEquals(listOf("Barbell Row", "Bench Press"), logged.map { it.displayName })
        assertEquals(listOf(rowId, benchId), logged.map { it.exerciseId })
    }

    /*
     * LIKE wildcards in a search term are searched for, not obeyed. Unescaped, "50%" matched any
     * session containing "50", and a lone "_" matched every session with at least one character.
     * The escaping lives in HistoryFilter.normalizedQuery, so these go through it rather than
     * handing the DAO an already-escaped string.
     */

    private suspend fun search(term: String): List<String> =
        history(query = HistoryFilter(query = term).normalizedQuery)

    @Test
    fun `a percent sign in the search term is literal`() = runTest {
        session("Deload 50% week", 9_000L, 9_500L, SessionStatus.COMPLETED, null, null, null, "Bench Press")
        session("Heavy 500 day", 9_600L, 9_700L, SessionStatus.COMPLETED, null, null, null, "Bench Press")

        assertEquals(listOf("Deload 50% week"), search("50%"))
    }

    @Test
    fun `an underscore in the search term is literal`() = runTest {
        session("Push_A", 9_000L, 9_500L, SessionStatus.COMPLETED, null, null, null, "Bench Press")
        session("PushB", 9_600L, 9_700L, SessionStatus.COMPLETED, null, null, null, "Bench Press")

        assertEquals(listOf("Push_A"), search("Push_"))
    }

    @Test
    fun `a plain term still matches`() = runTest {
        assertEquals(listOf("PPL Strength \u00b7 Push Day"), search("Push Day"))
    }
}
