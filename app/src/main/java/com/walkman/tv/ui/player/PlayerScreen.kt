package com.walkman.tv.ui.player

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode as AnimRepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Tune
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.walkman.tv.data.model.Track
import com.walkman.tv.playback.LyricParser
import com.walkman.tv.playback.RepeatMode
import com.walkman.tv.ui.appContainer
import com.walkman.tv.ui.components.Artwork
import com.walkman.tv.ui.components.TvFocusable
import com.walkman.tv.ui.components.TvPill
import com.walkman.tv.ui.theme.AppColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun PlayerScreen(onClose: () -> Unit, modifier: Modifier = Modifier) {
    val controller = appContainer.playbackController
    val state by controller.state.collectAsState()
    val lyrics by controller.lyrics.collectAsState()
    val love by appContainer.libraryStore.love.collectAsState()
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    var showMvQueue by remember { mutableStateOf(false) }
    val playFocus = remember { FocusRequester() }

    val track = state.currentTrack

    var showEqDialog by remember { mutableStateOf(false) }
    var showPicker by remember { mutableStateOf(false) }

    // Single combined focus restoration: when no modal is open AND we're not in MV mode,
    // focus the central play button. Re-fires on transitions back to the 'audio + no modal'
    // state (MV exit / drawer close / EQ dialog close / first composition). Importantly NOT
    // keyed on track.id, so plain track advances don't steal focus.
    //
    // Consolidating to ONE LaunchedEffect avoids four parallel tryRequestFocus loops fighting
    // each other in the 250ms after first composition.
    val wantsFocus = !state.isMv && !showMvQueue && !showEqDialog && !showPicker
    LaunchedEffect(wantsFocus) {
        if (wantsFocus) tryRequestFocus(playFocus)
    }

    BackHandler(enabled = true) {
        when {
            showMvQueue -> showMvQueue = false
            state.isMv -> controller.exitMv()
            else -> onClose()
        }
    }

    // Listen for the remote's Menu key to toggle the queue drawer (works in both audio and MV).
    LaunchedEffect(Unit) {
        appContainer.events.menuKey.collect { showMvQueue = !showMvQueue }
    }

    if (state.isMv) {
        // MV mode: back-key exits (handled above); Menu key opens the queue drawer.
        Box(modifier = modifier.fillMaxSize().background(AppColors.BgDeep)) {
            AndroidView(
                factory = { ctx -> PlayerView(ctx).apply { player = controller.player; useController = true } },
                modifier = Modifier.fillMaxSize(),
            )
            MvQueueDrawer(
                visible = showMvQueue,
                queue = state.queue,
                currentIndex = state.index,
                onSelect = { idx ->
                    showMvQueue = false
                    controller.playAt(idx)
                },
                modifier = Modifier.align(Alignment.CenterEnd),
            )
        }
        return
    }

    Box(
        modifier = modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(AppColors.BgPanel, AppColors.BgDeep)),
        ),
    ) {
        if (track == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("暂无播放", color = AppColors.TextSecondary)
            }
            return@Box
        }

        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 32.dp)) {
            // Main area: left = title + centered lyrics, right = rotating vinyl disc.
            Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
                Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    Text(
                        track.name,
                        color = AppColors.TextPrimary,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        track.singer,
                        color = AppColors.TextSecondary,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(18.dp))
                    LyricPane(
                        lyrics,
                        state.positionMs,
                        modifier = Modifier.fillMaxWidth().weight(1f),
                    )
                }
                Spacer(Modifier.width(32.dp))
                Box(
                    modifier = Modifier.fillMaxHeight().width(380.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    VinylDisc(track.picURL, state.isPlaying)
                }
            }

            // Bottom: waveform + progress bar + transport row.
            // (Status line removed — quality is shown in the TransportBar Hi-Res pill below;
            // the source / origin / message text is no longer surfaced to the player UI.)
            Spacer(Modifier.height(10.dp))
            Waveform(
                isPlaying = state.isPlaying,
                modifier = Modifier.fillMaxWidth().height(40.dp),
            )
            Spacer(Modifier.height(6.dp))
            ProgressBar(state.positionMs, state.durationMs, onSeek = { controller.seekTo(it) })
            Spacer(Modifier.height(14.dp))
            TransportBar(
                state = state,
                love = love,
                onTogglePlay = { controller.togglePlay() },
                onPrev = { controller.prev() },
                onNext = { controller.next() },
                onCycleRepeat = {
                    // 3-state cycle:
                    //   顺序循环 (ALL,  shuffle=off)  → 单曲循环 (ONE, shuffle=off)
                    //   单曲循环 (ONE,  shuffle=off)  → 随机循环 (ALL, shuffle=on)
                    //   随机循环 (ALL,  shuffle=on)   → 顺序循环 (ALL, shuffle=off)
                    val mode = state.repeatMode
                    val shuffle = state.shuffle
                    when {
                        mode == RepeatMode.ALL && !shuffle -> {
                            controller.setRepeatMode(RepeatMode.ONE)
                        }
                        mode == RepeatMode.ONE && !shuffle -> {
                            controller.setRepeatMode(RepeatMode.ALL)
                            if (!state.shuffle) controller.toggleShuffle()
                        }
                        else -> {
                            controller.setRepeatMode(RepeatMode.ALL)
                            if (state.shuffle) controller.toggleShuffle()
                        }
                    }
                },
                onToggleFav = { showPicker = true },
                onMv = {
                    // Toast lets the user see the click registered and surfaces both success and
                    // failure: silent no-op was indistinguishable from a broken button.
                    android.widget.Toast.makeText(context, "正在获取 MV…", android.widget.Toast.LENGTH_SHORT).show()
                    scope.launch {
                        val info = runCatching { appContainer.mvResolver.getMvUrl(track) }.getOrNull()
                        val url = info?.bestUrl()
                        if (url != null) {
                            controller.playMvUrl(url)
                        } else {
                            android.widget.Toast.makeText(context, "暂无可用 MV", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                onShowQueue = { showMvQueue = true },
                onTuneClick = { showEqDialog = true },
                playFocusRequester = playFocus,
            )
        }
        // Queue drawer. Selecting a row plays that track but keeps the drawer open so the
        // user can keep browsing; back-key closes the drawer (handled by BackHandler above).
        MvQueueDrawer(
            visible = showMvQueue,
            queue = state.queue,
            currentIndex = state.index,
            onSelect = { idx -> controller.playAt(idx) },
            modifier = Modifier.align(Alignment.CenterEnd),
        )

        if (showEqDialog) {
            EqualizerDialog(onDismiss = { showEqDialog = false })
        }
        if (showPicker) {
            com.walkman.tv.ui.components.PlaylistPickerDialog(
                track = track,
                onDismiss = { showPicker = false },
            )
        }
    }
}

@Composable
private fun EqualizerDialog(onDismiss: () -> Unit) {
    val eq = appContainer.equalizerManager
    val presets = remember { eq.presets }
    var selected by remember { mutableStateOf(eq.currentPresetIndex) }
    // Auto-focus the currently-selected preset (not the close button). This lets the user
    // immediately D-pad up/down through the list — focusing '完成' meant they had to first
    // D-pad up past the spacer to even reach the presets, and that's where the failure was.
    val activeRowFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        tryRequestFocus(activeRowFocus)
    }

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = Modifier
                .width(360.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(AppColors.BgPanel)
                .padding(horizontal = 22.dp, vertical = 20.dp),
        ) {
            Text("均衡器", color = AppColors.TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(
                if (presets.isEmpty()) "本机不支持系统均衡器" else "选择一个预设，立即生效",
                color = AppColors.TextSecondary,
                fontSize = 12.sp,
            )
            Spacer(Modifier.height(14.dp))
            if (presets.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    presets.forEachIndexed { idx, label ->
                        val active = idx == selected
                        TvFocusable(
                            onClick = {
                                selected = idx
                                eq.applyPreset(idx)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .let { if (active) it.focusRequester(activeRowFocus) else it },
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    label,
                                    color = if (active) AppColors.AccentGreen else AppColors.TextPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                                    modifier = Modifier.weight(1f),
                                )
                                if (active) {
                                    Text("✓", color = AppColors.AccentGreen, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ============== Vinyl disc =====================================================================

@Composable
private fun VinylDisc(picURL: String?, isPlaying: Boolean) {
    val rotation = remember { Animatable(0f) }
    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            // 30s revolution; continues from the current angle when un-paused, freezes on pause.
            while (true) {
                rotation.animateTo(
                    rotation.value + 360f,
                    animationSpec = tween(durationMillis = 30_000, easing = LinearEasing),
                )
            }
        }
    }
    val circle = androidx.compose.foundation.shape.CircleShape
    Box(modifier = Modifier.size(360.dp), contentAlignment = Alignment.Center) {
        // 1) Outer green halo (decorative glow, doesn't rotate).
        Box(
            modifier = Modifier
                .size(360.dp)
                .clip(circle)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            AppColors.AccentGreen.copy(alpha = 0.55f),
                            AppColors.AccentGreen.copy(alpha = 0.20f),
                            AppColors.AccentGreen.copy(alpha = 0.00f),
                        ),
                    ),
                ),
        )
        // 2) The whole record — grooves + album art rotate together. graphicsLayer applies the
        // transform at the draw phase so the Canvas + AsyncImage don't recompose per frame.
        Box(
            modifier = Modifier
                .size(300.dp)
                .graphicsLayer { rotationZ = rotation.value },
            contentAlignment = Alignment.Center,
        ) {
            VinylGrooves(modifier = Modifier.size(300.dp))
            // Album art clipped to circle, sits flush with the inner groove edge (≈230dp).
            Box(
                modifier = Modifier
                    .size(220.dp)
                    .clip(circle)
                    .background(AppColors.BgDeep),
            ) {
                Artwork(picURL, modifier = Modifier.fillMaxSize(), shape = circle)
            }
        }
        // 3) Center spindle hole (doesn't rotate — stays sharp).
        Box(modifier = Modifier.size(14.dp).clip(circle).background(Color.Black))
    }
}

/** Vinyl groove texture: dark green base, radial depth gradient, concentric groove rings,
 *  bright outer rim. Drawn once into a Canvas (then rotated by the parent graphicsLayer). */
@Composable
private fun VinylGrooves(modifier: Modifier) {
    androidx.compose.foundation.Canvas(modifier = modifier) {
        val center = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height / 2f)
        val outerRadius = size.minDimension / 2f
        // The grooves sit between the album-art edge (~110dp radius) and the outer rim.
        val innerRadius = 110.dp.toPx()

        // Base dark green disc.
        drawCircle(color = Color(0xFF0E2818), radius = outerRadius, center = center)

        // Subtle radial gradient for depth (centre slightly darker than the rim).
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFF0A1F12),
                    Color(0xFF112F1C),
                    Color(0xFF1B4528),
                ),
                center = center,
                radius = outerRadius,
            ),
            radius = outerRadius,
            center = center,
        )

        // Concentric groove lines (uneven alpha for a real-record look).
        val grooveCount = 32
        for (i in 0 until grooveCount) {
            val frac = i / (grooveCount - 1).toFloat()
            val r = innerRadius + (outerRadius - innerRadius) * frac
            // Vary thickness and alpha so the grooves don't look mechanical.
            val alpha = 0.20f + 0.10f * ((i % 5) / 5f)
            val w = if (i % 7 == 0) 1.2.dp.toPx() else 0.6.dp.toPx()
            drawCircle(
                color = Color.Black.copy(alpha = alpha),
                radius = r,
                center = center,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = w),
            )
        }

        // A subtle highlight ring just outside the album art so the cover doesn't bleed
        // into the grooves visually.
        drawCircle(
            color = AppColors.AccentGreen.copy(alpha = 0.45f),
            radius = innerRadius + 2.dp.toPx(),
            center = center,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5.dp.toPx()),
        )

        // Bright outer rim so the disc edge reads against the green halo.
        drawCircle(
            color = AppColors.AccentGreen.copy(alpha = 0.55f),
            radius = outerRadius - 1.dp.toPx(),
            center = center,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx()),
        )
    }
}

