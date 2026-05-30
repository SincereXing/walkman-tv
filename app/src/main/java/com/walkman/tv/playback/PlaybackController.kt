package com.walkman.tv.playback

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.walkman.tv.data.model.Quality
import com.walkman.tv.data.model.Track
import com.walkman.tv.source.ResolvedTrack
import com.walkman.tv.source.SourceManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.random.Random

enum class RepeatMode { OFF, ALL, ONE }

data class PlaybackState(
    val queue: List<Track> = emptyList(),
    val index: Int = -1,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val resolving: Boolean = false,
    val warning: String? = null,
    val originLabel: String? = null,
    val quality: Quality? = null,
    val error: String? = null,
    val repeatMode: RepeatMode = RepeatMode.ALL,
    val shuffle: Boolean = false,
    val isMv: Boolean = false,
) {
    val currentTrack: Track? get() = queue.getOrNull(index)
}

/**
 * Single source of truth for playback (iOS `PlaybackEngine`). Owns one ExoPlayer; resolves each
 * track's URL lazily via [SourceManager] on switch (URLs expire), records history, loads lyrics,
 * and reuses the same player for MV video.
 */
class PlaybackController(
    context: Context,
    private val sources: SourceManager,
    private val lyricsFetcher: LyricsFetcher,
) {
    val player: ExoPlayer = ExoPlayer.Builder(context.applicationContext)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .build(),
            true,
        )
        .setHandleAudioBecomingNoisy(true)
        .build()

    private val appContext = context.applicationContext
    private var serviceStarted = false
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _state = MutableStateFlow(PlaybackState())
    val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private val _lyrics = MutableStateFlow<List<LyricLine>>(emptyList())
    val lyrics: StateFlow<List<LyricLine>> = _lyrics.asStateFlow()

    /** Set by the app to record played tracks into history. */
    var onTrackStarted: ((Track) -> Unit)? = null
    /** Preferred play quality, fed from settings. */
    var preferredQuality: Quality = Quality.K320

    private var resolveJob: Job? = null

    init {
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _state.value = _state.value.copy(isPlaying = isPlaying)
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    if (_state.value.isMv) exitMv() else handleEnded()
                }
                if (playbackState == Player.STATE_READY) {
                    _state.value = _state.value.copy(durationMs = player.duration.coerceAtLeast(0))
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                _state.value = _state.value.copy(error = "播放失败: ${error.errorCodeName}", resolving = false)
            }
        })
        startPositionTicker()
    }

    // MARK: - queue control

    fun setQueue(tracks: List<Track>, startIndex: Int = 0, autoPlay: Boolean = true) {
        if (tracks.isEmpty()) return
        _state.value = _state.value.copy(queue = tracks, index = startIndex.coerceIn(0, tracks.size - 1))
        if (autoPlay) playAt(_state.value.index)
    }

    fun playTrack(track: Track) = setQueue(listOf(track), 0, true)

    fun playAt(index: Int) {
        val track = _state.value.queue.getOrNull(index) ?: return
        _state.value = _state.value.copy(index = index, resolving = true, error = null, warning = null, isMv = false)
        resolveJob?.cancel()
        resolveJob = scope.launch {
            val resolved = runCatching { sources.resolveMusicURL(track, preferredQuality) }
            runCatching { playResolvedOrFail(track, resolved) }
                .onFailure { e ->
                    android.util.Log.e("PlaybackController", "playAt crashed", e)
                    _state.value = _state.value.copy(
                        resolving = false,
                        error = "播放出错: ${e.message ?: e::class.simpleName}",
                    )
                }
        }
    }

    private fun playResolvedOrFail(track: Track, resolved: Result<ResolvedTrack>) {
        resolved.onSuccess { r ->
                val item = MediaItem.Builder()
                    .setUri(r.url)
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(track.name)
                            .setArtist(track.singer)
                            .setAlbumTitle(track.albumName)
                            .apply { track.picURL?.let { setArtworkUri(android.net.Uri.parse(it)) } }
                            .build(),
                    )
                    .build()
                player.setMediaItem(item)
                player.prepare()
                // NOTE: MediaSessionService startup is currently disabled — Android 12+ kills
                // the process via ForegroundServiceDidNotStartInTimeException if the service
                // doesn't post a media notification within 5s, which races with our URL-resolve
                // path. Re-enable once a proper MediaNotification.Provider is in place.
                // ensureService()
                player.playWhenReady = true
                _state.value = _state.value.copy(
                    resolving = false,
                    warning = r.warning,
                    originLabel = r.origin.label,
                    quality = r.quality,
                )
                onTrackStarted?.invoke(track)
                loadLyrics(track)
            }.onFailure { e ->
                _state.value = _state.value.copy(
                    resolving = false,
                    error = e.message ?: "无法播放",
                )
            }
        }
    }

    fun togglePlay() {
        if (player.isPlaying) player.pause() else player.play()
    }

    fun next() {
        val s = _state.value
        if (s.queue.isEmpty()) return
        val nextIdx = when {
            s.shuffle -> if (s.queue.size <= 1) s.index else randomOther(s.index, s.queue.size)
            s.index + 1 < s.queue.size -> s.index + 1
            s.repeatMode != RepeatMode.OFF -> 0
            else -> { player.pause(); return }
        }
        playAt(nextIdx)
    }

    fun prev() {
        val s = _state.value
        if (s.queue.isEmpty()) return
        if (player.currentPosition > 3000) { player.seekTo(0); return }
        val prevIdx = when {
            s.shuffle -> if (s.queue.size <= 1) s.index else randomOther(s.index, s.queue.size)
            s.index - 1 >= 0 -> s.index - 1
            else -> s.queue.size - 1
        }
        playAt(prevIdx)
    }

    fun seekTo(ms: Long) = player.seekTo(ms.coerceAtLeast(0))

    fun setRepeatMode(mode: RepeatMode) { _state.value = _state.value.copy(repeatMode = mode) }

    fun toggleShuffle() { _state.value = _state.value.copy(shuffle = !_state.value.shuffle) }

    fun stop() {
        player.stop()
        player.clearMediaItems()
        _state.value = PlaybackState()
        _lyrics.value = emptyList()
    }

    // MARK: - MV

    /** Play an MV video url on the same player (UI attaches a PlayerView for output). */
    fun playMvUrl(url: String) {
        resolveJob?.cancel()
        player.setMediaItem(MediaItem.fromUri(url))
        player.prepare()
        player.playWhenReady = true
        _state.value = _state.value.copy(isMv = true, resolving = false, error = null)
    }

    /** Exit MV and resume the current audio track. */
    fun exitMv() {
        if (!_state.value.isMv) return
        _state.value = _state.value.copy(isMv = false)
        playAt(_state.value.index)
    }

    // MARK: - internals

    private fun handleEnded() {
        if (_state.value.repeatMode == RepeatMode.ONE) {
            player.seekTo(0); player.play(); return
        }
        next()
    }

    private fun loadLyrics(track: Track) {
        _lyrics.value = emptyList()
        scope.launch {
            val lines = runCatching { lyricsFetcher.fetch(track) }.getOrDefault(emptyList())
            if (_state.value.currentTrack?.id == track.id) _lyrics.value = lines
        }
    }

    /** Start the MediaSessionService once, so background audio + remote controls work. */
    private fun ensureService() {
        if (serviceStarted) return
        serviceStarted = true
        runCatching {
            ContextCompat.startForegroundService(appContext, Intent(appContext, PlaybackService::class.java))
        }
    }

    private fun randomOther(current: Int, size: Int): Int {
        var i = current
        while (i == current) i = Random.nextInt(size)
        return i
    }

    private fun startPositionTicker() {
        scope.launch {
            while (true) {
                if (player.isPlaying || _state.value.isMv) {
                    _state.value = _state.value.copy(
                        positionMs = player.currentPosition.coerceAtLeast(0),
                        durationMs = player.duration.coerceAtLeast(0),
                    )
                }
                delay(500)
            }
        }
    }

    fun release() {
        resolveJob?.cancel()
        player.release()
    }
}
