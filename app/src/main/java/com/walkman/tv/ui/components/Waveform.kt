package com.walkman.tv.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.walkman.tv.ui.theme.AppColors
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * One wave layer = a sum of 3 sinusoids with incommensurate (non-integer-ratio) frequencies.
 * Summing three primes-ish ratios gives a quasi-random, non-repeating shape — no smooth-periodic
 * "wriggling worm" look you get from a single sine.
 *
 * - amp:      peak amplitude as a fraction of half-height
 * - k1/k2/k3: angular spatial frequency for each component (radians per px)
 * - d1/d2/d3: time drift speed for each component (radians per time-unit)
 * - w1/w2/w3: per-component amplitude weights (renormalised by 1/(w1+w2+w3))
 * - pulse:    breathing speed for amplitude beat
 * - pPhase:   offset so the two layers' beats don't peak simultaneously
 * - opacity:  stroke alpha at the centre of the gradient
 * - widthDp:  stroke width
 * - envBias:  centre of the amplitude envelope (0..1, 0.5 = dead-centre)
 */
internal data class WaveLayer(
    val amp: Float,
    val k1: Float, val k2: Float, val k3: Float,
    val d1: Float, val d2: Float, val d3: Float,
    val w1: Float, val w2: Float, val w3: Float,
    val pulse: Float, val pPhase: Float,
    val opacity: Float, val widthDp: Float,
    val envBias: Float,
)

private val DefaultWaveLayers = listOf(
    // Front line: brighter / thicker, biased slightly left of centre.
    WaveLayer(
        amp = 1.05f,
        k1 = 0.034f, k2 = 0.061f, k3 = 0.083f,
        d1 = 1.30f, d2 = -0.85f, d3 = 0.55f,
        w1 = 0.50f, w2 = 0.32f, w3 = 0.18f,
        pulse = 2.8f, pPhase = 0.0f,
        opacity = 0.90f, widthDp = 1.6f, envBias = 0.46f,
    ),
    // Back line: softer / faster phase drift, biased slightly right of centre.
    WaveLayer(
        amp = 0.82f,
        k1 = 0.041f, k2 = 0.073f, k3 = 0.107f,
        d1 = -1.05f, d2 = 1.45f, d3 = -0.72f,
        w1 = 0.45f, w2 = 0.35f, w3 = 0.20f,
        pulse = 3.6f, pPhase = 1.6f,
        opacity = 0.55f, widthDp = 1.2f, envBias = 0.54f,
    ),
)

/**
 * Two overlapping horizontal "audio" lines — same look as iOS AudioWave. Each line is a
 * sum of 3 sinusoids with non-integer-ratio frequencies (the *shape*), modulated by an
 * amplitude envelope (the *pulse*) that breathes with the music.
 *
 * When [level] > 0.02 the pulse rides the **real audio RMS** captured by
 * [com.walkman.tv.playback.AudioLevelProcessor] — the waveform now visibly snaps to drum
 * hits, bass drops and vocal swells, like its iOS sibling. When [level] is 0 (paused, no
 * processor data, or the player is in audio-offload mode), the pulse falls back to a
 * synthetic 2-sine envelope so the wave still looks alive on the recommend NowPlayingPanel
 * even before a track starts.
 *
 * Drifts while [isPlaying] is true, freezes at t=0 when paused. Shared between the
 * full-screen player and the recommend NowPlayingPanel.
 */
@Composable
fun Waveform(
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    level: Float = 0f,
) {
    val transition = rememberInfiniteTransition(label = "wave")
    val time by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1_000_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "wave-time",
    )
    Canvas(modifier = modifier) {
        val t = if (isPlaying) time.toDouble() else 0.0
        val midY = size.height / 2.0
        val width = size.width.toDouble()
        // Real-audio path activates the moment the processor publishes anything above the
        // numerical-noise threshold (matches iOS AudioWave.swift line ~381).
        val useRealLevel = level > 0.02f
        for (w in DefaultWaveLayers) {
            val pulse = if (useRealLevel) {
                // Real RMS drives every layer's amplitude. Floor at 0.15 so quiet passages
                // don't completely flatline the line; same floor iOS uses.
                maxOf(0.15, level.toDouble())
            } else {
                // Synthetic fallback — 2 incommensurate sines for a music-like rise/fall.
                // Floor at 0.28 so the wave never collapses near-flat between beats.
                val beat = 0.5 +
                    0.38 * sin(t * w.pulse + w.pPhase) +
                    0.22 * sin(t * w.pulse * 1.9 + w.pPhase * 1.7)
                maxOf(0.28, beat)
            }
            // Leave a stroke-radius gap top + bottom so the painted line never bleeds past
            // the canvas. Cap the fraction at 0.97 for extra safety against rounding overshoot.
            val strokeHalfPx = w.widthDp.dp.toPx() / 2.0
            val halfH = (size.height / 2.0 - strokeHalfPx).coerceAtLeast(1.0)
            val ampPx = minOf(w.amp * pulse, 0.97) * halfH
            val wSum = (w.w1 + w.w2 + w.w3).toDouble()
            val ph1 = t * w.d1
            val ph2 = t * w.d2 + 1.3
            val ph3 = t * w.d3 + 2.7

            val path = Path()
            fun yAt(xx: Double): Float {
                // Hann-like envelope centred at envBias — peaks in the middle, smoothly 0 at edges.
                val u = (xx / width - w.envBias) / 0.5
                val envRaw = 0.5 + 0.5 * cos(u.coerceIn(-1.0, 1.0) * PI)
                val env = envRaw * envRaw
                val sample = (
                    w.w1 * sin(xx * w.k1 + ph1) +
                    w.w2 * sin(xx * w.k2 + ph2) +
                    w.w3 * sin(xx * w.k3 + ph3)
                ) / wSum
                return (midY + sample * ampPx * env).toFloat()
            }
            path.moveTo(0f, yAt(0.0))
            var x = 0.0
            while (x <= width) {
                path.lineTo(x.toFloat(), yAt(x))
                x += 2.0
            }
            drawPath(
                path = path,
                brush = Brush.horizontalGradient(
                    colorStops = arrayOf(
                        0f to AppColors.AccentGreen.copy(alpha = 0f),
                        0.5f to AppColors.AccentGreen.copy(alpha = w.opacity),
                        1f to AppColors.AccentGreen.copy(alpha = 0f),
                    ),
                ),
                style = Stroke(width = w.widthDp.dp.toPx()),
            )
        }
    }
}