// ============== Waveform (ported from iOS AudioWave) ==========================================

/**
 * One wave layer = a sum of 3 sinusoids with incommensurate (non-integer-ratio) frequencies.
 * Summing three primes-ish ratios gives a quasi-random, non-repeating shape — none of the
 * smooth-periodic "wriggling worm" look you get from a single sine.
 *
 * - amp:      peak amplitude as a fraction of half-height
 * - k1/k2/k3: angular spatial frequency for each component (radians per px)
 * - d1/d2/d3: time drift speed for each component (radians per time-unit)
 * - w1/w2/w3: per-component amplitude weights (summed → renormalised by 1/(w1+w2+w3))
 * - pulse:    breathing speed for amplitude beat
 * - pPhase:   offset so the two layers' beats don't peak simultaneously
 * - opacity:  stroke alpha at the centre of the gradient
 * - widthDp:  stroke width
 * - envBias:  centre of the amplitude envelope (0..1, 0.5 = dead-centre)
 */
private data class WaveLayer(
    val amp: Float,
    val k1: Float, val k2: Float, val k3: Float,
    val d1: Float, val d2: Float, val d3: Float,
    val w1: Float, val w2: Float, val w3: Float,
    val pulse: Float, val pPhase: Float,
    val opacity: Float, val widthDp: Float,
    val envBias: Float,
)

