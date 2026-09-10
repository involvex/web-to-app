# Special Settings

Compatibility polyfills, bridges, and other specialized toggles. This card collects the specialized `WebViewConfig` options.

**Where:** the **Special settings** card in the [Edit Common Config](/guide/app-actions/edit-common-config/) editor.

## Polyfills & bridges

- **Clipboard polyfill** — `enableClipboardPolyfill`.
- **Notification polyfill** — Web Notification support (`enableNotificationPolyfill`).
- **Orientation polyfill** — `enableOrientationPolyfill`.
- **Compat polyfills** — a bundle of compatibility shims (`enableCompatPolyfills`).
- **Native bridge** — expose a native bridge with capability gates (`enableNativeBridge`, `nativeBridgeCapabilities`).
- **Screen capture** — native bridge screen capture (`nativeBridgeCapabilities.screenCapture`). Exposes `window.WtaScreenCapture`.
- **Print bridge** — intercept `window.print()` and PDF output to the Android print framework (`enablePrintBridge`).
- **Media Session bridge** — bridge web media to the system media notification and lock-screen controls, including Bluetooth headsets and Android Auto (`enableMediaSession`).
- **Share bridge** — `enableShareBridge`.
- **Zoom polyfill** — `enableZoomPolyfill`.

## Media & content

- **Media autoplay** — with scope (`mediaAutoplayEnabled`, `mediaAutoplayScope`: video-only, …).
- **Image repair** — fix broken images (`enableImageRepair`).
- **Scroll memory** — remember scroll position (`enableScrollMemory`).
- **Back-state preservation** — `enableBackStatePreservation`.
- **Blob download interception** — with scope and size threshold (`enableBlobDownloadInterception`, `blobInterceptThresholdMb`).

## JavaScript & windows

- **JS can open windows** — with policy (`javaScriptCanOpenWindows`, `jsOpenWindowsPolicy`).
- **Prime user activation** — synthesize a user gesture, with mode and timing (`primeUserActivation`, `primeUserActivationMode`, `primeUserActivationTiming`).
- **Base64 deep links** — decode base64 deep links, gesture-only or always (`decodeBase64DeepLinks`, `decodeBase64Mode`).

## Security & misc

- **Cross-origin isolation** — `enableCrossOriginIsolation`.
- **Anti-capture** — block screen capture (`antiCapture`).
- **File access from file URLs** — `allowFileAccessFromFileURLs`, `allowUniversalAccessFromFileURLs`.
- **Error page** — custom error page config (`errorPageConfig`).
- **Performance optimization** — `performanceOptimization`.
- **PWA offline** — offline cache strategy (`pwaOfflineEnabled`, `pwaOfflineStrategy`).
- **Floating back button** — `showFloatingBackButton`.
- **Keyboard adjust mode** — `keyboardAdjustMode` (resize, …).
- **Hide URL preview** — `hideUrlPreview`.

## Notes

- These are power-user toggles; most apps leave them at defaults.

## Screen Capture

Enable **Screen capture** on the Special settings card (it must also be allowed
by the **Native bridge** capability set). When on, the page gains
`window.WtaScreenCapture` — a promise-based API backed by the native bridge.

::: details One-shot WebView content capture

Returns a base64 JPEG of the current WebView viewport (no permissions).

```js
const imageData = await WtaScreenCapture.capture({ quality: 80 });
const img = document.createElement('img');
img.src = 'data:image/jpeg;base64,' + imageData;
document.body.appendChild(img);
```

:::

::: details Device screen capture (MediaProjection)

Captures the device screen (status bar, other apps) via a one-time consent
dialog. Resolves a real `MediaStream` so it works in `<video>` and WebRTC.

```js
window.onDeviceFrame = (base64) => {
  // base64 = each captured frame as JPEG data URI body
};

const stream = await WtaScreenCapture.getDisplayMedia({
  source: 'auto',          // 'device' | 'webview' | 'auto' (falls back silently)
  quality: 70,             // 0-100
  interval: 500,           // ms between frames
  frameTimeout: 15000,     // reject if no first frame in 15s
  onFrame: window.onDeviceFrame
});

const video = document.createElement('video');
video.srcObject = stream;
document.body.appendChild(video);
video.play();

// later
WtaScreenCapture.stopStream(stream);
```

`requestDeviceAccess()` resolves `true`/`false` when the consent flow completes
(timeout 120s by default):

```js
const granted = await WtaScreenCapture.requestDeviceAccess();
if (granted) { /* safe to call getDisplayMedia */ }
```

Notes:

- Device capture is unavailable in **floating-window** apps and Gecko-engine
  previews — `getDisplayMedia` with `source: 'auto'` transparently falls back to
  WebView-content capture there.
- If the system revokes the grant mid-capture, the stream's tracks end and
  `onFrame` receives `'revoked'`.
- The native loop is shared: multiple streams multiplex onto one capture, and
  `stopStream`/`stopAll` tear down everything.

