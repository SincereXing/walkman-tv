package com.walkman.tv.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text

/**
 * Remote-friendly virtual keypad — A-Z over 5 rows + 0-9 over 2 rows + a control row
 * (删除 / 清空 / [confirmLabel]). Layout matches SearchScreen's keypad so the user gets
 * consistent column count + key sizes across screens.
 *
 * Pure stateless: parents own the current text; callbacks signal each key. Auto-focuses
 * the first key on first composition so D-pad lands somewhere sensible.
 */
@Composable
fun TvKeypad(
    onAppend: (Char) -> Unit,
    onBackspace: () -> Unit,
    onClear: () -> Unit,
    onConfirm: () -> Unit,
    confirmLabel: String = "完成",
    modifier: Modifier = Modifier,
) {
    val letterRows = listOf("ABCDEF", "GHIJKL", "MNOPQR", "STUVWX", "YZ")
    val digitRows = listOf("012345", "6789")
    val firstKey = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { firstKey.requestFocus() } }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        letterRows.forEachIndexed { rowIdx, row ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                row.forEachIndexed { colIdx, c ->
                    KeyButton(
                        label = c.toString(),
                        focusRequester = if (rowIdx == 0 && colIdx == 0) firstKey else null,
                        onClick = { onAppend(c) },
                    )
                }
            }
        }
        Spacer(Modifier.size(6.dp))
        digitRows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                row.forEach { c -> KeyButton(c.toString()) { onAppend(c) } }
            }
        }
        Spacer(Modifier.size(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            KeyButton("删除", wide = true) { onBackspace() }
            KeyButton("清空", wide = true) { onClear() }
            KeyButton(confirmLabel, wide = true, primary = true) { onConfirm() }
        }
    }
}

@Composable
private fun KeyButton(
    label: String,
    wide: Boolean = false,
    primary: Boolean = false,
    focusRequester: FocusRequester? = null,
    onClick: () -> Unit,
) {
    // Letter/digit key = 44 wide, wide key = 92 (matches 2 letter slots + a gap).
    // Letter row total: 6*44 + 5*4 = 284dp. Control row: 3*92 + 2*6 = 288dp ≈ 284dp.
    TvPill(
        onClick = onClick,
        selected = primary,
        focusRequester = focusRequester,
        contentPadding = PaddingValues(0.dp),
    ) {
        Box(
            modifier = Modifier.size(width = if (wide) 92.dp else 44.dp, height = 38.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                label,
                fontSize = if (label.length == 1) 15.sp else 13.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
        }
    }
}
