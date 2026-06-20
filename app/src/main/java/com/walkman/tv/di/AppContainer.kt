package com.walkman.tv.di

import android.content.Context
import com.walkman.tv.data.store.LibraryStore
import com.walkman.tv.data.store.PlaybackSnapshotStore
import com.walkman.tv.data.store.ScriptStore
import com.walkman.tv.data.store.SearchHistoryStore
import com.walkman.tv.data.store.SettingsStore
import com.walkman.tv.playback.EqualizerManager
import com.walkman.tv.playback.LyricsFetcher
import com.walkman.tv.playback.PlaybackController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import com.walkman.tv.source.OtherSourceFinder
import com.walkman.tv.source.SourceManager
import com.walkman.tv.source.builtin.BuiltInLyricResolver
import com.walkman.tv.source.builtin.BuiltInResolver
import com.walkman.tv.source.catalog.Boards
import com.walkman.tv.source.catalog.CatalogHttp
import com.walkman.tv.source.catalog.Catalogs
import com.walkman.tv.source.catalog.MvResolver
import com.walkman.tv.source.catalog.Songlists
import com.walkman.tv.source.js.ScriptHttpClient
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Hand-rolled dependency container (no Hilt). Holds app-wide singletons; created once in [App].
 */
class AppContainer(val appContext: Context) {

    /** Process-wide event bus for hardware-key events (e.g. KEYCODE_MENU). */
    val events: AppEvents = AppEvents()

    /** In-app HTTP server backing the phone-to-TV QR flow. null when start failed. */
    @Volatile var localServer: LocalServer? = null
        private set

    val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS)
            .callTimeout(25, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    private val preload: String by lazy {
        appContext.assets.open("script/user-api-preload.js").use { it.readBytes().toString(Charsets.UTF_8) }
    }

    private val catalogHttp by lazy { CatalogHttp(httpClient) }

    val catalogs by lazy { Catalogs(catalogHttp) }
    val boards by lazy { Boards(catalogHttp) }
    val songlists by lazy { Songlists(catalogHttp) }
    /** Backs the recommend (discover) right column — heroes / recommendations / boards. */
    val homeStore by lazy { com.walkman.tv.ui.recommend.HomeStore(songlists, boards) }
    val mvResolver by lazy { MvResolver(catalogHttp) }

    private val scriptHttp by lazy { ScriptHttpClient(httpClient) }
    private val builtInResolver by lazy { BuiltInResolver(httpClient) }
    private val builtInLyricResolver by lazy { BuiltInLyricResolver(catalogHttp) }
    private val otherSourceFinder by lazy { OtherSourceFinder(catalogs) }

    val sourceManager: SourceManager by lazy {
        SourceManager(preload, scriptHttp, otherSourceFinder, builtInResolver)
    }

    private val lyricsFetcher by lazy {
        LyricsFetcher(sourceManager, builtInLyricResolver, otherSourceFinder)
    }

    /** Created eagerly on the main thread in [App] (ExoPlayer needs a consistent looper). */
    lateinit var playbackController: PlaybackController
        private set

    val equalizerManager: EqualizerManager by lazy {
        EqualizerManager { playbackController.audioSessionId }
    }

    val scriptStore: ScriptStore by lazy { ScriptStore(appContext, sourceManager) }
    val libraryStore: LibraryStore by lazy { LibraryStore(appContext) }
    val settingsStore: SettingsStore by lazy { SettingsStore(appContext) }
    val playbackSnapshotStore: PlaybackSnapshotStore by lazy { PlaybackSnapshotStore(appContext) }
    val searchHistoryStore: SearchHistoryStore by lazy { SearchHistoryStore(appContext) }
    val coverCache: com.walkman.tv.data.store.CoverCache by lazy {
        com.walkman.tv.data.store.CoverCache(appContext)
    }
    val downloadStore: com.walkman.tv.data.store.DownloadStore by lazy {
        com.walkman.tv.data.store.DownloadStore(appContext)
    }
    val localFolderStore: com.walkman.tv.data.store.LocalFolderStore by lazy {
        com.walkman.tv.data.store.LocalFolderStore(appContext)
    }
    val downloadCoordinator: com.walkman.tv.playback.download.DownloadCoordinator by lazy {
        com.walkman.tv.playback.download.DownloadCoordinator(
            store = downloadStore,
            sources = sourceManager,
            coverCache = coverCache,
            http = httpClient,
            lyricsFetcher = lyricsFetcher,
        )
    }
    val localMusicStore: com.walkman.tv.playback.local.LocalMusicStore by lazy {
        com.walkman.tv.playback.local.LocalMusicStore(
            context = appContext,
            localFolderStore = localFolderStore,
            coverCache = coverCache,
        )
    }

    /** Process-lived scope used for operations that mustn't be cancelled by a UI navigation
     *  (e.g. settings → script delete). Public so screens can opt into it explicitly. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    fun initPlayback() {
        if (!::playbackController.isInitialized) {
            playbackController = PlaybackController(
                appContext,
                sourceManager,
                lyricsFetcher,
                com.walkman.tv.playback.AudioSpecProbe(httpClient),
            )
            playbackController.onTrackStarted = { track ->
                appScope.launch { libraryStore.recordHistory(track) }
            }
            // Local-first URL resolver — downloads + SAF imports skip the online cascade.
            playbackController.localUrlResolver = { track ->
                downloadStore.localFile(track.id)?.toURI()?.toString()
                    ?: localMusicStore.fileUri(track)?.toString()
            }
        }
    }

    /** Fetch a remote text resource (used to import a custom-source script from a URL). */
    suspend fun fetchText(url: String): String = kotlinx.coroutines.withContext(Dispatchers.IO) {
        okhttp3.Request.Builder().url(url).build().let { req ->
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) throw IllegalStateException("HTTP ${resp.code}")
                resp.body?.string() ?: throw IllegalStateException("空响应")
            }
        }
    }

    /** Load persisted data and wire settings → playback. Called once at startup. */
    fun bootstrap() {
        // Bring up the LAN HTTP server (best-effort; QR features just won't work if it fails).
        if (localServer == null) {
            localServer = LocalServer.start(events)
        }
        appScope.launch {
            settingsStore.loadAll()
            libraryStore.loadAll()
            settingsStore.settings.onEach { s ->
                playbackController.preferredQuality = s.preferredQuality
                sourceManager.fallbackEnabled = s.fallbackEnabled
            }.launchIn(appScope)
            scriptStore.loadAll()
            searchHistoryStore.load()
            coverCache.loadAll()
            downloadStore.loadAll()
            localFolderStore.loadAll()

            // Restore last session's queue + index + playing state. Run after scripts load so
            // any custom-source needed to resolve URLs is ready.
            val snapshot = playbackSnapshotStore.load()
            playbackController.restoreSnapshot(snapshot)

            // Auto-save the snapshot whenever queue/index/isPlaying changes. distinctUntilChanged
            // already coalesces the high-frequency position ticks since we only project the three
            // fields that matter here.
            playbackController.state
                .map { Triple(it.queue.map { t -> t.id }, it.index, it.isPlaying) }
                .distinctUntilChanged()
                .onEach { playbackSnapshotStore.save(playbackController.snapshot()) }
                .launchIn(appScope)
        }
    }

    /** Synchronous final save — called right before the process is killed on user-confirmed exit. */
    fun saveSnapshotNow() {
        if (::playbackController.isInitialized) {
            runCatching { playbackSnapshotStore.saveBlocking(playbackController.snapshot()) }
        }
    }
}
