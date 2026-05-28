package cn.toside.music.mobile.ui.player

import androidx.activity.compose.BackHandler
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import cn.toside.music.mobile.playback.LyricParser
import cn.toside.music.mobile.playback.RepeatMode
import cn.toside.music.mobile.ui.appContainer
import cn.toside.music.mobile.ui.components.Artwork
import cn.toside.music.mobile.ui.components.TvPill
import cn.toside.music.mobile.ui.theme.AppColors
import kotlinx.coroutines.launch

@Composable
fun PlayerScreen(onClose: () -> Unit, modifier: Modifier = Modifier) {
    val controller = appContainer.playbackController
    val state by controller.state.collectAsState()
    val lyrics by controller.lyrics.collectAsState()
    val love by appContainer.libraryStore.love.collectAsState()
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf<String?>(null) }

    val track = state.currentTrack

    BackHandler(enabled = true) {
        if (state.isMv) controller.exitMv() else onClose()
    }

    if (state.isMv) {
        Box(modifier = modifier.fillMaxSize().background(AppColors.BgDeep)) {
            AndroidView(
                factory = { ctx -> PlayerView(ctx).apply { player = controller.player; useController = true } },
                modifier = Modifier.fillMaxSize(),
            )
            TvPill(onClick = { controller.exitMv() }, modifier = Modifier.padding(16.dp)) { Text("退出 MV") }
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

        Row(modifier = Modifier.fillMaxSize().padding(40.dp)) {
            // Left: cover + meta + transport
            Column(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                verticalArrangement = Arrangement.Center,
            ) {
                Artwork(
                    track.picURL,
                    modifier = Modifier.fillMaxWidth(0.7f).aspectRatio(1f),
                    shape = RoundedCornerShape(16.dp),
                )
                Spacer(Modifier.height(20.dp))
                Text(track.name, color = AppColors.TextPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold, maxLines = 2)
                Text(track.singer, color = AppColors.TextSecondary, fontSize = 15.sp)
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    state.quality?.let { Text(it.displayName, color = AppColors.AccentGreen, fontSize = 12.sp) }
                    state.originLabel?.let { Text(it, color = AppColors.TextMuted, fontSize = 12.sp) }
                }
                state.warning?.let { Text(it, color = AppColors.SourceMg, fontSize = 12.sp) }
                if (state.resolving) Text("解析中…", color = AppColors.TextMuted, fontSize = 12.sp)
                state.error?.let { Text(it, color = AppColors.SourceWy, fontSize = 12.sp) }
                message?.let { Text(it, color = AppColors.TextMuted, fontSize = 12.sp) }

                Spacer(Modifier.height(16.dp))
                ProgressBar(state.positionMs, state.durationMs)
                Spacer(Modifier.height(16.dp))

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    val repeatIcon = if (state.repeatMode == RepeatMode.ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat
                    IconPill(repeatIcon, active = state.repeatMode != RepeatMode.OFF) {
                        controller.setRepeatMode(
                            when (state.repeatMode) {
                                RepeatMode.OFF -> RepeatMode.ALL
                                RepeatMode.ALL -> RepeatMode.ONE
                                RepeatMode.ONE -> RepeatMode.OFF
                            },
                        )
                    }
                    IconPill(Icons.Filled.SkipPrevious) { controller.prev() }
                    IconPill(if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, large = true) { controller.togglePlay() }
                    IconPill(Icons.Filled.SkipNext) { controller.next() }
                    IconPill(Icons.Filled.Shuffle, active = state.shuffle) { controller.toggleShuffle() }
                    val faved = love.tracks.any { it.id == track.id }
                    IconPill(if (faved) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, active = faved) {
                        scope.launch { appContainer.libraryStore.toggleFavorite(track) }
                    }
                    IconPill(Icons.Filled.Movie) {
                        scope.launch {
                            message = "正在获取 MV…"
                            val info = runCatching { appContainer.mvResolver.getMvUrl(track) }.getOrNull()
                            val url = info?.bestUrl()
                            if (url != null) { message = null; controller.playMvUrl(url) } else message = "无可用 MV"
                        }
                    }
                }
            }

            Spacer(Modifier.width(32.dp))

            // Right: lyrics
            LyricPane(lyrics, state.positionMs, modifier = Modifier.weight(1f).fillMaxHeight())
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
private fun LyricPane(lyrics: List<cn.toside.music.mobile.playback.LyricLine>, positionMs: Long, modifier: Modifier = Modifier) {
    if (lyrics.isEmpty()) {
        Box(modifier, contentAlignment = Alignment.Center) { Text("暂无歌词", color = AppColors.TextMuted) }
        return
    }
    val active = LyricParser.activeIndex(positionMs / 1000.0, lyrics)
    val listState = rememberLazyListState()
    LaunchedEffect(active) {
        if (active >= 0) runCatching { listState.animateScrollToItem(active, scrollOffset = -240) }
    }
    LazyColumn(
        state = listState,
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        itemsIndexed(lyrics) { i, line ->
            val isActive = i == active
            Column {
                Text(
                    line.text,
                    color = if (isActive) AppColors.LyricActive else AppColors.LyricIdle,
                    fontSize = if (isActive) 18.sp else 15.sp,
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                    textAlign = TextAlign.Start,
                )
                line.translation?.let {
                    Text(it, color = if (isActive) AppColors.LyricActive.copy(alpha = 0.8f) else AppColors.TextMuted, fontSize = 12.sp)
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
