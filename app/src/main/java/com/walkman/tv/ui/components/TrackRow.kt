package com.walkman.tv.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.walkman.tv.data.model.Track
import com.walkman.tv.ui.theme.AppColors

/** One focusable track row: index, cover, name/singer, quality badge + source chip. */
@Composable
fun TrackRow(
    track: Track,
    index: Int,
    modifier: Modifier = Modifier,
    nowPlaying: Boolean = false,
    onClick: () -> Unit,
) {
    TvFocusable(onClick = onClick, modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.width(28.dp), contentAlignment = Alignment.Center) {
                Text(
                    "${index + 1}",
                    color = if (nowPlaying) AppColors.AccentGreen else AppColors.TextMuted,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Artwork(track.picURL, modifier = Modifier.size(44.dp))
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    track.name,
                    color = if (nowPlaying) AppColors.AccentGreen else AppColors.TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    track.subtitle,
                    color = AppColors.TextSecondary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            QualityBadge(track.qualities)
            Spacer(Modifier.width(8.dp))
            SourceChip(track.source)
        }
    }
}

/** A focusable list of tracks. */
@Composable
fun TrackList(
    tracks: List<Track>,
    modifier: Modifier = Modifier,
    nowPlayingId: String? = null,
    contentPadding: PaddingValues = PaddingValues(vertical = 8.dp),
    onPlay: (index: Int) -> Unit,
) {
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        contentPadding = contentPadding,
    ) {
        itemsIndexed(tracks, key = { _, t -> t.id }) { index, track ->
            TrackRow(
                track = track,
                index = index,
                nowPlaying = track.id == nowPlayingId,
                onClick = { onPlay(index) },
            )
        }
    }
}
