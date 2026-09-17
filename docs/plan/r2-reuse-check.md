# R2 — Reuse check (open-source candidates per part)

Date of research: 2026-09-16. Target: Windows 10 Mobile-style shell for Android, Kotlin + Jetpack Compose, sideloaded on Galaxy S25 Ultra, no root. Owner may sell it later, so copyleft is flagged in every row.

How to read this file:
- `License` = what GitHub's license detector reports for the repo's LICENSE file (`license.name` from `api.github.com/repos/<owner>/<repo>`) unless a row says "LICENSE file read directly". "none detected" means GitHub found no LICENSE file → default copyright, no reuse right.
- `Pushed` = `pushed_at` from the same API call (last push to any branch), ISO date. Stars are `stargazers_count` the same day.
- Verdicts: **REUSE** = pull code or depend on it. **REFERENCE** = read it, re-implement ours. **SKIP** = reason given.
- "Code read" lines name the files actually fetched and read (raw.githubusercontent.com) — anything not listed there was only seen via README/API.
- Anything not verified is marked **UNVERIFIED**. Nothing in this file asserts that something does not exist; where searches came up empty the queries are listed.
- Search surfaces used: GitHub (topics pages + API), F-Droid search, GitLab search (blocked: HTTP 403 on `gitlab.com/search?search=windows+phone+launcher` — not covered), XDA (blocked: HTTP 403 on `xdaforums.com/tags/windows-phone/` — only reachable via web-search snippets).

---

## Cross-cutting copyleft summary

