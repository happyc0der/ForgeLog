package dev.happyc0der.forgelog.debug

import dev.happyc0der.forgelog.domain.model.Exercise
import dev.happyc0der.forgelog.domain.model.ExerciseCategory
import dev.happyc0der.forgelog.domain.model.ExerciseUnit
import dev.happyc0der.forgelog.domain.model.ProgramDay
import dev.happyc0der.forgelog.domain.model.ProgramExercise
import dev.happyc0der.forgelog.domain.model.SessionStatus
import dev.happyc0der.forgelog.domain.model.SetLog
import dev.happyc0der.forgelog.domain.model.SetType
import dev.happyc0der.forgelog.domain.model.WorkoutProgram
import dev.happyc0der.forgelog.domain.model.WorkoutSession
import dev.happyc0der.forgelog.domain.repository.ExerciseRepository
import dev.happyc0der.forgelog.domain.repository.ProgramRepository
import dev.happyc0der.forgelog.domain.repository.WorkoutSessionRepository
import dev.happyc0der.forgelog.domain.time.TimeProvider
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

/**
 * Sample data, for the QA build only.
 *
 * Lives in the `qa` source set, so it cannot be compiled into the debug build that holds real
 * training, or into a release build, even by accident — a runtime `BuildConfig.DEBUG` check alone
 * would still ship the code. It is never invoked
 * automatically: an empty app on first launch should stay empty, and a seeder that runs on its own
 * would be indistinguishable from a bug.
 *
 * It writes twelve weeks of progressive sessions so the Analytics charts, trends and personal bests
 * have something real to draw, which is otherwise tedious to produce by hand.
 */
@Singleton
class SeedData @Inject constructor(
    private val exerciseRepository: ExerciseRepository,
    private val programRepository: ProgramRepository,
    private val sessionRepository: WorkoutSessionRepository,
    private val timeProvider: TimeProvider,
) {

    suspend fun seed(weeks: Int = DEFAULT_WEEKS) {
        val exercises = SEED_EXERCISES.associate { template ->
            template.name to exerciseRepository.upsert(
                Exercise(
                    name = template.name,
                    category = template.category,
                    defaultUnit = template.unit,
                    howToUrl = template.howToUrl,
                    defaultPointers = template.pointers,
                    isArchived = false,
                    createdAt = 0L,
                    updatedAt = 0L,
                ),
            )
        }

        val programId = programRepository.upsertProgram(
            WorkoutProgram(
                name = "PPL Strength",
                description = "Push, pull and legs, three days a week.",
                color = "#A855F7",
                createdAt = 0L,
                updatedAt = 0L,
            ),
        )

        val days = SEED_DAYS.mapIndexed { index, day ->
            val dayId = programRepository.upsertDay(
                ProgramDay(programId = programId, name = day.name, dayOrder = index, notes = day.notes),
            )
            day.exercises.forEachIndexed { exerciseIndex, planned ->
                programRepository.upsertProgramExercise(
                    ProgramExercise(
                        programDayId = dayId,
                        exerciseId = exercises.getValue(planned.exerciseName),
                        exerciseOrder = exerciseIndex,
                        plannedSets = planned.sets,
                        targetRepMin = planned.repMin,
                        targetRepMax = planned.repMax,
                        targetWeight = planned.startingWeight,
                        targetRestSeconds = planned.restSeconds,
                    ),
                )
            }
            dayId to day
        }

        // Deterministic, so repeated seeding produces comparable data rather than noise.
        val random = Random(SEED)
        val now = timeProvider.nowEpochMs()

        for (week in (weeks - 1) downTo 0) {
            days.forEachIndexed { dayIndex, (dayId, day) ->
                val startedAt = now - week * WEEK_MS - (DAYS_BETWEEN_SESSIONS * dayIndex) * DAY_MS
                val durationMs = (50L + random.nextInt(25)) * 60_000L
                val sessionId = sessionRepository.upsertSession(
                    WorkoutSession(
                        programId = programId,
                        programDayId = dayId,
                        sessionName = "PPL Strength · ${day.name}",
                        startedAt = startedAt,
                        completedAt = startedAt + durationMs,
                        status = SessionStatus.COMPLETED,
                        overallFeeling = 3 + random.nextInt(3),
                        overallNotes = if (random.nextInt(4) == 0) "Felt strong." else null,
                        createdAt = startedAt,
                        updatedAt = startedAt + durationMs,
                    ),
                )

                day.exercises.forEachIndexed { exerciseIndex, planned ->
                    val sessionExerciseId = sessionRepository.upsertSessionExercise(
                        dev.happyc0der.forgelog.domain.model.SessionExercise(
                            sessionId = sessionId,
                            exerciseId = exercises.getValue(planned.exerciseName),
                            displayNameSnapshot = planned.exerciseName,
                            exerciseOrder = exerciseIndex,
                            startedAt = startedAt,
                            plannedSets = planned.sets,
                            targetRepMin = planned.repMin,
                            targetRepMax = planned.repMax,
                            targetWeight = planned.startingWeight,
                            targetRestSeconds = planned.restSeconds,
                        ),
                    )

                    // Load creeps up week by week, so the trends actually trend.
                    val progression = (weeks - 1 - week) * planned.weeklyIncrement
                    val workingWeight = planned.startingWeight?.plus(progression)

                    // Only a loaded lift gets a warm-up, and only then does it take set number 1.
                    // The working sets were numbered from 2 regardless, so a bodyweight or timed
                    // lift came out as "Set 2, Set 3, Set 4" with no first set -- which reads as a
                    // bug in the app when the sample data is there to check the app against.
                    val warmupSets = if (planned.startingWeight != null) 1 else 0
                    if (warmupSets == 1) {
                        sessionRepository.upsertSetLog(
                            warmupSet(sessionExerciseId, planned, startedAt),
                        )
                    }
                    repeat(planned.sets ?: 3) { setIndex ->
                        val reps = (planned.repMin ?: 8) + random.nextInt(2)
                        sessionRepository.upsertSetLog(
                            SetLog(
                                sessionExerciseId = sessionExerciseId,
                                setNumber = setIndex + warmupSets + 1,
                                setType = SetType.WORKING,
                                reps = if (planned.durationSeconds == null) reps else null,
                                weight = workingWeight,
                                weightUnit = planned.unit,
                                durationSeconds = planned.durationSeconds,
                                restAfterSetSeconds = planned.restSeconds,
                                rpe = 6 + random.nextInt(4),
                                completed = true,
                                completedAt = startedAt + (setIndex + 1) * 180_000L,
                            ),
                        )
                    }
                }
            }
        }
    }

    private fun warmupSet(sessionExerciseId: Long, planned: PlannedSeed, startedAt: Long) = SetLog(
        sessionExerciseId = sessionExerciseId,
        setNumber = 1,
        setType = SetType.WARMUP,
        reps = 10,
        weight = planned.startingWeight?.times(0.5),
        weightUnit = planned.unit,
        restAfterSetSeconds = 60,
        completed = true,
        completedAt = startedAt,
    )

    private companion object {
        const val DEFAULT_WEEKS = 12
        const val SEED = 20260311
        const val DAY_MS = 24L * 60L * 60L * 1000L
        const val WEEK_MS = 7L * DAY_MS
        const val DAYS_BETWEEN_SESSIONS = 2
    }
}

