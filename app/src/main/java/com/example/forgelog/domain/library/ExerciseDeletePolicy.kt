package com.example.forgelog.domain.library

object ExerciseDeletePolicy {
    fun canHardDelete(hasSessionHistory: Boolean): Boolean = !hasSessionHistory
}
