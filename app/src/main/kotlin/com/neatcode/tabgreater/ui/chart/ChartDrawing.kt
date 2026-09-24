package com.neatcode.tabgreater.ui.chart

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldDecorator
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.neatcode.tabgreater.R
import com.neatcode.tabgreater.core.model.TGDimens
import com.neatcode.tabgreater.feature.chart.DrawingCatalogue
import com.neatcode.tabgreater.feature.chart.DrawingGroup
import com.neatcode.tabgreater.feature.chart.DrawingState
import com.neatcode.tabgreater.feature.chart.DrawingTool
import com.neatcode.tabgreater.feature.chart.MagnetMode
import com.neatcode.tabgreater.ui.components.ImmersiveDialogWindow
import com.neatcode.tabgreater.ui.components.TGBottomSheet
import com.neatcode.tabgreater.ui.components.TGSheetOption
import com.neatcode.tabgreater.ui.icons.TGIcons
import com.neatcode.tabgreater.ui.theme.TG
import com.neatcode.tabgreater.ui.theme.TGType
import java.util.Locale

// ---------------------------------------------------------------------------------- labels

/** The UI label of every drawing tool, keyed by its overlay template name. */
internal val DrawingToolLabels: Map<String, Int> = mapOf(
    "segment" to R.string.chart_draw_tool_segment,
    "rayLine" to R.string.chart_draw_tool_ray_line,
    "straightLine" to R.string.chart_draw_tool_straight_line,
    "arrow" to R.string.chart_draw_tool_arrow,
    "horizontalStraightLine" to R.string.chart_draw_tool_horizontal_straight_line,
    "horizontalRayLine" to R.string.chart_draw_tool_horizontal_ray_line,
    "horizontalSegment" to R.string.chart_draw_tool_horizontal_segment,
    "verticalStraightLine" to R.string.chart_draw_tool_vertical_straight_line,
    "verticalRayLine" to R.string.chart_draw_tool_vertical_ray_line,
    "verticalSegment" to R.string.chart_draw_tool_vertical_segment,
    "priceLine" to R.string.chart_draw_tool_price_line,
    "parallelStraightLine" to R.string.chart_draw_tool_parallel_straight_line,
    "priceChannelLine" to R.string.chart_draw_tool_price_channel_line,
    "fibonacciLine" to R.string.chart_draw_tool_fibonacci_line,
    "fibonacciExtension" to R.string.chart_draw_tool_fibonacci_extension,
    "fibonacciSegment" to R.string.chart_draw_tool_fibonacci_segment,
    "fibonacciCircle" to R.string.chart_draw_tool_fibonacci_circle,
    "fibonacciSpiral" to R.string.chart_draw_tool_fibonacci_spiral,
    "fibonacciSpeedResistanceFan" to R.string.chart_draw_tool_fibonacci_speed_resistance_fan,
    "gannBox" to R.string.chart_draw_tool_gann_box,
    "rect" to R.string.chart_draw_tool_rect,
    "circle" to R.string.chart_draw_tool_circle,
    "triangle" to R.string.chart_draw_tool_triangle,
    "parallelogram" to R.string.chart_draw_tool_parallelogram,
    "brush" to R.string.chart_draw_tool_brush,
    "threeWaves" to R.string.chart_draw_tool_three_waves,
    "fiveWaves" to R.string.chart_draw_tool_five_waves,
    "eightWaves" to R.string.chart_draw_tool_eight_waves,
    "anyWaves" to R.string.chart_draw_tool_any_waves,
    "abcd" to R.string.chart_draw_tool_abcd,
    "xabcd" to R.string.chart_draw_tool_xabcd,
    "simpleAnnotation" to R.string.chart_draw_tool_simple_annotation,
    "simpleTag" to R.string.chart_draw_tool_simple_tag,
)

/** Label resource of the tool called [name]; a generic "Drawing" for anything outside the catalogue. */
@StringRes
internal fun drawingToolLabelRes(name: String?): Int =
    name?.let(DrawingToolLabels::get) ?: R.string.chart_draw_tool_unknown

