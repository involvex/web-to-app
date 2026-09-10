# Feature Suggestions for web-to-app

> **Status key:** ✅ = implemented · ⚠️ = partially implemented · ❌ = not implemented · 🆕 = new since last update

A comprehensive analysis of the existing codebase and feature opportunities
organized by domain. Each suggestion references the relevant existing
architecture paths so implementers can trace the model → export → shell →
runtime chain required by the project conventions.

> **Codebase evolution note:** Since this file was last updated, the project
> has grown from a web-to-APK builder into a full mobile app development
> platform. The agent system now supports 55 tools across 6 LLM providers,
> Chrome extensions, GeckoView engine, AAB export, app cloning, embedded
> servers (Node/PHP/Python/Go/WordPress), and much more. Sections 15–21
> document these new areas. Run `python3 scripts/check_config_field_drift.py`
> and `./gradlew :app:checkConfigFieldDrift` to verify config field alignment
> after any export-side change.

---

## 1. WebView Features

### 1.1 WebRTC Screen Capture (`getDisplayMedia`)
**Status:** ✅ Implemented. `NativeBridge` exposes `captureScreen(quality?)`,
`startScreenCapture(quality?, callback?, interval?)`, `stopScreenCapture()`,
`setScreenCaptureQuality(quality)`, and device-capture variants
`isDeviceCaptureGranted()`, `requestDeviceCapture(callback?)`,
`startDeviceCapture(quality?, callback?, interval?)`, `stopDeviceCapture()`.
A Kotlin `MediaProjection` wrapper lives in `ScreenCaptureHelper.kt` /
`DeviceScreenCapture.kt`; the consent host is `ScreenCaptureConsentHost.kt`.
- **Export chain:** `NativeBridgeCapabilities.screenCapture` →
  `WebViewBehaviorBlock.enableNativeBridge` →
  `ApkConfigJsonFactory.toShellPayload()` → `WebViewShellConfig` →
  runtime gate in `NativeBridge.kt`.
- **Editor UI:** Toggle in `CreateAppWebViewCards.kt` via `WtaToggleRow`
  bound to `webViewBehaviorBlock.enableNativeBridge`.
- **Tests:** `NativeBridgeScreenCaptureTest.kt`, `ScreenCaptureHelperTest.kt`.

### 1.2 Additional Browser Kernel Flavors
**Paths:** `core/kernel/KernelFlavor.kt`, `core/appearance/BrowserDisguiseEngine.kt`
**Status:** ⚠️ Partially implemented. `KernelFlavor` now includes
`SYSTEM_DEFAULT`, `BLINK_CHROME`, `BLINK_EDGE`, `BLINK_SAMSUNG`,
`GECKO_FIREFOX`, `WEBKIT_SAFARI`. Samsung Internet and Firefox/Gecko are
covered. `OPERA`, `KIWI`, `BRAVE` are still missing.
- **Export chain:** `KernelFlavor` → `WebViewBlock.kernel` →
  `ApkConfigJsonFactory.toShellPayload()` → `WebViewShellConfig.kernel`
  → runtime UA selection in `BrowserKernel.kt`.
- **Remaining work:** Add `BLINK_OPERA`, `BLINK_KIWI`, `BLINK_BRAVE` entries
  to `KernelFlavor.kt` with UA strings, and add corresponding
  `KernelFlavorMetadata` entries.

### 1.3 HTTP/3 (QUIC) Support
**Paths:** `core/network/NetworkSecurityConfigBuilder.kt`, `core/engine/GeckoViewEngine.kt`
**Status:** ⚠️ Partially implemented. HTTP/3 is enabled in the GeckoView
engine via `prefs["network.dns.http3_echconfig.enabled"] = true`
(`GeckoViewEngine.kt:271`), but there is no general `webViewConfig.http3Enabled`
flag for the System WebView engine.
- **Remaining work:** Add a `webViewBehavior.http3Enabled` boolean to
  `WebViewBehaviorBlock` in `ApkConfig.kt`, export through
  `ApkConfigJsonFactory`, gate in `ShellWebViewConfig.kt`, and apply via
  `NetworkSecurityConfigBuilder.kt`.

### 1.4 Per-Domain Cache Control Policies
**Paths:** `data/model/WebApp.kt`, `core/webview/WebViewManager.kt`, `core/shell/ShellModeManager.kt`
**Status:** ❌ Not implemented. `cacheEnabled` is still a single boolean
in `WebViewBlock`. No per-domain cache rules exist.
- **Model → Shell → Runtime:** Add `cacheRules: List<CacheRule>` to
  `WebViewBlock` → `ApkConfigJsonFactory.toShellPayload()` →
  `WebViewShellConfig.cacheRules` → `ShellModeManager` →
  `WebViewManager` per-URL `WebSettings.setCacheMode()` routing.
- **Export chain:** `CacheRule` (domainPattern, mode: CACHE_FIRST /
  NETWORK_FIRST / NO_CACHE / OFFLINE_ONLY) → `ApkConfig` → JSON →
  `ShellConfig` data class with `@SerializedName("cacheRules")`.

### 1.5 Custom Certificate Pinning Per Domain
**Paths:** `data/model/NetworkTrustConfig.kt`, `core/webview/CustomCaTrustStore.kt`
**Status:** ❌ Not implemented. `CustomCaTrustStore.kt` provides custom CA
trust management, but there are no SHA-256 pin rules per domain.
- **Export chain:** Add `certificatePinningRules: List<PinningRule>` to
  `NetworkTrustConfig` → `ApkConfig` → `ApkConfigJsonFactory` →
  `WebViewShellConfig.pinningRules` → `ShellModeManager` →
  runtime `CertificatePinner` / `HostnameVerifier` in
  `CustomCaTrustStore.kt`.
- **`PinningRule` fields:** domainPattern, sha256Pins: List<String>.

### 1.6 Expanded TLS Fingerprint Templates
**Paths:** `core/tls/TlsFingerprintTemplate.kt`
**Status:** ⚠️ Partially implemented. Templates include `CHROME_131`,
`FIREFOX_133`, `SAFARI_18`. No mobile-browser-specific templates.
- **Remaining work:** Add `CHROME_ANDROID_131`, `SAFARI_IOS_18`,
  `SAMSUNG_INTERNET_24`, `EDGE_ANDROID_131` entries with their JA3/JA4
  signatures and TLS extension order. Wire through
  `DisguiseBlock.tlsFingerprint` → `WebViewShellConfig`.

### 1.7 New NativeBridge Capabilities Added Since Last Update
🆕 The `NativeBridge` API surface has expanded from ~20 documented methods
to **94 `@JavascriptInterface` methods** across 6 bridge classes:

| Bridge Class | Key Methods | Status |
|---|---|---|
| `NativeBridge` (44 methods) | toast, vibrate, clipboard, share, download, capture, orientation, fullscreen, device info | ✅ |
| `GeolocationBridge` | `setGeolocationEnabled`, `getCurrentPosition`, `watchPosition`, `clearWatch` | ✅ |
| `DownloadBridge` | `downloadWithHeaders`, `queryDownloads`, `cancelDownload` | ✅ |
| `MediaSessionBridge` | `setMediaMetadata`, `playPause`, `seekTo` | ✅ |
| `PrintBridge` | `startPrint`, `setPrintSettings`, `cancelPrint` | ✅ |
| `ShareBridge` | `shareText`, `shareUrl`, `shareImage` | ✅ |
| `TranslateBridge` | `translateText`, `detectLanguage` | ✅ |

- **Export chain:** Capabilities gated by `NativeBridgeCapabilities` →
  `WebViewBehaviorBlock.enableNativeBridge` → JSON → `ShellConfig` →
  runtime gate in each bridge class.

### 1.8 Picture-in-Picture Support
🆕 A built-in `StreamingPipModule` (`core/extension/BuiltInModules.kt:830`)
injects `requestPictureInPicture()` / `exitPictureInPicture()` JS APIs and
handles the PiP activity transition.

---

## 2. Local Server Features

### 2.1 Ruby Runtime Support
**Paths:** `core/ruby/` (does not exist yet; mirror `core/nodejs/`, `core/php/`)
**Status:** ❌ Not implemented.
- **What:** Add a Ruby runtime (JRuby or MRI cross-compiled for Android
  ARM64) that can serve Rails or Sinatra apps via a local HTTP server,
  similar to how `GoAppConfig` / `PythonAppConfig` work.
- **Export chain:** New `RUBY_APP` AppType → `RubyShellConfig` →
  `ApkConfig` → exported server-mode app.

### 2.2 MariaDB / MySQL Embedded
**Paths:** `core/wordpress/` (WordPress uses PHP+SQLite)
**Status:** ❌ Not implemented.
- **What:** Bundle MariaDB (stripped-down `libmariadb.so` for Android) so
  WordPress, PHP apps, or custom apps can use MySQL instead of SQLite.
  Expose a simple `MySQLConfig` block with host/port/database.

