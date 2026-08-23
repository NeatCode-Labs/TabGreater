package com.neatcode.tabgreater.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.neatcode.tabgreater.R
import com.neatcode.tabgreater.ui.theme.TG
import com.neatcode.tabgreater.ui.theme.TGType

/** Height of one option row in a value picker such as the "Tickers Timeframe" sheet. */
private val OptionHeight = 44.dp

/** Same row with a second line of explanation under the label (Settings → Widget refresh). */
private val OptionHeightWithSupport = 56.dp

/** Text inset without a leading glyph, and with one (24 dp glyph at 16 dp + 16 dp gap). */
private val LabelInset = 16.dp
private val LabelInsetWithIcon = 56.dp

/** The handle row: a 32 × 4 dp bar with 8 dp above and below it. */
private val HandleRowHeight = 16.dp
private val HandleBarWidth = 32.dp
private val HandleBarHeight = 4.dp

/**
 * The app's bottom sheet: the dark surface, the compact drag handle, an optional 16 sp
 * title and a column of rows that ends above the navigation bar.
 *
 * The sheet does **not** dismiss itself when a row is tapped — the caller decides, which is what
 * lets a row animate the sheet away (`sheetState.hide()`) before applying its change.
 *
 * @param sheetState pass a hoisted state to be able to animate the sheet out before dismissing.
 * @param snackbarHost drawn over the bottom of the content, for sheets that talk back (the
 *   Watchlist Manager's "Watchlist deleted / UNDO").
 * @param immersive hide the system bars of the sheet's own window, so a sheet opened over the
 *   fullscreen chart does not bring the status/navigation bars back while it is up.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TGBottomSheet(
    onDismiss: () -> Unit,
    title: String?,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(),
    snackbarHost: (@Composable () -> Unit)? = null,
    immersive: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
        sheetState = sheetState,
        containerColor = TG.NavSurface,
        contentColor = TG.TextPrimary,
        dragHandle = { TGSheetHandle() },
        contentWindowInsets = { WindowInsets.navigationBars },
    ) {
        if (immersive) ImmersiveSheetWindow()
        Box(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth()) {
                if (title != null) {
                    Text(
                        text = title,
                        style = TGType.sheetTitle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = LabelInset, end = LabelInset, bottom = 16.dp),
                    )
                }
                content()
                Spacer(Modifier.height(8.dp))
            }
            if (snackbarHost != null) {
                Box(Modifier.align(Alignment.BottomCenter)) { snackbarHost() }
            }
        }
    }
}

/**
 * A modal sheet lives in its own window, which shows the system bars again even when the
 * activity window hides them (fullscreen chart). Hide them on the sheet window too, with the
 * same swipe-to-reveal behaviour the chart uses.
 */
@Composable
private fun ImmersiveSheetWindow() {
    val view = LocalView.current
    LaunchedEffect(view) {
        val window = (view.parent as? DialogWindowProvider)?.window ?: return@LaunchedEffect
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, view).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }
}

/**
 * The drag handle sits 8 dp under the sheet edge; Material's `BottomSheetDefaults.DragHandle`
 * pads itself to 48 dp, which would push the whole sheet 32 dp down against the design.
 */
@Composable
private fun TGSheetHandle() {
    val description = stringResource(R.string.cd_sheet_handle)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(HandleRowHeight)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(width = HandleBarWidth, height = HandleBarHeight)
                .clip(CircleShape)
                .background(TG.TextSecondary),
        )
    }
}

/**
 * One row of a [TGBottomSheet]: optional 24 dp glyph, label, optional trailing text and the
 * white check that marks the current value.
 *
 * @param trailingText small secondary text before the check mark (the `45/100` item count).
 * @param supportingText a second, smaller line under the label — what picking this option costs,
 *   for sheets whose choices differ in consequence rather than in value. It grows the row to
 *   56 dp; without it the row stays at the standard 44 dp.
 * @param enabled `false` greys the row out and swallows the click — a move target that is full.
 */
@Composable
fun TGSheetOption(
    label: String,
    checked: Boolean,
    onClick: () -> Unit,
    leadingIcon: ImageVector? = null,
    trailingText: String? = null,
    supportingText: String? = null,
    enabled: Boolean = true,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(if (supportingText != null) OptionHeightWithSupport else OptionHeight)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = LabelInset),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leadingIcon != null) {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                tint = when {
                    !enabled -> TG.TextTertiary
                    checked -> TG.TextPrimary
                    else -> TG.TextSecondary
                },
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.width(LabelInsetWithIcon - LabelInset - 24.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = label,
                style = TGType.sheetItem,
                color = if (enabled) TGType.sheetItem.color else TG.TextTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (supportingText != null) {
                Text(
                    text = supportingText,
                    style = TGType.sheetItemSupport,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (trailingText != null) {
            Text(text = trailingText, style = TGType.listSubtitle, maxLines = 1)
            Spacer(Modifier.width(12.dp))
        }
        if (checked) {
            Icon(
                imageVector = Icons.Outlined.Check,
                contentDescription = null,
                tint = TG.TextPrimary,
                modifier = Modifier.size(20.dp),
            )
        } else {
            Spacer(Modifier.width(20.dp))
        }
    }
}

/** An empty-state line inside a sheet ("No other watchlists"), aligned with the option labels. */
@Composable
fun TGSheetEmpty(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(OptionHeight)
            .padding(horizontal = LabelInset),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(text = text, style = TGType.body, maxLines = 1)
    }
}
