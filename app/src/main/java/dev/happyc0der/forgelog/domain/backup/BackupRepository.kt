package dev.happyc0der.forgelog.domain.backup

/** Summary of what a restore brought back, so the UI can confirm something actually happened. */
data class RestoreSummary(
    val exercises: Int,
    val programs: Int,
    val sessions: Int,
    val setLogs: Int,
)

interface BackupRepository {
    /** Whole database as a JSON document. */
    suspend fun exportJson(): String

    /**
     * Replaces everything with the contents of [raw].
     *
     * Validated in full before anything is written, and applied in one transaction, so a bad file
     * leaves the existing data untouched rather than half-overwritten.
     */
    suspend fun importJson(raw: String): BackupCheck<RestoreSummary>

    /** Sessions in the half-open window `[fromEpochMs, untilEpochMs)` as CSV, one row per set. */
    suspend fun exportCsv(fromEpochMs: Long?, untilEpochMs: Long?): String

    suspend fun deleteAllData()
}
