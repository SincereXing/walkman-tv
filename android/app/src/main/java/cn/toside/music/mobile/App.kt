package cn.toside.music.mobile

import android.app.Application
import cn.toside.music.mobile.di.AppContainer
import com.whl.quickjs.android.QuickJSLoader

class App : Application() {

    override fun onCreate() {
        super.onCreate()
        instance = this
        // Load the QuickJS native lib once for the whole process (custom-source engine).
        QuickJSLoader.init()
        container = AppContainer(this)
    }

    companion object {
        lateinit var instance: App
            private set
        lateinit var container: AppContainer
            private set
    }
}
