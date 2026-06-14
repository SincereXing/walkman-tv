package com.walkman.tv.source.catalog

import com.walkman.tv.data.model.Quality
import com.walkman.tv.data.model.SourceID
import com.walkman.tv.data.model.Track
import com.walkman.tv.source.MusicSearcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.json.JSONArray
import org.json.JSONObject

interface SongCatalog {
    val source: SourceID
    suspend fun search(keyword: String, page: Int): List<Track>
}

/**
 * Aggregated multi-platform search via each platform's own public API
 * (ported from iOS `Catalogs` / `KuwoCatalog`). Implements [MusicSearcher] for other-source.
 */
class Catalogs(private val http: CatalogHttp) : MusicSearcher {

    private val services: Map<SourceID, SongCatalog> = listOf(
        KuwoSearch(http), KugouSearch(http), NetEaseSearch(http), QQSearch(http), MiguSearch(http),
    ).associateBy { it.source }

    fun serviceFor(source: SourceID): SongCatalog? = services[source]

    override suspend fun search(source: SourceID, keyword: String, page: Int): List<Track> =
        runCatching { services[source]?.search(keyword, page) ?: emptyList() }.getOrDefault(emptyList())

    /** Search every platform and interleave results (kw, wy, kg, tx, mg round-robin). */
    suspend fun searchAll(keyword: String, page: Int = 1): List<Track> {
        val groups: Map<SourceID, List<Track>> = coroutineScope {
            services.values.map { svc ->
                async { svc.source to runCatching { svc.search(keyword, page) }.getOrDefault(emptyList()) }
            }.awaitAll().toMap()
        }
        return interleave(groups)
    }

    private fun interleave(groups: Map<SourceID, List<Track>>): List<Track> {
        val order = listOf(SourceID.KW, SourceID.WY, SourceID.KG, SourceID.TX, SourceID.MG)
        val iters = order.associateWith { (groups[it] ?: emptyList()).iterator() }
        val out = mutableListOf<Track>()
        var going = true
        while (going) {
            going = false
            for (s in order) {
                val it = iters[s] ?: continue
                if (it.hasNext()) { out.add(it.next()); going = true }
            }
        }
        return out
    }
}

// MARK: - Kuwo (search.kuwo.cn r.s) ------------------------------------------------------------

private class KuwoSearch(private val http: CatalogHttp) : SongCatalog {
    override val source = SourceID.KW
    private val minfoRegex = Regex("""level:(\w+),bitrate:(\d+),format:(\w+),size:([\w.]+)""")

    override suspend fun search(keyword: String, page: Int): List<Track> {
        val enc = urlEncode(keyword)
        val url = "http://search.kuwo.cn/r.s?client=kt&all=$enc&pn=${page - 1}&rn=30&uid=794762570" +
            "&ver=kwplayer_ar_9.2.2.1&vipver=1&show_copyright_off=1&newver=1&ft=music&cluster=0" +
            "&strategy=2012&encoding=utf8&rformat=json&vermerge=1&mobi=1&issubtitle=1"
        val text = http.getText(url, mapOf("User-Agent" to "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15"))
        val abslist = runCatching { JSONObject(text).optJSONArray("abslist") }.getOrNull() ?: return emptyList()
        return (0 until abslist.length()).mapNotNull { build(abslist.optJSONObject(it)) }
    }

    private fun build(d: JSONObject?): Track? {
        d ?: return null
        val songmid = d.optString("MUSICRID").replace("MUSIC_", "")
        if (songmid.isEmpty()) return null
        val qs = mutableListOf<Quality>()
        d.optString("N_MINFO").split(";").forEach { chunk ->
            minfoRegex.find(chunk)?.groupValues?.getOrNull(2)?.let { br ->
                when (br) {
                    "4000" -> qs.add(Quality.FLAC24)
                    "2000" -> qs.add(Quality.FLAC)
                    "320" -> qs.add(Quality.K320)
                    "128" -> qs.add(Quality.K128)
                }
            }
        }

        if (qs.isEmpty()) qs.add(Quality.K128)
        val albumId = decode(d.optString("ALBUMID")).ifEmpty { null }
        val extras = mutableMapOf<String, String>()
        // Kuwo: MVFLAG=="1" means the song has an MV; Kuwo's mvId is generally the same rid.
        if (d.optString("MVFLAG") == "1") extras["mvId"] = songmid
        return Track(
            id = Track.makeID(SourceID.KW, songmid),
            name = decode(d.optString("SONGNAME", "未知")),
            singer = decode(d.optString("ARTIST")).replace("&", "、").ifEmpty { "未知歌手" },
            albumName = decode(d.optString("ALBUM")).ifEmpty { null },
            albumId = albumId,
            source = SourceID.KW,
            songmid = songmid,
            duration = d.optString("DURATION").toIntOrNull(),
            picURL = null,
            qualities = qs,
            extras = extras,
        )
    }