private val waveLayers = listOf(
    // Front line: brighter / thicker, biased slightly left of centre.
    WaveLayer(
        amp = 0.85f,
        k1 = 0.034f, k2 = 0.061f, k3 = 0.083f,
        d1 = 1.30f, d2 = -0.85f, d3 = 0.55f,
        w1 = 0.50f, w2 = 0.32f, w3 = 0.18f,
        pulse = 2.8f, pPhase = 0.0f,
        opacity = 0.90f, widthDp = 1.6f, envBias = 0.46f,
    ),
    // Back line: softer / faster phase drift, biased slightly right of centre.
    WaveLayer(
        amp = 0.62f,
        k1 = 0.041f, k2 = 0.073f, k3 = 0.107f,
        d1 = -1.05f, d2 = 1.45f, d3 = -0.72f,
        w1 = 0.45f, w2 = 0.35f, w3 = 0.20f,
        pulse = 3.6f, pPhase = 1.6f,
        opacity = 0.55f, widthDp = 1.2f, envBias = 0.54f,
    ),
)

/**
 * Two overlapping horizontal "audio" lines — same look as iOS AudioWave but each line is now a
 * sum of 3 sinusoids with non-integer-ratio frequencies so the curve is irregular (no obvious
 * repeating period). A Hann-like envelope centred slightly off-mid pulls both ends to 0 so the
 * lines converge at the edges. Drifts while playing, freezes at t=0 when paused.
 */
