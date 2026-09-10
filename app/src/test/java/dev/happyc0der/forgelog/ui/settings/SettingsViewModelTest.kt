package dev.happyc0der.forgelog.ui.settings

import android.app.Application
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import dev.happyc0der.forgelog.data.local.exerciseEntity
import dev.happyc0der.forgelog.data.local.sessionEntity
import dev.happyc0der.forgelog.data.local.sessionExerciseEntity
import dev.happyc0der.forgelog.data.local.setLogEntity
import dev.happyc0der.forgelog.domain.backup.DocumentHandle
import dev.happyc0der.forgelog.domain.backup.DocumentStore
import dev.happyc0der.forgelog.domain.model.ExerciseUnit
import dev.happyc0der.forgelog.domain.workout.DurationInputUnit
import dev.happyc0der.forgelog.testing.MainDispatcherRule
import dev.happyc0der.forgelog.testing.TestEnvironment
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.advanceUntilIdle
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
import java.io.IOException
import java.time.DayOfWeek

/** Stands in for the Storage Access Framework: the test plays the part of the file picker. */
private class FakeDocumentStore : DocumentStore {
    object Handle : DocumentHandle

    var written: String? = null
    var toRead: String? = null
    var readFailure: Throwable? = null
    var writeFailure: Throwable? = null

    override suspend fun readText(handle: DocumentHandle): Result<String> {
        readFailure?.let { return Result.failure(it) }
        return Result.success(toRead ?: "")
    }

