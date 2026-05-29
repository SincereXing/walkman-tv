package com.walkman.tv.playback

import com.walkman.tv.data.model.Track
import com.walkman.tv.source.OtherSourceFinder
import com.walkman.tv.source.SourceManager
import com.walkman.tv.source.builtin.BuiltInLyricResolver
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

/** Fetches lyrics: user script → direct fallback → cross-source. Ported from iOS `LyricsFetcher`. */
class LyricsFetcher(
    private val sources: SourceManager,
    private val builtIn: BuiltInLyricResolver,
    private val otherSourceFinder: OtherSourceFinder,
) {
    private val cache = ConcurrentHashMap<String, List<LyricLine>>()

    suspend fun fetch(track: Track): List<LyricLine> {
        cache[track.id]?.let { return it }
        tryScript(track)?.let { cache[track.id] = it; return it }
        builtIn.fetch(track)?.let { cache[track.id] = it; return it }
        tryOtherSources(track)?.let { cache[track.id] = it; return it }
        return emptyList()
    }

    private suspend fun tryScript(track: Track): List<LyricLine>? {
        val result = runCatching { sources.requestLyric(track) }.getOrNull() ?: return null
        val lyric = result.optString("lyric")
        val tlyric = result.optString("tlyric").ifEmpty { null }
        return LyricParser.parse(lyric, tlyric).ifEmpty { null }
    }

    private suspend fun tryOtherSources(track: Track): List<LyricLine>? {
        val candidates = otherSourceFinder.findMatches(track, maxIntervalDiff = 10)
        for (alt in candidates.take(4)) {
            val d1 = track.duration; val d2 = alt.duration
            if (d1 != null && d2 != null && d1 > 0 && d2 > 0 && abs(d1 - d2) > 10) continue
            builtIn.fetch(alt)?.let { return it }
            tryScript(alt)?.let { return it }
        }
        return null
    }

    fun clear() = cache.clear()
}
