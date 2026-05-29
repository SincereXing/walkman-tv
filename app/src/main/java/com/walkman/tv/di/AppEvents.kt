package com.walkman.tv.di

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Process-wide hardware-key event bus. [MainActivity] forwards remote key presses that need to
 * cross the AndroidView boundary (e.g. KEYCODE_MENU pressed while a PlayerView has focus) into
 * the Compose tree via these flows.
 */
class AppEvents {
    private val _menuKey = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val menuKey: SharedFlow<Unit> = _menuKey.asSharedFlow()

    fun postMenuKey() {
        _menuKey.tryEmit(Unit)
    }
}
