package com.walkman.tv.playback.download

import com.walkman.tv.data.model.SourceID
import com.walkman.tv.data.model.Track
import com.walkman.tv.data.model.TrackDetails
import com.walkman.tv.source.catalog.CatalogHttp
import org.json.JSONObject

/**
 * Spec §3.3: fire a per-platform detail lookup alongside the URL resolve so the download can
 * land with the right track number in its filename + the highest-resolution cover the catalog
 * offers (search results often return tiny thumbnails). Best-effort — any failure returns
 * null and the download still completes with whatever the search result already carried.
 */
internal class TrackDetailFetcher(private val http: CatalogHttp) {

    suspend fun fetch(track: Track): TrackDetails? = runCatching {
        when (track.source) {
            SourceID.WY -> fetchNetEase(track)
            SourceID.TX -> fetchQq(track)
            SourceID.KW -> fetchKuwo(track)
            else -> null
        }
    }.getOrNull()

    // ─── NetEase ────────────────────────────────────────────────────────────────

    /**
     * /api/v3/song/detail returns `songs[]` (basic track info) + `privileges[]`. The track
     * carries `position` (track number within album) + `album.picUrl` at 1024x or so.
     * The publicly documented endpoint requires a JSON-style ids param.
     */
    private suspend fun fetchNetEase(track: Track): TrackDetails? {
        val url = "https://music.163.com/api/song/detail/?ids=%5B${track.songmid}%5D"
        val headers = mapOf("Referer" to "https://music.163.com/")
        val text = runCatching { http.getText(url, headers) }.getOrNull() ?: return null
        val song = runCatching {
            JSONObject(text).optJSONArray("songs")?.optJSONObject(0)
        }.getOrNull() ?: return null
        val trackNumber = song.optInt("no", -1).takeIf { it > 0 }
        // Bump the album cover query so we get a chunkier image (NetEase serves up to 1024).
        val hiResCover = song.optJSONObject("album")?.optString("picUrl")?.ifEmpty { null }
            ?.let { it.substringBefore("?") + "?param=800y800" }
        return TrackDetails(trackNumber = trackNumber, hiResCoverURL = hiResCover)
    }

    // ─── QQ Music ───────────────────────────────────────────────────────────────

    /**
     * QQ's search returns `albumno` directly. We don't always have it in the cached Track but
     * we do have the songmid — look the song up via the public song-detail cgi for a fuller
     * record. Falls back to null on any glitch.
     */
    private suspend fun fetchQq(track: Track): TrackDetails? {
        val payload = JSONObject().apply {
            put(
                "songinfo",
                JSONObject().apply {
                    put("method", "get_song_detail_yqq")
                    put("module", "music.pf_song_detail_svr")
                    put("param", JSONObject().apply { put("song_mid", track.songmid) })
                },
            )
            put(
                "comm",
                JSONObject().apply {
                    put("g_tk", 5381); put("uin", 0)
                    put("format", "json"); put("platform", "yqq.json")
                },
            )
        }.toString()
        val headers = mapOf(
            "Referer" to "https://y.qq.com/",
            "Content-Type" to "application/x-www-form-urlencoded",
        )
        val text = runCatching {
            http.postJson("https://u.y.qq.com/cgi-bin/musicu.fcg", payload, headers)
        }.getOrNull() ?: return null
        val data = runCatching {
            JSONObject(text).optJSONObject("songinfo")?.optJSONObject("data")
        }.getOrNull() ?: return null
        val info = data.optJSONObject("track_info")
        val trackNumber = info?.optInt("index_album", -1)?.takeIf { it > 0 }
        val albumMid = info?.optJSONObject("album")?.optString("mid")?.ifEmpty { null }
        val hiResCover = albumMid?.let {
            "https://y.gtimg.cn/music/photo_new/T002R800x800M000$it.jpg"
        }
        return TrackDetails(trackNumber = trackNumber, hiResCoverURL = hiResCover)
    }

    // ─── Kuwo ───────────────────────────────────────────────────────────────────

    /**
     * Kuwo has no widely-available track-number field on the public endpoints, but artistpicserver
     * happily serves a bigger cover when we ask for size=800 instead of 240. Better than nothing.
     */
    private suspend fun fetchKuwo(track: Track): TrackDetails? {
        val hiRes = "http://artistpicserver.kuwo.cn/pic.web?corp=kuwo&type=rid_pic&pictype=800&size=800&rid=${track.songmid}"
        val pic = runCatching { http.getText(hiRes).trim() }.getOrNull()
            ?.takeIf { it.startsWith("http") }
        return TrackDetails(trackNumber = null, hiResCoverURL = pic)
    }
}
