package dev.happyc0der.forgelog.domain.backup

/** Why a backup file was refused. Each case is something the UI can explain in one sentence. */
sealed interface BackupProblem {
    /** The file is not JSON, or not this app's JSON. */
    data class Unreadable(val detail: String) : BackupProblem

    /** Written by a newer ForgeLog than the one reading it. */
    data class UnsupportedFormat(val fileVersion: Int, val supportedVersion: Int) : BackupProblem

    /** A row points at a parent that is not in the file. */
    data class DanglingReference(val table: String, val field: String, val missingId: Long) :
        BackupProblem

    /** A value outside what the schema allows — an unknown enum, a negative set number. */
    data class InvalidValue(val table: String, val field: String, val value: String) : BackupProblem

    /** Two rows in the same table share an id. */
    data class DuplicateId(val table: String, val id: Long) : BackupProblem

    /**
     * Two rows collide on a uniqueness rule the database enforces.
     *
     * Caught here so a bad file is refused with an explanation, rather than reaching the insert and
     * throwing a constraint violation out of the import as an unhandled failure.
     */
    data class DuplicateKey(val table: String, val field: String, val value: String) :
        BackupProblem

    data object Empty : BackupProblem
}

/** A validated file, or the first reason it was refused. */
sealed interface BackupCheck<out T> {
    data class Valid<T>(val value: T) : BackupCheck<T>
    data class Invalid(val problem: BackupProblem) : BackupCheck<Nothing>
}
