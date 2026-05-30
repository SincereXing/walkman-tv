package com.walkman.tv.playback

import android.media.audiofx.Equalizer
import android.util.Log
import androidx.media3.common.C

/**
 * Thin wrapper around [android.media.audiofx.Equalizer]. Owned by [AppContainer], reuses one
 * Equalizer instance per audio session. Exposes preset names + apply/disable.
 *
 * The Equalizer is bound to ExoPlayer's audioSessionId, which changes each time the player is
 * (re-)created with new content. [rebindIfNeeded] handles that.
 */
class EqualizerManager(private val getSessionId: () -> Int) {
    private var eq: Equalizer? = null
    private var boundSessionId: Int = C.AUDIO_SESSION_ID_UNSET
    private var currentPreset: Short = 0

    /** Localized preset names; the system equalizer ships with English labels like 'Normal',
     *  'Classical', 'Rock' etc, so we keep the order but map to friendlier Chinese labels. */
    val presets: List<String> by lazy {
        ensureBound()
        val raw = eq?.let { e ->
            (0 until e.numberOfPresets).map { i -> e.getPresetName(i.toShort()) }
        }.orEmpty()
        raw.map { name ->
            when (name.lowercase()) {
                "normal" -> "标准"
                "classical" -> "古典"
                "dance" -> "电音"
                "flat" -> "平直"
                "folk" -> "民谣"
                "heavy metal" -> "金属"
                "hip hop" -> "嘻哈"
                "jazz" -> "爵士"
                "pop" -> "流行"
                "rock" -> "摇滚"
                else -> name
            }
        }
    }

    val currentPresetIndex: Int get() = currentPreset.toInt()

    fun applyPreset(index: Int) {
        ensureBound()
        val e = eq ?: return
        val idx = index.coerceIn(0, e.numberOfPresets - 1).toShort()
        runCatching {
            e.usePreset(idx)
            e.enabled = true
            currentPreset = idx
        }.onFailure { Log.w(TAG, "applyPreset $index failed: ${it.message}") }
    }

    /** Disable EQ entirely (Normal/0 may still color the sound; this turns the effect off). */
    fun disable() {
        runCatching { eq?.enabled = false }
    }

    /** Re-bind to the current audio session if it changed (e.g. after a player re-create). */
    private fun ensureBound() {
        val sid = getSessionId()
        if (sid == C.AUDIO_SESSION_ID_UNSET) return
        if (eq != null && boundSessionId == sid) return
        runCatching { eq?.release() }
        eq = runCatching { Equalizer(0, sid).also { it.enabled = true } }
            .onFailure { Log.w(TAG, "Equalizer bind failed: ${it.message}") }
            .getOrNull()
        boundSessionId = sid
    }

    fun release() {
        runCatching { eq?.release() }
        eq = null
        boundSessionId = C.AUDIO_SESSION_ID_UNSET
    }

    private companion object { const val TAG = "EqualizerManager" }
}
