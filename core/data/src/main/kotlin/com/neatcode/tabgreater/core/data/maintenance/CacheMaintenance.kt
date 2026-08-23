package com.neatcode.tabgreater.core.data.maintenance

import android.util.Log
import com.neatcode.tabgreater.core.data.db.CandleDao
import com.neatcode.tabgreater.core.data.db.TickerSnapshotDao
import com.neatcode.tabgreater.core.data.repo.WatchlistRepository
import com.neatcode.tabgreater.core.model.MarketKey
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

/**
 * Drops cached candles and ticker snapshots for markets nothing references any more, so the
 * database does not grow forever as tickers are added and removed.
 *
 * The key set is debounced: removing a ticker and undoing it within [debounceMs] leaves the cache
 * untouched, which is what makes the watchlist's "Undo" instant instead of re-fetching history.
 */
class CacheMaintenance(
    private val watchlists: WatchlistRepository,
    private val candleDao: CandleDao,
    private val snapshotDao: TickerSnapshotDao,
    private val scope: CoroutineScope,
    private val debounceMs: Long = DEFAULT_DEBOUNCE_MS,
    /**
     * Markets that must survive the prune although no watchlist references them — the pairs on the
     * home-screen widgets. `:core:data` cannot see `:widget`, so the widget layer is handed in as
     * a flow through Koin; without it a widget-only pair loses its candles and its snapshot, and
     * its sparkline never comes back.
     */
    private val extraKeys: Flow<Set<MarketKey>> = flowOf(emptySet()),
) {

    /** Starts pruning in the app scope; the returned [Job] lives as long as the process. */
    // Flow.debounce is still a preview API in coroutines 1.11 but has been stable in behaviour for
    // years; the alternative (a hand-rolled timer) would only duplicate it.
    @OptIn(FlowPreview::class)
    fun start(): Job = scope.launch {
        combine(watchlists.observeAllKeys(), extraKeys) { watched, pinned -> watched + pinned }
            .debounce(debounceMs)
            .distinctUntilChanged()
            .collect { keys -> prune(keys) }
    }

    /** One-shot prune against the current key set (app foreground, tests). */
    suspend fun pruneOnce() {
        prune(watchlists.observeAllKeys().first() + extraKeys.first())
    }

    private suspend fun prune(keys: Set<MarketKey>) {
        try {
            // An empty set right after install means the database has not been seeded yet; an
            // empty set with watchlists present really does mean "nothing is watched any more".
            if (watchlists.observeWatchlists().first().isEmpty()) return
            val keep = keys.mapTo(HashSet(keys.size)) { it.value }
            deleteStale(candleDao.distinctKeys(), keep) { candleDao.deleteByKeys(it) }
            deleteStale(snapshotDao.distinctKeys(), keep) { snapshotDao.deleteByKeys(it) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "cache prune failed", e)
        }
    }

    private suspend fun deleteStale(
        cached: List<String>,
        keep: Set<String>,
        delete: suspend (List<String>) -> Unit,
    ) {
        val stale = cached.filterNot { it in keep }
        if (stale.isEmpty()) return
        for (chunk in stale.chunked(SQL_CHUNK)) delete(chunk)
    }

    private companion object {
        const val TAG = "CacheMaintenance"
        const val DEFAULT_DEBOUNCE_MS = 30_000L

        /** Well under SQLite's 999 bound variables per statement. */
        const val SQL_CHUNK = 500
    }
}
