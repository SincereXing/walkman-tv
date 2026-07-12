package com.walkman.tv.data.store

import android.content.Context
import com.walkman.tv.data.model.Quality
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.io.File

/** Lyric font-size preset for the full-screen player. [activeSp]/[inactiveSp]/[translationSp]
 *  are the rendered sizes for the current line / other lines / translation line. STANDARD matches
 *  the original hard-coded 22/15/12. */
@Serializable
enum class LyricSize(val label: String, val activeSp: Int, val inactiveSp: Int, val translationSp: Int) {
    STANDARD("标准", 22, 15, 12),
    LARGE("大", 30, 19, 15),
    MAX("最大", 40, 24, 18),
}

@Serializable
data class Settings(
    val preferredQuality: Quality = Quality.FLAC24,
    val fallbackEnabled: Boolean = true,
    /** Audio offload (DSP direct path / HDMI passthrough). Bit-perfect Hi-Res on good HALs,
     *  but some vendor HALs (小米/鸿蒙/坚果 etc.) crash natively in the offload path — the
     *  settings toggle lets affected users turn it off. Default on so existing users keep
     *  the current behaviour. */
    val audioOffloadEnabled: Boolean = true,
    val showLyricTranslation: Boolean = true,
    val lyricSize: LyricSize = LyricSize.STANDARD,
    /** Absolute path of the chosen download root (a writable storage volume). null ⇒ default
     *  app-scoped Music dir on primary storage. See DownloadStore.availableRoots(). */
    val customDownloadDir: String? = null,
    /** A user-picked SAF folder (content tree Uri) to download into. When non-null it takes
     *  precedence over [customDownloadDir] — files are exported into this folder so they live in
     *  a browsable location (e.g. a USB drive folder). null ⇒ use the File-based volume root. */
    val customDownloadTreeUri: String? = null,
    /** Max simultaneous download transfers (batch or otherwise). Clamped to 1..8 on apply. */
    val maxConcurrentDownloads: Int = 3,
    /** Batch "下载全部": when a track is already downloaded but at a different quality than the
     *  chosen target, re-download it to match (true) vs. always skip already-downloaded (false). */
    val redownloadOnQualityChange: Boolean = true,
    /** Sources that feed the discover (home) page. Spec docs/discover-page-spec-android-tv.md §2:
     *  fixed 4 in order kw → wy → kg → tx, default all enabled. */
    val homeSources: Set<com.walkman.tv.data.model.SourceID> = setOf(
        com.walkman.tv.data.model.SourceID.KW,
        com.walkman.tv.data.model.SourceID.WY,
        com.walkman.tv.data.model.SourceID.KG,
        com.walkman.tv.data.model.SourceID.TX,
    ),
)

/** App preferences persisted as JSON. */
class SettingsStore(context: Context) {
    private val store = JsonStore(
        File(context.filesDir, "settings.json"),
        Settings.serializer(),
        Settings(),
    )

    private val _settings = MutableStateFlow(Settings())
    val settings: StateFlow<Settings> = _settings.asStateFlow()

    suspend fun loadAll() {
        _settings.value = withContext(Dispatchers.IO) { store.load() }
    }

    suspend fun update(transform: (Settings) -> Settings) {
        _settings.value = transform(_settings.value)
        withContext(Dispatchers.IO) { store.save(_settings.value) }
    }
}
