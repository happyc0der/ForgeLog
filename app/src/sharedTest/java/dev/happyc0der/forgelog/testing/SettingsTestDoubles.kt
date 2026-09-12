package dev.happyc0der.forgelog.testing

import dev.happyc0der.forgelog.domain.backup.DocumentHandle
import dev.happyc0der.forgelog.domain.backup.DocumentStore
import dev.happyc0der.forgelog.domain.debug.DebugTools

/**
 * Stand-ins for the two things a Settings screen needs and a Compose test cannot have.
 *
 * Shared rather than declared per test file: two private top-level classes of the same name in one
 * package compile to the same JVM class, so the second file to want them is a redeclaration.
 */

/** The debug-only seeder, which the release build does not carry at all. */
internal class NoDebugTools(override val isAvailable: Boolean = false) : DebugTools {
    override suspend fun seedSampleData() = Unit
}

/** The Storage Access Framework, with nothing behind it. */
internal class NoDocumentStore : DocumentStore {
    override suspend fun readText(handle: DocumentHandle): Result<String> = Result.success("")
    override suspend fun writeText(handle: DocumentHandle, content: String): Result<Unit> =
        Result.success(Unit)
}
