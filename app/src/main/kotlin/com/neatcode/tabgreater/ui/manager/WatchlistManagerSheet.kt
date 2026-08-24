package com.neatcode.tabgreater.ui.manager

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldDecorator
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neatcode.tabgreater.R
import com.neatcode.tabgreater.core.model.Limits
import com.neatcode.tabgreater.ui.components.TGBottomSheet
import com.neatcode.tabgreater.ui.icons.TGIcons
import com.neatcode.tabgreater.ui.theme.TG
import com.neatcode.tabgreater.ui.theme.TGType
import com.neatcode.tabgreater.ui.watchlist.rememberSheetDismiss
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

/**
 * The Watchlist Manager: the list of watchlists with their per-list settings, a field that
 * appends a new one, drag-reorder by the handle, swipe-to-delete with a 5 s undo and a ⋮ menu
 * with Rename / Copy / Delete.
 *
 * Tapping a row switches the watchlist tab and closes the sheet; creating a list keeps it open.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WatchlistManagerSheet(
    onDismiss: () -> Unit,
    onSelectWatchlist: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: WatchlistManagerViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val snackbarHostState = remember { SnackbarHostState() }
    val dismiss = rememberSheetDismiss(sheetState)

    val listState = rememberLazyListState()
    val rows = remember { mutableStateListOf<WatchlistRow>() }
    val reorderState = rememberReorderState(
        listState = listState,
        onMove = { from, to -> rows.add(to, rows.removeAt(from)) },
        onDrop = { viewModel.reorder(rows.map { it.id }) },
    )

    // While a drag is running the rows belong to the gesture, not to the database.
    LaunchedEffect(state.watchlists) {
        if (!reorderState.isDragging) {
            rows.clear()
            rows.addAll(state.watchlists)
        }
    }
    LaunchedEffect(reorderState.isDragging) {
        while (reorderState.isDragging) {
            val delta = reorderState.autoScrollDelta()
            if (delta != 0f) reorderState.onAutoScrolled(listState.scrollBy(delta))
            withFrameNanos { }
        }
    }

    ManagerSnackbarEffect(state.message, snackbarHostState, viewModel)

    // The undo window lives in the view model, but nothing can show it once the sheet is gone:
    // drop the offer and the message together with the composition.
    DisposableEffect(Unit) {
        onDispose { viewModel.onSheetDismissed() }
    }

    var renameTarget by remember { mutableStateOf<WatchlistRow?>(null) }

    TGBottomSheet(
        onDismiss = {
            viewModel.onSheetDismissed()
            onDismiss()
        },
        title = null,
        modifier = modifier,
        sheetState = sheetState,
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = TG.Surface,
                    contentColor = TG.TextPrimary,
                    actionColor = TG.Accent,
                )
            }
        },
    ) {
        ManagerHeader(count = state.count)
        Spacer(Modifier.height(HEADER_TO_FIELD))
        NewWatchlistField(enabled = state.canAdd, onSubmit = viewModel::create)
        Spacer(Modifier.height(FIELD_TO_LIST))
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
            contentPadding = PaddingValues(bottom = LIST_BOTTOM_PADDING),
        ) {
            items(rows, key = { it.id }) { row ->
                WatchlistManagerRow(
                    row = row,
                    reorderState = reorderState,
                    onClick = {
                        dismiss {
                            onSelectWatchlist(row.id)
                            viewModel.onSheetDismissed()
                            onDismiss()
                        }
                    },
                    onRename = { renameTarget = row },
                    onCopy = { viewModel.copy(row.id) },
                    onDelete = { viewModel.delete(row.id) },
                )
            }
        }
    }

    renameTarget?.let { target ->
        RenameWatchlistDialog(
            initialName = target.name,
            onConfirm = { name ->
                viewModel.rename(target.id, name)
                renameTarget = null
            },
            onDismiss = { renameTarget = null },
        )
    }
}

/**
 * Shows one snackbar per [ManagerMessage] — and only shows it: the 5 s undo window belongs to
 * [WatchlistManagerViewModel]. When that window closes the view model clears the message, this
 * effect is cancelled with it and the indefinite snackbar leaves the host.
 */
