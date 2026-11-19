package com.neatcode.tabgreater.ui

import com.neatcode.tabgreater.core.model.InstrumentKind
import com.neatcode.tabgreater.core.model.Market
import com.neatcode.tabgreater.core.model.MarketKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.time.Instant

class MarketPresentationTest {
    @Test
    fun `legacy priceBinary above label uses inclusive operator and source threshold`() {
        val market = event(
            displayName = "BTC above \$63,330 · Nov 20, 2026 06:00 UTC · 1d period · Yes",
            outcomeSide = "Yes",
            description = "class:priceBinary|underlying:BTC|targetPrice:63330|expiry:20261120-0600",
            expiresAt = "2026-11-20T06:00:00Z",
        )

        assertEquals("Yes · BTC/USD ≥ 63,330\nEnds 20 Nov 2026 06:00 UTC", market.cardDisplayLabel())
    }

    @Test
    fun `new binary label keeps the source asset threshold and no context duplicates`() {
        val market = event(
            displayName = "SOL at least \$145.25 · Dec 31, 2026 17:00 UTC · 1d period · No",
            outcomeSide = "No",
            description = "class:priceBinary|underlying:SOL|targetPrice:145.25|expiry:20261231-1700",
            expiresAt = "2026-12-31T17:00:00Z",
        )

        assertEquals("No · SOL/USD ≥ 145.25\nEnds 31 Dec 2026 17:00 UTC", market.cardDisplayLabel())
        assertFalse(market.cardDisplayLabel().contains("1d period"))
        assertFalse(market.cardDisplayLabel().contains("USDC"))
    }

    @Test
    fun `binary threshold uses full source precision rather than rounded cached title`() {
        val market = event(
            displayName = "BTC above \$123,456.79 · Nov 20, 2026 06:00 UTC · Yes",
            outcomeSide = "Yes",
            description = "class:priceBinary|underlying:BTC|targetPrice:123456.789012345|expiry:20261120-0600",
            expiresAt = "2026-11-20T06:00:00Z",
        )

        assertEquals("Yes · BTC/USD ≥ 123,456.789012345\nEnds 20 Nov 2026 06:00 UTC", market.cardDisplayLabel())
    }

    @Test
    fun `priceBinary deployer wording stays exact without protocol class evidence`() {
        val market = event(
            displayName = "BTC above \$63,330 · Nov 20, 2026 06:00 UTC · Yes",
            outcomeSide = "Yes",
            description = "Will BTC close above the target?",
            expiresAt = "2026-11-20T06:00:00Z",
        )

        assertEquals("Yes · BTC above \$63,330\nNov 20, 2026 06:00 UTC", market.cardDisplayLabel())
    }

    @Test
    fun `legacy priceBucket midpoint spells out inclusive lower and exclusive upper bound`() {
        val market = event(
            displayName = "BTC price between \$62,063 and \$64,596 · Nov 20, 2026 06:00 UTC · Yes",
            outcomeSide = "Yes",
            description = "class:priceBucket|underlying:BTC|priceThresholds:62063,64596|index:1",
            expiresAt = "2026-11-20T06:00:00Z",
        )

        assertEquals(
            "Yes · BTC/USD from 62,063 to below 64,596\nEnds 20 Nov 2026 06:00 UTC",
            market.cardDisplayLabel(),
        )
    }

    @Test
    fun `sports card keeps both opponents without repeating a selected participant`() {
        val market = event(
            displayName = "Coventry v Palace: draw · Coventry · Yes",
            outcomeSide = "Yes",
            expiresAt = "2026-11-20T12:00:00Z",
        )

        assertEquals("Yes · Coventry\nvs Palace", market.cardDisplayLabel())
    }

    @Test
    fun `team winner card keeps the team and league prompt`() {
        val market = event(
            displayName = "2026/2027 NBA winner · Oklahoma City Thunder · No",
            outcomeSide = "No",
        )

        assertEquals("No · Oklahoma City Thunder\n2026/2027 NBA winner", market.cardDisplayLabel())
    }

    @Test
    fun `Fed issue keeps the selected action and its decision name without resolution deadline`() {
        val market = event(
            displayName = "Federal Open Market Committee December 2026 rate decision · Decrease · No",
            outcomeSide = "No",
            description = "decisionLabel:December 2026|institution:Federal Open Market Committee|decisionDeadline:20270127-1900|scheduledDecision:20261209-1900",
            expiresAt = "2027-01-27T19:00:00Z",
        )

        assertEquals("No · Decrease\nFederal Open Market Committee December 2026 rate decision", market.cardDisplayLabel())
    }

    @Test
    fun `sports metadata selects only a confirmed opponent and hides resolution deadline`() {
        val market = event(
            displayName = "Bundesliga: Borussia Monchengladbach v Borussia Dortmund · Borussia Monchengladbach · Yes",
            outcomeSide = "Yes",
            description = "participant:Borussia Monchengladbach|participantA:Borussia Monchengladbach|participantB:Borussia Dortmund|resolutionDeadline:20270221-1430|scheduledStart:20261121-1430",
            expiresAt = "2027-02-21T14:30:00Z",
        )

        assertEquals("Yes · Borussia Monchengladbach\nvs Borussia Dortmund", market.cardDisplayLabel())
    }

    @Test
    fun `participant matching preserves accented names`() {
        val market = event(
            displayName = "Cádiz v Sevilla · Cádiz · Yes",
            outcomeSide = "Yes",
            description = "participant:Cádiz|participantA:Cádiz|participantB:Sevilla",
        )

        assertEquals("Yes · Cádiz\nvs Sevilla", market.cardDisplayLabel())
    }

    @Test
    fun `touch prompt replaces machine time with a labeled precise UTC date`() {
        val market = event(
            displayName = "SOL touches \$145 by 20261201-0000 · Yes",
            outcomeSide = "Yes",
            description = "underlying:SOL|targetPrice:145.000000001|time:20261201-0000",
        )

        assertEquals("Yes · SOL/USD touches 145.000000001\nBy 1 Dec 2026 00:00 UTC", market.cardDisplayLabel())
    }

    @Test
    fun `a non-touch time keeps its original at relation`() {
        val market = event(
            displayName = "ETH above 2,500 at 20261201-0000 · Yes",
            outcomeSide = "Yes",
            description = "time:20261201-0000",
        )

        assertEquals("Yes · ETH above 2,500\nAt 1 Dec 2026 00:00 UTC", market.cardDisplayLabel())
    }

    @Test
    fun `unparsed time does not remove the source relation or date text`() {
        val market = event(
            displayName = "ETH above 2,500 at 2026Q4 · Yes",
            outcomeSide = "Yes",
            description = "time:unknown",
        )

        assertEquals("Yes · ETH above 2,500 at 2026Q4", market.cardDisplayLabel())
    }

    private fun event(
        displayName: String,
        outcomeSide: String,
        description: String? = null,
        expiresAt: String? = null,
    ) = Market(
        key = MarketKey("hyperliquid:OUTCOME_1_SIDE_0/USDC"),
        nativeSymbol = "#10",
        pricePrecision = 4,
        instrumentKind = InstrumentKind.OUTCOME,
        displayName = displayName,
        description = description,
        unit = "USDC per outcome token",
        outcomeSide = outcomeSide,
        expiresAt = expiresAt?.let { Instant.parse(it).toEpochMilli() },
    )
}
