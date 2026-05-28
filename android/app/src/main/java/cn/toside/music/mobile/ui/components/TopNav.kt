package cn.toside.music.mobile.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import cn.toside.music.mobile.ui.NavSection
import cn.toside.music.mobile.ui.theme.AppColors

/** Top navigation bar — brand + section pills + search + settings. */
@Composable
fun TopNav(
    active: NavSection,
    onSelect: (NavSection) -> Unit,
    modifier: Modifier = Modifier,
    recommendFocusRequester: FocusRequester? = null,
) {
    val pills = listOf(
        NavSection.Recommend,
        NavSection.Leaderboard,
        NavSection.Songlist,
        NavSection.Library,
    )
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Brand
        Icon(
            imageVector = Icons.Outlined.MusicNote,
            contentDescription = null,
            tint = AppColors.AccentGreen,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = "客厅音乐",
            color = AppColors.TextPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.width(20.dp))

        // Section pills
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            pills.forEach { section ->
                TvPill(
                    onClick = { onSelect(section) },
                    selected = active == section,
                    focusRequester = if (section == NavSection.Recommend) recommendFocusRequester else null,
                ) {
                    Text(
                        text = section.label,
                        fontSize = 14.sp,
                        fontWeight = if (active == section) FontWeight.Bold else FontWeight.Medium,
                    )
                }
            }
        }

        // Search pill
        TvPill(
            onClick = { onSelect(NavSection.Search) },
            selected = active == NavSection.Search,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Search, contentDescription = "搜索", modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(text = NavSection.Search.label, fontSize = 13.sp)
            }
        }
        Spacer(Modifier.width(8.dp))

        // Settings gear
        TvPill(
            onClick = { onSelect(NavSection.Settings) },
            selected = active == NavSection.Settings,
            shape = CircleShape,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(10.dp),
        ) {
            Icon(Icons.Filled.Settings, contentDescription = "设置", modifier = Modifier.size(22.dp))
        }
    }
}
