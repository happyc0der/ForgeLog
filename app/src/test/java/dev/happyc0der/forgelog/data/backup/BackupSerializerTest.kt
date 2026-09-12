package dev.happyc0der.forgelog.data.backup

import dev.happyc0der.forgelog.domain.backup.BackupCheck
import dev.happyc0der.forgelog.domain.backup.BackupProblem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The importer is the only code in the app that can destroy training history, so it is tested for
 * what it *refuses* at least as hard as for what it accepts.
 */
class BackupSerializerTest {

    private fun fullEnvelope() = BackupEnvelope(
        appVersion = "1.0",
        databaseVersion = 3,
        exportedAtEpochMs = 1_700_000_000_000L,
        exercises = listOf(
            ExerciseBackup(
                id = 1,
                name = "Bench Press",
                category = "push",
                defaultUnit = "lb",
                howToUrl = "https://example.com/bench",
                defaultPointers = "elbows tucked",
            ),
        ),
        programs = listOf(ProgramBackup(id = 1, name = "PPL", color = "#A855F7")),
        programDays = listOf(ProgramDayBackup(id = 1, programId = 1, name = "Push Day")),
        programExercises = listOf(
            ProgramExerciseBackup(
                id = 1,
                programDayId = 1,
                exerciseId = 1,
                plannedSets = 4,
                targetRepMin = 6,
                targetRepMax = 8,
                targetWeight = 185.0,
                targetRestSeconds = 150,
            ),
        ),
        sessions = listOf(
            SessionBackup(
                id = 1,
                programId = 1,
                programDayId = 1,
                sessionName = "PPL · Push Day",
                startedAt = 1_000,
                completedAt = 5_000,
                status = "completed",
                overallFeeling = 4,
                overallNotes = "strong",
            ),
        ),
        sessionExercises = listOf(
            SessionExerciseBackup(
                id = 1,
                sessionId = 1,
                exerciseId = 1,
                displayNameSnapshot = "Bench Press",
                feeling = 5,
                plannedSets = 4,
                targetWeight = 185.0,
            ),
        ),
        setLogs = listOf(
            SetLogBackup(
                id = 1,
                sessionExerciseId = 1,
                setNumber = 1,
                setType = "working",
                reps = 6,
                weight = 185.0,
                weightUnit = "lb",
                rpe = 8,
                rir = 2,
                completed = true,
                notes = "clean",
                completedAt = 2_000,
            ),
        ),
    )

    private fun valid(envelope: BackupEnvelope): BackupEnvelope {
        val check = BackupSerializer.validate(envelope)
        assertTrue("Expected valid, got $check", check is BackupCheck.Valid)
        return (check as BackupCheck.Valid).value
    }

    private fun problem(envelope: BackupEnvelope): BackupProblem {
        val check = BackupSerializer.validate(envelope)
        assertTrue("Expected invalid, got $check", check is BackupCheck.Invalid)
        return (check as BackupCheck.Invalid).problem
    }

    @Test
    fun `a full export round-trips unchanged`() {
        val original = fullEnvelope()
        val decoded = BackupSerializer.decode(BackupSerializer.encode(original))
        assertTrue(decoded is BackupCheck.Valid)
        assertEquals(original, (decoded as BackupCheck.Valid).value)
    }

    @Test
    fun `every table survives the round-trip`() {
        val decoded = valid(fullEnvelope())
        assertEquals(1, decoded.exercises.size)
        assertEquals(1, decoded.programs.size)
        assertEquals(1, decoded.programDays.size)
        assertEquals(1, decoded.programExercises.size)
        assertEquals(1, decoded.sessions.size)
        assertEquals(1, decoded.sessionExercises.size)
        assertEquals(1, decoded.setLogs.size)
        assertEquals(7, decoded.totalRows)
    }

    @Test
    fun `unknown fields from a newer build are ignored rather than fatal`() {
        val raw = """
            {
              "formatVersion": 1,
              "appVersion": "9.9",
              "somethingAddedLater": {"nested": true},
              "exercises": [
                {"id": 1, "name": "Bench", "category": "push", "defaultUnit": "lb", "futureField": 7}
              ]
            }
        """.trimIndent()
        val decoded = BackupSerializer.decode(raw)
        assertTrue("Expected valid, got $decoded", decoded is BackupCheck.Valid)
        assertEquals("Bench", (decoded as BackupCheck.Valid).value.exercises.single().name)
    }

    @Test
    fun `a newer format version is refused instead of half-read`() {
        val problem = problem(fullEnvelope().copy(formatVersion = 99))
        assertTrue(problem is BackupProblem.UnsupportedFormat)
        assertEquals(99, (problem as BackupProblem.UnsupportedFormat).fileVersion)
    }