@get:StringRes
internal val DrawingGroup.labelRes: Int
    get() = when (this) {
        DrawingGroup.LINES -> R.string.chart_draw_group_lines
        DrawingGroup.CHANNELS -> R.string.chart_draw_group_channels
        DrawingGroup.FIBONACCI -> R.string.chart_draw_group_fibonacci
        DrawingGroup.SHAPES -> R.string.chart_draw_group_shapes
        DrawingGroup.PATTERNS -> R.string.chart_draw_group_patterns
        DrawingGroup.ANNOTATIONS -> R.string.chart_draw_group_annotations
    }

@get:StringRes
private val MagnetMode.labelRes: Int
    get() = when (this) {
        MagnetMode.NONE -> R.string.chart_draw_magnet_none
        MagnetMode.WEAK -> R.string.chart_draw_magnet_weak
        MagnetMode.STRONG -> R.string.chart_draw_magnet_strong
    }

// ----------------------------------------------------------------------------------- sheet

/** Height of one tool cell, the same as a [TGSheetOption] row. */
private val ToolCellHeight = 44.dp

/** From this window width on the tools sit three to a row (landscape, tablets), else two. */
private val WideSheetFrom = 600.dp

/**
 * The scrolling part of the sheet never takes more than this share of the window. With the handle
 * and the title on top the sheet then stays well clear of the status bar, and because it is never
 * taller than the screen it has a single resting height.
 */
private const val SheetListFraction = 0.6f

/** Used until the window has been measured (its size reads zero on the very first frame). */
private val SheetListFallbackHeight = 420.dp

