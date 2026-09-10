package dev.happyc0der.forgelog.data.backup

import androidx.room.withTransaction
import dev.happyc0der.forgelog.data.local.ForgeLogDatabase
import dev.happyc0der.forgelog.data.local.dao.BackupDao
import dev.happyc0der.forgelog.data.local.entity.ExerciseEntity
import dev.happyc0der.forgelog.data.local.entity.ProgramDayEntity
import dev.happyc0der.forgelog.data.local.entity.ProgramExerciseEntity
import dev.happyc0der.forgelog.data.local.entity.SessionExerciseEntity
import dev.happyc0der.forgelog.data.local.entity.SetLogEntity
import dev.happyc0der.forgelog.data.local.entity.WorkoutProgramEntity
import dev.happyc0der.forgelog.data.local.entity.WorkoutSessionEntity
import dev.happyc0der.forgelog.data.mapper.toDomain
import dev.happyc0der.forgelog.di.AppVersion
import dev.happyc0der.forgelog.di.IoDispatcher
import dev.happyc0der.forgelog.domain.backup.BackupCheck
import dev.happyc0der.forgelog.domain.backup.BackupRepository
import dev.happyc0der.forgelog.domain.backup.RestoreSummary
import dev.happyc0der.forgelog.domain.model.ExerciseCategory
import dev.happyc0der.forgelog.domain.model.ExerciseUnit
import dev.happyc0der.forgelog.domain.model.RestTimerType
import dev.happyc0der.forgelog.domain.model.SessionSource
import dev.happyc0der.forgelog.domain.model.SessionStatus
import dev.happyc0der.forgelog.domain.model.SetType
import dev.happyc0der.forgelog.domain.time.TimeProvider
import dev.happyc0der.forgelog.domain.time.ZoneProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackupRepositoryImpl @Inject constructor(
    private val database: ForgeLogDatabase,
    private val backupDao: BackupDao,
    private val timeProvider: TimeProvider,
    private val zoneProvider: ZoneProvider,
    @param:AppVersion private val appVersion: String,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : BackupRepository {

    override suspend fun exportJson(): String = withContext(ioDispatcher) {
        // Read inside a transaction so a session logged mid-export cannot land in the file without
        // its sets, which would fail the importer's own referential check later.
        val envelope = database.withTransaction {
            BackupEnvelope(
                appVersion = appVersion,
                databaseVersion = database.openHelper.readableDatabase.version,
                exportedAtEpochMs = timeProvider.nowEpochMs(),
                exercises = backupDao.allExercises().map { it.toBackup() },
                programs = backupDao.allPrograms().map { it.toBackup() },
                programDays = backupDao.allProgramDays().map { it.toBackup() },
                programExercises = backupDao.allProgramExercises().map { it.toBackup() },
                sessions = backupDao.allSessions().map { it.toBackup() },
                sessionExercises = backupDao.allSessionExercises().map { it.toBackup() },
                setLogs = backupDao.allSetLogs().map { it.toBackup() },
            )
        }
        BackupSerializer.encode(envelope)
    }

    override suspend fun importJson(raw: String): BackupCheck<RestoreSummary> =
        withContext(ioDispatcher) {
            when (val decoded = BackupSerializer.decode(raw)) {
                is BackupCheck.Invalid -> decoded
                is BackupCheck.Valid -> {
                    val envelope = decoded.value
                    database.withTransaction {
                        // Wipe and replace. Ids are preserved exactly as exported, which keeps every
                        // foreign key intact without an id-remapping pass — safe precisely because
                        // the table is empty first.
                        backupDao.deleteAllSetLogs()
                        backupDao.deleteAllSessionExercises()
                        backupDao.deleteAllSessions()
                        backupDao.deleteAllProgramExercises()
                        backupDao.deleteAllProgramDays()
                        backupDao.deleteAllPrograms()
                        backupDao.deleteAllExercises()

                        backupDao.insertExercises(envelope.exercises.map { it.toEntity() })
                        backupDao.insertPrograms(envelope.programs.map { it.toEntity() })
                        backupDao.insertProgramDays(envelope.programDays.map { it.toEntity() })
                        backupDao.insertProgramExercises(envelope.programExercises.map { it.toEntity() })
                        backupDao.insertSessions(envelope.sessions.map { it.toEntity() })
                        backupDao.insertSessionExercises(
                            envelope.sessionExercises.map { it.toEntity() },
                        )
                        backupDao.insertSetLogs(envelope.setLogs.map { it.toEntity() })
                    }
                    BackupCheck.Valid(
                        RestoreSummary(
                            exercises = envelope.exercises.size,
                            programs = envelope.programs.size,
                            sessions = envelope.sessions.size,
                            setLogs = envelope.setLogs.size,
                        ),
                    )
                }
            }
        }

    override suspend fun exportCsv(fromEpochMs: Long?, untilEpochMs: Long?): String =
        withContext(ioDispatcher) {
            val details = backupDao
                .sessionDetailsBetween(fromEpochMs, untilEpochMs)
                .map { it.toDomain() }
            CsvExporter.export(details, zoneProvider.zone())
        }

    override suspend fun deleteAllData() = withContext(ioDispatcher) {
        database.withTransaction {
            backupDao.deleteAllSetLogs()
            backupDao.deleteAllSessionExercises()
            backupDao.deleteAllSessions()
            backupDao.deleteAllProgramExercises()
            backupDao.deleteAllProgramDays()
            backupDao.deleteAllPrograms()
            backupDao.deleteAllExercises()
        }
    }
}

private fun ExerciseEntity.toBackup() = ExerciseBackup(
    id = id,
    name = name,
    category = category.storageValue,
    defaultUnit = defaultUnit.storageValue,
    howToUrl = howToUrl,
    defaultPointers = defaultPointers,
    isArchived = isArchived,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

private fun ExerciseBackup.toEntity() = ExerciseEntity(
    id = id,
    name = name,
    category = ExerciseCategory.fromStorage(category),
    defaultUnit = ExerciseUnit.fromStorage(defaultUnit),
    howToUrl = howToUrl,
    defaultPointers = defaultPointers,
    isArchived = isArchived,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

private fun WorkoutProgramEntity.toBackup() = ProgramBackup(
    id = id,
    name = name,
    description = description,
    color = color,
    isArchived = isArchived,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

private fun ProgramBackup.toEntity() = WorkoutProgramEntity(
    id = id,
    name = name,
    description = description,
    color = color,
    isArchived = isArchived,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

private fun ProgramDayEntity.toBackup() = ProgramDayBackup(
    id = id,
    programId = programId,
    name = name,
    dayOrder = dayOrder,
    notes = notes,
)

private fun ProgramDayBackup.toEntity() = ProgramDayEntity(
    id = id,
    programId = programId,
    name = name,
    dayOrder = dayOrder,
    notes = notes,
)

private fun ProgramExerciseEntity.toBackup() = ProgramExerciseBackup(
    id = id,
    programDayId = programDayId,
    exerciseId = exerciseId,
    exerciseOrder = exerciseOrder,
    plannedSets = plannedSets,
    targetRepMin = targetRepMin,
    targetRepMax = targetRepMax,
    targetWeight = targetWeight,
    targetDurationSeconds = targetDurationSeconds,
    targetRestSeconds = targetRestSeconds,
    defaultPointersOverride = defaultPointersOverride,
    notes = notes,
)

private fun ProgramExerciseBackup.toEntity() = ProgramExerciseEntity(
    id = id,
    programDayId = programDayId,
    exerciseId = exerciseId,
    exerciseOrder = exerciseOrder,
    plannedSets = plannedSets,
    targetRepMin = targetRepMin,
    targetRepMax = targetRepMax,
    targetWeight = targetWeight,
    targetDurationSeconds = targetDurationSeconds,
    targetRestSeconds = targetRestSeconds,
    defaultPointersOverride = defaultPointersOverride,
    notes = notes,
)

private fun WorkoutSessionEntity.toBackup() = SessionBackup(
    id = id,
    programId = programId,
    programDayId = programDayId,
    sessionName = sessionName,
    startedAt = startedAt,
    completedAt = completedAt,
    status = status.storageValue,
    overallFeeling = overallFeeling,
    overallNotes = overallNotes,
    expandedSessionExerciseId = expandedSessionExerciseId,
    createdAt = createdAt,
    updatedAt = updatedAt,
    source = source.storageValue,
    externalSource = externalSource,
    externalId = externalId,
)

/**
 * The legacy rest-timer columns are not in the backup format. They have never held live data, and
 * writing them into every user's file would make them permanent.
 */
private fun SessionBackup.toEntity() = WorkoutSessionEntity(
    id = id,
    programDayId = programDayId,
    programId = programId,
    sessionName = sessionName,
    startedAt = startedAt,
    completedAt = completedAt,
    status = SessionStatus.fromStorage(status),
    overallFeeling = overallFeeling,
    overallNotes = overallNotes,
    restBetweenExercisesSeconds = 0,
    expandedSessionExerciseId = expandedSessionExerciseId,
    restTimerType = RestTimerType.NONE,
    restTimerStartedAt = null,
    restTimerDurationSeconds = null,
    restTimerPausedRemainingSeconds = null,
    createdAt = createdAt,
    updatedAt = updatedAt,
    source = SessionSource.fromStorage(source),
    externalSource = externalSource,
    externalId = externalId,
)

private fun SessionExerciseEntity.toBackup() = SessionExerciseBackup(
    id = id,
    sessionId = sessionId,
    exerciseId = exerciseId,
    displayNameSnapshot = displayNameSnapshot,
    exerciseOrder = exerciseOrder,
    startedAt = startedAt,
    howToUrlSnapshot = howToUrlSnapshot,
    pointersSnapshot = pointersSnapshot,
    exerciseNotes = exerciseNotes,
    feeling = feeling,
    plannedSets = plannedSets,
    targetRepMin = targetRepMin,
    targetRepMax = targetRepMax,
    targetWeight = targetWeight,
    targetDurationSeconds = targetDurationSeconds,
    targetRestSeconds = targetRestSeconds,
)

private fun SessionExerciseBackup.toEntity() = SessionExerciseEntity(
    id = id,
    sessionId = sessionId,
    exerciseId = exerciseId,
    displayNameSnapshot = displayNameSnapshot,
    exerciseOrder = exerciseOrder,
    startedAt = startedAt,
    completedAt = null,
    restBeforeExerciseSeconds = null,
    howToUrlSnapshot = howToUrlSnapshot,
    pointersSnapshot = pointersSnapshot,
    exerciseNotes = exerciseNotes,
    feeling = feeling,
    plannedSets = plannedSets,
    targetRepMin = targetRepMin,
    targetRepMax = targetRepMax,
    targetWeight = targetWeight,
    targetDurationSeconds = targetDurationSeconds,
    targetRestSeconds = targetRestSeconds,
)

private fun SetLogEntity.toBackup() = SetLogBackup(
    id = id,
    sessionExerciseId = sessionExerciseId,
    setNumber = setNumber,
    setType = setType.storageValue,
    reps = reps,
    weight = weight,
    weightUnit = weightUnit.storageValue,
    durationSeconds = durationSeconds,
    distanceMeters = distanceMeters,
    restAfterSetSeconds = restAfterSetSeconds,
    rpe = rpe,
    rir = rir,
    completed = completed,
    notes = notes,
    completedAt = completedAt,
)

private fun SetLogBackup.toEntity() = SetLogEntity(
    id = id,
    sessionExerciseId = sessionExerciseId,
    setNumber = setNumber,
    setType = SetType.fromStorage(setType),
    reps = reps,
    weight = weight,
    weightUnit = ExerciseUnit.fromStorage(weightUnit),
    durationSeconds = durationSeconds,
    distanceMeters = distanceMeters,
    restAfterSetSeconds = restAfterSetSeconds,
    rpe = rpe,
    rir = rir,
    completed = completed,
    notes = notes,
    completedAt = completedAt,
)
