package cn.toside.music.mobile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import cn.toside.music.mobile.ui.leaderboard.LeaderboardScreen
import cn.toside.music.mobile.ui.library.LibraryScreen
import cn.toside.music.mobile.ui.player.PlayerScreen
import cn.toside.music.mobile.ui.recommend.RecommendScreen
import cn.toside.music.mobile.ui.search.SearchScreen
import cn.toside.music.mobile.ui.settings.SettingsScreen
import cn.toside.music.mobile.ui.songlist.SonglistScreen
import cn.toside.music.mobile.ui.theme.AppColors
import cn.toside.music.mobile.ui.components.TopNav

@Composable
fun RootScreen(modifier: Modifier = Modifier) {
    var section by remember { mutableStateOf(NavSection.Recommend) }
    var showPlayer by remember { mutableStateOf(false) }
    val recommendFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) { runCatching { recommendFocus.requestFocus() } }

    Box(modifier = modifier) {
        Column(Modifier.fillMaxSize()) {
            TopNav(active = section, onSelect = { section = it }, recommendFocusRequester = recommendFocus)
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
