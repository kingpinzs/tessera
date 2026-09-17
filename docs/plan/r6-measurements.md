# Windows 10 Mobile fidelity measurements, round 2 (R6)

Extends R3 (`w10m-measurements.md`) to six new targets: Start edit mode and live folders (§1), the W10M keyboard (§2), Cortana listening/speaking/confirm flows and lock-screen access (§3), the Back and Search keys (§4), the app-list "New" caption (§5), and the Glance screen clock position and burn-in shift (§6). Nothing below is estimated: every value is measured from a cited frame or quoted from a cited Microsoft document, or the row says UNMEASURED with what was tried.

Governing build (owner ruling): the final W10M release (15063 Creators Update / 15254 Fall Creators Update). 14393 footage is used where the element did not visibly change; earlier builds are flagged per row.

Evidence root (scratchpad, shared with other agents): `$R6 = /tmp/claude-1000/-home-jeremyking/283f1050-f733-41ac-a752-f9b0871bdcbe/scratchpad/r6-work/`. Frames are under `$R6/frames/`, contact sheets under `$R6/sheets/`, scripts under `$R6/scripts/`. `$R3` is R3's evidence root (`…/scratchpad/r3-work/`). Videos were deleted after measurement; every source is cited by URL + timestamp so any frame can be re-extracted with `ffmpeg -ss <t> -i <video> -frames:v 1`.

Confidence: HIGH = 2+ agreeing screen recordings; MEDIUM = one clean screen recording, or camera footage, or a single Microsoft document statement; LOW = partial view, oblique camera, or a non-governing build.

---

## 0. Sources, calibration, units

### 0.1 Calibration reused from R3 (§0.2 there)

| ID | Video | Build | Capture | Ruler |
|---|---|---|---|---|
| S1 | https://www.youtube.com/watch?v=I98ENfXJRqA (Windows Central, 2016-08-16) | 14393 | Project My Screen, 1280x720 @ 60 fps | screen x 471.35–807.5, top y 43.57; 1440x2560 panel at 400 % → 360x640 epx; **1 video px = 4.289 phys px = 1.072 epx** |
| S2 | https://www.youtube.com/watch?v=E6vvrz4ozpE (Windows Central, 2017-03-16) | **15063** | Project My Screen, 1280x720 @ 60 fps | screen x 489.3–788.5, y 94.3–~626; 1440x2560 panel at 350 % → 411x731 epx; **1 video px = 4.813 phys px = 1.375 epx** |

Values are given in epx on the source's own canvas and, where the two captures are compared, in physical px on the 1440-wide panel (R3 showed the Start grid is laid out in physical px, identical on both phones despite their different scale factors). Timing tolerance is ±1 frame at the source fps.

### 0.2 Microsoft documents cited

| ID | Document | Notes |
|---|---|---|
| D1 | Microsoft, "User Guide — Lumia with Windows 10 Mobile", Issue 1.1 EN-US, © 2016 Microsoft Mobile (PDF created 2016-02-02) | URL in §4; local copy `$R6/docs/Lumia_W10M_UG_en_US.pdf`, text `$R6/docs/ug.txt` (line numbers cited) |
| D2 | Microsoft Security Response Center, CVE-2019-1314 "Windows 10 Mobile Security Feature Bypass Vulnerability" (2019-10-08) | https://msrc.microsoft.com/update-guide/vulnerability/CVE-2019-1314 ; API copy `$R6/docs/cve.json` |
| D3 | Microsoft, "Navigation history and backwards navigation for UWP apps" (windows-dev-docs commit d6050b7, ms.date 2017-05-19) | https://raw.githubusercontent.com/MicrosoftDocs/windows-dev-docs/d6050b733085394df70b4c16672195a7b26d7c07/windows-apps-src/design/basics/navigation-history-and-backwards-navigation.md ; local copy `$R6/docs/nav_history_d6050b7.md` |
| D4 | Microsoft Learn, "Policy CSP – AboveLock" (AllowCortanaAboveLock), fetched 2026-09-16 | https://learn.microsoft.com/en-us/windows/client-management/mdm/policy-csp-abovelock |

Per-section source lists (screen recordings and camera footage used by that section) are at the top of each section.

### 0.3 Summary

| § | Target | HIGH | MEDIUM | LOW | UNMEASURED | Rows |
|---|---|---|---|---|---|---|
| 1 | Start edit mode, live folders | 8 | 15 | 15 | 3 | 41 |
| 2 | Keyboard | 33 | 11 | 13 | 4 | 61 |
| 3 | Cortana | 5 | 26 | 5 | 4 | 40 |
| 4 | Back key on Start, Search key | 0 | 6 | 5 | 2 | 13 |
| 5 | App-list "New" caption | 0 | 6 | 1 | 1 | 8 |
| 6 | Glance clock position, burn-in shift | 0 | 1 | 6 | 1 | 8 |
| | **Total** | **46** | **65** | **45** | **15** | **171** |

Headline answers (details and evidence in the rows cited):
- **Edit mode (§1):** long press ≈783 ms. The other tiles shrink to 0.835 about their own centres, pull in to 0.90 around the screen centre, and dim. The selected tile stays at 1.00 with no lift. Two 31-epx discs appear: unpin on the top-right corner, resize on the bottom-right. Entry takes ≈417 ms (ease-out); exit ≈200 ms. Live-folder values come only from 2015/2016 footage (LOW). The one-tile folder case is UNMEASURED.
- **Keyboard (§2):** keys, labels, the press popup and the cursor dot are laid out in physical px. Column pitch is panel width / 10; keys are 202 phys tall at a 217.5-phys row pitch. The suggestion strip is 46.5 epx. Show is a ≈250 ms ease-out slide; hide is a ≈133 ms ease-in slide. Word Flow, the cursor-dot drag and the emoji panel come only from pre-14393 or camera footage (LOW).
- **Cortana (§3):** on 14393/15063 the large persona pulses (1.04-s period) while listening. The 14356 waveform is a small 23 × 14-epx glyph drawn inline after the recognised words in the white query box, stepping every ≈128 ms. Text messages go through a spoken read-back: "Okay, I'll text <name>: <message>. Send it, add more, or try again?" The call confirm flow is UNMEASURED. For lock screen, see the next bullet.
- **Cortana above the lock screen (§3.5):** yes. The 10586-era Cortana setting is "Lock screen options — Open Cortana when I press and hold the Search button – even when my device is locked." Microsoft's 2019 CVE-2019-1314 calls it the "Lock Screen" option in Cortana Settings. It was On on both user devices filmed, and the MDM policy default is "Allowed", but no fresh-device default was captured (LOW). While locked, pressing and holding Search opens Cortana for speech (D1). Footage of 15254 shows a reduced, listening-first Cortana with no ≡ menu over the lock screen. The CVE shows it could reach the photo library.
- **Back on Start (§4.1):** Back resumes the most recently used app on the page it was left on (camera footage plus the Microsoft user guide). In edit mode, Back exits edit mode. Back with no history is UNMEASURED.
- **Search key (§4.2):** a tap opens Cortana (home); press-and-hold opens Cortana listening, including from the lock screen (Microsoft user guide + camera).
- **"New" caption (§5):** accent-coloured 12-epx "New" under the app name, row pitch unchanged. It is still present 3 days after install, so it does not clear by a short timer. Whether it clears on first launch is UNMEASURED; an app installed the same day that is pinned to Start has no caption.
- **Glance (§6):** clock digits span 413–465 epx from the top of a 640-epx screen, with the left edge at 29 epx (LOW, camera, build unverified). No shift was seen across one minute change; the shift pattern is UNMEASURED.

---

## 1. Start edit mode and live folders

Paths are relative to `$R6`. "tile" in ratio columns = the side of a medium tile in the same frame. Frame numbers `sNNN` are 0-based indices at native fps from the stated segment start.

### 1.0 Sources

| ID | Video | Channel / date | Build (how known) | Capture | fps / res | Calibration / ruler |
|---|---|---|---|---|---|---|
| E1 (= R3 S1) | https://www.youtube.com/watch?v=I98ENfXJRqA | Windows Central, 2016-08-16 | 14393 (title) | Project My Screen (PMS), dark theme, full-screen picture, 3 medium columns | 60 fps, 1280x720 | R3 §0.2: 1 video px = 4.289 phys px = **1.072 epx** (1440x2560 @ 400 % → 360x640 epx). Frames `frames/e_s1/s1ed_NNN.png` = crop x 470 / y 42 from t0 = 1096.4 s (crop x = screen x + 1.35; screen y = crop y − 1.57) |
| E2 | https://www.youtube.com/watch?v=ltz0SUe4XKE "How to Customization Startscreen to Circle Tiles on Windows 10 Mobile" | "Windows Phone" channel, 2017-03-23 | **≥14393, likely 15063 or a late Insider build** — upload 2017-03-23, two days after the same channel's 15063 hands-on; build number not on screen. Its app list (t=244.5 s) shows a "Recently added" group with a × button, which R3 did not see on S1 (14393) or S2 (15063), so this may be an Insider build (flagged for §5) | PMS-style mirror with a mouse pointer (yellow/green dot + arrow), light theme, full-screen picture, 4 medium columns | 59 fps, 1280x720; **the capture only refreshes every ≈5–6 frames during heavy animation** (duplicate runs), so timings are ±85 ms | screen x 488.53–790.49 = 301.96 px (sub-pixel, white frame t=308.2 s, `frames/e_ltz/ltz_white_t308.2.png`); app-list row pitch 32.4 px = 44 epx (R3 C2) → **1 px = 1.358 epx**, canvas ≈410 epx wide (a 1440-px panel at 350 %). Frames `frames/e_ltz/ltz{a,b,c}_NNN.png` = crop x 487 / y 74 from t0 = 239.0 / 288.5 / 314.5 s |
| E3 | https://www.youtube.com/watch?v=UiG8MPKcYNY "Group Resize & Manage App Tiles in Windows 10 Mobile" | My Windows Mobile, 2015-10-31 | **pre-10586 (date) — not governing** | PMS, dark theme, 4 medium columns | 29.97 fps, 1280x720 | screen x 471.6–812.6 = 341 px; device/scale unknown → folder geometry given as ratios of the medium tile side (83 px in this capture) |
| E4 | https://www.youtube.com/watch?v=wqpUvABGeP8 "Ekran Startowy Windows 10 Mobile - personalizacja" (chapters 0:28 edit mode, 2:55 tile folders) | Windowsowo / wpworld.pl, 2016-04-30 | 10586–14332 era (date; build not shown) — not governing | **camera**, hand-held, head-on, Lumia 950 XL-class | 50 fps, 1280x720 | timings only; first 335 s downloaded |
| E5 | https://www.youtube.com/watch?v=svVwSYXQasA "How to Create Live Folder on Lumia 950…" | Shaan Haider, 2016-03-01 | 10586 era (date) | camera | 30 fps | structure check only (naming UI) |
| D1 | Microsoft, *User Guide — Lumia with Windows 10 Mobile*, Issue 1.1, © 2016 (`docs/ug.txt` lines 1033–1053) | | 10586-era document | | | quoted as documented behaviour |

Searched without finding a 14393/15063 screen recording of live folders: yt-dlp `ytsearch` for "Windows 10 Mobile live folders", "… live folder tutorial", "… folders start screen", "Lumia 950 live folder", "Windows 10 Mobile 15063 live folder", "… Creators Update start screen tiles resize", "W10M live folders 2018", "Windows 10 Mobile screen recording start screen tiles", "Project My Screen Windows 10 Mobile start", "Windows 10 Mobile Fall Creators Update hands on", "Windows 10 Mobile folder rename tile", "Lumia 650 / 950 XL tips and tricks", plus Spanish, Russian ("папки плиток"), German ("Live-Ordner"), Polish ("ekran startowy foldery kafelki"), Italian, French, Portuguese, Turkish and Vietnamese queries (12–15 results each). Only WP8.1-era, desktop-Windows or pre-14393 folder footage came back. Rejected after triage: k42uJ0i9qCo, OyIi4TO7GTs (camera, no frame-level view of edit mode), hNFQAduAWWk (dark camera), ze6BcCB4YA4.

### 1.1 Edit-mode entry (long press)

