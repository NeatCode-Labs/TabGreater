package com.neatcode.tabgreater.ui.manager

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neatcode.tabgreater.core.data.repo.WatchlistRepository
import com.neatcode.tabgreater.core.data.settings.AppSettings
import com.neatcode.tabgreater.core.model.Limits
import com.neatcode.tabgreater.core.model.Watchlist
import com.neatcode.tabgreater.core.model.WatchlistSnapshot
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Drives the Watchlist Manager sheet: create / rename / copy / delete + undo / drag-reorder.
 *
 * Everything the sheet can undo lives here rather than in the composable, so a rotation (or the
 * sheet being recomposed while the snackbar is up) does not lose the deleted watchlist. The 5 s
 * undo window is a [viewModelScope] job, not a `withTimeoutOrNull` in the snackbar effect: the
 * window has to close even when nothing is collecting, otherwise a dismissed sheet keeps a live
 * "UNDO" that resurrects a long-deleted list the next time it is opened.
 * A drag is committed with [reorder]; until Room echoes the new order back, [uiState] keeps
 * showing the dropped order so the rows do not jump.
 */
class WatchlistManagerViewModel(
    private val watchlistRepository: WatchlistRepository,
    private val settings: AppSettings,
) : ViewModel() {

    /** The watchlist a "Delete" took out, kept until "UNDO" is used or the snackbar times out. */
    private val pendingDelete = MutableStateFlow<WatchlistSnapshot?>(null)
    private val message = MutableStateFlow<ManagerMessage?>(null)

    /** Order dropped by the drag handle; applied on top of Room until Room agrees. */
    private val optimisticOrder = MutableStateFlow<List<Long>?>(null)

    private var messageSeq = 0L

    /** Closes the undo window 5 s after a delete, whether or not the sheet is on screen. */
    private var undoJob: Job? = null

    private val listsFlow: Flow<Lists> = combine(
        watchlistRepository.observeWatchlists(),
        watchlistRepository.observeItemCounts(),
        optimisticOrder,
    ) { lists, counts, order -> Lists(applyOrder(lists, order), counts) }

    val uiState: StateFlow<WatchlistManagerUiState> = combine(
        listsFlow,
        settings.selectedWatchlistId,
        pendingDelete,
        message,
    ) { lists, selectedId, pending, msg ->
        WatchlistManagerUiState(
            watchlists = lists.watchlists.map { watchlist ->
                WatchlistRow(
                    id = watchlist.id,
                    name = watchlist.name,
                    subtitle = subtitleOf(watchlist, lists.counts[watchlist.id] ?: 0),
                    selected = watchlist.id == selectedId,
                )
            },
            count = lists.watchlists.size,
            canAdd = lists.watchlists.size < Limits.MAX_WATCHLISTS,
            pendingUndo = pending != null,
            message = msg,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), WatchlistManagerUiState())

    /** Appends a watchlist. Blank names are ignored, long ones clamped, the 20-list cap reported. */
    fun create(name: String) {
        val clean = clampName(name) ?: return
        viewModelScope.launch {
            if (watchlistRepository.observeWatchlists().first().size >= Limits.MAX_WATCHLISTS) {
                post(ManagerMessageKind.LIMIT_REACHED)
                return@launch
            }
            optimisticOrder.value = null
            watchlistRepository.createWatchlist(clean)
        }
    }

    fun rename(id: Long, name: String) {
        val clean = clampName(name) ?: return
        viewModelScope.launch { watchlistRepository.renameWatchlist(id, clean) }
    }

    /** Duplicates a list as `"<name> copy"`; reports the cap when the repository refuses. */
    fun copy(id: Long) {
        viewModelScope.launch {
            val source = watchlistRepository.observeWatchlists().first().firstOrNull { it.id == id }
                ?: return@launch
            optimisticOrder.value = null
            if (watchlistRepository.copyWatchlist(id, copyName(source.name)) == null) {
                post(ManagerMessageKind.LIMIT_REACHED)
            }
        }
    }

    /**
     * Deletes a list after snapshotting it, unless it is the only one left. A second delete inside
     * the undo window finalises the first one — only the most recent delete can be taken back.
     */
    fun delete(id: Long) {
        finishUndoWindow()
        viewModelScope.launch {
            if (watchlistRepository.observeWatchlists().first().size <= 1) {
                post(ManagerMessageKind.KEEP_AT_LEAST_ONE)
                return@launch
            }
            val snapshot = watchlistRepository.snapshotWatchlist(id) ?: return@launch
            optimisticOrder.value = null
            watchlistRepository.deleteWatchlist(id)
            pendingDelete.value = snapshot
            val messageId = post(ManagerMessageKind.DELETED)
            undoJob = viewModelScope.launch {
                delay(UNDO_WINDOW_MS)
                pendingDelete.value = null
                consumeMessage(messageId)
            }
        }
    }

    /**
     * Puts the last deleted watchlist back at its old position, with its items and colours.
     * A no-op once the undo window has closed, so a late tap cannot resurrect an old list.
     */
    fun undoDelete() {
        finishUndoWindow()
        val snapshot = pendingDelete.getAndClear() ?: return
        message.value = null
        viewModelScope.launch {
            optimisticOrder.value = null
            if (watchlistRepository.restoreWatchlist(snapshot) == null) {
                post(ManagerMessageKind.RESTORE_FAILED)
            }
        }
    }

    /**
     * The sheet went away: nothing is left to show the snackbar, so the undo offer and whatever
     * message was up expire with it instead of replaying the next time the sheet is opened.
     */
    fun onSheetDismissed() {
        finishUndoWindow()
        pendingDelete.value = null
        message.value = null
    }

    private fun finishUndoWindow() {
        undoJob?.cancel()
        undoJob = null
    }

    /** Commits a finished drag; [orderedIds] is the full list of ids top to bottom. */
    fun reorder(orderedIds: List<Long>) {
        if (orderedIds.isEmpty()) return
        optimisticOrder.value = orderedIds
        viewModelScope.launch { watchlistRepository.reorderWatchlists(orderedIds) }
    }

    /** Clears [WatchlistManagerUiState.message] after the sheet has shown it. */
    fun consumeMessage(id: Long) {
        message.update { current -> current.takeIf { it?.id != id } }
    }

    /** Queues a snackbar and returns its id, so a timer can consume exactly that one. */
    private fun post(kind: ManagerMessageKind): Long {
        val id = ++messageSeq
        message.value = ManagerMessage(id, kind)
        return id
    }

    private fun MutableStateFlow<WatchlistSnapshot?>.getAndClear(): WatchlistSnapshot? {
        val snapshot = value
        value = null
        return snapshot
    }

    private class Lists(val watchlists: List<Watchlist>, val counts: Map<Long, Int>)

    companion object {
        /** Survives the configuration change that a rotation behind the sheet causes. */
        private const val STOP_TIMEOUT_MS = 5_000L

        /** The undo offer stays up for five seconds. */
        const val UNDO_WINDOW_MS = 5_000L
    }
}

