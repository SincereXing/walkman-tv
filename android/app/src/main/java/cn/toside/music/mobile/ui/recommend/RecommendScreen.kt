package cn.toside.music.mobile.ui.recommend

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import cn.toside.music.mobile.playback.LyricParser
import cn.toside.music.mobile.ui.NavSection
import cn.toside.music.mobile.ui.appContainer
import cn.toside.music.mobile.ui.components.Artwork
import cn.toside.music.mobile.ui.components.TvFocusable
import cn.toside.music.mobile.ui.components.TvPill
import cn.toside.music.mobile.ui.theme.AppColors

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

    Column(modifier = modifier.padding(horizontal = 8.dp)) {
        TvFocusable(onClick = onOpenPlayer, modifier = Modifier.fillMaxWidth().aspectRatio(1f), shape = RoundedCornerShape(14.dp)) {
            Artwork(track?.picURL, modifier = Modifier.fillMaxSize(), shape = RoundedCornerShape(14.dp))
        }
        Spacer(Modifier.height(10.dp))
        if (track != null) {
            Text(track.name, color = AppColors.TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 2)
            Text(track.singer, color = AppColors.TextSecondary, fontSize = 13.sp, maxLines = 1)
            Spacer(Modifier.height(6.dp))
            val active = LyricParser.activeIndex(state.positionMs / 1000.0, lyrics)
            val line = lyrics.getOrNull(active)?.text ?: if (state.resolving) "解析中…" else ""
            Text(line, color = AppColors.LyricIdle, fontSize = 13.sp, maxLines = 2)
        } else {
            Text("暂无播放，去推荐里点歌开始吧", color = AppColors.TextSecondary, fontSize = 14.sp)
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            CircleControl(Icons.Filled.SkipPrevious) { controller.prev() }
            CircleControl(if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow) { controller.togglePlay() }
            CircleControl(Icons.Filled.SkipNext) { controller.next() }
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

@Composable
private fun RecommendGrid(onNavigate: (NavSection) -> Unit, modifier: Modifier = Modifier) {
    val played by appContainer.libraryStore.history.collectAsState()
    val loved by appContainer.libraryStore.love.collectAsState()

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(modifier = Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BigCard("猜你喜欢", listOf(Color(0xFF3B2A5A), Color(0xFF7A4DBF)), Modifier.weight(1f)) { onNavigate(NavSection.Library) }
            BigCard("Daily 30", listOf(Color(0xFF1E3A5F), Color(0xFF2E7DBF)), Modifier.weight(1f)) { onNavigate(NavSection.Songlist) }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SmallCard("排行榜", listOf(Color(0xFF5A2A2A), Color(0xFFBF4D4D)), Modifier.weight(1f)) { onNavigate(NavSection.Leaderboard) }
                SmallCard("新歌", listOf(Color(0xFF2A5A3A), Color(0xFF4DBF7A)), Modifier.weight(1f)) { onNavigate(NavSection.Songlist) }
            }
        }
        Row(modifier = Modifier.fillMaxWidth().height(72.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard("已播", played.tracks.size, Modifier.weight(1f)) { onNavigate(NavSection.Library) }
            StatCard("收藏", loved.tracks.size, Modifier.weight(1f)) { onNavigate(NavSection.Library) }
        }
        Row(modifier = Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BigCard("新歌新碟", listOf(Color(0xFF4A3A1E), Color(0xFFBF8A2E)), Modifier.weight(1f)) { onNavigate(NavSection.Songlist) }
            BigCard("经典老歌", listOf(Color(0xFF2A3A4A), Color(0xFF4D7A9F)), Modifier.weight(1f)) { onNavigate(NavSection.Songlist) }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SmallCard("车载热门", listOf(Color(0xFF3A2A4A), Color(0xFF7A4DBF)), Modifier.weight(1f)) { onNavigate(NavSection.Songlist) }
                SmallCard("黑胶专区", listOf(Color(0xFF1A331A), Color(0xFF4ADE80)), Modifier.weight(1f)) { onNavigate(NavSection.Songlist) }
            }
        }
    }
}

@Composable
private fun BigCard(title: String, gradient: List<Color>, modifier: Modifier, onClick: () -> Unit) {
    TvFocusable(onClick = onClick, modifier = modifier, shape = RoundedCornerShape(14.dp)) {
        Box(
            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(14.dp))
                .background(Brush.linearGradient(gradient)),
            contentAlignment = Alignment.BottomStart,
        ) {
            Text(title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(16.dp))
        }
    }
}

@Composable
private fun SmallCard(title: String, gradient: List<Color>, modifier: Modifier, onClick: () -> Unit) {
    TvFocusable(onClick = onClick, modifier = modifier, shape = RoundedCornerShape(10.dp)) {
        Box(
            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp)).background(Brush.linearGradient(gradient)),
            contentAlignment = Alignment.Center,
        ) {
            Text(title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun StatCard(title: String, count: Int, modifier: Modifier, onClick: () -> Unit) {
    TvFocusable(onClick = onClick, modifier = modifier, shape = RoundedCornerShape(36.dp)) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(title, color = AppColors.TextSecondary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text("$count", color = AppColors.TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
    }
}
