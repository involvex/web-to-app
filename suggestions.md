# Feature Suggestions for web-to-app

A comprehensive analysis of the existing codebase and feature opportunities
organized by domain. Each suggestion references the relevant existing
architecture paths so implementers can trace the model → export → shell →
runtime chain required by the project conventions.

---

## 1. WebView Features

### 1.1 WebRTC Screen Capture (`getDisplayMedia`)
**Status:** Not implemented. The `NativeBridge` exposes `saveImageToGallery`,
`shareImage`, etc., but there is no screen-capture bridge.

- **What:** Add a `captureScreen()` / `startScreenCapture()` method to
  `NativeBridge.kt` that uses `MediaProjection` to capture the WebView content
  and deliver frames or video to the page via a callback URL or blob.
- **Model → Shell → Runtime:** Add a `captureScreen` capability flag in
  `NativeBridgeCapabilities`, expose in `WebViewConfig`, export it through
  `ApkConfigJsonFactory`, and gate it in `NativeBridge.kt`.
- **Editor UI:** Add a toggle in `CreateAppWebViewCards.kt`.

### 1.2 Additional Browser Kernel Flavors
**Paths:** `core/kernel/`, `core/appearance/DisguiseConfig.kt`

- **What:** The current `KernelFlavor` enum includes `SYSTEM_DEFAULT`,
  `CHROME`, `EDGE`, `SAFARI`, etc. Add `OPERA`, `SAMSUNG_INTERNET`, `KIWI`,
  `BRAVE`, and `FIREFOX` as selectable kernel flavors so the app can pretend
  to be a specific mobile browser, not just a generic desktop UA.
- **Export chain:** `KernelFlavor` → `ApkConfig` / `WebViewBlock` →
  `ApkConfigJsonFactory.webViewConfigPayload()` → `WebViewShellConfig` →
  `ShellModeManager` → runtime UA/kernel selection.

### 1.3 HTTP/3 (QUIC) Support
**Paths:** `core/network/`, `core/webview/`

- **What:** Android WebView on newer System WebView builds supports HTTP/3.
  Add a `webViewConfig.http3Enabled` flag and a network-security-config
  override that allows HTTP/3 for the app's domain.
- **Note:** This is mostly a manifest/network-config concern; the heavy
  lifting is in `NetworkTrustConfig`.

### 1.4 Per-Domain Cache Control Policies
**Paths:** `data/model/WebApp.kt`, `core/webview/WebViewManager.kt`
- **What:** Currently `cacheEnabled` is a single boolean. Add per-domain
  cache rules (e.g., "cache for example.com, no-cache for api.example.com")
  with `CACHE_FIRST`, `NETWORK_FIRST`, `NO_CACHE`, `OFFLINE_ONLY` modes.
- **Use case:** PWAs that want aggressive caching for static assets but
  real-time fetching for API endpoints.

### 1.5 Custom Certificate Pinning Per Domain
**Paths:** `data/model/NetworkTrustConfig.kt`, `core/webview/CustomCaTrustStore.kt`
- **What:** Extend `NetworkTrustConfig` with a `certificatePinningRules:
  List<PinningRule>` where each rule maps a domain pattern to a SHA-256
  pin. Use `okhttp3.CertificatePinner` or a custom `HostnameVerifier` in
  the WebView's `WebViewClient`.

### 1.6 Expanded TLS Fingerprint Templates
**Paths:** `core/tls/`
- **What:** The `tlsFingerprintTemplate` already supports `CHROME_131`,
  `FIREFOX_133`, `SAFI_SAFARI_18`. Add mobile browser templates
  (Chrome Android, Safari iOS, Samsung Internet) and per-platform
  variations (macOS, Windows) so the TLS fingerprint more closely matches
  the spoofed device.

---

## 2. Local Server Features

### 2.1 Ruby Runtime Support
**Paths:** `core/ruby/` (does not exist yet; mirror `core/nodejs/`, `core/php/`)
- **What:** Add a Ruby runtime (JRuby or MRI cross-compiled for Android
  ARM64) that can serve Rails or Sinatra apps via a local HTTP server,
  similar to how `GoAppConfig` / `PythonAppConfig` work.
