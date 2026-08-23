package com.neatcode.tabgreater.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.neatcode.tabgreater.R
import com.neatcode.tabgreater.ui.theme.TG
import com.neatcode.tabgreater.ui.theme.TGType

/**
 * The four One UI settings that decide whether a background service survives on a Samsung
 *. None of them can be deep-linked, so the dialog spells out the paths and
 * only offers the app's own details screen, which is the entry point for the first of them.
 */
@Composable
fun SamsungChecklistDialog(
    onOpenAppSettings: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        containerColor = TG.NavSurface,
        titleContentColor = TG.TextPrimary,
        textContentColor = TG.TextPrimary,
        title = { Text(text = stringResource(R.string.samsung_dialog_title), style = TGType.sheetTitle) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(STEP_GAP)) {
                Text(text = stringResource(R.string.samsung_step_battery), style = TGType.sheetItem)
                Text(text = stringResource(R.string.samsung_step_never_sleeping), style = TGType.sheetItem)
                Text(text = stringResource(R.string.samsung_step_unused), style = TGType.sheetItem)
                Text(text = stringResource(R.string.samsung_step_auto_blocker), style = TGType.sheetItem)
            }
        },
        confirmButton = {
            TextButton(onClick = onOpenAppSettings) {
                Text(text = stringResource(R.string.samsung_open_settings), style = TGType.button)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.action_close), style = TGType.button)
            }
        },
    )
}

private val STEP_GAP = 12.dp
