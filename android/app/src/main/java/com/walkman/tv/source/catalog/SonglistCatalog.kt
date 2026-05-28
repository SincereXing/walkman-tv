package com.walkman.tv.source.catalog

import com.walkman.tv.data.model.Quality
import com.walkman.tv.data.model.SonglistDetail
import com.walkman.tv.data.model.SonglistInfo
import com.walkman.tv.data.model.SonglistOrder
import com.walkman.tv.data.model.SonglistTag
import com.walkman.tv.data.model.SonglistTagGroup
import com.walkman.tv.data.model.SourceID
import com.walkman.tv.data.model.Track
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.json.JSONObject

interface SonglistService {
    val source: SourceID
    val orders: List<SonglistOrder>
    suspend fun fetchTags(): List<SonglistTagGroup> = emptyList()
    suspend fun fetchRecommended(order: SonglistOrder, tag: SonglistTag, page: Int): List<SonglistInfo>
    suspend fun fetchDetail(list: SonglistInfo): SonglistDetail
    suspend fun search(keyword: String, page: Int): List<SonglistInfo> = emptyList()
}

/** Songlist (歌单) plaza via each platform's API, ported from iOS `Songlists`. */
class Songlists(private val http: CatalogHttp) {
    private val services = listOf(
        KuwoSonglist(http), NetEaseSonglist(http), KugouSonglist(http), QQSonglist(http),
    )
    fun serviceFor(source: SourceID): SonglistService? = services.firstOrNull { it.source == source }
    fun servicesAll(): List<SonglistService> = services
}

private const val UA = "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X)"

// MARK: - Kuwo

private class KuwoSonglist(private val http: CatalogHttp) : SonglistService {
    override val source = SourceID.KW
    override val orders = listOf(SonglistOrder("new", "最新"), SonglistOrder("hot", "最热"))

    override suspend fun fetchRecommended(order: SonglistOrder, tag: SonglistTag, page: Int): List<SonglistInfo> {
        val url = if (tag.id.isEmpty())
            "http://wapi.kuwo.cn/api/pc/classify/playlist/getRcmPlayList?loginUid=0&loginSid=0&appUid=76039576&pn=$page&rn=36&order=${order.id}"
        else
            "http://wapi.kuwo.cn/api/pc/classify/playlist/getTagPlayList?loginUid=0&loginSid=0&appUid=76039576&pn=$page&id=${tag.id}&rn=36"
        val json = runCatching { JSONObject(http.getText(url, mapOf("User-Agent" to UA))) }.getOrNull() ?: return emptyList()
        if (json.optInt("code") != 200) return emptyList()
        val raw = json.optJSONObject("data")?.optJSONArray("data") ?: return emptyList()
        return (0 until raw.length()).mapNotNull { buildInfo(raw.optJSONObject(it)) }
    }

    override suspend fun fetchTags(): List<SonglistTagGroup> {
        val url = "http://wapi.kuwo.cn/api/pc/classify/playlist/getTagList?cmd=rcm_keyword_playlist&user=0&prod=kwplayer_pc_9.0.5.0&vipver=9.0.5.0&source=kwplayer_pc_9.0.5.0&loginUid=0&loginSid=0&appUid=76039576"
        val json = runCatching { JSONObject(http.getText(url, mapOf("User-Agent" to UA))) }.getOrNull() ?: return emptyList()
        if (json.optInt("code") != 200) return emptyList()
        val raw = json.optJSONArray("data") ?: return emptyList()
        val groups = mutableListOf<SonglistTagGroup>()
        for (i in 0 until raw.length()) {
            val type = raw.optJSONObject(i) ?: continue
            val name = type.optString("name").ifEmpty { continue }
            val items = type.optJSONArray("data") ?: continue
            val tags = (0 until items.length()).mapNotNull { j ->
                val item = items.optJSONObject(j) ?: return@mapNotNull null
                if (item.opt("digest")?.toString() != "10000") return@mapNotNull null
                val id = item.opt("id")?.toString() ?: return@mapNotNull null
                if (id.isEmpty()) null else SonglistTag(id, item.optString("name"))
            }
            if (tags.isNotEmpty()) groups.add(SonglistTagGroup(name, tags))
        }
        return groups
    }

