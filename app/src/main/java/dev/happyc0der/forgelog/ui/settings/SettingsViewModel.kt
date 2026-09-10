package dev.happyc0der.forgelog.ui.settings

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.happyc0der.forgelog.BuildConfig
import dev.happyc0der.forgelog.R
import dev.happyc0der.forgelog.domain.backup.BackupCheck
import dev.happyc0der.forgelog.domain.backup.BackupProblem
import dev.happyc0der.forgelog.domain.backup.BackupRepository
import dev.happyc0der.forgelog.domain.backup.DocumentHandle
import dev.happyc0der.forgelog.domain.backup.DocumentStore
import dev.happyc0der.forgelog.domain.model.ExerciseUnit
import dev.happyc0der.forgelog.domain.settings.AppSettings
import dev.happyc0der.forgelog.domain.settings.SettingsRepository
import dev.happyc0der.forgelog.domain.time.TimeProvider
import dev.happyc0der.forgelog.domain.time.WeekBoundary
import dev.happyc0der.forgelog.domain.time.ZoneProvider
import dev.happyc0der.forgelog.domain.workout.DurationInputUnit
import dev.happyc0der.forgelog.ui.common.launchSafely
import dev.happyc0der.forgelog.ui.common.reportErrors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import javax.inject.Inject

/** How much history a CSV export covers. */
enum class CsvRange {
    ALL_TIME,
    THIS_WEEK,
    LAST_4_WEEKS,
    LAST_YEAR,
}

data class SettingsUiState(
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val settings: AppSettings = AppSettings(),
    val csvRange: CsvRange = CsvRange.ALL_TIME,
    val isWorking: Boolean = false,
    val appVersion: String = BuildConfig.VERSION_NAME,
    val databaseVersion: Int = 0,
)

sealed interface SettingsEvent {
    data class Message(val value: String) : SettingsEvent

