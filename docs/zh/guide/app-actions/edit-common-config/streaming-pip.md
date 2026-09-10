# 流媒体画中画

自动检测视频播放 — 包括 HLS、DASH、YouTube 嵌入以及普通 MP4 — 并自动进入画中游模式,这样用户在跳转时音视频依然会播放。
当自动进入关闭或漏掉某个流时,会注入一个手动切换面板,提供逐视频的画中游开关。

这是一个[模块](/zh/guide/app-actions/edit-common-config/extension-modules),而非单个
`WebViewConfig` 开关:它以**内置模块**(`extension-streaming-pip`)的形式发行,也可在
**模块市场**中找到 `wta-stream-detect-pip`,并在创建新模块时作为**配置模板**
(`template-stream-detect-pip`)提供。

::: tip 已就地记录
[特殊设置](/zh/guide/app-actions/edit-common-config/special-settings) 页的“媒体与内容”
section 已提及此功能;本页是其展开。
:::

## 工作原理

1. **检测** — 模块扫描 `<video>` 元素、`<iframe>`/`embed` 源以及
   `MediaSource`/`MSE` 流。来源类型按 URL/后缀分类:
   - `.m3u8` → HLS
   - `.mpd` → DASH
   - `youtube.com` / `youtu.be` → YouTube(iframe/API 支持)
   - 其它 `.mp4`/`.webm` → 普通视频
2. **自动进入**(默认开启) — 当一个符合条件的来源开始播放时,模块调用 Android 的画中游
   模式。生成应用的目标是 API 28(低 targetSdk),在清单中声明 `PICTURE_IN_PICTURE`
   功能;无运行时权限提示。
3. **手动切换面板** — 会追加一个小浮标到每个被检测的视频。点击可在该播放器上
   切换画中游开/关,不受自动进入设置影响。两个全局按钮控制全部播放器:
   **全部进入画中游** 和 **全部退出画中游**。

内置版与市场版行为一致;模板版额外以可编辑字段暴露下文的两个配置项,当你从模板生成模块时可修改。

## 如何获取

- **内置:** 打开 **更多 → 浏览器与界面 → 扩展模块 → 视频**,启用
  **Streaming PiP Detector**。无需网络访问。
- **市场:** 打开应用内 **模块市场**,找到
  `wta-stream-detect-pip` 并安装。

## 配置模板选项

在从 `template-stream-detect-pip` 模板创建模块时(或编辑内置/市场版同名字段时)可用:

- **自动进入**(`autoEntry`) — 为 `true` 时,符合条件的视频播放自动进入画中游。默认:`true`。
- **流类型**(`streamingTypes`) — 过滤哪些来源会触发检测:
  `all` / `hls` / `dash` / `mp4`。默认:`all`。

## 画中游面板

注入的面板按单个视频播放器作用域,显示:

- 检测到的流类型(HLS / DASH / YouTube / MP4)
- 切换该播放器画中游开/关的开关
- 页面顶部的全局 **全部进入画中游** / **全部退出画中游** 按钮

当两个及以上播放器符合条件时,一次仅有一个处于画中游;之前的播放器会被暂停。
点击浮标可将所选播放器切回画中游。

## 注意事项

- 画中游是系统级模式;生成应用以 API 28 为目标并在清单中声明 `PICTURE_IN_PICTURE`
  功能。不会弹出额外的权限提示。
- YouTube 检测依赖 iframe 嵌入界面。若站点把播放器包在跨域 iframe 中,
  模块无法触达时,自动进入会回退到手动浮标。
- 对于非 YouTube、非 HLS/DASH 的来源(普通 `.mp4`),检测基于 `<video>` 元素,可靠性高。
- 若某页面无法自动进入画中游,请关闭 **自动进入** 并使用手动浮标。