    @Test
    fun `an older format version is still accepted`() {
        valid(fullEnvelope().copy(formatVersion = 1))
    }

    @Test
    fun `text that is not JSON is refused with a reason`() {
        val decoded = BackupSerializer.decode("this is not json")
        assertTrue(decoded is BackupCheck.Invalid)
        assertTrue((decoded as BackupCheck.Invalid).problem is BackupProblem.Unreadable)
    }

    @Test
    fun `a truncated file is refused`() {
        val truncated = BackupSerializer.encode(fullEnvelope()).take(80)
        val decoded = BackupSerializer.decode(truncated)
        assertTrue(decoded is BackupCheck.Invalid)
        assertTrue((decoded as BackupCheck.Invalid).problem is BackupProblem.Unreadable)
    }

    @Test
    fun `an empty file is refused rather than silently wiping everything`() {
        // This is the important one: decoding "{}" succeeds as JSON, and importing it would delete
        // every row the user has and restore nothing.
        val decoded = BackupSerializer.decode("{}")
        assertTrue(decoded is BackupCheck.Invalid)
        assertEquals(BackupProblem.Empty, (decoded as BackupCheck.Invalid).problem)
    }

    @Test
    fun `a day pointing at a missing program is refused`() {
        val problem = problem(
            fullEnvelope().copy(programDays = listOf(ProgramDayBackup(id = 1, programId = 42, name = "Orphan"))),
        )
        assertEquals(
            BackupProblem.DanglingReference("program_days", "programId", 42),
            problem,
        )
    }

    @Test
    fun `a set pointing at a missing session exercise is refused`() {
        val problem = problem(
            fullEnvelope().copy(
                setLogs = listOf(
                    SetLogBackup(
                        id = 1,
                        sessionExerciseId = 999,
                        setNumber = 1,
                        setType = "working",
                        weightUnit = "lb",
                    ),
                ),
            ),
        )
        assertEquals(
            BackupProblem.DanglingReference("set_logs", "sessionExerciseId", 999),
            problem,
        )
    }

    @Test
    fun `a session exercise with no library exercise is allowed`() {
        // Null means the library entry was deleted since. The name snapshot is what keeps the history
        // readable, so refusing this would make a legitimate export unrestorable.
        valid(
            fullEnvelope().copy(
                sessionExercises = listOf(
                    SessionExerciseBackup(
                        id = 1,
                        sessionId = 1,
                        exerciseId = null,
                        displayNameSnapshot = "Deleted Lift",
                    ),
                ),
            ),
        )
    }

    @Test
    fun `a session exercise pointing at a missing exercise id is refused`() {
        val problem = problem(
            fullEnvelope().copy(
                sessionExercises = listOf(
                    SessionExerciseBackup(
                        id = 1,
                        sessionId = 1,
                        exerciseId = 777,
                        displayNameSnapshot = "Ghost",
                    ),
                ),
            ),
        )
        assertEquals(
            BackupProblem.DanglingReference("session_exercises", "exerciseId", 777),
            problem,
        )
    }

    @Test
    fun `unknown enum values are refused by name`() {
        assertEquals(
            BackupProblem.InvalidValue("exercises", "category", "interpretive-dance"),
            problem(
                fullEnvelope().copy(
                    exercises = listOf(
                        ExerciseBackup(id = 1, name = "X", category = "interpretive-dance", defaultUnit = "lb"),
                    ),
                ),
            ),
        )
        assertEquals(
            BackupProblem.InvalidValue("sessions", "status", "paused"),
            problem(fullEnvelope().copy(sessions = listOf(session(status = "paused")))),
        )
        assertEquals(
            BackupProblem.InvalidValue("set_logs", "weightUnit", "stones"),
            problem(fullEnvelope().copy(setLogs = listOf(set(weightUnit = "stones")))),
        )
    }

    /*
     * The measurements were checked for their kind but never their size. Nothing the app writes can
     * be negative -- the numeric fields refuse a minus sign -- and none exceeds six digits, so these
     * only appear in a file edited by hand. Letting them through means showing them as fact: a
     * weight of 1e308 imported cleanly and then rendered as a 309-digit number on the summary.
     */

    @Test
    fun `a negative measurement is refused`() {
        val envelope = fullEnvelope()
        val problem = problem(
            envelope.copy(setLogs = envelope.setLogs.map { it.copy(weight = -5.0) }),
        )
        assertTrue(problem is BackupProblem.InvalidValue)
        assertEquals("weight", (problem as BackupProblem.InvalidValue).field)
    }

