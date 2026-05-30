package com.walkman.tv.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Text
import com.walkman.tv.di.encodeQrToBitmap
import com.walkman.tv.ui.theme.AppColors

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
    val bitmap = remember(url) { encodeQrToBitmap(url, 512) }
    val closeFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { closeFocus.requestFocus() } }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .width(380.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(AppColors.BgPanel)
                .padding(horizontal = 20.dp, vertical = 18.dp),
        ) {
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
                        Text("无法生成二维码", color = AppColors.TextMuted)
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
                    onClick = onDismiss,
                    selected = true,
                    focusRequester = closeFocus,
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
                ) {
                    Text("关闭", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
