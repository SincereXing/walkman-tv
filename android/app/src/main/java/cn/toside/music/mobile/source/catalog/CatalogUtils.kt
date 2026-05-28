package cn.toside.music.mobile.source.catalog

import org.json.JSONArray
import org.json.JSONObject

/** Format a play count like lx-music: 亿 / 万 / plain. */
fun formatPlayCount(n: Long): String = when {
    n > 100_000_000 -> String.format("%.1f亿", n / 100_000_000.0)
    n > 10_000 -> String.format("%.1f万", n / 10_000.0)
    else -> n.toString()
}

private val namedEntities = mapOf(
    "&amp;" to "&", "&lt;" to "<", "&gt;" to ">", "&quot;" to "\"", "&apos;" to "'",
    "&nbsp;" to " ", "&middot;" to "·", "&ndash;" to "–", "&mdash;" to "—", "&hellip;" to "…",
)
private val numDecRegex = Regex("&#(\\d+);")
private val numHexRegex = Regex("&#[xX]([0-9A-Fa-f]+);")

/** Decode HTML entities in platform-supplied titles (iOS `decodingHTMLEntities`). */
fun String.decodeHtmlEntities(): String {
    if (!contains("&")) return this
    var s = this
    namedEntities.forEach { (k, v) -> s = s.replace(k, v) }
    s = numDecRegex.replace(s) { m -> m.groupValues[1].toIntOrNull()?.let { String(Character.toChars(it)) } ?: m.value }
    s = numHexRegex.replace(s) { m -> m.groupValues[1].toIntOrNull(16)?.let { String(Character.toChars(it)) } ?: m.value }
    return s
}

/**
 * Kuwo's `search.kuwo.cn/r.s` returns JS-object-ish text with unquoted keys even with rformat=json.
 * Pull out a top-level array by [key], quoting bare identifier keys when needed (iOS KuwoTolerantJSON).
 */
object KuwoTolerantJSON {
    private val quoteKeysRegex = Regex("""([{,\s])([A-Za-z_][A-Za-z0-9_]*)\s*:""")

    fun array(text: String, key: String): JSONArray {
        runCatching { JSONObject(text).optJSONArray(key) }.getOrNull()?.let { return it }
        val keyMarker = "$key:"
        val markerIdx = text.indexOf(keyMarker)
        if (markerIdx < 0) return JSONArray()
        val after = text.substring(markerIdx + keyMarker.length)
        val start = after.indexOf('[')
        if (start < 0) return JSONArray()
        var depth = 0
        var endIdx = -1
        for (i in start until after.length) {
            when (after[i]) {
                '[' -> depth++
                ']' -> { depth--; if (depth == 0) { endIdx = i; break } }
            }
        }
        if (endIdx < 0) return JSONArray()
        val body = quoteKeysRegex.replace(after.substring(start, endIdx + 1)) { m ->
            "${m.groupValues[1]}\"${m.groupValues[2]}\":"
        }
        return runCatching { JSONArray(body) }.getOrNull() ?: JSONArray()
    }
}