### 2.3 Redis Embedded for Caching
**Paths:** `core/linux/`, `core/engine/`
**Status:** ❌ Not implemented.
- **What:** Bundle a stripped-down `redis-server` native binary (or use a
  pure-Kotlin in-memory key/value store with Redis protocol support) so
  generated apps can use Redis for session storage or caching.

### 2.4 PostgreSQL Embedded
**Paths:** `core/linux/`
**Status:** ❌ Not implemented.
- **What:** Bundle `libpg` / `postgres` binary for Android to support
  Django, Rails, and PHP apps that need PostgreSQL.

### 2.5 Static File Server Customization
**Paths:** `core/webview/LocalHttpServer.kt`, `core/scraper/StaticAssetScraper.kt`
**Status:** ⚠️ Partially implemented. `LocalHttpServer.kt` serves static
  assets, but MIME-type overrides, custom headers, directory listing
  toggles, and index-file fallback are not configurable per app.
- **Export chain:** Add `staticServerConfig` to `HtmlBlock` →
  `ApkConfigJsonFactory` → `HtmlShellConfig` → `LocalHttpServer` runtime.

### 2.6 Rust Native Binary Support
**Paths:** `core/linux/`, `core/golang/`
**Status:** ❌ Not implemented.
- **What:** Allow users to include a pre-compiled Rust `.so` or binary
  that the shell loads and execs, similar to the Go exec loader pattern
  in `libgo_exec_loader.so` (`core/golang/GoRuntime.kt`).

### 2.7 Erlang / Elixir Support
**Paths:** mirror `core/golang/` structure
**Status:** ❌ Not implemented.
- **What:** Bundle BEAM (Erlang VM) for Elixir/Phoenix apps. Phoenix
  is a popular Elixir web framework; supporting it extends the server
  runtime ecosystem.

### 2.8 GeckoView Browser Engine (🆕)
**Paths:** `core/engine/GeckoViewEngine.kt`, `core/engine/EngineType.kt`,
  `core/engine/EngineManager.kt`, `core/engine/download/GeckoEngineDownloader.kt`,
  `core/engine/download/EngineFileManager.kt`
**Status:** ✅ Implemented. GeckoView engine as alternative to System WebView.
  - `EngineType` enum: `SYSTEM_WEBVIEW` (0MB, built-in), `GECKOVIEW` (80MB,
    downloadable).
  - Downloads Gecko native libraries + omni.ja from CDN mirror.
  - HTTP/3 enabled via `prefs["network.dns.http3_echconfig.enabled"] = true`.
  - Export chain: `MetaBlock.engineType` → `ApkConfigJsonFactory` →
    `WebViewShellConfig.engineType` → `EngineViewFactory.resolveEngineType()`.
  - **Tests:** `GeckoViewEngineSeedTest.kt`, `EngineFileManagerTest.kt`,
    `EngineTypeTest.kt`, `GeckoEngineDownloaderTest.kt`.

### 2.9 Frontend Project Builder (🆕)
**Paths:** `core/frontend/FrontendProjectBuilder.kt`, `core/frontend/GitHubRepoFetcher.kt`,
  `core/frontend/ProjectDetector.kt`
**Status:** ✅ Implemented. Detects GitHub frontend projects (React/Vue/
  Svelte/Angular), downloads source, resolves dependencies, packages as
  `FRONTEND` app type.
  - **Export chain:** `AppType.FRONTEND` → `FrontendProjectConfig` →
    `HtmlShellConfig` → `LocalHttpServer` serving the built frontend.
  - **Tests:** `HtmlProjectProcessorTest.kt`, `ProjectDetector.kt`.

### 2.10 Website Scraper & Static Asset Pack (🆕)
**Paths:** `core/scraper/WebsiteScraper.kt`, `core/scraper/StaticAssetScraper.kt`,
  `core/webapp/StaticAssetPack.kt`
**Status:** ✅ Implemented. Downloads a website's HTML/CSS/JS/images/fonts
  into a static asset pack for offline use in generated APKs.
  - **Export chain:** `StaticAssetPack` config → `WebViewBlock` static
    asset fields → `ApkConfigJsonFactory` → `HtmlShellConfig.staticAssets`
    → `StaticAssetScraper` / `LocalHttpServer` at runtime.
  - **Tests:** `StaticAssetScraperTest.kt`, `StaticAssetPackExportWiringTest.kt`.

---

## 3. Network & Security

### 3.1 Tor Proxy Integration
**Paths:** `core/dns/DnsManager.kt`, `core/webview/LocalHttpToSocksBridge.kt`
**Status:** ⚠️ Partially implemented. `LocalHttpToSocksBridge` provides
  the SOCKS5 bridge infrastructure; no Tor circuit builder or Tor control
  port integration.
- **Remaining work:** Add Tor bundle download + control (`TorCircuitBuilder`),
  wire through `DnsManager` Tor mode, export via `ProxyBlock.proxyType = "TOR"`.

### 3.2 Certificate Transparency Monitoring
**Paths:** `core/webview/CustomCaTrustStore.kt`, `core/crypto/NativeCrypto.kt`
**Status:** ❌ Not implemented.
- **What:** When a custom CA is configured, log or alert if the server's
  certificate is not present in a CT log. Use a lightweight CT verifier
  (or a remote API) to validate certificate transparency.

### 3.3 DNS Leak Protection Test
**Paths:** `core/dns/DnsManager.kt`, `ui/screens/MoreScreen.kt`
**Status:** ❌ Not implemented.
- **What:** Add a "Test DNS Leak" button in the DNS config card that
  fetches `https://dnsleaktest.com/api/` or a similar endpoint and reports
  whether the resolved IP/provider matches the configured DoH provider.

### 3.4 Per-Domain CA Management
**Paths:** `data/model/NetworkTrustConfig.kt`, `core/webview/CustomCaTrustStore.kt`
**Status:** ✅ Implemented. `CustomCaTrustStore.kt` manages custom CAs;
  domain-scoping via `domainPatterns` is supported in the model.
- **Implementation:** Custom CA → `CustomCaCertificate` →
  `NetworkTrustConfig.customCaCertificates` → export → runtime in
  `CustomCaTrustStore.kt`.

---

## 4. Privacy & Anti-Detection

### 4.1 Advanced TLS Version Restriction UI
**Paths:** `core/webview/`, `ui/components/IsolationConfigCard.kt`
**Status:** ❌ Not implemented.
- **What:** Add a UI to restrict the minimum TLS version (e.g., TLS 1.3
  only, or TLS 1.2+) for the generated app, and to disable deprecated
  protocols.
- **Implementation:** Add `minTlsVersion` field to `WebViewBehaviorBlock`
  → `ApkConfigJsonFactory` → `WebViewShellConfig` → apply in
  `NetworkSecurityConfigBuilder.kt`.

### 4.2 Per-Domain Isolation Rules
**Paths:** `core/privacy/IsolationConfig.kt`, `core/private/IsolationManager.kt`
**Status:** ⚠️ Partially implemented. `IsolationConfig.kt` and
  `IsolationManager.kt` exist; per-domain override UI is missing.
- **Remaining work:** Add per-domain isolation rules to the editor UI
  (`IsolationConfigCard.kt`), export through `IsolationShellConfig`,
  apply in `IsolationScriptInjector.kt` at runtime.

### 4.3 WebRTC IP Leak Test
**Paths:** `core/webview/`, `core/webview/ScreenCaptureHelper.kt`
**Status:** ❌ Not implemented. (WebRTC capture exists; IP leak test does not.)
- **What:** Add a tool (host-side or generated-app-side) that checks
  whether the app's WebRTC implementation is leaking local IPs, and
  reports the result.

### 4.4 Headless Browser Detection Bypass
**Paths:** `core/appearance/BrowserDisguiseEngine.kt`, `core/appearance/BrowserDisguiseJsGenerator.kt`, `core/kernel/BrowserKernel.kt`
**Status:** ✅ Implemented. `BrowserDisguiseEngine` covers
  `navigator.webdriver`, `Chrome` runtime object, automation flags,
  `navigator.plugins`, and `permissions` API spoofing. Patches are
  injected via `BrowserDisguiseJsGenerator.kt` (89 KB of JS).
- **Config:** `DisguiseBlock.enableKernelDisguise` → `WebViewBlock`
  → export → `WebViewShellConfig` → runtime in
  `BrowserKernel.kt` / shell WebView config.
- **Tests:** `DeviceDisguiseConfigTest.kt`, `BrowserKernelScreen.kt`.

---

## 5. Agent Tool System (55 tools)

**Paths:** `core/agent/tool/builtin/`, `core/agent/tool/ToolRegistryFactory.kt`
**Status:** 🔄 Significantly expanded. The agent system now has **55 tools**
  (up from 54), with 6 LLM providers, plan mode, image generation, file
  management, and session export capabilities. Many suggested tools are now
  implemented; see §15 (AI & Agent System) for the full infrastructure.

