package com.walkman.tv

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.util.DisplayMetrics
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import com.walkman.tv.ui.RootScreen
import com.walkman.tv.ui.theme.AppColors
import com.walkman.tv.ui.theme.WalkmanTvTheme

class MainActivity : ComponentActivity() {
    /**
     * 界面整体按屏幕自动缩放：把屏幕统一当成 960×540dp（1080p 电视的标准值，界面就是按它设计的）。
     * 多数电视本来就是这个值，不受影响；有的电视 / 盒子上报的 density 偏小（比如 1080p 报 240dpi，
     * 相当于 1280×720dp），文字和控件就会整体偏小（#15）。这里改的是 Activity 的 density，
     * 所以 Compose 界面、弹窗、MV 画面都一起按比例缩放，系统的字体大小设置也仍然生效。
     */
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(newBase)
        val dm = newBase.resources.displayMetrics
        val longPx = maxOf(dm.widthPixels, dm.heightPixels)
        val shortPx = minOf(dm.widthPixels, dm.heightPixels)
        if (longPx <= 0 || shortPx <= 0) return
        val density = minOf(longPx / DESIGN_WIDTH_DP, shortPx / DESIGN_HEIGHT_DP)
        val override = Configuration().apply {
            densityDpi = (density * DisplayMetrics.DENSITY_DEFAULT).toInt()
            screenWidthDp = (dm.widthPixels / density).toInt()
            screenHeightDp = (dm.heightPixels / density).toInt()
            smallestScreenWidthDp = (shortPx / density).toInt()
        }
        runCatching { applyOverrideConfiguration(override) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            WalkmanTvTheme {
                // Keep the TV screen awake while audio/video is playing.
                val playing = App.container.playbackController.state.collectAsState().value.isPlaying
                LaunchedEffect(playing) {
                    if (playing) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
                RootScreen(modifier = Modifier.fillMaxSize().background(AppColors.BgDeep))
            }
        }
    }

    /**
     * Forward the remote's "Menu / Options / 三条横线" button into the Compose tree via
     * [App.container.events]. Needed because the AndroidView-hosted [androidx.media3.ui.PlayerView]
     * in MV mode owns focus and Compose's onPreviewKeyEvent never sees the event.
     *
     * Several keycodes are accepted because OEM remotes are inconsistent — Sony BRAVIA Z9K maps
     * its three-line button to KEYCODE_MENU, but other vendors send KEYCODE_TV_CONTENTS_MENU
     * or KEYCODE_BUTTON_MODE for the same physical key.
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (App.container.events.keyInterceptor?.invoke(event) == true) return true
        return super.dispatchKeyEvent(event)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_MENU,
            KeyEvent.KEYCODE_TV_CONTENTS_MENU,
            KeyEvent.KEYCODE_BUTTON_MODE,
            KeyEvent.KEYCODE_GUIDE -> {
                App.container.events.postMenuKey()
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    private companion object {
        const val DESIGN_WIDTH_DP = 960f
        const val DESIGN_HEIGHT_DP = 540f
    }
}