@Composable
private fun Waveform(isPlaying: Boolean, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "wave")
    val time by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1_000_000, easing = LinearEasing),
            repeatMode = AnimRepeatMode.Restart,
        ),
        label = "wave-time",
    )
    Canvas(modifier = modifier) {
        val t = if (isPlaying) time.toDouble() else 0.0
        val midY = size.height / 2.0
        val width = size.width.toDouble()
        for (w in waveLayers) {
            // Amplitude breath — 2 incommensurate sines summed for a "music-like" rise/fall.
            val beat = 0.5 +
                0.38 * sin(t * w.pulse + w.pPhase) +
                0.22 * sin(t * w.pulse * 1.9 + w.pPhase * 1.7)
            val pulse = maxOf(0.10, beat)
            val ampPx = minOf(w.amp * pulse, 0.92) * size.height / 2.0
            val wSum = (w.w1 + w.w2 + w.w3).toDouble()
            val ph1 = t * w.d1
            val ph2 = t * w.d2 + 1.3
            val ph3 = t * w.d3 + 2.7

            val path = Path()
            fun yAt(xx: Double): Float {
                // Hann-like envelope centred at envBias — peaks in the middle, smoothly 0 at edges.
                val u = (xx / width - w.envBias) / 0.5 // -1..1 across the canvas (when envBias=0.5)
                val envRaw = 0.5 + 0.5 * cos(u.coerceIn(-1.0, 1.0) * PI) // 1 at centre, 0 at ±1
                val env = envRaw * envRaw // sharpen the falloff so edges fade harder
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

// ============== Transport ======================================================================

@Composable
private fun TransportBar(
    state: com.walkman.tv.playback.PlaybackState,
    love: com.walkman.tv.data.model.Playlist,
    onTogglePlay: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onCycleRepeat: () -> Unit,
    onToggleFav: () -> Unit,
    onMv: () -> Unit,
    onShowQueue: () -> Unit,
    onTuneClick: () -> Unit,
    playFocusRequester: FocusRequester? = null,
) {
    val track = state.currentTrack
    val faved = track?.let { t -> love.tracks.any { it.id == t.id } } ?: false
    // Box + align modifiers instead of sub-Rows separated by Spacer(weight=1f). With the old
    // layout the wide Spacers seemed to confuse Compose's geometric focus search — D-pad would
    // 'stop' before crossing from center to left/right. Putting all buttons as siblings of one
    // Box, each anchored with Modifier.align, makes the focus traversal reliable.
    Box(modifier = Modifier.fillMaxWidth()) {
        // Left group anchored to CenterStart.
        Row(
            modifier = Modifier.align(Alignment.CenterStart),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            IconPill(Icons.Filled.Tune, onClick = onTuneClick)
            state.quality?.let {
                TvPill(
                    onClick = { /* future: open a quality picker */ },
                    selected = true,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                ) {
                    Text(it.displayName, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        // Center group anchored to Center.
        Row(
            modifier = Modifier.align(Alignment.Center),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Icon reflects the *combined* (repeatMode, shuffle) state:
            //   shuffle=on            -> Shuffle (随机循环)
            //   shuffle=off, ONE      -> RepeatOne (单曲循环)
            //   shuffle=off, ALL/OFF  -> Repeat (顺序循环)
            val repeatIcon = when {
                state.shuffle -> Icons.Filled.Shuffle
                state.repeatMode == RepeatMode.ONE -> Icons.Filled.RepeatOne
                else -> Icons.Filled.Repeat
            }
            IconPill(repeatIcon, active = true, onClick = onCycleRepeat)
            IconPill(Icons.Filled.SkipPrevious, onClick = onPrev)
            IconPill(
                if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                large = true,
                focusRequester = playFocusRequester,
                onClick = onTogglePlay,
            )
            IconPill(Icons.Filled.SkipNext, onClick = onNext)
            IconPill(
                if (faved) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                active = faved,
                onClick = onToggleFav,
            )
        }
        // Right group anchored to CenterEnd.
        Row(
            modifier = Modifier.align(Alignment.CenterEnd),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TvPill(
                onClick = onMv,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Text("MV", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            IconPill(Icons.AutoMirrored.Filled.QueueMusic, onClick = onShowQueue)
        }
    }
}

private const val SEEK_STEP_MS = 5_000L

/**
 * D-pad-seekable progress bar. When focused (D-pad up from the play button), left/right step
 * the position by 5 seconds. Visual focus cue: bar thickens (3 -> 6dp) and the elapsed-time
 * label turns green. Long-press auto-repeat is handled by the OS (held D-pad emits repeats).
 */
@Composable
private fun ProgressBar(positionMs: Long, durationMs: Long, onSeek: (Long) -> Unit) {
    val fraction = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    var focused by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused }
            .onPreviewKeyEvent { evt ->
                if (evt.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                if (durationMs <= 0) return@onPreviewKeyEvent false
                when (evt.key) {
                    Key.DirectionLeft -> {
                        onSeek((positionMs - SEEK_STEP_MS).coerceAtLeast(0L)); true
                    }
                    Key.DirectionRight -> {
                        onSeek((positionMs + SEEK_STEP_MS).coerceAtMost(durationMs)); true
                    }
                    else -> false
                }
            }
            .focusable(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (focused) 6.dp else 3.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(if (focused) AppColors.Card.copy(alpha = 0.9f) else AppColors.Card),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction)
                    .fillMaxHeight()
                    .background(AppColors.AccentGreen),
            )
        }
        Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                fmt(positionMs),
                color = if (focused) AppColors.AccentGreen else AppColors.TextMuted,
                fontSize = 11.sp,
                fontWeight = if (focused) FontWeight.Bold else FontWeight.Normal,
            )
            Text(fmt(durationMs), color = AppColors.TextMuted, fontSize = 11.sp)
        }
    }
}

@Composable
private fun LyricPane(
    lyrics: List<com.walkman.tv.playback.LyricLine>,
    positionMs: Long,
    modifier: Modifier = Modifier,
) {
    if (lyrics.isEmpty()) {
        Box(modifier, contentAlignment = Alignment.Center) { Text("暂无歌词", color = AppColors.TextMuted) }
        return
    }
    // Compute active line on every call but only notify observers when the resulting index
    // actually changes — the LazyColumn items don't re-evaluate alpha/color/size on every
    // 1s position tick, only when the active line index advances.
    val active = LyricParser.activeIndex(positionMs / 1000.0, lyrics)
    val listState = rememberLazyListState()
    LaunchedEffect(active) {
        if (active >= 0) runCatching { listState.animateScrollToItem(active, scrollOffset = -160) }
    }
    LazyColumn(
        state = listState,
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 80.dp),
    ) {
        itemsIndexed(lyrics) { i, line ->
            val isActive = i == active
            // Lines further from the active line progressively fade out so the active
            // one reads as the focal point (matches the reference layout).
            val distance = if (active >= 0) abs(i - active) else 0
            val a = if (isActive) 1f else (1f - distance * 0.22f).coerceAtLeast(0.18f)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    line.text,
                    color = if (isActive) AppColors.LyricActive else AppColors.TextSecondary,
                    fontSize = if (isActive) 22.sp else 15.sp,
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.alpha(a),
                )
                line.translation?.let {
                    Text(
                        it,
                        color = if (isActive) AppColors.LyricActive.copy(alpha = 0.7f) else AppColors.TextMuted,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.alpha(a),
                    )
                }
            }
        }
    }
}

@Composable
private fun IconPill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    active: Boolean = false,
    large: Boolean = false,
    focusRequester: FocusRequester? = null,
    onClick: () -> Unit,
) {
    TvPill(
        onClick = onClick,
        shape = androidx.compose.foundation.shape.CircleShape,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(if (large) 14.dp else 10.dp),
        selected = active,
        focusRequester = focusRequester,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(if (large) 30.dp else 22.dp))
    }
}

