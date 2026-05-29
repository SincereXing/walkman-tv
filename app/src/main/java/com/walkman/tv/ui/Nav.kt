package com.walkman.tv.ui

/** Top-nav sections, mirroring the RN TV app's NAV_MENUS. */
enum class NavSection(val label: String) {
    Recommend("推荐"),
    Leaderboard("排行榜"),
    Songlist("歌单"),
    Library("我的列表"),
    Search("搜索"),
    Settings("设置"),
}
