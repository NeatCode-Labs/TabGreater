package com.neatcode.tabgreater.core.data.db

import androidx.room.withTransaction

/**
 * Runs a block of DAO calls inside one database transaction.
 *
 * The watchlist operations that span both tables (restore, move between lists, import) are plain
 * Kotlin in [com.neatcode.tabgreater.core.data.repo.RoomWatchlistRepository] so they stay unit
 * testable against fake DAOs. A `@Transaction` DAO method cannot wrap them because it only sees
 * its own DAO, so the repository takes this one-method indirection instead of the whole
 * [TabGreaterDatabase].
 */
interface TransactionRunner {
    suspend operator fun <R> invoke(block: suspend () -> R): R

    /** Runs the block as-is — the default, and what unit tests with in-memory fakes use. */
    object Direct : TransactionRunner {
        override suspend fun <R> invoke(block: suspend () -> R): R = block()
    }
}

/** [TransactionRunner] backed by Room's `withTransaction`. */
class RoomTransactionRunner(private val database: TabGreaterDatabase) : TransactionRunner {
    override suspend fun <R> invoke(block: suspend () -> R): R = database.withTransaction(block)
}
