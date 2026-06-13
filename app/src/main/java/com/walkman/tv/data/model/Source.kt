package com.walkman.tv.data.model

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

/**
 * Audio qualities, mirroring iOS `Quality`. Raw value matches lx-music v4 quality strings —
 * **must** be exact for the `type` field in musicUrl requests.
 *
 * Spec lives at docs/audio-quality-spec-android-tv.md §1 (8-tier system + ordering).
 *
 * [isExtendedTier] flags the four tiers that catalog metadata almost never reports
 * (HIRES / ATMOS / ATMOS_PLUS / MASTER) — those bypass the trackQualities check in
 * [pickPlayQuality] / [qualityCascade] so they can still be tried when the script declares
 * support, even if the search-result blob didn't list them.
 *
 * [badgeLabel] maps to the visible badge text per spec §1.
 */
@Serializable
enum class Quality(val key: String, val displayName: String) {
    @SerialName("128k")       K128       ("128k",       "标准 128k"),
    @SerialName("320k")       K320       ("320k",       "高品 320k"),
    @SerialName("flac")       FLAC       ("flac",       "无损 FLAC"),
    @SerialName("flac24bit")  FLAC24     ("flac24bit",  "Hi-Res 24bit"),
    @SerialName("hires")      HIRES      ("hires",      "Hi-Res 高解析"),
    @SerialName("atmos")      ATMOS      ("atmos",      "臻品全景声"),
    @SerialName("atmos_plus") ATMOS_PLUS ("atmos_plus", "臻品全景声 2.0"),
    @SerialName("master")     MASTER     ("master",     "臻品母带");

    /** Extended tiers may be played even when catalog metadata doesn't list them — see spec §3. */
    val isExtendedTier: Boolean
        get() = this == HIRES || this == ATMOS || this == ATMOS_PLUS || this == MASTER

    /** Short text shown on the player's Hi-Res pill. Spec §1 map. */
    val badgeLabel: String
        get() = when (this) {
            MASTER -> "Master"
            ATMOS, ATMOS_PLUS -> "Atmos"
            HIRES, FLAC24 -> "Hi-Res"
            FLAC -> "SQ"
            K320 -> "HQ"
            K128 -> "STD"
        }

    companion object {
        fun fromKey(key: String?): Quality? = entries.firstOrNull { it.key == key }

        /**
         * Highest → lowest. Single source of truth for "pick highest" and cascade order.
         * Spec §1: master > atmos_plus > atmos > hires > flac24bit > flac > 320k > 128k.
         */
        val orderedHighToLow: List<Quality> = listOf(
            MASTER, ATMOS_PLUS, ATMOS, HIRES, FLAC24, FLAC, K320, K128,
        )

        /** Numeric rank — 0 = highest (master). Lower is better. */
        fun rank(q: Quality): Int = orderedHighToLow.indexOf(q)
    }
}
