package com.walkman.tv.playback.local

import com.walkman.tv.data.model.Tags
import java.io.InputStream

/**
 * Byte-level tag reader. Spec §7.
 *
 * Platform-native APIs (MediaMetadataRetriever) are unreliable for FLAC's Vorbis Comment —
 * they routinely return nulls. This reader parses the file header directly so we can fill in
 * the gaps for the local-import flow + the cover-backfill path on already-downloaded songs.
 *
 * Caller decides which fields they want via the `want*` flags so we can short-circuit out of
 * the file once we've satisfied them all.
 *
 * Threading: caller's. I/O happens on whatever dispatcher the caller is on.
 */
internal object EmbeddedTagReader {

    fun read(
        input: InputStream,
        wantCover: Boolean = true,
        wantLyrics: Boolean = true,
        wantFields: Boolean = false,
    ): Tags {
        val tags = Tags()
        if (!wantCover && !wantLyrics && !wantFields) return tags

        // Sniff first 4 bytes for magic.
        val head = ByteArray(4)
        val read = readFully(input, head, 0, 4)
        if (read < 4) return tags
        return when {
            head[0] == 'f'.code.toByte() && head[1] == 'L'.code.toByte() &&
                head[2] == 'a'.code.toByte() && head[3] == 'C'.code.toByte() -> {
                readFlac(input, tags, wantCover, wantLyrics, wantFields)
            }
            head[0] == 'I'.code.toByte() && head[1] == 'D'.code.toByte() &&
                head[2] == '3'.code.toByte() -> {
                readId3(input, head, tags, wantCover, wantLyrics, wantFields)
            }
            else -> tags
        }
    }

    // ───────────────────────────────────────────────────────────────────────────
    // FLAC — sequence of metadata blocks, each [1B flags+type | 3B BE size | body]
    // ───────────────────────────────────────────────────────────────────────────

    private fun readFlac(
        input: InputStream,
        tags: Tags,
        wantCover: Boolean,
        wantLyrics: Boolean,
        wantFields: Boolean,
    ): Tags {
        var coverDone = !wantCover
        var lyricsDone = !wantLyrics
        var fieldsDone = !wantFields

        while (true) {
            val header = ByteArray(4)
            if (readFully(input, header, 0, 4) < 4) break
            val isLast = (header[0].toInt() and 0x80) != 0
            val type = header[0].toInt() and 0x7F
            val size = ((header[1].toInt() and 0xFF) shl 16) or
                ((header[2].toInt() and 0xFF) shl 8) or
                (header[3].toInt() and 0xFF)
            if (size < 0 || size > 50_000_000) break // sanity

            val body = ByteArray(size)
            if (readFully(input, body, 0, size) < size) break

            when (type) {
                4 -> { // VORBIS_COMMENT
                    parseVorbisComment(body, tags, wantLyrics, wantFields)
                    if (wantLyrics && tags.lyrics != null) lyricsDone = true
                    if (wantFields && tags.title != null && tags.artist != null && tags.album != null) fieldsDone = true
                }
                6 -> { // PICTURE
                    if (wantCover && tags.cover == null) {
                        parseFlacPicture(body)?.let { tags.cover = it; coverDone = true }
                    }
                }
            }
            if (isLast || (coverDone && lyricsDone && fieldsDone)) break
        }
        return tags
    }

    /** Vorbis Comment payload uses **little-endian** 32-bit lengths (FLAC PICTURE uses BE). */
    private fun parseVorbisComment(body: ByteArray, tags: Tags, wantLyrics: Boolean, wantFields: Boolean) {
        var p = 0
        fun le32(): Int? {
            if (p + 4 > body.size) return null
            val v = (body[p].toInt() and 0xFF) or
                ((body[p + 1].toInt() and 0xFF) shl 8) or
                ((body[p + 2].toInt() and 0xFF) shl 16) or
                ((body[p + 3].toInt() and 0xFF) shl 24)
            p += 4
            return v
        }

        val vendorLen = le32() ?: return
        if (vendorLen < 0 || p + vendorLen > body.size) return
        p += vendorLen
        val count = le32() ?: return
        if (count <= 0 || count > 1024) return
        val artists = mutableListOf<String>()
        repeat(count) {
            val len = le32() ?: return
            if (len < 0 || p + len > body.size) return
            val raw = String(body, p, len, Charsets.UTF_8)
            p += len
            val eq = raw.indexOf('=')
            if (eq <= 0) return@repeat
            val key = raw.substring(0, eq).uppercase()
            val value = raw.substring(eq + 1)
            when (key) {
                "LYRICS", "UNSYNCEDLYRICS", "UNSYNCED LYRICS" -> if (wantLyrics) tags.lyrics = value
                "TITLE" -> if (wantFields) tags.title = value
                "ARTIST", "ALBUMARTIST" -> if (wantFields) artists.add(value)
                "ALBUM" -> if (wantFields) tags.album = value
            }
        }
        if (wantFields && artists.isNotEmpty()) tags.artist = artists.joinToString(" / ")
    }

