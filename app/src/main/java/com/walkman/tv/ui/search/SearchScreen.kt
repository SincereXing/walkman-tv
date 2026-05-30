package com.walkman.tv.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import androidx.tv.material3.Icon
import com.walkman.tv.data.model.SonglistInfo
import com.walkman.tv.data.model.SourceID
import com.walkman.tv.data.model.Track
import com.walkman.tv.di.getLanIp
import com.walkman.tv.ui.appContainer
import com.walkman.tv.ui.components.EmptyHint
import com.walkman.tv.ui.components.LoadingState
import com.walkman.tv.ui.components.MediaCard
import com.walkman.tv.ui.components.QrDialog
import com.walkman.tv.ui.components.TrackList
import com.walkman.tv.ui.components.TvPill
import com.walkman.tv.ui.playList
import com.walkman.tv.ui.theme.AppColors
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

@Composable
fun SearchScreen(onOpenPlayer: () -> Unit, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf<SourceID?>(null) }
    var tracks by remember { mutableStateOf<List<Track>>(emptyList()) }
    var songlists by remember { mutableStateOf<List<SonglistInfo>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var searched by remember { mutableStateOf(false) }
    var showQr by remember { mutableStateOf(false) }

    fun runSearch() {
        if (query.isBlank()) return
        scope.launch {
            loading = true
            searched = true
            val f = filter
            val q = query
            val pair = runCatching {
                coroutineScope {
                    val tracksJob = async {
                        if (f == null) appContainer.catalogs.searchAll(q)
                        else appContainer.catalogs.search(f, q)
                    }
                    val songlistsJob = async {
                        if (f == null) appContainer.songlists.searchAll(q)
                        else appContainer.songlists.serviceFor(f)?.search(q, 1) ?: emptyList()
                    }
                    tracksJob.await() to songlistsJob.await()
                }
            }.getOrDefault(emptyList<Track>() to emptyList<SonglistInfo>())
            tracks = pair.first
            songlists = pair.second
            loading = false
        }
    }

    // Re-run search when the platform filter changes (only after the first manual search).
    LaunchedEffect(filter) {
        if (searched) runSearch()
    }

    // Phone-to-TV: incoming search keywords from the QR-served form.
    LaunchedEffect(Unit) {
        appContainer.events.qrSearchKeyword.collect { k ->
            query = k
            showQr = false
            runSearch()
        }
    }

    Row(modifier = modifier.fillMaxSize().padding(top = 8.dp)) {
        // LEFT: search input + virtual keypad
        Column(modifier = Modifier.width(360.dp).fillMaxHeight().padding(end = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.weight(1f)) { SearchInputBox(query) }
                Spacer(Modifier.width(8.dp))
                TvPill(
                    onClick = { showQr = true },
                    shape = androidx.compose.foundation.shape.CircleShape,
                    contentPadding = PaddingValues(10.dp),
                ) {
                    Icon(Icons.Filled.QrCode2, contentDescription = "扫码搜索", modifier = Modifier.size(22.dp))
                }
            }
            Spacer(Modifier.padding(top = 12.dp))
            SearchKeypad(
                onAppend = { ch -> query += ch },
                onBackspace = { if (query.isNotEmpty()) query = query.dropLast(1) },
                onClear = { query = "" },
                onSearch = { runSearch() },
            )
        }

        // RIGHT: platform chips + results
        Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
            PlatformChipRow(filter, onSelect = { filter = it })
            Spacer(Modifier.padding(top = 8.dp))
            ResultsPane(
                loading = loading,
                searched = searched,
                songlists = songlists,
                tracks = tracks,
                onOpenPlayer = onOpenPlayer,
                onPickSonglist = { info ->
                    scope.launch {
                        val svc = appContainer.songlists.serviceFor(info.source) ?: return@launch
                        val detail = runCatching { svc.fetchDetail(info) }.getOrNull()
                        val ts = detail?.tracks.orEmpty()
                        if (ts.isNotEmpty()) {
                            playList(ts, 0); onOpenPlayer()
                        }
                    }
                },
            )
        }
    }

    if (showQr) {
        val ip = remember { getLanIp() }
        val port = appContainer.localServer?.boundPort
        if (ip != null && port != null) {
            QrDialog(
                url = "http://$ip:$port/search",
                title = "扫码搜索",
                subtitle = "手机扫码、电脑直接打开地址都可以，输入关键词后会自动回到电视搜索",
                onDismiss = { showQr = false },
            )
        } else {
            QrUnavailableDialog(onDismiss = { showQr = false })
        }
    }
}

@Composable
private fun QrUnavailableDialog(onDismiss: () -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("无法启动扫码") },
        text = { Text("请确认电视已连接 Wi-Fi 且本地服务已启动后再试。") },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text("好", color = AppColors.AccentGreen)
            }
        },
        containerColor = AppColors.BgPanel,
        titleContentColor = AppColors.TextPrimary,
        textContentColor = AppColors.TextSecondary,
    )
}

