# R11 §Alarms & Clock — Alarms & Clock (Windows 10 Mobile) measurements

Scope: the Alarms & Clock app's four pivots (Alarm, World Clock, Timer, Stopwatch), the alarm editor and its flyouts,
the timer editor, the ringing UI (alarm and timer), the pinned tile and the splash. Gates phase 15's FINAL
(docs/plan/phase-15-inbox-clock-calculator-recorder.md). Governing build: the final release (15063 / 15254). Format and
confidence rules: docs/plan/r8-groove-measurements.md, plus the shared skeleton of the three phase-15 sections.

## Summary — what was and was not established

Established from one clean 30-fps screen recording of the **14393-era app** (CK1, January 2017, German UI, dark theme)
and cross-checked for structure and English wording against a **10586** recording (CK2, English, light theme, 320-epx
canvas), a **2015 pre-10586** recording (CK3, English, dark theme) and Microsoft's 2016 desktop Store screenshots (CK6):

- The four pivots are **tab-style headers, not text pivots**: an icon above a caption label, four tabs centred as a group
  at a 64.5-epx pitch, the selected tab in accent with a 64.4-epx accent underline at the bottom of a 68.4-epx grey band
  (24.0 → 92.4 epx) under a 24.0-epx status bar. English labels: **Alarm, World Clock, Timer, Stopwatch**.
- The alarm row is time (Light, 17.8-epx digits) / name (semibold, accent when on) / repeat line (grey), with the
  44 × 20 ToggleSwitch and an On/Off label on the right. Empty list: **"No alarms"** in large grey Light type.
- The 14393 alarm editor is an **inline looping time spinner** (32-epx rows, accent band at ≈60 % opacity) followed by
  four label/value rows at a **64-epx pitch** (Alarm name, Repeats, Sound, Snooze time), values in accent. The 10586 and
  2015 editors are a different, older form (TextBox + TimePicker field + ComboBoxes).
- Snooze choices **5 / 10 / 20 / 30 minutes and 1 hour**, default **10 minutes** (every source).
- World clock: map band, an accent-filled "Local time" row, city rows at a **96.9-epx pitch** with a third line
  "&lt;day&gt;, N hours ahead/behind"; a compare mode swaps the app bar for a 48-epx accent hour strip.
- Timer and stopwatch: centred digits (hh:mm grey, seconds white), a 59.6-epx ring play/pause button flanked by reset and
  expand, the running state drawn as an accent arc on the ring; stopwatch hundredths at 55 % size; laps as "Laps / Splits".
- **Ringing is a toast, not a page.** In every source (the 15254 alarm, CK4; the 14393 timer, CK1; an older alarm, CK5)
  the alarm or timer rings as an interactive banner pinned to the top of the screen — over the lock screen with the lock
  screen still visible under it. The 15254 alarm toast carries the alarm name and time, a "Snooze for" ComboBox (default
  10 minutes) and two icon buttons, **Snooze** and **Dismiss**. No full-screen ring page exists in any capture.
- Tab switching by tap does **not slide**: the old content fades within one 30-fps frame, the underline jumps in one frame,
  and the new page appears as it loads (LOW, 30 fps).

Not established: **any capture of the app itself on 15063 / 15254** (the only final-build source is a camera thumbnail of
the alarm toast); every in-app value is 14393 and is a governing-build gap. No source is ≥ 55 fps, so every motion value
is LOW or UNMEASURED. The English wording of 14393-only strings (timer toast, menu, compare labels) was not captured. The
14393 live-tile face and the laps list on 14393 were not captured (2015 structure only). Fixed-epx versus
width-proportional cannot be separated: CK1 is the only 360-epx source and the 320-epx sources are older builds.

---

## 0. Method, sources and calibration

### 0.1 Unit convention and calibration

