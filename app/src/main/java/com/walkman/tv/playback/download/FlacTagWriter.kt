package com.walkman.tv.playback.download

import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Write Vorbis Comment + PICTURE blocks onto a FLAC file. Spec §4.2.
 *
 * FLAC layout: magic "fLaC" + sequence of metadata blocks + audio frames. We:
 *  1. Read the whole file.
 *  2. Find STREAMINFO (always first; we keep it).
 *  3. Skip past any existing VORBIS_COMMENT / PICTURE blocks (we replace).
 *  4. Walk to the end of the existing metadata chain to know where audio starts.
 *  5. Emit: "fLaC" + STREAMINFO + new VORBIS_COMMENT + new PICTURE? + audio frames.
 *
 * Vorbis Comment lengths are **little-endian** 32-bit. PICTURE block fields are **big-endian**.
 * That's a real footgun; the readers above hit the same one.
 */
internal object FlacTagWriter {

    fun write(
        file: File,
        title: String?,
        artist: String?,
        album: String?,
        trackNumber: Int? = null,
        trackTotal: Int? = null,
        year: String? = null,
        genre: String? = null,
        publisher: String? = null,
        albumArtist: String? = null,
        lyrics: String? = null,
        cover: ByteArray? = null,
        coverMime: String = "image/jpeg",
    ): Result<Unit> = runCatching {
        val bytes = file.readBytes()
        require(bytes.size >= 4) { "file too short" }
        require(
            bytes[0] == 'f'.code.toByte() && bytes[1] == 'L'.code.toByte() &&
                bytes[2] == 'a'.code.toByte() && bytes[3] == 'C'.code.toByte()
        ) { "not a FLAC file" }

        var p = 4
        // First block must be STREAMINFO — preserve verbatim.
        require(p + 4 <= bytes.size) { "truncated STREAMINFO header" }
        val streamHeader = bytes.copyOfRange(p, p + 4)
        val streamSize = ((streamHeader[1].toInt() and 0xFF) shl 16) or
            ((streamHeader[2].toInt() and 0xFF) shl 8) or
            (streamHeader[3].toInt() and 0xFF)
        require(p + 4 + streamSize <= bytes.size) { "truncated STREAMINFO" }
        val streamBody = bytes.copyOfRange(p + 4, p + 4 + streamSize)
        p += 4 + streamSize

        var isLast = (streamHeader[0].toInt() and 0x80) != 0
        // Walk to the end of the metadata chain.
        while (!isLast) {
            require(p + 4 <= bytes.size) { "truncated metadata header" }
            val header = bytes.copyOfRange(p, p + 4)
            val size = ((header[1].toInt() and 0xFF) shl 16) or
                ((header[2].toInt() and 0xFF) shl 8) or
                (header[3].toInt() and 0xFF)
            require(p + 4 + size <= bytes.size) { "truncated metadata block" }
            isLast = (header[0].toInt() and 0x80) != 0
            p += 4 + size
        }
        val audioStart = p

        // Build new metadata chain.
        val out = ByteArrayOutputStream(bytes.size + 100_000)
        out.write(byteArrayOf('f'.code.toByte(), 'L'.code.toByte(), 'a'.code.toByte(), 'C'.code.toByte()))

        // STREAMINFO — clear the last-block bit since we're adding more after it.
        out.write(0x00) // flags + type 0
        out.write(intBE24(streamBody.size))
        out.write(streamBody)

        val vorbisBody = buildVorbisComment(
            title, artist, album, trackNumber, trackTotal, year, genre,
            publisher, albumArtist, lyrics,
        )
        val hasCover = cover != null && cover.isNotEmpty()
        out.write(if (hasCover) 0x04 else 0x84) // type 4 VORBIS_COMMENT, last-bit if no PICTURE
        out.write(intBE24(vorbisBody.size))
        out.write(vorbisBody)

        if (hasCover) {
            val picBody = buildPicture(cover!!, coverMime)
            out.write(0x86) // last-block bit + type 6 PICTURE
            out.write(intBE24(picBody.size))
            out.write(picBody)
        }

        // Original audio frames.
        out.write(bytes, audioStart, bytes.size - audioStart)

        val tmp = File(file.parentFile, file.name + ".tagging")
        tmp.outputStream().use { it.write(out.toByteArray()) }
        if (!tmp.renameTo(file)) {
            tmp.copyTo(file, overwrite = true)
            tmp.delete()
        }
    }

    private fun buildVorbisComment(
        title: String?,
        artist: String?,
        album: String?,
        trackNumber: Int?,
        trackTotal: Int?,
        year: String?,
        genre: String?,
        publisher: String?,
        albumArtist: String?,
        lyrics: String?,
    ): ByteArray {
        val out = ByteArrayOutputStream()
        val vendor = "walkman-tv"
        out.write(intLE(vendor.length))
        out.write(vendor.toByteArray(Charsets.UTF_8))

        val entries = mutableListOf<String>()
        title?.takeIf { it.isNotBlank() }?.let { entries += "TITLE=$it" }
        artist?.takeIf { it.isNotBlank() }?.let { entries += "ARTIST=$it" }
        album?.takeIf { it.isNotBlank() }?.let { entries += "ALBUM=$it" }
        if (trackNumber != null && trackNumber > 0) entries += "TRACKNUMBER=$trackNumber"
        if (trackTotal != null && trackTotal > 0) entries += "TRACKTOTAL=$trackTotal"
        albumArtist?.takeIf { it.isNotBlank() }?.let { entries += "ALBUMARTIST=$it" }
        year?.takeIf { it.isNotBlank() }?.let { entries += "DATE=$it" }
        genre?.takeIf { it.isNotBlank() }?.let { entries += "GENRE=$it" }
        publisher?.takeIf { it.isNotBlank() }?.let { entries += "ORGANIZATION=$it" }
        lyrics?.takeIf { it.isNotBlank() }?.let { entries += "LYRICS=$it" }

        out.write(intLE(entries.size))
        for (e in entries) {
            val bytes = e.toByteArray(Charsets.UTF_8)
            out.write(intLE(bytes.size))
            out.write(bytes)
        }
        return out.toByteArray()
    }

    private fun buildPicture(data: ByteArray, mime: String): ByteArray {
        val out = ByteArrayOutputStream(32 + data.size)
        out.write(intBE(3))                              // picture type 3 = front cover
        val mimeBytes = mime.toByteArray(Charsets.US_ASCII)
        out.write(intBE(mimeBytes.size)); out.write(mimeBytes)
        out.write(intBE(0))                              // empty description
        out.write(intBE(0)); out.write(intBE(0))          // width / height (0 = unknown)
        out.write(intBE(0)); out.write(intBE(0))          // depth / colors
        out.write(intBE(data.size))
        out.write(data)
        return out.toByteArray()
    }

    private fun intBE(v: Int): ByteArray = byteArrayOf(
        ((v shr 24) and 0xFF).toByte(),
        ((v shr 16) and 0xFF).toByte(),
        ((v shr 8) and 0xFF).toByte(),
        (v and 0xFF).toByte(),
    )

    private fun intBE24(v: Int): ByteArray = byteArrayOf(
        ((v shr 16) and 0xFF).toByte(),
        ((v shr 8) and 0xFF).toByte(),
        (v and 0xFF).toByte(),
    )

    private fun intLE(v: Int): ByteArray = byteArrayOf(
        (v and 0xFF).toByte(),
        ((v shr 8) and 0xFF).toByte(),
        ((v shr 16) and 0xFF).toByte(),
        ((v shr 24) and 0xFF).toByte(),
    )
}