/**
 * The "Draw" sheet: the tools in a two-column grid (three on wide windows) grouped under captions,
 * then the magnet as one row of three and the other options. Picking a tool closes the sheet so the
 * canvas is free for the taps that place it; the options keep it open.
 *
 * The sheet skips Material's half-expanded state: it opens at its full (capped) height, and one
 * Back or one swipe down closes it. A half-open sheet would cover the chart toolbar, and a tap
 * meant for the toolbar would arm a tool instead (emulator QA).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DrawingSheet(
    drawingState: DrawingState,
    magnetMode: MagnetMode,
    drawingsVisible: Boolean,
    onPickTool: (String) -> Unit,
    onMagnetMode: (MagnetMode) -> Unit,
    onDrawingsVisible: (Boolean) -> Unit,
    onDeleteAll: () -> Unit,
    onDismiss: () -> Unit,
    immersive: Boolean = false,
) {
    val window = LocalWindowInfo.current.containerDpSize
    val columns = if (window.width >= WideSheetFrom) 3 else 2
    val listMaxHeight = if (window.height > 0.dp) window.height * SheetListFraction else SheetListFallbackHeight
    TGBottomSheet(
        onDismiss = onDismiss,
        title = stringResource(R.string.chart_draw_title),
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        immersive = immersive,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = listMaxHeight)
                .verticalScroll(rememberScrollState()),
        ) {
            DrawingGroup.entries.forEach { group ->
                SheetCaption(stringResource(group.labelRes))
                CellGrid(DrawingCatalogue.inGroup(group), columns) { tool: DrawingTool ->
                    SheetCell(
                        label = stringResource(drawingToolLabelRes(tool.name)),
                        selected = drawingState.drawing && drawingState.tool == tool.name,
                        role = Role.Button,
                        onClick = { onPickTool(tool.name) },
                    )
                }
            }
            SheetCaption(stringResource(R.string.chart_draw_group_magnet))
            CellGrid(MagnetMode.entries, MagnetMode.entries.size) { mode: MagnetMode ->
                SheetCell(
                    label = stringResource(mode.labelRes),
                    selected = mode == magnetMode,
                    role = Role.RadioButton,
                    onClick = { onMagnetMode(mode) },
                )
            }
            SheetCaption(stringResource(R.string.chart_draw_group_options))
            TGSheetOption(
                label = stringResource(R.string.chart_draw_show),
                checked = drawingsVisible,
                onClick = { onDrawingsVisible(!drawingsVisible) },
            )
            TGSheetOption(
                label = stringResource(R.string.chart_draw_delete_all),
                checked = false,
                onClick = onDeleteAll,
                trailingText = drawingState.count.takeIf { it > 0 }?.toString(),
                enabled = drawingState.count > 0,
            )
        }
    }
}

/** [items] laid out [columns] to a row; a short last row keeps its cells at the same width. */
@Composable
private fun <T> CellGrid(items: List<T>, columns: Int, cell: @Composable RowScope.(T) -> Unit) {
    items.chunked(columns).forEach { row ->
        Row(Modifier.fillMaxWidth()) {
            row.forEach { item -> cell(item) }
            repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

/**
 * One cell of the sheet's grid: a [TGSheetOption] cut to share its row. The current value (the
 * magnet mode, the tool being placed) is marked by an accent label rather than a trailing check,
 * which in a grid would sit right before the next cell's label and read as belonging to it.
 * [selected] is also exposed to accessibility services ([role] `RadioButton` for the magnet).
 */
@Composable
private fun RowScope.SheetCell(label: String, selected: Boolean, role: Role, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .weight(1f)
            .height(ToolCellHeight)
            .selectable(selected = selected, role = role, onClick = onClick)
            .padding(start = 16.dp, end = 8.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = label,
            style = TGType.sheetItem,
            color = if (selected) TG.Accent else TGType.sheetItem.color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** A section caption inside the sheet, in the settings screen's accent caps. */
@Composable
private fun SheetCaption(text: String) {
    Text(
        text = text.uppercase(Locale.ROOT),
        style = TGType.sectionHeader,
        maxLines = 1,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 6.dp),
    )
}

// ----------------------------------------------------------------------------------- strip

private val StripShape = RoundedCornerShape(percent = 50)

/** Pills of the strip are taller than the `log` / `auto` pills: they are the only way to act on a drawing. */
internal const val STRIP_HEIGHT_DP = TGDimens.CHIP_H_DP + 8
private val StripPillHeight = STRIP_HEIGHT_DP.dp

/** Gap between the strip and the edges of the plot it sits in. */
internal const val STRIP_MARGIN_DP = 8

/** Where the strip goes while the page has not reported its plot: just under the candle legend. */
internal const val STRIP_FALLBACK_TOP_DP = 48

/** Least gap between the candle legend's bottom edge and the strip. */
internal const val STRIP_LEGEND_GAP_DP = 4

/**
 * Touch target of a strip button. The pill drawn for it stays [STRIP_HEIGHT_DP] tall; the target
 * reaches past it, so the strip's row is this tall and centred on the pills.
 */
internal const val STRIP_TOUCH_DP = 48

/** How far a button's touch target reaches past its pill above and below (and left and right). */
internal const val STRIP_TOUCH_OVERHANG_DP = (STRIP_TOUCH_DP - STRIP_HEIGHT_DP) / 2

/**
 * The strip's place on the canvas, in dp from the canvas' top-left corner, and the widest it may
 * be (`null`: no limit known).
 */
internal data class StripSlot(val startDp: Int, val topDp: Int, val maxWidthDp: Int?)

/**
 * The bottom-left corner of the candle pane's plot ([DrawingState.plotLeft] ..
 * [DrawingState.plotBottom]). Nothing that has to stay readable lives there: the candle legend
 * is at the top of that pane, an indicator's at the top of its own pane below, the price labels
 * are right of [DrawingState.plotWidth] and the time labels under the plot. The strip is also
 * kept [bottomReserveDp] above the canvas' bottom edge, where the `log` / `auto` pills (and, in
 * fullscreen, the toolbar) float over it.
 *
 * A candle pane too short for that (many indicator panes, a landscape window) would put the
 * strip on the legend ([DrawingState.legendBottom]). It then moves down to the bottom-left of the
 * lowest pane, where only indicator lines are, and when even that is not below the legend (no
 * indicator pane) it sits right under the legend: the legend always stays readable.
 *
 * `topDp` is where the pills are drawn; the buttons' touch targets reach
 * [STRIP_TOUCH_OVERHANG_DP] above and below it.
 *
 * @param canvasHeightDp height of the canvas the slot is measured in.
 */
internal fun stripSlot(state: DrawingState, canvasHeightDp: Int, bottomReserveDp: Int): StripSlot {
    if (state.plotWidth <= 0 || state.plotBottom <= 0) {
        return StripSlot(STRIP_MARGIN_DP, STRIP_FALLBACK_TOP_DP, null)
    }
    val lowestTop = canvasHeightDp - bottomReserveDp - STRIP_HEIGHT_DP
    val preferred = minOf(state.plotBottom - STRIP_MARGIN_DP - STRIP_HEIGHT_DP, lowestTop)
    val clearOfLegend = state.legendBottom + STRIP_LEGEND_GAP_DP
    val top = when {
        state.legendBottom <= 0 || preferred >= clearOfLegend -> preferred   // 0: not reported
        lowestTop >= clearOfLegend -> lowestTop
        else -> clearOfLegend
    }.coerceAtLeast(0)
    val maxWidth = (state.plotWidth - 2 * STRIP_MARGIN_DP).coerceAtLeast(STRIP_HEIGHT_DP)
    return StripSlot(state.plotLeft + STRIP_MARGIN_DP, top, maxWidth)
}

/**
 * The floating strip in the bottom-left corner of the candle pane's plot ([stripSlot]). While a
 * tool is being placed it reads "<tool> · tap to place" with a cancel ✕; while a drawing is
 * selected it names the drawing and offers lock/unlock and delete. Nothing is drawn otherwise.
 * A label that does not fit the plot's width is cut with an ellipsis; the buttons always show.
 *
 * @param canvasHeight height of the canvas box the strip is placed in (top-start aligned).
 * @param bottomReserve what floats over the bottom of that box and must stay uncovered.
 */
@Composable
internal fun DrawingStrip(
    state: DrawingState,
    onCancel: () -> Unit,
    onToggleLock: () -> Unit,
    onDelete: () -> Unit,
    canvasHeight: Dp,
    bottomReserve: Dp,
    modifier: Modifier = Modifier,
) {
    if (!state.busy) return
    val slot = stripSlot(state, canvasHeight.value.toInt(), bottomReserve.value.toInt())
    // The row is as tall as the buttons' touch targets and centred on the pills at slot.topDp.
    // The buttons follow each other with no gap: their pills are then 2 * STRIP_TOUCH_OVERHANG_DP
    // apart, so lock and delete are hard to hit by mistake.
    val placed = modifier
        .offset(x = slot.startDp.dp, y = (slot.topDp - STRIP_TOUCH_OVERHANG_DP).dp)
        .height(STRIP_TOUCH_DP.dp)
        .then(if (slot.maxWidthDp != null) Modifier.widthIn(max = slot.maxWidthDp.dp) else Modifier)
    when {
        state.drawing -> Row(
            placed,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StripLabel(stringResource(R.string.chart_draw_placing, stringResource(drawingToolLabelRes(state.tool))))
            StripIcon(Icons.Outlined.Close, stringResource(R.string.cd_chart_draw_cancel), onCancel)
        }

        else -> Row(
            placed,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StripLabel(stringResource(drawingToolLabelRes(state.selectedName)))
            StripIcon(
                imageVector = if (state.selectedLocked) TGIcons.Lock else TGIcons.LockOpen,
                contentDescription = stringResource(
                    if (state.selectedLocked) R.string.cd_chart_draw_unlock else R.string.cd_chart_draw_lock,
                ),
                onClick = onToggleLock,
                active = state.selectedLocked,
            )
            StripIcon(Icons.Outlined.Delete, stringResource(R.string.cd_chart_draw_delete), onDelete)
        }
    }
}

@Composable
private fun RowScope.StripLabel(text: String) {
    Box(
        modifier = Modifier
            .weight(1f, fill = false)
            .height(StripPillHeight)
            .widthIn(max = 220.dp)
            .clip(StripShape)
            .background(TG.ChipFill)
            .border(1.dp, TG.Outline, StripShape)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = TGType.toolbarChip,
            color = TG.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** A strip button: a round [StripPillHeight] pill inside a [STRIP_TOUCH_DP] square touch target. */
@Composable
private fun StripIcon(
    imageVector: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    active: Boolean = false,
) {
    Box(
        modifier = Modifier
            .size(STRIP_TOUCH_DP.dp)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(StripPillHeight)
                .clip(StripShape)
                .background(TG.ChipFill)
                .border(1.dp, if (active) TG.Accent else TG.Outline, StripShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = imageVector,
                contentDescription = contentDescription,
                tint = if (active) TG.Accent else TG.TextPrimary,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

// --------------------------------------------------------------------------------- dialogs

/**
 * Asks for the text of a just-placed "Text" or "Tag" drawing, titled after the tool ([tool], the
 * template name). OK is only enabled with some text; cancelling removes the drawing, which would
 * otherwise sit on the chart empty.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DrawingTextDialog(
    tool: String?,
    onConfirm: (String) -> Unit,
    onCancel: () -> Unit,
    immersive: Boolean = false,
) {
    val textFieldState = rememberTextFieldState()
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(focusRequester) { focusRequester.requestFocus() }
    val canConfirm = textFieldState.text.isNotBlank()

    val title = stringResource(textDialogTitleRes(tool))
    // A landscape phone is 360 dp tall and the keyboard takes ~265 dp of that: the usual
    // title / field / buttons stack cannot fit above it, so the dialog collapses to one row with
    // the title as the field's placeholder.
    val compact = LocalWindowInfo.current.containerDpSize.height < CompactTextDialogBelow
    val confirm = { onConfirm(textFieldState.text.toString().trim()) }

    val field: @Composable (Modifier) -> Unit = { modifier ->
        Box(
            modifier
                .heightIn(min = TextFieldHeight)
                .clip(RoundedCornerShape(TextFieldCorner))
                .background(TG.ChipFill)
                .border(1.dp, TG.Outline, RoundedCornerShape(TextFieldCorner))
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            BasicTextField(
                state = textFieldState,
                modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                textStyle = TGType.sheetItem,
                lineLimits = TextFieldLineLimits.SingleLine,
                cursorBrush = SolidColor(TG.Accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                onKeyboardAction = { if (canConfirm) confirm() },
                decorator = TextFieldDecorator { innerTextField ->
                    if (compact && textFieldState.text.isEmpty()) {
                        Text(text = title, style = TGType.sheetItem, color = TG.TextTertiary)
                    }
                    innerTextField()
                },
            )
        }
    }
    val buttons: @Composable RowScope.() -> Unit = {
        TextButton(onClick = onCancel) {
            Text(text = stringResource(R.string.action_cancel), style = TGType.button)
        }
        TextButton(onClick = confirm, enabled = canConfirm) {
            Text(
                text = stringResource(R.string.action_ok),
                style = TGType.button,
                color = if (canConfirm) TG.Accent else TG.TextTertiary,
            )
        }
    }

    BasicAlertDialog(
        onDismissRequest = onCancel,
        // The immersive window no longer fits system windows, so the keyboard would cover the
        // buttons; the padding lifts the dialog above it (a no-op when the window resizes itself).
        modifier = Modifier.imePadding(),
    ) {
        if (immersive) ImmersiveDialogWindow()
        Surface(
            shape = RoundedCornerShape(TextDialogCorner),
            color = TG.NavSurface,
            contentColor = TG.TextPrimary,
        ) {
            if (compact) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    field(Modifier.weight(1f))
                    Spacer(Modifier.width(8.dp))
                    buttons()
                }
            } else {
                Column(Modifier.padding(24.dp)) {
                    Text(text = title, style = TGType.sheetTitle)
                    Spacer(Modifier.height(16.dp))
                    field(Modifier.fillMaxWidth())
                    Spacer(Modifier.height(24.dp))
                    Row(Modifier.align(Alignment.End), content = buttons)
                }
            }
        }
    }
}

/** Below this window height the text dialog is one row (a phone in landscape). */
private val CompactTextDialogBelow = 480.dp
private val TextDialogCorner = 28.dp

/** The text dialog's title: the tool's own label ("Text", "Tag"), "Text" when it is not known. */
@StringRes
internal fun textDialogTitleRes(tool: String?): Int =
    if (tool != null && tool in DrawingToolLabels) drawingToolLabelRes(tool) else R.string.chart_draw_text_title

/**
 * "Delete all drawings?" for the market on screen, named with its exchange ([market], e.g.
 * "Binance BTC/USDT"): drawings are kept per exchange. The delete cannot be undone.
 *
 * @param immersive keep the system bars hidden while the dialog is up (fullscreen chart).
 */
@Composable
internal fun DeleteAllDrawingsDialog(
    market: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    immersive: Boolean = false,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = TG.NavSurface,
        titleContentColor = TG.TextPrimary,
        textContentColor = TG.TextSecondary,
        title = {
            if (immersive) ImmersiveDialogWindow()
            Text(text = stringResource(R.string.chart_draw_delete_all_title), style = TGType.sheetTitle)
        },
        text = { Text(text = stringResource(R.string.chart_draw_delete_all_body, market), style = TGType.body) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = stringResource(R.string.chart_draw_delete_all_confirm),
                    style = TGType.button,
                    color = TG.Down,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.action_cancel), style = TGType.button)
            }
        },
    )
}

private val TextFieldHeight = 44.dp
private val TextFieldCorner = 8.dp
