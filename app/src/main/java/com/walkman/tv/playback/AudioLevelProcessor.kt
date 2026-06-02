package com.walkman.tv.playback

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.AudioProcessor.UnhandledAudioFormatException
import androidx.media3.common.audio.BaseAudioProcessor
import java.nio.ByteBuffer
import kotlin.math.sqrt

/**
 * Media3 [androidx.media3.common.audio.AudioProcessor] that taps every PCM sample heading to
 * the output sink, computes a per-chunk RMS, applies iOS-style peak-meter smoothing
 * (fast attack / slow release) and pushes the resulting [0, 1] level via [onLevel].
 *
 * Direct equivalent of `AudioLevelTap` in the iOS reference: same RMS-of-samples step, same
 * "rises fast, decays slow" envelope, same x3.5 boost so typical music RMS (~0.05–0.2) maps
 * to a visible range. The shared [com.walkman.tv.ui.components.Waveform] composable consumes
 * this level — when it's >0.02 the visualiser switches from its synthetic sinusoidal beat to
 * riding the real audio amplitude.
 *
 * Pass-through: forwards the input bytes unchanged so the downstream [DefaultAudioSink]
 * keeps playing bit-identical audio. When ExoPlayer chooses audio offload (DSP / HDMI
 * passthrough / Bluetooth A2DP offload) the processor chain is bypassed entirely and [onLevel]
 * stops firing — the consumer falls back to the synthetic waveform automatically.
 *
 * Threading: [onLevel] is invoked on Media3's audio thread (~50Hz at default buffer sizes).
 * The receiver should use a thread-safe sink like `MutableStateFlow` — Compose collects on
 * the main thread fine.
 */
class AudioLevelProcessor(
    private val onLevel: (Float) -> Unit,
) : BaseAudioProcessor() {

    private var bytesPerSample = 2  // default Int16
    private var floatSamples = false
    private var smoothedLevel = 0f

    override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat {
        when (inputAudioFormat.encoding) {
            C.ENCODING_PCM_16BIT -> { bytesPerSample = 2; floatSamples = false }
            C.ENCODING_PCM_FLOAT -> { bytesPerSample = 4; floatSamples = true }
            // 24/32-bit integer sources would need their own paths; in practice ExoPlayer
            // upconverts those to float when setEnableAudioFloatOutput(true) is set on the
            // renderers factory (we do). Anything else means we can't reason about samples,
            // so refuse to install — sink will skip us and play normally.
            else -> throw UnhandledAudioFormatException(inputAudioFormat)
        }
        smoothedLevel = 0f
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return

        // Compute RMS over a *duplicate* view so we don't consume the input here; the actual
        // pass-through happens below via `output.put(inputBuffer)`.
        val view = inputBuffer.duplicate().order(inputBuffer.order())
        val sampleCount = remaining / bytesPerSample
        if (sampleCount > 0) {
            var sumSq = 0.0
            if (floatSamples) {
                var i = 0
                while (i < sampleCount) {
                    val s = view.float
                    sumSq += s * s
                    i++
                }
            } else {
                var i = 0
                while (i < sampleCount) {
                    val s = view.short.toFloat() / Short.MAX_VALUE
                    sumSq += s * s
                    i++
                }
            }
            val rms = sqrt(sumSq / sampleCount).toFloat()
            // Typical music RMS lives around 0.05-0.2 — boost so 0.3+ maps to "loud peaks".
            val boosted = (rms * 3.5f).coerceAtMost(1f)
            // Peak-meter envelope: fast attack on rises, slow release on falls.
            smoothedLevel = if (boosted > smoothedLevel) {
                smoothedLevel * 0.5f + boosted * 0.5f
            } else {
                smoothedLevel * 0.85f + boosted * 0.15f
            }
            onLevel(smoothedLevel)
        }

        // Pass-through: copy input bytes into output buffer of identical size.
        val output = replaceOutputBuffer(remaining).order(inputBuffer.order())
        output.put(inputBuffer)
        output.flip()
    }

    override fun onQueueEndOfStream() {
        // Decay quickly to 0 so the wave settles when the track ends.
        smoothedLevel = 0f
        onLevel(0f)
    }

    override fun onReset() {
        smoothedLevel = 0f
        onLevel(0f)
    }
}
