package cn.toside.music.mobile.ui.leaderboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import cn.toside.music.mobile.data.model.BoardInfo
import cn.toside.music.mobile.data.model.SourceID
import cn.toside.music.mobile.data.model.Track
import cn.toside.music.mobile.ui.appContainer
import cn.toside.music.mobile.ui.components.EmptyHint
import cn.toside.music.mobile.ui.components.LoadingState
import cn.toside.music.mobile.ui.components.TrackList
import cn.toside.music.mobile.ui.components.TvFocusable
import cn.toside.music.mobile.ui.components.TvPill
import cn.toside.music.mobile.ui.playList
import cn.toside.music.mobile.ui.theme.AppColors
import kotlinx.coroutines.launch

private val boardSources = listOf(SourceID.KW, SourceID.WY, SourceID.KG, SourceID.TX)

@Composable
fun LeaderboardScreen(onOpenPlayer: () -> Unit, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    var source by remember { mutableStateOf(SourceID.KW) }
    var boards by remember { mutableStateOf<List<BoardInfo>>(emptyList()) }
    var selected by remember { mutableStateOf<BoardInfo?>(null) }
    var tracks by remember { mutableStateOf<List<Track>>(emptyList()) }
    var loadingTracks by remember { mutableStateOf(false) }

    LaunchedEffect(source) {
        boards = runCatching { appContainer.boards.serviceFor(source)?.fetchBoards() ?: emptyList() }.getOrDefault(emptyList())
        selected = boards.firstOrNull()
    }
    LaunchedEffect(selected) {
        val b = selected ?: return@LaunchedEffect
        loadingTracks = true
        tracks = runCatching { appContainer.boards.serviceFor(b.source)?.fetchTracks(b.bangid, 1) ?: emptyList() }.getOrDefault(emptyList())
        loadingTracks = false
    }

    Row(modifier = modifier.fillMaxSize().padding(top = 8.dp)) {
        Column(modifier = Modifier.width(300.dp).fillMaxHeight()) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                boardSources.forEach { s ->
                    TvPill(onClick = { source = s }, selected = source == s) { Text(s.displayName, fontSize = 12.sp) }
                }
            }
            Spacer(Modifier.padding(top = 6.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(boards, key = { it.id }) { b ->
                    TvFocusable(onClick = { selected = b }, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            b.name,
                            color = if (selected?.id == b.id) AppColors.AccentGreen else AppColors.TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        )
                    }
                }
            }
        }
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
            Text(selected?.name ?: "排行榜", color = AppColors.TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            when {
                loadingTracks -> LoadingState(Modifier.fillMaxSize())
                tracks.isNotEmpty() -> {
                    val nowId = appContainer.playbackController.state.collectAsState().value.currentTrack?.id
                    TrackList(tracks, modifier = Modifier.fillMaxWidth().weight(1f), nowPlayingId = nowId) { idx ->
                        playList(tracks, idx); onOpenPlayer()
                    }
                }
                else -> EmptyHint("暂无内容", Modifier.fillMaxSize())
            }
        }
    }
}