    override suspend fun writeText(handle: DocumentHandle, content: String): Result<Unit> {
        writeFailure?.let { return Result.failure(it) }
        written = content
        return Result.success(Unit)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var env: TestEnvironment
    private lateinit var documents: FakeDocumentStore
    private val created = mutableListOf<SettingsViewModel>()

    @Before
    fun setUp() = runTest {
        env = TestEnvironment(mainDispatcherRule.dispatcher)
        env.time.now = 1_700_000_000_000L
        documents = FakeDocumentStore()
        seed()
    }

    @After
    fun tearDown() {
        created.forEach { it.viewModelScope.cancel() }
        created.clear()
        env.tearDown()
    }

    private suspend fun seed() {
        val dao = env.database.workoutSessionDao()
        val benchId = env.database.exerciseDao().upsert(exerciseEntity(name = "Bench Press"))
        val sessionId = dao.insertSession(
            sessionEntity(sessionName = "Push Day", startedAt = 1_000L, completedAt = 2_000L),
        )
        val sessionExerciseId = dao.insertSessionExercise(
            sessionExerciseEntity(sessionId = sessionId, exerciseId = benchId),
        )
        dao.upsertSetLog(setLogEntity(sessionExerciseId = sessionExerciseId))
    }

    private fun viewModel(): SettingsViewModel = SettingsViewModel(
        application = ApplicationProvider.getApplicationContext<Application>(),
        settingsRepository = env.settingsRepository,
        backupRepository = env.backupRepository,
        documentStore = documents,
        timeProvider = env.time,
        zoneProvider = env.zone,
    ).also(created::add)

    @Test
    fun `settings are surfaced and edits round-trip`() = runTest {
        val vm = viewModel()
        vm.uiState.test {
            var state = awaitItem()
            while (state.isLoading) state = awaitItem()
            assertEquals(ExerciseUnit.LB, state.settings.defaultWeightUnit)

            vm.setDefaultWeightUnit(ExerciseUnit.KG)
            vm.setDefaultRestSeconds(180)
            vm.setWeekStartDay(DayOfWeek.SUNDAY)
            vm.setIncludeWarmupInVolume(true)
            vm.setRestTimerVibration(false)
            vm.setRestTimerSound(true)
            vm.setDurationInputUnit(DurationInputUnit.MINUTES)

            while (state.settings.defaultWeightUnit != ExerciseUnit.KG ||
                state.settings.defaultRestSeconds != 180 ||
                state.settings.weekStartDay != DayOfWeek.SUNDAY ||
                !state.settings.includeWarmupInVolume ||
                state.settings.restTimerVibration ||
                !state.settings.restTimerSound ||
                state.settings.durationInputUnit != DurationInputUnit.MINUTES
            ) {
                state = awaitItem()
            }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `an export asks for a destination and names the file by date`() = runTest {
        val vm = viewModel()
        vm.events.test {
            vm.beginJsonExport()
            advanceUntilIdle()
            val event = awaitItem()
            assertTrue(event is SettingsEvent.PickExportDestination)
            val pick = event as SettingsEvent.PickExportDestination
            assertEquals("application/json", pick.mimeType)
            assertTrue(pick.suggestedName, pick.suggestedName.startsWith("forgelog-backup-"))
            assertTrue(pick.suggestedName.endsWith(".json"))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the export is written only once a destination is chosen`() = runTest {
        val vm = viewModel()
        vm.beginJsonExport()
        advanceUntilIdle()
        assertEquals(null, documents.written)

        vm.onExportDestinationChosen(FakeDocumentStore.Handle)
        advanceUntilIdle()

        val written = documents.written
        assertNotNull(written)
        assertTrue(written!!.contains("\"sessions\""))
        assertTrue(written.contains("Push Day"))
    }

    @Test
    fun `cancelling the picker writes nothing and reports nothing`() = runTest {
        val vm = viewModel()
        vm.beginJsonExport()
        advanceUntilIdle()

        vm.onExportDestinationChosen(null)
        advanceUntilIdle()

        assertEquals(null, documents.written)
    }

    @Test
    fun `a failed write is reported rather than silently dropped`() = runTest {
        documents.writeFailure = IOException("disk full")
        val vm = viewModel()
        vm.events.test {
            vm.beginJsonExport()
            advanceUntilIdle()
            // The destination request comes first; the failure follows once a destination is chosen.
            assertTrue(awaitItem() is SettingsEvent.PickExportDestination)

            vm.onExportDestinationChosen(FakeDocumentStore.Handle)
            advanceUntilIdle()
            val event = awaitItem()
            assertTrue(event is SettingsEvent.Message)
            assertTrue((event as SettingsEvent.Message).value.contains("disk full"))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a CSV export is offered with a csv name and mime type`() = runTest {
        val vm = viewModel()
        vm.setCsvRange(CsvRange.ALL_TIME)
        vm.events.test {
            vm.beginCsvExport()
            advanceUntilIdle()
            val pick = awaitItem() as SettingsEvent.PickExportDestination
            assertEquals("text/csv", pick.mimeType)
            assertTrue(pick.suggestedName.endsWith(".csv"))
            cancelAndIgnoreRemainingEvents()
        }
        vm.onExportDestinationChosen(FakeDocumentStore.Handle)
        advanceUntilIdle()
        assertTrue(documents.written!!.startsWith("session_id,"))
    }

    @Test
    fun `importing a valid export restores it and reports the counts`() = runTest {
        val vm = viewModel()
        val exported = env.backupRepository.exportJson()
        env.backupRepository.deleteAllData()
        documents.toRead = exported

        vm.events.test {
            vm.onImportSourceChosen(FakeDocumentStore.Handle)
            advanceUntilIdle()
            val message = awaitItem() as SettingsEvent.Message
            assertTrue(message.value, message.value.contains("1 session"))
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(1, env.database.backupDao().allSessions().size)
    }

    @Test
    fun `an unreadable file is explained, not just failed`() = runTest {
        documents.toRead = "this is not json"
        val vm = viewModel()
        vm.events.test {
            vm.onImportSourceChosen(FakeDocumentStore.Handle)
            advanceUntilIdle()
            val message = awaitItem() as SettingsEvent.Message
            assertTrue(message.value, message.value.contains("could not be read"))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `an empty document is refused with its own message and changes nothing`() = runTest {
        documents.toRead = "{}"
        val before = env.database.backupDao().allSessions().size
        val vm = viewModel()
        vm.events.test {
            vm.onImportSourceChosen(FakeDocumentStore.Handle)
            advanceUntilIdle()
            val message = awaitItem() as SettingsEvent.Message
            assertTrue(message.value, message.value.contains("no ForgeLog data"))
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(before, env.database.backupDao().allSessions().size)
    }

    @Test
    fun `a newer backup format is refused with both version numbers`() = runTest {
        documents.toRead = """{"formatVersion": 99, "exercises": []}"""
        val vm = viewModel()
        vm.events.test {
            vm.onImportSourceChosen(FakeDocumentStore.Handle)
            advanceUntilIdle()
            val message = awaitItem() as SettingsEvent.Message
            assertTrue(message.value, message.value.contains("99"))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a file the picker could not open is reported`() = runTest {
        documents.readFailure = IOException("permission denied")
        val vm = viewModel()
        vm.events.test {
            vm.onImportSourceChosen(FakeDocumentStore.Handle)
            advanceUntilIdle()
            val message = awaitItem() as SettingsEvent.Message
            assertTrue(message.value.contains("permission denied"))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `cancelling the import picker does nothing at all`() = runTest {
        val before = env.database.backupDao().allSessions().size
        val vm = viewModel()
        vm.onImportSourceChosen(null)
        advanceUntilIdle()
        assertEquals(before, env.database.backupDao().allSessions().size)
    }

    @Test
    fun `deleting all data clears the database and says so`() = runTest {
        val vm = viewModel()
        vm.events.test {
            vm.deleteAllData()
            advanceUntilIdle()
            val message = awaitItem() as SettingsEvent.Message
            assertTrue(message.value.contains("deleted"))
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(0, env.database.backupDao().allSessions().size)
        assertEquals(0, env.database.backupDao().allExercises().size)
    }

    @Test
    fun `the about section reports the schema version`() = runTest {
        val vm = viewModel()
        vm.uiState.test {
            var state = awaitItem()
            while (state.isLoading) state = awaitItem()
            assertEquals(3, state.databaseVersion)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