    private fun decode(s: String) = s.replace("&nbsp;", " ").replace("&amp;", "&")
}

// MARK: - Kugou (songsearch.kugou.com) -------------------------------------------------------

private class KugouSearch(private val http: CatalogHttp) : SongCatalog {
    override val source = SourceID.KG

    override suspend fun search(keyword: String, page: Int): List<Track> {
        val url = "https://songsearch.kugou.com/song_search_v2?keyword=${urlEncode(keyword)}&page=$page" +
            "&pagesize=30&userid=0&clientver=&platform=WebFilter&filter=2&iscorrection=1&privilege_filter=0&area_code=1"
        val json = JSONObject(http.getText(url))
        val items = json.optJSONObject("data")?.optJSONArray("lists") ?: return emptyList()
        val seen = HashSet<String>()
        val out = mutableListOf<Track>()
        for (i in 0 until items.length()) {
            val item = items.optJSONObject(i) ?: continue
            val hash = item.optString("FileHash")
            if (hash.isNotEmpty() && seen.add(hash)) build(item)?.let(out::add)
            item.optJSONArray("Grp")?.let { grp ->
                for (j in 0 until grp.length()) {
                    val sub = grp.optJSONObject(j) ?: continue
                    val h = sub.optString("FileHash")
                    if (h.isNotEmpty() && seen.add(h)) build(sub)?.let(out::add)
                }
            }
        }
        return out
    }

    private fun build(d: JSONObject): Track? {
        val hash = d.optString("FileHash").ifEmpty { return null }
        val qs = mutableListOf<Quality>()
        if (d.optLong("FileSize") != 0L) qs.add(Quality.K128)
        if (d.optLong("HQFileSize") != 0L) qs.add(Quality.K320)
        if (d.optLong("SQFileSize") != 0L) qs.add(Quality.FLAC)
        if (d.optLong("ResFileSize") != 0L) qs.add(Quality.FLAC24)
        if (qs.isEmpty()) qs.add(Quality.K128)
        val songmid = when (val a = d.opt("Audioid")) {
            is Number -> a.toLong().toString()
            is String -> a
            else -> hash
        }
        val albumId = d.opt("AlbumID")?.toString()?.ifEmpty { null }
        val extras = mutableMapOf("hash" to hash)
        albumId?.let { extras["albumId"] = it }
        // Kugou MV: MvHash is a non-empty string when an MV exists.
        d.optString("MvHash").ifEmpty { null }?.let { extras["mvId"] = it }
        val pic = d.optString("Image").ifEmpty { null }?.replace("{size}", "240")
        return Track(
            id = Track.makeID(SourceID.KG, songmid),
            name = decode(d.optString("SongName", "未知")),
            singer = decode(d.optString("SingerName")).ifEmpty { "未知歌手" },
            albumName = decode(d.optString("AlbumName")).ifEmpty { null },
            albumId = albumId,
            source = SourceID.KG,
            songmid = songmid,
            duration = d.optInt("Duration").takeIf { it > 0 },
            picURL = pic,
            qualities = qs,
            extras = extras,
        )
    }

    private fun decode(s: String) = s.replace("<em>", "").replace("</em>", "")
}

// MARK: - NetEase (music.163.com/api/search/get) ---------------------------------------------

private class NetEaseSearch(private val http: CatalogHttp) : SongCatalog {
    override val source = SourceID.WY

    override suspend fun search(keyword: String, page: Int): List<Track> {
        val offset = (page - 1) * 30
        val body = urlEncode("s=$keyword&type=1&offset=$offset&limit=30")
        val headers = mapOf("Referer" to "https://music.163.com/")
        val text = http.postForm("https://music.163.com/api/search/get", body, headers)
        val songs = JSONObject(text).optJSONObject("result")?.optJSONArray("songs") ?: return emptyList()
        val tracks = (0 until songs.length()).mapNotNull { build(songs.optJSONObject(it)) }
        return enrichFromDetail(tracks)
    }

