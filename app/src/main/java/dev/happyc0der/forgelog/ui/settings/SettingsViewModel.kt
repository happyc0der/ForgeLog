package dev.happyc0der.forgelog.ui.settings

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.happyc0der.forgelog.BuildConfig
import dev.happyc0der.forgelog.R
import dev.happyc0der.forgelog.domain.backup.BackupCheck
import dev.happyc0der.forgelog.domain.backup.BackupProblem
import dev.happyc0der.forgelog.domain.backup.BackupRepository
import dev.happyc0der.forgelog.domain.backup.DocumentHandle
import dev.happyc0der.forgelog.domain.backup.DocumentStore
import dev.happyc0der.forgelog.domain.debug.DebugTools
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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
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
    /** True only in builds that actually carry the sample-data code. */
    val debugToolsAvailable: Boolean = false,
)

sealed interface SettingsEvent {
    data class Message(val value: String) : SettingsEvent

    /** Ask the user where to put an export. The content is held until they pick a destination. */
    data class PickExportDestination(val suggestedName: String, val mimeType: String) : SettingsEvent
    data object PickImportSource : SettingsEvent
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val application: Application,
    private val settingsRepository: SettingsRepository,
    private val backupRepository: BackupRepository,
    private val documentStore: DocumentStore,
    private val debugTools: DebugTools,
    private val timeProvider: TimeProvider,
    private val zoneProvider: ZoneProvider,
) : ViewModel() {

    private val csvRange = MutableStateFlow(CsvRange.ALL_TIME)
    private val isWorking = MutableStateFlow(false)
    private val errorMessage = MutableStateFlow<String?>(null)

    /**
     * Bumped by [retry] to re-subscribe the settings flow.
     *
     * reportErrors uses `catch`, which ends the upstream, so clearing the message alone left the
     * screen showing default settings forever with a Retry button that did nothing.
     */
    private val retryToken = MutableStateFlow(0)

    /** What a pending export will write, once the user has chosen where it goes. */
    private var pendingExport: String? = null

    private val eventsChannel = Channel<SettingsEvent>(Channel.BUFFERED)
    val events = eventsChannel.receiveAsFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    private val settings = retryToken.flatMapLatest {
        settingsRepository.settings.reportErrors(AppSettings()) { reportError(it) }
    }

    val uiState: StateFlow<SettingsUiState> = combine(
        settings,
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
            debugToolsAvailable = debugTools.isAvailable,
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

    fun setRestInputUnit(unit: DurationInputUnit) =
        edit { settingsRepository.setRestInputUnit(unit) }

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
                savedStateHandle[PENDING_EXPORT_KIND] = ExportKind.JSON.name
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
                val chosen = csvRange.value
                pendingExport = csv(chosen)
                savedStateHandle[PENDING_EXPORT_KIND] = ExportKind.CSV.name
                savedStateHandle[PENDING_CSV_RANGE] = chosen.name
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

    /**
     * Writes the pending export to the file the user chose.
     *
     * The export is held in memory while the picker is open, and Android can close ForgeLog behind
     * the picker; the file already exists by then, created by the picker. That used to end in an
     * empty file and no word of it -- a backup that was never written. Which export was asked for
     * is kept in the saved state, so a lost one is built again and written.
     */
    fun onExportDestinationChosen(handle: DocumentHandle?) {
        val content = pendingExport
        val kind = savedStateHandle.get<String>(PENDING_EXPORT_KIND)
            ?.let { name -> ExportKind.entries.firstOrNull { it.name == name } }
        val range = savedStateHandle.get<String>(PENDING_CSV_RANGE)
            ?.let { name -> CsvRange.entries.firstOrNull { it.name == name } }
            ?: CsvRange.ALL_TIME
        pendingExport = null
        savedStateHandle.remove<String>(PENDING_EXPORT_KIND)
        savedStateHandle.remove<String>(PENDING_CSV_RANGE)
        if (handle == null || (content == null && kind == null)) return
        launchSafely(::reportAsMessage) {
            isWorking.value = true
            try {
                val text = content ?: when (kind) {
                    ExportKind.JSON -> backupRepository.exportJson()
                    ExportKind.CSV -> csv(range)
                    null -> return@launchSafely
                }
                documentStore.writeText(handle, text)
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
                                application.resources.getQuantityString(
                                    R.plurals.session_count,
                                    result.value.sessions,
                                    result.value.sessions,
                                ),
                                application.resources.getQuantityString(
                                    R.plurals.workout_set_count,
                                    result.value.setLogs,
                                    result.value.setLogs,
                                ),
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

    /** The QA build only; the debug and release implementations do nothing. */
    fun seedSampleData() {
        if (!debugTools.isAvailable) return
        launchSafely(::reportAsMessage) {
            isWorking.value = true
            try {
                debugTools.seedSampleData()
                message(R.string.settings_seed_done)
            } finally {
                isWorking.value = false
            }
        }
    }

    fun retry() {
        errorMessage.value = null
        retryToken.update { it + 1 }
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
        is BackupProblem.DuplicateKey ->
            application.getString(R.string.settings_import_duplicate_key, field, table, value)
        BackupProblem.Empty -> application.getString(R.string.settings_import_empty)
    }

    private suspend fun csv(range: CsvRange): String {
        val bounds = csvRangeBounds(range, settingsRepository.settings.first().weekStartDay)
        return backupRepository.exportCsv(bounds?.start, bounds?.endExclusive)
    }

    /**
     * The week starts where Settings says it does.
     *
     * This was fixed to Monday while Home, History and Analytics all followed the setting, so with a
     * Sunday week a CSV of "this week" covered a different seven days from the ones the app had just
     * shown -- and omitted today's session on the Sunday itself.
     */
    private fun csvRangeBounds(range: CsvRange, weekStart: DayOfWeek): WeekBoundary.Range? {
        val now = timeProvider.nowEpochMs()
        val zone = zoneProvider.zone()
        val endExclusive = WeekBoundary.dayRange(now, zone).endExclusive
        return when (range) {
            CsvRange.ALL_TIME -> null
            CsvRange.THIS_WEEK -> WeekBoundary.weekRange(now, zone, weekStart)
            CsvRange.LAST_4_WEEKS -> WeekBoundary.Range(
                start = WeekBoundary.weekRangeOffset(now, zone, weeksAgo = 3, weekStart = weekStart).start,
                endExclusive = endExclusive,
            )
            CsvRange.LAST_YEAR -> WeekBoundary.Range(
                start = WeekBoundary.weekRangeOffset(now, zone, weeksAgo = 52, weekStart = weekStart).start,
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

    private enum class ExportKind { JSON, CSV }

    private companion object {
        /** Mirrors ForgeLogDatabase's version, shown in About so a support question is answerable. */
        const val DATABASE_VERSION = 3

        /** Which export is waiting on the file picker, and for CSV which range; see onExportDestinationChosen. */
        const val PENDING_EXPORT_KIND = "pendingExportKind"
        const val PENDING_CSV_RANGE = "pendingCsvRange"
    }
}