    /** PICTURE block layout: 4B picType, 4B mimeLen, mime, 4B descLen, desc, 4*4B image-info,
     *  4B dataLen, data. All **big-endian**. */
    private fun parseFlacPicture(body: ByteArray): ByteArray? {
        var p = 0
        fun be32(): Int? {
            if (p + 4 > body.size) return null
            val v = ((body[p].toInt() and 0xFF) shl 24) or
                ((body[p + 1].toInt() and 0xFF) shl 16) or
                ((body[p + 2].toInt() and 0xFF) shl 8) or
                (body[p + 3].toInt() and 0xFF)
            p += 4
            return v
        }

        be32() ?: return null // picture type, ignored
        val mimeLen = be32() ?: return null
        if (mimeLen < 0 || p + mimeLen > body.size) return null
        p += mimeLen
        val descLen = be32() ?: return null
        if (descLen < 0 || p + descLen > body.size) return null
        p += descLen
        // width/height/depth/colors — 4 BE32s skipped
        repeat(4) { be32() ?: return null }
        val dataLen = be32() ?: return null
        if (dataLen <= 0 || p + dataLen > body.size) return null
        val out = ByteArray(dataLen)
        System.arraycopy(body, p, out, 0, dataLen)
        return out
    }

    // ───────────────────────────────────────────────────────────────────────────
    // MP3 ID3v2 — "ID3" + 1B version + 1B revision + 1B flags + 4B syncsafe size
    // ───────────────────────────────────────────────────────────────────────────

    private fun readId3(
        input: InputStream,
        firstFour: ByteArray,
        tags: Tags,
        wantCover: Boolean,
        wantLyrics: Boolean,
        wantFields: Boolean,
    ): Tags {
        // Already consumed "ID3" + 1B version. Pull the rest of the header: revision + flags + size.
        val version = firstFour[3].toInt() and 0xFF
        val rest = ByteArray(6)
        if (readFully(input, rest, 0, 6) < 6) return tags
        // rest[0] = revision (ignored); rest[1] = flags (ignored — we don't handle ext header / unsync);
        // rest[2..5] = syncsafe size (4 bytes, 7 bits each, big-endian).
        val tagSize = ((rest[2].toInt() and 0x7F) shl 21) or
            ((rest[3].toInt() and 0x7F) shl 14) or
            ((rest[4].toInt() and 0x7F) shl 7) or
            (rest[5].toInt() and 0x7F)
        if (tagSize <= 0 || tagSize > 50_000_000) return tags

        val body = ByteArray(tagSize)
        if (readFully(input, body, 0, tagSize) < tagSize) return tags
        parseId3Frames(body, version, tags, wantCover, wantLyrics, wantFields)
        return tags
    }

