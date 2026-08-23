package com.neatcode.tabgreater.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.widget.Toast
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neatcode.tabgreater.R
import com.neatcode.tabgreater.ui.theme.TG
import com.neatcode.tabgreater.ui.theme.TGType

/**
 * The donation prompt: a Ko-fi link and a Monero address, nothing else. Donations unlock no
 * feature, so the dialog is purely informational and is never shown on its own — only from the
 * Settings row ([Donate.ENABLED]).
 *
 * The address is a tap target rather than selectable text: 95 characters are impossible to select
 * by hand on a phone, and a wrong character costs the sender the payment.
 */
@Composable
fun DonateDialog(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        containerColor = TG.NavSurface,
        titleContentColor = TG.TextPrimary,
        textContentColor = TG.TextPrimary,
        title = { Text(text = stringResource(R.string.donate_dialog_title), style = TGType.sheetTitle) },
        text = {
            Column {
                Text(text = stringResource(R.string.donate_dialog_message), style = TGType.sheetItem)
                Spacer(Modifier.height(MESSAGE_GAP))
                MoneroAddress(onClick = { context.copyMoneroAddress() })
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    context.openUrl(Donate.KOFI_URL)
                    onDismiss()
                },
            ) {
                Text(text = stringResource(R.string.donate_kofi), style = TGType.button)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.action_close), style = TGType.button)
            }
        },
    )
}

/** The label and the address as one clickable block, read as a single node by TalkBack. */
@Composable
private fun MoneroAddress(onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(BLOCK_CORNER))
            .clickable(onClick = onClick)
            .padding(BLOCK_PADDING),
        verticalArrangement = Arrangement.spacedBy(BLOCK_GAP),
    ) {
        Text(text = stringResource(R.string.donate_monero), style = TGType.button)
        Text(text = Donate.MONERO_ADDRESS, style = MoneroStyle)
    }
}

/**
 * Copies the address and confirms it. Android 13 raised its own clipboard confirmation over the
 * bottom of the screen, so an app toast on top of it would just say the same thing twice.
 */
private fun Context.copyMoneroAddress() {
    val clipboard = getSystemService(ClipboardManager::class.java) ?: return
    clipboard.setPrimaryClip(
        ClipData.newPlainText(getString(R.string.donate_clip_label), Donate.MONERO_ADDRESS),
    )
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        Toast.makeText(this, getString(R.string.donate_copied), Toast.LENGTH_SHORT).show()
    }
}

/**
 * Monospace so the address can be compared character by character against the wallet that
 * receives it; everything but the family stays on the token scale.
 */
private val MoneroStyle = TGType.listSubtitle.copy(
    fontFamily = FontFamily.Monospace,
    fontSize = 11.sp,
    lineHeight = 15.sp,
    color = TG.TextPrimary,
)

private val MESSAGE_GAP = 16.dp
private val BLOCK_CORNER = 8.dp
private val BLOCK_PADDING = 8.dp
private val BLOCK_GAP = 6.dp
