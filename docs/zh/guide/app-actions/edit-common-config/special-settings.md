# 特殊设置

兼容性 polyfill、桥接和其他专门开关。这张卡片汇集了专门的 `WebViewConfig` 选项。

**位置:**[编辑通用配置](/zh/guide/app-actions/edit-common-config/)编辑器中的 **特殊设置** 卡片。

## Polyfill 与桥接

- **剪贴板 polyfill** —— `enableClipboardPolyfill`。
- **通知 polyfill** —— Web Notification 支持(`enableNotificationPolyfill`)。
- **方向 polyfill** —— `enableOrientationPolyfill`。
- **兼容 polyfill** —— 一组兼容 shim(`enableCompatPolyfills`)。
- **原生桥** —— 暴露带能力门控的原生桥(`enableNativeBridge`、`nativeBridgeCapabilities`)。
- **屏幕截图** —— 原生桥屏幕截图功能(`nativeBridgeCapabilities.screenCapture`)。在页面中暴露 `window.WtaScreenCapture`。
- **打印桥** —— 拦截 `window.print()` 和 PDF 输出到 Android 打印框架(`enablePrintBridge`)。
- **媒体会话桥** —— 把网页媒体接入系统媒体通知和锁屏控制,支持蓝牙耳机和 Android Auto(`enableMediaSession`)。
- **分享桥** —— `enableShareBridge`。
- **缩放 polyfill** —— `enableZoomPolyfill`。

## 媒体与内容

- **媒体自动播放** —— 带范围(`mediaAutoplayEnabled`、`mediaAutoplayScope`:仅视频……)。
- **图片修复** —— 修复损坏的图片(`enableImageRepair`)。
- **滚动记忆** —— 记住滚动位置(`enableScrollMemory`)。
- **返回状态保留** —— `enableBackStatePreservation`。
- **Blob 下载拦截** —— 带范围和大小阈值(`enableBlobDownloadInterception`、`blobInterceptThresholdMb`)。
- **流媒体画中画** —— 在 **扩展模块** 下启用 **视频增强**(画中画、倍速、后台播放)或 **流媒体画中画**(自动检测视频播放,自动进入 HLS/DASH/YouTube 的画中画)。流媒体画中画会注册面板入口,带逐视频的 PiP 开关;适合内置增强模块手动开关容易被忽略的视频密集型网站。

## JavaScript 与窗口

- **JS 可打开窗口** —— 带策略(`javaScriptCanOpenWindows`、`jsOpenWindowsPolicy`)。
- **预置用户激活** —— 合成用户手势,带模式和时机(`primeUserActivation`、`primeUserActivationMode`、`primeUserActivationTiming`)。
- **Base64 深度链接** —— 解码 base64 深度链接,仅手势或总是(`decodeBase64DeepLinks`、`decodeBase64Mode`)。

## 安全与其他

- **跨源隔离** —— `enableCrossOriginIsolation`。
- **防截屏** —— 阻止屏幕截取(`antiCapture`)。
- **文件 URL 的文件访问** —— `allowFileAccessFromFileURLs`、`allowUniversalAccessFromFileURLs`。
- **错误页** —— 自定义错误页配置(`errorPageConfig`)。
- **性能优化** —— `performanceOptimization`。
- **PWA 离线** —— 离线缓存策略(`pwaOfflineEnabled`、`pwaOfflineStrategy`)。
- **浮动返回按钮** —— `showFloatingBackButton`。
- **键盘调整模式** —— `keyboardAdjustMode`(resize……)。
- **隐藏 URL 预览** —— `hideUrlPreview`。

## 说明

- 这些是高级用户开关;大多数应用保持默认即可。

## 屏幕截图

在**特殊设置**卡片中启用**屏幕截图**（同时需要**原生桥**能力集允许）。启用后，页面将获得 `window.WtaScreenCapture` —— 一个基于 Promise 的 API，由原生桥提供支持。

::: details 一次性 WebView 内容截图

返回当前 WebView 视口的 base64 JPEG 图像（无需权限）。

```js
const imageData = await WtaScreenCapture.capture({ quality: 80 });
const img = document.createElement('img');
img.src = 'data:image/jpeg;base64,' + imageData;
document.body.appendChild(img);
```

:::

::: details 设备屏幕截图（MediaProjection）

通过一次性 consent 对话框捕获设备屏幕（状态栏、其他应用等）。返回一个真正的 `MediaStream`，适用于 `<video>` 和 WebRTC。

```js
window.onDeviceFrame = (base64) => {
  // base64 = 每帧作为 JPEG 数据 URI 的主体
};

const stream = await WtaScreenCapture.getDisplayMedia({
  source: 'auto',          // 'device' | 'webview' | 'auto'（不支持时静默回退）
  quality: 70,             // 0-100
  interval: 500,           // 帧间隔（毫秒）
  frameTimeout: 15000,     // 15 秒内无首帧则拒绝
  onFrame: window.onDeviceFrame
});

const video = document.createElement('video');
video.srcObject = stream;
document.body.appendChild(video);
video.play();

// 稍后
WtaScreenCapture.stopStream(stream);
```

`requestDeviceAccess()` 在 consent 流程完成时resolve `true`/`false`（默认超时 120 秒）：

```js
const granted = await WtaScreenCapture.requestDeviceAccess();
if (granted) { /* 可以安全调用 getDisplayMedia */ }
```

注意事项：

- 设备截图在**悬浮窗**应用和 Gecko 引擎预览中不可用 —— 使用 `source: 'auto'`
  时会自动回退到 WebView 内容截图。
- 若系统在捕获过程中收回授权，流的 track 将结束，且 `onFrame` 会收到 `'revoked'`。
- 原生循环是共享的：多个流复用单次捕获，`stopStream`/`stopAll` 会一并清理。
