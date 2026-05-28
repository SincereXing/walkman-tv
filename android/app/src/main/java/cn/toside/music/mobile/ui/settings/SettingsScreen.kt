package cn.toside.music.mobile.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
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
import androidx.tv.material3.Text
import cn.toside.music.mobile.data.model.Quality
import cn.toside.music.mobile.ui.appContainer
import cn.toside.music.mobile.ui.components.TvFocusable
import cn.toside.music.mobile.ui.components.TvPill
import cn.toside.music.mobile.ui.theme.AppColors
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    val settings by appContainer.settingsStore.settings.collectAsState()
    val scripts by appContainer.scriptStore.scripts.collectAsState()
    var url by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<String?>(null) }

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
                Spacer(Modifier.width(12.dp))
                TvPill(onClick = { importFromUrl(scope, url) { status = it } }, selected = true) { Text("导入", fontSize = 14.sp) }
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
