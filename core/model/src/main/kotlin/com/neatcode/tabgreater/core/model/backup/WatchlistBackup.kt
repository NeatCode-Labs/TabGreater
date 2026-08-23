package com.neatcode.tabgreater.core.model.backup

import com.neatcode.tabgreater.core.model.MarketKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Portable JSON form of every watchlist (Settings → Export / Import).
 *
 * The file is meant to survive app reinstalls and to be hand-editable, so it carries only
 * stable identifiers: watchlist names, the `SparkPeriod` / `TileSize` / `SortMode` ids and
 * canonical market keys. Database ids are deliberately absent.
 *
 * ```json
 * {
 *   "format": "tabgreater-watchlists",
 *   "version": 1,
 *   "exportedAt": 1787000000000,
 *   "watchlists": [
 *     { "name": "Main", "period": "24h", "tileSize": "small", "sort": "custom",
 *       "items": [ { "key": "binance:BTC/EUR", "accentColor": "#FFFFBF66" } ] }
 *   ]
 * }
 * ```
 */
@Serializable
data class WatchlistBackup(
    val format: String = FORMAT,
    val version: Int = VERSION,
    /** Epoch millis of the export. */
    val exportedAt: Long,
    val watchlists: List<WatchlistBackupEntry>,
) {
    companion object {
        const val FORMAT = "tabgreater-watchlists"
        const val VERSION = 1
    }
}

/** One watchlist: its per-list settings (by enum id) and its tickers in custom order. */
@Serializable
data class WatchlistBackupEntry(
    val name: String,
    val period: String = "24h",
    @SerialName("tileSize") val tileSize: String = "small",
    val sort: String = "custom",
    val items: List<WatchlistBackupItem> = emptyList(),
)

/**
 * One ticker. [key] is the canonical `"exchange:BASE/QUOTE"`; [accentColor] is `#AARRGGBB`
 * (upper case, 8 hex digits) or `null` for no stripe.
 */
@Serializable
data class WatchlistBackupItem(
    val key: String,
    @SerialName("accentColor") val accentColor: String? = null,
) {
    /** `null` when [key] is not a valid canonical key for a supported exchange. */
    val marketKey: MarketKey? get() = MarketKey.parseOrNull(key)

    /** `null` when absent or malformed. */
    val accentArgb: Long? get() = accentColor?.let(WatchlistBackupCodec::parseArgb)
}

/** Error raised by [WatchlistBackupCodec.decode] for input that is not a TabGreater backup. */
class BackupFormatException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** JSON encoding of [WatchlistBackup] with kotlinx.serialization (unknown keys ignored). */
object WatchlistBackupCodec {

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    }

    fun encode(backup: WatchlistBackup): String = json.encodeToString(WatchlistBackup.serializer(), backup)

    /**
     * Parses [text]. Fails with [BackupFormatException] when the JSON is malformed, the
     * `format` marker is wrong or the `version` is newer than this build understands.
     */
    fun decode(text: String): Result<WatchlistBackup> = runCatching {
        val backup = try {
            json.decodeFromString(WatchlistBackup.serializer(), text)
        } catch (e: IllegalArgumentException) {
            throw BackupFormatException("Not a TabGreater watchlist backup", e)
        }
        if (backup.format != WatchlistBackup.FORMAT) {
            throw BackupFormatException("Unexpected format '${backup.format}'")
        }
        if (backup.version > WatchlistBackup.VERSION) {
            throw BackupFormatException("Backup version ${backup.version} is newer than this app supports")
        }
        backup
    }

    /** `0xFFFFBF66` → `"#FFFFBF66"`. */
    fun formatArgb(argb: Long): String = "#%08X".format(argb and 0xFFFFFFFFL)

    /** `"#FFFFBF66"` → `0xFFFFBF66`; `"#FFBF66"` (no alpha) is treated as opaque. `null` when malformed. */
    fun parseArgb(text: String): Long? {
        val hex = text.trim().removePrefix("#")
        val value = when (hex.length) {
            8 -> hex.toLongOrNull(16)
            6 -> hex.toLongOrNull(16)?.let { it or 0xFF000000L }
            else -> null
        }
        return value
    }
}
