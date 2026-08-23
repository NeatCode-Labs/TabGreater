package com.neatcode.tabgreater.ui.settings

import android.app.Application
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neatcode.tabgreater.core.data.repo.WatchlistRepository
import com.neatcode.tabgreater.core.data.settings.AppSettings
import com.neatcode.tabgreater.core.live.LiveDiagnosticsState
import com.neatcode.tabgreater.core.live.LiveSettings
import com.neatcode.tabgreater.core.live.WidgetRefresh
import com.neatcode.tabgreater.core.model.ImportMode
import com.neatcode.tabgreater.core.model.backup.WatchlistBackupCodec
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Settings: the display switches, the live/widget knobs and the JSON export/import of every
 * watchlist.
 *
 * All file IO happens here (off the main thread, inside [BackupIo]); the composables only launch
 * the Storage Access Framework pickers and hand the resulting Uri back as a string.
 *
 * The live layer is injected as three narrow seams instead of as `LiveDiagnostics` itself: that
 * class needs a `Context`, and its mutators are `internal` to `:core:live`, so a JVM test could
 * neither build one nor drive it. A `StateFlow` and two functions are all this screen needs.
 *
 * @param diagnostics `LiveDiagnostics.state`.
 * @param batteryUnrestricted `LiveDiagnostics::isIgnoringBatteryOptimizations` — re-read on every
 *   [refreshPermissions], i.e. whenever the user comes back from the system dialog.
 * @param onWidgetsChanged `LiveTickerLauncher.onWidgetsChanged(application)`: makes sure
 *   `LiveTickerService` is up after the widget cadence changed.
 */
class SettingsViewModel(
    private val settings: AppSettings,
    private val watchlistRepository: WatchlistRepository,
    private val liveSettings: LiveSettings,
    diagnostics: StateFlow<LiveDiagnosticsState>,
    private val batteryUnrestricted: () -> Boolean,
    private val onWidgetsChanged: () -> Unit,
    application: Application,
) : ViewModel() {

    /**
     * Seam for JVM unit tests, which replace it with an in-memory map. Production always uses the
     * application's ContentResolver — never an Activity, so a rotation cannot leak one.
     */
    internal var backupIo: BackupIo = ContentResolverBackupIo(application)

    /** Seam for tests so exported file names and timestamps are deterministic. */
    internal var now: () -> Long = System::currentTimeMillis

    private val busy = MutableStateFlow(false)
    private val pendingImport = MutableStateFlow<PendingImport?>(null)

    /** Last answer from `PowerManager`; refreshed on start and on [refreshPermissions]. */
    private val batteryAllowlisted = MutableStateFlow(batteryUnrestricted())

    /**
     * A buffered channel rather than a `SharedFlow`: the export/import work runs in
     * [viewModelScope] and outlives the screen, so a result that lands while the user is on the
     * Watchlists tab has to wait in the buffer and be shown when Settings is composed again.
     */
    private val eventChannel = Channel<SettingsEvent>(Channel.BUFFERED)
    val events: Flow<SettingsEvent> = eventChannel.receiveAsFlow()

    val uiState: StateFlow<SettingsUiState> = combine(
        combine(settings.shrinkZeros, settings.watchlistRefreshMs) { shrink, rate -> shrink to rate },
        busy,
        pendingImport,
        liveSettings.values,
        combine(diagnostics, batteryAllowlisted) { diag, allowlisted -> diag to allowlisted },
    ) { (shrinkZeros, refreshMs), isBusy, pending, live, (diag, allowlisted) ->
        SettingsUiState(
            shrinkZeros = shrinkZeros,
            watchlistRefreshMs = refreshMs,
            busy = isBusy,
            pendingImport = pending,
            live = live,
            diagnostics = diag,
            batteryUnrestricted = allowlisted || diag.ignoringBatteryOptimizations,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), SettingsUiState())

    fun setShrinkZeros(enabled: Boolean) {
        viewModelScope.launch { settings.setShrinkZeros(enabled) }
    }

    /** Redraw cadence of the watchlist grid and the chart header; no service involved. */
    fun setWatchlistRefreshMs(ms: Long) {
        viewModelScope.launch { settings.setWatchlistRefreshMs(ms) }
    }

    /**
     * The widget cadence. The running service picks the change up from its own settings flow, but
     * a service an OEM killed cannot — and this screen being in the foreground is the one moment
     * a foreground service may legally be (re)started, so ask for it here too.
     */
    fun setWidgetRefresh(refresh: WidgetRefresh) {
        viewModelScope.launch {
            liveSettings.setWidgetRefresh(refresh)
            onWidgetsChanged()
        }
    }

    /** "Live only on Wi-Fi" needs no restart: the service collects it while it runs. */
    fun setWifiOnly(enabled: Boolean) {
        viewModelScope.launch { liveSettings.setWifiOnly(enabled) }
    }

    /** Re-reads the battery allowlist; called when the screen resumes from the system dialog. */
    fun refreshPermissions() {
        batteryAllowlisted.value = batteryUnrestricted()
    }

    /** Name the "create document" picker should propose. */
    fun suggestedFileName(): String = BackupFiles.suggestedFileName(now())

    /** Writes every watchlist to the document the user created. */
    fun exportTo(uri: String) {
        viewModelScope.launch {
            busy.value = true
            try {
                val backup = watchlistRepository.exportBackup(now())
                backupIo.write(uri, WatchlistBackupCodec.encode(backup))
                eventChannel.send(SettingsEvent.Exported(backup.watchlists.size))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "export failed", e)
                eventChannel.send(SettingsEvent.ExportFailed(e.message))
            } finally {
                busy.value = false
            }
        }
    }

    /** Reads and validates a picked document, then asks for the import mode. */
    fun loadImport(uri: String) {
        viewModelScope.launch {
            busy.value = true
            try {
                val backup = WatchlistBackupCodec.decode(backupIo.read(uri)).getOrElse { e ->
                    Log.w(TAG, "not a backup file", e)
                    eventChannel.send(SettingsEvent.NotABackup)
                    return@launch
                }
                pendingImport.value = PendingImport(
                    backup = backup,
                    watchlists = backup.watchlists.size,
                    items = backup.watchlists.sumOf { it.items.size },
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "import read failed", e)
                eventChannel.send(SettingsEvent.ImportFailed(e.message))
            } finally {
                busy.value = false
            }
        }
    }

    /** Applies the backup the user confirmed in the import-mode dialog. */
    fun confirmImport(mode: ImportMode) {
        val pending = pendingImport.value ?: return
        pendingImport.value = null
        viewModelScope.launch {
            busy.value = true
            try {
                eventChannel.send(SettingsEvent.Imported(watchlistRepository.importBackup(pending.backup, mode)))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "import failed", e)
                eventChannel.send(SettingsEvent.ImportFailed(e.message))
            } finally {
                busy.value = false
            }
        }
    }

    fun cancelImport() {
        pendingImport.value = null
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        const val TAG = "SettingsVM"
    }
}