    @Test
    fun `a measurement too big to have been typed is refused`() {
        val envelope = fullEnvelope()
        assertTrue(
            problem(envelope.copy(setLogs = envelope.setLogs.map { it.copy(weight = 1e308) }))
                is BackupProblem.InvalidValue,
        )
        assertTrue(
            problem(envelope.copy(setLogs = envelope.setLogs.map { it.copy(reps = 1_000_000) }))
                is BackupProblem.InvalidValue,
        )
    }

    @Test
    fun `the largest values the fields accept are still allowed`() {
        val envelope = fullEnvelope()
        valid(
            envelope.copy(
                setLogs = envelope.setLogs.map {
                    it.copy(weight = 999_999.99, reps = 999_999, restAfterSetSeconds = 999_999)
                },
            ),
        )
    }

    @Test
    fun `a planned target cannot be negative either`() {
        val envelope = fullEnvelope()
        assertTrue(
            problem(
                envelope.copy(
                    programExercises = envelope.programExercises.map { it.copy(targetWeight = -1.0) },
                ),
            ) is BackupProblem.InvalidValue,
        )
        assertTrue(
            problem(
                envelope.copy(
                    sessionExercises = envelope.sessionExercises.map { it.copy(targetRestSeconds = -30) },
                ),
            ) is BackupProblem.InvalidValue,
        )
    }

    @Test
    fun `zero is a measurement, not a refusal`() {
        val envelope = fullEnvelope()
        valid(
            envelope.copy(
                setLogs = envelope.setLogs.map {
                    it.copy(weight = 0.0, reps = 0, durationSeconds = 0, distanceMeters = 0.0)
                },
            ),
        )
    }

    @Test
    fun `out-of-range subjective values are refused`() {
        assertEquals(
            BackupProblem.InvalidValue("sessions", "overallFeeling", "9"),
            problem(fullEnvelope().copy(sessions = listOf(session(feeling = 9)))),
        )
        assertEquals(
            BackupProblem.InvalidValue("set_logs", "rpe", "11"),
            problem(fullEnvelope().copy(setLogs = listOf(set(rpe = 11)))),
        )
        assertEquals(
            BackupProblem.InvalidValue("set_logs", "rir", "-1"),
            problem(fullEnvelope().copy(setLogs = listOf(set(rir = -1)))),
        )
    }

    @Test
    fun `a set number below one is refused`() {
        assertEquals(
            BackupProblem.InvalidValue("set_logs", "setNumber", "0"),
            problem(fullEnvelope().copy(setLogs = listOf(set(setNumber = 0)))),
        )
    }

    @Test
    fun `a blank name is refused`() {
        assertEquals(
            BackupProblem.InvalidValue("exercises", "name", "<blank>"),
            problem(
                fullEnvelope().copy(
                    exercises = listOf(ExerciseBackup(id = 1, name = "  ", category = "push", defaultUnit = "lb")),
                ),
            ),
        )
    }

    @Test
    fun `duplicate ids within a table are refused`() {
        assertEquals(
            BackupProblem.DuplicateId("exercises", 1),
            problem(
                fullEnvelope().copy(
                    exercises = listOf(
                        ExerciseBackup(id = 1, name = "A", category = "push", defaultUnit = "lb"),
                        ExerciseBackup(id = 1, name = "B", category = "pull", defaultUnit = "kg"),
                    ),
                ),
            ),
        )
    }

    @Test
    fun `an ad-hoc session with no program is valid`() {
        valid(
            fullEnvelope().copy(
                sessions = listOf(
                    SessionBackup(
                        id = 1,
                        programId = null,
                        programDayId = null,
                        sessionName = "Ad-hoc",
                        startedAt = 1,
                        status = "completed",
                    ),
                ),
                programExercises = emptyList(),
            ),
        )
    }

    @Test
    fun `notes containing JSON-hostile characters survive the round-trip`() {
        val nasty = "quote \" backslash \\ newline \n unicode ü emoji 🏋 comma ,"
        val envelope = fullEnvelope().copy(setLogs = listOf(set(notes = nasty)))
        val decoded = valid(
            (BackupSerializer.decode(BackupSerializer.encode(envelope)) as BackupCheck.Valid).value,
        )
        assertEquals(nasty, decoded.setLogs.single().notes)
    }

    private fun session(status: String = "completed", feeling: Int? = null) = SessionBackup(
        id = 1,
        programId = 1,
        programDayId = 1,
        sessionName = "S",
        startedAt = 1,
        status = status,
        overallFeeling = feeling,
    )

    private fun set(
        setNumber: Int = 1,
        weightUnit: String = "lb",
        rpe: Int? = null,
        rir: Int? = null,
        notes: String? = null,
    ) = SetLogBackup(
        id = 1,
        sessionExerciseId = 1,
        setNumber = setNumber,
        setType = "working",
        weightUnit = weightUnit,
        rpe = rpe,
        rir = rir,
        notes = notes,
    )
}
