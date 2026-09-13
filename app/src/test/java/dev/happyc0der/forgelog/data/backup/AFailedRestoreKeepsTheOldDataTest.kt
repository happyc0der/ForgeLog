package dev.happyc0der.forgelog.data.backup

import dev.happyc0der.forgelog.data.local.dao.BackupDao
import dev.happyc0der.forgelog.data.local.entity.ExerciseEntity
import dev.happyc0der.forgelog.data.local.entity.ProgramDayEntity
import dev.happyc0der.forgelog.data.local.entity.ProgramExerciseEntity
import dev.happyc0der.forgelog.data.local.entity.SessionExerciseEntity
import dev.happyc0der.forgelog.data.local.entity.SetLogEntity
import dev.happyc0der.forgelog.data.local.entity.WorkoutProgramEntity
import dev.happyc0der.forgelog.data.local.entity.WorkoutSessionEntity
import dev.happyc0der.forgelog.data.local.exerciseEntity
import dev.happyc0der.forgelog.data.local.dayEntity
import dev.happyc0der.forgelog.data.local.programEntity
import dev.happyc0der.forgelog.data.local.programExerciseEntity
import dev.happyc0der.forgelog.data.local.relation.SessionDetailEntity
import dev.happyc0der.forgelog.data.local.sessionEntity
import dev.happyc0der.forgelog.data.local.sessionExerciseEntity
import dev.happyc0der.forgelog.data.local.setLogEntity
import dev.happyc0der.forgelog.testing.MainDispatcherRule
import dev.happyc0der.forgelog.testing.TestEnvironment
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import java.io.IOException