private fun fmt(ms: Long): String {
    val s = (ms / 1000).toInt()
    return "%02d:%02d".format(s / 60, s % 60)
}

/**
 * Try requesting focus up to 5 times across ~250ms. Dialogs/drawers tear down asynchronously,
 * so a single delay(80) + requestFocus() commonly fires before the target composable has its
 * modifier re-attached, and the request silently fails. Looping until success is the canonical
 * Compose-for-TV workaround.
 */
private suspend fun tryRequestFocus(target: FocusRequester) {
    repeat(5) {
        kotlinx.coroutines.delay(50)
        if (runCatching { target.requestFocus() }.isSuccess) return
    }
}

@Composable
private fun MvQueueDrawer(
    visible: Boolean,
    queue: List<Track>,
    currentIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val firstFocus = remember { FocusRequester() }

    // When the drawer opens, scroll to the currently-playing item and focus it.
    LaunchedEffect(visible) {
        if (visible && queue.isNotEmpty()) {
            val target = currentIndex.coerceIn(0, queue.size - 1)
            runCatching { listState.scrollToItem(target) }
            delay(80)
            runCatching { firstFocus.requestFocus() }
        }
    }

    AnimatedVisibility(
        visible = visible,
        enter = slideInHorizontally(initialOffsetX = { it }),
        exit = slideOutHorizontally(targetOffsetX = { it }),
        modifier = modifier,
    ) {
        Box(modifier = Modifier.fillMaxHeight().width(360.dp).background(AppColors.BgPanel.copy(alpha = 0.95f))) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                Text(
                    "播放队列 (${queue.size})",
                    color = AppColors.TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(10.dp))
                LazyColumn(
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    itemsIndexed(queue, key = { _, t -> t.id }) { idx, track ->
                        val isCurrent = idx == currentIndex
                        QueueRow(
                            track = track,
                            index = idx,
                            isCurrent = isCurrent,
                            modifier = if (isCurrent) Modifier.focusRequester(firstFocus) else Modifier,
                            onClick = { onSelect(idx) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QueueRow(
    track: Track,
    index: Int,
    isCurrent: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    TvFocusable(onClick = onClick, modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            androidx.compose.foundation.layout.Box(
                modifier = Modifier.width(22.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (isCurrent) {
                    com.walkman.tv.ui.components.MiniWaveform(
                        modifier = Modifier.size(width = 18.dp, height = 14.dp),
                    )
                } else {
                    Text(
                        "${index + 1}",
                        color = AppColors.TextMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    track.name,
                    color = if (isCurrent) AppColors.AccentGreen else AppColors.TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    track.singer,
                    color = AppColors.TextMuted,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