@Composable
private fun ManagerSnackbarEffect(
    message: ManagerMessage?,
    snackbarHostState: SnackbarHostState,
    viewModel: WatchlistManagerViewModel,
) {
    val deletedText = stringResource(R.string.manager_deleted)
    val limitText = pluralStringResource(
        R.plurals.manager_limit_reached,
        Limits.MAX_WATCHLISTS,
        Limits.MAX_WATCHLISTS,
    )
    val keepOneText = stringResource(R.string.manager_keep_one)
    val restoreFailedText = stringResource(R.string.manager_restore_failed)
    val undoLabel = stringResource(R.string.action_undo)

    LaunchedEffect(message) {
        val pending = message ?: return@LaunchedEffect
        when (pending.kind) {
            ManagerMessageKind.DELETED -> {
                val result = snackbarHostState.showSnackbar(
                    message = deletedText,
                    actionLabel = undoLabel,
                    withDismissAction = false,
                    duration = SnackbarDuration.Indefinite,
                )
                if (result == SnackbarResult.ActionPerformed) viewModel.undoDelete()
                // The view model owns this message: it expires it when the undo window closes.
                return@LaunchedEffect
            }

            ManagerMessageKind.LIMIT_REACHED ->
                snackbarHostState.showSnackbar(limitText, duration = SnackbarDuration.Short)

            ManagerMessageKind.KEEP_AT_LEAST_ONE ->
                snackbarHostState.showSnackbar(keepOneText, duration = SnackbarDuration.Short)

            ManagerMessageKind.RESTORE_FAILED ->
                snackbarHostState.showSnackbar(restoreFailedText, duration = SnackbarDuration.Short)
        }
        viewModel.consumeMessage(pending.id)
    }
}

/** `Watchlists` on the left, `5/20` on the right. */
@Composable
private fun ManagerHeader(count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(HEADER_HEIGHT)
            .padding(horizontal = SIDE_MARGIN),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.manager_title),
            style = TGType.sheetTitle,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = stringResource(R.string.manager_count, count, Limits.MAX_WATCHLISTS),
            style = TGType.sheetItem,
            color = TG.TextSecondary,
            maxLines = 1,
        )
    }
}

/**
 * The outlined "New watchlist" field with its round submit button. Submitting keeps the focus so
 * several lists can be typed in a row; the field is dead once the 25-list cap is reached.
 */
@Composable
private fun NewWatchlistField(
    enabled: Boolean,
    onSubmit: (String) -> Unit,
) {
    val textFieldState = rememberTextFieldState()
    val focusRequester = remember { FocusRequester() }
    val submit: () -> Unit = {
        val text = textFieldState.text.toString()
        if (text.isNotBlank()) {
            onSubmit(text)
            textFieldState.clearText()
        }
    }

    Column(Modifier.fillMaxWidth().padding(horizontal = SIDE_MARGIN)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(FIELD_HEIGHT)
                .clip(RoundedCornerShape(FIELD_CORNER))
                .background(TG.ChipFill)
                .border(1.dp, TG.Outline, RoundedCornerShape(FIELD_CORNER))
                .padding(start = FIELD_TEXT_INSET, end = FIELD_BUTTON_INSET),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NewWatchlistInput(
                state = textFieldState,
                enabled = enabled,
                onSubmit = submit,
                modifier = Modifier.weight(1f).focusRequester(focusRequester),
            )
            Spacer(Modifier.width(8.dp))
            SubmitButton(enabled = enabled, onClick = submit)
        }
        if (!enabled) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = pluralStringResource(
                    R.plurals.manager_limit_reached,
                    Limits.MAX_WATCHLISTS,
                    Limits.MAX_WATCHLISTS,
                ),
                style = TGType.listSubtitle,
            )
        }
    }
}

@Composable
private fun NewWatchlistInput(
    state: TextFieldState,
    enabled: Boolean,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val placeholder = stringResource(R.string.manager_new_placeholder)
    BasicTextField(
        state = state,
        modifier = modifier,
        enabled = enabled,
        textStyle = TGType.sheetItem,
        lineLimits = TextFieldLineLimits.SingleLine,
        cursorBrush = SolidColor(TG.Accent),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        onKeyboardAction = { onSubmit() },
        decorator = TextFieldDecorator { innerTextField ->
            Box(contentAlignment = Alignment.CenterStart) {
                if (state.text.isEmpty()) {
                    Text(text = placeholder, style = TGType.sheetItem, color = TG.TextSecondary)
                }
                innerTextField()
            }
        },
    )
}

@Composable
private fun SubmitButton(enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(SUBMIT_BUTTON)
            .clip(CircleShape)
            .background(if (enabled) TG.Accent else TG.Outline)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
            contentDescription = stringResource(R.string.cd_create_watchlist),
            tint = if (enabled) TG.Scrim else TG.TextSecondary,
            modifier = Modifier.size(20.dp),
        )
    }
}

