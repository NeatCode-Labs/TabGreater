package com.neatcode.tabgreater.ui.watchlist

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.neatcode.tabgreater.core.model.TileSize
import com.neatcode.tabgreater.ui.theme.TabGreaterTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class MarketCardLayoutTest {
    @get:Rule val compose = createComposeRule()

    @Test fun titleDeadlineAndPercentageStayFullyVisibleWhenTheAbsoluteMoveCannotFit() {
        val title = "BTC/USD ≥ 63,330"
        val deadline = "Ends 20 Nov 2026 06:00 UTC"
        val percentage = "+234.56%"
        val tile = previewTile().copy(
            pair = "$title\n$deadline",
            twoLineLabel = true,
            isEventOutcome = true,
            marketContext = "USDC",
            changeText = percentage,
            absChangeText = "+1,234,567,890,123.0000000001 (234.56%)",
        )
        val size = mutableStateOf(TileSize.SMALL)
        val scale = mutableStateOf(1f)
        compose.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, fontScale = scale.value)) {
                TabGreaterTheme {
                    val half = size.value == TileSize.SMALL || size.value == TileSize.COMPACT
                    Column(Modifier.width(if (half) 158.dp else 324.dp)) {
                        TickerTile(tile, size.value, shrinkZeros = false)
                    }
                }
            }
        }
        for (fontScale in listOf(1f, 1.5f)) {
            for (tileSize in TileSize.entries) {
                compose.runOnIdle {
                    size.value = tileSize
                    scale.value = fontScale
                }
                for (label in listOf(title, "$deadline · USDC", percentage)) {
                    val node = compose.onNodeWithText(label, useUnmergedTree = true).assertIsDisplayed()
                    val layouts = mutableListOf<TextLayoutResult>()
                    node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
                    val layout = layouts.single()
                    val visible = node.fetchSemanticsNode().boundsInRoot
                    val context = "$tileSize at $fontScale: $label"
                    assertEquals(context, label.length, layout.getLineEnd(layout.lineCount - 1))
                    for (line in 0 until layout.lineCount) {
                        assertFalse(context, layout.isLineEllipsized(line))
                        assertTrue(context, layout.getLineLeft(line) >= -1f)
                        assertTrue("$context right=${layout.getLineRight(line)} width=${visible.width}",
                            layout.getLineRight(line) <= visible.width + 1f)
                    }
                    assertTrue(context, visible.height + 1f >= layout.multiParagraph.height)
                }
            }
        }
    }
}
