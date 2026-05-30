package com.walkman.tv.ui.recommend

import androidx.compose.animation.core.animateFloat
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.walkman.tv.playback.LyricParser
import com.walkman.tv.ui.NavSection
import com.walkman.tv.ui.appContainer
import com.walkman.tv.ui.components.Artwork
import com.walkman.tv.ui.components.TvFocusable
import com.walkman.tv.ui.components.TvPill
import com.walkman.tv.ui.theme.AppColors

@Composable
fun RecommendScreen(
    onNavigate: (NavSection) -> Unit,
    onOpenPlayer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.fillMaxSize().padding(vertical = 8.dp)) {
        NowPlayingPanel(onOpenPlayer = onOpenPlayer, modifier = Modifier.width(340.dp).fillMaxHeight())
        Spacer(Modifier.width(16.dp))
        RecommendGrid(onNavigate = onNavigate, modifier = Modifier.weight(1f).fillMaxHeight())
    }
}

@Composable
private fun NowPlayingPanel(onOpenPlayer: () -> Unit, modifier: Modifier = Modifier) {
    val controller = appContainer.playbackController
    val state by controller.state.collectAsState()
    val lyrics by controller.lyrics.collectAsState()
    val track = state.currentTrack

    Column(
        modifier = modifier.padding(horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Smaller cover (240dp instead of full panel width), centered.
        TvFocusable(
            onClick = onOpenPlayer,
            modifier = Modifier.size(240.dp),
            shape = RoundedCornerShape(14.dp),
        ) {
            Artwork(track?.picURL, modifier = Modifier.fillMaxSize(), shape = RoundedCornerShape(14.dp))
        }
        Spacer(Modifier.height(12.dp))
        if (track != null) {
            Text(track.name, color = AppColors.TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(track.singer, color = AppColors.TextSecondary, fontSize = 13.sp, maxLines = 1)
            Spacer(Modifier.height(8.dp))
            val active = LyricParser.activeIndex(state.positionMs / 1000.0, lyrics)
            val line = lyrics.getOrNull(active)?.text ?: if (state.resolving) "解析中…" else ""
            Text(line, color = AppColors.LyricIdle, fontSize = 13.sp, maxLines = 1)
        } else {
            Text("暂无播放，去推荐里点歌开始吧", color = AppColors.TextSecondary, fontSize = 14.sp)
        }
        // Two-line waveform between the lyric and the transport controls.
        // Only shown when a track is loaded, and constrained to the cover's width so it
        // doesn't extend past the left/right edges of the disc.
        if (track != null) {
            Spacer(Modifier.height(10.dp))
            MiniLineWaveform(
                isPlaying = state.isPlaying,
                modifier = Modifier.width(240.dp).height(24.dp),
            )
        }
        Spacer(Modifier.height(10.dp))
        // Controls centered.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircleControl(Icons.Filled.SkipPrevious) { controller.prev() }
            Spacer(Modifier.width(20.dp))
            CircleControl(if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow) { controller.togglePlay() }
            Spacer(Modifier.width(20.dp))
            CircleControl(Icons.Filled.SkipNext) { controller.next() }
        }
    }
}

/** Two flowing sine wave lines, ~24dp tall, for the recommend NowPlaying panel. */
@Composable
private fun MiniLineWaveform(isPlaying: Boolean, modifier: Modifier = Modifier) {
    val transition = androidx.compose.animation.core.rememberInfiniteTransition(label = "rec-wave")
    val time by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(durationMillis = 1_000_000, easing = androidx.compose.animation.core.LinearEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Restart,
        ),
        label = "rec-wave-time",
    )
    androidx.compose.foundation.Canvas(modifier = modifier) {
        val t = if (isPlaying) time.toDouble() else 0.0
        val midY = size.height / 2.0
        val W = size.width.toDouble()
        // Two layers tuned smaller than the player.
        val layers = listOf(
            Triple(0.80f, 110.0, 0.85f) to Pair(0.8f, 1.4f),  // (amp, wavelength, opacity) and (drift, lineWidthDp)
            Triple(0.50f, 75.0, 0.45f) to Pair(1.3f, 1.0f),
        )
        for ((spec, mods) in layers) {
            val (amp, wl, opacity) = spec
            val (drift, widthDp) = mods
            val phase = t * drift * 2.2
            val pulseBeat = 0.5 + 0.35 * kotlin.math.sin(t * 3.0)
            val ampPx = (amp * maxOf(0.2, pulseBeat)) * size.height / 2.0
            val path = androidx.compose.ui.graphics.Path()
            fun yAt(xx: Double): Float {
                val env = kotlin.math.sin(xx / W * Math.PI)
                return (midY + kotlin.math.sin(xx / wl * 2 * Math.PI + phase) * ampPx * env).toFloat()
            }
            path.moveTo(0f, yAt(0.0))
            var x = 0.0
            while (x <= W) {
                path.lineTo(x.toFloat(), yAt(x))
                x += 2.0
            }
            drawPath(
                path = path,
                brush = androidx.compose.ui.graphics.Brush.horizontalGradient(
                    colorStops = arrayOf(
                        0f to AppColors.AccentGreen.copy(alpha = 0f),
                        0.5f to AppColors.AccentGreen.copy(alpha = opacity),
                        1f to AppColors.AccentGreen.copy(alpha = 0f),
                    ),
                ),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = widthDp.dp.toPx()),
            )
        }
    }
}

