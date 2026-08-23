package com.neatcode.tabgreater.ui.settings

import android.content.Context
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Reads and writes the backup JSON behind a document-picker Uri.
 *
 * The Uri is passed as a `String` on purpose: `android.net.Uri` is a framework class that JVM unit
 * tests cannot parse, so this seam lets [SettingsViewModel] be tested with an in-memory map while
 * the app uses [ContentResolverBackupIo].
 */
interface BackupIo {
    /** @throws IOException when the document cannot be opened or is bigger than [BackupFiles.MAX_BYTES]. */
    suspend fun read(uri: String): String

    /** @throws IOException when the document cannot be opened for writing. */
    suspend fun write(uri: String, text: String)
}

/** Storage Access Framework implementation; needs no permission because the user picks the file. */
class ContentResolverBackupIo(private val context: Context) : BackupIo {

    override suspend fun read(uri: String): String = withContext(Dispatchers.IO) {
        context.contentResolver.openInputStream(uri.toUri())
            ?.use { BackupFiles.readLimited(it) }
            ?: throw IOException("Cannot open the selected file")
    }

    override suspend fun write(uri: String, text: String): Unit = withContext(Dispatchers.IO) {
        // "wt" truncates, so overwriting an existing, longer backup does not leave a tail behind.
        context.contentResolver.openOutputStream(uri.toUri(), "wt")
            ?.use { it.write(text.toByteArray(Charsets.UTF_8)) }
            ?: throw IOException("Cannot write to the selected file")
    }
}

/** Pure helpers around the export/import documents. */
object BackupFiles {

    /** Refuse anything that cannot plausibly be a watchlist backup (20 lists × 100 tickers). */
    const val MAX_BYTES: Int = 5 * 1024 * 1024

    const val MIME_JSON: String = "application/json"

    /** Some file managers hand JSON out as `text/plain`, so the picker accepts a wider net. */
    val OPEN_MIME_TYPES: Array<String> = arrayOf(MIME_JSON, "text/plain", "*/*")

    /** `tabgreater-watchlists-20260822.json`, dated in UTC so exports sort chronologically. */
    fun suggestedFileName(epochMillis: Long): String {
        val date = Instant.ofEpochMilli(epochMillis).atZone(ZoneOffset.UTC).toLocalDate()
        return "tabgreater-watchlists-${date.format(DateTimeFormatter.BASIC_ISO_DATE)}.json"
    }

    /** Reads [input] as UTF-8, failing before the whole heap is spent on a wrong file. */
    fun readLimited(input: InputStream, maxBytes: Int = MAX_BYTES): String {
        val buffer = ByteArrayOutputStream()
        val chunk = ByteArray(CHUNK_BYTES)
        while (true) {
            val read = input.read(chunk)
            if (read < 0) break
            if (buffer.size() + read > maxBytes) {
                throw IOException("File is larger than ${maxBytes / (1024 * 1024)} MB")
            }
            buffer.write(chunk, 0, read)
        }
        return String(buffer.toByteArray(), Charsets.UTF_8)
    }

    private const val CHUNK_BYTES = 8 * 1024
}
