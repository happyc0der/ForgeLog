package dev.happyc0der.forgelog.data.backup

import dev.happyc0der.forgelog.domain.backup.BackupCheck
import dev.happyc0der.forgelog.domain.backup.BackupProblem
import dev.happyc0der.forgelog.domain.model.ExerciseCategory
import dev.happyc0der.forgelog.domain.model.ExerciseUnit
import dev.happyc0der.forgelog.domain.model.SessionSource
import dev.happyc0der.forgelog.domain.model.SessionStatus
import dev.happyc0der.forgelog.domain.model.SetType
import kotlinx.serialization.json.Json

/**
 * Reads and writes the backup file, and refuses anything it cannot fully trust.
 *
 * Pure and JVM-testable on purpose: this is the one place where a silent bug destroys real training
 * history, so it is worth being able to test exhaustively without a device. Import is validated
 * before a single row is written — a half-applied restore is worse than a refused one.
 */
object BackupSerializer {

    private val json = Json {
        prettyPrint = true
        // A file written by a slightly newer build may carry fields this one does not know. Ignoring
        // them lets an older app still restore the data it does understand, instead of refusing
        // outright over a field it would not have used anyway.
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encode(envelope: BackupEnvelope): String = json.encodeToString(envelope)

    fun decode(raw: String): BackupCheck<BackupEnvelope> {
        val envelope = try {
            json.decodeFromString<BackupEnvelope>(raw)
        } catch (error: Exception) {
            return BackupCheck.Invalid(
                BackupProblem.Unreadable(error.message ?: "Not a ForgeLog backup"),
            )
        }
        return validate(envelope)
    }

    fun validate(envelope: BackupEnvelope): BackupCheck<BackupEnvelope> {
        if (envelope.formatVersion > BackupEnvelope.CURRENT_FORMAT_VERSION) {
            return BackupCheck.Invalid(
                BackupProblem.UnsupportedFormat(
                    fileVersion = envelope.formatVersion,
                    supportedVersion = BackupEnvelope.CURRENT_FORMAT_VERSION,
                ),
            )
        }
        if (envelope.totalRows == 0) return BackupCheck.Invalid(BackupProblem.Empty)

        duplicateIds(envelope)?.let { return BackupCheck.Invalid(it) }
        invalidValues(envelope)?.let { return BackupCheck.Invalid(it) }
        danglingReferences(envelope)?.let { return BackupCheck.Invalid(it) }

        return BackupCheck.Valid(envelope)
    }

    private fun duplicateIds(envelope: BackupEnvelope): BackupProblem? {
        fun check(table: String, ids: List<Long>): BackupProblem? {
            val seen = mutableSetOf<Long>()
            ids.forEach { id ->
                if (!seen.add(id)) return BackupProblem.DuplicateId(table, id)
            }
            return null
        }
        return check("exercises", envelope.exercises.map { it.id })
            ?: check("programs", envelope.programs.map { it.id })
            ?: check("program_days", envelope.programDays.map { it.id })
            ?: check("program_exercises", envelope.programExercises.map { it.id })
            ?: check("sessions", envelope.sessions.map { it.id })
            ?: check("session_exercises", envelope.sessionExercises.map { it.id })
            ?: check("set_logs", envelope.setLogs.map { it.id })
    }

    private fun invalidValues(envelope: BackupEnvelope): BackupProblem? {
        envelope.exercises.forEach { exercise ->
            if (exercise.name.isBlank()) {
                return BackupProblem.InvalidValue("exercises", "name", "<blank>")
            }
            if (ExerciseCategory.entries.none { it.storageValue == exercise.category }) {
                return BackupProblem.InvalidValue("exercises", "category", exercise.category)
            }
            if (ExerciseUnit.entries.none { it.storageValue == exercise.defaultUnit }) {
                return BackupProblem.InvalidValue("exercises", "defaultUnit", exercise.defaultUnit)
            }
        }
        envelope.programs.forEach { program ->
            if (program.name.isBlank()) {
                return BackupProblem.InvalidValue("programs", "name", "<blank>")
            }
        }
        envelope.sessions.forEach { session ->
            if (SessionStatus.entries.none { it.storageValue == session.status }) {
                return BackupProblem.InvalidValue("sessions", "status", session.status)
            }
            if (SessionSource.entries.none { it.storageValue == session.source }) {
                return BackupProblem.InvalidValue("sessions", "source", session.source)
            }
            session.overallFeeling?.let { feeling ->
                if (feeling !in FEELING_RANGE) {
                    return BackupProblem.InvalidValue("sessions", "overallFeeling", feeling.toString())
                }
            }
        }
        envelope.sessionExercises.forEach { exercise ->
            exercise.feeling?.let { feeling ->
                if (feeling !in FEELING_RANGE) {
                    return BackupProblem.InvalidValue(
                        "session_exercises",
                        "feeling",
                        feeling.toString(),
                    )
                }
            }
        }
        envelope.setLogs.forEach { set ->
            if (SetType.entries.none { it.storageValue == set.setType }) {
                return BackupProblem.InvalidValue("set_logs", "setType", set.setType)
            }
            if (ExerciseUnit.entries.none { it.storageValue == set.weightUnit }) {
                return BackupProblem.InvalidValue("set_logs", "weightUnit", set.weightUnit)
            }
            if (set.setNumber < 1) {
                return BackupProblem.InvalidValue("set_logs", "setNumber", set.setNumber.toString())
            }
            set.rpe?.let { rpe ->
                if (rpe !in RPE_RANGE) {
                    return BackupProblem.InvalidValue("set_logs", "rpe", rpe.toString())
                }
            }
            set.rir?.let { rir ->
                if (rir !in RIR_RANGE) {
                    return BackupProblem.InvalidValue("set_logs", "rir", rir.toString())
                }
            }
        }
        return null
    }

    /**
     * Every foreign key has to resolve inside the file. A restore wipes the database first, so a row
     * pointing outside the file would be orphaned or rejected by the database halfway through.
     */
    private fun danglingReferences(envelope: BackupEnvelope): BackupProblem? {
        val exerciseIds = envelope.exercises.map { it.id }.toSet()
        val programIds = envelope.programs.map { it.id }.toSet()
        val dayIds = envelope.programDays.map { it.id }.toSet()
        val sessionIds = envelope.sessions.map { it.id }.toSet()
        val sessionExerciseIds = envelope.sessionExercises.map { it.id }.toSet()

        envelope.programDays.forEach { day ->
            if (day.programId !in programIds) {
                return BackupProblem.DanglingReference("program_days", "programId", day.programId)
            }
        }
        envelope.programExercises.forEach { item ->
            if (item.programDayId !in dayIds) {
                return BackupProblem.DanglingReference(
                    "program_exercises",
                    "programDayId",
                    item.programDayId,
                )
            }
            if (item.exerciseId !in exerciseIds) {
                return BackupProblem.DanglingReference(
                    "program_exercises",
                    "exerciseId",
                    item.exerciseId,
                )
            }
        }
        envelope.sessions.forEach { session ->
            session.programId?.let { id ->
                if (id !in programIds) {
                    return BackupProblem.DanglingReference("sessions", "programId", id)
                }
            }
            session.programDayId?.let { id ->
                if (id !in dayIds) {
                    return BackupProblem.DanglingReference("sessions", "programDayId", id)
                }
            }
        }
        envelope.sessionExercises.forEach { exercise ->
            if (exercise.sessionId !in sessionIds) {
                return BackupProblem.DanglingReference(
                    "session_exercises",
                    "sessionId",
                    exercise.sessionId,
                )
            }
            // A null exerciseId is legitimate: the library entry may have been deleted since, and the
            // session keeps its name snapshot precisely so that history survives that.
            exercise.exerciseId?.let { id ->
                if (id !in exerciseIds) {
                    return BackupProblem.DanglingReference("session_exercises", "exerciseId", id)
                }
            }
        }
        envelope.setLogs.forEach { set ->
            if (set.sessionExerciseId !in sessionExerciseIds) {
                return BackupProblem.DanglingReference(
                    "set_logs",
                    "sessionExerciseId",
                    set.sessionExerciseId,
                )
            }
        }
        return null
    }

    private val FEELING_RANGE = 1..5
    private val RPE_RANGE = 1..10
    private val RIR_RANGE = 0..10
}
