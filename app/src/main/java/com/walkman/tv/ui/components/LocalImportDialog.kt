package com.walkman.tv.ui.components

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
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
import com.walkman.tv.data.model.LocalFolderRecord
import com.walkman.tv.ui.appContainer
import com.walkman.tv.ui.theme.AppColors
import kotlinx.coroutines.launch

/**
 * Local-folder import dialog. Spec §6.1 + §6.2.
 *
 * Flow:
 *   1. User taps 选择文件夹 → system SAF picker (ACTION_OPEN_DOCUMENT_TREE)
 *   2. URI captured + permission persisted by LocalMusicStore
 *   3. Optional: user names the playlist (defaults to folder name)
 *   4. Tap 确定导入 → scan + extract tags + create a Playlist in LibraryStore
 *   5. Show progress; close on completion.
 */
@Composable
fun LocalImportDialog(onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var pickedUri by remember { mutableStateOf<Uri?>(null) }
    var playlistName by remember { mutableStateOf("") }
    var progress by remember { mutableStateOf(0f) }
    var importing by remember { mutableStateOf(false) }
    var doneMessage by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) {
            pickedUri = uri
            if (playlistName.isBlank()) {
                playlistName = uri.lastPathSegment?.substringAfter(":")?.substringAfterLast('/')
                    ?: "本地音乐"
            }
        }
    }

    Dialog(
        onDismissRequest = { if (!importing) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = Modifier
                .width(440.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(AppColors.BgPanel)
                .padding(horizontal = 22.dp, vertical = 20.dp),
        ) {
            Text("导入本地音乐", color = AppColors.TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.size(4.dp))
            Text(
                "选一个文件夹，应用会递归扫描所有 .mp3/.flac/.m4a/.wav 等音频文件并导入。",
                color = AppColors.TextSecondary,
                fontSize = 12.sp,
            )
            Spacer(Modifier.size(14.dp))

            Text("文件夹", color = AppColors.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.size(6.dp))
            TvPill(
                onClick = { launcher.launch(null) },
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
            ) {
                Text(
                    pickedUri?.lastPathSegment ?: "选择文件夹…",
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.size(12.dp))

            Text("歌单名（导入后创建一个用户歌单）", color = AppColors.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.size(6.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(AppColors.BgDeep)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                Text(
                    text = playlistName.ifEmpty { "（默认用文件夹名）" },
                    color = if (playlistName.isEmpty()) AppColors.TextMuted else AppColors.TextPrimary,
                    fontSize = 14.sp,
                )
            }

            if (importing) {
                Spacer(Modifier.size(14.dp))
                Text(
                    "正在导入 ${(progress * 100).toInt()}%",
                    color = AppColors.AccentGreen,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.size(4.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(AppColors.Card),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress.coerceIn(0f, 1f))
                            .height(3.dp)
                            .background(AppColors.AccentGreen),
                    )
                }
            }
            doneMessage?.let {
                Spacer(Modifier.size(10.dp))
                Text(it, color = AppColors.AccentGreen, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
            error?.let {
                Spacer(Modifier.size(10.dp))
                Text(it, color = AppColors.Danger, fontSize = 13.sp)
            }

            Spacer(Modifier.size(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                TvPill(
                    onClick = onDismiss,
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
                ) {
                    Text(if (doneMessage != null) "完成" else "取消", fontSize = 13.sp)
                }
                if (doneMessage == null) {
                    TvPill(
                        onClick = {
                            val uri = pickedUri ?: return@TvPill
                            if (importing) return@TvPill
                            importing = true
                            error = null
                            scope.launch {
                                runCatching {
                                    appContainer.localMusicStore.importFolder(
                                        uri,
                                        playlistName,
                                    ) { p -> progress = p }
                                }.onSuccess { result ->
                                    // Create a user playlist + dump the tracks in via LibraryStore.
                                    val playlist = appContainer.libraryStore.createList(result.record.name)
                                    result.tracks.forEach { t ->
                                        appContainer.libraryStore.addToList(playlist.id, t)
                                    }
                                    doneMessage = "✓ 导入完成，共 ${result.tracks.size} 首"
                                    importing = false
                                }.onFailure { e ->
                                    error = "导入失败：${e.message ?: "未知错误"}"
                                    importing = false
                                }
                            }
                        },
                        selected = pickedUri != null && !importing,
                        contentPadding = PaddingValues(horizontal = 22.dp, vertical = 8.dp),
                    ) {
                        Text("确定导入", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}
