package com.walkman.tv.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.walkman.tv.data.model.Quality
import com.walkman.tv.di.getLanIp
import com.walkman.tv.ui.appContainer
import com.walkman.tv.ui.components.QrDialog
import com.walkman.tv.ui.components.TvFocusable
import com.walkman.tv.ui.components.TvPill
import com.walkman.tv.ui.theme.AppColors
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    val settings by appContainer.settingsStore.settings.collectAsState()
    val scripts by appContainer.scriptStore.scripts.collectAsState()
    var url by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<String?>(null) }
    var showQr by remember { mutableStateOf(false) }

    // Receive script payloads from the phone-to-TV QR flow.
    LaunchedEffect(Unit) {
        appContainer.events.qrScriptUrl.collect { u ->
            showQr = false
            importFromUrl(scope, u) { status = it }
        }
    }
    LaunchedEffect(Unit) {
        appContainer.events.qrScriptText.collect { raw ->
            showQr = false
            status = "正在导入上传的脚本…"
            val r = appContainer.scriptStore.import(raw)
            status = r.fold({ "已导入：${it.name}" }, { "导入失败：${it.message}" })
        }
    }

    Column(
        modifier = modifier.fillMaxSize().padding(top = 8.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Section("播放音质") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Quality.entries.forEach { q ->
                    TvPill(onClick = { scope.launch { appContainer.settingsStore.update { it.copy(preferredQuality = q) } } }, selected = settings.preferredQuality == q) {
                        Text(q.displayName, fontSize = 13.sp)
                    }
                }
            }
        }

        Section("内置直连兜底") {
            ToggleRow("脚本失败时尝试内置直连（kw/wy）", settings.fallbackEnabled) {
                scope.launch { appContainer.settingsStore.update { it.copy(fallbackEnabled = !it.fallbackEnabled) } }
            }
        }

        Section("歌词翻译") {
            ToggleRow("显示歌词翻译", settings.showLyricTranslation) {
                scope.launch { appContainer.settingsStore.update { it.copy(showLyricTranslation = !it.showLyricTranslation) } }
            }
        }

        Section("自定义音源") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("粘贴脚本 URL（.js）", color = AppColors.TextMuted) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { importFromUrl(scope, url) { status = it } }),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = AppColors.TextPrimary,
                        unfocusedTextColor = AppColors.TextPrimary,
                        focusedBorderColor = AppColors.AccentGreen,
                        unfocusedBorderColor = AppColors.Card,
                        cursorColor = AppColors.AccentGreen,
                    ),
                )
                Spacer(Modifier.width(8.dp))
                TvPill(onClick = { importFromUrl(scope, url) { status = it } }, selected = true) { Text("导入", fontSize = 14.sp) }
                Spacer(Modifier.width(8.dp))
                TvPill(
                    onClick = { showQr = true },
                    shape = CircleShape,
                    contentPadding = PaddingValues(10.dp),
                ) {
                    Icon(Icons.Filled.QrCode2, contentDescription = "扫码导入", modifier = Modifier.size(22.dp))
                }
            }
            status?.let { Text(it, color = AppColors.TextSecondary, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp)) }

            Spacer(Modifier.padding(top = 6.dp))
            if (scripts.isEmpty()) {
                Text("尚未导入任何脚本", color = AppColors.TextMuted, fontSize = 13.sp)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    scripts.forEach { s ->
                        TvFocusable(onClick = { scope.launch { appContainer.scriptStore.setEnabled(s.id, !s.enabled) } }, modifier = Modifier.fillMaxWidth()) {
                            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(s.name, color = AppColors.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                                    Text("v${s.version}  ${s.author}", color = AppColors.TextMuted, fontSize = 12.sp)
                                }
                                Text(if (s.enabled) "已启用" else "已停用", color = if (s.enabled) AppColors.AccentGreen else AppColors.TextMuted, fontSize = 12.sp)
                                Spacer(Modifier.width(12.dp))
                                TvPill(onClick = { scope.launch { appContainer.scriptStore.remove(s.id) } }) { Text("删除", fontSize = 12.sp) }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showQr) {
        val ip = remember { getLanIp() }
        val port = appContainer.localServer?.boundPort
        if (ip != null && port != null) {
            QrDialog(
                url = "http://$ip:$port/script",
                title = "扫码导入音源",
                subtitle = "手机扫码、电脑打开地址都可以，可粘贴脚本 URL 或上传 .js 文件",
                onDismiss = { showQr = false },
            )
        } else {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { showQr = false },
                title = { Text("无法启动扫码") },
                text = { Text("请确认电视已连接 Wi-Fi 且本地服务已启动后再试。") },
                confirmButton = {
                    androidx.compose.material3.TextButton(onClick = { showQr = false }) {
                        Text("好", color = AppColors.AccentGreen)
                    }
                },
                containerColor = AppColors.BgPanel,
                titleContentColor = AppColors.TextPrimary,
                textContentColor = AppColors.TextSecondary,
            )
        }
    }
}

private fun importFromUrl(scope: kotlinx.coroutines.CoroutineScope, url: String, onStatus: (String) -> Unit) {
    if (url.isBlank()) return
    scope.launch {
        onStatus("正在导入…")
        val result = runCatching {
            val raw = appContainer.fetchText(url.trim())
            appContainer.scriptStore.import(raw).getOrThrow()
        }
        onStatus(result.fold({ "已导入：${it.name}" }, { "导入失败：${it.message}" }))
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, color = AppColors.AccentGreen, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        content()
    }
}

@Composable
private fun ToggleRow(label: String, on: Boolean, onToggle: () -> Unit) {
    TvFocusable(onClick = onToggle, modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = AppColors.TextPrimary, fontSize = 14.sp, modifier = Modifier.weight(1f))
            Text(if (on) "开" else "关", color = if (on) AppColors.AccentGreen else AppColors.TextMuted, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
    }
}