    /**
     * `/api/search/get` doesn't return hMusic / sqMusic / hrMusic, mvid is always 0, and
     * album.picUrl is often missing. Spec docs/netease-search-fix-tv.md §1: batch-call
     * `/api/song/detail` once to fill those gaps. 30 ids per request is cheap.
     */
    private suspend fun enrichFromDetail(tracks: List<Track>): List<Track> {
        if (tracks.isEmpty()) return tracks
        val idList = tracks.joinToString(",") { it.songmid }
        val ids = urlEncode("[$idList]")
        val headers = mapOf("Referer" to "https://music.163.com/")
        val text = runCatching {
            http.getText("https://music.163.com/api/song/detail/?ids=$ids", headers)
        }.getOrNull() ?: return tracks
        val songs = runCatching { JSONObject(text).optJSONArray("songs") }.getOrNull() ?: return tracks

        data class Detail(val picURL: String?, val qualities: List<Quality>, val mvId: String?)
        val lookup = HashMap<String, Detail>()
        for (i in 0 until songs.length()) {
            val s = songs.optJSONObject(i) ?: continue
            val id = s.opt("id")?.toString() ?: continue
            // Correct NetEase semantics: hMusic = 320k, sqMusic = FLAC 16/44, hrMusic = Hi-Res 24bit.
            // mMusic = 192k (no exact tier in our enum); bMusic/lMusic = 128k (handled by K128 floor).
            val qs = mutableListOf(Quality.K128)
            if (s.optJSONObject("hMusic") != null) qs.add(Quality.K320)
            if (s.optJSONObject("sqMusic") != null) qs.add(Quality.FLAC)
            if (s.optJSONObject("hrMusic") != null) qs.add(Quality.HIRES)
            val pic = s.optJSONObject("album")?.optString("picUrl")?.ifEmpty { null }
            val mvId = s.optLong("mvid").takeIf { it > 0 }?.toString()
            lookup[id] = Detail(pic, qs, mvId)
        }
        return tracks.map { t ->
            val d = lookup[t.songmid] ?: return@map t
            t.copy(
                picURL = d.picURL ?: t.picURL,
                qualities = d.qualities,
                extras = if (d.mvId != null) t.extras + ("mvId" to d.mvId) else t.extras,
            )
        }
    }

    private fun build(d: JSONObject?): Track? {
        d ?: return null
        val id = d.opt("id")?.toString() ?: return null
        val artists = d.optJSONArray("artists")
        val singer = (0 until (artists?.length() ?: 0))
            .mapNotNull { artists?.optJSONObject(it)?.optString("name") }
            .filter { it.isNotEmpty() }.joinToString(" / ")
        val album = d.optJSONObject("album")
        // /api/search/get doesn't return the quality fields — enrichFromDetail overwrites
        // these. K128 floor here is a safety net for when the detail call fails so the song
        // is still playable.
        val qs = mutableListOf(Quality.K128)
        if (d.optJSONObject("hMusic") != null) qs.add(Quality.K320)
        if (d.optJSONObject("sqMusic") != null) qs.add(Quality.FLAC)
        if (d.optJSONObject("hrMusic") != null) qs.add(Quality.HIRES)
        // mvid is always 0 from /api/search/get — the real one comes from enrichFromDetail.
        val extras = mutableMapOf<String, String>()
        d.optLong("mvid").takeIf { it > 0 }?.toString()?.let { extras["mvId"] = it }
        return Track(
            id = Track.makeID(SourceID.WY, id),
            name = d.optString("name", "未知"),
            singer = singer.ifEmpty { "未知歌手" },
            albumName = album?.optString("name")?.ifEmpty { null },
            albumId = album?.opt("id")?.toString(),
            source = SourceID.WY,
            songmid = id,
            duration = d.optInt("duration").takeIf { it > 0 }?.div(1000),
            picURL = album?.optString("picUrl")?.ifEmpty { null },
            qualities = qs,
            extras = extras,
        )
    }
}

// MARK: - QQ Music (u.y.qq.com musicu.fcg) ----------------------------------------------------

private class QQSearch(private val http: CatalogHttp) : SongCatalog {
    override val source = SourceID.TX

    override suspend fun search(keyword: String, page: Int): List<Track> {
        val param = JSONObject()
            .put("query", keyword).put("num_per_page", 30).put("page_num", page)
            .put("search_type", 0).put("grp", 0).put("nqc_flag", 0)
        val req = JSONObject().put(
            "req",
            JSONObject()
                .put("module", "music.search.SearchCgiService")
                .put("method", "DoSearchForQQMusicLite")
                .put("param", param),
        )
        val headers = mapOf("Referer" to "https://y.qq.com/")
        val text = http.postJson("https://u.y.qq.com/cgi-bin/musicu.fcg", req.toString(), headers)
        val songs = JSONObject(text).optJSONObject("req")?.optJSONObject("data")
            ?.optJSONObject("body")?.optJSONArray("item_song") ?: return emptyList()
        return (0 until songs.length()).mapNotNull { build(songs.optJSONObject(it)) }
    }

