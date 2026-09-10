package dev.happyc0der.forgelog.domain.library

object ExerciseDeletePolicy {
    fun canHardDelete(hasSessionHistory: Boolean): Boolean = !hasSessionHistory
}
