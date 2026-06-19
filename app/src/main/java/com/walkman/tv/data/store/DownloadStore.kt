package com.walkman.tv.data.store

import android.content.Context
import com.walkman.tv.data.model.DownloadFolder
import com.walkman.tv.data.model.DownloadRecord
import com.walkman.tv.data.model.DownloadStatus
import com.walkman.tv.data.model.Quality
import com.walkman.tv.data.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    /** Primary on-disk directory for finished audio files. App-scoped Music dir — no
     *  permissions needed on Android 10+ and survives uninstall warnings. */
    val downloadRoot: File by lazy {
        val dir = context.getExternalFilesDir(android.os.Environment.DIRECTORY_MUSIC)
            ?: File(context.filesDir, "Music")
        dir.also { it.mkdirs() }
    }

    suspend fun loadAll() {
        val foldersData = withContext(Dispatchers.IO) { foldersStore.load() }
        val recordsData = withContext(Dispatchers.IO) { recordsStore.load() }
        // Spec §3.6 / §8.1: any DOWNLOADING record left over from a previous process is by
        // definition interrupted — convert to FAILED with a stable message so the UI can
        // surface a retry button.
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
    }

    // ─── Reads ──────────────────────────────────────────────────────────────────

    fun isDownloaded(trackID: String): Boolean =
        _records.value[trackID]?.status == DownloadStatus.COMPLETED

    fun recordFor(trackID: String): DownloadRecord? = _records.value[trackID]

    fun localFile(trackID: String): File? {
        val rec = _records.value[trackID] ?: return null
        if (rec.status != DownloadStatus.COMPLETED) return null
        if (rec.fileName.isEmpty()) return null
        return File(downloadRoot, rec.fileName).takeIf { it.isFile }
    }

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
