package com.walkman.tv.playback

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.InputStream

/**
 * Probes a played audio URL's file header to recover the **actual** codec parameters,
 * independent of what the source script claimed. Spec docs/audio-quality-spec-android-tv.md §5.
 *
 * Strategy:
 *  - Remote: HTTP `Range: bytes=0-65535`. Some CDNs ignore Range and return the whole file,
 *    so we hand-truncate to `count` bytes.
 *  - Local file: seek+read directly.
 *  - Parse by magic:
 *      `fLaC`  → FLAC STREAMINFO bit-unpack
 *      `ID3`   → skip ID3v2 tag (length syncsafe), fetch from `10+size` if it overshoots the
 *                64 KB buffer, then continue at the next MPEG sync
 *      anything starting with `0xFF 0xEx` → bare MP3 sync scan
 *  - Anything else (m4a, encrypted .mgg, …) → return null, never throw.
 *
 *  UI surfaces the result only on success; failures are silently logged.
 */
class AudioSpecProbe(private val http: OkHttpClient) {

    suspend fun probe(url: String): AudioSpec? = withContext(Dispatchers.IO) {
        runCatching {
            val bytes = if (url.startsWith("http")) fetchRemote(url, 65_536) else readLocal(url, 65_536)
            if (bytes == null || bytes.size < 32) return@runCatching null
            parse(bytes, url)
        }.onFailure { Log.d(TAG, "probe failed for $url: ${it.message}") }
            .getOrNull()
    }

    // MARK: - Bytes -------------------------------------------------------------------------