    override suspend fun search(keyword: String, page: Int): List<SonglistInfo> {
        val url = "http://search.kuwo.cn/r.s?client=kt&all=${urlEncode(keyword)}&pn=${page - 1}&rn=30&uid=794762570&ver=kwplayer_ar_9.2.2.1&vipver=1&show_copyright_off=1&newver=1&ft=playlist&cluster=0&strategy=2012&encoding=utf8&rformat=json&vermerge=1&mobi=1&issubtitle=1"
        val text = runCatching { http.getText(url, mapOf("User-Agent" to UA)) }.getOrNull() ?: return emptyList()
        val abslist = KuwoTolerantJSON.array(text, "abslist")
        return (0 until abslist.length()).mapNotNull { i ->
            val d = abslist.optJSONObject(i) ?: return@mapNotNull null
            val id = d.opt("playlistid")?.toString()?.ifEmpty { null } ?: return@mapNotNull null
            val plays = d.optString("playcnt").toLongOrNull() ?: d.optLong("playcnt")
            SonglistInfo(
                id = id, source = SourceID.KW,
                name = d.optString("name", "未知歌单").decodeHtmlEntities(),
                author = d.optString("nickname").decodeHtmlEntities(),
                picURL = d.optString("pic").ifEmpty { null },
                trackCount = d.optString("songnum").toIntOrNull() ?: d.optInt("songnum").takeIf { it > 0 },
                playCount = formatPlayCount(plays),
            )
        }
    }

    private fun buildInfo(d: JSONObject?): SonglistInfo? {
        d ?: return null
        val id = d.optString("id").ifEmpty { return null }
        val total = d.optString("total").toIntOrNull() ?: d.optInt("total").takeIf { it > 0 }
        val plays = d.optString("listencnt").toLongOrNull() ?: d.optLong("listencnt")
        return SonglistInfo(
            id = id, source = SourceID.KW,
            name = d.optString("name", "未知歌单").decodeHtmlEntities(),
            author = d.optString("uname").decodeHtmlEntities(),
            picURL = d.optString("img").ifEmpty { null },
            trackCount = total,
            playCount = formatPlayCount(plays),
        )
    }

    override suspend fun fetchDetail(list: SonglistInfo): SonglistDetail {
        val url = "http://nplserver.kuwo.cn/pl.svc?op=getlistinfo&pid=${list.id}&pn=0&rn=300&encode=utf8&keyset=pl2012&identity=kuwo&pcmp4=1&vipver=MUSIC_9.0.5.0_W1&newver=1"
        val json = runCatching { JSONObject(http.getText(url, mapOf("User-Agent" to UA))) }.getOrNull()
            ?: return SonglistDetail(list, emptyList())
        if (json.optString("result") != "ok") return SonglistDetail(list, emptyList())
        val ml = json.optJSONArray("musiclist") ?: return SonglistDetail(list, emptyList())
        val raw = (0 until ml.length()).mapNotNull { build(ml.optJSONObject(it)) }
        val tracks = fillKuwoCovers(http, raw)
        val updated = list.copy(
            name = json.optString("title").ifEmpty { list.name },
            author = json.optString("uname").ifEmpty { list.author },
            picURL = json.optString("pic").ifEmpty { null } ?: list.picURL,
            trackCount = json.optInt("total").takeIf { it > 0 } ?: tracks.size,
        )
        return SonglistDetail(updated, tracks)
    }

    private fun build(d: JSONObject?): Track? {
        d ?: return null
        val id = d.optString("id").ifEmpty { return null }
        val qs = mutableListOf<Quality>()
        val codes = d.optString("formats").split("|").toSet()
        if ("MP3128" in codes) qs.add(Quality.K128)
        if ("MP3H" in codes) qs.add(Quality.K320)
        if ("ALFLAC" in codes) qs.add(Quality.FLAC)
        if ("HIRFLAC" in codes || "AC4256" in codes) qs.add(Quality.FLAC24)
        if (qs.isEmpty()) { qs.add(Quality.K128); qs.add(Quality.K320) }
        return Track(
            id = Track.makeID(SourceID.KW, id),
            name = decode(d.optString("name", "未知")),
            singer = decode(d.optString("artist")).ifEmpty { "未知歌手" },
            albumName = decode(d.optString("album")).ifEmpty { null },
            albumId = d.optString("albumid").ifEmpty { null },
            source = SourceID.KW,
            songmid = id,
            duration = d.optString("duration").toIntOrNull()?.takeIf { it > 0 },
            picURL = null,
            qualities = qs,
        )
    }

