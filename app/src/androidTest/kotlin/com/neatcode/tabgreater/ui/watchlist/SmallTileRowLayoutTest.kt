package com.neatcode.tabgreater.ui.watchlist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.geometry.Offset
import com.neatcode.tabgreater.core.model.MarketKey
import com.neatcode.tabgreater.core.model.TGDimens
import com.neatcode.tabgreater.ui.theme.TabGreaterTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SmallTileRowLayoutTest {
    @get:Rule val compose = createComposeRule()

    @Test fun pairedSmallCardsStayAlignedAtLargeFontScalesAndOddTailKeepsItsColumn() {
        val short = previewTile().copy(itemId = 11)
        val long = previewTile(up = false).copy(
            itemId = 12,
            key = MarketKey("kraken:BTC/EUR"),
            exchangeLabel = "KRAKEN",
            pair = "BTC/USD ≥ 63,330\nEnds 20 Nov 2026 06:00 UTC",
            twoLineLabel = true,
            isEventOutcome = true,
            marketContext = "USDC",
        )
        val odd = previewTile(up = false).copy(
            itemId = 13,
            key = MarketKey("binance:XRP/USDT"),
            exchangeLabel = "BINANCE",
            pair = "XRP/USDT",
            priceText = "12.34",
            changeText = "+0.88%",
        )
        val tiles = listOf(short, long, odd)
        val fontScale = mutableStateOf(1f)

        compose.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, fontScale.value)) {
                TabGreaterTheme {
                    SmallTileGrid(
                        tiles = tiles,
                        selectedIds = emptySet(),
                        isSelecting = false,
                        onMove = { _, _ -> },
                        onDragEnd = {},
                        onLongPress = {},
                        onOpenChart = {},
                        onToggleSelection = {},
                    )
                }
            }
        }

        var originalHeight = 0f
        for (scale in listOf(1f, 1.5f, 2f)) {
            compose.runOnIdle { fontScale.value = scale }
            compose.waitForIdle()

            val first = compose.onNodeWithText("ETH/USDT").fetchSemanticsNode().boundsInRoot
            val second = compose.onNodeWithText("BTC/USD ≥ 63,330").fetchSemanticsNode().boundsInRoot
            val third = compose.onNodeWithText("XRP/USDT").fetchSemanticsNode().boundsInRoot
            val firstPrice = compose.onNodeWithText(short.priceText!!).fetchSemanticsNode().boundsInRoot
            val secondPrice = compose.onNodeWithText(long.priceText!!).fetchSemanticsNode().boundsInRoot
            val firstChange = compose.onNodeWithText(short.changeText!!).fetchSemanticsNode().boundsInRoot
            val secondChange = compose.onNodeWithText(long.changeText!!).fetchSemanticsNode().boundsInRoot

            assertEquals("Small row card heights at fontScale=$scale", first.height, second.height, 1f)
            assertEquals("Bottom prices align at fontScale=$scale", firstPrice.bottom, secondPrice.bottom, 1f)
            assertEquals("Bottom changes align at fontScale=$scale", firstChange.bottom, secondChange.bottom, 1f)
            assertEquals("Small row card widths at fontScale=$scale", first.width, third.width, 1f)
            assertEquals("Odd final card stays in the first column at fontScale=$scale", first.left, third.left, 1f)
            assertTrue("The odd final card follows the first pair at fontScale=$scale", third.top > first.top)
            assertTrue("The first card stays before the second card at fontScale=$scale", first.left < second.left)
            if (scale == 1f) originalHeight = first.height
            if (scale == 2f) assertTrue("Large font scale can grow the row", first.height >= originalHeight)
        }
    }

    @Test fun oddPixelColumnRemainderUsesTheNarrowerWidthForBothIntrinsicHeights() {
        val short = previewTile().copy(itemId = 14)
        val long = previewTile(up = false).copy(
            itemId = 15,
            pair = "BTC/USD ≥ 63,330\nEnds 20 Nov 2026 06:00 UTC",
            twoLineLabel = true,
            isEventOutcome = true,
            marketContext = "USDC",
        )
        compose.setContent {
            TabGreaterTheme {
                SmallTileGrid(
                    tiles = listOf(short, long),
                    selectedIds = emptySet(),
                    isSelecting = false,
                    fixedWidthPx = 361,
                    onMove = { _, _ -> },
                    onDragEnd = {},
                    onLongPress = {},
                    onOpenChart = {},
                    onToggleSelection = {},
                )
            }
        }

        val first = compose.onNodeWithText("ETH/USDT").fetchSemanticsNode().boundsInRoot
        val second = compose.onNodeWithText("BTC/USD ≥ 63,330").fetchSemanticsNode().boundsInRoot
        assertEquals(
            "Pair heights match at one-pixel remainder: first=$first second=$second",
            first.height,
            second.height,
            1f,
        )
        assertEquals("Odd pixel remainder gives the logical first column one px", 1f, first.width - second.width, 1f)
    }

    @Test fun measurementPeerDoesNotDuplicateSemanticsAndLongPressSelectsOnlyItsCard() {
        val tiles = mutableStateOf(listOf(previewTile().copy(itemId = 21), previewTile(up = false).copy(itemId = 22)))
        val selectedIds = mutableStateOf(emptySet<Long>())
        val chartOpens = mutableStateOf(0)
        compose.setContent {
            TabGreaterTheme {
                SmallTileGrid(
                    tiles = tiles.value,
                    selectedIds = selectedIds.value,
                    isSelecting = selectedIds.value.isNotEmpty(),
                    onMove = { _, _ -> },
                    onDragEnd = {},
                    onLongPress = { id -> selectedIds.value = selectedIds.value + id },
                    onOpenChart = { chartOpens.value++ },
                    onToggleSelection = { id -> selectedIds.value = selectedIds.value + id },
                )
            }
        }

        compose.onAllNodesWithText("BTC/EUR").assertCountEquals(1)
        compose.onNodeWithText("BTC/EUR").performTouchInput { longClick() }
        compose.runOnIdle {
            assertEquals(setOf(22L), selectedIds.value)
            assertEquals(0, chartOpens.value)
        }
    }

    @Test fun dragMovesOneCardAcrossMultipleRowsAndCommitsTheFlattenedOrder() {
        val tiles = mutableStateOf((31L..36L).mapIndexed { index, id ->
            previewTile(up = index % 2 == 0).copy(itemId = id, pair = "${'A' + index}/USDT")
        })
        val commits = mutableStateOf(0)
        val chartOpens = mutableStateOf(0)
        val moves = mutableStateOf(emptyList<Pair<Int, Int>>())
        val longPresses = mutableStateOf(0)
        compose.setContent {
            TabGreaterTheme {
                SmallTileGrid(
                    tiles = tiles.value,
                    selectedIds = emptySet(),
                    isSelecting = false,
                    onMove = { from, to ->
                        moves.value = moves.value + (from to to)
                        tiles.value = tiles.value.toMutableList().apply { add(to, removeAt(from)) }
                    },
                    onDragEnd = { commits.value++ },
                    onLongPress = { longPresses.value++ },
                    onOpenChart = { chartOpens.value++ },
                    onToggleSelection = {},
                )
            }
        }

        val source = compose.onNodeWithText("A/USDT").fetchSemanticsNode().boundsInRoot.center
        val firstTarget = compose.onNodeWithText("C/USDT").fetchSemanticsNode().boundsInRoot.center
        val finalTarget = compose.onNodeWithText("E/USDT").fetchSemanticsNode().boundsInRoot.center
        val grid = compose.onNodeWithTag("smallTileGrid")
        val gridBounds = grid.fetchSemanticsNode().boundsInRoot
        val sourceInGrid = Offset(source.x - gridBounds.left, source.y - gridBounds.top)
        val firstTargetInGrid = Offset(firstTarget.x - gridBounds.left, firstTarget.y - gridBounds.top)
        val finalTargetInGrid = Offset(finalTarget.x - gridBounds.left, finalTarget.y - gridBounds.top)

        grid.performTouchInput {
            down(sourceInGrid)
            advanceEventTime(600)
            fun moveGradually(from: Offset, to: Offset) {
                for (step in 1..8) {
                    val fraction = step / 8f
                    val point = Offset(
                        from.x + (to.x - from.x) * fraction,
                        from.y + (to.y - from.y) * fraction,
                    )
                    moveTo(point, delayMillis = 16)
                }
            }
            moveGradually(sourceInGrid, firstTargetInGrid)
            moveGradually(firstTargetInGrid, finalTargetInGrid)
            up()
        }

        compose.runOnIdle {
            assertTrue(
                "The tile must visit multiple row targets; moves=${moves.value} longPresses=${longPresses.value} " +
                    "order=${tiles.value.map { it.itemId }} commits=${commits.value}",
                moves.value.size >= 2,
            )
            assertEquals(32L, tiles.value[0].itemId)
            assertEquals(33L, tiles.value[1].itemId)
            assertEquals(34L, tiles.value[2].itemId)
            assertEquals(35L, tiles.value[3].itemId)
            assertEquals(31L, tiles.value[4].itemId)
            assertEquals(1, commits.value)
            assertEquals(0, chartOpens.value)
            assertEquals(0, longPresses.value)
        }
    }

    @Composable
    private fun SmallTileGrid(
        tiles: List<TileUiState>,
        selectedIds: Set<Long>,
        isSelecting: Boolean,
        fixedWidthPx: Int? = null,
        onMove: (Int, Int) -> Unit,
        onDragEnd: () -> Unit,
        onLongPress: (Long) -> Unit,
        onOpenChart: (MarketKey) -> Unit,
        onToggleSelection: (Long) -> Unit,
    ) {
        val density = LocalDensity.current
        val gridState = rememberLazyGridState()
        val reorder = rememberReorderState(
            gridState = gridState,
            onMove = onMove,
            onDragEnd = onDragEnd,
            onLongPress = onLongPress,
        )
        BoxWithConstraints(
            modifier = if (fixedWidthPx == null) {
                Modifier.fillMaxSize()
            } else {
                Modifier.width(with(density) { fixedWidthPx.toDp() }).fillMaxSize()
            },
        ) {
            val widthPx = with(density) { maxWidth.roundToPx() }
            val gapPx = with(density) { TGDimens.GRID_GAP_DP.dp.roundToPx() }
            val firstColumnIsOnePixelWider = (widthPx - gapPx).coerceAtLeast(0) % 2 != 0

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.testTag("smallTileGrid").fillMaxSize(),
                state = gridState,
                horizontalArrangement = Arrangement.spacedBy(TGDimens.GRID_GAP_DP.dp),
                verticalArrangement = Arrangement.spacedBy(TGDimens.GRID_GAP_DP.dp),
            ) {
                itemsIndexed(tiles, key = { _, tile -> tile.itemId }) { index, tile ->
                    val dragging = tile.itemId == reorder.draggingItemId
                    val itemModifier = Modifier
                        .fillMaxWidth()
                        .then(if (dragging) Modifier else Modifier.animateItem())
                    val peer = tiles.getOrNull(if (index % 2 == 0) index + 1 else index - 1)
                    if (peer != null) {
                        SmallTileCell(
                            tile = tile,
                            peer = peer,
                            selectedIds = selectedIds,
                            isSelecting = isSelecting,
                            shrinkZeros = false,
                            reorder = reorder,
                            peerWidthAdjustmentPx = if (firstColumnIsOnePixelWider) {
                                if (index % 2 == 0) -1 else 1
                            } else {
                                0
                            },
                            modifier = itemModifier,
                            onOpenChart = onOpenChart,
                            onToggleSelection = onToggleSelection,
                        )
                    } else {
                        TickerTile(
                            tile = tile,
                            size = com.neatcode.tabgreater.core.model.TileSize.SMALL,
                            modifier = itemModifier
                                .reorderable(reorder, tile.itemId, enabled = !isSelecting)
                                .clickable {
                                    when {
                                        reorder.consumeLongPress() -> Unit
                                        isSelecting -> onToggleSelection(tile.itemId)
                                        else -> onOpenChart(tile.key)
                                    }
                                },
                            selected = tile.itemId in selectedIds,
                            shrinkZeros = false,
                        )
                    }
                }
            }
        }
    }
}
