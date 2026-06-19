package com.walkman.tv.playback.download

import com.walkman.tv.data.model.Quality
import com.walkman.tv.data.model.Track
import java.io.File

/**
 * Spec §3.4. File-naming utility for the on-disk download tree:
 *
 *  - Has album + track number → `歌手/专辑/NN - 歌名.ext`
 *  - Has album, no track number → `歌手/专辑 - 歌名.ext`
 *  - No album → `歌手 - 歌名.ext`
 *
 * Each path component is sanitised so it round-trips through Finder / Files / SAF without
 * any invalid-filename surprises.
 */
internal fun relativePath(track: Track, ext: String, trackNumber: Int?): String {
    val artist = sanitise(track.singer.ifBlank { "未知歌手" })
    val title = sanitise(track.name.ifBlank { "未知" })
    val album = track.albumName?.takeIf { it.isNotBlank() }?.let(::sanitise)
    return when {
        album != null && trackNumber != null && trackNumber > 0 ->
            "$artist/$album/${"%02d".format(trackNumber)} - $title.$ext"
        album != null ->
            "$artist/$album - $title.$ext"
        else ->
            "$artist - $title.$ext"
    }
}

/**
 * Sanitise a single path component:
 *  - `/`  → full-width `／` (visually similar, but legal in filenames)
 *  - `:`  → full-width `：`
 *  - other Windows-illegal chars (`?`, `*`, `"`, `<`, `>`, `|`, `\`) → `_`
 *  - leading dots stripped (hidden-file booby-trap)
 *  - empty → `未知`
 */
internal fun sanitise(s: String): String {
    var t = s.replace('/', '／').replace(':', '：')
    for (ch in listOf('?', '*', '"', '<', '>', '|', '\\')) t = t.replace(ch.toString(), "_")
    t = t.trim()
    while (t.startsWith(".")) t = t.drop(1)
    return t.ifEmpty { "未知" }
}

/**
 * If the proposed [fileName] would collide with another record OR an actual file on disk,
 * append ` (2)`, ` (3)`, … to the basename until it's unique. Used after we know the
 * sanitised path so we don't lose data when two different tracks happen to map to the same
 * relative path (e.g. covers of the same song from different sources).
 *
 * [excludeTrackID] prevents a record from colliding with its own previous entry when the
 * caller is re-downloading the same track at a different quality.
 */
internal fun uniquify(
    fileName: String,
    primaryDir: File,
    existing: Map<String, String>,
    excludeTrackID: String,
): String {
    fun taken(name: String): Boolean {
        if (existing.any { (id, n) -> id != excludeTrackID && n == name }) return true
        return File(primaryDir, name).exists()
    }
    if (!taken(fileName)) return fileName
    val dot = fileName.lastIndexOf('.')
    val base = if (dot > 0) fileName.substring(0, dot) else fileName
    val ext = if (dot > 0) fileName.substring(dot) else ""
    var n = 2
    while (taken("$base ($n)$ext")) n++
    return "$base ($n)$ext"
}

/**
 * Pick the actual file extension. Spec §3.3:
 *  - trust the URL's pathExtension when it's a known audio container (handles "I asked for
 *    Hi-Res but the server gave me 320k MP3" downgrade case),
 *  - else infer from the quality tier — `K128` / `K320` → mp3, anything else → flac.
 */
internal fun pickExtension(url: String, quality: Quality): String {
    val candidate = url.substringBefore('?').substringAfterLast('.').lowercase()
    if (candidate in audioExtWhitelist) return candidate
    return when (quality) {
        Quality.K128, Quality.K320 -> "mp3"
        else -> "flac"
    }
}

private val audioExtWhitelist = setOf("mp3", "flac", "m4a", "wav", "ogg")
