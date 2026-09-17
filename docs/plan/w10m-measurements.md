# Windows 10 Mobile fidelity measurements (R3)

Frame-by-frame measurements from real W10M footage for every row R1 (`w10m-reference.md`) marked NOT FOUND, plus the two review-round additions: (B) legacy WP7/8 motion values re-checked against W10M, and (C) the Phase 1 addendum (Settings, app list, live-tile content layouts). Nothing below is estimated; every number is measured from a cited frame, or the row says UNMEASURED.

Governing build (owner ruling): the final W10M release (15063 Creators Update / 15254). 14393 footage is used where the element did not visibly change; pre-14393 footage is used only for elements that did not change after 14322/14356/14393 and is flagged.

Evidence root (scratchpad, shared with other agents): `$R3 = /tmp/claude-1000/-home-jeremyking/283f1050-f733-41ac-a752-f9b0871bdcbe/scratchpad/r3-work/`. Frames are under `$R3/frames/`, scripts under `$R3/scripts/`. Videos were deleted after measurement (per the brief); each source is cited by URL + timestamp so any frame can be re-extracted with `ffmpeg -ss <t> -i <video> -frames:v 1`.

Confidence: HIGH = 2+ agreeing screen recordings; MEDIUM = one clean screen recording or agreeing camera footage; LOW = partial view / camera at an angle / build not governing.

---

## 0. Sources, calibration, and unit conversions

### 0.1 Footage used

| ID | Video | Channel / date | Build (how known) | Capture type | Downloaded | Notes |
|---|---|---|---|---|---|---|
| S1 | https://www.youtube.com/watch?v=I98ENfXJRqA "Windows 10 Mobile Anniversary Update - Official Release Demo" | Windows Central, 2016-08-16 | **14393** (title; status bar shows 7/28/2016; 16 quick actions in Settings) | Project My Screen capture with a phone-frame overlay (no camera skew) | 1280x720 @ 60.00 fps (native 1080p60) | Device 1440x2560 at **400 %** → 360x640 epx (see 0.2). Primary 14393 source. |
| S2 | https://www.youtube.com/watch?v=E6vvrz4ozpE "Windows 10 Mobile Creators Update - Official Release Demo" | Windows Central, 2017-03-16 | **15063** (title) — governing | Project My Screen capture, Lumia 950 XL frame overlay | 1280x720 @ 60.00 fps (native 1080p60) | Device 1440x2560 at **350 %** → 411x731 epx (see 0.2). Primary governing source. |
| S3 | https://www.youtube.com/watch?v=nTSncoDOr_w "Windows 10 Mobile Action center blur effect" | OneGeeks, 2016-01-02 | unknown (≥10586 by date; Russian UI) | full-frame screen capture (landscape video of a portrait screen) | 1280x720 @ 29.97 fps | 4-column Start → 480-epx-wide canvas (Lumia 1520-class or a 950 XL at reduced scale). Frame blending visible; used for layouts, timings marked LOW. |
| S4 | https://www.youtube.com/watch?v=gkofbDh_t0o "Hands-on with Windows 10 Mobile build 14342" | Neowin, 2016-05-18 | 14342 (title) | camera, head-on, tripod | 1280x720 @ 59.94 fps | 3-column Start. Timings only. |
| S5 | https://www.youtube.com/watch?v=kU9iT0wWUbo "Hands on with Windows 10 Mobile Anniversary Update build 14393" | Ian Dixon, 2016-07-19 | 14393 (title) | camera, head-on | 1280x720 @ 23.98 fps | People tile timing only. |
| S6 | https://www.youtube.com/watch?v=DmKXg93Qpac "Dissecting Windows 10 Mobile: Start screen" | On MSFT, 2016-05-13 | ≈14342 (date) | camera, handheld | 1280x720 @ 59.94 fps | Full-screen-picture Start; parallax; Colors page; Start settings page. |
| S7 | https://www.youtube.com/watch?v=l5tVO2ZZwzI "Dissecting Windows 10 Mobile: Glance" | On MSFT, 2016-05-07 | ≈14342 (date) | camera, oblique | 1280x720 @ 59.94 fps | Glance only. LOW. |
| S8 | https://www.youtube.com/watch?v=Nr9Z-9a8l9c "Windows 10 mobile Start screen Personalization" | My Windows Mobile, 2015-08-10 | pre-10586 (date) — **not governing** | Project My Screen capture, generic phone frame | 1280x720 @ 30 fps | Only used for the Colors palette grid and Start-settings slider observation; flagged. |
| S9 | https://www.youtube.com/watch?v=lWIsas3izaI "Hands-on with Windows 10 Mobile Build 10586" | Windows (Microsoft), 2015-11-11 | 10586 (title) — **not governing** | Project My Screen capture | 640x360 @ 25 fps (only format served) | Jump-grid only; LOW. |
| S10 | https://www.youtube.com/watch?v=NzNw0Rj-wME "Windows 10 Mobile - MSN Weather" | My Windows Mobile, 2015-08-10 | pre-10586 (date) — **not governing** | Project My Screen capture, generic phone frame | 1280x720 @ 29.97 fps (native 1080p30) | Only screen recording of the MSN Weather app found; screen x 470.4–814.5, y 46–656 (344 px wide, canvas assumed 360 epx → 1 px = 1.047 epx; device unknown). Used for C5, flagged. |
| S11 | https://www.youtube.com/watch?v=T-vdpqDeRHk "MSN weather part2" | dadosparco, 2017-05-15 | 15063-era (date; Serbian UI) | camera, steep oblique angle | 1280x720 @ 29 fps | Structure check of the final-release Weather app only (t=44–50 s); no geometry. |

Other candidates downloaded and rejected as camera footage of no extra value: 6zcTRCEPlcM, ASU_banSWRY, wtjeV4RfFfg, 6AtcDqGeLXs, xILUjhhSiGk, Q4dK36A1nug, yCF3ENNXor8, ZTPNhjCGgk0, iWP1vE7Rd5A, 4czNxERQnSU (desktop), 8-0N0M4b2ns (14965 camera, Weather app too small/blurred), Zno0fJYWKxk (15063 camera, no Weather), v9rLzdQzPjY (Dec 2017 camera, Weather app stuck on its loading screen). No_emcWR9Cc (portrait 608x1080, May 2017) could not be downloaded (403 on every format). Total downloaded ≈ 1.1 GB; all ≤720p.

### 0.2 Screen rectangles and rulers

S1 (I98): screen rectangle measured on the white Edge page at t=1000 s and t=975 s (`$R3/frames/calib/I98_white_t1000.png`): x 471.35 → 807.5 (sub-pixel edge from partial-pixel luminance), top y 43.57. Width **335.7 ± 0.6 px = 1440 physical px** → 4.289 phys px per video px. Canvas: 1440x2560 at 400 % = 360x640 epx (R1 §11) → **1 video px = 1.072 epx**; ±1 px = ±4.3 phys = ±1.1 epx. The bottom edge is black-on-black and could not be measured; the nav-bar height is therefore UNMEASURED.

