package com.neatcode.tabgreater.core.exchange

import com.neatcode.tabgreater.core.model.ExchangeId
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class ExchangeRequestsTest {
    @Test fun `last HTTP waiter cancels a response while its body is still being read`() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            val cancelled = CountDownLatch(1)
            val client = OkHttpClient.Builder().eventListener(object : okhttp3.EventListener() {
                override fun canceled(call: okhttp3.Call) { cancelled.countDown() }
            }).build()
            server.enqueue(MockResponse.Builder().body("delayed body").bodyDelay(30, TimeUnit.SECONDS).build())
            val request = launch(Dispatchers.IO) {
                ExchangeRequests.execute(ExchangeId.BINANCE, client, Request.Builder().url(server.url("/slow")).build()) { response, body ->
                    ExchangeHttpException(ExchangeId.BINANCE, response.code, body)
                }
            }
            withContext(Dispatchers.IO) { server.takeRequest() }
            request.cancelAndJoin()
            assertTrue(cancelled.await(5, TimeUnit.SECONDS))
            client.connectionPool.evictAll()
            client.dispatcher.executorService.shutdown()
        }
    }

    @Test fun `regional refusal of tokenized markets does not block a crypto request`() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            val client = OkHttpClient()
            server.enqueue(MockResponse.Builder().code(451).body("region").build())
            server.enqueue(MockResponse.Builder().code(200).body("crypto").build())
            suspend fun call(path: String) = ExchangeRequests.execute(ExchangeId.KRAKEN, client, Request.Builder().url(server.url(path)).build()) { response, body ->
                ExchangeHttpException(ExchangeId.KRAKEN, response.code, body)
            }
            assertEquals(ExchangeFailureKind.REGION_RESTRICTED, runCatching { call("/markets?class=stocks") }.exceptionOrNull()!!.exchangeFailureKind())
            assertEquals("crypto", call("/markets?class=crypto").body)
            assertEquals(2, server.requestCount)
            client.connectionPool.evictAll()
            client.dispatcher.executorService.shutdown()
        }
    }
    @Test fun `rate limit applies to other REST paths and explicit retry cannot bypass it`() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            val client = OkHttpClient()
            server.enqueue(MockResponse.Builder().code(429).addHeader("Retry-After", "60").body("limited").build())
            suspend fun call(path: String) = ExchangeRequests.execute(ExchangeId.BINANCE, client, Request.Builder().url(server.url(path)).build()) { response, body ->
                ExchangeHttpException(ExchangeId.BINANCE, response.code, body)
            }
            val first = runCatching { call("/ticker") }.exceptionOrNull()!!
            assertEquals(ExchangeFailureKind.RATE_LIMITED, first.exchangeFailureKind())
            assertEquals(60_000, first.retryDelayMs())
            ExchangeRequests.retry(ExchangeId.BINANCE)
            val other = runCatching { call("/candles") }.exceptionOrNull()!!
            assertEquals(ExchangeFailureKind.DEFERRED, other.exchangeFailureKind())
            assertTrue(other.retryDelayMs() > 0)
            assertEquals(1, server.requestCount)
            client.connectionPool.evictAll()
            client.dispatcher.executorService.shutdown()
        }
    }

    @Test fun `permanent refusal requires explicit retry`() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            val client = OkHttpClient()
            server.enqueue(MockResponse.Builder().code(403).body("blocked").build())
            server.enqueue(MockResponse.Builder().code(200).body("ok").build())
            suspend fun call() = ExchangeRequests.execute(ExchangeId.GATE, client, Request.Builder().url(server.url("/ticker")).build()) { response, body ->
                ExchangeHttpException(ExchangeId.GATE, response.code, body)
            }
            assertEquals(ExchangeFailureKind.FORBIDDEN, runCatching { call() }.exceptionOrNull()!!.exchangeFailureKind())
            assertEquals(ExchangeFailureKind.FORBIDDEN, runCatching { call() }.exceptionOrNull()!!.exchangeFailureKind())
            assertEquals(1, server.requestCount)
            ExchangeRequests.retry(ExchangeId.GATE)
            assertEquals("ok", call().body)
            assertEquals(2, server.requestCount)
            client.connectionPool.evictAll()
            client.dispatcher.executorService.shutdown()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test fun `overlapping batches share keys and retain work for remaining waiter`() = runTest {
        val shared = BatchSingleFlight<String, Int>()
        val calls = mutableListOf<Set<String>>()
        val release = CompletableDeferred<Unit>()
        var cancelled = false
        val first = async { shared.run(setOf("a", "b")) { keys ->
            calls += keys
            try { release.await(); keys.associateWith { 1 } } finally { cancelled = true }
        } }
        runCurrent()
        val second = async { shared.run(setOf("b", "c")) { keys -> calls += keys; keys.associateWith { 2 } } }
        runCurrent()
        assertEquals(listOf(setOf("a", "b"), setOf("c")), calls)
        first.cancelAndJoin()
        assertFalse(cancelled)
        release.complete(Unit)
        assertEquals(mapOf("b" to 1, "c" to 2), second.await())
    }
}
