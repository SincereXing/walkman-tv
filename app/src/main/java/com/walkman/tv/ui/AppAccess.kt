package com.walkman.tv.ui

import com.walkman.tv.App
import com.walkman.tv.data.model.Track
import com.walkman.tv.di.AppContainer

/** Convenience accessor for the app-wide container from composables. */
val appContainer: AppContainer get() = App.container

/**
 * Start playing [tracks] at [index] — unless the track at that index is already the current
 * track, in which case we leave the player alone (so re-tapping the now-playing row in any
 * list opens the full-screen player without restarting playback from 00:00).
 */
fun playList(tracks: List<Track>, index: Int) {
    if (tracks.isEmpty()) return
    val target = tracks.getOrNull(index) ?: return
    val current = appContainer.playbackController.state.value.currentTrack
    if (current?.id == target.id) return
    appContainer.playbackController.setQueue(tracks, index, autoPlay = true)
}
