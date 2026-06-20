package com.walkman.tv.ui.library

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items as lazyItems
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.walkman.tv.data.model.Playlist
import com.walkman.tv.ui.appContainer
import com.walkman.tv.ui.components.Artwork
import com.walkman.tv.ui.components.EmptyHint
import com.walkman.tv.ui.components.TrackList
import com.walkman.tv.ui.components.TvFocusable
import com.walkman.tv.ui.components.TvPill
import com.walkman.tv.ui.playList
import com.walkman.tv.ui.theme.AppColors
import kotlinx.coroutines.launch

/**
 * 「我的列表」screen. Two top tabs:
 *  - 我的收藏: a grid of playlists ("我喜欢的" + user-created) + a 新建歌单 card.
 *    Tap a playlist to slide into its detail (track list + 播放全部 / 删除歌单 controls).
 *  - 播放历史: flat track list of the most-recently-played songs.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun LibraryScreen(onOpenPlayer: () -> Unit, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    val love by appContainer.libraryStore.love.collectAsState()
    val history by appContainer.libraryStore.history.collectAsState()
    val userLists by appContainer.libraryStore.userLists.collectAsState()

    var tab by remember { mutableStateOf(TAB_FAVORITES) }
    var showLocalImport by remember { mutableStateOf(false) }
    val downloadStore = appContainer.downloadStore
    val downloadFolders by downloadStore.folders.collectAsState()
    val downloadRecords by downloadStore.records.collectAsState()
    val downloadProgress by downloadStore.progress.collectAsState()
    var detailId by remember { mutableStateOf<String?>(null) }
    var showCreate by remember { mutableStateOf(false) }

    // Per-tab FocusRequester so D-pad Up from the lower area lands on the *active* tab,
    // not some other geometric candidate. Reused across recompositions.
    val tabFocus = remember {
        mapOf(
            TAB_FAVORITES to FocusRequester(),
            TAB_HISTORY to FocusRequester(),
            TAB_DOWNLOADED to FocusRequester(),
        )
    }
    val activeTabFocus = tabFocus[tab] ?: tabFocus[TAB_FAVORITES]!!

    // Back closes the playlist detail; otherwise falls through to the parent (which jumps to
    // Recommend / shows the exit dialog).
    BackHandler(enabled = detailId != null) { detailId = null }

    Column(modifier = modifier.fillMaxSize().padding(top = 8.dp)) {
        // Tab row — 4dp outer padding gives the 1.06x focus-scale + 2dp border somewhere
        // to grow without clipping at the screen edge.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 4.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TvPill(
                onClick = { tab = TAB_FAVORITES; detailId = null },
                selected = tab == TAB_FAVORITES,
                focusRequester = tabFocus[TAB_FAVORITES],
            ) {
                Text("我的收藏 (${1 + userLists.size})", fontSize = 13.sp)
            }
            TvPill(
                onClick = { tab = TAB_HISTORY; detailId = null },
                selected = tab == TAB_HISTORY,
                focusRequester = tabFocus[TAB_HISTORY],
            ) {
                Text("播放历史 (${history.tracks.size})", fontSize = 13.sp)
            }
            TvPill(
                onClick = { tab = TAB_DOWNLOADED; detailId = null },
                selected = tab == TAB_DOWNLOADED,
                focusRequester = tabFocus[TAB_DOWNLOADED],
            ) {
                Text("已下载 (${downloadStore.completedCount})", fontSize = 13.sp)
            }
            TvPill(
                onClick = { showLocalImport = true },
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 7.dp),
            ) {
                Text("+ 导入本地", fontSize = 12.sp)
            }
        }
        Spacer(Modifier.padding(top = 6.dp))

        when (tab) {
            TAB_HISTORY -> HistoryTab(
                history = history,
                onClear = { scope.launch { appContainer.libraryStore.clearHistory() } },
                onOpenPlayer = onOpenPlayer,
                upFocus = activeTabFocus,
            )
            TAB_FAVORITES -> {
                val detail = detailId?.let { id -> (listOf(love) + userLists).firstOrNull { it.id == id } }
                if (detail == null) {
                    PlaylistGrid(
                        playlists = listOf(love) + userLists,
                        onPick = { detailId = it.id },
                        onCreate = { showCreate = true },
                        upFocus = activeTabFocus,
                    )
                } else {
                    PlaylistDetailPane(
                        playlist = detail,
                        onBack = { detailId = null },
                        onOpenPlayer = onOpenPlayer,
                        onDelete = if (detail.id != Playlist.LOVE) {
                            {
                                scope.launch { appContainer.libraryStore.deleteList(detail.id) }
                                detailId = null
                            }
                        } else null,
                    )
                }
            }
            TAB_DOWNLOADED -> DownloadedTab(
                folders = downloadFolders,
                records = downloadRecords,
                progress = downloadProgress,
                onPlay = { tracks, idx ->
                    com.walkman.tv.ui.playList(tracks, idx)
                    onOpenPlayer()
                },
                upFocus = activeTabFocus,
            )
        }
    }

    if (showLocalImport) {
        com.walkman.tv.ui.components.LocalImportDialog(onDismiss = { showLocalImport = false })
    }

    if (showCreate) {
        com.walkman.tv.ui.components.PlaylistNameDialog(
            initial = "",
            title = "新建歌单",
            onDismiss = { showCreate = false },
            onConfirm = { name ->
                scope.launch { appContainer.libraryStore.createList(name) }
                showCreate = false
            },
        )
    }
}

private const val TAB_FAVORITES = "fav"
private const val TAB_HISTORY = "history"
private const val TAB_DOWNLOADED = "downloaded"

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun HistoryTab(
    history: Playlist,
    onClear: () -> Unit,
    onOpenPlayer: () -> Unit,
    upFocus: FocusRequester,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(history.name, fontSize = 18.sp, color = AppColors.TextPrimary)
        Spacer(Modifier.weight(1f))
        if (history.tracks.isNotEmpty()) {
            TvPill(onClick = onClear, accent = AppColors.Danger) { Text("清空", fontSize = 12.sp) }
        }
    }
    if (history.tracks.isEmpty()) {
        EmptyHint("列表为空", Modifier.fillMaxSize())
    } else {
        val nowId = appContainer.playbackController.state.collectAsState().value.currentTrack?.id
        TrackList(
            history.tracks,
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .focusProperties {
                    exit = { dir -> if (dir == FocusDirection.Up) upFocus else FocusRequester.Default }
                },
            nowPlayingId = nowId,
        ) { idx ->
            playList(history.tracks, idx); onOpenPlayer()
        }
    }
}

/** 4-column responsive grid of playlist covers + a "+ 新建歌单" tile at the end. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun PlaylistGrid(
    playlists: List<Playlist>,
    onPick: (Playlist) -> Unit,
    onCreate: () -> Unit,
    upFocus: FocusRequester,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(4),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(vertical = 8.dp, horizontal = 4.dp),
        modifier = Modifier
            .fillMaxSize()
            .focusProperties {
                exit = { dir -> if (dir == FocusDirection.Up) upFocus else FocusRequester.Default }
            },
    ) {
        items(playlists, key = { it.id }) { p ->
            PlaylistCard(p, onClick = { onPick(p) })
        }
        item { CreatePlaylistCard(onClick = onCreate) }
    }
}

@Composable
private fun PlaylistCard(playlist: Playlist, onClick: () -> Unit) {
    TvFocusable(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(8.dp)) {
            CoverMosaic(
                urls = playlist.tracks.take(4).map { it.picURL },
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(10.dp)),
            )
            Spacer(Modifier.padding(top = 8.dp))
            Text(
                playlist.name,
                color = AppColors.TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${playlist.tracks.size} 首",
                color = AppColors.TextMuted,
                fontSize = 11.sp,
            )
        }
    }
}

@Composable
private fun CreatePlaylistCard(onClick: () -> Unit) {
    TvFocusable(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(8.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(AppColors.AccentGreenDim),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = "新建歌单",
                    tint = AppColors.AccentGreen,
                    modifier = Modifier.size(40.dp),
                )
            }
            Spacer(Modifier.padding(top = 8.dp))
            Text(
                "新建歌单",
                color = AppColors.AccentGreen,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text("自定义名称", color = AppColors.TextMuted, fontSize = 11.sp)
        }
    }
}

/** 2x2 mosaic of up to 4 covers (or a single image when only 1 is available, or a fallback when
 *  none). Reuses [Artwork] for each cell so loading + placeholder behaviour stays consistent. */
@Composable
private fun CoverMosaic(urls: List<String?>, modifier: Modifier = Modifier) {
    Box(modifier = modifier.background(Color(0x14FFFFFF))) {
        val cleaned = urls.filterNotNull().filter { it.isNotEmpty() }
        when (cleaned.size) {
            0 -> Artwork(null, modifier = Modifier.fillMaxSize(), shape = RoundedCornerShape(0.dp))
            1 -> Artwork(cleaned[0], modifier = Modifier.fillMaxSize(), shape = RoundedCornerShape(0.dp))
            else -> {
                // We always render a 2x2 grid for 2/3/4 covers; missing cells get a null artwork.
                val padded = (cleaned + List(4) { null }).take(4)
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        Artwork(padded[0], modifier = Modifier.weight(1f).fillMaxHeight(), shape = RoundedCornerShape(0.dp))
                        Artwork(padded[1], modifier = Modifier.weight(1f).fillMaxHeight(), shape = RoundedCornerShape(0.dp))
                    }
                    Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        Artwork(padded[2], modifier = Modifier.weight(1f).fillMaxHeight(), shape = RoundedCornerShape(0.dp))
                        Artwork(padded[3], modifier = Modifier.weight(1f).fillMaxHeight(), shape = RoundedCornerShape(0.dp))
                    }
                }
            }
        }
    }
}

