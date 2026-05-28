package cn.toside.music.mobile.source

import cn.toside.music.mobile.data.model.Quality
import cn.toside.music.mobile.data.model.SourceID
import cn.toside.music.mobile.data.model.Track

/** How a play URL was produced — surfaced to the UI for transparency (iOS `ResolveOrigin`). */
sealed interface ResolveOrigin {
    val label: String

    data class Script(val name: String) : ResolveOrigin {
        override val label get() = "脚本: $name"
    }
    data object DirectFallback : ResolveOrigin {
        override val label get() = "内置直连"
    }
    data class OtherSource(val source: SourceID) : ResolveOrigin {
        override val label get() = "换源: ${source.displayName}"
    }
    data object LocalFile : ResolveOrigin {
        override val label get() = "本地"
    }
}

/** Resolved playable track (iOS `ResolvedTrack`). [warning] is a soft notice for the UI. */
data class ResolvedTrack(
    val url: String,
    val origin: ResolveOrigin,
    val quality: Quality,
    val warning: String? = null,
)

/** Implemented by the catalog layer so [OtherSourceFinder] can search other platforms. */
interface MusicSearcher {
    suspend fun search(source: SourceID, keyword: String, page: Int = 1): List<Track>
}
