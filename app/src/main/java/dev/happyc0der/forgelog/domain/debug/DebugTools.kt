package dev.happyc0der.forgelog.domain.debug

/**
 * Development-only actions, declared in main so the UI can ask for them, implemented per build type.
 *
 * The real implementation lives in the `debug` source set and the release build gets a no-op, so
 * sample data cannot reach a release APK even by mistake. A `BuildConfig.DEBUG` check alone would
 * still compile the seeding code into the shipped binary.
 */
interface DebugTools {
    /** False in release builds, where nothing here is offered to the user. */
    val isAvailable: Boolean

    /** Writes sample programs and several weeks of sessions. Never called automatically. */
    suspend fun seedSampleData()
}