epx = 1/360 of screen width, as r8 §0.1. Measurements: Python + Pillow on unaltered PNG frames extracted with ffmpeg
(`-ss <t> -frames:v 1`); text and glyph extents by thresholded row/column runs; colours as the 90th percentile of the ink
pixels (capture rendition, as R3 §0.2's colour caveat).

- **CK1** (the primary source). Project My Screen-style capture filling the 720-px frame height. Screen left edge 440.0 px
  (first full band pixel; the pixel before is 0), right edge 842.4 px (partial pixel 842 at 10/26) → **402.4 px wide**;
  height 720 px (status icons from y = 4, nav glyph centre 27 px above the bottom). Canvas **360 × 640 epx** (dual-SIM icons;
  Lumia 950 DS / 650 DS class): **1 epx = 1.1178 px horizontally, 1.125 px vertically** (0.6 % anisotropy from the capture,
  kept as two scales). Validated against earlier measurements: the ToggleSwitch measures **43.8 × 19.6 epx** (R3 C1:
  44 × 20) and the app bar **48.9 epx** with buttons on a **68.9-epx** pitch (R7 §3.5.8: 48.2 ± 1, 68). Tolerance ±1 px =
  ±0.9 epx; one frame = ±33 ms. The capture is downscaled about 3.6× from the panel, so single-source values are MEDIUM.
- **CK2**. Screen 480.54 → 798.37 px × 58.67 → 588.48 px = **317.8 × 529.8 px**, exactly 5:3 → a WVGA phone with capacitive
  keys (drawn in the capture skin) on a **320 × 533 epx** canvas: **1 epx = 0.993 px**. Blurred (≈1 px per epx): LOW for
  geometry, used for English strings, light-theme colours and one row pitch.
- **CK3**. Screen inside a phone skin, x 687 → 1242 px (555 px), y 71 → 985 px; 15:9 → **320-epx canvas assumed**
  (1.734 px/epx). Used for English strings, the 2015 structure and the tile face only (tile-relative values).
- **CK4 / CK5**. Camera thumbnails (1280 × 720 JPEG). CK4: the toast spans the full screen width, 680.5 → 1004.5 px =
  324 px → **0.9 px/epx**, near head-on, ±3 %. CK5: structure only.
- **CK6**. Desktop screenshots, 1366 × 768: strings and desktop layout only, never phone geometry.

### 0.2 Sources

| ID | URL (image, or video @ t) | Date | Pixels / fps | Device, canvas | Build / app version (how known) | Stored file(s) in docs/plan/r11/src/clock/ | sha256 (16) |
|---|---|---|---|---|---|---|---|
| CK1 | https://www.youtube.com/watch?v=e4tA7bElN4U "Windows 10 Mobile: Alarm & Uhr" (WPLive DE) @ t = 21, 29, 47, 63, 74, 92, 104, 111, 120, 125, 134.607, 135.241, 135.441, 149, 162, 171, 185, 200, 206, 212, 224, 227, 235, 244, 249 s | 2017-01-31 (world-clock date line reads "Donnerstag, 5. Januar 2017") | 1280 × 720 @ 29.97 | dual-SIM Lumia, 360 × 640 epx, dark theme, orange accent, de-DE | **≈14393** (Anniversary Update was the production build in January 2017; 15063 shipped April 2017); app version not on screen, earlier than 10.1704.1013.0 by date | e4tA7bElN4U_t21.png, _t29, _t47, _t63, _t74, _t92, _t104, _t111, _t120, _t125, _t134.607, _t135.241, _t135.441, _t149, _t162, _t171, _t185, _t200, _t206, _t212, _t224, _t227, _t235, _t244, _t249 (.png) | 012ed9d3ddb5cebe, becb60591da34b7b, 5c9ea63c72bc270d, d38107ff89339e24, b6a9b2ce6d42d0c7, abe652b4cc32caf4, ae7561841aa633ba, 2647a1e8f950915a, 3858c14bee8f35b5, 7594a3cfe603d2c4, 2b7752a62cbc89ba, d8a8d59ac9fb0818, bfab561e7fd31a5c, c615916dbb65c539, 1427c5e7d7e0756a, d39e95fd8781cb78, af6e4036fe00fcc7, 6bfff771367044b6, af76e5981b334d65, 3f155af04376c24a, 97219562f52ceabe, dad1de652af011a9, 13f5b1d3550cdd6d, cd3bfae2bbe26501, 75d33de32ffc65db |
| CK2 | https://www.youtube.com/watch?v=1WGjRETBix8 "Windows 10 mobile: How to set a alarm" (Neelu) @ t = 6, 13, 18, 24, 26, 28, 34 s | 2016-03-01 | 1280 × 678 @ 29.95 | WVGA phone, 320 × 533 epx, light theme, purple accent, en | **≈10586** (production build in March 2016) | 1WGjRETBix8_t6, _t13, _t18, _t24, _t26, _t28, _t34 (.png) | 05b643cc339cede3, abc56b0c458ccf2c, d01f51d39e5321fa, 4d51111a855e1c66, c2df04d1738b2859, 11104a00c38447ed, d3ab0a47f3c8acd1 |
| CK3 | https://www.youtube.com/watch?v=kq60r2S4iZ8 "Windows 10 Mobile - Alarms and Clocks presentation" (My Windows Mobile) @ t = 12, 19, 102, 105, 126, 132, 138, 144, 189, 196, 234, 247, 263 s | 2015-08-12 | 1920 × 1080 @ 29.97 | 15:9 phone in a capture skin, 320-epx canvas assumed, dark theme, blue accent, en (pl-PL region) | **pre-10586 Insider** (by date; build not on screen) — not governing | kq60r2S4iZ8_t12, _t19, _t102, _t105, _t126, _t132, _t138, _t144, _t189, _t196, _t234, _t247, _t263 (.png) | 77bbdad403ca12ac, c097aca5b09bcd4a, 5550a38602926aed, 03d43a23a9f1fa10, a9bf7e9a833bae95, e4094fd1bf8ccef7, 222efc83377f1f8f, 97ef1cae88074320, d83f0a8e7b8a9c2f, 7295a6139fa176e8, 3fce217d511dbce2, b038b1c12a32f13d, 0b6f20cc79d7437a |
| CK4 | https://i.ytimg.com/vi/neU9rQX7Tfc/maxresdefault.jpg (thumbnail of "Clock Alarm Alert Nokia N8 vs Microsoft Lumia 650", No Caller ID) | 2022 (the N8 beside it reads "Thu 27/10/2022"; the Lumia lock screen "Thursday, October 27") | 1280 × 720 JPEG | Lumia 650, 360-epx canvas, camera | **15254.x** (Lumia 650's last build; 2022) — governing | neU9rQX7Tfc_maxresdefault.jpg | b81a3c67f96ce89c |
| CK5 | https://i.ytimg.com/vi/FAf0TAZ73zk/maxresdefault.jpg (thumbnail of "Microsoft Windows 10 (Microsoft Lumia 650) Voice Type Alarm Screen", MobiMix TV) | unknown | 1280 × 720 JPEG | white Lumia 650, camera | unknown | FAf0TAZ73zk_maxresdefault.jpg | 1646e137e449a4bb |
| CK6 | Microsoft Store listing for Alarms & Clock (9WZDNCRFJ3PR), Wayback snapshot 2016-02-12 (web.archive.org/web/20160212172956/…/9WZDNCRFJ3PR), images store-images.s-microsoft.com/image/apps.13198… and apps.58724… | 2016-02-12 (screenshots dated 4/2/2015 in their taskbars) | 1366 × 768 | Windows 10 desktop, en-US | desktop app, 2015 | store_2016_timer_desktop.png, store_2016_alarms_desktop.png | 6451f2cd1c5e8715, b033e1f971036f06 |

The video files and all intermediate crops, contact sheets and scripts are scratch evidence (not committed). The stored
frames are unaltered ffmpeg output; each was re-measured after storage and reproduces the numbers below.

### 0.3 Searched without finding

- **Any 15063 / 15254 capture of the app itself.** YouTube search (yt-dlp, 15 results per query) for "Windows 10 Mobile
  Alarms Clock app", "Windows 10 Mobile 15063 alarms clock", "Lumia 950 alarm clock wake up", "Lumia 650 alarm",
  "Windows 10 Mobile alarm ringing", "Windows 10 Mobile alarm lock screen snooze dismiss", "Windows 10 Mobile Creators
  Update apps tour", plus the earlier session's searches (Spanish, German, Russian, Italian variants). The candidates that
  show a final-build Lumia ringing (neU9rQX7Tfc, FAf0TAZ73zk, PpdscSpUZOs, dqNlab5vZK8, bWRPxPCQeyE) could not be downloaded:
  YouTube now answers every player client with "Sign in to confirm you're not a bot" (tv_simply, web_safari, mweb,
  android_vr, ios, web_embedded, tv), so only their thumbnails were used. -igf0FzKNTU (Ho Young Won, "How are the Apps
  now?") and gDq3AH7ZF58 / _8fBCTiRfN8 (Ian Dixon, 15063) were not reachable for the same reason.
- **GSMArena** Lumia 950 (Dec 2015), 950 XL and 650 review galleries (every shot pulled): no Alarms & Clock screen.
- **Microsoft Store**: the current catalog (displaycatalog.mp.microsoft.com, 9WZDNCRFJ3PR) and the 2016 / 2017 Wayback
  listings carry desktop screenshots only.
- **WindowsBlogItalia**, "Sveglie e orologio … 10.1704.1013.0" (2017-04): an app icon only, no screenshots.
- Other downloaded videos checked and rejected for this app: t8BVxMFX6A4 (third-party clock tiles, camera), 3ZQxepVak9E
  (10536, camera), ypkpcWrxx2M (10166, camera), CeXukYrO_1o (desktop), lpRcZAO91lw (2026 Lumia 950, no Alarms & Clock),
  E6vvrz4ozpE and I98ENfXJRqA (R3's S2 / S1; neither opens Alarms & Clock).

### 0.4 Shared values cited, not re-measured

| Value | Where measured | Used here for |
|---|---|---|
| ToggleSwitch 44 × 20 ± 2 epx; state label 56 epx right of the toggle's left edge | w10m-measurements.md R3 C1 | alarm-row toggle (agrees: 43.8 × 19.6, label at +57.3) |
| Two-line list item pitch 64 ± 1 epx | R3 A15 | editor field rows (agrees: 64.0) |
| List row 44 ± 1 epx | R3 C2 | flyout / dropdown rows (agrees: 44.0) |
| App bar 48.2 ± 1 epx, 68-epx pitch, "…" 48 epx flush right | r7-measurements.md §3.5.8 | every pivot's app bar (agrees: 48.9, 68.9, "…" centre 24.1 from the edge) |
| Flyout fill (40,40,40), 1-epx lighter border, 44-epx items, 8-epx padding | R7 §3.6.2 | repeat-days flyout, sound flyout, snooze dropdown (fill agrees exactly) |
| Page title geometry, all-caps title cap 11 ± 1 epx | R3 C1, R7 §3.3.2, r8 §1.2 | editor titles (cap 11.6) |
| Type ramp (caption 12, body 15, base 15 semibold, subtitle 20, title 24 semilight, subheader 34 light, header 46 light) | R1 §5.1 as used in phase 15 Decisions | font-size inferences from cap heights (Segoe UI cap / digit height ≈ 0.70 em) |
| Status bar 28 epx; nav bar 48 epx | R3 C4, phase 01 X6 | compared with CK1's in-app 24.0 / 47.1 (see gap G5) |

---

## 1. Page frame, tab header and app bar (all four pivots)

| # | Item | Value | Conf | Build | Source @ t / file | Derivation |
|---|---|---|---|---|---|---|
| 1.1 | Status bar (in app) | **24.0 epx** (0 → 27 px), black, drawn by the system over the app | MEDIUM | 14393 | CK1 @ 29, e4tA7bElN4U_t29.png | column profile x = 520: black to y 26, band from 27 px; 27 / 1.125 |
| 1.2 | Tab header band | **24.0 → 92.4 epx (68.4 epx tall)**, fill (24,26,23) capture = the app-bar fill; full width | MEDIUM | 14393 | CK1 @ 29 | profile: band 27 → 103 px, black from 104 px |
| 1.3 | Tabs, order and form | four tabs **Alarm · World Clock · Timer · Stopwatch**; each an outline icon above a caption label; selected tab's icon and label in accent; accent underline under the selected tab | HIGH | 14393, 10586, 2015 | CK1 @ 29; CK2 @ 34; CK3 @ 19, 132, 263; CK6 | all four agree on count, order and form (desktop labels "Alarms" plural) |
| 1.4 | Tab centres | **82.3 · 146.7 · 211.4 · 275.8 epx** → pitch **64.5 epx**; group centre 179.1 (≈ W/2) | MEDIUM | 14393 | CK1 @ 29 | icon and label column runs; label centres 82.3 / 146.7 / 211.6 / 276.0 |
| 1.5 | Tab icon | ink ≈ 16.1 × 16.9 epx (alarm, world clock) / 14.3 wide (timer, stopwatch), rows 42.7 → 59.6 epx, **centre y 51.2 epx** | MEDIUM | 14393 | CK1 @ 29 | lum > 60 runs in the band |
| 1.6 | Tab label | caption class (12 epx), ink rows 66.7 → 78.2 epx including descenders, centred under the icon; widths "Alarm" 30.4, "Weltuhr" 41.2, "Zeitgeber" 51.0, "Stoppuhr" 49.2 epx | MEDIUM | 14393 | CK1 @ 29 | as 1.5 |
| 1.7 | Tab colours | selected: accent (icon (162,100,23), label (130,88,34) anti-aliased; underline (254,136,0)); unselected icon (102,104,101) ≈ 40 % white, label (93,95,92) ≈ 37 % white | MEDIUM | 14393 | CK1 @ 29 | ink p90 per blob |
| 1.8 | Selected-tab underline | accent bar **64.4 epx wide (= one tab pitch)**, rows 88.0 → 92.4 epx at the bottom of the band, **≈3.7 epx** thick integrated (rows 99–103 px at 0.71 / 1 / 1 / 0.91 / 0.59 coverage) | MEDIUM | 14393 | CK1 @ 29, 120, 206, 244 (moves with the tab) | accent-channel runs |
| 1.9 | Same header on a 320-epx canvas | tab icon centres 77.0 · 133.9 · 190.8 · 244.1 epx → pitch **55.7 epx**; underline 53.4 × 2.0 epx at 69.8 → 71.8 epx; band and page (222,222,222) / (247,241,246) in light theme | LOW | 10586 | CK2 @ 34, 1WGjRETBix8_t34.png | blurred 1 px ≈ 1 epx capture |
| 1.10 | Fixed epx or width-proportional? | **not separable**: pitch = 0.179 W (CK1) vs 0.174 W (CK2) — neither the same epx nor exactly the same fraction, and the builds differ | LOW | 14393 vs 10586 | 1.4, 1.9 | two canvases, two builds |
| 1.11 | Page background | dark theme **(0,0,0)**; light theme (247,241,246) capture | MEDIUM | 14393; 10586 | CK1; CK2 | samples |
| 1.12 | App bar | **544.0 → 592.9 epx (48.9 epx)**, fill (24,26,23); glyph box ≈ 20.6 epx; button centres **208.9 · 277.8 · 335.9 epx** (68.9 pitch, "…" 24.1 from the right edge), glyph centre y 568.2 | HIGH | 14393 | CK1 @ 29, 200; agrees with R7 §3.5.8 | glyph runs; profile 612 → 667 px |
| 1.13 | App-bar buttons per page | Alarm pivot: **Add, Select (MultiSelect), More**; World Clock: **Add ("New"), Compare, More**; Timer: **Add, Select, Pin, More**; Stopwatch: **Pin, Share, More**; alarm / timer editor: **Save, More** (+ **Delete** when editing an existing item) | HIGH | 14393; 10586; 2015 | CK1 @ 29, 200, 206, 212, 244; CK2 @ 6; CK3 @ 126, 234 | visual; the 14393 timer adds Pin, 2015 had none |
| 1.14 | Expanded app bar ("…" tapped) | labels appear under the glyphs ("New", "Vergleichen"); a menu grows above with items at a **44-epx pitch**, ink x 10.7 epx: "Feedback senden", "Benachrichtigungseinstellungen", "Info"; menu fill (40,40,40) | MEDIUM | 14393 | CK1 @ 162, e4tA7bElN4U_t162.png | item cap tops 456.9 / 500.4 / 544.0 epx. The user had hidden the nav bar by this point, so the bar sits at the screen bottom |
| 1.15 | Nav bar (as captured) | 592.9 → 640 epx (47.1), black; glyph centres x 66.2 · 178.5 · 291.7, y 616.0 epx | MEDIUM | 14393 | CK1 @ 29 | glyph runs |

## 2. Alarm pivot — list, row, empty state

| # | Item | Value | Conf | Build | Source @ t / file | Derivation |
|---|---|---|---|---|---|---|
| 2.1 | Empty state | "Keine Alarme" / EN **"No alarms"**: Light weight, **cap 17.8 epx** (≈ 24–25 epx, Title class), ink x 9.8 epx, cap top 113.8 epx (21.4 below the band), **grey** (p90 87, max 101 ≈ 34–40 % white); no other text, app bar unchanged | MEDIUM | 14393 (EN wording 2015) | CK1 @ 29; CK3 @ 19 | "K" rows 129–148 px; CK3 shows the same grey Light line |
| 2.2 | Row structure | **time / name / repeat line** left-aligned; ToggleSwitch + state label right | HIGH | 14393, 10586, 2015 | CK1 @ 120; CK2 @ 34; CK6 | four sources agree |
| 2.3 | Time line | digit height **17.8 epx** (106.7 → 124.4 epx), Light / SemiLight (≈ 25 epx), white (p90 215), ink x 9.8 epx; 24-h here, "h:mm AM" in English 12-h (CK2 "07:45", desktop "7:10 AM") | MEDIUM | 14393 | CK1 @ 120, 125 | row runs |
| 2.4 | Name line | cap **11.6 epx** (134.2 → 145.8), semibold body, **accent when the alarm is on**, default text colour when off | MEDIUM | 14393; 10586 | CK1 @ 120 (on, accent (208,128,57)); CK2 @ 34 ("Alarm" accent on 08:00 = On, black on 07:45 = Off) | colour p90 |
| 2.5 | Repeat line | rows 154.7 → 169.8 epx (descender included), grey (124 ≈ 49 %), body regular; "Einmalig" / EN "Only once", "Every day", "Mon, Tue, Wed, Thu, Fri, Sat" | MEDIUM | 14393 | CK1 @ 120; CK2 @ 34; CK3 @ 102 | |
| 2.6 | Line spacing | time cap top → name cap top 27.5 epx; name baseline → repeat baseline ≈ 20.5 epx (body line height 20) | MEDIUM | 14393 | CK1 @ 120 | |
| 2.7 | First row offset | first time cap top **14.3 epx** below the band | MEDIUM | 14393 | CK1 @ 120 | 106.7 − 92.4 |
| 2.8 | Row pitch | **88 ± 1 epx** (time tops 105.0 / 192.6 / 281.2) | LOW | 10586 (320 canvas) | CK2 @ 34 | 14393 shows one alarm only |
| 2.9 | Toggle | **43.8 × 19.6 epx**, x 263.0 → 306.9 (right edge 53.1 from the screen edge), **centre y 134.2** (level between the time and name lines); state label "Ein"/"Aus" (EN "On"/"Off") cap 10.7 at x 320.3 → 338.2 (21.8 from the edge), 13.4 epx gap | HIGH | 14393 | CK1 @ 120, 125; agrees with R3 C1 | accent / lum runs |
| 2.10 | Toggle colours | On: accent fill, white knob; Off: 1-epx outline, knob in the text colour | MEDIUM | 14393; 10586 | CK1 @ 120; CK2 @ 34 | |

## 3. Alarm editor (NEW ALARM / EDIT ALARM)

| # | Item | Value | Conf | Build | Source @ t / file | Derivation |
|---|---|---|---|---|---|---|
| 3.1 | Structure | title; **inline looping time spinner** (hours │ minutes); "In X hours, Y minutes" caption; four label/value rows **Alarm name · Repeats · Sound · Snooze time**; app bar Save, More | MEDIUM | 14393 | CK1 @ 47 | one source |
| 3.2 | Older form (not governing) | title; "Alarm name" TextBox; "Time" TimePicker field (7 │ 00 [│ AM]); caption; "Repeats", "Sound", "Snooze time" ComboBoxes; tapping Time opens a full-page looping picker with a ✓ / ✕ bar | MEDIUM | 10586; 2015 | CK2 @ 6, 13; CK3 @ 47 | the 14393 editor replaced this form |
| 3.3 | Title | "NEUER ALARM" / EN **"NEW ALARM"** (edit: "EDIT ALARM", CK2): all caps, semibold, **cap 11.6 epx** (40.9 → 52.4, centre 46.7), ink x 10.7, white | MEDIUM | 14393 | CK1 @ 47; CK2 @ 6 | agrees with R3 C1 / r8 §1.2 cap 11 ± 1 |
| 3.4 | Spinner frame | top rule **64.0 epx**, bottom rule **255.1 epx** (191 epx tall, six 32-epx rows, cut rows at both edges); rules lum 36–40 | MEDIUM | 14393 | CK1 @ 47 | horizontal-rule scan |
| 3.5 | Spinner rows | pitch **32.0 epx** (digit tops 90.7, 122.7, 154.7, 186.7, 218.7, 250.7); digits cap 11.6 epx (≈ 16–17 epx), unselected grey (p90 ≈ 190 ≈ 75 %), selected white | MEDIUM | 14393 | CK1 @ 47 | row runs per column |
| 3.6 | Selected band | **144.0 → 176.0 epx (32 epx)** — the centre row — full width, fill (154,84,0) ≈ **0.61 × accent** (254,136,0): accent at ≈ 60 % over black (INFERRED: the list-accent-low brush) | MEDIUM | 14393 | CK1 @ 47 | accent-row scan; ratio of channels |
| 3.7 | Columns | divider (1–2 px, lum 40) at **x 178.9 epx** (≈ W/2); hour digits centred ≈ 88, minute digits ≈ 270 epx | MEDIUM | 14393 | CK1 @ 47 | vertical-line scan |
| 3.8 | Countdown caption | "In 7 Stunden, 42 Minuten" / EN **"In 5 hours, 57 minutes"**: centred on W/2 (x 95.7 → 262.1), rows 266.7 → 280.0, grey (159 ≈ 62 %), body | MEDIUM | 14393 | CK1 @ 47; CK2 @ 6 | |
| 3.9 | Field rows pitch | label cap tops **298.7 · 364.4 · 428.4 · 492.4 epx** → **64 epx** | HIGH | 14393 | CK1 @ 47; agrees with R3 A15 | row runs |
| 3.10 | Label / value | label body, cap 11.6, near-white (p90 200 ≈ 78 %); value body in **accent**, cap top = label cap top + 26.6 epx; ink x 9.8–12.5 epx; the Sound value is a bell glyph (Ringer) + "Standard" / EN "Default" | MEDIUM | 14393 | CK1 @ 47 | |
| 3.11 | 12-hour spinner | UNMEASURED on 14393 (source is 24-h); the 2015 form shows AM/PM as a third field | UNMEASURED | — | CK3 @ 47 | see U4 |

## 4. Editor flyouts, Sounds page, timer editor

| # | Item | Value | Conf | Build | Source @ t / file | Derivation |
|---|---|---|---|---|---|---|
| 4.1 | Repeat-days flyout | fill **(40,40,40)** with a 1-epx lighter border; **x 11.6 → 347.1, y 137.8 → 496.0 epx**; close ✕ (11.6-epx glyph) at centre (325.2, 158.7); seven rows **Montag … Sonntag** at a **44.0-epx** pitch; checkbox **20.4 epx** square, left edge 26.8 epx; label after it | MEDIUM | 14393 | CK1 @ 74, e4tA7bElN4U_t74.png | colour box + row runs; fill = R7 §3.6.2 |
| 4.2 | Repeat-days order | de list starts Monday; the English 2015 flyout starts **Sunday** (locale's first day of week) | MEDIUM | 14393; 2015 | CK1 @ 74; CK3 @ 23–36 | |
| 4.3 | Sound flyout | fill (40,40,40), **x 11.6 → 346.2, y 355.6 → 530.7 epx** (opens under the value); row 1 vibrate glyph + "Nur Vibrieren" / EN **"Vibrate only"** (text top 376.9); separator 412.4 epx; links in accent **"Aus meiner Musik auswählen ›" / "Pick from my music ›"** (440.9) and **"Aus Klingeltönen auswählen ›" / "Pick from ringtones ›"** (488.9), 48-epx pitch; ink x 24.2 | MEDIUM | 14393; EN 10586 | CK1 @ 92; CK2 @ 18 | |
| 4.4 | Snooze dropdown | ComboBox list, fill (40,40,40), **x 11.6 → 346.2, y 315.6 → 551.1 epx**; items **5 Minuten · 10 Minuten · 20 Minuten · 30 Minuten · 1 Stunde** at a **44.0-epx** pitch (text tops 337.8 … 513.8), ink x 23.3 (11.7 inset); selected item fill (172,100,14) ≈ 0.68 × accent, 44.5 epx tall | MEDIUM | 14393 | CK1 @ 111, e4tA7bElN4U_t111.png | colour box + row runs |
| 4.5 | Snooze default | **10 minutes** | HIGH | 14393, 10586, 2015, 15254 | CK1 @ 47; CK2 @ 6; CK3 @ 47; CK4 | every source |
| 4.6 | Sounds page (system ringtone picker) | title **"Sounds"** Light (ascender 27.6 epx → ≈ 34 epx, subheader) at x 24.2, top 60.4; **"Use default"** (≈ 24 epx) at 133.3; 1-epx rule (lum 129) at 185.8, x 22.4 → 335.5; list rows with an outline ▷ at x 28.6 → 41.2 and the name after it, **pitch 60.4 epx**, names ≈ 24 epx | MEDIUM | 14393; 10586 | CK1 @ 104; CK2 @ 26, 28 | row runs |
| 4.7 | Sound names | EN (10586): Pure, Silk, Symmetry, Mosaic, Lattice, Ginger, Two Step, … Cruise, Sunrise, Haze, Tribe, City, Glitter, Peace …; de (14393): Klar, Seide, Symmetrie, Mosaik, Gitter, Ingwer … (same order) | MEDIUM | 10586; 14393 | CK2 @ 26, 28; CK1 @ 104 | read |
| 4.8 | Timer editor | title "ZEITGEBER BEARBEITEN" (EN "NEW TIMER", CK3; edit form EN not captured), same title geometry as 3.3; spinner **64.0 → 319.1 epx** (eight 32-epx rows); three columns split at **117.7 and 239.4 epx** (≈ W/3); band 176.0 → 208.0; column captions "Stunden · Minuten · Sekunden" centred per column at 330.7 → 342.2 (grey 165); "Zeitgebername" / EN **"Timer name"** label at 374.2, value in accent at 407.1; app bar Save (dim until changed), Delete, More | MEDIUM | 14393 | CK1 @ 212, e4tA7bElN4U_t212.png | as §3 |

## 5. World Clock pivot

| # | Item | Value | Conf | Build | Source @ t / file | Derivation |
|---|---|---|---|---|---|---|
| 5.1 | Structure | map band (dark map, grey land, day/night shading, city dots), then the **Local time** row, then one row per added city; app bar Add, Compare, More | HIGH | 14393, 2015, desktop | CK1 @ 200; CK3 @ 126; CK6 | three sources |
| 5.2 | Map band | from the header bottom **92.4 → 227.6 epx** (135 epx); land (121,121,121), night shade darker | MEDIUM | 14393 | CK1 @ 200 | profile x = 445 (map is out of scope: A11 / phase 15 H4) |
| 5.3 | Local row | full-width fill **(164,92,9) ≈ 0.64 × accent**, **228.0 → 312.9 epx (84.9 tall)**; white text: time (digit height 17.8, cap top 15.6 below the row top), "Lokale Uhrzeit" / EN **"Local time"** semibold, date line "Donnerstag, 5. Januar 2017"; ink x 18.8 epx | MEDIUM | 14393 | CK1 @ 200 | 2015: no fill, "Local time" in accent text (CK3 @ 126) |
| 5.4 | City row | time digit height **17.8 epx** (Light ≈ 25), city name semibold body (cap ≈ 11, white 228), difference line body grey (130 ≈ 51 %); ink x 18.8–19.7 epx; time cap top → city cap top 30.3, city → difference line 19.5 epx | MEDIUM | 14393 | CK1 @ 200 | row runs |
| 5.5 | Row pitch | **96.9 epx** (time tops 243.6 · 340.4 · 437.3 · 534.2) | MEDIUM | 14393 | CK1 @ 200 | |
| 5.6 | Difference line | de: "&lt;weekday&gt;, 9 Stunden zurück" / "Freitag, 2 Stunden voraus"; EN (2015): **"Today, 2 hours ahead"**, **"Today, 7 hours behind"** | MEDIUM | 14393; 2015 | CK1 @ 200; CK3 @ 126 | read |
| 5.7 | Compare mode | the app bar is replaced by a **48-epx accent strip** at the bottom (592 → 640 epx in the source, nav bar hidden) with "‹ 23 · 00 · 01 · 02 · 03 ›" hour labels and a centred up-notch at its top edge; rows show full dates; the list background turns (32,34,34); 2015 titled the page "COMPARE TIMES" | MEDIUM | 14393; 2015 | CK1 @ 171, 185; CK3 @ 138, 144 | profile + visual |
| 5.8 | City search | a text box over the top of the map band with suggestions in a list under it ("Washi" → "Washington, DC, USA"); keyboard below | LOW | 14393 | CK1 @ 149 | visual only |
| 5.9 | No-match and empty wording | UNMEASURED; the Local time row is always present, so there is no empty-list text | UNMEASURED | — | — | see U5 |

## 6. Timer pivot

| # | Item | Value | Conf | Build | Source @ t / file | Derivation |
|---|---|---|---|---|---|---|
| 6.1 | Structure per timer | big digits hh:mm:ss; control row **reset · ring play/pause · expand**; timer name; original duration; several timers stack vertically | HIGH | 14393, 2015, desktop | CK1 @ 206; CK3 @ 189; CK6 | |
| 6.2 | Digits | digit height **30.2 epx** (113.8 → 144.0), block centred (93.9 → 263.0, centre 178.5), advance 24.2 epx; **hh:mm and colons grey (91 ≈ 36 %), seconds white**, heavy weight (≈ 43 epx, INFERRED from the 0.70 ratio) | MEDIUM | 14393 | CK1 @ 206 | glyph runs |
| 6.3 | Control row | centre y **196.9 epx**; reset glyph (19.7 wide) centre x 75.1; ring centre x ≈ 179.0; expand glyph (17.9 wide) centre x 282.7 → **± 104 epx** from the centre | MEDIUM | 14393 | CK1 @ 206 | glyph runs |
| 6.4 | Ring button | outer **Ø 59.6 epx**, stroke ≈ 1.8 epx, very dark grey (lum 19–23) at rest; play glyph 12.5 × 16.9 epx, optically offset +2.6 epx right | MEDIUM | 14393 | CK1 @ 206, 244 (both give 59.6) | centre-column runs |
| 6.5 | Running state | an **accent arc** (stroke ≈ 2.7 epx) drawn on the ring from the top; play → pause glyph | MEDIUM | 14393 | CK1 @ 224 | accent at ring top (215,107,0) |
| 6.6 | Name and duration | name "Video" cap 11.5 (240.9 → 252.4), grey (115 ≈ 45 %), centred; duration "00:00:05" rows 260.4 → 272.0, grey (147 ≈ 58 %), semibold | MEDIUM | 14393 | CK1 @ 206 | |
| 6.7 | States | finished: play dim, reset bright; idle reset dim | MEDIUM | 14393 | CK1 @ 235 | |
| 6.8 | Stack pitch | ≈ **175 epx** between two timers (digit tops 246 → 550 px) | LOW | 2015 | CK3 @ 189 | 320-epx canvas assumed |
| 6.9 | Expanded (full-screen) timer | the expand button opens a **full-screen page in the accent colour** (status bar on accent, no tab header, no app bar): larger digits centred at mid-screen (hh: dimmed), the timer name above a bottom control row reset · ring pause · collapse | LOW | 2015 | CK3 @ 196, kq60r2S4iZ8_t196.png | 14393 expanded view not captured |
| 6.10 | Default name | "Timer" (phone, 2015); "Countdown" (desktop, 2015) | LOW | 2015 | CK3 @ 189; CK6 | |

## 7. Stopwatch pivot

| # | Item | Value | Conf | Build | Source @ t / file | Derivation |
|---|---|---|---|---|---|---|
| 7.1 | Digits | main digit height **32.0 epx** (131.6 → 163.6), block 69.8 → 291.7 (centre 180.7), advance 25.8; hh:mm grey (91), seconds white; **hundredths digit height 17.7 epx**, bottom-aligned (146.7 → 164.4) after the locale decimal separator ("," de, "." en) | MEDIUM | 14393 | CK1 @ 244, 249; CK3 @ 263 ("00:00:00.00") | glyph runs |
| 7.2 | Control row | centre y **218.2 epx**; reset centre x 83.2, ring Ø 59.6 (centre ≈ 179), expand centre x 274.7 → **≈ ± 96 epx** (narrower than the timer's ± 104) | MEDIUM | 14393 | CK1 @ 244 | glyph runs |
| 7.3 | Running | reset → **Flag** (lap) glyph; play → pause; accent dot / arc at the ring top (187.6 → 190.2 epx) | MEDIUM | 14393; 2015 | CK1 @ 249; CK3 @ 234 | |
| 7.4 | Laps list | header **"Laps"** (semibold) over **"Splits"** (grey); each lap: dim index, lap time (hh:mm grey, ss.cc white bold), split time under it (smaller, grey); newest on top | LOW | 2015; desktop | CK3 @ 234; CK6 | 14393 laps not captured |
| 7.5 | Expanded stopwatch | the same accent full-screen page; digits centred with hundredths, the latest lap and split under them, bottom row Flag · ring pause · collapse | LOW | 2015 | CK3 @ 247, kq60r2S4iZ8_t247.png | |

## 8. Ringing — alarm and timer

| # | Item | Value | Conf | Build | Source @ t / file | Derivation |
|---|---|---|---|---|---|---|
| 8.1 | Form | an **interactive toast banner pinned to the top of the screen**, full width, drawn over the status bar; the lock screen (alarm) or the dimmed app (timer) stays visible below; **no full-screen ring page** in any source | HIGH | 15254, 14393, unknown | CK4; CK1 @ 227; CK5 | three sources, two builds |
| 8.2 | 15254 alarm toast — extent | **0 → 248 epx** (± 3 %) | LOW | 15254 | CK4, neU9rQX7Tfc_maxresdefault.jpg | camera; 223.5 px / 0.9 |
| 8.3 | 15254 alarm toast — header | small app icon (accent, ≈ 22 epx) at x 9.4, top 35.6; title **"Alarm"** (semibold), alarm name ("Good morning"), time ("2:17") at x ≈ 42.8, line tops 35.6 · 55.6 · 73.3 | LOW | 15254 | CK4 | camera |
| 8.4 | 15254 alarm toast — snooze | label **"Snooze for"** (98.9 → 111.1, x 10.6); ComboBox **10.6 → 346.1 × 117.2 → 149.4 epx (32.2 tall)**, "10 minutes" with a chevron | LOW | 15254 | CK4 | camera |
| 8.5 | 15254 alarm toast — buttons | two **icon-over-label buttons, right-aligned**: alarm-clock glyph **"Snooze"**, ✕ **"Dismiss"**; glyph rows 177.8 → 201.1, labels 203.3 → 213.3, the pair spanning x 231.7 → 335.0 epx | LOW | 15254 | CK4 | camera |
| 8.6 | Grab handle | "=" 16.6 epx wide, rows 237.8 → 243.3, centred (178.9) | LOW | 15254 | CK4 | camera |
| 8.7 | Under the toast (alarm, locked) | the lock screen with its clock; a small alarm glyph after the lock-screen time; the nav bar with normal-looking Back / Windows / Search glyphs | LOW | 15254 | CK4 | camera |
| 8.8 | Older alarm toast | 48-epx accent app tile, title "Alarm", name, time; ComboBox "10 minutes" (no label); two side-by-side filled text buttons **"Snooze" │ "Dismiss"**; handle | LOW | unknown | CK5, FAf0TAZ73zk_maxresdefault.jpg | camera |
| 8.9 | 14393 timer toast — extent and fill | **0 → 216 epx**, fill (57,57,57), status icons drawn on it; handle 16 epx wide at 207.1 → 211.6, centre 179 | MEDIUM | 14393 | CK1 @ 227, e4tA7bElN4U_t227.png | profile x = 760 |
| 8.10 | 14393 timer toast — content | app tile **48 × 48 epx** accent with a timer glyph at x 9.8 → 58.2, y 35.6 → 83.6; title "Zeitgeber abgelaufen" semibold white at x 69.8, rows 34.7 → 50.7; body "Video" (56.0) and "23:21" (73.8), pitch 17.8, grey (150); attribution "Alarm & Uhr" caption at 92.4 (grey 140) | MEDIUM | 14393 | CK1 @ 227 | row runs |
| 8.11 | 14393 timer toast — button | one button, left: ✕ glyph (17.9 epx, rows 128.0 → 145.8) over the label "Schließen" (caption, 151.1 → 160.9), centre x 42.0 | MEDIUM | 14393 | CK1 @ 227 | |
| 8.12 | Page under the timer toast | dimmed (ghosted timer text visible) | MEDIUM | 14393 | CK1 @ 227 | visual |

## 9. Pinned tile

| # | Item | Value | Conf | Build | Source @ t / file | Derivation |
|---|---|---|---|---|---|---|
| 9.1 | Small / medium static face | alarm-clock glyph centred (medium adds the label "Alarms & Clock" bottom-left) | LOW | 2015 | CK3 @ 12 | |
| 9.2 | Small face with an alarm set | alarm-clock glyph with a small bell badge at its lower right | LOW | 2015 | CK3 @ 105 | |
| 9.3 | Wide face (next alarm) | line 1 **"7:00 AM"** large (cap ≈ 0.14 × tile height), line 2 the alarm name ("Alarm work"), line 3 the repeat days ("Mon, Tue, Wed, Thu, Fri, Sat"), all left-aligned at the tile's text inset; label **"Alarms & Clock"** bottom-left; **bell glyph bottom-right** | LOW | 2015 | CK3 @ 102, kq60r2S4iZ8_t102.png | tile-relative, compressed video |
| 9.4 | 14393 / 15063 / 15254 tile face | UNMEASURED | UNMEASURED | — | — | see U10 |

## 10. Splash

| # | Item | Value | Conf | Build | Source @ t / file | Derivation |
|---|---|---|---|---|---|---|
| 10.1 | Splash | white alarm-clock glyph **53.7 × 53.3 epx**, centred at (178.9, 318.2) on black; status bar visible | MEDIUM | 14393 | CK1 @ 21, e4tA7bElN4U_t21.png | glyph run |

---

## Strings as shipped

English is given where a source shows it (build noted); otherwise the 14393 German string and "EN not captured".

| Where | de-DE, 14393 (CK1) | en-US as captured |
|---|---|---|
| App name | Alarm & Uhr | **Alarms & Clock** (CK3 tile, CK6) |
| Tabs | Alarm · Weltuhr · Zeitgeber · Stoppuhr | **Alarm · World Clock · Timer · Stopwatch** (CK2 10586, CK3 2015; desktop "Alarms") |
| Empty alarm list | Keine Alarme | **No alarms** (CK3) |
| Editor titles | NEUER ALARM · ZEITGEBER BEARBEITEN | **NEW ALARM** · **EDIT ALARM** (CK2) · **NEW TIMER** (CK3); edit-timer title not captured |
| Countdown caption | In 7 Stunden, 42 Minuten | **In 5 hours, 57 minutes** (CK2) |
| Field labels | Alarmname · Wiederholungen · Sound · Erinnerungszeit | **Alarm name · Repeats · Sound · Snooze time** (CK2, CK3) |
| Field defaults | Alarm · Einmalig · Standard · 10 Minuten | **Alarm · Only once · Default · 10 minutes** (CK2; CK3 "default" lower-case) |
| Repeat summaries | Einmalig | **Only once · Every day · Mon, Tue, Wed, Thu, Fri, Sat** (CK2, CK3) |
| Days flyout | Montag … Sonntag | **Sunday … Saturday** (CK3) |
| Sound flyout | Nur Vibrieren · Aus meiner Musik auswählen › · Aus Klingeltönen auswählen › | **Vibrate only · Pick from my music › · Pick from ringtones ›** (CK2) |
| Sounds page | Sounds · Standard verwenden | **Sounds · Use default** (CK2); list in 4.7 |
| Loading | — | **Loading...** (CK2 @ 24) |
| Snooze choices | 5 Minuten · 10 Minuten · 20 Minuten · 30 Minuten · 1 Stunde | "5 minutes", "10 minutes" seen (CK2, CK3); "20 minutes", "30 minutes", "1 hour" not captured |
| Toggle | Ein · Aus | **On · Off** (CK2, CK6) |
| World clock | Lokale Uhrzeit · "Donnerstag, 9 Stunden zurück" · app bar "New", "Vergleichen" | **Local time** · **"Today, 2 hours ahead"**, **"Today, 7 hours behind"** (CK3) · compare page title **COMPARE TIMES** (CK3, 2015) |
| Menu | Feedback senden · Benachrichtigungseinstellungen · Info | not captured |
| Timer | Zeitgebername · Stunden · Minuten · Sekunden | **Timer name** (CK3); default name **Timer** (CK3) / Countdown (desktop) |
| Stopwatch | — | **Laps · Splits** (CK3, CK6) |
| Timer toast | Zeitgeber abgelaufen · Schließen · Alarm & Uhr | not captured |
| Alarm toast | — | **Alarm · &lt;alarm name&gt; · &lt;time&gt; · Snooze for · 10 minutes · Snooze · Dismiss** (CK4, 15254) |
| Tile | — | **Alarms & Clock**; wide face "7:00 AM / Alarm work / Mon, Tue, Wed, Thu, Fri, Sat" (CK3) |

## Segoe MDL2 glyphs the shell's icon font needs

Codepoints are from Microsoft's "Segoe MDL2 Assets icons" list (learn.microsoft.com/windows/apps/design/style/segoe-ui-symbol-font,
fetched 2026-09-23). The app's own alarm-clock, world-clock and timer icons have **no named entry** in that list.

| Glyph (seen as) | MDL2 name, codepoint | Where | Conf |
|---|---|---|---|
| alarm clock with bells | no public name — codepoint unverified (draw as vector) | Alarm tab, splash, tile, toast Snooze button | MEDIUM (shape) |
| globe with clock | no public name — codepoint unverified | World Clock tab, Compare button | MEDIUM (shape) |
| timer (clock face with a hand, crown) | no public name — codepoint unverified | Timer tab, timer-toast tile | MEDIUM (shape) |
| stopwatch | Stopwatch E916 | Stopwatch tab | MEDIUM |
| + | Add E710 | app bars | HIGH |
| list with checks | MultiSelect E762 | Alarm / Timer app bars | MEDIUM |
| ••• | More E712 | app bars | HIGH |
| floppy | Save E74E | editors | HIGH |
| bin | Delete E74D | editors (edit mode) | MEDIUM |
| pin | Pin E718 | Timer / Stopwatch app bars | MEDIUM |
| share | Share E72D | Stopwatch app bar | MEDIUM |
| ↺ | Refresh E72C (closest named match; unverified as the app's glyph) | timer / stopwatch reset | LOW |
| ▷ / ‖ | Play E768 / Pause E769 | ring buttons, Sounds list preview | MEDIUM |
| ↗↙ | FullScreen E740 | expand | MEDIUM |
| ↙↗ inward | BackToWindow E73F | collapse on the expanded page | LOW |
| flag | Flag E7C1 | stopwatch lap | MEDIUM |
| bell | Ringer EA8F | Sound value, tile badge | MEDIUM |
| vibrate | Vibrate E877 | "Vibrate only" | MEDIUM |
| ✕ | Cancel E711 | days-flyout close, toast Dismiss | MEDIUM |
| ˅ | ChevronDown E70D | toast ComboBox | MEDIUM |
| ☐ | Checkbox E739 / CheckboxComposite E73A | days flyout | MEDIUM |
| ‹ › | ChevronLeft E76B / ChevronRight E76C | compare strip | MEDIUM |

## Motion

| # | Item | Value | Conf | Build | Source @ t | Derivation |
|---|---|---|---|---|---|---|
| M1 | Tab switch by tap (Alarm → World Clock) | **no horizontal slide**: the old content is ghosted in one frame (t = 134.607), the content area is blank until 135.241 (634 ms — the page loading, not a design value), the **underline jumps to the new tab in one frame**, the new page appears in steps (local row + app bar at 135.241, map land and city rows at 135.441) | LOW | 14393 | CK1 @ 134.607 → 135.441 (30 fps), frames e4tA7bElN4U_t134.607 / _t135.241 / _t135.441 | per-frame underline tracker and content difference; 30 fps → ± 33 ms |
| M2 | Pivot swipe | UNMEASURED — no source swipes between tabs | UNMEASURED | — | — | see U12 |
| M3 | Timer toast entrance | the toast is complete within two frames (226.803 partial → 226.836 full); its form (slide or fade) cannot be resolved at 30 fps | LOW | 14393 | CK1 @ 226.8 | per-frame band tracker |
| M4 | Flyout / dropdown open, spinner inertia, toggle, ring arc | UNMEASURED | UNMEASURED | — | — | see U12 |

## UNMEASURED — each with a proposed tagged approximation

| # | What | Why not | Proposed approximation (tag it) and the pattern it derives from |
|---|---|---|---|
| U1 | The app on the governing build (15063 / 15254), every pivot | no capture found (0.3); only the 15254 alarm toast exists | Use the 14393 values (§1–§7) as the governing values, tagged "14393, unchanged-since not proven". CK4 (15254) shows the same toast family and Alarms & Clock received no announced phone redesign after 10.1704 (WindowsBlogItalia 2017-04 lists notification-settings access and pin-to-Start sections only). |
| U2 | English for 14393-only strings: timer toast title and button, the menu, the compare button label, the edit-timer title | CK1 is German | Tagged approximations: timer toast title **"Timer finished"** (no English source; keeps 8.10's layout), button **"Dismiss"** (the 15254 alarm toast's word for the same action, 8.5); menu **"Send feedback", "Notification settings", "About"** (literal translations of 1.14); compare button **"Compare"**; edit title **"EDIT TIMER"** (pattern of "EDIT ALARM", CK2). |
| U3 | 14393 alarm row pitch (more than one alarm) | CK1 shows one alarm | **88 epx** (CK2 10586), consistent with CK1's 14.3-epx top offset + 63.1-epx content + ≈ 10.6 bottom. |
| U4 | 12-hour spinner (AM/PM) | CK1 is 24-h | Add a third spinner column exactly as the timer editor's three W/3 columns (4.8) with AM/PM rows at the 32-epx pitch; the 2015 form's AM/PM field (CK3) confirms a third field exists. |
| U5 | World clock search no-match wording; search box geometry | CK1 shows a match only | Box: the R7 §3.7.3 field form (43.4 epx, accent border when focused) across the map top with 12-epx side margins; no-match line in R7 §3.5.9's style with the wording tagged approximation ("No results"). |
| U6 | 14393 laps list geometry | not captured | Two-line lap rows at the R3 A15 64-epx pitch, lap time in the stopwatch digit styling at subtitle size, split in grey caption, dim index at x 12 epx (2015 structure, 7.4). |
| U7 | 14393 expanded (full-screen) timer / stopwatch | not captured | Accent full-screen page, digits centred, controls under them (2015 structure, 6.9); values from 6.2–6.5 scaled ×1.5 as a tagged approximation. |
| U8 | Native-resolution 15254 alarm toast (type sizes, exact colours) | camera thumbnail only | Geometry of 8.2–8.6 with the type metrics of the 14393 timer toast (8.10): title semibold body, body lines at a 17.8-epx pitch, caption labels; fill (57,57,57) from 8.9. |
| U9 | Snooze choices inside the toast ComboBox | never opened in a source | The editor's list (4.4): 5 / 10 / 20 / 30 minutes, 1 hour, preselected with the alarm's snooze time. |
| U10 | Tile face on 14393+ | not captured | The 2015 wide face (9.3) and bell badge (9.2) on the R3 C3 tile-text insets (7.5-epx inset, 16-epx caption pitch) — phase 15 H14 stays [accept]. |
| U11 | Ring sound, vibration pattern, ring timeout, missed-alarm notification | audio / behaviour not visible | Phase 15's own approximations stand (H6–H8). |
| U12 | Motion: pivot swipe, flyout open, dropdown, spinner fling, toggle, ring arc, toast entrance form | no ≥ 55-fps source; 30-fps source shows none of these clearly | Pivot swipe: X13's 250 ms settle (phase 10) tagged approximation; flyout / dropdown: R7 §3.6.4's grow-from-anchor 233 ms ease-out; tab tap: the measured no-slide jump (M1); toast entrance: R7 §4.5.1's volume-panel grow-down 217 ms, tagged. |
| U13 | Accent shades for the spinner band, dropdown selection and local row | one capture, one accent | Accent at 0.6 opacity over the page (UWP list-accent-low), INFERRED from the 0.61–0.68 channel ratios (3.6, 4.4, 5.3). |
| U14 | 14393 light theme | CK1 is dark | CK2's 10586 light values (1.9, 1.11), tagged. |

## Gaps for the phase doc

| # | Phase 15 line | What R11 found | Consequence |
|---|---|---|---|
| G1 | Decisions "Ringing" (ring page, `showWhenLocked`), "Bars" (ring page with the lock-screen nav variant), H5, E4, E10, E23 | W10M had **no full-screen ring page**. Alarms (15254) and timers (14393) ring as a **toast pinned to the top, ≈ 216–248 epx tall**, over the lock screen or the dimmed app: alarm = icon, "Alarm", name, time, "Snooze for" ComboBox (default 10 minutes), right-aligned icon buttons **Snooze** / **Dismiss**; timer = 48-epx app tile, title (German "Zeitgeber abgelaufen"; English not captured), name, time, one ✕ button. The lock-screen nav bar in CK4 shows normal glyphs. | A decision for Jeremy: keep the full-screen ring page as a P4 design ([accept]) or draw the W10M toast form ([fidelity] against 8.1–8.12). The Android mechanics (full-screen intent, `showWhenLocked`) can host either. |
| G2 | Decisions "Ringing": snooze 5 / 10 / 20 / 30 min; H6 | Choices are **5, 10, 20, 30 minutes and 1 hour**; default 10 minutes; the ring toast lets the user change the snooze length at ring time | Add 1 hour; add the ring-time snooze choice or record its absence as an approximation. |
| G3 | "the four pivots on the shell's pivot geometry (phase 10 task 6's, until R11)"; E10 "four pivot headers" | The headers are **tabs**: icon over caption label, 64.5-epx pitch centred, accent underline 64.4 × ≈3.7 epx, grey band 24.0 → 92.4 epx (§1). English labels **World Clock** (capital C), not "World clock". | Replace the phase-10 text pivot block for this app with §1's tab header; fix the label. |
| G4 | E10 "the pivot settle 250 ± 17 ms (X13)" | Tapping a tab does **not slide** (M1, LOW); no swipe was captured | Assert the jump for taps; keep X13 for swipes only as a tagged approximation (U12). |
| G5 | Fidelity line (2): status bar 28 epx (R3 C4) | In-app status bar measured **24.0 epx** (1.1), as r8 §1.1 (Groove) and the calculator section found | Flag to the lead: the phase's 28-epx bar and W10M in-app 24-epx bar disagree; not settled here. |
| G6 | Editor "time, repeat days, sound, snooze length, name" | 14393 order: **time spinner, Alarm name, Repeats, Sound, Snooze time**, rows at 64 epx, values in accent (§3); Sound opens a flyout (Vibrate only / Pick from my music / Pick from ringtones) and the system Sounds page | Follow §3's order and form. "Pick from my music" is an offline, local-file feature the phase does not list (Jeremy can ask). |
| G7 | World clock: "a difference line ('+8 hours', 'tomorrow')"; rows at 44 epx (R3 C2 stand-in) | Third line reads **"Today, 2 hours ahead" / "Today, 7 hours behind"** (weekday name instead of "Today" when the day differs, 14393); rows **96.9 epx**; the local row is accent-filled "Local time"; a **compare mode** exists | Use §5's wording pattern and pitch; decide whether compare mode is in (not in the phase's scope list). |
| G8 | Empty-list line in R7 §3.5.9's style (white subtitle, x 11.7, cap top 70.4) | The Alarm empty state is **"No alarms", grey Light, cap 17.8 epx at x 9.8, cap top 113.8** (2.1) | Use 2.1 for this app. |
| G9 | Timers "several at once, each named" | Confirmed; each timer has reset / ring / **expand (full-screen view)**, name and original duration (§6); default name "Timer" | The expand view is not in the phase (U7). |
| G10 | Stopwatch "laps" | Lap = Flag button replacing reset while running; header "Laps / Splits"; app bar **Pin** and **Share** (§7) | Share and pin-a-stopwatch are not in the phase. |
| G11 | Tiles H14 "next alarm's time and day on its face" | 2015 wide face = time, alarm name, repeat days, "Alarms & Clock" label, bell glyph; small tile = glyph + bell badge (§9); timers and the stopwatch can be pinned as their own tiles (Pin, 1.13) | H14's approximation should follow §9; secondary tiles are out unless ruled in. |
| G12 | E4 "Snooze … notification reads 'Snoozed until h:mm'" | No source shows a snoozed-state notification | Stays an approximation (H6). |
| G13 | Motion rows (C-5 `[motion]` clock) | No ≥ 55-fps source exists; P6's phone re-measure has nothing from R11 to re-measure except M1 (LOW) | P6 has no R11 value at ≤ 17 ms tolerance. |

## Tally

| HIGH | MEDIUM | LOW | UNMEASURED | Rows |
|---|---|---|---|---|
| 13 | 70 | 23 | 5 | 111 |

Counted by parsing the Conf column of every row in §1–§10, the MDL2 table and the Motion table; a row with two levels
counts at the lower. UNMEASURED table rows U1–U14 are not counted again.
