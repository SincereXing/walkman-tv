package com.walkman.tv.source.catalog

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

const val MOBILE_UA =
    "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1"

class CatalogException(message: String) : Exception(message)

/** Thin HTTP helper for the direct platform catalog/search/leaderboard/songlist APIs. */
class CatalogHttp(private val client: OkHttpClient) {

    suspend fun getText(url: String, headers: Map<String, String> = emptyMap()): String =
        exec(Request.Builder().url(url).applyHeaders(headers).get().build())

    suspend fun postForm(url: String, form: String, headers: Map<String, String> = emptyMap()): String {
        val body = form.toRequestBody("application/x-www-form-urlencoded".toMediaTypeOrNull())
        return exec(Request.Builder().url(url).applyHeaders(headers).post(body).build())
    }

    suspend fun postJson(url: String, json: String, headers: Map<String, String> = emptyMap()): String {
        val body = json.toRequestBody("application/json".toMediaTypeOrNull())
        return exec(Request.Builder().url(url).applyHeaders(headers).post(body).build())
    }

    private suspend fun exec(request: Request): String = withContext(Dispatchers.IO) {
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) throw CatalogException("HTTP ${resp.code}")
            resp.body?.string() ?: throw CatalogException("空响应")
        }
    }

    private fun Request.Builder.applyHeaders(headers: Map<String, String>): Request.Builder {
        if (headers.none { it.key.equals("User-Agent", true) }) header("User-Agent", MOBILE_UA)
        headers.forEach { (k, v) -> header(k, v) }
        return this
    }
}

/** URL-encode a query parameter value. */
fun urlEncode(s: String): String =
    java.net.URLEncoder.encode(s, "UTF-8").replace("+", "%20")
