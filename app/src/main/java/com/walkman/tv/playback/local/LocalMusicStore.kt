package com.walkman.tv.playback.local

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.walkman.tv.data.model.LocalFolderRecord
import com.walkman.tv.data.model.Quality
import com.walkman.tv.data.model.SourceID
import com.walkman.tv.data.model.Track
import com.walkman.tv.data.store.CoverCache
import com.walkman.tv.data.store.LocalFolderStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest

/**
 * Local-folder import + playback resolver. Spec §6 + §10.
 *
 * Public surface:
 *   - [importFolder]      one-shot: takes a SAF tree-Uri, scans, builds Tracks, returns the list.
 *   - [fileUri]           playback: resolves `lf://<folderID>/<rel>` to a content/file Uri.
 *
 * Persisted state (the SAF root Uri + a user-facing folder name) lives in [LocalFolderStore].
 * Caller decides what to do with the Tracks (typically: create a user playlist and dump them in).
 */
class LocalMusicStore(
    private val context: Context,
    private val localFolderStore: LocalFolderStore,
    private val coverCache: CoverCache,
) {

    /** Audio file extensions we'll scan + import. Anything else is silently ignored. */
    private val audioExtensions = setOf("mp3", "flac", "m4a", "aac", "wav", "aif", "aiff")

    /**
     * Take persistent SAF permission on [treeUri], scan all audio files under it (recursively),
     * extract tags, register a [LocalFolderRecord], and return the assembled tracks ready to be
     * dropped into a playlist by the caller.
     *
     * Progress is reported via [onProgress] (0..1). Throws [IllegalArgumentException] if the
     * folder doesn't contain any audio files.
     */
    suspend fun importFolder(
        treeUri: Uri,
        playlistName: String,
        onProgress: (Float) -> Unit = {},
    ): ImportResult = withContext(Dispatchers.IO) {
        // 1) Persist the SAF permission so we can read this tree across process restarts.
        try {
            context.contentResolver.takePersistableUriPermission(
                treeUri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        } catch (e: SecurityException) {
            android.util.Log.w("LocalMusicStore", "takePersistableUriPermission failed: ${e.message}")
        }
        val folderID = java.util.UUID.randomUUID().toString()
        val root = DocumentFile.fromTreeUri(context, treeUri)
            ?: throw IllegalArgumentException("SAF tree uri unreadable")

        // 2) Walk the tree. Each entry is (DocumentFile leaf, relative path under root).
        val leaves = mutableListOf<Pair<DocumentFile, String>>()
        walk(root, "", leaves)
        leaves.sortBy { it.second.lowercase() }
        if (leaves.isEmpty()) throw IllegalArgumentException("no audio files found")

        // 3) Extract per-file metadata + persist any embedded covers.
        val tracks = ArrayList<Track>(leaves.size)
        leaves.forEachIndexed { idx, (file, relPath) ->
            val (track, cover) = readTrack(file, relPath, folderID)
            tracks += track
            cover?.let { coverCache.put(track.id, it) }
            onProgress((idx + 1).toFloat() / leaves.size)
        }

        // 4) Anchor the persistent Uri.
        val record = LocalFolderRecord(
            id = folderID,
            name = playlistName.ifBlank { root.name ?: "本地音乐" },
            persistedUriString = treeUri.toString(),
        )
        localFolderStore.add(record)
        ImportResult(record, tracks)
    }

    /**
     * Resolve a Track imported via [importFolder] back to a Uri. Returns null if the folder
     * was deleted, permission was revoked, or the file moved.
     */
    fun fileUri(track: Track): Uri? {
        val songmid = track.songmid
        if (!songmid.startsWith("lf://")) return null
        val body = songmid.removePrefix("lf://")
        val slash = body.indexOf('/')
        if (slash < 0) return null
        val folderID = body.substring(0, slash)
        val relPath = body.substring(slash + 1)
        val record = localFolderStore.find(folderID) ?: return null
        val rootUri = runCatching { Uri.parse(record.persistedUriString) }.getOrNull() ?: return null
        val root = runCatching { DocumentFile.fromTreeUri(context, rootUri) }.getOrNull() ?: return null
        return findByPath(root, relPath.split('/'))?.uri
    }

    /** Drop a folder + revoke its SAF permission. Cover-cache entries are left alone since
     *  the user may have copies of those tracks elsewhere. */
    suspend fun forgetFolder(folderID: String) = withContext(Dispatchers.IO) {
        val record = localFolderStore.find(folderID) ?: return@withContext
        runCatching {
            context.contentResolver.releasePersistableUriPermission(
                Uri.parse(record.persistedUriString),
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
        localFolderStore.remove(folderID)
    }

    data class ImportResult(val record: LocalFolderRecord, val tracks: List<Track>)

    // ─── Scan ───────────────────────────────────────────────────────────────────

    private fun walk(node: DocumentFile, prefix: String, out: MutableList<Pair<DocumentFile, String>>) {
        for (child in node.listFiles()) {
            val name = child.name ?: continue
            val rel = if (prefix.isEmpty()) name else "$prefix/$name"
            if (child.isDirectory) {
                walk(child, rel, out)
            } else if (child.isFile) {
                val ext = name.substringAfterLast('.', "").lowercase()
                if (ext in audioExtensions) out += child to rel
            }
        }
    }

    private fun findByPath(node: DocumentFile, parts: List<String>): DocumentFile? {
        var current: DocumentFile? = node
        for (part in parts) {
            current = current?.findFile(part) ?: return null
        }
        return current
    }

    // ─── Tag extraction ─────────────────────────────────────────────────────────

    /**
     * Build a Track from one audio file. Spec §6.4. Priority chain:
     *   1) MediaMetadataRetriever (Android's stock extractor)
     *   2) EmbeddedTagReader byte-level (FLAC's Vorbis Comment is unreliable in MMR)
     *   3) Filename fallback for artist/title (with track-number-prefix guard)
     */
    private fun readTrack(file: DocumentFile, relPath: String, folderID: String): Pair<Track, ByteArray?> {
        val fileNameSansExt = relPath.substringAfterLast('/').substringBeforeLast('.', relPath)
        var title = fileNameSansExt
        var artist = ""
        var album: String? = null
        var duration: Int? = null
        var cover: ByteArray? = null

        // 1) Native metadata extractor.
        runCatching {
            MediaMetadataRetriever().use { mmr ->
                mmr.setDataSource(context, file.uri)
                mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)?.takeIf { it.isNotBlank() }
                    ?.let { title = it }
                mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)?.takeIf { it.isNotBlank() }
                    ?.let { artist = it }
                mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)?.takeIf { it.isNotBlank() }
                    ?.let { album = it }
                mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
                    ?.let { duration = (it / 1000L).toInt() }
                cover = mmr.embeddedPicture
            }
        }

        // 2) Byte-level fallback for FLAC Vorbis Comment (MMR misses it) or when fields are blank.
        val needsFields = artist.isBlank() || album.isNullOrBlank() || title == fileNameSansExt
        val needsCover = cover == null
        if (needsCover || needsFields) {
            runCatching {
                context.contentResolver.openInputStream(file.uri)?.use { input ->
                    val tags = EmbeddedTagReader.read(
                        input,
                        wantCover = needsCover,
                        wantLyrics = false,
                        wantFields = needsFields,
                    )
                    if (cover == null) cover = tags.cover
                    if (artist.isBlank()) tags.artist?.takeIf { it.isNotBlank() }?.let { artist = it }
                    if (album.isNullOrBlank()) tags.album?.takeIf { it.isNotBlank() }?.let { album = it }
                    if (title == fileNameSansExt) tags.title?.takeIf { it.isNotBlank() }?.let { title = it }
                }
            }
        }

        // 3) Filename pattern fallback. "歌手 - 歌名.mp3" — but "01 - 歌名.mp3" is a
        //    track-number prefix, not a real artist. Reject when the head is all digits.
        if (artist.isBlank()) {
            val parts = title.split(" - ", limit = 2)
            if (parts.size == 2) {
                val head = parts[0].trim()
                val isAllDigits = head.isNotEmpty() && head.all { it.isDigit() }
                if (!isAllDigits) {
                    artist = head
                    title = parts[1].trim()
                } else {
                    title = parts[1].trim()
                }
            }
        }
        if (artist.isBlank()) artist = "未知歌手"

        // 4) Stable, file-safe trackID. SHA-256(folderID + "|" + relPath), first 24 hex chars.
        val hash = sha256Hex("$folderID|$relPath").take(24)
        val ext = relPath.substringAfterLast('.', "").lowercase()
        val lossless = ext in setOf("flac", "wav", "aif", "aiff")
        val track = Track(
            id = "local_$hash",
            name = title,
            singer = artist,
            albumName = album,
            source = SourceID.LOCAL,
            songmid = "lf://$folderID/$relPath",
            duration = duration,
            picURL = null, // CoverCache takes over via displayCoverURL
            qualities = listOf(if (lossless) Quality.FLAC else Quality.K320),
        )
        return track to cover
    }

    private fun sha256Hex(input: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val bytes = md.digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