| # | Item | Value ± tol | Method | Build | Source (device, epx canvas, fps) | URL @ t | Evidence frame | Conf |
|---|---|---|---|---|---|---|---|---|
| 1.1.1 | Hold time before edit mode starts | **783 ± 33 ms** (47 frames from the touch dot's first frame to the first changed frame); no visible feedback on the pressed tile or anywhere else during the hold (Start-region luminance 80.7 constant for frames s018–s064) | PMS touch-dot onset (yellow-pixel detector) → first frame with changed tile edges / glyph pixels | 14393 | E1, 360x640, 60 fps | I98ENfXJRqA @ 1096.700 s (touch) → 1097.483 s (first change) | `frames/e_s1/s1ed_018.png`, `s1ed_064.png`, `s1ed_065.png` | MEDIUM |
| 1.1.2 | Non-selected tiles: size | each tile shrinks about its own centre to **0.835 ± 0.01** of its side (E1: wide Cortana width 216.4 → 181.8 px = 0.840, People 107.2 × 107.7 → 89.8 × 88.9 = 0.838 / 0.826; E2: red tile 71.1 × 72.0 → 59.8 × 59.6 = 0.84 / 0.83) | sub-pixel gradient edges on the blue (E1) / green (E2) channel, pre vs settled | 14393 + RS2 | E1 360x640 60 fps; E2 410-wide 59 fps | I98 @ 1097.47 vs 1098.07; ltz @ 240.36 vs 241.20 | `frames/e_s1/s1ed_064.png`/`s1ed_100.png` (`scripts/e_edgetrack.py`, `scripts/e_s1spec.json`); `frames/e_ltz/ltza_080.png`/`ltza_130.png` | **HIGH** |
| 1.1.3 | Non-selected tiles: positions | tile centres contract to **0.90 ± 0.01** of their distance from a fixed point (E1: row pitch 110.7 → 99.9 px = 0.903, column centre spacing 165.8 → 149.9 = 0.904; E2: 0.893 / 0.901). Fixed point = screen centre horizontally (E1 51 %, E2 50 % of width) and **46–49 % of screen height** (E1 277 ± 5 px = 297 epx on the 640-epx canvas; E2 ≈49 %) | centre-to-centre ratios; fixed point solved from (post − s·pre)/(1 − s) | 14393 + RS2 | E1, E2 | as 1.1.2 | as 1.1.2; brute-force uniform fit `scripts/e_scalefit.py` gives s = 0.900 about (165, 282) crop px, NCC 0.72 | **HIGH** |
| 1.1.4 | Resulting gutter in edit mode | E1: pitch 99.9 − tile 88.9 = **11.0 px = 11.8 epx = 47 phys** (normal 4.5 px = 18 phys, R3 A1) | derived from 1.1.2/1.1.3 | 14393 | E1 | — | — | MEDIUM |
| 1.1.5 | Non-selected tiles: dimming, dark theme | tile pixels × **0.53 ± 0.03** (G and B channels; e.g. Cortana (17,57,141) → (3,31,73)); wallpaper between/behind tiles × **0.25–0.30** ((0,35,81) → (0,5–12,15–23)); status-bar glyphs stay white | mapped 5×5 patches pre vs post through the 1.1.2/1.1.3 transform | 14393 | E1 | I98 @ 1097.47 / 1098.07 | `s1ed_064.png`, `s1ed_100.png` | MEDIUM |
| 1.1.6 | Non-selected tiles: dimming, light theme | washed toward grey, not darkened: tile c' ≈ **0.63·c + 62** per channel (red (230,20,0) → (207,72,62); orange (255,140,0) → (224–232,143–147,59–64)), i.e. ≈37 % mix toward ≈(168,168,168); wallpaper (48,48,48) → (108,105,109) | same patch method | RS2 era | E2 | ltz @ 240.36 / 241.20 | `ltza_080.png`, `ltza_130.png` | MEDIUM |
| 1.1.7 | Selected (pressed) tile | **no lift, no scale: 1.00 ± 0.01** (E1 107.5 × 107 px vs 106.3 × 108 before; E2 medium 71–72 px); not dimmed (its colour shifts only where the darker wallpaper shows through its translucency: (38,73,153) → (44,69,135)); its centre moves with the grid contraction of 1.1.3 (E1: 19.5 px = 21 epx down); no shadow or outline visible | edges + patches | 14393 + RS2 | E1, E2 | as 1.1.2 | `frames/e_s1/s1ed_100_glyphs_zoom.png`, `sheets/e_map/s1ed_pre_post_zoom.png` | **HIGH** |
| 1.1.8 | Entry animation: scale | first changed frame already at 10.6 % of the travel; 50 % by ≈4 frames (67 ms); 90 % by 11–17 frames (183–283 ms; capture stutters here); settled at **25 ± 3 frames = 417 ± 50 ms** — strong ease-out. Per-frame right edge of the Cortana wide tile (px): 331.77 (s064), 329.40, =, 323.33, =, 319.39, 317.58, =, 315.62, 315.48, 315.21, =, 311.69 (s076–s081 flat), 311.36, =, =, 309.71 (s086), 309.53, 309.41 (s090) | edge tracker per frame | 14393 | E1 60 fps (duplicate frames present) | I98 @ 1097.483–1097.900 | `s1ed_064…s1ed_098.png`; table from `scripts/e_edgetrack.py` | MEDIUM |
| 1.1.9 | Entry animation: dimming | Start-area mean luminance 80.7 → 74.5 (s065), 66.4 (s067), 61.4 (s069), 59.7, 56.7 (s072), 54.9 (s074), 52.5 (s076), 51.4 (s081), 49.8 (s086), 47.9 (s090), 47.1 (s095), 46.4 (s098): 50 % at ≈5 frames (83 ms), 90 % at ≈21 frames (350 ms), settled **≈33 frames = 550 ± 50 ms**, ease-out | region mean per frame | 14393 | E1 | same | same | MEDIUM |
| 1.1.10 | Entry duration cross-check | first change 240.390 s; tile bboxes settled by 240.695 s (≈305 ms) with capture refresh every 2–4 frames | colour-bbox per frame | RS2 era | E2 | ltz @ 240.39–240.70 | `ltza_080…ltza_103.png` | LOW |

### 1.2 Unpin and resize glyphs

| # | Item | Value ± tol | Method | Build | Source | URL @ t | Evidence frame | Conf |
|---|---|---|---|---|---|---|---|---|
| 1.2.1 | Glyph disc diameter | E1 28.5 px = **30.5 epx** (122 phys); E2 23.5 px = **32 epx** → **31 ± 1.5 epx** | half-max width through the disc centroid | 14393 + RS2 | E1, E2 | I98 @ 1098.07; ltz @ 298.67 | `frames/e_s1/s1ed_100_glyphs_zoom.png`; `sheets/e_map/ltz_glyph_states.png` | **HIGH** |
| 1.2.2 | Unpin disc position | centred on the selected tile's **top-right corner** (E1 centroid (221.2, 46.8) vs corner (222, 47.5): −0.8 / −0.7 px) | centroid vs tile edges | 14393 + RS2 | E1; E2 (disc spacing = tile height) | same | same | **HIGH** |
| 1.2.3 | Resize disc position | centred on the selected tile's **bottom-right corner** (E1 (221.2, 152.9) vs (222, 154.5)); E2 vertical spacing between the two discs = tile height (medium 71 px: discs at y 386.1 / 457.1; wide at y 351 / 422) | same | 14393 + RS2 | E1, E2 | I98 @ 1098.07; ltz @ 297.3, 298.67 | same | **HIGH** |
| 1.2.4 | Disc / glyph colours | dark theme: **white disc (249–252) with a dark glyph** (reads 90–96 at 720p, anti-aliased); light theme: **black disc with a white glyph** — i.e. disc = theme foreground, glyph = theme background | patch means | 14393 (dark), RS2 (light) | E1, E2 | same | same | MEDIUM (one recording per theme) |
| 1.2.5 | Glyph shapes and sizes | unpin: push-pin with a small slashed circle at its lower right, glyph box 15 × 14 px = **16 × 15 epx**; resize: single arrow, 11 × 11 px = **12 × 12 epx** (E1) | dark-pixel bbox inside the disc | 14393 | E1 | I98 @ 1098.07 | `s1ed_100_glyphs_zoom.png` | MEDIUM |
| 1.2.6 | Resize arrow direction per size (= next size in the cycle) | **medium → ↖ (to small)** (E1 Calendar, E2 TuneIn / Instagram, E3); **small → ↘** (E2: Instagram small → wide at 296.87 s; E3 2015: Word small); **wide → ←** (E2 t=320.8 s; E3 PowerPoint). Observed cycle on E2: medium → small → wide → medium | visual, zoomed | 14393 + RS2 (+2015) | E1, E2, E3 | ltz @ 295.96–297.5, 320.8; UiG8 @ 14.0, 30.5, 34.0 | `sheets/e_map/ltzb_small_glyph.png`, `ltz_glyph_states.png`, `uig_glyphs.png` | medium ↖ **HIGH**; small ↘ / wide ← MEDIUM |
| 1.2.7 | Glyph appearance / motion | both discs appear **in the first changed frame at full size** (no scale or fade; E1 s065 disc 25–29 px already) and ride with the tile during the entry contraction (centre y 29.1 → 46.8 px over s065–s090); they vanish in the first frame of exit (s130). During a resize they jump straight to the new corners (E2: new disc at the small tile's corner in the first changed frame s448; hidden for ≈10 frames then shown at the wide tile's corners at s504) | disc detector per frame | 14393 + RS2 | E1, E2 | I98 @ 1097.48–1097.90; ltz @ 296.09, 297.04 | `s1ed_065…s1ed_090.png`, `ltzb_448.png`, `ltzb_504.png` | MEDIUM |
| 1.2.8 | Unpin action result (tile removal / reflow) | UNMEASURED: no unpin tap in E1–E5 (E2 pins apps but never unpins; E3/E4 only resize and move) | — | — | — | — | — | UNMEASURED |
| 1.2.9 | Documented behaviour | D1: "To resize, tap and hold the tile, and tap the arrow icon. The tiles can be either small, medium, or wide." "To unpin the tile, tap and hold the tile, and tap [unpin]." | quote | 10586-era doc | D1 | `docs/ug.txt` l.1036–1046 | — | MEDIUM |

### 1.3 Drag reflow

| # | Item | Value ± tol | Method | Build | Source | URL @ t | Evidence frame | Conf |
|---|---|---|---|---|---|---|---|---|
| 1.3.1 | Dragged tile | stays at full size (1.00) and undimmed, follows the finger with its grab offset; the other tiles keep the edit-mode 0.835 scale and dimming | bbox per frame (dragged medium 72 × 70 px while moving, s238–s280) | RS2 era | E2 | ltz @ 318.0–319.3 | `sheets/e_map/ltzc_drag.png` | MEDIUM |
| 1.3.2 | Neighbour reflow motion when space is needed (here: after a resize to wide) | neighbour tile moved **28 px = 38 epx** up in **≈300 ms** (320.669 → 320.975 s) in steps of 12 / 14 / 2 px at the capture's ≈10-Hz refresh → ease-out; neighbours slide, they do not fade or jump | top-edge tracking of the displaced tile (y 358 → 346 → 332 → 330) | RS2 era | E2 | ltz @ 320.67–320.98 | `frames/e_ltz/ltzc_364.png`, `ltzc_370.png`, `ltzc_376.png`, `ltzc_382.png` | LOW |
| 1.3.3 | Reflow while dragging over occupied cells (when neighbours start moving, how far ahead) | UNMEASURED: E2's drags move tiles into empty space below the grid; E3 (2015) drags inside a folder only; E4 is hand-held camera with the finger covering the target | — | — | — | — | — | UNMEASURED |
| 1.3.4 | Documented behaviour | D1: "Tap and hold the tile, drag and drop it to the new location, and tap the screen." | quote | 10586-era doc | D1 | `docs/ug.txt` l.1033–1034 | — | MEDIUM |

### 1.4 Resize animation

| # | Item | Value ± tol | Method | Build | Source | URL @ t | Evidence frame | Conf |
|---|---|---|---|---|---|---|---|---|
| 1.4.1 | Medium → small | tile content disappears, the tile rectangle shrinks toward its top-left cell, the small icon re-appears: first change 296.093 s, content back 296.602 s → **≈0.5 ± 0.1 s** | per-frame mean abs difference + purple-pixel count (inline tracker run in session) | RS2 era | E2 (capture refresh ≈every 5 frames) | ltz @ 295.96–296.60 | `ltzb_440.png`, `ltzb_448.png`, `ltzb_460.png`, `ltzb_478.png`; `sheets/e_map/ltzb_small_glyph.png` | LOW |
| 1.4.2 | Small → wide | rectangle grows from the small cell to the full wide size in ≈170 ms (296.873 → 297.042 s), stays blank (accent fill) ≈270 ms, then the new icon fades in ≈170 ms (297.314 → 297.483 s, faint → full); total **≈0.6 ± 0.1 s** | same | RS2 era | E2 | ltz @ 296.84–297.48 | `sheets/e_map/ltzb_resize.png` (frames 486–544) | LOW |
| 1.4.3 | Cross-check, 2015 build | medium → small and small → wide both complete within **≤2 frames (≤67 ms)** — effectively instant | visual per frame | pre-10586 (not governing) | E3, 30 fps PMS | UiG8 @ 31.53–31.60, 32.20–32.27 | `sheets/e_map/uig_drag.png` | LOW |

### 1.5 Exit

| # | Item | Value ± tol | Method | Build | Source | URL @ t | Evidence frame | Conf |
|---|---|---|---|---|---|---|---|---|
| 1.5.1 | Exit triggers observed | (a) tap on the selected tile itself (E1: touch 1098.283–1098.417 s inside Calendar); (b) tap on an empty grid cell / empty wallpaper (E2 @ 242.8 s and ≈299.0 s); (c) **Back key** (E4 camera @ 249.9 s, see Notes for §4). Tapping a *different* tile does not exit: it moves the selection (1.5.4). D1: "…drag and drop it to the new location, and tap the screen." | visual + touch dots | 14393, RS2, 2016 camera | E1, E2, E4, D1 | as listed | `s1ed_113.png`, `sheets/e_map/ltza_c.png`, `sheets/e_map/wqp_back.png` | **HIGH** (tap exits: E1 + E2) |
| 1.5.2 | Latency tap → exit | first exit frame **150 ± 17 ms** after touch-up (E1: dot gone at 1098.417 s, exit starts 1098.567 s); the 5 frames before show a ≈6 % luminance drop in the Start region | touch dot + luminance | 14393 | E1 | I98 @ 1098.28–1098.57 | `s1ed_113…s1ed_130.png` | MEDIUM |
| 1.5.3 | Exit animation | reverse of entry but faster: glyphs vanish in the first frame; tiles return to 1.00 and pitch to 1.00 in **11–13 frames = 183–217 ms** (Cortana left edge 127.56 → 123.75 → 121.55 → 117.77 → 117.55 → 117.37 → 115.56 → 115.46 at s130–s138, final 115.45 at s141); undim 50 % in 2 frames, 90 % in 6 frames (100 ms), settled ≈18 frames (300 ms) (luminance 41.8, 49.9, 57.5, 67.7, 70.9, 74.7, 76.3 … 79.1). E2 cross-check: 242.831 → 243.068 s (≈200–240 ms). | edge tracker + luminance | 14393 + RS2 | E1, E2 | I98 @ 1098.567–1098.867; ltz @ 242.83–243.07 | `s1ed_130…s1ed_148.png`; `ltza_226…ltza_240.png` | **HIGH** (≈200 ms, two recordings) |
| 1.5.4 | Selecting another tile while in edit mode | the tapped tile grows to 1.00 and undims while the previously selected tile shrinks to 0.835 and dims; the glyph pair moves to the new tile; ≈170–200 ms (297.975 → 298.18 s, capture ≈10 Hz) | visual | RS2 era | E2 | ltz @ 297.92–298.18 | `sheets/e_map/ltzb_drag.png` (frames 556–571) | LOW |

### 1.6 Live folders: create, look, expand, collapse

No 14393/15063 screen recording of folders was found (search list in 1.0). Values come from a 2015 PMS recording (E3) and 2016 camera footage (E4), so every row is LOW as a governing-build value.

| # | Item | Value ± tol | Method | Build | Source | URL @ t | Evidence frame | Conf |
|---|---|---|---|---|---|---|---|---|
| 1.6.1 | Create: drop-on-tile feedback | while a dragged tile hovers over another tile, the **target's icon shrinks into a mini tile at the target's top-left corner** (folder preview) and the target stays highlighted behind the dragged tile (7.83 → 8.83 s); on release the dragged tile shrinks into the target as a second mini tile beside the first (9.50 → 9.97 s) | visual, 30 fps | pre-10586 | E3 PMS | UiG8 @ 7.5–10.0 | `sheets/e_map/uig_droptarget.png`, `frames/e_uig5/uig_t8.8.png` | LOW |
| 1.6.2 | Create: what follows | the folder is created in place, Start stays in edit mode and the new folder **auto-expands** with the dropped tile still selected and a "Name folder" placeholder shown (10.04 → 10.30 s, ≈230 ms, with Start scrolling to bring the folder into view). D1: "Tap and hold the tile, and drag and drop it on top of the tile or folder you want to group it with." | visual | pre-10586 | E3; D1 | UiG8 @ 10.04–10.50 | `sheets/e_map/uig_create.png`, `frames/e_uig5/uig_t10.4.png` | LOW |
| 1.6.3 | Collapsed folder tile (medium) | tile tinted like a system tile; member icons as **mini tiles in a 3-column grid starting at the top-left** (mini tile ≈0.20 × tile side, pitch ≈0.31 ×, inset ≈0.07 ×; 4 members → 3 + 1); folder name as the normal tile label bottom-left | visual measurement at 720p (±1.5 px ≈ ±0.02 tile) | pre-10586 | E3 | UiG8 @ 47.5 | `sheets/e_map/uig_folder_states.png` (right panel), `frames/e_uig5/uig_t47.5.png` | LOW |
| 1.6.4 | Wide folder tile | shows a member tile's live content (text) on the right, the mini-tile grid on the left, the name bottom-left and a numeric badge | visual | 2016 (camera) | E4 | wqp @ 314–320 | `frames/e_wqp/wqp_t316.0.png` | LOW |
| 1.6.5 | Expanded folder geometry | the folder tile stays in the grid and shows only a centred **"^" chevron** on its tint; a **full-width band** opens directly below it, bounded by **1–2 px light horizontal rules** (luminance ≈150–160 on the dark wallpaper); top rule ≈0.20 tile below the folder tile's bottom edge; member tiles start ≈0.22 tile below the top rule, **full size on the same column grid**; bottom rule ≈0.19 tile below the last member row; band background = the wallpaper (no tint, no arrow/notch); tiles after the folder are pushed below the band | row/column luminance profiles at 720p | pre-10586 (E3); same structure in 2016 camera (E4 @ 305.9 s) | E3, E4 | UiG8 @ 35.0; wqp @ 305.9 | `sheets/e_map/uig_folder_states.png` (middle), `frames/e_uig5/uig_t35.0.png`, `frames/e_wqp/wqp_t305.9.png` | LOW |
| 1.6.6 | Expand timing | tap 48.934 s → first change 49.234 s (Start auto-scrolls so the band fits) → band rows revealed top-to-bottom, settled 49.57–49.63 s: **≈350–400 ms** from first change. Camera cross-check: 305.52 → 305.76 s (≈240 ms, scroll hides the start) | per-frame visual at 30 fps / 50 fps | pre-10586; 2016 | E3; E4 | UiG8 @ 48.9–49.7; wqp @ 305.5–305.8 | `sheets/e_map/uig_expand.png`; `sheets/e_map/wqp_ex.png` | LOW |
| 1.6.7 | Collapse timing | tap on the "^" folder tile 53.734 s → first change 53.834 s: member rows fold away (top row gone first, remaining row squashes to a strip, then only the rule) in **4 frames ≈133 ms** (53.834 → 53.968 s); the folder tile's mini-tile content appears over 54.001 → 54.101 s (≈100 ms); Start then scrolls back ≈370 ms (54.20 → 54.57 s). Camera cross-check: 307.52 → 307.88 s (≈360 ms including scroll) | same | pre-10586; 2016 | E3; E4 | UiG8 @ 53.7–54.6; wqp @ 307.5–307.9 | `sheets/e_map/uig_collapse.png`; `sheets/e_map/wqp_co.png` | LOW |

### 1.7 Folder naming UI

| # | Item | Value ± tol | Method | Build | Source | URL @ t | Evidence frame | Conf |
|---|---|---|---|---|---|---|---|---|
| 1.7.1 | Placeholder | in edit mode an unnamed folder shows a caption-size, dimmed **"Name folder"** placeholder at the top-left of the expanded band (Polish UI: "Nowy folder") | visual | pre-10586; 2016 camera | E3, E4, E5 | UiG8 @ 12.2; wqp @ 259.5; svv @ 93–107 | `frames/e_uig5/uig_t12.2.png`, `frames/e_wqp/wqp_t259.5.png` | LOW |
| 1.7.2 | Editing | tapping the placeholder replaces it with a **full-width single-line text box with a white fill** and opens the keyboard; the band stays expanded above the keyboard; E3 box = 22 px tall (≈0.27 × tile side) spanning x 10–336 of the 341-px screen; the typed name then becomes the band label and the folder tile's label (E3 "Office", E4 "Folder aplikacji") | white-row/column extent at 720p | pre-10586; 2016 camera | E3, E4, E5 | UiG8 @ 39–44 (box @ 41.5); wqp @ 257.5–272.5; svv @ 108.5–118 | `frames/e_uig5/uig_t41.5.png`, `frames/e_wqp/wqp_t264.5.png`, `sheets/e_map/wqp_256_00.png` | LOW |
| 1.7.3 | Documented gesture | D1: "To change the name of the folder, tap and hold the name, and type in the name you want." (footage shows a plain tap on the placeholder while already in edit mode) | quote | 10586-era doc | D1 | `docs/ug.txt` l.1051–1052 | — | MEDIUM |

### 1.8 Folder left with one tile

| # | Item | Value ± tol | Method | Build | Source | URL @ t | Evidence frame | Conf |
|---|---|---|---|---|---|---|---|---|
| 1.8.1 | What happens when a folder is left with one tile | **UNMEASURED**: no footage in E1–E5 removes tiles from a folder down to one (E3 95–104 s and E4 309–326 s only add members, resize the folder, or move members inside it). D1 only says "To remove a folder, unpin the tiles in it." The one web statement found is WP8.1-era forum anecdote (forums.windowscentral.com thread 301433, 2014-08-05: "Usually when the last app is removed, the folder is deleted", contradicted in the same thread by a user whose empty folder persisted) — neither covers the one-tile case nor W10M | yt-dlp searches (1.0), WebSearch "live folder last tile removed", "Windows 10 Mobile live folder remove tile only one app left" | — | D1; forum | — | — | UNMEASURED |

### 1.9 Tally

41 rows, each counted once (a split row counts at its lower level):

- **HIGH 8**: 1.1.2, 1.1.3, 1.1.7, 1.2.1, 1.2.2, 1.2.3, 1.5.1, 1.5.3.
- **MEDIUM 15**: 1.1.1, 1.1.4, 1.1.5, 1.1.6, 1.1.8, 1.1.9, 1.2.4, 1.2.5, 1.2.6 (medium ↖ part is HIGH), 1.2.7, 1.2.9, 1.3.1, 1.3.4, 1.5.2, 1.7.3.
- **LOW 15**: 1.1.10, 1.3.2, 1.4.1, 1.4.2, 1.4.3, 1.5.4, 1.6.1–1.6.7, 1.7.1, 1.7.2.
- **UNMEASURED 3**: 1.2.8 (unpin result), 1.3.3 (reflow while dragging over occupied cells), 1.8.1 (one-tile folder).

Back-key observation made while reviewing this footage (Back exits edit mode) is recorded in §4 row 4.1.7.

---

## 2. Keyboard

Unit finding (answers the brief's key question): **the key grid, key labels, press popup and cursor-control dot are laid out in physical pixels** — S1 (14393, 400 %) and S2 (15063, 350 %) agree to ±3 phys px on every one of them while their epx values differ by 12–15 %. **The suggestion strip is laid out in epx** (46.4 vs 46.7 epx; 162 vs 187 phys). So on a 1440-wide panel the key rows are identical on both phones, and the strip above them is taller on the 400 % phone. Physical px ("phys") below are on the 1440-px-wide panel; the key pitch is exactly panel width / 10. To reproduce on another panel width, scale phys by (panel width / 1440).

Rulers (from §0.1): S1 1 px = 4.289 phys = 1.072 epx; S2 1 px = 4.813 phys = 1.375 epx. Edges are sub-pixel threshold crossings at the midpoint between the two adjoining colours (`$R6/scripts/k_prof.py`, `k_chprof.py`, `k_bands.py`); ±1 video px = ±4.3 (S1) / ±4.8 (S2) phys.

### 2.0 Sources

| ID | Video | Channel / date | Build (how known) | Capture | fps / res | Ruler |
|---|---|---|---|---|---|---|
| S1 | https://www.youtube.com/watch?v=I98ENfXJRqA | Windows Central, 2016-08-16 | 14393 (R3 §0.1) | Project My Screen | 60 / 1280x720 | §0.1 |
| S2 | https://www.youtube.com/watch?v=E6vvrz4ozpE | Windows Central, 2017-03-16 | **15063** (R3 §0.1) | Project My Screen | 60 / 1280x720 | §0.1 |
| K1 | https://www.youtube.com/watch?v=TBvRd4JxjE0 "Keyboard Windows 10 mobile" | My Windows Mobile, 2015-10-06 | pre-10586 (upload date) — **not governing** | Project My Screen, generic phone-frame overlay (same channel/overlay as R3's S8) | 29.97 / 1280x720 | no panel calibration: screen width taken as 10 × the measured key pitch (34.25 px → screen x 470.7–813.2 = 342.5 px). "phys-eq" = 1440/342.5 = 4.20 per px; epx on an assumed 360-epx canvas = 1.051 per px |
| K2 | https://www.youtube.com/watch?v=9-s7fRw82_k "Windows 10 Mobile Build 14322 - Action center, Emojis, and more" | hologei tech, 2016-04-15 | 14322 (title) | camera, near head-on | 60 / 1280x720 | none (structure only) |
| K3 | https://www.youtube.com/watch?v=ZTPNhjCGgk0 "Windows 10 Mobile Build 15240 - New Emoji, Continuum, Changes" | Windows Central, 2017-08-14 | 15240 (title; Fall Creators Update branch) | camera, hand-held | 30 / 1280x720 | none (structure only) |
| K4 | https://www.youtube.com/watch?v=s1XpL7h490k "Dissecting Windows 10 Mobile: cursor controller" | On MSFT, 2016-02-26 | ≈10586 (date) | camera, oblique | 60 / 1280x720 | none (checked; the controller is not resolvable) |
| D1 | Lumia with Windows 10 Mobile User Guide, Issue 1.1 (§0.2), "Write text", `$R6/docs/ug.txt` lines 1485–1610 | Microsoft, 2016 | 10586-era document | document | — | — |

Checked and not used (camera footage, keyboard too small/oblique, or no keyboard): MrxZ5ojyG7Y (Nokiapoweruser 2016-06, camera — the dot and drag look the same as K1), CvZN7tll4iI, S48uepXuUwU, a4wZZQZoTO4, bR_jJf5p-n0, uvOZh2Q2wCQ (15047 screen recording, no keyboard), GbbSNBNuzZY (15250, no keyboard), gDq3AH7ZF58 (15063 camera, no keyboard use), jjxNllAANmI (camera), 5Q5UlIZLfM0 (camera), Rqe6RIqqjnE (15240 camera, emoji panel too dark). YouTube searches run: "windows 10 mobile keyboard word flow", "…keyboard emoji", "…creators update keyboard", "…cursor control keyboard", "lumia 950 keyboard shape writing", "…keyboard screen recording", "…emoji keyboard lumia", "…15063 hands on", "…15254 hands on", "Ian Dixon windows 10 mobile build", "…keyboard tips tricks 2017", "…swype keyboard lumia 950", "…emoji skin tone", "…anniversary update emoji keyboard", "…new emoji keyboard 14322", "lumia 950 emoji" (12–15 results each): **no 14393/15063 screen recording of Word Flow, the cursor-controller drag, or the emoji panel was found.**

### 2.1 Key grid (portrait, English QWERTY)

Frames: S1 t=602.0 s Settings search (`$R6/frames/k_s1/S1_t602.png`, 3x crop `S1_t602_kb3x.png`), t=690.0 s Store search (`S1_t690.png`), t=979.0 s Edge address bar (`S1_t979.png`); S2 t=113.0 s Messaging (`$R6/frames/k_s2/S2_t113.png`, 4x crop `S2_t113_kb4x.png`), t=105/108 s.

Layout (both builds): 4 key rows under the suggestion strip, then the nav bar.
- Row 1: q w e r t y u i o p — 10 keys on a 10-column grid.
- Row 2: a s d f g h j k l — 9 keys offset by half a pitch.
- Row 3: shift (1.5 columns) · z x c v b n m (on row 2's s…k columns) · backspace (1.5 columns).
- Row 4 (default text field): &123 (1.5) · emoji ☺ (1) · comma (1) · space (4) · period (1) · Enter ↵ (1.5). Shift, backspace, &123, emoji and Enter are lighter "function" keys; letters, comma, space and period are dark keys. The &123 key carries three small dots at its top-left (long-press options); the space bar carries a two-line grip at its top centre; with more than one keyboard language the space bar shows the language as dim "< ENG (US) >" (S2), otherwise it is blank (S1).

| # | Item | Value ± tol | Method | Build | Source (device, epx canvas, fps) | URL @ t | Evidence frame | Conf |
|---|---|---|---|---|---|---|---|---|
| 2.1.1 | Column pitch (all rows) | **144 phys = panel width / 10** (S1 33.65 px = 144.3 phys = 36.1 epx; S2 29.92 px = 144.0 phys = 41.1 epx) | (last key left − first key left)/9 along row 1, thr 36 | 14393 + 15063 | S1 950-class 1440x2560 @400 % (360x640), 60 fps; S2 1440x2560 @350 % (411x731), 60 fps | S1 @ 602.0 s; S2 @ 113.0 s | `k_s1/S1_t602.png`, `k_s2/S2_t113.png` | HIGH |
| 2.1.2 | Row 1 letter key width | **130 ± 3 phys** (S1 30.27 px = 129.8 phys = 32.4 epx; S2 27.30 px = 131.4 phys = 37.5 epx; mean of 10 keys each) | edge pairs at thr 36 (bg 25 / key 48) | both | as 2.1.1 | same | same | HIGH |
| 2.1.3 | Row 1 horizontal gap | **14 ± 3 phys** (S1 3.38 px = 14.5 phys; S2 2.61 px = 12.6 phys; both 3.6 epx) | pitch − width | both | as 2.1.1 | same | same | HIGH |
| 2.1.4 | Row 1 edge margins | left **5 ± 4**, right **9 ± 4 phys** (S1 1.10 / 2.08 px; S2 1.05 / 1.71 px) — together one gap, i.e. ≈ half a gap per edge, the same asymmetry direction as R3's Start margins | screen edge (R3 ruler) → first/last key edge | both | as 2.1.1 | same | same | HIGH (sum) / MEDIUM (split) |
| 2.1.5 | Row 2 letter key width / gap | **128 ± 3 / 16 ± 3 phys** (S1 29.94 / 3.68 px; S2 26.56 / 3.38 px) | as 2.1.2 | both | as 2.1.1 | same | same | HIGH |
| 2.1.6 | Row 2 insets | left **77.5 ± 3 phys** (S1 18.06 px = 77.5; S2 16.13 px = 77.6), right **82 ± 3 phys** (S1 19.11 px; S2 16.97 px) — half a pitch plus the edge margin | screen edge → a / l key edge | both | as 2.1.1 | same | same | HIGH |
| 2.1.7 | Shift and backspace width | **201 ± 2 phys** (S1 46.79 / 46.72 px = 200.7 / 200.4; S2 41.75 / 41.93 px = 200.9 / 201.8) = 1.5 pitch − gap; shift from the left margin to the a-column's right edge, backspace from the l-column's left edge to the right margin | thr 50 (bg 25 / function key 74) | both | as 2.1.1 | same | same | HIGH |
| 2.1.8 | Row 3 letters z…m | width **128 ± 3 phys**, on row 2's s…k columns (S1 z 522.92 vs s 522.90 px; S2 z 535.41 vs s 535.38 px) | thr 36 | both | as 2.1.1 | same | same | HIGH |
| 2.1.9 | Row 4 widths | &123 **201** (S1 46.76 px, S2 41.80 px) · emoji **128** (29.78 / 26.57 px) · comma **128** (29.94 / 26.46 px) · space **560 ± 3** (S1 130.68 px = 560.5; S2 116.35 px = 560.0; 4 columns) · period **128** (29.90 / 26.61 px) · Enter **201** (46.68 / 41.93 px) phys, all ± 3 | thr 36 dark keys, thr 50 function keys | both | as 2.1.1 | same | same | HIGH |
| 2.1.10 | Key height | **202 ± 3 phys** (S1 rows 1–4: 47.0 / 47.05 / 47.2 / 47.4 px = 201.6–203.3 phys = 50.4–50.8 epx; S2: 41.6 / 42.4 / 42.4 / 42.1 px = 200.2–204.1 phys = 57.2–58.3 epx) | column profiles at 7 x positions, thr 36 (dark keys) / 50 (function keys) | both | as 2.1.1 | same | same | HIGH |
| 2.1.11 | Row pitch / vertical gap | pitch **217.5 ± 1.5 phys** (S1 row 1 top 394.45 → row 4 top 546.6 px: 50.72 px = 217.5; S2 412.5 → 547.9 px: 45.13 px = 217.2); gap **15 ± 3 phys** (S1 3.6–3.9 px; S2 2.7–3.2 px) | successive key tops | both | as 2.1.1 | same | same | HIGH |
| 2.1.12 | Bottom margin (row 4 bottom → nav bar) | **7 ± 4 phys** (S1 594.6→596.2 px; S2 590.3→591.9 px, both 1.6 px) | thr 12 on the gap column (bg 25 / nav bar 0) | both | as 2.1.1 | same | same | HIGH |
| 2.1.13 | Key block height (row 1 top → nav bar top) | **865 ± 5 phys** (S1 201.75 px = 865.3; S2 179.5 px = 863.9) | profiles | both | as 2.1.1 | same | same | HIGH |
| 2.1.14 | Letter label size | lowercase x-height **41 ± 2 phys** (e: S1 9.73 px = 41.7, S2 8.47 px = 40.8; a: 40.6 / 41.5); capital height **48 ± 3 phys** (E/T/H: S1 11.3 px avg = 48.4 at t=628 s; S2 9.9 px = 47.6 at t=108 s); label centred in the key (S2 E cap centre 433.5 = key centre 433.5 px) | luminance bands > 150 inside one key | both | as 2.1.1 | S1 @ 602, 628 s; S2 @ 105, 108, 113 s | `k_s1/S1_t628.png`, `k_s2/S2_t108.png` | HIGH |
| 2.1.15 | "&123" label | digit height **40 ± 2 phys** (S1 9.11 px = 39.1; S2 8.54 px = 41.1), text width 112 ± 3 phys (S1 26.2 px; S2 23.1 px), centred | bands | both | as 2.1.1 | S1 @ 602; S2 @ 113 | as 2.1.1 | HIGH |
| 2.1.16 | Space-bar grip | two horizontal lines, overall **59 × 18 ± 5 phys** (S1 x 648–662, rows 552–555 = 14 × 4 px; S2 x 648–659, rows 552–555 = 12 × 4 px), its top **20 ± 4 phys** below the space bar's top (S1 5.4 px; S2 3.8 px), centred on the space bar (S1 grip centre 655.0 vs bar 655.7 px; S2 653.5 vs 653.5); grey (lum ≈ 80) on the key (lum 48). D1 lines 1604–1605: "Tap and hold the space bar, and drag the keyboard up or down." | pixel grid dump | both | as 2.1.1 | S1 @ 602; S2 @ 113 | as 2.1.1 | HIGH |
| 2.1.17 | &123 long-press dots | three ≈1-px dots at the key's top-left, pitch 4.5 px = 19 phys, first dot 4.6 px (20 phys) from the key's left edge, 6 px (26 phys) below its top (S1); present but below threshold in S2 (visible in the 4x crop) | pixel bands | 14393 (seen 15063) | S1 | S1 @ 602 | `k_s1/S1_t602_kb3x.png`, `k_s2/S2_t113_kb4x.png` | MEDIUM |
| 2.1.18 | Colours (capture rendition) | panel/gap background (22,27,21); letter/comma/space/period keys (48,48,48); function keys (71,76,72)–(74,74,74); labels (255,255,255)-class; nav bar (0,0,0); pressed key and popup accent (48,104,255) (R3 §0.2 accent caveat applies) | pixel samples | both | as 2.1.1 | S1 @ 602; S2 @ 113 | as 2.1.1 | MEDIUM |

### 2.2 Suggestion strip

| # | Item | Value ± tol | Method | Build | Source | URL @ t | Evidence frame | Conf |
|---|---|---|---|---|---|---|---|---|
| 2.2.1 | Strip height (panel top → row 1 key top) | **46.5 ± 1.3 epx** (S1 43.55 px = 46.7 epx = 187 phys, t=690 and 979 s, panel top 350.9–351.3 px; S2 33.77 px = 46.4 epx = 162.5 phys, t=113 and 128 s, panel top 378.63–378.75 px). Epx, not phys: the one keyboard dimension that differs physically between the two phones | column profile, page colour → panel colour (thr 120 on a white page / thr 11 on a black page) | both | S1 / S2 as 2.1.1 | S1 @ 690.0, 979.0 s; S2 @ 113.0, 128.0 s | `k_s1/S1_t979.png`, `k_s1/S1_t690.png`, `k_s2/S2_t113.png`, `k_s2/S2_t128.png` | HIGH |
| 2.2.2 | Strip background | same colour as the keyboard panel ((22,27,21) capture), no separator line between strip and keys; nothing visible separates suggestions | pixel samples; 3x crops | both | as 2.1.1 | same | `k_s1/S1_t602_strip3x.png`, `k_s2/S2_strips_116_128_138.png` | HIGH |
| 2.2.3 | Suggestion text size | cap height **13.3 ± 1 epx** (S1 "Come Corn Cone Contact" 12.53 px = 13.4 epx; S2 "Becase" 9.62 px = 13.2 epx) → Segoe UI ≈ **19–20 epx** Regular (cap 0.69–0.70 em); text vertically centred in the strip (S1 text centre 373.6 vs strip 372.7 px; S2 396.1 vs 395.5 px) | bands > 120 over the word area | both | as 2.1.1 | S1 @ 602.0 s; S2 @ 113.0, 125.0 s | `k_s1/S1_t602_strip3x.png`, `k_s2/S2_t125_strip3x.png` | HIGH |
| 2.2.4 | Spacing between suggestions | **26 ± 1.5 epx** gap between words (S1 23.8–24.0 px = 25.5–25.8 epx; S2 18.9–19.0 px = 26.0–26.1 epx; in phys 102 vs 91, so epx-based like the strip) — variable-width items, no dividers | word bands | both | as 2.1.1 | same | same | HIGH |
| 2.2.5 | Left inset | first item starts **12.7–13.5 ± 1.4 epx** from the screen edge (S2 word at 9.25 px; S1 microphone glyph at 12.6 px). The S1 strip leads with a microphone (dictation) glyph 12.9 x 18.9 px = 13.8 x 20.3 epx, with the first word 26.7 px (28.6 epx) to its right | bands | both | as 2.1.1 | same | same | MEDIUM |
| 2.2.6 | Bold autocorrect candidate | the word that will auto-replace on space is drawn in bold as the first item ("**Because** Becase Became Bekasi Hexadec…", S2 t=113 s). D1 lines 1553–1555: "If the suggested word is marked in bold, your phone automatically uses it to replace the word you wrote. If the word is wrong, tap it to see the original word and a few other suggestions." | frame inspection + D1 | 15063 | S2 | S2 @ 113.0 s | `k_s2/S2_t113_kb4x.png` | HIGH (S2 + D1) |
| 2.2.7 | After tapping a word | strip becomes the word's original spelling plus alternatives; an unknown word is offered as "– word" (remove) or "+ word" (add to dictionary); the "+ Becase" item turns into an accent-filled rectangle spanning the strip height while pressed (S2 t=128 s: "becase – Becase because became be case"; t=138 s: "Because [+ Becase] Became Bekasi Be case"). D1 lines 1558–1560: "write the word, tap it, and tap the plus sign (+) in the suggestion bar." | frame inspection | 15063 | S2 | S2 @ 128.0, 138.0 s | `k_s2/S2_strips_116_128_138.png` | MEDIUM |
| 2.2.8 | More suggestions | D1 line 1552: "To see more suggestions, swipe left." (items overflow off the right edge, S2 t=113 s "Hexadec…" clipped) | D1 + frame | 15063 | S2 / D1 | S2 @ 113.0 s | `k_s2/S2_t113_kb4x.png` | MEDIUM |

### 2.3 Key press popup

| # | Item | Value ± tol | Method | Build | Source | URL @ t | Evidence frame | Conf |
|---|---|---|---|---|---|---|---|---|
| 2.3.1 | Form | the pressed key fills with the accent colour and a separate accent rectangle with a larger white glyph appears directly above it, centred on the key, overlapping the row above (or the strip / app content for row 1); no tail, no shadow, square corners | 5x crops | both | S1, S2 | S2 @ 109.5 s ('e'), 109.2 s ('b'); S1 @ 979.05 s ('i'), 979.35 s ('n') | `k_s2/S2_t109.5_popup_5x.png`, `k_s1/S1_t979.05_popup_5x.png` | HIGH |
| 2.3.2 | Popup size | **173 × 233 ± 4 phys** (S1 'n' 40.44 × 54.34 px = 173.4 × 233.1; S2 'e' 36.0 × 48.5 px = 173.3 × 233.4, 'b' 35.8 × 49.5 px) | blue-channel threshold 140 edges | both | as 2.1.1 | same | same | HIGH |
| 2.3.3 | Popup position | centred on the key (S2 'e' popup centre 563.8 vs key 563.6 px; 'b' 668.2 vs 668.45; S1 'n' 706.0 vs 706.3); ≈ 21–23 phys wider than the key on each side; its bottom edge sits **7 ± 4 phys** above the pressed key's top (S1 'n' 1.8–2.2 px; S2 'e' 1.8 px, 'b' 1.1 px), its top **25 ± 4 phys** above the top of the row above (S1 'n' 439.34 vs 445.3 px; S2 'b' 452.09 vs 457.32 px). For row-1 keys the popup covers the suggestion strip and rises above the keyboard panel (S1 'i': 14.1 px = 61 phys above the panel top; S2 'e': 17.1 px = 82 phys) | edges | both | as 2.1.1 | same | same | HIGH |
| 2.3.4 | Popup glyph | lowercase 'e' x-height 12.37 px = **59.5 ± 5 phys** in the popup vs 8.47 px (40.8 phys) on the key (≈ 1.45x) | bands > 200 | 15063 | S2 | S2 @ 109.5 s | `k_s2/S2_t109.5_popup_5x.png` | MEDIUM |
| 2.3.5 | Timing | appears **17–50 ms** (1–3 frames) after the capture's touch indicator appears, at ≥ 91 % of its final accent-pixel count in the first frame (the shortfall is the touch indicator overlapping it; no scale, slide or fade); on quick taps it stays **100–167 ms** (6–10 frames: S1 979.000–979.100, 979.317–979.400, 979.633–979.783, 979.950–980.083; S2 109.150–109.283); the next key's popup replaces it immediately (S2 109.533 → 109.550). Function keys (backspace) only fill with accent, no popup (S1 981.333–981.45, 982.533–982.8) | per-frame accent-pixel bbox (`$R6/scripts/k_popup.py`) | both | S1/S2 60 fps | S1 978.9–981.5 s; S2 109.0–110.0 s | `$R6/sheets/k/S2_press108_00.png` | HIGH |


### 2.4 Word Flow (shape-writing) trail

Only K1 (2015 build, screen recording) shows swiping; S1/S2 never swipe. D1 lines 1503–1506 document the behaviour: "Swipe from the first letter of the word, and draw a path from letter to letter. Lift your finger after the last letter. To continue, keep on swiping without tapping the space key. Your phone adds the spaces."

| # | Item | Value ± tol | Method | Build | Source | URL @ t | Evidence frame | Conf |
|---|---|---|---|---|---|---|---|---|
| 2.4.1 | Colour | the system accent, opaque, flat (no gradient along its length): trail core (13–16,101–105,181–192) vs the same capture's accent send button (7–12,120,221) | pixel medians inside the trail (distance-transform ≥ 2 px) | pre-10586 | K1, 342.5-px screen, 29.97 fps | K1 @ 202.80, 203.10, 204.30 s | `$R6/frames/k_tbv/tbv_wf_t202.80.png`, `tbv_wf_t203.10.png`, `tbv_wf_t204.30.png` | LOW |
| 2.4.2 | Width | FWHM **8.2–9.7 px = 8.9 ± 0.8 px** ≈ 2.6 % of screen width = **37 ± 4 phys-eq ≈ 9.4 ± 1 epx** (360-epx canvas); uniform along the trail, round-ish ends | blueness (B − (R+G)/2) cross-sections at 5 x positions (horizontal stroke) and 15 y positions (vertical stroke) | pre-10586 | K1 | K1 @ 201.30, 203.10 s | `tbv_wf_t201.30.png`, `tbv_wf_t203.10.png` | LOW |
| 2.4.3 | Length while drawing | a comet: the tail is erased behind the finger, so only the recent path shows — trail area stays bounded at 2000–2350 px² (≈ 240 ± 30 px of path) while the head moves; the tail lags the touch point by **≈ 170–280 ms** (tail x matched against earlier touch positions) | per-frame trail bbox/area vs touch-indicator centroid (`$R6/scripts/k_trail.py`) | pre-10586 | K1 29.97 fps (±33 ms) | K1 202.55–203.85 s | `$R6/sheets/k/tbv_wf1_00.png` | LOW |
| 2.4.4 | After lift | trail stays complete for **≈ 100 ms** (3 frames), then retracts from its tail toward the lift point over **≈ 230–270 ms**; fully gone **≈ 330–370 ms** after lift (two strokes: lift 202.08 → gone 202.467; lift 203.47 → gone 203.818) | same tracker | pre-10586 | K1 | K1 202.0–202.5, 203.45–203.85 s | same | LOW |
| 2.4.5 | Suggestions during a swipe | the strip shows candidates for the swiped word as soon as the finger lifts (bold first candidate, as 2.2.6) | frame inspection | pre-10586 | K1 | K1 @ 202.1, 205.44 s | `tbv_wf1_00.png` | LOW |
| 2.4.6 | 14393/15063 trail | UNMEASURED: no screen recording or legible camera footage of swiping on a 14393+ build found (searches listed in 2.0; MrxZ5ojyG7Y 376–440 s is camera with the finger covering the trail) | — | — | — | — | — | UNMEASURED |

### 2.5 Cursor controller (the dot)

| # | Item | Value ± tol | Method | Build | Source | URL @ t | Evidence frame | Conf |
|---|---|---|---|---|---|---|---|---|
| 2.5.1 | Form | three concentric parts: an accent-blue centre dot, a key-grey disc around it, and a dark ring (panel-background colour) that cuts into the four surrounding keys | radial luminance profile over 90 angles, excluding the directions along key gaps (`$R6/scripts/k_radial.py`) | 14393 + 15063 (same in K1 2015) | S1, S2 | S1 @ 602.0, 979.0 s; S2 @ 113.0 s | `k_s1/S1_t602_kb3x.png`, `k_s2/S2_t113_kb4x.png` | HIGH |
| 2.5.2 | Blue centre dot diameter | **21 ± 3 phys** (S1 half-max radius 2.5 px → 5.0 px = 21.4 phys = 5.4 epx; S2 2.2 px → 4.4 px = 21.2 phys = 6.0 epx) | half-max of the radial profile (106 core → 48 disc) | both | S1, S2 | same | same | HIGH |
| 2.5.3 | Grey disc / dark ring | grey disc (key colour) diameter **56 ± 4 phys** (S1 13.2 px = 56.6; S2 11.6 px = 55.8); dark ring outer diameter **87 ± 4 phys** (S1 20.2 px = 86.6 = 21.7 epx; S2 18.1 px = 87.1 = 24.9 epx); ring thickness ≈ 15 phys | 36.5-level crossings (grey 48 ↔ dark 25) | both | S1, S2 | same | same | HIGH |
| 2.5.4 | Position | centred on the crossing of three gaps: row 3 ↔ row 4, z ↔ x, emoji ↔ comma (or ↔ ".com"). Centre **358 ± 3 phys from the screen's left edge** (S1 (554.76 − 471.35) × 4.289 = 357.7; S2 (563.61 − 489.3) × 4.813 = 357.6) and **218 ± 3 phys above the nav bar's top edge** (S1 218.1; S2 218.0) — i.e. exactly one row pitch up and 2.5 pitches minus half a gap across. Same place in the URL layout (S1 t=979: 554.83, 545.34) and in K1 2015 (555.5, 598.4 at the z/x and row 3/4 gaps) | blue centroid vs gap midpoints | both | S1, S2, K1 | S1 @ 602, 979 s; S2 @ 113 s; K1 @ 30 s | as 2.5.1; `k_tbv/tbv_t30_crop.png` | HIGH |
| 2.5.5 | Handedness setting | Settings > Time & language > Keyboard > "More keyboard settings" has **"Cursor controller: Right handed usage"** (combo box); with that value the dot is at the left (z/x) position. The mirrored (right-side) position was not captured | frame inspection | pre-10586 | K1 | K1 @ 71 s (setting), 30 s (keyboard) | `k_tbv/tbv_t71_settings.png` | LOW |
| 2.5.6 | Documented behaviour | D1 lines 1542–1545: "To move the cursor from one character or line to another, tap and hold the cursor controller, and drag your finger to the direction you want." | document | 10586-era | D1 | ug.txt 1542–1545 | — | MEDIUM |
| 2.5.7 | Drag state visuals | while held: the whole keyboard dims to ≈ 50 % (key colour lum 48 → 25, panel 27 → 16, letter labels 220 → 110 at p99.5, suggestion strip dims too); four white chevrons (‹ › ˄ ˅) appear around the dot, inner edges **25–30 px = 105–126 phys-eq** from its centre, each ≈ 13–17 px wide (up/down 17 × 9 px); an accent line ≈ 3.5 px (≈ 15 phys-eq) thick runs from the dot toward the finger (39 px long with the finger 70 px above the dot) | luminance statistics and bright/blue-pixel extents vs K1 t=139.5 (idle) | pre-10586 | K1 | K1 @ 142.0, 146.0, 148.5 s | `$R6/sheets/k/tbv_cur_00.png`, `k_tbv/tbv_cur_t148.5_4x.png` | LOW |
| 2.5.8 | Drag behaviour | direction-locked joystick, not a trackpad: holding the finger left of the dot keeps stepping the caret left one character at a time while held (≈ 5 characters in 0.75 s ≈ **150 ms per character**, K1 150.0–150.75 s); holding above steps up one line per ≈ 0.5–1 s and scrolls the field at its top (151.0–152.5 s); releasing leaves the caret in place and restores the keyboard | caret position read per 0.25 s | pre-10586 | K1 | K1 146.0–153.5 s | `$R6/sheets/k/tbv_caret_00.png` | LOW |
| 2.5.9 | 14393/15063 drag | UNMEASURED: S1/S2 never press the dot; K4 (≈10586 camera) and MrxZ5ojyG7Y (2016 camera) show the same chevrons but too oblique to measure | — | — | — | — | — | UNMEASURED |

### 2.6 Emoji panel

| # | Item | Value ± tol | Method | Build | Source | URL @ t | Evidence frame | Conf |
|---|---|---|---|---|---|---|---|---|
| 2.6.1 | Structure (final branch) | the emoji panel replaces the four key rows (the suggestion strip stays above it): **4 rows of emoji cells** in the space of key rows 1–3, scrolling horizontally (partial columns visible at the edges), and a **10-cell category row** in the space of key row 4: `abc` · recent (clock) · smileys · people · celebration (balloon) · food (pizza) · travel (car) · symbols (heart) · text emoticons (";-)") · backspace. The active category cell is accent-filled; `abc` and backspace are function-key grey. Flat colour emoji | frame inspection | 15240 (and identical on 14322) | K3 camera; K2 camera | K3 @ 60 s; K2 @ 125 s | `$R6/frames/k_wc/wc15240_t60_3x.png`, `$R6/frames/k_hol/hol_t125_wide.png` | LOW |
| 2.6.2 | Cell counts (final branch) | ≈ 7.5 columns visible (K3: 7 full + a partial column; K2: 7 full + partial) × 4 rows; category row 10 equal cells | visible-cell count | 15240 / 14322 | K3, K2 | same | same | LOW |
| 2.6.3 | Geometry (2015 build) | emoji grid occupies exactly key rows 1–3 (top 428.4 px = row 1 top 429.2; bottom 597.3 = row 3 bottom 596.8); **6 columns** at pitch 56.0 px = 1.64 key pitches (**235 phys-eq**) × **4 rows** at pitch 42.2 px (**177 phys-eq**); category row = key row 4 (600.0–654.8 px) at key pitch; 10 cells: `abc` · ♥ · ":)" · 😀 · balloon · pizza · plane · weather · "!?" · backspace (no "recent" or "people" cell in 2015) | luminance profiles | pre-10586 | K1 | K1 @ 88, 94, 98 s | `$R6/frames/k_tbv/tbv_emoji_88_94_98.png` | LOW |
| 2.6.4 | Final-release cell geometry in px/epx | UNMEASURED: only camera footage of 14322/15240 exists in the searches (2.0); the gap lines are below the camera noise (profiles on K3 t=60 s found no consistent gap minima) | — | — | — | — | — | UNMEASURED |
| 2.6.5 | Emoji key → panel | the ☺ key sits in row 4 between &123 and comma (2.1.9); D1 line 1497 names it the "Smiley key"; K1's settings page has "Switch back to letters after I type an emoticon" (checked in that capture) | frame + D1 | pre-10586 / D1 | K1, D1 | K1 @ 71 s | `k_tbv/tbv_t71_settings.png` | LOW |

### 2.7 Show / hide animation

| # | Item | Value ± tol | Method | Build | Source | URL @ t | Evidence frame | Conf |
|---|---|---|---|---|---|---|---|---|
| 2.7.1 | Show: motion | the whole panel (strip + keys) **slides up from below the screen edge, no fade** (a row-1 key already has its final luminance 48–49 in the first visible frame) and no scale; strong ease-out | per-frame vertical offset of the key block by template correlation against the rest frame (`$R6/scripts/k_slide.py`) + key luminance | both | S1 60 fps, S2 60 fps (content at 30 fps in S2: duplicated frames) | S2 103.88–104.15 s; S1 599.50–599.78, 627.68–627.80, 633.97–634.18 s | `$R6/sheets/k/S2_show103_00.png` | HIGH |
| 2.7.2 | Show: offsets (fraction of key-block height still to travel, per 16.7 ms) | S1 599.50: 0.69, 0.69, 0.40, 0.30, 0.23, 0.17, 0.13, 0.10, 0.074, 0.055, 0.040, 0.040, 0.010, 0.010, 0.010, 0.005, 0 · S1 633.97: 0.53, 0.40, 0.23, 0.23, 0.13, 0.10, 0.074, 0.055, 0.040, 0.025, 0.025, 0.010, 0.010, 0.005 · S2 103.88 (30-fps content): 0.89, 0.38, 0.21, 0.12, 0.06, 0.033, 0.017, 0.006, 0 | offsets / block height (S1 201.75 px, S2 179.5 px) | both | S1, S2 | as 2.7.1 | per-frame tables in this row | HIGH |
| 2.7.3 | Show: duration | first visible frame → at rest **≈ 250 ± 33 ms** (S1 267, 217+, S2 250 ms); 90 % of the travel done in **≈ 100–117 ms** | as 2.7.2 | both | S1, S2 | as 2.7.1 | — | HIGH |
| 2.7.4 | Hide: motion and duration | slides **down** off the screen, accelerating (ease-in), no fade: S1 413.23: offsets 1, 2, 3, 6, 19, 50, 95, 95 px then off-screen at 413.367 → **≈ 133 ms** from first movement, the last 76 px in ≈ 50 ms; S1 603.82: 19, 51, 96, 164 px then off at 603.90 → ≈ 83–100 ms (this one coincides with a page navigation, and a 1–8 px upward shift precedes it at 603.67–603.78) | as 2.7.1 | 14393 | S1 (two instances) | S1 413.2–413.4 s; 603.6–603.9 s | `$R6/sheets/k/S1_hide413_00.png`, `S1_hide603_00.png` | MEDIUM |

### 2.8 Action key and bottom-row variants

| # | Item | Value ± tol | Method | Build | Source | URL @ t | Evidence frame | Conf |
|---|---|---|---|---|---|---|---|---|
| 2.8.1 | Default text field (Messaging, Skype, Settings search) | `&123 · ☺ · , · space(4) · . · ↵` — Enter is a grey function key with a white ↵ glyph | frame inspection + widths (2.1.9) | 14393 + 15063 | S1 (Skype, Settings), S2 (Messaging) | S1 @ 411, 602 s; S2 @ 113 s | `$R6/frames/k_s1/S1_bottomrows_411_602_686_979.png`, `k_s2/S2_t113_kb4x.png` | HIGH |
| 2.8.2 | Search field (Store search) | same row, but the action key is **white-filled (254,255,253) with a dark magnifier glyph** (34,36,33), 201 phys wide (S1 46.75 px) | pixel samples, edges thr 140 | 14393 | S1 | S1 @ 686, 690 s | `k_s1/S1_t690_kb3x.png` | MEDIUM |
| 2.8.3 | URL field (Edge address bar) | `&123 · ☺ · .com(1.5) · space(3) · .(1.5) · →` — ".com" replaces the comma and widens to 201 phys (S1 46.97 px), space narrows to **418.6 phys** (97.59 px = 3 columns − gap), period widens to 201 phys (46.84 px); the action key is white-filled with a black → arrow; ".com" label x-height 6.5 px = 28 phys (smaller than letter labels) | edges thr 36/140 + bands | 14393 | S1 | S1 @ 979.0 s | `k_s1/S1_t979_kb3x.png` | MEDIUM |
| 2.8.4 | Phone-number field | a 3 × 4 numeric keypad (1 2 3 / 4 5 6 / 7 8 9 / ✱ 0 #) with letter sub-labels and a backspace column replaces the QWERTY rows | frame inspection | pre-10586 | K1 | K1 @ 4–12 s | `$R6/sheets/k/tbv_2s_00.png` | LOW |
| 2.8.5 | Other action-key variants (Send, Next, Done, Go text labels; email "@" key) | UNMEASURED: not present in any footage examined; no Microsoft document lists the W10M input-scope → key mapping in the sources read | — | — | — | — | — | UNMEASURED |
| 2.8.6 | Language key / "< ENG (US) >" | with two or more keyboard languages the space bar shows the language name dimmed with chevrons (S2, K1); D1 lines 1540–1541: "If your keyboard has a language key (located between the numbers and symbols and comma key), tap it"; K1 settings "Show the language switching key" (unchecked in that capture); D1 line 1538: "Swipe left or right on the space bar until the language you want to write in appears" | frames + D1 | 15063 / D1 | S2, K1, D1 | S2 @ 113 s; K1 @ 71 s | `k_s2/S2_t113_kb4x.png` | MEDIUM |

### 2.9 Tally

Counted per row above (61 rows; 2.1.4 counted HIGH).

| Confidence | Count | Rows |
|---|---|---|
| HIGH | 33 | 2.1.1–2.1.16, 2.2.1–2.2.4, 2.2.6, 2.3.1–2.3.3, 2.3.5, 2.5.1–2.5.4, 2.7.1–2.7.3, 2.8.1 |
| MEDIUM | 11 | 2.1.17, 2.1.18, 2.2.5, 2.2.7, 2.2.8, 2.3.4, 2.5.6, 2.7.4, 2.8.2, 2.8.3, 2.8.6 |
| LOW | 13 | 2.4.1–2.4.5, 2.5.5, 2.5.7, 2.5.8, 2.6.1–2.6.3, 2.6.5, 2.8.4 |
| UNMEASURED | 4 | 2.4.6, 2.5.9, 2.6.4, 2.8.5 |

---

## 3. Cortana

All paths below are relative to `$R6`. Per-frame data files: `c_audio/track_826.csv` (listening persona + query box, 826.5–835.5 s), `c_audio/spk_841.txt`, `c_audio/spk_857.txt`, `c_audio/spk_908.txt` (small persona at the top of a card), `c_audio/wave_828.txt`, `c_audio/wave_852.txt` (waveform-glyph change events). Scripts: `scripts/c_track.py`, `scripts/c_speak.py`, `scripts/c_wavediff.py`, `scripts/c_transcribe.py` (faster-whisper `small.en`, GPU; transcripts in `c_audio/*.txt`). S1 conversions: 1 video px = 1.072 epx on the 360-epx canvas; S1 crops are `crop=336:598:471:43`, so crop y 0 = screen top (43.57) − 0.57 px and crop x 0 = screen left (471.35) − 0.35 px. S2: 1 px = 1.375 epx.

### 3.0 Sources

| ID | Video / document | Channel, date | Build (how known) | Capture | Res @ fps | Ruler |
|---|---|---|---|---|---|---|
| C1 = S1 | https://www.youtube.com/watch?v=I98ENfXJRqA | Windows Central, 2016-08-16 | 14393 (title; R3 §0.1) | Project My Screen (PMS) | 1280x720 @ 60 | R3 S1: 1 px = 1.072 epx (360x640, 400 %) |
| C2 = S2 | https://www.youtube.com/watch?v=E6vvrz4ozpE | Windows Central, 2017-03-16 | **15063** (title) | PMS | 1280x720 @ 60 | R3 S2: 1 px = 1.375 epx (411x731, 350 %) |
| C3 | https://www.youtube.com/watch?v=b1X5fGYCd14 "Windows 10 Mobile Creators Update: What's new?" | Ho Young Won, 2017-04-25 | **10.0.15063.251** (Settings ▸ About on screen at 24 s, `frames/c_b1x/b1x_about_t24.png`), Lumia 640 LTE | handheld camera | 1280x720 @ 30 | none (structure, wording, form only) |
| C4 | https://www.youtube.com/watch?v=0jQzbOIeoWc "Unpatched Windows 10 Mobile Vulnerability (CVE-2019-1314): Lock Screen Bypass Using Cortana" | YuvalRonSec, 2019-10-03 | **10.0.15254.541** (About page at 88 s, `sheets/c_cve/cve_about.png`), Lumia 650 | handheld camera | 406x720 @ 30 | none |
| C5 | https://www.youtube.com/watch?v=u3PRlONm1CE "How Windows 10 Mobile Handles SMS over Bluetooth: Completely Hands Free!" | Richard Durishin, 2017-08-10 | 15063-era (upload date; build not shown), Lumia 950 (narration) | camera, phone lying flat in a dark room; audio transcribed (`c_audio/u3p.txt`) | 720x720 @ 29.79 | none |
| C6 | https://www.youtube.com/watch?v=qjwS3_f5OMU "Enable Hey Cortana in Windows 10 Mobile (950 XL / 950)" | WPXBOX, 2016-01-04 | 10586-era (date) — not governing | camera on a table | 1280x720 @ 24 | none |
| C7 | https://www.youtube.com/watch?v=2t3ymUSPGvc "Windows 10 Mobile Tutorial: Setting up Hey Cortana & How To Train" | Nokiapoweruser, 2016-03-10 | 10586-era (date) — not governing | handheld camera | 1280x720 @ 30 | none |
| C8 | https://www.youtube.com/watch?v=USBlwy-kpiM "Windows 10 Mobile Build 14322 - Action Center, Settings, Cortana + MORE" | On MSFT, 2016-04-14 | 14322 (title) — pre-waveform | PMS in a generic phone frame | 1280x720 @ 30 | not calibrated (comparison only) |
| C9 | https://www.youtube.com/watch?v=uvOZh2Q2wCQ "Hands On : Windows 10 Mobile Build 15047" | Windows Phone, 2017-03-04 | 15047 (title) | screen recording with captions | 1280x720 @ 25 | caption only |
| D1 | Microsoft "User Guide — Lumia with Windows 10 Mobile" (§0.2) | 2016 | 10586-era | document | — | `docs/ug.txt` line numbers |
| D2 | MSRC CVE-2019-1314 (§0.2) | 2019-10-08 | final W10M | document | — | `docs/cve.json` |
| D4 | Microsoft Learn, "AboveLock Policy CSP", https://learn.microsoft.com/en-us/windows/client-management/mdm/policy-csp-abovelock (AllowCortanaAboveLock) | live page, fetched 2026-09-16 | 1607 [14393]+ (page lists desktop editions only) | document | — | — |

Searched but not used (no W10M Cortana voice-call footage, or not W10M): audio-only downloads transcribed for call/text wording — j5xXn12d53c (Lumia 950 XL Cortana vs Siri, 2016-01), qlTdNe9VPRc (Ian Dixon AU review), NrBvrs1yfIk (14393.67 Lumia 730), 9VqBhVAZ-mc, hXjl-7lG6wk (WP8.1 car Bluetooth), C3PJpGNeSPE (music only), p46dLrUu4_E (14322 news read-out), WzWFP1ybysw; video eD0Rc-4pOYo (Nomad Tech 2017 three-phone comparison; W10M screen too small, no call). YouTube queries: "Windows 10 Mobile Cortana lock screen", "Cortana text message Windows 10 Mobile", "Cortana call Lumia 950", "Windows 10 Mobile Cortana send text", "Lumia 950 Cortana text message demo", "Hey Cortana call mom Lumia", "Windows 10 Mobile car Bluetooth Cortana call", "Cortana calling contact Windows Phone demo", "Windows 10 Mobile hands free driving Cortana", "Windows 10 Mobile 15063 Cortana", "Windows 10 Mobile Cortana settings 2017", "Lumia Cortana settings lock screen 14393" (12–15 results each). Web: Windows Insider blog posts for 14322 and 14327 (no lock-screen Cortana setting mentioned), PCWorld 14322 article, Windows Experience Blog 2016-08-22 "Use Cortana above your lock screen" (PC only), support.microsoft.com/help/4046591 (HTTP 410), BetaWiki and MSPoweruser (HTTP 403).

### 3.1 Listening state, waveform

Answer to the brief's question: **both** exist on the final builds. The large persona pulses (filled disc + halo, R3 A22's "listening ring"), and the 14356 "miniature audio waveform" is a small glyph drawn **inline in the query box, immediately after the last recognised word**, not in place of the persona. Seen on 14393 (C1, screen recording) and 15063.251 (C3, camera, `frames/c_b1x/b1x_box_t89.3_x4.png`: "…onth that I'm cool" + glyph + →).

| # | Item | Value ± tol | Method | Build | Source (device, epx canvas, fps) | URL @ t | Evidence frame | Conf |
|---|---|---|---|---|---|---|---|---|
| 3.1.1 | Waveform location | Inline glyph after the recognised text inside the white listening query box; moves right as words are appended; absent while the box still shows the "Listening..." placeholder | visual, 60-fps zoom sheets | 14393; 15063.251 | C1 (400 %, 360 epx, 60 fps); C3 camera | C1 @ 830.217–832.95 s; C3 @ 89.3 s | `sheets/c_s1/listen_full.png`, `sheets/c_s1/wave_831.8_60fps_zoom.png`, `frames/c_b1x/b1x_box_t89.3_x4.png` | MEDIUM |
| 3.1.2 | Waveform glyph size | 21.5 ± 1 px = **23 ± 1 epx wide**; max height 13 ± 1 px = **14 ± 1 epx** (11–13 px as the shape changes) | bbox of 140 < luma < 226 pixels right of the last dark text column, 180 frames | 14393 | C1, 60 fps | C1 @ 829.9–832.9 s | `frames/c_s1/s1_box_832.5_x4.png`; ASCII dump in session | MEDIUM |
| 3.1.3 | Waveform glyph form and colour | Vertical bars mirrored about a horizontal centre line (like an audio envelope), ≈1-px bars with 1–2-px gaps at 720p; light grey ≈(190,190,190) capture on the white box; centre line 1.5 ± 1 px above the text x-height centre (crop row 531 vs 532.5) | 8x nearest-neighbour zoom | 14393 (15063.251 same look) | C1; C3 | C1 @ 831.8–832.4 s | `sheets/c_s1/wave_831.8_60fps_zoom.png` | MEDIUM |
| 3.1.4 | Gap text → glyph | 5 ± 1.5 px = **5.4 ± 1.6 epx** | median over 180 frames (dark-text threshold 110) | 14393 | C1 | C1 @ 829.9–832.9 s | as 3.1.2 | MEDIUM |
| 3.1.5 | Waveform motion | Whole bar pattern is **replaced in discrete steps every 128 ± 15 ms (≈8 Hz)**; no interpolation between shapes. 16 steady intervals 831.0–832.9 s (0.116–0.167 s); extra change events are text updates | frame differencing of the box row band (`scripts/c_wavediff.py`) | 14393 | C1, 60 fps | C1 @ 829.8–832.95 and 852.6–856.6 s | `c_audio/wave_828.txt`, `c_audio/wave_852.txt` | MEDIUM |
| 3.1.6 | Listening persona form | Filled inner accent disc (48,104,255 capture) inside a larger translucent halo disc (36–38, 55–60, 133–138), no ring gap | pixel profile through centre | 14393; same form 15063.251 and 15254.541 (camera) | C1; C3; C4 | C1 @ 830.5 s; C3 @ 88–89 s; C4 @ 26.5 s | `frames/c_s1/s1_t830.5.png`, `sheets/c_b1x/b1x_listen.png`, `sheets/c_cve/cve_cortana.png` | MEDIUM |
| 3.1.7 | Listening persona size | Halo outer Ø **80.0 → 88.3 px = 85.8 → 94.7 ± 1 epx**; inner disc Ø **38.3 → 34.8 px = 41.1 → 37.3 ± 1 epx**, in antiphase (halo largest when disc smallest). Agrees with R3 A22 (82→88 px) | sub-pixel threshold crossings on the centre row/column, every frame (`scripts/c_track.py`) | 14393 | C1, 60 fps | C1 @ 828.5–832.95 s | `c_audio/track_826.csv` | MEDIUM |
| 3.1.8 | Listening pulse period and shape | **1.04 ± 0.02 s** (7 half-cycles, mid-level crossings); one cycle: rise ≈0.35 s (830.20→830.55), top hold ≈0.2 s, fall ≈0.35 s (830.75→831.10), bottom hold ≈0.15 s. Capture shows paired duplicate frames (≈30 Hz updates) | as above | 14393 | C1 | C1 @ 828.8–832.9 s | `c_audio/track_826.csv` | MEDIUM |
| 3.1.9 | Listening persona position | Centred horizontally (168.7 px = 180.8 epx on 360); centre y = 227.4 px = **243.8 ± 1 epx** from screen top (R3: 242) | bbox centre | 14393 | C1 | C1 @ 829–832 s | `frames/c_s1/s1_t830.5.png` | MEDIUM |
| 3.1.10 | Entrance into listening (mic tap on a page with the persona at the top) | Persona travels from the top slot (centre 44 epx) to 243.8 epx while the halo grows 50 → 88 px, **20 frames = 333 ± 17 ms** (828.183 → 828.517); inner disc fully filled by 828.367; query box switches to the listening style on the first moving frame (828.183) | per-frame tracker | 14393 | C1 | C1 @ 827.88–828.52 s | `c_audio/track_826.csv` rows 827.883–828.517 | MEDIUM |
| 3.1.11 | Listening query box | White fill (237–247 capture), **2-epx accent border**; outer height 48.6 ± 1 px = **52 ± 1.5 epx** vs idle 48; its top edge is 3.5 ± 1 px (≈4 epx) above the idle bar's top, bottom edge unchanged (nav-bar top); placeholder "Listening..." in accent, 15-epx class; recognised text dark (27,25,26), 15-epx class, starting 14.65 px = 15.7 epx from screen left; submit glyph "→" grey, ≈15 px = 16 epx wide, right edge 16.6 epx from screen right | column/row transitions (thr 10–30) | 14393; 15063.251 same (white, accent border, "Listening...", →) | C1; C3 | C1 @ 829.0, 830.5 s; C3 @ 89.3, 95 s | `frames/c_s1/s1_full_t829.0.png`, `frames/c_s1/s1_t830.5.png`, `sheets/c_b1x/b1x_listen.png` | MEDIUM |
| 3.1.12 | Exit from listening | End of speech: the box returns to the grey bar showing the recognised query + "✕" (832.950) and the persona becomes the hollow thinking ring 3 frames later (833.000); thinking motion and the move to the top = R3 A22 | per-frame tracker | 14393 | C1 | C1 @ 832.95–833.6 s | `frames/c_s1/s1_t834.0.png`, `c_audio/track_826.csv` | MEDIUM |
| 3.1.13 | Hands-free variant | After the user stops talking the box shows the recognised sentence with "→" (89.5 s), then placeholder "Thinking..." with "✕" while the ring spins (90.5–92 s) | visual | 15063-era | C5 camera | C5 @ 89.5–92 s | `sheets/c_u3p/u3p_box_00.png` | LOW |
| 3.1.14 | Page background | 14393: (14,19,13) capture = dark grey; 15063: **(0,0,0) black**. C9's caption at 102 s states the change arrived in 15047 ("Cortana's background is now black rather than the previous dark grey") | pixel samples | 14393 / 15063 | C1; C2; C9 | C1 @ 808.5 s; C2 @ 479.0 s; C9 @ 102 s | `frames/c_s1/s1_t808.5.png`, `frames/c_s2/s2_full_t479.0.png`, `frames/c_uvo/uvo_t102.png` | MEDIUM |

### 3.2 Speaking state

Speech was identified from the audio (faster-whisper word timestamps on C1's soundtrack): Cortana says "Remind you to do the dishes at 8am tomorrow. Sound good?" at 857.68–861.46 s and "I'm sorry, I can't find any P[Cs]…" at 908.94–910.52 s; the user's "Yes." is at 863.22 s. In every captured utterance the persona is the **small persona at the top of a response card**, not the large centred one.

| # | Item | Value ± tol | Method | Build | Source | URL @ t | Evidence frame | Conf |
|---|---|---|---|---|---|---|---|---|
| 3.2.1 | Speaking persona form and size | Filled accent disc Ø **17.7 ± 0.3 px = 19.0 ± 0.5 epx** (constant) with a halo whose outer Ø **jumps between 32.2 and 37.7 px = 34.5–40.4 epx**; centre 37.4 px = **40 epx** from screen top, horizontally centred | per-frame tracker (`scripts/c_speak.py`: disc B>180, halo B−R threshold) | 14393 | C1, 60 fps | C1 @ 858.1–862.9 and 908.7–910.8 s | `c_audio/spk_857.txt`, `c_audio/spk_908.txt`, `frames/c_s1/s1_t858.5.png` | MEDIUM |
| 3.2.2 | Speaking motion | Halo size changes in steps every **51 ± 17 ms** (88 changes in 4.5 s), no periodicity (audio-level driven); starts within 1 frame of the card appearing and stops at end of speech | change count on the halo series | 14393 | C1 | C1 @ 858.3–862.8 s | `c_audio/spk_857.txt` | MEDIUM |
| 3.2.3 | Speaking → awaiting reply | 2-frame hollow ring at 862.98, then the small persona pulses like 3.1.8: disc 16.2 ↔ 17.9 px (17.4 ↔ 19.2 epx), halo 36.4 ↔ 40.1 px (39.0 ↔ 43.0 epx), antiphase, period ≈1.0 s (halo peaks 863.3, 864.35, 865.3); query box shows white "Listening..." | tracker | 14393 | C1 | C1 @ 862.98–865.3 s | `c_audio/spk_857.txt` | MEDIUM |
| 3.2.4 | Idle after speaking (result page) | Hollow bright ring outer Ø **24.2 ↔ 27.1 px = 25.9 ↔ 29.1 epx** (dark hole 18 ↔ 21 px) with halo **36.3 ↔ 32.3 px = 38.9 ↔ 34.6 epx** in antiphase; slow breathing, period **3.75 ± 0.1 s** (ring minima 842.3, 846.1, 849.9, 911.7, 915.4, 919.2) | tracker | 14393 | C1 | C1 @ 841.7–851.6 and 910.9–921.4 s | `c_audio/spk_841.txt`, `c_audio/spk_908.txt`, `sheets/c_s1/speak_842_60fps.png` | MEDIUM |
| 3.2.5 | Governing-build cross-check | Small filled-disc persona at the top of the "When would you like to be reminded?" card while Cortana asks | visual | 15063.251 | C3 camera 30 fps | C3 @ 88–89 s | `sheets/c_b1x/b1x_listen.png` | LOW |
| 3.2.6 | Large centred persona while speaking | UNMEASURED: every utterance in C1, C3, C5 happens on a card with the small top persona; no capture of speech over the centred persona | — | — | — | — | — | UNMEASURED |

### 3.3 Typed-request text box

| # | Item | Value ± tol | Method | Build | Source | URL @ t | Evidence frame | Conf |
|---|---|---|---|---|---|---|---|---|
| 3.3.1 | Bar height | S1 44.8 ± 1 px = 48.0 ± 1.1 epx; S2 34.4 ± 1 px = 47.3 ± 1.4 epx → **48 epx** | column edge crossings | 14393, 15063 | C1 (360 epx); C2 (411 epx) | C1 @ 828.0 s; C2 @ 481.0 s | `frames/c_s1/s1_full_t828.0.png`, `frames/c_s2/s2_full_t481.0.png` | HIGH |
| 3.3.2 | Bar position | Full screen width, docked directly on top of the nav bar (bottom edge S1 y 596.7, S2 y 592.4 in video px); fixed while the page scrolls | edges | 14393, 15063 | C1; C2 | as above | as above | HIGH |
| 3.3.3 | Bar fill | (46,46,46) S1 / (48,48,48) S2, capture rendition | pixel sample | 14393, 15063 | C1; C2 | as above | as above | HIGH |
| 3.3.4 | Mic button | Accent-filled, flush right, **48 × 48 epx** (S1 44.9 × 44.8 px; S2 34.5 × 34.6 px), white mic glyph centred | row/column edges | 14393, 15063 | C1; C2 | C1 @ 808.5 s; C2 @ 481.0 s | `frames/c_s1/s1_t808.5.png`, `frames/c_s2/s2_full_t481.0.png` | HIGH |
| 3.3.5 | Placeholder wording | "**Ask me anything**" (S1 14393, S2 15063). C3 (15063.251, US AT&T Lumia 640) shows "**Type here to search**" on the greeting and music pages; the cause of the difference is not visible | visual | 14393, 15063 | C1; C2; C3 | C1 @ 808.5; C2 @ 481.0; C3 @ 75.5, 88 s | as above; `sheets/c_b1x/b1x_listen.png` | MEDIUM |
| 3.3.6 | Placeholder style | 15-epx class (cap 10 px S1 = 10.7 epx; 8 px S2 = 11 epx), light grey (max luma 207–246 S1 / 225 S2), left inset **12 ± 1 epx** (S1 11.65 px, S2 8.7 px), vertically centred in the bar | text-band bbox | 14393, 15063 | C1; C2 | as 3.3.4 | as 3.3.4 | HIGH |
| 3.3.7 | Other bar states | (a) query submitted / thinking: grey bar with the query text and "✕" at the right (C1 834.0; C5 "Thinking..." + ✕); (b) awaiting a spoken yes/no on a confirm card: grey bar, no text, **grey mic glyph with no accent fill** (C1 858.5); (c) listening: 3.1.11 | visual | 14393 | C1; C5 | C1 @ 834.0, 858.5 s | `sheets/c_s1/listen_full.png` (4th panel), `frames/c_s1/s1_confirm_bottom_x3.png` | MEDIUM |
| 3.3.8 | Typing into the box with the keyboard up | UNMEASURED: no capture of typed entry into the Cortana query box on 14393+ (checked C1 805–925 s, C2 476–512 s, C3 66–101 s, C8 157–175 s, where the keyboard only appears for a reminder card field) | — | — | — | — | — | UNMEASURED |
| 3.3.9 | Voice-hint callout above the bar | Accent rectangle 25 px = **26.8 epx** tall, white 15-epx text, right edge 10.5 px = 11 epx from screen right, width follows text (193 px = 207 epx for "you can say Yes, No, or Cancel"); downward triangular tail 8 px = 8.6 epx tall, 16 px = 17 epx base, tip at crop x 310.5 (over the mic glyph), tip 4 px above the bar. Wordings seen: "you can say Yes, No, or Cancel" (C1), "you can say Resume" and "try 10th of every month at 5 PM" (C3) | accent-pixel runs per row | 14393; 15063.251 (C3) | C1; C3 | C1 @ 858.5 s; C3 @ 88, 89.3 s | `frames/c_s1/s1_confirm_bottom_x3.png`, `sheets/c_b1x/b1x_listen.png` | MEDIUM |

### 3.4 Read-back and confirm before acting

**Sending a text (C5, 15063-era, voice via Bluetooth headset; wording from the audio transcript and the legible on-screen titles, which agree):**

| Step | Screen (C5 @ t) | On-screen wording | Cortana says (audio) |
|---|---|---|---|
| 1 | Listening, large persona (83–88 s) | greeting "How can I help, Richard?" | — ("Hey Cortana" to wake) |
| 2 | Query box shows the recognised text + → (89.5 s), then "Thinking..." + ✕ with ring (90.5–92 s) | "Send a text to Richard directions." (mis-recognition shown in box) | — |
| 3 | Message card, small persona at top, box listening (93–106 s) | Title (accent, 2 lines): "Send a text to Richard Durishin. What do you want to say?"; caption "Message"; "To" + outlined contact chip (round avatar, name, subtitle "mobile"); outlined field "Enter your message."; "with [SMS ▾]"; two buttons side by side, left "Send" (disabled grey), right label not legible | "Send a text to Richard Durishin. What do you want to say?" |
| 4 | Card with the dictated text in the field (107–119 s); a white callout bubble above the mic (text not legible) | "Send it, add more, or try again?" | "Okay, I'll text Richard Durishin: *<message>*. Send it, add more, or try again?" |
| 5 | User: "Add more" (120–126 s) | "What would you like to add?" | "Sure, what would you like to add?" |
| 6 | Appended text (127–140 s) | "Send it, add more, or try again?" | "Okay, now I've got: *<whole message>*. Send it, add more, or try again?" |
| 7 | User: "Send it" → result page (141–145 s) | "Message sent." (accent), box "Ask me anything" | "Message sent." |
| 8 | Incoming text read-out (192–210 s) | "Reply, call back, or are you done?"; caption "Message"; sender row (avatar + name); message body; two buttons | "You got a text from *<sender>*. Want to read it or ignore it?" → user "Read it" → "It says *<body>*. Reply, call back, or are you done?" → user "I'm done" |

Evidence: `sheets/c_u3p/u3p_zoom.png`, `sheets/c_u3p/u3p_1s_zoom_00.png`, `sheets/c_u3p/u3p_1s_zoom_01.png`, `c_audio/u3p.txt`. URL https://www.youtube.com/watch?v=u3PRlONm1CE @ 80–210 s.

| # | Item | Value ± tol | Method | Build | Source | URL @ t | Evidence frame | Conf |
|---|---|---|---|---|---|---|---|---|
| 3.4.1 | Text-message confirm flow (screens + wording, table above) | as tabulated | zoomed camera frames + faster-whisper transcript | 15063-era | C5 camera 29.79 fps | C5 @ 80–210 s | as above | MEDIUM (wording) / LOW (layout) |
| 3.4.2 | Reminder confirm card (same pattern, screen recording) | Title "Remind you about this?" accent, cap 13 px = 14 epx (20-epx Subtitle class), left edge 16 epx; caption "Reminder"; outlined fields "Do the dishes" / "8:00 AM" / "Tomorrow" each 40 px = **43 epx** tall at 50 px = **53.6 epx** pitch (field text cap 15 px = 16 epx); "Only once ▾" dropdown 30 px = 32 epx; "Add a photo" row with camera glyph; buttons **"Remind" / "Cancel"**, accent fill, 30.0 px = **32.2 epx** tall, widths 153.6 / 149.3 px = 164.6 / 160.0 epx, gap 3.55 px = 3.8 epx, side margins 15.2 / 14.6 px = 16.3 / 15.7 epx; accent link 'Search for "Remind me to do the dishes tomorrow at 8 AM."'; callout "you can say Yes, No, or Cancel" (3.3.9). Spoken: "Remind you to do the dishes at 8am tomorrow. Sound good?" (857.68–861.46 s); user "Yes." (863.22) → page "I'll remind you." with the reminder listed (866 s) | sub-pixel edges (midpoint crossings); whisper word timestamps | 14393 | C1, 60 fps | C1 @ 857.5–868 s | `frames/c_s1/s1_t858.5.png`, `frames/c_s1/s1_t866.0.png`, `frames/c_s1/s1_confirm_bottom_x3.png`, `sheets/c_s1/confirm_full.png` | MEDIUM |
| 3.4.3 | Reminder slot-filling on the governing build | Card "When would you like to be reminded?" with fields (reminder text, Time, Day, "Every Month ▾", "Add a photo") and callout "try 10th of every month at 5 PM"; box goes grey-with-mic → "Listening..." | visual | 15063.251 | C3 camera | C3 @ 88–97 s | `sheets/c_b1x/b1x_listen.png` | LOW |
| 3.4.4 | Placing a call: read-back / confirm screens and wording | UNMEASURED: no W10M footage of a voice call command found (see 3.0 "Searched but not used"; C1/C2/C3/C5 contain no call command) | — | — | — | — | — | UNMEASURED |
| 3.4.5 | Sending an email | UNMEASURED: C1 shows only the hint '"send an email to Violet about weekend plans."' under the listening persona (897–907 s); the command actually spoken was "send a photo to my PC" | — | 14393 | C1 | C1 @ 897–921 s | `frames/c_s1/s1_t903.5.png` | UNMEASURED |

### 3.5 Cortana above the lock screen

| # | Item | Value ± tol | Method | Build | Source | URL @ t | Evidence frame | Conf |
|---|---|---|---|---|---|---|---|---|
| 3.5.1 | Setting name and location (on-screen) | Cortana ▸ ≡ ▸ Notebook ▸ Settings; section header "**Lock screen options**", description "**Open Cortana when I press and hold the Search button – even when my device is locked.**", toggle below; it sits between "Hey Cortana" and "Find flights and more" | read from screen | 10586-era (not governing) | C6 camera (950 XL); C7 camera (950/950 XL) | C6 @ 52 s; C7 @ 39, 50 s | `frames/c_wpx/wpx_t52.png`, `sheets/c_s1/npu_settings.png` | MEDIUM |
| 3.5.2 | Setting name on the final build (document) | D2 workaround text: open Cortana ▸ Menu (3 horizontal bars) ▸ Settings ▸ set "the slider for the **Lock Screen** option to **Off** to prevent access to Cortana when the device is locked". The exact 15063/15254 on-screen wording was not captured | quote | final W10M (2019 doc) | D2 | https://msrc.microsoft.com/update-guide/vulnerability/CVE-2019-1314 | `docs/cve.json` | MEDIUM |
| 3.5.3 | Default value | UNMEASURED as a fresh-device default: no out-of-box capture found. Both user devices show it **On** (C6, C7; C6 has "Hey Cortana" Off in the same frame, so the toggles were not all switched on). D4: MDM policy AllowCortanaAboveLock default = 1 "Allowed" ("If you enable or don't configure this setting, the user can interact with Cortana using speech while the system is locked"); that is the policy default, not the user toggle, and the live page lists desktop editions only. Inference **On**, LOW | observation + doc | 10586-era; policy 14393+ | C6; C7; D4 | C6 @ 52 s; C7 @ 50 s; D4 | `frames/c_wpx/wpx_t52.png` | LOW |
| 3.5.4 | How it is opened while locked | D1 "Use your phone when it's locked" ▸ "Open Cortana: If your phone has Cortana, to open Cortana, **tap and hold the search key**." (`docs/ug.txt` l.4281, 4313–4314). D1 also: with "Let Cortana respond to 'Hey Cortana.'" on, "You can now wake up Cortana just by saying Hey Cortana, even if your screen is on standby." (l.1282–1283) | quote | 10586-era doc | D1 | D1 p.128–129, p.44 | `docs/ug.txt` | MEDIUM |
| 3.5.5 | What Cortana shows while locked (footage) | Full-screen over the lock screen: black page, **no ≡ menu**, reduced status bar, persona in the listening state (filled disc, 3.1.6) with the non-personalised greeting "**What's on your mind?**" (unlocked greetings on the same era are personalised: "What can I help you with, Ho Young?" C3 88 s; "How can I help, Richard?" C5 83 s), light query box with a mic glyph at the right; nav bar Back / Start / Search; after it closes the lock screen returns (30.5 s). The user's finger covers the Search-key area as it opens (25–26 s), key itself not visible | visual | **15254.541** | C4 camera 30 fps, Lumia 650 | C4 @ 25–31 s | `sheets/c_cve/cve_cortana.png`, `frames/c_cve/cve_t26.5.png`, `frames/c_cve/cve_t27.5.png` | LOW |
| 3.5.6 | What worked while locked (documents) | D4: "interact with Cortana using speech while the system is locked". D2: with the option on, Cortana above lock let a person with physical access reach the photo library and modify or delete photos without signing in (security bypass; Microsoft did not fix it; the workaround is turning the Lock Screen option Off). No Microsoft W10M-specific list of allowed commands was found; the Windows Experience Blog 2016-08-22 list (weather, reminders, play songs) is PC-only | quote | 14393+ | D2; D4 | D2, D4 URLs | `docs/cve.json` | MEDIUM |

### 3.6 Tally

Counting table rows in 3.1–3.5 (the text-message step table is one row, 3.4.1):

| Subsection | HIGH | MEDIUM | LOW | UNMEASURED |
|---|---|---|---|---|
| 3.1 listening / waveform (14 rows) | 0 | 13 | 1 | 0 |
| 3.2 speaking (6) | 0 | 4 | 1 | 1 |
| 3.3 text box (9) | 5 | 3 | 0 | 1 |
| 3.4 confirm flows (5) | 0 | 2 | 1 | 2 |
| 3.5 lock screen (6) | 0 | 4 | 2 | 0 |
| **Total (40)** | **5** | **26** | **5** | **4** |

(3.4.1 is counted MEDIUM: wording MEDIUM, layout LOW.)

Key observations made while reviewing this footage (Search key on a locked phone; S2 nav-bar press scan) are recorded in §4 rows 4.2.5 and 4.2.6.

---

## 4. Back key on Start, and the Search key

### 4.0 Sources

| ID | Video / document | Channel, date | Build (how known) | Capture | Res / fps | Ruler |
|---|---|---|---|---|---|---|
| B1 | https://www.youtube.com/watch?v=K0iHuoHJlqs "Microsoft Lumia 550 - How to Navigate (Windows 10 Device)" | Asian Geek Squad, 2016-01-18 | not shown; a Lumia 550 in January 2016 ships 10586 → **≈10586, non-governing** | camera, handheld, near head-on; on-screen nav bar visible; narrated | 1280x720 @ 30 fps | timing only (±1 frame = ±33 ms). Key identity from the thumb covering that key's glyph and the glyph re-appearing on release (`$R6/sheets/o_back/K0i_navzoom.png`, `K0i_navzoom2.png`); narration transcribed with faster-whisper small.en (`$R6/scripts/o_stt.py`) |
| S1 / S2 | R3 S1 (14393) and S2 (15063) Project My Screen recordings | — | 14393 / 15063 | screen recordings | 60 fps | all touch-indicator dots scanned at 10 fps (`$R6/scripts/o_dots.py` → `$R6/frames/o_back/S1_dots.tsv`, `S2_dots.tsv`); no Back or Search press is made while Start is showing in either recording (S1: 30 nav-bar dot events, S2: 15; every Back press is inside an app) |
| D1 | Microsoft, Lumia with Windows 10 Mobile User Guide, Issue 1.1 (§0.2) | 2016 | 10586-era document | document | — | line numbers in `$R6/docs/ug.txt` |
| D3 | Microsoft, "Navigation history and backwards navigation for UWP apps", windows-dev-docs commit d6050b7 (ms.date 05/19/2017): https://raw.githubusercontent.com/MicrosoftDocs/windows-dev-docs/d6050b733085394df70b4c16672195a7b26d7c07/windows-apps-src/design/basics/navigation-history-and-backwards-navigation.md | 2017 | covers phones (15063 era) | document | — | local copy `$R6/docs/nav_history_d6050b7.md` |

Other footage checked and not used: YJXlI-g3Hd8 (On MSFT, 2016-01, oblique camera of the app switcher, nav bar not legible), n225eAeKYys (2016-04, phone in a case, hardware keys not visible), OyIi4TO7GTs (camera; key presses not resolvable at its scale). YouTube searches: "windows 10 mobile back button start screen", "windows 10 mobile multitasking back button task switcher", "windows 10 mobile close apps back button", "windows 10 mobile navigation bar back start search explained", "windows 10 mobile search button bing search", "lumia 950 navigation bar tutorial". Web searches: "Windows 10 Mobile back button on start screen does nothing previous app", "support.microsoft.com Windows 10 Mobile search button Cortana press and hold …", "… back button start screen last app OR previous app resume forum Lumia".

### 4.1 Back key pressed while Start is showing

| # | Item | Value ± tol | Method | Build | Source (device, epx canvas, fps) | URL @ t | Evidence frame | Conf |
|---|---|---|---|---|---|---|---|---|
| 4.1.1 | What happens | **Start leaves and the most recently used app resumes on the page it was left on** (here Settings › Wi-Fi, opened before returning to Start). Narration over the press (47.4–52.5 s): "Now back button will take you back to the last app you opened and holding the back button will take you to a recent list of apps that you have opened." | frame sequence + key identity (thumb covers the ← glyph 47.17–47.50 s, glyph back at 47.53 s) + narration | ≈10586 | B1, Lumia 550, 30 fps | K0iHuoHJlqs @ 47.2–48.7 s | `$R6/sheets/o_back/K0i_47f_00.png`, `K0i_navzoom.png`, `$R6/sheets/o_back/K0i_44_00.png` | MEDIUM |
| 4.1.2 | Documented rule (consistent with 4.1.1) | D1 line 439–440: "To go back to the previous screen you were in, tap the back key. Your phone remembers all the apps and websites you've visited since the last time your screen was locked." D3 line 121: "If the in-app back stack is empty, the system might navigate to the previous app in the app stack or to the Start screen." | quotation | 10586 doc / 2017 doc | D1, D3 | — | `$R6/docs/ug.txt`, `$R6/docs/nav_history_d6050b7.md` | MEDIUM |
| 4.1.3 | Motion on the way out | Start exit begins **≈230 ms after the key is released** (release 47.53 s, first Start change 47.77 s) and reaches black in **3 frames = 100 ± 33 ms**; the frames look like the tap-launch exit (grid enlarging and fading, R3 A11), too blurred at 30 fps to fit a scale | per-frame luminance difference vs a Start reference, 30 fps | ≈10586 | B1 | @ 47.53–47.83 s | `K0i_47f_00.png` frames 47.77–47.83 | LOW |
| 4.1.4 | Resume gap and app entrance | black for 20 frames (47.83→48.50 s, **670 ms**: app resume, not animation), then the Settings page appears and settles in **≈5 frames = 167 ± 33 ms** (48.53→48.67 s) | same | ≈10586 | B1 | @ 47.83–48.70 s | `K0i_47f_00.png` | LOW |
| 4.1.5 | Back pressed on Start with nothing to go back to (e.g. right after unlocking) | **UNMEASURED**: no footage of it (the dot scan of S1/S2 found no Back press on Start; B1 and the other videos only press it with a previous app). D1's "since the last time your screen was locked" implies the history is cleared by locking, but what the key then does on Start is not shown or stated | — | — | — | — | — | UNMEASURED |
| 4.1.6 | Press-and-hold Back (related) | opens the app switcher: cards of the open apps side by side (B1 52.67–55.5 s); D1 line 437: "To see which apps you have open, tap and hold the back key. To switch to another app, tap the app you want. To close an app, tap [×] at the top right corner of the app." | frame sequence + quotation | ≈10586 | B1, D1 | K0iHuoHJlqs @ 52.6–55.7 s | `$R6/sheets/o_back/K0i_44_00.png` | MEDIUM |
| 4.1.7 | Back pressed while Start is in **edit mode** | **exits edit mode** (tiles undim and return to full size, as the tap exit in §1.5.3); Start begins to undim ≈160 ms after the finger lifts (lift 249.90 s → first change 250.06 s), done by 250.26 s; an expanded folder stayed expanded | frame sequence (50 fps camera; finger partly occludes the key) | 10586–14332 era (date) — non-governing | E4 (§1.0), Lumia 950 XL-class, 50 fps | https://www.youtube.com/watch?v=wqpUvABGeP8 @ 247.3–250.46 s | `$R6/sheets/e_map/wqp_back.png`, `$R6/sheets/e_map/wqp_back_zoom.png` | LOW |

### 4.2 Search key

| # | Item | Value ± tol | Method | Build | Source (device, epx canvas, fps) | URL @ t | Evidence frame | Conf |
|---|---|---|---|---|---|---|---|---|
| 4.2.1 | Tap | **opens Cortana** (on a phone with Cortana). D1 lines 445–448: "To search the web or for items on your phone, tap the search key. If your phone has Cortana, to control your phone with your voice, tap and hold the search key, and say what you want to do. Or, to change the Cortana settings or use other Cortana functions, tap the search key." Footage: from Start, a tap (thumb on the ⌕ glyph 57.10–57.37 s, released 57.43 s) opens Cortana's first-run page ("Before we get started, I'll need you to sign in with a Microsoft account", "No thanks" / "Sign in", box "I'm Cortana. Ask me anything.") — this account was not signed in; narration: "And the search here is Cortana, we'll sign in at a later time." | quotation + frame sequence | ≈10586 | D1; B1, 30 fps | K0iHuoHJlqs @ 57.1–60.7 s | `$R6/sheets/o_back/K0i_55_00.png`, `K0i_navzoom2.png` | MEDIUM |
| 4.2.2 | Tap: motion | Start exit begins **≈400 ms after release** (57.43 → 57.83 s) and reaches black in **5 frames = 167 ± 33 ms** (57.83 → 58.00 s); Cortana page appears at 58.43–58.50 s | per-frame luminance difference, 30 fps | ≈10586 | B1 | @ 57.43–58.50 s | same | LOW |
| 4.2.3 | Press and hold | **opens Cortana listening** (voice). D1 lines 437–448 (above) and line 1260: "To open the Cortana voice assistant quickly, tap and hold the search key." | quotation | 10586 doc | D1 | — | `$R6/docs/ug.txt` | MEDIUM |
| 4.2.4 | Press and hold while locked | opens Cortana without unlocking — D1 lines 4312–4314 under "Use your phone when it's locked": "Open Cortana — If your phone has Cortana, to open Cortana, tap and hold the search key." (setting and scope: §3.5) | quotation | 10586 doc | D1 | — | `$R6/docs/ug.txt` | MEDIUM |
| 4.2.5 | Tap / hold behaviour on 14393/15063 footage | **UNMEASURED** from footage: neither governing recording presses Search (S2's only dot near the key, 492.2 s at x 204 y 498, is above the nav bar inside Settings; S1's dots at 1008–1011 s are on Edge's tab button; an independent 30-fps scan of S2 in §3's review found the same: no Search press and no Back press while Start is on screen) | touch-dot scan | 14393 / 15063 | S1, S2 | — | `$R6/frames/o_back/S1_dots.tsv`, `S2_dots.tsv` | UNMEASURED |
| 4.2.6 | Search key on a **locked** phone (footage, final build) | with the finger over the Search-key end of the nav bar, Cortana opens full-screen over the lock screen directly in the **listening** state, greeting "What's on your mind?", no ≡ menu (details §3.5.5); tap vs hold not visible. The 10586-era Cortana setting that enables it reads "Open Cortana when I press and hold the Search button – even when my device is locked." (§3.5.1) | frame sequence | **15254.541** (About page) | C4 (§3.0), Lumia 650 camera, 30 fps | https://www.youtube.com/watch?v=0jQzbOIeoWc @ 25–31 s | `$R6/sheets/c_cve/cve_cortana.png`, `$R6/frames/c_wpx/wpx_t52.png` | LOW |

### 4.3 Tally

HIGH 0 · MEDIUM 6 (4.1.1, 4.1.2, 4.1.6, 4.2.1, 4.2.3, 4.2.4) · LOW 5 (4.1.3, 4.1.4, 4.1.7, 4.2.2, 4.2.6) · UNMEASURED 2 (4.1.5, 4.2.5).

---

## 5. App list "New" caption

### 5.0 Sources

| ID | Video / document | Build | Capture | Ruler |
|---|---|---|---|---|
| S2 | https://www.youtube.com/watch?v=E6vvrz4ozpE (Windows Central, 2017-03-16) | **15063** | Project My Screen, 60 fps | 1 px = 4.813 phys = 1.375 epx (§0.1) |
| S1 | https://www.youtube.com/watch?v=I98ENfXJRqA (Windows Central, 2016-08-16) | 14393 | Project My Screen, 60 fps | 1 px = 4.289 phys = 1.072 epx |

Searches for a written rule (none found for W10M): WebSearch "Windows 10 Mobile app list new label under app name disappear open app", "windows phone app list new tag under newly installed apps remove forum", "Windows 10 Mobile apps list newly installed apps New highlighted under the app name", "Windows Phone 8.1 app list new apps highlighted until opened"; WebFetch of the Microsoft Devices Blog "Lumia screens explained" (2015-01-26: says new apps land in the App list, nothing about a label); D1 user guide text search for "new" (no match about the app list). The hits describe **desktop** Windows 8.1/10 Start-menu "New" tags and one WP8.1 statement that new apps are "highlighted with the word new"; none gives a clearing rule for W10M, so none is used as a value.

### 5.1 Look and placement (governing build)

| # | Item | Value ± tol | Method | Build | Source (device, epx canvas, fps) | URL @ t | Evidence frame | Conf |
|---|---|---|---|---|---|---|---|---|
| 5.1.1 | Which apps carry it | Audible, Lumia Offers, Microsoft Band, The Guardian (and Netflix, visible at 515 s) carry "New"; pre-installed system apps and British Airways do not. No "Recently added" group exists (R3 A13) | visual read-off | 15063 | S2, 950 XL, 411x731 epx, 60 fps | E6vvrz4ozpE @ 76, 83, 87, 515 s | `$R6/sheets/o_new/S2_new_zoom.png`, `$R6/sheets/o_new/S2_64_00.png`, `$R6/sheets/o_back/S2_514_00.png` | MEDIUM |
| 5.1.2 | Text and colour | the word "New", accent colour (capture rendition of the blurred 12-epx text (26,52,131)–(35,61,132); R3 measured opaque accent in this capture as (48,104,255)) | pixel sampling | 15063 | S2 | @ 76, 83, 87 s | same | MEDIUM |
| 5.1.3 | Size | "N" cap 6 px = **8.3 ± 1.4 epx** cap → **12 epx** font class (cap/0.7 ≈ 11.8); app name cap 9 px = 12.4 epx on the same frames | row bands (luminance / blue-channel) | 15063 | S2 | @ 76 s (Audible: name rows 219–228, "New" rows 236–241); 83 s (Lumia Offers: 351–360 / 368–373); 87 s (The Guardian: 366–375 / 384–388) | `$R6/frames/o_new/S2_t76.png`, `S2_t83.png`, `S2_t87.png` | MEDIUM |
| 5.1.4 | Horizontal position | left-aligned with the app name: both start at video x 531 (±1 px) = 57 epx from the screen's left edge (R3 C2 name x = 57 epx) | column bands | 15063 | S2 | @ 76, 83 s | same | MEDIUM |
| 5.1.5 | Vertical position / row height | the row keeps the normal pitch: icon runs at t=83 s are 314–343, 346–375 ("Lumia Offers", has caption), then 478–506 ("Microsoft Band", has caption), 509–539 → **pitch 32 px = 44 epx, unchanged**. Inside a captioned row the name moves up: name text centre 355.5 vs icon centre 360.5 (**−5 px = −7 ± 1.4 epx**; uncaptioned rows sit +2 px below the icon centre), and "New" sits below it with baseline-to-baseline **13 px = 18 ± 1.4 epx**. The name and caption stack is centred on the 41-epx icon | row runs of icon and text pixels | 15063 | S2 | @ 83 s | `$R6/frames/o_new/S2_t83.png` | MEDIUM |
| 5.1.6 | 14393 check | no "New" caption on any app-list frame in S1 (572–596 s, 1072–1075 s, 1136–1149 s; blue-text row scan returned none except the accent icons), and no newly installed app is visible there, so S1 neither confirms nor refutes the caption on 14393 | blue-text row scan (`$R6/frames/o_new/S1_t*.png`) | 14393 | S1, 950, 360x640 epx | I98ENfXJRqA @ 574–596, 1073, 1139–1148 s | `$R6/frames/o_new/S1_t1146.png` etc. | — (not counted) |
| 5.1.7 | Conflicting build: a "Recently added" group instead | a light-theme recording uploaded 2017-03-23 (build not on screen; E2 in §1.0) shows, under the Search box, a grey caption header **"Recently added"** with a **×** at the right, two entries ("Blanker", "Device Diagnostics HUB") with **no "New" caption**, a hairline rule, then "#" and the alphabetical list. S1 (14393) and S2 (15063) show no such group (R3 A13), so this is most likely an Insider build; which form the final 15254 build used is **not established** | visual | unverified (≥14393; probably Insider) | E2, PMS-style mirror, 59 fps | https://www.youtube.com/watch?v=ltz0SUe4XKE @ 244.5 s | `$R6/frames/e_ltz/ltz_applist_t244.5.png`, `$R6/frames/e_ltz/ltz_applist_zoom.png` | LOW |

### 5.2 When it clears

| # | Item | Value ± tol | Method | Build | Source (device, epx canvas, fps) | URL @ t | Evidence frame | Conf |
|---|---|---|---|---|---|---|---|---|
| 5.2.1 | Clears after a fixed time? | **Not within 3 days.** Settings › Apps & features lists Audible with the date **3/13/2017**; the lock screen in the same recording reads "Thursday, March 16" (2017-03-16 is a Thursday). Audible still shows "New" at 76 s and again at 515 s (after its data was reset at 361–374 s). So any timer is longer than ≈3 days | date read-off across the recording | 15063 | S2 | @ 347–349.5 s (install dates), 451 s (lock-screen date), 76 s and 515 s (caption) | `$R6/sheets/o_new/S2_347_zoom.png`, `$R6/sheets/o_new/S2_338_00.png`, `$R6/sheets/o_back/S2_448_00.png` | MEDIUM |
| 5.2.2 | Clears on first launch (or on pinning)? | **UNMEASURED.** Indirect only: British Airways has the **same date (3/13/2017)** but no caption, and it is pinned to Start as a small tile (S2 t=20 s) — consistent with the caption clearing once the app is opened or pinned, but its launch history is not shown, and no footage shows a captioned app being opened and the list revisited. Tried: every app-list pass in S1 and S2 (no captioned app is launched in either), the D1 user guide, and the web searches in 5.0 | — | 15063 | S2 | @ 20 s (BA tile), 76 s, 347 s | `$R6/sheets/o_new/S2_start_top.png`, `S2_347_zoom.png` | UNMEASURED |
| 5.2.3 | Reset app data re-adds it? | Not testable: Audible already had the caption before its reset (76 s) and still has it after (515 s) | — | 15063 | S2 | @ 76, 361–374, 515 s | as 5.2.1 | — (not counted) |

### 5.3 Tally

HIGH 0 · MEDIUM 6 (5.1.1–5.1.5, 5.2.1) · LOW 1 (5.1.7) · UNMEASURED 1 (5.2.2). Rows 5.1.6 and 5.2.3 are observations, not values.

---

## 6. Glance screen

### 6.0 Sources

| ID | Video / document | Channel, date | Build (how known) | Capture | Res / fps | Ruler |
|---|---|---|---|---|---|---|
| G1 | https://www.youtube.com/watch?v=3mW49AE_1Lw "How to Customize Glance Screen on Lumia 950, 950 XL, 650, 640 XL, 930, 830 or ANY Windows 10 Phone" | Shaan Haider, uploaded 2016-07-03; the Glance date reads "Tuesday, June 21" (2016) | **not shown**: June 2016 means 10586.x or a 14xxx Insider build → **non-governing, unverified** | camera, handheld, near head-on | 1280x720 @ 30 fps | Body-relative units. Per frame, straight lines are fitted to the black body's left, right and top edges (`$R6/scripts/o_glance_body.py`, `o_glance_bands.py`). **u** = distance from the left edge / body width W; **v** = perpendicular distance below the top edge / W. The display rectangle is located on a lit Start frame of the same phone (t=300 s, `$R6/scripts/o_glance_calib.py`, see 6.1) |
| S1 / S2 | R3 S1 (14393) and S2 (15063) Project My Screen recordings | — | 14393 / **15063** | screen recordings | 60 fps | Nav-bar Windows-glyph centre sits at **0.962** (S1, t=580 s) and **0.968** (S2, t=20 s) of the screen height; 0.964 ± 0.003 is used below. The Start grid top, 108 ± 5 phys of 2560 (R3 A1), is the cross-check. S2 also gives the 15063 settings page |

No screen recording of the Glance display itself was found. Downloaded and rejected:
- kKXEWaIMvhU (2016-06): Glance half out of frame.
- aT66sELYJsw (2016-04): close-up edited sequence; the clips before and after the 06:10 → 06:11 change are framed differently, so positions cannot be compared.
- r5Kdk9j7J1w: Lumia 830, build 10536, oblique.
- DNkfxQIIiHI (2016-02): Lumia 640, top-down; display edges not separable.
- qPZLJqWEQdA: 15063 Lumia 950 XL; no Glance shown.
- AgoXrhpjlfc: GSMArena 950/950 XL review; no Glance shown.

YouTube searches: "windows 10 mobile glance screen burn in", "lumia 950 glance screen timelapse", "windows 10 mobile glance screen always on", "lumia 950 xl glance screen", "glance screen windows 10 mobile creators update", "lumia 950 glance screen night mode", "windows 10 mobile glance screen 15063", "glance screen lumia 650 windows 10", "pantalla glance lumia 950", "glance screen lumia 950 xl moving clock", "lumia 950 always on display glance test", "glance screen w10m 15254", "Lumia 950 XL review glance screen AMOLED". D1 (user guide lines 1107–1121) covers the settings only, nothing on position or burn-in.

### 6.1 Clock vertical position (and the block under it)

Calibration, from G1 t=300 s (lit Start, status-bar clock 2:17; `$R6/sheets/o_glance/g300b.png`):
- Body width W = 299.8 px.
- Display top edge (bezel → wallpaper crossing on 4 columns): v = 0.2226 / 0.2249 / 0.2271 / 0.2257 → **0.225**.
- Nav-bar glyph centres: v = 1.766 / 1.770 / 1.775 → **1.770**. Body bottom: v = 2.02.
- With the glyph at 0.964 of the screen height, the display is **H = 1.603 W** tall and 0.9015 W wide (9:16), with a 0.049 W side margin.
- Check: the first tile row's top edge is at v = 0.293 → 0.0424 H = **109 phys of 2560**, matching R3 A1's grid top of 108 ± 5 phys.
- Conversion: `y_epx = (v − 0.225) / 1.603 × 640`, `x_epx = (u − 0.049) / 0.9015 × 360` (360x640 epx canvas assumed for the 950).

Glance band values below are the mean of four near-frontal frames: t=18, 20 and 30 s ("2:12") and t=56 s ("2:13"). Two frames are excluded. At t=44 s a thumb covers the content. At t=77 s every band reads 0.03–0.04 W lower; it is the same minute as t=56 s, so this is a perspective error.

| # | Item | Value ± tol | Method | Build | Source (device, epx canvas, fps) | URL @ t | Evidence frame | Conf |
|---|---|---|---|---|---|---|---|---|
| 6.1.1 | Clock digit top, from the screen top | **413 ± 10 epx** (0.646 of the screen height); v = 1.27 / 1.26 / 1.24 / 1.27 | bright-pixel row bands (L > 140) in body units, converted as above | unverified (June 2016) | G1, Lumia 950 (body 2.02 W tall), 360x640 epx assumed, 30 fps | 3mW49AE_1Lw @ 18, 20, 30, 56 s | `$R6/frames/o_glance/sha_t18.png`, `sha_t20.png`, `sha_t30.png`, `sha_t56.png`; `$R6/sheets/o_glance/g_multi2.png` | LOW |
| 6.1.2 | Clock digit bottom | **465 ± 10 epx** (0.727); v = 1.40 / 1.38 / 1.38 / 1.40 | same | unverified | G1 | same | same | LOW |
| 6.1.3 | Clock digit height and width | Height **52 ± 5 epx** (0.13 W). "2:12" spans u 0.121–0.385 = **29 → 134 epx** (105 epx wide; width/height 2.0). R3 A21 (S7, oblique camera, ≈14342) gave 45–47 ± 3 epx. The two disagree beyond tolerance, and both come from camera footage | same | unverified | G1 | same | same | LOW |
| 6.1.4 | Clock left edge | **29 ± 4 epx** from the screen's left edge (u = 0.121, 0.122, 0.119, 0.121); R3 A21 gave ≈31 epx | same | unverified | G1 | same | same | LOW |
| 6.1.5 | Block under the clock | **Date line** "Tuesday, June 21", left-aligned with the clock: centre v 1.469 → **497 ± 10 epx** (0.776); text right end u ≈ 0.52 → ≈ 190 epx. **Notification row** below it, glyphs with counts ("5", "1", wider gap, "4"): bright glyphs at v 1.54–1.60 → **525–549 epx** (centre 539, 0.842) and u 0.264–0.706 → **86–262 epx**. The first icon glyph is dimmer than the threshold, so the row may start slightly further left. **Clock format** "h:mm": no leading zero, no AM/PM | same + visual read-off | unverified | G1 | same | same | LOW |
| 6.1.6 | 15063 settings that shape the screen | Page "Glance screen": "See your clock and your lock screen info at a glance after your screen has turned off." Controls: toggle "Show Glance screen when your screen is turned off" (On); dropdown "Show Glance screen for" ("5 minutes"); toggle "Always show Glance screen when charging" (On); toggle "Show lock screen background picture" (Off). Header "Night mode": "Dim the Glance screen during the set time range (background picture won't show)" (Off). This page has no text-colour picker; D1 (Feb 2016) still describes choosing a night-mode text colour | transcription | **15063** | S2, 950 XL, 411x731 epx, 60 fps | E6vvrz4ozpE @ 318, 326 s | `$R6/sheets/o_glance/S2_glance_settings.png` | MEDIUM (one screen recording; the values shown are that user's choices, not necessarily defaults) |

### 6.2 Burn-in shift pattern

| # | Item | Value ± tol | Method | Build | Source (device, epx canvas, fps) | URL @ t | Evidence frame | Conf |
|---|---|---|---|---|---|---|---|---|
| 6.2.1 | Displacement at a minute change (2:12 → 2:13) | **None detected above ≈ 0.02 W (≈ 8 epx).** Clock top v is 1.24–1.27 at 2:12 (t=18, 20, 30 s) and 1.27 at 2:13 (t=56 s). Clock left u is 0.119–0.122 at 2:12 and 0.121 at 2:13. On tilted frames a single-frame error reaches 0.04 W: t=77 s, also 2:13, reads 0.03–0.04 W lower than t=56 s | per-frame body-relative bands | unverified (June 2016) | G1 | 3mW49AE_1Lw @ 18–77 s | `$R6/frames/o_glance/sha_t*.png`, `$R6/sheets/o_glance/g_multi2.png` | LOW |
| 6.2.2 | Shift period, amplitude and path over longer periods | **UNMEASURED.** No footage keeps one phone framed across more than one minute change:<br>• G1's later "2:14" (154–170 s) and "2:16" (236–250 s) shots are zoomed in with the body's top edge out of frame, so the fit fails.<br>• aT66sELYJsw is re-framed between clips.<br>• The searches in 6.0 found no screen recording or time-lapse.<br>User reports exist but are not measurements and are not used as values. In one Windows Central forum thread (https://forums.windowscentral.com/windows-phones/395820-glance-screen-stays-stationary-shouldnt-move.html), a 2015-11-22 post says the 950's Glance "is not supposed to move", and a 2016-05-11 reply says "Mine moves now" | — | — | — | — | — | UNMEASURED |

### 6.3 Tally

HIGH 0 · MEDIUM 1 (6.1.6) · LOW 6 (6.1.1–6.1.5, 6.2.1) · UNMEASURED 1 (6.2.2).

---
