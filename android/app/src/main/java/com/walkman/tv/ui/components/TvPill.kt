package com.walkman.tv.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import com.walkman.tv.ui.theme.AppColors

/**
 * A focusable rounded "pill" surface — the base interactive element across the TV UI.
 * Focus state shows a green border + slight scale, mirroring the RN `TvPressable` look.
 */
@Composable
fun TvPill(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(20.dp),
    containerColor: Color = AppColors.NavInactiveBg,
    selectedContainerColor: Color = AppColors.NavActiveBg,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 9.dp),
    focusRequester: FocusRequester? = null,
    content: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    Surface(
        onClick = onClick,
        modifier = modifier.let { if (focusRequester != null) it.focusRequester(focusRequester) else it },
        interactionSource = interaction,
        shape = ClickableSurfaceDefaults.shape(shape = shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) selectedContainerColor else containerColor,
            focusedContainerColor = AppColors.AccentGreen,
            pressedContainerColor = AppColors.AccentGreen,
            contentColor = AppColors.TextPrimary,
            focusedContentColor = AppColors.BgDeep,
            pressedContentColor = AppColors.BgDeep,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(BorderStroke(2.dp, AppColors.AccentGreen), shape = shape),
        ),
    ) {
        Box(modifier = Modifier.padding(contentPadding), contentAlignment = Alignment.Center) {
            content()
        }
    }
}
