package cn.toside.music.mobile.ui

import cn.toside.music.mobile.App
import cn.toside.music.mobile.data.model.Track
import cn.toside.music.mobile.di.AppContainer

/** Convenience accessor for the app-wide container from composables. */
val appContainer: AppContainer get() = App.container

/** Start playing [tracks] at [index]. */
fun playList(tracks: List<Track>, index: Int) {
    if (tracks.isEmpty()) return
    appContainer.playbackController.setQueue(tracks, index, autoPlay = true)
}