### 5.1 App Screenshot Tool
**Status:** ✅ Implemented. `WebsiteScreenshotService.kt` captures WebView
  screenshots; displayed in the host `HomeScreen.kt` with caching.
  `ViewImageTool` / `ListImagesTool` in the imagery tools set provide
  agent-level image viewing.
- **Implementation:** Uses `EngineManager.createEngine()` →
  `BrowserEngine.captureToBitmap()` path in `WebViewActivity.kt`.

### 5.2 Network Inspection Tool
**Status:** ❌ Not implemented. (No web-request interceptor tool exists.)

### 5.3 Database Export Tool
**Status:** ❌ Not implemented.

### 5.4 Performance Profiling Tool
**Status:** ⚠️ Partially implemented. `PerformanceOptimizer.kt`,
  `NativePerfEngine.kt`, `SystemPerfOptimizer.kt` exist for host-side
  optimization. No Agent tool returns FPS/memory/load-time metrics.
- **Remaining work:** Wrap as `ProfileAppPerformanceTool` →
  `PerformanceOptimizer.profileWebview(durationMs)` → return metrics.

### 5.5 Remote Control Tool
**Status:** ❌ Not implemented.

### 5.6 Crash Log Inspection Tool
**Status:** ❌ Not implemented.

### 5.7 Permission Audit Tool
**Status:** ❌ Not implemented.

### 5.8 Certificate Inspection Tool
**Status:** ❌ Not implemented.

### 5.9 Network Speed Test Tool
**Status:** ❌ Not implemented.

### 5.10 Export Queue / History Tool
**Status:** ⚠️ Partially implemented. `BuildLogger.kt` persists build logs
  to disk; `ApkBuildCache.kt` tracks incremental plans. No structured
  Room table for export history accessible from the agent.
- **Remaining work:** Add `ExportHistoryEntity` → `GetExportHistoryTool`.

### 5.11 App Backup/Restore Tools
**Status:** ✅ Partially implemented. `DataBackupManager.kt` provides full
  backup/restore. `ExportAppTemplateTool` wraps template save. A dedicated
  `BackupAppTool` / `RestoreAppTool` pair is still missing.

### 5.12 Shell Template Management Tool
**Status:** ⚠️ Partially implemented. `GetBuildEnvStatusTool` and
  `InitializeBuildEnvTool` cover build-env status and initialization.
  A `RebuildShellTemplateTool` / `InspectShellTemplateTool` pair is
  still missing.

### 5.13 Agent Tool Inventory (🆕)
The 55 registered tools (in `ToolRegistryFactory.kt`), grouped by domain:

| Domain | Tools | Count |
|--------|-------|-------|
| File system | ReadFileTool, ReadAppFileTool, WriteFileTool, EditFileTool, DeleteFileTool, ListFilesTool, GlobTool, GrepTool | 8 |
| Apps | ListAppsTool, GetAppTool, CreateAppTool, UpdateAppTool, ShareApkTool, DeleteAppTool, DuplicateAppTool, MoveToCategoryTool, ClearAppCacheTool | 9 |
| Build & export | BuildApkTool, ExportAppTool, ExportAabTool, ExportAppTemplateTool | 4 |
| Shorts & media | CreateShortcutTool, GenerateImageTool, ViewImageTool, ListImagesTool | 4 |
| Ports & engine | ScanPortsTool, KillPortTool, KillAllPortsTool, GetEngineStatusTool, SelectEngineTool, DeleteEngineTool | 6 |
| Hosts & runtime | GetAdBlockStatusTool, ManageHostsRulesTool, GetRuntimeStatusTool, InstallRuntimeTool, ClearRuntimeCacheTool | 5 |
| Modules | ListModulesTool, GetModuleTool, CreateModuleTool, UpdateModuleTool | 4 |
| Stats & apps | GetUsageStatsTool, CheckAppHealthTool, ListInstalledAppsTool, CloneAppTool, BatchImportAppsTool | 5 |
| Build env | GetBuildEnvStatusTool, InitializeBuildEnvTool, CheckPlayPolicyTool | 3 |
| Plan | EnterPlanModeTool, ExitPlanModeTool | 2 |
| Interaction | AskUserTool, TodoWriteTool, TodoUpdateTool | 3 |
| **Total** | | **55** |

---

## 6. Export Pipeline

**Paths:** `core/apkbuilder/`, `core/export/`, `core/playstore/aab/`
**Status:** 🔄 Heavily refactored. AAB export, artifact verification,
  build logging, encrypted builds, and preflight checks are now
  implemented.

### 6.1 Incremental Asset Pack Updates
**Status:** ⚠️ Partially implemented. `ApkBuildCache` supports
  `FULL` / `CONTENT_OVERLAY` / `REUSE_UNSIGNED`. A granular "smart overlay"
  that tracks per-asset hashes across builds is still missing.
- **Remaining work:** Extend `IncrementalPlan` to track per-asset content
  hashes in `ApkBuildCache.kt`.

### 6.2 Build Artifact Metadata / SBOM
**Status:** ⚠️ Partially implemented. `ApkArtifactVerifier.kt` verifies
  APK contents (assets, native libs, permissions, signing). `ApkAnalyzer.kt`
  produces optimization hints. A full SBOM with CVE scanning is not yet
  implemented.
- **Remaining work:** Integrate `ApkArtifactVerifier` results into a
  machine-readable SBOM format + CVE database lookup.

### 6.3 Export Result History Tracking
**Status:** ⚠️ Partially implemented. `BuildLogger.kt` persists logs
  to `files/build_logs/` with timestamps and success/failure status.
  No structured Room table exists yet.
- **Remaining work:** Add `ExportHistoryEntity` (Room) → populate from
  `BuildLogger.endLog()` → UI in `BuildApkScreen.kt`.

### 6.4 Cloud Build Integration
**Status:** ❌ Not implemented.

### 6.5 Export Scheduling / Queue
**Status:** ❌ Not implemented. (Auto-start scheduling exists for app
  launch, not export.)

### 6.6 Export Preview / Diff
**Status:** ⚠️ Partially implemented. `LineDiff.kt` exists in the agent
  system. `ChangesReviewCard.kt` shows config changes. Not yet integrated
  as a mandatory pre-export review step.
- **Remaining work:** Wire `ChangesReviewCard.kt` into `BuildApkScreen.kt`.

### 6.7 AAB (Android App Bundle) Export (🆕)
**Paths:** `core/playstore/aab/AabExporter.kt`,
  `core/playstore/aab/AabSigner.kt`,
  `core/apkbuilder/ApkToAabAssembler.kt`,
  `core/playstore/aab/AabValidationHelper.kt`,
  `core/playstore/aab/AabBundleConfigFactory.kt`
**Status:** ✅ Implemented. Converts APK → AAB using proto manifest
  (`AxmlToProtoXml.kt`) and proto resource table (`ArscToProtoTable.kt`).
  - **Tests:** `AabProtoSmokeTest.kt`, `ApkToAabAssemblerTest.kt`,
    `ApkToAabAssemblerPlaintextXmlTest.kt`.

### 6.8 APK Artifact Verification (🆕)
**Paths:** `core/apkbuilder/ApkArtifactVerifier.kt`
**Status:** ✅ Implemented. Verifies APK contents against expected
  assets, native libraries, permissions, and signing info.
  - **Tests:** `ApkArtifactVerifierTest.kt`.

### 6.9 Encrypted APK Builds (🆕)
**Paths:** `core/crypto/EncryptedApkBuilder.kt`,
  `core/crypto/AesCryptoEngine.kt`, `core/crypto/AssetEncryptor.kt`,
  `core/crypto/AssetDecryptor.kt`, `core/crypto/SecureAssetLoader.kt`
**Status:** ✅ Implemented. AES-GCM encryption, asset encryption,
  secure loading, content/string obfuscation. Forces `FULL` rebuild.
  - **Tests:** `EncryptionConfigTest.kt`, `EncryptionConfigExtendedTest.kt`,
    `EnhancedCryptoTest.kt`.

### 6.10 Build Input Preflight (🆕)
**Paths:** `core/apkbuilder/BuildInputPreflight.kt`,
  `core/apkbuilder/ApkExportPreflight.kt`
**Status:** ✅ Implemented. Pre-build validation: required files,
  binary presence, project structure, config validity.
  - **Tests:** `BuildInputPreflightTest.kt`, `ApkExportPreflightTest.kt`.

---

## 7. Editor UI

**Paths:** `ui/screens/CreateAppWebViewCards.kt`, `ui/components/`, `ui/screens/`
**Status:** 🔄 Significantly expanded. ~40 new Compose screens now exist.
  See §21 (New Editor UI Screens Catalog) for the full list.

