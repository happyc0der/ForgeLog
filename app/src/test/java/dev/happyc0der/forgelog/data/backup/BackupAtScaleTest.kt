package dev.happyc0der.forgelog.data.backup

import dev.happyc0der.forgelog.domain.backup.BackupCheck
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

/**
 * A backup the size of a training life, through the whole export and restore.
 *
 * Every other test here works on a handful of rows, which says nothing about what happens to someone
 * who has used the app for years — and they are the person with the most to lose and the one most
 * likely to be restoring. The importer holds the entire file in memory as objects before it writes a
 * row, so the question is whether that is merely large or fatal, and whether a restore of that size
 * is quick enough that a user would not kill the app part way through it.
 *
 * Ten years of training at four sessions a week: two thousand sessions, six lifts each, four sets a
 * lift — a little under fifty thousand sets.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BackupAtScaleTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var env: TestEnvironment

    private val sessions = 2_000
    private val liftsPerSession = 6
    private val setsPerLift = 4

    @Before
    fun setUp() {
        env = TestEnvironment(mainDispatcherRule.dispatcher)
        env.time.now = 1_700_000_000_000L
    }

    @After
    fun tearDown() = env.tearDown()

    private fun aDecadeOfTraining(): BackupEnvelope {
        val exercises = (1..40).map { id ->
            ExerciseBackup(
                id = id.toLong(),
                name = "Lift $id",
                category = "push",
                defaultUnit = if (id % 3 == 0) "kg" else "lb",
                defaultPointers = "Keep the ribs down and the brace honest.",
            )
        }
        val sessionRows = mutableListOf<SessionBackup>()
        val exerciseRows = mutableListOf<SessionExerciseBackup>()
        val setRows = mutableListOf<SetLogBackup>()
        var sessionExerciseId = 0L
        var setId = 0L

        for (s in 1..sessions) {
            val startedAt = 1_000_000_000_000L + s * 86_400_000L / 2
            sessionRows += SessionBackup(
                id = s.toLong(),
                sessionName = "Session $s",
                startedAt = startedAt,
                completedAt = startedAt + 3_600_000,
                status = "completed",
                overallNotes = "Felt reasonable. Bar speed fine on the top sets.",
            )
            for (l in 1..liftsPerSession) {
                sessionExerciseId++
                val exerciseId = ((s + l) % 40 + 1).toLong()
                exerciseRows += SessionExerciseBackup(
                    id = sessionExerciseId,
                    sessionId = s.toLong(),
                    exerciseId = exerciseId,
                    displayNameSnapshot = "Lift $exerciseId",
                    exerciseOrder = l - 1,
                    startedAt = startedAt,
                )
                for (n in 1..setsPerLift) {
                    setId++
                    setRows += SetLogBackup(
                        id = setId,
                        sessionExerciseId = sessionExerciseId,
                        setNumber = n,
                        setType = "working",
                        reps = 5 + n,
                        weight = 100.0 + n * 2.5,
                        weightUnit = if (exerciseId % 3 == 0L) "kg" else "lb",
                        rpe = 8,
                        completed = true,
                        completedAt = startedAt + n * 120_000L,
                    )
                }
            }
        }
        return BackupEnvelope(
            appVersion = "1.0",
            databaseVersion = 3,
            exportedAtEpochMs = env.time.now,
            exercises = exercises,
            sessions = sessionRows,
            sessionExercises = exerciseRows,
            setLogs = setRows,
        )
    }

    @Test
    fun tenYearsOfTrainingSurvivesTheWholeRoundTrip() = runTest {
        val envelope = aDecadeOfTraining()
        assertEquals(sessions * liftsPerSession * setsPerLift, envelope.setLogs.size)

        val json = BackupSerializer.encode(envelope)
        assertTrue("the file is implausibly small", json.length > 1_000_000)

        val decoded = BackupSerializer.decode(json)
        assertTrue("a file this size was refused: $decoded", decoded is BackupCheck.Valid)

        val restored = env.backupRepository.importJson(json)
        assertTrue("the restore failed: $restored", restored is BackupCheck.Valid)
        val summary = (restored as BackupCheck.Valid).value
        assertEquals(sessions, summary.sessions)
        assertEquals(envelope.setLogs.size, summary.setLogs)

        // And it is all actually there, not merely reported.
        val dao = env.database.backupDao()
        assertEquals(envelope.setLogs.size, dao.allSetLogs().size)
        assertEquals(sessions, dao.allSessions().size)
        assertEquals(envelope.exercises.size, dao.allExercises().size)
    }

    /** Exporting what was just restored gives the same file back, at this size too. */
    @Test
    fun aDecadeExportsBackToWhatWasImported() = runTest {
        val json = BackupSerializer.encode(aDecadeOfTraining())
        env.backupRepository.importJson(json)

        val exported = env.backupRepository.exportJson()
        val reDecoded = BackupSerializer.decode(exported)

        assertTrue("the app could not read back its own export", reDecoded is BackupCheck.Valid)
        val envelope = (reDecoded as BackupCheck.Valid).value
        assertEquals(sessions, envelope.sessions.size)
        assertEquals(sessions * liftsPerSession * setsPerLift, envelope.setLogs.size)
    }

    /** A CSV of the same history is written without falling over either. */
    @Test
    fun aDecadeExportsToCsv() = runTest {
        env.backupRepository.importJson(BackupSerializer.encode(aDecadeOfTraining()))

        val csv = env.backupRepository.exportCsv(fromEpochMs = null, untilEpochMs = null)

        val rows = csv.split("\r\n").filter { it.isNotBlank() }
        assertEquals(
            "one header and one row per set",
            sessions * liftsPerSession * setsPerLift + 1,
            rows.size,
        )
    }
}
