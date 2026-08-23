package com.neatcode.tabgreater.core.exchange.ws

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext
import kotlin.random.Random

/** Lifecycle of one [ExchangeSocket]. */
enum class SocketState { CONNECTING, OPEN, CLOSED }

/**
 * Reusable OkHttp WebSocket wrapper for exchange streams.
 *
 * One instance owns exactly one logical connection: it reconnects with exponential backoff plus
 * jitter after every drop, and it recycles the connection proactively before [maxLifetimeMs]
 * (Binance hangs up at 24 h). Text frames are published on [messages]; the owner resubscribes from
 * [onReconnected], which fires after every re-connect but not after the first connect (the
 * [send] queue already covers that one).
 *
 * The class is pure JVM: instead of a logger it takes a [logger] lambda.
 *
 * @param client shared OkHttp client; a per-socket copy is derived only when [pingIntervalMs] is set.
 * @param urlProvider resolves the `ws://` / `wss://` endpoint before **every** session, so exchanges
 *   whose endpoint carries a short-lived token (KuCoin `bullet-public`) get a fresh one on each
 *   (re)connect. A failure is treated like a dropped connection and retried with backoff. Use the
 *   secondary constructor when the URL is static.
 * @param name short identifier used in log lines.
 * @param scope scope the connection loop runs in; cancelling it stops the socket.
 * @param maxLifetimeMs recycle the connection after this long; `0` disables proactive recycling.
 * @param pingIntervalMs when non-null, OkHttp sends client ping frames at this interval. Leave it
 *   `null` for exchanges (such as Binance) that ping the client themselves — OkHttp answers those
 *   pongs automatically.
 * @param minSendGapMs minimum spacing between outgoing frames. All frames — including those queued
 *   while the socket was down and flushed on (re)connect — go through one paced sender, so an
 *   exchange limit such as Binance's 5 messages/second can never be exceeded.
 */
