package com.walkman.tv.source.catalog

import com.walkman.tv.data.model.BoardInfo
import com.walkman.tv.data.model.Quality
import com.walkman.tv.data.model.SourceID
import com.walkman.tv.data.model.Track
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.json.JSONObject

interface BoardService {
    val source: SourceID
    val list: List<BoardInfo>
    suspend fun fetchBoards(): List<BoardInfo> = list
    suspend fun fetchTracks(bangid: String, page: Int): List<Track>
}

/** Leaderboards via each platform's own API, ported from iOS `Boards`. */
class Boards(private val http: CatalogHttp) {
    private val services: List<BoardService> = listOf(
        KuwoBoard(http), NetEaseBoard(http), KugouBoard(http), QQBoard(http),
    )

    fun serviceFor(source: SourceID): BoardService? = services.firstOrNull { it.source == source }
    fun servicesAll(): List<BoardService> = services
}

/** Fill per-song covers via Kuwo's artistpicserver (plain-text URL body). */
internal suspend fun fillKuwoCovers(http: CatalogHttp, tracks: List<Track>): List<Track> = coroutineScope {
    tracks.map { t ->
        async {
            if (t.picURL != null) return@async t
            val url = "http://artistpicserver.kuwo.cn/pic.web?corp=kuwo&type=rid_pic&pictype=500&size=500&rid=${t.songmid}"
            val pic = runCatching { http.getText(url).trim() }.getOrNull()
            if (pic != null && pic.startsWith("http")) t.copy(picURL = pic) else t
        }
    }.awaitAll()
}

// MARK: - Kuwo

private class KuwoBoard(private val http: CatalogHttp) : BoardService {
    override val source = SourceID.KW
    override val list = listOf(
        BoardInfo("kw__93", SourceID.KW, "93", "飙升榜"),
        BoardInfo("kw__16", SourceID.KW, "16", "热歌榜"),
        BoardInfo("kw__17", SourceID.KW, "17", "新歌榜"),
        BoardInfo("kw__158", SourceID.KW, "158", "抖音热歌榜"),
        BoardInfo("kw__187", SourceID.KW, "187", "流行趋势榜"),
        BoardInfo("kw__104", SourceID.KW, "104", "华语榜"),
        BoardInfo("kw__182", SourceID.KW, "182", "粤语榜"),
        BoardInfo("kw__22", SourceID.KW, "22", "欧美榜"),
        BoardInfo("kw__184", SourceID.KW, "184", "韩语榜"),
        BoardInfo("kw__183", SourceID.KW, "183", "日语榜"),
        BoardInfo("kw__26", SourceID.KW, "26", "经典怀旧榜"),
        BoardInfo("kw__278", SourceID.KW, "278", "古风音乐榜"),
        BoardInfo("kw__242", SourceID.KW, "242", "电音榜"),
        BoardInfo("kw__186", SourceID.KW, "186", "ACG神曲榜"),
        BoardInfo("kw__284", SourceID.KW, "284", "热评榜"),
        BoardInfo("kw__64", SourceID.KW, "64", "影视金曲榜"),
        BoardInfo("kw__12", SourceID.KW, "12", "Billboard榜"),
    )

    override suspend fun fetchBoards(): List<BoardInfo> {
        val url = "http://qukudata.kuwo.cn/q.k?op=query&cont=tree&node=2&pn=0&rn=200&fmt=json&level=2"
        val children = runCatching { JSONObject(http.getText(url)).optJSONArray("child") }.getOrNull() ?: return list
        val picByBangid = mutableMapOf<String, String>()
        for (i in 0 until children.length()) {
            val c = children.optJSONObject(i) ?: continue
            val bangid = c.optString("sourceid")
            val pic = c.optString("pic")
            if (bangid.isNotEmpty() && pic.isNotEmpty()) picByBangid[bangid] = pic
        }
        return list.map { it.copy(picURL = picByBangid[it.bangid] ?: it.picURL) }
    }

    override suspend fun fetchTracks(bangid: String, page: Int): List<Track> {
        val pn = maxOf(0, page - 1)
        val url = "http://kbangserver.kuwo.cn/ksong.s?from=pc&fmt=json&pn=$pn&rn=100&type=bang&data=content&id=$bangid&show_copyright_off=0&pcmp4=1&isbang=1"
        val ml = runCatching { JSONObject(http.getText(url)).optJSONArray("musiclist") }.getOrNull() ?: return emptyList()
        val tracks = (0 until ml.length()).mapNotNull { build(ml.optJSONObject(it)) }
        return fillKuwoCovers(http, tracks)
    }

