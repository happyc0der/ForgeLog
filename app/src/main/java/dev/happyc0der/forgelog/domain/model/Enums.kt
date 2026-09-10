package dev.happyc0der.forgelog.domain.model

enum class ExerciseCategory(val storageValue: String) {
    PUSH("push"),
    PULL("pull"),
    LEGS("legs"),
    CORE("core"),
    CONDITIONING("conditioning"),
    MOBILITY("mobility"),
    OTHER("other");

    companion object {
        fun fromStorage(value: String): ExerciseCategory =
            entries.firstOrNull { it.storageValue == value }
                ?: throw IllegalArgumentException("Unknown exercise category: $value")
    }
}

enum class ExerciseUnit(val storageValue: String) {
    LB("lb"),
    KG("kg"),
    BODYWEIGHT("bodyweight"),
    SECONDS("seconds"),
    METERS("meters");

    val isLoadedWeight: Boolean
        get() = this == LB || this == KG

    companion object {
        fun fromStorage(value: String): ExerciseUnit =
            entries.firstOrNull { it.storageValue == value }
                ?: throw IllegalArgumentException("Unknown exercise unit: $value")
    }
}

enum class SessionStatus(val storageValue: String) {
    IN_PROGRESS("in_progress"),
    COMPLETED("completed"),
    ABANDONED("abandoned");

    companion object {
        fun fromStorage(value: String): SessionStatus =
            entries.firstOrNull { it.storageValue == value }
                ?: throw IllegalArgumentException("Unknown session status: $value")
    }
}

enum class SetType(val storageValue: String) {
    WARMUP("warmup"),
    WORKING("working"),
    DROP("drop"),
    FAILURE("failure"),
    CUSTOM("custom");

    companion object {
        fun fromStorage(value: String): SetType =
            entries.firstOrNull { it.storageValue == value }
                ?: throw IllegalArgumentException("Unknown set type: $value")
    }
}

/** Legacy Phase 4 manual rest-timer kind. No longer written as live data. */
enum class RestTimerType(val storageValue: String) {
    NONE("none"),
    SET("set"),
    EXERCISE("exercise");

    companion object {
        fun fromStorage(value: String): RestTimerType =
            entries.firstOrNull { it.storageValue == value }
                ?: RestTimerType.NONE
    }
}

/**
 * Where a session came from.
 *
 * Exists so an imported activity can be told apart from one logged by hand, which is what makes a
 * future Garmin or Strava import idempotent — re-importing the same activity updates its row
 * instead of duplicating it. Nothing imports anything yet; every session today is [MANUAL].
 */
enum class SessionSource(val storageValue: String) {
    MANUAL("manual"),
    IMPORTED("imported");

    companion object {
        fun fromStorage(value: String): SessionSource =
            entries.firstOrNull { it.storageValue == value } ?: MANUAL
    }
}