/**
 * A restore that fails halfway must leave the training history it was about to replace.
 *
 * Restoring wipes every table and then writes the file's rows in their place, so between those two
 * halves the user has nothing at all. The only thing standing between a failure there and the loss
 * of everything is that both halves are inside one transaction — and nothing exercises that. The
 * importer is thorough enough that no file reaches the wipe and then fails: duplicate ids, duplicate
 * external ids, dangling references, bad enums and out-of-range values are all turned away first, and
 * a missing required field fails to parse. Which is exactly why this needs saying out loud in a test.
 * The guarantee is load-bearing, unreachable from outside, and its absence would be invisible until
 * the one day it mattered — a disk filling up, a database error, or someone moving the wipe out of
 * the transaction for a perfectly sensible-looking reason.
 *
 * So the failure is injected instead, on the last insert of the restore, with everything else real.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AFailedRestoreKeepsTheOldDataTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var env: TestEnvironment

    @Before
    fun setUp() = runTest {
        env = TestEnvironment(mainDispatcherRule.dispatcher)
        env.time.now = 5_000L
        seed()
    }

    @After
    fun tearDown() = env.tearDown()

    private suspend fun seed() {
        val exerciseDao = env.database.exerciseDao()
        val programDao = env.database.programDao()
        val sessionDao = env.database.workoutSessionDao()

        val benchId = exerciseDao.upsert(exerciseEntity(name = "Bench Press"))
        val programId = programDao.insertProgram(programEntity(name = "PPL"))
        val dayId = programDao.insertDay(dayEntity(programId = programId, name = "Push Day"))
        programDao.insertProgramExercise(
            programExerciseEntity(programDayId = dayId, exerciseId = benchId),
        )
        val sessionId = sessionDao.insertSession(
            sessionEntity(sessionName = "PPL · Push Day", startedAt = 1_000, completedAt = 4_000),
        )
        val sessionExerciseId = sessionDao.insertSessionExercise(
            sessionExerciseEntity(sessionId = sessionId, exerciseId = benchId),
        )
        sessionDao.upsertSetLog(
            setLogEntity(sessionExerciseId = sessionExerciseId, setNumber = 1, weight = 185.0, reps = 5),
        )
    }

    private suspend fun snapshot(): Map<String, Any> {
        val dao = env.database.backupDao()
        return mapOf(
            "exercises" to dao.allExercises(),
            "programs" to dao.allPrograms(),
            "programDays" to dao.allProgramDays(),
            "programExercises" to dao.allProgramExercises(),
            "sessions" to dao.allSessions(),
            "sessionExercises" to dao.allSessionExercises(),
            "setLogs" to dao.allSetLogs(),
        )
    }

    /** The real dao in every respect but one: the named insert throws, as a failing disk would. */
    private class FailsOn(
        private val real: BackupDao,
        private val failing: String,
    ) : BackupDao {
        private fun stopIf(step: String) {
            if (step == failing) throw IOException("the disk gave out during $step")
        }

        override suspend fun allExercises(): List<ExerciseEntity> = real.allExercises()
        override suspend fun allPrograms(): List<WorkoutProgramEntity> = real.allPrograms()
        override suspend fun allProgramDays(): List<ProgramDayEntity> = real.allProgramDays()
        override suspend fun allProgramExercises(): List<ProgramExerciseEntity> =
            real.allProgramExercises()
        override suspend fun allSessions(): List<WorkoutSessionEntity> = real.allSessions()
        override suspend fun allSessionExercises(): List<SessionExerciseEntity> =
            real.allSessionExercises()
        override suspend fun allSetLogs(): List<SetLogEntity> = real.allSetLogs()
        override suspend fun sessionDetailsBetween(
            fromEpochMs: Long?,
            untilEpochMs: Long?,
        ): List<SessionDetailEntity> = real.sessionDetailsBetween(fromEpochMs, untilEpochMs)

        override suspend fun insertExercises(entities: List<ExerciseEntity>) {
            stopIf("insertExercises"); real.insertExercises(entities)
        }

        override suspend fun insertPrograms(entities: List<WorkoutProgramEntity>) {
            stopIf("insertPrograms"); real.insertPrograms(entities)
        }

        override suspend fun insertProgramDays(entities: List<ProgramDayEntity>) {
            stopIf("insertProgramDays"); real.insertProgramDays(entities)
        }

        override suspend fun insertProgramExercises(entities: List<ProgramExerciseEntity>) {
            stopIf("insertProgramExercises"); real.insertProgramExercises(entities)
        }

        override suspend fun insertSessions(entities: List<WorkoutSessionEntity>) {
            stopIf("insertSessions"); real.insertSessions(entities)
        }

        override suspend fun insertSessionExercises(entities: List<SessionExerciseEntity>) {
            stopIf("insertSessionExercises"); real.insertSessionExercises(entities)
        }

        override suspend fun insertSetLogs(entities: List<SetLogEntity>) {
            stopIf("insertSetLogs"); real.insertSetLogs(entities)
        }

        override suspend fun deleteAllSetLogs() = real.deleteAllSetLogs()
        override suspend fun deleteAllSessionExercises() = real.deleteAllSessionExercises()
        override suspend fun deleteAllSessions() = real.deleteAllSessions()
        override suspend fun deleteAllProgramExercises() = real.deleteAllProgramExercises()
        override suspend fun deleteAllProgramDays() = real.deleteAllProgramDays()
        override suspend fun deleteAllPrograms() = real.deleteAllPrograms()
        override suspend fun deleteAllExercises() = real.deleteAllExercises()
    }

    private fun repositoryFailingOn(step: String) = BackupRepositoryImpl(
        database = env.database,
        backupDao = FailsOn(env.database.backupDao(), step),
        timeProvider = env.time,
        zoneProvider = env.zone,
        appVersion = "test",
        ioDispatcher = mainDispatcherRule.dispatcher,
    )

    /**
     * Every insert of the restore, one at a time. The last one matters most — by then the wipe and
     * six inserts have all run — but a failure at any of them has to put everything back.
     */
    @Test
    fun `a restore that fails at any point leaves every original row in place`() = runTest {
        val exported = env.backupRepository.exportJson()
        val before = snapshot()
        assertTrue("nothing was seeded, so this proves nothing", before.values.all { (it as List<*>).isNotEmpty() })

        listOf(
            "insertExercises",
            "insertPrograms",
            "insertProgramDays",
            "insertProgramExercises",
            "insertSessions",
            "insertSessionExercises",
            "insertSetLogs",
        ).forEach { step ->
            val failed = runCatching { repositoryFailingOn(step).importJson(exported) }
            assertTrue(
                "the restore reported success despite failing at $step: $failed",
                failed.isFailure,
            )
            assertEquals(
                "a restore that died at $step took the training history with it",
                before,
                snapshot(),
            )
        }
    }

    /** And a restore that does not fail still replaces everything, so the above is not vacuous. */
    @Test
    fun `a restore that does not fail still replaces the data`() = runTest {
        val exported = env.backupRepository.exportJson()
        env.backupRepository.deleteAllData()
        assertTrue(snapshot().values.all { (it as List<*>).isEmpty() })

        env.backupRepository.importJson(exported)

        assertTrue(
            "the restore put nothing back, so the failure cases above mean nothing",
            snapshot().values.all { (it as List<*>).isNotEmpty() },
        )
    }
}
