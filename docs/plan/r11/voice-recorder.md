# R11 §Voice Recorder — Voice Recorder (Windows 10 Mobile) measurements

Scope: the record page (empty and recording states), the recordings list, the playback page, their menus and strings.
Gates phase 15's FINAL (phase-15-inbox-clock-calculator-recorder.md: build task 7, E24, H3, H12). Governing build: the
final release (15063 / 15254). Format and confidence rules: r8-groove-measurements.md, via the shared R11 conventions.

## Summary — what was and was not established

**Structure is settled; numbers are not governing-build.** Voice Recorder on W10M is **one page with no header, no pivot
and no ≡ pane**. With no recordings it shows a large centred accent record button (and, by 10586, the line "No
recordings found" at the top-left). Recording turns the button into a stop button with two thin level rings around it, an
hh:mm:ss timer above it whose leading zero fields are dimmed, pause and flag (marker) buttons under it and a row of
"⚑ time" markers at the bottom. After stop the same page becomes the list: date groups with accent headers ("Today",
"This week", "Last week"), two-line rows (name; date + time, duration right-aligned), and the record button shrunk and
docked bottom-centre, sitting on a minimal (dots-only) app bar. By 10586, when call recordings exist, a "Search
Recordings" box and a "Showing Call recordings" filter line head the list and call rows carry a 48-epx contact avatar and
"Incoming / Outgoing m:ss". A row tap opens a separate playback page: centred name and date, a large grey play disc, a
flag button, a scrubber with elapsed (left) and total (right) labels, and a 48-epx app bar Share · Trim · Delete ·
Rename (Contact for call recordings) · "…" (Settings, Feedback, Open file location). Long-press on a row gives Share /
Delete / Rename / Open file location. Sharing uses the system share page.

**Sources and their limits.** No screen recording of Voice Recorder on any build was found. The numbers come from
(a) Microsoft's own Store screenshots, which are exact-epx renders (100 % desktop, window 320 epx wide, the same width
as a WVGA phone canvas) but are **April-2015 pre-release mockups**; (b) a light-theme camera video of **build 10166**
(app 10.1507.7020.0) on a 320 × 533-epx WVGA phone, whose screen edges are visible, calibrated per row; (c) three
dark-theme camera videos of **≈10586** Lumia 950-class phones, calibrated with the nav bar's back↔search glyph distance
(224 epx, measured here from native screenshots). Where (a) and (b)/(c) agree the value is MEDIUM; the record-page button
is where they disagree most (desktop 113.5 epx vs phone 96 epx). **Nothing was found for 14393, 15063 or 15254**; the
app received a 10.1705 "Neon" update in May 2017 for Insider Fast (RS3) only (WindowsBlogItalia, 0.3), so the 15063/15254
look is the 10586 structure by inference. Trim, rename, delete confirmation and the pause state were never shown.
All motion is from 30-fps camera footage of 10166 (LOW) or UNMEASURED.

---

## 0. Method, sources and calibration

### 0.1 Unit convention and calibration

`epx` is the project unit, 1/360 of the screen width on the Lumia 950 canvas (r8 §0.1). W10M UWP layouts are fixed in
real epx (R3 §0.2, R8 §0.1), so each source below is converted to its own real epx and reported as epx; values that are
fractions of width say so.

