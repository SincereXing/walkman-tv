package com.walkman.tv.ui.songlist

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.walkman.tv.data.model.SonglistInfo
import com.walkman.tv.data.model.SonglistOrder
import com.walkman.tv.data.model.SonglistTag
import com.walkman.tv.data.model.SourceID
import com.walkman.tv.data.model.Track
import com.walkman.tv.ui.appContainer
import com.walkman.tv.ui.components.EmptyHint
import com.walkman.tv.ui.components.LoadingState
import com.walkman.tv.ui.components.MediaCard
import com.walkman.tv.ui.components.TrackList
import com.walkman.tv.ui.components.TvPill
import com.walkman.tv.ui.playList
import com.walkman.tv.ui.theme.AppColors
import kotlinx.coroutines.launch

private val songlistSources = listOf(SourceID.KW, SourceID.WY, SourceID.KG, SourceID.TX)

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun SonglistScreen(onOpenPlayer: () -> Unit, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    var source by remember { mutableStateOf(SourceID.KW) }
    var order by remember { mutableStateOf<SonglistOrder?>(null) }
    var tags by remember { mutableStateOf<List<SonglistTag>>(listOf(SonglistTag.ALL)) }
    var tag by remember { mutableStateOf(SonglistTag.ALL) }
    var lists by remember { mutableStateOf<List<SonglistInfo>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var detail by remember { mutableStateOf<Pair<SonglistInfo, List<Track>>?>(null) }
    var loadingDetail by remember { mutableStateOf(false) }

    val service = appContainer.songlists.serviceFor(source)

    LaunchedEffect(source) {
        order = service?.orders?.firstOrNull()
        tag = SonglistTag.ALL
        val groups = runCatching { service?.fetchTags() ?: emptyList() }.getOrDefault(emptyList())
        tags = listOf(SonglistTag.ALL) + groups.flatMap { it.tags }
    }
    LaunchedEffect(source, order, tag) {
        val o = order ?: return@LaunchedEffect
        loading = true
        lists = runCatching { service?.fetchRecommended(o, tag, 1) ?: emptyList() }.getOrDefault(emptyList())
        loading = false
    }

    // IMPORTANT: keep the grid UI in the composition even when detail is open. If we early-return
    // here, the LaunchedEffect(source, order, tag) above leaves the composition; on the way back
    // (detail=null) it re-fires as a fresh effect and re-fetches lists, blowing away the user's
    // grid scroll position and the previously-focused card. Rendering detail as an overlay keeps
    // the grid alive (state, focus, fetched lists), so back-navigation is instant and preserves
    // the selection.
    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().padding(top = 8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                songlistSources.forEach { s ->
                    TvPill(onClick = { source = s }, selected = source == s) { Text(s.displayName, fontSize = 12.sp) }
                }
                Spacer(Modifier.padding(start = 8.dp))
                service?.orders?.forEach { o ->
                    TvPill(onClick = { order = o }, selected = order?.id == o.id) { Text(o.name, fontSize = 12.sp) }
                }
            }
            Spacer(Modifier.padding(top = 6.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(tags) { t ->
                    TvPill(onClick = { tag = t }, selected = tag.id == t.id && tag.name == t.name) { Text(t.name, fontSize = 12.sp) }
                }
            }
            Spacer(Modifier.padding(top = 6.dp))
            when {
                loading -> LoadingState(Modifier.fillMaxSize())
                lists.isNotEmpty() -> LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    modifier = Modifier.fillMaxWidth().weight(1f)
                        .focusRestorer(), // restore the previously-focused card on back-nav
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(lists, key = { it.id }) { sl ->
                        MediaCard(title = sl.name, picURL = sl.picURL, subtitle = sl.playCount?.let { "▶ $it" }) {
                            scope.launch {
                                loadingDetail = true
                                val d = runCatching { service?.fetchDetail(sl) }.getOrNull()
                                detail = sl to (d?.tracks ?: emptyList())
                                loadingDetail = false
                            }
                        }
                    }
                }
                else -> EmptyHint("暂无歌单", Modifier.fillMaxSize())
            }
        }

        // Detail overlay — visually replaces the grid but the grid remains in composition,
        // preserving scroll position, fetched lists, and focus.
        if (detail != null) {
            val (info, tracks) = detail!!
            BackHandler { detail = null }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(AppColors.BgDeep)
                    .padding(top = 8.dp),
            ) {
                Text(info.name, color = AppColors.TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                val nowId = appContainer.playbackController.state.collectAsState().value.currentTrack?.id
                TrackList(tracks, modifier = Modifier.fillMaxWidth().weight(1f), nowPlayingId = nowId) { idx ->
                    playList(tracks, idx); onOpenPlayer()
                }
            }
        }
    }
}