class ExchangeSocket(
    private val client: OkHttpClient,
    private val urlProvider: suspend () -> String,
    private val name: String,
    private val scope: CoroutineScope,
    private val maxLifetimeMs: Long = DEFAULT_MAX_LIFETIME_MS,
    private val pingIntervalMs: Long? = null,
    private val minSendGapMs: Long = 0,
    private val logger: (String) -> Unit = {},
) {
    /** Socket with a static [url]. */
    constructor(
        client: OkHttpClient,
        url: String,
        name: String,
        scope: CoroutineScope,
        maxLifetimeMs: Long = DEFAULT_MAX_LIFETIME_MS,
        pingIntervalMs: Long? = null,
        minSendGapMs: Long = 0,
        logger: (String) -> Unit = {},
    ) : this(client, { url }, name, scope, maxLifetimeMs, pingIntervalMs, minSendGapMs, logger)

    private val lock = Any()

    private val _messages = MutableSharedFlow<String>(
        replay = 0,
        extraBufferCapacity = MESSAGE_BUFFER,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    /** Incoming text frames. Hot: frames produced while nobody collects are dropped. */
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    private val _state = MutableStateFlow(SocketState.CLOSED)

    /** Current connection state. */
    val state: StateFlow<SocketState> = _state.asStateFlow()

    /** Called after every re-connect (never after the first connect) so the owner can resubscribe. */
    @Volatile
    var onReconnected: (() -> Unit)? = null

    private var loopJob: Job? = null
    private var senderJob: Job? = null

    /** `true` once any session reached [SocketState.OPEN]; later sessions are reconnects. */
    @Volatile
    private var hasOpened = false
    private var webSocket: WebSocket? = null
    private val outbound = Channel<String>(Channel.UNLIMITED)

    /** Starts the connection loop. Calling it again while connected is a no-op. */
    fun connect() {
        synchronized(lock) {
            if (loopJob?.isActive == true) return
            loopJob = scope.launch { runConnectionLoop() }
            if (senderJob?.isActive != true) senderJob = scope.launch { runSender() }
        }
    }

    /** Closes the socket and stops reconnecting. Frames still queued in [send] are discarded. */
    fun close() {
        val job: Job?
        val sender: Job?
        val socket: WebSocket?
        synchronized(lock) {
            job = loopJob
            loopJob = null
            sender = senderJob
            senderJob = null
            socket = webSocket
            webSocket = null
        }
        onReconnected = null
        sender?.cancel()
        while (outbound.tryReceive().isSuccess) { /* drain */ }
        // Cancel the loop first so the listener's onFailure/onClosed is not reported as a drop.
        job?.cancel()
        socket?.close(NORMAL_CLOSURE, null)
        _state.value = SocketState.CLOSED
        logger("$name: closed by owner (had socket: ${socket != null})")
    }

    /**
     * Queues a text frame. Frames are delivered in order by a single sender that waits for
     * [SocketState.OPEN] and honours [minSendGapMs]; a frame whose socket dies mid-flight is
     * retried on the next connection.
     */
    fun send(text: String) {
        outbound.trySend(text)
    }

    private suspend fun runSender() {
        while (coroutineContext.isActive) {
            val text = outbound.receive()
            var delivered = false
            while (!delivered && coroutineContext.isActive) {
                _state.first { it == SocketState.OPEN }
                val socket = synchronized(lock) { webSocket }
                delivered = socket != null && socket.send(text)
                if (!delivered) {
                    // The connection dropped between OPEN and send(): wait for the next session.
                    _state.first { it != SocketState.OPEN }
                }
            }
            if (minSendGapMs > 0) delay(minSendGapMs)
        }
    }

    private suspend fun runConnectionLoop() {
        var attempt = 0
        try {
            while (coroutineContext.isActive) {
                // A session that never opened (DNS failure, token fetch failure) has nothing to
                // resubscribe: the frames queued by the owner are still waiting in the sender.
                val recycled = runSession(isReconnect = hasOpened)
                if (!coroutineContext.isActive) break
                if (recycled) {
                    attempt = 0
                    logger("$name: max lifetime reached, recycling connection")
                } else {
                    attempt++
                    val wait = backoffMs(attempt)
                    logger("$name: disconnected, reconnecting in $wait ms (attempt $attempt)")
                    delay(wait)
                }
            }
        } finally {
            _state.value = SocketState.CLOSED
        }
    }

    /** Runs one connection until it dies. Returns `true` when it was recycled because of [maxLifetimeMs]. */
    private suspend fun runSession(isReconnect: Boolean): Boolean {
        _state.value = SocketState.CONNECTING
        val url = try {
            urlProvider()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger("$name: endpoint resolution failed ${e::class.java.simpleName}: ${e.message}")
            _state.value = SocketState.CLOSED
            return false
        }
        val finished = CompletableDeferred<Unit>()
        val sessionClient = if (pingIntervalMs != null) {
            client.newBuilder().pingInterval(pingIntervalMs, TimeUnit.MILLISECONDS).build()
        } else {
            client
        }
        val socket = sessionClient.newWebSocket(
            Request.Builder().url(url).build(),
            SessionListener(finished, isReconnect),
        )
        synchronized(lock) { webSocket = socket }
        var recycled = false
        try {
            if (maxLifetimeMs > 0) {
                if (withTimeoutOrNull(maxLifetimeMs) { finished.await() } == null) recycled = true
            } else {
                finished.await()
            }
        } finally {
            synchronized(lock) { if (webSocket === socket) webSocket = null }
            _state.value = SocketState.CLOSED
            socket.close(NORMAL_CLOSURE, null)
        }
        return recycled
    }

    /** Exponential backoff capped at [MAX_BACKOFF_MS], jittered into `[half, full]`. */
    private fun backoffMs(attempt: Int): Long {
        val shift = (attempt - 1).coerceIn(0, MAX_BACKOFF_SHIFT)
        val ceiling = (INITIAL_BACKOFF_MS shl shift).coerceAtMost(MAX_BACKOFF_MS)
        val half = ceiling / 2
        return half + Random.nextLong(half + 1)
    }

    private inner class SessionListener(
        private val finished: CompletableDeferred<Unit>,
        private val isReconnect: Boolean,
    ) : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            hasOpened = true
            _state.value = SocketState.OPEN
            logger("$name: connected")
            if (isReconnect) onReconnected?.invoke()
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            _messages.tryEmit(text)
        }

        override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
            _messages.tryEmit(bytes.utf8())
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            webSocket.close(NORMAL_CLOSURE, null)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            logger("$name: closed ($code $reason)")
            finished.complete(Unit)
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            logger("$name: failed (${response?.code ?: 0}) ${t::class.java.simpleName}: ${t.message}")
            finished.complete(Unit)
        }
    }

    companion object {
        /** Binance drops streams after 24 h; recycle a little earlier. */
        const val DEFAULT_MAX_LIFETIME_MS: Long = 23L * 60 * 60 * 1000
        const val INITIAL_BACKOFF_MS: Long = 1_000
        const val MAX_BACKOFF_MS: Long = 60_000

        private const val MAX_BACKOFF_SHIFT = 16
        private const val MESSAGE_BUFFER = 256
        private const val NORMAL_CLOSURE = 1000
    }
}