| Source | Device, canvas | px per epx | How measured |
|---|---|---|---|
| D0, D0w, D1, D2 (Store) | Windows 10 desktop at 100 % (taskbar 40 px, title bar 32 px, caption glyphs at a 48-px pitch) | **1.000** | Window content x 538–857 = **320 epx** wide, y 100–666 = 567 epx tall (narrow D0/D2); D1 wide window 1024 × 600 with a **512-epx** detail pane (x 700–1211). Edges by half-max crossings |
| Y (10166) | 480 × 800 phone at 150 % → **320 × 533 epx**, hardware keys (no nav bar) | ≈1.10–1.13, per row | Screen is bright on a black background: left/right edges fitted per frame, x = (x−l(y))/(r(y)−l(y))·320, y = ∫320/W(y) dy from the screen top (R7 P4's method). Check: calibrated screen height 531.5 / 533.2 / 537.8 / 537.0 epx on four frames vs 533.3 nominal (±1 %) |
| A, Q, N (≈10586) | Lumia 950-class, dark theme; A and Q presumed 400 % (360-epx canvas); N runs non-default display scaling (≈375 %: its nav-glyph distance is 0.587 W, between the 950's 0.622 W and the 950 XL's 0.544 W) | A 1.196–1.221; N 0.904 | **Nav ruler:** back↔search glyph centres are 272.0 / 1167.5 px of 1440 on the Lumia 950 at 400 % and 328.0 / 1111.5 px on the 950 XL at 350 % (docs/plan/r11/src/calculator/gsm950_086.jpg, gsm950xl_084.jpg) — **223.9 epx on both**, so fixed. Valid only near the nav bar: A and Q are hand-held and tilted, so elements > ~150 px from the ruler carry ±5–8 %. A t256 uses the 32-epx UWP text box as its local ruler (1.125 px/epx) |

Camera bloom inflates bright-on-dark blobs by ≈1–2 px per edge; Y and N values below are bbox values with that stated.
Timing tolerance: ±1 frame = ±33 ms at 29.97 fps.

### 0.2 Sources

All stored files are unaltered and live in docs/plan/r11/src/voice-recorder/ (local only). Video frames were extracted
with `ffmpeg -ss <t> -i <video> -frames:v 1` from copies downloaded earlier in this R11 run (YouTube now refuses
downloads from this machine); sha256 is the first 16 hex digits of the stored file.

| ID | URL @ t | Date | Pixels / fps | Device, canvas | Build / app version (how known) | Stored file | sha256 |
|---|---|---|---|---|---|---|---|
| D0 | https://store-images.microsoft.com/image/apps.40562.9007199266246865.6703ebb8-53df-4f62-ba0e-907a6e05cee8.a98c8de0-4c87-46ab-a39b-9ecdb1a4b0f0 (Store listing 9WZDNCRFHWKN "Windows Voice Recorder", Desktop/0, caption "Start a recording with the press of a button.") | listing current (catalog modified 2026-08-31); taskbar in the image reads 4/2/2015 | 1366 × 768 | desktop 100 %, window 320 × 567 epx | **pre-release**, Windows 10 preview era (taskbar date); light theme | store_9wzdncrfhwkn_desktop0_recording.png | 729e6bd34592c87b |
| D0w | same slot as archived 2017-07-01: http://web.archive.org/web/20170701191302/https://www.microsoft.com/en-us/store/p/windows-voice-recorder/9wzdncrfhwkn → image apps.20787.9007199266246865.bd240c9a-15ea-4ebc-8609-994e789e3731.bf4ba628-6868-4737-bf1b-11256180bd9e | archived 2017-07-01; taskbar 4/2/2015 | 1366 × 768 | as D0 | as D0 — same frame with **two level rings** instead of D0's filled halo | store_9wzdncrfhwkn_wb20170701_recording_rings.png | 3351a777e4d52f28 |
| D1 | https://store-images.microsoft.com/image/apps.21550.9007199266246865.df745838-28cf-4f03-a3cf-006430b9bfd9.4e2b686e-e974-40d8-ae4f-cc1bb3212027 (Desktop/1, "Play back, trim, rename and delete your recording.") | current = 2017 archive (identical hash) | 1366 × 768 | desktop 100 %, wide 3-pane window, detail pane 512 epx | as D0 | store_9wzdncrfhwkn_desktop1_playback.png | 2e54622a8064eb07 |
| D2 | https://store-images.microsoft.com/image/apps.17775.9007199266246865.b02eda5d-f5e0-4e5a-a0c2-76631fd19919.19873092-5e62-4fa4-8f27-8b2c319434af (Desktop/2, "Choose from a list of all your recordings.") | current = 2017 archive (identical hash) | 1366 × 768 | desktop 100 %, window 320 × 567 epx | as D0 | store_9wzdncrfhwkn_desktop2_list.png | 6f452ad4b5600233 |
| Y | https://www.youtube.com/watch?v=ypkpcWrxx2M (WPuhelin, "Windows 10 Mobile (Build 10166): Calculator, Alarms, Clock, Sound Recorder etc.") @ 82, 86, 95, 108, 110, 115.5 s | 2015-07-26 | 1280 × 720 @ 29.97 | camera, head-on; WVGA phone 320 × 533 epx; light theme, Finnish UI | **10166** (title); Voice Recorder **10.1507.7020.0** (its About page @ 86 s) — pre-release | ypkpcWrxx2M_t82.png, _t86.png, _t95.png, _t108.png, _t110.png, _t115.5.png | 7392f71158b30ef1, 66c85f85a36ea3ec, 5993e35c0d59baab, a48e7f638e8029c0, e09da058c08ab3d4, e882d15cc33753ad |
| Q | https://www.youtube.com/watch?v=97QSgcf0jaI (Windows Central, "Lumia 950 & native two-way call recorder") @ 137, 165, 168, 172 s | 2015-11-23 | 1920 × 1080 @ 29.97 | camera, hand-held, tilted; Lumia 950; dark, English | ≈**10586** (launch-week Lumia 950; not on screen) | 97QSgcf0jaI_t137.0.png, _t165.0.png, _t168.0.png, _t172.0.png | ac4ce0850b60a18a, 488f08b70b862939, 01c60f29db04c810, 22739f21198aa579 |
| A | https://www.youtube.com/watch?v=ApNBz_UnMzY (Shaan Haider, "How to Record Phone Calls on Microsoft Lumia 950, 950 XL, 650, 550 or ANY Windows 10 Phone") @ 254, 256, 279 s | 2016-06-27 | 1280 × 720 @ 29.97 | camera, hand-held; Lumia 950-class; dark, English | ≈10586.x by date (not on screen) | ApNBz_UnMzY_t254.0.png, _t256.0.png, _t279.0.png | 9ee74b64483eabe5, a55acf602f549c40, 0e1aac4d470a536c |
| N | https://www.youtube.com/watch?v=NVfKiquSWTo (Nokiapoweruser, "Windows 10 Mobile Tutorial: Setup Call Recording & Vibrate when call picked") @ 43 s | 2016-03-10 | 1280 × 720 @ 30 | camera, near head-on; dual-SIM Lumia 950 / 950 XL at ≈375 %; dark, cyan accent, English | ≈10586 by date | NVfKiquSWTo_t43.0.png | 37aac035d863e6e5 |
| MS | https://learn.microsoft.com/en-us/windows/apps/design/style/segoe-ui-symbol-font (Segoe MDL2 Assets icon list) | read 2026-09-23 | — | document | — | not stored | — |

Scratch evidence (not committed): contact sheets of the four videos at 1–2 fps and native-fps crops of the record-start
and stop sequences; scripts for per-row calibration (screen-edge fit), blob listing in epx, radial ring profiles and
half-max edge crossings.

### 0.3 Searched without finding

- **Any screen recording of Voice Recorder**, any build. yt-dlp searches (this R11 run): "Windows 10 Mobile Voice
  Recorder app", "Registratore vocale Windows 10 Mobile", "Sprachrekorder Windows 10 Mobile", "Grabadora de voz Windows 10
  Mobile Lumia", "диктофон Windows 10 Mobile", "Lumia Voice Recorder app Windows 10 Mobile", "Lumia 950 XL call recorder
  playback voice recorder" — results are desktop Voice Recorder tutorials, third-party WP8 recorders and camera videos of
  call recording. Checked by contact sheet without a Voice Recorder screen: 1jEym4gclB8 (reaches the in-call Record button
  only), XrcggfMxvf4, gl4xwft2UxU (sheets 2–3), 3ZQxepVak9E (sheets 1–3), OGNKtVEXdLE (sheet 3; sheets 1, 2, 4 not
  reviewed; 10572 camera). Rejected: 0bUqTJPyfoY (thumbnail shows an outlined ring button with an hours/minutes/seconds
  timer — not W10M Voice Recorder's filled accent button; a WP8-era or third-party recorder).
- **A 14393 / 15063 / 15254 capture.** None. WindowsBlogItalia reports Voice Recorder 10.1705 (Neon elements) for
  Redstone 3 Insider Fast only (https://www.windowsblogitalia.com/2017/05/registratore-vocale-neon/); NokiaPowerUser reports
  10.1607.1931.0 as a call-recording bug-fix release. Neither article carries a phone screenshot (WBI shows the app icon
  only).
- **Phone screenshots in the Store listing.** The current catalog (displaycatalog, 9WZDNCRFHWKN) and the 2017 archived
  page list only the three 1366 × 768 desktop screenshots.
- **Trim page, rename dialog, delete confirmation, pause state, the filter behind "Showing …", Settings on 10586+, the
  Start tile face.** Not shown in any source above.

### 0.4 Shared values cited, not re-measured

Drawn status bar 28 epx and nav bar 48 epx (R3 C4; phase 01 X6). App bar 48.2 ± 1 epx on the nav bar, buttons at a 68-epx
pitch, "…" button 48 epx flush right with dots 24.7 epx from the right edge (R7 §3.5.8; A agrees, 4.24). Flyout / context
menu 44-epx items, 8-epx padding, fill (40,40,40), 1-epx (71,76,70) border (R7 §3.6.2). Top-anchored dialog 194 epx,
fill (74,74,74), page dimmed to (2,2,2) (R7 §1.3.9). Empty-list line in white subtitle type at x 11.7 epx, cap top 70.4 epx
(R7 §3.5.9; N agrees, 2.11). UWP toggle 44 × 20 epx (R3 C1). Type ramp: caption 12, body 15, base 15 semibold, subtitle 20,
title 24 semilight, subheader 34 light (R1 §5.1). List row press fill (63,68,64) (R7 §3.5.7). Sliders (X23, R3 A20).

---

## 1. App structure and navigation

| # | Item | Value | Conf | Build | Source @ t / file | Derivation |
|---|---|---|---|---|---|---|
| 1.1 | Page model | **One page, no pivot, no ≡ pane, no page title.** It has two states: the record state (big centred button) and the list state (list plus a small docked button). Playback is a separate page | HIGH | 2015 pre-release; 10166; ≈10586 | D0, D2, Y @ 82/110, A @ 254, Q @ 137, N @ 43 | every source shows content starting directly under the status bar (or the desktop title bar) with no header text |
| 1.2 | Record state → list state | After Stop the record page keeps the final time for ≈0.6 s, resets the timer to 00:00:00 for ≈0.4 s, then becomes the list with the new row at the top | LOW | 10166 | Y @ 108.4–109.4 (frames 118–150 of the 104.5-s sequence) | 30-fps camera sequence (§7) |
| 1.3 | Which state shows on launch | List state when recordings exist, record state (with "No recordings found" on ≈10586) when none | MEDIUM | 10166; ≈10586 | A @ 250–254 (launch → list), N @ 38–43 (launch → empty record state), Y @ 80–82 | launch sequences |
| 1.4 | Wide layout (desktop / Continuum only) | Three panes: button pane 192 epx (fill (220,220,220)) · list 320 epx (fill (230,230,230)) · detail pane (242,242,242); selected row marked by a 3-epx accent bar at its left | MEDIUM | 2015 pre-release | D1 | row y 110: x 188 / 380 / 700 / 1212 transitions; accent x 380–382 |
| 1.5 | Row tap | Opens the playback page (phone) or fills the detail pane (wide) | HIGH | 2015; ≈10586 | A @ 267–268, Q @ 150, D1 | footage + D1 |
| 1.6 | Row long-press | Context menu **Share / Delete / Rename / Open file location** ("Jaa / Poista / Nimeä uudelleen / Avaa tiedostosijainti"), a bordered flyout opening under the pressed row, not full width | MEDIUM | 10166 | Y @ 113.5–116.5, file ypkpcWrxx2M_t115.5.png | read from frames; item pitch ≈46 ± 2 epx (R7 §3.6.2's 44 is the measured W10M value to use) |
| 1.7 | List-state "…" | Minimal app bar (dots only) at the bottom; menu **Settings / Feedback** ("Asetukset / Palaute") | MEDIUM | 10166 | Y @ 85 (menu open), Y @ 82 (dots) | read |
| 1.8 | Settings destination | An About-style page: app name, "Julkaisija: Microsoft Corporation", "Versio 10.1507.7020.0", licence-terms link, privacy link, copyright, **"Mikrofonin asetukset"** (microphone settings) link | LOW | 10166 only | Y @ 86, file ypkpcWrxx2M_t86.png | read; 10586+ content UNMEASURED (U13) |
| 1.9 | Share | Hands off to the **system share page** ("Share" title; groups "recent" / "more"; Messaging, Outlook Mail, Bluetooth, OneDrive, Tap to share (NFC), WhatsApp) | MEDIUM | ≈10586 | Q @ 172, file 97QSgcf0jaI_t172.0.png | read; system UI, not the app's |
| 1.10 | "Open file location" | Opens File Explorer at the recording's folder, file selected; the folder is "Äänitallenteet" (Sound recordings) under "Tämä l[aite] > … " | LOW | 10166 | Y @ 119–127 | read from frames (not stored) |

## 2. Record page (empty and recording states)

| # | Item | Value | Conf | Build | Source @ t / file | Derivation |
|---|---|---|---|---|---|---|
| 2.1 | Record button (empty state) | **Filled accent disc, diameter 96 ± 2 epx**, white microphone glyph, centred on W/2 | LOW | 10166 (≈10586 agrees within ±4 %) | Y @ 82 (97.4 × 96.9 bbox); N @ 43 (83 × 84 px ÷ 0.904 = 92 ± 4); file ypkpcWrxx2M_t82.png, NVfKiquSWTo_t43.0.png | Y: per-row calibration, bbox less ≈1 epx bloom; N: nav ruler. D0 (pre-release) disagrees: 113.5 epx (2.3) |
| 2.2 | Same disc while recording (stop button) | **Diameter 96.0 ± 1 epx**, same centre as 2.1 — the button does not move or resize when recording starts | LOW | 10166 | Y @ 92–103 (96.0, 96.3, 96.1, 97.5, 97.1, 96.8, 97.6 on 7 frames); file ypkpcWrxx2M_t95.png | radial half-max edge on a 360° luminance profile |
| 2.3 | Desktop mockup disc | 113.5 epx (x 641.25–754.75, y 311.25–424.75), centre (698.0, 368.0) in a 320-wide window | LOW | 2015 pre-release | D0; D0w 113.06 | half-max crossings; superseded by 2.2 on phones |
| 2.4 | Vertical placement | Disc centre **276.5 ± 3 epx** below the screen top of a 533-epx canvas (51.8 %); on a 567-epx desktop window 268 epx below the content top (47.3 %). On a 16:9 canvas: UNMEASURED (U9) | LOW | 10166; 2015 | Y @ 82 (275.7), @ 95 (276.5); D0 | calibrated centre |
| 2.5 | Microphone glyph | ≈**22 × 34 epx** box (Y 23.3 × 35.6, N 23 × 35, both bloom-inflated), white | LOW | 10166; ≈10586 | Y @ 82; N @ 43 | white pixels inside 0.8 r |
| 2.6 | Stop glyph | White filled square ≈**21 epx** (Y 22.8 × 21.8 with bloom) = 0.22 of the disc; D0: 26 epx in a 113.5 disc (0.23) | LOW | 10166; 2015 | Y @ 95; D0 (x 685–710, y 355–380) | same method |
| 2.7 | Level rings while recording | **Two 1-epx rings** concentric with the disc, radius changing frame to frame with input level: inner **109–122 epx**, outer **123–150 epx** diameter over 9 frames; none before recording starts | LOW | 10166 | Y @ 92–103, file ypkpcWrxx2M_t95.png | radial profile minima; D0w shows the same two rings (133 / 164 epx around 113); D0 shows instead a filled halo 169 epx of accent at 30 % over the page — two renders of the same level meter |
| 2.8 | Timer | **hh:mm:ss**, always 8 characters ("00:00:03"); **leading zero fields dimmed** (Y: "00:00:" light grey, seconds dark; D0: "00:" in (194,194,194) = black at 20 % on (242,242,242), "37:44" black) | MEDIUM | 10166; 2015 | Y @ 91–108; D0 | two sources agree on the rule |
| 2.9 | Timer type | Digit height **24 epx** (D0 exact; Y 25.4–25.9 with bloom), digit pitch 19 epx → **Segoe UI Light ≈34 epx** (subheader) ; centred on W/2 | MEDIUM | 2015; 10166 | D0 (y 188–211, pitch 635→654); Y @ 95, 100, 109 | digit height ÷ 0.70 cap ratio; thin strokes |
| 2.10 | Timer position | Digit centre **141.5 epx above the disc centre** (Y); D0 168.5 above (pre-release, larger disc) | LOW | 10166; 2015 | Y @ 95; D0 | calibrated |
| 2.11 | Empty-state line | **"No recordings found"** at the top-left: left edge aligned with the status-bar content (≈12 epx), cap top ≈71 epx from the screen top, cap ≈13–14 epx (subtitle class); colour a light, possibly accent-tinted white ((157,201,255) captured vs (220–238,255,255) for the white status glyphs in the same frame) | LOW | ≈10586 (absent on 10166) | N @ 43, file NVfKiquSWTo_t43.0.png | nav ruler; matches R7 §3.5.9's empty line (x 11.7, cap top 70.4) — use R7's numbers |
| 2.12 | Pause and flag buttons (recording) | Pause glyph (two 2-epx bars, 9 × 16 epx) left and flag glyph (17 × 20 epx) right, **centred ±46.7 epx either side of W/2** (Y); D0 ±40 epx; row centre **139 epx below the disc centre** (Y; D0 164.5) | LOW | 10166; 2015 | Y @ 95, 100; D0 (pause x 652–660, flag x 729–745, y 523–542) | calibrated blobs |
| 2.13 | Flag pressed | A grey circular fill appears behind the flag glyph while pressed; a marker is added to 2.14 | LOW | 10166 | Y @ 105.2–105.3 (sequence frames 23–25) | visual, 30 fps |
| 2.14 | Markers row | "⚑ mm:ss" entries in one centred row at the bottom: flag glyph 13 × 15 epx then the time in body type; D0 flag (133,133,133), time (97,97,97); Y flags accent. Row centre **211.5 epx below the disc centre / 45 epx above the screen bottom** (Y; D0 248 / 51). Order: oldest first on Y ("00:07 00:11"), newest first on D0 ("33:56 24:17") | LOW | 10166; 2015 | Y @ 104, 108, file ypkpcWrxx2M_t108.png; D0 (y 609–623) | read + blobs |
| 2.15 | App bar while recording | None: no "…" in the recording state (Y, D0); the minimal bar returns after Stop | MEDIUM | 10166; 2015 | Y @ 92–108; D0 | absence in two sources |
| 2.16 | "…" in the empty state | Dots centred ≈11 epx above the screen bottom and ≈22 epx from the right edge (a minimal 24-epx bar with a 48-epx "…" button) | LOW | 10166 | Y @ 82 (dots y 520.9–523.6, x 294.1–302.1) | calibrated |

## 3. List state

| # | Item | Value | Conf | Build | Source @ t / file | Derivation |
|---|---|---|---|---|---|---|
| 3.1 | Search box | **"Search Recordings"** placeholder text box, full width with ≈12-epx margins, **32 epx tall**, first element under the status bar; present on ≈10586 with call recordings; absent on 10166 and in D2 | MEDIUM | ≈10586 | A @ 256 (x 615–992, border centres y 119 / 152), Q @ 137; file ApNBz_UnMzY_t256.0.png | used as the local ruler (UWP TextBox MinHeight 32) |
| 3.2 | Filter line | **"Showing" + "Call recordings"** (the second part in accent, a link), body type, left-aligned under the search box | MEDIUM | ≈10586 | A @ 256, Q @ 137 | read in both; choices behind it UNMEASURED (U6) |
| 3.3 | Group headers | Accent, base (15 semibold) class, left margin 12 epx, followed by a 1-epx full-width rule (D2: (217,217,217) on (242,242,242), x 550–845 = 12-epx margins, 19 epx below the header cap top). Seen: **"Today", "This week", "Last week"** ("Tänään") | MEDIUM | 2015; 10166; ≈10586 | D2 (cap 11 epx, y 121–131, rule y 140); Y @ 110; A @ 256; Q @ 137 | D2 exact; structure in all four |
| 3.4 | Plain recording row (no avatar) | Line 1 **name** in body 15 (cap 11 epx), line 2 **date + time** in caption 12 grey (digits 9 epx), **duration right-aligned on line 2** with a 12-epx right inset. Line-2 top 22 epx below line-1 cap top (D2 22; Y 22.2). **Pitch 56 epx** (D2: title cap tops 157, 213, 269, 325) | MEDIUM | 2015; 10166 | D2; Y @ 110 (file ypkpcWrxx2M_t110.png) | D2 exact, Y agrees on the 22-epx line offset |
| 3.5 | Header ↔ row spacing | Header cap top → first row cap top **36 epx**; last row cap top → next header cap top **60 epx** (D2). Y shows 48 epx header → row | LOW | 2015; 10166 | D2 (121→157, 325→385, 385→421); Y @ 110 (43.5→91.6) | the two disagree by 12 epx |
| 3.6 | Call-recording row | Circular **contact avatar ≈48 epx** (47 ± 2 on A; photo or initial on a coloured disc) at the left margin; **name** base/semibold; **date + time** under it; right-aligned **"Incoming m:ss" / "Outgoing m:ss"** on line 2; pitch ≈**74 epx** (≈1.55 × avatar) | LOW | ≈10586 | A @ 256 (avatar x 612–664 px at 1.125 px/epx); Q @ 137 (three rows) | local ruler; Q pitch from avatar-relative distances in a tilted frame |
| 3.7 | Date and duration formats | Row date: short date + short time, **no seconds** ("6/21/2016 12:15 AM", "11/23/2015 2:45 PM", "26.7.2015 12.25"); D2 mockup shows seconds ("4/11/2014 12:15:04 AM"). Duration **m:ss**, **h:mm:ss** from one hour ("0:13", "4:24", "1:07:03") | MEDIUM | ≈10586; 10166; 2015 | A @ 256, Q @ 137, Y @ 110, D2 | read |
| 3.8 | Docked record button | Accent disc **≈76 ± 3 epx** (Y 78.0 × 75.5 with bloom; A 90 px ÷ 1.221 = 73.7; D2 exact **78.0**), centred on W/2, its bottom edge on the top of the minimal app bar: centre **60.6 epx above the screen bottom** (Y, no nav bar) / **63 epx above the nav bar top** (A); D2 51.5 above the window bottom | MEDIUM | 10166; ≈10586; 2015 | Y @ 110; A @ 254; D2 (x 658.62–736.64, y 576.51–654.51) | D2 exact + two phone cameras within ±3 epx |
| 3.9 | Docked button glyph | White microphone ≈18 × 26 epx on phones (Y 19.3 × 27.6 with bloom); D2 23 × 32 epx | LOW | 10166; 2015 | Y @ 110; D2 | white pixels inside 0.8 r |
| 3.10 | List "…" | Dots only (minimal bar), dots centred **≈12 epx above the nav bar top** (A: 43 px above the nav glyph row = 35 epx) at the right; D2 dots 16 × 4 epx, centre 20 epx from the right, 13.5 epx above the bottom | MEDIUM | ≈10586; 2015 | A @ 254 (dots x 986–1003, y 537–540); D2 | nav ruler; D2 exact |
| 3.11 | Pressed row | Full-width grey fill over the row while held (light theme (≈205) on the page) | LOW | 10166 | Y @ 112–113 | visual; use R7 §3.5.7 for the dark fill |

## 4. Playback page

| # | Item | Value | Conf | Build | Source @ t / file | Derivation |
|---|---|---|---|---|---|---|
| 4.1 | Header block | No page title. **Name** centred, base 15 semibold (cap 11 epx); **date + time** centred under it in caption 12 grey, its top 22 epx below the name's cap top | MEDIUM | 2015; ≈10586 | D1 (y 120–130 / 142–151, centred at 955.5 = pane centre); A @ 279; Q @ 168 | D1 exact; phones agree on structure |
| 4.2 | Markers row | "⚑ m:ss" entries centred, accent flag glyph 13 × 15 epx + time in body 15, entry pitch ≈63.5 epx, between the header and the play disc | LOW | 2015 only | D1 (y 246–260) | no phone playback with markers found |
| 4.3 | Play / pause disc | **Diameter 96.0 epx**, fill (230,230,230) on (242,242,242) (= SystemChromeMedium #E6E6E6 light) with an outlined play triangle (22 × 30 epx) or pause glyph; centred on W/2. Phones: a grey disc on black (colour U8) | MEDIUM | 2015; ≈10586 | D1 (coverage-weighted diameter 95.98, centre 955.50, 366.50); A @ 279; Q @ 165 | D1 exact; same form on phones |
| 4.4 | Flag (add marker) | Flag glyph 17 × 20 epx centred on W/2 below the disc: D1 111 epx below the disc centre; A ≈99 epx (LOW, tilted) | LOW | 2015; ≈10586 | D1 (y 468–487); A @ 279 (y 418–442 px) | — |
| 4.5 | Scrubber row | Left label **elapsed**, right label **total duration** (A: 0:09 → 0:13 total 0:13; Q: 0:07 / 0:11 → 0:39 total 0:39), caption 12 (digits 9 epx); track **2 epx**, played part accent, unplayed (204,205,206) light; thumb an **accent ring 24 epx, hollow** (D1 x 809–832, y 572–595; A: accent ring with dark centre); marker **dots 6 epx** accent on the track | MEDIUM | 2015; ≈10586 | D1; A @ 279; Q @ 168 | right label constant while left advances in two recordings |
| 4.6 | Scrubber geometry | D1 (512-wide pane): labels inset 12 epx, label↔track gap 6 epx, track x 738–1171, thumb centre 34.5 epx above the app bar top. A (phone): track ≈227 epx long with ≈22.6-epx label↔track gaps each side, track centre ≈75 epx above the app bar top | LOW | 2015; ≈10586 | D1; A @ 279 (labels x 556–587 / 912–943, track 614–885 px at 1.196 px/epx) | the two layouts differ; phone values carry ±6 % |
| 4.7 | App bar | 48-epx CommandBar on the nav bar, right-aligned buttons at a **67 ± 2 epx** pitch plus "…" 58 epx from the last button (A) — R7 §3.5.8's 68 / 48 layout. Order: **Share · Trim · Delete · Rename · "…"** (D1, pencil glyph); for a call recording **Share · Trim · Delete · Contact · "…"** (A, Q). Labels shown when expanded: "Share", "Trim", "Delete", "Contact" | MEDIUM | 2015; ≈10586 | D1 (y 618–665, fill (220,220,220), icon centres 937.5 / 1006 / 1077 / 1146 / dots 1195.5); A @ 279 (centres 629.5 / 709.5 / 788.5 / 871.5 / 940.5 px); Q @ 165 (labels), file 97QSgcf0jaI_t165.0.png | nav ruler; pitch and "…" offset reproduce R7 §3.5.8 within 1–2 epx |
| 4.8 | Playback "…" menu | **Settings / Feedback / Open file location**, left-aligned items in the expanded bar's panel | MEDIUM | ≈10586 | Q @ 164.5–166.0 | read |
| 4.9 | Media transport | Playback registers with the system media controls: the volume panel shows "Media + Apps", the recording's name and prev / pause / next | LOW | ≈10586 | A @ 272–273; Q @ 150 | read |

## 5. Colours

| # | Item | Value | Conf | Build | Source @ t / file | Derivation |
|---|---|---|---|---|---|---|
| 5.1 | Record / stop button | **Accent** (D0/D2 (0,120,215) = default blue; N cyan accent captured (0,211–217,255); Y and A blue) | HIGH | 2015; 10166; ≈10586 | D0, D2, Y, A, N | two accents on two phones → follows the system accent |
| 5.2 | Level halo / rings | D0 halo = accent at **30 %** over the page ((169,205,234) on (242,242,242); the same α on all three channels); D0w rings 1 epx, (48,144,220) | MEDIUM | 2015 | D0, D0w | α = (242−v)/(242−accent) per channel |
| 5.3 | Timer zero fields | Black at 20 % on light (194 on 242) = SystemBaseLow; live fields black | MEDIUM | 2015 | D0 | computed α; dark-theme value U8 |
| 5.4 | Light-theme list | Page (242,242,242); header accent; name black; date and duration (133,133,133); rule (217,217,217) | MEDIUM | 2015 | D2 | pixel samples |
| 5.5 | Light-theme playback | Disc (230,230,230); unplayed track (204,205,206); app bar (220,220,220); markers' time black, flags accent | MEDIUM | 2015 | D1 | pixel samples |
| 5.6 | Dark theme (phones) | Page black; header, filter link and played track accent; app bar a lighter grey band; play disc dark grey; avatar-initial disc coloured | LOW | ≈10586 | A, Q | camera only; values U8 |

## Strings as shipped

English (≈10586 phone): **"Search Recordings"**, **"Showing"** + **"Call recordings"**, **"Today"**, **"Last week"**,
**"Incoming"**, **"Outgoing"**, **"No recordings found"**, app bar labels **"Share"**, **"Trim"**, **"Delete"**,
**"Contact"**, "…" menu **"Settings"**, **"Feedback"**, **"Open file location"**; app name **"Voice Recorder"** (app list,
window title). English desktop (2015 mockups): **"This week"**, **"Last week"**; Store captions "Start a recording with the
press of a button.", "Play back, trim, rename and delete your recording.", "Choose from a list of all your recordings.";
Store description "Record sounds, lectures, interviews, and other events. Mark key moments as you record, edit, or play
them back."

Finnish (10166, as shipped; English gloss in brackets is ours): app name "Puheentallennus"; default name of a new recording
**"Tallenne"** [Recording]; group "Tänään" [Today]; hold menu "Jaa" [Share], "Poista" [Delete], "Nimeä uudelleen"
[Rename], "Avaa tiedostosijainti" [Open file location]; "…" menu "Asetukset" [Settings], "Palaute" [Feedback]; About page
"Julkaisija: Microsoft Corporation", "Versio 10.1507.7020.0", "Tietosuojatiedot" [Privacy statement], "Copyright 2015
Microsoft Corporation. Kaikki oikeudet pidätetään.", "Mikrofonin asetukset" [Microphone settings]; storage folder
"Äänitallenteet" [Sound recordings].

Formats: timer "00:00:03" (hh:mm:ss, zero fields dimmed); markers "00:07" while recording (Y), "1:08" on playback (D1);
row date "6/21/2016 12:15 AM" (locale short date + short time); durations "0:13", "1:07:03"; scrubber labels "0:09" /
"0:13". Call recordings are named by the contact ("Usman", "Mark Guim").

Not captured: the English default name of a new recording and how a second one is numbered; the other date groups; the
filter choices; delete / rename / trim wording; error or permission messages.

## Segoe MDL2 glyphs the shell's icon font needs

Codepoints from MS (0.2). Shapes matched to the footage by eye.

| Glyph | Codepoint | Where | Conf |
|---|---|---|---|
| Microphone | E720 | record button (both sizes); tile (approximation, U12) | MEDIUM |
| Stop | E71A | recording-state button (filled square) | MEDIUM |
| Pause | E769 | recording controls; playback disc while playing | MEDIUM |
| Play | E768 | playback disc (outlined triangle) | MEDIUM |
| Flag | E7C1 | add-marker buttons; markers rows | MEDIUM |
| Share | E72D | playback app bar | MEDIUM |
| Trim | E78A | playback app bar | MEDIUM |
| Delete | E74D | playback app bar | MEDIUM |
| Rename (E8AC) or Edit (E70F) | pencil drawn in D1; which of the two is **unverified** | playback app bar, 4th button (plain recordings) | LOW |
| ContactInfo | E779 | playback app bar, 4th button (call recordings, "Contact") | LOW |
| More | E712 | "…" | MEDIUM |

## Motion

All from Y (camera, 29.97 fps, build 10166): LOW, ±33 ms. No 60-fps source exists, so every value is UNMEASURED for
RV11 purposes and none reaches phase 15's P6 (≤ 17 ms) threshold.

| # | Item | Value | Conf | Source |
|---|---|---|---|---|
| 7.1 | Record press → recording state | After release the disc shows a paler accent (pressed) for ≈6 frames (200 ms), then turns **grey (disabled) for ≈22 frames (730 ms)** while capture starts, then the stop state appears in **one frame**; the timer text appears one frame later. The grey interval is start-up latency, not a design value | LOW | Y @ 90.3–91.3 (native-fps sequence from 88.5 s, frames 54–84) |
| 7.2 | Level rings | Appear ≈14 frames (467 ms) after the stop state; radius updates every frame (2.7) | LOW | Y frames 83–97 |
| 7.3 | Stop press → list | Paler accent ≈2 frames; microphone glyph back in one frame with the final time; ≈600 ms later the timer shows 00:00:00 for ≈400 ms; then the list: the small button is at its docked place in the first list frame (no travel visible) and the rows fade in over ≈2 frames (67 ms) | LOW | Y @ 108.4–109.5 (sequence from 104.5 s, frames 116–150) |
| 7.4 | Row tap → playback page | UNMEASURED (A shows a "Loading" state in the page for ≈1 s before content; camera, hand-held) | UNMEASURED | A @ 266–268 |
| 7.5 | Scrubber, disc press, app bar expand | UNMEASURED | UNMEASURED | — |

## UNMEASURED — with proposed tagged approximations

| # | What | Proposed approximation (tag it) | Derived from |
|---|---|---|---|
| U1 | Every value on 14393 / 15063 / 15254 | Use the ≈10586 phone structure of §1–§4 and the numbers above as the governing-build stand-ins | the 10586 footage is the latest found; 10.1705's Neon changes were RS3-Insider-only (0.3) |
| U2 | English default name and numbering of new recordings | "Recording", then "Recording (2)", "Recording (3)" … | Y's first name "Tallenne" (= "Recording", 10166); numbering is Windows' own duplicate-name form, not observed here |
| U3 | Trim page | Playback page with two accent handles on the 2-epx track (thumb form of 4.5) marking in / out points, app bar Save / Cancel at R7 §3.5.8's pitch | 4.5 thumb; R7 §3.5.8 app bar |
| U4 | Rename dialog | R7 §1.3.9 top-anchored dialog (194 epx, fill (74,74,74), page dimmed) with a 32-epx text box (3.1) and Rename / Cancel buttons | R7 §1.3.9; 3.1 |
| U5 | Delete confirmation | Same dialog: "Delete this recording?" + Delete / Cancel (wording is an approximation) | R7 §1.3.9 |
| U6 | Choices behind "Showing …" | A flyout (R7 §3.6.2: 44-epx items, fill (40,40,40)) listing the recording kinds; wording approximation "All recordings" / "Voice recordings" / "Call recordings" | 3.2 (only "Call recordings" seen) |
| U7 | Date groups beyond Today / This week / Last week | Today, Yesterday, This week, Last week, then month names — approximation | 3.3 (three seen) |
| U8 | Dark-theme colours: play disc, app bar, secondary text, dimmed timer fields, rings | Disc (31,31,31) = SystemChromeMedium dark (D1's light disc is SystemChromeMedium light #E6E6E6); app bar R7 §3.5.8's (22,27,21); secondary text and zero fields white at 60 % / 20 % (SystemBaseMedium / SystemBaseLow, mirroring 5.3–5.4); rings accent | 5.3–5.5 light values mapped to their system brushes |
| U9 | Record-page vertical layout on a 16:9 or 19.5:9 canvas | Disc centred in the space between the status bar and the bottom; timer digit centre 141.5 epx above the disc centre; pause/flag row 139 epx below; markers row 45 epx above the bottom (Y's offsets, disc 96 epx) | 2.4, 2.10, 2.12, 2.14 (Y) |
| U10 | All motion at 60 fps | State changes as one-frame cuts (7.1, 7.3); ring radius follows the input level every frame; list rows fade in over ≈67 ms; page entrance per phase 01's standard page transition | §7 (30 fps) |
| U11 | Pause state (after the pause button) | Timer holds (no blinking assumed); the pause glyph is replaced in place by a resume glyph (Microphone E720); rings stop | none — pure approximation |
| U12 | Start tile face | Static tile: Microphone E720 centred on the accent tile with the label "Voice Recorder" | phase 15's static-glyph decision; app-list icon is a microphone (visual, not measured) |
| U13 | Settings page on 10586+ | Phase 03's microphone checklist row is the Android stand-in; keep an About block (name, version, privacy) | 1.8 (10166) |

## Gaps for the phase doc

- **Naming (Decisions "Recording names 'Recording N' … H12").** Legible only in Finnish: 10166 named its first take
  "Tallenne" (= "Recording"). No English name and no second-take numbering were captured, so "Recording N" stays an
  approximation; U2's "Recording", "Recording (2)" is the closer stand-in. Call recordings carry the contact's name — rows
  for Samsung call recordings (T15-3) have a W10M form to copy (3.6: avatar, name, date, Incoming / Outgoing + duration).
- **Pivot vs single page.** W10M had neither a pivot nor a ≡ pane: one page with a record state and a list state, plus a
  playback page (1.1). The App Shortcuts "New recording" and "Recordings" map to those two states of one page.
- **E24 numbers now available (all LOW or MEDIUM, none governing-build):** record button 96 epx in the record state
  (2.1–2.2) and 76 ± 3 epx docked bottom-centre on a minimal app bar in the list state (3.8); list rows 56-epx pitch
  (plain, 3.4) / ≈74 epx (call, 3.6); playback disc 96 epx (4.3), 24-epx ring thumb on a 2-epx track (4.5), 48-epx app bar
  at R7 §3.5.8's pitch (4.7). The phase's stand-in "rows at 44 ± 1 epx (R3 C2)" is wrong for this app: its rows are
  two-line, 56 epx.
- **Empty state (phase E21/edge cases).** "No recordings found" at R7 §3.5.9's position (2.11) is the W10M wording for
  the empty list; the phase's permission-denied page has no W10M equivalent (approximation).
- **Features W10M had that the phase does not list:** markers ("Add marker" flag while recording and on playback, the
  "⚑ time" rows and track dots, 2.12–2.14, 4.2, 4.5); a user **pause** button while recording (2.12 — the phase only
  pauses on a call); list **search** and the recording-kind **filter** (3.1–3.2); "Open file location" (1.10, 4.8 —
  on Android it would open phase 18's Files); the level rings (2.7). Each is a scope question for the phase, not a value.
- **Hold menu.** W10M's row menu was Share / Delete / Rename / Open file location (1.6); trim was on the playback page
  only (4.7). This fits T15-3 (other apps' files: play and share only) by hiding Delete / Rename there.
- **Share.** W10M handed recordings to the system share page (1.9), so Android's `ACTION_SEND` resolver is the faithful
  equivalent (phase Decisions already say so).
- **Trim, rename, delete confirmation, pause state, tile face** were never captured: U3–U5, U11, U12 need NEEDS-HUMAN
  rows ([accept]); H12's list should add markers / search / filter if the phase takes them in.
- **Motion (E24 "the record button's transition", P6).** Only 30-fps camera footage of 10166 exists (§7): the record-state
  change is a cut; nothing qualifies for a ≤ 17 ms P-row.

## Tally

| HIGH | MEDIUM | LOW | UNMEASURED | Rows |
|---|---|---|---|---|
| 3 | 33 | 30 | 15 | 81 |

Counted by parsing the Conf column of §1–§5, the glyph table and §7 (2 UNMEASURED rows), plus the 13 U-rows as
UNMEASURED.
