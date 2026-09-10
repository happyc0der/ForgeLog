package dev.happyc0der.forgelog.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.happyc0der.forgelog.data.mapper.toDomain
import dev.happyc0der.forgelog.domain.model.hasTargets
import dev.happyc0der.forgelog.domain.model.SessionSource
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Upgrades a real database through the migrations and checks that data survives.
 *
 * This is the most consequential test in the suite: a broken migration does not fail a build, it
 * destroys training history on a phone that already has the app installed. The older database is
 * built from the committed schema JSON and then opened with Room, so Room performs the upgrade and
 * validates the result against its compiled-in schema — a column added to an entity without a
 * matching migration fails here instead of on the device.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MigrationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val databaseFile = File(context.cacheDir, "forgelog-migration-test.db")
    private var database: ForgeLogDatabase? = null

    @After
    fun tearDown() {
        database?.close()
        databaseFile.delete()
    }

    private fun openWithRoom(): ForgeLogDatabase =
        Room.databaseBuilder(context, ForgeLogDatabase::class.java, databaseFile.absolutePath)
            .addMigrations(*ForgeLogMigrations.ALL)
            .allowMainThreadQueries()
            .build()
            .also { database = it }

    @Test
    fun `a version 1 database upgrades and keeps its logged session`() {
        LegacySchemaBuilder.create(context, databaseFile, version = 1) { db ->
            db.execSQL(
                """
                INSERT INTO workout_sessions
                    (id, programDayId, programId, sessionName, startedAt, completedAt, status,
                     overallFeeling, overallNotes, restBetweenExercisesSeconds, createdAt, updatedAt)
                VALUES (1, NULL, NULL, 'Push Day', 1000, 5000, 'completed', 4, 'solid', 0, 1000, 5000)
                """.trimIndent(),
            )
            db.execSQL(
                """
                INSERT INTO session_exercises
                    (id, sessionId, exerciseId, displayNameSnapshot, exerciseOrder, startedAt,
                     completedAt, restBeforeExerciseSeconds, howToUrlSnapshot, pointersSnapshot,
                     exerciseNotes, feeling)
                VALUES (1, 1, NULL, 'Bench Press', 0, 1000, NULL, NULL, NULL, NULL, NULL, NULL)
                """.trimIndent(),
            )
            db.execSQL(
                """
                INSERT INTO set_logs
                    (id, sessionExerciseId, setNumber, setType, reps, weight, weightUnit,
                     durationSeconds, distanceMeters, restAfterSetSeconds, rpe, rir, completed,
                     notes, completedAt)
                VALUES (1, 1, 1, 'working', 5, 135.0, 'lb', NULL, NULL, NULL, NULL, NULL, 1, NULL, 2000)
                """.trimIndent(),
            )
        }

        val detail = runBlocking {
            openWithRoom().workoutSessionDao().getSessionDetail(1L)?.toDomain()
        }

        assertNotNull(detail)
        assertEquals("Push Day", detail?.session?.sessionName)
        assertEquals(4, detail?.session?.overallFeeling)
        assertEquals("solid", detail?.session?.overallNotes)
        assertEquals(1, detail?.exercises?.size)
        assertEquals("Bench Press", detail?.exercises?.first()?.exercise?.displayNameSnapshot)
        assertEquals(1, detail?.exercises?.first()?.sets?.size)
        assertEquals(135.0, detail?.exercises?.first()?.sets?.first()?.weight ?: 0.0, 0.001)
    }

    @Test
    fun `a version 2 database upgrades to manual provenance and no invented targets`() {
        LegacySchemaBuilder.create(context, databaseFile, version = 2) { db ->
            db.execSQL(
                """
                INSERT INTO workout_sessions
                    (id, programDayId, programId, sessionName, startedAt, completedAt, status,
                     overallFeeling, overallNotes, restBetweenExercisesSeconds,
                     expandedSessionExerciseId, restTimerType, restTimerStartedAt,
                     restTimerDurationSeconds, restTimerPausedRemainingSeconds, createdAt, updatedAt)
                VALUES (7, NULL, NULL, 'Legs Day', 1000, 5000, 'completed', NULL, NULL, 0,
                        NULL, 'none', NULL, NULL, NULL, 1000, 5000)
                """.trimIndent(),
            )
            db.execSQL(
                """
                INSERT INTO session_exercises
                    (id, sessionId, exerciseId, displayNameSnapshot, exerciseOrder, startedAt,
                     completedAt, restBeforeExerciseSeconds, howToUrlSnapshot, pointersSnapshot,
                     exerciseNotes, feeling)
                VALUES (9, 7, NULL, 'Squat', 0, 1000, NULL, NULL, NULL, NULL, NULL, NULL)
                """.trimIndent(),
            )
        }

        val detail = runBlocking {
            openWithRoom().workoutSessionDao().getSessionDetail(7L)?.toDomain()
        }

        // A session logged before importing existed is manual, not unknown.
        assertEquals(SessionSource.MANUAL, detail?.session?.source)
        assertNull(detail?.session?.externalSource)
        assertNull(detail?.session?.externalId)

        val exercise = detail?.exercises?.first()?.exercise
        assertNotNull(exercise)
        // No plan was recorded for it, and the migration must not invent one.
        assertNull(exercise?.plannedSets)
        assertNull(exercise?.targetRepMin)
        assertNull(exercise?.targetRepMax)
        assertNull(exercise?.targetWeight)
        assertNull(exercise?.targetDurationSeconds)
        assertNull(exercise?.targetRestSeconds)
        assertFalse(exercise?.hasTargets ?: true)
    }

    @Test
    fun `programs, days and exercises survive a version 2 upgrade`() {
        LegacySchemaBuilder.create(context, databaseFile, version = 2) { db ->
            db.execSQL(
                """
                INSERT INTO exercises (id, name, category, defaultUnit, howToUrl, defaultPointers,
                                       isArchived, createdAt, updatedAt)
                VALUES (3, 'Squat', 'legs', 'lb', NULL, 'brace hard', 0, 1, 1)
                """.trimIndent(),
            )
            db.execSQL(
                """
                INSERT INTO workout_programs (id, name, description, color, isArchived, createdAt, updatedAt)
                VALUES (2, 'PPL Strength', 'three day split', '#A855F7', 0, 1, 1)
                """.trimIndent(),
            )
            db.execSQL(
                "INSERT INTO program_days (id, programId, name, dayOrder, notes) VALUES (4, 2, 'Legs Day', 0, NULL)",
            )
            db.execSQL(
                """
                INSERT INTO program_exercises
                    (id, programDayId, exerciseId, exerciseOrder, plannedSets, targetRepMin,
                     targetRepMax, targetWeight, targetDurationSeconds, targetRestSeconds,
                     defaultPointersOverride, notes)
                VALUES (5, 4, 3, 0, 5, 3, 5, 225.0, NULL, 180, NULL, 'work up')
                """.trimIndent(),
            )
        }

        val day = runBlocking { openWithRoom().programDao().getDayDetail(4L)?.toDomain() }

        assertEquals("Legs Day", day?.day?.name)
        assertEquals(1, day?.exercises?.size)
        val programExercise = day?.exercises?.first()?.programExercise
        // The program's own targets are untouched by the migration that copies them into sessions.
        assertEquals(5, programExercise?.plannedSets)
        assertEquals(225.0, programExercise?.targetWeight ?: 0.0, 0.001)
        assertEquals(180, programExercise?.targetRestSeconds)
    }

    @Test
    fun `many hand-logged sessions coexist under the unique provenance index`() {
        LegacySchemaBuilder.create(context, databaseFile, version = 2)
        val db = openWithRoom()

        // Both rows leave externalSource and externalId null. SQLite treats NULLs as distinct in a
        // unique index, so the index must not stop someone logging a second workout by hand.
        runBlocking {
            repeat(3) { index ->
                db.workoutSessionDao().insertSession(
                    sessionEntity(sessionName = "Session $index", startedAt = 1_000L, completedAt = 2_000L),
                )
            }
        }

        assertEquals(3, runBlocking { db.workoutSessionDao().observeSessionsCount() })
    }
}
