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
     * Forward the remote's Menu button into the Compose tree via [App.container.events].
     * Needed because the AndroidView-hosted [androidx.media3.ui.PlayerView] in MV mode owns
     * focus and Compose's onPreviewKeyEvent never sees the event.
     */
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            App.container.events.postMenuKey()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }
}
