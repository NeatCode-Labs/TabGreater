package com.neatcode.tabgreater.feature.chart

import kotlinx.serialization.builtins.serializer

/**
 * A one-shot instruction to the chart's drawing layer. [seq] grows with every command a screen
 * issues, so issuing the same [action] twice is still two commands.
 */
data class DrawingCommand(val seq: Int, val action: DrawingAction)

/** What a [DrawingCommand] asks the drawing layer to do; each maps to one `tg.*` call. */
sealed interface DrawingAction {
    /** Start placing an overlay of template [name]; an overlay still being placed is cancelled first. */
    data class StartTool(val name: String) : DrawingAction

    /** Discard the overlay being placed, if any. */
    data object Cancel : DrawingAction

    data object RemoveSelected : DrawingAction
    data object ToggleSelectedLock : DrawingAction
    data object Deselect : DrawingAction

    /** [Cancel] and [Deselect] at once: the canvas as a picture, without handles or a half-placed tool. */
    data object ClearFocus : DrawingAction

    /** Remove every drawing of the current market. */
    data object ClearAll : DrawingAction

    /** Give annotation [id] its [text]. */
    data class SetText(val id: String, val text: String) : DrawingAction

    /** Remove overlay [id] (an annotation whose text dialog was cancelled). */
    data class RemoveDrawing(val id: String) : DrawingAction
}

/** The JavaScript that carries out [action]; every call is guarded so a page mid-reload ignores it. */
internal fun DrawingAction.toJs(): String = when (this) {
    is DrawingAction.StartTool -> "window.tg&&tg.startDrawing(${jsString(name)})"
    DrawingAction.Cancel -> "window.tg&&tg.cancelDrawing()"
    DrawingAction.RemoveSelected -> "window.tg&&tg.removeSelectedDrawing()"
    DrawingAction.ToggleSelectedLock -> "window.tg&&tg.toggleSelectedLock()"
    DrawingAction.Deselect -> "window.tg&&tg.deselectDrawing()"
    DrawingAction.ClearFocus -> "window.tg&&(tg.cancelDrawing(),tg.deselectDrawing())"
    DrawingAction.ClearAll -> "window.tg&&tg.clearDrawings()"
    is DrawingAction.SetText -> "window.tg&&tg.setDrawingText(${jsString(id)},${jsString(text)})"
    is DrawingAction.RemoveDrawing -> "window.tg&&tg.removeDrawing(${jsString(id)})"
}

/** [value] as a JSON string literal, which is also a valid JavaScript string literal. */
internal fun jsString(value: String): String = ChartProtocol.json.encodeToString(String.serializer(), value)
