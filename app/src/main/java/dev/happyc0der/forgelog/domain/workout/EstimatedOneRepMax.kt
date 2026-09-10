package dev.happyc0der.forgelog.domain.workout

import dev.happyc0der.forgelog.domain.model.SetLog
import dev.happyc0der.forgelog.domain.model.SetType

object EstimatedOneRepMax {
    const val MIN_REPS = 1
    const val MAX_REPS = 10

    fun isValid(set: SetLog): Boolean {
        if (!set.completed) return false
        if (set.setType != SetType.WORKING && set.setType != SetType.FAILURE) return false
        val reps = set.reps ?: return false
        if (reps !in MIN_REPS..MAX_REPS) return false
        val weight = set.weight ?: return false
        if (weight <= 0.0) return false
        return set.weightUnit.isLoadedWeight
    }

    fun pounds(set: SetLog): Double? {
        if (!isValid(set)) return null
        val weightLb = set.weight!!.toPounds(set.weightUnit) ?: return null
        val reps = set.reps!!
        if (reps == 1) return weightLb
        return weightLb * (1.0 + reps / 30.0)
    }
}
