package dev.happyc0der.forgelog.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.happyc0der.forgelog.data.local.entity.ExerciseEntity
import dev.happyc0der.forgelog.data.local.entity.ProgramDayEntity
import dev.happyc0der.forgelog.data.local.entity.ProgramExerciseEntity
import dev.happyc0der.forgelog.data.local.entity.SessionExerciseEntity
import dev.happyc0der.forgelog.data.local.entity.SetLogEntity
import dev.happyc0der.forgelog.data.local.entity.WorkoutProgramEntity
import dev.happyc0der.forgelog.data.local.entity.WorkoutSessionEntity
import dev.happyc0der.forgelog.domain.model.ExerciseCategory
import dev.happyc0der.forgelog.domain.model.ExerciseUnit
import dev.happyc0der.forgelog.domain.model.RestTimerType
import dev.happyc0der.forgelog.domain.model.SessionStatus
import dev.happyc0der.forgelog.domain.model.SetType
import java.util.concurrent.Executor

/**
 * In-memory Room for unit tests.
 *
 * Running the real database under Robolectric rather than mocking the DAOs is the point: these
 * tests execute the actual SQL, the actual `@Relation` fetches and the actual type converters, which
 * is where the bugs live.
 *
 * Pass [executor] wherever a test advances virtual time. Room's suspend DAO functions hop to the
 * database's own query and transaction executors, which are real background threads by default —
 * outside the test scheduler entirely. That makes `advanceUntilIdle()` return before writes have
 * landed, and turns every write-then-assert into a real race that passes or fails on timing. Handing
 * Room the test dispatcher puts its work back under the scheduler's control.
 */
internal fun inMemoryDatabase(executor: Executor? = null): ForgeLogDatabase {
    val context = ApplicationProvider.getApplicationContext<Context>()
    return Room.inMemoryDatabaseBuilder(context, ForgeLogDatabase::class.java)
        .allowMainThreadQueries()
        .apply {
            if (executor != null) {
                setQueryExecutor(executor)
                setTransactionExecutor(executor)
            }
        }
        .build()
}

internal fun exerciseEntity(
    id: Long = 0L,
    name: String = "Bench Press",
    category: ExerciseCategory = ExerciseCategory.PUSH,
    defaultUnit: ExerciseUnit = ExerciseUnit.LB,
    howToUrl: String? = null,
    defaultPointers: String? = null,
    isArchived: Boolean = false,
    createdAt: Long = 0L,
    updatedAt: Long = 0L,
) = ExerciseEntity(
    id = id,
    name = name,
    category = category,
    defaultUnit = defaultUnit,
    howToUrl = howToUrl,
    defaultPointers = defaultPointers,
    isArchived = isArchived,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

internal fun programEntity(
    id: Long = 0L,
    name: String = "PPL Strength",
    description: String? = null,
    color: String = "#A855F7",
    isArchived: Boolean = false,
    createdAt: Long = 0L,
    updatedAt: Long = 0L,
) = WorkoutProgramEntity(
    id = id,
    name = name,
    description = description,
    color = color,
    isArchived = isArchived,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

internal fun dayEntity(
    id: Long = 0L,
    programId: Long,
    name: String = "Push Day",
    dayOrder: Int = 0,
    notes: String? = null,
) = ProgramDayEntity(
    id = id,
    programId = programId,
    name = name,
    dayOrder = dayOrder,
    notes = notes,
)

internal fun programExerciseEntity(
    id: Long = 0L,
    programDayId: Long,
    exerciseId: Long,
    exerciseOrder: Int = 0,
    plannedSets: Int? = null,
    targetRepMin: Int? = null,
    targetRepMax: Int? = null,
    targetWeight: Double? = null,
    targetDurationSeconds: Int? = null,
    targetRestSeconds: Int? = null,
    defaultPointersOverride: String? = null,
    notes: String? = null,
) = ProgramExerciseEntity(
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

internal fun sessionEntity(
    id: Long = 0L,
    programId: Long? = null,
    programDayId: Long? = null,
    sessionName: String = "Push Day",
    startedAt: Long = 0L,
    completedAt: Long? = null,
    status: SessionStatus = SessionStatus.COMPLETED,
    overallFeeling: Int? = null,
    overallNotes: String? = null,
) = WorkoutSessionEntity(
    id = id,
    programDayId = programDayId,
    programId = programId,
    sessionName = sessionName,
    startedAt = startedAt,
    completedAt = completedAt,
    status = status,
    overallFeeling = overallFeeling,
    overallNotes = overallNotes,
    restBetweenExercisesSeconds = 0,
    expandedSessionExerciseId = null,
    restTimerType = RestTimerType.NONE,
    restTimerStartedAt = null,
    restTimerDurationSeconds = null,
    restTimerPausedRemainingSeconds = null,
    createdAt = startedAt,
    updatedAt = completedAt ?: startedAt,
)

internal fun sessionExerciseEntity(
    id: Long = 0L,
    sessionId: Long,
    exerciseId: Long? = null,
    displayNameSnapshot: String = "Bench Press",
    exerciseOrder: Int = 0,
    startedAt: Long = 0L,
    howToUrlSnapshot: String? = null,
    pointersSnapshot: String? = null,
    exerciseNotes: String? = null,
    feeling: Int? = null,
) = SessionExerciseEntity(
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
)

internal fun setLogEntity(
    id: Long = 0L,
    sessionExerciseId: Long,
    setNumber: Int = 1,
    setType: SetType = SetType.WORKING,
    reps: Int? = 5,
    weight: Double? = 100.0,
    weightUnit: ExerciseUnit = ExerciseUnit.LB,
    durationSeconds: Int? = null,
    distanceMeters: Double? = null,
    restAfterSetSeconds: Int? = null,
    rpe: Int? = null,
    rir: Int? = null,
    completed: Boolean = true,
    notes: String? = null,
    completedAt: Long? = 0L,
) = SetLogEntity(
    id = id,
    sessionExerciseId = sessionExerciseId,
    setNumber = setNumber,
    setType = setType,
    reps = reps,
    weight = weight,
    weightUnit = weightUnit,
    durationSeconds = durationSeconds,
    distanceMeters = distanceMeters,
    restAfterSetSeconds = restAfterSetSeconds,
    rpe = rpe,
    rir = rir,
    completed = completed,
    notes = notes,
    completedAt = completedAt,
)
