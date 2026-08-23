package com.neatcode.tabgreater.ui.manager

/**
 * One row of the Watchlist Manager sheet.
 *
 * @property subtitle `24 hours · Small · Custom · 45/100` — the per-list settings plus the item
 *   count, assembled in the view model so the sheet stays free of formatting logic.
 * @property selected the tab that is currently open on the watchlist screen.
 */
data class WatchlistRow(
    val id: Long,
    val name: String,
    val subtitle: String,
    val selected: Boolean,
)

/** What the sheet's own snackbar has to say. */
enum class ManagerMessageKind {
    /** A list was deleted and can be restored for the next 5 seconds. */
    DELETED,

    /** `Limits.MAX_WATCHLISTS` reached — create/copy refused. */
    LIMIT_REACHED,

    /** The last remaining watchlist cannot be deleted. */
    KEEP_AT_LEAST_ONE,

    /** The undo could not put the watchlist back (the cap filled up meanwhile). */
    RESTORE_FAILED,
}

/**
 * A pending snackbar. [id] is monotonic so the same [kind] twice in a row still re-triggers the
 * `LaunchedEffect` that shows it; the sheet calls
 * [WatchlistManagerViewModel.consumeMessage] with it once the snackbar is gone.
 */
data class ManagerMessage(val id: Long, val kind: ManagerMessageKind)

/**
 * State of the Watchlist Manager sheet.
 *
 * @property count number of watchlists, rendered as `count/20` in the header.
 * @property canAdd `false` once the 20-list cap is reached; disables the new-list field.
 * @property pendingUndo a delete snapshot is still held, so "UNDO" can restore it.
 */
data class WatchlistManagerUiState(
    val watchlists: List<WatchlistRow> = emptyList(),
    val count: Int = 0,
    val canAdd: Boolean = true,
    val pendingUndo: Boolean = false,
    val message: ManagerMessage? = null,
)
