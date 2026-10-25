package com.walkman.tv.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.unit.dp
import com.walkman.tv.ui.theme.AppColors
import kotlinx.coroutines.delay
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.compose.ui.platform.SoftwareKeyboardController

/**
 * Makes Back leave the text field instead of only closing the IME. By default the IME eats Back
 * and hides itself while focus stays in the field — on some TV IMEs that re-opens the keyboard
 * right away, trapping the remote in a show/hide loop. We intercept Back *before* the IME sees
 * it, hide the keyboard ourselves and move focus to the next control (the dialog's buttons).
 * A second Back then reaches the dialog normally and dismisses it.
 *
 * [target] picks where focus lands (e.g. the「导入」button next to the field); null keeps the
 * default of moving down.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun Modifier.leaveFieldOnBack(target: FocusRequester? = null): Modifier {
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    return onInterceptKeyBeforeSoftKeyboard { event ->
        if (event.key != Key.Back) return@onInterceptKeyBeforeSoftKeyboard false
        // Consume both down and up so the dialog doesn't also dismiss on this press.
        if (event.type == KeyEventType.KeyUp) {
            if (target != null) {
                runCatching { keyboard?.hide() }
                if (runCatching { target.requestFocus() }.isFailure) leaveField(focusManager, keyboard)
            } else {
                leaveField(focusManager, keyboard)
            }
        }
        true
    }
}

/** Hide the IME and move focus off the current text field (down, else just clear it). */
fun leaveField(focusManager: FocusManager, keyboard: SoftwareKeyboardController?) {
    runCatching { keyboard?.hide() }
    if (!focusManager.moveFocus(FocusDirection.Down)) focusManager.clearFocus(force = true)
}

/**
 * 电视上的输入框外壳：遥控器移动焦点时只停在外壳上（显示选中框），不激活输入法；按确认键才把
 * 焦点交给里面的输入框、弹出键盘，输入框失去焦点后退回外壳状态。
 *
 * 不这样做的话，焦点只要经过输入框，电视输入法就会在后台挂上，焦点移走后仍会截走下一次确认键，
 * 表现为附近的按钮、开关「第一次按没反应」，离开页面再回来才正常。
 *
 * 按返回键收起键盘，焦点回到外壳上（调用方不用再加 [leaveFieldOnBack]）。
 *
 * [content] 收到的 Modifier 要加在真正的输入框上。
 */
@Composable
fun TvTextFieldHost(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(10.dp),
    content: @Composable (fieldModifier: Modifier) -> Unit,
) {
    val fieldFocus = remember { FocusRequester() }
    val hostFocus = remember { FocusRequester() }
    var editing by remember { mutableStateOf(false) }
    var hostFocused by remember { mutableStateOf(false) }
    var fieldHadFocus by remember { mutableStateOf(false) }
    // 输入框所在的窗口（弹窗里是弹窗自己的窗口）。不在输入时给窗口加 FLAG_ALT_FOCUSABLE_IM，让它暂时
    // 不接收输入法：键盘弹出过一次后，输入法会一直挂在窗口上，系统分发按键时先交给它，下一次确认键
    // 就被吃掉了（设置页开关「第一次按没反应」）。按确认开始输入时先去掉这个标志，再把焦点交给输入框。
    val view = LocalView.current
    val window = remember(view) { (view.parent as? DialogWindowProvider)?.window ?: view.context.findActivity()?.window }
    LaunchedEffect(editing) {
        val flag = WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM
        if (!editing) {
            runCatching { window?.addFlags(flag) }
            return@LaunchedEffect
        }
        runCatching { window?.clearFlags(flag) }
        delay(50)
        repeat(10) {
            if (runCatching { fieldFocus.requestFocus() }.isSuccess) return@LaunchedEffect
            delay(30)
        }
        editing = false
    }
    Box(
        modifier = modifier
            .border(2.dp, if (hostFocused) AppColors.Focus else Color.Transparent, shape)
            .focusRequester(hostFocus)
            .onFocusChanged { hostFocused = it.isFocused }
            .onKeyEvent { evt ->
                if (editing) return@onKeyEvent false
                val isOk = evt.key == Key.DirectionCenter || evt.key == Key.Enter || evt.key == Key.NumPadEnter
                if (!isOk) return@onKeyEvent false
                if (evt.type == KeyEventType.KeyUp) editing = true
                true
            }
            .focusable(),
    ) {
        content(
            Modifier
                .focusRequester(fieldFocus)
                .focusProperties { canFocus = editing }
                .onFocusChanged { fs ->
                    if (fs.isFocused) {
                        fieldHadFocus = true
                    } else if (fieldHadFocus) {
                        fieldHadFocus = false
                        editing = false
                    }
                }
                // 返回键：收起键盘，焦点回到外壳上。
                .leaveFieldOnBack(hostFocus),
        )
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