- **Export chain:** New `RUBY_APP` AppType → `RubyShellConfig` →
  `ApkConfig` → exported server-mode app.

### 2.2 MariaDB / MySQL Embedded
**Paths:** `core/wordpress/` (WordPress uses PHP+SQLite)
- **What:** Bundle MariaDB (stripped-down `libmariadb.so` for Android) so
  WordPress, PHP apps, or custom apps can use MySQL instead of SQLite.
  Expose a simple `MySQLConfig` block with host/port/database.

### 2.3 Redis Embedded for Caching
**Paths:** `core/linux/`, `core/engine/`
- **What:** Bundle a stripped-down `redis-server` native binary (or use a
  pure-Kotlin in-memory key/value store with Redis protocol support) so
  generated apps can use Redis for session storage or caching.

### 2.4 PostgreSQL Embedded
**Paths:** `core/linux/`
- **What:** Bundle `libpg` / `postgres` binary for Android to support
  Django, Rails, and PHP apps that need PostgreSQL.

### 2.5 Static File Server Customization
**Paths:** `core/webview/LocalHttpServer.kt`
- **What:** Add configurable MIME-type overrides, custom headers (e.g.,
  `Cache-Control`, `Content-Security-Policy`), directory listing toggles,
  and index-file fallback so the local HTTP server can be fine-tuned
  per app.

### 2.6 Rust Native Binary Support
**Paths:** `core/linux/`, `core/golang/`
- **What:** Allow users to include a pre-compiled Rust `.so` or binary
  that the shell loads and execs, similar to the Go exec loader pattern
  in `libgo_exec_loader.so`.

### 2.7 Erlang / Elixir Support
**Paths:** mirror `core/golang/` structure
- **What:** Bundle BEAM (Erlang VM) for Elixir/Phoenix apps. Phoenix
  is a popular Elixir web framework; supporting it extends the server
  runtime ecosystem.

---

## 3. Network & Security

### 3.1 Tor Proxy Integration
**Paths:** `core/dns/`, `core/webview/`
- **What:** Add a "TOR" proxy mode that routes WebView HTTP traffic
  through a local SOCKS5 proxy connected to Tor (or a Tor-on-Android
  helper). The `LocalHttpToSocksBridge` already provides the SOCKS5
  bridge infrastructure; just add a Tor circuit builder / control.

### 3.2 Certificate Transparency Monitoring
**Paths:** `core/webview/CustomCaTrustStore.kt`, `core/crypto/`
- **What:** When a custom CA is configured, log or alert if the server's
  certificate is not present in a CT log. Use a lightweight CT verifier
  (or a remote API) to validate certificate transparency.

### 3.3 DNS Leak Protection Test
**Paths:** `core/dns/`, `ui/screens/`
- **What:** Add a "Test DNS Leak" button in the DNS config card that
  fetches `https://dnsleaktest.com` or a similar endpoint and reports
  whether the resolved IP/provider matches the configured DoH provider.

### 3.4 Per-Domain CA Management
**Paths:** `data/model/NetworkTrustConfig.kt`
- **What:** Extend `CustomCaCertificate` with a `domainPatterns:
  List<String>` field so a CA can be scoped to specific domains only,
  rather than trusting it globally.

---

## 4. Privacy & Anti-Detection

### 4.1 Advanced TLS Version Restriction UI
**Paths:** `core/webview/`, `ui/components/IsolationConfigCard.kt`
- **What:** Add a UI to restrict the minimum TLS version (e.g., TLS 1.3
  only, or TLS 1.2+) for the generated app, and to disable deprecated
  protocols.

