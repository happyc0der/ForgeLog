package dev.happyc0der.forgelog.testing

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.test.core.app.ApplicationProvider
import dev.happyc0der.forgelog.data.local.ForgeLogDatabase
import dev.happyc0der.forgelog.data.local.inMemoryDatabase
import dev.happyc0der.forgelog.data.backup.BackupRepositoryImpl
import dev.happyc0der.forgelog.data.repository.ExerciseRepositoryImpl
import dev.happyc0der.forgelog.data.repository.ProgramRepositoryImpl
import dev.happyc0der.forgelog.data.repository.WorkoutSessionRepositoryImpl
import dev.happyc0der.forgelog.data.settings.SettingsRepositoryImpl
import dev.happyc0der.forgelog.domain.time.TimeProvider
import dev.happyc0der.forgelog.domain.time.ZoneProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import java.io.File
import java.time.ZoneId

/** A clock the test moves by hand. */
class FakeTimeProvider(var now: Long = 0L) : TimeProvider {
    override fun nowEpochMs(): Long = now
}

class FakeZoneProvider(var zoneId: ZoneId = ZoneId.of("Asia/Kolkata")) : ZoneProvider {
    override fun zone(): ZoneId = zoneId
}

/**
 * Real repositories over an in-memory database.
 *
 * ViewModels are tested against this rather than hand-written fakes: the SQL, the `@Relation`
 * fetches, the type converters and the mappers are exactly where bugs hide, and a fake would
 * cheerfully agree with a broken query. Failure paths that a real database will not produce on
 * demand are provoked by closing it — see [breakDatabase].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TestEnvironment(dispatcher: TestDispatcher) {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val io: CoroutineDispatcher = UnconfinedTestDispatcher(dispatcher.scheduler)

    val database: ForgeLogDatabase = inMemoryDatabase(executor = dispatcher.asExecutor())
    val time = FakeTimeProvider()
    val zone = FakeZoneProvider()

    private val settingsFile: File =
        File.createTempFile("settings_test", ".preferences_pb").also { it.delete() }

    private val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
        scope = CoroutineScope(io),
        produceFile = { settingsFile },
    )

    val settingsRepository = SettingsRepositoryImpl(dataStore, io)

    val sessionRepository = WorkoutSessionRepositoryImpl(
        database = database,
        workoutSessionDao = database.workoutSessionDao(),
        timeProvider = time,
        ioDispatcher = io,
    )

    val programRepository = ProgramRepositoryImpl(
        database = database,
        programDao = database.programDao(),
        workoutSessionDao = database.workoutSessionDao(),
        timeProvider = time,
        ioDispatcher = io,
    )

    val backupRepository = BackupRepositoryImpl(
        database = database,
        backupDao = database.backupDao(),
        timeProvider = time,
        zoneProvider = zone,
        appVersion = "test",
        ioDispatcher = io,
    )

    val exerciseRepository = ExerciseRepositoryImpl(
        database = database,
        exerciseDao = database.exerciseDao(),
        programDao = database.programDao(),
        workoutSessionDao = database.workoutSessionDao(),
        timeProvider = time,
        ioDispatcher = io,
    )

    /** Makes every subsequent query fail, so error handling can be tested for real. */
    fun breakDatabase() = database.close()

    fun tearDown() {
        if (database.isOpen) database.close()
        settingsFile.delete()
    }
}
