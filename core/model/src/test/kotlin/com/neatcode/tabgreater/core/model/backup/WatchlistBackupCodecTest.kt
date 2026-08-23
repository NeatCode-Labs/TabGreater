package com.neatcode.tabgreater.core.model.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchlistBackupCodecTest {

    @Test
    fun `encode decode round trip keeps every field`() {
        val backup = WatchlistBackup(
            exportedAt = 1_787_000_000_000L,
            watchlists = listOf(
                WatchlistBackupEntry(
                    name = "Main",
                    period = "7d",
                    tileSize = "large",
                    sort = "change",
                    items = listOf(
                        WatchlistBackupItem("binance:BTC/EUR", "#FFFFBF66"),
                        WatchlistBackupItem("kraken:ETH/EUR"),
                    ),
                ),
                WatchlistBackupEntry(name = "Empty"),
            ),
        )

        val decoded = WatchlistBackupCodec.decode(WatchlistBackupCodec.encode(backup)).getOrThrow()

        assertEquals(backup, decoded)
        assertEquals(WatchlistBackup.FORMAT, decoded.format)
        assertEquals(WatchlistBackup.VERSION, decoded.version)
        assertNull(decoded.watchlists[0].items[1].accentColor)
        assertEquals(emptyList<WatchlistBackupItem>(), decoded.watchlists[1].items)
    }

    @Test
    fun `unknown json keys are ignored and missing ones default`() {
        val json = """
            {
              "format": "tabgreater-watchlists",
              "version": 1,
              "exportedAt": 5,
              "flavour": "vanilla",
              "watchlists": [ { "name": "Main", "items": [ { "key": "binance:BTC/EUR", "note": "x" } ] } ]
            }
        """.trimIndent()

        val backup = WatchlistBackupCodec.decode(json).getOrThrow()

        val entry = backup.watchlists.single()
        assertEquals("Main", entry.name)
        assertEquals("24h", entry.period)
        assertEquals("small", entry.tileSize)
        assertEquals("custom", entry.sort)
        assertNull(entry.items.single().accentColor)
    }

    @Test
    fun `malformed json fails with a backup format exception`() {
        val failure = WatchlistBackupCodec.decode("{ not json").exceptionOrNull()
        assertTrue(failure is BackupFormatException)
    }

    @Test
    fun `a foreign format marker fails`() {
        val json = """{"format":"other-app","version":1,"exportedAt":0,"watchlists":[]}"""
        assertTrue(WatchlistBackupCodec.decode(json).exceptionOrNull() is BackupFormatException)
    }

    @Test
    fun `a newer version fails`() {
        val json = """{"format":"tabgreater-watchlists","version":2,"exportedAt":0,"watchlists":[]}"""
        val failure = WatchlistBackupCodec.decode(json).exceptionOrNull()
        assertTrue(failure is BackupFormatException)
        assertTrue(failure!!.message!!.contains("2"))
    }

    @Test
    fun `argb is formatted upper case with eight digits`() {
        assertEquals("#FFFFBF66", WatchlistBackupCodec.formatArgb(0xFFFFBF66L))
        assertEquals("#00000000", WatchlistBackupCodec.formatArgb(0L))
    }

    @Test
    fun `argb parses six and eight digit forms`() {
        assertEquals(0xFFFFBF66L, WatchlistBackupCodec.parseArgb("#FFFFBF66"))
        assertEquals(0xFFFFBF66L, WatchlistBackupCodec.parseArgb("#FFBF66"))
        assertEquals(0xFFFFBF66L, WatchlistBackupCodec.parseArgb("  ffbf66 "))
        assertNull(WatchlistBackupCodec.parseArgb("#FFF"))
        assertNull(WatchlistBackupCodec.parseArgb("not a colour"))
    }

    @Test
    fun `accentArgb is null when the colour is malformed`() {
        assertNull(WatchlistBackupItem("binance:BTC/EUR", "#XYZ").accentArgb)
        assertEquals(0xFFFFBF66L, WatchlistBackupItem("binance:BTC/EUR", "#FFFFBF66").accentArgb)
    }

    @Test
    fun `marketKey is null for an unknown exchange or a malformed pair`() {
        assertNull(WatchlistBackupItem("foo:BAR/EUR").marketKey)
        assertNull(WatchlistBackupItem("binance:BTCEUR").marketKey)
        assertNull(WatchlistBackupItem("").marketKey)
        assertNotNull(WatchlistBackupItem("binance:BTC/EUR").marketKey)
    }
}
