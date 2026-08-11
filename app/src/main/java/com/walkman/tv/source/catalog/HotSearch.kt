package com.walkman.tv.source.catalog

import com.walkman.tv.data.model.SourceID
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.json.JSONObject

/** One platform's hot-search words, in rank order. */
data class HotSearchColumn(val source: SourceID, val words: List<String>)

/**
 * Hot / trending search words for each platform, shown on the search screen (and pushed to the
 * phone page) before the user has typed anything.
 *
 * - QQ / NetEase / Kugou: public endpoints, no signing.
 * - Kuwo: the `/api/www` endpoints are behind a `Secret` header derived from a cookie; we
 *   fabricate a self-consistent (cookie, Secret) pair — see [KuwoSecret]. The hot list is the
 *   `searchKey` suggestion endpoint called with an empty key.
 */
class HotSearch(private val http: CatalogHttp) {
    private val ua = "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X)"

    /** Fetch all four platforms in parallel; a platform that fails yields an empty column (kept
     *  so the UI still renders its labeled column). Order: 酷我 / 网易云 / 酷狗 / QQ. */
    suspend fun fetchAll(): List<HotSearchColumn> = coroutineScope {
        val jobs = listOf(
            async { HotSearchColumn(SourceID.KW, runCatching { kuwo() }.getOrDefault(emptyList())) },
            async { HotSearchColumn(SourceID.WY, runCatching { netease() }.getOrDefault(emptyList())) },
            async { HotSearchColumn(SourceID.KG, runCatching { kugou() }.getOrDefault(emptyList())) },
            async { HotSearchColumn(SourceID.TX, runCatching { qq() }.getOrDefault(emptyList())) },
        )
        jobs.awaitAll()
    }

    private suspend fun qq(): List<String> {
        val url = "https://c.y.qq.com/splcloud/fcgi-bin/gethotkey.fcg?format=json&inCharset=utf-8&outCharset=utf-8&notice=0&platform=h5&needNewCode=1"
        val json = JSONObject(http.getText(url, mapOf("Referer" to "https://y.qq.com/", "User-Agent" to ua)))
        val arr = json.optJSONObject("data")?.optJSONArray("hotkey") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.optString("k")?.trim()?.ifEmpty { null } }
    }

    private suspend fun netease(): List<String> {
        val url = "https://music.163.com/api/search/hot?type=1111"
        val json = JSONObject(http.getText(url, mapOf("Referer" to "https://music.163.com/", "User-Agent" to ua)))
        val arr = json.optJSONObject("result")?.optJSONArray("hots") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.optString("first")?.trim()?.ifEmpty { null } }
    }

    private suspend fun kugou(): List<String> {
        val url = "http://msearchcdn.kugou.com/api/v3/search/hot?version=9108&plat=0&clientver=9108"
        val json = JSONObject(http.getText(url, mapOf("User-Agent" to ua)))
        val arr = json.optJSONObject("data")?.optJSONArray("info") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.optString("keyword")?.trim()?.ifEmpty { null } }
    }

    private suspend fun kuwo(): List<String> {
        // Fabricate a self-consistent cookie value + Secret. Server only checks they match.
        val hm = (System.currentTimeMillis() / 1000).let { "$it,$it" }
        val secret = KuwoSecret.compute(hm)
        if (secret.isEmpty()) return emptyList()
        val token = "kw${System.nanoTime().toString(16)}"
        val headers = mapOf(
            "Referer" to "http://www.kuwo.cn/",
            "User-Agent" to ua,
            "Secret" to secret,
            "csrf" to token,
            "Cookie" to "${KuwoSecret.COOKIE_NAME}=$hm; kw_token=$token",
        )
        // Empty key → the suggestion endpoint returns the hot-search list (newhotKeyList).
        val json = JSONObject(http.getText("http://www.kuwo.cn/api/www/search/searchKey?key=", headers))
        val arr = json.optJSONArray("data") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { arr.optString(it).trim().ifEmpty { null } }
    }
}
