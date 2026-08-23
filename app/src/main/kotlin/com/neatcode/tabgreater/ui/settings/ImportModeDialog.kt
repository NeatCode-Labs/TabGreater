package com.neatcode.tabgreater.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.neatcode.tabgreater.R
import com.neatcode.tabgreater.ui.theme.TG
import com.neatcode.tabgreater.ui.theme.TGType

/**
 * Asks how a decoded backup should be applied. Each mode is a full-width choice with its
 * explanation directly underneath it — "Replace all" throws the current lists away and cannot be
 * undone, so the sentence that says so has to belong to the button that does it (a `clickable`
 * column also merges both lines into one accessibility node, read as label + hint).
 */
@Composable
fun ImportModeDialog(
    pending: PendingImport,
    onReplace: () -> Unit,
    onMerge: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        containerColor = TG.NavSurface,
        titleContentColor = TG.TextPrimary,
        textContentColor = TG.TextPrimary,
        title = { Text(text = stringResource(R.string.import_dialog_title), style = TGType.sheetTitle) },
        text = {
            Column {
                Text(
                    text = pluralStringResource(
                        R.plurals.import_dialog_message,
                        pending.watchlists,
                        pending.watchlists,
                        pending.items,
                    ),
                    style = TGType.sheetItem,
                )
                Spacer(Modifier.height(16.dp))
                ImportModeChoice(
                    label = stringResource(R.string.import_merge),
                    hint = stringResource(R.string.import_merge_hint),
                    onClick = onMerge,
                )
                Spacer(Modifier.height(8.dp))
                ImportModeChoice(
                    label = stringResource(R.string.import_replace),
                    hint = stringResource(R.string.import_replace_hint),
                    onClick = onReplace,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.action_cancel), style = TGType.button)
            }
        },
    )
}

/** One import mode: the action on top, the sentence that explains it directly underneath. */
@Composable
private fun ImportModeChoice(
    label: String,
    hint: String,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CHOICE_CORNER))
            .clickable(onClick = onClick)
            .padding(vertical = CHOICE_PADDING),
        verticalArrangement = Arrangement.spacedBy(CHOICE_GAP),
    ) {
        Text(text = label, style = TGType.button)
        Text(text = hint, style = TGType.listSubtitle)
    }
}

private val CHOICE_CORNER = 8.dp
private val CHOICE_PADDING = 8.dp
private val CHOICE_GAP = 3.dp