    private fun build(d: JSONObject?): Track? {
        d ?: return null
        val id = d.optString("id").ifEmpty { return null }
        val qs = mutableListOf<Quality>()
        val codes = d.optString("formats").split("|").toSet()
        if ("MP3128" in codes) qs.add(Quality.K128)
        if ("MP3H" in codes) qs.add(Quality.K320)
        if ("ALFLAC" in codes) qs.add(Quality.FLAC)
        if ("AC4256" in codes || "DDJOC768" in codes) qs.add(Quality.FLAC24)
        if (qs.isEmpty()) { qs.add(Quality.K128); qs.add(Quality.K320) }
        val duration = (d.optString("duration").ifEmpty { d.optString("song_duration") }).toIntOrNull()
        // Kuwo: MVFLAG=="1" means the song has an MV; the mvId is the same rid as the song.
        val extras = mutableMapOf<String, String>()
        if (d.optString("MVFLAG") == "1") extras["mvId"] = id
        return Track(
            id = Track.makeID(SourceID.KW, id),
            name = decode(d.optString("name", "未知")),
            singer = decode(d.optString("artist")).ifEmpty { "未知歌手" },
            albumName = decode(d.optString("album")).ifEmpty { null },
            albumId = d.optString("albumid").ifEmpty { null },
            source = SourceID.KW,
            songmid = id,
            duration = duration?.takeIf { it > 0 },
            picURL = null,
            qualities = qs,
            extras = extras,
        )
    }

    private fun decode(s: String) = s.replace("&nbsp;", " ").replace("&amp;", "&")
}

// MARK: - NetEase

private class NetEaseBoard(private val http: CatalogHttp) : BoardService {
    override val source = SourceID.WY
    override val list = listOf(
        BoardInfo("wy__19723756", SourceID.WY, "19723756", "飙升榜"),
        BoardInfo("wy__3779629", SourceID.WY, "3779629", "新歌榜"),
        BoardInfo("wy__3778678", SourceID.WY, "3778678", "热歌榜"),
        BoardInfo("wy__2884035", SourceID.WY, "2884035", "原创榜"),
    )

    override suspend fun fetchBoards(): List<BoardInfo> {
        val headers = mapOf("Referer" to "https://music.163.com/")
        val arr = runCatching { JSONObject(http.getText("https://music.163.com/api/toplist", headers)).optJSONArray("list") }
            .getOrNull() ?: return list
        return (0 until arr.length()).mapNotNull { i ->
            val item = arr.optJSONObject(i) ?: return@mapNotNull null
            val id = item.opt("id")?.toString() ?: return@mapNotNull null
            BoardInfo("wy__$id", SourceID.WY, id, item.optString("name", "未知"), item.optString("coverImgUrl").ifEmpty { null })
        }
    }

    override suspend fun fetchTracks(bangid: String, page: Int): List<Track> {
        val headers = mapOf("Referer" to "https://music.163.com/")
        val tracks = runCatching {
            JSONObject(http.getText("https://music.163.com/api/playlist/detail?id=$bangid", headers))
                .optJSONObject("result")?.optJSONArray("tracks")
        }.getOrNull() ?: return emptyList()
        return (0 until tracks.length()).mapNotNull { buildNetEaseTrack(tracks.optJSONObject(it)) }
    }
}