    private fun fetchRemote(url: String, count: Int): ByteArray? {
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", UA)
            .header("Range", "bytes=0-${count - 1}")
            .build()
        // Custom 10s timeouts per the spec — independent of the shared OkHttp client config.
        val client = http.newBuilder()
            .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
            .build()
        return client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return@use null
            val source = resp.body?.byteStream() ?: return@use null
            readUpTo(source, count)
        }
    }

    private fun readLocal(path: String, count: Int): ByteArray? = runCatching {
        File(path).inputStream().use { readUpTo(it, count) }
    }.getOrNull()

    private fun readUpTo(input: InputStream, count: Int): ByteArray {
        val out = ByteArray(count)
        var total = 0
        while (total < count) {
            val read = input.read(out, total, count - total)
            if (read <= 0) break
            total += read
        }
        return if (total == count) out else out.copyOf(total)
    }

    // MARK: - Parse -------------------------------------------------------------------------

    private suspend fun parse(buf: ByteArray, originalUrl: String): AudioSpec? {
        // FLAC magic: "fLaC" then STREAMINFO is guaranteed to be the first metadata block.
        if (buf.size >= 4 && buf[0] == 'f'.code.toByte() && buf[1] == 'L'.code.toByte() &&
            buf[2] == 'a'.code.toByte() && buf[3] == 'C'.code.toByte()
        ) {
            return parseFlac(buf)
        }
        // ID3v2 tag: "ID3" + 2-byte version + 1-byte flags + 4-byte syncsafe size at offset 6-9.
        if (buf.size >= 10 && buf[0] == 'I'.code.toByte() && buf[1] == 'D'.code.toByte() &&
            buf[2] == '3'.code.toByte()
        ) {
            val size = ((buf[6].toInt() and 0x7F) shl 21) or
                ((buf[7].toInt() and 0x7F) shl 14) or
                ((buf[8].toInt() and 0x7F) shl 7) or
                (buf[9].toInt() and 0x7F)
            val frameStart = 10 + size
            // Album art commonly bloats ID3 tags past 64 KB. Refetch a small window at the
            // computed offset to find the MPEG sync.
            return if (frameStart < buf.size) {
                parseMpegSync(buf, frameStart)
            } else if (originalUrl.startsWith("http")) {
                val extra = fetchRangeWindow(originalUrl, frameStart, 8192) ?: return null
                parseMpegSync(extra, 0)
            } else {
                null
            }
        }
        // Bare MP3 / other: scan for an MPEG sync word at the start of the buffer.
        return parseMpegSync(buf, 0)
    }

    private fun parseFlac(buf: ByteArray): AudioSpec? {
        // Metadata block 1 at offset 4: byte[4] = (lastBlockFlag << 7) | blockType (0 = STREAMINFO).
        if (buf.size < 4 + 4 + 18) return null
        val blockType = buf[4].toInt() and 0x7F
        if (blockType != 0) return null
        // STREAMINFO body starts at offset 4 (header) + 4 (block header) = 8.
        // Bytes 8..17 are min/max block sizes and frame sizes; the 8-byte big-endian packed
        // value at offset 8+10 = 18 contains sample rate / channels / bits-per-sample.
        var packed = 0L
        for (i in 0 until 8) {
            packed = (packed shl 8) or (buf[18 + i].toLong() and 0xFF)
        }
        val sampleRate = ((packed ushr 44) and 0xFFFFF).toInt() // 20 bits
        val bitsPerSample = (((packed ushr 36) and 0x1F) + 1).toInt() // 5 bits + 1
        if (sampleRate <= 0 || bitsPerSample <= 0) return null
        return AudioSpec(codec = "FLAC", sampleRate = sampleRate, bitsPerSample = bitsPerSample)
    }

    private fun parseMpegSync(buf: ByteArray, fromOffset: Int): AudioSpec? {
        val end = minOf(buf.size - 4, fromOffset + 8192)
        for (i in fromOffset..end) {
            if (buf[i] != 0xFF.toByte()) continue
            val b1 = buf[i + 1].toInt() and 0xFF
            if (b1 and 0xE0 != 0xE0) continue
            val versionBits = (b1 shr 3) and 0x3
            if (versionBits == 1) continue // reserved
            val layerBits = (b1 shr 1) and 0x3
            if (layerBits != 1) continue // we only care about Layer 3
            val b2 = buf[i + 2].toInt() and 0xFF
            val bitrateIdx = b2 shr 4
            if (bitrateIdx == 0 || bitrateIdx == 15) continue
            val rateIdx = (b2 shr 2) and 0x3
            if (rateIdx == 3) continue

            val bitrateTable = if (versionBits == 3) MPEG1_L3_BITRATE else MPEG2_L3_BITRATE
            val rateTable = when (versionBits) {
                3 -> MPEG1_RATES
                2 -> MPEG2_RATES
                0 -> MPEG2_5_RATES
                else -> return null
            }
            val kbps = bitrateTable.getOrNull(bitrateIdx) ?: continue
            val hz = rateTable.getOrNull(rateIdx) ?: continue
            return AudioSpec(codec = "MP3", sampleRate = hz, bitrateKbps = kbps)
        }
        return null
    }

    private fun fetchRangeWindow(url: String, from: Int, count: Int): ByteArray? {
        val to = from + count - 1
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", UA)
            .header("Range", "bytes=$from-$to")
            .build()
        return runCatching {
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                resp.body?.byteStream()?.use { readUpTo(it, count) }
            }
        }.getOrNull()
    }

    companion object {
        private const val TAG = "AudioSpecProbe"
        private const val UA = "Mozilla/5.0 (Linux; Android 14; AndroidTV) AppleWebKit/537.36"

        // Bitrate / sample-rate tables straight from the MPEG audio Layer-III spec.
        private val MPEG1_L3_BITRATE = intArrayOf(
            0, 32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320,
        )
        private val MPEG2_L3_BITRATE = intArrayOf(
            0, 8, 16, 24, 32, 40, 48, 56, 64, 80, 96, 112, 128, 144, 160,
        )
        private val MPEG1_RATES = intArrayOf(44100, 48000, 32000)
        private val MPEG2_RATES = intArrayOf(22050, 24000, 16000)
        private val MPEG2_5_RATES = intArrayOf(11025, 12000, 8000)
    }
}
