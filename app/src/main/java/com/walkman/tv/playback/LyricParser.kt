package com.walkman.tv.playback

/** A single lyric line (iOS `LyricLine`). [time] is seconds; -1 means untimed (plain text). */
data class LyricLine(
    val id: Int,
    val time: Double,
    val text: String,
    val translation: String? = null,
)

/** Standard LRC parser, ported from iOS `LRCParser`. */
object LyricParser {
    private val timeTag = Regex("""\[(\d{1,2}):(\d{1,2})(?:[.:](\d{1,3}))?]""")

    fun parse(raw: String, translation: String? = null): List<LyricLine> {
        val main = parseLines(raw)
        val trans = translation?.let { parseLines(it) } ?: emptyList()
        val transMap = trans.associate { (it.time * 100).toInt() to it.text }

        val lines = mutableListOf<LyricLine>()
        var nextId = 0
        for (l in main) {
            lines.add(LyricLine(nextId++, l.time, l.text, transMap[(l.time * 100).toInt()]))
        }
        if (lines.isEmpty()) {
            raw.replace("\r\n", "\n").replace("\r", "\n")
                .split("\n").filter { it.isNotBlank() }
                .forEach { lines.add(LyricLine(nextId++, -1.0, it.trim())) }
        }
        return lines
    }

    private data class Parsed(val time: Double, val text: String)

    private fun parseLines(raw: String): List<Parsed> {
        val out = mutableListOf<Parsed>()
        val normalised = raw.replace("\r\n", "\n").replace("\r", "\n")
        for (rawLine in normalised.split("\n")) {
            if (rawLine.isBlank()) continue
            val matches = timeTag.findAll(rawLine).toList()
            if (matches.isEmpty()) continue
            val last = matches.last()
            val text = rawLine.substring(last.range.last + 1).trim()
            for (m in matches) {
                val mins = m.groupValues[1].toDoubleOrNull() ?: 0.0
                val secs = m.groupValues[2].toDoubleOrNull() ?: 0.0
                var msStr = m.groupValues.getOrNull(3)?.ifEmpty { "0" } ?: "0"
                if (msStr.length == 2) msStr += "0"
                val ms = msStr.toDoubleOrNull() ?: 0.0
                out.add(Parsed(mins * 60 + secs + ms / 1000.0, text))
            }
        }
        return out.sortedBy { it.time }
    }

    /** Index of the active line at [time] (binary search), or -1 if none yet. */
    fun activeIndex(time: Double, lines: List<LyricLine>): Int {
        if (lines.isEmpty()) return -1
        var lo = 0; var hi = lines.size - 1; var ans = -1
        while (lo <= hi) {
            val mid = (lo + hi) / 2
            if (lines[mid].time <= time) { ans = mid; lo = mid + 1 } else { hi = mid - 1 }
        }
        return ans
    }
}