| Trap | Where it bites | Consequence if we ship closed-source |
|---|---|---|
| **GPL-3.0** on every mature dialer/SMS app (Fossify Phone, Fossify Messages, QUIK, Deku, Koler, an1ndra/Messages, Secure-Dialer) | Part 4 | Copying any of their code makes the whole APK GPL. Only permissive options found are small/young (OpenDialer Apache-2.0, SMS-TECH Apache-2.0, simple-phone MIT). |
| **GPL-3.0** on HeliBoard, Unexpected Keyboard, OpenBoard | Part 3 | Do not fork them. FlorisBoard (Apache-2.0) and AnySoftKeyboard (Apache-2.0) are the permissive ones. |
| **GPL-3.0** on HayaiTTS and on the Piper engine (`piper1-gpl`) | Part 5 | Do not link them. Kokoro-82M (Apache-2.0) and Piper *voice files* via sherpa-onnx (Apache-2.0 runtime; per-voice licences vary) avoid it. |
| **GPL-3.0** on Sayboard (Vosk voice IME) | Parts 3/5 | Reference only. |
| **Custom model licences**: Gemma 3n ("gemma" terms, gated), Llama 3.2 (Llama 3.2 Community License, gated), Moonshine legacy non-English models (Moonshine Community License, revenue cap) | Part 6 / Part 5 | Prefer Apache-2.0 models: Gemma 4 E2B/E4B, Qwen3, SmolLM3; MIT: Phi-4-mini, Moonshine English/streaming. |
| **No LICENSE file** on MetroLauncherAndroid, Factor Launcher, SuperShade, thbecker overlay sample, aug16vcc Compose-overlay sample, android-smsmms | Parts 1/2/4 | Default copyright — read, do not copy. |
| **Trademarks** (not licences): "Windows Phone", "Metro", "Segoe", "Cortana" are Microsoft marks (MangoTile's README says so explicitly); Selawik fonts are OFL-1.1 with a Reserved Font Name. | Naming/branding | Pick a non-Microsoft product name and assistant name before any pitch. |

---

## Part 1 — Start screen, live tiles, edit mode, folders, tile animations

### 1.1 Diffechento/MangoTile — Jetpack Compose WP8 UI kit
- URL: https://github.com/Diffechento/MangoTile · Maven Central `io.github.diffechento:metro:1.0.1`
- License: **MIT License** (GitHub detector; README says "MIT — permits building closed and commercial applications while retaining copyright notice"). Bundled Selawik fonts are under `licenses/Selawik-OFL-1.1.txt` (SIL OFL 1.1, Reserved Font Name).
- Language/UI: Kotlin, Jetpack Compose (Compose exposed as `api`), minSdk 26, compileSdk 36.
- Pushed: 2026-09-14 · created 2026-07-27 · stars 3, forks 1 (very young, one author).
- Code read: `metro/src/main/java/com/metrocompose/MetroComponents.kt` (~600 lines), `MetroMotion.kt` (~420 lines), `MetroReorder.kt` (~400 lines), plus full file tree and README.
  - `MetroComponents.kt`: `Tile(label, color, w: Dp, h: Dp, glyph, modifier, onClick)` — a **static** tile (label + glyph, no live content, no flip/cycling). Also `MetroPage`, `Pivot`, `AppBar`, `AppBarButton`, `MetroToggle`, `ListRow`, `SettingRow`, `TransportButton`.
  - `MetroMotion.kt`: `Modifier.metroTilt(interactionSource, maxTiltDegrees=10f, pressedScale=0.94f)` (the WP press-tilt), `Modifier.metroGrowIn(key, edge, durationMillis=360)`, `Modifier.metroSlideIn(...)`, `MetroSwap` (turnstile-style out/pause/in), `MetroCrossfade` (crossfade with parallax drift).
  - `MetroReorder.kt`: `rememberMetroReorder(listState, rowCount, onMove)` + `Modifier.metroReorderRow(state, index, enabled, onHeldStill)` — long-press lift + drag-to-reorder for **LazyList rows** (1-D), with edge auto-scroll. Not a 2-D tile grid.
  - Other files present: `MetroPanorama.kt`, `MetroPivot.kt`, `MetroLongList.kt` (LongListSelector + jump grid), `MetroPageSwipe.kt`, `MetroEdgeScroll.kt`, `MetroGestures.kt`, `MetroNav.kt`, `MetroTransitions.kt`, `MetroTheme.kt`, `MetroBanner.kt`, `MetroDialogs.kt`, `MetroWidget.kt` (an AppWidget), `MetroIcons.kt`.
- Reusable for us: tilt-on-press, page/turnstile transitions, Pivot, LongListSelector for the app list, theme scaffolding, the 1-D reorder as a starting point for edit mode. **Not** provided: live-tile flip/cycle, 2-D tile grid with resize, folders, notification binding — those are ours to write.
- Verdict: **REUSE (dependency, MIT)** — for motion/pivot/long-list; write the tile grid and live-tile engine ourselves. Risk: 3 stars / single maintainer — pin the version and be ready to vendor the ~15 files.

### 1.2 lorddie/MetroLauncherAndroid — W10M launcher, XML + Canvas
- URL: https://github.com/lorddie/MetroLauncherAndroid
- License: **none detected** (no LICENSE/COPYING in the tree). README says "Personal project — feel free to adapt it" — that is not a licence grant we can sell on.
- Language/UI: Kotlin, XML views + custom `Canvas` (`TileView`, `TileGridLayout`); minSdk 24, target 34. No Compose.
- Pushed: 2026-06-02 · created 2026-05-14 · 5 commits · stars 2, forks 0 · no releases.
- Code read: `app/src/main/java/com/metrolauncher/view/TileView.kt` (~2,200 lines) + full file tree.
  - Live-tile mechanics worth copying as *behaviour*: tile sizes 1x1/2x2/4x2/4x4; `LiveContentMode {ICON, PREVIEW}`; medium tiles show icon 10 s then each notification preview 5 s; wide/large cycle messages every 6 s; weather tile 20 s animation + 40 s info; `playFlipAnimation()` 700 ms on `rotationX` with content swap mid-way; `playSlideAnimation()` 350 ms `DecelerateInterpolator`; `drawOdometerCount()` 600 ms vertical digit roll for badge counts.
  - Notification binding: `service/NotificationListener.kt` (NotificationListenerService feeding `Tile.liveMessages/liveBody/liveTitle`), `util/MediaInfoCache.kt` (MediaSession mini-player), `util/GridPacker.kt` (auto-placement), `util/TileStorage.kt`.
- Verdict: **REFERENCE** — the best existing description of W10M live-tile *timing and notification mapping*; re-implement in Compose. If the owner wants to lift code, open an issue asking the author to add an MIT/Apache LICENSE first.

### 1.3 louis993546/Metro-Compose — WP8 look in Compose (WIP) incl. a launcher demo
- URL: https://github.com/louis993546/Metro-Compose
- License: **MIT License** (LICENSE at root).
- Language/UI: Kotlin, Jetpack Compose (README: Compose BoM 2024.11.00 for 0.260.0+).
- Pushed: 2026-08-28 · created 2021-07-10 · 738 commits · stars 9, forks 5 · Play Store open-testing.
- Code read: `demoLauncher/src/main/java/com/louis993546/metro/demo/launcher/Launcher.kt` (~160 lines): uses the library's `VerticalTilesGrid(gap = 12.dp)` with `s/m/l` tile size modifiers, `CircleButton`, `Text`, composition locals `LocalAccentColor` / `LocalTextOnAccentColor`; no live-tile animation; contains `TODO` linking the WP8 parallax-background StackOverflow question. Library also ships `verticalTilesGrid`, an app drawer demo (`DrawerPage.kt`), app search, and a "skylight" Bluesky client.
- Verdict: **REFERENCE** (MIT, so pieces may be lifted) — older Compose BoM and demo-grade code; MangoTile is the cleaner dependency.

### 1.4 Tgo1014/GridLauncher — "Windows phone inspired open source launcher"
- URL: https://github.com/Tgo1014/GridLauncher
- License: **Apache License 2.0** (LICENSE file, 11,357 bytes).
- Language/UI: **GitHub language = null.** Recursive tree and repo page on 2026-09-16 show only `.github/workflows/android.yml`, `.gitignore`, `LICENSE`, `README.md` — no source, only APKs on the Releases page.
- Pushed: 2024-09-09 · stars 111, forks 8.
- Verdict: **SKIP** — no source code in the repository as of 2026-09-16 (binary-only).

### 1.5 valkriaine/Factor_Launcher_Reboot — WP7-style launcher
- URL: https://github.com/valkriaine/Factor_Launcher_Reboot
- License: **none detected** (only `privacy_policy.md`); Play Store app.
- Language/UI: Java, XML views; Android 6–12 per README; live-tile animations reacting to notifications/media; icon packs; app shortcuts.
- Pushed: 2024-03-02 · 385 commits · stars 82, forks 7.
- Verdict: **REFERENCE** (no licence → no copying).

### 1.6 Theaninova/NativeWindowsLauncher — "Windows 10 Mobile Style Launcher"
- URL: https://github.com/Theaninova/NativeWindowsLauncher
- License: **MIT License**.
- Language/UI: GitHub language C++ (toolkit **UNVERIFIED** — README does not say; not Kotlin/Compose either way).
- Pushed: 2020-08-04 · 46 commits · stars 5 · author: "I lost interest in it".
- Verdict: **SKIP** (dead, wrong toolkit) — at most watch its videos for animation feel.

### 1.7 Not open source / not usable (listed so nobody re-searches them)
- **Metrov: WP Launcher** — https://github.com/metrov-wp-launcher/metrov-app is a feedback-only repo ("This is not the source code repository of the app"); proprietary, paid tiers (Action Center, Dynamic Tiles). Pushed 2025-05-31, 23 stars. **SKIP.** Worth installing as the UX benchmark (it has live tiles, parallax wallpaper, an Action Center, WP7/8.1 modes).
- **Launcher 10**, **Square Home**, **Launcher 8**, **Metro UI Launcher 10** (`com.mss.metro10.launcher`) — Play Store, closed. The F-Droid forum thread for Launcher 8 got "Where is the sourcecode?" with no answer. **SKIP.**
- **AaronW-BE/metro-launcher** — appears in search snippets ("smooth tile flip animations") but both `github.com/AaronW-BE/metro-launcher` and the API returned **404 on 2026-09-16**. Not evaluable.
- **F-Droid**: `search.f-droid.org/?q=windows+phone` returns only MetroMusic (a WP8-idiom music player); `?q=launcher+tiles` returns nothing.

### 1.8 Tile-animation libraries
- Nothing WP-specific beyond MangoTile/Metro-Compose was found. Queries run: `github android "live tile" flip animation library metro`; `github Android launcher "Jetpack Compose" "Windows Phone" OR "WP8" OR "Windows 10 Mobile" tiles`. Generic flip libraries surfaced (HasnathJami/AnimatedFlipView, View-based) — **SKIP**, Compose `graphicsLayer { rotationX }` + `animateFloatAsState` covers it in a few lines; copy the timings from 1.2.
- AOSP **Launcher3** (folders, drag-and-drop, `LauncherApps`, widget host) is the canonical reference for edit mode/folders; licence is Apache-2.0 by AOSP convention but the NOTICE fetch returned 404 in this pass — **UNVERIFIED here**. REFERENCE.

**Part 1 pick:** build the Start screen ourselves in Compose; depend on **MangoTile (MIT)** for tilt/turnstile/pivot/long-list; copy live-tile *timings* and the NotificationListener→tile mapping from **MetroLauncherAndroid** (reference only, unlicensed). No existing W10M launcher is worth building on: the two licensed ones are binary-only (GridLauncher) or dead C++ (NativeWindowsLauncher); the good one (MetroLauncherAndroid) is unlicensed and XML/Canvas.

---

## Part 2 — Action center + volume panel as accessibility overlays

### 2.1 noel-digital-fan/volume_plus_plus (Volume++) — volume-panel replacement
- URL: https://github.com/noel-digital-fan/volume_plus_plus
- License: **MIT License** (LICENSE at root).
- Language/UI: Kotlin; settings UI in Jetpack Compose/Material 3; the **overlay itself is classic Views** (`LinearLayout`/`FrameLayout`/custom `VolumeSlider`). minSdk 24, target 36. Nine panel skins (Android 7→15).
- Pushed: 2026-08-29 · created 2026-07-18 · stars 112, forks 5.
- Code read: `app/src/main/java/com/volume_plus_plus/app/service/VolumeKeyService.kt` (~150 lines) and `app/src/main/java/com/volume_plus_plus/app/overlay/OverlayController.kt` (~2,600 lines, ~70% fetched). Tree also lists `overlay/VolumeSlider.kt`, `overlay/OverlayStyle.kt`, `overlay/LiveEditSession.kt`, `service/UserService.kt`/`RootUserService.kt` (Shizuku/root, optional), `AndroidManifest.xml`.
  - Mechanism: `VolumeKeyService : AccessibilityService`, overrides `onKeyEvent`, consumes `KEYCODE_VOLUME_UP/DOWN` on both DOWN and UP ("Consume both down and up so the system panel never appears"), then `overlay?.adjustAndShow(direction)`. Falls back to system UI if draw-over-apps is missing ("Without draw-over-other-apps we can't show our panel — leave the system UI in charge").
  - Overlay: scrim window + panel window, `FLAG_NOT_FOCUSABLE | FLAG_LAYOUT_IN_SCREEN | FLAG_LAYOUT_NO_LIMITS`; auto-hide 3 s (compact) / 6 s (Android 7–8 skin) via `handler.postDelayed`. Window **type not seen** in the fetched excerpt (`overlayType()` helper) — **UNVERIFIED** whether it uses `TYPE_ACCESSIBILITY_OVERLAY` or `TYPE_APPLICATION_OVERLAY`.
  - Per-app volume needs Shizuku or root; plain stream volume does not. No Samsung-specific code seen.
- Verdict: **REUSE (MIT)** — take `VolumeKeyService` key-capture + show/hide lifecycle as-is; replace the View panel with a Compose W10M volume flyout. This is the top pick for the volume part.

### 2.2 thejaustin/SuperShade — notification-shade replacement (Shizuku)
- URL: https://github.com/thejaustin/SuperShade
- License: **none detected**.
- Language/UI: Kotlin (README indicates Compose); AccessibilityService + overlay + NotificationListenerService; **Shizuku mandatory** (works without root via wireless-debugging pairing, but must be re-started after every reboot).
- Pushed: 2026-09-16 · created 2026-09-01 · 51 commits · stars 6.
- Verdict: **REFERENCE** — unlicensed and two weeks old; useful only to see which Shizuku-privileged calls it needs for quick-settings toggles.

### 2.3 Samples for the overlay pattern (both unlicensed → pattern only)
- https://github.com/aug16vcc/AccessibilityServiceWithCompose — "AccessibilityService using Jetpack Compose overlay", Kotlin, **none detected**, pushed 2023-01-01, 17 stars. **REFERENCE**: shows the ComposeView-in-overlay setup (attaching lifecycle/saved-state owners to the window view).
- https://github.com/thbecker/android-accessibility-overlay — Kotlin, **none detected**, pushed 2021-04-22, 11 stars. **REFERENCE.**

### 2.4 Full no-root open-source notification-shade / quick-settings replacements
- **Not found in searches:** `github open source Android notification shade replacement AccessibilityService overlay quick settings`; `"Material Notification Shade" github source OR "control center" Android open source github overlay quick settings panel`; `github open source "notification shade" OR "notification panel" replacement Android NotificationListenerService overlay swipe Kotlin no root -LSPosed -Xposed`; `github open source Android AccessibilityService "quick settings" panel overlay "control center" iOS style Kotlin MIT OR GPL 2024 2025 2026`; `F-Droid quick settings panel overlay app open source "control center" OR "notification shade"`.
- What did surface: Power Shade / One Shade / Material Notification Shade (treydev — Play Store, closed), LSPosed modules (need root — out), Shade (mhss1, AGPL — a content blocker, unrelated), QuickTiles / Screenshot Tile (add QS tiles to the *system* shade, not replacements).
- Other volume overlays seen: Rzuss/granular-volume (GPL-3.0 per README — copyleft, SKIP), farizanjum/mixer-1 (per-app mixer, licence **UNVERIFIED**), punksta/volume_control_android (unmaintained per its title, licence UNVERIFIED) — none beat Volume++.

**Part 2 pick:** **Volume++ (MIT)** for key capture/overlay lifecycle; action center = our own `AccessibilityService` + `NotificationListenerService` + Compose overlay, patterned on the two samples. Plan note: without Shizuku, third-party apps cannot flip Wi-Fi/airplane/mobile-data directly on modern Android; expect the action-center toggles to be a mix of direct calls (flashlight, brightness with `WRITE_SETTINGS`, rotation lock, DND with policy access) and `Settings.Panel` intents for the rest. (Platform-constraint statement from general Android knowledge — re-verify per toggle when building.)

---

## Part 3 — Keyboard (IME)

### 3.1 florisboard/florisboard
- URL: https://github.com/florisboard/florisboard
- License: **Apache License 2.0** (README quotes the Apache 2.0 header).
- Language/UI: Kotlin + **Jetpack Compose** (verified in `app/build.gradle.kts`: `buildFeatures { compose = true }`, `alias(libs.plugins.kotlin.plugin.compose)`, `androidx.compose.material3/ui/activity-compose` deps). Library modules in `lib/`: `android`, `color`, `compose`, `kotlin`, `native`, **`snygg`** (its styling/theme framework). Android 8.0+.
- Pushed: 2026-09-14 · stars 8,655, forks 731 · "beta"; README: word suggestions/spell-check "not included in the current releases".
- Verdict: **REUSE** — top pick. Two routes: (a) ship a Snygg theme/extension that restyles stock FlorisBoard as W10M (no fork, user installs FlorisBoard), or (b) fork and strip to a W10M-only IME (Apache-2.0 permits closed derivative, keep NOTICE). Gap to accept: no suggestions/autocorrect yet.

### 3.2 HeliBoard (github.com/Helium314/HeliBoard → API reports `HeliBorg/HeliBoard`)
- License: **GNU General Public License v3.0** — **copyleft**.
- Language/UI: Kotlin, AOSP-LatinIME lineage (View-based keyboard). Pushed 2026-09-09 · stars 6,145.
- Verdict: **SKIP for code** (GPL would capture the whole shell); REFERENCE only for its dictionary/gesture handling.

### 3.3 AnySoftKeyboard/AnySoftKeyboard
- License: **Apache License 2.0**. Java, View-based. Pushed 2026-09-13 · stars 3,375.
- Verdict: **REFERENCE** — permissive and mature (has suggestions/gestures) but Java/XML; only worth mining if FlorisBoard's missing autocorrect becomes a blocker.

### 3.4 Julow/Unexpected-Keyboard
- License: **GNU General Public License v3.0**. Java. Pushed 2026-09-16 · stars 3,253.
- Verdict: **SKIP** (GPL, non-standard layout).

### 3.5 OpenBoard
- License GPL-3.0 and archived on F-Droid since 2022 per search snippets — **UNVERIFIED** (not fetched). **SKIP** (superseded by HeliBoard).

### 3.6 Voice IMEs (for the assistant's dictation, not the keyboard)
- ElishaAz/Sayboard — Vosk voice IME, Kotlin, **GNU General Public License v3.0**, pushed 2025-07-01, 582 stars. **REFERENCE** (how to wire Vosk into an `InputMethodService`).
- FUTO Voice Input — "FUTO Source First License 1.0" (non-OSI, source-available; per search snippet). **SKIP** for a sellable product.

---

## Part 4 — Phone dialer + SMS

All the mature apps are GPL-3.0. Options: (1) write dialer/SMS ourselves on `InCallService` / `Telephony` APIs using GPL apps as reference only; (2) start from the small permissive repos below; (3) ship the dialer/SMS as *separate* GPL APKs talking to the shell via intents (mere-aggregation argument — legal-opinion territory, not a free pass).

### 4.1 FossifyOrg/Phone (dialer)
- URL: https://github.com/FossifyOrg/Phone · License: **GNU General Public License v3.0** (LICENSE 35,149 bytes).
- Kotlin, **XML views** (18+ layouts under `app/src/main/res/layout/`, zero Compose), `app/src/main/kotlin/org/fossify/phone/services/CallService.kt` = the `InCallService`. Multi-SIM, blocking, conference, speed dial. Pushed 2026-09-14 · stars 1,329 · Play + F-Droid.
- Verdict: **REFERENCE** (copyleft) — best map of the call-state edge cases.

### 4.2 FossifyOrg/Messages (SMS/MMS)
- URL: https://github.com/FossifyOrg/Messages · License: **GNU General Public License v3.0**. Kotlin. Pushed 2026-09-14 · stars 1,552.
- Verdict: **REFERENCE** (copyleft).

### 4.3 quik-sms/quik (QKSMS revival)
- URL: https://github.com/quik-sms/quik · License: **GNU General Public License v3.0**. GitHub language Java (README: Kotlin/Java). Pushed 2026-09-16 · stars 2,769. MMS via `klinker41/android-smsmms` — that library is **archived (2024-06-25) with no licence detected** → do not depend on it.
- Verdict: **REFERENCE** (copyleft).

### 4.4 dekusms/DekuSMS-Android · Chooloo/koler · an1ndra/Messages
- Deku: **GNU General Public License v3.0**, Kotlin, pushed 2026-09-10, 598 stars — SKIP (GPL + E2EE/RMQ scope creep).
- Koler: LICENSE file read directly: "GNU GENERAL PUBLIC LICENSE Version 3, 29 June 2007", Kotlin, pushed 2024-12-27, 1,069 stars — SKIP (GPL, stale).
- an1ndra/Messages (Kotlin + Compose + M3 Google-Messages clone): **GNU General Public License v3.0**, created 2026-08-23, pushed 2026-09-16, 16 stars — SKIP (GPL).

### 4.5 Permissive candidates (small)
- **oxcened/opendialer** — https://github.com/oxcened/opendialer · **Apache License 2.0** · Kotlin, minSdk 24, Hilt/ViewModel/Flow, "fully modularized"; call log, contacts, dialer, in-call UI, conference, quick responses. Pushed 2026-09-06 · created 2023-11 · 266 commits · stars 22. UI toolkit **UNVERIFIED** (README silent). Verdict: **REUSE** — the only actively maintained permissive dialer found; reskin its in-call/dialpad screens to W10M.
- **gitubpatrice/SMS-TECH** — https://github.com/gitubpatrice/SMS-TECH · **Apache License 2.0** · Kotlin + Compose + Hilt, SQLCipher vault, default-SMS role. Created 2026-05-15, pushed 2026-09-16, stars 3. Verdict: **REUSE (cautiously)** — permissive and Compose, but 3 stars and a "vault/panic decoy" feature set we would strip; treat as a starting skeleton for the SMS provider/role plumbing, not a codebase to inherit.
- **arekolek/simple-phone** — https://github.com/arekolek/simple-phone · **MIT License** · Kotlin, "bare minimum" `InCallService` example. Pushed 2019-02-01 · stars 215. Verdict: **REFERENCE** — the 200-line proof of the `InCallService` + default-dialer role flow.

**Part 4 pick:** **OpenDialer (Apache-2.0)** as the dialer base; SMS built on `Telephony.Sms`/`SmsManager` using **SMS-TECH (Apache-2.0)** for role/provider plumbing and Fossify Messages as behaviour reference. Do not copy from Fossify/QUIK/Deku/Koler.

---

## Part 5 — Offline speech-to-text (fixed commands) and TTS

### 5.1 alphacep/vosk-api
- URL: https://github.com/alphacep/vosk-api · License: **Apache License 2.0**. Models page (alphacephei.com/vosk/models): "most models Apache 2.0", some AGPL / LGPL-3.0 / CC-BY-NC-SA 4.0 / GPLv3 / MIT — **check the licence column per model**. `vosk-model-small-en-us-0.15` = 40 MB.
- Android AAR (Java API), streaming, zero-latency partials, **reconfigurable vocabulary / grammar** (the feature that makes fixed voice commands robust). Pushed 2026-08-09 · stars 15,131.
- Verdict: **REUSE** — top pick for "offline fixed voice commands": small model + grammar constrained to the command list.

### 5.2 k2-fsa/sherpa-onnx
- URL: https://github.com/k2-fsa/sherpa-onnx · License: **Apache License 2.0**. C++ core with Kotlin/Android bindings and prebuilt APK demos; streaming STT (zipformer), **open-vocabulary keyword spotting** (models `sherpa-onnx-kws-zipformer-gigaspeech-3.3M-2024-01-01` English, `…-zh-en-3M-2025-12-20`; keywords as token files with per-keyword boost/threshold via `sherpa-onnx-cli text2token`), VAD, and **TTS** (Piper/VITS, Matcha, Kokoro, etc.). Pushed 2026-09-14 · stars 14,808. Model licences vary per model.
- Verdict: **REUSE** — one runtime for wake-word/KWS + STT + TTS; the strongest single dependency if we want a Cortana-style hotword. Pick Vosk (5.1) or this, not both.

### 5.3 moonshine-ai/moonshine
- URL: https://github.com/moonshine-ai/moonshine · LICENSE file read directly: code "apart from the source in core/third-party, is licensed under the MIT License"; models "released under the MIT License by default"; **exception**: legacy non-streaming Arabic/Japanese/Korean/Mandarin/Spanish/Ukrainian/Vietnamese models under the *Moonshine Community License* (commercial use requires registration; >USD 1M annual revenue needs an enterprise licence); TTS and G2P models have separate third-party-derived terms. Android supported per README. Pushed 2026-08-31 · stars 11,089.
- Verdict: **REUSE (English streaming only, MIT)** — accuracy edge over Vosk; watch the model-by-model licence.

### 5.4 ggml-org/whisper.cpp
- URL: https://github.com/ggml-org/whisper.cpp · License: **MIT License**. `examples/whisper.android` exists (recommends tiny/base models). Not streaming; heavier than Vosk for a command vocabulary. Whisper model weights licence **UNVERIFIED** in this pass. Pushed 2026-09-15 · stars 53,711.
- Verdict: **REFERENCE / optional** — only if free-form dictation quality matters more than latency.

### 5.5 Android platform `SpeechRecognizer` (on-device)
- `SpeechRecognizer.isOnDeviceRecognitionAvailable(Context)` / `createOnDeviceSpeechRecognizer(Context)` — `ApiSince=31` (from learn.microsoft.com mirror of the Android reference; the developer.android.com fetch returned only the nav shell). Availability is per-device/per-language and must be probed at runtime — **UNVERIFIED on the S25 Ultra**.
- ML Kit GenAI **Speech Recognition** API also exists (developers.google.com/ml-kit/genai lists six APIs incl. Speech Recognition; Galaxy S25/S25+/S25 Ultra are listed for "feature-specific APIs"; the Prompt API device list on that page named Fold7/TriFold/S26/Flip8/Fold8 but **not** the S25 series). Governed by "ML Kit GenAI API Additional Terms of Service"; inference only while the app is the top foreground app.
- Verdict: **REUSE as zero-cost fallback** (no model to bundle), not as the primary path for an overlay assistant.

### 5.6 TTS
- **sherpa-onnx TTS** (5.2, Apache-2.0 runtime) with **Kokoro-82M** — https://huggingface.co/hexgrad/Kokoro-82M, licence tag **apache-2.0**, trained on "permissive/non-copyrighted audio" per its card — is the permissive neural path. Piper *voices* run in sherpa-onnx too; per-voice licences vary (HayaiTTS catalog: "most are MIT or Apache-2.0").
- **OHF-Voice/piper1-gpl** (the Piper engine) — **GNU General Public License v3.0**, pushed 2026-09-15, 5,598 stars. **SKIP** (copyleft); not needed when voices run under sherpa-onnx.
- **HayaiApp/HayaiTTS** — https://github.com/HayaiApp/HayaiTTS · **GPL-3.0** (README; GitHub detector says "Other") · Kotlin + Compose M3 Expressive · registers a system `TextToSpeechService` · 600+ voices via sherpa-onnx · created 2026-05-16, pushed 2026-09-14, 40 stars. **REFERENCE** for the `TextToSpeechService` registration; or simply let users install it and call `android.speech.tts.TextToSpeech` (arm's-length, no licence contamination).
- **CodeBySonu95/VoxSherpa-TTS** — licence **UNVERIFIED** (not fetched). Not evaluated.
- **Platform TTS** (Samsung/Google engines already on the S25 Ultra, offline with downloaded voices) via `android.speech.tts.TextToSpeech` — zero licence, zero size. **REUSE** as v1.

**Part 5 pick:** v1 = **Vosk small-en + grammar (Apache-2.0)** for fixed commands and **platform TextToSpeech** for replies; v2 = **sherpa-onnx (Apache-2.0)** for hotword KWS + Kokoro TTS. Copyleft traps: piper1-gpl, HayaiTTS, Sayboard.

---

## Part 6 — On-device LLM runtimes and models (12 GB RAM phone)

### 6.1 Runtimes
| Runtime | URL | License | Lang / Android API | Pushed · stars | Verdict |
|---|---|---|---|---|---|
| **LiteRT-LM** | https://github.com/google-ai-edge/LiteRT-LM | **Apache License 2.0** | C++; Kotlin API `com.google.ai.edge.litertlm:litertlm-android` (Google Maven); `.litertlm` models from https://huggingface.co/litert-community; GPU via OpenCL (manifest must declare `libOpenCL.so`/`libvndksupport.so`), NPU dir = `applicationInfo.nativeLibraryDir`; `Engine(EngineConfig(modelPath)).createConversation().sendMessage(...)` | 2026-09-16 · 6,459 | **REUSE — top pick** |
| MediaPipe LLM Inference API | developers.google.com/edge/mediapipe/solutions/genai/llm_inference/android | Apache-2.0 samples | quoted notice: "The MediaPipe LLM Inference API is in maintenance-only mode. We recommend migrating your Android projects to LiteRT-LM Android (Kotlin) API." | — | **SKIP** (superseded) |
| llama.cpp | https://github.com/ggml-org/llama.cpp | **MIT License** | C/C++, GGUF; `examples/llama.android` (Kotlin app + JNI `lib` module) exists | 2026-09-16 · 128,468 | **REUSE (alternate)** — widest model choice, CPU-first |
| MLC LLM | https://github.com/mlc-ai/mlc-llm | **Apache License 2.0** | Python/TVM compile pipeline, Android via prebuilt libs | 2026-08-17 · 23,164 | **REFERENCE** (heavy toolchain) |
| ExecuTorch | https://github.com/pytorch/executorch | LICENSE file read directly: "BSD License / For 'ExecuTorch' software" | Python/C++; Android LLM demo | 2026-09-16 · 5,031 | **REFERENCE** |
| FilipFan/PolyEngineInfer | https://github.com/FilipFan/PolyEngineInfer | **Apache License 2.0** | Kotlin app running llama.cpp / ExecuTorch / LiteRT / ONNX side by side | 2026-03-29 · 9 | **REFERENCE** (integration recipe for several runtimes in one app) |
| ML Kit GenAI Prompt API (Gemini Nano via AICore) | https://developers.google.com/ml-kit/genai | "ML Kit GenAI API Additional Terms of Service" | no model to bundle; foreground-only inference; per-app quota (`ErrorCode.BUSY`); Prompt-API device list fetched 2026-09-16 did **not** include the S25 series | — | **SKIP for the assistant** (foreground-only kills an overlay assistant; device gating) |

### 6.2 Models that fit 12 GB RAM (all far under; RAM numbers where published)
| Model | Source / licence (as read) | Size / speed evidence | Verdict |
|---|---|---|---|
| **Gemma 4 E2B-it** | https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm — licence tag **apache-2.0**; Google Open Source Blog 2026-04-02: released under "OSI-approved Apache 2.0 license" | `.litertlm` 2,583 MB (web variant 2,008 MB); card benchmarks on Galaxy S26 Ultra: CPU 46.9 tok/s decode, 1,733 MB RAM; GPU 52.1 tok/s decode, 676 MB RAM; 32k context | **REUSE — top pick** with LiteRT-LM |
| Gemma 4 E4B-it | https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm (Apache-2.0 per the same announcement; card **not fetched**) | larger, same runtime | REUSE if E2B quality is short |
| Gemma 3n E2B-it | https://huggingface.co/google/gemma-3n-E2B-it — licence tag **"gemma"** (custom Gemma terms, gated: "you have to accept the conditions") | — | **SKIP** — superseded by Gemma 4 with a clean licence |
| Qwen3-1.7B | https://huggingface.co/Qwen/Qwen3-1.7B — **apache-2.0** | GGUF via llama.cpp | REUSE (llama.cpp path) |
| SmolLM3-3B | https://huggingface.co/HuggingFaceTB/SmolLM3-3B — **apache-2.0**, 3B | GGUF via llama.cpp | REUSE (llama.cpp path) |
| Phi-4-mini-instruct | https://huggingface.co/microsoft/Phi-4-mini-instruct — "licensed under the MIT license", 3.8B | GGUF via llama.cpp | REUSE (llama.cpp path) |
| Llama-3.2-3B-Instruct | https://huggingface.co/meta-llama/Llama-3.2-3B-Instruct — **"Llama 3.2 Community License"** (custom; gated) | — | **SKIP** — custom terms when Apache/MIT peers exist |

**Part 6 pick:** **LiteRT-LM (Apache-2.0) + Gemma 4 E2B (.litertlm, Apache-2.0)**; keep **llama.cpp (MIT)** as the escape hatch for Qwen3/SmolLM3/Phi-4-mini GGUFs. Avoid Gemma 3n and Llama 3.2 licences.

---

## Plan-affecting findings
1. **No W10M launcher to build on.** Every licensed candidate is binary-only, dead, or not Compose; the one good Kotlin implementation (MetroLauncherAndroid) is unlicensed. Keep the plan's "build the Start screen from scratch in Compose", add **MangoTile** as a dependency and steal timings from MetroLauncherAndroid.
2. **Volume panel is largely solved** (Volume++, MIT): budget it as "re-skin + Compose panel", not a research item.
3. **Action center has no OSS precedent without Shizuku**; scope the quick-settings toggles to what a non-root app can do (flashlight, brightness, rotation, DND, media, `Settings.Panel` deep links). Decide early whether to require Shizuku for the rest.
4. **Dialer/SMS are the copyleft minefield.** Switch the plan from "Fossify" to **OpenDialer (Apache-2.0)** + own SMS on platform APIs (SMS-TECH as plumbing reference).
5. **Keyboard:** FlorisBoard (Apache-2.0, Compose, Snygg themes) — consider shipping a Snygg theme first (zero fork) and only forking if the theme engine cannot reach the W10M look. Accept "no autocorrect" until FlorisBoard lands it.
6. **Assistant stack** is fully permissive if we choose Vosk/sherpa-onnx + Kokoro/platform TTS + LiteRT-LM/Gemma 4; the "later LLM" part should target `.litertlm` from the start so the Cortana UI does not need a second integration.
7. **Naming:** "Cortana", "Metro", "Windows Phone" are Microsoft marks — pick product/assistant names before any pitch material is written.
8. **Glance screen:** no OSS candidate found — queries: `github open source always on display "glance" app Android no root overlay Kotlin license` (returned Jetpack Glance widgets and a kiosk app, unrelated). Treat as design work (lock-screen `showWhenLocked` activity or overlay), not reuse.
