package com.neatcode.tabgreater.ui.settings

import android.app.Application
import app.cash.turbine.test
import com.neatcode.tabgreater.core.live.LiveDiagnosticsState
import com.neatcode.tabgreater.core.live.TickerMode
import com.neatcode.tabgreater.core.live.Transport
import com.neatcode.tabgreater.core.live.WidgetRefresh
import com.neatcode.tabgreater.core.model.ImportMode
import com.neatcode.tabgreater.core.model.backup.WatchlistBackup
import com.neatcode.tabgreater.core.model.backup.WatchlistBackupCodec
import com.neatcode.tabgreater.core.model.backup.WatchlistBackupEntry
import com.neatcode.tabgreater.core.model.backup.WatchlistBackupItem
import com.neatcode.tabgreater.ui.testing.FakeAppSettings
import com.neatcode.tabgreater.ui.testing.FakeLiveSettings
import com.neatcode.tabgreater.ui.testing.FakeWatchlistRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val repository = FakeWatchlistRepository()
    private val settings = FakeAppSettings()
    private val io = FakeBackupIo()
    private val liveSettings = FakeLiveSettings()
    private val diagnostics = MutableStateFlow(LiveDiagnosticsState())

    /** Stands in for `PowerManager.isIgnoringBatteryOptimizations` and for the service launcher. */
    private var batteryUnrestricted = false
    private var widgetsChanged = 0

    /** Unconfined everywhere: the view models' `stateIn` must be hot before every assertion. */
    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `shrink zeros is written through to the settings store`() = settingsTest { viewModel ->
        viewModel.setShrinkZeros(false)

        assertEquals(false, settings.shrinkZerosValue)
        assertEquals(false, viewModel.uiState.value.shrinkZeros)
    }

    @Test
    fun `the defaults are five seconds in the app and five minutes on the home screen`() =
        settingsTest { viewModel ->
            assertEquals(5_000L, viewModel.uiState.value.watchlistRefreshMs)
            assertEquals(WidgetRefresh.MIN_5, viewModel.uiState.value.live.widgetRefresh)
        }

    @Test
    fun `the widget cadence is stored and the service is told to re-evaluate`() = settingsTest { viewModel ->
        viewModel.setWidgetRefresh(WidgetRefresh.LIVE)

        assertEquals(WidgetRefresh.LIVE, liveSettings.current.widgetRefresh)
        assertEquals(WidgetRefresh.LIVE, viewModel.uiState.value.live.widgetRefresh)
        assertEquals(1, widgetsChanged)

        viewModel.setWidgetRefresh(WidgetRefresh.MIN_15)

        assertEquals(WidgetRefresh.MIN_15, liveSettings.current.widgetRefresh)
        assertEquals(2, widgetsChanged)
    }

    @Test
    fun `the wifi gate is stored without restarting the service`() = settingsTest { viewModel ->
        viewModel.setWifiOnly(false)

        assertEquals(false, liveSettings.current.wifiOnly)
        assertEquals(false, viewModel.uiState.value.live.wifiOnly)
        assertEquals(0, widgetsChanged)
    }

    @Test
    fun `the watchlist rate is written through and snaps to the offered values`() = settingsTest { viewModel ->
        viewModel.setWatchlistRefreshMs(1_000L)

        assertEquals(1_000L, settings.watchlistRefreshMsValue)
        assertEquals(1_000L, viewModel.uiState.value.watchlistRefreshMs)
        // Nothing about the grid's redraw cadence involves the background service.
        assertEquals(0, widgetsChanged)

        viewModel.setWatchlistRefreshMs(3_000L)

        assertEquals(2_000L, viewModel.uiState.value.watchlistRefreshMs)
    }

    @Test
    fun `diagnostics pushed by the live layer reach the ui state`() = settingsTest { viewModel ->
        val running = LiveDiagnosticsState(
            serviceRunning = true,
            mode = TickerMode.LIVE,
            transport = Transport.WIFI,
            widgetCount = 2,
        )

        diagnostics.value = running

        assertEquals(running, viewModel.uiState.value.diagnostics)
    }

    @Test
    fun `the battery allowlist is re-read when the user comes back from the system dialog`() =
        settingsTest { viewModel ->
            assertEquals(false, viewModel.uiState.value.batteryUnrestricted)

            batteryUnrestricted = true
            assertEquals(false, viewModel.uiState.value.batteryUnrestricted)

            viewModel.refreshPermissions()

            assertEquals(true, viewModel.uiState.value.batteryUnrestricted)
        }

    @Test
    fun `the running service's own answer also counts as unrestricted`() = settingsTest { viewModel ->
        diagnostics.value = LiveDiagnosticsState(serviceRunning = true, ignoringBatteryOptimizations = true)

        assertEquals(true, viewModel.uiState.value.batteryUnrestricted)
    }

    @Test
    fun `the suggested file name is the UTC export date`() = settingsTest { viewModel ->
        viewModel.now = { EXPORTED_AT }

        assertEquals("tabgreater-watchlists-20231114.json", viewModel.suggestedFileName())
    }

    @Test
    fun `export writes JSON that decodes back to the same backup`() = settingsTest { viewModel ->
        repository.seed("Main", listOf("binance:BTC/EUR", "kraken:ETH/EUR"))
        repository.seed("Alts", listOf("kucoin:SOL/USDT"))
        viewModel.now = { EXPORTED_AT }

        viewModel.events.test {
            viewModel.exportTo(URI)
            assertEquals(SettingsEvent.Exported(2), awaitItem())
        }

        val decoded = WatchlistBackupCodec.decode(io.read(URI)).getOrThrow()
        assertEquals(repository.exportBackup(EXPORTED_AT), decoded)
        assertEquals(EXPORTED_AT, decoded.exportedAt)
    }

    @Test
    fun `export reports the failure reason`() = settingsTest { viewModel ->
        repository.seed("Main")
        io.writeFailure = IOException("disk full")

        viewModel.events.test {
            viewModel.exportTo(URI)
            assertEquals(SettingsEvent.ExportFailed("disk full"), awaitItem())
        }
        assertEquals(false, viewModel.uiState.value.busy)
    }

    @Test
    fun `a picked file is decoded and offered for confirmation`() = settingsTest { viewModel ->
        io.write(URI, WatchlistBackupCodec.encode(backup()))

        viewModel.loadImport(URI)

        val pending = viewModel.uiState.value.pendingImport!!
        assertEquals(2, pending.watchlists)
        assertEquals(3, pending.items)
    }

    @Test
    fun `replace drops the existing lists and reports the counts`() = settingsTest { viewModel ->
        repository.seed("Old", listOf("binance:BTC/EUR"))
        io.write(URI, WatchlistBackupCodec.encode(backup()))

        viewModel.loadImport(URI)
        viewModel.events.test {
            viewModel.confirmImport(ImportMode.REPLACE)
            val event = awaitItem() as SettingsEvent.Imported
            assertEquals(2, event.result.watchlistsAdded)
            assertEquals(0, event.result.watchlistsMerged)
            assertEquals(3, event.result.itemsAdded)
        }

        assertEquals(listOf("Main", "Alts"), repository.watchlists.map { it.name })
        assertNull(viewModel.uiState.value.pendingImport)
    }

    @Test
    fun `merge keeps the existing lists and fills in the missing tickers`() = settingsTest { viewModel ->
        val main = repository.seed("Main", listOf("binance:BTC/EUR"))
        io.write(URI, WatchlistBackupCodec.encode(backup()))

        viewModel.loadImport(URI)
        viewModel.events.test {
            viewModel.confirmImport(ImportMode.MERGE)
            val event = awaitItem() as SettingsEvent.Imported
            assertEquals(1, event.result.watchlistsMerged)
            assertEquals(1, event.result.watchlistsAdded)
            // BTC/EUR is already there; ETH/EUR and SOL/USDT are new.
            assertEquals(2, event.result.itemsAdded)
            assertEquals(1, event.result.itemsSkipped)
        }

        assertEquals(listOf("Main", "Alts"), repository.watchlists.map { it.name })
        assertEquals(
            listOf("binance:BTC/EUR", "kraken:ETH/EUR"),
            repository.itemsOf(main).map { it.key.value },
        )
    }

    @Test
    fun `an unreadable file reports the read failure`() = settingsTest { viewModel ->
        io.readFailure = IOException("permission denied")

        viewModel.events.test {
            viewModel.loadImport(URI)
            assertEquals(SettingsEvent.ImportFailed("permission denied"), awaitItem())
        }
        assertNull(viewModel.uiState.value.pendingImport)
    }

    @Test
    fun `malformed input is rejected as not a backup`() = settingsTest { viewModel ->
        io.write(URI, "{ this is not json")

        viewModel.events.test {
            viewModel.loadImport(URI)
            assertEquals(SettingsEvent.NotABackup, awaitItem())
        }
        assertNull(viewModel.uiState.value.pendingImport)
    }

    @Test
    fun `valid JSON with a foreign format marker is rejected`() = settingsTest { viewModel ->
        io.write(URI, """{"format":"something-else","version":1,"exportedAt":0,"watchlists":[]}""")

        viewModel.events.test {
            viewModel.loadImport(URI)
            assertEquals(SettingsEvent.NotABackup, awaitItem())
        }
    }

    @Test
    fun `a result produced while the screen is gone is delivered when it comes back`() = settingsTest { viewModel ->
        repository.seed("Main")
        io.write(URI, WatchlistBackupCodec.encode(backup()))

        // Nothing collects `events` here: the user navigated away, so SettingsScreen is not composed.
        viewModel.exportTo(URI)
        viewModel.loadImport(URI)
        viewModel.confirmImport(ImportMode.MERGE)

        viewModel.events.test {
            assertEquals(SettingsEvent.Exported(1), awaitItem())
            assertTrue(awaitItem() is SettingsEvent.Imported)
        }
    }

    @Test
    fun `cancelling the dialog forgets the pending import`() = settingsTest { viewModel ->
        io.write(URI, WatchlistBackupCodec.encode(backup()))
        viewModel.loadImport(URI)
        assertTrue(viewModel.uiState.value.pendingImport != null)

        viewModel.cancelImport()

        assertNull(viewModel.uiState.value.pendingImport)
    }

    private fun backup() = WatchlistBackup(
        exportedAt = EXPORTED_AT,
        watchlists = listOf(
            WatchlistBackupEntry(
                name = "Main",
                items = listOf(
                    WatchlistBackupItem("binance:BTC/EUR"),
                    WatchlistBackupItem("kraken:ETH/EUR", "#FFFFBF66"),
                ),
            ),
            WatchlistBackupEntry(name = "Alts", items = listOf(WatchlistBackupItem("kucoin:SOL/USDT"))),
        ),
    )

    private fun settingsTest(block: suspend TestScope.(SettingsViewModel) -> Unit) = runTest(dispatcher) {
        val viewModel = SettingsViewModel(
            settings = settings,
            watchlistRepository = repository,
            liveSettings = liveSettings,
            diagnostics = diagnostics,
            batteryUnrestricted = { batteryUnrestricted },
            onWidgetsChanged = { widgetsChanged++ },
            application = Application(),
        )
        viewModel.backupIo = io
        backgroundScope.launch { viewModel.uiState.collect { } }
        block(viewModel)
    }

    private companion object {
        const val URI = "content://downloads/backup.json"

        /** 2023-11-14T22:13:20Z — a fixed instant so the file name is deterministic. */
        const val EXPORTED_AT = 1_700_000_000_000L
    }
}

/** In-memory [BackupIo]: the SettingsViewModel never touches a ContentResolver in these tests. */
private class FakeBackupIo : BackupIo {
    private val files = mutableMapOf<String, String>()
    var readFailure: Exception? = null
    var writeFailure: Exception? = null

    override suspend fun read(uri: String): String {
        readFailure?.let { throw it }
        return files[uri] ?: throw IOException("no such document: $uri")
    }

    override suspend fun write(uri: String, text: String) {
        writeFailure?.let { throw it }
        files[uri] = text
    }
}
