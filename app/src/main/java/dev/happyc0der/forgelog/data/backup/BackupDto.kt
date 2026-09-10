package dev.happyc0der.forgelog.data.backup

import kotlinx.serialization.Serializable

/**
 * The JSON backup format.
 *
 * These DTOs are deliberately *not* the Room entities. Serializing entities directly would tie the
 * file format to the database schema, so every future column rename would silently break every
 * backup a user had already taken. Keeping a separate shape means a schema change is a mapping
 * change here, and old files keep loading.
 *
 * [formatVersion] is the file format, which moves independently of [databaseVersion]. A file from a
 * newer format is refused rather than half-read.
 */
@Serializable
data class BackupEnvelope(
    val formatVersion: Int = CURRENT_FORMAT_VERSION,
    val appVersion: String = "",
    val databaseVersion: Int = 0,
    val exportedAtEpochMs: Long = 0L,
    val exercises: List<ExerciseBackup> = emptyList(),
    val programs: List<ProgramBackup> = emptyList(),
    val programDays: List<ProgramDayBackup> = emptyList(),
    val programExercises: List<ProgramExerciseBackup> = emptyList(),
    val sessions: List<SessionBackup> = emptyList(),
    val sessionExercises: List<SessionExerciseBackup> = emptyList(),
    val setLogs: List<SetLogBackup> = emptyList(),
) {
    val totalRows: Int
        get() = exercises.size + programs.size + programDays.size + programExercises.size +
            sessions.size + sessionExercises.size + setLogs.size

    companion object {
        /** Bump only when the shape changes in a way older readers cannot handle. */
        const val CURRENT_FORMAT_VERSION = 1
    }
}

@Serializable
data class ExerciseBackup(
    val id: Long,
    val name: String,
    val category: String,
    val defaultUnit: String,
    val howToUrl: String? = null,
    val defaultPointers: String? = null,
    val isArchived: Boolean = false,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
)

@Serializable
data class ProgramBackup(
    val id: Long,
    val name: String,
    val description: String? = null,
    val color: String,
    val isArchived: Boolean = false,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
)

@Serializable
data class ProgramDayBackup(
    val id: Long,
    val programId: Long,
    val name: String,
    val dayOrder: Int = 0,
    val notes: String? = null,
)

@Serializable
data class ProgramExerciseBackup(
    val id: Long,
    val programDayId: Long,
    val exerciseId: Long,
    val exerciseOrder: Int = 0,
    val plannedSets: Int? = null,
    val targetRepMin: Int? = null,
    val targetRepMax: Int? = null,
    val targetWeight: Double? = null,
    val targetDurationSeconds: Int? = null,
    val targetRestSeconds: Int? = null,
    val defaultPointersOverride: String? = null,
    val notes: String? = null,
)

@Serializable
data class SessionBackup(
    val id: Long,
    val programId: Long? = null,
    val programDayId: Long? = null,
    val sessionName: String,
    val startedAt: Long,
    val completedAt: Long? = null,
    val status: String,
    val overallFeeling: Int? = null,
    val overallNotes: String? = null,
    val expandedSessionExerciseId: Long? = null,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val source: String = "manual",
    val externalSource: String? = null,
    val externalId: String? = null,
)

@Serializable
data class SessionExerciseBackup(
    val id: Long,
    val sessionId: Long,
    val exerciseId: Long? = null,
    val displayNameSnapshot: String,
    val exerciseOrder: Int = 0,
    val startedAt: Long = 0L,
    val howToUrlSnapshot: String? = null,
    val pointersSnapshot: String? = null,
    val exerciseNotes: String? = null,
    val feeling: Int? = null,
    val plannedSets: Int? = null,
    val targetRepMin: Int? = null,
    val targetRepMax: Int? = null,
    val targetWeight: Double? = null,
    val targetDurationSeconds: Int? = null,
    val targetRestSeconds: Int? = null,
)

@Serializable
data class SetLogBackup(
    val id: Long,
    val sessionExerciseId: Long,
    val setNumber: Int,
    val setType: String,
    val reps: Int? = null,
    val weight: Double? = null,
    val weightUnit: String,
    val durationSeconds: Int? = null,
    val distanceMeters: Double? = null,
    val restAfterSetSeconds: Int? = null,
    val rpe: Int? = null,
    val rir: Int? = null,
    val completed: Boolean = false,
    val notes: String? = null,
    val completedAt: Long? = null,
)
