package com.neatcode.tabgreater.core.exchange

import com.neatcode.tabgreater.core.model.ExchangeId
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.*
import java.io.IOException
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.WeakHashMap
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

enum class ExchangeFailureKind { TRANSIENT, RATE_LIMITED, FORBIDDEN, REGION_RESTRICTED, INVALID_MARKET, INVALID_RESPONSE, DEFERRED }

fun Throwable.exchangeFailureKind(): ExchangeFailureKind = when (this) {
    is ExchangeUnavailableException -> ExchangeFailureKind.REGION_RESTRICTED
    is ExchangeHttpException -> kind
    else -> ExchangeFailureKind.TRANSIENT
}

fun Throwable.retryDelayMs(): Long = (this as? ExchangeHttpException)?.retryAfterMs ?: 0L
fun Throwable.canRetryAutomatically(): Boolean = exchangeFailureKind() in setOf(
    ExchangeFailureKind.TRANSIENT, ExchangeFailureKind.RATE_LIMITED, ExchangeFailureKind.DEFERRED,
)

internal fun httpFailureKind(code: Int): ExchangeFailureKind = when (code) {
    418, 429 -> ExchangeFailureKind.RATE_LIMITED
    403 -> ExchangeFailureKind.FORBIDDEN
    451 -> ExchangeFailureKind.REGION_RESTRICTED
    400, 404 -> ExchangeFailureKind.INVALID_MARKET
    in 500..599, 408 -> ExchangeFailureKind.TRANSIENT
    else -> ExchangeFailureKind.INVALID_RESPONSE
}

fun parseRetryAfter(value: String?, nowEpochMs: Long): Long? {
    val raw = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    raw.toLongOrNull()?.let { seconds ->
        return if (seconds >= 0) seconds.coerceAtMost(Long.MAX_VALUE / 1_000) * 1_000 else null
    }
    return runCatching {
        (ZonedDateTime.parse(raw, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli() - nowEpochMs).coerceAtLeast(0)
    }.getOrNull()
}

data class ExchangeResponse(val code: Int, val body: String)

/** Shared by every REST path using the application's client, including adapter polling and chart RPCs. */
object ExchangeRequests {
    private class Policy {
        val requests = SingleFlight<String, ExchangeResponse>()
        val blockedUntil = mutableMapOf<ExchangeId, Long>()
        val blockedKind = mutableMapOf<ExchangeId, ExchangeFailureKind>()
        val refusedRequests = mutableMapOf<String, ExchangeFailureKind>()
    }
    private val policies = WeakHashMap<OkHttpClient, Policy>()
    private fun policy(client: OkHttpClient): Policy = synchronized(policies) { policies.getOrPut(client) { Policy() } }
    private fun now() = TimeUnit.NANOSECONDS.toMillis(System.nanoTime())

    fun recordRateLimit(client: OkHttpClient, exchange: ExchangeId, delayMs: Long = 900_000) {
        val policy = policy(client)
        synchronized(policy) {
            val duration = delayMs.coerceIn(0, Long.MAX_VALUE / 2)
            policy.blockedKind[exchange] = ExchangeFailureKind.RATE_LIMITED
            policy.blockedUntil[exchange] = maxOf(policy.blockedUntil[exchange] ?: 0, now() + duration)
        }
    }

    /** Explicit retry clears permanent refusals, never a server rate-limit deadline. */
    fun retry(exchange: ExchangeId) {
        synchronized(policies) {
            for (policy in policies.values) synchronized(policy) {
                policy.refusedRequests.keys.removeAll { it.startsWith("${exchange.id}:") }
                if (policy.blockedKind[exchange] in setOf(ExchangeFailureKind.FORBIDDEN, ExchangeFailureKind.REGION_RESTRICTED)) {
                    policy.blockedUntil.remove(exchange)
                    policy.blockedKind.remove(exchange)
                }
            }
        }
    }

    suspend fun execute(
        exchange: ExchangeId,
        client: OkHttpClient,
        request: Request,
        errorFor: (Response, String) -> Exception,
    ): ExchangeResponse {
        val policy = policy(client)
        val requestKey = "${exchange.id}:${request.method}:${request.url}"
        // POST token requests are safe to share here: each has the same empty public body.
        return policy.requests.run(requestKey) {
            synchronized(policy) { policy.refusedRequests[requestKey] }?.let { kind ->
                throw ExchangeHttpException(exchange, if (kind == ExchangeFailureKind.FORBIDDEN) 403 else 451,
                    "This request was refused by the exchange; retry manually", kind)
            }
            val remaining = synchronized(policy) { (policy.blockedUntil[exchange] ?: 0) - now() }
            if (remaining > 0) {
                val kind = synchronized(policy) { policy.blockedKind[exchange] } ?: ExchangeFailureKind.DEFERRED
                throw ExchangeHttpException(exchange, 429, "Refresh is temporarily paused by the exchange",
                    if (kind == ExchangeFailureKind.RATE_LIMITED) ExchangeFailureKind.DEFERRED else kind, remaining)
            }
            val bounded = client.newBuilder().callTimeout(30, TimeUnit.SECONDS).build()
            bounded.newCall(request).awaitBody { response, body ->
                if (!response.isSuccessful) {
                    val failure = errorFor(response, body)
                    if (failure is ExchangeHttpException && failure.kind == ExchangeFailureKind.RATE_LIMITED) {
                        failure.retryAfterMs = parseRetryAfter(response.header("Retry-After"), System.currentTimeMillis()) ?: 900_000
                        recordRateLimit(client, exchange, failure.retryAfterMs ?: 900_000)
                    } else if (failure.exchangeFailureKind() in setOf(ExchangeFailureKind.FORBIDDEN, ExchangeFailureKind.REGION_RESTRICTED)) {
                        synchronized(policy) {
                            // A regional refusal of one asset class must not disable the exchange's other markets.
                            policy.refusedRequests[requestKey] = failure.exchangeFailureKind()
                        }
                    }
                    throw failure
                }
                ExchangeResponse(response.code, body)
            }
        }
    }
}

private suspend fun Call.awaitBody(decode: (Response, String) -> ExchangeResponse): ExchangeResponse = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { cancel() }
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            if (continuation.isActive) continuation.resumeWithException(e)
        }
        override fun onResponse(call: Call, response: Response) {
            try {
                val result = response.use { decode(it, it.body.string()) }
                if (continuation.isActive) continuation.resume(result)
            } catch (e: Exception) {
                if (continuation.isActive) continuation.resumeWithException(e)
            }
        }
    })
}
