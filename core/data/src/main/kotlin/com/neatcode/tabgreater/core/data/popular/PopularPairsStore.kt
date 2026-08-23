package com.neatcode.tabgreater.core.data.popular

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.IOException

private val Context.popularPairsDataStore: DataStore<Preferences> by
    preferencesDataStore(name = "popular_pairs")

/** A ranking that was written to disk, with the wall clock at which it was fetched. */
data class CachedPopularPairs(val pairs: List<String>, val fetchedAtMs: Long)

/**
 * Disk cache of the quick-add ranking. [PopularPairsRepository] depends on this interface so the
 * 24 h refresh gate can be unit tested without Android.
 */
interface PopularPairsCache {
    /** The stored ranking, or `null` when nothing usable has been written yet. */
    suspend fun read(): CachedPopularPairs?

    /** Replaces the stored ranking. A failed write is swallowed: the next open simply refetches. */
    suspend fun write(pairs: List<String>, fetchedAtMs: Long)
}

/**
 * Preferences DataStore implementation of [PopularPairsCache], in its own `popular_pairs` file so
 * a corrupt ranking can never take the app settings down with it. The pairs are stored as a JSON
 * array because their order *is* the ranking (a string set would lose it).
 */
class PopularPairsStore(private val context: Context) : PopularPairsCache {

    private val store: DataStore<Preferences> get() = context.popularPairsDataStore

    override suspend fun read(): CachedPopularPairs? {
        val prefs = store.data
            .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
            .first()
        val raw = prefs[Keys.PAIRS] ?: return null
        val fetchedAt = prefs[Keys.FETCHED_AT] ?: return null
        val pairs = try {
            Json.decodeFromString<List<String>>(raw)
        } catch (e: SerializationException) {
            return null
        }
        return if (pairs.isEmpty()) null else CachedPopularPairs(pairs, fetchedAt)
    }

    override suspend fun write(pairs: List<String>, fetchedAtMs: Long) {
        try {
            store.edit { prefs ->
                prefs[Keys.PAIRS] = Json.encodeToString(pairs)
                prefs[Keys.FETCHED_AT] = fetchedAtMs
            }
        } catch (e: IOException) {
            // Nothing to recover: the ranking is a convenience, and the next open refetches it.
        }
    }

    private object Keys {
        val PAIRS = stringPreferencesKey("pairs")
        val FETCHED_AT = longPreferencesKey("fetched_at")
    }
}
