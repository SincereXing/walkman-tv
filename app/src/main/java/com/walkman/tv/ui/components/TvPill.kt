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
import androidx.tv.material3.Glow
import androidx.tv.material3.Surface
import com.walkman.tv.ui.theme.AppColors

/**
 * A focusable rounded "pill" surface — the base interactive element across the TV UI.
 *
 *   - default: dim card background, light text
 *   - selected (but not focused): accent-tinted bg + accent text + thin accent outline
 *   - focused: full-accent bg + dark text + scale + accent glow (wins over selected)
 *
 *  Pass [accent] to repurpose the same pill for non-brand semantics — most importantly
 *  `accent = AppColors.Danger` for destructive actions (exit, delete, clear) so the
 *  selected/focused state reads as a warning instead of "OK, proceed".
 */
@Composable
fun TvPill(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(20.dp),
    containerColor: Color = AppColors.NavInactiveBg,
    accent: Color = AppColors.BrandPrimary,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 9.dp),
    focusRequester: FocusRequester? = null,
    onLongClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val selectedBg = accent.copy(alpha = 0.30f)
    val selectedOutline = accent.copy(alpha = 0.55f)
    // Focus border colour: use the brighter Focus token when the pill is on the brand path,
    // otherwise stay on the accent itself so danger / warning pills don't drift back to green.
    val focusBorder = if (accent == AppColors.BrandPrimary) AppColors.Focus else accent
    Surface(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier.let { if (focusRequester != null) it.focusRequester(focusRequester) else it },
        interactionSource = interaction,
        shape = ClickableSurfaceDefaults.shape(shape = shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) selectedBg else containerColor,
            focusedContainerColor = accent,
            pressedContainerColor = accent,
            contentColor = if (selected) accent else AppColors.TextPrimary,
            focusedContentColor = AppColors.BgDeep,
            pressedContentColor = AppColors.BgDeep,
        ),
        // 1.08 (was 1.06) — a touch more lift for the 3m TV viewing distance.
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.08f),
        border = ClickableSurfaceDefaults.border(
            border = if (selected) {
                Border(BorderStroke(1.dp, selectedOutline), shape = shape)
            } else {
                Border(BorderStroke(0.dp, Color.Transparent), shape = shape)
            },
            focusedBorder = Border(BorderStroke(2.dp, focusBorder), shape = shape),
        ),
        // tv-material native focus halo — gives pills visible elevation on focus instead of
        // relying on the outline alone. Tinted to the accent so danger pills glow red, etc.
        glow = ClickableSurfaceDefaults.glow(
            focusedGlow = Glow(elevationColor = accent, elevation = 10.dp),
        ),
    ) {
        Box(modifier = Modifier.padding(contentPadding), contentAlignment = Alignment.Center) {
            content()
        }
    }
}
