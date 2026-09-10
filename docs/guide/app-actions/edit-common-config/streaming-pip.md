# Streaming PiP

Auto-detects video playback — including HLS, DASH, YouTube embeds, and plain MP4 — and
enters Picture-in-Picture automatically so the audio/video keeps playing while the user
navigates away. A manual toggle panel is injected into the page for per-video control
when auto-entry is off or misses a stream.

This is a [module](/guide/app-actions/edit-common-config/extension-modules) rather than a
single `WebViewConfig` toggle: it ships as a **built-in module** (`extension-streaming-pip`),
is available in the **module market** as `wta-stream-detect-pip`, and is offered as a
**config template** (`template-stream-detect-pip`) when you create a new module.

## How it works

1. **Detection** — the module scans `<video>` elements, `<iframe>`/embed sources, and
   `MediaSource`/`MSE` streams. Source type is classified by URL/extension:
   - `.m3u8` → HLS
   - `.mpd` → DASH
   - `youtube.com` / `youtu.be` → YouTube (iframe/API-backed)
   - other `.mp4`/`.webm` → generic video
2. **Auto-enter** (on by default) — when playback starts on a qualifying source, the
   module calls Android's PiP mode. On API 28 (the generated-app target), PiP is
   entered via `enterPictureInPictureMode()`; there is no runtime permission gate.
3. **Manual toggle panel** — a small overlay badge is appended to each detected video.
   Clicking it toggles PiP on/off for that player, regardless of the auto-entry setting.
   Two global buttons control every player: **Start all PiP** and **Stop all PiP**.

The built-in and market variants behave identically; the template additionally exposes
the two config items below as editable fields when you generate a module from it.

## Getting it

- **Built-in:** open **More → Browser & Interface → Extension Modules → Video** and
  enable **Streaming PiP Detector**. No network access required.
- **Market:** open the **Module Market** inside the app, find
  `wta-stream-detect-pip`, and install it. The same module is also surfaced inside the
  in-app docs.

## Config template options

Available when creating a module from the `template-stream-detect-pip` template (or when
editing the equivalent fields on the built-in/module variant where supported):

- **Auto-entry** (`autoEntry`) — when `true`, qualifying video playback enters PiP
  automatically. Default: `true`.
- **Stream types** (`streamingTypes`) — filter which sources trigger detection:
  `all` / `hls` / `dash` / `mp4`. Default: `all`.

## PiP panel

The injected panel is scoped per video player and shows:

- the detected stream type (HLS / DASH / YouTube / MP4)
- a toggle to enter/exit PiP for that player
- the global **Start all PiP** / **Stop all PiP** actions at the top of the page

When two or more players are eligible, only one is active in PiP at a time; the previous
player is paused. Tapping the badge re-enters PiP for the selected player.

## Notes

- Android PiP is a system-level mode; the generated app targets API 28 and declares the
  `PICTURE_IN_PICTURE` feature in its manifest. No additional permission prompt is shown.
- YouTube detection relies on the iframe embed surface. If a site wraps the player in a
  cross-origin iframe the module cannot reach, auto-entry falls back to the manual badge.
- For non-YouTube, non-HLS/DASH sources (plain `.mp4`), detection is based on the `<video>`
  element and fires reliably.
- If PiP fails to engage on a given page, disable **Auto-entry** and use the manual badge.
