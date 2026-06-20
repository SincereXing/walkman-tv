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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.walkman.tv.data.model.Playlist
import com.walkman.tv.data.model.Track
import com.walkman.tv.ui.appContainer
import com.walkman.tv.ui.theme.AppColors
import kotlinx.coroutines.launch

private fun Modifier.focusRequesterIfFirst(fr: FocusRequester): Modifier = this.focusRequester(fr)

/**
 * "Add this track to..." dialog. Shows every playlist (我喜欢的 + user-created) with a check
 * indicator showing whether [track] is already in it; tapping toggles membership. Bottom row
 * has a 新建歌单 affordance that opens [PlaylistNameDialog] inline.
 */
@Composable
fun PlaylistPickerDialog(track: Track, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    val love by appContainer.libraryStore.love.collectAsState()
    val userLists by appContainer.libraryStore.userLists.collectAsState()
    val all = listOf(love) + userLists
    var showCreate by remember { mutableStateOf(false) }
    var showDownload by remember { mutableStateOf(false) }

    // Focus the row most-likely useful first — the first playlist the track is *not* in (so
    // tapping just adds, no toggle dance), falling back to row 0.
    val firstRowFocus = remember { FocusRequester() }
    LaunchedEffect(track.id) {
        runCatching { firstRowFocus.requestFocus() }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = Modifier
                .width(420.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(AppColors.BgPanel)
                .padding(horizontal = 22.dp, vertical = 20.dp),
        ) {
            Text(
                "添加到歌单",
                color = AppColors.TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.size(4.dp))
            Text(
                track.name,
                color = AppColors.TextSecondary,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.size(14.dp))
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(all, key = { it.id }) { pl ->
                    val inList = pl.tracks.any { it.id == track.id }
                    val rowMod = if (pl.id == all.first().id) {
                        Modifier.fillMaxWidth().focusRequesterIfFirst(firstRowFocus)
                    } else {
                        Modifier.fillMaxWidth()
                    }
                    TvFocusable(
                        onClick = {
                            scope.launch {
                                if (pl.id == Playlist.LOVE) {
                                    appContainer.libraryStore.toggleFavorite(track)
                                } else if (inList) {
                                    appContainer.libraryStore.removeFromList(pl.id, track.id)
                                } else {
                                    appContainer.libraryStore.addToList(pl.id, track)
                                }
                            }
                        },
                        modifier = rowMod,
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                pl.name,
                                color = if (inList) AppColors.AccentGreen else AppColors.TextPrimary,
                                fontSize = 14.sp,
                                fontWeight = if (inList) FontWeight.Bold else FontWeight.Medium,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                "${pl.tracks.size} 首",
                                color = AppColors.TextMuted,
                                fontSize = 11.sp,
                            )
                            Spacer(Modifier.size(8.dp))
                            if (inList) {
                                Icon(
                                    Icons.Filled.Check,
                                    contentDescription = null,
                                    tint = AppColors.AccentGreen,
                                    modifier = Modifier.size(18.dp),
                                )
                            } else {
                                Box(modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.size(12.dp))
            // All actions hug the right edge so the dialog footer is consistent across the app.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                TvPill(
                    onClick = { showDownload = true },
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                ) {
                    Text("下载", fontSize = 13.sp)
                }
                TvPill(
                    onClick = { showCreate = true },
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.size(4.dp))
                        Text("新建歌单", fontSize = 13.sp)
                    }
                }
                TvPill(
                    onClick = onDismiss,
                    selected = true,
                    contentPadding = PaddingValues(horizontal = 22.dp, vertical = 8.dp),
                ) {
                    Text("完成", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }

    if (showDownload) {
        DownloadDialog(track = track, onDismiss = { showDownload = false })
    }

    if (showCreate) {
        PlaylistNameDialog(
            initial = "",
            title = "新建歌单",
            onDismiss = { showCreate = false },
            onConfirm = { name ->
                scope.launch {
                    val created = appContainer.libraryStore.createList(name)
                    // Convenience: add the current track to the just-created playlist.
                    appContainer.libraryStore.addToList(created.id, track)
                }
                showCreate = false
            },
        )
    }
}

/**
 * Name-input dialog for a playlist. Chinese-friendly: shows a real BasicTextField (Compose
 * pops the system IME — whatever Chinese / Pinyin keyboard the user has installed on the TV),
 * plus a 手机扫码输入 affordance that opens a QR code pointing to LocalServer's
 * /playlist-name form so the user can type on their phone instead. Name received from the QR
 * flow is pushed into the field; the user can edit further or hit 完成 immediately.
 */
@Composable
fun PlaylistNameDialog(
    initial: String,
    title: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember { mutableStateOf(initial) }
    var showQr by remember { mutableStateOf(false) }
    val fieldFocus = remember { FocusRequester() }
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current

    // Auto-focus the text field on open so the system IME can be invoked immediately.
    LaunchedEffect(Unit) {
        runCatching { fieldFocus.requestFocus() }
        runCatching { keyboard?.show() }
    }
    // QR-submitted names get pushed straight into the field.
    LaunchedEffect(Unit) {
        appContainer.events.qrPlaylistName.collect { received ->
            name = received.take(24)
            showQr = false
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = Modifier
                .width(440.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(AppColors.BgPanel)
                .padding(horizontal = 22.dp, vertical = 20.dp),
        ) {
            Text(title, color = AppColors.TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.size(10.dp))

            // Real editable text field — Compose brings up the system IME on focus, supporting
            // whichever Chinese / pinyin keyboard the user has installed.
            androidx.compose.foundation.text.BasicTextField(
                value = name,
                onValueChange = { v -> if (v.length <= 24) name = v },
                singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(
                    color = AppColors.TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(AppColors.AccentGreen),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    imeAction = androidx.compose.ui.text.input.ImeAction.Done,
                ),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                    onDone = {
                        val trimmed = name.trim()
                        if (trimmed.isNotEmpty()) onConfirm(trimmed)
                    },
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(AppColors.BgDeep)
                    .padding(horizontal = 14.dp, vertical = 14.dp)
                    .focusRequester(fieldFocus),
                decorationBox = { inner ->
                    Box {
                        if (name.isEmpty()) {
                            Text(
                                "请输入歌单名（按确认键调出系统输入法）",
                                color = AppColors.TextMuted,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        inner()
                    }
                },
            )
            Spacer(Modifier.size(6.dp))
            Text(
                "中文输入不便？点击「手机扫码输入」用手机键盘",
                color = AppColors.TextMuted,
                fontSize = 11.sp,
            )

            Spacer(Modifier.size(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                TvPill(
                    onClick = { showQr = true },
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                ) {
                    Text("手机扫码输入", fontSize = 13.sp)
                }
                TvPill(
                    onClick = onDismiss,
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
                ) {
                    Text("取消", fontSize = 13.sp)
                }
                TvPill(
                    onClick = {
                        val trimmed = name.trim()
                        if (trimmed.isNotEmpty()) onConfirm(trimmed)
                    },
                    selected = name.isNotBlank(),
                    contentPadding = PaddingValues(horizontal = 22.dp, vertical = 8.dp),
                ) {
                    Text("完成", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }

    if (showQr) {
        val ip = remember { com.walkman.tv.di.getLanIp() }
        val port = appContainer.localServer?.boundPort
        if (ip != null && port != null) {
            QrDialog(
                url = "http://$ip:$port/playlist-name",
                title = "扫码输入名字",
                subtitle = "在手机上输入（中英文都行），提交后名字会自动填入输入框",
                onDismiss = { showQr = false },
            )
        } else {
            // Best-effort fallback when the LAN server isn't up — just close the QR overlay so
            // the user can fall back to TV-side IME.
            LaunchedEffect(Unit) { showQr = false }
        }
    }
}