/** Detail view shown when a playlist is tapped: header (mosaic + name + actions) + TrackList. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun PlaylistDetailPane(
    playlist: Playlist,
    onBack: () -> Unit,
    onOpenPlayer: () -> Unit,
    onDelete: (() -> Unit)?,
) {
    val playFocus = remember { FocusRequester() }
    LaunchedEffect(playlist.id) { runCatching { playFocus.requestFocus() } }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 12.dp)) {
            CoverMosaic(
                urls = playlist.tracks.take(4).map { it.picURL },
                modifier = Modifier.size(96.dp).clip(RoundedCornerShape(12.dp)),
            )
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(playlist.name, color = AppColors.TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text("${playlist.tracks.size} 首", color = AppColors.TextMuted, fontSize = 12.sp)
                Spacer(Modifier.padding(top = 6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TvPill(
                        onClick = {
                            if (playlist.tracks.isNotEmpty()) {
                                playList(playlist.tracks, 0); onOpenPlayer()
                            }
                        },
                        selected = playlist.tracks.isNotEmpty(),
                        focusRequester = playFocus,
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 7.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("播放全部", fontSize = 12.sp)
                        }
                    }
                    TvPill(onClick = onBack, contentPadding = PaddingValues(horizontal = 14.dp, vertical = 7.dp)) {
                        Text("返回", fontSize = 12.sp)
                    }
                    if (onDelete != null) {
                        TvPill(
                            onClick = onDelete,
                            accent = AppColors.Danger,
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 7.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("删除歌单", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
        if (playlist.tracks.isEmpty()) {
            EmptyHint("歌单为空", Modifier.fillMaxSize())
        } else {
            val nowId = appContainer.playbackController.state.collectAsState().value.currentTrack?.id
            TrackList(
                playlist.tracks,
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .focusProperties {
                        exit = { dir -> if (dir == FocusDirection.Up) playFocus else FocusRequester.Default }
                    },
                nowPlayingId = nowId,
            ) { idx ->
                playList(playlist.tracks, idx); onOpenPlayer()
            }
        }
    }
}

/**
 * Downloaded-songs tab: per-folder sections (default "默认" + any user-created), each shows a
 * TrackList of completed records belonging to that folder. Active downloads (in progress + failed)
 * surface at the top so the user sees the running progress + can retry/cancel without hunting.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun DownloadedTab(
    folders: List<com.walkman.tv.data.model.DownloadFolder>,
    records: Map<String, com.walkman.tv.data.model.DownloadRecord>,
    progress: Map<String, Float>,
    onPlay: (List<com.walkman.tv.data.model.Track>, Int) -> Unit,
    upFocus: FocusRequester,
) {
    val active = records.values.filter { it.status == com.walkman.tv.data.model.DownloadStatus.DOWNLOADING }
    val failed = records.values.filter { it.status == com.walkman.tv.data.model.DownloadStatus.FAILED }

    androidx.compose.foundation.lazy.LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .focusProperties {
                exit = { dir -> if (dir == FocusDirection.Up) upFocus else FocusRequester.Default }
            },
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        if (active.isNotEmpty()) {
            item {
                Text("进行中", color = AppColors.AccentGreen, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
            lazyItems(active) { rec ->
                ActiveDownloadRow(rec, progress[rec.track.id] ?: 0f)
            }
        }
        if (failed.isNotEmpty()) {
            item {
                Text("失败", color = AppColors.Danger, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
            lazyItems(failed) { rec ->
                FailedDownloadRow(rec)
            }
        }
        val nowId = appContainer.playbackController.state.collectAsState().value.currentTrack?.id
        folders.forEach { folder ->
            val folderTracks = folder.trackIDs.mapNotNull { id ->
                records[id]?.takeIf { it.status == com.walkman.tv.data.model.DownloadStatus.COMPLETED }
            }
            if (folderTracks.isNotEmpty()) {
                item {
                    Text(
                        "${folder.name}  (${folderTracks.size})",
                        color = AppColors.TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                val tracks = folderTracks.map { it.track }
                itemsIndexed(tracks) { idx, track ->
                    com.walkman.tv.ui.components.TrackRow(
                        track = track,
                        index = idx,
                        nowPlaying = track.id == nowId,
                        onClick = { onPlay(tracks, idx) },
                    )
                }
            }
        }
        if (active.isEmpty() && failed.isEmpty() && folders.none { f ->
            f.trackIDs.any { id ->
                records[id]?.status == com.walkman.tv.data.model.DownloadStatus.COMPLETED
            }
        }) {
            item {
                EmptyHint("还没有下载，去搜索 / 歌单里长按歌曲下载", Modifier.fillMaxSize())
            }
        }
    }
}

@Composable
private fun ActiveDownloadRow(
    rec: com.walkman.tv.data.model.DownloadRecord,
    progress: Float,
) {
    val pct = (progress * 100).toInt()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(AppColors.Card)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(rec.track.name, color = AppColors.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text("${rec.track.singer} · ${rec.quality.displayName}", color = AppColors.TextMuted, fontSize = 11.sp, maxLines = 1)
            Spacer(Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(AppColors.BgDeep),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress.coerceIn(0f, 1f))
                        .height(2.dp)
                        .background(AppColors.AccentGreen),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Text("$pct%", color = AppColors.AccentGreen, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(12.dp))
        TvPill(
            onClick = { appContainer.downloadCoordinator.cancel(rec.track.id) },
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
        ) { Text("取消", fontSize = 12.sp) }
    }
}

@Composable
private fun FailedDownloadRow(rec: com.walkman.tv.data.model.DownloadRecord) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(AppColors.Card)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(rec.track.name, color = AppColors.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text(
                rec.errorMessage ?: "下载失败",
                color = AppColors.Danger,
                fontSize = 11.sp,
                maxLines = 1,
            )
        }
        Spacer(Modifier.width(12.dp))
        TvPill(
            onClick = { appContainer.downloadCoordinator.retry(rec.track.id) },
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
        ) { Text("重试", fontSize = 12.sp) }
        Spacer(Modifier.width(8.dp))
        TvPill(
            onClick = { appContainer.downloadCoordinator.removeDownload(rec.track.id) },
            accent = AppColors.Danger,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
        ) { Text("删除", fontSize = 12.sp) }
    }
}
