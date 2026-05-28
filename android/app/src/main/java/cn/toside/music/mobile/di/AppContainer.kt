package cn.toside.music.mobile.di

import android.content.Context
import cn.toside.music.mobile.source.OtherSourceFinder
import cn.toside.music.mobile.source.SourceManager
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
    private val otherSourceFinder by lazy { OtherSourceFinder(catalogs) }

    val sourceManager: SourceManager by lazy {
        SourceManager(preload, scriptHttp, otherSourceFinder, builtInResolver)
    }
}
