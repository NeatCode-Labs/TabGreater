package com.neatcode.tabgreater.core.model

import java.util.Locale

/** Stable family identities for recurring outcomes; each contract still has its own [MarketKey]. */
object MarketSeriesId {
    private const val MILLIS_PER_DAY = 24L * 60 * 60 * 1_000

    /** Daily priceBinary contracts may change target price, but keep their perp and UTC expiry slot. */
    fun dailyPriceBinary(
        underlying: String?,
        period: String?,
        targetPrice: String?,
        expiresAtEpochMs: Long?,
    ): String? {
        val asset = underlying?.trim()?.takeIf(String::isNotEmpty) ?: return null
        if (period?.trim()?.equals("1d", ignoreCase = true) != true) return null
        if (targetPrice?.trim()?.toBigDecimalOrNull()?.signum()?.let { it > 0 } != true) return null
        val expiry = expiresAtEpochMs?.takeIf { it > 0L } ?: return null
        val utcSlotMillis = Math.floorMod(expiry, MILLIS_PER_DAY)
        return "priceBinary:${asset.uppercase(Locale.ROOT)}:1d:$utcSlotMillis"
    }
}
