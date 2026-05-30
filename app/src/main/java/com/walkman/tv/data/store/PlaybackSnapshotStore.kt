package com.walkman.tv.data.store

import android.content.Context
import com.walkman.tv.data.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.io.File

/** Minimal playback state persisted across app launches. URLs are not stored — they expire — so
 *  the queue is restored as Track metadata and re-resolved if the user (or auto-play) starts it. */
@Serializable
data class PlaybackSnapshot(
    val queue: List<Track> = emptyList(),
    val currentIndex: Int = -1,
    val wasPlaying: Boolean = false,
)

class PlaybackSnapshotStore(context: Context) {
    private val store = JsonStore(
        File(context.filesDir, "playback.json"),
        PlaybackSnapshot.serializer(),
        PlaybackSnapshot(),
    )

    suspend fun load(): PlaybackSnapshot = withContext(Dispatchers.IO) { store.load() }

    suspend fun save(snapshot: PlaybackSnapshot) = withContext(Dispatchers.IO) {
        store.save(snapshot)
    }

    /** Synchronous save used right before [android.os.Process.killProcess] so we don't lose the
     *  final state to the kill. The data is tiny (a few tracks + index + boolean) so blocking
     *  the calling thread is fine. */
    fun saveBlocking(snapshot: PlaybackSnapshot) = runBlocking { save(snapshot) }
}
