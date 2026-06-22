package com.walkman.tv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Text
import com.walkman.tv.data.model.DownloadFolder
import com.walkman.tv.data.model.Quality
import com.walkman.tv.data.model.Track
import com.walkman.tv.ui.appContainer
import com.walkman.tv.ui.theme.AppColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Batch download dialog for a whole playlist (我的列表). Pick one target quality + folder, then
 * enqueue every track via [com.walkman.tv.playback.download.DownloadCoordinator.downloadAll].
 *
 * Quality is a *target*: each track resolves at the best tier ≤ this with the normal cascade, so
 * picking a high tier on a list of mixed-quality songs is safe. Already-downloaded-and-present
 * tracks are skipped by default.
 */
@Composable
fun BatchDownloadDialog(playlistName: String, tracks: List<Track>, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    val folders by appContainer.downloadStore.folders.collectAsState()
    val settings by appContainer.settingsStore.settings.collectAsState()
    val preferred = settings.preferredQuality

    var selectedQuality by remember { mutableStateOf(preferred) }
    var selectedFolderId by remember { mutableStateOf(DownloadFolder.DEFAULT_ID) }
    var showNewFolder by remember { mutableStateOf(false) }
    var enqueued by remember { mutableStateOf<Int?>(null) }

    val startFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { startFocus.requestFocus() } }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = Modifier
                .width(520.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(AppColors.BgPanel)
                .padding(horizontal = 22.dp, vertical = 20.dp),
        ) {
            Text("批量下载", color = AppColors.TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.size(4.dp))
            Text(
                "《$playlistName》· ${tracks.size} 首",
                color = AppColors.TextSecondary,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.size(14.dp))

            Text("目标音质", color = AppColors.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.size(2.dp))
            Text(
                "每首歌取不超过此档的最佳音质（不支持时自动降级）。",
                color = AppColors.TextMuted,
                fontSize = 11.sp,
            )
            Spacer(Modifier.size(6.dp))
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxWidth().heightIn(max = 150.dp),
            ) {
                items(Quality.orderedHighToLow) { q ->
                    val active = q == selectedQuality
                    TvFocusable(onClick = { selectedQuality = q }, modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                q.displayName,
                                color = if (active) AppColors.AccentGreen else AppColors.TextPrimary,
                                fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 14.sp,
                                modifier = Modifier.weight(1f),
                            )
                            if (active) {
                                Text("✓", color = AppColors.AccentGreen, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.size(12.dp))
            Text("保存到", color = AppColors.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.size(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                folders.forEach { folder ->
                    TvPill(
                        onClick = { selectedFolderId = folder.id },
                        selected = folder.id == selectedFolderId,
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    ) {
                        Text(folder.name, fontSize = 12.sp)
                    }
                }
                TvPill(
                    onClick = { showNewFolder = true },
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                ) {
                    Text("+ 新建", fontSize = 12.sp)
                }
            }

            Spacer(Modifier.size(10.dp))
            Text(
                if (settings.redownloadOnQualityChange) {
                    "已下载且音质相同的会跳过；音质不同的将按新音质重下（可在设置里关闭）。"
                } else {
                    "已下载的歌曲将全部跳过（可在设置里开启「按新音质重下」）。"
                },
                color = AppColors.TextMuted,
                fontSize = 11.sp,
            )

            enqueued?.let {
                Spacer(Modifier.size(12.dp))
                Text(
                    if (it > 0) "✓ 已加入 $it 首到下载队列" else "全部已下载，无需重复",
                    color = AppColors.AccentGreen,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            Spacer(Modifier.size(18.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                TvPill(
                    onClick = onDismiss,
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
                ) {
                    Text(if (enqueued != null) "完成" else "取消", fontSize = 13.sp)
                }
                if (enqueued == null) {
                    TvPill(
                        onClick = {
                            val n = appContainer.downloadCoordinator.downloadAll(
                                tracks = tracks,
                                quality = selectedQuality,
                                folderID = selectedFolderId,
                                redownloadOnQualityChange = settings.redownloadOnQualityChange,
                            )
                            enqueued = n
                            // Auto-close only when something was actually queued.
                            if (n > 0) scope.launch { delay(1100); onDismiss() }
                        },
                        selected = true,
                        focusRequester = startFocus,
                        contentPadding = PaddingValues(horizontal = 22.dp, vertical = 8.dp),
                    ) {
                        Text("开始下载", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }

    if (showNewFolder) {
        PlaylistNameDialog(
            initial = "",
            title = "新建下载分类",
            onDismiss = { showNewFolder = false },
            onConfirm = { name ->
                scope.launch {
                    val folder = appContainer.downloadStore.createFolder(name)
                    selectedFolderId = folder.id
                }
                showNewFolder = false
            },
        )
    }
}