internal fun buildNetEaseTrack(d: JSONObject?): Track? {
    d ?: return null
    val id = d.opt("id")?.toString() ?: return null
    val artists = d.optJSONArray("artists")
    val singer = (0 until (artists?.length() ?: 0))
        .mapNotNull { artists?.optJSONObject(it)?.optString("name") }
        .filter { it.isNotEmpty() }.joinToString(" / ")
    val album = d.optJSONObject("album")
    val qs = mutableListOf(Quality.K128)
    if (d.optJSONObject("mMusic") != null) qs.add(Quality.K320)
    if (d.optJSONObject("hMusic") != null) qs.add(Quality.FLAC)
    // NetEase MV: 'mvid' > 0 means an MV exists. Same field name in board / songlist / search.
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

// MARK: - Kugou

private class KugouBoard(private val http: CatalogHttp) : BoardService {
    override val source = SourceID.KG
    override val list = listOf(
        BoardInfo("kg__8888", SourceID.KG, "8888", "TOP500"),
        BoardInfo("kg__6666", SourceID.KG, "6666", "飙升榜"),
        BoardInfo("kg__23784", SourceID.KG, "23784", "网络红歌榜"),
        BoardInfo("kg__52144", SourceID.KG, "52144", "抖音热歌榜"),
        BoardInfo("kg__52767", SourceID.KG, "52767", "快手热歌榜"),
        BoardInfo("kg__24971", SourceID.KG, "24971", "DJ热歌榜"),
        BoardInfo("kg__44412", SourceID.KG, "44412", "说唱先锋榜"),
        BoardInfo("kg__31308", SourceID.KG, "31308", "内地榜"),
        BoardInfo("kg__33160", SourceID.KG, "33160", "电音榜"),
        BoardInfo("kg__33161", SourceID.KG, "33161", "古风新歌榜"),
        BoardInfo("kg__33165", SourceID.KG, "33165", "粤语金曲榜"),
        BoardInfo("kg__33166", SourceID.KG, "33166", "欧美金曲榜"),
        BoardInfo("kg__33163", SourceID.KG, "33163", "影视金曲榜"),
        BoardInfo("kg__31311", SourceID.KG, "31311", "韩国榜"),
        BoardInfo("kg__31312", SourceID.KG, "31312", "日本榜"),
    )

    override suspend fun fetchBoards(): List<BoardInfo> {
        val url = "http://mobilecdnbj.kugou.com/api/v5/rank/list?version=9108&plat=0&showtype=2&parentid=0&apiver=6&area_code=1&withsong=1"
        val info = runCatching { JSONObject(http.getText(url)).optJSONObject("data")?.optJSONArray("info") }.getOrNull() ?: return list
        val picByBangid = mutableMapOf<String, String>()
        for (i in 0 until info.length()) {
            val b = info.optJSONObject(i) ?: continue
            val bangid = b.opt("rankid")?.toString() ?: continue
            val raw = b.optString("imgurl").ifEmpty { b.optString("img_cover") }
            if (bangid.isNotEmpty() && raw.isNotEmpty()) picByBangid[bangid] = raw.replace("{size}", "240")
        }
        return list.map { it.copy(picURL = picByBangid[it.bangid] ?: it.picURL) }
    }

    override suspend fun fetchTracks(bangid: String, page: Int): List<Track> {
        val url = "http://mobilecdnbj.kugou.com/api/v3/rank/song?version=9108&ranktype=1&plat=0&pagesize=100&area_code=1&page=$page&rankid=$bangid&with_res_tag=0&show_portrait_mv=1"
        val info = runCatching { JSONObject(http.getText(url)).optJSONObject("data")?.optJSONArray("info") }.getOrNull() ?: return emptyList()
        return (0 until info.length()).mapNotNull { build(info.optJSONObject(it)) }
    }

    private fun build(d: JSONObject?): Track? {
        d ?: return null
        val hash = d.optString("hash").ifEmpty { return null }
        val audioId = when (val a = d.opt("audio_id")) {
            is Number -> a.toLong().toString()
            is String -> a.ifEmpty { hash }
            else -> hash
        }
        val authors = d.optJSONArray("authors")
        val singer = (0 until (authors?.length() ?: 0))
            .mapNotNull { authors?.optJSONObject(it)?.optString("author_name") }
            .filter { it.isNotEmpty() }.joinToString("、")
        val albumId = d.opt("album_id")?.toString()?.ifEmpty { null }
        val duration = when (val dur = d.opt("duration")) {
            is Number -> dur.toInt()
            is String -> dur.toIntOrNull() ?: 0
            else -> 0
        }
        val qs = mutableListOf<Quality>()
        if (d.optLong("filesize") != 0L) qs.add(Quality.K128)
        if (d.optLong("320filesize") != 0L) qs.add(Quality.K320)
        if (d.optLong("sqfilesize") != 0L) qs.add(Quality.FLAC)
        if (d.optLong("filesize_high") != 0L) qs.add(Quality.FLAC24)
        if (qs.isEmpty()) qs.add(Quality.K128)
        val extras = mutableMapOf("hash" to hash)
        albumId?.let { extras["albumId"] = it }
        // Kugou MV: rank API returns `mvhash` (lower-case) as the MV identifier when present.
        d.optString("mvhash").ifEmpty { null }?.let { extras["mvId"] = it }
        val pic = authors?.optJSONObject(0)?.optString("sizable_avatar")?.ifEmpty { null }?.replace("{size}", "150")
        return Track(
            id = Track.makeID(SourceID.KG, audioId),
            name = decode(d.optString("songname", "未知")),
            singer = singer.ifEmpty { "未知歌手" },
            albumName = decode(d.optString("remark")).ifEmpty { null },
            albumId = albumId,
            source = SourceID.KG,
            songmid = audioId,
            duration = duration.takeIf { it > 0 },
            picURL = pic,
            qualities = qs,
            extras = extras,
        )
    }

    private fun decode(s: String) = s.replace("&nbsp;", " ").replace("&amp;", "&")
}

// MARK: - QQ

private class QQBoard(private val http: CatalogHttp) : BoardService {
    override val source = SourceID.TX
    override val list = listOf(
        BoardInfo("tx__4", SourceID.TX, "4", "流行指数榜"),
        BoardInfo("tx__26", SourceID.TX, "26", "热歌榜"),
        BoardInfo("tx__27", SourceID.TX, "27", "新歌榜"),
        BoardInfo("tx__62", SourceID.TX, "62", "飙升榜"),
        BoardInfo("tx__28", SourceID.TX, "28", "网络歌曲榜"),
        BoardInfo("tx__60", SourceID.TX, "60", "抖快榜"),
        BoardInfo("tx__5", SourceID.TX, "5", "内地榜"),
        BoardInfo("tx__3", SourceID.TX, "3", "欧美榜"),
        BoardInfo("tx__59", SourceID.TX, "59", "香港地区榜"),
        BoardInfo("tx__16", SourceID.TX, "16", "韩国榜"),
        BoardInfo("tx__17", SourceID.TX, "17", "日本榜"),
        BoardInfo("tx__65", SourceID.TX, "65", "国风热歌榜"),
        BoardInfo("tx__58", SourceID.TX, "58", "说唱榜"),
        BoardInfo("tx__29", SourceID.TX, "29", "影视金曲榜"),
        BoardInfo("tx__63", SourceID.TX, "63", "DJ舞曲榜"),
    )

    override suspend fun fetchBoards(): List<BoardInfo> {
        val url = "https://c.y.qq.com/v8/fcg-bin/fcg_myqq_toplist.fcg?g_tk=1928093487&inCharset=utf-8&outCharset=utf-8&notice=0&format=json&uin=0&needNewCode=1&platform=h5"
        val topList = runCatching { JSONObject(http.getText(url)).optJSONObject("data")?.optJSONArray("topList") }.getOrNull() ?: return list
        val picByBangid = mutableMapOf<String, String>()
        for (i in 0 until topList.length()) {
            val b = topList.optJSONObject(i) ?: continue
            val bangid = b.opt("id")?.toString() ?: continue
            val pic = b.optString("picUrl")
            if (bangid.isNotEmpty() && pic.isNotEmpty()) picByBangid[bangid] = pic
        }
        return list.map { it.copy(picURL = picByBangid[it.bangid] ?: it.picURL) }
    }

    override suspend fun fetchTracks(bangid: String, page: Int): List<Track> {
        val body = JSONObject()
            .put(
                "toplist",
                JSONObject()
                    .put("module", "musicToplist.ToplistInfoServer")
                    .put("method", "GetDetail")
                    .put("param", JSONObject().put("topid", bangid.toIntOrNull() ?: 0).put("num", 100)),
            )
            .put("comm", JSONObject().put("uin", 0).put("format", "json").put("ct", 20).put("cv", 1859))
        val songs = runCatching {
            JSONObject(http.postJson("https://u.y.qq.com/cgi-bin/musicu.fcg", body.toString()))
                .optJSONObject("toplist")?.optJSONObject("data")?.optJSONArray("songInfoList")
        }.getOrNull() ?: return emptyList()
        return (0 until songs.length()).mapNotNull { buildQqTrack(songs.optJSONObject(it)) }
    }
}

/** Shared QQ track builder for boards + songlists (mirrors iOS `QQTrackBuilder`). */
internal fun buildQqTrack(d: JSONObject?): Track? {
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
    // QQ MV: optional `mv.vid` (search shape) or `mv` as a direct string (toplist shape).
    d.optJSONObject("mv")?.optString("vid")?.ifEmpty { null }?.let { extras["mvId"] = it }
    if (!extras.containsKey("mvId")) {
        d.optString("mv").ifEmpty { null }?.let { extras["mvId"] = it }
    }
    return Track(
        id = Track.makeID(SourceID.TX, mid),
        name = d.optString("name").ifEmpty { d.optString("title", "未知") },
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