    private fun build(d: JSONObject?): Track? {
        d ?: return null
        val mid = d.optString("mid").ifEmpty { return null }
        val singers = d.optJSONArray("singer")
        val singer = (0 until (singers?.length() ?: 0))
            .mapNotNull { singers?.optJSONObject(it)?.optString("name") }
            .filter { it.isNotEmpty() }.joinToString(" / ")
        val album = d.optJSONObject("album")
        val albumMid = album?.optString("mid")?.ifEmpty { null }
        val file = d.optJSONObject("file") ?: JSONObject()
        val qs = mutableListOf<Quality>()
        if (file.optLong("size_128mp3") > 0) qs.add(Quality.K128)
        if (file.optLong("size_320mp3") > 0) qs.add(Quality.K320)
        if (file.optLong("size_flac") > 0) qs.add(Quality.FLAC)
        if (file.optLong("size_hires") > 0) qs.add(Quality.FLAC24)
        if (qs.isEmpty()) qs.add(Quality.K128)
        val extras = mutableMapOf<String, String>()
        albumMid?.let { extras["albumMid"] = it }
        file.optString("media_mid").ifEmpty { null }?.let { extras["strMediaMid"] = it }
        d.opt("id")?.toString()?.let { extras["songId"] = it }
        // QQ MV: optional `mv.vid` carries the MV id when present.
        d.optJSONObject("mv")?.optString("vid")?.ifEmpty { null }?.let { extras["mvId"] = it }
        return Track(
            id = Track.makeID(SourceID.TX, mid),
            name = d.optString("name", "未知"),
            singer = singer.ifEmpty { "未知" },
            albumName = album?.optString("name")?.ifEmpty { null },
            albumId = album?.opt("id")?.toString(),
            source = SourceID.TX,
            songmid = mid,
            duration = d.optInt("interval").takeIf { it > 0 },
            picURL = albumMid?.let { "https://y.gtimg.cn/music/photo_new/T002R300x300M000$it.jpg" },
            qualities = qs,
            extras = extras,
        )
    }
}

// MARK: - Migu (app.c.nf.migu.cn) -------------------------------------------------------------

private class MiguSearch(private val http: CatalogHttp) : SongCatalog {
    override val source = SourceID.MG

    override suspend fun search(keyword: String, page: Int): List<Track> {
        val url = "https://app.c.nf.migu.cn/MIGUM2.0/v1.0/content/search_all.do?isCopyright=1&isCorrect=1" +
            "&pageNo=$page&pageSize=30&searchSwitch=%7B%22song%22%3A1%7D&sort=0&text=${urlEncode(keyword)}"
        val text = http.getText(url, mapOf("User-Agent" to "Android_migu"))
        val list = JSONObject(text).optJSONObject("songResultData")?.optJSONArray("resultList") ?: return emptyList()
        val out = mutableListOf<Track>()
        for (i in 0 until list.length()) {
            when (val entry = list.opt(i)) {
                is JSONArray -> for (j in 0 until entry.length()) build(entry.optJSONObject(j))?.let(out::add)
                is JSONObject -> build(entry)?.let(out::add)
            }
        }
        return out
    }

    private fun build(d: JSONObject?): Track? {
        d ?: return null
        val copyrightId = d.optString("copyrightId")
        val id = d.optString("id")
        if (copyrightId.isEmpty() && id.isEmpty()) return null
        val songmid = id.ifEmpty { copyrightId }
        val singers = d.optJSONArray("singers")
        val singer = (0 until (singers?.length() ?: 0))
            .mapNotNull { singers?.optJSONObject(it)?.optString("name") }
            .filter { it.isNotEmpty() }.joinToString(" / ")
        val albums = d.optJSONArray("albums")
        val firstAlbum = albums?.optJSONObject(0)
        var pic: String? = null
        d.optJSONArray("imgItems")?.let { imgs ->
            for (i in 0 until imgs.length()) {
                val img = imgs.optJSONObject(i) ?: continue
                if (img.optString("imgSizeType") == "03") { pic = img.optString("img"); break }
                if (pic == null) pic = img.optString("img")
            }
        }
        val extras = mutableMapOf("copyrightId" to copyrightId)
        d.optString("lyricUrl").ifEmpty { null }?.let { extras["lrcUrl"] = it }
        // Migu MV: mvCopyrightId is the resourceId fed to /resourceinfo.do?resourceType=D.
        d.optString("mvCopyrightId").ifEmpty { null }?.let { extras["mvId"] = it }
        return Track(
            id = Track.makeID(SourceID.MG, songmid),
            name = d.optString("name", "未知"),
            singer = singer.ifEmpty { "未知" },
            albumName = firstAlbum?.optString("name")?.ifEmpty { null },
            albumId = firstAlbum?.optString("id")?.ifEmpty { null },
            source = SourceID.MG,
            songmid = songmid,
            duration = null,
            picURL = pic?.ifEmpty { null },
            qualities = listOf(Quality.K128, Quality.K320),
            extras = extras,
        )
    }
}
