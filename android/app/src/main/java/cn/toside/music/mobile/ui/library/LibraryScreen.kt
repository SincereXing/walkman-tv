package cn.toside.music.mobile.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import cn.toside.music.mobile.data.model.Playlist
import cn.toside.music.mobile.ui.appContainer
import cn.toside.music.mobile.ui.components.EmptyHint
import cn.toside.music.mobile.ui.components.TrackList
import cn.toside.music.mobile.ui.components.TvPill
import cn.toside.music.mobile.ui.playList
import kotlinx.coroutines.launch

@Composable
fun LibraryScreen(onOpenPlayer: () -> Unit, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    val love by appContainer.libraryStore.love.collectAsState()
    val history by appContainer.libraryStore.history.collectAsState()
    val userLists by appContainer.libraryStore.userLists.collectAsState()
    var selectedId by remember { mutableStateOf(Playlist.LOVE) }

    val allLists = listOf(love, history) + userLists
    val current = allLists.firstOrNull { it.id == selectedId } ?: love

    Column(modifier = modifier.fillMaxSize().padding(top = 8.dp)) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(allLists, key = { it.id }) { list ->
                TvPill(onClick = { selectedId = list.id }, selected = selectedId == list.id) {
                    Text("${list.name} (${list.tracks.size})", fontSize = 13.sp)
                }
            }
        }
        Spacer(Modifier.padding(top = 6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(current.name, fontSize = 18.sp, color = cn.toside.music.mobile.ui.theme.AppColors.TextPrimary)
            Spacer(Modifier.weight(1f))
            if (current.id == Playlist.HISTORY && current.tracks.isNotEmpty()) {
                TvPill(onClick = { scope.launch { appContainer.libraryStore.clearHistory() } }) { Text("清空", fontSize = 12.sp) }
            }
        }
        if (current.tracks.isEmpty()) {
            EmptyHint("列表为空", Modifier.fillMaxSize())
        } else {
            val nowId = appContainer.playbackController.state.collectAsState().value.currentTrack?.id
            TrackList(current.tracks, modifier = Modifier.fillMaxWidth().weight(1f), nowPlayingId = nowId) { idx ->
                playList(current.tracks, idx); onOpenPlayer()
            }
        }
    }
}
