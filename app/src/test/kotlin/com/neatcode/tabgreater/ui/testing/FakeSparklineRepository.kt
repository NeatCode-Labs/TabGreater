package com.neatcode.tabgreater.ui.testing

import com.neatcode.tabgreater.core.data.repo.Sparkline
import com.neatcode.tabgreater.core.data.repo.SparklineRepository
import com.neatcode.tabgreater.core.model.MarketKey
import com.neatcode.tabgreater.core.model.SparkPeriod
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart

/**
 * Records every sparkline subscription so a test can prove that adding one ticker does not
 * restart the others: [starts] grows once per `observeSparkline` collection and [active] holds
 * the keys whose flow is currently being collected.
 */
class FakeSparklineRepository : SparklineRepository {

    /** Every subscription in the order it was started. */
    val starts = mutableListOf<Pair<MarketKey, SparkPeriod>>()

    /** Keys whose flow is collected right now. */
    val active = linkedSetOf<MarketKey>()

    /** Keys passed to the last [refresh] call. */
    var refreshed: List<MarketKey> = emptyList()
        private set

    private val values = mutableMapOf<MarketKey, MutableStateFlow<Sparkline>>()

    /** Subscriptions started for [key], regardless of period. */
    fun startsFor(key: MarketKey): Int = starts.count { it.first == key }

    /**
     * Publishes a mini-chart for [key] — before a subscription starts as its initial value, after
     * one as an update. Two points is the minimum a tile draws.
     */
    fun setPoints(key: MarketKey, vararg closes: Float) {
        val sparkline = Sparkline(
            points = closes,
            firstClose = closes.firstOrNull()?.toDouble(),
            lastClose = closes.lastOrNull()?.toDouble(),
            high = closes.maxOrNull()?.toDouble(),
            low = closes.minOrNull()?.toDouble(),
            volume = null,
            updatedAt = 1L,
        )
        values.getOrPut(key) { MutableStateFlow(Sparkline.EMPTY) }.value = sparkline
    }

    override fun observeSparkline(key: MarketKey, period: SparkPeriod): Flow<Sparkline> =
        values.getOrPut(key) { MutableStateFlow(Sparkline.EMPTY) }
            .onStart {
                starts += key to period
                active += key
            }
            .onCompletion { active -= key }

    override suspend fun refresh(keys: Collection<MarketKey>, period: SparkPeriod) {
        refreshed = keys.toList()
    }

    override suspend fun cached(key: MarketKey, period: SparkPeriod): Sparkline =
        values[key]?.value ?: Sparkline.EMPTY
}
