package dev.happyc0der.forgelog.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.dataStoreFile
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ApplicationProvider
import dev.happyc0der.forgelog.domain.model.ExerciseUnit
import dev.happyc0der.forgelog.domain.settings.AppSettings
import dev.happyc0der.forgelog.domain.workout.DurationInputUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.time.DayOfWeek

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingsRepositoryImplTest {

    private lateinit var context: Context
    private lateinit var scope: TestScope
    private lateinit var file: File

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        scope = TestScope(StandardTestDispatcher())
        file = File.createTempFile("settings", ".preferences_pb").also { it.delete() }
        context.getSharedPreferences(SettingsKeys.LEGACY_PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @After
    fun tearDown() {
        file.delete()
    }

    private fun dataStore(migrate: Boolean = false): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            migrations = if (migrate) listOf(LegacyDurationUnitMigration(context)) else emptyList(),
            scope = CoroutineScope(UnconfinedTestDispatcher(scope.testScheduler)),
            produceFile = { file },
        )

    private fun repository(store: DataStore<Preferences>) =
        SettingsRepositoryImpl(store, UnconfinedTestDispatcher(scope.testScheduler))

    @Test
    fun `empty store yields the documented defaults`() = runTest {
        val settings = repository(dataStore()).settings.first()
        assertEquals(AppSettings(), settings)
        assertEquals(ExerciseUnit.LB, settings.defaultWeightUnit)
        assertEquals(90, settings.defaultRestSeconds)
        assertEquals(DayOfWeek.MONDAY, settings.weekStartDay)
        assertEquals(false, settings.includeWarmupInVolume)
    }

    @Test
    fun `each setting round-trips`() = runTest {
        val repo = repository(dataStore())
        repo.setDefaultWeightUnit(ExerciseUnit.KG)
        repo.setDefaultRestSeconds(150)
        repo.setDurationInputUnit(DurationInputUnit.MINUTES)
        repo.setWeekStartDay(DayOfWeek.SUNDAY)
        repo.setIncludeWarmupInVolume(true)
        repo.setRestTimerVibration(false)
        repo.setRestTimerSound(true)

        assertEquals(
            AppSettings(
                defaultWeightUnit = ExerciseUnit.KG,
                defaultRestSeconds = 150,
                durationInputUnit = DurationInputUnit.MINUTES,
                weekStartDay = DayOfWeek.SUNDAY,
                includeWarmupInVolume = true,
                restTimerVibration = false,
                restTimerSound = true,
            ),
            repo.settings.first(),
        )
    }

    @Test
    fun `a non-weight unit is rejected as the weight default`() = runTest {
        val repo = repository(dataStore())
        repo.setDefaultWeightUnit(ExerciseUnit.KG)
        repo.setDefaultWeightUnit(ExerciseUnit.BODYWEIGHT)
        repo.setDefaultWeightUnit(ExerciseUnit.SECONDS)
        assertEquals(ExerciseUnit.KG, repo.settings.first().defaultWeightUnit)
    }

    @Test
    fun `rest seconds are clamped to a sane range`() = runTest {
        val repo = repository(dataStore())
        repo.setDefaultRestSeconds(-30)
        assertEquals(AppSettings.MIN_REST_SECONDS, repo.settings.first().defaultRestSeconds)
        repo.setDefaultRestSeconds(99_999)
        assertEquals(AppSettings.MAX_REST_SECONDS, repo.settings.first().defaultRestSeconds)
    }

    @Test
    fun `an unparseable stored value falls back to the default instead of throwing`() = runTest {
        val store = dataStore()
        store.edit { prefs ->
            prefs[SettingsKeys.DEFAULT_WEIGHT_UNIT] = "stones"
            prefs[SettingsKeys.WEEK_START_DAY] = "FUNDAY"
            prefs[SettingsKeys.DURATION_INPUT_UNIT] = "FORTNIGHTS"
        }
        val settings = repository(store).settings.first()
        assertEquals(ExerciseUnit.LB, settings.defaultWeightUnit)
        assertEquals(DayOfWeek.MONDAY, settings.weekStartDay)
        assertEquals(DurationInputUnit.SECONDS, settings.durationInputUnit)
    }

    @Test
    fun `a weight unit stored as a valid but non-loaded unit is ignored`() = runTest {
        val store = dataStore()
        store.edit { it[SettingsKeys.DEFAULT_WEIGHT_UNIT] = ExerciseUnit.BODYWEIGHT.storageValue }
        assertEquals(ExerciseUnit.LB, repository(store).settings.first().defaultWeightUnit)
    }

    @Test
    fun `the legacy duration unit is carried over and its source is left intact`() = runTest {
        val legacy = context.getSharedPreferences(SettingsKeys.LEGACY_PREFS_NAME, Context.MODE_PRIVATE)
        legacy.edit().putString("duration_input_unit", DurationInputUnit.MINUTES.name).commit()

        val settings = repository(dataStore(migrate = true)).settings.first()

        assertEquals(DurationInputUnit.MINUTES, settings.durationInputUnit)
        // DurationUnitPreference still reads SharedPreferences directly, so the source must survive.
        assertEquals(
            DurationInputUnit.MINUTES.name,
            legacy.getString("duration_input_unit", null),
        )
    }

    @Test
    fun `migration does not overwrite a value already in DataStore`() = runTest {
        val legacy = context.getSharedPreferences(SettingsKeys.LEGACY_PREFS_NAME, Context.MODE_PRIVATE)
        legacy.edit().putString("duration_input_unit", DurationInputUnit.MINUTES.name).commit()

        val store = dataStore(migrate = true)
        store.edit { it[SettingsKeys.DURATION_INPUT_UNIT] = DurationInputUnit.SECONDS.name }

        assertEquals(DurationInputUnit.SECONDS, repository(store).settings.first().durationInputUnit)
    }

    @Test
    fun `settings emits again when a value changes`() = runTest {
        val repo = repository(dataStore())
        val before = repo.settings.first()
        repo.setIncludeWarmupInVolume(true)
        val after = repo.settings.first()
        assertTrue(!before.includeWarmupInVolume && after.includeWarmupInVolume)
    }

    /*
     * Rest and an exercise's own duration are different quantities that appear on the same screen:
     * a plank is 45 seconds, the rest after it is two minutes. One shared toggle forced both into
     * one unit, so choosing minutes rendered the hold as "0.75".
     */

    @Test
    fun `rest and duration units are independent`() = runTest {
        val repository = repository(dataStore())
        repository.setDurationInputUnit(DurationInputUnit.SECONDS)
        repository.setRestInputUnit(DurationInputUnit.MINUTES)

        val settings = repository.settings.first()
        assertEquals(DurationInputUnit.SECONDS, settings.durationInputUnit)
        assertEquals(DurationInputUnit.MINUTES, settings.restInputUnit)

        // Changing one must leave the other alone.
        repository.setDurationInputUnit(DurationInputUnit.MINUTES)
        val after = repository.settings.first()
        assertEquals(DurationInputUnit.MINUTES, after.durationInputUnit)
        assertEquals(DurationInputUnit.MINUTES, after.restInputUnit)

        repository.setRestInputUnit(DurationInputUnit.SECONDS)
        val last = repository.settings.first()
        assertEquals(DurationInputUnit.MINUTES, last.durationInputUnit)
        assertEquals(DurationInputUnit.SECONDS, last.restInputUnit)
    }

    @Test
    fun `rest unit defaults to seconds on a fresh install`() = runTest {
        assertEquals(DurationInputUnit.SECONDS, repository(dataStore()).settings.first().restInputUnit)
    }
}