    private fun decode(s: String) = s.replace("&nbsp;", " ").replace("&amp;", "&")
}

// MARK: - NetEase

private class NetEaseSonglist(private val http: CatalogHttp) : SonglistService {
    override val source = SourceID.WY
    override val orders = listOf(SonglistOrder("hot", "最热"))

    override suspend fun fetchRecommended(order: SonglistOrder, tag: SonglistTag, page: Int): List<SonglistInfo> {
        val limit = 36
        val offset = (page - 1) * limit
        val catName = if (tag.id.isEmpty()) "全部" else tag.name
        val url = "https://music.163.com/api/playlist/list?cat=${urlEncode(catName)}&order=${order.id}&limit=$limit&offset=$offset"
        val arr = runCatching { JSONObject(http.getText(url, refererHeaders())).optJSONArray("playlists") }.getOrNull() ?: return emptyList()
        return (0 until arr.length()).mapNotNull { buildInfo(arr.optJSONObject(it)) }
    }

    override suspend fun fetchTags(): List<SonglistTagGroup> = staticTags

    override suspend fun search(keyword: String, page: Int): List<SonglistInfo> {
        val limit = 30
        val offset = (page - 1) * limit
        val url = "https://music.163.com/api/search/get?s=${urlEncode(keyword)}&type=1000&limit=$limit&offset=$offset"
        val arr = runCatching {
            JSONObject(http.getText(url, refererHeaders())).optJSONObject("result")?.optJSONArray("playlists")
        }.getOrNull() ?: return emptyList()
        return (0 until arr.length()).mapNotNull { buildInfo(arr.optJSONObject(it)) }
    }

    private fun buildInfo(d: JSONObject?): SonglistInfo? {
        d ?: return null
        val id = d.opt("id")?.toString() ?: return null
        val creator = d.optJSONObject("creator")
        return SonglistInfo(
            id = id, source = SourceID.WY,
            name = d.optString("name", "未知歌单").decodeHtmlEntities(),
            author = (creator?.optString("nickname") ?: "").decodeHtmlEntities(),
            picURL = d.optString("coverImgUrl").ifEmpty { null },
            trackCount = d.optInt("trackCount").takeIf { it > 0 },
            playCount = formatPlayCount(d.optLong("playCount")),
        )
    }

    override suspend fun fetchDetail(list: SonglistInfo): SonglistDetail {
        val url = "https://music.163.com/api/playlist/detail?id=${list.id}"
        val result = runCatching { JSONObject(http.getText(url, refererHeaders())).optJSONObject("result") }.getOrNull()
            ?: return SonglistDetail(list, emptyList())
        val trackArr = result.optJSONArray("tracks")
        val tracks = (0 until (trackArr?.length() ?: 0)).mapNotNull { buildNetEaseTrack(trackArr?.optJSONObject(it)) }
        val updated = list.copy(
            name = result.optString("name").ifEmpty { list.name },
            author = result.optJSONObject("creator")?.optString("nickname")?.ifEmpty { null } ?: list.author,
            picURL = result.optString("coverImgUrl").ifEmpty { null } ?: list.picURL,
            trackCount = result.optInt("trackCount").takeIf { it > 0 } ?: tracks.size,
        )
        return SonglistDetail(updated, tracks)
    }

    private fun refererHeaders() = mapOf("Referer" to "https://music.163.com/", "User-Agent" to UA)

