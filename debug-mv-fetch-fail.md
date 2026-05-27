# [OPEN] mv-fetch-fail

## 症状
- 用户测试 `晴天`、`江南`、`稻香`，点击全屏播放器 `MV` 后都提示“获取MV失败”。

## 期望
- 对存在 MV 的歌曲，至少应进入 MV 面板或拿到可播放/可降级的 MV 信息。

## 假设
- A. 统一入口 `getMusicMvUrl()` 在进入具体音源前就抛错了。
- B. `tx` 音源的 MV 地址接口请求参数或返回结构不对，导致解析异常。
- C. `kg` 音源的 `mvHash` 获取链路没有命中，或 `mv.php` 返回结构与当前解析不一致。
- D. 播放页按钮点击时拿到的 `currentMusic` 元数据缺少 `source/mvId/mvHash/hash`，导致查询链路前置条件不足。
- E. 有些 MV URL 是明文/跨域/空字段，导致统一层把“有 MV”误判成“失败”。

## 调试计划
- 先只加埋点，不改业务逻辑。
- 记录统一入口、音源适配层、播放页点击入口三处的运行时参数和返回值。
- 用实际复现日志确认失败点，再做最小修复。

## 已获证据
- 用户屏幕报错为：`Rendered more hooks than during the previous render`
- 该错误与音源接口无关，先落在 `TvMvPanel.tsx`
- 根因是组件在 `if (!visible || !mvInfo) return null` 之后又声明了 `useCallback(setSurfaceNode)`，导致不同渲染轮次 Hook 数量不一致

## 当前修复
- 已把 `setSurfaceNode` 的 `useCallback` 挪到条件返回之前，保证 Hook 顺序稳定
- 仍保留埋点，等待用户再次复现确认是否已进入真正的 MV 获取链路
