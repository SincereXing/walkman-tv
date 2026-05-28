package cn.toside.music.mobile.ui

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import cn.toside.music.mobile.ui.components.TopNav

@Composable
fun RootScreen(modifier: Modifier = Modifier) {
    var section by remember { mutableStateOf(NavSection.Recommend) }
    val recommendFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        runCatching { recommendFocus.requestFocus() }
    }

    Column(modifier = modifier) {
        TopNav(
            active = section,
            onSelect = { section = it },
            recommendFocusRequester = recommendFocus,
        )
        Box(modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
            SectionContent(section)
        }
    }
}

@Composable
private fun SectionContent(section: NavSection) {
    // Placeholder bodies — replaced by real screens in the UI milestone.
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = section.label)
    }
}
