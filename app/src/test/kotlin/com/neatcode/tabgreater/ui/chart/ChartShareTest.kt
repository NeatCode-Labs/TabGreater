package com.neatcode.tabgreater.ui.chart

import com.neatcode.tabgreater.core.model.MarketKey
import com.neatcode.tabgreater.core.model.Timeframe
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.ZoneId

/** The pure half of "Share chart": names, footer text and the cache pruning. */
class ChartShareTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val key = MarketKey("kraken:BTC/EUR")
    private val zone = ZoneId.of("Europe/Zagreb")

    // 2026-08-23T13:52:30Z = 15:52:30 in Zagreb (CEST).
    private val now = 1_787_493_150_000L

    @Test
    fun `file name carries exchange, pair, timeframe and a second stamp without path characters`() {
        val name = ChartShare.fileName(key, Timeframe.D1, now, zone)

        assertEquals("TabGreater_KRAKEN_BTC-EUR_1D_20260823-155230.png", name)
        assertEquals(false, name.contains('/') || name.contains(':'))
    }

    @Test
    fun `footer names the market, the timeframe and the local time`() {
        assertEquals("KRAKEN · BTC/EUR · 1D · 2026-08-23 15:52", ChartShare.footerText(key, Timeframe.D1, now, zone))
        assertEquals("GATE.IO · ETH/USDT · 15m · 2026-08-23 15:52",
            ChartShare.footerText(MarketKey("gate:ETH/USDT"), Timeframe.M15, now, zone))
    }

    @Test
    fun `prune keeps the newest files and deletes the rest`() {
        val dir = folder.newFolder("shared")
        val files = (1..5).map { i ->
            File(dir, "chart-$i.png").apply {
                writeText("x")
                setLastModified(1_000_000L * i)
            }
        }

        assertEquals(2, ChartShare.prune(dir, keep = 3))
        assertEquals(listOf("chart-3.png", "chart-4.png", "chart-5.png"), dir.list()!!.sorted())
        assertEquals(0, ChartShare.prune(dir, keep = 3))
        assertEquals(true, files.takeLast(3).all { it.exists() })
    }

    @Test
    fun `provider authority follows the package name, debug suffix included`() {
        assertEquals("com.neatcode.tabgreater.fileprovider", ChartShare.authority("com.neatcode.tabgreater"))
        assertEquals("com.neatcode.tabgreater.debug.fileprovider", ChartShare.authority("com.neatcode.tabgreater.debug"))
    }

    @Test
    fun `prune on a missing directory is a no-op`() {
        assertEquals(0, ChartShare.prune(File(folder.root, "nope"), keep = 3))
    }
}
