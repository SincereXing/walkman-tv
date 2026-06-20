package com.walkman.tv.playback.download

import com.walkman.tv.data.model.DownloadFolder
import com.walkman.tv.data.model.DownloadRecord
import com.walkman.tv.data.model.DownloadStatus
import com.walkman.tv.data.model.Quality
import com.walkman.tv.data.model.Track
import com.walkman.tv.data.store.CoverCache
import com.walkman.tv.data.store.DownloadStore
import com.walkman.tv.playback.LyricsFetcher
import com.walkman.tv.source.SourceManager
import com.walkman.tv.source.catalog.CatalogHttp
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
    private val lyricsFetcher: LyricsFetcher? = null,
    catalogHttp: CatalogHttp? = null,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val downloader = FileDownloader(http)
    private val detailFetcher: TrackDetailFetcher? = catalogHttp?.let { TrackDetailFetcher(it) }
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
            // Spec §3.3: kick the detail lookup in parallel with the URL resolve so the
            // track-number can land in the filename and the hi-res cover is ready by the time
            // we embed metadata.
            val detailsTask = async { detailFetcher?.fetch(track) }
            val resolved = sources.resolveMusicURL(track, quality)
            val details = detailsTask.await()

            val ext = pickExtension(resolved.url, quality)
            var fileName = relativePath(track, ext, trackNumber = details?.trackNumber)
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
                    onCompleted(track, dest, details)
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

    /**
     * Fire-and-forget metadata embedding. Spec §4. Runs on the download scope so it doesn't
     * block subsequent downloads. Any failure here only logs — the song is already on disk
     * and playable; tagging is a nice-to-have.
     */
    private fun onCompleted(track: Track, dest: File, details: com.walkman.tv.data.model.TrackDetails?) {
        scope.launch {
            runCatching {
                // 1) Cover bytes — prefer the detail-fetcher's hi-res URL, fall back to track.picURL.
                val coverUrl = details?.hiResCoverURL?.takeIf { it.isNotBlank() } ?: track.picURL
                val coverBytes = fetchCover(coverUrl)
                if (coverBytes != null && coverBytes.isNotEmpty()) {
                    coverCache.put(track.id, coverBytes)
                }
                // 2) Lyrics (best-effort via the same fetcher playback uses).
                val lyricsText = try {
                    val lines = lyricsFetcher?.fetch(track) ?: emptyList()
                    LrcSerializer.serialize(lines)
                } catch (_: Exception) { null }

                // 3) Dispatch by extension.
                val ext = dest.extension.lowercase()
                when (ext) {
                    "mp3" -> Mp3TagWriter.write(
                        file = dest,
                        title = track.name,
                        artist = track.singer,
                        album = track.albumName,
                        trackNumber = details?.trackNumber,
                        trackTotal = details?.trackTotal,
                        year = details?.releaseDate,
                        genre = details?.genre,
                        publisher = details?.company,
                        albumArtist = details?.albumArtist,
                        lyrics = lyricsText,
                        cover = coverBytes,
                    )
                    "flac" -> FlacTagWriter.write(
                        file = dest,
                        title = track.name,
                        artist = track.singer,
                        album = track.albumName,
                        trackNumber = details?.trackNumber,
                        trackTotal = details?.trackTotal,
                        year = details?.releaseDate,
                        genre = details?.genre,
                        publisher = details?.company,
                        albumArtist = details?.albumArtist,
                        lyrics = lyricsText,
                        cover = coverBytes,
                    )
                    else -> { /* m4a / wav / ogg — no writer yet, cover cache still good */ }
                }
            }.onFailure { e ->
                android.util.Log.w("DownloadCoordinator", "tag-write failed for ${track.id}: ${e.message}")
            }
        }
    }

    private fun fetchCover(url: String?): ByteArray? {
        if (url.isNullOrBlank()) return null
        return runCatching {
            val req = okhttp3.Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0")
                .build()
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                resp.body?.bytes()
            }
        }.getOrNull()
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
