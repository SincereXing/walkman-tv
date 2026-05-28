package cn.toside.music.mobile.di

import android.content.Context
import cn.toside.music.mobile.data.store.LibraryStore
import cn.toside.music.mobile.data.store.ScriptStore
import cn.toside.music.mobile.data.store.SettingsStore
import cn.toside.music.mobile.playback.LyricsFetcher
import cn.toside.music.mobile.playback.PlaybackController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import cn.toside.music.mobile.source.OtherSourceFinder
import cn.toside.music.mobile.source.SourceManager
import cn.toside.music.mobile.source.builtin.BuiltInLyricResolver
import cn.toside.music.mobile.source.builtin.BuiltInResolver
import cn.toside.music.mobile.source.catalog.Boards
import cn.toside.music.mobile.source.catalog.CatalogHttp
import cn.toside.music.mobile.source.catalog.Catalogs
import cn.toside.music.mobile.source.catalog.MvResolver
import cn.toside.music.mobile.source.catalog.Songlists
import cn.toside.music.mobile.source.js.ScriptHttpClient
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Hand-rolled dependency container (no Hilt). Holds app-wide singletons; created once in [App].
 */
class AppContainer(val appContext: Context) {

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

    val scriptStore: ScriptStore by lazy { ScriptStore(appContext, sourceManager) }
    val libraryStore: LibraryStore by lazy { LibraryStore(appContext) }
    val settingsStore: SettingsStore by lazy { SettingsStore(appContext) }

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    fun initPlayback() {
        if (!::playbackController.isInitialized) {
            playbackController = PlaybackController(appContext, sourceManager, lyricsFetcher)
            playbackController.onTrackStarted = { track ->
                appScope.launch { libraryStore.recordHistory(track) }
            }
        }
    }

    /** Load persisted data and wire settings → playback. Called once at startup. */
    fun bootstrap() {
        appScope.launch {
            settingsStore.loadAll()
            libraryStore.loadAll()
            settingsStore.settings.onEach { s ->
                playbackController.preferredQuality = s.preferredQuality
                sourceManager.fallbackEnabled = s.fallbackEnabled
            }.launchIn(appScope)
            scriptStore.loadAll()
        }
    }
}
