package cn.toside.music.mobile.data.model

/** A leaderboard entry (iOS `BoardInfo`). [bangid] is the platform-side id. */
data class BoardInfo(
    val id: String,
    val source: SourceID,
    val bangid: String,
    val name: String,
    val picURL: String? = null,
)
