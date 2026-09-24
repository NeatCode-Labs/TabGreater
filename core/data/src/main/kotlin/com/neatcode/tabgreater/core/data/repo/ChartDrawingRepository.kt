package com.neatcode.tabgreater.core.data.repo

import com.neatcode.tabgreater.core.data.db.ChartDrawingDao
import com.neatcode.tabgreater.core.data.db.ChartDrawingEntity
import com.neatcode.tabgreater.core.model.MarketKey
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray

/**
 * The chart drawings of each market, stored as the opaque JSON array the chart page exchanges
 * with the app. This layer does not interpret the drawings; the chart feature validates them on
 * the way in and on the way out.
 */
interface ChartDrawingRepository {

    /** The stored JSON array for [key], or `null` when the market has no drawings. */
    suspend fun load(key: MarketKey): String?

    /** Replaces the drawings of [key]. An empty (or blank) array deletes the row instead. */
    suspend fun save(key: MarketKey, drawingsJson: String, now: Long = System.currentTimeMillis())

    /** Removes every drawing of [key]. */
    suspend fun clear(key: MarketKey)
}

/** Room-backed [ChartDrawingRepository]: one `chart_drawings` row per market key. */
class RoomChartDrawingRepository(private val dao: ChartDrawingDao) : ChartDrawingRepository {

    override suspend fun load(key: MarketKey): String? =
        dao.get(key.value)?.drawings?.takeUnless { isEmptyArray(it) }

    override suspend fun save(key: MarketKey, drawingsJson: String, now: Long) {
        if (isEmptyArray(drawingsJson)) {
            dao.delete(key.value)
        } else {
            dao.upsert(ChartDrawingEntity(marketKey = key.value, drawings = drawingsJson, updatedAt = now))
        }
    }

    override suspend fun clear(key: MarketKey) {
        dao.delete(key.value)
    }

    internal companion object {
        /** `true` for blank input and for a JSON array without elements (`[]`, `[ ]`). */
        fun isEmptyArray(json: String): Boolean {
            if (json.isBlank()) return true
            val parsed = runCatching { Json.parseToJsonElement(json) }.getOrNull()
            return parsed is JsonArray && parsed.isEmpty()
        }
    }
}
