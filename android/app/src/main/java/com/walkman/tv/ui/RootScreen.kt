package com.walkman.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import com.walkman.tv.ui.leaderboard.LeaderboardScreen
import com.walkman.tv.ui.library.LibraryScreen
import com.walkman.tv.ui.player.PlayerScreen
import com.walkman.tv.ui.recommend.RecommendScreen
import com.walkman.tv.ui.search.SearchScreen
import com.walkman.tv.ui.settings.SettingsScreen
import com.walkman.tv.ui.songlist.SonglistScreen
import com.walkman.tv.ui.theme.AppColors
import com.walkman.tv.ui.components.TopNav

@Composable
fun RootScreen(modifier: Modifier = Modifier) {
    var section by remember { mutableStateOf(NavSection.Recommend) }
    var showPlayer by remember { mutableStateOf(false) }
    val recommendFocus = remember { FocusRequester() }
    val playbackState = appContainer.playbackController.state.collectAsState().value

    LaunchedEffect(Unit) { runCatching { recommendFocus.requestFocus() } }

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
    }
}
