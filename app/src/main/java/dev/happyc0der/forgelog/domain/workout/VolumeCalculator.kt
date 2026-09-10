package dev.happyc0der.forgelog.domain.workout

import dev.happyc0der.forgelog.domain.model.SetLog
import dev.happyc0der.forgelog.domain.model.SetType

data class WorkoutVolume(
    val loadLb: Double,
    val completedSetCount: Int,
    val totalReps: Int,
    val totalDurationSeconds: Int,
    val totalDistanceMeters: Double,
)

object VolumeCalculator {
    fun calculate(
        sets: List<SetLog>,
        includeWarmup: Boolean = false,
    ): WorkoutVolume {
        val counted = sets.filter { set ->
            set.completed && (includeWarmup || set.setType != SetType.WARMUP)
        }
        val loadLb = counted.sumOf { set ->
            val reps = set.reps ?: return@sumOf 0.0
            val weight = set.weight ?: return@sumOf 0.0
            if (reps <= 0 || weight <= 0.0) return@sumOf 0.0
            val pounds = weight.toPounds(set.weightUnit) ?: return@sumOf 0.0
            reps * pounds
        }
        return WorkoutVolume(
            loadLb = loadLb,
            completedSetCount = counted.size,
            totalReps = counted.sumOf { it.reps?.coerceAtLeast(0) ?: 0 },
            totalDurationSeconds = counted.sumOf { it.durationSeconds?.coerceAtLeast(0) ?: 0 },
            totalDistanceMeters = counted.sumOf { it.distanceMeters?.coerceAtLeast(0.0) ?: 0.0 },
        )
    }

    fun sessionVolume(
        sessionExercises: List<List<SetLog>>,
        includeWarmup: Boolean = false,
    ): WorkoutVolume {
        val volumes = sessionExercises.map { calculate(it, includeWarmup) }
        return WorkoutVolume(
            loadLb = volumes.sumOf { it.loadLb },
            completedSetCount = volumes.sumOf { it.completedSetCount },
            totalReps = volumes.sumOf { it.totalReps },
            totalDurationSeconds = volumes.sumOf { it.totalDurationSeconds },
            totalDistanceMeters = volumes.sumOf { it.totalDistanceMeters },
        )
    }
}