S2 (E6v): screen rectangle from the bezel/tile transitions at t=20 s (`$R3/frames/start/E6v_start_t20.png`): x 489.3 → 788.5, y 94.3 → ~626. Width **299.2 ± 0.6 px = 1440 phys px** → 4.813 phys/px. Scaling: this phone is at **350 %, not 400 %** — inferred, because the UWP toggle switch measures 32 × 14.5 px = 154 × 70 phys, which is 44 × 20 epx only at 350 % (38.5 × 17.5 at 400 %), and the app-list row pitch 32 px = 154 phys = 44 epx at 350 % (S1 gives 41 px = 176 phys = 44 epx at 400 %); the status bar (103 phys ≈ 29 epx at 350 %) agrees. Canvas 411x731 epx → **1 video px = 1.375 epx**; ±1 px = ±4.8 phys = ±1.4 epx.

Because the two capture phones run different scale factors, every value below is given in **physical px on a 1440-wide panel** (device-independent for the Start shell, which lays out in physical pixels — see A1) **and** in epx on the source's own canvas. Values from different sources are never mixed without this conversion.

S3 (nTS): screen 396 px wide (x 442–838), canvas assumed 480 epx wide → 1 px = 1.21 epx (device unverified; LOW for geometry).
S8 (Nr9Z): screen 342 px wide (x 472–814), canvas assumed 360 epx → 1 px = 1.05 epx (2015 device unknown).

Timing tolerance: ±1 frame at the source fps (±16.7 ms at 60, ±33 ms at 30, ±42 ms at 24). Both Project My Screen captures contain occasional duplicated frames (identical consecutive frames, e.g. S1 13244=13245, S2 151.25=151.267), so a frame count can under-report motion by one frame; wall-clock durations are unaffected.

Colour caveat: the captures render the system accent as (48,104,255) where the nominal Win10 default blue is #0078D7; hex values sampled from video are therefore reported as "capture rendition", not as authoritative colours.

### 0.3 Method summary

- Durations: ffprobe native fps; frames extracted with `fps=<native>`; per-frame numeric trackers (`$R3/scripts/tilediff.py`, `launch_track.py`, `rowstagger.py`, `scalefit2.py`, `nts_track*.py`, `parallax.py`, `ret_track.py`) locate first/last change; every counted sequence was also inspected visually via contact sheets in `$R3/frames/sheets/`.
- Geometry: sub-pixel edge crossings on a single channel (`$R3/scripts/edges_ch.py`) or luminance bands (`$R3/scripts/bands.py`), converted with the rulers in 0.2.
- Scale fits for the launch animations: brute-force uniform-scale search on gradient images about candidate centres (`$R3/scripts/scalefit2.py`), reported with the correlation so weak fits are visible.

---

## A. R1 NOT FOUND rows

Row numbers follow R1's sections.

### A1. Start gutter and edge margin (R1 §1.2), plus the full grid

Both captures show the 3-medium-column layout ("Show more tiles" ON, which is the Lumia 950/950 XL default per R1 §1.3). Measured edges (blue-channel threshold 110/114, sub-pixel):

| Item | S1 (14393, 400 %) | S2 (15063, 350 %) | Physical px on 1440 (consensus, ±5) | Fraction of screen width |
|---|---|---|---|---|
| Column/row pitch (tile + gutter) | 110.8 px = 118.8 epx | 98.5 px = 135.4 epx | **474.5** | 0.3295 |
| Medium tile side | 106.5 px = 114.2 epx | 95.2 px = 130.9 epx | **457.5** | 0.3177 |
| Gutter | 4.5 px = 4.8 epx | 3.3 px = 4.5 epx | **17.5 ± 4** | 0.012 |
| Left edge margin | 3.1 px = 3.3 epx | 2.7 px = 3.7 epx | **13 ± 4** | 0.009 |
| Right edge margin | 5.2 px = 5.6 epx | 4.2 px = 5.8 epx | **21 ± 4** | 0.015 |
| Small tile side | 50.7 px = 54.4 epx | 45.5 px = 62.6 epx | **218 ± 5** | 0.151 |
| Gutter between small tiles | 4.45 px = 4.8 epx | 3.9 px = 5.4 epx | 19 ± 4 | — |
| Wide tile width (= 2 medium + gutter) | 216.95 px | 193.9 px | 931 ± 5 | 0.647 |
| Grid top (screen top → first tile row) | 26.1 px = 28.0 epx | 21.4 px = 29.4 epx | 108 ± 5 | — |

Check: 13 + 3×457.5 + 2×17.5 + 21 = 1441.5 ≈ 1440.

Key finding: the Start grid is identical in **physical pixels** on both phones despite the different scale factors (S1 tile 457 phys, S2 458 phys) — the Start shell lays tiles out from the panel width, not from epx. Expressed on the 360-epx canvas used by R1: pitch 118.6, tile 114.4, gutter ≈4.4, margins ≈3 (left) / ≈5 (right), small tile 54.5, all ± ~1.2 epx. The margins are asymmetric (left ≈ 13 phys, right ≈ 21 phys) in both sources. The UWP 150/71 epx tile sizes do not apply on-phone.

- Sources: S1 t=12.0 s (`$R3/frames/start/I98_start_t12.png`); S2 t=20.0 s (`$R3/frames/start/E6v_start_t20.png`). Edge listings: run `edges_ch.py` as recorded in the session (rows 235/255/330/360, cols 520/540/640/660/760 for S1; rows 150/260/360/460/560, cols 520/540/640/740 for S2).
- Confidence: **HIGH** (two screen recordings, 14393 and 15063, agree within 1 phys px on tile and pitch).
- 2-column layout ("Show more tiles" OFF): only S6 camera footage at an angle shows it (t=72 s, `$R3/frames/dmk/DmK_t72.png`: wide tile spans ≈96 % of the visible screen width, medium ≈ half). Exact gutter/margins for that layout: **UNMEASURED** (no screen recording found with the 2-column layout on a 14393+ build; searched "show more tiles off", "Lumia 640 Windows 10 Mobile start", "Windows 10 Mobile 2 columns").
- Nav bar height: UNMEASURED (black nav bar on black bezel in both captures).

### A2. Tile label (app name) font size (R1 §1.4)

- Cap height of "Cortana"/"People" labels: S1 rows 272–279 = 8 px = 34 phys = **8.6 ± 1.1 epx** (400 %); S2 rows 297–303 = 7 px = 34 phys = 9.6 ± 1.4 epx (350 %). Segoe UI cap-height ratio ≈0.70 em → font size **12 epx ± 1.5** in both (12.3 / 13.7), i.e. the `caption` style R1 pointed at.
- Label inset: text starts 32 ± 4 phys px from the tile's left edge (S1: 482 − 474.45 = 7.5 px; S2: 597 − 590.45 = 6.5 px) and the baseline sits 32 ± 4 phys above the tile's bottom edge (S1: 286.4 − 279; S2: 309.4 − 303). On the 400 % canvas that is **8 epx**, matching the UWP 8-px tile content margin.
- Sources: S1 t=12 s; S2 t=20 s (frames as A1). Bands from `bands.py` thr 450 ("PeopleLabel", "CortanaLabel", "StoreLabel").
- Confidence: **HIGH** for the 12-epx size class (two recordings), MEDIUM for the exact cap height (±1 px at 720p).

### A3. Tile transparency slider default (R1 §1.5)

**UNMEASURED.** No footage shows a fresh device's slider. Observed user-set positions: S6 (≈14342, camera) t=40 s thumb at 59.7 % of the track (`$R3/frames/dmk/DmK_t40.png`, thumb x=645 on track 460–770); S8 (2015 build) t=76 s thumb at 22.6 % (`$R3/frames/nr9z/Nr9Z_t76.png`, thumb x=543 on track 483–748). Two different values, both after user customisation, so no default can be inferred.

