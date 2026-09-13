package dev.happyc0der.forgelog.data.local

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import dev.happyc0der.forgelog.data.local.entity.SessionExerciseEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * What deleting something does to everything pointing at it — through the open path the app uses.
 *
 * Every one of these behaviours is a foreign key, and every foreign key is inert unless SQLite is
 * told to enforce them. Room tells it, in `onConfigure`. The app no longer hands Room's callback
 * straight to the framework, though: [CorruptionPreservingFactory] builds a configuration of its own
 * and wraps that callback, so the pragma now reaches SQLite only because the wrapper passes
 * `onConfigure` on. Nothing tied those two facts together. A wrapper that forgot one method would
 * leave the app opening perfectly, deleting happily, and quietly accumulating orphans — sets with no
 * session, days with no program — with the damage only visible much later.
 *
 * So this opens a real database the way `DatabaseModule` does and asks what actually happens. The
 * first test is the pragma itself; the rest are the five behaviours that depend on it, which are
 * worth pinning in their own right because they decide what survives a deletion.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DeletesBehaveThroughTheRealOpenPathTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var database: ForgeLogDatabase

    @Before
    fun setUp() {
        deleteFiles()
        // Assembled exactly as DatabaseModule does, on a real file, so the wrapper is in the path.
        database = Room
            .databaseBuilder(context, ForgeLogDatabase::class.java, NAME)
            .openHelperFactory(
                CorruptionPreservingFactory(
                    delegate = FrameworkSQLiteOpenHelperFactory(),
                    recoveryLog = NoDatabaseRecoveryLog,
                    now = { 0L },
                ),
            )
            .addMigrations(*ForgeLogMigrations.ALL)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
        deleteFiles()
    }

    private fun deleteFiles() {
        val file = context.getDatabasePath(NAME)
        listOf("", "-wal", "-shm").forEach { suffix -> File(file.path + suffix).delete() }
    }

    private suspend fun seed(): Seeded {
        val exercises = database.exerciseDao()
        val programs = database.programDao()
        val sessions = database.workoutSessionDao()

        val benchId = exercises.upsert(exerciseEntity(name = "Bench Press"))
        val programId = programs.insertProgram(programEntity(name = "PPL"))
        val dayId = programs.insertDay(dayEntity(programId = programId, name = "Push Day"))
        programs.insertProgramExercise(programExerciseEntity(programDayId = dayId, exerciseId = benchId))
        val sessionId = sessions.insertSession(
            sessionEntity(
                sessionName = "PPL · Push Day",
                startedAt = 1_000,
                completedAt = 4_000,
                programId = programId,
                programDayId = dayId,
            ),
        )
        val sessionExerciseId = sessions.insertSessionExercise(
            sessionExerciseEntity(sessionId = sessionId, exerciseId = benchId),
        )
        sessions.upsertSetLog(setLogEntity(sessionExerciseId = sessionExerciseId, setNumber = 1))
        return Seeded(benchId, programId, dayId, sessionId, sessionExerciseId)
    }

    private data class Seeded(
        val exerciseId: Long,
        val programId: Long,
        val dayId: Long,
        val sessionId: Long,
        val sessionExerciseId: Long,
    )

    private suspend fun sessionExercises(): List<SessionExerciseEntity> =
        database.backupDao().allSessionExercises()

    @Test
    fun `foreign keys are enforced on a database opened the way the app opens it`() {
        database.openHelper.writableDatabase.query("PRAGMA foreign_keys").use { cursor ->
            assertTrue("the pragma returned nothing", cursor.moveToFirst())
            assertEquals(
                "foreign keys are off, so every cascade and set-null below is inert",
                1,
                cursor.getInt(0),
            )
        }
    }

    @Test
    fun `deleting a program keeps the workouts logged from it`() = runTest {
        val seeded = seed()

        database.programDao().deleteProgram(seeded.programId)

        val session = database.backupDao().allSessions().singleOrNull()
        assertNotNull("deleting a program took the training logged from it", session)
        assertNull("the session still points at a program that is gone", session!!.programId)
        assertNull(session.programDayId)
        assertEquals("PPL · Push Day", session.sessionName)
        assertEquals(1, database.backupDao().allSetLogs().size)
        // Its days and their exercises go with it, which is the half that should cascade.
        assertEquals(0, database.backupDao().allProgramDays().size)
        assertEquals(0, database.backupDao().allProgramExercises().size)
    }

    @Test
    fun `deleting a program day keeps the workouts logged from it`() = runTest {
        val seeded = seed()

        database.programDao().deleteDay(seeded.dayId)

        val session = database.backupDao().allSessions().singleOrNull()
        assertNotNull(session)
        assertNull("the session still points at a day that is gone", session!!.programDayId)
        assertEquals("the program pointer was cleared too", seeded.programId, session.programId)
        assertEquals(1, database.backupDao().allSetLogs().size)
    }

    @Test
    fun `deleting a session takes its exercises and sets with it`() = runTest {
        seed()

        database.workoutSessionDao().deleteSession(database.backupDao().allSessions().first().id)

        assertEquals(0, database.backupDao().allSessions().size)
        assertEquals("orphaned session exercises were left behind", 0, sessionExercises().size)
        assertEquals("orphaned sets were left behind", 0, database.backupDao().allSetLogs().size)
    }

    /**
     * A library exercise still in a program cannot simply be deleted: the column is not nullable, so
     * the database refuses rather than leaving a program row pointing at nothing. The app's own
     * delete policy is what decides between archiving it and taking it out of the programs first;
     * this is the floor beneath that decision.
     */
    @Test
    fun `deleting an exercise a program still uses is refused`() = runTest {
        val seeded = seed()

        val failed = runCatching { database.exerciseDao().deleteById(seeded.exerciseId) }

        assertTrue("the delete was allowed: $failed", failed.isFailure)
        assertEquals(1, database.backupDao().allExercises().size)
        assertEquals(1, database.backupDao().allProgramExercises().size)
    }

    @Test
    fun `deleting an exercise no program uses keeps the history under its logged name`() = runTest {
        val seeded = seed()
        database.programDao().deleteProgram(seeded.programId)

        database.exerciseDao().deleteById(seeded.exerciseId)

        val logged = sessionExercises().singleOrNull()
        assertNotNull("deleting a lift took the sessions that used it", logged)
        assertNull("the logged exercise still points at a library row that is gone", logged!!.exerciseId)
        assertEquals("Bench Press", logged.displayNameSnapshot)
        assertEquals(1, database.backupDao().allSetLogs().size)
    }

    private companion object {
        const val NAME = "delete-behaviour-test.db"
    }
}
