package com.walkman.tv.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Text
import com.walkman.tv.di.encodeQrToBitmap
import com.walkman.tv.ui.appContainer
import com.walkman.tv.ui.theme.AppColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Centered dialog showing a QR code + the encoded URL. Useful for phone-to-TV flows where the
 * user scans the code on their phone to open a page served by [com.walkman.tv.di.LocalServer].
 */
@Composable
fun QrDialog(
    url: String,
    title: String,
    subtitle: String,
    onDismiss: () -> Unit,
) {
    // 二维码在后台线程生成，弹窗先出来。以前在主线程生成，首次打开会卡住界面，用户以为没点中
    // 又按确认，积压的按键在弹窗出现后落到「关闭」上，弹窗一闪就没了。
    // first = 是否还在生成，second = 结果（null 表示生成失败）。
    val qr by produceState<Pair<Boolean, android.graphics.Bitmap?>>(true to null, url) {
        value = false to withContext(Dispatchers.Default) { encodeQrToBitmap(url, 512) }
    }
    val (generating, bitmap) = qr
    // 弹窗打开后焦点先放在地址框上（按确认没有反应），不直接放在「关闭」上：首次打开电视上
    // 创建弹窗较慢，用户以为没点中会再按确认，这些积压的按键如果落到「关闭」上，弹窗刚出来就被关掉，
    // 看起来就是「怎么点都不出来」。按下键到「关闭」，返回键也能关。
    val urlFocus = remember { FocusRequester() }
    var urlFocused by remember { mutableStateOf(false) }
    val debug = appContainer.settingsStore.settings.collectAsState().value.showDebugInfo
    val context = androidx.compose.ui.platform.LocalContext.current
    fun dismiss(reason: String) {
        if (debug) android.widget.Toast.makeText(context, "二维码弹窗关闭：$reason", android.widget.Toast.LENGTH_SHORT).show()
        onDismiss()
    }
    LaunchedEffect(Unit) {
        if (debug) android.widget.Toast.makeText(context, "二维码弹窗打开", android.widget.Toast.LENGTH_SHORT).show()
    }

    Dialog(
        onDismissRequest = { dismiss("返回键") },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .width(380.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(AppColors.BgPanel)
                .padding(horizontal = 20.dp, vertical = 18.dp),
        ) {
            // 放在弹窗内容里请求焦点，保证弹窗窗口已经创建；创建慢时多试几次。
            LaunchedEffect(Unit) {
                repeat(20) {
                    if (runCatching { urlFocus.requestFocus() }.isSuccess) return@LaunchedEffect
                    delay(50)
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(title, color = AppColors.TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(
                    subtitle,
                    color = AppColors.TextSecondary,
                    fontSize = 12.sp,
                )
                Spacer(Modifier.height(12.dp))
                if (bitmap != null) {
                    Box(
                        modifier = Modifier
                            .size(220.dp)
                            .background(androidx.compose.ui.graphics.Color.White)
                            .padding(6.dp),
                    ) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier.fillMaxWidth(),
                            contentScale = ContentScale.Fit,
                        )
                    }
                } else {
                    Box(modifier = Modifier.size(220.dp), contentAlignment = Alignment.Center) {
                        Text(if (generating) "正在生成二维码…" else "无法生成二维码", color = AppColors.TextMuted)
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "或在电脑 / 手机浏览器中打开下方地址：",
                    color = AppColors.TextMuted,
                    fontSize = 12.sp,
                )
                Spacer(Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(AppColors.BgDeep)
                        .border(1.dp, if (urlFocused) AppColors.Focus else Color.Transparent, RoundedCornerShape(8.dp))
                        .focusRequester(urlFocus)
                        .onFocusChanged { urlFocused = it.isFocused }
                        .focusable()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Text(
                        url,
                        color = AppColors.AccentGreen,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Spacer(Modifier.height(12.dp))
                TvPill(
                    onClick = { dismiss("关闭按钮") },
                    selected = true,
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
                ) {
                    Text("关闭", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
