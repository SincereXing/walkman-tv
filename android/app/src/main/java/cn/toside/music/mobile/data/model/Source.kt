package cn.toside.music.mobile.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Music platforms, mirroring iOS `SourceID`. Raw value is the lx-music source key. */
@Serializable
enum class SourceID(val key: String, val displayName: String) {
    @SerialName("kw") KW("kw", "酷我"),
    @SerialName("kg") KG("kg", "酷狗"),
    @SerialName("tx") TX("tx", "QQ音乐"),
    @SerialName("wy") WY("wy", "网易云"),
    @SerialName("mg") MG("mg", "咪咕"),
    @SerialName("local") LOCAL("local", "本地");

    companion object {
        fun fromKey(key: String?): SourceID? = entries.firstOrNull { it.key == key }
        /** Sources usable as online music platforms (exclude local). */
        val onlineSources: List<SourceID> = listOf(KW, KG, TX, WY, MG)
    }
}

/** Audio qualities, mirroring iOS `Quality`. Raw value matches lx-music quality strings. */
@Serializable
enum class Quality(val key: String, val displayName: String) {
    @SerialName("128k") K128("128k", "标准 128k"),
    @SerialName("320k") K320("320k", "高品 320k"),
    @SerialName("flac") FLAC("flac", "无损 FLAC"),
    @SerialName("flac24bit") FLAC24("flac24bit", "Hi-Res 24bit");

    companion object {
        fun fromKey(key: String?): Quality? = entries.firstOrNull { it.key == key }
        /** Highest → lowest, used when building the v4 musicInfo `types` array. */
        val orderedHighToLow: List<Quality> = listOf(FLAC24, FLAC, K320, K128)
    }
}
