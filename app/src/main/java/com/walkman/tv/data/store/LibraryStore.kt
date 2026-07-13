package com.walkman.tv.data.store

import android.content.Context
import com.walkman.tv.data.model.Playlist
import com.walkman.tv.data.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.io.File

@Serializable
private data class LibraryData(
    val love: Playlist = Playlist(Playlist.LOVE, Playlist.LOVE_NAME),
    val history: Playlist = Playlist(Playlist.HISTORY, Playlist.HISTORY_NAME),
    val userLists: List<Playlist> = emptyList(),
)

/**
 * Favorites (我的收藏), auto-recorded play history (取代「试听列表」), and user playlists.
 * Persisted as one JSON file; exposed as StateFlows for the UI.
 */
class LibraryStore(context: Context) {
    private val store = JsonStore(
        File(context.filesDir, "library.json"),
        LibraryData.serializer(),
        LibraryData(),
    )

    private val _love = MutableStateFlow(Playlist(Playlist.LOVE, Playlist.LOVE_NAME))
    val love: StateFlow<Playlist> = _love.asStateFlow()

    private val _history = MutableStateFlow(Playlist(Playlist.HISTORY, Playlist.HISTORY_NAME))
    val history: StateFlow<Playlist> = _history.asStateFlow()

    private val _userLists = MutableStateFlow<List<Playlist>>(emptyList())
    val userLists: StateFlow<List<Playlist>> = _userLists.asStateFlow()

    suspend fun loadAll() {
        val data = withContext(Dispatchers.IO) { store.load() }
        // Force the built-in names to whatever the current model declares (so renames in the
        // model — e.g. "我的收藏" → "我喜欢的" — apply on next launch without a migration step).
        _love.value = data.love.copy(name = Playlist.LOVE_NAME)
        _history.value = data.history.copy(name = Playlist.HISTORY_NAME)
        _userLists.value = data.userLists
    }

    fun isFavorite(trackId: String): Boolean = _love.value.tracks.any { it.id == trackId }

    suspend fun toggleFavorite(track: Track) {
        val tracks = _love.value.tracks
        _love.value = _love.value.copy(
            tracks = if (tracks.any { it.id == track.id }) tracks.filter { it.id != track.id }
            else listOf(track) + tracks,
            updatedAt = System.currentTimeMillis(),
        )
        persist()
    }

    suspend fun recordHistory(track: Track) {
        val deduped = listOf(track) + _history.value.tracks.filter { it.id != track.id }
        _history.value = _history.value.copy(tracks = deduped.take(200), updatedAt = System.currentTimeMillis())
        persist()
    }

    suspend fun clearHistory() {
        _history.value = _history.value.copy(tracks = emptyList())
        persist()
    }

    suspend fun createList(name: String): Playlist {
        val list = Playlist(name = name)
        _userLists.value = _userLists.value + list
        persist()
        return list
    }

    suspend fun deleteList(id: String) {
        _userLists.value = _userLists.value.filter { it.id != id }
        persist()
    }

    suspend fun addToList(listId: String, track: Track) {
        _userLists.value = _userLists.value.map {
            if (it.id == listId && it.tracks.none { t -> t.id == track.id }) {
                it.copy(tracks = it.tracks + track, updatedAt = System.currentTimeMillis())
            } else {
                it
            }
        }
        persist()
    }

    /** Batch add — one StateFlow update + one disk write. Songlist import uses this instead of
     *  per-track [addToList] so a several-hundred-track import doesn't rewrite library.json once
     *  per song (O(n²) serialization work that can stall low-end TVs). Dedupes against the list
     *  and within [tracks]. */
    suspend fun addAllToList(listId: String, tracks: List<Track>) {
        _userLists.value = _userLists.value.map { pl ->
            if (pl.id == listId) {
                val seen = pl.tracks.mapTo(HashSet()) { it.id }
                val fresh = tracks.filter { seen.add(it.id) }
                if (fresh.isEmpty()) pl
                else pl.copy(tracks = pl.tracks + fresh, updatedAt = System.currentTimeMillis())
            } else {
                pl
            }
        }
        persist()
    }

    suspend fun removeFromList(listId: String, trackId: String) {
        _userLists.value = _userLists.value.map {
            if (it.id == listId) it.copy(tracks = it.tracks.filter { t -> t.id != trackId }) else it
        }
        persist()
    }

    private suspend fun persist() = withContext(Dispatchers.IO) {
        store.save(LibraryData(_love.value, _history.value, _userLists.value))
    }
}