// MARK: - Left panel ---------------------------------------------------------------------------

@Composable
private fun SearchInputBox(query: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(AppColors.BgPanel)
            .padding(horizontal = 14.dp, vertical = 14.dp),
    ) {
        Text(
            text = query.ifEmpty { "输入关键词" },
            color = if (query.isEmpty()) AppColors.TextMuted else AppColors.TextPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SearchKeypad(
    onAppend: (Char) -> Unit,
    onBackspace: () -> Unit,
    onClear: () -> Unit,
    onSearch: () -> Unit,
) {
    // 6 columns. Letters first (5 rows of 6 = 26 with last row holding Y Z), then a separator,
    // then digits in 2 quieter rows, then a control row.
    val letterRows = listOf("ABCDEF", "GHIJKL", "MNOPQR", "STUVWX", "YZ")
    val digitRows = listOf("012345", "6789")
    val firstKey = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { firstKey.requestFocus() } }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        letterRows.forEachIndexed { rowIdx, row ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                row.forEachIndexed { colIdx, c ->
                    KeyButton(
                        label = c.toString(),
                        focusRequester = if (rowIdx == 0 && colIdx == 0) firstKey else null,
                        onClick = { onAppend(c) },
                    )
                }
            }
        }
        Spacer(Modifier.size(6.dp))
        digitRows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                row.forEach { c -> KeyButton(c.toString()) { onAppend(c) } }
            }
        }
        Spacer(Modifier.size(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            KeyButton("删除", wide = true) { onBackspace() }
            KeyButton("清空", wide = true) { onClear() }
            KeyButton("搜索", wide = true, primary = true) { onSearch() }
        }
    }
}

@Composable
private fun KeyButton(
    label: String,
    wide: Boolean = false,
    primary: Boolean = false,
    focusRequester: FocusRequester? = null,
    onClick: () -> Unit,
) {
    // Letter/digit key = 44 wide, wide key = 92 (matches 2 letter slots + a gap).
    // Letter row total: 6*44 + 5*4 = 284dp. Control row: 3*92 + 2*6 = 288dp ≈ 284dp.
    TvPill(
        onClick = onClick,
        selected = primary,
        focusRequester = focusRequester,
        contentPadding = PaddingValues(0.dp),
    ) {
        Box(
            modifier = Modifier.size(width = if (wide) 92.dp else 44.dp, height = 38.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                label,
                fontSize = if (label.length == 1) 15.sp else 13.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
        }
    }
}

// MARK: - Right panel --------------------------------------------------------------------------

private val PLATFORMS_LABELS: List<Pair<SourceID?, String>> = listOf(
    null to "全部",
    SourceID.KW to "酷我",
    SourceID.KG to "酷狗",
    SourceID.TX to "QQ音乐",
    SourceID.WY to "网易云",
    SourceID.MG to "咪咕",
)

@Composable
private fun PlatformChipRow(filter: SourceID?, onSelect: (SourceID?) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PLATFORMS_LABELS.forEach { (src, label) ->
            TvPill(
                onClick = { onSelect(src) },
                selected = filter == src,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 7.dp),
            ) {
                Text(label, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun ResultsPane(
    loading: Boolean,
    searched: Boolean,
    songlists: List<SonglistInfo>,
    tracks: List<Track>,
    onOpenPlayer: () -> Unit,
    onPickSonglist: (SonglistInfo) -> Unit,
) {
    when {
        loading -> LoadingState(Modifier.fillMaxSize())
        !searched -> EmptyHint("输入关键词开始搜索", Modifier.fillMaxSize())
        songlists.isEmpty() && tracks.isEmpty() ->
            EmptyHint("没有找到结果", Modifier.fillMaxSize())
        else -> Column(modifier = Modifier.fillMaxSize()) {
            if (songlists.isNotEmpty()) {
                SectionHeader("歌单 (${songlists.size})")
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(vertical = 4.dp),
                ) {
                    items(songlists, key = { it.id + "_" + it.source.key }) { sl ->
                        Box(modifier = Modifier.width(140.dp)) {
                            MediaCard(
                                title = sl.name,
                                picURL = sl.picURL,
                                subtitle = sl.playCount?.let { "▶ $it" },
                                onClick = { onPickSonglist(sl) },
                            )
                        }
                    }
                }
                Spacer(Modifier.padding(top = 8.dp))
            }
            if (tracks.isNotEmpty()) {
                SectionHeader("歌曲 (${tracks.size})")
                val nowId = appContainer.playbackController.state.collectAsState().value.currentTrack?.id
                TrackList(
                    tracks = tracks,
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    nowPlayingId = nowId,
                    onPlay = { idx -> playList(tracks, idx); onOpenPlayer() },
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        color = AppColors.AccentGreen,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(vertical = 4.dp),
    )
}
