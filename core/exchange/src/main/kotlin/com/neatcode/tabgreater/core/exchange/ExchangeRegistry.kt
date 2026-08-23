package com.neatcode.tabgreater.core.exchange

import com.neatcode.tabgreater.core.model.ExchangeId

/** Lookup of adapters by exchange; only exchanges with an adapter are "supported". */
class ExchangeRegistry(adapters: List<ExchangeAdapter>) {
    private val byId: Map<ExchangeId, ExchangeAdapter> = adapters.associateBy { it.id }

    val supported: Set<ExchangeId> get() = byId.keys

    operator fun get(id: ExchangeId): ExchangeAdapter =
        byId[id] ?: throw IllegalArgumentException("No adapter for ${id.id}")

    fun getOrNull(id: ExchangeId): ExchangeAdapter? = byId[id]

    val all: Collection<ExchangeAdapter> get() = byId.values
}
