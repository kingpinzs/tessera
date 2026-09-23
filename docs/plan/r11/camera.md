# R11 §Camera — Windows Camera (Windows 10 Mobile) measurements

Research section of R11 (`docs/plan/r11-inbox-apps.md`) for phase 17 (`docs/plan/phase-17-inbox-photos-camera-video.md`,
Q2 C: photo and video with automatic everything, flash / timer / grid / front-back / zoom, the Lumia pro dial, panorama, slow
motion and Living Images; Q5 A: the capture intents). It feeds phase 17's approximation rows Y3 (viewfinder chrome), Y4 (pro
dial), Y7 (bars) and Y12 (panorama UI). Format follows `docs/plan/r8-groove-measurements.md`; shared values are cited from R3,
R6, R7 and R8.

## Summary — what was and was not established

**Three generations of Windows Camera are visible in the sources, and the viewfinder changed between them.**

- **V-2015** (Windows Camera **2015.1088.21.0**, "About this app" on X4): measured at native resolution on **three phones** —
  Lumia 950 (400 %), 950 XL (350 %) and 650 (200 %). The portrait viewfinder is W10M app-bar grammar: status bar hidden, nav bar
  drawn, the 4:3 preview fitted to width and centred above the nav bar, a **control column 24 epx from the right edge on a
  44-epx pitch** (camera switch, flash, Rich Capture / Living Images, a chevron that expands it to add WB, focus, ISO, shutter
  speed and exposure), and a bottom row with the **shutter glyph at W/2, 44.75 epx above the nav bar**, the other capture mode
  as a small glyph **60 epx** to one side, and "•••" 24 epx from the right. The **pro dial is five concentric arcs centred on
  the nav bar's top edge at W/2, radii 130.5 + 65·k epx** — the same epx on 400 % and 350 %, so fixed. One control alone shows
  one 130-epx arc. The camera roll is a **36-epx round thumbnail in the top-right 48-epx corner cell**.
- **V-2016** (panorama arrived July-Aug 2016; **2016.1018.11.0** in October 2016): Microsoft's changelog lists
  "higher-contrast capture buttons", a photo-timer toggle "right from the camera dashboard", Settings "directly from the camera
  UI", the camera roll moved to "its new spot on the screen" for one-handed use, a zoom slider and a more prominent front /
  rear control. One native-scaled capture (July 2016) shows the bottom row regrouped: the active mode large at W/2, the other two
  modes small at **±60 epx**.
- **V-2017** (Windows Camera **2017.1003.21.2000**, the build on the final release — 16 native Wikimedia Commons screenshots
  from 2021): the settings page is measured in full, and **one landscape viewfinder**: a **72-epx grey shutter disc** 56 epx from
  the nav bar, video and panorama as 32-epx dark discs at **±60 epx**, settings gear and camera roll in the corners, and the
  quick toggles (flash, timer, …) in a **rounded capsule** along the top.

**Not established:** the **portrait viewfinder of V-2017** (only a 1280x720 YouTube still, LOW); the V-2016+ pro dial (the
same still shows a single ring with a "WB" badge and a large "White balance" label, LOW); zoom slider, timer countdown,
panorama progress beyond one frame, slow motion (the three measured phones do not offer it); **all motion**.

---

## 0. Method, sources and calibration

### 0.1 Unit convention

`epx` = 1/360 of screen width (R8 §0.1). Scale factors were measured from the 48-epx nav bar on every source: 950 192 px
(400 %, 360x640), 950 XL 168 px (350 %, 411.43x731.43), 650 96 px (200 %, 360x640), Commons 2021 192 px (400 %, 360x640 portrait,
640x360 landscape), OnMSFT 2016 102 px in a 1024-wide copy (a 480-epx-wide canvas, 2.133 px per epx). Fixed-vs-proportional is
decided between the 950 and the 950 XL; the 650 adds a third, independent 360-epx capture.

### 0.2 Sources

| ID | Local file (`docs/plan/r11/src/camera/`) | Pixel size | Device / canvas | App version, date | Build |
|---|---|---|---|---|---|
| **C1** | `gsm950_067.jpg` viewfinder | 1440x2560 | Lumia 950 @400 % → 360x640 | GSMArena 950 review, camera page, 2015-12-02 | ≈10586 |
| **C2** | `gsm950_068.jpg` column expanded | 1440x2560 | as C1 | as C1 | ≈10586 |
| **C3** | `gsm950_069.jpg` pro dial, all five rings | 1440x2560 | as C1 | as C1 | ≈10586 |
| **C4** | `gsm950_065.jpg`, `gsm950_066.jpg` settings, video-quality list | 1440x2560 | as C1 | as C1 | ≈10586 |
| **X1** | `gsm950xl_072.jpg` viewfinder | 1440x2560 | Lumia 950 XL @350 % → 411x731 | GSMArena 950 XL review, Dec 2015 | ≈10586 |
| **X2** | `gsm950xl_073.jpg` column expanded | 1440x2560 | as X1 | as X1 | ≈10586 |
| **X3** | `gsm950xl_074.jpg` pro dial, all five rings | 1440x2560 | as X1 | as X1 | ≈10586 |
| **X4** | `gsm950xl_075.jpg`, `gsm950xl_076.jpg` settings, list, "About this app 2015.1088.21.0" | 1440x2560 | as X1 | Windows Camera **2015.1088.21.0** | ≈10586 |
| **L1** | `gsm650_028.jpg`, `gsm650_029.jpg` viewfinder, column expanded | 720x1280 | Lumia 650 @200 % → 360x640 | GSMArena 650 review, 2016 | ≈10586 |
| **L2** | `gsm650_030.jpg`, `gsm650_031.jpg` single-control dial | 720x1280 | as L1 | as L1 | ≈10586 |
| **L3** | `gsm650_033.jpg` video mode | 720x1280 | as L1 | as L1 | ≈10586 |
| **L4** | `gsm650_034.jpg`, `gsm650_035.jpg` settings | 720x1280 | as L1 | as L1 | ≈10586 |
| **L5** | `gsm650_032.jpg` the Lenses page (Store) | 720x1280 | as L1 | as L1 | ≈10586 |
| **O1** | `onmsft_pano_2016-07-19.png` panorama mode | 1024x1820 (scaled) | 480-epx canvas (nav 102 px) | OnMSFT 2016-07-19, Windows Camera with panorama, Insider | 14393 era |
| **K1-K10** | `commons_camera_settings_2021_01.png` … `_10.png` settings (portrait) | 1440x2560 | @400 % → 360x640 | Wikimedia Commons, uploaded 2021-02-10, Windows Camera **2017.1003.21.2000** (K9 "About this app") | **final release** |
| **K11** | `commons_camera_settings_2021_11.png` landscape **viewfinder** with the Lenses hint | 2560x1440 | @400 % → 640x360 | as K1 | **final release** |
| **K12-K16** | `commons_camera_settings_2021_12.png` … `_16.png` settings (landscape) | 2560x1440 | as K11 | as K1 | final release |
| **Y1** | `yt_-TZ9Ev-LbOo_maxres.jpg` portrait viewfinder, WB dial | 1280x720 | YouTube still, phone screen ≈263 px wide inside the frame | "Windows 10 mobile camera app update - New features contains Panorama, HDR, Slow motion, Photo timer" | V-2016+ |
| **D1** | Lumia W10M user guide (R6 D1), `ug.txt` lines | — | — | Issue 1.1, 2016 | 10586-era document |
| **D2** | Windows Camera 2016.1018.11.0 changelog, quoted by WindowsLatest 2016-10-21 (Wayback) | — | — | Microsoft's changelog text | 14393 era |

