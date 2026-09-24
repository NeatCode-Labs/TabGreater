package com.neatcode.tabgreater.feature.chart

import kotlinx.serialization.Serializable

/** The sections of the "Draw" sheet, in display order. */
enum class DrawingGroup { LINES, CHANNELS, FIBONACCI, SHAPES, PATTERNS, ANNOTATIONS }

/**
 * One drawing tool. [name] is the KLineChart overlay template it creates (a built-in one or one
 * registered by `assets/chart/overlays.js`); it is also what a persisted drawing is keyed by.
 *
 * @property requiresText the overlay is a text annotation: completing it asks the user for the text.
 * @property offered listed in the "Draw" sheet. The three KLineChart built-ins that are not
 *   (segment variants a trend line or ray already covers) are still accepted when restoring.
 */
data class DrawingTool(
    val name: String,
    val group: DrawingGroup,
    val requiresText: Boolean = false,
    val offered: Boolean = true,
)

/**
 * Every drawing the chart knows, grouped as the sheet shows them: the 16 overlay templates
 * KLineChart ships plus the 17 ported from KLineChart Pro. 30 of them are offered in the sheet
 * ([DrawingTool.offered]); all 33 are valid in a persisted set. Resource-free on purpose: the
 * user-visible labels are mapped from [DrawingTool.name] in `:app`.
 */
object DrawingCatalogue {

    val tools: List<DrawingTool> = listOf(
        // Lines
        DrawingTool("segment", DrawingGroup.LINES),
        DrawingTool("rayLine", DrawingGroup.LINES),
        DrawingTool("straightLine", DrawingGroup.LINES),
        DrawingTool("arrow", DrawingGroup.LINES),
        DrawingTool("horizontalStraightLine", DrawingGroup.LINES),
        DrawingTool("horizontalRayLine", DrawingGroup.LINES),
        DrawingTool("horizontalSegment", DrawingGroup.LINES, offered = false),
        DrawingTool("verticalStraightLine", DrawingGroup.LINES),
        DrawingTool("verticalRayLine", DrawingGroup.LINES, offered = false),
        DrawingTool("verticalSegment", DrawingGroup.LINES, offered = false),
        DrawingTool("priceLine", DrawingGroup.LINES),
        // Channels
        DrawingTool("parallelStraightLine", DrawingGroup.CHANNELS),
        DrawingTool("priceChannelLine", DrawingGroup.CHANNELS),
        // Fibonacci
        DrawingTool("fibonacciLine", DrawingGroup.FIBONACCI),
        DrawingTool("fibonacciExtension", DrawingGroup.FIBONACCI),
        DrawingTool("fibonacciSegment", DrawingGroup.FIBONACCI),
        DrawingTool("fibonacciCircle", DrawingGroup.FIBONACCI),
        DrawingTool("fibonacciSpiral", DrawingGroup.FIBONACCI),
        DrawingTool("fibonacciSpeedResistanceFan", DrawingGroup.FIBONACCI),
        DrawingTool("gannBox", DrawingGroup.FIBONACCI),
        // Shapes
        DrawingTool("rect", DrawingGroup.SHAPES),
        DrawingTool("circle", DrawingGroup.SHAPES),
        DrawingTool("triangle", DrawingGroup.SHAPES),
        DrawingTool("parallelogram", DrawingGroup.SHAPES),
        DrawingTool("brush", DrawingGroup.SHAPES),
        // Patterns
        DrawingTool("threeWaves", DrawingGroup.PATTERNS),
        DrawingTool("fiveWaves", DrawingGroup.PATTERNS),
        DrawingTool("eightWaves", DrawingGroup.PATTERNS),
        DrawingTool("anyWaves", DrawingGroup.PATTERNS),
        DrawingTool("abcd", DrawingGroup.PATTERNS),
        DrawingTool("xabcd", DrawingGroup.PATTERNS),
        // Annotations
        DrawingTool("simpleAnnotation", DrawingGroup.ANNOTATIONS, requiresText = true),
        DrawingTool("simpleTag", DrawingGroup.ANNOTATIONS, requiresText = true),
    )

    private val byName: Map<String, DrawingTool> = tools.associateBy { it.name }

    /** Every template name the app creates or restores. */
    val names: Set<String> = byName.keys

    /** The tools the "Draw" sheet lists, in sheet order. */
    val offered: List<DrawingTool> = tools.filter { it.offered }

    fun find(name: String?): DrawingTool? = if (name == null) null else byName[name]

    /** The [offered] tools of [group] in sheet order. */
    fun inGroup(group: DrawingGroup): List<DrawingTool> = offered.filter { it.group == group }
}

/**
 * Snapping of new and edited drawing points to the nearest bar's OHLC value. [jsValue] is what
 * `tg.setMagnet` takes; KLineChart's own name for [NONE] is `normal`.
 */
@Serializable
enum class MagnetMode(val jsValue: String) {
    NONE("none"),
    WEAK("weak_magnet"),
    STRONG("strong_magnet"),
    ;

    companion object {
        /** Parses a persisted enum name, falling back to [WEAK] for anything unknown. */
        fun fromNameOrDefault(name: String?): MagnetMode = entries.firstOrNull { it.name == name } ?: WEAK
    }
}