/** One watchlist: drag handle · name + settings summary · separator · ⋮ menu, swipeable to delete. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WatchlistManagerRow(
    row: WatchlistRow,
    reorderState: ReorderState,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit,
) {
    // The row is never removed by the gesture itself: the delete goes through the view model so
    // "UNDO" can put it back, and a refused delete simply snaps the row into place again.
    val dismissState = rememberSwipeToDismissBoxState()
    val scope = rememberCoroutineScope()

    SwipeToDismissBox(
        state = dismissState,
        modifier = Modifier.reorderableItem(reorderState, row.id),
        backgroundContent = { DeleteBackground() },
        onDismiss = {
            onDelete()
            scope.launch { dismissState.reset() }
        },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(ROW_HEIGHT)
                .background(TG.NavSurface)
                .clickable(onClick = onClick)
                .padding(start = HANDLE_START_MARGIN, end = MENU_END_MARGIN),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(width = HANDLE_TOUCH_WIDTH, height = ROW_HEIGHT)
                    .reorderHandle(reorderState, row.id),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = TGIcons.DragHandle,
                    contentDescription = stringResource(R.string.cd_reorder_watchlist),
                    tint = TG.TextSecondary,
                    modifier = Modifier.size(24.dp),
                )
            }
            Spacer(Modifier.width(HANDLE_TO_TEXT))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(NAME_TO_SUBTITLE)) {
                Text(
                    text = row.name,
                    style = TGType.listTitle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(text = row.subtitle, style = TGType.listSubtitle, maxLines = 1)
            }
            Spacer(Modifier.width(SEPARATOR_GAP))
            Box(
                Modifier
                    .width(1.dp)
                    .height(SEPARATOR_HEIGHT)
                    .background(TG.Outline),
            )
            Spacer(Modifier.width(SEPARATOR_TO_MENU))
            RowMenuButton(onRename = onRename, onCopy = onCopy, onDelete = onDelete)
        }
    }
}

/** Red plate with a bin, revealed while the row is swiped in either direction. */
@Composable
private fun DeleteBackground() {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(TG.Down)
            .padding(horizontal = SIDE_MARGIN),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        val label = stringResource(R.string.cd_delete_watchlist)
        Icon(Icons.Outlined.Delete, contentDescription = label, tint = TG.TextPrimary, modifier = Modifier.size(20.dp))
        Icon(Icons.Outlined.Delete, contentDescription = null, tint = TG.TextPrimary, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun RowMenuButton(
    onRename: () -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .size(width = MENU_TOUCH_WIDTH, height = ROW_HEIGHT)
            .clickable { expanded = true },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Outlined.MoreVert,
            contentDescription = stringResource(R.string.cd_watchlist_actions),
            tint = TG.TextSecondary,
            modifier = Modifier.size(24.dp),
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = TG.NavSurface,
        ) {
            ManagerMenuItem(R.string.manager_rename, Icons.Outlined.Edit) {
                expanded = false
                onRename()
            }
            ManagerMenuItem(R.string.manager_copy, TGIcons.ContentCopy) {
                expanded = false
                onCopy()
            }
            ManagerMenuItem(R.string.manager_delete, Icons.Outlined.Delete) {
                expanded = false
                onDelete()
            }
        }
    }
}

@Composable
private fun ManagerMenuItem(
    @StringRes labelRes: Int,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        text = { Text(text = stringResource(labelRes), style = TGType.sheetItem) },
        onClick = onClick,
        leadingIcon = {
            Icon(icon, contentDescription = null, tint = TG.TextSecondary, modifier = Modifier.size(20.dp))
        },
    )
}

// Geometry of the manager sheet, in the 360 dp reference frame.
private val SIDE_MARGIN = 16.dp
private val HEADER_HEIGHT = 40.dp
private val HEADER_TO_FIELD = 7.5.dp
private val FIELD_HEIGHT = 42.dp
private val FIELD_CORNER = 8.dp
private val FIELD_TEXT_INSET = 16.dp
private val FIELD_BUTTON_INSET = 4.dp
private val SUBMIT_BUTTON = 34.dp
private val FIELD_TO_LIST = 6.dp
private val ROW_HEIGHT = 52.dp
private val HANDLE_START_MARGIN = 6.dp
private val HANDLE_TOUCH_WIDTH = 32.dp
private val HANDLE_TO_TEXT = 21.dp
private val NAME_TO_SUBTITLE = 3.dp
private val SEPARATOR_GAP = 10.dp
private val SEPARATOR_HEIGHT = 28.dp
private val SEPARATOR_TO_MENU = 5.dp
private val MENU_END_MARGIN = 5.dp
private val MENU_TOUCH_WIDTH = 34.dp
private val LIST_BOTTOM_PADDING = 8.dp
