package dev.happyc0der.forgelog.data.backup

import dev.happyc0der.forgelog.data.local.dayEntity
import dev.happyc0der.forgelog.data.local.exerciseEntity
import dev.happyc0der.forgelog.data.local.programEntity
import dev.happyc0der.forgelog.data.local.programExerciseEntity
import dev.happyc0der.forgelog.data.local.sessionEntity
import dev.happyc0der.forgelog.data.local.sessionExerciseEntity
import dev.happyc0der.forgelog.data.local.setLogEntity
import dev.happyc0der.forgelog.domain.backup.BackupCheck
import dev.happyc0der.forgelog.domain.backup.BackupProblem
import dev.happyc0der.forgelog.domain.model.ExerciseUnit
import dev.happyc0der.forgelog.domain.model.SessionStatus
import dev.happyc0der.forgelog.domain.model.SetType
import dev.happyc0der.forgelog.testing.MainDispatcherRule
import dev.happyc0der.forgelog.testing.TestEnvironment
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
 * Export, wipe, restore, compare — against a real database.
 *
 * This is the manual check every user is told to perform before trusting the app with data they care
 * about, done automatically. The serializer tests prove the format is validated; this proves the
 * round-trip actually returns the same training history.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BackupRoundTripTest {

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

    /** A database with every table populated, including the awkward cases. */
    private suspend fun seed() {
        val programDao = env.database.programDao()
        val exerciseDao = env.database.exerciseDao()
        val sessionDao = env.database.workoutSessionDao()

        val benchId = exerciseDao.upsert(
            exerciseEntity(name = "Bench Press", howToUrl = "https://example.com", defaultPointers = "tuck"),
        )
        val archivedId = exerciseDao.upsert(exerciseEntity(name = "Old Lift", isArchived = true))
        val programId = programDao.insertProgram(programEntity(name = "PPL", description = "split"))
        val dayId = programDao.insertDay(dayEntity(programId = programId, name = "Push Day", notes = "heavy"))
        programDao.insertProgramExercise(
            programExerciseEntity(
                programDayId = dayId,
                exerciseId = benchId,
                plannedSets = 4,
                targetRepMin = 6,
                targetRepMax = 8,
                targetWeight = 185.0,
                targetRestSeconds = 150,
                notes = "work up",
            ),
        )

        val sessionId = sessionDao.insertSession(
            sessionEntity(
                sessionName = "PPL · Push Day",
                programId = programId,
                programDayId = dayId,
                startedAt = 1_000L,
                completedAt = 4_000L,
                status = SessionStatus.COMPLETED,
                overallFeeling = 4,
                overallNotes = "felt good, comma, and \"quotes\"",
            ),
        )
        val sessionExerciseId = sessionDao.insertSessionExercise(
            sessionExerciseEntity(
                sessionId = sessionId,
                exerciseId = benchId,
                displayNameSnapshot = "Bench Press",
                exerciseNotes = "elbow twinge",
                feeling = 5,
            ),
        )
        sessionDao.upsertSetLog(
            setLogEntity(
                sessionExerciseId = sessionExerciseId,
                setNumber = 1,
                setType = SetType.WARMUP,
                reps = 10,
                weight = 95.0,
            ),
        )
        sessionDao.upsertSetLog(
            setLogEntity(
                sessionExerciseId = sessionExerciseId,
                setNumber = 2,
                reps = 6,
                weight = 60.0,
                weightUnit = ExerciseUnit.KG,
                rpe = 8,
                rir = 2,
                notes = "clean",
            ),
        )
        // An exercise whose library row is gone: exerciseId null, name snapshot only.
        val orphanExerciseId = sessionDao.insertSessionExercise(
            sessionExerciseEntity(
                sessionId = sessionId,
                exerciseId = null,
                displayNameSnapshot = "Deleted Lift",
                exerciseOrder = 1,
            ),
        )
        sessionDao.upsertSetLog(setLogEntity(sessionExerciseId = orphanExerciseId, reps = 12, weight = null))
        // An ad-hoc session with no program at all.
        sessionDao.insertSession(
            sessionEntity(sessionName = "Ad-hoc", startedAt = 6_000L, completedAt = 7_000L),
        )
        assertNotNull(archivedId)
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

    @Test
    fun `export then wipe then restore returns exactly the same rows`() = runTest {
        val before = snapshot()
        val json = env.backupRepository.exportJson()

        env.backupRepository.deleteAllData()
        assertEquals(emptyList<Any>(), env.database.backupDao().allSessions())

        val result = env.backupRepository.importJson(json)

        assertTrue("Import failed: $result", result is BackupCheck.Valid)
        assertEquals(before, snapshot())
    }

    @Test
    fun `the restore summary counts what came back`() = runTest {
        val json = env.backupRepository.exportJson()
        env.backupRepository.deleteAllData()

        val summary = (env.backupRepository.importJson(json) as BackupCheck.Valid).value

        assertEquals(2, summary.exercises)
        assertEquals(1, summary.programs)
        assertEquals(2, summary.sessions)
        assertEquals(3, summary.setLogs)
    }

    @Test
    fun `restoring over existing data replaces it rather than merging`() = runTest {
        val json = env.backupRepository.exportJson()
        val sessionCountBefore = env.database.backupDao().allSessions().size

        // Import the same file again without wiping first.
        val result = env.backupRepository.importJson(json)

        assertTrue(result is BackupCheck.Valid)
        assertEquals(sessionCountBefore, env.database.backupDao().allSessions().size)
    }

    @Test
    fun `a rejected file leaves existing data untouched`() = runTest {
        val before = snapshot()

        val result = env.backupRepository.importJson("{\"formatVersion\": 999}")

        assertTrue(result is BackupCheck.Invalid)
        // Nothing was deleted on the way to discovering the file was bad.
        assertEquals(before, snapshot())
    }

    @Test
    fun `garbage input leaves existing data untouched`() = runTest {
        val before = snapshot()
        val result = env.backupRepository.importJson("not json at all")
        assertTrue((result as BackupCheck.Invalid).problem is BackupProblem.Unreadable)
        assertEquals(before, snapshot())
    }

    @Test
    fun `an empty document is refused and does not wipe anything`() = runTest {
        val before = snapshot()
        val result = env.backupRepository.importJson("{}")
        assertEquals(BackupProblem.Empty, (result as BackupCheck.Invalid).problem)
        assertEquals(before, snapshot())
    }

    @Test
    fun `the exported file records the database version and app version`() = runTest {
        val decoded = BackupSerializer.decode(env.backupRepository.exportJson())
        val envelope = (decoded as BackupCheck.Valid).value
        assertEquals(3, envelope.databaseVersion)
        assertEquals("test", envelope.appVersion)
        assertEquals(5_000L, envelope.exportedAtEpochMs)
        assertEquals(BackupEnvelope.CURRENT_FORMAT_VERSION, envelope.formatVersion)
    }

    @Test
    fun `deleting all data clears every table`() = runTest {
        env.backupRepository.deleteAllData()
        val dao = env.database.backupDao()
        assertEquals(0, dao.allExercises().size)
        assertEquals(0, dao.allPrograms().size)
        assertEquals(0, dao.allProgramDays().size)
        assertEquals(0, dao.allProgramExercises().size)
        assertEquals(0, dao.allSessions().size)
        assertEquals(0, dao.allSessionExercises().size)
        assertEquals(0, dao.allSetLogs().size)
    }

    @Test
    fun `a session exercise with no library row survives the round-trip`() = runTest {
        val json = env.backupRepository.exportJson()
        env.backupRepository.deleteAllData()
        env.backupRepository.importJson(json)

        val orphan = env.database.backupDao().allSessionExercises()
            .first { it.displayNameSnapshot == "Deleted Lift" }
        assertEquals(null, orphan.exerciseId)
    }

    @Test
    fun `kilogram sets keep their unit through the round-trip`() = runTest {
        val json = env.backupRepository.exportJson()
        env.backupRepository.deleteAllData()
        env.backupRepository.importJson(json)

        val kgSet = env.database.backupDao().allSetLogs().first { it.weightUnit == ExerciseUnit.KG }
        assertEquals(60.0, kgSet.weight ?: 0.0, 0.001)
        assertEquals(6, kgSet.reps)
    }

    @Test
    fun `CSV export covers the requested window only`() = runTest {
        // The PPL session starts at 1000, the ad-hoc one at 6000.
        val narrow = env.backupRepository.exportCsv(fromEpochMs = 0L, untilEpochMs = 5_000L)
        assertTrue(narrow.contains("PPL · Push Day"))
        assertTrue(!narrow.contains("Ad-hoc"))

        val wide = env.backupRepository.exportCsv(fromEpochMs = null, untilEpochMs = null)
        assertTrue(wide.contains("PPL · Push Day"))
        assertTrue(wide.contains("Ad-hoc"))
    }

    @Test
    fun `CSV export quotes a session note containing a comma and quotes`() = runTest {
        val csv = env.backupRepository.exportCsv(null, null)
        assertTrue(csv.contains("\"felt good, comma, and \"\"quotes\"\"\""))
    }
}
