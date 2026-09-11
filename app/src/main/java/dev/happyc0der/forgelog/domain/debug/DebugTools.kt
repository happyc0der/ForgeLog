package dev.happyc0der.forgelog.domain.debug

/**
 * Development-only actions, declared in main so the UI can ask for them, implemented per build type.
 *
 * The real implementation lives in the `qa` source set; the debug and release builds get a no-op,
 * so sample data cannot reach an install that holds real training even by mistake. A
 * `BuildConfig.DEBUG` check alone would still compile the seeding code into those binaries.
 */
interface DebugTools {
    /** False outside the QA build, where nothing here is offered to the user. */
    val isAvailable: Boolean

    /** Writes sample programs and several weeks of sessions. Never called automatically. */
    suspend fun seedSampleData()
}
