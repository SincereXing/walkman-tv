package com.walkman.tv.di

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Process-wide event bus. [MainActivity] forwards hardware-key presses that need to cross the
 * AndroidView boundary (e.g. KEYCODE_MENU while a PlayerView has focus). [LocalServer] forwards
 * payloads received over the phone-to-TV QR flow (search keyword, custom-source script URL or
 * raw script content).
 */
class AppEvents {
    private val _menuKey = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val menuKey: SharedFlow<Unit> = _menuKey.asSharedFlow()

    private val _qrSearchKeyword = MutableSharedFlow<String>(
        extraBufferCapacity = 4,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val qrSearchKeyword: SharedFlow<String> = _qrSearchKeyword.asSharedFlow()

    private val _qrScriptUrl = MutableSharedFlow<String>(
        extraBufferCapacity = 4,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val qrScriptUrl: SharedFlow<String> = _qrScriptUrl.asSharedFlow()

    private val _qrScriptText = MutableSharedFlow<String>(
        extraBufferCapacity = 2,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val qrScriptText: SharedFlow<String> = _qrScriptText.asSharedFlow()

    private val _qrPlaylistName = MutableSharedFlow<String>(
        extraBufferCapacity = 4,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val qrPlaylistName: SharedFlow<String> = _qrPlaylistName.asSharedFlow()

    /** Emitted when the full-screen player overlay closes — TrackLists collect this to restore
     *  focus on the row that was last clicked. */
    private val _playerClosed = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val playerClosed: SharedFlow<Unit> = _playerClosed.asSharedFlow()

    fun postMenuKey() { _menuKey.tryEmit(Unit) }
    fun postQrSearchKeyword(q: String) { _qrSearchKeyword.tryEmit(q) }
    fun postQrScriptUrl(url: String) { _qrScriptUrl.tryEmit(url) }
    fun postQrScriptText(text: String) { _qrScriptText.tryEmit(text) }
    fun postQrPlaylistName(name: String) { _qrPlaylistName.tryEmit(name) }
    fun postPlayerClosed() { _playerClosed.tryEmit(Unit) }
}
