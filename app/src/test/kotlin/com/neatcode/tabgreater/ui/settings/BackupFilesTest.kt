package com.neatcode.tabgreater.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.IOException

class BackupFilesTest {

    @Test
    fun `the suggested file name carries the UTC date`() {
        // 2023-11-14T22:13:20Z — still the 14th in UTC, already the 15th east of it.
        assertEquals(
            "tabgreater-watchlists-20231114.json",
            BackupFiles.suggestedFileName(1_700_000_000_000L),
        )
    }

    @Test
    fun `the epoch is formatted without separators`() {
        assertEquals("tabgreater-watchlists-19700101.json", BackupFiles.suggestedFileName(0L))
    }

    @Test
    fun `readLimited decodes UTF-8 across chunk boundaries`() {
        val text = "{\"format\":\"tabgreater-watchlists\",\"name\":\"Ärger · ok\"}".repeat(500)

        val read = BackupFiles.readLimited(ByteArrayInputStream(text.toByteArray(Charsets.UTF_8)))

        assertEquals(text, read)
    }

    @Test
    fun `readLimited refuses a file over the cap`() {
        val stream = ByteArrayInputStream(ByteArray(2048))

        val error = assertThrows(IOException::class.java) {
            BackupFiles.readLimited(stream, maxBytes = 1024)
        }

        assertEquals(true, error.message?.contains("larger than"))
    }

    @Test
    fun `the open picker accepts JSON, plain text and anything else`() {
        assertEquals(listOf("application/json", "text/plain", "*/*"), BackupFiles.OPEN_MIME_TYPES.toList())
    }
}