GSMArena's XL review re-uses 950 images elsewhere (its shots 338 / 339 are byte-identical to the 950's 111 / 112); X1-X4 were
checked and are genuine XL captures (different pixel positions and a 411-epx layout).

### 0.3 Searched without finding

- **A native portrait viewfinder of V-2016 / V-2017:** Wikimedia Commons (category "Windows 10 Mobile camera settings" —
  16 files, one landscape viewfinder; category and intitle searches for Windows Camera / Lumia 950 XL / Windows 10 Mobile),
  Microsoft Store listing via `displaycatalog.mp.microsoft.com` (9WZDNCRFJBBG: its two "Mobile" screenshots are 1366x768 landscape
  images of the 2018+ desktop camera with Document / Whiteboard modes, not W10M), Thurrott 2016-07-20 panorama article (images
  404 live and in the Wayback Machine), OnMSFT 2016-08-24 (its second image fails to load), WindowsLatest 2016-10-21 (stock image
  only), GSMArena Lumia 550 (full-size originals 404).
- **Video:** `yt-dlp` blocked ("Sign in to confirm you're not a bot"); YouTube search candidates -TZ9Ev-LbOo (26 s, the V-2016+
  update), T2B-VT8Q6UU; signed storyboards were not obtainable for them (the public Invidious API returned a companion error),
  so only `maxresdefault.jpg` stills were used. No 60-fps source.
