package com.walkman.tv.playback

import com.walkman.tv.data.model.Quality

/**
 * Compute the badge-display quality from a claimed tier and the measured [AudioSpec].
 * Spec docs/audio-quality-spec-android-tv.md §6: clamp **down** only — never promote a
 * claimed lower tier to look higher. When the script claimed Hi-Res but the bytes are
 * 16-bit/44.1 kHz FLAC, this returns FLAC; when claimed Hi-Res but bytes are 128 kbps MP3,
 * this returns K128. Returns null when there's nothing to show.
 *
 * @param claimed the tier the cascade settled on (see [PlaybackController.lastAttemptedQuality])
 * @param spec    measured file-header parameters; null while probing or for unrecognised formats
 */
fun displayQuality(claimed: Quality?, spec: AudioSpec?): Quality? {
    if (claimed == null) return null
    if (spec == null) return claimed
    val ceiling = computeCeiling(claimed, spec) ?: return claimed
    val claimedRank = Quality.rank(claimed)
    val ceilingRank = Quality.rank(ceiling)
    // Lower rank = higher tier. Return whichever is *lower in absolute tier*.
    return if (claimedRank < ceilingRank) ceiling else claimed
}

private fun computeCeiling(claimed: Quality, spec: AudioSpec): Quality? = when (spec.codec) {
    "MP3" -> if ((spec.bitrateKbps ?: 0) >= 256) Quality.K320 else Quality.K128
    "FLAC" -> {
        val bits = spec.bitsPerSample ?: 16
        when {
            // True 24-bit. ≥176.4 kHz is the master threshold.
            bits > 16 -> if (spec.sampleRate >= 176_400) Quality.MASTER else Quality.HIRES
            // 16-bit FLAC. Atmos 2.0 is delivered as 16/44.1 stereo render — let it through
            // without demoting.
            claimed == Quality.ATMOS || claimed == Quality.ATMOS_PLUS -> claimed
            else -> Quality.FLAC
        }
    }
    else -> null
}
