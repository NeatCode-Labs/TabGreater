package com.neatcode.tabgreater.widget

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.neatcode.tabgreater.core.model.MarketKey
import com.neatcode.tabgreater.core.model.TGColors
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.IOException

/**
 * What one placed home-screen widget shows: the instrument and its exchange, the background
 * colour, the transparency and whether the sparkline is drawn.
 *
 * One widget = one pair, so the market key is part of the configuration and never
 * changes without the user reconfiguring the widget.
 *
 * @property backgroundArgb opaque ARGB of the widget background; [alpha] is applied on top of it.
 * @property alpha `0f` fully transparent … `1f` fully opaque.
 */
@Serializable
data class WidgetConfig(
    val key: MarketKey,
    val backgroundArgb: Long = TGColors.SURFACE,
    val alpha: Float = 1f,
    val showSparkline: Boolean = true,
) {
    /** The background as a single ARGB int, [alpha] folded into the alpha channel. */
    val blendedArgb: Int
        get() {
            val a = (alpha.coerceIn(0f, 1f) * 255f + 0.5f).toInt()
            return (a shl 24) or (backgroundArgb.toInt() and 0x00FFFFFF)
        }

    companion object {
        /** Background swatches offered by the configuration screen, before the accent palette. */
        val BACKGROUNDS: List<Long> = listOf(
            TGColors.SURFACE, // #202121 — the tile colour
            0xFF000000L, // black
            0xFF0F1A24L, // navy
        ) + TGColors.ACCENT_PALETTE
    }
}

private val Context.widgetConfigDataStore: DataStore<Preferences> by preferencesDataStore(name = "widget_configs")

/**
 * Per-`appWidgetId` [WidgetConfig] storage (Preferences DataStore file `widget_configs`, one JSON
 * document per widget). Kept out of Room on purpose: the widget host may re-inflate a widget in a
 * process that has not opened the database yet, and DataStore is the cheaper read.
 */
class WidgetConfigStore(private val context: Context) {

    private val store: DataStore<Preferences> get() = context.widgetConfigDataStore

    /** A corrupt preferences file degrades to "no widgets configured" instead of crashing the host. */
    private val data: Flow<Preferences> = store.data.catch { e ->
        if (e is IOException) emit(emptyPreferences()) else throw e
    }

    fun observeAll(): Flow<Map<Int, WidgetConfig>> = data.map { prefs ->
        buildMap {
            for ((key, value) in prefs.asMap()) {
                val id = appWidgetIdOf(key.name) ?: continue
                val config = decode(value as? String ?: continue) ?: continue
                put(id, config)
            }
        }
    }

    suspend fun get(appWidgetId: Int): WidgetConfig? = observeAll().first()[appWidgetId]

    suspend fun put(appWidgetId: Int, config: WidgetConfig) {
        store.edit { it[keyOf(appWidgetId)] = WidgetJson.format.encodeToString(config) }
    }

    suspend fun remove(appWidgetId: Int) {
        store.edit { it.remove(keyOf(appWidgetId)) }
    }

    private fun decode(raw: String): WidgetConfig? = try {
        WidgetJson.format.decodeFromString<WidgetConfig>(raw)
    } catch (e: IllegalArgumentException) {
        Log.w(TAG, "unreadable widget config, dropping it", e)
        null
    }

    private companion object {
        const val TAG = "TickerWidget"
        const val PREFIX = "widget_"

        fun keyOf(appWidgetId: Int): Preferences.Key<String> = stringPreferencesKey("$PREFIX$appWidgetId")

        fun appWidgetIdOf(name: String): Int? =
            if (name.startsWith(PREFIX)) name.removePrefix(PREFIX).toIntOrNull() else null
    }
}

/** The single JSON codec of the widget module (configs and render models). */
internal object WidgetJson {
    val format: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
}
