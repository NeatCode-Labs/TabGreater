package com.neatcode.tabgreater.core.exchange

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Shares work, not caller lifetimes. The last waiter cancels the underlying operation. */
class SingleFlight<K, V> {
    private class Entry<V>(val owner: Job, val result: Deferred<V>, var waiters: Int = 0)
    private val lock = Mutex()
    private val pending = mutableMapOf<K, Entry<V>>()

    suspend fun run(key: K, block: suspend () -> V): V {
        val context = currentCoroutineContext()
        val entry = lock.withLock {
            pending.getOrPut(key) {
                val owner = SupervisorJob()
                val task = CoroutineScope(context.minusKey(Job) + owner).async(start = CoroutineStart.LAZY) { block() }
                Entry(owner, task)
            }.also { it.waiters++ }
        }
        try {
            return entry.result.await()
        } finally {
            withContext(NonCancellable) {
                lock.withLock {
                    entry.waiters--
                    if (entry.waiters == 0) {
                        if (pending[key] === entry) pending.remove(key)
                        entry.owner.cancel()
                    }
                }
            }
        }
    }
}
