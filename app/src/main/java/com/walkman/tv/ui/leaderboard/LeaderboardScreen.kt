package com.walkman.tv.ui.leaderboard

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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.walkman.tv.data.model.BoardInfo
import com.walkman.tv.data.model.SourceID
import com.walkman.tv.data.model.Track
import com.walkman.tv.ui.appContainer
import com.walkman.tv.ui.components.EmptyHint
import com.walkman.tv.ui.components.LoadingState
import com.walkman.tv.ui.components.TrackList
import com.walkman.tv.ui.components.TvFocusable
import com.walkman.tv.ui.components.TvPill
import com.walkman.tv.ui.playList
import com.walkman.tv.ui.theme.AppColors

private val boardSources = listOf(SourceID.KW, SourceID.WY, SourceID.KG, SourceID.TX)

@Composable
fun LeaderboardScreen(onOpenPlayer: () -> Unit, modifier: Modifier = Modifier) {
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
        Column(modifier = Modifier.width(150.dp).fillMaxHeight()) {
            // Wrap to 2x2 — 4 platform chips can't fit horizontally in a 150dp column
            // (QQ音乐 alone is ~52dp; the row total ~175dp would clip the last chip).
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                boardSources.chunked(2).forEach { rowChips ->
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        rowChips.forEach { s ->
                            TvPill(
                                onClick = { source = s },
                                selected = source == s,
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                            ) { Text(s.displayName, fontSize = 11.sp) }
                        }
                    }
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
