package com.walkman.tv.source.catalog

import com.walkman.tv.data.model.MusicVideoInfo
import com.walkman.tv.data.model.MvQuality
import com.walkman.tv.data.model.SourceID
import com.walkman.tv.data.model.Track
import org.json.JSONObject

/**
 * Resolves MV (music video) URLs per platform, ported from RN src/utils/musicSdk/(source)/mv.js.
 * Currently supports wy (NetEase) + kw (Kuwo); others return null until ported.
 */
class MvResolver(private val http: CatalogHttp) {

    suspend fun getMvUrl(track: Track): MusicVideoInfo? = when (track.source) {
        SourceID.WY -> netease(track)
        SourceID.KW -> kuwo(track)
        else -> null
    }

    // NetEase: song detail → mvid, then /api/mv/detail → brs map.
    private suspend fun netease(track: Track): MusicVideoInfo? {
        val headers = mapOf("Referer" to "https://music.163.com/", "Origin" to "https://music.163.com")
        val mvId = track.extras["mvId"]?.takeIf { it.isNotEmpty() && it != "0" }
            ?: runCatching {
                val detail = JSONObject(http.getText("https://music.163.com/api/song/detail?ids=[${track.songmid}]", headers))
                detail.optJSONArray("songs")?.optJSONObject(0)?.optLong("mvid")?.takeIf { it > 0 }?.toString()
            }.getOrNull()
            ?: return null

        val info = MusicVideoInfo(
            id = mvId, name = track.name, pageUrl = "https://music.163.com/#/mv?id=$mvId",
        )
        return runCatching {
            val body = JSONObject(http.getText("https://music.163.com/api/mv/detail?id=$mvId&type=mp4", headers))
            if (body.optInt("code") != 200) return info
            val data = body.optJSONObject("data") ?: return info
            val brs = data.optJSONObject("brs")
            val qualities = mutableListOf<MvQuality>()
            brs?.keys()?.forEach { k ->
                val u = brs.optString(k)
                if (u.isNotEmpty()) qualities.add(MvQuality(k, u))
            }
            qualities.sortByDescending { it.type.toIntOrNull() ?: 0 }
            info.copy(
                name = data.optString("name").ifEmpty { info.name },
                url = qualities.firstOrNull()?.url,
                qualities = qualities,
            )
        }.getOrDefault(info)
    }

    // Kuwo: convert_url MV (mvId frequently equals the song rid). If the request lacks
    // app-side auth Kuwo returns a DRM placeholder *audio* mp3 file (e.g. 588957081.mp3)
    // whose content is the recording "this song only plays on Kuwo mobile" — we have to
    // reject those, otherwise the user clicks MV and hears the placeholder instead.
    private suspend fun kuwo(track: Track): MusicVideoInfo? {
        val mvId = track.extras["mvId"]?.takeIf { it.isNotEmpty() && it != "0" } ?: track.songmid
        val url = runCatching {
            http.getText(
                "http://antiserver.kuwo.cn/anti.s?type=convert_url&rid=MV_$mvId&format=mp4&response=url",
                mapOf("User-Agent" to "okhttp/3.10.0"),
            ).trim()
        }.getOrNull()
        if (url == null || !url.startsWith("http")) return null
        if (isKuwoMvPlaceholder(url)) return null
        return MusicVideoInfo(
            id = mvId, name = track.name, url = url,
            pageUrl = "http://www.kuwo.cn/mvplay/$mvId",
            qualities = listOf(MvQuality("mp4", url)),
        )
    }

    /** A real MV is mp4; the DRM placeholder is an mp3. Catch the known placeholder filenames
     *  and also any *.mp3 returned by the MV endpoint (it should never be mp3 for a real MV). */
    private fun isKuwoMvPlaceholder(url: String): Boolean {
        val tail = url.substringAfterLast('/').substringBefore('?').lowercase()
        if (tail in KUWO_PLACEHOLDER_FILES) return true
        if (tail.endsWith(".mp3")) return true
        return false
    }

    private companion object {
        val KUWO_PLACEHOLDER_FILES = setOf("588957081.mp3", "588957081.mp4")
    }
}