### 7.1 Visual WebView DOM Inspector
**Status:** ❌ Not implemented.
- **What:** Add a devtools-like overlay in the host preview that
  allows inspecting the rendered DOM tree, highlighting elements, and
  viewing computed styles.

### 7.2 Real-Time Config Change Preview
**Status:** ❌ Not implemented.

### 7.3 Config Comparison / Diff Tool
**Status:** ⚠️ Partially implemented. `LineDiff.kt` exists in the agent
  system. `ChangesReviewCard.kt` shows changes. A dedicated config
  comparison/diff screen for app configs is still needed.
- **Remaining work:** Add `ConfigDiffUtil.compare()` → render with `LineDiff`.

### 7.4 Bulk Edit (Multi-App Config)
**Status:** ❌ Not implemented.

### 7.5 Theme Preview Before Building
**Status:** ⚠️ Partially implemented. `CreateAppSplashCard.kt` previews
  the splash with selected colors. Full theme preview (splash + status
  bar + error page + gallery) is not implemented.
- **Remaining work:** Add `PreviewPane.kt` theme mode.

### 7.6 In-App Code Snippet Library
**Status:** ✅ Implemented. `CodeSnippets.kt` (1200+ lines) provides
  categorized JS/CSS snippets, NativeBridge call examples, and polyfill
  patches. Accessible via `CodeSnippetSelector.kt`.

### 7.7 App Icon Adaptive Icon Customization
**Status:** ⚠️ Partially implemented. `ApkTemplate.kt` provides
  `createAdaptiveForegroundIcon()`, `createRoundIcon()`, and
  `scaleBitmapToPng()`. Visual editor (`IconGeneratorDialog.kt`,
  `IconLibraryDialog.kt`) exists but needs icon-design tooling.

---

## 8. Module System

**Paths:** `modules/`, `core/extension/`, `core/market/`
**Status:** ✅ Fully evolved. Chrome extension support, declarative net
  request, module market, sandboxing, and module editor are now core
  features.

### 8.1 Module Dependency Management
**Status:** ✅ Implemented. `ExtensionModule.kt` declares a
  `dependencies: List<String>` field; `ModulePresetManager.kt` resolves
  transitive dependencies.

### 8.2 Module Version Pinning
**Status:** ❌ Not implemented.

### 8.3 Module Sandboxing Controls
**Status:** ✅ Implemented. `ModulePermission` enum (14 perms: CLIPBOARD,
  STORAGE, SCREEN_CAPTURE, NOTIFICATIONS, etc.) gates module access.
  `PermissionConfigScreen.kt` for review/grant/revoke.
  - Export chain: permissions → `ExtensionBlock` → `ApkConfigJsonFactory`
    → `EmbeddedShellModule.permissions` → `ShellModeManager`.

### 8.4 Module Performance Monitoring
**Status:** ❌ Not implemented.

### 8.5 Module Sharing via QR Code
**Status:** ⚠️ Partially implemented. `QrCodeUtils.kt` exists; QR sharing
  for modules needs integration into `ExtensionModuleScreen.kt`.

### 8.6 User Script Import from File
**Status:** ✅ Implemented. `UserScriptParser.kt` parses
  Tampermonkey/Greasemonkey `.user.js`. `SaveSessionAsModuleUseCase.kt`
  and `SessionArtifactDetector.kt` auto-detect user scripts in code
  sessions. `ChromeExtensionParser.kt` handles CRX/MV3 extensions.
  - **Tests:** `ChromeExtensionParserTest.kt`,
    `ChromeExtensionContentScriptRegistryTest.kt`.

### 8.7 Chrome Extension Full Runtime (🆕)
**Paths:** `core/extension/ChromeExtensionRuntime.kt`,
  `core/extension/ChromeExtensionPolyfill.kt`,
  `core/extension/ChromeExtensionContentScriptRegistry.kt`,
  `core/extension/ChromeExtensionScriptingBridge.kt`,
  `core/extension/ChromeExtensionMobileCompat.kt`,
  `core/extension/BuiltInChromeExtensions.kt`
**Status:** ✅ Implemented. Full Chrome extension runtime with 92 KB of
  JS polyfill. Content scripts, background scripts, declarative net
  request, storage sync, popup management.

### 8.8 Declarative Net Request (🆕)
**Paths:** `core/extension/DeclarativeNetRequestEngine.kt`
**Status:** ✅ Implemented. Chrome's content-blocking rule engine with
  `ActionType`, `ResourceType`, `DnrRule`, `StaticRuleset`.
  - **Tests:** `DeclarativeNetRequestEngineTest.kt`,
    `ExtensionStorageSyncTest.kt`.

---

## 9. Internationalization

**Paths:** `core/i18n/Strings.kt`, `core/i18n/LanguageManager.kt`, `data/model/AppLanguage`
**Status:** ✅ 10 languages (Chinese, English, Arabic, Portuguese, Spanish,
  French, German, Russian, Japanese, Korean). All user-visible strings
  are inline `when (Strings.lang)` blocks — `else ->` is banned
  (enforced by `StringsKtTranslationParityTest.kt`).

### 9.1 Expand Host UI Languages
**Status:** ⚠️ Partially addressable. 10 languages currently supported.
  The `TranslateLanguage` enum includes more for the in-app translation
  feature. Adding host UI languages requires extending `AppLanguage`
  and all `when (Strings.lang)` blocks.
  - **Languages to add:** Italian, Dutch, Polish, Hindi, Thai, Vietnamese, Turkish.

### 9.2 RTL Layout Testing Tool
**Status:** ❌ Not implemented.

### 9.3 Per-App Language Override
**Status:** ❌ Not implemented.
- **Export chain:** Add `languageOverride` to `MetaBlock` →
  `ApkConfigJsonFactory` → `ShellConfig` →
  `LanguageManager.setLanguage()` at shell runtime.

---

## 10. App Experience Features

### 10.1 App Widgets
**Paths:** `ui/shell/`, `core/apkbuilder/ApkBuilder.kt`
**Status:** ❌ Not implemented.
- **What:** Allow generated apps to embed Android App Widgets.
- **Export chain:** Add `WidgetConfig` to `ApkConfig` →
  `ApkConfigJsonFactory` → declare `AppWidgetProvider` in
  `AxmlEditor.kt` manifest → runtime in `ShellActivity.kt`.

### 10.2 Dynamic App Shortcuts
**Paths:** `core/export/AppExporter.kt`,
  `core/agent/tool/builtin/AppLifecycleTools.kt` (`CreateShortcutTool`)
**Status:** ⚠️ Partially implemented. Static shortcuts are supported via
  `AppExporter.kt` (`ShortcutManagerCompat.requestPinShortcut`). Dynamic
  shortcuts (updated at runtime) are not supported.
- **Remaining work:** Add runtime dynamic shortcut generation in the
  shell based on web app navigation history or config-defined list.

### 10.3 In-App Purchases (IAP) Bridge
**Paths:** `core/webview/NativeBridge.kt`
**Status:** ❌ Not implemented. NativeBridge has 94 `@JavascriptInterface`
  methods but none for Play Billing.
- **What:** Add `NativeBridge.launchBillingFlow(productId)`,
  `queryPurchases()`, `consumePurchase()` that wraps Play Billing.
- **Implementation:** New `BillingBridge.kt` class → gate via
  `NativeBridgeCapabilities.iap` → export flag in `ApkConfig`.

### 10.4 App Rating Integration
**Status:** ❌ Not implemented.
- **What:** Add a `rateApp()` NativeBridge method and an editor
  toggle for "Show rating prompt after N days / N launches".
- **Implementation:** Add `rateApp()` to `NativeBridge.kt`, add
  `ratingEnabled: Boolean`, `ratingTriggerDays: Int`,
  `ratingTriggerLaunches: Int` to `WebViewBehaviorBlock`.

### 10.5 Notification Categories Management
**Paths:** `core/notification/NotificationFcmService.kt`,
  `ui/components/NotificationConfigCard.kt`
**Status:** ⚠️ Partially implemented. FCM/WebSocket/Polling channels exist
  with `SafeNotificationChannels` fail-soft creation. Editor UI only
  supports a single notification channel config.
- **Remaining work:** Extend `NotificationConfigCard.kt` to support
  multiple named channels with per-channel sound/vibration/importance.

### 10.6 Custom Notification LED Color
**Status:** ❌ Not implemented.

### 10.7 App Shortcuts (Static)
**Paths:** `core/export/AppExporter.kt`,
  `core/agent/tool/builtin/AppLifecycleTools.kt` (`CreateShortcutTool`)
**Status:** ✅ Implemented. Static shortcuts declared in manifest +
  `CreateShortcutTool` for agent-level creation.

### 10.8 Push Notification Rich Media
**Paths:** `core/notification/`
**Status:** ⚠️ Partially implemented. The three push channels
  (FCM/WebSocket/Polling) exist. Rich notification styles (image,
  big-text, action buttons) are not supported.
