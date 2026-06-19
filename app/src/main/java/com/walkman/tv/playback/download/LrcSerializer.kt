package com.walkman.tv.playback.download

import com.walkman.tv.playback.LyricLine

/** Serialize a list of [LyricLine] back into LRC text. Counterpart to LyricParser.parse. */
internal object LrcSerializer {
    fun serialize(lines: List<LyricLine>): String? {
        if (lines.isEmpty()) return null
        val sb = StringBuilder()
        for (line in lines) {
            if (line.time < 0) {
                sb.appendLine(line.text)
                continue
            }
            val mm = (line.time / 60).toInt()
            val ss = (line.time % 60).toInt()
            val ms = ((line.time - mm * 60 - ss) * 100).toInt().coerceIn(0, 99)
            sb.append('[')
                .append("%02d".format(mm))
                .append(':')
                .append("%02d".format(ss))
                .append('.')
                .append("%02d".format(ms))
                .append(']')
                .appendLine(line.text)
        }
        return sb.toString().trimEnd('\n').takeIf { it.isNotEmpty() }
    }
}
