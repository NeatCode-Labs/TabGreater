package com.neatcode.tabgreater.core.exchange

import com.neatcode.tabgreater.core.model.Candle
import com.neatcode.tabgreater.core.model.ExchangeId
import com.neatcode.tabgreater.core.model.Market
import com.neatcode.tabgreater.core.model.Ticker
import com.neatcode.tabgreater.core.model.Timeframe
import kotlinx.coroutines.flow.Flow

/**
 * One implementation per exchange. All symbol normalisation lives behind this
 * interface: callers only ever see [com.neatcode.tabgreater.core.model.MarketKey].
 */
interface ExchangeAdapter {
    val id: ExchangeId

    /** Every active spot market with its native symbol and price precision. */
    suspend fun listMarkets(): List<Market>

    /** One-shot 24 h ticker snapshot for the given markets. */
    suspend fun fetchTickers(markets: List<Market>): List<Ticker>

    /**
     * Historical bars, oldest first. [endTime] is an exclusive upper bound in epoch millis
     * (`null` = now). Implementations clamp [limit] to the exchange maximum.
     */
    suspend fun fetchOHLCV(market: Market, timeframe: Timeframe, endTime: Long? = null, limit: Int): List<Candle>

    /**
     * Live ticker updates for the given markets. Cold flow: subscribing opens/reuses the
     * exchange WebSocket (or a polling loop for MEXC); cancelling releases the subscription.
     */
    fun watchTickers(markets: List<Market>): Flow<Ticker>

    /** Live (forming + closed) bars for one market. */
    fun watchKlines(market: Market, timeframe: Timeframe): Flow<Candle>

    /** Timeframes the exchange serves natively; others are aggregated client-side. */
    val nativeTimeframes: Set<Timeframe>
}

/** Thrown when the exchange refuses service for this region (e.g. Binance HTTP 451). */
class ExchangeUnavailableException(val exchange: ExchangeId, message: String) : Exception(message)

/** Thrown for non-2xx responses that are not regional blocks. */
class ExchangeHttpException(val exchange: ExchangeId, val code: Int, message: String) : Exception(message)