@Composable
private fun CircleControl(icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    TvPill(
        onClick = onClick,
        shape = androidx.compose.foundation.shape.CircleShape,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(10.dp),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
    }
}

// Image URLs ported from the RN TV recommend page (Unsplash, small thumbs).
private object CardImages {
    const val GUESS = "https://images.unsplash.com/photo-1514525253161-7a46d19cd819?w=600&q=80"
    const val DAILY = "https://images.unsplash.com/photo-1470225620780-dba8ba36b745?w=600&q=80"
    const val CHART = "https://images.unsplash.com/photo-1493225255756-d9584f8606e9?w=600&q=80"
    const val NEW = "https://images.unsplash.com/photo-1619983081563-430f63602796?w=600&q=80"
    const val ALBUM = "https://images.unsplash.com/photo-1508700115892-45ecd05ae2ad?w=600&q=80"
    const val CLASSIC = "https://images.unsplash.com/photo-1514320291840-2e0a9bf2a9ae?w=600&q=80"
    const val DRIVE = "https://images.unsplash.com/photo-1525085475163-c65546867694?w=600&q=80"
    const val VINYL = "https://images.unsplash.com/photo-1605218427368-35b844d95791?w=600&q=80"
    const val PLAYED = "https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=200&q=60"
    const val LOVE = "https://images.unsplash.com/photo-1459749411175-04bf5292ceea?w=200&q=60"
}

@Composable
private fun RecommendGrid(onNavigate: (NavSection) -> Unit, modifier: Modifier = Modifier) {
    val played by appContainer.libraryStore.history.collectAsState()
    val loved by appContainer.libraryStore.love.collectAsState()

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(modifier = Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BigCard("猜你喜欢", CardImages.GUESS, Modifier.weight(1f)) { onNavigate(NavSection.Library) }
            BigCard("Daily 30", CardImages.DAILY, Modifier.weight(1f)) { onNavigate(NavSection.Songlist) }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SmallCard("排行榜", CardImages.CHART, Modifier.weight(1f)) { onNavigate(NavSection.Leaderboard) }
                SmallCard("新歌", CardImages.NEW, Modifier.weight(1f)) { onNavigate(NavSection.Songlist) }
            }
        }
        Row(modifier = Modifier.fillMaxWidth().height(72.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard("已播", played.tracks.size, CardImages.PLAYED, Modifier.weight(1f)) { onNavigate(NavSection.Library) }
            StatCard("收藏", loved.tracks.size, CardImages.LOVE, Modifier.weight(1f)) { onNavigate(NavSection.Library) }
        }
        Row(modifier = Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BigCard("新歌新碟", CardImages.ALBUM, Modifier.weight(1f)) { onNavigate(NavSection.Songlist) }
            BigCard("经典老歌", CardImages.CLASSIC, Modifier.weight(1f)) { onNavigate(NavSection.Songlist) }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SmallCard("车载热门", CardImages.DRIVE, Modifier.weight(1f)) { onNavigate(NavSection.Songlist) }
                SmallCard("黑胶专区", CardImages.VINYL, Modifier.weight(1f)) { onNavigate(NavSection.Songlist) }
            }
        }
    }
}

@Composable
private fun BigCard(title: String, picURL: String, modifier: Modifier, onClick: () -> Unit) {
    TvFocusable(onClick = onClick, modifier = modifier, shape = RoundedCornerShape(14.dp)) {
        Box(modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(14.dp))) {
            AsyncImage(
                model = picURL, contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            // Bottom-to-top dark overlay so the title is always readable.
            Box(
                modifier = Modifier.fillMaxSize().background(
                    Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))),
                ),
            )
            Text(
                title,
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.align(Alignment.BottomStart).padding(16.dp),
            )
        }
    }
}

@Composable
private fun SmallCard(title: String, picURL: String, modifier: Modifier, onClick: () -> Unit) {
    TvFocusable(onClick = onClick, modifier = modifier, shape = RoundedCornerShape(10.dp)) {
        Box(modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp))) {
            AsyncImage(
                model = picURL, contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)))
            Text(
                title,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.align(Alignment.Center),
            )
        }
    }
}

@Composable
private fun StatCard(title: String, count: Int, picURL: String, modifier: Modifier, onClick: () -> Unit) {
    TvFocusable(onClick = onClick, modifier = modifier, shape = RoundedCornerShape(36.dp)) {
        Row(
            modifier = Modifier.fillMaxSize().padding(start = 8.dp, end = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Small round thumbnail on the left.
            Box(
                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(50)),
            ) {
                AsyncImage(
                    model = picURL,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
            Spacer(Modifier.width(12.dp))
            Text(title, color = AppColors.TextSecondary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text("$count", color = AppColors.TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
    }
}
