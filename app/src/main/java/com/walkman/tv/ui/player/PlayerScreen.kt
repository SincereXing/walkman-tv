package com.walkman.tv.ui.player

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
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
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.abs
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
    val settings by appContainer.settingsStore.settings.collectAsState()
    val lyricSize = settings.lyricSize
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

    Box(modifier = modifier.fillMaxSize().background(AppColors.BgDeep)) {
        // Frosted-glass backdrop: the current cover blurred + heavily darkened so it tints
        // the whole player without competing with the vinyl / lyrics / transport bar.
        CoverBackdrop(picURL = track?.picURL)

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
                        lyricSize = lyricSize,
                        modifier = Modifier.fillMaxWidth().weight(1f),
                    )
                }
                Spacer(Modifier.width(32.dp))
                // Vinyl scales with the available vertical space so it grows on larger screens.
                // Clamped so it stays sensible on very short / very tall layouts.
                androidx.compose.foundation.layout.BoxWithConstraints(
                    modifier = Modifier.fillMaxHeight(),
                    contentAlignment = Alignment.Center,
                ) {
                    val vinylSize = maxHeight.coerceIn(300.dp, 560.dp)
                    VinylDisc(track.picURL, state.isPlaying, size = vinylSize)
                }
            }

            // Bottom: waveform + progress bar + transport row.
            // (Status line removed — quality is shown in the TransportBar Hi-Res pill below;
            // the source / origin / message text is no longer surfaced to the player UI.)
            Spacer(Modifier.height(10.dp))
            val audioLevel by controller.audioLevel.collectAsState()
            com.walkman.tv.ui.components.Waveform(
                isPlaying = state.isPlaying,
                level = audioLevel,
                modifier = Modifier.fillMaxWidth().height(40.dp),
            )
            Spacer(Modifier.height(6.dp))
            ProgressBar(state.positionMs, state.durationMs, onSeek = { controller.seekTo(it) })
            Spacer(Modifier.height(14.dp))
            val audioSpec by controller.audioSpec.collectAsState()
            TransportBar(
                state = state,
                love = love,
                audioSpec = audioSpec,
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
private fun VinylDisc(picURL: String?, isPlaying: Boolean, size: Dp = 360.dp) {
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
    // All inner elements scale off [size] (the halo) so the whole disc grows with the screen.
    // Ratios preserve the original 360/300/220/14 design.
    val record = size * (300f / 360f)
    val art = size * (220f / 360f)
    val spindle = size * (14f / 360f)
    val circle = androidx.compose.foundation.shape.CircleShape
    Box(modifier = Modifier.size(size), contentAlignment = Alignment.Center) {
        // 1) Outer green halo (decorative glow, doesn't rotate).
        Box(
            modifier = Modifier
                .size(size)
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
                .size(record)
                .graphicsLayer { rotationZ = rotation.value },
            contentAlignment = Alignment.Center,
        ) {
            VinylGrooves(modifier = Modifier.size(record))
            // Album art clipped to circle, sits flush with the inner groove edge.
            Box(
                modifier = Modifier
                    .size(art)
                    .clip(circle)
                    .background(AppColors.BgDeep),
            ) {
                Artwork(picURL, modifier = Modifier.fillMaxSize(), shape = circle)
            }
        }
        // 3) Center spindle hole (doesn't rotate — stays sharp).
        Box(modifier = Modifier.size(spindle).clip(circle).background(Color.Black))
    }
}

/** Vinyl groove texture: dark green base, radial depth gradient, concentric groove rings,
 *  bright outer rim. Drawn once into a Canvas (then rotated by the parent graphicsLayer). */
@Composable
private fun VinylGrooves(modifier: Modifier) {
    androidx.compose.foundation.Canvas(modifier = modifier) {
        val center = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height / 2f)
        val outerRadius = size.minDimension / 2f
        // The grooves sit between the album-art edge and the outer rim. Proportional to the
        // canvas (110/150 of the radius — matches the original 300dp record / 220dp art) so it
        // scales with the disc on larger screens.
        val innerRadius = outerRadius * (110f / 150f)

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

// ============== Transport ======================================================================

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
private fun TransportBar(
    state: com.walkman.tv.playback.PlaybackState,
    love: com.walkman.tv.data.model.Playlist,
    audioSpec: com.walkman.tv.playback.AudioSpec?,
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
    // Per-pill explicit focus neighbours. Geometric focus search and group-level
    // `focusProperties.exit` both failed to reliably hand off across the centre→side gaps on
    // 1080p TVs, so we wire the edge pills directly:
    //   Tune   : Left  → Cancel (stays put so focus never falls off the left edge)
    //   HiRes  : Right → Repeat (jump the gap between left group and centre group)
    //   Repeat : Left  → HiRes when present (else default search finds Tune)
    //   Heart  : Right → MV    (jump the gap between centre group and right group)
    //   MV     : Left  → Heart (mirror back across the right gap)
    //   Queue  : Right → Cancel (right-edge mirror of the Tune trap)
    val qualityFocus = remember { FocusRequester() }
    val repeatFocus = remember { FocusRequester() }
    val mvFocus = remember { FocusRequester() }
    val faveFocus = remember { FocusRequester() }
    val hasQuality = state.quality != null

    Box(modifier = Modifier.fillMaxWidth()) {
        // Left group anchored to CenterStart.
        Row(
            modifier = Modifier.align(Alignment.CenterStart),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            IconPill(
                Icons.Filled.Tune,
                onClick = onTuneClick,
                modifier = Modifier.focusProperties { left = FocusRequester.Cancel },
            )
            // Badge shows the *measured* tier (claimed clamped down by AudioSpec) so a 16/44
            // FLAC arriving from a hires request reads as "SQ", not "Hi-Res". Inline caption
            // (e.g. "FLAC 24bit/192kHz") appears the moment probing completes.
            val displayed = com.walkman.tv.playback.displayQuality(state.quality, audioSpec)
            displayed?.let { q ->
                TvPill(
                    onClick = { /* future: open a quality picker */ },
                    selected = true,
                    focusRequester = qualityFocus,
                    modifier = Modifier.focusProperties { right = repeatFocus },
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                ) {
                    Text(q.badgeLabel, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                audioSpec?.let { spec ->
                    Text(
                        spec.displayText,
                        color = AppColors.TextMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                    )
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
            IconPill(
                repeatIcon,
                active = true,
                focusRequester = repeatFocus,
                onClick = onCycleRepeat,
                modifier = if (hasQuality) Modifier.focusProperties { left = qualityFocus } else Modifier,
            )
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
                focusRequester = faveFocus,
                onClick = onToggleFav,
                modifier = Modifier.focusProperties { right = mvFocus },
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
                focusRequester = mvFocus,
                modifier = Modifier.focusProperties { left = faveFocus },
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Text("MV", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            IconPill(
                Icons.AutoMirrored.Filled.QueueMusic,
                onClick = onShowQueue,
                modifier = Modifier.focusProperties { right = FocusRequester.Cancel },
            )
        }
    }
}

// Quick tap (held ≤ this) = a discrete nudge; anything longer is treated as a continuous scrub.
private const val SCRUB_TAP_MS = 220L
private const val SCRUB_TAP_STEP_MS = 5_000L
// Continuous-scrub speed in ms-of-media per real second, ramping from BASE up to MAX so a short
// hold seeks precisely while a long hold sweeps across the track.
private const val SCRUB_BASE_PER_SEC = 6_000f
private const val SCRUB_MAX_PER_SEC = 60_000f
private const val SCRUB_ACCEL_PER_SEC2 = 40_000f
// Safety net for a missed KeyUp: stop scrubbing if no key signal arrives for this long.
private const val SCRUB_RELEASE_TIMEOUT_MS = 1_000L

/**
 * D-pad-seekable progress bar. When focused (D-pad up from the play button), holding left/right
 * scrubs **smoothly and proportionally to how long you hold** — the bar previews the new position
 * frame-by-frame and only commits ONE real player seek on release, so there's no per-step
 * re-buffer stutter. A quick tap still does a discrete ±5s nudge.
 *
 * Release is detected via KeyUp (primary) plus a repeat-timeout watchdog (backstop for a dropped
 * KeyUp). Visual focus cue: bar thickens (3 -> 6dp) and the elapsed-time label turns green.
 */
@Composable
private fun ProgressBar(positionMs: Long, durationMs: Long, onSeek: (Long) -> Unit) {
    var focused by remember { mutableStateOf(false) }
    // Scrub state. dir: -1 rewind / 0 idle / +1 forward. previewMs is what the bar shows while
    // scrubbing; startPos/startAt anchor tap-vs-hold detection + the acceleration ramp.
    var scrubDir by remember { mutableStateOf(0) }
    var previewMs by remember { mutableStateOf(0L) }
    var scrubStartPos by remember { mutableStateOf(0L) }
    var scrubStartAt by remember { mutableStateOf(0L) }
    var lastKeyAt by remember { mutableStateOf(0L) }

    fun commit() {
        if (scrubDir == 0) return
        val held = android.os.SystemClock.uptimeMillis() - scrubStartAt
        val target = if (held <= SCRUB_TAP_MS) {
            (scrubStartPos + scrubDir * SCRUB_TAP_STEP_MS).coerceIn(0L, durationMs)
        } else {
            previewMs.coerceIn(0L, durationMs)
        }
        onSeek(target)
        scrubDir = 0
    }

    // Frame-paced scrub loop: advances previewMs by real elapsed time while a direction is held.
    LaunchedEffect(scrubDir) {
        if (scrubDir == 0) return@LaunchedEffect
        var lastT = android.os.SystemClock.uptimeMillis()
        while (scrubDir != 0) {
            withFrameNanos { } // pace to the display refresh
            val now = android.os.SystemClock.uptimeMillis()
            val dt = (now - lastT).coerceAtLeast(0L)
            lastT = now
            val heldSec = (now - scrubStartAt) / 1000f
            val speed = (SCRUB_BASE_PER_SEC + SCRUB_ACCEL_PER_SEC2 * heldSec)
                .coerceAtMost(SCRUB_MAX_PER_SEC)
            previewMs = (previewMs + scrubDir * speed * (dt / 1000f)).toLong()
                .coerceIn(0L, durationMs)
            // Backstop: KeyUp was dropped — commit and stop.
            if (now - lastKeyAt > SCRUB_RELEASE_TIMEOUT_MS) commit()
        }
    }

    val shownMs = if (scrubDir != 0) previewMs else positionMs
    val fraction = if (durationMs > 0) (shownMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { fs ->
                focused = fs.isFocused
                if (!fs.isFocused && scrubDir != 0) commit() // committed if focus leaves mid-scrub
            }
            .onPreviewKeyEvent { evt ->
                if (durationMs <= 0) return@onPreviewKeyEvent false
                val dir = when (evt.key) {
                    Key.DirectionLeft -> -1
                    Key.DirectionRight -> 1
                    else -> return@onPreviewKeyEvent false
                }
                when (evt.type) {
                    KeyEventType.KeyDown -> {
                        lastKeyAt = android.os.SystemClock.uptimeMillis()
                        if (scrubDir == 0) {
                            scrubStartPos = positionMs
                            previewMs = positionMs
                            scrubStartAt = lastKeyAt
                        }
                        scrubDir = dir
                        true
                    }
                    KeyEventType.KeyUp -> {
                        commit(); true
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
                fmt(shownMs),
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
    lyricSize: com.walkman.tv.data.store.LyricSize,
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
                    fontSize = if (isActive) lyricSize.activeSp.sp else lyricSize.inactiveSp.sp,
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.alpha(a),
                )
                line.translation?.let {
                    Text(
                        it,
                        color = if (isActive) AppColors.LyricActive.copy(alpha = 0.7f) else AppColors.TextMuted,
                        fontSize = lyricSize.translationSp.sp,
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
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    TvPill(
        onClick = onClick,
        modifier = modifier,
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

// ============== Cover backdrop =================================================================

/**
 * Frosted-glass background for the full-screen player. Three layers stacked over [AppColors.BgDeep]:
 *
 *  1. Current cover loaded via Coil with [coil.transform.BlurTransformation] — radius 25 (the
 *     RenderScript hard cap), sampling 2 (downscale by 2 before blurring) for speed. Runs on
 *     RenderScript so it works back to API 21 — including the 32-bit ARM TVs on Android 10
 *     where [Modifier.blur] (API 31+) isn't available.
 *  2. A heavy black wash (alpha ≈ 0.55) — pulls the blurred image down so the player's
 *     foreground content (vinyl, lyrics, transport bar) keeps its contrast.
 *  3. A top + bottom vertical gradient toward [AppColors.BgDeep] so the corners read clean
 *     for the TopNav-adjacent area and the transport row.
 *
 * Falls back to the previous BgPanel→BgDeep gradient when no cover is available.
 */
@Composable
private fun CoverBackdrop(picURL: String?) {
    Box(modifier = Modifier.fillMaxSize()) {
        if (!picURL.isNullOrBlank()) {
            val context = androidx.compose.ui.platform.LocalContext.current
            coil.compose.AsyncImage(
                model = coil.request.ImageRequest.Builder(context)
                    .data(picURL)
                    .transformations(com.walkman.tv.ui.components.BlurTransformation(context, radius = 25f, sampling = 2f))
                    .crossfade(400)
                    .build(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            )
            // Darken so the foreground content keeps WCAG-ish contrast.
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)))
            // Edge gradient — keeps top + bottom strips close to pure BgDeep so transport / text
            // never sit on a high-saturation patch of the blurred cover.
            Box(
                modifier = Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        0f to AppColors.BgDeep.copy(alpha = 0.55f),
                        0.28f to Color.Transparent,
                        0.72f to Color.Transparent,
                        1f to AppColors.BgDeep.copy(alpha = 0.72f),
                    ),
                ),
            )
        } else {
            // No cover yet — original look stays.
            Box(
                modifier = Modifier.fillMaxSize().background(
                    Brush.verticalGradient(listOf(AppColors.BgPanel, AppColors.BgDeep)),
                ),
            )
        }
    }
}
