package com.walkman.tv.data.store

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.walkman.tv.data.model.DownloadFolder
import com.walkman.tv.data.model.DownloadRecord
import com.walkman.tv.data.model.DownloadStatus
import com.walkman.tv.data.model.Quality
import com.walkman.tv.data.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import java.io.File

/** Combined persistence shape — keeps load order deterministic. */
@Serializable
internal data class DownloadStoreData(
    val folders: List<DownloadFolder> = emptyList(),
    val records: Map<String, DownloadRecord> = emptyMap(),
)

/** A pickable download root (a writable storage volume) for the settings UI. */
data class DownloadRootOption(val label: String, val dir: File)

/**
 * Persistent store for downloaded songs + their folder groupings. Spec §8.1.
 *
 * Two JSON files — folders list and records map — share the same parent so a transactional
 * save is just two writes. We collapse them into one combined save call to keep them in
 * sync (a record referencing a folder that doesn't exist is a bug).
 *
 * StateFlow surfaces drive the UI. Progress (transient, never persisted) lives in
 * [progress] keyed by trackID.
 *
 * The store doesn't itself perform downloads — that's [com.walkman.tv.playback.download.DownloadCoordinator].
 * This class is the source of truth for "what's downloaded / planned / failed."
 */
class DownloadStore(private val context: Context) {
    private val foldersStore = JsonStore(
        File(context.filesDir, "downloadFolders.json"),
        ListSerializer(DownloadFolder.serializer()),
        listOf(DownloadFolder.makeDefault()),
    )
    private val recordsStore = JsonStore(
        File(context.filesDir, "downloadRecords.json"),
        MapSerializer(String.serializer(), DownloadRecord.serializer()),
        emptyMap(),
    )

    private val _folders = MutableStateFlow<List<DownloadFolder>>(emptyList())
    val folders: StateFlow<List<DownloadFolder>> = _folders.asStateFlow()

    private val _records = MutableStateFlow<Map<String, DownloadRecord>>(emptyMap())
    val records: StateFlow<Map<String, DownloadRecord>> = _records.asStateFlow()

    /** Live download progress (0..1). Transient — not persisted. */
    private val _progress = MutableStateFlow<Map<String, Float>>(emptyMap())
    val progress: StateFlow<Map<String, Float>> = _progress.asStateFlow()

    /** TrackIDs of COMPLETED downloads whose file is currently missing on disk (deleted, or the
     *  storage volume isn't mounted). Reactive + not persisted — re-derived at load and updated
     *  at play time, so a restored file / re-mounted card clears automatically. */
    private val _missing = MutableStateFlow<Set<String>>(emptySet())
    val missingDownloads: StateFlow<Set<String>> = _missing.asStateFlow()

    private fun computeMissing(records: Map<String, DownloadRecord>): Set<String> =
        records.values
            .filter { it.status == DownloadStatus.COMPLETED && !fileExists(it) }
            .map { it.track.id }
            .toSet()

    /** Flag a COMPLETED download whose file just turned up missing (called from the play path). */
    fun markMissing(trackID: String) {
        _missing.update { if (trackID in it) it else it + trackID }
    }

    /** Clear the missing flag (file is back / freshly downloaded). */
    fun markPresent(trackID: String) {
        _missing.update { if (trackID in it) it - trackID else it }
    }

    /** Default on-disk directory for finished audio files: the app-scoped Music dir on primary
     *  external storage. No runtime permission needed on any Android version. */
    val defaultRoot: File by lazy {
        val dir = context.getExternalFilesDir(android.os.Environment.DIRECTORY_MUSIC)
            ?: File(context.filesDir, "Music")
        dir.also { it.mkdirs() }
    }

    /** User-configured download root (absolute path), pushed in from settings on startup +
     *  whenever it changes. null ⇒ use [defaultRoot]. */
    @Volatile
    var configuredRoot: File? = null

    /** The directory NEW downloads are written to right now. */
    val downloadRoot: File
        get() = (configuredRoot ?: defaultRoot).also { runCatching { it.mkdirs() } }

    /** Where a specific record's file physically lives — honours the root it was saved under so
     *  a later root change doesn't lose it. */
    fun rootFor(record: DownloadRecord): File =
        record.baseDir?.let { File(it) } ?: defaultRoot

