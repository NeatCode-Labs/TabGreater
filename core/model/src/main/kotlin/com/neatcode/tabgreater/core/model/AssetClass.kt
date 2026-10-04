package com.neatcode.tabgreater.core.model

import kotlinx.serialization.Serializable

/**
 * What a market's base asset is. [id] is the stable string used in persistence.
 *
 * [STOCK] covers tokenized shares ("stock tokens") that crypto exchanges list as ordinary spot
 * markets next to their coins; the exchange adapters classify them from each catalogue.
 */
@Serializable
enum class AssetClass(val id: String) {
    CRYPTO("crypto"),
    STOCK("stock");

    companion object {
        private val byId = entries.associateBy { it.id }

        /** Unknown or missing ids read as [CRYPTO], the class of every market stored before stocks. */
        fun fromId(id: String?): AssetClass = id?.let(byId::get) ?: CRYPTO
    }
}