- **Remaining work:** Extend `NotificationFcmService.kt` to parse
  `image` and `action_buttons` from FCM payload → build
  `NotificationCompat.BigPictureStyle` / `BigTextStyle`.

### 10.9 Floating Window (🆕)
**Paths:** `core/floatingwindow/FloatingWindowManager.kt`,
  `core/floatingwindow/FloatingWindowService.kt`,
  `ui/components/FloatingWindowConfigCard.kt`
**Status:** ✅ Implemented. Overlay window support for running apps
  alongside other activities.

### 10.10 Background Music (🆕)
**Paths:** `core/bgm/BgmPlayer.kt`, `core/bgm/OnlineMusicApi.kt`,
  `core/bgm/OnlineMusicDownloader.kt`,
  `ui/components/BgmCard.kt`, `ui/components/BgmSelector.kt`
**Status:** ✅ Implemented. Online music search (NetEase API), download,
  LRC lyrics, and playback service.

---

## 11. Analytics & Monitoring

**Paths:** `core/stats/`, `core/logging/`

### 11.1 In-App Analytics Dashboard
**Status:** ⚠️ Partially implemented. `AppStatsRepository.kt` and
  `AppUsageStatsDao.kt` track app usage stats. `StatsScreen.kt` displays
  usage stats. Per-app web analytics (page views, bandwidth breakdown) for
  exported apps is not implemented.
- **Remaining work:** Add web analytics collection in the shell; build
  dashboard in `StatsScreen.kt`.
- **Tests:** `AppUsageStatsTest.kt`.

### 11.2 Crash Reporting (Self-Hosted)
**Status:** ❌ Not implemented. (`AppLogger.kt` logs to file but doesn't
  send to a remote endpoint.)

### 11.3 Network Usage Monitoring
**Status:** ❌ Not implemented.

### 11.4 Performance Metrics Dashboard
**Status:** ⚠️ Partially implemented. `PerformanceOptimizer.kt`,
  `NativePerfEngine.kt` exist for host-side optimization. No dashboard
  for exported apps.
- **Remaining work:** Add perf metrics collection in the shell + display
  in `StatsScreen.kt`.

---

## 12. Developer Tools

**Paths:** `core/apkbuilder/`, `core/apkbuilder/ApkAnalyzer.kt`, `ui/screens/`

### 12.1 APK Analyzer
**Status:** ✅ Implemented. `ApkAnalyzer.kt` shows manifest, resources,
  native libraries, permissions, signing info, and optimization hints.
  `ApkArtifactVerifier.kt` extends with content verification.

### 12.2 Manifest Viewer
**Status:** ⚠️ Partially implemented. `AxmlRebuilder.kt` handles manifest
  modification; `ApkAnalyzer.kt` can dump the manifest. A friendly
  tree-view UI does not exist.
- **Remaining work:** Add Compose tree-view screen using `AxmlReader.kt`.

### 12.3 Asset Explorer
**Status:** ❌ Not implemented.

### 12.4 Export Template Management
**Status:** ✅ Implemented. `ConfigTemplateStore.kt` (schema-free JSON) +
  `ConfigTemplateTools.kt` (4 Agent tools: List, Save, Apply, Delete).
  - **Tests:** `ConfigTemplateStoreTest.kt`.

### 12.5 Code Snippet Library (Host-Side)
**Status:** ✅ Implemented. `CodeSnippets.kt` (1200+ lines) provides
  categorized JS/CSS snippets, NativeBridge call examples, polyfill
  patches. Accessible via `CodeSnippetSelector.kt`.

### 12.6 Dependency Analysis for Export
**Status:** ✅ Implemented. `BuildInputPreflight.kt` checks for required
  files, binary presence, project structure, and config validity.
  - **Tests:** `BuildInputPreflightTest.kt`.

---

## 13. Cross-Platform & Distribution

### 13.1 iOS Export (Future)
**Note:** Currently Android-only.
**Status:** ❌ Not implemented.

### 13.2 Desktop Export
**Paths:** `core/export/`
**Status:** ❌ Not implemented.

### 13.3 Firebase App Distribution Integration
**Paths:** `core/playstore/aab/`, `ui/screens/PlayStoreScreen.kt`
**Status:** ⚠️ Partially implemented. AAB export + signing done
  (`AabExporter.kt`). Firebase App Distribution upload not wired.
- **Remaining work:** Add Firebase App Distribution API integration
  in `PlayStoreScreen.kt` → upload AAB after build.

### 13.4 GitHub Actions Integration
**Status:** ❌ Not implemented.

### 13.5 App Store Optimization (ASO)
**Paths:** `ui/screens/PlayStoreScreen.kt`
**Status:** ❌ Not implemented.

---

### 14.1 Shell Config Drift Coverage Tests
**Status:** ✅ Implemented. `WebViewConfigBooleanCoverageTest.kt` enforces
  that every declared Boolean field in `WebViewConfig` is listed in
  `flipAllBooleans()`. `ConfigRoundTripSentinelTest.kt` verifies the
  full WebApp → ApkConfig → JSON → ShellConfig round-trip.
  `check_config_field_drift.py` enforces field-name alignment between
  `ApkConfigJsonFactory.toShellPayload()` and `ShellModeManager.ShellConfig`.
  - **CI gate:** `./gradlew :app:checkConfigFieldDrift`.
  - **Tests:** `WebViewConfigBooleanCoverageTest.kt`,
    `ConfigRoundTripSentinelTest.kt`, `ApkConfigJsonFactoryTest.kt`,
    `ApkConfigWiringGuardTest.kt`.

### 14.2 Export Round-Trip Tests
**Status:** ✅ Implemented (broader scope). See 14.1 above. The
  round-trip test covers the full `WebApp` → `ApkConfig` → JSON →
  `ShellConfig` chain.
  - **Tests:** `ConfigRoundTripSentinelTest.kt` (sentinel field injection),
    `BackButtonBehaviorExportWiringTest.kt`,
    `StaticAssetPackExportWiringTest.kt`,
    `ErrorPageApkRoundTripTest.kt`,
    `EncryptionConfigTest.kt`, `EncryptionConfigExtendedTest.kt`.

### 14.3 Agent Tool Schema Alignment Tests
**Status:** ❌ Not implemented.
- **What:** Tests that verify each Agent tool's `parametersSchema`
  matches the fields read by its `execute()` method, so the LLM
  can always pass every available parameter.
- **Implementation:** Add `AgentToolSchemaAlignmentTest.kt` that
  iterates over `ToolRegistryFactory.baseTools()` and uses reflection
  to verify schema ↔ `execute()` field alignment.

---

## 15. AI & Agent System (🆕)

**Paths:** `core/agent/`, `core/ai/`, `ui/agent/AgentScreen.kt`,
`ui/agent/AgentViewModel.kt`, `ui/AiSettingsScreen.kt`
**Status:** ✅ Major new area. Full coding-assistant agent system.

### 15.1 LLM Provider Infrastructure (🆕)
**Paths:** `core/agent/llm/`, `core/ai/`
**Status:** ✅ Implemented. Six providers behind a unified `LlmGateway`:

| Provider | File | Capabilities |
|---|---|---|
| OpenAI (compat) | `OpenAiCompatProvider.kt` | Text, multimodal, image gen |
| Anthropic | `AnthropicProvider.kt` | Text, multimodal |
| Google Gemini | `GeminiProvider.kt` | Text, multimodal |
| OpenAI Responses API | `ResponsesProvider.kt` | Text, structured output |
| Ollama | `OllamaProvider.kt` | Local text |
| OpenAI/Gen Image Gen | `OpenAiImageGenerator.kt`, `GeminiImageGenerator.kt` | Image gen |

- **SSE streaming:** `SseParser.kt` with resilient parse boundaries.
- **Config:** `AiConfigManager.kt` (encrypted API keys via Android
  Keystore + AES-GCM), `ModelsDevRepository.kt` (model catalog),
  `AiStreamModels.kt`.
- **AI features:** `AiFeature` enum (AGENT, AGENT_IMAGE,
  ICON_GENERATION, MODULE_DEVELOPMENT, LRC_GENERATION, TRANSLATION, GENERAL).
- **Tests:** `AiConfigManagerTest.kt`, `AiConfigManagerMigrationTest.kt`,
  `AiPromptManagerTest.kt`, `AiProviderCatalogTest.kt`.

### 15.2 Agent Engine & Session Management (🆕)
**Paths:** `core/agent/engine/AgentEngine.kt`,
`core/agent/session/SessionStore.kt`,
`core/agent/session/SessionModels.kt`,
`core/agent/AgentService.kt`
**Status:** ✅ Implemented. Full conversation management with structured
  message history, session pinning, draft restoration, plan mode,
  permission prompting, and agent-to-app/module export.
