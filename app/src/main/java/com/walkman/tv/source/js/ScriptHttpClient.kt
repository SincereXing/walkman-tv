package com.walkman.tv.source.js

import okhttp3.Call
import okhttp3.Callback
import okhttp3.Headers
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap

/**
 * HTTP requests issued by JS user scripts via `lx.request`, ported from iOS `ScriptHTTPClient`.
 * Mirrors lx-music-mobile request.js: same default UA, JSON-parse-on-response, Content-Type rules.
 */
class ScriptHttpClient(private val client: OkHttpClient) {

    /** [body] is a JSON value (JSONObject/JSONArray/String) or a List<Int> for binary. */
    data class ScriptResponse(
        val statusCode: Int,
        val statusMessage: String,
        val headers: Map<String, String>,
        val body: Any,
    )

    private val calls = ConcurrentHashMap<String, Call>()

    fun send(
        requestKey: String,
        url: String,
        options: JSONObject,
        completion: (error: String?, response: ScriptResponse?) -> Unit,
    ) {
        val req = try {
            buildRequest(url, options)
        } catch (e: Exception) {
            completion(e.message ?: "Invalid request", null); return
        }
        val binary = options.optBoolean("binary", false)
        val call = client.newCall(req)
        calls[requestKey] = call
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                calls.remove(requestKey)
                if (call.isCanceled()) return
                completion(e.message ?: "request failed", null)
            }

            override fun onResponse(call: Call, response: Response) {
                calls.remove(requestKey)
                response.use { resp ->
                    val headers = mutableMapOf<String, String>()
                    resp.headers.forEach { (k, v) -> headers[k.lowercase()] = v }
                    val bytes = resp.body?.bytes()
                    val bodyValue = decodeBody(bytes, binary)
                    completion(
                        null,
                        ScriptResponse(
                            statusCode = resp.code,
                            statusMessage = resp.message.ifEmpty { statusText(resp.code) },
                            headers = headers,
                            body = bodyValue,
                        ),
                    )
                }
            }
        })
    }

    fun cancel(requestKey: String) {
        calls.remove(requestKey)?.cancel()
    }

    private fun buildRequest(url: String, options: JSONObject): Request {
        val method = (options.optString("method", "GET")).uppercase()
        val builder = Request.Builder().url(url)

        // Caller headers
        val callerHeaders = mutableMapOf<String, String>()
        options.optJSONObject("headers")?.let { h ->
            h.keys().forEach { k ->
                val v = h.get(k).toString()
                callerHeaders[k] = v
            }
        }

        val contentType: String? = headerCI(callerHeaders, "Content-Type")
        var body: RequestBody? = null

        if (method == "POST" && options.has("form") && options.opt("form") is JSONObject) {
            body = encodeForm(options.getJSONObject("form"))
                .toRequestBody((contentType ?: "application/x-www-form-urlencoded").toMediaTypeOrNull())
        } else if (method == "POST" && options.has("formData") && options.opt("formData") is JSONObject) {
            body = options.getJSONObject("formData").toString()
                .toRequestBody((contentType ?: "multipart/form-data").toMediaTypeOrNull())
        } else if (options.has("body") && !options.isNull("body")) {
            val raw = options.get("body")
            val effectiveCt = contentType ?: if (method == "POST") "application/json" else null
            val ctLower = (effectiveCt ?: "").lowercase()
            val text: String = when {
                ctLower.contains("application/json") && raw is JSONObject -> raw.toString()
                raw is JSONObject -> raw.toString()
                raw is JSONArray -> bytesFromIntArray(raw).let { String(it, Charsets.ISO_8859_1) }
                else -> raw.toString()
            }
            body = text.toRequestBody(effectiveCt?.toMediaTypeOrNull())
        }

        // Method + body
        if (method == "GET" || method == "HEAD") {
            builder.method(method, null)
        } else {
            builder.method(method, body ?: ByteArray(0).toRequestBody(null))
        }

        // Headers (caller first)
        val hb = Headers.Builder()
        callerHeaders.forEach { (k, v) -> hb.add(k, v) }
        if (headerCI(callerHeaders, "User-Agent") == null) hb.add("User-Agent", DEFAULT_UA)
        if (headerCI(callerHeaders, "Accept") == null) hb.add("Accept", "application/json")
        builder.headers(hb.build())
        return builder.build()
    }

    private fun encodeForm(form: JSONObject): String =
        form.keys().asSequence().map { k ->
            val v = form.get(k).toString()
            "${enc(k)}=${enc(v)}"
        }.joinToString("&")

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8").replace("+", "%20")

    private fun bytesFromIntArray(arr: JSONArray): ByteArray =
        ByteArray(arr.length()) { (arr.optInt(it) and 0xFF).toByte() }

    private fun headerCI(headers: Map<String, String>, name: String): String? {
        val target = name.lowercase()
        return headers.entries.firstOrNull { it.key.lowercase() == target }?.value
    }

    companion object {
        const val DEFAULT_UA =
            "Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/69.0.3497.100 Safari/537.36"

        /** request.js contract: JSON.parse → object/array; else raw text; binary → int array. */
        fun decodeBody(data: ByteArray?, binary: Boolean): Any {
            if (data == null || data.isEmpty()) return ""
            if (binary) return JSONArray().also { a -> for (b in data) a.put(b.toInt() and 0xFF) }
            val text = String(data, Charsets.UTF_8)
            return try {
                JSONTokener(text).nextValue() ?: text
            } catch (e: Exception) {
                text
            }
        }

        private fun statusText(code: Int): String = when (code) {
            200 -> "OK"; 201 -> "Created"; 204 -> "No Content"
            301 -> "Moved Permanently"; 302 -> "Found"; 304 -> "Not Modified"
            400 -> "Bad Request"; 401 -> "Unauthorized"; 403 -> "Forbidden"; 404 -> "Not Found"
            500 -> "Internal Server Error"; 502 -> "Bad Gateway"; 503 -> "Service Unavailable"
            else -> ""
        }
    }
}
