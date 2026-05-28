# 客厅音乐（walkman-tv）

原生 Android TV 音乐播放器，使用 **Kotlin + Compose for TV** 重写。逻辑参考 `walkman` 工程（iOS Swift/SwiftUI 版），保留 lx-music v4 自定义源的 **JS 解析逻辑（QuickJS 引擎）**。

## 模块

```
android/app/src/main/java/cn/toside/music/mobile/
├── App.kt / MainActivity.kt          应用入口（QuickJSLoader + bootstrap）
├── di/AppContainer.kt                手写 DI 容器
├── data/model                        Track / SourceID / Quality / UserScript / Playlist / ...
├── data/store                        ScriptStore / LibraryStore / SettingsStore（filesDir JSON）
├── crypto/AES.java + RSA.java        从原 RN 工程保留的密码学辅助（被 JS 引擎调用）
├── source/js                         JsScriptRuntime / ScriptHttpClient / CryptoBridge
├── source/catalog                    各平台直连 API：搜索 / 排行榜 / 歌单 / MV
├── source/builtin                    kw·wy 直连兜底 + 直连歌词
├── source                            SourceManager（音质级联+换源）/ OtherSourceFinder
├── playback                          PlaybackController (Media3) / PlaybackService / LyricParser
├── ui                                RootScreen + TopNav + 6 个分区屏幕 + 全屏播放器
└── assets/script/user-api-preload.js QuickJS 预加载脚本（保留自原工程）
```

## 功能

- **推荐（首页）** —— 正在播放面板 + 推荐卡片网格 + 已播/收藏统计
- **搜索** —— 多源搜歌（kw/kg/tx/wy/mg）+ 单源筛选
- **排行榜** —— kw/wy/kg/tx 平台榜单
- **歌单** —— 歌单广场（标签/排序）+ 歌单详情
- **我的列表** —— 收藏、**播放历史**（替代原 RN 的「试听列表」自动记录）、自建列表
- **播放器** —— 全屏封面 + 逐行滚动歌词 + 传输控件 + 收藏 + **MV** 视频播放
- **设置 + 自定义源管理** —— 音质偏好 / 内置直连兜底 / 歌词翻译 / 通过 URL 导入 lx-music v4 脚本

## 自定义源（保留）

`source/js/JsScriptRuntime.kt` 用 `wang.harlon.quickjs:wrapper-android` 运行 lx-music v4 用户脚本，复刻原 RN 工程的预加载契约（`lx_setup` / `__lx_native__` / `__lx_native_call__*`）。HTTP 请求由 `ScriptHttpClient`（OkHttp）原生执行，AES/RSA 走 `CryptoBridge` 委托给保留下来的 Java 辅助类。

## 构建

```
cd android && ./gradlew assembleDebug
```

需要 `local.properties` 指向本机 Android SDK；`compileSdk 35`、`minSdk 21`、`targetSdk 34`。
