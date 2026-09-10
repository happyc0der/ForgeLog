package dev.happyc0der.forgelog.domain.history

import dev.happyc0der.forgelog.domain.model.SessionStatus

/**
 * Every way history can be narrowed. All fields are optional and combine with AND.
 *
 * [fromEpochMs] is inclusive and [untilEpochMs] exclusive, matching the half-open convention used
 * by [dev.happyc0der.forgelog.domain.time.WeekBoundary] so a date range built from week or day
 * boundaries lines up exactly.
 */
data class HistoryFilter(
    val query: String = "",
    val status: SessionStatus? = null,
    val programId: Long? = null,
    val programDayId: Long? = null,
    val exerciseId: Long? = null,
    val fromEpochMs: Long? = null,
    val untilEpochMs: Long? = null,
) {
    val isActive: Boolean
        get() = query.isNotBlank() || status != null || programId != null ||
            programDayId != null || exerciseId != null ||
            fromEpochMs != null || untilEpochMs != null

    /**
     * Trimmed once here so the SQL never sees stray whitespace as a search term, and with `LIKE`'s
     * wildcards escaped so they are searched for rather than obeyed.
     *
     * Unescaped, "50%" matched any session containing "50", and a lone "_" matched everything.
     * The escape character is a backslash, matching the `ESCAPE '\'` the query declares; it has to
     * be escaped first or it would escape the escapes.
     */
    val normalizedQuery: String
        get() = query.trim()
            .replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_")
}

/** An exercise that appears somewhere in logged history. */
data class LoggedExercise(
    val exerciseId: Long,
    val displayName: String,
)
