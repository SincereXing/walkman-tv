package com.walkman.tv.data.store

import android.content.Context
import com.walkman.tv.data.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Cache for embedded covers extracted from downloaded / locally-imported audio files.
 * Spec §5 + §8.3.
 *
 * Files land at `Caches/EmbeddedCovers/<safeTrackID>.img` — raw image bytes, no re-encoding.
 * MIME isn't tracked; AsyncImage decodes by magic bytes.
 *
 * Two public surfaces:
 *  - [put] / [get] — direct file I/O for tag-writer / reader code.
 *  - [displayCoverURL] — UI helper. Returns a `file://...` URI if there's a cached cover,
 *    else falls back to the network URL on [Track.picURL].
 *
 * [cachedTrackIDs] is a hot set the UI can observe to invalidate Coil's memory cache when
 * a new cover lands (Coil keys on URL, but the URL doesn't change between a missing cache
 * and a present cache — so we bump a counter on writes that the AsyncImage callsites watch).
 */
class CoverCache(context: Context) {

    private val dir: File =
        File(context.cacheDir, "EmbeddedCovers").apply { mkdirs() }

    private val _cachedTrackIDs = MutableStateFlow<Set<String>>(emptySet())
    val cachedTrackIDs: StateFlow<Set<String>> = _cachedTrackIDs.asStateFlow()

    /** Cheap sanitisation — trackID is already file-safe (`kw_665163`, `local_<hash>`). */
    private fun fileFor(trackID: String): File = File(dir, "${sanitise(trackID)}.img")

    suspend fun loadAll() = withContext(Dispatchers.IO) {
        val ids = dir.listFiles()
            ?.filter { it.isFile && it.length() > 0 }
            ?.map { it.nameWithoutExtension }
            ?.toSet()
            ?: emptySet()
        _cachedTrackIDs.value = ids
    }

    /** Persist cover bytes for [trackID]. Called from the tag-writer success path. */
    suspend fun put(trackID: String, data: ByteArray) = withContext(Dispatchers.IO) {
        if (data.isEmpty()) return@withContext
        val f = fileFor(trackID)
        runCatching {
            f.outputStream().use { it.write(data) }
            _cachedTrackIDs.value = _cachedTrackIDs.value + trackID
        }
    }

    fun get(trackID: String): File? = fileFor(trackID).takeIf { it.isFile && it.length() > 0 }

    suspend fun remove(trackID: String) = withContext(Dispatchers.IO) {
        if (fileFor(trackID).delete()) {
            _cachedTrackIDs.value = _cachedTrackIDs.value - trackID
        }
    }

    /**
     * Return the best image URL to show for [track]:
     *  - local cache file URI if we've extracted a cover (offline + high-res when downloaded),
     *  - else the network [Track.picURL],
     *  - else null.
     */
    fun displayCoverURL(track: Track): String? {
        get(track.id)?.let { return it.toURI().toString() }
        return track.picURL?.takeIf { it.isNotBlank() }
    }

    private fun sanitise(id: String): String =
        id.map { c -> if (c.isLetterOrDigit() || c == '_' || c == '-' || c == '.') c else '_' }
            .joinToString("")
}