- **Components:** `PermissionPrompter.kt` (Channel-based confirmation),
  `ProjectFileManager.kt`, `TodoManager.kt`, `LineDiff.kt`,
  `SessionArtifactDetector.kt`, `SaveSessionAsAppUseCase.kt`,
  `SaveSessionAsModuleUseCase.kt`, `CompactService.kt`.

### 15.3 Agent File & Project Tools (🆕)
**Paths:** `core/agent/tool/builtin/{ReadFileTool,WriteFileTool,EditFileTool,
  DeleteFileTool,ListFilesTool,GlobTool,GrepTool}.kt`
**Status:** ✅ Implemented. The agent can explore the codebase, read/write/
  edit files, grep/glob search, and manage todo lists.

### 15.4 Image Generation (🆕)
**Paths:** `core/agent/imagery/`,
`core/agent/tool/builtin/imagery/`
**Status:** ✅ Implemented. `GenerateImageTool`, `ViewImageTool`,
`ListImagesTool` with Gemini and OpenAI backends.
  - `DefaultImageGenerators.kt` factory, `ImageGeneratorRegistry`.

---

## 16. Browser Engine System (🆕)

**Paths:** `core/engine/`, `core/engine/download/`,
`ui/screens/BrowserKernelScreen.kt`, `ui/screens/RuntimeDepsScreen.kt`
**Status:** ✅ Implemented. Supports selectable browser engines.

### 16.1 GeckoView Engine (🆕)
**Paths:** `core/engine/GeckoViewEngine.kt`,
`core/engine/EngineType.kt`, `core/engine/EngineManager.kt`,
`core/engine/download/GeckoEngineDownloader.kt`,
`core/engine/download/EngineFileManager.kt`
**Status:** ✅ Implemented.
  - `EngineType` enum: `SYSTEM_WEBVIEW` (0MB), `GECKOVIEW` (80MB, downloadable).
  - Downloads Gecko native libs + `omni.ja` from CDN.
  - HTTP/3 enabled via `prefs["network.dns.http3_echconfig.enabled"] = true`.
  - Export chain: `MetaBlock.engineType` → `ApkConfigJsonFactory` →
    `WebViewShellConfig.engineType` → `EngineViewFactory`.
  - **Tests:** `GeckoViewEngineSeedTest.kt`, `EngineFileManagerTest.kt`,
    `EngineTypeTest.kt`, `GeckoEngineDownloaderTest.kt`.

### 16.2 Engine Download & Management (🆕)
**Status:** ✅ Implemented. Version tracking, ABI selection, disk space
  checks, partial download resume, cleanup on deletion.
  - **Tests:** `EngineFileManagerTest.kt`,
    `LocalBuildEnvironmentTest.kt`.

---

## 17. Chrome Extension & Module System (🆕)

**Paths:** `core/extension/`, `modules/`, `core/market/`,
`ui/screens/ModuleEditorScreen.kt`, `ui/screens/ModuleMarketScreen.kt`
**Status:** ✅ Fully implemented. Comprehensive Chrome extension ecosystem.

### 17.1 Chrome Extension Runtime (🆕)
**Paths:** `core/extension/ChromeExtensionRuntime.kt`,
`core/extension/ChromeExtensionPolyfill.kt`,
`core/extension/ChromeExtensionContentScriptRegistry.kt`,
`core/extension/ChromeExtensionScriptingBridge.kt`,
`core/extension/ChromeExtensionMobileCompat.kt`,
`core/extension/BuiltInChromeExtensions.kt`
**Status:** ✅ Implemented.
  - 92 KB of JS polyfill covering `chrome.runtime`, `chrome.tabs`,
    `chrome.storage`, `chrome.scripting`, `chrome.action`,
    `chrome.webRequest`, etc.
  - Content scripts, background scripts, storage sync, popup management.
  - `DeclarativeNetRequestEngine.kt` (100+ rules supported).
  - `BuiltInModules.kt` (1000+ lines) ships built-in modules.
  - **Tests:** `ChromeExtensionParserTest.kt`,
    `ChromeExtensionContentScriptRegistryTest.kt`,
    `ChromeExtensionScriptingBridgeTest.kt`,
    `ChromeExtensionPolyfillTest.kt`,
    `DeclarativeNetRequestEngineTest.kt`,
    `ExtensionStorageSyncTest.kt`, `ExtensionManagerTest.kt`,
    `ExtensionModuleTest.kt`, `ModulePresetManagerTest.kt`,
    `GreasyForkSearchTest.kt`, `ChromeWebStoreSearchTest.kt`.

### 17.2 Module Market & Preset Management (🆕)
**Paths:** `core/market/ModuleMarketRepository.kt`,
`core/market/ModuleMarketRepository.kt`,
`core/market/GreasyForkSearch.kt`,
`core/market/ChromeWebStoreSearch.kt`,
`core/market/CwsTags.kt`, `core/extension/ModulePreset.kt`,
`core/extension/ModuleTemplates.kt`
**Status:** ✅ Implemented. Market with GreasyFork + Chrome Web Store
  search. Presets for one-click config bundles. Module templates
  with 3500+ lines of built-in modules.

---

## 18. Build & Export Pipeline Evolution (🆕)

**Paths:** `core/apkbuilder/`, `core/export/`, `core/playstore/aab/`,
`core/crypto/`
**Status:** ✅ Implemented. AAB support, artifact verification,
  encrypted builds, and preflight checks.

### 18.1 AAB Export (🆕)
**Paths:** `core/playstore/aab/AabExporter.kt`,
`core/playstore/aab/AabSigner.kt`,
`core/apkbuilder/ApkToAabAssembler.kt`,
`core/playstore/aab/AabValidationHelper.kt`
**Status:** ✅ Implemented.
  - Converts APK → AAB using proto manifest (`AxmlToProtoXml.kt`).
  - Signing via keystore (`AabSigner.kt`).
  - Validation: asset files, proto XML integrity.
  - **Tests:** `AabProtoSmokeTest.kt`, `ApkToAabAssemblerTest.kt`.

### 18.2 Artifact Verification (🆕)
**Paths:** `core/apkbuilder/ApkArtifactVerifier.kt`
**Status:** ✅ Implemented.
  - **Tests:** `ApkArtifactVerifierTest.kt`.

### 18.3 Encrypted Builds (🆕)
**Paths:** `core/crypto/EncryptedApkBuilder.kt`,
`core/crypto/AesCryptoEngine.kt`, `core/crypto/AssetEncryptor.kt`
**Status:** ✅ Implemented. AES-GCM encryption. Forces `FULL` rebuild.
  - **Tests:** `EncryptionConfigTest.kt`,
    `EncryptionConfigExtendedTest.kt`.

### 18.4 Build Input Preflight (🆕)
**Paths:** `core/apkbuilder/BuildInputPreflight.kt`,
`core/apkbuilder/ApkExportPreflight.kt`
**Status:** ✅ Implemented.
  - **Tests:** `BuildInputPreflightTest.kt`,
    `ApkExportPreflightTest.kt`.

---

## 19. App Cloning & Identity Reshape (🆕)

**Paths:** `core/appmodifier/`, `ui/screens/AppModifierScreen.kt`,
`ui/screens/AppModifierEditState.kt`
**Status:** ✅ Implemented. Clone and reshape existing APKs.

### 19.1 APK Cloner (🆕)
**Paths:** `core/appmodifier/AppCloner.kt`,
`core/appmodifier/CloneConfigBuilder.kt`,
`core/appmodifier/CloneManifestRewriter.kt`
**Status:** ✅ Implemented. Clone APK with new package name, app name,
  icon, and identity. Supports static shortcuts, manifest rewriting,
  and resource updates.
  - Agent tool: `CloneAppTool` (in `StatsModifierImportTools.kt`).

---

## 20. High-Priority Quick Wins

These items have existing infrastructure that makes implementation small-moderate
effort with clear user value. Each includes the specific code paths to modify.

### 20.1 App Screenshot Agent Tool
**Status:** ⚠️ Infrastructure exists.
- **Files to add:** `core/agent/tool/builtin/ScreenshotTool.kt`
- **Registration:** Add `ScreenshotTool()` to `ToolRegistryFactory.baseTools()`.
- **Implementation:** `execute()` calls
  `WebsiteScreenshotService.captureScreenshot(appId, url)` → returns
  base64 via `ViewImageTool` display pipeline.

### 20.2 DNS Leak Test
**Status:** ❌ No infrastructure, trivial to add.
- **Files to modify:** `core/dns/DnsManager.kt` → add
  `testDnsLeak(complianceUrl: String): LeakTestResult`.
- **UI:** Add button in `MoreScreen.kt` or `DnsConfigCard.kt`.
- **Implementation:** Simple OkHttp GET to `https://dnsleaktest.com`
  → parse resolved IP/provider → compare with configured DoH.

