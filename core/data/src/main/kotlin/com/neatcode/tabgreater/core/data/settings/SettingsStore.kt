package com.neatcode.tabgreater.core.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * How often the **watchlist screen** is allowed to redraw prices and mini-charts.
 *
 * It is a sampling rate, not a fetch rate: the exchange sockets stay connected and keep filling
 * the repositories at full speed — this only decides how often the view model lets a new frame
 * through. A tile stays readable at about this cadence, while repainting a 20-tile grid on
 * every WebSocket message is what made the screen unreadable (owner feedback, 23. 8. 2026.).
 */
object WatchlistRefreshRates {

    /** The rates the Settings sheet offers, in millis. */
    val OPTIONS: List<Long> = listOf(1_000L, 2_000L, 5_000L, 10_000L)

    /** Calm enough to read a price off a tile, fast enough to feel live. */
    const val DEFAULT: Long = 5_000L

    /**
     * Snaps [value] to the closest offered rate, so a hand-edited preferences file can never put
     * the grid on a cadence the sheet cannot show. Anything negative falls back to [DEFAULT].
     */
    fun snap(value: Long): Long {
        if (value in OPTIONS) return value
        if (value <= 0L) return DEFAULT
        return OPTIONS.minByOrNull { kotlin.math.abs(it - value) } ?: DEFAULT
    }
}

/**
 * App-wide settings (not per-watchlist). View models depend on this interface so they can be
 * unit-tested with an in-memory fake; [SettingsStore] is the DataStore-backed implementation.
 */
interface AppSettings {
    /** The watchlist tab that was open last; `null` until the user picks one. */
    val selectedWatchlistId: Flow<Long?>

    /** The "shrink zeros" price compression (`0.0₄123`) on tile prices. Default `true`. */
    val shrinkZeros: Flow<Boolean>

    /**
     * Redraw cadence of the watchlist grid and of the chart header, in millis; one of
     * [WatchlistRefreshRates.OPTIONS], [WatchlistRefreshRates.DEFAULT] until the user picks one.
     */
    val watchlistRefreshMs: Flow<Long>

    suspend fun setSelectedWatchlistId(id: Long)
    suspend fun setShrinkZeros(enabled: Boolean)
    suspend fun setWatchlistRefreshMs(ms: Long)
}

/** Preferences DataStore implementation of [AppSettings]. */
class SettingsStore(private val context: Context) : AppSettings {

    private val store: DataStore<Preferences> get() = context.settingsDataStore

    /** A corrupt/unreadable preferences file degrades to defaults instead of crashing every launch. */
    private val data: Flow<Preferences> = store.data.catch { e ->
        if (e is IOException) emit(emptyPreferences()) else throw e
    }

    override val selectedWatchlistId: Flow<Long?> = data.map { it[Keys.SELECTED_WATCHLIST] }
    override val shrinkZeros: Flow<Boolean> = data.map { it[Keys.SHRINK_ZEROS] ?: true }
    override val watchlistRefreshMs: Flow<Long> = data.map {
        WatchlistRefreshRates.snap(it[Keys.WATCHLIST_REFRESH_MS] ?: WatchlistRefreshRates.DEFAULT)
    }

    override suspend fun setSelectedWatchlistId(id: Long) {
        store.edit { it[Keys.SELECTED_WATCHLIST] = id }
    }

    override suspend fun setShrinkZeros(enabled: Boolean) {
        store.edit { it[Keys.SHRINK_ZEROS] = enabled }
    }

    override suspend fun setWatchlistRefreshMs(ms: Long) {
        store.edit { it[Keys.WATCHLIST_REFRESH_MS] = WatchlistRefreshRates.snap(ms) }
    }

    private object Keys {
        val SELECTED_WATCHLIST = longPreferencesKey("selected_watchlist_id")
        val SHRINK_ZEROS = booleanPreferencesKey("shrink_zeros")
        val WATCHLIST_REFRESH_MS = longPreferencesKey("watchlist_refresh_ms")
    }
}
