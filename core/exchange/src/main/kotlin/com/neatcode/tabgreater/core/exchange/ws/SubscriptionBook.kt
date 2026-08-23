package com.neatcode.tabgreater.core.exchange.ws

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Reference-counted set of stream keys shared by every collector of one exchange socket, plus a
 * coalescing command queue.
 *
 * Adapters keep one book per socket. Each `watchTickers` / `watchKlines` flow calls [acquire] with
 * the keys it needs (a key is whatever the exchange subscribes to: `"btceur@miniTicker"`,
 * `"ticker:BTC/EUR"`, `"/market/candles:BTC-USDT_1hour"`, ...) and [release] when it is cancelled.
 * Keys that go `0 -> 1` are reported as fresh and keys that go `1 -> 0` as gone, so the adapter
 * subscribes and unsubscribes exactly once per key no matter how many flows share it.
 *
 * [queueSubscribe] / [queueUnsubscribe] merge every command issued within [coalesceWindowMs]
 * (e.g. one `watchKlines` per tile on screen entry) into a single [onFlush] call, and a key that
 * is subscribed and unsubscribed inside the window cancels out. [onFlush] receives the net lists
 * and turns them into exchange frames, chunking them as the exchange requires.
 *
 * Thread-safe; all methods may be called from any thread.
 *
 * @param scope scope the flush timer runs in (the adapter's app scope).
 * @param onFlush invoked off the calling thread with the net `(subscribe, unsubscribe)` lists;
 *   both are non-empty-or-empty independently and never overlap.
 */
class SubscriptionBook(
    private val scope: CoroutineScope,
    private val coalesceWindowMs: Long = DEFAULT_COALESCE_WINDOW_MS,
    private val onFlush: (subscribe: List<String>, unsubscribe: List<String>) -> Unit,
) {
    /** Result of [acquire]. [fresh] are keys with no prior reference; [wasEmpty] is the book state before. */
    data class Acquired(val fresh: List<String>, val wasEmpty: Boolean)

    /** Result of [release]. [gone] are keys with no remaining reference; [isEmpty] is the book state after. */
    data class Released(val gone: List<String>, val isEmpty: Boolean)

    private val lock = Any()
    private val refs = LinkedHashMap<String, Int>()
    private val pendingSubscribe = LinkedHashSet<String>()
    private val pendingUnsubscribe = LinkedHashSet<String>()
    private var flushJob: Job? = null

    /** Keys currently referenced by at least one collector, in first-acquire order. */
    val active: List<String>
        get() = synchronized(lock) { refs.keys.toList() }

    /** Adds one reference to every key. */
    fun acquire(keys: Collection<String>): Acquired = synchronized(lock) {
        val wasEmpty = refs.isEmpty()
        val fresh = ArrayList<String>()
        for (key in keys) {
            val count = refs[key] ?: 0
            if (count == 0) fresh += key
            refs[key] = count + 1
        }
        Acquired(fresh, wasEmpty)
    }

    /** Drops one reference from every key. */
    fun release(keys: Collection<String>): Released = synchronized(lock) {
        val gone = ArrayList<String>()
        for (key in keys) {
            val count = (refs[key] ?: 0) - 1
            if (count <= 0) {
                if (refs.remove(key) != null) gone += key
            } else {
                refs[key] = count
            }
        }
        Released(gone, refs.isEmpty())
    }

    /** Queues a subscribe for [keys]; cancels a pending unsubscribe of the same key instead. */
    fun queueSubscribe(keys: Collection<String>) = queue(keys, subscribe = true)

    /** Queues an unsubscribe for [keys]; cancels a pending subscribe of the same key instead. */
    fun queueUnsubscribe(keys: Collection<String>) = queue(keys, subscribe = false)

    /**
     * After a reconnect the server has forgotten everything: drop whatever was pending and queue a
     * subscribe for every active key.
     */
    fun resubscribeAll() {
        val keys: List<String>
        synchronized(lock) {
            keys = refs.keys.toList()
            pendingUnsubscribe.clear()
            pendingSubscribe.clear()
            pendingSubscribe += keys
        }
        if (keys.isNotEmpty()) scheduleFlush()
    }

    /** Discards pending commands and the flush timer (the owner is closing the socket). */
    fun clearPending() {
        synchronized(lock) {
            pendingSubscribe.clear()
            pendingUnsubscribe.clear()
            flushJob?.cancel()
            flushJob = null
        }
    }

    private fun queue(keys: Collection<String>, subscribe: Boolean) {
        if (keys.isEmpty()) return
        synchronized(lock) {
            val add = if (subscribe) pendingSubscribe else pendingUnsubscribe
            val cancel = if (subscribe) pendingUnsubscribe else pendingSubscribe
            for (key in keys) if (!cancel.remove(key)) add += key
        }
        scheduleFlush()
    }

    private fun scheduleFlush() {
        synchronized(lock) {
            if (flushJob?.isActive == true) return
            flushJob = scope.launch {
                delay(coalesceWindowMs)
                flushPending()
            }
        }
    }

    private fun flushPending() {
        val subscribe: List<String>
        val unsubscribe: List<String>
        synchronized(lock) {
            subscribe = pendingSubscribe.toList()
            unsubscribe = pendingUnsubscribe.toList()
            pendingSubscribe.clear()
            pendingUnsubscribe.clear()
            flushJob = null
        }
        if (subscribe.isEmpty() && unsubscribe.isEmpty()) return
        onFlush(subscribe, unsubscribe)
    }

    companion object {
        /** Commands issued within this window are merged into one flush. */
        const val DEFAULT_COALESCE_WINDOW_MS: Long = 100
    }
}
