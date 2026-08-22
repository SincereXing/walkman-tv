package com.walkman.tv.ui.components

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import java.io.File
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
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
    val ctx = LocalContext.current
    var pickedFile by remember { mutableStateOf<File?>(null) }
    var playlistName by remember { mutableStateOf("") }
    var progress by remember { mutableStateOf(0f) }
    var importing by remember { mutableStateOf(false) }
    var doneMessage by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var showBrowser by remember { mutableStateOf(false) }
    var permHint by remember { mutableStateOf(false) }
    // Default focus = 选择文件夹 since that's step 1 in the flow.
    val pickerFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { pickerFocus.requestFocus() } }

    // Uses an in-app File browser (not SAF): many TVs have no DocumentsUI picker. Needs storage
    // read access first — READ_EXTERNAL_STORAGE on API ≤ 29, All-files access on 30+.
    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> if (granted) showBrowser = true else permHint = true }

    fun openPicker() {
        if (hasStorageAccess(ctx)) {
            showBrowser = true
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Route to the per-app "All files access" screen; user grants, returns, taps again.
            permHint = true
            runCatching {
                ctx.startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        Uri.parse("package:${ctx.packageName}"),
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }.onFailure {
                runCatching {
                    ctx.startActivity(
                        Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }
            }
        } else {
            permLauncher.launch(android.Manifest.permission.READ_EXTERNAL_STORAGE)
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
                onClick = { openPicker() },
                focusRequester = pickerFocus,
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
            ) {
                Text(
                    pickedFile?.name ?: "选择文件夹…",
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (permHint) {
                Spacer(Modifier.size(6.dp))
                Text(
                    "需要存储访问权限：请在系统设置里为「随便听」开启文件访问（或允许存储权限），然后返回再点选择文件夹。",
                    color = AppColors.Warning,
                    fontSize = 12.sp,
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
                            val dir = pickedFile ?: return@TvPill
                            if (importing) return@TvPill
                            importing = true
                            error = null
                            scope.launch {
                                runCatching {
                                    appContainer.localMusicStore.importFolderFile(
                                        dir,
                                        playlistName,
                                    ) { p -> progress = p }
                                }.onSuccess { result ->
                                    // Create a user playlist + batch-dump the tracks (one write,
                                    // not one per track — a big folder would otherwise O(n²) stall).
                                    val playlist = appContainer.libraryStore.createList(result.record.name)
                                    appContainer.libraryStore.addAllToList(playlist.id, result.tracks)
                                    doneMessage = "✓ 导入完成，共 ${result.tracks.size} 首"
                                    importing = false
                                }.onFailure { e ->
                                    error = "导入失败：${e.message ?: "未知错误"}"
                                    importing = false
                                }
                            }
                        },
                        selected = pickedFile != null && !importing,
                        contentPadding = PaddingValues(horizontal = 22.dp, vertical = 8.dp),
                    ) {
                        Text("确定导入", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }

    if (showBrowser) {
        FolderBrowser(
            onPick = { dir ->
                pickedFile = dir
                if (playlistName.isBlank()) playlistName = dir.name.ifBlank { "本地音乐" }
                permHint = false
                showBrowser = false
            },
            onCancel = { showBrowser = false },
        )
    }
}

/** Whether we can browse arbitrary folders with java.io.File right now. */
private fun hasStorageAccess(ctx: android.content.Context): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Environment.isExternalStorageManager()
    } else {
        ContextCompat.checkSelfPermission(
            ctx,
            android.Manifest.permission.READ_EXTERNAL_STORAGE,
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }
