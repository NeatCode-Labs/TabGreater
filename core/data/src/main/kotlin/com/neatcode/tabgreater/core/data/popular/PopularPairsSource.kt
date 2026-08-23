package com.neatcode.tabgreater.core.data.popular

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Where the ranked quick-add pairs come from. Implemented by [CoinGeckoPopularPairsSource]. */
fun interface PopularPairsSource {
    /** One attempt at a fresh ranking, or `null` when it failed. Never retries. */
    suspend fun fetch(): List<String>?
}

/**
 * Reads the top coins by market cap from CoinGecko's keyless public endpoint.
 *
 * The call is a single GET with no key, no retry and a hard call timeout: CoinGecko's public tier
 * asks callers to cache instead of hammering, so a failure — including a `429` — simply returns
 * `null` and [PopularPairsRepository] keeps serving the cached list until the next 24 h window.
 * Request and decode both run on [Dispatchers.IO]; callers may be on the main thread.
 *
 * @param client the app-wide OkHttp client; only the call timeout is overridden, so the connection
 *   pool and dispatcher stay shared.
 */
class CoinGeckoPopularPairsSource(
    client: OkHttpClient,
    private val parser: PopularPairParser = PopularPairParser(),
    private val endpoint: String = DEFAULT_ENDPOINT,
) : PopularPairsSource {

    private val client: OkHttpClient = client.newBuilder()
        .callTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false)
        .build()

    override suspend fun fetch(): List<String>? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(endpoint)
            .header("Accept", "application/json")
            .get()
            .build()
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                parser.parse(response.body.string()).takeIf { it.isNotEmpty() }
            }
        } catch (e: IOException) {
            null
        }
    }

    companion object {
        /** Keyless, no attribution header required beyond the visible "Powered by CoinGecko". */
        const val DEFAULT_ENDPOINT: String =
            "https://api.coingecko.com/api/v3/coins/markets" +
                "?vs_currency=usd&order=market_cap_desc&per_page=30&page=1"

        private const val TIMEOUT_SECONDS = 10L
    }
}
