package com.neatcode.tabgreater.feature.chart

import android.util.Log
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** One anchor of a drawing: a bar open time (epoch millis) and a price. */
@Serializable
data class DrawingPoint(
    val timestamp: Long? = null,
    val value: Double? = null,
)

/**
 * One completed user drawing, in the shape `chart.js` hands over and takes back.
 *
 * @property name the KLineChart overlay template ([DrawingCatalogue.names]).
 * @property points every point of the completed overlay.
 * @property mode KLineChart's `OverlayMode`: `normal`, `weak_magnet` or `strong_magnet`.
 * @property text the annotation text of a [DrawingTool.requiresText] tool, else `null`.
 */
@Serializable
data class Drawing(
    val name: String = "",
    val points: List<DrawingPoint> = emptyList(),
    val lock: Boolean = false,
    val mode: String = DrawingsCodec.MODE_WEAK,
    val text: String? = null,
)

/** `tg.setDrawings` argument and `drawingsChanged` payload: the drawings of one market. */
@Serializable
data class DrawingsPayload(
    val exchange: String = "",
    val ticker: String = "",
    val drawings: List<Drawing> = emptyList(),
)

/**
 * `drawingState` payload: what the drawing layer is doing right now.
 *
 * @property drawing an overlay is being placed with [tool].
 * @property selectedId the overlay the user tapped, with its template [selectedName].
 * @property count completed user drawings on the chart.
 * @property needsTextId a just-placed annotation still waiting for its text.
 * @property needsTextTool the template of [needsTextId] (`simpleAnnotation` or `simpleTag`).
 * @property plotLeft left edge of the candle pane's plot area, in dp from the canvas' left edge.
 * @property plotBottom bottom edge of that plot area, in dp from the canvas' top edge.
 * @property plotWidth width of that plot area in dp (the canvas minus the price axis).
 * @property legendBottom bottom edge of the candle legend (title, O/H/L/C row and a row per
 *   indicator drawn on the candle pane), in dp from the canvas' top edge.
 *   The plot fields and [legendBottom] are only filled in while a tool is placed or a drawing is
 *   selected, i.e. while the drawing strip is up; `0` otherwise and on pages that do not report them.
 */
@Serializable
data class DrawingState(
    val drawing: Boolean = false,
    val tool: String? = null,
    val selectedId: String? = null,
    val selectedName: String? = null,
    val selectedLocked: Boolean = false,
    val count: Int = 0,
    val needsTextId: String? = null,
    val needsTextTool: String? = null,
    val plotLeft: Int = 0,
    val plotBottom: Int = 0,
    val plotWidth: Int = 0,
    val legendBottom: Int = 0,
) {
    /** A tool is being placed or a drawing is selected: the canvas has something to put down. */
    val busy: Boolean get() = drawing || selectedId != null

    companion object {
        val IDLE = DrawingState()
    }
}

/**
 * JSON codec of the persisted drawing list. Everything that crosses into or out of Room goes
 * through [sanitize], so a drawing the chart cannot restore — an unknown template, no points, a
 * non-finite coordinate — is dropped instead of breaking the whole set.
 */
object DrawingsCodec {

    const val MODE_NORMAL = "normal"
    const val MODE_WEAK = "weak_magnet"
    const val MODE_STRONG = "strong_magnet"

    /** Longest annotation text kept; anything longer is cut, not rejected. */
    const val MAX_TEXT_LENGTH = 500

    private val MODES = setOf(MODE_NORMAL, MODE_WEAK, MODE_STRONG)
    private val listSerializer = ListSerializer(Drawing.serializer())
    private val json get() = ChartProtocol.json

    fun encode(drawings: List<Drawing>): String = json.encodeToString(listSerializer, drawings)

    /** The persisted array, sanitised; blank or unreadable input is an empty list. */
    fun decode(raw: String?): List<Drawing> {
        if (raw.isNullOrBlank()) return emptyList()
        val element = runCatching { json.parseToJsonElement(raw) }.getOrNull() ?: return emptyList()
        return fromJson(element)
    }

    /**
     * Decodes a JSON array element by element: one malformed drawing costs that drawing only.
     * Anything but an array is an empty list.
     */
    fun fromJson(element: JsonElement?): List<Drawing> {
        val array = element as? JsonArray ?: return emptyList()
        val decoded = array.mapNotNull { item ->
            runCatching { json.decodeFromJsonElement(Drawing.serializer(), item) }
                .onFailure { Log.w(CHART_LOG_TAG, "dropping unreadable drawing: ${it.message}") }
                .getOrNull()
        }
        return sanitize(decoded)
    }

    /**
     * Drops drawings the chart could not restore and normalises the rest: unknown template,
     * no points, a point without a timestamp or with a missing/non-finite price, an annotation
     * without text. An unknown [Drawing.mode] becomes `weak_magnet`; tools that take no text
     * lose any.
     */
    fun sanitize(drawings: List<Drawing>): List<Drawing> = drawings.mapNotNull { drawing ->
        val tool = DrawingCatalogue.find(drawing.name) ?: return@mapNotNull null
        if (drawing.points.isEmpty()) return@mapNotNull null
        val valid = drawing.points.all { p -> p.timestamp != null && p.value != null && p.value.isFinite() }
        if (!valid) return@mapNotNull null
        val text = if (tool.requiresText) drawing.text?.take(MAX_TEXT_LENGTH) else null
        if (tool.requiresText && text.isNullOrBlank()) return@mapNotNull null
        drawing.copy(
            mode = if (drawing.mode in MODES) drawing.mode else MODE_WEAK,
            text = text,
        )
    }

    /** KLineChart's `OverlayMode` for [magnet] (`none` is called `normal` there). */
    fun overlayMode(magnet: MagnetMode): String = when (magnet) {
        MagnetMode.NONE -> MODE_NORMAL
        MagnetMode.WEAK -> MODE_WEAK
        MagnetMode.STRONG -> MODE_STRONG
    }

    /** The `tg.setDrawings` argument for one market. */
    fun encodePayload(payload: DrawingsPayload): String =
        json.encodeToString(DrawingsPayload.serializer(), payload)

    /**
     * A `drawingsChanged` payload, read leniently: the market fields must be strings, the drawings
     * go through [fromJson]. `null` when the market is not named.
     */
    fun decodePayload(payload: JsonObject): DrawingsPayload? {
        val exchange = (payload["exchange"] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull
        val ticker = (payload["ticker"] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull
        if (exchange.isNullOrEmpty() || ticker.isNullOrEmpty()) return null
        return DrawingsPayload(exchange, ticker, fromJson(payload["drawings"]))
    }
}
