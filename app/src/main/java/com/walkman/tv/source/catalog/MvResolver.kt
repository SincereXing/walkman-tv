package com.walkman.tv.source.catalog

import com.walkman.tv.data.model.MusicVideoInfo
import com.walkman.tv.data.model.MvQuality
import com.walkman.tv.data.model.SourceID
import com.walkman.tv.data.model.Track
import org.json.JSONArray
import org.json.JSONObject

/**
 * Resolves MV (music video) URLs per platform. Ported from RN src/utils/musicSdk/(source)/mv.js.
 *
 * Required [Track.extras] hints captured during search (populated in SearchCatalog/BoardCatalog):
 *   - kw: "mvId" (kw's MV id, usually the same as songmid when MVFLAG=='1')
 *   - wy: "mvId" (NetEase mvid; otherwise we look it up via /api/song/detail)
 *   - kg: "mvId" (Kugou MvHash; otherwise we fall back to searching by name)
 *   - tx: "mvId" (QQ Music mv.vid; otherwise we look it up via song detail)
 *   - mg: "mvId" (Migu mvCopyrightId returned by the search list)
 */
class MvResolver(private val http: CatalogHttp) {

    suspend fun getMvUrl(track: Track): MusicVideoInfo? = when (track.source) {
        SourceID.KW -> kuwo(track)
        SourceID.WY -> netease(track)
        SourceID.KG -> kugou(track)
        SourceID.TX -> qq(track)
        SourceID.MG -> migu(track)
        else -> null
    }

    // ===================================================================== Kuwo

    /** Kuwo's `anti.s?type=convert_url&rid=MV_<mvId>` returns either a real mp4 URL or the
     *  DRM placeholder mp3 ('仅在酷我音乐手机端播放'). Reject the latter — there's no auth-free
     *  way around it, the JS source has the same limitation. */
    private suspend fun kuwo(track: Track): MusicVideoInfo? {
        val mvId = track.extras["mvId"]?.takeIf { it.isNotEmpty() && it != "0" } ?: track.songmid
        val url = runCatching {
            http.getText(
                "http://antiserver.kuwo.cn/anti.s?type=convert_url&rid=MV_$mvId&format=mp4&response=url",
                mapOf("User-Agent" to "okhttp/3.10.0"),
            ).trim()
        }.getOrNull()
        if (url == null || !url.startsWith("http") || isKuwoMvPlaceholder(url)) return null
        return MusicVideoInfo(
            id = mvId, name = track.name, url = url,
            pageUrl = "http://www.kuwo.cn/mvplay/$mvId",
            qualities = listOf(MvQuality("mp4", url)),
        )
    }

    private fun isKuwoMvPlaceholder(url: String): Boolean {
        val tail = url.substringAfterLast('/').substringBefore('?').lowercase()
        if (tail in KUWO_PLACEHOLDER_FILES) return true
        if (tail.endsWith(".mp3")) return true // a real MV is mp4
        return false
    }

    // ===================================================================== NetEase

    /** NetEase: derive mvid (from extras or song-detail), then /api/mv/detail. */
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

    // ===================================================================== Kugou

    /** Kugou: m.kugou.com/app/i/mv.php?cmd=100&hash=<MvHash>. The response has qualities under
     *  `mvdata` (or top-level `data`): uhd > rq > sq > hd > sd > lq, each `{downurl, url}`. */
    private suspend fun kugou(track: Track): MusicVideoInfo? {
        val mvHash = track.extras["mvId"]?.takeIf { it.isNotEmpty() } ?: return null
        val body = runCatching {
            JSONObject(http.getText("https://m.kugou.com/app/i/mv.php?cmd=100&hash=$mvHash"))
        }.getOrNull() ?: return null
        val data = body.optJSONObject("mvdata") ?: body.optJSONObject("data") ?: body
        val qualities = mutableListOf<MvQuality>()
        listOf("uhd", "rq", "sq", "hd", "sd", "lq").forEach { tier ->
            val q = data.optJSONObject(tier) ?: return@forEach
            val u = q.optString("downurl").ifEmpty { q.optString("url") }
            if (u.isNotEmpty()) qualities.add(MvQuality(tier, u))
        }
        // Fallback: some payloads put a single URL at the data root.
        if (qualities.isEmpty()) {
            val u = data.optString("downurl").ifEmpty { data.optString("url") }
                .ifEmpty { data.optString("mv_url") }.ifEmpty { data.optString("playurl") }
            if (u.isNotEmpty()) qualities.add(MvQuality("default", u))
        }
        if (qualities.isEmpty()) return null
        return MusicVideoInfo(
            id = mvHash, name = track.name,
            url = qualities.first().url,
            pageUrl = "https://www.kugou.com/mvweb/html/mv_$mvHash.html",
            qualities = qualities,
        )
    }

    // ===================================================================== QQ Music