    private fun parseId3Frames(
        body: ByteArray,
        majorVersion: Int,
        tags: Tags,
        wantCover: Boolean,
        wantLyrics: Boolean,
        wantFields: Boolean,
    ) {
        var p = 0
        while (p + 10 <= body.size) {
            val id = String(body, p, 4, Charsets.US_ASCII)
            // Padding / end marker.
            if (id[0].code == 0) break
            val sizeBytes = body.copyOfRange(p + 4, p + 8)
            // v2.4 uses syncsafe; v2.3 uses big-endian 32-bit.
            val frameSize = if (majorVersion >= 4) {
                ((sizeBytes[0].toInt() and 0x7F) shl 21) or
                    ((sizeBytes[1].toInt() and 0x7F) shl 14) or
                    ((sizeBytes[2].toInt() and 0x7F) shl 7) or
                    (sizeBytes[3].toInt() and 0x7F)
            } else {
                ((sizeBytes[0].toInt() and 0xFF) shl 24) or
                    ((sizeBytes[1].toInt() and 0xFF) shl 16) or
                    ((sizeBytes[2].toInt() and 0xFF) shl 8) or
                    (sizeBytes[3].toInt() and 0xFF)
            }
            // flags = body[p+8..p+9] ignored
            if (frameSize <= 0 || p + 10 + frameSize > body.size) break
            val frameStart = p + 10

            when (id) {
                "TIT2" -> if (wantFields) tags.title = decodeTextFrame(body, frameStart, frameSize)
                "TPE1" -> if (wantFields) tags.artist = decodeTextFrame(body, frameStart, frameSize)
                "TALB" -> if (wantFields) tags.album = decodeTextFrame(body, frameStart, frameSize)
                "USLT" -> if (wantLyrics) tags.lyrics = decodeUslt(body, frameStart, frameSize)
                "APIC" -> if (wantCover && tags.cover == null) {
                    tags.cover = decodeApic(body, frameStart, frameSize)
                }
            }
            p = frameStart + frameSize
        }
    }

    private fun decodeTextFrame(body: ByteArray, start: Int, size: Int): String? {
        if (size <= 1) return null
        val encoding = body[start].toInt() and 0xFF
        val text = decodeString(body, start + 1, size - 1, encoding) ?: return null
        // First null-terminator splits multi-value text frames (v2.4); take the first.
        return text.split(' ').firstOrNull { it.isNotBlank() }?.trim()
    }

    private fun decodeUslt(body: ByteArray, start: Int, size: Int): String? {
        if (size <= 5) return null
        val encoding = body[start].toInt() and 0xFF
        // skip language (3 bytes) + content descriptor (null-terminated string in same encoding).
        var p = start + 1 + 3
        // Walk past the descriptor to its null terminator.
        val termLen = if (encoding == 1 || encoding == 2) 2 else 1
        while (p < start + size - termLen) {
            val isNull = if (termLen == 2) {
                body[p].toInt() == 0 && body[p + 1].toInt() == 0
            } else body[p].toInt() == 0
            p += termLen
            if (isNull) break
        }
        val payloadLen = start + size - p
        if (payloadLen <= 0) return null
        return decodeString(body, p, payloadLen, encoding)
    }

    private fun decodeApic(body: ByteArray, start: Int, size: Int): ByteArray? {
        if (size <= 4) return null
        val encoding = body[start].toInt() and 0xFF
        var p = start + 1
        // MIME type: Latin-1, null-terminated.
        while (p < start + size && body[p].toInt() != 0) p++
        if (p >= start + size) return null
        p++ // skip null
        if (p >= start + size) return null
        p++ // skip picture type (1 byte)
        // Description: encoding-dependent null-terminated.
        val termLen = if (encoding == 1 || encoding == 2) 2 else 1
        while (p < start + size - termLen) {
            val isNull = if (termLen == 2) {
                body[p].toInt() == 0 && body[p + 1].toInt() == 0
            } else body[p].toInt() == 0
            p += termLen
            if (isNull) break
        }
        val dataLen = start + size - p
        if (dataLen <= 0) return null
        return body.copyOfRange(p, p + dataLen)
    }

    private fun decodeString(body: ByteArray, start: Int, len: Int, encoding: Int): String? {
        if (len <= 0 || start + len > body.size) return null
        val charset = when (encoding) {
            0 -> Charsets.ISO_8859_1
            1 -> Charsets.UTF_16 // BOM-driven
            2 -> Charsets.UTF_16BE
            3 -> Charsets.UTF_8
            else -> Charsets.ISO_8859_1
        }
        return runCatching { String(body, start, len, charset).trimEnd(' ', ' ') }.getOrNull()
    }

    private fun readFully(input: InputStream, into: ByteArray, offset: Int, count: Int): Int {
        var total = 0
        while (total < count) {
            val n = input.read(into, offset + total, count - total)
            if (n <= 0) break
            total += n
        }
        return total
    }
}
