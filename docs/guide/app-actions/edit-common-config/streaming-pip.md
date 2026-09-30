# Streaming PiP

Auto-detects video playback — including HLS, DASH, YouTube embeds, and plain MP4 — and
enters Picture-in-Picture automatically so the audio/video keeps playing while the user
navigates away. A toolbar panel lists every detected video for per-video control
when auto-entry is off or misses a stream.

This ships as a **built-in plugin** (`builtin-streaming-pip`) rather than a single
`WebViewConfig` toggle. Open it from the page toolbar; no network access required.

## How it works

1. **Detection** — the plugin watches `<video>` elements (including ones added later by
   the page or SPA navigation). Source type is classified by URL/extension:
   - `.m3u8` → HLS
   - `.mpd` → DASH
   - `youtube.com` / `youtu.be` → YouTube (iframe/API-backed)
   - other `.mp4`/`.webm` → generic video
2. **Auto-enter** (on by default) — when playback starts on a qualifying source, the
   plugin enters PiP via HTML5 `requestPictureInPicture()`, falling back to
   `NativeBridge.enterPiP()` where the WebView surface needs it. On API 28 (the
   generated-app target), PiP is entered via `enterPictureInPictureMode()`; there is no
   runtime permission gate. The auto-entry choice persists per site.
3. **Panel** — the toolbar panel shows each detected video with its stream type and a
   toggle button, plus global **Start all PiP** / **Stop all PiP** actions and an
   **Auto PiP** switch.

## PiP panel

The panel lists:

- the detected stream type (HLS / DASH / YouTube / MP4) and video title for each player
- a toggle to enter/exit PiP for that player
- global **Start all PiP** / **Stop all PiP** actions
- the **Auto PiP** switch (persisted; applies to later playbacks on the site)

When two or more players are eligible, only one is active in PiP at a time.

## Notes

- Android PiP is a system-level mode; the generated app targets API 28 and declares the
  `PICTURE_IN_PICTURE` feature in its manifest. No additional permission prompt is shown.
- YouTube detection relies on the iframe embed surface. If a site wraps the player in a
  cross-origin iframe the plugin cannot reach, auto-entry falls back to the manual toggle.
- For non-YouTube, non-HLS/DASH sources (plain `.mp4`), detection is based on the `<video>`
  element and fires reliably.
- If PiP fails to engage on a given page, turn **Auto PiP** off in the panel and use the
  per-video toggle.