### 4.2 Per-Domain Isolation Rules
**Paths:** `core/privacy/IsolationConfig.kt`
- **What:** Allow users to specify domains where isolation should be
  relaxed or tightened (e.g., "strict isolation for login.example.com,
  relaxed for cdn.example.com").

### 4.3 WebRTC IP Leak Test
**Paths:** `core/webview/`, `ui/components/`
- **What:** Add a tool (host-side or generated-app-side) that checks
  whether the app's WebRTC implementation is leaking local IPs, and
  reports the result.

### 4.4 Headless Browser Detection Bypass
**Paths:** `core/appearance/DisguiseConfig.kt`
- **What:** The browser disguise already covers many headless-detection
  vectors. Add specific patches for newer detection techniques:
  `navigator.webdriver`, `Chrome` runtime object, automation flags,
  `navigator.plugins` array, and `permissions` API spoofing.

---

## 5. Agent Tool System (54 tools → expand)

**Paths:** `core/agent/tool/builtin/`, `core/agent/tool/ToolRegistryFactory.kt`

### 5.1 App Screenshot Tool
- **What:** `CaptureAppScreenshotTool` that renders the hosted WebView
  to a bitmap and returns it as a base64 image. Uses the existing
  `WebViewManager` capture path.

### 5.2 Network Inspection Tool
- **What:** `InspectNetworkRequestsTool` that starts a lightweight
  network interceptor (or reads WebView console logs) and returns
  captured request/response headers, status codes, and timing.

### 5.3 Database Export Tool
- **What:** `ExportAppDatabaseTool` that extracts the generated app's
  internal SQLite database (cookies, localStorage, app data) and
  packages it for download. Uses `context.getDatabasePath()` introspection.

### 5.4 Performance Profiling Tool
- **What:** `ProfileAppPerformanceTool` that instruments the WebView
  for a configurable duration and returns FPS, memory usage, and load
  timing metrics.

### 5.5 Remote Control Tool
- **What:** `ControlWebViewTool` that sends synthetic touch/click events,
  key events, and JavaScript evaluation commands to the running WebView.

### 5.6 Crash Log Inspection Tool
- **What:** `GetCrashLogsTool` that reads the app's crash log buffer
  (from the generated-app or from the host's own crash files) and
  returns structured crash data.

### 5.7 Permission Audit Tool
- **What:** `AuditPermissionsTool` that lists all declared permissions
  in a generated app's manifest and flags ones that are declared but
  not used.

### 5.8 Certificate Inspection Tool
- **What:** `InspectCertificateTool` that fetches the TLS certificate
  chain for a given URL and displays the cert details (issuer, valid
  dates, SANs, fingerprints).

### 5.9 Network Speed Test Tool
- **What:** `TestNetworkSpeedTool` that performs a download/upload
  bandwidth test against a configurable endpoint and reports Mbps.

### 5.10 Export Queue / History Tool
- **What:** `ListExportJobsTool` that queries the export job queue
  (from `ApkBuilder`) and returns the status of recent and pending
  export jobs.

### 5.11 App Backup/Restore Tools
- **What:** `ExportAppBackupTool` / `ImportAppBackupTool` that
  leverages `DataBackupManager` to create and restore a full backup
  (config + resources + extension modules) as a single archive.

### 5.12 Shell Template Management Tool
- **What:** `RebuildShellTemplateTool` that triggers
  `:shell:assembleRelease :app:syncShellTemplateApk` from the agent
  loop, and `InspectShellTemplateTool` that reports the template's
  current hash and sync status.

---

## 6. Export Pipeline

**Paths:** `core/apkbuilder/`

### 6.1 Incremental Asset Pack Updates
- **What:** The current `ApkBuildCache` supports `FULL` /
  `CONTENT_OVERLAY` / `REUSE_UNSIGNED`. Add a "smart overlay" mode that
  only re-exports the assets that changed since the last build, rather
  than reprocessing the entire APK.

### 6.2 Build Artifact Metadata / SBOM
- **What:** Generate a Software Bill of Materials (SBOM) for each
  exported APK that lists all bundled runtimes, extensions, and native
  libraries with their versions and CVE scan results.

### 6.3 Export Result History Tracking
- **What:** Persist each export attempt (success/failure, timestamp,
  size, duration, config hash) in a local Room table so the user can
  see build history and retry failed builds.

### 6.4 Cloud Build Integration
- **What:** Add an option to offload the APK build to a cloud
  worker (the host can upload the config + resources via a signed
  URL). Useful for users whose devices don't meet the build
  environment requirements.

### 6.5 Export Scheduling / Queue
- **What:** Allow the user to schedule exports (e.g., nightly builds
  with fresh ad-filter updates) or queue multiple exports. The
  `ApkBuilder` would need a simple job queue backed by Room.

### 6.6 Export Preview / Diff
- **What:** Before building, show a diff of what changed between the
  current config and the last exported config (which blocks, which
  runtime, which modules, etc.).

---

## 7. Editor UI

**Paths:** `ui/screens/CreateAppWebViewCards.kt`, `ui/components/`

### 7.1 Visual WebView DOM Inspector
- **What:** Add a devtools-like overlay in the host preview that
  allows inspecting the rendered DOM tree, highlighting elements, and
  viewing computed styles — similar to browser devtools but embedded
  in the Compose preview.

### 7.2 Real-Time Config Change Preview
- **What:** When the user toggles a setting (e.g., fullscreen,
  orientation, status bar color), immediately reflect the change in
  the live preview pane without requiring a full reload.

### 7.3 Config Comparison / Diff Tool
- **What:** A "Compare" view that shows a side-by-side diff of two
  app configs (or a config vs. default) so users can see exactly what
  changed.

### 7.4 Bulk Edit (Multi-App Config)
- **What:** Allow selecting multiple apps and applying a shared config
  change (e.g., update the ad-block subscriptions, or change the
  splash across all apps in a category).

### 7.5 Theme Preview Before Building
- **What:** Render a live preview of the app's splash screen, status
  bar, and error page with the selected theme colors before exporting.

### 7.6 In-App Code Snippet Library
- **What:** A reusable library of JavaScript snippets (analytics
  injection, polyfill patches, custom CSS hooks) that can be
  drag-and-dropped into the "inject scripts" field.

### 7.7 App Icon Adaptive Icon Customization
- **What:** A visual editor for creating adaptive icons (foreground +
  background layers) with shape presets, shadows, and color filters.

---

## 8. Module System

**Paths:** `modules/`, `core/extension/`

### 8.1 Module Dependency Management
- **What:** Allow modules to declare dependencies on other modules
  (a `dependencies: List<String>` field in the module manifest) and
  auto-install them.

### 8.2 Module Version Pinning
- **What:** Allow users to pin a module to a specific version rather
  than always using the latest, so a breaking update doesn't break
  their app.

### 8.3 Module Sandboxing Controls
- **What:** Add a `sandboxLevel` field to modules that controls
  which `NativeBridge` capabilities a module can access (e.g., a
  "safe" module can only access clipboard/toast, a "full" module
  gets everything).

### 8.4 Module Performance Monitoring
- **What:** Instrument injected modules to measure execution time,
  memory, and errors, and display a per-module performance dashboard.

### 8.5 Module Sharing via QR Code
- **What:** Add a "Share as QR" button that encodes the module config
  (code, CSS, URL matches, config values) into a scannable QR code.

### 8.6 User Script Import from File
- **What:** Allow importing/exporting user scripts (Tampermonkey /
  Greasemonkey `.user.js` format) as files, not just via the built-in
  code editor.

---

## 9. Internationalization

**Paths:** `core/i18n/`, `data/model/`

### 9.1 Expand Host UI Languages
- **Status:** 10 languages are supported for the host UI (Chinese,
  English, Arabic, Portuguese, Spanish, French, German, Russian,
  Japanese, Korean).
- **What:** Add Italian, Dutch, Polish, Hindi, Thai, Vietnamese, and
  Turkish to the host UI. The `TranslateLanguage` enum already
  includes many of these for the in-app translation feature.

### 9.2 RTL Layout Testing Tool
- **What:** A debug toggle that forces RTL layout rendering so
  translators and designers can verify Arabic/Hebrew layouts without
  changing the device language.

### 9.3 Per-App Language Override
- **What:** Allow setting a different UI language for individual
  generated apps, independent of the host app language.

---

## 10. App Experience Features

### 10.1 App Widgets
**Paths:** `ui/shell/`
- **What:** Allow generated apps to embed Android App Widgets
  (e.g., a weather widget, a music controller) alongside the WebView.
- **Export chain:** `WidgetConfig` → `ApkConfig` → shell layout →
  runtime widget provider.

### 10.2 Dynamic App Shortcuts
**Paths:** `core/app/`
- **What:** Generate dynamic shortcuts (Android 7.1+) that deep-link
  to specific URLs or pages within the web app.

### 10.3 In-App Purchases (IAP) Bridge
**Paths:** `core/webview/NativeBridge.kt`
- **What:** Add `NativeBridge.launchBillingFlow(productId)`,
  `queryPurchases()`, `consumePurchase()` that wraps the Play Billing
  library, so web apps can offer premium features.

### 10.4 App Rating Integration
- **What:** Add a `rateApp()` NativeBridge method and an editor
  toggle for "Show rating prompt after N days / N launches".

### 10.5 Notification Categories Management
**Paths:** `core/notification/`
- **What:** Allow users to define multiple notification channels
  (categories) for the generated app, each with its own sound,
  vibration, and importance level.

### 10.6 Custom Notification LED Color
- **What:** In the notification config, add an LED color and blink
  pattern for devices that have a notification LED.

### 10.7 App Shortcuts (Static)
- **What:** Allow defining static shortcuts in the app manifest that
  appear in the Android app launcher long-press menu.

### 10.8 Push Notification Rich Media
**Paths:** `core/notification/`
- **What:** Extend the FCM notification system to support image,
  big-text, and action-button rich notifications.

---

## 11. Analytics & Monitoring

**Paths:** `core/stats/`, `core/logging/`

### 11.1 In-App Analytics Dashboard
- **What:** A built-in analytics dashboard that tracks page views,
  session duration, crash counts, and bandwidth for each generated
  app. Data stored locally (opt-in).

### 11.2 Crash Reporting (Self-Hosted)
- **What:** A lightweight crash reporter that sends crash details to
  a user-configured endpoint (similar to the notification polling
  system). No third-party SDKs.

### 11.3 Network Usage Monitoring
- **What:** Track and display per-app network usage (mobile vs.
  WiFi) with a breakdown by asset type (HTML, CSS, JS, images, video).

### 11.4 Performance Metrics Dashboard
- **What:** Collect and display FPS, memory usage, and load time
  metrics for the host preview and for exported apps (when the user
  grants the appropriate permission).

---

## 12. Developer Tools

**Paths:** `core/apkbuilder/`, `ui/`

### 12.1 APK Analyzer
- **What:** A built-in APK analyzer that shows the manifest,
  resources, native libraries, permissions, and signing info of a
  generated APK or AAB before sharing.

### 12.2 Manifest Viewer
- **What:** Render the generated app's `AndroidManifest.xml` in a
  friendly tree view, highlighting permissions and intent filters.

### 12.3 Asset Explorer
- **What:** Browse the assets of a generated app (icons, splash,
  HTML files, extension modules) in a file-tree view.

### 12.4 Export Template Management
- **What:** Save and manage named "export templates" — reusable
  bundles of common config (ad-block lists, browser disguise preset,
  notification config, etc.) that can be applied to new apps
  quickly. (Note: `ConfigTemplateStore` already exists for common
  config templates; extend it to cover export-specific settings.)

### 12.5 Code Snippet Library (Host-Side)
- **What:** A local library of reusable JavaScript snippets, CSS
  snippets, and NativeBridge call examples that can be inserted
  into any app's inject-scripts field.

### 12.6 Dependency Analysis for Export
- **What:** Before building, analyze the web app's dependencies
  (external JS/CSS resources, fonts, images) and warn if any are
  missing from the static asset pack or if the total size exceeds
  a threshold.

---

## 13. Cross-Platform & Distribution

### 13.1 iOS Export (Future)
**Note:** Currently Android-only.
- **What:** Investigate a build pipeline that produces an iOS app
  (WKWebView-based) with equivalent features. This is a major
  architectural undertaking but would be a significant differentiator.

### 13.2 Desktop Export
**Paths:** `core/export/`
- **What:** Generate a desktop Electron or Tauri app from the same
  config, so web apps can be packaged for Windows, macOS, and Linux
  with the same settings.

### 13.3 Firebase App Distribution Integration
**Paths:** `core/playstore/`
- **What:** After building an AAB, optionally upload it to a Firebase
  App Distribution track for internal testing.

### 13.4 GitHub Actions Integration
- **What:** Export a GitHub Actions workflow YAML that replicates
  the app's export config, so CI can build APKs automatically on
  push.

### 13.5 App Store Optimization (ASO)
**Paths:** `ui/screens/PlayStoreScreen.kt`
- **What:** Add ASO recommendations (keyword suggestions,
  title/description length analysis, icon/screenshot guidance)
  based on the app's category and target market.

---

## 14. Testing & CI

### 14.1 Shell Config Drift Coverage Tests
- **What:** Ensure every new boolean field in `WebViewConfig` is
  added to `flipAllBooleans()` in
  `WebViewConfigBooleanCoverageTest.kt` to catch preview/export
  divergence.

### 14.2 Export Round-Trip Tests
- **What:** Add tests that serialize a `WebApp` → `ApkConfig` → JSON
  → `ShellConfig` and assert every field survives the round-trip,
  catching field-name drift between `ApkConfigJsonFactory` and
  `ShellModeManager`.

### 14.3 Agent Tool Schema Alignment Tests
- **What:** Tests that verify each Agent tool's `parametersSchema`
  matches the fields read by its `execute()` method, so the LLM
  can always pass every available parameter.

---

## Priority Matrix

| Priority | Feature Category | Rationale |
|----------|-----------------|-----------|
| **P0** | WebRTC screen capture, App screenshot tool, Config diff tool, Export template management, Android 15 16KB alignment verification | High user impact, moderate effort, fills existing gaps |
| **P1** | Additional browser kernels, Module sandboxing, Per-domain cache control, APK analyzer, In-app rating | Moderate effort, clear user value |
| **P2** | Tor integration, Ruby runtime, iOS export, Desktop export, Cloud build | High effort / strategic / platform expansion |
| **P3** | Crash reporting, ASO, Certificate transparency monitoring | Nice-to-have polish |

---

## Implementation Notes

1. **Config field drift:** Every new config field must flow through
   `WebApp` → `ApkConfig` (block + getter) →
   `ApkConfigJsonFactory.toShellPayload()` → `ShellModeManager`
   `ShellConfig` data class with matching `@SerializedName`. Run
   `./gradlew :app:checkConfigFieldDrift` to verify.

2. **Editor card UI:** Follow the established card grammar in
   `CreateAppWebViewCards.kt` — copy the neighboring card patterns
   (toggle header, `WtaToggleRow`, `AnimatedVisibility` with
   `CardExpandTransition`, `WtaSectionDivider`).

3. **Agent tools:** Each new tool must be registered in
   `ToolRegistryFactory.baseTools()`, aligned with its `parametersSchema`,
   and marked `isReadOnly()` correctly (read-only tools run without
   confirmation, write tools trigger `PermissionPrompter`).

4. **Shell template:** Changes to shell-synced code require rebuilding
   the template:
   `./gradlew :shell:assembleRelease :app:syncShellTemplateApk`

5. **Native bridge:** New `NativeBridge` methods must be added to
   `NativeBridgeCapabilities`, exported in `ApkConfig` /
   `ApkConfigJsonFactory`, and exposed as `@JavascriptInterface` in
   `NativeBridge.kt`.
