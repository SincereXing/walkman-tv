package com.walkman.tv.ui.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.walkman.tv.data.model.SourceID
import com.walkman.tv.data.model.Track
import com.walkman.tv.ui.appContainer
import com.walkman.tv.ui.components.EmptyHint
import com.walkman.tv.ui.components.LoadingState
import com.walkman.tv.ui.components.TrackList
import com.walkman.tv.ui.components.TvPill
import com.walkman.tv.ui.playList
import com.walkman.tv.ui.theme.AppColors
import kotlinx.coroutines.launch

@Composable
fun SearchScreen(onOpenPlayer: () -> Unit, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf<SourceID?>(null) }
    var results by remember { mutableStateOf<List<Track>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var searched by remember { mutableStateOf(false) }

    fun runSearch() {
        if (query.isBlank()) return
        scope.launch {
            loading = true
            searched = true
            results = runCatching {
                val f = filter
                if (f == null) appContainer.catalogs.searchAll(query) else appContainer.catalogs.search(f, query)
            }.getOrDefault(emptyList())
            loading = false
        }
    }

    Column(modifier = modifier.fillMaxSize().padding(top = 8.dp)) {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                modifier = Modifier.weight(1f),
                placeholder = { Text("搜索歌曲、歌手", color = AppColors.TextMuted) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { runSearch() }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = AppColors.TextPrimary,
                    unfocusedTextColor = AppColors.TextPrimary,
                    focusedBorderColor = AppColors.AccentGreen,
                    unfocusedBorderColor = AppColors.Card,
                    cursorColor = AppColors.AccentGreen,
                ),
            )
            Spacer(Modifier.width(12.dp))
            TvPill(onClick = { runSearch() }, selected = true) { Text("搜索", fontSize = 14.sp) }
        }

        Spacer(Modifier.padding(top = 8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SourceFilterChip("全部", filter == null) { filter = null; if (searched) runSearch() }
            SourceID.onlineSources.forEach { s ->
                SourceFilterChip(s.displayName, filter == s) { filter = s; if (searched) runSearch() }
            }
        }

        Spacer(Modifier.padding(top = 4.dp))
        when {
            loading -> LoadingState(Modifier.fillMaxSize())
            results.isNotEmpty() -> {
                val nowId = appContainer.playbackController.state.value.currentTrack?.id
                TrackList(results, modifier = Modifier.fillMaxWidth().weight(1f), nowPlayingId = nowId) { idx ->
                    playList(results, idx)
                    onOpenPlayer()
                }
            }
            searched -> EmptyHint("没有找到结果", Modifier.fillMaxSize())
            else -> EmptyHint("输入关键词开始搜索", Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun SourceFilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    TvPill(onClick = onClick, selected = selected) { Text(label, fontSize = 13.sp) }
}