### 20.3 In-App Rating Prompt
**Status:** ❌ Trivial to add.
- **Files to modify:** `core/webview/NativeBridge.kt` → add
  `@JavascriptInterface fun rateApp()`.
- **ApkConfig:** Add `ratingEnabled`, `ratingTriggerDays`,
  `ratingTriggerLaunches` to `WebViewBehaviorBlock`.
- **Export chain:** → `ApkConfigJsonFactory` → `WebViewShellConfig` →
  runtime in `ShellActivity.kt`.

### 20.4 Certificate Transparency Monitoring
**Status:** ⚠️ `CustomCaTrustStore.kt` exists; CT verification missing.
- **Files to modify:** `core/webview/CustomCaTrustStore.kt` → add
  `verifyCertificateTransparency(host, certChain)` that queries a CT
  log API.
- **Export chain:** Add `ctMonitoringEnabled` to `NetworkTrustConfig`
  → `ApkConfig` → `WebViewShellConfig.ctMonitoring` → runtime.

### 20.5 Per-Domain Cache Control
**Status:** ⚠️ Single `cacheEnabled` boolean exists.
- **Files to modify:** `data/model/WebApp.kt` → add
  `CacheRule(domainPattern, mode)`.
  `core/webview/WebViewManager.kt` → apply per-domain cache mode.
- **Export chain:** `WebViewBlock.cacheRules` → `ApkConfigJsonFactory`
  → `WebViewShellConfig.cacheRules` → `ShellModeManager`.
- **Tests:** Add to `WebViewConfigBooleanCoverageTest.kt` if booleans
  are added.

### 20.6 Certificate Pinning Per Domain
**Status:** ⚠️ `CustomCaTrustStore.kt` exists; no pinning rules.
- **Files to modify:** `data/model/NetworkTrustConfig.kt` → add
  `PinningRule(domainPattern, sha256Pins: List<String>)`.
- **Export chain:** → `ApkConfig` → `ApkConfigJsonFactory` →
  `WebViewShellConfig.pinningRules` → `ShellModeManager` →
  `CustomCaTrustStore.kt` / `HostnameVerifier`.

### 20.7 Export History Tracking
**Status:** ⚠️ `BuildLogger.kt` persists logs to disk.
- **Files to add:** `core/apkbuilder/ExportHistoryEntity.kt` (Room entity)
  + `ExportHistoryDao.kt`.
- **Populate from:** `BuildLogger.endLog()` callback.
- **UI:** `BuildHistoryScreen.kt` or tab in `BuildApkScreen.kt`.

### 20.8 Config Diff / Preview
**Status:** ⚠️ `LineDiff.kt` + `ChangesReviewCard.kt` exist.
- **Files to modify:** `ui/components/ChangesReviewCard.kt` →
  integrate into `BuildApkScreen.kt` as mandatory pre-export step.
  Diff `WebApp` configs using `LineDiff`.
- **Implementation:** `ConfigDiffUtil.compare(old, new)` →
  render with `LineDiff.render()`.

### 20.9 Rich Media Notifications
**Status:** ⚠️ FCM/WebSocket/Polling channels exist.
- **Files to modify:** `core/notification/NotificationFcmService.kt` →
  parse `image` and `action_buttons` → build
  `NotificationCompat.BigPictureStyle` / `BigTextStyle`.
- **ApkConfig:** Add `richNotificationsEnabled` to `NotificationBlock`.

### 20.10 Tor Proxy Integration
**Status:** ⚠️ SOCKS5 bridge exists.
- **Files to add:** `core/tor/TorCircuitBuilder.kt` — download Tor
  bundle, start Tor daemon, control via SOCKS port.
- **Integration:** `DnsManager.kt` Tor mode →
  `ProxyBlock.proxyType = "TOR"` → `LocalHttpToSocksBridge`.
- **Export chain:** `DnsShellConfig.proxyType` → `ShellServerLauncher`.

### 20.11 Module Performance Monitoring
**Status:** ❌ No infrastructure.
- **Files to add:** `core/extension/ModulePerformanceMonitor.kt` —
  instrument `ExtensionManager.executeModule()` with timing and memory.
- **UI:** `ModuleEditorScreen.kt` performance tab.

### 20.12 Per-Domain Isolation Override UI
**Status:** ⚠️ `IsolationConfig.kt` / `IsolationManager.kt` exist.
- **Files to modify:** `ui/components/IsolationConfigCard.kt` → add
  per-domain rule editor.
- **Export chain:** `IsolationRule` → `ApkConfig` →
  `IsolationShellConfig` → `IsolationScriptInjector.kt`.

### 20.13 Additional Browser Kernels (Opera/Kiwi/Brave)
**Status:** ⚠️ Samsung + Firefox already added; OPERA, KIWI, BRAVE missing.
- **Files to modify:** `core/kernel/KernelFlavor.kt` → add
  `BLINK_OPERA`, `BLINK_KIWI`, `BLINK_BRAVE` with UA strings.
- **Tests:** `TlsFingerprintTemplateTest.kt` (if fingerprinting changes).

### 20.14 Certificate Inspection Agent Tool
**Status:** ❌ No infrastructure.
- **Files to add:** `core/agent/tool/builtin/CertificateInspectTool.kt`.
- **Registration:** `ToolRegistryFactory.baseTools()`.
- **Implementation:** Fetch TLS chain via OkHttp → parse cert details
  (issuer, SANs, fingerprints).

### 20.15 Network Speed Test Agent Tool
**Status:** ❌ No infrastructure.
- **Files to add:** `core/agent/tool/builtin/NetworkSpeedTestTool.kt`.
- **Implementation:** Download/upload test against configurable endpoint.

### 20.16 Web-based Tool Agent (Built-in Browser)
**Status:** ❌ No infrastructure.
- **Files to add:** `core/agent/tool/builtin/WebBrowseTool.kt`.
- **What:** An agent tool that launches a headless/interactive WebView with
  `capture` + `setElement` + `click` + `getElement` + `evaluateJS` +
  `getConsoleLogs` + `navigate` + `waitForLoad` + `screenshot` methods.
  Enables the agent to interact with web pages, extract data, fill forms,
  take full-page screenshots, and scrape content via JavaScript execution.
- **Implementation:** Reuse the existing `SystemWebViewEngine` /
  `GeckoViewEngine` from `EngineManager.createEngine()`; inject a
  capture/control bridge via `WebViewManager`.

---

## 21. New Editor UI Screens Catalog (🆕)

Since the original suggestions.md was written, ~40 new Compose screens have
been added to the host app. Key ones:

### AI & Agent Screens
| Screen | Path | Purpose |
|--------|------|---------|
| `AgentScreen.kt` | `ui/agent/` | Full coding-assistant chat UI with SSE streaming |
| `AgentViewModel.kt` | `ui/agent/` | Orchestrates LLM providers, tools, sessions |
| `AiSettingsScreen.kt` | `ui/screens/` | Configure API keys, default model, AI features |
| `ModelPickerDialog.kt` | `ui/screens/` | Select LLM provider/model |
| `ModelCatalogSection.kt` | `ui/screens/` | Browse available models by capability |

### App Creation Flows (🆕)
| Screen | Path | Purpose |
|--------|------|---------|
| `CreateFrontendAppScreen.kt` | `ui/screens/` | Import frontend project from GitHub |
| `CreateGoAppScreen.kt` | `ui/screens/` | Go app creation wizard |
| `CreateNodeJsAppScreen.kt` | `ui/screens/` | Node.js app creation wizard |
| `CreatePhpAppScreen.kt` | `ui/screens/` | PHP app creation wizard |
| `CreatePythonAppScreen.kt` | `ui/screens/` | Python app creation wizard |
| `CreateWordPressAppScreen.kt` | `ui/screens/` | WordPress app creation wizard |
| `CreateHtmlAppScreen.kt` | `ui/screens/` | Static HTML app creation wizard |
| `CreateMediaAppScreen.kt` | `ui/screens/` | Media/image/video app creation wizard |
| `CreateGalleryAppScreen.kt` | `ui/screens/` | Gallery app creation wizard |
| `CreateMultiWebAppScreen.kt` | `ui/screens/` | Multi-site web app creation wizard |
| `CreateOfflinePackScreen.kt` | `ui/screens/` | Offline asset pack creation wizard |
| `BatchImportDialog.kt` | `ui/screens/` | Batch import multiple apps |

### Engine & Runtime Screens (🆕)
| Screen | Path | Purpose |
|--------|------|---------|
| `BrowserKernelScreen.kt` | `ui/screens/` | Select browser engine (System WebView / GeckoView) |
| `RuntimeDepsScreen.kt` | `ui/screens/` | Manage runtime dependencies |
| `LinuxEnvironmentScreen.kt` | `ui/screens/` | Linux env for fork+exec runtimes |
| `InstallProjectDepsCard.kt` | `ui/screens/` | Install Go/npm/PHP deps |

