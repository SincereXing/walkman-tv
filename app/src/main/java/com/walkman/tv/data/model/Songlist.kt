package com.walkman.tv.data.model

/** A playlist/歌单 summary (iOS `SonglistInfo`). [id] is the platform-side playlist id. */
data class SonglistInfo(
    val id: String,
    val source: SourceID,
    val name: String,
    val author: String = "",
    val picURL: String? = null,
    val trackCount: Int? = null,
    val playCount: String? = null,
)

data class SonglistDetail(
    val info: SonglistInfo,
    val tracks: List<Track>,
)

/** A platform sort option (id = API value, name = label). */
data class SonglistOrder(val id: String, val name: String)

/** A single filter tag (歌单分类). id == "" ⇒ 全部. */
data class SonglistTag(val id: String, val name: String) {
    companion object {
        val ALL = SonglistTag("", "全部")
    }
}

/** A named group of tags (热门/语种/风格/场景…). */
data class SonglistTagGroup(val name: String, val tags: List<SonglistTag>)
