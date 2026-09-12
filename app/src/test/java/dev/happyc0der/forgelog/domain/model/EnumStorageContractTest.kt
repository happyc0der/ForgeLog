package dev.happyc0der.forgelog.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * The strings these enums are stored as, pinned.
 *
 * Every one is written into the database by a type converter and into the backup file by the
 * exporter, so they are data rather than identifiers. Renaming a constant is harmless; changing the
 * string beside it is not, and nothing in the app would say so: the write still succeeds, and the
 * damage shows up on the next read, when fromStorage meets a value it has never heard of and
 * throws. That surfaces as History and Home failing to load, for rows already on the user's phone.
 *
 * Adding a constant is fine and these tests will ask for it to be listed. Changing an existing
 * string needs a migration for the rows already written with the old one.
 */
class EnumStorageContractTest {

    @Test
    fun exerciseCategoriesAreStoredAsThese() = assertEquals(
        mapOf(
            "push" to ExerciseCategory.PUSH,
            "pull" to ExerciseCategory.PULL,
            "legs" to ExerciseCategory.LEGS,
            "core" to ExerciseCategory.CORE,
            "conditioning" to ExerciseCategory.CONDITIONING,
            "mobility" to ExerciseCategory.MOBILITY,
            "other" to ExerciseCategory.OTHER,
        ),
        ExerciseCategory.entries.associateBy { it.storageValue },
    )

    @Test
    fun exerciseUnitsAreStoredAsThese() = assertEquals(
        mapOf(
            "lb" to ExerciseUnit.LB,
            "kg" to ExerciseUnit.KG,
            "bodyweight" to ExerciseUnit.BODYWEIGHT,
            "seconds" to ExerciseUnit.SECONDS,
            "meters" to ExerciseUnit.METERS,
        ),
        ExerciseUnit.entries.associateBy { it.storageValue },
    )

    @Test
    fun sessionStatusesAreStoredAsThese() = assertEquals(
        mapOf(
            "in_progress" to SessionStatus.IN_PROGRESS,
            "completed" to SessionStatus.COMPLETED,
            "abandoned" to SessionStatus.ABANDONED,
        ),
        SessionStatus.entries.associateBy { it.storageValue },
    )

    @Test
    fun setTypesAreStoredAsThese() = assertEquals(
        mapOf(
            "warmup" to SetType.WARMUP,
            "working" to SetType.WORKING,
            "drop" to SetType.DROP,
            "failure" to SetType.FAILURE,
            "custom" to SetType.CUSTOM,
        ),
        SetType.entries.associateBy { it.storageValue },
    )

    @Test
    fun sessionSourcesAreStoredAsThese() = assertEquals(
        mapOf(
            "manual" to SessionSource.MANUAL,
            "imported" to SessionSource.IMPORTED,
        ),
        SessionSource.entries.associateBy { it.storageValue },
    )

    @Test
    fun restTimerTypesAreStoredAsThese() = assertEquals(
        mapOf(
            "none" to RestTimerType.NONE,
            "set" to RestTimerType.SET,
            "exercise" to RestTimerType.EXERCISE,
        ),
        RestTimerType.entries.associateBy { it.storageValue },
    )

    @Test
    fun everyStoredValueReadsBackAsItself() {
        ExerciseCategory.entries.forEach { assertEquals(it, ExerciseCategory.fromStorage(it.storageValue)) }
        ExerciseUnit.entries.forEach { assertEquals(it, ExerciseUnit.fromStorage(it.storageValue)) }
        SessionStatus.entries.forEach { assertEquals(it, SessionStatus.fromStorage(it.storageValue)) }
        SetType.entries.forEach { assertEquals(it, SetType.fromStorage(it.storageValue)) }
        SessionSource.entries.forEach { assertEquals(it, SessionSource.fromStorage(it.storageValue)) }
        RestTimerType.entries.forEach { assertEquals(it, RestTimerType.fromStorage(it.storageValue)) }
    }

    /**
     * A value the app does not know is refused rather than quietly turned into something else.
     *
     * These four decide what a set *was*, so guessing would rewrite training history: a dropped set
     * silently counted as a working one changes volume, records and the weight suggested next time.
     * The importer checks every one of these before writing a row, so a backup carrying an unknown
     * value is refused as a whole file instead of reaching here.
     */
    @Test
    fun anUnknownStoredValueIsRefusedRatherThanGuessedAt() {
        assertThrows(IllegalArgumentException::class.java) { ExerciseCategory.fromStorage("cardio") }
        assertThrows(IllegalArgumentException::class.java) { ExerciseUnit.fromStorage("stone") }
        assertThrows(IllegalArgumentException::class.java) { SessionStatus.fromStorage("paused") }
        assertThrows(IllegalArgumentException::class.java) { SetType.fromStorage("cluster") }
    }

    /**
     * These two fall back instead, on purpose.
     *
     * A session's source is provenance rather than training data -- treating an unrecognised one as
     * manual loses nothing a user typed -- and the rest-timer kind is a legacy column that no longer
     * holds live data at all.
     */
    @Test
    fun provenanceAndTheLegacyTimerFallBackInstead() {
        assertEquals(SessionSource.MANUAL, SessionSource.fromStorage("garmin"))
        assertEquals(RestTimerType.NONE, RestTimerType.fromStorage("circuit"))
    }

    @Test
    fun onlyPoundsAndKilogramsAreLoadedWeights() {
        assertEquals(
            listOf(ExerciseUnit.LB, ExerciseUnit.KG),
            ExerciseUnit.entries.filter { it.isLoadedWeight },
        )
    }
}
