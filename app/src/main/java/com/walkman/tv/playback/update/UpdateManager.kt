package com.walkman.tv.playback.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/** A GitHub release resolved to the one APK asset that fits this device's ABI. */
data class AppRelease(
    val versionName: String,   // tag without the leading 'v', e.g. "1.3.2"
    val tag: String,           // raw tag, e.g. "v1.3.2"
    val notes: String,         // release body (markdown; shown as plain text)
    val apkUrl: String,        // browser_download_url of the chosen asset
    val apkName: String,
)

/** Drives the whole in-app update flow. Consumed by the settings 检查更新 section. */
sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val release: AppRelease) : UpdateState
    data class Downloading(val progress: Float) : UpdateState
    data class Downloaded(val file: File, val release: AppRelease) : UpdateState
    data class Failed(val message: String) : UpdateState
}

/**
 * In-app updater: checks GitHub Releases for a newer version, downloads the ABI-matched APK to
 * the app cache, and hands it to the system package installer. No auto-install — the system
 * always shows its own confirm dialog, and on Android 8+ the user must have granted "install
 * unknown apps" to us first (we route them to that settings screen when needed).
 */
class UpdateManager(
    private val appContext: Context,
    private val http: OkHttpClient,
) {
    /** Current installed version, read from the package — avoids a BuildConfig dependency. */
    val currentVersion: String = runCatching {
        appContext.packageManager.getPackageInfo(appContext.packageName, 0).versionName
    }.getOrNull() ?: "?"

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    /** Query GitHub for the latest release and compare against the installed version. */
    suspend fun check() {
        _state.value = UpdateState.Checking
        _state.value = runCatching {
            withContext(Dispatchers.IO) {
                val req = Request.Builder()
                    .url("https://api.github.com/repos/$OWNER/$REPO/releases/latest")
                    .header("User-Agent", UA)               // GitHub 403s requests without a UA
                    .header("Accept", "application/vnd.github+json")
                    .build()
                val body = http.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
                    resp.body?.string() ?: throw IOException("空响应")
                }
                parseRelease(JSONObject(body))
            }
        }.fold(
            { release -> if (isNewer(release.versionName, currentVersion)) UpdateState.Available(release) else UpdateState.UpToDate },
            { e -> Log.w(TAG, "check failed", e); UpdateState.Failed(e.message ?: "检查更新失败") },
        )
    }

    /** Stream the chosen APK into the app cache, reporting progress via [UpdateState.Downloading]. */
    suspend fun download(release: AppRelease) {
        _state.value = UpdateState.Downloading(0f)
        val dest = File(File(appContext.cacheDir, "update"), "walkman-tv-${release.versionName}.apk")
        _state.value = runCatching {
            withContext(Dispatchers.IO) {
                downloadTo(release.apkUrl, dest) { p -> _state.value = UpdateState.Downloading(p) }
            }
            UpdateState.Downloaded(dest, release)
        }.getOrElse { e ->
            Log.w(TAG, "download failed", e)
            UpdateState.Failed(e.message ?: "下载失败")
        }
    }

    /**
     * Launch the system installer for [file]. On Android 8+ this needs the "install unknown apps"
     * grant; if we don't have it we send the user to that settings screen and return false so the
     * caller can prompt them to come back and tap install again.
     */
    fun install(context: Context, file: File): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
            val pkgUri = Uri.parse("package:${context.packageName}")
            runCatching {
                context.startActivity(
                    Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, pkgUri)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }.onFailure {
                // Some TV firmwares lack the per-app screen — fall back to the global one.
                runCatching {
                    context.startActivity(
                        Intent(Settings.ACTION_SECURITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }
            }
            return false
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
            .onFailure { Log.w(TAG, "install intent failed", it) }
        return true
    }

    fun reset() { _state.value = UpdateState.Idle }

    // MARK: - internals

    private fun parseRelease(json: JSONObject): AppRelease {
        val tag = json.optString("tag_name").ifEmpty { throw IOException("无有效发布") }
        val version = tag.trimStart('v', 'V')
        val notes = json.optString("body").trim()
        val assets = json.optJSONArray("assets") ?: JSONArray()
        val apks = (0 until assets.length())
            .mapNotNull { assets.optJSONObject(it) }
            .filter { it.optString("name").endsWith(".apk", ignoreCase = true) }
        if (apks.isEmpty()) throw IOException("该版本没有可用的 APK")
        // Prefer an APK matching the device's ABI (highest-priority ABI first); the asset names
        // are like walkman-tv-1.3.2-arm64-v8a-release.apk. Fall back to the universal build.
        val chosen = Build.SUPPORTED_ABIS.firstNotNullOfOrNull { abi ->
            apks.firstOrNull { it.optString("name").contains("-$abi-") }
        } ?: apks.firstOrNull { it.optString("name").contains("-universal-") }
            ?: apks.first()
        return AppRelease(
            versionName = version,
            tag = tag,
            notes = notes,
            apkUrl = chosen.optString("browser_download_url"),
            apkName = chosen.optString("name"),
        )
    }

    private fun downloadTo(url: String, dest: File, onProgress: (Float) -> Unit) {
        // Dedicated client: the shared one has a 25s callTimeout that would kill a big APK on
        // slow TV Wi-Fi. No call timeout here, generous read timeout instead.
        val client = http.newBuilder()
            .callTimeout(0, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
        val req = Request.Builder().url(url).header("User-Agent", UA).build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
            val body = resp.body ?: throw IOException("空响应")
            val total = body.contentLength()
            dest.parentFile?.mkdirs()
            val tmp = File(dest.parentFile, dest.name + ".part")
            tmp.delete()
            body.byteStream().use { input ->
                tmp.outputStream().use { output ->
                    val buf = ByteArray(64 * 1024)
                    var read = 0L
                    var lastPct = -1
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        output.write(buf, 0, n)
                        read += n
                        if (total > 0) {
                            val pct = (read * 100 / total).toInt()
                            if (pct != lastPct) { lastPct = pct; onProgress(pct / 100f) }
                        }
                    }
                }
            }
            onProgress(1f)
            if (!tmp.renameTo(dest)) { tmp.copyTo(dest, overwrite = true); tmp.delete() }
        }
    }

    companion object {
        private const val TAG = "UpdateManager"
        private const val OWNER = "SincereXing"
        private const val REPO = "walkman-tv"
        private const val UA = "walkman-tv-updater"

        /** Dotted numeric compare: "1.3.10" > "1.3.2". Non-numeric parts count as 0. */
        fun isNewer(remote: String, current: String): Boolean {
            val r = remote.split('.', '-').map { it.toIntOrNull() ?: 0 }
            val c = current.split('.', '-').map { it.toIntOrNull() ?: 0 }
            for (i in 0 until maxOf(r.size, c.size)) {
                val rv = r.getOrElse(i) { 0 }
                val cv = c.getOrElse(i) { 0 }
                if (rv != cv) return rv > cv
            }
            return false
        }
    }
}