### App Management Screens (🆕)
| Screen | Path | Purpose |
|--------|------|---------|
| `AppModifierScreen.kt` | `ui/screens/` | Clone/reshape existing APKs |
| `PortManagerScreen.kt` | `ui/screens/` | View/manage allocated ports |
| `StatsScreen.kt` | `ui/screens/` | App usage stats and health |
| `PermissionConfigScreen.kt` | `ui/screens/` | Review/grant module permissions |
| `ModuleEditorScreen.kt` | `ui/screens/` | Edit module config items |
| `ModuleMarketScreen.kt` | `ui/screens/` | Browse/install modules |
| `ExtensionModuleScreen.kt` | `ui/screens/` | Manage installed extensions |
| `ExtensionModuleSelector.kt` | `ui/screens/` | Select modules for an app |
| `ExtensionSourceBrowser.kt` | `ui/screens/` | Browse extension sources |

### Configuration Cards (🆕)
| Card | Path | Purpose |
|------|------|---------|
| `FloatingWindowConfigCard.kt` | `ui/components/` | Overlay window config |
| `NotificationConfigCard.kt` | `ui/components/` | Push notification channels |
| `BgmCard.kt` / `BgmSelector.kt` | `ui/components/` | Background music config |
| `IsolationConfigCard.kt` | `ui/components/` | Request isolation config |
| `DnsConfigCard.kt` | `ui/components/` | DNS-over-HTTPS config |
| `StatusBarConfigCard.kt` | `ui/components/` | Status bar appearance |
| `EncryptionConfigCard.kt` | `ui/components/` | APK encryption settings |
| `AutoStartCard.kt` | `ui/screens/` | Auto-start on boot |
| `BackgroundRunConfigCard.kt` | `ui/screens/` | Background execution config |
| `DeviceDisguiseCard.kt` | `ui/screens/` | Device/browser disguise |
| `BrowserDisguiseConfigCard.kt` | `ui/components/` | Browser disguise config |
| `NodeExtensionsCard.kt` | `ui/screens/` | Node.js extensions |
| `PhpExtensionsCard.kt` | `ui/screens/` | PHP extensions |
| `PythonExtensionsCard.kt` | `ui/screens/` | Python packages |
| `RuntimePortConfigSection.kt` | `ui/screens/` | Runtime port config |
| `ChangesReviewCard.kt` | `ui/screens/` | Review config changes before export |

### Editor Components (🆕)
| Component | Path | Purpose |
|-----------|------|---------|
| `PreviewPane.kt` | `ui/` | Live WebView preview in the host |
| `PreviewSheet.kt` | `ui/` | Preview bottom sheet with controls |
| `ServerPreviewOverlays.kt` | `ui/` | Overlay controls for server-mode apps |
| `CodeRenderer.kt` | `ui/` | Syntax-highlighted code display |
| `CodeSnippetSelector.kt` | `ui/` | Select from built-in code snippets |

---

## Priority Matrix

| Priority | Feature Category | Status | Effort | Rationale |
|----------|-----------------|--------|--------|-----------|
| **P0** | WebRTC screen capture | ✅ | Low | Already done |
| **P0** | App screenshot Agent tool | ⚠️ | Trivial | `WebsiteScreenshotService` exists |
| **P0** | Config diff / preview tool | ⚠️ | Low | `LineDiff` + `ChangesReviewCard` exist |
| **P0** | Export template management | ✅ | Low | Already done |
| **P0** | 16KB alignment verification | ✅ | Low | `ElfAligner16kTest` exists |
| **P0** | Per-domain cache control | ❌ | Medium | Extends existing `cacheEnabled` |
| **P0** | Certificate pinning per domain | ❌ | Medium | Extends `CustomCaTrustStore` |
| **P1** | Additional browser kernels (Opera/Kiwi/Brave) | ⚠️ | Low | Just add enum entries + UAs |
| **P1** | Module sandboxing | ✅ | Low | Already done |
| **P1** | APK/AAB analyzer | ✅ | Low | `ApkAnalyzer` + `ApkArtifactVerifier` exist |
| **P1** | In-app rating prompt | ❌ | Trivial | New `NativeBridge.rateApp()` |
| **P1** | AAB export | ✅ | Low | Already done |
| **P1** | Chrome extension runtime | ✅ | Done | Already implemented |
| **P2** | Tor proxy integration | ⚠️ | High | SOCKS5 bridge exists; needs Tor bundle |
| **P2** | Ruby runtime | ❌ | High | New runtime, no infrastructure |
| **P2** | PostgreSQL embedded | ❌ | High | New native dependency |
| **P2** | Cloud build integration | ❌ | High | New backend infrastructure |
| **P2** | AI agent system | ✅ | Done | Already implemented |
| **P3** | Crash reporting (self-hosted) | ❌ | Medium | New endpoint config |
| **P3** | ASO recommendations | ❌ | Medium | Google Play API integration |
| **P3** | Certificate transparency | ⚠️ | Medium | Extends `CustomCaTrustStore` |

---

## Implementation Notes

1. **Config field drift:** Every new config field must flow through
   `WebApp` → `ApkConfig` (block + getter) →
   `ApkConfigJsonFactory.toShellPayload()` → `ShellModeManager`
   `ShellConfig` data class with matching `@SerializedName`. Run
   `python3 scripts/check_config_field_drift.py` or
   `./gradlew :app:checkConfigFieldDrift` to verify. Current drift:
   payload=440, shell=471, allowlist=48, shared=433.

2. **Editor card UI:** Follow the established card grammar in
   `CreateAppWebViewCards.kt` — copy the neighboring card patterns
   (toggle header, `WtaToggleRow`, `AnimatedVisibility` with
   `CardExpandTransition`, `WtaSectionDivider`). See AGENTS.md §12
   for the 7 hard rules learned across 4 PR iterations.

3. **Agent tools:** Each new tool must be registered in
   `ToolRegistryFactory.baseTools()` (or `planTools()` /
   `imageryTools()`), aligned with its `parametersSchema` via
   `SchemaDsl.jsonSchema {}`, and marked `isReadOnly()` correctly
   (read-only tools run without confirmation, write tools trigger
   `PermissionPrompter`).

4. **Shell template:** Changes to shell-synced code require rebuilding
   the template:
   `./gradlew :shell:assembleRelease :app:syncShellTemplateApk --no-configuration-cache`
   Content stability: template / entry identities must be content-stable
   (no mtime-based keys). Encrypted builds always force full rebuild.

5. **Native bridge:** New `NativeBridge` methods must be added to
   `NativeBridgeCapabilities` (`data/model/NativeBridgeCapabilities.kt`),
   exported in `ApkConfig` / `ApkConfigJsonFactory`, and exposed as
   `@JavascriptInterface` in the appropriate bridge class
   (`NativeBridge.kt`, `GeolocationBridge.kt`, `DownloadBridge.kt`,
   `MediaSessionBridge.kt`, `PrintBridge.kt`, `ShareBridge.kt`,
   `TranslateBridge.kt`).

6. **Engine selection:** `EngineType` (`core/engine/EngineType.kt`)
   determines whether the app uses System WebView or GeckoView. Engine
   download uses `NetworkModule.downloadClient` (extended timeouts).
   GeckoView native libs must be 16KB-aligned (`ElfAligner16k.kt`)
   for Android 15+ devices.

7. **Extension modules:** Module config items use `ModuleConfigItem` /
   `ModuleUiType` / `ConfigItemType` (defined in
   `core/extension/ExtensionModule.kt`). Permissions use the
   `ModulePermission` enum. Storage sync uses
   `ExtensionStorageSync.kt` (areas: LOCAL, SYNC, MANAGED).

8. **Agent-to-app export:** Agent sessions can be exported as apps via
   `SaveSessionAsAppUseCase.kt` or as modules via
   `SaveSessionAsModuleUseCase.kt`. The `SessionArtifactDetector.kt`
   auto-detects JS files, user scripts, and Chrome extensions in the
   session workspace and classifies them for export.

9. **Test coverage:** Run the following focused test suites after
   nearby edits:
   - `WebViewConfigBooleanCoverageTest` (config drift)
   - `ConfigRoundTripSentinelTest` (export round-trip)
   - `ApkBuildCacheTest` (incremental builds)
   - `AdBlockerHostRuntimeTest`, `AdBlockExportWiringTest`
   - `PortManagerTest`, `BuildInputPreflightTest`
   - `GoBuildEnvironmentTest`, `RuntimePermissionSyncTest`
   - `GeckoViewEngineSeedTest`, `EngineFileManagerTest`
   - `ChromeExtensionParserTest`, `ChromeExtensionPolyfillTest`
   - `ApkToAabAssemblerTest`, `ApkArtifactVerifierTest`
   - `EncryptionConfigTest`, `EncryptionConfigExtendedTest`
