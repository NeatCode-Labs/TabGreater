package com.neatcode.tabgreater.ui.settings

import com.neatcode.tabgreater.core.data.settings.WatchlistRefreshRates
import com.neatcode.tabgreater.core.live.LiveDiagnosticsState
import com.neatcode.tabgreater.core.live.LiveSettingsValues
import com.neatcode.tabgreater.core.model.ImportResult
import com.neatcode.tabgreater.core.model.backup.WatchlistBackup

/**
 * A decoded backup waiting for the user to pick Replace or Merge.
 *
 * @property watchlists number of lists in the file.
 * @property items number of tickers across all of them.
 */
data class PendingImport(
    val backup: WatchlistBackup,
    val watchlists: Int,
    val items: Int,
)

/**
 * @property busy a file is being read or written; the export/import rows are disabled meanwhile.
 * @property pendingImport non-null while the import-mode dialog is up (kept here so it survives
 *   a rotation instead of being lost with the composable).
 * @property watchlistRefreshMs how often the watchlist grid and the chart header redraw.
 * @property live what the WIDGETS section's rows show.
 * @property diagnostics what the live layer is actually doing right now; drives the Status row.
 * @property batteryUnrestricted the app is on the battery allowlist. Read straight from
 *   `PowerManager` rather than only from [diagnostics], which is stale while the service is not
 *   running — which is exactly when the user is most likely to be fixing this.
 */
data class SettingsUiState(
    val shrinkZeros: Boolean = true,
    val watchlistRefreshMs: Long = WatchlistRefreshRates.DEFAULT,
    val busy: Boolean = false,
    val pendingImport: PendingImport? = null,
    val live: LiveSettingsValues = LiveSettingsValues(),
    val diagnostics: LiveDiagnosticsState = LiveDiagnosticsState(),
    val batteryUnrestricted: Boolean = false,
)

/** One-shot results the screen turns into a snackbar. */
sealed interface SettingsEvent {
    data class Exported(val watchlists: Int) : SettingsEvent

    /** [reason] is the exception message, `null` when the platform did not give one. */
    data class ExportFailed(val reason: String?) : SettingsEvent

    data class Imported(val result: ImportResult) : SettingsEvent

    /** The file could be read but is not a TabGreater backup. */
    data object NotABackup : SettingsEvent

    /** The file could not be read, or applying it failed. */
    data class ImportFailed(val reason: String?) : SettingsEvent
}