    private val staticTags = listOf(
        group("语种", listOf("华语", "欧美", "日语", "韩语", "粤语", "小语种")),
        group("风格", listOf("流行", "摇滚", "民谣", "电子", "舞曲", "说唱", "轻音乐", "爵士", "乡村", "R&B/Soul", "古典", "民族", "英伦", "金属", "朋克", "蓝调", "雷鬼", "世界音乐", "拉丁", "另类/独立", "New Age", "古风", "后摇", "Bossa Nova")),
        group("场景", listOf("清晨", "夜晚", "学习", "工作", "午休", "下午茶", "地铁", "驾车", "运动", "旅行", "散步", "酒吧")),
        group("情感", listOf("怀旧", "清新", "浪漫", "性感", "伤感", "治愈", "放松", "孤独", "感动", "兴奋", "快乐", "安静", "思念")),
        group("主题", listOf("影视原声", "ACG", "儿童", "校园", "游戏", "70后", "80后", "90后", "网络歌曲", "KTV", "经典", "翻唱", "吉他", "钢琴", "器乐", "榜单", "00后")),
    )

    private fun group(name: String, names: List<String>) = SonglistTagGroup(name, names.map { SonglistTag(it, it) })
}

// MARK: - QQ

private class QQSonglist(private val http: CatalogHttp) : SonglistService {
    override val source = SourceID.TX
    override val orders = listOf(SonglistOrder("5", "最热"), SonglistOrder("2", "最新"))

    override suspend fun fetchRecommended(order: SonglistOrder, tag: SonglistTag, page: Int): List<SonglistInfo> {
        val size = 36
        val inner: JSONObject = if (tag.id.isEmpty()) {
            JSONObject()
                .put("comm", JSONObject().put("cv", 1602).put("ct", 20))
                .put(
                    "playlist",
                    JSONObject()
                        .put("method", "get_playlist_by_tag")
                        .put("param", JSONObject().put("id", 10000000).put("sin", size * (page - 1)).put("size", size).put("order", order.id.toIntOrNull() ?: 5).put("cur_page", page))
                        .put("module", "playlist.PlayListPlazaServer"),
                )
        } else {
            val cid = tag.id.toIntOrNull() ?: 0
            JSONObject()
                .put("comm", JSONObject().put("cv", 1602).put("ct", 20))
                .put(
                    "playlist",
                    JSONObject()
                        .put("method", "get_category_content")
                        .put("param", JSONObject().put("titleid", cid).put("caller", "0").put("category_id", cid).put("size", size).put("page", page - 1).put("use_page", 1))
                        .put("module", "playlist.PlayListCategoryServer"),
                )
        }
        val url = "https://u.y.qq.com/cgi-bin/musicu.fcg?format=json&inCharset=utf-8&outCharset=utf-8&notice=0&platform=wk_v15.json&needNewCode=0&data=${urlEncode(inner.toString())}"
        val pdata = runCatching {
            JSONObject(http.getText(url, mapOf("User-Agent" to UA))).optJSONObject("playlist")?.optJSONObject("data")
        }.getOrNull() ?: return emptyList()
        return if (tag.id.isEmpty()) {
            val arr = pdata.optJSONArray("v_playlist") ?: return emptyList()
            (0 until arr.length()).mapNotNull { buildInfo(arr.optJSONObject(it)) }
        } else {
            val items = pdata.optJSONObject("content")?.optJSONArray("v_item") ?: return emptyList()
            (0 until items.length()).mapNotNull { buildInfo(items.optJSONObject(it)?.optJSONObject("basic")) }
        }
    }

    private fun buildInfo(d: JSONObject?): SonglistInfo? {
        d ?: return null
        val id = d.opt("tid")?.toString() ?: return null
        val creator = d.optJSONObject("creator_info") ?: d.optJSONObject("creator")
        val cover = d.optJSONObject("cover")
        val pic = d.optString("cover_url_medium").ifEmpty { d.optString("cover_url_big") }
            .ifEmpty { cover?.optString("medium_url") ?: "" }
            .ifEmpty { cover?.optString("default_url") ?: "" }
        val plays = d.optLong("access_num").takeIf { it > 0 } ?: d.optLong("play_cnt")
        return SonglistInfo(
            id = id, source = SourceID.TX,
            name = d.optString("title", "未知歌单").decodeHtmlEntities(),
            author = (creator?.optString("nick") ?: "").decodeHtmlEntities(),
            picURL = pic.ifEmpty { null },
            trackCount = d.optJSONArray("song_ids")?.length(),
            playCount = formatPlayCount(plays),
        )
    }

