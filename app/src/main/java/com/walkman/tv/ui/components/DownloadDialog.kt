package com.walkman.tv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import kotlinx.coroutines.launch

/**
 * Spec §3.1 download dialog. Quality picker on the left, folder picker on the right, start
 * button hugs the bottom-right. Available qualities = track.qualities ∪ the script's declared
 * extended tiers (hires / atmos / atmos_plus / master) — matches the pick logic in SourceManager.
 */
@Composable
fun DownloadDialog(track: Track, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    val folders by appContainer.downloadStore.folders.collectAsState()

    // Available qualities: track-declared + extended tiers any loaded script supports.
    val available = remember(track) {
        val trackQs = track.qualities.toSet()
        val extendedFromScripts = Quality.entries.filter { q ->
            q.isExtendedTier && appContainer.sourceManager.availableQualities(track.source).contains(q)
        }
        (Quality.orderedHighToLow).filter { it in trackQs || it in extendedFromScripts }
    }
    var selectedQuality by remember {
        mutableStateOf(available.firstOrNull() ?: Quality.K320)
    }
    var selectedFolderId by remember { mutableStateOf(DownloadFolder.DEFAULT_ID) }
    var showNewFolder by remember { mutableStateOf(false) }

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
            Text("下载歌曲", color = AppColors.TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.size(4.dp))
            Text(
                "${track.name} · ${track.singer}",
                color = AppColors.TextSecondary,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.size(14.dp))

            // Quality picker
            Text("音质", color = AppColors.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.size(6.dp))
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxWidth().heightIn(max = 160.dp),
            ) {
                items(available) { q ->
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

            Spacer(Modifier.size(18.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                TvPill(
                    onClick = onDismiss,
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
                ) {
                    Text("取消", fontSize = 13.sp)
                }
                TvPill(
                    onClick = {
                        appContainer.downloadCoordinator.download(track, selectedQuality, selectedFolderId)
                        onDismiss()
                    },
                    selected = true,
                    contentPadding = PaddingValues(horizontal = 22.dp, vertical = 8.dp),
                ) {
                    Text("开始下载", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
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