- **Slow motion on a measured phone:** the 950, 950 XL and 650 do not list a slow-motion mode in any capture; slow motion was
  offered on the Lumia 930 / 1520 / Icon class (search summaries of Windows Central's "Windows Camera hands-on with slow motion
  video"), none captured here.

---

## 1. Values

Confidence as in R8: **HIGH** = native, agreeing on two devices / scales or two independent captures; **MEDIUM** = native on
one capture, or a Microsoft document statement; **LOW** = downscaled, camera photo, a still inside a video frame, or a single
ambiguous reading; **INFERRED** = derived from measured values.

### 1.1 Viewfinder frame (V-2015, portrait)

| # | Value | Number | Confidence | Build | Derived from |
|---|---|---|---|---|---|
| 1.1.1 | Bars | **status bar hidden, nav bar drawn** (48 epx, black) | HIGH | V-2015 (C1, X1, L1), V-2016 (O1), V-2017 (K11) | no status icons in any capture; nav bar 48 epx |
| 1.1.2 | Photo preview (4:3) | fitted to the width, **vertically centred in the area above the nav bar**: 56 → 536 epx on 360x640, 67.43 → 616 on 411x731 | **HIGH** | V-2015 | C1 noisy-row run 56.0-536.0; X1 67.43-616.0; centres 296.0 = 592 / 2 and 341.7 = 683.43 / 2 |
| 1.1.3 | Video preview (16:9) | fills the width and the full height above the nav bar (0 → 592 epx on 360x640) | MEDIUM | V-2015 | L3 noisy rows 0-592 |
| 1.1.4 | Outside the preview | black | HIGH | V-2015 | C1, X1 |

### 1.2 Bottom row (V-2015)

| # | Value | Number | Confidence | Build | Derived from |
|---|---|---|---|---|---|
| 1.2.1 | Shutter (active mode) glyph | Camera glyph **24.0 x 19.5 epx**, white, at **x = W/2**, centre **44.75 epx above the nav bar top** | **HIGH** | V-2015 | C1 (180.0, 547.25 of 592); X1 (205.71, 638.58 of 683.43 → 44.85); L1 (180.0, 547.25) |
| 1.2.2 | Other capture mode | small glyph (Video, 16 x 8 epx) at **W/2 + 60** (fixed offset), centre **23.5 epx above the nav bar top** | **HIGH** | V-2015 | C1 x 240.0; X1 x 266.0 = 205.71 + 60.29; L1 x 240.0; cy 568.5 / 659.14 |
| 1.2.3 | In video mode | the modes swap: Video glyph (24 x 12) at W/2, cy 44 above the nav bar; Camera small (16 x 13) at **W/2 − 60.5**, cy 24.5 above | MEDIUM | V-2015 | L3 (180.0, 548.0), (119.5, 567.5) |
| 1.2.4 | "•••" | dots at **18 / 24 / 30 epx from the right**, cy **24 epx above the nav bar top** — the app bar's More cell (R7 2.1.14) | **HIGH** | V-2015 | C1, X1, L1 all R 24.00, cy 568.0 / 659.43 |
| 1.2.5 | Bottom-row backplate | none — the glyphs sit on the preview / black with no bar fill | HIGH | V-2015 | C1, X1, L1 samples between glyphs are preview or black |
| 1.2.6 | Video timer (recording mode) | "00:00", digit height **24.5 epx** (≈35-epx Light), centre y **483.25** (108.75 above the nav bar), centre x ≈164 (not W/2) | MEDIUM | V-2015 | L3 x 125.0-203.0, y 471.0-495.5 |

### 1.3 Control column (V-2015)

| # | Value | Number | Confidence | Build | Derived from |
|---|---|---|---|---|---|
| 1.3.1 | Column x | glyph centres **24 epx from the right edge** — fixed epx | **HIGH** | V-2015 | C1 R 23.4-24.3; X1 R 23.3-24.0; L1 R 23.5-24.0 |
| 1.3.2 | Pitch | **44 epx** (43.3-44.7) | **HIGH** | V-2015 | C1 241.5 / 285.0 / 329.25 / 362.6; X1 287.3 / 330.9 / 375.6 / 408.4; C2 153.6 / 197 / 231.5 / 276 / 320 / 364 / 408 / 449.5 |
| 1.3.3 | Collapsed items, top → bottom | **camera switch** (20 x 18.5), **flash** (16 x 20, "A" = auto), **Rich Capture / Living Images** (20 x 19.5, wand), **chevron down** (≈9 x 5.5) | HIGH (positions) / MEDIUM (identity of the third glyph) | V-2015 | C1, X1, L1 |
| 1.3.4 | Expanded items | + **WB**, **focus**, **ISO**, **shutter speed**, **exposure (±)**; the Rich Capture glyph is replaced; **chevron up** last | HIGH | V-2015 | C2, X2, L1 (029) |
| 1.3.5 | Caption under each manual control | current value ("auto", exposure "0.0"), 12-epx, **#878787** (135), caption centre ≈20 epx below the glyph centre | MEDIUM | V-2015 | C2 "auto" 247.5-255.25 under WB 231.5 |
| 1.3.6 | Glyph sizes | text glyphs "WB" 13.5 x 7, "ISO" 14.5 x 7.5; ring glyphs 14 x 14 | HIGH | V-2015 | X2, L1 |
| 1.3.7 | Vertical placement | the column (first glyph to chevron) is centred **≈6 epx below the preview centre** on the 950 and the 950 XL (302 vs 296; 347.6 vs 341.7); the 650 sits ≈30 below (326) | MEDIUM (950 / XL) / LOW (650) | V-2015 | C1 / C2 midpoints 302.1 / 301.6; X1 / X2 347.9 / 347.3; L1 326.3 / 325.5 |
| 1.3.8 | Camera roll | top-right corner cell centred **(W − 24, 24)**: a **36-epx round thumbnail** of the last shot (L1: x 318.2-354, y 6-41.4), or the Photo glyph (16 x 12) with no shot (X1) | MEDIUM | V-2015 | L1, X1 |
| 1.3.9 | Hand-off | "To view the photo you just took, tap the round thumbnail at the corner of the screen. The photo is saved in Photos." — it opens Photos' viewer on that shot (Photos §1.6, P8) | MEDIUM | 10586 doc + capture | D1 l.2697-2698; photos.md P8 (viewer with the 50-epx date header right after a capture) |

### 1.4 Pro dial (V-2015)

| # | Value | Number | Confidence | Build | Derived from |
|---|---|---|---|---|---|
| 1.4.1 | Form | **concentric circular arcs** clipped by the screen, all centred on the **nav bar's top edge at x = W/2** | **HIGH** | V-2015 | circle fits: C3 centre (179.9, 591.4-591.9); X3 (205.6, 683.1-683.3); L2 (179.8-179.9, 591.4-591.6) |
| 1.4.2 | Radii, all five rings | **130.5 · 195.4 · 260.3 · 325.3 · 390.2 epx** = 130.5 + **65**·k — fixed epx | **HIGH** | V-2015 | C3 130.53 / 195.43 / 260.39 / 325.25 / 389.99; X3 130.52 / 195.38 / 260.26 / 325.32 / 390.31 |
| 1.4.3 | Ring order, inner → outer | **exposure · shutter speed · ISO · focus · white balance** | HIGH | V-2015 | C3, X3 icon on each ring |
| 1.4.4 | Ring stroke | ≈1 epx, light grey (peak ≈#B4B4B4-#BEBEBE) | MEDIUM | V-2015 | L2 ring p95 lum 179; C3 column crossings 188-192 |
| 1.4.5 | Ring icons (WB, focus, ISO, shutter) | white (≈#EEEEEE), ≈17.5 epx, **on their own ring at the left**, at x ≈ **56 epx** (950) / **52 epx** (950 XL) from the left edge | MEDIUM | V-2015 | C3 icon centres x 56.0-56.25 at angles 108-130°; X3 51.7-52.1 at 113-142° |
| 1.4.6 | Exposure icon | on the **innermost ring's top**, at x = W/2, drawn over a black knockout | HIGH | V-2015 | C3 (180.0, 462.0) vs ring top 461.4; X3 (205.71, 553.43) vs 552.81 |
| 1.4.7 | Value labels | centred at x = W/2, **30.3 epx above each ring's top** (mid-band between rings), ≈15-epx, **#656565** (101) | **HIGH** | V-2015 | C3 "auto" at 171.1 / 236.1 / 301.1 / 366.1 and "0.0" at 430.6; X3 262.1 / 327.0 / 392.0 / 457.0 / 521.6 |
| 1.4.8 | Shutter in the five-ring view | moves up to centre **74.75 epx above the nav bar top** (from 44.75) | HIGH | V-2015 | C3 517.25 (592 − 74.75); X3 608.71 (683.1 − 74.4) |
| 1.4.9 | How the five-ring view opens | "slide the on-screen shutter button to the left" stacks all sliders | MEDIUM | V-2015 | GSMArena 950 review text, camera page |
| 1.4.10 | Single-control dial | tapping one control in the column shows **one arc of r 130.25** at the same centre, the control's icon on it at **139°** (x ≈ 80-85 epx), its value label 29.25 above the ring top; the column stays expanded with the active glyph white and the rest dimmed to **#7B7B7D**; the shutter stays at 44.75 | MEDIUM | V-2015 | L2 (030 focus, 031 shutter speed) |
| 1.4.11 | V-2016+ dial | a single ring with the control as a **black disc badge "WB" on the ring**, a large "White balance" label over the preview and "auto" under it; white-ringed shutter disc at the bottom centre | LOW | V-2016+ | Y1 |

### 1.5 Panorama (V-2016, portrait)

| # | Value | Number | Confidence | Build | Derived from |
|---|---|---|---|---|---|
| 1.5.1 | Bottom row | active mode (Panorama glyph 23.4 x 19.7) at **W/2**, centre **52 epx above the nav bar top**; Video at **W/2 − 60.2** and Photo at **W/2 + 60.2**, small (11.7 epx wide), centre 32 above; "•••" in a dark disc at the right | MEDIUM | 14393 era | O1 (240.0, 753.3); (179.8, 773.4); (300.2, 772.5); nav top 805.3 on a 480 x 853 canvas |
| 1.5.2 | Mode buttons' backplates | faint dark discs behind the small mode glyphs | LOW | 14393 era | O1 |
| 1.5.3 | Guide band | a translucent dark band across the full width, ≈168 epx tall centred at ≈427 epx; the captured strip in a **white-outlined frame at the left** (≈94 x 166 epx) and a **white arrow →** to its right on a thin centre line | LOW | 14393 era | O1 band ≈342-511, arrow x ≈104-128 epx |
| 1.5.4 | Direction | phone held upright (portrait), sweep left → right, the arrow pointing right | MEDIUM | 14393 era | O1 |
| 1.5.5 | Camera roll | round thumbnail ≈30-32 epx in the top-right corner (centre ≈21 from the right, 25 from the top) | MEDIUM | 14393 era | O1 443.9-473.4 x 8.9-41.3 |

### 1.6 Final-release viewfinder (V-2017, landscape)

| # | Value | Number | Confidence | Build | Derived from |
|---|---|---|---|---|---|
| 1.6.1 | Shutter button | a **72-epx disc**, fill **#666666**, a 2-epx dark inner ring just inside the edge, black Camera glyph (≈24 epx); centre **56 epx** from the nav bar edge, vertically centred (y 180 of 360) | MEDIUM | 2017.1003 | K11 x 500-572, y 144-215 |
| 1.6.2 | Other modes | **Video** above and **Panorama** below the shutter as **32-epx dark translucent discs** with 16-epx glyphs, centres **±60 epx** from the shutter centre, **36 epx** from the nav bar edge | MEDIUM | 2017.1003 | K11 discs y 104-136 and 225-257 at x 540-572 |
| 1.6.3 | Settings | gear in a ≈36-epx dark disc in the top corner next to the nav bar (centre ≈28 from the nav edge, 24 from the top) — D2's "launch into Settings directly from the camera UI" | MEDIUM | 2017.1003 | K11 |
| 1.6.4 | Camera roll | **36 x 36-epx square thumbnail** in the bottom corner next to the nav bar (centre 28 from the nav edge, 24 from the bottom) — D2's "new spot on the screen" | MEDIUM | 2017.1003 | K11 x 546-582, y 318-354 |
| 1.6.5 | Quick toggles | a **rounded capsule** across the top edge, ≈43 epx tall (y 2-45), x ≈219-423 epx on the 640-wide landscape canvas, dark translucent, holding **flash (off)**, a rectangle-with-lines glyph with a "disabled" slash (identity not established), **photo timer (off)** and a **chevron >** at ≈44-epx pitch | MEDIUM (form) / LOW (glyph identity) | 2017.1003 | K11 glyph centres x ≈254 / 299 / 341 / 386 |
| 1.6.6 | Camera switch | its own ≈36-epx disc in the top-left corner | LOW | 2017.1003 | K11 (partly under the hint text) |
| 1.6.7 | Lenses hint | "Add more features to your camera using Lenses. You can get all kinds of Lenses from the Store." (≈15-epx white, two lines) and "find more lenses" (≈20-epx) over the preview, top-left | MEDIUM | 2017.1003 | K11 |
| 1.6.8 | Portrait form of the same controls | shutter a large white-ringed disc at the bottom centre; camera roll thumbnail square at the bottom-left | LOW | V-2016+ | Y1 |

### 1.7 Settings page

| # | Value | Number | Confidence | Build | Derived from |
|---|---|---|---|---|---|
| 1.7.1 | Header | "SETTINGS", all caps, cap **16.5-16.75 epx** (≈24-epx), left 12.0, cap top 24.5 — larger than the Photos / Groove 11-epx titles | **HIGH** | V-2015 (L4) = V-2017 (K1) | L4 'E' 24.5-41.0; K1 'E' 24.5-41.25 |
| 1.7.2 | Section titles | "Photos", "Videos", "Related settings", "About this app": cap **16.75** (≈24-epx), left 12.0 | HIGH | both | K1 'P' 274.5-291.25; L4 161.5-179.5 band |
| 1.7.3 | Item labels | 15-epx white (cap 10.5), left 13.25-13.5 | HIGH | both | K1 'L' 73.0-83.5; L4 |
| 1.7.4 | Descriptions | 15-epx **#999999** (153) | HIGH | both | K1, L4 (153) |
| 1.7.5 | Combo box | **32 epx** tall (K1 337.0-369.0), **2-epx** border, x 12 → W − 12, chevron at **22.25 epx** from the right (326.25-337.75 on 360; 377.71-389.14 on 411 — fixed from the right) | **HIGH** | both | K1; C4 / X4 chevron columns |
| 1.7.6 | Border colour | #656565 (101) on the black V-2015 page; ≈#828282 on the V-2017 translucent page | HIGH (V-2015, three devices) / MEDIUM (V-2017) | both | L4, C4 border lum 101; K1 125-130 |
| 1.7.7 | Label → next label pitch (label + box row) | **80 epx** | HIGH | both | K1 314.5 → 394.25 → 474.25; X4 154.57 → 234.29 → 314.29 |
| 1.7.8 | Open list | fill **#2B2B2B**, items 15-epx at x 24.25-25.5 (12 inside the box), **44-epx** pitch, the current item filled with the **accent** | HIGH | both | K2 121.25 / 165.25 / 209.25; X4 (076) 340.57 / 384.57 / 428.57 / 472.57 |
| 1.7.9 | Page background | V-2015: **black**; V-2017: a **dark translucent layer over the live preview** (samples (25,14,13)-(45,40,41)) | HIGH (V-2015) / MEDIUM (V-2017) | both | C4 / L4 (0,0,0); K1 |
| 1.7.10 | Toggle | as R3 C1 (44 x 20, label 56 right of its left edge) | HIGH | both | C4 "Off" at 68.75 |
| 1.7.11 | Landscape settings | the same page as a column beside the live preview (K12-K16) | MEDIUM | 2017.1003 | K12-K16 |

### 1.8 Modes and features present per version

| # | Value | Number | Confidence | Build | Derived from |
|---|---|---|---|---|---|
| 1.8.1 | V-2015 | photo, video, manual controls (dial), Rich Capture (950 / 950 XL), Living Images toggle, DNG (950), framing grid, 4K on the 950 / XL | HIGH | V-2015 | C1-C4, X4, L4 |
| 1.8.2 | Panorama | added July 2016 (Insider) / Aug 2016 (all), on Lumia 550 / 650 / 950 / 950 XL | MEDIUM | 14393 era | OnMSFT 2016-07-19 / 2016-08-24 |
| 1.8.3 | Photo timer, zoom slider, settings button, moved camera roll | 2016.1018.11.0 changelog | MEDIUM | 14393 era | D2 |
| 1.8.4 | V-2017 settings additions | Lenses, "Press and hold camera button" = Video / Photo burst / Disabled, Time lapse, Digital video stabilization, Related settings links | HIGH | 2017.1003 | K1-K10 |
| 1.8.5 | Slow motion | not on the measured phones; no capture | UNMEASURED | — | §0.3 |

---

## 2. Strings as shipped

V-2015 (C4, X4, L4; en-GB phones): "SETTINGS", "Press and hold camera button" / "Disabled", "Photos", "Aspect ratio" / "4:3",
"Framing grid" / "Off", "Image size for main camera" / "JPEG (19 MP)" (950), "Focus light" / "Use flash settings", "Focus light
is set to automatic unless the flash is turned off.", "Capture living images" / "Off", "Videos", "Video recording" /
"1920x1080p/60 fps" (list 3840x2160p/30, /25, /24 fps; 1920x1080p/60, /30, /25, /24 fps; 1280x720p/60, /30, /25 fps …), "About
this app", "2015.1088.21.0", "© 2015 Microsoft Mobile. All rights reserved.", "Terms of Use"; dial labels "WB", "ISO", values
"auto", "0.0"; flash toast "Auto" (C1).

V-2017 (K1-K16): "SETTINGS"; "Lenses" / "Use other photography apps as extra lenses for your camera." / "View lenses"; "Press
and hold camera button" / "Video", "Photo burst", "Disabled"; "Photos"; "Aspect ratio" / "16:9", "4:3"; "Framing grid" / "Off";
"Image size for main camera" / "JPEG (16 MP)", "JPEG (8 MP)", "JPEG (8 MP) + DNG (16 MP)"; "Focus light" / "Use flash settings",
"Auto", "Always off"; "Focus light is set to automatic unless the flash is turned off."; "Time lapse" / "When the timer is on,
keep taking photos until I press the camera button again."; "Capture living images"; "Videos"; "Video recording" /
"1920x1080p/30 fps" …; "Digital video stabilization"; "Related settings" / "Manage OneDrive upload settings", "Change where
photos and videos are saved", "Choose whether camera can use location info", "Change privacy settings"; "About this app" /
"2017.1003.21.2000" / "© 2016 Microsoft Mobile. All rights reserved." / "Terms of Use" / "Privacy Statement" / "Send feedback";
viewfinder hint "Add more features to your camera using Lenses. You can get all kinds of Lenses from the Store." / "find more
lenses"; toast "Saving to Screenshots..." (system).

D1 (en-US, 10586): "To zoom in or out, slide your fingers apart or together."; "To focus the camera on a specific object, tap
the object on the screen."; "To switch from photo to video mode, tap [video]."; "To start recording, tap [video]. The timer
starts to run."; "To view the photo you just took, tap the round thumbnail at the corner of the screen."; "Living Images capture
a brief moment of video with every photo you take, so they seem alive when you browse them in Photos." (l.2686-2728).

D2 (2016.1018.11.0): "Enjoy taking photos, videos, and panoramas with our higher-contrast capture buttons."; "Set a photo timer
right from the camera dashboard with our new toggle control."; "Get to Settings faster! Now, launch into Settings directly from
the camera UI."; "Access your camera roll with one hand from its new spot on the screen."; "Zoom more easily with the new zoom
slider."; "Make sure you nailed the shot, with a more noticeable capture animation."; "Change between front- and rear-facing
cameras with a more prominent button control."

## 3. Segoe MDL2 glyphs the shell's icon font needs

Codepoints from Microsoft's Segoe MDL2 Assets list (learn.microsoft.com/en-us/windows/apps/design/iconography/segoe-ui-symbol-font,
read 2026-09-23). **Windows Camera's own control glyphs (flash with "A", Rich Capture wand, WB, ISO text badges, focus ring,
shutter-speed clock, exposure ±, panorama frame, timer with slash) are not in the public MDL2 list** — the shell draws them as
its own vectors (P4-style, NEEDS-HUMAN look row) or uses the nearest listed glyph below.

| Use | Glyph name | Codepoint | Note |
|---|---|---|---|
| Photo shutter | Camera | E722 | C1, K11 |
| Video mode | Video | E714 | C1, K11 |
| Front / back switch | RotateCamera | E89E | nearest listed; W10M's glyph is a camera with two arrows |
| Flash | LightningBolt / Flashlight | E945 / E754 | W10M draws a bolt; E754 is a torch — use E945 |
| Photo timer | Stopwatch | E916 | nearest listed |
| Settings | Setting | E713 | K11 |
| Camera roll placeholder | Photo | E91B | X1 (16 x 12 glyph when no shot exists) |
| Column expand / collapse | ChevronDown / ChevronUp | E70D / E70E | C1, C2 |
| Capsule expand | ChevronRight | E76C | K11 |
| Overflow | More | E712 | C1 |
| Slow motion toggle | SlowMotionOn | EA79 | listed; not seen in a capture |
| Document mode (not W10M) | Scan | E8FE | only on the 2018+ desktop camera; not needed |

## 4. Motion (RV11)

No 60-fps source exists (§0.3); every motion value is UNMEASURED with a tagged approximation.

| Motion | Status | Proposed approximation | Derived from |
|---|---|---|---|
| Camera open (tile / slot tap) | UNMEASURED | the shell's Start exit (R3 A11) then the viewfinder appears with the preview; no in-app entrance animation | R3 A11 |
| Column expand / collapse (chevron) | UNMEASURED | items slide down / up with R7 4.3's quick-action curves: expand **≈200 ms**, collapse **≈283 ms** | R7 4.3.2-4.3.3 (action center quick-actions expand / collapse, 14393) |
| Five-ring dial open (shutter slid left) | UNMEASURED | finger-tracked, release settles with R7 4.1.4's fixed **≈290 ms** | R7 4.1.2 / 4.1.4 |
| Dial value change | UNMEASURED | finger-tracked along the arc, no settle | as above |
| Mode switch (photo ↔ video ↔ panorama) | UNMEASURED | glyph swap cut; preview aspect change as a cut | R7 1.8.2 (W10M in-app switches were one-frame cuts) |
| Capture feedback ("more noticeable capture animation", D2) | UNMEASURED | a **≈133 ms** white flash of the preview (R3 C5 pane timing class) | R3 C5 (133 ms MSN Weather pane slide, the shortest measured W10M transition) — NEEDS-HUMAN accept |
| Settings page open | UNMEASURED | page fade-in from black, ease-out 200-317 ms | R7 3.2.2 |

## UNMEASURED

| # | What | Why not | Proposed tagged approximation (and its source) | What would settle it |
|---|---|---|---|---|
| **1** | The **V-2017 portrait viewfinder** (governing build) | only a landscape native capture (K11) and a LOW YouTube still (Y1) exist | rotate K11's measured layout into portrait: shutter **72-epx disc** at W/2, **56 epx above the nav bar**; Video / Panorama 32-epx discs at **±60 epx** horizontally, 36 epx above the nav bar; camera roll 36-epx square in the bottom-left corner cell; settings disc top-right; the capsule of toggles along the top — consistent with Y1 (bottom-centre shutter, bottom-left roll) | a native portrait screenshot of Windows Camera 2016.1018+ |
| **2** | The **V-2016+ pro dial** geometry | Y1 is a still inside a 16:9 frame | V-2015's measured dial (1.4: arcs r 130.5 + 65·k centred on the nav bar top at W/2, labels 30.3 epx above each ring) with Y1's badge-on-ring and large control-name label as the LOW-confidence change | a native capture of the 2016+ dial |
| **3** | Zoom slider (D2) | no capture | R3 C1 slider metrics (2-epx track, 6.4 x 20.4 thumb) vertical beside the shutter, P4 look row | a capture while zooming |
| **4** | Timer countdown and time-lapse indicator | no capture | the V-2015 video timer style (1.2.6, 24.5-epx digits) centred on the preview | a capture with the timer running |
| **5** | Panorama progress, completion and stitching preview | one frame of the guide (1.5.3) | the guide band and strip frame as measured; progress as the frame's growing width | a panorama capture sequence |
| **6** | Slow motion and Living Images indicators in the viewfinder | not on the measured phones / not captured | Living Images: the V-2015 wand-slot glyph lit (1.3.3); slow motion: SlowMotionOn (EA79) in the capsule, P4 | captures on a 930 / 1520 / Icon |
| **7** | Capture-intent (Q5) UI — accept / retake after a capture | W10M's camera answered camera requests from other apps, but no capture of that flow was found | the Rich Capture editor's app bar pattern (photos.md 1.7.5: transparent bar, Accept ✓ at 82 and More at 24 from the right) plus a Retake at 150 | a capture of an app requesting a photo |
| **8** | All motion | no 60-fps source | §4 | a Project My Screen recording |
| **9** | Front-camera viewfinder differences (mirroring, beauty) | no capture | same layout, preview mirrored | a front-camera capture |
| **10** | The flash / rich-capture toasts ("Auto" in C1) — size and duration | one frame, no timing | C1: "Auto" ≈ 20-epx at the lower preview centre; 1.5 s (agent pick) | video |

## Gaps for the phase doc

- **Y3 (viewfinder chrome):** measured, but it **conflicts with phase 17's stand-in** ("shutter 64 epx centred above the nav bar;
  controls in a 48-epx row at the top; modes as a horizontal strip above the shutter"). What W10M did: V-2015 — a right-edge
  column on a 44-epx pitch 24 epx from the edge, shutter glyph (no disc) 44.75 above the nav bar, the other mode 60 epx to the
  side, "•••" in the app-bar corner; V-2016 / V-2017 — a **72-epx shutter disc** with the other modes as **32-epx discs at ±60
  epx**, the toggles in a **top capsule**, settings and camera roll in corner cells. There is **no horizontal mode strip** in any
  version. Recommend building the governing V-2017 form (UNMEASURED-1's portrait rotation) as a tagged approximation with a
  fidelity row against K11, or V-2015 fully measured — an owner ruling (NEEDS-HUMAN), like Photos' version ruling.
- **Y4 (pro dial):** measured (V-2015): five arcs centred on the nav bar top at W/2, radii **130.5 + 65·k epx**, inner →
  outer exposure / shutter / ISO / focus / WB, labels at mid-band, icons on the arcs at the left, exposure icon at the innermost
  top; one control alone = one 130-epx arc. The phase's stand-in ("a quarter-arc dial from the shutter, one control at a time")
  is close to the single-control form (1.4.10) but the arc is centred on the **nav bar top at W/2**, not on the shutter. The
  five-ring view is part of W10M's dial and should be in the build.
- **Y7 (bars):** measured — **status bar hidden, nav bar drawn** in every version (1.1.1). Matches phase 17's candidate.
- **Y12 (panorama):** LOW-MEDIUM (1.5): full-width translucent guide band, strip frame at the left, arrow and centre line;
  phone held vertically. Phase 17's stand-in ("a centre guide line with a growing preview strip") matches the elements.
- **Settings page:** fully measured for the governing build (1.7, §2): phase 17 can take the page as is, dropping Lenses,
  OneDrive and "Related settings" links that have no Android meaning, and adding Android-only rows as P4 (e.g. HEIC) if wanted.
- **Flash / timer / grid / front-back / zoom (Q2 C):** flash and front-back are measured positions (1.3.3, 1.6.5-1.6.6); timer
  and zoom exist only as D2 text (UNMEASURED-3 / -4); grid is a settings row (1.7, "Framing grid").
- **Slow motion:** no W10M capture on any phone reachable (UNMEASURED-6); the phase's "not shown when the phone cannot"
  rule is unaffected.
- **Capture intents (Q5):** UNMEASURED-7 — the accept / retake UI needs a P4 row.
- **Motion (Y6):** UNMEASURED; §4 gives tagged approximations.

## Tally

Counted by script from the Confidence column of every row in §1 (a row with two levels counts at the lower): see the line
below, plus 10 items in the UNMEASURED table and 7 motion values in §4, all UNMEASURED with tagged approximations.

**HIGH 27 · MEDIUM 24 · LOW 7 · UNMEASURED 1** (59 rows).

## Sources

Provenance (originals unaltered; the `src/` folder is gitignored, third-party images stay local):

| Local file | URL | Fetched | Resolution | sha256 |
|---|---|---|---|---|
| `docs/plan/r11/src/camera/gsm950_065.jpg` | https://st.gsmarena.com/imgroot/reviews/15/microsoft-lumia-950/shots/gsmarena_065.jpg | 2026-09-23 | 1440x2560 | `7cbf270f15ed339d952f2185b37f327b6b8ead8a4cd49ed22bd9c164cded9845` |
| `docs/plan/r11/src/camera/gsm950_066.jpg` | https://st.gsmarena.com/imgroot/reviews/15/microsoft-lumia-950/shots/gsmarena_066.jpg | 2026-09-23 | 1440x2560 | `12fb3e187c3a6a811296752674c1ae71746c04b2ef75d726b4c3aff4e6b10646` |
| `docs/plan/r11/src/camera/gsm950_067.jpg` | https://st.gsmarena.com/imgroot/reviews/15/microsoft-lumia-950/shots/gsmarena_067.jpg | 2026-09-23 | 1440x2560 | `01386a0817a36eef17c0612a39674709a29742a84db987b91da1b9e587015052` |
| `docs/plan/r11/src/camera/gsm950_068.jpg` | https://st.gsmarena.com/imgroot/reviews/15/microsoft-lumia-950/shots/gsmarena_068.jpg | 2026-09-23 | 1440x2560 | `10ed0ce912b75d4244ae1379239fd71922c52e39197bea1cb87bdb743feb13f3` |
| `docs/plan/r11/src/camera/gsm950_069.jpg` | https://st.gsmarena.com/imgroot/reviews/15/microsoft-lumia-950/shots/gsmarena_069.jpg | 2026-09-23 | 1440x2560 | `bf1e151031418a7fc2aaa73948797bb4a3347d31b5971f45c01a995706ec45cc` |
| `docs/plan/r11/src/camera/gsm950xl_072.jpg` | https://fdn.gsmarena.com/imgroot/reviews/15/microsoft-lumia-950-xl/shots/gsmarena_072.jpg | 2026-09-23 | 1440x2560 | `93f89e1ff222b960fb0297c752409b774eaaf971feec50f9497ad153ddde180c` |
| `docs/plan/r11/src/camera/gsm950xl_073.jpg` | https://fdn.gsmarena.com/imgroot/reviews/15/microsoft-lumia-950-xl/shots/gsmarena_073.jpg | 2026-09-23 | 1440x2560 | `9250330a44d62f30417c02b5c8d5e2db39b7dc7dabd319a4fcb42bbab13d9bff` |
| `docs/plan/r11/src/camera/gsm950xl_074.jpg` | https://fdn.gsmarena.com/imgroot/reviews/15/microsoft-lumia-950-xl/shots/gsmarena_074.jpg | 2026-09-23 | 1440x2560 | `a6e4ef23e8ef5c7c46bc9db58e5a299c89898f58a4a42143b690673c04e39038` |
| `docs/plan/r11/src/camera/gsm950xl_075.jpg` | https://fdn.gsmarena.com/imgroot/reviews/15/microsoft-lumia-950-xl/shots/gsmarena_075.jpg | 2026-09-23 | 1440x2560 | `3d2680c5850e7da235f22d3e8f5aa22703ae6f34d3bc50ff41633a4154a7cbbf` |
| `docs/plan/r11/src/camera/gsm950xl_076.jpg` | https://fdn.gsmarena.com/imgroot/reviews/15/microsoft-lumia-950-xl/shots/gsmarena_076.jpg | 2026-09-23 | 1440x2560 | `17948dcdeb7bda2b8adc247696b69784f2cda096aedbfe0257ced8c58e2c7d6f` |
| `docs/plan/r11/src/camera/gsm650_028.jpg` | https://fdn.gsmarena.com/imgroot/reviews/16/microsoft-lumia-650/sshots/gsmarena_028.jpg | 2026-09-23 | 720x1280 | `7ad00627e5214499cbdfda12b6f3d78a0f507d4472f99e9216890dd143b929c4` |
| `docs/plan/r11/src/camera/gsm650_029.jpg` | https://fdn.gsmarena.com/imgroot/reviews/16/microsoft-lumia-650/sshots/gsmarena_029.jpg | 2026-09-23 | 720x1280 | `7a75a7d6390c00a847514dedff152d6dba250ca0adb8d7af45523e24eae55580` |
| `docs/plan/r11/src/camera/gsm650_030.jpg` | https://fdn.gsmarena.com/imgroot/reviews/16/microsoft-lumia-650/sshots/gsmarena_030.jpg | 2026-09-23 | 720x1280 | `0a8930a6a3a3df2fb20409ea4347ebc9c8397a87338029f8beeff16f12011f89` |
| `docs/plan/r11/src/camera/gsm650_031.jpg` | https://fdn.gsmarena.com/imgroot/reviews/16/microsoft-lumia-650/sshots/gsmarena_031.jpg | 2026-09-23 | 720x1280 | `f9eaf7fd585bf6a27685ace124b65fce94e05cd601c1f99c006e12a1c43ba915` |
| `docs/plan/r11/src/camera/gsm650_032.jpg` | https://fdn.gsmarena.com/imgroot/reviews/16/microsoft-lumia-650/sshots/gsmarena_032.jpg | 2026-09-23 | 720x1280 | `f6541d0cabb5f8a28200ebca77b4f13703cec5a3be405668bd27b30a548914bb` |
| `docs/plan/r11/src/camera/gsm650_033.jpg` | https://fdn.gsmarena.com/imgroot/reviews/16/microsoft-lumia-650/sshots/gsmarena_033.jpg | 2026-09-23 | 720x1280 | `7b5c18286ec63bd3fabd9512d66c1f7e170ea654f5b0cad48f023519ec43b3cc` |
| `docs/plan/r11/src/camera/gsm650_034.jpg` | https://fdn.gsmarena.com/imgroot/reviews/16/microsoft-lumia-650/sshots/gsmarena_034.jpg | 2026-09-23 | 720x1280 | `584530112eb95a2414eac243084d998aedd5aa3ca171444c12e051645d3a464c` |
| `docs/plan/r11/src/camera/gsm650_035.jpg` | https://fdn.gsmarena.com/imgroot/reviews/16/microsoft-lumia-650/sshots/gsmarena_035.jpg | 2026-09-23 | 720x1280 | `6865bd1cb0bd9438b42b147de944eb469dbddea25dd76bfae75ff30b97aac5fc` |
| `docs/plan/r11/src/camera/onmsft_pano_2016-07-19.png` | https://i1.wp.com/www.onmsft.com/wp-content/uploads/2016/07/wp_ss_20160719_0002.png | 2026-09-23 | 1024x1820 | `1d1c71234fe8a21ef3535601f9c5a3cb5391b6ed536383d70f29087d8e2e3acb` |
| `docs/plan/r11/src/camera/commons_camera_settings_2021_01.png` | https://upload.wikimedia.org/wikipedia/commons/1/13/Camera_settings_-_Microsoft_Windows_10_Mobile_%282021%29_01.png | 2026-09-23 | 1440x2560 | `f930e29932b848ff368a0d31734f3502137bfa301e927fd1a9303f0650d01da4` |
| `docs/plan/r11/src/camera/commons_camera_settings_2021_02.png` | https://upload.wikimedia.org/wikipedia/commons/9/99/Camera_settings_-_Microsoft_Windows_10_Mobile_%282021%29_02.png | 2026-09-23 | 1440x2560 | `45a8183072f4b1d7aabdb643c316eaa24575c61a3060582aea4b9f8f386d10e7` |
| `docs/plan/r11/src/camera/commons_camera_settings_2021_03.png` | https://upload.wikimedia.org/wikipedia/commons/a/ad/Camera_settings_-_Microsoft_Windows_10_Mobile_%282021%29_03.png | 2026-09-23 | 1440x2560 | `b1d8dcc3404601a2bb6e201740b051193de67e17e329f550fcc17c2c27d67418` |
| `docs/plan/r11/src/camera/commons_camera_settings_2021_04.png` | https://upload.wikimedia.org/wikipedia/commons/3/39/Camera_settings_-_Microsoft_Windows_10_Mobile_%282021%29_04.png | 2026-09-23 | 1440x2560 | `2b6f06c3a7fd857ad208e2b11cd394a5ab6155f73dc54f1bd8a737c5d073b0c2` |
| `docs/plan/r11/src/camera/commons_camera_settings_2021_05.png` | https://upload.wikimedia.org/wikipedia/commons/d/dd/Camera_settings_-_Microsoft_Windows_10_Mobile_%282021%29_05.png | 2026-09-23 | 1440x2560 | `d8f7c84acfc8dd172613fc921b5d822dff1b25ebeaa25e34460fc2b2bb308180` |
| `docs/plan/r11/src/camera/commons_camera_settings_2021_06.png` | https://upload.wikimedia.org/wikipedia/commons/0/0a/Camera_settings_-_Microsoft_Windows_10_Mobile_%282021%29_06.png | 2026-09-23 | 1440x2560 | `72fa65399de89b0bc52ad68d569e0abdf18f36fdafa43145fcc3983bec76ef1b` |
| `docs/plan/r11/src/camera/commons_camera_settings_2021_07.png` | https://upload.wikimedia.org/wikipedia/commons/f/f5/Camera_settings_-_Microsoft_Windows_10_Mobile_%282021%29_07.png | 2026-09-23 | 1440x2560 | `3909255b5146dbe579743395d65536e4a59d598a62b3896a5844555d1ec222bf` |
| `docs/plan/r11/src/camera/commons_camera_settings_2021_08.png` | https://upload.wikimedia.org/wikipedia/commons/f/fb/Camera_settings_-_Microsoft_Windows_10_Mobile_%282021%29_08.png | 2026-09-23 | 1440x2560 | `b6b5e566b3758f55e118a8045d8b85386d2100d05f8b45296674ea7bb587ed7d` |
| `docs/plan/r11/src/camera/commons_camera_settings_2021_09.png` | https://upload.wikimedia.org/wikipedia/commons/8/88/Camera_settings_-_Microsoft_Windows_10_Mobile_%282021%29_09.png | 2026-09-23 | 1440x2560 | `8782132cef89736c6a1becdb87edcd34b00ed763c11ad97f6dd4435e02e2f6ab` |
| `docs/plan/r11/src/camera/commons_camera_settings_2021_10.png` | https://upload.wikimedia.org/wikipedia/commons/d/de/Camera_settings_-_Microsoft_Windows_10_Mobile_%282021%29_10.png | 2026-09-23 | 1440x2560 | `660d3ae5e8f27684e027eb03d70ff615c338e7915913cb389fdb308be2b7f6d8` |
| `docs/plan/r11/src/camera/commons_camera_settings_2021_11.png` | https://upload.wikimedia.org/wikipedia/commons/8/84/Camera_settings_-_Microsoft_Windows_10_Mobile_%282021%29_11.png | 2026-09-23 | 2560x1440 | `f8e4c17a8ea7ff54fe4a46e817cfc5f9dfc5e25bb9d3b012f9e41a9b4b32a117` |
| `docs/plan/r11/src/camera/commons_camera_settings_2021_12.png` | https://upload.wikimedia.org/wikipedia/commons/1/19/Camera_settings_-_Microsoft_Windows_10_Mobile_%282021%29_12.png | 2026-09-23 | 2560x1440 | `fb42632dad134d89d722313d3ef43662de43709fd8def25c7ffab8f131950092` |
| `docs/plan/r11/src/camera/commons_camera_settings_2021_13.png` | https://upload.wikimedia.org/wikipedia/commons/8/89/Camera_settings_-_Microsoft_Windows_10_Mobile_%282021%29_13.png | 2026-09-23 | 2560x1440 | `83ddca99a6f14488c23c5d92f3e078f555a4364e6dae89098197b4ffd128afc9` |
| `docs/plan/r11/src/camera/commons_camera_settings_2021_14.png` | https://upload.wikimedia.org/wikipedia/commons/9/9e/Camera_settings_-_Microsoft_Windows_10_Mobile_%282021%29_14.png | 2026-09-23 | 2560x1440 | `3e4ef441dbd97ff3c6870c4d465c6bf45c3e6be9e09693b89da39077c9850385` |
| `docs/plan/r11/src/camera/commons_camera_settings_2021_15.png` | https://upload.wikimedia.org/wikipedia/commons/6/61/Camera_settings_-_Microsoft_Windows_10_Mobile_%282021%29_15.png | 2026-09-23 | 2560x1440 | `751d9ad8764a04d1d4560f601c0b182ced1f087c2d9504ce93c5d157fe8c51ef` |
| `docs/plan/r11/src/camera/commons_camera_settings_2021_16.png` | https://upload.wikimedia.org/wikipedia/commons/3/3c/Camera_settings_-_Microsoft_Windows_10_Mobile_%282021%29_16.png | 2026-09-23 | 2560x1440 | `231547ce2bbe7ba93ce24c2d77925b6226d731ec1547f601e65a80925fd26f2f` |
| `docs/plan/r11/src/camera/yt_-TZ9Ev-LbOo_maxres.jpg` | https://i.ytimg.com/vi/-TZ9Ev-LbOo/maxresdefault.jpg | 2026-09-23 | 1280x720 | `732454a0b85ff86c1dd326bca507cd436fdb57ffc7c18a315a45ffb2ee8133d5` |

Articles and documents that date or version the sources:
- https://www.gsmarena.com/microsoft_lumia_950-review-1347p8.php — GSMArena Lumia 950 review, camera features (2015-12-02): "slide the on-screen shutter button to the left" for all sliders; C1-C4
- https://www.gsmarena.com/microsoft_lumia_950_xl-review-1349p8.php — GSMArena Lumia 950 XL review, camera features; X1-X4
- https://www.gsmarena.com/microsoft_lumia_650-review-1418p4.php — GSMArena Lumia 650 review, camera; L1-L5
- https://web.archive.org/web/2018id_/https://www.onmsft.com/news/panorama-arrives-windows-10-mobile-update-windows-camera — OnMSFT 2016-07-19, panorama for Insiders; O1
- https://web.archive.org/web/2018id_/https://www.onmsft.com/news/windows-10-mobile-camera-app-updated-users-panorama-mode — OnMSFT 2016-08-24, panorama for all users
- https://web.archive.org/web/2017id_/https://www.windowslatest.com/2016/10/21/windows-camera-app-updated-slow-release-preview-ring-windows-10-mobile-10/ — Windows Camera 2016.1018.11.0 changelog (D2)
- https://commons.wikimedia.org/wiki/Category:Windows_10_Mobile_camera_settings — the 16 Commons screenshots (public domain, uploader Donald Trung, 2021-02-10); K1-K16
- Lumia with Windows 10 Mobile User Guide, Issue 1.1 (R6 D1)
- https://learn.microsoft.com/en-us/windows/apps/design/iconography/segoe-ui-symbol-font — MDL2 codepoints (§3)

Evidence root (scratchpad, this session): `r11pcmf/` — `img/`, `scripts/` (`arcs.py` circle fits for the dial, `lines.py`,
`appbar.py`, `gridcrop.py`, `prov.py`), `sheets/`. Every value can be re-derived from a fresh download of the URLs above.