    /** QQ Music: musicu.fcg with the MvUrlProxy batch payload. Response has
     *  `mvUrl.data[<vid>].mp4[]` — each entry carries `freeflow_url[]` or `url[] + vkey + cn`. */
    private suspend fun qq(track: Track): MusicVideoInfo? {
        val mvVid = track.extras["mvId"]?.takeIf { it.isNotEmpty() } ?: return null
        val payload = qqPayload(mvVid)
        val headers = mapOf(
            "Referer" to "https://y.qq.com/",
            "User-Agent" to "Mozilla/5.0",
            "Content-Type" to "application/x-www-form-urlencoded",
        )
        val body = runCatching {
            JSONObject(http.postJson("https://u.y.qq.com/cgi-bin/musicu.fcg", payload, headers))
        }.getOrNull() ?: return null
        val urlInfo = body.optJSONObject("mvUrl")?.optJSONObject("data")?.optJSONObject(mvVid)
        val videoInfo = body.optJSONObject("mvInfo")?.optJSONObject("data")?.optJSONObject(mvVid)
        val qualities = qqQualities(urlInfo)
        if (qualities.isEmpty()) return null
        return MusicVideoInfo(
            id = mvVid,
            name = videoInfo?.optString("name")?.ifEmpty { null } ?: track.name,
            url = qualities.first().url,
            pageUrl = "https://y.qq.com/n/ryqq/mv/$mvVid",
            qualities = qualities,
        )
    }

    private fun qqPayload(mvVid: String): String {
        val required = JSONArray(
            listOf(
                "vid", "type", "sid", "cover_pic", "duration", "singers", "new_switch_str",
                "video_pay", "hint", "code", "msg", "name", "desc", "playcnt", "pubdate", "isfav",
                "fileid", "filesize_v2", "switch_pay_type", "pay", "pay_info", "uploader_headurl",
                "uploader_nick", "uploader_uin", "uploader_encuin", "play_forbid_reason",
            ),
        )
        val vidlist = JSONArray().put(mvVid)
        val comm = JSONObject()
            .put("ct", 6).put("cv", 0).put("g_tk", 1646675364).put("uin", 0)
            .put("format", "json").put("platform", "yqq")
        val mvInfo = JSONObject()
            .put("module", "music.video.VideoData")
            .put("method", "get_video_info_batch")
            .put(
                "param",
                JSONObject().put("vidlist", vidlist).put("required", required),
            )
        val mvUrl = JSONObject()
            .put("module", "music.stream.MvUrlProxy")
            .put("method", "GetMvUrls")
            .put(
                "param",
                JSONObject()
                    .put("vids", JSONArray().put(mvVid))
                    .put("request_type", 10003)
                    .put("addrtype", 3)
                    .put("format", 264)
                    .put("maxFiletype", 60),
            )
        return JSONObject().put("comm", comm).put("mvInfo", mvInfo).put("mvUrl", mvUrl).toString()
    }

    private fun qqQualities(urlInfo: JSONObject?): List<MvQuality> {
        val mp4 = urlInfo?.optJSONArray("mp4") ?: return emptyList()
        val out = mutableListOf<Pair<Int, MvQuality>>()
        for (i in 0 until mp4.length()) {
            val item = mp4.optJSONObject(i) ?: continue
            if (item.optInt("code", -1) != 0) continue
            val freeflow = item.optJSONArray("freeflow_url")
            val base = (0 until (freeflow?.length() ?: 0))
                .map { freeflow!!.optString(it) }
                .firstOrNull { it.isNotEmpty() }
                ?: run {
                    val urls = item.optJSONArray("url")
                    val u0 = urls?.optString(0).orEmpty()
                    val vkey = item.optString("vkey")
                    val cn = item.optString("cn")
                    if (u0.isNotEmpty() && vkey.isNotEmpty() && cn.isNotEmpty()) {
                        "$u0$vkey/$cn?fname=$cn"
                    } else null
                }
            if (base.isNullOrEmpty()) continue
            val order = item.optInt("newFileType")
                .takeIf { it != 0 } ?: item.optInt("filetype")
                .takeIf { it != 0 } ?: item.optInt("format", 0)
            out.add(order to MvQuality(order.toString(), base))
        }
        return out.sortedByDescending { it.first }.map { it.second }
    }

    // ===================================================================== Migu

    /** Migu: c.musicapp.migu.cn/MIGUM2.0/v1.0/content/resourceinfo.do?resourceType=D
     *  &resourceId=<mvCopyrightId>. Response.resource[0] has widescreenPath / highscreenPath. */
    private suspend fun migu(track: Track): MusicVideoInfo? {
        val mvId = track.extras["mvId"]?.takeIf { it.isNotEmpty() } ?: return null
        val body = runCatching {
            JSONObject(http.getText(
                "https://c.musicapp.migu.cn/MIGUM2.0/v1.0/content/resourceinfo.do?resourceType=D&resourceId=$mvId",
                mapOf("User-Agent" to "Mozilla/5.0"),
            ))
        }.getOrNull() ?: return null
        val resource = body.optJSONArray("resource")?.optJSONObject(0) ?: return null
        val widescreen = resource.optString("widescreenPath").ifEmpty { null }?.let(::buildMguUrl)
        val highscreen = resource.optString("highscreenPath").ifEmpty { null }?.let(::buildMguUrl)
        val qualities = mutableListOf<MvQuality>()
        widescreen?.let { qualities.add(MvQuality("widescreen", it)) }
        highscreen?.takeIf { it != widescreen }?.let { qualities.add(MvQuality("highscreen", it)) }
        if (qualities.isEmpty()) return null
        return MusicVideoInfo(
            id = mvId,
            name = resource.optString("songName").ifEmpty { track.name },
            url = qualities.first().url,
            qualities = qualities,
        )
    }

    private fun buildMguUrl(path: String): String {
        val p = if (path.startsWith("/")) path else "/$path"
        return "https://freetyst.nf.migu.cn/public$p"
    }

    private companion object {
        val KUWO_PLACEHOLDER_FILES = setOf("588957081.mp3", "588957081.mp4")
    }
}
