package com.walkman.tv.playback.download

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException

/**
 * Streaming HTTP download to disk with progress callbacks. Spec §3.3 step "FileDownloader.start".
 *
 * - Mobile-style UA so picky CDNs (酷狗 / 网易 / kw) don't reject the request.
 * - Writes to `<dest>.part` first then renames on success — atomic from the consumer's POV,
 *   safe against process death during the write.
 * - Cancellation: respects coroutine cancellation; on cancel the partial `.part` file is deleted.
 * - `onProgress` is called at most every 200ms or every 5% (whichever comes first) so the
 *   StateFlow consumer isn't flooded.
 */
internal class FileDownloader(private val http: OkHttpClient) {

    suspend fun download(
        url: String,
        dest: File,
        onProgress: (Float) -> Unit,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val tmp = File(dest.parentFile, dest.name + ".part")
        runCatching {
            dest.parentFile?.mkdirs()
            tmp.delete()
            tmp.outputStream().use { output -> streamTo(url, output, onProgress) }
            // Final 100% tick + atomic rename.
            onProgress(1f)
            if (!tmp.renameTo(dest)) {
                // Some filesystems can refuse cross-volume rename; fall back to copy + delete.
                tmp.copyTo(dest, overwrite = true)
                tmp.delete()
            }
            Unit
        }.onFailure { e ->
            tmp.delete()
            if (e is CancellationException) throw e
        }
    }

    /**
     * Download straight into an arbitrary [OutputStream] (e.g. a ByteArrayOutputStream for SAF
     * downloads, which we tag in memory then write once to the picked folder). Does not close
     * [out] — the caller owns it.
     */
    suspend fun download(
        url: String,
        out: java.io.OutputStream,
        onProgress: (Float) -> Unit,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            streamTo(url, out, onProgress)
            onProgress(1f)
            Unit
        }.onFailure { e ->
            if (e is CancellationException) throw e
        }
    }

    /** Shared streaming core: GET [url], copy the body to [output] with throttled progress.
     *  Does not close [output]. */
    private fun CoroutineScope.streamTo(
        url: String,
        output: java.io.OutputStream,
        onProgress: (Float) -> Unit,
    ) {
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", MOBILE_UA)
            .build()
        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
            val body = resp.body ?: throw IOException("empty body")
            val totalBytes = body.contentLength().takeIf { it > 0 } ?: -1L
            var written = 0L
            var lastReported = 0L
            var lastPctReported = 0f
            body.byteStream().use { input ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    ensureActive()
                    val n = input.read(buffer)
                    if (n <= 0) break
                    output.write(buffer, 0, n)
                    written += n
                    if (totalBytes > 0) {
                        val pct = written.toFloat() / totalBytes.toFloat()
                        val now = System.currentTimeMillis()
                        val timeOk = now - lastReported >= PROGRESS_MIN_INTERVAL_MS
                        val pctOk = pct - lastPctReported >= PROGRESS_MIN_DELTA
                        if (timeOk || pctOk) {
                            onProgress(pct.coerceIn(0f, 1f))
                            lastReported = now
                            lastPctReported = pct
                        }
                    }
                }
                output.flush()
            }
        }
    }

    private companion object {
        const val MOBILE_UA = "Mozilla/5.0 (Linux; Android 12; AndroidTV) AppleWebKit/537.36"
        const val PROGRESS_MIN_INTERVAL_MS = 200L
        const val PROGRESS_MIN_DELTA = 0.05f
    }
}
