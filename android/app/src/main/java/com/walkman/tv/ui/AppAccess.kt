package com.walkman.tv.ui

import com.walkman.tv.App
import com.walkman.tv.data.model.Track
import com.walkman.tv.di.AppContainer

/** Convenience accessor for the app-wide container from composables. */
val appContainer: AppContainer get() = App.container

/** Start playing [tracks] at [index]. */
fun playList(tracks: List<Track>, index: Int) {
    if (tracks.isEmpty()) return
    appContainer.playbackController.setQueue(tracks, index, autoPlay = true)
}
