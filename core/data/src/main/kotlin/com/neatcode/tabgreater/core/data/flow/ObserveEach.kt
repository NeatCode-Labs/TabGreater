package com.neatcode.tabgreater.core.data.flow

import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Keeps one [source] flow alive per key of the **current** key set and merges their latest
 * values into a map.
 *
 * Unlike `flatMapLatest { keys -> combine(keys.map(source)) }`, a change of the key set only
 * starts flows for the keys that were added and cancels the ones that were removed — every
 * other key's flow (and whatever socket subscription it holds) keeps running untouched. That
 * is what lets the watchlist add or remove one ticker without re-fetching every sparkline.
 *
 * Semantics:
 * - A key missing from the map has not produced a value yet (or was removed).
 * - Every key-set change emits the map immediately (so removals are visible at once).
 * - Emissions are conflated; collectors only ever see the newest map.
 * - Source flows are collected in the caller's context; a source that throws fails the whole
 *   flow, so callers wrap `source` with `catch` if one key must not take the others down.
 */
fun <K, V> Flow<Set<K>>.observeEach(source: (K) -> Flow<V>): Flow<Map<K, V>> = channelFlow {
    val values = LinkedHashMap<K, V>()
    val jobs = HashMap<K, Job>()
    val lock = Mutex()

    collect { keys ->
        lock.withLock {
            val removed = jobs.keys.filterNot { it in keys }
            for (key in removed) {
                jobs.remove(key)?.cancel()
                values.remove(key)
            }
            for (key in keys) {
                if (key in jobs) continue
                lateinit var job: Job
                job = launch {
                    source(key).collect { value ->
                        // Building the map and sending it happen under the same lock, otherwise a
                        // value captured just before a key was removed could overtake the removal
                        // and resurrect that key in the map. `conflate()` fuses the channel to
                        // CONFLATED, so `send` never suspends and cannot deadlock the lock.
                        lock.withLock {
                            if (jobs[key] !== job) return@withLock
                            values[key] = value
                            send(LinkedHashMap(values))
                        }
                    }
                    // A source that completes on its own gives its slot back, so the next key-set
                    // emission re-subscribes it instead of leaving the key dead for the session.
                    lock.withLock { if (jobs[key] === job) jobs.remove(key) }
                }
                jobs[key] = job
            }
            send(LinkedHashMap(values))
        }
    }
}.conflate()
