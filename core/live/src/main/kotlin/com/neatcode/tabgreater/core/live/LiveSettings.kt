package com.neatcode.tabgreater.core.live

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException

/**
 * How often the home-screen widgets are refreshed — the single knob that replaces the old
 * "Live updates / Update interval / Screen-off interval / Sleep interval" quartet, none of which
 * the owner could map to an observable behaviour (feedback from the S23, 23. 8. 2026.).
 *
 * Everything except [LIVE] is a *timed* mode: no sockets at all, one short REST round per
 * [intervalMs] driven by an exact alarm, screen on or off. [LIVE] is the expensive one: exchange
 * WebSockets while the screen is on, and a 5-minute REST tick once it goes off.
 *
 * @property id what is written to DataStore — stable across reorderings of this enum.
 * @property intervalMs the timed cadence; for [LIVE] the widget re-render spacing while awake.
 */
enum class WidgetRefresh(val id: String, val intervalMs: Long) {
    LIVE("live", 2_000L),
    MIN_1("1m", 60_000L),
    MIN_2("2m", 120_000L),
    MIN_5("5m", 300_000L),
    MIN_15("15m", 900_000L),
    ;

    companion object {
        /** Unknown or missing ids fall back to [DEFAULT_WIDGET_REFRESH] instead of failing. */
        fun fromId(id: String?): WidgetRefresh = entries.firstOrNull { it.id == id } ?: DEFAULT_WIDGET_REFRESH
    }
}

/**
 * Five minutes: cheap enough that the widget costs nothing measurable, fresh enough that the
 * first glance at the home screen is never more than five minutes behind the market.
 */
val DEFAULT_WIDGET_REFRESH: WidgetRefresh = WidgetRefresh.MIN_5

/** Sockets on cellular are the expensive case: opt in, never default. */
const val DEFAULT_WIFI_ONLY: Boolean = true

/** Immutable snapshot of every live setting — what the service and the diagnostics screen read. */
data class LiveSettingsValues(
    val widgetRefresh: WidgetRefresh = DEFAULT_WIDGET_REFRESH,
    val wifiOnly: Boolean = DEFAULT_WIFI_ONLY,
)

/**
 * Settings of the resident live layer. The Settings screen writes them, [LiveTickerService]
 * collects [values] and switches mode on every change.
 *
 * An interface so tests (and the mode calculator's truth table) can use an in-memory fake.
 */
interface LiveSettings {
    /** Widget cadence: [WidgetRefresh.LIVE] or one of the timed options. */
    val widgetRefresh: Flow<WidgetRefresh>

    /**
     * Only meaningful in [WidgetRefresh.LIVE]: on a metered link, do not hold a socket — poll
     * every 15 s instead ([TickerMode.NEAR]). It governs the *link*, never the timed modes, whose
     * requests are far too small to be worth gating.
     */
    val wifiOnly: Flow<Boolean>

    /** Both of the above as one conflated snapshot. */
    val values: Flow<LiveSettingsValues>

    suspend fun setWidgetRefresh(refresh: WidgetRefresh)
    suspend fun setWifiOnly(enabled: Boolean)
}

private val Context.liveSettingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "live_settings")

/**
 * Preferences DataStore implementation of [LiveSettings].
 *
 * The old `live_enabled` / `live_interval_ms` / `live_screen_off_interval_ms` /
 * `live_sleep_interval_ms` keys are simply ignored — the app has exactly one user and nothing was
 * ever shipped, so a migration would be dead code from the day it was written.
 */
class LiveSettingsStore(private val context: Context) : LiveSettings {

    private val store: DataStore<Preferences> get() = context.liveSettingsDataStore

    /** A corrupt preferences file degrades to defaults instead of killing the service on start. */
    private val data: Flow<Preferences> = store.data.catch { e ->
        if (e is IOException) emit(emptyPreferences()) else throw e
    }

    override val values: Flow<LiveSettingsValues> = data.map { it.toValues() }.distinctUntilChanged()

    override val widgetRefresh: Flow<WidgetRefresh> = values.map { it.widgetRefresh }.distinctUntilChanged()
    override val wifiOnly: Flow<Boolean> = values.map { it.wifiOnly }.distinctUntilChanged()

    override suspend fun setWidgetRefresh(refresh: WidgetRefresh) {
        store.edit { it[Keys.WIDGET_REFRESH] = refresh.id }
    }

    override suspend fun setWifiOnly(enabled: Boolean) {
        store.edit { it[Keys.WIFI_ONLY] = enabled }
    }

    private fun Preferences.toValues() = LiveSettingsValues(
        widgetRefresh = WidgetRefresh.fromId(this[Keys.WIDGET_REFRESH]),
        wifiOnly = this[Keys.WIFI_ONLY] ?: DEFAULT_WIFI_ONLY,
    )

    private object Keys {
        val WIDGET_REFRESH = stringPreferencesKey("widget_refresh")
        val WIFI_ONLY = booleanPreferencesKey("live_wifi_only")
    }
}
