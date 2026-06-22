package com.walkman.tv.data.model

import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * Models for downloaded songs and local-imported folders. Spec
 * docs/download-localimport-spec-android-tv.md §2.
 *
 * - [DownloadRecord] tracks one downloaded (or in-progress / failed) song. Stored in a
 *   map keyed by [Track.id] inside [com.walkman.tv.data.store.DownloadStore].
 * - [DownloadFolder] groups multiple records — a user-visible "downloaded sub-playlist."
 * - [LocalFolderRecord] anchors a SAF-imported tree to its persistent permission Uri.
 */

enum class DownloadStatus {
    DOWNLOADING, COMPLETED, FAILED
}

@Serializable
data class DownloadRecord(
    val track: Track,
    val quality: Quality,
    /** Relative path under the primary download dir — e.g. "歌手/专辑/01 - 歌名.flac".
     *  Empty until [com.walkman.tv.playback.download.relativePath] finishes computing
     *  the trackNumber-aware path (see spec §3.3 — first-frame race). */
    var fileName: String = "",
    var status: DownloadStatus = DownloadStatus.DOWNLOADING,
    val folderID: String,
    /** Original error message if [status] is [DownloadStatus.FAILED]. */
    var errorMessage: String? = null,
    /** Absolute path of the download root this file was saved under. null ⇒ the legacy/default
     *  app-scoped Music dir. Stored per-record so changing the download directory in settings
     *  never orphans already-downloaded files — each one keeps resolving under its own root. */
    var baseDir: String? = null,
    /** When the file was exported into a user-picked SAF folder, this is the resulting document
     *  Uri (content://…). Non-null ⇒ the file lives there (not under [baseDir]); playback +
     *  existence checks + deletion go through the ContentResolver. */
    var safUri: String? = null,
)

@Serializable
data class DownloadFolder(
    val id: String = UUID.randomUUID().toString(),
    var name: String,
    val trackIDs: MutableList<String> = mutableListOf(),
) {
    companion object {
        const val DEFAULT_ID = "default"
        const val DEFAULT_NAME = "默认"
        fun makeDefault() = DownloadFolder(id = DEFAULT_ID, name = DEFAULT_NAME)
    }
}

/**
 * Optional per-track metadata fetched before download so we can write a richer ID3/Vorbis
 * tag set. Every field is best-effort — if the detail endpoint fails we still proceed.
 */
@Serializable
data class TrackDetails(
    val trackNumber: Int? = null,
    val trackTotal: Int? = null,
    val albumArtist: String? = null,
    /** "YYYY" or "YYYY-MM-DD" */
    val releaseDate: String? = null,
    val genre: String? = null,
    val company: String? = null,
    /** Higher-resolution cover URL than [Track.picURL] when the source has one. */
    val hiResCoverURL: String? = null,
)

@Serializable
data class LocalFolderRecord(
    val id: String = UUID.randomUUID().toString(),
    var name: String,
    /** Persistent SAF tree-Uri string. Use [android.net.Uri.parse] to revive. */
    val persistedUriString: String,
)

/**
 * Whatever a tag reader could pull out of an audio file. All fields nullable — used as a
 * partial result that we merge over whatever the platform-native MediaMetadataRetriever
 * already produced.
 */
data class Tags(
    var cover: ByteArray? = null,
    var lyrics: String? = null,
    var title: String? = null,
    var artist: String? = null,
    var album: String? = null,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as Tags
        if (cover != null && other.cover != null) {
            if (!cover.contentEquals(other.cover)) return false
        } else if (cover != other.cover) return false
        return lyrics == other.lyrics && title == other.title &&
            artist == other.artist && album == other.album
    }

    override fun hashCode(): Int {
        var result = cover?.contentHashCode() ?: 0
        result = 31 * result + (lyrics?.hashCode() ?: 0)
        result = 31 * result + (title?.hashCode() ?: 0)
        result = 31 * result + (artist?.hashCode() ?: 0)
        result = 31 * result + (album?.hashCode() ?: 0)
        return result
    }
}
