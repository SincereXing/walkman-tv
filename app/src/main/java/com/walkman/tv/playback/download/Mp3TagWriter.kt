package com.walkman.tv.playback.download

import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Write ID3v2.4 tags onto an MP3 file. Spec §4.1.
 *
 * Strategy:
 *  1. Read the entire file (Spec §4.3 — music files fit, keeps the code simple).
 *  2. Detect + strip any existing ID3v2 tag at the head.
 *  3. Build new tag bytes (header + frames + padding).
 *  4. Atomic write via tmp + rename so an interrupted write doesn't corrupt the file.
 *
 * Frames written when the corresponding param is non-null/non-empty:
 *   TIT2 title / TPE1 artist / TALB album / TRCK NN/total / TYER year /
 *   TCON genre / TPUB publisher / TPE2 album artist / USLT lyrics / APIC cover
 *
 * All text uses encoding byte = 3 (UTF-8). iTunes < 10 chokes on UTF-16 BOMs, UTF-8 reads
 * fine in everything modern.
 */
internal object Mp3TagWriter {

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
        val tagged = tag(
            file.readBytes(), title, artist, album,
            trackNumber, trackTotal, year, genre, publisher, albumArtist,
            lyrics, cover, coverMime,
        )
        val tmp = File(file.parentFile, file.name + ".tagging")
        tmp.outputStream().use { it.write(tagged) }
        if (!tmp.renameTo(file)) {
            tmp.copyTo(file, overwrite = true)
            tmp.delete()
        }
    }

    /** Pure transform: take the original MP3 bytes, return a copy with our ID3v2.4 tag in front
     *  (existing head tag stripped). Lets callers write the result anywhere (File or SAF). */
    fun tag(
        original: ByteArray,
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
    ): ByteArray {
        val audioStart = detectExistingTagSize(original)
        val newTag = buildTag(
            title, artist, album,
            trackNumber, trackTotal, year, genre, publisher, albumArtist,
            lyrics, cover, coverMime,
        )
        val out = ByteArrayOutputStream(newTag.size + (original.size - audioStart))
        out.write(newTag)
        out.write(original, audioStart, original.size - audioStart)
        return out.toByteArray()
    }

    /** Return the byte offset where the actual audio frames start (= existing tag size, or 0). */
    private fun detectExistingTagSize(bytes: ByteArray): Int {
        if (bytes.size < 10) return 0
        if (bytes[0] != 'I'.code.toByte() || bytes[1] != 'D'.code.toByte() || bytes[2] != '3'.code.toByte()) {
            return 0
        }
        val size = ((bytes[6].toInt() and 0x7F) shl 21) or
            ((bytes[7].toInt() and 0x7F) shl 14) or
            ((bytes[8].toInt() and 0x7F) shl 7) or
            (bytes[9].toInt() and 0x7F)
        return 10 + size
    }

    private fun buildTag(
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
        cover: ByteArray?,
        coverMime: String,
    ): ByteArray {
        val frames = ByteArrayOutputStream()
        title?.takeIf { it.isNotBlank() }?.let { frames.write(textFrame("TIT2", it)) }
        artist?.takeIf { it.isNotBlank() }?.let { frames.write(textFrame("TPE1", it)) }
        album?.takeIf { it.isNotBlank() }?.let { frames.write(textFrame("TALB", it)) }
        if (trackNumber != null && trackNumber > 0) {
            val trckText = if (trackTotal != null && trackTotal > 0) "$trackNumber/$trackTotal" else trackNumber.toString()
            frames.write(textFrame("TRCK", trckText))
        }
        year?.takeIf { it.isNotBlank() }?.let { frames.write(textFrame("TYER", it)) }
        genre?.takeIf { it.isNotBlank() }?.let { frames.write(textFrame("TCON", it)) }
        publisher?.takeIf { it.isNotBlank() }?.let { frames.write(textFrame("TPUB", it)) }
        albumArtist?.takeIf { it.isNotBlank() }?.let { frames.write(textFrame("TPE2", it)) }
        lyrics?.takeIf { it.isNotBlank() }?.let { frames.write(usltFrame(it)) }
        cover?.takeIf { it.isNotEmpty() }?.let { frames.write(apicFrame(it, coverMime)) }

        val padding = ByteArray(1024) // helps editors avoid re-shuffling later
        val body = frames.toByteArray() + padding
        val out = ByteArrayOutputStream(10 + body.size)
        out.write('I'.code); out.write('D'.code); out.write('3'.code)
        out.write(4); out.write(0) // version 2.4, revision 0
        out.write(0) // flags
        out.write(syncsafe(body.size))
        out.write(body)
        return out.toByteArray()
    }

    private fun textFrame(id: String, text: String): ByteArray {
        require(id.length == 4)
        val payload = ByteArrayOutputStream()
        payload.write(3) // UTF-8
        payload.write(text.toByteArray(Charsets.UTF_8))
        return frame(id, payload.toByteArray())
    }

    private fun usltFrame(lyrics: String): ByteArray {
        val payload = ByteArrayOutputStream()
        payload.write(3) // UTF-8
        payload.write("eng".toByteArray(Charsets.US_ASCII)) // language
        payload.write(0) // empty content descriptor + null terminator
        payload.write(lyrics.toByteArray(Charsets.UTF_8))
        return frame("USLT", payload.toByteArray())
    }

    private fun apicFrame(data: ByteArray, mime: String): ByteArray {
        val payload = ByteArrayOutputStream()
        payload.write(3) // UTF-8 (for description)
        payload.write(mime.toByteArray(Charsets.ISO_8859_1)); payload.write(0)
        payload.write(3) // picture type 3 = cover (front)
        payload.write(0) // empty description + null terminator
        payload.write(data)
        return frame("APIC", payload.toByteArray())
    }

    private fun frame(id: String, payload: ByteArray): ByteArray {
        val out = ByteArrayOutputStream(10 + payload.size)
        out.write(id.toByteArray(Charsets.US_ASCII))
        out.write(syncsafe(payload.size))
        out.write(0); out.write(0) // flags
        out.write(payload)
        return out.toByteArray()
    }

    /** Encode [size] as a 4-byte syncsafe integer (each byte holds 7 significant bits). */
    private fun syncsafe(size: Int): ByteArray = byteArrayOf(
        ((size shr 21) and 0x7F).toByte(),
        ((size shr 14) and 0x7F).toByte(),
        ((size shr 7) and 0x7F).toByte(),
        (size and 0x7F).toByte(),
    )
}
