package com.walkman.tv.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.CircularProgressIndicator
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.walkman.tv.data.model.Quality
import com.walkman.tv.data.model.SourceID
import com.walkman.tv.ui.theme.AppColors
import coil.compose.AsyncImage

/** A focusable surface with green border + scale on focus — base for cards and rows.
 *  Pass [onLongClick] to wire long-press OK (tv-material Surface picks it up natively). */
@Composable
fun TvFocusable(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(12.dp),
    container: Color = AppColors.Card,
    focusedContainer: Color = AppColors.CardElevated,
    onLongClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Surface(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier,
        shape = ClickableSurfaceDefaults.shape(shape = shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = container,
            focusedContainerColor = focusedContainer,
            pressedContainerColor = focusedContainer,
            contentColor = AppColors.TextPrimary,
            focusedContentColor = AppColors.TextPrimary,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.04f),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(BorderStroke(2.dp, AppColors.AccentGreen), shape = shape),
        ),
        content = { content() },
    )
}

/** Album/cover artwork via Coil with a music-note placeholder. */
@Composable
fun Artwork(
    url: String?,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(8.dp),
) {
    Box(modifier = modifier.clip(shape).background(Color(0x14FFFFFF)), contentAlignment = Alignment.Center) {
        if (url.isNullOrEmpty()) {
            Icon(Icons.Filled.MusicNote, contentDescription = null, tint = AppColors.TextMuted, modifier = Modifier.size(28.dp))
        } else {
            AsyncImage(
                model = url,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
    }
}

@Composable
fun SourceChip(source: SourceID, modifier: Modifier = Modifier) {
    val tint = source.tint()
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(tint.copy(alpha = 0.16f))
            .padding(horizontal = 7.dp, vertical = 2.dp),
    ) {
        Text(source.displayName, color = tint, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun QualityBadge(qualities: List<Quality>, modifier: Modifier = Modifier) {
    val (label, tint) = when {
        qualities.contains(Quality.FLAC24) -> "Hi-Res" to AppColors.QualityHires
        qualities.contains(Quality.FLAC) -> "SQ" to AppColors.QualityLossless
        qualities.contains(Quality.K320) -> "HQ" to AppColors.QualityHq
        else -> return
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(tint.copy(alpha = 0.18f))
            .padding(horizontal = 5.dp, vertical = 1.dp),
    ) {
        Text(label, color = tint, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun LoadingState(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = AppColors.AccentGreen)
    }
}

@Composable
fun EmptyHint(text: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, color = AppColors.TextSecondary, fontSize = 15.sp)
    }
}

fun SourceID.tint(): Color = when (this) {
    SourceID.KW -> AppColors.SourceKw
    SourceID.KG -> AppColors.SourceKg
    SourceID.TX -> AppColors.SourceTx
    SourceID.WY -> AppColors.SourceWy
    SourceID.MG -> AppColors.SourceMg
    SourceID.LOCAL -> AppColors.SourceLocal
}
