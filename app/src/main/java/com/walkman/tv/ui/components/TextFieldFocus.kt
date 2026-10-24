package com.walkman.tv.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onInterceptKeyBeforeSoftKeyboard
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.SoftwareKeyboardController

/**
 * Makes Back leave the text field instead of only closing the IME. By default the IME eats Back
 * and hides itself while focus stays in the field — on some TV IMEs that re-opens the keyboard
 * right away, trapping the remote in a show/hide loop. We intercept Back *before* the IME sees
 * it, hide the keyboard ourselves and move focus to the next control (the dialog's buttons).
 * A second Back then reaches the dialog normally and dismisses it.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun Modifier.leaveFieldOnBack(): Modifier {
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    return onInterceptKeyBeforeSoftKeyboard { event ->
        if (event.key != Key.Back) return@onInterceptKeyBeforeSoftKeyboard false
        // Consume both down and up so the dialog doesn't also dismiss on this press.
        if (event.type == KeyEventType.KeyUp) leaveField(focusManager, keyboard)
        true
    }
}

/**
 * Makes DPAD Right leave the text field for an explicitly chosen neighbouring control.
 * Text fields normally consume the key to move the cursor, so TV focus search never sees it.
 * Intercept both phases before the IME and move focus on KeyUp to keep the release event from
 * leaking to the newly focused control.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun Modifier.leaveFieldOnDpadRight(target: FocusRequester): Modifier {
    val keyboard = LocalSoftwareKeyboardController.current
    return onInterceptKeyBeforeSoftKeyboard { event ->
        if (event.key != Key.DirectionRight) return@onInterceptKeyBeforeSoftKeyboard false
        if (event.type == KeyEventType.KeyUp) {
            runCatching { keyboard?.hide() }
            target.requestFocus()
        }
        true
    }
}

/** Hide the IME and move focus off the current text field (down, else just clear it). */
fun leaveField(focusManager: FocusManager, keyboard: SoftwareKeyboardController?) {
    runCatching { keyboard?.hide() }
    if (!focusManager.moveFocus(FocusDirection.Down)) focusManager.clearFocus(force = true)
}