/** `null` when [name] is blank; otherwise trimmed and clamped to the persisted name length. */
internal fun clampName(name: String): String? =
    name.trim().take(Limits.MAX_WATCHLIST_NAME_LENGTH).takeIf { it.isNotEmpty() }

/** `"Main"` -> `"Main copy"`, shortening the original half first so the suffix always survives. */
internal fun copyName(name: String): String {
    val room = Limits.MAX_WATCHLIST_NAME_LENGTH - COPY_SUFFIX.length - 1
    return "${name.trim().take(room).trimEnd()} $COPY_SUFFIX"
}

/** `24 hours · Small · Custom · 45/100`. The labels come from the enums in `core:model`. */
internal fun subtitleOf(watchlist: Watchlist, itemCount: Int): String = listOf(
    watchlist.period.label,
    watchlist.tileSize.label,
    watchlist.sort.label,
    "$itemCount/${Limits.MAX_ITEMS_PER_WATCHLIST}",
).joinToString(SUBTITLE_SEPARATOR)

/**
 * Sorts [lists] by a dropped drag order. A hint that no longer describes exactly the same set of
 * ids (a list was created, deleted or restored meanwhile) is stale and ignored.
 */
internal fun applyOrder(lists: List<Watchlist>, order: List<Long>?): List<Watchlist> {
    if (order == null || order.size != lists.size) return lists
    val rank = order.withIndex().associate { (index, id) -> id to index }
    if (lists.any { it.id !in rank }) return lists
    return lists.sortedBy { rank.getValue(it.id) }
}

private const val COPY_SUFFIX = "copy"
private const val SUBTITLE_SEPARATOR = " · "
