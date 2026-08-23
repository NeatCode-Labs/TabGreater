package com.neatcode.tabgreater.ui.chart

import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import androidx.core.content.FileProvider
import androidx.core.graphics.createBitmap
import com.neatcode.tabgreater.R
import com.neatcode.tabgreater.core.model.MarketKey
import com.neatcode.tabgreater.core.model.TGColors
import com.neatcode.tabgreater.core.model.Timeframe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * "Share chart" — the leftmost toolbar action: a screenshot of the chart (statistics
 * grid and canvas; the app bar row with its back arrow and ★ stays out) with a small branded
 * footer, handed to the system share sheet.
 *
 * The pixels come from [PixelCopy] on the activity window, which is the only capture that
 * includes the hardware-composited WebView; `View.draw` would leave the canvas black. The PNG is
 * written under `cacheDir/shared/` and exposed through the manifest's `FileProvider`, so nothing
 * is ever granted a path, and the last few files are kept so a share sheet that is still reading
 * one does not lose it while the next is being written.
 */
internal object ChartShare {

    /** Cache sub-directory the manifest's `share_paths.xml` exposes. */
    const val DIR = "shared"

    /** Screenshots kept under [DIR]; older ones are deleted before each new capture. */
    const val KEEP_FILES = 3

    private const val FOOTER_HEIGHT_DP = 28f
    private const val FOOTER_PADDING_DP = 12f
    /** Multiplied by density only — a picture does not follow the viewer's font scale. */
    private const val FOOTER_TEXT_DP = 11f
    private const val MIME = "image/png"
    private const val AUTHORITY_SUFFIX = ".fileprovider"

    private val fileStamp: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss", Locale.ROOT)
    private val footerStamp: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ROOT)

    /**
     * `TabGreater_KRAKEN_BTC-EUR_1D_20260823-155230.png` — no `/` or `:` (illegal in a file name), and
     * down to the second; the screen allows one capture at a time, so two shares can only collide
     * when they are started within the same second, in which case the newer picture wins.
     */
    fun fileName(key: MarketKey, timeframe: Timeframe, now: Long, zone: ZoneId): String {
        val stamp = fileStamp.format(Instant.ofEpochMilli(now).atZone(zone))
        val pair = key.pair.replace('/', '-')
        return "TabGreater_${key.exchange.displayName.uppercase(Locale.ROOT)}_${pair}_${timeframe.label}_$stamp.png"
    }

    /** The footer's right-hand text: `KRAKEN · BTC/EUR · 1D · 2026-08-23 15:52`. */
    fun footerText(key: MarketKey, timeframe: Timeframe, now: Long, zone: ZoneId): String {
        val stamp = footerStamp.format(Instant.ofEpochMilli(now).atZone(zone))
        return "${key.exchange.displayName.uppercase(Locale.ROOT)} · ${key.pair} · ${timeframe.label} · $stamp"
    }

    /** The manifest's `${'$'}{applicationId}.fileprovider`, for the debug suffix as much as for release. */
    fun authority(packageName: String): String = packageName + AUTHORITY_SUFFIX

    /**
     * Deletes the oldest files in [dir] until at most [keep] remain. Returns how many were removed.
     * A file that refuses to go is skipped — the next share tries again.
     */
    fun prune(dir: File, keep: Int = KEEP_FILES): Int {
        val files = dir.listFiles { f -> f.isFile }?.sortedByDescending { it.lastModified() } ?: return 0
        return files.drop(keep).count { it.delete() }
    }

    /**
     * Captures [bounds] (window pixel coordinates) of [activity]'s window, brands it, writes the PNG
     * and opens the share sheet. Runs on the caller's dispatcher; the compress happens on IO.
     *
     * @throws IOException when the copy or the write fails — the caller turns it into a snackbar.
     */
    suspend fun share(
        activity: Activity,
        bounds: Rect,
        key: MarketKey,
        timeframe: Timeframe,
        now: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault(),
    ) {
        require(bounds.width() > 0 && bounds.height() > 0) { "empty capture bounds $bounds" }
        val raw = capture(activity, bounds)
        val density = activity.resources.displayMetrics.density
        val appName = activity.getString(R.string.app_name)
        val footer = footerText(key, timeframe, now, zone)
        val dir = File(activity.cacheDir, DIR)
        val name = fileName(key, timeframe, now, zone)
        // Everything after the copy — compositing two full-screen bitmaps and the PNG encode —
        // is off the main thread; only the copy itself needs the UI looper.
        val file = withContext(Dispatchers.IO) {
            val branded = try {
                brand(source = raw, density = density, left = appName, right = footer)
            } finally {
                raw.recycle()
            }
            try {
                dir.mkdirs()
                File(dir, name).also { target ->
                    target.outputStream().use { out ->
                        if (!branded.compress(Bitmap.CompressFormat.PNG, 100, out)) throw IOException("PNG encode failed")
                    }
                }
            } finally {
                branded.recycle()
            }
        }
        withContext(Dispatchers.IO) { prune(dir) }
        activity.startActivity(shareIntent(activity, file))
    }

    private suspend fun capture(activity: Activity, bounds: Rect): Bitmap {
        val bitmap = createBitmap(bounds.width(), bounds.height())
        return suspendCancellableCoroutine { cont ->
            PixelCopy.request(
                activity.window,
                bounds,
                bitmap,
                { result ->
                    if (result == PixelCopy.SUCCESS) {
                        // Cancelled while the copy was in flight: nobody will receive the bitmap.
                        cont.resume(bitmap) { _, _, _ -> bitmap.recycle() }
                    } else {
                        bitmap.recycle()
                        cont.resumeWithException(IOException("PixelCopy failed: $result"))
                    }
                },
                Handler(Looper.getMainLooper()),
            )
        }
    }

    /**
     * Appends a 28 dp footer — app name left, market/timeframe/time right — in the toolbar colours,
     * so the shared picture says where it came from without a logo in the chart itself.
     */
    private fun brand(source: Bitmap, density: Float, left: String, right: String): Bitmap {
        val footer = (FOOTER_HEIGHT_DP * density + 0.5f).toInt()
        val padding = FOOTER_PADDING_DP * density
        val out = createBitmap(source.width, source.height + footer)
        val canvas = Canvas(out)
        canvas.drawBitmap(source, 0f, 0f, null)

        val fill = Paint().apply { color = TGColors.NAV_SURFACE.toInt() }
        canvas.drawRect(0f, source.height.toFloat(), out.width.toFloat(), out.height.toFloat(), fill)

        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = TGColors.TEXT_SECONDARY.toInt()
            textSize = FOOTER_TEXT_DP * density
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }
        val baseline = source.height + footer / 2f - (text.descent() + text.ascent()) / 2f
        text.textAlign = Paint.Align.LEFT
        canvas.drawText(left, padding, baseline, text)
        text.textAlign = Paint.Align.RIGHT
        canvas.drawText(right, out.width - padding, baseline, text)
        return out
    }

    private fun shareIntent(context: Context, file: File): Intent {
        val uri = FileProvider.getUriForFile(context, authority(context.packageName), file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = MIME
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newUri(context.contentResolver, file.name, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        // No chooser title: the system sheet has ignored it since Android P and logs a warning.
        return Intent.createChooser(send, null)
    }
}
