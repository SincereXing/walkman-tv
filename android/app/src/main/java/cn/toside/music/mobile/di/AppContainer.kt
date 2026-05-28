package cn.toside.music.mobile.di

import android.content.Context
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Hand-rolled dependency container (no Hilt). Holds app-wide singletons; created once in [App].
 * Components are added as the app grows (stores, SourceManager, PlaybackController…).
 */
class AppContainer(val appContext: Context) {

    val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS)
            .callTimeout(20, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }
}
