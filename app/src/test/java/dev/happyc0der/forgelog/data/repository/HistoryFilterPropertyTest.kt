package dev.happyc0der.forgelog.data.repository

import dev.happyc0der.forgelog.data.local.dayEntity
import dev.happyc0der.forgelog.data.local.exerciseEntity
import dev.happyc0der.forgelog.data.local.programEntity
import dev.happyc0der.forgelog.data.local.sessionEntity
import dev.happyc0der.forgelog.data.local.sessionExerciseEntity
import dev.happyc0der.forgelog.domain.history.HistoryFilter
import dev.happyc0der.forgelog.domain.model.SessionDetail
import dev.happyc0der.forgelog.domain.model.SessionStatus
import dev.happyc0der.forgelog.testing.MainDispatcherRule
import dev.happyc0der.forgelog.testing.TestEnvironment
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Every combination of History's filters, against the same question asked in Kotlin.
 *
 * History is one SQL statement with seven optional filters, each written as `:param IS NULL OR ...`
 * so that one query serves every combination. That is the right shape and it is also a lot of
 * combinations, and the failure mode is the worst kind of quiet: a session that was logged does not
 * come back, and the user concludes the app lost their workout rather than that a filter is wrong.
 *
 * So the oracle is not a second query. It is the filter applied by hand, in Kotlin, to every session
 * in the database — which is a statement of what the filter is supposed to mean, independent of the
 * SQL that implements it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HistoryFilterPropertyTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var env: TestEnvironment
    private var benchId = 0L
    private var squatId = 0L
    private var plankId = 0L
    private var pplId = 0L
    private var otherProgramId = 0L
    private var pushDayId = 0L
    private var pullDayId = 0L

    private val day = 24L * 60 * 60 * 1000

    @Before
    fun setUp() = runTest {
        env = TestEnvironment(mainDispatcherRule.dispatcher)
        val exercises = env.database.exerciseDao()
        val programs = env.database.programDao()
        val sessions = env.database.workoutSessionDao()

        benchId = exercises.upsert(exerciseEntity(name = "Bench Press"))
        squatId = exercises.upsert(exerciseEntity(name = "Back Squat"))
        plankId = exercises.upsert(exerciseEntity(name = "Plank"))
        pplId = programs.insertProgram(programEntity(name = "PPL"))
        otherProgramId = programs.insertProgram(programEntity(name = "Upper/Lower"))
        pushDayId = programs.insertDay(dayEntity(programId = pplId, name = "Push Day"))
        pullDayId = programs.insertDay(dayEntity(programId = pplId, name = "Pull Day"))

        // Names chosen to be awkward for LIKE: wildcards, mixed case, an underscore.
        data class Seed(
            val name: String,
            val program: Long?,
            val dayId: Long?,
            val status: SessionStatus,
            val startedAt: Long,
            val lifts: List<Long?>,
        )

        val seeds = listOf(
            Seed("PPL · Push Day", pplId, pushDayId, SessionStatus.COMPLETED, 10 * day, listOf(benchId)),
            Seed("PPL · Pull Day", pplId, pullDayId, SessionStatus.COMPLETED, 11 * day, listOf(squatId)),
            Seed("PPL · Push Day", pplId, pushDayId, SessionStatus.ABANDONED, 12 * day, listOf(benchId, plankId)),
            Seed("Upper/Lower A", otherProgramId, null, SessionStatus.COMPLETED, 13 * day, listOf(benchId, squatId)),
            Seed("Ad-hoc workout", null, null, SessionStatus.COMPLETED, 14 * day, listOf(plankId)),
            Seed("50% week", null, null, SessionStatus.COMPLETED, 15 * day, listOf(benchId)),
            Seed("de_load", null, null, SessionStatus.IN_PROGRESS, 16 * day, listOf(squatId)),
            Seed("BENCH focus", pplId, pushDayId, SessionStatus.COMPLETED, 17 * day, listOf(benchId)),
            Seed("empty session", null, null, SessionStatus.COMPLETED, 18 * day, emptyList()),
            Seed("deleted lift", null, null, SessionStatus.COMPLETED, 19 * day, listOf(null)),
        )

        seeds.forEach { seed ->
            val id = sessions.insertSession(
                sessionEntity(
                    sessionName = seed.name,
                    programId = seed.program,
                    programDayId = seed.dayId,
                    status = seed.status,
                    startedAt = seed.startedAt,
                    completedAt = seed.startedAt + 3_600_000,
                ),
            )
            seed.lifts.forEachIndexed { index, lift ->
                sessions.insertSessionExercise(
                    sessionExerciseEntity(
                        sessionId = id,
                        exerciseId = lift,
                        displayNameSnapshot = when (lift) {
                            benchId -> "Bench Press"
                            squatId -> "Back Squat"
                            plankId -> "Plank"
                            else -> "Gone From Library"
                        },
                        exerciseOrder = index,
                    ),
                )
            }
        }
    }

    @After
    fun tearDown() = env.tearDown()

    /** What the filter is supposed to mean, said in Kotlin rather than SQL. */
    private fun matches(filter: HistoryFilter, detail: SessionDetail): Boolean {
        val session = detail.session
        if (filter.status != null && session.status != filter.status) return false
        if (filter.programId != null && session.programId != filter.programId) return false
        if (filter.programDayId != null && session.programDayId != filter.programDayId) return false
        if (filter.fromEpochMs != null && session.startedAt < filter.fromEpochMs!!) return false
        if (filter.untilEpochMs != null && session.startedAt >= filter.untilEpochMs!!) return false
        if (filter.exerciseId != null &&
            detail.exercises.none { it.exercise.exerciseId == filter.exerciseId }
        ) {
            return false
        }
        val term = filter.query.trim()
        if (term.isNotEmpty()) {
            // Searched for literally, wildcards included: "50%" must not match "50 reps".
            val inName = session.sessionName.contains(term, ignoreCase = true)
            val inLift = detail.exercises.any {
                it.exercise.displayNameSnapshot.contains(term, ignoreCase = true)
            }
            if (!inName && !inLift) return false
        }
        return true
    }

    private suspend fun everySession(): List<SessionDetail> =
        env.sessionRepository.observeSessionHistory(HistoryFilter()).first()

    private suspend fun check(filter: HistoryFilter) {
        val fromSql = env.sessionRepository.observeSessionHistory(filter).first()
        val expected = everySession().filter { matches(filter, it) }

        assertEquals(
            "filter $filter returned the wrong sessions",
            expected.map { it.session.id }.sortedDescending(),
            fromSql.map { it.session.id }.sortedDescending(),
        )
        assertEquals(
            "filter $filter is not ordered newest first",
            fromSql.map { it.session.startedAt }.sortedDescending(),
            fromSql.map { it.session.startedAt },
        )
    }

    @Test
    fun everyCombinationOfFiltersAgreesWithTheSameQuestionInKotlin() = runTest {
        val statuses = listOf<SessionStatus?>(null) + SessionStatus.entries
        val programIds = listOf(null, pplId, otherProgramId, 9_999L)
        val dayIds = listOf(null, pushDayId, pullDayId, 9_999L)
        val exerciseIds = listOf(null, benchId, squatId, plankId, 9_999L)
        val froms = listOf(null, 12 * day, 20 * day)
        val untils = listOf(null, 15 * day, 5 * day)
        val queries = listOf("", "push", "PUSH", "bench", "50%", "de_load", "%", "_", "  push  ", "nothing")

        var checked = 0
        for (status in statuses) {
            for (programId in programIds) {
                for (dayId in dayIds) {
                    check(HistoryFilter(status = status, programId = programId, programDayId = dayId))
                    checked++
                }
            }
        }
        for (exerciseId in exerciseIds) {
            for (from in froms) {
                for (until in untils) {
                    check(
                        HistoryFilter(
                            exerciseId = exerciseId,
                            fromEpochMs = from,
                            untilEpochMs = until,
                        ),
                    )
                    checked++
                }
            }
        }
        for (query in queries) {
            for (status in statuses) {
                check(HistoryFilter(query = query, status = status))
                checked++
            }
            for (exerciseId in exerciseIds) {
                check(HistoryFilter(query = query, exerciseId = exerciseId))
                checked++
            }
        }
        // Everything at once, which is the combination the screen can actually produce.
        check(
            HistoryFilter(
                query = "push",
                status = SessionStatus.COMPLETED,
                programId = pplId,
                programDayId = pushDayId,
                exerciseId = benchId,
                fromEpochMs = 9 * day,
                untilEpochMs = 20 * day,
            ),
        )
        checked++

        assertTrue("nothing was checked", checked > 150)
    }

    /** A wildcard is a character to search for, not an instruction. */
    @Test
    fun wildcardsInASearchAreSearchedForLiterally() = runTest {
        val percent = env.sessionRepository.observeSessionHistory(HistoryFilter(query = "50%")).first()
        assertEquals(listOf("50% week"), percent.map { it.session.sessionName })

        val bareWildcard = env.sessionRepository.observeSessionHistory(HistoryFilter(query = "%")).first()
        assertEquals("a lone % matched everything", listOf("50% week"), bareWildcard.map { it.session.sessionName })

        val underscore = env.sessionRepository.observeSessionHistory(HistoryFilter(query = "_")).first()
        assertEquals("a lone _ matched everything", listOf("de_load"), underscore.map { it.session.sessionName })
    }

    /** A session whose lift was deleted from the library is still findable by its snapshot. */
    @Test
    fun aDeletedLiftIsStillSearchableByTheNameItWasLoggedUnder() = runTest {
        val found = env.sessionRepository
            .observeSessionHistory(HistoryFilter(query = "Gone From Library"))
            .first()
        assertEquals(listOf("deleted lift"), found.map { it.session.sessionName })

        // ...but not filterable by exercise id, because it no longer has one.
        val byId = env.sessionRepository
            .observeSessionHistory(HistoryFilter(exerciseId = 9_999L))
            .first()
        assertEquals(emptyList<String>(), byId.map { it.session.sessionName })
    }
}