    /** Ask the user where to put an export. The content is held until they pick a destination. */
    data class PickExportDestination(val suggestedName: String, val mimeType: String) : SettingsEvent
    data object PickImportSource : SettingsEvent
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val application: Application,
    private val settingsRepository: SettingsRepository,
    private val backupRepository: BackupRepository,
    private val documentStore: DocumentStore,
    private val timeProvider: TimeProvider,
    private val zoneProvider: ZoneProvider,
) : ViewModel() {

    private val csvRange = MutableStateFlow(CsvRange.ALL_TIME)
    private val isWorking = MutableStateFlow(false)
    private val errorMessage = MutableStateFlow<String?>(null)

    /** What a pending export will write, once the user has chosen where it goes. */
    private var pendingExport: String? = null

    private val eventsChannel = Channel<SettingsEvent>(Channel.BUFFERED)
    val events = eventsChannel.receiveAsFlow()

    val uiState: StateFlow<SettingsUiState> = combine(
        settingsRepository.settings.reportErrors(AppSettings()) { reportError(it) },
        csvRange,
        isWorking,
        errorMessage,
    ) { settings, range, working, error ->
        SettingsUiState(
            isLoading = false,
            errorMessage = error,
            settings = settings,
            csvRange = range,
            isWorking = working,
            databaseVersion = DATABASE_VERSION,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SettingsUiState(),
    )

    fun setDefaultWeightUnit(unit: ExerciseUnit) =
        edit { settingsRepository.setDefaultWeightUnit(unit) }

    fun setDefaultRestSeconds(seconds: Int) =
        edit { settingsRepository.setDefaultRestSeconds(seconds) }

    fun setDurationInputUnit(unit: DurationInputUnit) =
        edit { settingsRepository.setDurationInputUnit(unit) }

    fun setWeekStartDay(day: DayOfWeek) = edit { settingsRepository.setWeekStartDay(day) }

    fun setIncludeWarmupInVolume(include: Boolean) =
        edit { settingsRepository.setIncludeWarmupInVolume(include) }

    fun setRestTimerVibration(enabled: Boolean) =
        edit { settingsRepository.setRestTimerVibration(enabled) }

    fun setRestTimerSound(enabled: Boolean) =
        edit { settingsRepository.setRestTimerSound(enabled) }

    fun setCsvRange(range: CsvRange) {
        csvRange.value = range
    }

    /**
     * Builds the JSON first and only then asks where to save it, so a failure to read the database
     * surfaces before the user has gone through a file picker for nothing.
     */
    fun beginJsonExport() {
        launchSafely(::reportAsMessage) {
            isWorking.value = true
            try {
                pendingExport = backupRepository.exportJson()
                eventsChannel.send(
                    SettingsEvent.PickExportDestination(
                        suggestedName = "forgelog-backup-${stamp()}.json",
                        mimeType = "application/json",
                    ),
                )
            } finally {
                isWorking.value = false
            }
        }
    }

    fun beginCsvExport() {
        launchSafely(::reportAsMessage) {
            isWorking.value = true
            try {
                val range = csvRangeBounds(csvRange.value)
                pendingExport = backupRepository.exportCsv(range?.start, range?.endExclusive)
                eventsChannel.send(
                    SettingsEvent.PickExportDestination(
                        suggestedName = "forgelog-sets-${stamp()}.csv",
                        mimeType = "text/csv",
                    ),
                )
            } finally {
                isWorking.value = false
            }
        }
    }

    fun beginImport() {
        viewModelScope.launch { eventsChannel.send(SettingsEvent.PickImportSource) }
    }

    fun onExportDestinationChosen(handle: DocumentHandle?) {
        val content = pendingExport
        pendingExport = null
        if (handle == null || content == null) return
        launchSafely(::reportAsMessage) {
            isWorking.value = true
            try {
                documentStore.writeText(handle, content)
                    .onSuccess { message(R.string.settings_export_done) }
                    .onFailure { reportAsMessage(it) }
            } finally {
                isWorking.value = false
            }
        }
    }

    fun onImportSourceChosen(handle: DocumentHandle?) {
        if (handle == null) return
        launchSafely(::reportAsMessage) {
            isWorking.value = true
            try {
                val raw = documentStore.readText(handle).getOrElse { error ->
                    reportAsMessage(error)
                    return@launchSafely
                }
                when (val result = backupRepository.importJson(raw)) {
                    is BackupCheck.Invalid -> eventsChannel.send(
                        SettingsEvent.Message(result.problem.describe()),
                    )
                    is BackupCheck.Valid -> eventsChannel.send(
                        SettingsEvent.Message(
                            application.getString(
                                R.string.settings_import_done,
                                result.value.sessions,
                                result.value.setLogs,
                            ),
                        ),
                    )
                }
            } finally {
                isWorking.value = false
            }
        }
    }

    fun deleteAllData() {
        launchSafely(::reportAsMessage) {
            isWorking.value = true
            try {
                backupRepository.deleteAllData()
                message(R.string.settings_delete_all_done)
            } finally {
                isWorking.value = false
            }
        }
    }

    fun retry() {
        errorMessage.value = null
    }

    /** Every problem gets a sentence that says what is wrong with the file, not just "failed". */
    private fun BackupProblem.describe(): String = when (this) {
        is BackupProblem.Unreadable ->
            application.getString(R.string.settings_import_unreadable, detail)
        is BackupProblem.UnsupportedFormat ->
            application.getString(R.string.settings_import_newer, fileVersion, supportedVersion)
        is BackupProblem.DanglingReference ->
            application.getString(R.string.settings_import_dangling, table, field, missingId)
        is BackupProblem.InvalidValue ->
            application.getString(R.string.settings_import_invalid, field, table, value)
        is BackupProblem.DuplicateId ->
            application.getString(R.string.settings_import_duplicate, table, id)
        BackupProblem.Empty -> application.getString(R.string.settings_import_empty)
    }

    private fun csvRangeBounds(range: CsvRange): WeekBoundary.Range? {
        val now = timeProvider.nowEpochMs()
        val zone = zoneProvider.zone()
        val endExclusive = WeekBoundary.dayRange(now, zone).endExclusive
        return when (range) {
            CsvRange.ALL_TIME -> null
            CsvRange.THIS_WEEK -> WeekBoundary.weekRange(now, zone)
            CsvRange.LAST_4_WEEKS -> WeekBoundary.Range(
                start = WeekBoundary.weekRangeOffset(now, zone, weeksAgo = 3).start,
                endExclusive = endExclusive,
            )
            CsvRange.LAST_YEAR -> WeekBoundary.Range(
                start = WeekBoundary.weekRangeOffset(now, zone, weeksAgo = 52).start,
                endExclusive = endExclusive,
            )
        }
    }

    private fun stamp(): String {
        val zone = zoneProvider.zone()
        return java.time.format.DateTimeFormatter
            .ofPattern("yyyy-MM-dd-HHmm")
            .format(java.time.Instant.ofEpochMilli(timeProvider.nowEpochMs()).atZone(zone))
    }

    private fun edit(block: suspend () -> Unit) {
        launchSafely(::reportAsMessage) { block() }
    }

    private suspend fun message(resId: Int) {
        eventsChannel.send(SettingsEvent.Message(application.getString(resId)))
    }

    private fun reportError(throwable: Throwable) {
        errorMessage.value = throwable.message?.takeIf { it.isNotBlank() }
            ?: application.getString(R.string.state_error_generic)
    }

    private fun reportAsMessage(throwable: Throwable) {
        viewModelScope.launch {
            eventsChannel.send(
                SettingsEvent.Message(
                    throwable.message?.takeIf { it.isNotBlank() }
                        ?: application.getString(R.string.state_error_generic),
                ),
            )
        }
    }

    private companion object {
        /** Mirrors ForgeLogDatabase's version, shown in About so a support question is answerable. */
        const val DATABASE_VERSION = 3
    }
}