    override suspend fun fetchTags(): List<SonglistTagGroup> {
        val url = "https://u.y.qq.com/cgi-bin/musicu.fcg?loginUin=0&hostUin=0&format=json&inCharset=utf-8&outCharset=utf-8&notice=0&platform=wk_v15.json&needNewCode=0&data=%7B%22tags%22%3A%7B%22method%22%3A%22get_all_categories%22%2C%22param%22%3A%7B%22qq%22%3A%22%22%7D%2C%22module%22%3A%22playlist.PlaylistAllCategoriesServer%22%7D%7D"
        val groups = runCatching {
            JSONObject(http.getText(url, mapOf("User-Agent" to UA))).optJSONObject("tags")?.optJSONObject("data")?.optJSONArray("v_group")
        }.getOrNull() ?: return emptyList()
        val out = mutableListOf<SonglistTagGroup>()
        for (i in 0 until groups.length()) {
            val g = groups.optJSONObject(i) ?: continue
            val name = g.optString("group_name").ifEmpty { continue }
            val items = g.optJSONArray("v_item") ?: continue
            val tags = (0 until items.length()).mapNotNull { j ->
                val item = items.optJSONObject(j) ?: return@mapNotNull null
                val id = item.opt("id")?.toString() ?: return@mapNotNull null
                SonglistTag(id, item.optString("name"))
            }
            if (tags.isNotEmpty()) out.add(SonglistTagGroup(name, tags))
        }
        return out
    }

    override suspend fun fetchDetail(list: SonglistInfo): SonglistDetail {
        val url = "https://c.y.qq.com/qzone/fcg-bin/fcg_ucc_getcdinfo_byids_cp.fcg?type=1&json=1&utf8=1&onlysong=0&new_format=1&disstid=${list.id}&format=json&inCharset=utf8&outCharset=utf-8&notice=0&platform=yqq.json&needNewCode=0"
        val headers = mapOf("Origin" to "https://y.qq.com", "Referer" to "https://y.qq.com/n/yqq/playsquare/${list.id}.html", "User-Agent" to UA)
        val cd = runCatching {
            JSONObject(http.getText(url, headers)).optJSONArray("cdlist")?.optJSONObject(0)
        }.getOrNull() ?: return SonglistDetail(list, emptyList())
        val songlist = cd.optJSONArray("songlist") ?: return SonglistDetail(list, emptyList())
        val tracks = (0 until songlist.length()).mapNotNull { buildQqTrack(songlist.optJSONObject(it)) }
        val updated = list.copy(
            name = cd.optString("dissname").ifEmpty { list.name },
            author = cd.optString("nickname").ifEmpty { list.author },
            picURL = cd.optString("logo").ifEmpty { null } ?: list.picURL,
            trackCount = tracks.size,
        )
        return SonglistDetail(updated, tracks)
    }
}

// MARK: - Kugou

private class KugouSonglist(private val http: CatalogHttp) : SonglistService {
    override val source = SourceID.KG
    override val orders = listOf(
        SonglistOrder("5", "推荐"), SonglistOrder("6", "最热"), SonglistOrder("7", "最新"),
        SonglistOrder("3", "热藏"), SonglistOrder("8", "飙升"),
    )
    private val kgUA = "Mozilla/5.0 (iPhone; CPU iPhone OS 9_1 like Mac OS X) AppleWebKit/601.1.46 (KHTML, like Gecko) Version/9.0 Mobile/13B143 Safari/601.1"

