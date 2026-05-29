package com.walkman.tv.ui

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
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
    val playbackState = appContainer.playbackController.state.collectAsState().value
    val context = LocalContext.current

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
                nowPlayingTitle = playbackState.currentTrack?.name,
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
            AlertDialog(
                onDismissRequest = { showExitDialog = false },
                title = { Text("退出应用") },
                text = { Text("确定要退出随便听吗？") },
                confirmButton = {
                    TextButton(onClick = { (context as? Activity)?.finish() }) {
                        Text("确定", color = AppColors.AccentGreen)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showExitDialog = false }) {
                        Text("取消", color = AppColors.TextSecondary)
                    }
                },
                containerColor = AppColors.BgPanel,
                titleContentColor = AppColors.TextPrimary,
                textContentColor = AppColors.TextSecondary,
            )
        }
    }
}
