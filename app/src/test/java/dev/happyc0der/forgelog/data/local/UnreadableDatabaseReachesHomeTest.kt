package dev.happyc0der.forgelog.data.local

import android.app.Application
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import dev.happyc0der.forgelog.data.repository.ProgramRepositoryImpl
import dev.happyc0der.forgelog.data.repository.WorkoutSessionRepositoryImpl
import dev.happyc0der.forgelog.data.settings.SettingsRepositoryImpl
import dev.happyc0der.forgelog.testing.FakeTimeProvider
import dev.happyc0der.forgelog.testing.FakeZoneProvider
import dev.happyc0der.forgelog.testing.MainDispatcherRule
import dev.happyc0der.forgelog.ui.home.HomeViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import kotlin.random.Random

/**
 * The whole data-loss path at once: a database SQLite will not open, through Room and the real
 * recovery log, to the notice on Home.
 *
 * Everything here is the real thing apart from the clock. That is the point — the bug this pins was
 * a question of *when* each part runs, and every piece replaced by a double is a piece whose timing
 * is the test's rather than the app's. Room opens the database lazily, on its first query, and the
 * first query comes from Home's own flows, so the corruption is found after the ViewModel has been
 * constructed. Home used to read the log in its constructor and so always read it before the record
 * existed: the launch that lost the history was the one launch that said nothing about it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UnreadableDatabaseReachesHomeTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val io = UnconfinedTestDispatcher(mainDispatcherRule.dispatcher.scheduler)
    private val time = FakeTimeProvider(now = 1_765_000_000_000L)
    private val zone = FakeZoneProvider()

    private lateinit var databaseFile: File
    private lateinit var recoveryLog: SharedPreferencesDatabaseRecoveryLog
    private var database: ForgeLogDatabase? = null
    private val created = mutableListOf<HomeViewModel>()

    @Before
    fun setUp() {
        databaseFile = context.getDatabasePath(DATABASE_NAME)
        deleteDatabaseFiles()
        preservedCopies().forEach { it.delete() }
        recoveryLog = SharedPreferencesDatabaseRecoveryLog(context)
        recoveryLog.markReported()
    }

    @After
    fun tearDown() {
        created.forEach { it.viewModelScope.cancel() }
        created.clear()
        database?.close()
        deleteDatabaseFiles()
        preservedCopies().forEach { it.delete() }
        recoveryLog.markReported()
    }

    private fun deleteDatabaseFiles() {
        databaseFile.delete()
        File(databaseFile.path + "-wal").delete()
        File(databaseFile.path + "-shm").delete()
    }

    private fun preservedCopies(): List<File> =
        databaseFile.parentFile
            ?.listFiles { file -> file.name.startsWith("$DATABASE_NAME.unreadable-") }
            ?.toList()
            .orEmpty()

    /**
     * Bytes that are not a database. SQLite answers `SQLITE_NOTADB`, which Android raises as the
     * same corruption exception a half-written page would, and the open helper's handling follows.
     * There is no journal to leave behind, which matters: a `-wal` beside a damaged file can let
     * SQLite put it right on its own, and then there is no corruption to handle.
     */
    private fun writeSomethingThatIsNotADatabase() {
        databaseFile.parentFile?.mkdirs()
        databaseFile.writeBytes(Random(20260912).nextBytes(64 * 1024))
    }

    /** Room over the real file, wrapped the way [dev.happyc0der.forgelog.di.DatabaseModule] does. */
    private fun openDatabase(): ForgeLogDatabase = Room
        .databaseBuilder(context, ForgeLogDatabase::class.java, DATABASE_NAME)
        .openHelperFactory(
            CorruptionPreservingFactory(
                delegate = FrameworkSQLiteOpenHelperFactory(),
                recoveryLog = recoveryLog,
                now = time::nowEpochMs,
            ),
        )
        .addMigrations(*ForgeLogMigrations.ALL)
        .setQueryExecutor(io.asExecutor())
        .setTransactionExecutor(io.asExecutor())
        .allowMainThreadQueries()
        .build()
        .also { database = it }

    private fun homeViewModel(database: ForgeLogDatabase): HomeViewModel {
        val settingsFile = File.createTempFile("settings_recovery", ".preferences_pb")
            .also { it.delete() }
        val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(io),
            produceFile = { settingsFile },
        )
        return HomeViewModel(
            application = ApplicationProvider.getApplicationContext<Application>(),
            programRepository = ProgramRepositoryImpl(
                database = database,
                programDao = database.programDao(),
                workoutSessionDao = database.workoutSessionDao(),
                timeProvider = time,
                ioDispatcher = io,
            ),
            workoutSessionRepository = WorkoutSessionRepositoryImpl(
                database = database,
                workoutSessionDao = database.workoutSessionDao(),
                timeProvider = time,
                ioDispatcher = io,
            ),
            settingsRepository = SettingsRepositoryImpl(dataStore, io),
            timeProvider = time,
            zoneProvider = zone,
            databaseRecoveryLog = recoveryLog,
        ).also(created::add)
    }

    @Test
    fun `a database that cannot be opened is kept, replaced, and reported on the same launch`() =
        runTest {
            writeSomethingThatIsNotADatabase()
            val damaged = databaseFile.readBytes()

            // The order the app itself does this in: Room is built, then Home, and only then does
            // anything ask the database a question.
            val database = openDatabase()
            val viewModel = homeViewModel(database)

            viewModel.uiState.test {
                var state = awaitItem()
                while (state.isLoading || state.unreadableDatabase == null) state = awaitItem()

                val preserved = state.unreadableDatabase
                assertNotNull("Home was never told the database could not be read", preserved)
                assertEquals(time.now, preserved?.atEpochMs)
                assertEquals(
                    "$DATABASE_NAME.unreadable-${time.now}",
                    preserved?.preservedFileName,
                )

                // The app is usable: an empty database, not a dead one. That is what makes the
                // notice worth showing, since reaching Settings to restore a backup needs a
                // running app.
                assertEquals(false, state.hasPrograms)
                assertEquals(null, state.errorMessage)
                cancelAndIgnoreRemainingEvents()
            }

            val copy = File(databaseFile.parentFile, "$DATABASE_NAME.unreadable-${time.now}")
            assertTrue("the unreadable database was not kept", copy.exists())
            assertTrue(
                "the copy is not the bytes that were there",
                copy.readBytes().contentEquals(damaged),
            )
        }

    @Test
    fun `an ordinary database is opened without a word about lost data`() = runTest {
        val database = openDatabase()
        val viewModel = homeViewModel(database)

        viewModel.uiState.test {
            var state = awaitItem()
            while (state.isLoading) state = awaitItem()

            assertEquals(null, state.unreadableDatabase)
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(emptyList<File>(), preservedCopies())
    }

    private companion object {
        const val DATABASE_NAME = "recovery-path-test.db"
    }
}
