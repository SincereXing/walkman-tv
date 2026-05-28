package com.walkman.tv.data.model

import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * A list of tracks. Built-in lists use fixed ids ([LOVE], [HISTORY]); user lists get a UUID.
 * The former RN "试听列表" default list is replaced by the auto-recorded [HISTORY] list.
 */
@Serializable
data class Playlist(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val tracks: List<Track> = emptyList(),
    val updatedAt: Long = System.currentTimeMillis(),
) {
    companion object {
        const val LOVE = "love"
        const val HISTORY = "history"
        const val LOVE_NAME = "我的收藏"
        const val HISTORY_NAME = "播放历史"
    }
}
