package cn.toside.music.mobile.data.model

/** MV result, mirroring RN `LX.Music.MusicVideoInfo` (per-platform getMvUrl/getMvInfo). */
data class MusicVideoInfo(
    val id: String? = null,
    val name: String? = null,
    /** Direct playable video url (may be empty when only [qualities] is populated). */
    val url: String? = null,
    /** Web page url as a fallback. */
    val pageUrl: String? = null,
    val qualities: List<MvQuality> = emptyList(),
) {
    /** Best playable url: explicit [url], else the highest-quality entry. */
    fun bestUrl(): String? = url?.takeIf { it.isNotEmpty() }
        ?: qualities.firstOrNull { !it.url.isNullOrEmpty() }?.url
}

data class MvQuality(
    val type: String,
    val url: String? = null,
    val size: String? = null,
)
