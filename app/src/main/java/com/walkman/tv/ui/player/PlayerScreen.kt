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

    // Park focus on the central play button at moments where the previously focused element is
    // gone. We deliberately do NOT key on track.id — that would yank focus back to play on every
    // track change, which is what made the player feel 'dead' after one operation.
    //
    // Each of these uses tryRequestFocus() (retries for ~250ms) because dialogs/drawers tear
    // down asynchronously: a single delay(80) often races the recomposition and silently fails.
    LaunchedEffect(Unit) { tryRequestFocus(playFocus) }
    LaunchedEffect(state.isMv) {
        // MV's AndroidView PlayerView swallows focus; reclaim it when we come back to audio.
        if (!state.isMv) tryRequestFocus(playFocus)
    }
    LaunchedEffect(showMvQueue) {
        // The drawer item that was focused just disappeared.
        if (!showMvQueue) tryRequestFocus(playFocus)
    }
    LaunchedEffect(showEqDialog) {
        // Same story for the EQ dialog — '完成' is gone, focus needs a new home.
        if (!showEqDialog) tryRequestFocus(playFocus)
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
            ProgressBar(state.positionMs, state.durationMs)
            Spacer(Modifier.height(14.dp))
            TransportBar(
                state = state,
                love = love,
                onTogglePlay = { controller.togglePlay() },
                onPrev = { controller.prev() },
                onNext = { controller.next() },
                onCycleRepeat = {
                    controller.setRepeatMode(
                        when (state.repeatMode) {
                            RepeatMode.OFF -> RepeatMode.ALL
                            RepeatMode.ALL -> RepeatMode.ONE
                            RepeatMode.ONE -> RepeatMode.OFF
                        },
                    )
                },
                onToggleFav = { scope.launch { appContainer.libraryStore.toggleFavorite(track) } },
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
    }
}

@Composable
private fun EqualizerDialog(onDismiss: () -> Unit) {
    val eq = appContainer.equalizerManager
    val presets = remember { eq.presets }
    var selected by remember { mutableStateOf(eq.currentPresetIndex) }
    val closeFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { closeFocus.requestFocus() } }

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
                            modifier = Modifier.fillMaxWidth(),
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
            Spacer(Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TvPill(
                    onClick = onDismiss,
                    selected = true,
                    focusRequester = closeFocus,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 24.dp, vertical = 8.dp),
                ) {
                    Text("完成", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
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

/** One sine-wave layer: independent amplitude / wavelength / drift / pulse / opacity. */
private data class WaveLayer(
    val amp: Float, val wl: Float, val drift: Float,
    val pulse: Float, val pPhase: Float,
    val opacity: Float, val widthDp: Float,
)

private val waveLayers = listOf(
    // Front line: brighter / thicker.
    WaveLayer(0.85f, 185f, 0.90f, 2.8f, 0.0f, 0.85f, 1.4f),
    // Back line: softer / shorter wavelength for a subtle counter-rhythm.
    WaveLayer(0.55f, 130f, 1.45f, 3.8f, 1.6f, 0.45f, 1.1f),
)

/**
 * Three overlapping horizontal sine wave lines that drift and pulse — ported from iOS AudioWave.
 * Each line is stroked with a gradient (transparent at edges, opaque in the middle) so the bands
 * converge at the sides. Drifts while playing, freezes at t=0 when paused.
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
            // Amplitude "beats": two summed sines with a wide swing -> snappy, music-like rise/fall.
            val beat = 0.5 +
                0.38 * sin(t * w.pulse + w.pPhase) +
                0.22 * sin(t * w.pulse * 1.9 + w.pPhase * 1.7)
            val pulse = maxOf(0.08, beat)
            val amp = minOf(w.amp * pulse, 0.92) * size.height / 2.0
            val phase = t * w.drift * 2.2

            val path = Path()
            fun yAt(xx: Double): Float {
                // 0 at both ends, 1 in the middle — all lines converge at the edges.
                val env = sin(xx / width * PI)
                return (midY + sin(xx / w.wl * 2 * PI + phase) * amp * env).toFloat()
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
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Left: EQ-style icon + Hi-Res pill (mirrors the reference layout).
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconPill(Icons.Filled.Tune, onClick = onTuneClick)
            state.quality?.let {
                // Selected = true gives the AccentGreenDim background + green text + thin green
                // outline — same passive-active look as the reference 'Hi-Res ▼' pill.
                TvPill(
                    onClick = { /* future: open a quality picker */ },
                    selected = true,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                ) {
                    Text(it.displayName, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        Spacer(modifier = Modifier.weight(1f))
        // Center: repeat, prev, PLAY (large green), next, heart.
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            val repeatIcon = if (state.repeatMode == RepeatMode.ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat
            IconPill(repeatIcon, active = state.repeatMode != RepeatMode.OFF, onClick = onCycleRepeat)
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
        Spacer(modifier = Modifier.weight(1f))
        // Right: MV pill + playlist hamburger.
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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

@Composable
private fun ProgressBar(positionMs: Long, durationMs: Long) {
    val fraction = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    Column {
        Box(modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)).background(AppColors.Card)) {
            Box(modifier = Modifier.fillMaxWidth(fraction).fillMaxHeight().background(AppColors.AccentGreen))
        }
        Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(fmt(positionMs), color = AppColors.TextMuted, fontSize = 11.sp)
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
            Text(
                "${index + 1}",
                color = if (isCurrent) AppColors.AccentGreen else AppColors.TextMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.width(22.dp),
            )
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
