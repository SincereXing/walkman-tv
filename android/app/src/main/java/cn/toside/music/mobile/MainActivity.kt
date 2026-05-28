package cn.toside.music.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import cn.toside.music.mobile.ui.RootScreen
import cn.toside.music.mobile.ui.theme.AppColors
import cn.toside.music.mobile.ui.theme.WalkmanTvTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            WalkmanTvTheme {
                RootScreen(modifier = Modifier.fillMaxSize().background(AppColors.BgDeep))
            }
        }
    }
}
