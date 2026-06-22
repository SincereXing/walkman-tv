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
import kotlinx.coroutines.sync.withPermit
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
    /** Caps how many downloads actually transfer at once. A batch "下载全部" enqueues every
     *  track immediately (all show as DOWNLOADING), but only N stream at a time; the rest wait
     *  on this permit so we don't open 100 sockets / thrash the disk.
     *
     *  A Semaphore can't be resized, so changing the limit (from settings) swaps in a fresh one.
     *  In-flight downloads keep the instance they already acquired and drain on it; new downloads
     *  use the replacement — so the configured limit takes effect for subsequent transfers. */
    @Volatile
    private var transferGate = kotlinx.coroutines.sync.Semaphore(DEFAULT_CONCURRENT)

    /** Apply a new concurrency limit (from settings). Clamped to a sane 1..8. */
    fun setMaxConcurrent(n: Int) {
        val permits = n.coerceIn(1, 8)
        transferGate = kotlinx.coroutines.sync.Semaphore(permits)
    }

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
                // Pin this download to the currently configured root so a later directory
                // change in settings doesn't lose the file.
                baseDir = store.downloadRoot.absolutePath,
            )
            store.addRecord(record)
            store.publishProgress(track.id, 0f)
            val job = scope.launch { runDownload(track, quality) }
            jobs[track.id] = job
        }
    }

    /**
     * Batch-download a whole playlist at [quality] into [folderID].
     *
     * Skip rules per track:
     *  - already in flight → never re-enqueue
     *  - downloaded but file missing → always re-download
     *  - downloaded + present, [redownloadOnQualityChange] = true, and its stored quality differs
     *    from [quality] → re-download to match the new target (e.g. upgrade 128k → flac)
     *  - downloaded + present, otherwise → skip
     *
     * Returns the number of tracks actually enqueued. Concurrency is capped by [transferGate].
     */
    fun downloadAll(
        tracks: List<Track>,
        quality: Quality,
        folderID: String = DownloadFolder.DEFAULT_ID,
        redownloadOnQualityChange: Boolean = true,
    ): Int {
        val missing = store.missingDownloads.value
        val pending = tracks.filter { t ->
            val rec = store.recordFor(t.id)
            when {
                rec?.status == DownloadStatus.DOWNLOADING -> false
                // Not downloaded, or its file is gone → (re)download.
                !(store.isDownloaded(t.id) && t.id !in missing) -> true
                // Present already: re-download only to apply a different target quality.
                redownloadOnQualityChange && rec?.quality != quality -> true
                else -> false
            }
        }
        pending.forEach { download(it, quality, folderID) }
        return pending.size
    }

    fun retry(trackID: String) {
        val rec = store.recordFor(trackID) ?: return
        if (rec.status != DownloadStatus.FAILED) return
        download(rec.track, rec.quality, rec.folderID)
    }

    /** Re-download a record regardless of status — used by the 已下载 tab when a COMPLETED
     *  download's file went missing (still COMPLETED, so [retry] would no-op). */
    fun redownload(trackID: String) {
        val rec = store.recordFor(trackID) ?: return
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
                // Best-effort clean of any partial File download. (SAF downloads buffer in memory
                // and only create the document on success, so there's nothing to clean there.)
                if (rec.fileName.isNotEmpty()) {
                    val root = store.rootFor(rec)
                    File(root, rec.fileName).delete()
                    File(root, rec.fileName + ".part").delete()
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
            // Gate the actual transfer so a big batch only streams N at once. Queued tracks
            // suspend here (still shown as DOWNLOADING at 0%) until a permit frees up. Capture the
            // current gate so acquire + release happen on the same instance even if the limit is
            // changed mid-flight.
            val gate = transferGate
            gate.withPermit {
                // Spec §3.3: kick the detail lookup in parallel with the URL resolve so the
                // track-number can land in the filename and the hi-res cover is ready by the time
                // we embed metadata.
                val detailsTask = async { detailFetcher?.fetch(track) }
                val resolved = sources.resolveMusicURL(track, quality)
                val details = detailsTask.await()

                // Target: a user-picked SAF folder, or a File root. Snapshot the tree here so a
                // mid-flight settings change is consistent.
                val treeUri = store.downloadTreeUri
                val ext = pickExtension(resolved.url, quality)

                if (treeUri != null) {
                    // ── SAF target ── Download into memory, tag in memory, write once to the
                    // picked folder. No scratch file on internal storage, single write to the
                    // (possibly slow) destination, and no partial file if it's cancelled.
                    var fileName = relativePath(track, ext, trackNumber = details?.trackNumber)
                    fileName = uniquify(
                        fileName,
                        primaryDir = store.downloadRoot, // disk check is moot; dedup is record-based
                        existing = store.records.value.mapValues { it.value.fileName },
                        excludeTrackID = track.id,
                    )
                    store.updateRecord(track.id) { it.copy(fileName = fileName) }

                    val buffer = java.io.ByteArrayOutputStream()
                    val result = downloader.download(resolved.url, buffer) { p ->
                        store.publishProgress(track.id, p)
                    }
                    result.fold(
                        onSuccess = {
                            store.publishProgress(track.id, null)
                            val finalBytes = embedTagsInMemory(track, ext, buffer.toByteArray(), details)
                            val uri = store.writeBytesToTree(treeUri, fileName, mimeForExtension(ext), finalBytes)
                            if (uri != null) {
                                store.updateRecord(track.id) {
                                    it.copy(status = DownloadStatus.COMPLETED, quality = resolved.quality, safUri = uri)
                                }
                                store.markPresent(track.id)
                            } else {
                                fail(track.id, "写入所选文件夹失败，请重试或更换下载目录")
                            }
                        },
                        onFailure = { fail(track.id, it.message ?: "下载失败") },
                    )
                } else {
                    // ── File target ── Write straight to the pinned root; tag in place (async).
                    val root = store.recordFor(track.id)?.let { store.rootFor(it) } ?: store.downloadRoot
                    var fileName = relativePath(track, ext, trackNumber = details?.trackNumber)
                    fileName = uniquify(
                        fileName,
                        primaryDir = root,
                        existing = store.records.value.mapValues { it.value.fileName },
                        excludeTrackID = track.id,
                    )
                    store.updateRecord(track.id) { it.copy(fileName = fileName) }

                    val dest = File(root, fileName)
                    val result = downloader.download(resolved.url, dest) { p ->
                        store.publishProgress(track.id, p)
                    }
                    result.fold(
                        onSuccess = {
                            store.publishProgress(track.id, null)
                            // Record the quality that actually resolved (may have degraded from the
                            // requested target), so the UI shows the true tier on disk.
                            store.updateRecord(track.id) {
                                it.copy(status = DownloadStatus.COMPLETED, quality = resolved.quality)
                            }
                            store.markPresent(track.id) // clear any stale "文件缺失" flag
                            scope.launch { embedTags(track, dest, details) }
                        },
                        onFailure = { fail(track.id, it.message ?: "下载失败") },
                    )
                }
            }
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
     * Metadata embedding for a File download (in place). Launched fire-and-forget — the song is
     * already on disk + playable; tagging is a nice-to-have and any failure only logs.
     */
    private suspend fun embedTags(track: Track, dest: File, details: com.walkman.tv.data.model.TrackDetails?) {
        runCatching {
            val coverBytes = fetchAndCacheCover(track, details)
            val lyricsText = fetchLyrics(track)
            when (dest.extension.lowercase()) {
                "mp3" -> Mp3TagWriter.write(
                    file = dest, title = track.name, artist = track.singer, album = track.albumName,
                    trackNumber = details?.trackNumber, trackTotal = details?.trackTotal,
                    year = details?.releaseDate, genre = details?.genre, publisher = details?.company,
                    albumArtist = details?.albumArtist, lyrics = lyricsText, cover = coverBytes,
                )
                "flac" -> FlacTagWriter.write(
                    file = dest, title = track.name, artist = track.singer, album = track.albumName,
                    trackNumber = details?.trackNumber, trackTotal = details?.trackTotal,
                    year = details?.releaseDate, genre = details?.genre, publisher = details?.company,
                    albumArtist = details?.albumArtist, lyrics = lyricsText, cover = coverBytes,
                )
                else -> { /* m4a / wav / ogg — no writer yet, cover cache still good */ }
            }
        }.onFailure { e ->
            android.util.Log.w("DownloadCoordinator", "tag-write failed for ${track.id}: ${e.message}")
        }
    }

    /**
     * In-memory metadata embedding for a SAF download. Caches the cover, then returns the tagged
     * bytes for mp3/flac (or the original bytes unchanged for other formats / on any failure — the
     * download still succeeds, tagging is best-effort).
     */
    private suspend fun embedTagsInMemory(
        track: Track,
        ext: String,
        raw: ByteArray,
        details: com.walkman.tv.data.model.TrackDetails?,
    ): ByteArray {
        val coverBytes = runCatching { fetchAndCacheCover(track, details) }.getOrNull()
        return runCatching {
            when (ext.lowercase()) {
                "mp3" -> Mp3TagWriter.tag(
                    raw, title = track.name, artist = track.singer, album = track.albumName,
                    trackNumber = details?.trackNumber, trackTotal = details?.trackTotal,
                    year = details?.releaseDate, genre = details?.genre, publisher = details?.company,
                    albumArtist = details?.albumArtist, lyrics = fetchLyrics(track), cover = coverBytes,
                )
                "flac" -> FlacTagWriter.tag(
                    raw, title = track.name, artist = track.singer, album = track.albumName,
                    trackNumber = details?.trackNumber, trackTotal = details?.trackTotal,
                    year = details?.releaseDate, genre = details?.genre, publisher = details?.company,
                    albumArtist = details?.albumArtist, lyrics = fetchLyrics(track), cover = coverBytes,
                )
                else -> raw
            }
        }.getOrElse { e ->
            android.util.Log.w("DownloadCoordinator", "in-memory tag failed for ${track.id}: ${e.message}")
            raw
        }
    }

    /** Fetch the cover (hi-res URL if available, else track.picURL), store it in the cover cache,
     *  and return the bytes (or null). */
    private suspend fun fetchAndCacheCover(track: Track, details: com.walkman.tv.data.model.TrackDetails?): ByteArray? {
        val coverUrl = details?.hiResCoverURL?.takeIf { it.isNotBlank() } ?: track.picURL
        val coverBytes = fetchCover(coverUrl)
        if (coverBytes != null && coverBytes.isNotEmpty()) coverCache.put(track.id, coverBytes)
        return coverBytes
    }

    private suspend fun fetchLyrics(track: Track): String? = try {
        val lines = lyricsFetcher?.fetch(track) ?: emptyList()
        LrcSerializer.serialize(lines)
    } catch (_: Exception) { null }

    private fun mimeForExtension(ext: String): String = when (ext.lowercase()) {
        "mp3" -> "audio/mpeg"
        "flac" -> "audio/flac"
        "m4a" -> "audio/mp4"
        "wav" -> "audio/wav"
        "aac" -> "audio/aac"
        "ogg" -> "audio/ogg"
        else -> "audio/*"
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

    /** Delete a record's backing file — handles both File downloads and SAF exports. */
    private fun deleteFile(record: DownloadRecord) = store.deleteBackingFile(record)

    private companion object {
        /** Default simultaneous transfers until settings push the user's choice in. */
        const val DEFAULT_CONCURRENT = 3
    }
}
