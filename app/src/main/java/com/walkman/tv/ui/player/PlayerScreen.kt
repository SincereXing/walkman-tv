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
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
    var message by remember { mutableStateOf<String?>(null) }
    var showMvQueue by remember { mutableStateOf(false) }

    val track = state.currentTrack

    BackHandler(enabled = true) {
        when {
            showMvQueue -> showMvQueue = false
            state.isMv -> controller.exitMv()
            else -> onClose()
        }
    }

    // While in MV, listen for the remote's Menu key to toggle the queue drawer.
    LaunchedEffect(state.isMv) {
        if (!state.isMv) {
            showMvQueue = false
            return@LaunchedEffect
        }
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

            // Status line — quality / origin / warning / resolve / error / transient message.
            Spacer(Modifier.height(10.dp))
            StatusLine(state, message)

            // Bottom: waveform + progress bar + transport row.
            Spacer(Modifier.height(8.dp))
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
                onToggleShuffle = { controller.toggleShuffle() },
                onToggleFav = { scope.launch { appContainer.libraryStore.toggleFavorite(track) } },
                onMv = {
                    scope.launch {
                        message = "正在获取 MV…"
                        val info = runCatching { appContainer.mvResolver.getMvUrl(track) }.getOrNull()
                        val url = info?.bestUrl()
                        if (url != null) {
                            message = null; controller.playMvUrl(url)
                        } else {
                            message = "无可用 MV"
                        }
                    }
                },
            )
        }
    }
}

// ============== Vinyl disc =====================================================================

@Composable
private fun VinylDisc(picURL: String?, isPlaying: Boolean) {
    val rotation = remember { Animatable(0f) }
    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            // 30-second revolution. Keep going from the current angle when un-paused.
            while (true) {
                rotation.animateTo(
                    rotation.value + 360f,
                    animationSpec = tween(durationMillis = 30_000, easing = LinearEasing),
                )
            }
        }
    }
    Box(modifier = Modifier.size(320.dp), contentAlignment = Alignment.Center) {
        // Outer green halo.
        Box(
            modifier = Modifier
                .size(320.dp)
                .clip(androidx.compose.foundation.shape.CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            AppColors.AccentGreen,
                            AppColors.AccentGreen.copy(alpha = 0.6f),
                            AppColors.AccentGreen.copy(alpha = 0.0f),
                        ),
                    ),
                ),
        )
        // Album art clipped to circle, rotated.
        Box(
            modifier = Modifier
                .size(240.dp)
                .rotate(rotation.value)
                .clip(androidx.compose.foundation.shape.CircleShape)
                .background(AppColors.BgDeep),
        ) {
            Artwork(
                picURL,
                modifier = Modifier.fillMaxSize(),
                shape = androidx.compose.foundation.shape.CircleShape,
            )
        }
        // Center spindle hole.
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(androidx.compose.foundation.shape.CircleShape)
                .background(Color.Black),
        )
    }
}

// ============== Waveform =======================================================================

@Composable
private fun Waveform(isPlaying: Boolean, modifier: Modifier = Modifier) {
    val bars = 36
    val transition = rememberInfiniteTransition(label = "wave")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = LinearEasing),
            repeatMode = AnimRepeatMode.Restart,
        ),
        label = "wave-phase",
    )
    Canvas(modifier = modifier) {
        val totalW = size.width
        val barWidth = totalW / bars * 0.55f
        val gap = (totalW - barWidth * bars) / (bars - 1)
        val centerY = size.height / 2
        val maxH = size.height * 0.95f
        val minH = size.height * 0.15f
        for (i in 0 until bars) {
            val x = i * (barWidth + gap)
            val v = if (isPlaying) {
                val freq = 1.0f + (i % 5) * 0.45f
                val amp = 0.45f + 0.45f * (i.toFloat() / bars)
                ((sin(phase * freq + i * 0.6f) + 1f) / 2f * amp).coerceIn(0f, 1f)
            } else {
                0.08f
            }
            val h = (minH + (maxH - minH) * v).coerceAtLeast(2.dp.toPx())
            drawRoundRect(
                color = AppColors.AccentGreen.copy(alpha = 0.85f),
                topLeft = Offset(x, centerY - h / 2),
                size = Size(barWidth, h),
                cornerRadius = CornerRadius(barWidth / 2, barWidth / 2),
            )
        }
    }
}

// ============== Status + transport =============================================================

@Composable
private fun StatusLine(state: com.walkman.tv.playback.PlaybackState, message: String?) {
    val parts = buildList {
        state.quality?.displayName?.let { add(it to AppColors.AccentGreen) }
        state.originLabel?.let { add(it to AppColors.TextMuted) }
        state.warning?.let { add(it to AppColors.SourceMg) }
        if (state.resolving) add("解析中…" to AppColors.TextMuted)
        state.error?.let { add(it to AppColors.SourceWy) }
        message?.let { add(it to AppColors.TextMuted) }
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        parts.forEach { (txt, color) ->
            Text(txt, color = color, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun TransportBar(
    state: com.walkman.tv.playback.PlaybackState,
    love: com.walkman.tv.data.model.Playlist,
    onTogglePlay: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onCycleRepeat: () -> Unit,
    onToggleShuffle: () -> Unit,
    onToggleFav: () -> Unit,
    onMv: () -> Unit,
) {
    val track = state.currentTrack
    val faved = track?.let { t -> love.tracks.any { it.id == t.id } } ?: false
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Left group: source chip + quality
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            track?.let { com.walkman.tv.ui.components.SourceChip(it.source) }
            state.quality?.let {
                Text(
                    it.displayName,
                    color = AppColors.TextMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        Spacer(modifier = Modifier.weight(1f))
        // Center group: repeat, prev, PLAY (large), next, shuffle
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            val repeatIcon = if (state.repeatMode == RepeatMode.ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat
            IconPill(repeatIcon, active = state.repeatMode != RepeatMode.OFF, onClick = onCycleRepeat)
            IconPill(Icons.Filled.SkipPrevious, onClick = onPrev)
            IconPill(
                if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                large = true,
                onClick = onTogglePlay,
            )
            IconPill(Icons.Filled.SkipNext, onClick = onNext)
            IconPill(Icons.Filled.Shuffle, active = state.shuffle, onClick = onToggleShuffle)
        }
        Spacer(modifier = Modifier.weight(1f))
        // Right group: heart + MV
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconPill(
                if (faved) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                active = faved,
                onClick = onToggleFav,
            )
            IconPill(Icons.Filled.Movie, onClick = onMv)
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
    onClick: () -> Unit,
) {
    TvPill(
        onClick = onClick,
        shape = androidx.compose.foundation.shape.CircleShape,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(if (large) 14.dp else 10.dp),
        selected = active,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(if (large) 30.dp else 22.dp))
    }
}

private fun fmt(ms: Long): String {
    val s = (ms / 1000).toInt()
    return "%02d:%02d".format(s / 60, s % 60)
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