    /**
     * Writable storage volumes the user can pick as a download root. One app-scoped Music dir per
     * mounted volume (internal storage + any SD card / USB drive). All are real [File] paths
     * writable without any runtime permission, so the whole download + tagging pipeline works
     * against them unchanged.
     */
    fun availableRoots(): List<DownloadRootOption> {
        val dirs = context.getExternalFilesDirs(android.os.Environment.DIRECTORY_MUSIC)
            .filterNotNull()
        return dirs.mapIndexed { index, dir ->
            runCatching { dir.mkdirs() }
            val removable = runCatching {
                android.os.Environment.isExternalStorageRemovable(dir)
            }.getOrDefault(index > 0)
            val label = when {
                index == 0 -> "内部存储"
                removable -> "外置存储 ${index}（SD卡 / U盘）"
                else -> "存储 ${index}"
            }
            DownloadRootOption(label = label, dir = dir)
        }.ifEmpty { listOf(DownloadRootOption("内部存储", defaultRoot)) }
    }

    // ─── SAF (user-picked folder) target ─────────────────────────────────────────

    /** A user-picked SAF folder to export downloads into. When non-null it takes precedence over
     *  [downloadRoot]. Pushed in from settings on startup + whenever it changes. */
    @Volatile
    var downloadTreeUri: Uri? = null

    /** True if new downloads should be exported into the SAF folder rather than a File root. */
    fun usingTree(): Boolean = downloadTreeUri != null

    /** Human-readable name of the picked SAF folder (for the settings UI). */
    fun treeDisplayName(uri: Uri): String =
        runCatching { DocumentFile.fromTreeUri(context, uri)?.name }.getOrNull()
            ?: uri.lastPathSegment?.substringAfterLast(':')?.substringAfterLast('/')
            ?: "已选文件夹"

    /**
     * Write already-finished (and, for mp3/flac, already-tagged) bytes into the SAF tree under
     * [relativePath] (e.g. "歌手/专辑/01 - 歌名.flac"), creating the directory chain as needed.
     * The document is only created once the bytes are ready, so a cancelled/failed download never
     * leaves a partial file. Returns the new document Uri string, or null on failure.
     */
    fun writeBytesToTree(tree: Uri, relativePath: String, mime: String, bytes: ByteArray): String? =
        runCatching {
            val root = DocumentFile.fromTreeUri(context, tree) ?: return null
            val parts = relativePath.split('/').filter { it.isNotEmpty() }
            if (parts.isEmpty()) return null
            var dir = root
            for (seg in parts.dropLast(1)) {
                dir = dir.findFile(seg)?.takeIf { it.isDirectory }
                    ?: dir.createDirectory(seg) ?: return null
            }
            val displayName = parts.last()
            // Replace an existing same-named file so a re-download (quality upgrade) overwrites.
            dir.findFile(displayName)?.takeIf { it.isFile }?.delete()
            val doc = dir.createFile(mime, displayName) ?: return null
            context.contentResolver.openOutputStream(doc.uri)?.use { out ->
                out.write(bytes)
            } ?: return null
            doc.uri.toString()
        }.getOrNull()

    /** Does the file backing [record] actually exist right now (File or SAF)? */
    private fun fileExists(record: DownloadRecord): Boolean {
        val saf = record.safUri
        return if (saf != null) {
            runCatching { DocumentFile.fromSingleUri(context, Uri.parse(saf))?.exists() == true }
                .getOrDefault(false)
        } else {
            record.fileName.isNotEmpty() && File(rootFor(record), record.fileName).isFile
        }
    }

    /** Best-effort delete of the file backing [record] (File or SAF). */
    fun deleteBackingFile(record: DownloadRecord) {
        val saf = record.safUri
        if (saf != null) {
            runCatching { DocumentFile.fromSingleUri(context, Uri.parse(saf))?.delete() }
            return
        }
        if (record.fileName.isEmpty()) return
        val root = rootFor(record)
        val file = File(root, record.fileName)
        file.delete()
        File(file.parentFile, file.name + ".part").delete()
        file.parentFile?.let { albumDir ->
            if (albumDir.isDirectory && albumDir.list().isNullOrEmpty()) {
                albumDir.delete()
                albumDir.parentFile?.let { artistDir ->
                    if (artistDir.isDirectory && artistDir.list().isNullOrEmpty() && artistDir != root) {
                        artistDir.delete()
                    }
                }
            }
        }
    }

    /** Playable Uri string for a completed download (File `file://` or SAF `content://`), or null
     *  if the file is gone. Used by the play-time resolver. */
    fun localPlayableUri(trackID: String): String? {
        val rec = _records.value[trackID] ?: return null
        if (rec.status != DownloadStatus.COMPLETED) return null
        if (!fileExists(rec)) return null
        return rec.safUri ?: File(rootFor(rec), rec.fileName).toURI().toString()
    }

