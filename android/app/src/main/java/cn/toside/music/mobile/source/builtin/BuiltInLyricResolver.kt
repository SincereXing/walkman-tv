package cn.toside.music.mobile.source.builtin

import android.util.Base64
import cn.toside.music.mobile.data.model.SourceID
import cn.toside.music.mobile.data.model.Track
import cn.toside.music.mobile.playback.LyricLine
import cn.toside.music.mobile.playback.LyricParser
import cn.toside.music.mobile.source.catalog.CatalogHttp
import org.json.JSONObject

/**
 * Direct lyric fetchers per platform, ported from iOS `BuiltInLyricResolver`.
 * Uses the simplest endpoints (no krc decryption).
 */
class BuiltInLyricResolver(private val http: CatalogHttp) {

    private val ua = mapOf("User-Agent" to "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X)")

    suspend fun fetch(track: Track): List<LyricLine>? = when (track.source) {
        SourceID.KW -> kuwo(track.songmid)
        SourceID.TX -> qq(track.songmid)
        SourceID.WY -> netease(track.songmid)
        SourceID.KG -> kugou(track.name, track.extras["hash"] ?: track.songmid, (track.duration ?: 0) * 1000)
        else -> null
    }

    private suspend fun kuwo(songmid: String): List<LyricLine>? {
        val json = runCatching {
            JSONObject(http.getText("http://m.kuwo.cn/newh5/singles/songinfoandlrc?musicId=$songmid", ua))
        }.getOrNull() ?: return null
        val lrclist = json.optJSONObject("data")?.optJSONArray("lrclist") ?: return null
        val rebuilt = StringBuilder()
        for (i in 0 until lrclist.length()) {
            val item = lrclist.optJSONObject(i) ?: continue
            val secs = item.optString("time").toDoubleOrNull() ?: continue
            val text = item.optString("lineLyric")
            val m = secs.toInt() / 60
            val s = secs.toInt() % 60
            val ms = ((secs - secs.toInt()) * 100).toInt()
            rebuilt.append(String.format("[%02d:%02d.%02d]%s\n", m, s, ms, text))
        }
        return LyricParser.parse(rebuilt.toString()).ifEmpty { null }
    }

    private suspend fun qq(songmid: String): List<LyricLine>? {
        val headers = ua + mapOf("Referer" to "https://y.qq.com/portal/player.html")
        val json = runCatching {
            JSONObject(http.getText("https://c.y.qq.com/lyric/fcgi-bin/fcg_query_lyric_new.fcg?songmid=$songmid&g_tk=5381&loginUin=0&hostUin=0&format=json&inCharset=utf8&outCharset=utf-8&platform=yqq", headers))
        }.getOrNull() ?: return null
        if (json.optInt("code") != 0) return null
        val lrc = decodeB64(json.optString("lyric")) ?: return null
        val trans = json.optString("trans").ifEmpty { null }?.let { decodeB64(it) }
        return LyricParser.parse(lrc, trans).ifEmpty { null }
    }

    private suspend fun netease(songmid: String): List<LyricLine>? {
        val json = runCatching {
            JSONObject(http.getText("https://music.163.com/api/song/lyric?id=$songmid&lv=1&kv=1&tv=-1", ua + mapOf("Referer" to "https://music.163.com/")))
        }.getOrNull() ?: return null
        val lyric = json.optJSONObject("lrc")?.optString("lyric") ?: ""
        if (lyric.isEmpty()) return null
        val tlyric = json.optJSONObject("tlyric")?.optString("lyric")?.ifEmpty { null }
        return LyricParser.parse(lyric, tlyric).ifEmpty { null }
    }

    private suspend fun kugou(name: String, hash: String, durationMs: Int): List<LyricLine>? {
        val headers = mapOf(
            "KG-RC" to "1",
            "KG-THash" to "expand_search_manager.cpp:852736169:451",
            "User-Agent" to "KuGou2012-9020-ExpandSearchManager",
        )
        val nameEnc = java.net.URLEncoder.encode(name, "UTF-8").replace("+", "%20")
        val searchUrl = "http://lyrics.kugou.com/search?ver=1&man=yes&client=pc&keyword=$nameEnc&hash=$hash&timelength=$durationMs&lrctxt=1"
        val first = runCatching {
            JSONObject(http.getText(searchUrl, headers)).optJSONArray("candidates")?.optJSONObject(0)
        }.getOrNull() ?: return null
        val id = first.opt("id")?.toString() ?: return null
        val accessKey = first.optString("accesskey").ifEmpty { return null }
        val dlUrl = "http://lyrics.kugou.com/download?ver=1&client=pc&id=$id&accesskey=$accessKey&fmt=lrc&charset=utf8"
        val dJson = runCatching { JSONObject(http.getText(dlUrl, headers)) }.getOrNull() ?: return null
        if (dJson.optInt("status") != 200 || dJson.optString("fmt") != "lrc") return null
        val lrc = decodeB64(dJson.optString("content")) ?: return null
        return LyricParser.parse(lrc).ifEmpty { null }
    }

    private fun decodeB64(b64: String): String? =
        runCatching { String(Base64.decode(b64, Base64.DEFAULT), Charsets.UTF_8) }.getOrNull()?.ifEmpty { null }
}