### A4. Which tiles go transparent in full-screen-picture mode (R1 §1.5)

Observed in S1 t=12 s and S2 t=20 s (both full-screen-picture Starts): tiles drawn with the system accent background are translucent — Phone, Messaging, People, Cortana, Microsoft Edge, Continuum, Store (logo state), Camera, Lumia Highlights/Offers, Outlook Calendar, Outlook Mail, Transfer my Data, Photos, Groove, Movies & TV. Tiles with an app-supplied colour or image are opaque — Skype (light blue), Word/OneNote/PowerPoint/Excel, Candy Crush, Shazam, Gameloft Hub, Minecraft/Store promo images, News/Money photo tiles. Translucency sample: S1 Cortana tile flat colour (19,63,151) over a gutter/background reading (0,37,80) in the same frame, where opaque accent renders (48,104,255) in the same capture (A19) — so the accent tiles are a blend, not opaque. The blend fraction depends on the (unknown) slider setting, so no alpha is reported.
- Confidence: MEDIUM (two recordings, qualitative).

### A5/A6. Parallax rate; parallax in W10M full-screen mode (R1 §1.5)

- Only S6 (camera, ≈14342) contains a vertical Start scroll with a full-screen picture (S1/S2/S3 contain no vertical Start scroll — their Start "scroll" events were Start↔app-list swipes). Template tracking (`$R3/frames/dmk_scroll/s_013.png` reference, t=72.43 s; frames s_016/s_018/s_020 = t 72.53/72.60/72.67): wallpaper features (girl's face, window frame line; NCC 0.80–1.00) moved −14 / −23 / −30 px while the weather-tile icons (NCC 0.91–0.95) moved −56 / −88 / −117 px; the on-screen nav bar (camera reference) moved 0 px (NCC 1.00). **Background/tile displacement ratio = 0.25, 0.26, 0.26 → 0.25 ± 0.03, same direction as the tiles.**
- So yes, full-screen-picture mode has vertical parallax in the 14342-era build, background moving at ≈¼ of the tile scroll. Visual pair: `$R3/frames/sheets/DmK_parallax_pair.png`.
- Confidence: **MEDIUM** (single camera source; the phone was steady relative to the camera for the tracked frames). Not verified on 15063.

### A7. Live tile flip / peek / cycle durations (R1 §2)

Governing values from S2 (15063, 60 fps):

| Animation | Measured | Frames | Source timestamps / evidence |
|---|---|---|---|
| **Flip** (front ↔ back; vertical squash about the tile's horizontal centre line, symmetric, no visible perspective) | **108 ± 17 ms** | 6–7 frames: 6 (143.533–143.617), 6–7 (444.65–444.75), 7 (520.78–520.90), Store 7 (465.93–466.05) | `$R3/frames/sheets/E6v_cortana_flip_60fps.png`, `E6v_cortA_60fps.png`, `E6v_cortC_60fps.png`, `E6v_store_60fps.png` |
| **Peek / slide** (text panel retracting downward to reveal the image; Store tile) | panel travels 97 tile-px: 50 % of travel at **250 ms**, 90 % at **550 ms**, fully gone at ≈0.92 s — strong ease-out | 15 / 33 / ~55 frames from 460.383 | per-frame panel top: 313→314 (460.383)→318 (460.467)→322→328→336 (460.583)→342→354 (460.633)→364→372 (460.717)→380→388 (460.833)→394→398 (460.983)→402→406 (461.217); `$R3/frames/sheets/E6v_store_460.3.png` |
| **Cycle** (image → image crossfade, Gameloft Hub tile) | **367 ± 17 ms** | 22 frames (462.37–462.75) | `$R3/frames/sheets/E6v_gameloft_60fps.png` |

Cross-checks from other builds:
- S4 (14342, camera 60 fps): Store flip 8 frames = 133 ms; Store text-panel slide-up visibly complete in 18 frames = 300 ms (the slow tail is not resolvable on camera); Xbox tile scroll-cycle (old content up and out, new in from below) 12 frames = 200 ms. `$R3/frames/sheets/neo_xbox_10.3_60fps.png`, `neo_xbox_12.4_60fps.png`.
- S3 (unknown build, 30 fps, blended frames): News panel slide-up 20 frames = 667 ms (ease-out; 90 % at ≈12 frames), slide-down 25 frames = 833 ms, transit-tile flip 5 frames = 167 ms, Weather two-phase cycle ≈15 frames = 500 ms (bottom block slides in ≈4 frames, then the whole content scrolls up ≈11 frames). `$R3/frames/sheets/nTS_news_29.3.png`, `nTS_news_33.9.png`, `nTS_flip_zoom2.png`, `nTS_weather_zoom.png`. The slide durations agree with S2's ≈0.9 s total once the ease-out tail is included.
- Confidence: flip **HIGH** (4 instances in the governing build, S4 within 2 frames); peek MEDIUM (one governing instance, curve fully sampled); cycle MEDIUM.

### A8. Stagger / randomisation rule (R1 §2)

Per-tile event times from `tilediff.py` on S2's Start segments: Cortana tile flips at 444.60, 458.07, 462.98, 467.73, 472.67, 520.68, 525.58, 530.97, 535.85 s → intervals 4.91, 4.75, 4.94, 4.90, 5.39, 4.88 → **period 4.96 ± 0.22 s**; Gameloft Hub crossfades at 457.87, 462.08, 466.32, 471.28, 475.35 → **4.4 ± 0.4 s**; Store tile events irregular (2.4–5.5 s, mixing flips and image swaps). The tiles are not synchronised with each other (different periods and phases). S5 People tile: 7.7 ± 0.2 s (A9). No evidence of a global choreography ("flip one by one") in any capture — each tile runs its own timer.
- Confidence: MEDIUM (one governing recording, 9 + 5 intervals).

### A9. People tile animation (R1 §2)

- With contact photos (S5, 14393, camera 24 fps): a photo bubble slides out to the left (≈8 frames = 333 ms), ≈6-frame pause, a new bubble slides in from the right and settles (≈14 frames = 583 ms); the whole event lasts 45 ± 1 frames = **1.88 s** and repeats every **7.7 ± 0.2 s** (runs at 25.46, 33.25, 40.83, 48.50, 86.58, 94.25, 101.71, 109.38 s). `$R3/frames/sheets/ian_people_33.1.png`.
- Without contact photos (S1, S2, S4): the tile shows the static circle pattern; S1 shows only a 250-ms sub-threshold flicker at 20.48 s.
- Confidence: MEDIUM/LOW (camera, 24 fps, one source).

### A10. Pointer-down press amount on Start tiles (R1 §3.1)

- S1 Groove tile tap (touch dot visible 13223–13225 = 220.383–220.417 s): tile region mean abs difference vs the pre-touch frame = **0.00** (no change of any kind for the tile, its neighbours, or the row). `$R3/frames/launch/groove_023.png` vs `groove_025.png`.
- S2 Edge tile tap (press held ≥7 frames, 151.15–151.27 s): tile mean RGB unchanged (29.7,70.6,150.7 vs 29.6,70.3,150.6); best-fit content shift **≤0.5 video px = ≤2.4 phys = ≤0.7 epx**, no scale change. `$R3/frames/sheets/E6v_edge_tile_press_zoom.png`. S2 Cortana tile tap: same (mean diff ≤2.4 with no colour change).
- Result: **no visible tilt, depression, scale or highlight on Start tiles at pointer-down.** Confidence **HIGH** (two recordings).

### A11. W10M shell Start → app launch animation (R1 §3.2)

Measured on three taps (S1 Groove 220.6 s; S2 Edge 151.2 s; S2 Cortana 477.2 s) and two returns (S1 Wallet→Start 1179.7 s via Windows key; S1 Settings→Start 566.9 s).

Start exit (on tap):
- No rotation. The whole grid **scales up about the screen centre while fading out**, rows leaving **top-to-bottom** with a stagger, and the tapped tile fading last.
- Scale per 16.7-ms frame (gradient scale fit, screen centre wins over tap point / tile centre in every case): S2 Edge: 1.00, 1.04, 1.04(dup), 1.08, 1.12, 1.20, 1.24, 1.32, 1.32(dup), 1.44, 1.48 (151.317→151.483); S2 Cortana: 1.04, 1.04, 1.08, 1.12, 1.16, 1.24, 1.32, 1.44, 1.56 (477.267→477.400); S1 Groove: 1.16, 1.24, 1.32, 1.32(dup), 1.40, 1.56 (13242→13247) then too faded to fit. So ≈ +0.04→+0.12 per frame, accelerating (ease-in), reaching ≈1.5× when opacity hits 0.
- Row fade start times (row band luminance): S2 Cortana tap: row1 477.183, row2 477.233, row5 477.233, row3 477.283, row4 477.317, all black by 477.400; S2 Edge tap: r1 151.233, r5 151.300, r2 151.333, r3 151.383, r4 151.417, black by 151.483; S1 Groove: r1 220.633, r2 220.667, r3/r4 220.767, r5 220.800, black 220.900. Stagger ≈30 ms per row; each row's fade ≈130–170 ms.
- Whole exit, first visible change → black: **217–267 ms** (13–16 frames) in all three cases. The tapped tile stays ≈4 frames (67 ms) after its row (S1 13248–13253; S2 frames 20–23 of `E6v_start_exit_60fps.png`).
- Evidence: `$R3/frames/sheets/groove_launch_zoom.png`, `E6v_edge_launch2_60fps.png`, `E6v_start_exit_60fps.png`; per-frame tables from `rowstagger.py` and `scalefit2.py`.

App entrance:
- Black gap while the app starts (not animation): S1 Groove 59 frames (983 ms); S2 Edge 9 frames (150 ms) to the splash.
- S1 Groove page: **scale 0.916 → 1.00 about the screen centre + fade-in over 10 frames = 167 ± 17 ms** (13314→13324; header bbox 283→309 px wide, luminance 6.9→11.7). `$R3/frames/sheets/groove_entrance_zoom.png`.
- S2 Edge splash: background appears at full colour instantly (151.633); the logo grows ≈0.81→1.0 (white pixel count 611→939) over 13 frames = **217 ms**. `$R3/frames/sheets/E6v_edge_launch2_60fps.png` frames 38–53.

App → Start (Windows key) and Start entrance:
- S1 Wallet exit: scale 1.06 (1179.733) → 1.30 → 1.44 → 1.54 → 1.64 → 1.72 (1179.833) about the screen centre while gain 1.0→0.94→0.69→0.49→0.27→0.09→0: **8 frames = 133 ms**. Gap 11 frames (183 ms) black.
- S1 Start entrance: scale **0.78 → 0.98 in 9 frames (150 ms)** (0.78, 0.86, 0.92, 0.94, 0.94, 0.94, 0.96, 0.98, 0.98) with fade-in gain 0.01→0.13→0.35→0.51→0.59→0.73→0.88→0.92→0.95→0.99 completing in **13 frames = 217 ms** (70803→70815, 1180.05→1180.25). `$R3/frames/sheets/return_60fps.png`; a second instance at 566.9 s (`I98_566-568.5_10fps.png`) shows the same scale-in.
- S2's only captured return (143.28 s) jumps in one frame (capture dropped frames or an edit) and is not used.
- Confidence: **HIGH** for the exit form and duration (three taps, two builds agree), MEDIUM for the exact per-frame scale (fit correlation 0.5–0.6 while fading), MEDIUM for the entrance/return (S1 only).

### A12. "Standard" easing curve values (R1 §3.3)

Not a documented number, but the measured curves above are the data: exit scale samples (A11) rise ≈0.04/frame at the start and ≈0.12/frame at the end (accelerating), opacity falls mostly in the last 7 frames; entrance scale samples decelerate (0.78→0.92 in 2 frames, then 0.92→0.98 over 6) with a roughly linear fade. Use A11's per-frame tables directly; no named curve is claimed. UNMEASURED as a formula.

### A13. "Recently added" group in the app list (R1 §4)

- **None** in either build. S1 (14393) t=573.5 s, `$R3/frames/applist/I98_applist_t573.5.png`: search box, then header "A" immediately. S2 (15063) t=76 s, `$R3/frames/e6v/E6v_t76.png`: same, and a newly installed app ("Audible") carries an accent-coloured "New" caption (≈12 epx) under its name instead of a group.
- Confidence: **HIGH** (two recordings, both builds).

### A14. App list search box (R1 §4)

- Height **32 ± 1 epx** (S1 30 px = 127 phys = 32 epx; S2 24 px = 115 phys = 33 epx); top edge directly under the status bar (S1 top at 113 phys = 28.3 epx from the screen top); width = screen minus **4–6 epx** side margins (S1 x 475–803: 16/19 phys; S2 x 493–784: 18/22 phys). Placeholder text "Search" left-aligned, magnifier glyph right-aligned inside the box; 1-px outline on the wallpaper-showing-through background.
- Confidence: HIGH.

### A15. Per-style line heights (R1 §5.1)

- Body text (15 epx) line pitch in Settings paragraphs: S2 15 px = 72 phys = 20.6 epx; S1 18.5 px = 79 phys = 19.8 epx → **20 ± 1 epx**, matching R1's "125 % rounded to 4" rule. Sources: S2 t=250 s (`$R3/frames/e6v/E6v_t250.png` rows 280–291/295–305); S1 t=100 s (`$R3/frames/settings/I98_notif_t100.png` rows 191–197/209–215/228–234).
- Two-line list item (title + subtitle) pitch: **64 ± 1 epx** (S1 Settings home 59.7 px = 256 phys). Tile text lines (12-epx caption) in the Outlook Mail tile: 15 px = 64 phys = **16 epx** pitch (S1). Other styles: UNMEASURED.
- Confidence: MEDIUM/HIGH.

### A16. Which accent palette the W10M Colors page exposes (R1 §6.1)

- The **48-colour Windows 10 palette, 6 columns × 8 rows, in exactly R1's row/column order.** S8 (2015 build, screen recording, `$R3/frames/nr9z/Nr9Z_t84.png`): 42 visible swatches sampled and compared with R1's hex table: mean absolute channel error 7.5/255, worst swatch 32 (video colour error), no ordering mismatch. S6 (≈14342, camera, `$R3/frames/dmk/DmK_t120.png`, t=120 s and 180 s) shows the same 6×8 grid with "Choose your mode: Light/Dark" radios above it. Neither S1 nor S2 opens the Colors page, so the 15063 page is not directly verified.
- Swatch geometry (S8, 360-epx assumption): 42 px = **44 ± 1 epx** square, horizontal gap 4 epx, vertical gap 7–8 epx, left edge at 12 epx.
- Confidence: **MEDIUM** (two sources agree on the palette; governing build not filmed).

### A17. Accent shade formula (R1 §6.1)

UNMEASURED. No footage shows the light/dark accent variants in a measurable way; the capture colour error (A16) is larger than shade differences.

### A18. WP8 PhoneBackgroundColor / PhoneForegroundColor hex (R1 §6.2)

WP8 value not measurable from W10M footage. Measured W10M dark-theme equivalents (capture rendition): page background (0,0,0) in Settings, Action Center and the volume host (S1/S2); app-list background shows the Start wallpaper through it (samples (0,10,23)); nav bar (0,0,0) with light glyphs. Text (255,255,255)-class. Confidence: MEDIUM for "pure black background".

### A19. Action Center expanded grid geometry, tile size, spacing, Clear all (R1 §7)

S1 (14393; 400 %), frames `$R3/frames/ac/I98_ac_exp_t72.png` (expanded, t=72.0 s) and `$R3/frames/settings/I98_t452.png` (collapsed, t=452 s; note this file is the collapsed AC, not Settings):

| Item | Measured | epx (±1.1) |
|---|---|---|
| Quick-action tile | 79.3 × 56.5 px | **85 × 60.5** |
| Column pitch / row pitch | 83.0 / 59.7 px | **89 / 64** (gaps 4 h, 3.4 v) |
| Columns × rows | 4 × 4 expanded (16), 4 × 1 collapsed (4) | — |
| Grid side margins | 3.65 / 4.2 px | 4 / 4.5 |
| Grid top | expanded frame 59.4 px; collapsed frame 71.4 px | 64 / 77 (the two states differ by 13 epx; both frames are at rest) |
| Status area inside AC | two lines: time/battery icon row 47–58 px and "46 % 7/28" row 71–79 px | second line ends at 38 epx |
| Tile icon | ≈12-px glyph at ≈11 px from the tile's left/top | ≈13 epx glyph, ≈12 epx inset |
| Tile label | 12-epx caption at the bottom-left, inset ≈5 epx, baseline ≈8 epx above the bottom | |
| "Clear all" (left) / "Collapse" (right) | text row 360–368 px; left x=482, right edge 795 | cap 9.6 epx; row at 339–348 epx; 12-epx side margins; 24 epx below the grid |
| Notification group header | app icon 20 px + name, rows 394–413 px | icon 21 epx; row 376–396 epx |
| Notification item | 48-px circular avatar, title/preview/time lines | avatar 51 epx |
| Drag handle | full-width accent bar 12 px tall with "=" grip at the AC's bottom edge | 13 epx |
| Colours (capture) | tile (48,48,48); active tile accent (48,104,255); background (0,0,0) | |

- Confidence: **MEDIUM** (one clean 14393 recording; S2 never opens the Action Center, so 15063 is not verified; quick actions did not change between AU and CU per the ruling's cumulative-update note, but that is unverified here).

### A20. Volume panel height and colours (R1 §8.1)

S1 (14393) t=228.5 s, `$R3/frames/volume/I98_vol_t228.5.png` (two-slider state, media playing):
- Panel: full width, from the screen top to **274 ± 1 epx** (255.4 px); background (55,55,55) capture rendition; the app behind stays black.
- Row 1 "Ringer + Notifications": title cap 10 px = 10.7 epx (15-epx body) at 12–26 epx from the top, x = 12 epx; bell icon 29 epx wide at x 12–39 epx; slider track at y = **48.7 epx**, x 67–293 epx (length 226 epx), 2 epx thick, (103,103,103) unfilled / accent filled; thumb **6.4 × 20.4 epx** (6 × 19 px) accent; value "01" digits 21 epx tall right-aligned at 347 epx; value 1/10 → thumb at 10.7 % of the track (measured 10.7 %).
- Row 2 "Media + Apps": identical geometry **100 epx lower** (track at 149 epx); value 15/30 → thumb at 49.5 %.
- Footer: "Vibrate on" caption (cap 8.6 epx) at 217–225 epx, x 12 epx; chevron at x ≈ 340 epx; panel bottom 274 epx.
- Confidence: MEDIUM (single recording; S3 t=30 s shows the same single-slider layout on a 480-epx canvas: `$R3/frames/volume/nTS_vol_t30.png`).

### A21. Glance clock position / font size (R1 §8.2)

S7 (≈14342, camera, phone lying flat, camera oblique; screen top out of frame): `$R3/frames/glance/gl_t18.png`.
- Clock digits "2:36" 75–79 px tall on a ≈600-px-wide screen image → **45–47 ± 3 epx digit height** (360-epx canvas assumption); Segoe UI Light digit height ≈0.7 em → font ≈65 epx. Date "Wednesday, May 4" cap ≈26 px → ≈16 epx cap (font ≈22 epx), one line under the clock; a small notification glyph (≈12 px) under the date. Clock left edge ≈31 epx from the screen's left edge.
- Vertical position: **UNMEASURED** (screen top not in frame) and the content shifts position over time (t=44 s shows the clock near the lower-left, `gl_t44.png`).
- Confidence: **LOW**.

### A22. Cortana ring radii, stroke, frame timings (R1 §9)

Geometry (blue-pixel radial profiles):

| Ring | Source | Outer Ø | Inner Ø | Stroke | Centre y from screen top |
|---|---|---|---|---|---|
| Idle/thinking ring on the Cortana page (governing) | S2 15063, t=479.47 s, `$R3/frames/e6v_cortana/c_071.png` | 51 px = 245 phys = **70 epx** (350 %) | 35 px = 168 phys = 48 epx | 8 px = 38 phys = **11 epx**, two-tone (inner darker (28,44,107)-class, outer bright (60–68,97–103,209)-class) | 244 epx (33 % of 731) |
| Thinking ring | S1 14393, t=833.0–834.7 s, `$R3/frames/cortana60/b_045.png` | 74 px = 317 phys = **79 epx** (400 %) | 46 px = 197 phys = 49 epx | 14 px = 60 phys = 15 epx; inner dark band ≈10 epx (35,53,116) + outer bright band ≈5 epx (46,105,255) | 242 epx (38 % of 640) |
| Listening ring (pulsing) | S1 14393, t=832.4–833.0 s | 82 → 88 → 82 px = 88–94 epx | — | — | 242 epx |
| Small ring at the top of the Cortana home page | S1 14393, t=828.1 s, `$R3/frames/cortana60/a_020.png` | 49 px = 210 phys = 52.5 epx | 24 px = 26 epx | 12.5 px = 13 epx | 43 epx |

Timings (60 fps):
- S2 (15063) ring pop-in on opening Cortana: a dot appears at 478.483, grows to the full ring width by 478.667 (11 frames = 183 ms), filled disc → ring by 479.13 (**≈650 ms** from first pixel to the settled ring); ring steady 479.13–479.6; then it **rotates about its vertical axis** (edge-on at 479.97, 22 frames from 479.6 → **367 ms** to edge-on), then **moves up to the top of the page in 13 frames = 217 ms** (480.02–480.23) as the results card appears (480.38). `$R3/frames/sheets/E6v_cortana_ring_zoom.png`, `E6v_cortana_10fps.png`.
- S1 (14393) listening: outer diameter pulses 82→88→82 px over 36 frames = **600 ms** (one cycle captured), then switches to the smaller thinking ring at 833.0. Thinking: apparent width 74→38→74 px (Y-axis rotation to ≈60°) over 22 frames = **367 ms**, repeating every 55 frames = **917 ms** (minima at 833.383 and 834.30). `$R3/frames/sheets/I98_cortana_805-865_03.png`, per-frame table from the session.
- Note: S1 shows the ring (not the 14356 waveform) in the listening state with the "play songs by Drake." hint; the waveform did not appear in these captures.
- Confidence: MEDIUM (one recording per build; geometry ±1 px = ±1.1–1.4 epx).

### A23. Segoe MDL2 EULA text (R1 §10)

UNMEASURED — not a footage item.

### A24. Fluent System Icons vs MDL2 lineage (R1 §10)

UNMEASURED — not a footage item.

### A25. Large (2×2 medium) tile on W10M (R1 §1.1, partial)

Not observed on any Start in S1–S9 (small/medium/wide only). Consistent with R1's "three sizes".

---

## B. Legacy values checked against W10M

| R1 value (WP7/8 toolkit) | W10M measurement | Verdict |
|---|---|---|
| Tilt `MaxAngle` 0.3 rad (17.2°) | Start tiles: 0.00 change during a press (S1), ≤0.7 epx content shift during a ≥117-ms press (S2); no rotation of any edge | **DIFFERS** — W10M Start tiles do not tilt (0° ± measurement floor 0.5 px). |
| Tilt `MaxDepression` 25 px | same frames: no scale/translation beyond ≤0.5 video px (≤2.4 phys) | **DIFFERS** — depression 0 (≤2.4 phys px). |
| `TiltReturnAnimationDelay` 200 ms + `Duration` 100 ms | no press state exists to return from; first visible reaction after touch-up is the launch exit itself, 250–300 ms after touch-down in S1 (13223 → 13241), ≈80 ms in S2 (151.15 → 151.233) | **DIFFERS** — no return animation; launch latency is not a fixed 200/100 ms. |
| TurnstileForwardOut 0°→50°, 250 ms, ExponentialEase 6 | Start exit on launch: no rotation; uniform scale-up 1.0→≈1.5 about the screen centre + fade, rows staggered top-to-bottom ≈30 ms/row, total 217–267 ms (A11) | **DIFFERS** (no turnstile; duration coincidentally similar). |
| TurnstileForwardIn −80°→0°, 350 ms | App entrance: scale 0.92→1.0 + fade about the screen centre, 167 ms (S1); splash logo 0.81→1.0 in 217 ms (S2) | **DIFFERS**. |
| TurnstileBackwardOut 0°→−80°, 250 ms | App exit on Windows key: scale 1.0→1.72 + fade, 133 ms (S1) | **DIFFERS**. |
| TurnstileBackwardIn 50°→0°, 350 ms | Start entrance: scale 0.78→1.0 in 150 ms + fade completing in 217 ms (S1) | **DIFFERS**. |
| In-app page-to-page turnstile (Settings sub-pages etc.) | not measured in this pass | UNMEASURED. |

Applies to builds 14393 and 15063 (S1/S2 agree on every form above).

---

## C. Phase 1 addendum

All epx values are on the source's own canvas (S1 400 % → 360 wide; S2 350 % → 411 wide); phys = physical px on the 1440-wide panel.

### C1. Settings page geometry

| Item | Measured | Source / evidence |
|---|---|---|
| Page header block (status bar bottom → content top) | S1 53 px = 227 phys = **57 epx**; S2 41 px = 197 phys = 56 epx → 57 ± 2 epx | S1 t=452 s is the AC; Settings home is S1 t=452's sibling frame `$R3/frames/settings/I98_t452.png` (verified visually as "Settings" home, t=452 s); S2 t=342 s `$R3/frames/e6v/E6v_t342.png` |
| Header title | gear glyph 20–21 px (S1) / 17 px (S2) = **21–23 epx** at x = 12 epx; title cap 10–11 px (S1) = **11 ± 1 epx** cap (15-epx SemiBold class), vertical centre ≈49 epx from the screen top | same |
| "Find a setting" box (Settings home) | 29 px = 124 phys = **31 epx** tall, x 12-epx margins both sides, magnifier at the right | S1 t=452 |
| List item, two-line (icon + title + subtitle) | pitch 59.7 px = 256 phys = **64 ± 1 epx**; icon 29 × 27 px ≈ **30 epx** glyph at x = 12 epx; text at x = **55 epx**; title 15-epx class, subtitle 12-epx class, title→subtitle pitch 17 px = 18 epx | S1 t=452 rows 168/226/286/347/406/466/526 |
| Toggle switch | S1 39.5 × 17 px = 42 × 18 epx; S2 32 × 14.5 px = 44 × 20 epx → **44 × 20 ± 2 epx**; thumb ≈7–8 epx; state label ("On"/"Off") starts 56 epx right of the toggle's left edge; On = accent fill with white thumb, Off = 1-px outline with white thumb | S1 t=100 `$R3/frames/settings/I98_notif_t100.png`; S2 t=250 `$R3/frames/e6v/E6v_t250.png` |
| Slider | only the volume slider was captured at rest: track 2 epx thick, thumb 6.4 × 20.4 epx (A20). Start-settings "Tile transparency" slider seen only in S6 camera / S8 2015 (thumb ≈5 × 17 epx in S8): LOW | A20; `$R3/frames/nr9z/Nr9Z_t76.png` |
| Body paragraph line pitch | 20 ± 1 epx (A15) | |
| Section sub-header ("Quick actions", "Notifications") | cap 15 px in S1 = **16 epx** cap (Title 24 class) | S1 t=100 rows 146–160 |

Confidence: MEDIUM/HIGH (two recordings for header, list pitch, toggle; one for the search box).

### C2. App list rows, letter headers, jump grid

| Item | Measured | Source |
|---|---|---|
| Row pitch | S1 41 px = 176 phys = 44 epx; S2 32 px = 154 phys = 44 epx → **44 ± 1 epx** | `$R3/frames/sheets/applist_side_by_side.png` (both at equal physical scale) |
| App icon | S1 38 px = 163 phys = 40.7 epx; S2 30 px = 144 phys = 41.3 epx → **41 ± 1 epx** square, left edge at 4–6 epx | same |
| App name | text starts at x = 55 (S1) / 57 (S2) epx; 15-epx class | same |
| "New" sub-label | accent colour, 12-epx class, directly under the name (S2 only, newly installed app) | S2 t=76 |
| Letter header glyph | cap 17 px (S1) = 18 epx / 11 px (S2) = 15 epx → **15–18 epx cap** (Title 24 SemiLight fits both within ±1 px); left edge x = 4–6 epx (same as icons) | same |
| Letter-header group gap (previous icon bottom → next icon top) | S1 49 px = 52.5 epx; S2 39 px = 53.6 epx → **53 ± 1.5 epx** | same |
| Search box | A14 | |
| Jump grid (tap a header) | **LOW, build 10586 (S9, 640x360):** 5 columns; cell pitch ≈51 epx horizontal / ≈48 epx vertical (≈50 ± 4); letters ≈19 ± 3 epx cap, available letters bright, unavailable dimmed, "#" cell first; grid covers the list area below the search box | `$R3/frames/sheets/lWI_31-36.png` (t=31–32 s) |

No 14393/15063 footage of the jump grid was found (S1, S2, S4, S5 never tap a header).

### C3. Live tile content layouts

Measured text/element bands (luminance > 250) inside the tile; positions are from the tile's own top-left corner.

| Tile | Layout (measured) | Source / evidence |
|---|---|---|
| **Calendar** (medium, "Thursday 28", branding none) | day name: 12-epx-class text? no — cap 10 px = 10.7 epx (15-epx body) centred horizontally (centre x 638.5 vs tile centre 639.4), top at 31 px = 33 epx below the tile top; day number: 28-px-tall digits = **30 epx digit height** (≈43-epx Light font), centred, top at 47 px = 50 epx; no icon, no label | S1 t=12, `$R3/frames/start/I98_start_t12.png`, bands "CalTile" |
| **Mail / notification** (medium, "Outlook Mail") | four caption-class text lines (sender name 2 lines, subject 2 lines) at the top-left, first line top at 6 px = 6 epx below the tile top, x inset 7 px = 7.5 epx, line pitch 15 px = **16 epx**; label "Outlook Mail" at the bottom-left (A2 insets); no badge visible in this frame (S6 camera shows a numeric badge at the bottom-right of the same tile type: LOW) | S1 t=12, bands "MailTile" (rows 75–83, 90–96, 101–109, 115–124, 160–169) |
| **Cortana back / news headline** (medium back face after a flip) | 2-line headline at the top-left (rows 13–34 of the 98-px tile = 18–47 epx, 350 %), source line "NBC News - 5 minutes ago" bottom-right and label "Cortana" bottom-left on the same baseline row (rows 79–87 = 109–120 epx), text left inset 8 px = 11 epx | S2 t=143.9 s, `$R3/frames/e6v_flip/cort_030.png`, `$R3/frames/sheets/E6v_cortana_flip_60fps.png` |
| **Money / News** (medium, photo with text overlay) | Money: 4 lines of white 12-epx-class text over a darkened photo starting 6 px below the top, line pitch ≈14.5 px = 15.5 epx; label at the bottom-left. News: photo full-bleed, label only | S1 t=12, bands "MoneyTile", "NewsTile" |
| **Weather** (S3, medium, 480-epx canvas, 1 px = 1.21 epx; **LOW** build unknown) | 3-day face: day-name row cap 8 px = 10 epx at 8 px from the top; icon+temperature row 23 px = 28 epx tall at 28–50 px; three columns at pitch ≈62 px = 75 epx; location label bottom-left. Current-conditions face: condition word top-left, big temperature digits 25 px = **30 epx** at 33–57 px from the top, hi/lo + precip + wind lines to the right, location label bottom-left. Faces alternate with the two-phase cycle in A7 | S3 t=30.0 / 33.5 s, `$R3/frames/volume/nTS_t30.0.png`, `nTS_t33.5.png`, `$R3/frames/sheets/nTS_weather_zoom.png` |
| **Photos** (S3, wide) | full-bleed photo, app icon + label at the bottom-left; no text overlay | S3 t=30.0, band "Photos" (whole tile bright) |
| **Music now playing** | UNMEASURED — every Groove tile in S1–S9 shows the static logo (no now-playing face was captured) | |
| **Messaging small tile** | 28-epx glyph centred (rows 141–167 of a 51-px tile); badge not present in the frame | S1 t=12, band "MsgSmall" |
| **People** | circle pattern; photo bubbles as in A9 | S1/S2/S5 |

Confidence: MEDIUM for Calendar/Mail/Cortana-back (screen recordings), LOW for Weather/Photos (S3).

### C4. Start-screen chrome observed alongside

- Status bar: 28 epx (S1 112 phys) / 29 epx (S2 103 phys); in the Action Center a second status line (battery % and date) appears under it (A19).
- Start background colour with a full-screen picture: the picture; gutters show the picture, not black.

### C5. MSN Weather app (owner ruling: modelled on the final-release MSN Weather)

Sources: **S10** (2015 build, clean Project My Screen recording — the only screen recording of the app found; 344 px = 360 epx assumed, 1 px = 1.047 epx, ±1 px = ±1 epx, 30 fps → ±33 ms) and **S11** (15063-era, steep-angle camera, structure only). Searched: "MSN Weather Windows 10 Mobile", "Windows 10 Mobile weather app hands on Lumia 950", "MSN Weather Windows Phone 10 update review", "Windows 10 Mobile MSN Weather 2017" (yt-dlp, 15–20 results each). No screen recording of the 14393/15063 app exists in those results, so every number below is from the 2015 build and is **LOW as a governing-build value**; the page structure is confirmed on 15063 by S11 where noted.

Structure confirmed on 15063 (S11 t=46 s, `$R3/frames/weather/dado_t46.png`): header bar (hamburger + page title + search), centred location line, condition icon + large temperature + C/F stack, condition text, "Updated at hh:mm" line, two lines of feels-like / wind / barometer / humidity, a "Daily" section header, a horizontal 3-cell day strip with the selected cell tinted and a caret under it. Differences from 2015: the header bar is dark (2015: white), the temperature has a condition icon to its left, the updated-at and barometer/humidity lines and the "Daily" header are new.

Forecast page (S10 t=30 s, 36 s, 45 s, 55 s, 63 s, 74.3 s; `$R3/frames/weather/Nz_t30.png`, `Nz_t36.png`, `Nz_t45.png`, `Nz_t55.png`, `Nz_t63.png`, `Nz_t74.3.png`; 1-fps map `$R3/frames/sheets/Nz_24-150_1fps.png`, labels are seconds after 24 s):

| Element | Measured (S10, 2015) | epx (360 canvas) |
|---|---|---|
| Status bar | not shown while the app is open (header bar at the top of the screen) | — |
| Header bar | y 51–89 = 39 px tall, full width; 2015 colour white (255,254,255); hamburger button 45 px wide, full bar height, accent fill; title "Forecast" SemiBold, cap 12 px, starts at x = 57 px; search glyph right inset 24 px | **41** tall; button **47** wide; title cap 12.6 (≈18-epx), at x = 60; glyph inset 25 |
| Location line | cap 17 px, centred, top at 70 px from the screen top (32 epx below the bar) | cap **18** (≈26-epx Light/Regular), top 73 |
| Temperature | digit height 58 px, centred block; "°" then a C/F stack to the right: "C" cap 15 px (selected), "F" dimmed below | digit height **61** (≈87-epx Light), top 113; C cap 16 (≈22-epx) |
| Condition text | cap 13 px, centred | cap **14** (≈20-epx Subtitle), top 197 |
| Feels like / Wind line | cap 9.5 px, centred | cap 10 (≈14–15-epx Body), top 238 |
| Day strip | top at 307 px; 3 visible cells each W/3 = 114 px wide × 141 px tall; selected cell = page colour + ≈20 % white ((58,64,92) over (16,32,57)) with an 8-px caret under it; cell content: day name cap 10.5 px at 12 px from the cell's left and top, condition icon 21 px at 34 px, hi temp digits 15 px + dimmed lo temp ≈12 px at 67 px, condition caption cap 8 px at 95 px; 10 days available, strip scrolls one cell per tap | top **273**; cell **119 × 148**; day name cap 11 (≈15-epx) at 13/12; icon 22; hi temp 16 (≈22-epx), lo ≈12; caption 12-epx class |
| "Hourly" section header | cap 12 px at x 12 px; two 36-px square toggle buttons (chart / list) right-aligned with 18 px right inset, active one tinted (58,64,92); 1-px rule (37,37,37) under the header | cap 12.6 (≈18–20-epx); buttons **38 × 38**; rule 1 |
| Hourly chart | 174 px tall below the rule: filled temperature curve, peak/trough labels (12-epx class), hour labels (8 am / 2 pm / 8 pm / 2 am) and condition icons along the bottom | **182** tall |
| Hourly list mode | 3-column grid of hour cells (W/3 each): icon 21 px, temp cap 17 px, condition caption, precipitation % row with drop glyph, wind row with arrow glyph, hour label at the bottom; cell ≥165 px tall | icon 22; temp cap 18 (≈25-epx); cell ≥173 |
| "Day Details" section | header as "Hourly"; "Day" / "Night" paragraphs (12–15-epx class); Sunrise/Sunset/Moonrise/Moonset rows: label cap 10 px + 20-px glyph + value digits 22 px, row pitch 84 px, 1-px separators; Moon Phase row of five 20-px glyphs; gauges two per row: outer Ø 90 px, arc stroke ≈4 px accent on a dark track, value cap ≈16 px centred, label above, column pitch 160 px; Record Rain and Temperature (thermometer) blocks below | label cap 10.5 (≈15-epx); value cap 23 (≈33-epx Light); row pitch **88**; gauge Ø **94**, stroke 4, value cap 17 (≈24-epx); gauge pitch 167 |
| Command bar | bottom, 44 px tall, darker translucent (14,26,48); ★ favourite, pin, "…" glyphs 17–19 px right-aligned at ≈60-px pitch | **46** tall; glyphs 18–20; pitch ≈62 |
| Page background | 2015: navy gradient (5,18,37) top → (16,32,57) with a faint cloud texture; 15063 (S11): full-bleed night-sky photograph behind the current-conditions block, navy elsewhere. Whether the photo is keyed to condition/time of day: UNMEASURED | — |

Navigation (S10 t=97–101 s; `$R3/frames/sheets/Nz_menu_97_30fps.png`, `Nz_nav_98.6_30fps.png`, `Nz_maps_99.5_30fps.png`; `$R3/frames/weather/Nz_t98.3.png`):
- **Hamburger pane, not pivots.** The Forecast page is one vertically scrolling page (header block → day strip → Hourly → Day Details) with the header bar and command bar fixed; no pivot headers exist.
- Pane geometry: overlays the content area only (below the header bar, above the command bar), width 242 px = **253 epx** (70 % of the width), colour (40,40,40); items are **48-epx rows** (pitch 45.7 px): Forecast, Maps, Historical Weather, Places, Send Feedback, with 16-px (17 epx) glyphs at 17 epx from the left and text at x = 60 epx; the current page's row is tinted (16,68,110); a hairline separator then an account row (24-px = 25-epx avatar, e-mail caption, "Sign out") and a "Settings" row at the bottom.
- Pane open: slides in from the left in **4 frames = 133 ± 33 ms** (97.53–97.67 s). Item press: the row lightens while pressed (10 frames observed, press-held). On release the new page's background appears within 1 frame and the pane slides out in **4 frames = 133 ± 33 ms** (99.40–99.53 s); no other cross-page animation is visible at 30 fps. The new page then loads asynchronously (Maps: title changes 0.5 s later, progress ring 1.3 s — network, not animation).
- Day-strip tap (S10 t=55.67 s, `Nz_daytap_55_30fps.png`): a 1-px light outline appears on the tapped cell within 1 frame and stays while pressed; on release the strip scrolls one cell (119 epx) in **≈5 frames = 167 ± 33 ms** so the tapped day sits in the selected slot, then the Hourly/Day Details content below updates.
- Hourly chart ↔ list toggle (S10 t=73.5 s, `Nz_menu_73_30fps.png` frames 25–29): crossfade **5 frames = 167 ± 33 ms**.

Other pages (S10; light theme): Maps (`Nz_t112.8.png`, t=112.8 s): light header "Maps", layer dropdown ("Obserwacja radarowa"), full-bleed radar map, bottom legend + play/pause + timeline slider. Historical Weather (`Nz_t140.png`): location title cap 17 px (18 epx), month cards on a light grey page at 141-px (148-epx) pitch, each with two rows × three stat columns (Average High / Record High / Average Rainfall; Average Low / Record Low), blue month headings. Places (t=149 s): favourite-place cards with large temperatures and a "+" add button.

Weather live tile: see C3 (nTS 3-day and current faces; S10 t=24 s shows the 2015 medium tile: "Sunny" caption top-left, large temperature 31° with hi/lo to the right, location label bottom-left, `$R3/frames/weather/Nz_t24.png`).

Confidence: MEDIUM for the 2015 measurements as such; **LOW as final-release values** (structure confirmed on 15063 by S11; header colour differs; no 14393/15063 screen recording found).

---

## D. Scripts and frame index

- `$R3/scripts/edges.py`, `edges_ch.py` — sub-pixel edge crossings on rows/columns.
- `$R3/scripts/bands.py` — bright/dark row and column bands inside named boxes.
- `$R3/scripts/tilediff.py` — per-region mean-abs frame difference runs (live-tile event finder).
- `$R3/scripts/launch_track.py`, `rowstagger.py`, `scalefit.py`, `scalefit2.py`, `ret_track.py` — launch/return analysis.
- `$R3/scripts/nts_track.py`, `nts_track2.py` — S3 slide/flip trackers.
- `$R3/scripts/parallax.py` — 1-D strip correlation (superseded by the 2-D template tracking run inline for S6).
- Key evidence frames: `$R3/frames/start/` (Start grids), `frames/launch/` (S1 tap/return 60-fps crops), `frames/e6v_launch/` (S2 taps), `frames/cortana60/` and `frames/e6v_cortana/` (rings), `frames/ac/`, `frames/volume/`, `frames/applist/`, `frames/settings/`, `frames/e6v/`, `frames/nr9z/`, `frames/dmk/`, `frames/dmk_scroll/`, `frames/glance/`, `frames/e6v_flip/`, `frames/sheets/` (all contact sheets cited above).

## E. Status summary of R1's NOT FOUND rows

HIGH: A1 grid (3-column), A2 label size class, A10 press, A11 launch/exit form, A13 no "Recently added", A14 search box, A7 flip.
MEDIUM: A4 transparent-tile rule, A5/A6 parallax (0.25), A7 peek/cycle, A8 stagger, A15 line heights, A16 palette, A18 background, A19 Action Center, A20 volume, A22 Cortana, A11 entrance/return numbers.
LOW: A9 People tile, A21 Glance, C2 jump grid, A1 two-column layout observation, C5 MSN Weather app (2015 recording; structure confirmed on 15063).
UNMEASURED: A3 transparency default, A12 easing formula, A17 shade formula, A23 MDL2 EULA, A24 Fluent lineage, nav-bar height, in-app turnstile, Music now-playing tile, Weather background-image keying.

Counting the 24 R1 NOT FOUND rows (A1–A24): HIGH 7 (A1, A2, A7, A10, A11, A13, A14), MEDIUM 10 (A4, A5, A6, A8, A15, A16, A18, A19, A20, A22), LOW 2 (A9, A21), UNMEASURED 5 (A3, A12 as a formula, A17, A23, A24).
