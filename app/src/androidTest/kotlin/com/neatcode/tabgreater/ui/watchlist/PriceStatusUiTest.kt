package com.neatcode.tabgreater.ui.watchlist

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import com.neatcode.tabgreater.core.model.MarketKey
import com.neatcode.tabgreater.ui.components.TGAppBar
import com.neatcode.tabgreater.ui.theme.TabGreaterTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class PriceStatusUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun everyStatusKeepsHeaderAndContentAtTheSamePosition() {
        val status = mutableStateOf(PriceRefreshStatus.CURRENT)
        var clicks = 0
        compose.setContent {
            TabGreaterTheme {
                Column(Modifier.fillMaxSize()) {
                    TGAppBar { PriceStatusButton(status.value) { clicks++ } }
                    Text("Content below header", Modifier.testTag("contentOrigin"))
                }
            }
        }
        val button = compose.onNodeWithContentDescription("Show price status")
        val content = compose.onNodeWithTag("contentOrigin")
        val buttonBounds = button.fetchSemanticsNode().boundsInRoot
        val contentBounds = content.fetchSemanticsNode().boundsInRoot
        PriceRefreshStatus.entries.forEach { next ->
            compose.runOnIdle { status.value = next }
            button.assertHasClickAction().performClick()
            assertEquals(buttonBounds, button.fetchSemanticsNode().boundsInRoot)
            assertEquals(contentBounds, content.fetchSemanticsNode().boundsInRoot)
        }
        compose.runOnIdle { assertEquals(PriceRefreshStatus.entries.size, clicks) }
    }

    @Test fun longPressExplainsTheIconWithoutOpeningDetails() {
        var clicks = 0
        compose.setContent {
            TabGreaterTheme { PriceStatusButton(PriceRefreshStatus.CURRENT) { clicks++ } }
        }
        compose.onNodeWithContentDescription("Show price status").performTouchInput { longClick() }
        compose.onNodeWithText("Price status").assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, clicks) }
    }

    @Test fun detailsScrollAtLargeFontAndRetryCannotBeDuplicatedWhileRunning() {
        val running = mutableStateOf(true)
        var retries = 0
        val issues = (1..20).map {
            PriceRefreshIssue(MarketKey("binance:PAIR$it/USDT"), PriceIssueKind.ERROR)
        }
        compose.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, fontScale = 2f)) {
                TabGreaterTheme {
                    PriceStatusSheet(
                        status = PriceRefreshStatus.ERROR,
                        issues = issues,
                        refreshInProgress = running.value,
                        onRetry = { retries++ },
                        onDismiss = {},
                    )
                }
            }
        }
        compose.onNodeWithText("Retry").assertIsNotEnabled()
        compose.onNodeWithText("PAIR20/USDT · Binance").performScrollTo().assertIsDisplayed()
        compose.runOnIdle { running.value = false }
        compose.onNodeWithText("Retry").assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(1, retries) }
    }
}
