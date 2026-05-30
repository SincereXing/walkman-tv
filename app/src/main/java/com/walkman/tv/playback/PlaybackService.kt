package com.walkman.tv.playback

import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.walkman.tv.App

/**
 * Publishes the shared ExoPlayer as a MediaSession so the TV remote / system media controls
 * (play/pause/next/prev) and background audio work. The player itself is owned by
 * [PlaybackController]; this service only wraps it in a session.
 */
class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        runCatching {
            val player = App.container.playbackController.player
            session = MediaSession.Builder(this, player).build()
        }.onFailure {
            android.util.Log.e("PlaybackService", "MediaSession init failed", it)
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onDestroy() {
        // Do not release the player here — it is app-owned by PlaybackController.
        session?.release()
        session = null
        super.onDestroy()
    }
}
