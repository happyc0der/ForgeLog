package dev.happyc0der.forgelog.domain.sync

/**
 * A workout that came from somewhere other than this app.
 *
 * Provider-neutral on purpose: a FIT or TCX file exported from Garmin Connect, a Strava activity,
 * and a JSON backup all reduce to this shape. Keeping the model free of provider concepts is what
 * lets the adapter that produces them live outside the app's core.
 */
data class ExternalActivity(
    /** Stable id from the source. Together with [source] it makes re-importing idempotent. */
    val externalId: String,
    val source: String,
    val name: String,
    val startedAtEpochMs: Long,
    val durationSeconds: Int?,
    val exercises: List<ExternalExercise> = emptyList(),
)

data class ExternalExercise(
    val name: String,
    val sets: List<ExternalSet> = emptyList(),
)

data class ExternalSet(
    val reps: Int? = null,
    val weight: Double? = null,
    val weightUnit: String? = null,
    val durationSeconds: Int? = null,
    val distanceMeters: Double? = null,
)

/**
 * The seam a future Garmin or Strava import plugs into.
 *
 * Deliberately unimplemented. Declaring the port now — and keeping it in `domain`, which by test
 * cannot reach `data` or `ui` — means the eventual adapter is an addition rather than a refactor,
 * while the app still ships with no `INTERNET` permission and nothing to sync. The realistic first
 * implementation reads files the user picks through the Storage Access Framework, which needs no
 * account and no network at all.
 *
 * Note that adding any network-backed implementation changes what the app must declare on a Play
 * listing, which is a decision to take openly rather than a side effect of writing an adapter.
 */
interface ActivityImportPort {
    /** Activities available from this source since [sinceEpochMs]. */
    suspend fun fetchSince(sinceEpochMs: Long): List<ExternalActivity>
}
