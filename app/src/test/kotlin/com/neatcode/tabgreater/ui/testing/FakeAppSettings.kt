package com.neatcode.tabgreater.ui.testing

import com.neatcode.tabgreater.core.data.settings.AppSettings
import com.neatcode.tabgreater.core.data.settings.WatchlistRefreshRates
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** In-memory [AppSettings] so view models can be exercised without DataStore. */
class FakeAppSettings(
    selectedWatchlistId: Long? = null,
    shrinkZeros: Boolean = true,
    watchlistRefreshMs: Long = WatchlistRefreshRates.DEFAULT,
) : AppSettings {

    private val selected = MutableStateFlow(selectedWatchlistId)
    private val shrink = MutableStateFlow(shrinkZeros)
    private val refreshMs = MutableStateFlow(WatchlistRefreshRates.snap(watchlistRefreshMs))

    override val selectedWatchlistId: Flow<Long?> = selected
    override val shrinkZeros: Flow<Boolean> = shrink
    override val watchlistRefreshMs: Flow<Long> = refreshMs

    /** Current values, for assertions. */
    val selectedIdValue: Long? get() = selected.value
    val shrinkZerosValue: Boolean get() = shrink.value
    val watchlistRefreshMsValue: Long get() = refreshMs.value

    override suspend fun setSelectedWatchlistId(id: Long) {
        selected.value = id
    }

    override suspend fun setShrinkZeros(enabled: Boolean) {
        shrink.value = enabled
    }

    /** Snaps exactly like `SettingsStore`, so a test that writes a stray value sees what it stores. */
    override suspend fun setWatchlistRefreshMs(ms: Long) {
        refreshMs.value = WatchlistRefreshRates.snap(ms)
    }
}
