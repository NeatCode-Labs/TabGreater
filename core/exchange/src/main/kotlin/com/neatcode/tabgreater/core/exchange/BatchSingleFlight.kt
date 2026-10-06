package com.neatcode.tabgreater.core.exchange

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Shares overlapping keys while retaining the provider's efficient batch requests. */
class BatchSingleFlight<K, V> {
    private class Entry<K, V>(val keys: Set<K>, val owner: Job, val result: Deferred<Map<K, V>>, var waiters: Int = 0)
    private val lock = Mutex()
    private val pending = mutableMapOf<K, Entry<K, V>>()

    suspend fun run(keys: Set<K>, block: suspend (Set<K>) -> Map<K, V>): Map<K, V> {
        val context = currentCoroutineContext()
        val entries = lock.withLock {
            val joined = keys.mapNotNull { pending[it] }.toMutableSet()
            val fresh = keys.filterTo(linkedSetOf()) { it !in pending }
            if (fresh.isNotEmpty()) {
                val owner = SupervisorJob()
                val task = CoroutineScope(context.minusKey(Job) + owner).async(start = CoroutineStart.LAZY) { block(fresh) }
                val entry = Entry(fresh, owner, task)
                fresh.forEach { pending[it] = entry }
                joined += entry
            }
            joined.onEach { it.waiters++ }.toList()
        }
        try {
            // Start all newly owned batches before waiting for an overlapping one.
            entries.forEach { it.result.start() }
            return entries.flatMap { it.result.await().entries }.filter { it.key in keys }.associate { it.key to it.value }
        } finally {
            withContext(NonCancellable) {
                lock.withLock {
                    for (entry in entries) if (--entry.waiters == 0) {
                        entry.keys.forEach { if (pending[it] === entry) pending.remove(it) }
                        entry.owner.cancel()
                    }
                }
            }
        }
    }
}
