package com.walkman.tv

import android.os.Bundle
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
}