private data class ExerciseSeed(
    val name: String,
    val category: ExerciseCategory,
    val unit: ExerciseUnit,
    val howToUrl: String? = null,
    val pointers: String? = null,
)

private data class PlannedSeed(
    val exerciseName: String,
    val sets: Int?,
    val repMin: Int?,
    val repMax: Int?,
    val startingWeight: Double?,
    val weeklyIncrement: Double = 0.0,
    val restSeconds: Int? = null,
    val durationSeconds: Int? = null,
    val unit: ExerciseUnit = ExerciseUnit.LB,
)

private data class DaySeed(
    val name: String,
    val notes: String?,
    val exercises: List<PlannedSeed>,
)

private val SEED_EXERCISES = listOf(
    ExerciseSeed("Bench Press", ExerciseCategory.PUSH, ExerciseUnit.LB, pointers = "Elbows tucked, feet planted."),
    ExerciseSeed("Overhead Press", ExerciseCategory.PUSH, ExerciseUnit.LB, pointers = "Brace hard, ribs down."),
    ExerciseSeed("Pull-up", ExerciseCategory.PULL, ExerciseUnit.BODYWEIGHT, pointers = "Full hang at the bottom."),
    ExerciseSeed("Barbell Row", ExerciseCategory.PULL, ExerciseUnit.LB, pointers = "Hinge to roughly 45 degrees."),
    ExerciseSeed("Squat", ExerciseCategory.LEGS, ExerciseUnit.LB, pointers = "Knees track over the toes."),
    ExerciseSeed("Deadlift", ExerciseCategory.LEGS, ExerciseUnit.LB, pointers = "Bar over mid-foot before the pull."),
    ExerciseSeed("Assisted Nordic Curl", ExerciseCategory.LEGS, ExerciseUnit.BODYWEIGHT, pointers = "Lower as slowly as you can control."),
    ExerciseSeed("Plank", ExerciseCategory.CORE, ExerciseUnit.SECONDS, pointers = "Squeeze glutes, neutral spine."),
)

private val SEED_DAYS = listOf(
    DaySeed(
        name = "Push Day",
        notes = "Press first, then accessories.",
        exercises = listOf(
            PlannedSeed("Bench Press", sets = 4, repMin = 5, repMax = 8, startingWeight = 135.0, weeklyIncrement = 5.0, restSeconds = 180),
            PlannedSeed("Overhead Press", sets = 3, repMin = 6, repMax = 10, startingWeight = 75.0, weeklyIncrement = 2.5, restSeconds = 120),
            PlannedSeed("Plank", sets = 3, repMin = null, repMax = null, startingWeight = null, restSeconds = 60, durationSeconds = 45, unit = ExerciseUnit.SECONDS),
        ),
    ),
    DaySeed(
        name = "Pull Day",
        notes = null,
        exercises = listOf(
            PlannedSeed("Pull-up", sets = 4, repMin = 5, repMax = 10, startingWeight = null, restSeconds = 150, unit = ExerciseUnit.BODYWEIGHT),
            PlannedSeed("Barbell Row", sets = 4, repMin = 6, repMax = 10, startingWeight = 115.0, weeklyIncrement = 5.0, restSeconds = 150),
        ),
    ),
    DaySeed(
        name = "Legs Day",
        notes = "Warm up the hips properly.",
        exercises = listOf(
            PlannedSeed("Squat", sets = 5, repMin = 3, repMax = 5, startingWeight = 185.0, weeklyIncrement = 10.0, restSeconds = 240),
            PlannedSeed("Deadlift", sets = 3, repMin = 3, repMax = 5, startingWeight = 225.0, weeklyIncrement = 10.0, restSeconds = 240),
            PlannedSeed("Assisted Nordic Curl", sets = 3, repMin = 5, repMax = 8, startingWeight = null, restSeconds = 120, unit = ExerciseUnit.BODYWEIGHT),
        ),
    ),
)
