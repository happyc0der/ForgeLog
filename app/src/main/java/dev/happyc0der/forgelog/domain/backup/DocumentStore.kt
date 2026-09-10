package dev.happyc0der.forgelog.domain.backup

/**
 * Reads and writes a document the user picked.
 *
 * An interface so the backup flow can be tested without the Storage Access Framework, and so the
 * ViewModel never touches a ContentResolver. The opaque [DocumentHandle] keeps Android's `Uri` out of
 * the domain entirely.
 */
interface DocumentStore {
    suspend fun readText(handle: DocumentHandle): Result<String>
    suspend fun writeText(handle: DocumentHandle, content: String): Result<Unit>
}

/** Opaque reference to a user-picked document. */
interface DocumentHandle