    suspend fun loadAll() {
        val foldersData = withContext(Dispatchers.IO) { foldersStore.load() }
        val recordsData = withContext(Dispatchers.IO) { recordsStore.load() }
        // Spec §3.6 / §8.1: any DOWNLOADING record left over from a previous process is by
        // definition interrupted → FAILED("已中断") so the UI surfaces a retry button.
        val sanitised = recordsData.mapValues { (_, rec) ->
            if (rec.status == DownloadStatus.DOWNLOADING) {
                rec.copy(status = DownloadStatus.FAILED, errorMessage = "已中断")
            } else rec
        }
        // Make sure the "默认" folder is always present even on a fresh install.
        val foldersWithDefault =
            if (foldersData.any { it.id == DownloadFolder.DEFAULT_ID }) foldersData
            else listOf(DownloadFolder.makeDefault()) + foldersData
        _folders.value = foldersWithDefault
        _records.value = sanitised
        // Initial pass: which COMPLETED downloads have lost their file on disk. Kept as a
        // reactive set separate from the record's status — the record stays a download (still
        // listed in 已下载), we just know its file is gone so badges / playback can react.
        _missing.value = withContext(Dispatchers.IO) { computeMissing(sanitised) }
    }

    // ─── Reads ──────────────────────────────────────────────────────────────────

    fun isDownloaded(trackID: String): Boolean =
        _records.value[trackID]?.status == DownloadStatus.COMPLETED

    fun recordFor(trackID: String): DownloadRecord? = _records.value[trackID]

    fun tracksIn(folderID: String): List<Track> {
        val folder = _folders.value.firstOrNull { it.id == folderID } ?: return emptyList()
        return folder.trackIDs.mapNotNull { id ->
            _records.value[id]?.takeIf { it.status == DownloadStatus.COMPLETED }?.track
        }
    }

    val activeDownloads: List<DownloadRecord>
        get() = _records.value.values.filter { it.status == DownloadStatus.DOWNLOADING }

    val completedCount: Int
        get() = _records.value.values.count { it.status == DownloadStatus.COMPLETED }

    // ─── Folder CRUD ────────────────────────────────────────────────────────────

    suspend fun createFolder(name: String): DownloadFolder {
        val folder = DownloadFolder(name = name.ifBlank { "新建" })
        _folders.value = _folders.value + folder
        persist()
        return folder
    }

    suspend fun renameFolder(id: String, name: String) {
        if (name.isBlank()) return
        _folders.value = _folders.value.map { f ->
            if (f.id == id) f.copy(name = name) else f
        }
        persist()
    }

    /** Delete a folder. Records inside are not auto-removed — the caller (DownloadCoordinator)
     *  is responsible for moving / deleting files first. */
    suspend fun deleteFolder(id: String) {
        if (id == DownloadFolder.DEFAULT_ID) return // never let the default go
        _folders.value = _folders.value.filter { it.id != id }
        persist()
    }

    // ─── Record state ───────────────────────────────────────────────────────────

    suspend fun addRecord(record: DownloadRecord) {
        // Append the trackID to the folder if it isn't already there.
        _folders.value = _folders.value.map { f ->
            if (f.id == record.folderID && record.track.id !in f.trackIDs) {
                f.copy(trackIDs = (f.trackIDs + record.track.id).toMutableList())
            } else f
        }
        _records.value = _records.value + (record.track.id to record)
        persist()
    }

    suspend fun updateRecord(trackID: String, transform: (DownloadRecord) -> DownloadRecord) {
        val current = _records.value[trackID] ?: return
        val next = transform(current)
        if (next == current) return
        _records.value = _records.value + (trackID to next)
        persist()
    }

    /** Remove a record + the file. Caller responsible for actually deleting the file from disk
     *  via [localFile] before invoking; this just drops the bookkeeping. */
    suspend fun removeRecord(trackID: String) {
        if (trackID !in _records.value) return
        _records.value = _records.value - trackID
        _folders.value = _folders.value.map { f ->
            if (trackID in f.trackIDs) {
                f.copy(trackIDs = f.trackIDs.filter { it != trackID }.toMutableList())
            } else f
        }
        _progress.value = _progress.value - trackID
        _missing.value = _missing.value - trackID
        persist()
    }

    fun publishProgress(trackID: String, p: Float?) {
        _progress.value = if (p == null) {
            _progress.value - trackID
        } else {
            _progress.value + (trackID to p.coerceIn(0f, 1f))
        }
    }

    private suspend fun persist() = withContext(Dispatchers.IO) {
        foldersStore.save(_folders.value)
        recordsStore.save(_records.value)
    }
}
