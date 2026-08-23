package com.neatcode.tabgreater.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldDecorator
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neatcode.tabgreater.R
import com.neatcode.tabgreater.core.model.Market
import com.neatcode.tabgreater.ui.components.ExchangeGlyph
import com.neatcode.tabgreater.ui.components.TGIconButton
import com.neatcode.tabgreater.ui.components.TGPillButton
import com.neatcode.tabgreater.ui.components.TGTopBar
import com.neatcode.tabgreater.ui.theme.TG
import com.neatcode.tabgreater.ui.theme.TGType
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * "+ Ticker": multi-select market search across every supported exchange. Instrument lists are
 * refreshed on entry (thin accent progress bar while that runs) and the picked markets are appended
 * to the watchlist by the "Add N" pill.
 */
@Composable
fun TickerSearchScreen(
    watchlistId: Long,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TickerSearchViewModel = koinViewModel { parametersOf(watchlistId) },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val textFieldState = rememberTextFieldState()
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(focusRequester) { focusRequester.requestFocus() }
    LaunchedEffect(textFieldState) {
        snapshotFlow { textFieldState.text.toString() }.collect(viewModel::onQueryChange)
    }
    LaunchedEffect(state.finished) {
        if (state.finished) {
            keyboard?.hide()
            onBack()
        }
    }

    Column(modifier.fillMaxSize().background(TG.Background)) {
        TGTopBar {
            TGIconButton(
                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = stringResource(R.string.cd_back),
                onClick = onBack,
            )
            Spacer(Modifier.width(16.dp))
            SearchField(
                state = textFieldState,
                modifier = Modifier.weight(1f).focusRequester(focusRequester),
            )
        }

        if (state.loading) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().height(2.dp),
                color = TG.Accent,
                trackColor = Color.Transparent,
                gapSize = 0.dp,
            )
        } else {
            Spacer(Modifier.height(2.dp))
        }

        // Quick-add chips only make sense as a starting point; once the user types they are noise.
        if (state.query.isEmpty()) {
            PopularPairsRow(
                pairs = state.popularPairs,
                onPairClick = { pair -> textFieldState.setTextAndPlaceCursorAtEnd(pair) },
            )
        }

        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .imePadding()
                .navigationBarsPadding(),
        ) {
            if (state.results.isEmpty()) {
                Text(
                    text = stringResource(
                        if (state.query.isBlank()) R.string.search_hint else R.string.search_no_match,
                    ),
                    style = TGType.body,
                    modifier = Modifier.align(Alignment.Center),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 72.dp),
                ) {
                    items(state.results, key = { it.key.value }) { market ->
                        MarketRow(
                            market = market,
                            selected = market.key in state.selected,
                            onClick = { viewModel.toggle(market.key) },
                        )
                    }
                }
            }

            if (state.selected.isNotEmpty()) {
                TGPillButton(
                    label = stringResource(R.string.search_add, state.selected.size),
                    contentDescription = stringResource(R.string.cd_add_selected),
                    onClick = viewModel::addSelected,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 16.dp, bottom = 16.dp),
                )
            }
        }
    }
}

@Composable
private fun SearchField(
    state: androidx.compose.foundation.text.input.TextFieldState,
    modifier: Modifier = Modifier,
) {
    val placeholder = stringResource(R.string.search_placeholder)
    BasicTextField(
        state = state,
        modifier = modifier,
        textStyle = TGType.searchInput,
        lineLimits = TextFieldLineLimits.SingleLine,
        cursorBrush = SolidColor(TG.Accent),
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Characters,
            imeAction = ImeAction.Search,
        ),
        decorator = TextFieldDecorator { innerTextField ->
            Box(contentAlignment = Alignment.CenterStart) {
                if (state.text.isEmpty()) {
                    Text(text = placeholder, style = TGType.searchInput, color = TG.TextSecondary)
                }
                innerTextField()
            }
        },
    )
}

@Composable
private fun MarketRow(
    market: Market,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ExchangeGlyph(market.exchange, size = 16.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = market.exchange.displayName.uppercase(),
                style = TGType.exchange,
                maxLines = 1,
            )
            Text(
                text = market.key.pair,
                style = TGType.pair,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (selected) {
            Icon(
                imageVector = Icons.Outlined.Check,
                contentDescription = stringResource(R.string.cd_selected),
                tint = TG.Accent,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