    override suspend fun fetchRecommended(order: SonglistOrder, tag: SonglistTag, page: Int): List<SonglistInfo> {
        val url = "http://www2.kugou.kugou.com/yueku/v9/special/getSpecial?is_ajax=1&cdn=cdn&t=${order.id}&c=${tag.id}&p=$page"
        val arr = runCatching { JSONObject(http.getText(url, mapOf("User-Agent" to kgUA))).optJSONArray("special_db") }.getOrNull() ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val d = arr.optJSONObject(i) ?: return@mapNotNull null
            val id = d.opt("specialid")?.toString() ?: return@mapNotNull null
            val plays = d.optString("total_play_count").ifEmpty { formatPlayCount(d.optLong("play_count")) }
            val img = d.optString("img").ifEmpty { d.optString("imgurl") }
            SonglistInfo(
                id = id, source = SourceID.KG,
                name = d.optString("specialname", "未知歌单").decodeHtmlEntities(),
                author = d.optString("nickname").decodeHtmlEntities(),
                picURL = img.ifEmpty { null }?.replace("{size}", "240"),
                trackCount = d.optInt("songcount").takeIf { it > 0 },
                playCount = plays,
            )
        }
    }

    override suspend fun fetchTags(): List<SonglistTagGroup> {
        val url = "http://www2.kugou.kugou.com/yueku/v9/special/getSpecial?is_smarty=1&"
        val json = runCatching { JSONObject(http.getText(url, mapOf("User-Agent" to kgUA))) }.getOrNull() ?: return emptyList()
        if (json.optInt("status") != 1) return emptyList()
        val tagids = json.optJSONObject("data")?.optJSONObject("tagids") ?: return emptyList()
        val out = mutableListOf<SonglistTagGroup>()
        tagids.keys().forEach { name ->
            val items = tagids.optJSONObject(name)?.optJSONArray("data") ?: return@forEach
            val tags = (0 until items.length()).mapNotNull { j ->
                val item = items.optJSONObject(j) ?: return@mapNotNull null
                val id = item.opt("id")?.toString() ?: return@mapNotNull null
                SonglistTag(id, item.optString("name"))
            }
            if (tags.isNotEmpty()) out.add(SonglistTagGroup(name, tags))
        }
        return out
    }

    override suspend fun search(keyword: String, page: Int): List<SonglistInfo> {
        val url = "http://msearchretry.kugou.com/api/v3/search/special?keyword=${urlEncode(keyword)}&page=$page&pagesize=30&showtype=10&filter=0&version=7910&sver=2"
        val info = runCatching { JSONObject(http.getText(url, mapOf("User-Agent" to kgUA))).optJSONObject("data")?.optJSONArray("info") }.getOrNull() ?: return emptyList()
        return (0 until info.length()).mapNotNull { i ->
            val item = info.optJSONObject(i) ?: return@mapNotNull null
            val id = item.opt("specialid")?.toString() ?: return@mapNotNull null
            val img = item.optString("imgurl").ifEmpty { item.optString("img") }
            SonglistInfo(
                id = id, source = SourceID.KG,
                name = item.optString("specialname", "未知歌单").decodeHtmlEntities(),
                author = item.optString("nickname").decodeHtmlEntities(),
                picURL = img.ifEmpty { null }?.replace("{size}", "240"),
                trackCount = item.optInt("songcount").takeIf { it > 0 },
                playCount = formatPlayCount(item.optLong("playcount")),
            )
        }
    }

    override suspend fun fetchDetail(list: SonglistInfo): SonglistDetail {
        val htmlUrl = "http://www2.kugou.kugou.com/yueku/v9/special/single/${list.id}-5-9999.html"
        val html = runCatching { http.getText(htmlUrl, mapOf("User-Agent" to kgUA)) }.getOrNull()
            ?: return SonglistDetail(list, emptyList())
        val hashes = extractHashes(html)
        if (hashes.isEmpty()) return SonglistDetail(list, emptyList())
        val tracks = resolveHashes(hashes)
        return SonglistDetail(list.copy(trackCount = tracks.size), tracks)
    }

    private fun extractHashes(html: String): List<String> {
        val marker = "global.data = "
        val start = html.indexOf(marker)
        if (start < 0) return emptyList()
        val after = html.substring(start + marker.length)
        val end = after.indexOf("];")
        if (end < 0) return emptyList()
        val arrLiteral = after.substring(0, end + 1)
        val arr = runCatching { org.json.JSONArray(arrLiteral) }.getOrNull() ?: return emptyList()
        return (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.optString("hash")?.ifEmpty { null }?.uppercase() }
    }

    private suspend fun resolveHashes(hashes: List<String>): List<Track> {
        val capped = hashes.take(500)
        val chunks = capped.chunked(100)
        val indexed = coroutineScope {
            chunks.mapIndexed { i, chunk -> async { i to runCatching { resolveChunk(chunk) }.getOrDefault(emptyList()) } }.awaitAll()
        }
        return indexed.sortedBy { it.first }.flatMap { it.second }
    }

    private suspend fun resolveChunk(hashes: List<String>): List<Track> {
        if (hashes.isEmpty()) return emptyList()
        val body = JSONObject()
            .put("area_code", "1").put("show_privilege", 1).put("show_album_info", "1").put("is_publish", "")
            .put("appid", 1005).put("clientver", 11451).put("mid", "1").put("dfid", "-")
            .put("clienttime", 1586163263991L).put("key", "OIlwieks28dk2k092lksi2UIkp")
            .put("fields", "album_info,author_name,audio_info,ori_audio_name,base,songname")
            .put("data", org.json.JSONArray().apply { hashes.forEach { put(JSONObject().put("hash", it)) } })
        val headers = mapOf(
            "KG-THash" to "13a3164", "KG-RC" to "1", "KG-Fake" to "0", "KG-RF" to "00869891",
            "User-Agent" to "Android712-AndroidPhone-11451-376-0-FeeCacheUpdate-wifi", "x-router" to "kmr.service.kugou.com",
        )
        val outer = runCatching {
            JSONObject(http.postJson("http://gateway.kugou.com/v2/album_audio/audio", body.toString(), headers)).optJSONArray("data")
        }.getOrNull() ?: return emptyList()
        val out = mutableListOf<Track>()
        for (i in 0 until outer.length()) {
            val row = outer.opt(i)
            val d = when (row) {
                is org.json.JSONArray -> row.optJSONObject(0)
                is JSONObject -> row
                else -> null
            }
            build(d)?.let(out::add)
        }
        return out
    }

    private fun build(d: JSONObject?): Track? {
        d ?: return null
        val audio = d.optJSONObject("audio_info") ?: JSONObject()
        val hash = audio.optString("hash").ifEmpty { return null }
        val audioId = audio.opt("audio_id")?.toString()?.ifEmpty { hash } ?: hash
        val albumInfo = d.optJSONObject("album_info")
        val albumId = albumInfo?.opt("album_id")?.toString()?.ifEmpty { null }
        val durMs = audio.optString("timelength").toIntOrNull() ?: audio.optInt("timelength")
        fun nonZero(key: String): Boolean {
            val v = audio.opt(key)
            return when (v) { is String -> v != "0" && v.isNotEmpty(); is Number -> v.toLong() != 0L; else -> false }
        }
        val qs = mutableListOf<Quality>()
        if (nonZero("filesize")) qs.add(Quality.K128)
        if (nonZero("filesize_320")) qs.add(Quality.K320)
        if (nonZero("filesize_flac")) qs.add(Quality.FLAC)
        if (nonZero("filesize_high")) qs.add(Quality.FLAC24)
        if (qs.isEmpty()) qs.add(Quality.K128)
        val extras = mutableMapOf("hash" to hash)
        albumId?.let { extras["albumId"] = it }
        val pic = albumInfo?.optString("sizable_cover")?.ifEmpty { null }?.replace("{size}", "240")
        return Track(
            id = Track.makeID(SourceID.KG, audioId),
            name = decode(d.optString("songname").ifEmpty { d.optString("ori_audio_name", "未知") }),
            singer = decode(d.optString("author_name")).ifEmpty { "未知歌手" },
            albumName = albumInfo?.optString("album_name")?.let { decode(it) }?.ifEmpty { null },
            albumId = albumId,
            source = SourceID.KG,
            songmid = audioId,
            duration = if (durMs > 0) durMs / 1000 else null,
            picURL = pic,
            qualities = qs,
            extras = extras,
        )
    }

    private fun decode(s: String) = s.replace("&nbsp;", " ").replace("&amp;", "&")
}
