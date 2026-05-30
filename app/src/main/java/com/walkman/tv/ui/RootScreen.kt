package com.walkman.tv.ui

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.walkman.tv.ui.components.TvPill
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.walkman.tv.ui.components.TopNav
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import com.walkman.tv.ui.leaderboard.LeaderboardScreen
import com.walkman.tv.ui.library.LibraryScreen
import com.walkman.tv.ui.player.PlayerScreen
import com.walkman.tv.ui.recommend.RecommendScreen
import com.walkman.tv.ui.search.SearchScreen
import com.walkman.tv.ui.settings.SettingsScreen
import com.walkman.tv.ui.songlist.SonglistScreen
import com.walkman.tv.ui.theme.AppColors

@Composable
fun RootScreen(modifier: Modifier = Modifier) {
    var section by remember { mutableStateOf(NavSection.Recommend) }
    var showPlayer by remember { mutableStateOf(false) }
    var showExitDialog by remember { mutableStateOf(false) }
    val recommendFocus = remember { FocusRequester() }
    val context = LocalContext.current

    // Subscribe only to the bits the TopNav cares about — without distinctUntilChanged the 1Hz
    // position ticker would recompose the whole root and TopNav once per second.
    data class NavPlaybackBits(val title: String?, val picURL: String?, val isPlaying: Boolean)
    val navBits by remember {
        appContainer.playbackController.state
            .map { NavPlaybackBits(it.currentTrack?.name, it.currentTrack?.picURL, it.isPlaying) }
            .distinctUntilChanged()
    }.collectAsState(initial = NavPlaybackBits(null, null, false))

    LaunchedEffect(Unit) { runCatching { recommendFocus.requestFocus() } }

    // Top-level back handler: non-Recommend sections jump back to Recommend; Recommend asks to exit.
    // Inner overlays (PlayerScreen / songlist-detail) register their own BackHandlers which take
    // precedence over this one.
    BackHandler(enabled = !showPlayer && !showExitDialog) {
        if (section != NavSection.Recommend) {
            section = NavSection.Recommend
        } else {
            showExitDialog = true
        }
    }

    Box(modifier = modifier) {
        Column(Modifier.fillMaxSize()) {
            TopNav(
                active = section,
                onSelect = { section = it },
                recommendFocusRequester = recommendFocus,
                nowPlayingTitle = navBits.title,
                nowPlayingPicURL = navBits.picURL,
                nowPlayingIsPlaying = navBits.isPlaying,
                onOpenPlayer = { showPlayer = true },
            )
            Box(modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
                val openPlayer = { showPlayer = true }
                when (section) {
                    NavSection.Recommend -> RecommendScreen(onNavigate = { section = it }, onOpenPlayer = openPlayer)
                    NavSection.Search -> SearchScreen(onOpenPlayer = openPlayer)
                    NavSection.Leaderboard -> LeaderboardScreen(onOpenPlayer = openPlayer)
                    NavSection.Songlist -> SonglistScreen(onOpenPlayer = openPlayer)
                    NavSection.Library -> LibraryScreen(onOpenPlayer = openPlayer)
                    NavSection.Settings -> SettingsScreen()
                }
            }
        }

        if (showPlayer) {
            PlayerScreen(
                onClose = { showPlayer = false },
                modifier = Modifier.fillMaxSize().background(AppColors.BgDeep),
            )
        }

        if (showExitDialog) {
            ExitConfirmDialog(
                onCancel = { showExitDialog = false },
                onConfirm = {
                    // Cleanly shut down before the process dies, otherwise ExoPlayer keeps
                    // the audio focus / surfaces and we hear playback after Activity.finish.
                    // Snapshot the playback state to disk *before* killing the process so the
                    // next launch can pick up where we left off.
                    runCatching { appContainer.saveSnapshotNow() }
                    runCatching { appContainer.playbackController.stop() }
                    runCatching { appContainer.playbackController.release() }
                    (context as? Activity)?.finishAndRemoveTask()
                    android.os.Process.killProcess(android.os.Process.myPid())
                },
            )
        }
    }
}

@Composable
private fun ExitConfirmDialog(onCancel: () -> Unit, onConfirm: () -> Unit) {
    // Initial focus goes to '取消' so an accidental OK doesn't quit the app.
    val cancelFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { cancelFocus.requestFocus() } }

    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = Modifier
                .width(360.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(AppColors.BgPanel)
                .padding(horizontal = 24.dp, vertical = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "退出应用",
                color = AppColors.TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = "确定要退出随便听吗？",
                color = AppColors.TextSecondary,
                fontSize = 14.sp,
            )
            Spacer(Modifier.height(22.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
            ) {
                TvPill(
                    onClick = onCancel,
                    focusRequester = cancelFocus,
                    contentPadding = PaddingValues(horizontal = 28.dp, vertical = 10.dp),
                ) {
                    Text("取消", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
                TvPill(
                    onClick = onConfirm,
                    selected = true, // green-tinted so '确定' reads as the danger option
                    contentPadding = PaddingValues(horizontal = 28.dp, vertical = 10.dp),
                ) {
                    Text("确定", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
