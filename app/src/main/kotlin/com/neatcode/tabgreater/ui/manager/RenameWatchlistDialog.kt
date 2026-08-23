package com.neatcode.tabgreater.ui.manager

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldDecorator
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.neatcode.tabgreater.R
import com.neatcode.tabgreater.ui.theme.TG
import com.neatcode.tabgreater.ui.theme.TGType

/** "Rename" from the Watchlist Manager row menu: the name prefilled, OK / Cancel. */
@Composable
fun RenameWatchlistDialog(
    initialName: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val textFieldState = rememberTextFieldState(initialName)
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(focusRequester) { focusRequester.requestFocus() }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        containerColor = TG.NavSurface,
        titleContentColor = TG.TextPrimary,
        textContentColor = TG.TextPrimary,
        title = { Text(text = stringResource(R.string.manager_rename_title), style = TGType.sheetTitle) },
        text = {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(FIELD_HEIGHT)
                    .clip(RoundedCornerShape(FIELD_CORNER))
                    .background(TG.ChipFill)
                    .border(1.dp, TG.Outline, RoundedCornerShape(FIELD_CORNER))
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
                    onKeyboardAction = { onConfirm(textFieldState.text.toString()) },
                    decorator = TextFieldDecorator { innerTextField -> innerTextField() },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(textFieldState.text.toString()) }) {
                Text(text = stringResource(R.string.action_ok), style = TGType.button)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.action_cancel), style = TGType.button)
            }
        },
    )
}

private val FIELD_HEIGHT = 44.dp
private val FIELD_CORNER = 8.dp
