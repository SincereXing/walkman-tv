package com.walkman.tv.playback.download

import com.walkman.tv.data.model.DownloadFolder
import com.walkman.tv.data.model.DownloadRecord
import com.walkman.tv.data.model.DownloadStatus
import com.walkman.tv.data.model.Quality
import com.walkman.tv.data.model.Track
import com.walkman.tv.data.store.CoverCache
import com.walkman.tv.data.store.DownloadStore
import com.walkman.tv.source.SourceManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Orchestrates the download lifecycle. Spec §3.2 + §3.3.
 *
 * Drives:
 *  1. URL resolution via [SourceManager] (same cascade as playback uses)
 *  2. Filename derivation via [relativePath] / [uniquify] / [pickExtension]
 *  3. Streaming HTTP fetch via [FileDownloader] with live progress
 *  4. Metadata embedding (deferred — wired in Phase 5)
 *  5. Cover-cache update on success
 *
 * Lives in the app container; UI calls [download] / [retry] / [removeDownload] /
 * [cancel] and observes [DownloadStore.records] + [DownloadStore.progress].
 */
class DownloadCoordinator(
    private val store: DownloadStore,
    private val sources: SourceManager,
    private val coverCache: CoverCache,
    private val http: OkHttpClient,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val downloader = FileDownloader(http)
    /** Per-track download jobs so [cancel] can interrupt cleanly. */
    private val jobs = ConcurrentHashMap<String, Job>()

    /**
     * Start downloading [track] at [quality] into [folderID]. Idempotent for
     * in-progress entries; for completed entries it deletes the old file and re-downloads
     * (so the user can switch from 320k → FLAC without leaking the old file).
     */
    fun download(track: Track, quality: Quality, folderID: String = DownloadFolder.DEFAULT_ID) {
        val existing = store.recordFor(track.id)
        if (existing?.status == DownloadStatus.DOWNLOADING) return // already in flight

        scope.launch {
            // Re-download path: drop the old file before we start.
            if (existing != null) {
                deleteFile(existing)
            }
            val record = DownloadRecord(
                track = track,
                quality = quality,
                fileName = "",
                status = DownloadStatus.DOWNLOADING,
                folderID = folderID,
            )
            store.addRecord(record)
            store.publishProgress(track.id, 0f)
            val job = scope.launch { runDownload(track, quality) }
            jobs[track.id] = job
        }
    }

    fun retry(trackID: String) {
        val rec = store.recordFor(trackID) ?: return
        if (rec.status != DownloadStatus.FAILED) return
        download(rec.track, rec.quality, rec.folderID)
    }

    /** Cancel an in-flight download and roll back its record. */
    fun cancel(trackID: String) {
        jobs.remove(trackID)?.cancel()
        scope.launch {
            store.publishProgress(trackID, null)
            val rec = store.recordFor(trackID) ?: return@launch
            if (rec.status == DownloadStatus.DOWNLOADING) {
                store.removeRecord(trackID)
                // Best-effort clean of any partial file.
                if (rec.fileName.isNotEmpty()) {
                    File(store.downloadRoot, rec.fileName).delete()
                    File(store.downloadRoot, rec.fileName + ".part").delete()
                }
            }
        }
    }

    /** Remove a finished (or failed) record — deletes the file + its cached cover. */
    fun removeDownload(trackID: String) {
        scope.launch {
            val rec = store.recordFor(trackID) ?: return@launch
            jobs.remove(trackID)?.cancel()
            deleteFile(rec)
            coverCache.remove(trackID)
            store.removeRecord(trackID)
        }
    }

    fun deleteFolder(folderID: String) {
        if (folderID == DownloadFolder.DEFAULT_ID) return
        scope.launch {
            val toRemove = store.records.value.values.filter { it.folderID == folderID }
            for (rec in toRemove) {
                deleteFile(rec)
                coverCache.remove(rec.track.id)
                store.removeRecord(rec.track.id)
            }
            store.deleteFolder(folderID)
        }
    }

    // ─── Core flow ─────────────────────────────────────────────────────────────

    private suspend fun runDownload(track: Track, quality: Quality) = coroutineScope {
        try {
            // Future TrackDetailFetcher hook — for now no trackNumber/coverHiRes lookup.
            val details = async { null /* TrackDetails? */ }

            val resolved = sources.resolveMusicURL(track, quality)
            details.await()

            val ext = pickExtension(resolved.url, quality)
            // Currently no trackNumber — defaults to "Artist/Album - Title.ext".
            var fileName = relativePath(track, ext, trackNumber = null)
            fileName = uniquify(
                fileName,
                primaryDir = store.downloadRoot,
                existing = store.records.value.mapValues { it.value.fileName },
                excludeTrackID = track.id,
            )
            store.updateRecord(track.id) { it.copy(fileName = fileName) }

            val dest = File(store.downloadRoot, fileName)
            val result = downloader.download(resolved.url, dest) { p ->
                store.publishProgress(track.id, p)
            }
            result.fold(
                onSuccess = {
                    store.updateRecord(track.id) { it.copy(status = DownloadStatus.COMPLETED) }
                    store.publishProgress(track.id, null)
                    // Metadata embedding hook — Phase 5 will write tags + cover here.
                    onCompleted(track, dest)
                },
                onFailure = { fail(track.id, it.message ?: "下载失败") },
            )
        } catch (ce: kotlinx.coroutines.CancellationException) {
            throw ce
        } catch (e: Exception) {
            fail(track.id, e.message ?: e::class.simpleName ?: "未知错误")
        } finally {
            jobs.remove(track.id)
        }
    }

    private suspend fun fail(trackID: String, message: String) {
        store.publishProgress(trackID, null)
        store.updateRecord(trackID) {
            it.copy(status = DownloadStatus.FAILED, errorMessage = message)
        }
        android.util.Log.w("DownloadCoordinator", "download $trackID failed: $message")
    }

    /** Tag-writer / cover-cache integration — placeholder until Phase 5. */
    private suspend fun onCompleted(@Suppress("UNUSED_PARAMETER") track: Track,
                                    @Suppress("UNUSED_PARAMETER") dest: File) {
        // Phase 5 will:
        //   - fetch hi-res cover (track.picURL or details.hiResCoverURL)
        //   - put bytes in coverCache
        //   - resolve lyrics via LyricsFetcher → LRC
        //   - dispatch to MP3TagWriter or FLACTagWriter based on dest.extension
    }

    private fun deleteFile(record: DownloadRecord) {
        if (record.fileName.isEmpty()) return
        val file = File(store.downloadRoot, record.fileName)
        file.delete()
        File(file.parentFile, file.name + ".part").delete()
        // Best-effort: empty album folder → delete; empty artist folder → delete.
        file.parentFile?.let { albumDir ->
            if (albumDir.isDirectory && albumDir.list().isNullOrEmpty()) {
                albumDir.delete()
                albumDir.parentFile?.let { artistDir ->
                    if (artistDir.isDirectory && artistDir.list().isNullOrEmpty() &&
                        artistDir != store.downloadRoot
                    ) {
                        artistDir.delete()
                    }
                }
            }
        }
    }
}
