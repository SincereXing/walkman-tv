package com.walkman.tv.playback.imports

import com.walkman.tv.data.model.SonglistInfo
import com.walkman.tv.data.model.SourceID
import com.walkman.tv.data.store.LibraryStore
import com.walkman.tv.source.catalog.Songlists

/** URL/ID parser result. Source + id uniquely identifies a platform playlist. */
data class SonglistRef(val source: SourceID, val id: String)

sealed class ImportError(message: String) : Throwable(message) {
    object UnrecognizedURL : ImportError("无法识别歌单链接，请粘贴酷我 / 酷狗 / QQ / 网易云的歌单分享链接")
    class FetchFailed(detail: String) : ImportError("获取歌单失败：$detail")
    object EmptyPlaylist : ImportError("歌单为空或暂时无法读取")
}

data class ImportResult(val playlistId: String, val count: Int)

/**
 * URL → SonglistRef parser + import orchestrator. Spec docs/songlist-import-spec-android-tv.md.
 *
 * Parse rules are domain-anchored so there's no cross-platform mis-match risk; order is
 * irrelevant. UI layer calls [parse] reactively on every keystroke and [pureIdOrNull] as a
 * fallback when [parse] fails (lets the user pair a bare ID with a manual source picker).
 */
object SonglistImporter {

    private val rules: List<Pair<SourceID, List<Regex>>> = listOf(
        SourceID.WY to listOf(
            Regex("music\\.163\\.com[^\\s]*?[?#&]id=(\\d+)", RegexOption.IGNORE_CASE),
        ),
        SourceID.TX to listOf(
            Regex("y\\.qq\\.com/n/ryqq/playlist/(\\d+)", RegexOption.IGNORE_CASE),
            Regex("qq\\.com[^\\s]*?taoge\\.html[^\\s]*?[?&]id=(\\d+)", RegexOption.IGNORE_CASE),
            Regex("y\\.qq\\.com[^\\s]*?/playsquare/(\\d+)", RegexOption.IGNORE_CASE),
        ),
        SourceID.KG to listOf(
            Regex("kugou\\.com/songlist/(\\d+)", RegexOption.IGNORE_CASE),
            Regex("kugou\\.com[^\\s]*?[?&]listid=(\\d+)", RegexOption.IGNORE_CASE),
            Regex("kugou\\.com[^\\s]*?[?&]global_collection_id=([0-9A-Fa-f]+)", RegexOption.IGNORE_CASE),
        ),
        SourceID.KW to listOf(
            Regex("kuwo\\.cn[^\\s]*?/playlist_detail/(\\d+)", RegexOption.IGNORE_CASE),
            Regex("kuwo\\.cn[^\\s]*?[?&]pid=(\\d+)", RegexOption.IGNORE_CASE),
        ),
    )

    fun parse(raw: String): SonglistRef? {
        val s = raw.trim()
        if (s.isEmpty()) return null
        for ((source, patterns) in rules) {
            for (pat in patterns) {
                val m = pat.find(s) ?: continue
                val id = m.groupValues.getOrNull(1)?.takeIf { it.isNotEmpty() } ?: continue
                return SonglistRef(source, id)
            }
        }
        return null
    }

    fun pureIdOrNull(raw: String): String? {
        val s = raw.trim()
        return if (s.isNotEmpty() && s.all { it.isDigit() }) s else null
    }

    /** Orchestrator: fetch detail → create user playlist → dump tracks via LibraryStore. */
    suspend fun importPlaylist(
        ref: SonglistRef,
        customName: String?,
        songlists: Songlists,
        library: LibraryStore,
    ): ImportResult {
        val svc = songlists.serviceFor(ref.source) ?: throw ImportError.UnrecognizedURL

        // Stub only needs id + source — fetchDetail looks at those, the rest get overwritten.
        val stub = SonglistInfo(
            id = ref.id, source = ref.source,
            name = "", author = "", picURL = null, trackCount = null, playCount = null,
        )
        val detail = try {
            svc.fetchDetail(stub)
        } catch (e: Exception) {
            throw ImportError.FetchFailed(e.localizedMessage ?: e.toString())
        }
        if (detail.tracks.isEmpty()) throw ImportError.EmptyPlaylist

        val trimmed = customName?.trim().orEmpty()
        val name = when {
            trimmed.isNotEmpty() -> trimmed
            detail.info.name.isNotEmpty() -> detail.info.name
            else -> "导入的歌单"
        }
        val playlist = library.createList(name)
        // LibraryStore.addToList is per-track + dedupes by trackId — fine for a few hundred items.
        detail.tracks.forEach { library.addToList(playlist.id, it) }
        return ImportResult(playlistId = playlist.id, count = detail.tracks.size)
    }
}
