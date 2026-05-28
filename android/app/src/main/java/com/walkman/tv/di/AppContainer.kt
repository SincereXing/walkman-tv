package com.walkman.tv.di

import android.content.Context
import com.walkman.tv.data.store.LibraryStore
import com.walkman.tv.data.store.ScriptStore
import com.walkman.tv.data.store.SettingsStore
import com.walkman.tv.playback.LyricsFetcher
import com.walkman.tv.playback.PlaybackController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.launchIn
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
