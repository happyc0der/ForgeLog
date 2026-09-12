package dev.happyc0der.forgelog.data.backup

import dev.happyc0der.forgelog.domain.backup.BackupCheck
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the backup file actually looks like, pinned.
 *
 * Every other test in this package round-trips through the same serializer, so all of them would
 * still pass if a field were renamed -- both ends would simply agree on the new name, and the app
 * would keep working perfectly on files it had written since the rename. The damage lands on the
 * user's existing backup: `ignoreUnknownKeys` means the old key is skipped in silence, and because
 * almost every field has a default, the row imports with the weight, the reps or the notes quietly
 * gone rather than failing loudly.
 *
 * So these two tests deliberately do not use the serializer to decide what is correct. One names
 * every key out loud; the other decodes a file typed out by hand, as a copy of what version 1.0
 * wrote. Renaming a field or adding one without a default fails them, which is the point: the fix
 * is an @SerialName keeping the old key, not a new expectation here.
 */
class BackupFormatContractTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun keysOf(table: String): Set<String> {
        val encoded = BackupSerializer.encode(fullEnvelope())
        val root = json.parseToJsonElement(encoded).jsonObject
        val rows = requireNotNull(root[table]) { "The export has no $table" } as JsonArray
        assertTrue("$table needs a row for its keys to be checked", rows.isNotEmpty())
        return (rows.first() as JsonObject).keys
    }

    @Test
    fun theEnvelopeKeysAreTheFormat() {
        val root = json.parseToJsonElement(BackupSerializer.encode(fullEnvelope())).jsonObject
        assertEquals(
            setOf(
                "formatVersion", "appVersion", "databaseVersion", "exportedAtEpochMs",
                "exercises", "programs", "programDays", "programExercises",
                "sessions", "sessionExercises", "setLogs",
            ),
            root.keys,
        )
    }

    @Test
    fun theExerciseKeysAreTheFormat() = assertEquals(
        setOf(
            "id", "name", "category", "defaultUnit", "howToUrl", "defaultPointers",
            "isArchived", "createdAt", "updatedAt",
        ),
        keysOf("exercises"),
    )

    @Test
    fun theProgramKeysAreTheFormat() = assertEquals(
        setOf("id", "name", "description", "color", "isArchived", "createdAt", "updatedAt"),
        keysOf("programs"),
    )

    @Test
    fun theProgramDayKeysAreTheFormat() = assertEquals(
        setOf("id", "programId", "name", "dayOrder", "notes"),
        keysOf("programDays"),
    )

    @Test
    fun theProgramExerciseKeysAreTheFormat() = assertEquals(
        setOf(
            "id", "programDayId", "exerciseId", "exerciseOrder", "plannedSets",
            "targetRepMin", "targetRepMax", "targetWeight", "targetDurationSeconds",
            "targetRestSeconds", "defaultPointersOverride", "notes",
        ),
        keysOf("programExercises"),
    )

    @Test
    fun theSessionKeysAreTheFormat() = assertEquals(
        setOf(
            "id", "programId", "programDayId", "sessionName", "startedAt", "completedAt",
            "status", "overallFeeling", "overallNotes", "expandedSessionExerciseId",
            "createdAt", "updatedAt", "source", "externalSource", "externalId",
        ),
        keysOf("sessions"),
    )

    @Test
    fun theSessionExerciseKeysAreTheFormat() = assertEquals(
        setOf(
            "id", "sessionId", "exerciseId", "displayNameSnapshot", "exerciseOrder",
            "startedAt", "howToUrlSnapshot", "pointersSnapshot", "exerciseNotes", "feeling",
            "plannedSets", "targetRepMin", "targetRepMax", "targetWeight",
            "targetDurationSeconds", "targetRestSeconds",
        ),
        keysOf("sessionExercises"),
    )

    @Test
    fun theSetLogKeysAreTheFormat() = assertEquals(
        setOf(
            "id", "sessionExerciseId", "setNumber", "setType", "reps", "weight", "weightUnit",
            "durationSeconds", "distanceMeters", "restAfterSetSeconds", "rpe", "rir",
            "completed", "notes", "completedAt",
        ),
        keysOf("setLogs"),
    )

    /**
     * A whole file, written out by hand rather than produced by the code under test.
     *
     * One session: a warm-up and a working set of bench, and a timed plank whose library entry has
     * since been deleted. If this stops decoding, every backup a user already holds has stopped
     * decoding too.
     */
    private val fileFromVersionOne = """
        {
          "formatVersion": 1,
          "appVersion": "1.0",
          "databaseVersion": 3,
          "exportedAtEpochMs": 1700000000000,
          "exercises": [
            {
              "id": 7,
              "name": "Bench Press",
              "category": "push",
              "defaultUnit": "lb",
              "howToUrl": "https://example.com/bench",
              "defaultPointers": "elbows tucked",
              "isArchived": false,
              "createdAt": 100,
              "updatedAt": 200
            }
          ],
          "programs": [
            {
              "id": 3,
              "name": "PPL",
              "description": "push pull legs",
              "color": "#A855F7",
              "isArchived": false,
              "createdAt": 100,
              "updatedAt": 200
            }
          ],
          "programDays": [
            { "id": 5, "programId": 3, "name": "Push Day", "dayOrder": 0, "notes": "heavy" }
          ],
          "programExercises": [
            {
              "id": 9,
              "programDayId": 5,
              "exerciseId": 7,
              "exerciseOrder": 0,
              "plannedSets": 4,
              "targetRepMin": 6,
              "targetRepMax": 8,
              "targetWeight": 185.5,
              "targetDurationSeconds": null,
              "targetRestSeconds": 150,
              "defaultPointersOverride": null,
              "notes": "work up"
            }
          ],
          "sessions": [
            {
              "id": 11,
              "programId": 3,
              "programDayId": 5,
              "sessionName": "PPL · Push Day",
              "startedAt": 1000,
              "completedAt": 5000,
              "status": "completed",
              "overallFeeling": 4,
              "overallNotes": "felt strong",
              "expandedSessionExerciseId": null,
              "createdAt": 1000,
              "updatedAt": 5000,
              "source": "manual",
              "externalSource": null,
              "externalId": null
            }
          ],
          "sessionExercises": [
            {
              "id": 13,
              "sessionId": 11,
              "exerciseId": 7,
              "displayNameSnapshot": "Bench Press",
              "exerciseOrder": 0,
              "startedAt": 1000,
              "howToUrlSnapshot": "https://example.com/bench",
              "pointersSnapshot": "elbows tucked",
              "exerciseNotes": "elbow twinge",
              "feeling": 5,
              "plannedSets": 4,
              "targetRepMin": 6,
              "targetRepMax": 8,
              "targetWeight": 185.5,
              "targetDurationSeconds": null,
              "targetRestSeconds": 150
            },
            {
              "id": 14,
              "sessionId": 11,
              "exerciseId": null,
              "displayNameSnapshot": "Plank",
              "exerciseOrder": 1,
              "startedAt": 1000,
              "howToUrlSnapshot": null,
              "pointersSnapshot": null,
              "exerciseNotes": null,
              "feeling": null,
              "plannedSets": 3,
              "targetRepMin": null,
              "targetRepMax": null,
              "targetWeight": null,
              "targetDurationSeconds": 45,
              "targetRestSeconds": 60
            }
          ],
          "setLogs": [
            {
              "id": 21,
              "sessionExerciseId": 13,
              "setNumber": 1,
              "setType": "warmup",
              "reps": 10,
              "weight": 95.0,
              "weightUnit": "lb",
              "durationSeconds": null,
              "distanceMeters": null,
              "restAfterSetSeconds": 90,
              "rpe": null,
              "rir": null,
              "completed": true,
              "notes": null,
              "completedAt": 1500
            },
            {
              "id": 22,
              "sessionExerciseId": 13,
              "setNumber": 2,
              "setType": "working",
              "reps": 6,
              "weight": 185.5,
              "weightUnit": "lb",
              "durationSeconds": null,
              "distanceMeters": null,
              "restAfterSetSeconds": 180,
              "rpe": 8,
              "rir": 2,
              "completed": true,
              "notes": "clean",
              "completedAt": 3000
            },
            {
              "id": 23,
              "sessionExerciseId": 14,
              "setNumber": 1,
              "setType": "working",
              "reps": null,
              "weight": null,
              "weightUnit": "lb",
              "durationSeconds": 45,
              "distanceMeters": null,
              "restAfterSetSeconds": 60,
              "rpe": null,
              "rir": null,
              "completed": true,
              "notes": "shaky",
              "completedAt": 4500
            }
          ]
        }
    """.trimIndent()

    @Test
    fun aFileWrittenByVersionOneStillDecodesFieldForField() {
        val check = BackupSerializer.decode(fileFromVersionOne)

        assertTrue("Version 1.0's own backup no longer loads: $check", check is BackupCheck.Valid)
        val envelope = (check as BackupCheck.Valid).value

        assertEquals(1, envelope.formatVersion)
        assertEquals(
            ExerciseBackup(
                id = 7,
                name = "Bench Press",
                category = "push",
                defaultUnit = "lb",
                howToUrl = "https://example.com/bench",
                defaultPointers = "elbows tucked",
                isArchived = false,
                createdAt = 100,
                updatedAt = 200,
            ),
            envelope.exercises.single(),
        )
        assertEquals(
            ProgramBackup(
                id = 3,
                name = "PPL",
                description = "push pull legs",
                color = "#A855F7",
                isArchived = false,
                createdAt = 100,
                updatedAt = 200,
            ),
            envelope.programs.single(),
        )
        assertEquals(
            ProgramDayBackup(id = 5, programId = 3, name = "Push Day", dayOrder = 0, notes = "heavy"),
            envelope.programDays.single(),
        )
        assertEquals(
            ProgramExerciseBackup(
                id = 9,
                programDayId = 5,
                exerciseId = 7,
                exerciseOrder = 0,
                plannedSets = 4,
                targetRepMin = 6,
                targetRepMax = 8,
                targetWeight = 185.5,
                targetDurationSeconds = null,
                targetRestSeconds = 150,
                defaultPointersOverride = null,
                notes = "work up",
            ),
            envelope.programExercises.single(),
        )
        assertEquals(
            SessionBackup(
                id = 11,
                programId = 3,
                programDayId = 5,
                sessionName = "PPL · Push Day",
                startedAt = 1000,
                completedAt = 5000,
                status = "completed",
                overallFeeling = 4,
                overallNotes = "felt strong",
                expandedSessionExerciseId = null,
                createdAt = 1000,
                updatedAt = 5000,
                source = "manual",
                externalSource = null,
                externalId = null,
            ),
            envelope.sessions.single(),
        )
        assertEquals(
            SessionExerciseBackup(
                id = 14,
                sessionId = 11,
                exerciseId = null,
                displayNameSnapshot = "Plank",
                exerciseOrder = 1,
                startedAt = 1000,
                plannedSets = 3,
                targetDurationSeconds = 45,
                targetRestSeconds = 60,
            ),
            envelope.sessionExercises.last(),
        )
        assertEquals(
            SetLogBackup(
                id = 22,
                sessionExerciseId = 13,
                setNumber = 2,
                setType = "working",
                reps = 6,
                weight = 185.5,
                weightUnit = "lb",
                restAfterSetSeconds = 180,
                rpe = 8,
                rir = 2,
                completed = true,
                notes = "clean",
                completedAt = 3000,
            ),
            envelope.setLogs[1],
        )
    }

    /** The same file, re-exported, must still be the same file. */
    @Test
    fun whatVersionOneWroteIsWhatThisVersionWrites() {
        val decoded = (BackupSerializer.decode(fileFromVersionOne) as BackupCheck.Valid).value

        val reDecoded = BackupSerializer.decode(BackupSerializer.encode(decoded))

        assertEquals(decoded, (reDecoded as BackupCheck.Valid).value)
    }

    private fun fullEnvelope() = BackupEnvelope(
        appVersion = "1.0",
        databaseVersion = 3,
        exportedAtEpochMs = 1_700_000_000_000L,
        exercises = listOf(
            ExerciseBackup(id = 1, name = "Bench Press", category = "push", defaultUnit = "lb"),
        ),
        programs = listOf(ProgramBackup(id = 1, name = "PPL", color = "#A855F7")),
        programDays = listOf(ProgramDayBackup(id = 1, programId = 1, name = "Push Day")),
        programExercises = listOf(
            ProgramExerciseBackup(id = 1, programDayId = 1, exerciseId = 1),
        ),
        sessions = listOf(
            SessionBackup(
                id = 1,
                sessionName = "PPL · Push Day",
                startedAt = 1_000,
                status = "completed",
            ),
        ),
        sessionExercises = listOf(
            SessionExerciseBackup(id = 1, sessionId = 1, displayNameSnapshot = "Bench Press"),
        ),
        setLogs = listOf(
            SetLogBackup(
                id = 1,
                sessionExerciseId = 1,
                setNumber = 1,
                setType = "working",
                weightUnit = "lb",
            ),
        ),
    )
}
