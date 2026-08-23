package com.neatcode.tabgreater.feature.chart

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.neatcode.tabgreater.core.model.Timeframe
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.chartSettingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "chart_settings")

/**
 * The chart's user preferences, as the screen sees them. [ChartSettingsStore] is the DataStore
 * implementation; view models depend on this interface so they can be unit-tested in-memory.
 */
interface ChartPreferences {
    /** The current settings, re-emitted on every change. */
    val settings: Flow<ChartSettings>

    suspend fun setTimeframe(timeframe: Timeframe)
    suspend fun setCandleType(type: CandleType)
    suspend fun setLogScale(enabled: Boolean)

    /** Entries outside [IndicatorCatalogue] are dropped before the list is written. */
    suspend fun setIndicators(indicators: List<IndicatorSpec>)
}

/**
 * The chart's own Preferences DataStore file (`chart_settings`), separate from the app settings so
 * the chart can be opened straight from a widget deep link without touching the watchlist store.
 *
 * Every setter persists immediately; the screen applies the change through the `tg.*` API in the
 * same frame, so the WebView never waits for the write.
 */
class ChartSettingsStore(private val context: Context) : ChartPreferences {

    private val store: DataStore<Preferences> get() = context.chartSettingsDataStore

    /** A corrupt/unreadable preferences file degrades to defaults instead of failing every open. */
    private val data: Flow<Preferences> = store.data.catch { e ->
        if (e is IOException) emit(emptyPreferences()) else throw e
    }

    override val settings: Flow<ChartSettings> = data.map { prefs ->
        ChartSettings(
            timeframe = prefs[Keys.TIMEFRAME]?.let { id -> Timeframe.entries.firstOrNull { it.id == id } }
                ?: ChartSettings.DEFAULT.timeframe,
            candleType = CandleType.fromNameOrDefault(prefs[Keys.CANDLE_TYPE]),
            logScale = prefs[Keys.LOG_SCALE] ?: ChartSettings.DEFAULT.logScale,
            indicators = ChartSettingsCodec.decodeIndicators(prefs[Keys.INDICATORS]),
        )
    }.distinctUntilChanged()

    override suspend fun setTimeframe(timeframe: Timeframe) {
        store.edit { it[Keys.TIMEFRAME] = timeframe.id }
    }

    override suspend fun setCandleType(type: CandleType) {
        store.edit { it[Keys.CANDLE_TYPE] = type.name }
    }

    override suspend fun setLogScale(enabled: Boolean) {
        store.edit { it[Keys.LOG_SCALE] = enabled }
    }

    override suspend fun setIndicators(indicators: List<IndicatorSpec>) {
        val sanitized = IndicatorCatalogue.sanitize(indicators)
        store.edit { it[Keys.INDICATORS] = ChartSettingsCodec.encodeIndicators(sanitized) }
    }

    private object Keys {
        val TIMEFRAME = stringPreferencesKey("timeframe")
        val CANDLE_TYPE = stringPreferencesKey("candle_type")
        val LOG_SCALE = booleanPreferencesKey("log_scale")
        val INDICATORS = stringPreferencesKey("indicators")
    }
}
