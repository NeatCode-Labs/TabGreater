package com.neatcode.tabgreater.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.neatcode.tabgreater.R
import com.neatcode.tabgreater.core.model.ExchangeId
import com.neatcode.tabgreater.core.model.InstrumentKind
import com.neatcode.tabgreater.ui.theme.TG
import com.neatcode.tabgreater.ui.theme.TGType

private val FilterShape = RoundedCornerShape(percent = 50)

/** The only search filters: instrument kind and exchange source. */
@Composable
fun SearchMarketFilterRow(
    kind: InstrumentKind?,
    source: ExchangeId?,
    onKindSelected: (InstrumentKind?) -> Unit,
    onSourceSelected: (ExchangeId?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .horizontalScroll(rememberScrollState())
            .padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val kindLabel = stringResource(kindResource(kind))
        FilterMenu(
            label = kindLabel,
            contentDescription = stringResource(R.string.search_filter_kind, kindLabel),
            options = listOf(null to stringResource(R.string.search_kind_all)) +
                listOf(
                    InstrumentKind.SPOT to stringResource(R.string.search_kind_spot),
                    InstrumentKind.PERPETUAL to stringResource(R.string.search_kind_perpetual),
                    InstrumentKind.OUTCOME to stringResource(R.string.search_kind_outcome),
                ),
            selected = kind,
            onSelected = onKindSelected,
        )
        Spacer(Modifier.width(8.dp))
        val sourceLabel = source?.displayName ?: stringResource(R.string.search_source_all)
        FilterMenu(
            label = sourceLabel,
            contentDescription = stringResource(R.string.search_filter_source, sourceLabel),
            options = listOf(null to stringResource(R.string.search_source_all)) +
                ExchangeId.entries.map { it to it.displayName },
            selected = source,
            onSelected = onSourceSelected,
        )
    }
}

@Composable
private fun <T> FilterMenu(
    label: String,
    contentDescription: String,
    options: List<Pair<T?, String>>,
    selected: T?,
    onSelected: (T?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val stateDescription = stringResource(R.string.search_filter_state, label)
    Box {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(FilterShape)
                .background(TG.ChipFill)
                .border(1.dp, if (selected == null) TG.Outline else TG.Accent, FilterShape)
                .clickable(
                    role = Role.Button,
                    onClickLabel = stringResource(R.string.search_filter_action),
                ) { expanded = true }
                .semantics(mergeDescendants = true) {
                    this.contentDescription = contentDescription
                    this.stateDescription = stateDescription
                }
                .padding(start = 12.dp, end = 8.dp, top = 7.dp, bottom = 7.dp),
        ) {
            Text(text = label, style = TGType.chip, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Icon(
                imageVector = Icons.Filled.ArrowDropDown,
                contentDescription = null,
                tint = TG.TextSecondary,
                modifier = Modifier.size(20.dp),
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (value, optionLabel) ->
                DropdownMenuItem(
                    text = { Text(optionLabel) },
                    onClick = {
                        onSelected(value)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun kindResource(kind: InstrumentKind?): Int = when (kind) {
    null -> R.string.search_kind_all
    InstrumentKind.SPOT -> R.string.search_kind_spot
    InstrumentKind.PERPETUAL -> R.string.search_kind_perpetual
    InstrumentKind.OUTCOME -> R.string.search_kind_outcome
}
