package com.walkman.tv.playback

/**
 * Result of reading a played URL's file-header to recover the *actual* codec parameters.
 *
 * Spec docs/audio-quality-spec-android-tv.md §5: backends silently downgrade — script
 * claims hires but file is 16/44.1 FLAC, or claims FLAC but bytes are MP3. We probe the
 * first 64 KB and surface the real numbers so badge logic can clamp the claimed tier down.
 *
 * @property codec         "FLAC" or "MP3"
 * @property sampleRate    Hz from STREAMINFO (FLAC) or MPEG header (MP3)
 * @property bitsPerSample 16 / 24 — FLAC only
 * @property bitrateKbps   constant bitrate from MPEG frame header — MP3 only
 */
data class AudioSpec(
    val codec: String,
    val sampleRate: Int,
    val bitsPerSample: Int? = null,
    val bitrateKbps: Int? = null,
) {
    /**
     * Display text: "FLAC 24bit/192kHz" / "FLAC 16bit/44.1kHz" / "MP3 320kbps".
     * kHz integer when whole, else 1 decimal (per spec §5.3).
     */
    val displayText: String
        get() = when (codec) {
            "FLAC" -> {
                val khz = sampleRate / 1000.0
                val khzStr = if (sampleRate % 1000 == 0) khz.toInt().toString() else "%.1f".format(khz)
                val bits = bitsPerSample?.let { "${it}bit/" } ?: ""
                "FLAC $bits${khzStr}kHz"
            }
            "MP3" -> bitrateKbps?.let { "MP3 ${it}kbps" } ?: "MP3"
            else -> codec
        }
}
