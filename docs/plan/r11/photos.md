# R11 §Photos — Microsoft Photos (Windows 10 Mobile) measurements

Research section of R11 (`docs/plan/r11-inbox-apps.md`) for phase 17 (`docs/plan/phase-17-inbox-photos-camera-video.md`,
Q1 C: collection, albums, viewer, share / delete / set as, slideshow, the editor and video trim). It feeds phase 17's
approximation rows Y1 (collection, grid, album tiles), Y2 (viewer chrome and app-bar glyph set), Y6 (motion), Y7 (bars) and
Y11 (editor tool UI). Format and rigour follow `docs/plan/r8-groove-measurements.md`; shared values are cited from R3
(`w10m-measurements.md`), R6, R7 and R8 rather than re-measured.

## Summary — what was and was not established

**Two versions of Photos shipped on W10M, and they differ visibly.** The **V-2015** app (10586 era, the GSMArena Lumia 950 and
950 XL reviews, Dec 2015) is measured at native resolution on **two devices at two scale factors** (400 % and 350 %), which
separates fixed-epx from width-proportional values: nearly every chrome value is fixed in epx and the thumbnail grid changes
column count with width. The **V-2016+** app is the universal Photos redesign that Microsoft pushed to phones from October 2016
(Thurrott 2016-10-06: "the update is also available on Windows 10 Mobile"; production-ring version 17.202.10012.0 on
2017-02-18). The **only native governing-era capture** is one Wikimedia Commons screenshot of the collection page on a Lumia
950 XL, 2018-10-30 (G1). It shows the V-2016+ collection: mixed-case 28-epx pivot titles on a black page with no header
band, an accent 15-epx month header, a "29 items" day row, **3** columns of 127-epx squares with 4-epx gutters at 411 epx wide,
and the bottom app bar with Sync / Select / Slideshow / More at the standard W10M pitch. Every other V-2016+ screen (albums,
folders, viewer, settings, editor) has **no native phone capture**; a Feb-2018 Fast-ring build (2018.18011.13438.0) was captured
with a different, desktop-style layout (top command bar with text labels) and Windows Central reported it might be rolled
back. The Oct-2018 production capture does not have that layout, which is consistent with the roll-back.

What V-2015 establishes firmly (HIGH): the status bar is **hidden** in Photos (the page starts at the screen top); a 48-epx
#1F1F1F pivot header with 11-epx-cap all-caps SemiBold items at fixed x; the W10M app bar (48 epx, 68-epx pitch, More 24 epx
from the right, R7 2.1.14) with #1F1F1F on library pages and #171717 in the viewer; a 50-epx #171717 viewer header carrying
the date; the photo fitted to width and centred on the **whole screen** with chrome overlaid; album tiles 60 epx tall filling
two columns; folder tiles square with a 52-epx #333333 label band; the "Edit" sheet (189 epx, 32-epx icon squares).

What is missing: (1) any native V-2016+ viewer, albums, folders or editor capture, so **the editor the phase rules in (Q1 C:
straighten, light and colour, filters, red-eye) has no measured phone form** — V-2015's own editor was only "Crop, Rotate,
Auto-enhance" (filters, light, colour and the rest lived in the separate Lumia Creative Studio app, user guide l.2876-2907);
(2) video trim: one low-resolution video thumbnail only (Y1); (3) **all motion** (no 60-fps source; YouTube video
extraction blocked, as in R8 §0.3).

---

## 0. Method, sources and calibration

### 0.1 Unit convention

`epx` = 1/360 of screen width on a 360-wide canvas, as R8 §0.1 defines it; on the Lumia 950 XL at 350 % the canvas is
411.43 x 731.43 epx and a value that is fixed in epx lands on a different pixel column than on the 950 at 400 % (R8 §0.1).
Scale factors were measured, not assumed: on every native source the nav bar is exactly 48 epx (950: y 2368-2560 = 192 px /
4; XL: y 2392-2560 = 168 px / 3.5; G1: y 2392-2560 = 168 px / 3.5, so G1's 950 XL runs at 350 %) and the app bar and pivot
header land on whole epx at that factor. Every table gives epx on the source's own canvas; "fixed" means equal epx on both
canvases, "proportional" means equal fraction of width.

### 0.2 Sources

| ID | Local file (`docs/plan/r11/src/photos/`) | Pixel size | Device / canvas | App version, date | Build |
|---|---|---|---|---|---|
| **P1** | `gsm950_039.jpg` collection | 1440x2560 | Lumia 950 @400 % → 360x640 | GSMArena 950 review, 2015-12-02 | ≈10586 |
| **P2** | `gsm950_040.jpg` albums | 1440x2560 | as P1 | as P1 | ≈10586 |
| **P3** | `gsm950_041.jpg` folders | 1440x2560 | as P1 | as P1 | ≈10586 |
| **P4** | `gsm950_042.jpg` settings | 1440x2560 | as P1 | as P1 | ≈10586 |
| **P5** | `gsm950_043.jpg` viewer | 1440x2560 | as P1 | as P1 | ≈10586 |
| **P6** | `gsm950_044.jpg` viewer, Edit sheet | 1440x2560 | as P1 | as P1 | ≈10586 |
| **P7** | `gsm950_111.jpg` Rich Capture "best lighting" editor | 1440x2560 | as P1 | as P1 (camera page) | ≈10586 |
| **P8** | `gsm950_112.jpg` viewer with Rich Capture banner | 1440x2560 | as P1 | as P1 | ≈10586 |
| **X1** | `gsm950xl_051.jpg` collection (videos present) | 1440x2560 | Lumia 950 XL @350 % → 411x731 | GSMArena 950 XL review, Dec 2015 | ≈10586 |
| **X2** | `gsm950xl_052.jpg` albums, empty user albums | 1440x2560 | as X1 | as X1 | ≈10586 |
| **X3** | `gsm950xl_054.jpg` folders | 1440x2560 | as X1 | as X1 | ≈10586 |
| **X4** | `gsm950xl_055.jpg` settings | 1440x2560 | as X1 | as X1 | ≈10586 |
| **X5** | `gsm950xl_056.jpg` viewer | 1440x2560 | as X1 | as X1 | ≈10586 |
| **X6** | `gsm950xl_057.jpg` viewer, app bar expanded | 1440x2560 | as X1 | as X1 | ≈10586 |
| **X7** | `gsm950xl_058.jpg` viewer, Edit sheet | 1440x2560 | as X1 | as X1 | ≈10586 |
| **G1** | `commons_photos_950xl_2018-10-30.png` collection | 1440x2560 | Lumia 950 XL @350 % → 411x731 | Wikimedia Commons, uploaded 2018-10-30 (thumbnails render as grey squares, the bug the upload documents) | **final-release era** (Photos after the 2016 redesign; exact app version not shown) |
| **F1** | `twc_photos_18011_2018-02.jpg` two 720x1280 panels (collection, albums) | 1450x1286 | 720p phone @200 % → 360x640 (nav 96 px) | TheWinCentral 2018-02-11, Photos **2018.18011.13438.0**, Fast ring | 15254 Insider; layout not on G1 |
| **F2** | `wc_photos_editor_2018-02_a.jpg`, `_b.jpg` landscape editor + ink | 2048x1152 | 950 XL @350 %, landscape, downscaled 0.8 (nav 132 px = 48 epx → 2.8 px/epx) | Windows Central 2018-02-10, same Fast-ring build | 15254 Insider |
| **F3** | `wl_photos_viewer_2017-07.jpg` | 1141x638 | camera photo of a Lumia 950 in landscape | WindowsLatest 2017-08; photo dated 2017-07-26 on screen | 15063 era, not measurable |
| **Y1** | `yt_UgcGMP3gJ4g_maxres.jpg` | 1280x720 | YouTube still, phone in landscape inside the frame | "Microsoft Photos - Windows 10 Mobile Original App", upload date not read | unknown |
| **D1** | Lumia W10M user guide (R6 D1), text lines cited `ug.txt l.N` | — | — | Issue 1.1, 2016 | 10586-era document |

GSMArena's XL review re-uses two 950 images: its shots 338 / 339 are byte-identical to 950 shots 111 / 112 (md5
`791803f4…`, `158f629d…`), so P7 / P8 have one device only. X1-X7 are genuine XL captures (different content, 4-column grid).
Locale: both GSMArena phones run English (UK) ("Favourite", "30/11"); G1 also uses d/m ("30/10").

### 0.3 Searched without finding

- **Native V-2016+ viewer / albums / folders / editor / trim on a phone:** Windows Central (2015-09-23 update note, 2018-02-10
  Fast-ring article — full-size originals pulled from `cdn.mos.cms.futurecdn.net`), Thurrott 2016-10-06 (desktop images only),
  WindowsLatest 2017-02-18 / 2017-08-04 / 2018-02-10 (410 live; Wayback copies carry a stock image and one camera photo, F3),
  TheWinCentral 2018-02-11 (F1), Wikimedia Commons (category searches "Windows 10 Mobile", "Microsoft Photos", intitle searches
  for Photos / Windows 10 Mobile / Lumia 950 XL — G1 is the only Photos file), Microsoft Store listing via
  `displaycatalog.mp.microsoft.com` (9WZDNCRFJBH4: desktop screenshots only), GSMArena Lumia 650 (no Photos shots) and Lumia 550
  (full-size originals return 404; only 432x768 lightbox copies exist).
- **Video:** `yt-dlp` returns "Sign in to confirm you're not a bot" (tried 2026-09-23 on E6vvrz4ozpE). YouTube search works:
  candidates UgcGMP3gJ4g, T2B-VT8Q6UU (2015 camera footage of an early build), m2bfZqwWlgA. Storyboards need a signed URL; the one
  public Invidious API that returned signed storyboard URLs (invidious.f5.si) failed with a companion error on these IDs, so only
  `maxresdefault.jpg` stills were used (Y1). No frame-accurate motion source exists here.

---

## 1. Values

Confidence: **HIGH** = native resolution, agreeing on two devices / two scales (or two independent native captures);
**MEDIUM** = native resolution on one capture, or a Microsoft document statement; **LOW** = downscaled, camera photo,
Insider-only build, or a single ambiguous reading; **INFERRED** = reasoned from measured values, not read from an image.

### 1.1 Page frame (library pages)

| # | Value | Number | Confidence | Build | Derived from |
|---|---|---|---|---|---|
| 1.1.1 | Status bar | **hidden** in Photos: the pivot header starts at y = 0 | HIGH | V-2015 (P1-P4, X1-X4); V-2016+ (G1) | P1/X1 row 0 is header fill (31,31,31), no status icons; G1 row 0 is page black with the pivot text at 18.6 epx, no status icons |
| 1.1.2 | Pivot header band (V-2015) | **0 → 48.00 epx**, fill **#1F1F1F** (31,31,31) | HIGH | V-2015 | P1 bottom edge y 192 px / 4 = 48.00; X1 y 168 / 3.5 = 48.00; σ 0 |
| 1.1.3 | Pivot header band (V-2016+) | **none** — titles sit directly on the black page | MEDIUM | V-2016+ | G1 rows 0-70 epx sample (0,0,0) outside the glyphs |
| 1.1.4 | Page background | **#000000** | HIGH | both | P1 (1430, 250) = 0; X1; G1 |
| 1.1.5 | App bar | **48 epx**, directly on the nav bar (544 → 592 on the 640 canvas, 635.43 → 683.43 on 731) | HIGH | both | P1 x=20 transition 2176 px; X1 2224 px; G1 2224 px (350 %) |
| 1.1.6 | App bar fill, library pages | **#1F1F1F** (31,31,31) | HIGH | both | P1, X1, G1 bar samples |
| 1.1.7 | Nav bar | 48 epx, black; G1 draws it in the user's accent (0,72,129) — the system "accent nav bar" setting, not Photos | HIGH (height) | both | as 0.1 |

### 1.2 Pivot header items

| # | Value | Number | Confidence | Build | Derived from |
|---|---|---|---|---|---|
| 1.2.1 | Items and order | **COLLECTION · ALBUMS · FOLDERS** (V-2015, all caps); **Collection · Albums · Folders** (V-2016+, mixed case) | HIGH | both | P1-P3, X1-X3; G1 |
| 1.2.2 | V-2015 ink extents (fixed epx) | COLLECTION **12.50 → 110.75**, ALBUMS **136.25 → 202.50**, FOLDERS **228.75 → 297.50** epx | **HIGH** | V-2015 | identical on P1 (400 %) and X1 (350 %) to ±0.3 epx: P1 x 50 / 443 / 545 / 810 / 915 / 1190 px; X1 44 / 388 / 477 / 709 / 800 / 1041 px |
| 1.2.3 | V-2015 cap height / band | cap **11.0 epx** (rows 18.75 → 29.50), ≈15-16-epx SemiBold all caps — the same title class as R8 §1.2 "NOW PLAYING" | HIGH (cap) / MEDIUM (font size) | V-2015 | P1 y 75-118 px; X1 y 65-103 px (11.14) |
| 1.2.4 | V-2015 gap between items (ink to ink) | **25.5 / 26.25 epx** | HIGH | V-2015 | from 1.2.2 |
| 1.2.5 | V-2015 selected / unselected colour | **#FFFFFF** / **#ADADAD** (173,173,173) on #1F1F1F | HIGH | V-2015 | top-30 brightest pixels, P1 and X1 agree to 0.3 |
| 1.2.6 | V-2016+ title size | cap **19.7 epx** ('A' 19.71, 'C' 20.29 with overshoot) → ≈**28-epx** SemiLight | MEDIUM | V-2016+ | G1 x 156-173, y 19.71-39.43 epx |
| 1.2.7 | V-2016+ ink extents | Collection **13.14 → 129.71**, Albums **156.29 → 245.14**, Folders **272.86 → 356.29**; text band 18.57 → 40.00 epx | MEDIUM | V-2016+ | G1 |
| 1.2.8 | V-2016+ selected / unselected | **#FFFFFF** / **#999999** (153) on black — 60 % white | MEDIUM | V-2016+ | G1 top-40 samples |
| 1.2.9 | Underline under the selected pivot | **none** on V-2015 and on G1; the F1 Fast-ring build draws an accent underline | HIGH (none, 3 captures) / LOW (F1) | both | P1, X1, G1 rows below the text are background; F1 left panel y 214-222 px accent bar |

### 1.3 Collection page

| # | Value | Number | Confidence | Build | Derived from |
|---|---|---|---|---|---|
| 1.3.1 | Grouping | by **month**, with a **day** sub-row inside each month ("30/11 … 4 photos" V-2015; "30/10 … 29 items" V-2016+) | HIGH | both | P1, G1 |
| 1.3.2 | V-2015 month header style | system **accent** colour, cap **14.0 epx** → ≈20-epx font, left **12.75 epx** | **HIGH** | V-2015 | P1 'D' 63.75-77.75 / 'N' 410.5-424.25 epx, colour (207,6,128) = that phone's magenta accent; X1 'D' 63.43-77.43, colour (70,110,254) = that phone's blue accent |
| 1.3.3 | V-2015 month header is **sticky** | the current month's header stays at cap top **63.75 epx** and the grid scrolls under it, clipped at **96.0 epx** | HIGH | V-2015 | both P1 and X1 show a part-row (47.75 / 54.86 epx tall) cut exactly at y 96.0 under the pinned header |
| 1.3.4 | V-2016+ month header style | accent (0,120,215 in G1), cap **10.86 epx** → ≈15-epx, band 73.14 → 84.57 epx, left ≈10-12 epx | MEDIUM | V-2016+ | G1 'O' 9.71-30.0 x 73.71-84.57 |
| 1.3.5 | Day sub-row | date left at **11.75-12.0 epx**, count right-aligned to **13.0 epx** (V-2015) / **12.0 epx** (V-2016+) from the right edge; digits cap **11.0** (V-2015) / **10.86** (V-2016+); colour **#A1A1A1** (161) V-2015, **#999999** (153) V-2016+ | MEDIUM | both | P1 y 452.25-466.5; G1 y 105.43-118.29 |
| 1.3.6 | Count wording | "N photos" (V-2015) → "N items" (V-2016+, photos and videos together) | HIGH | both | P1 "4 photos"; G1 "29 items" |
| 1.3.7 | V-2015 vertical rhythm | last thumb bottom → next month cap top **40.75**; month cap bottom → day digits top **28.0**; day digits bottom → thumbs **11.75 epx** | MEDIUM | V-2015 | P1 369.75 / 410.5 / 424.25 / 452.25 / 463.0 / 474.75 epx |
| 1.3.8 | V-2016+ vertical rhythm | month cap bottom → day digits top **21.1**; day digits bottom → thumbs **13.4 epx** (first row at 130.0) | MEDIUM | V-2016+ | G1 84.57 / 105.71 / 116.57 / 130.0 |
| 1.3.9 | Thumbnails | **square**, centre-cropped | HIGH | both | P1, X1, G1 |
| 1.3.10 | V-2015 grid on 360 epx | **3 columns of 111.0 epx**, gutter **2.0**, left **11.0**, right **12.0**; pitch **113.0** both ways | **HIGH** | V-2015 | P1 columns 44-487 / 496-939 / 948-1391 px; rows 145.75 / 258.75 epx |
| 1.3.11 | V-2015 grid on 411 epx | **4 columns of 95.14 epx**, gutter **1.71-2.0**, left **11.14**, right **14.29**; pitch **96.95** | **HIGH** | V-2015 | X1 columns 39 / 378 / 718 / 1057 px, width 333 px |
| 1.3.12 | V-2015 column rule | column count follows width (3 at 360, 4 at 411); gutter fixed at 2 epx; side margins ≈11-14 epx — a minimum thumbnail of ≈95 epx fits both | INFERRED | V-2015 | 1.3.10-1.3.11 |
| 1.3.13 | V-2016+ grid on 411 epx | **3 columns of 126.86-127.14 epx**, gutter **4.0**, left **11.14**, right **11.43**; row pitch **131.14** | MEDIUM | V-2016+ | G1 columns 11.14-138.0 / 142.0-269.14 / 273.14-400.0 epx; rows 130.0 / 261.14 / 392.29 / 523.43 |
| 1.3.14 | Video tile overlay | a centred **disc ≈34-36 epx**, black at ≈55-62 % opacity, with a **white outline play triangle 12.9 x 18 epx**, stroke ≈2 epx, sitting right of centre | MEDIUM (V-2015, X1 only) / MEDIUM (V-2016+, G1: disc 36.0, triangle 12.86 x 18.29) | both | X1 gridded zoom of tile at 718/1215 px; G1 tile 273-400 x 392-519 epx centre-row profile |

### 1.4 Albums page (V-2015)

| # | Value | Number | Confidence | Build | Derived from |
|---|---|---|---|---|---|
| 1.4.1 | System album tiles | **60 epx tall**, two columns filling the width with **12-epx** margins and a **12-epx** gutter: **162 epx** wide at 360, **187.1-187.7** at 411 — width proportional, height fixed | **HIGH** | V-2015 | P2 x 12-174 / 186-348, y 60-120 and 132-192; X2 x 12-199.14 / 211.14-398.3, y 60-120 |
| 1.4.2 | Tile content | the album's newest photo, darkened, full-bleed; label white **15-epx SemiBold** (cap 10.75), left **24.75-25.0 epx** (12.75 inside the tile), vertically centred (cap centre 91.1 in a 60-120 tile) | HIGH | V-2015 | P2 'C' 24.75 / 85.75-96.5; X2 'C' 24.86 / 85.71-96.57 |
| 1.4.3 | Screenshots tile with no image | flat **#141414** (20,20,20) | MEDIUM | V-2015 | P2 (100-170, 170-190 epx) |
| 1.4.4 | Separator between system and user albums | 1-epx line **#333333** at y **215.0-215.75**, full content width | MEDIUM | V-2015 | P2 row scan x 20-340 epx |
| 1.4.5 | User album tile | photo **162 epx** wide (x 12-174, top 239.0) with two lines **below** it: title white **15-epx** (cap 10.5, left 12.25, cap top 422.75) and date **#A1A1A1** 12-epx (digits 8.5, cap top 441.5) | MEDIUM | V-2015 | P2 lines at 422.25-433.5 and 441.5-451.5 epx |
| 1.4.6 | Empty user-album state | icon + "Enjoy your albums here" (≈20-epx) + one centred paragraph | MEDIUM | V-2015 | X2 |
| 1.4.7 | App bar | **Add (+)** at 82 epx from the right, More at 24 | HIGH | V-2015 | P2, X2 (glyph 20 x 20 at R 82.00 on both) |

### 1.5 Folders page (V-2015)

| # | Value | Number | Confidence | Build | Derived from |
|---|---|---|---|---|---|
| 1.5.1 | Folder tiles | **square**, two columns: **162 x 162** at 360 (x 11-173 / 186-348), **187.1 x 187.1** at 411 (x 11.14-198.29 / 210.0-397.43); first row top **67.1-67.5 epx** | **HIGH** | V-2015 | P3, X3 |
| 1.5.2 | Label band | **52 epx** at the tile bottom, **#333333** (51,51,51), over the image | HIGH | V-2015 | P3 177.5-229.5; X3 203.43-254.29 |
| 1.5.3 | Label lines | line 1 folder name white **15-epx** (cap 10.5), line 2 location white ≈12-13-epx ("This Device", "SD Card (D:)", or "OneDrive" with a cloud glyph); text left ≈11-12 epx inside the tile | MEDIUM (±1.5 epx between devices) | V-2015 | P3 'P' 190.5-201.0, line 2 209.0-218.0; X3 'P' 214.86-225.71 |
| 1.5.4 | App bar | **Sync** at 82 epx from the right, More at 24 | HIGH | V-2015 | P3, X3 |

### 1.6 Viewer

| # | Value | Number | Confidence | Build | Derived from |
|---|---|---|---|---|---|
| 1.6.1 | Chrome visible at rest | a top **date header** and the bottom **app bar**, both over the photo; status bar hidden; nav bar drawn | HIGH | V-2015 | P5, X5, P8 |
| 1.6.2 | Date header | **0 → 50.00 epx**, fill **#171717** (23,23,23) — 2 epx taller than the 48-epx pivot header | **HIGH** | V-2015 | P5 edge at y 200 px / 4; X5 y 175 / 3.5 |
| 1.6.3 | Date text | long date, white, **15-epx** (cap 10.5-10.86), left **24.25-25.4 epx**, cap band 18.75 → 29.0 epx | HIGH | V-2015 | P5 'T' 24.25 / 18.75-29.0; X5 'F' 25.43 / 18.86-29.43 |
| 1.6.4 | Photo fit | fitted to the screen **width** and centred on the **whole screen** (not on the space between the bars); chrome overlays it | **HIGH** | V-2015 | P5 photo 185.0-455.0 → centre 320.0 = 640 / 2; X5 210.86-520.0 → 365.43 = 731.43 / 2 |
| 1.6.5 | Viewer app bar | 48 epx, **#171717** | HIGH | V-2015 | P5, X5 |
| 1.6.6 | Viewer glyph set and positions | **Share · Favourite (heart) · Edit (pencil) · Delete · More** at **286 / 218 / 150 / 82 / 24 epx from the right**, glyph box 20 x 20, centre 24 below the bar top | **HIGH** | V-2015 | P5 cx 74 / 142 / 210 / 277.4 / 336 on 360; X5 cx 125.43 / 193.43 / 261.43 / 328.7 / 387.43 on 411 — same distances from the right |
| 1.6.7 | Expanded app bar ("•••") | bar grows **up by 12 epx to 60 epx**, fill **#1F1F1F**; glyph row stays **24 epx** below the new top; labels 12-epx (cap **8.57**) centred under each glyph, label cap centre **46.1 epx** below the bar top | MEDIUM | V-2015 | X6 bar top 623.43, glyph cy 647.43, label caps 665.43-673.71 |
| 1.6.8 | Expanded labels | "Share", "Favourite", "Edit", "Delete" | MEDIUM | V-2015 | X6 |
| 1.6.9 | Overflow menu | panel **#2B2B2B** (43,43,43) directly above the expanded bar, **155.1 epx** tall for three items + a separator; items 15-epx white (cap 10.57), text left **12.86 epx**, first two items at **44.1-epx** pitch, first item centre **28.6 epx** below the panel top; separator **1.14 epx** (4 px) #818181 from 12.0 to 399.4 epx, then the third item | MEDIUM | V-2015 | X6 panel 468.29 → 623.43; "Slideshow" 491.14-502.57, "Set as" 535.43-546.57, rule 567.43-568.57, "File information" 588.0-599.71 |
| 1.6.10 | Menu items | **Slideshow, Set as, —, File information** | MEDIUM | V-2015 | X6 |
| 1.6.11 | Same metrics as R7 | the 44-epx menu pitch and ≈13-14-epx text inset equal R7 2.1.15 (Messaging's "…" menu: 44.0 / 14.3) | MEDIUM | V-2015 | cross-reference |

### 1.7 Editing (V-2015 inbox form)

| # | Value | Number | Confidence | Build | Derived from |
|---|---|---|---|---|---|
| 1.7.1 | Edit entry | the viewer's **Edit** opens a bottom sheet listing editors, not an editor | HIGH | V-2015 | P6, X7 |
| 1.7.2 | Edit sheet geometry | **189 epx** tall, bottom-anchored on the nav bar, fill **#1F1F1F**; three rows: 32 x 32-epx icon square at x **12-44**, label white 15-epx at x **57.25**; icon tops at sheet top **+15 / +80 / +142**; a 1-epx rule at **+64**; **15 epx** under the last icon | **HIGH** | V-2015 | P6 sheet 403 → 592, icons 418 / 483 / 545; X7 494.3 → 683.43, icons 509.43 / 574.57 / 636.57 — same offsets on both scales |
| 1.7.3 | Edit sheet rows | "Lumia Creative Studio" (icon in that app's blue #0063B1), rule, "**Crop, Rotate, Auto-enhance**" (icon in accent), "Find more editors" (Store icon in accent) | HIGH | V-2015 | P6, X7 |
| 1.7.4 | Inbox editor scope | "quick edits, such as rotate and crop … Or use auto-enhance"; filters, reframe, enhance sliders, blur and colour pop were **Lumia Creative Studio**, a separate app | MEDIUM | 10586 doc | D1 l.2876-2907 |
| 1.7.5 | Rich Capture "best lighting" editor | black page, no header; photo centred on the screen; slider track **60.0 → 300.0 epx** (240 long, 60 insets), **3 epx** thick, filled part **#5C2FC8** (92,47,200) — not the user accent — unfilled **#292929**; thumb white disc **15 epx** at cy **524 epx** (68 above the nav bar top); app bar transparent with **Accept (✓)** at 82 and More at 24 from the right | MEDIUM | V-2015 | P7 rows 516.5-531.5, track runs 60.0-300.0 at y 523-524 |
| 1.7.6 | Rich Capture banner in the viewer | 60 x 60-epx accent square icon at x 12-72, 12 epx above the app bar; label "Choose the best lighting" ≈20-epx white at x 85 | MEDIUM | V-2015 | P8 square 472-532, text 497.25-516.75 |

### 1.8 Editing and ink (V-2016+ as seen on the Feb-2018 Fast-ring build)

| # | Value | Number | Confidence | Build | Derived from |
|---|---|---|---|---|---|
| 1.8.1 | Editor form (landscape) | photo on the left; an editing panel **≈320 epx** wide on the right, fill **#171717**; status bar on the left edge, nav bar on the right | LOW | 18011 Insider | F2 panel x 363.6-684.3 epx at 2.8 px/epx |
| 1.8.2 | Panel contents top → bottom | "**Crop and rotate**" tile (#404040, ≈288 x 96 epx, 24-epx glyph over a 15-epx label); tabs "**Enhance** · **Adjust**" (15-epx), selected tab with a **2.5-epx accent underline** spanning half the panel; "Choose a filter"; filter thumbnails ≈**88 epx** square, 3 across with ≈4-epx gaps, the selected one outlined in accent; "**Undo all**" and "**Save**" side by side (#454545, **60 epx** tall, disabled until an edit); "**Save a copy**" full width, **accent fill #0078D7**, 60 epx | LOW | 18011 Insider | F2 `_b` |
| 1.8.3 | Photo zoom controls under the photo | fit / − / + glyphs | LOW | 18011 Insider | F2 |
| 1.8.4 | Ink toolbar | a top strip of pen / pencil / highlighter / eraser / touch-writing glyphs and a close X over the photo | LOW | 18011 Insider | F2 `_a` |
| 1.8.5 | "Adjust" tab contents (light, colour, clarity, vignette, red eye, spot fix — the desktop 2016 editor's set) | **not captured on a phone** | UNMEASURED | — | see UNMEASURED-2 |

### 1.9 Settings page (V-2015)

The page is R3 C1's settings form; only what differs or is new is listed.

| # | Value | Number | Confidence | Build | Derived from |
|---|---|---|---|---|---|
| 1.9.1 | Header | "SETTINGS" in the pivot-header style (48-epx #1F1F1F band, cap 11.0, left 12.5 epx) | HIGH | V-2015 | P4 18.75-29.75 / X4 18.57-29.71, x 12.5 / 12.57 |
| 1.9.2 | Group titles | "Viewing and editing", "Tile", "Sources" — ≈20-epx, white, left 12.0 | HIGH | V-2015 | P4 / X4 identical epx (56.25 / 244.25 / 383.0 on P4) |
| 1.9.3 | Description text | 15-epx, **#9D9D9D** (157), 20-epx line pitch (R3 A15) | HIGH | V-2015 | P4 / X4 lines at 116.75 / 137.0 / 157.0 epx |
| 1.9.4 | Toggle | 43.75 x 20 epx, state label 56.75 epx right of the toggle's left edge (R3 C1: 44 x 20, 56) | HIGH | V-2015 | P4 182-202 x 12-55.75, "On" at 68.75 |
| 1.9.5 | Combo box | **32 epx** tall, x 12 → 311 (299 wide) at 360 epx | MEDIUM | V-2015 | P4 308-340 |
| 1.9.6 | Source rows | device glyph + name, **44-epx** pitch ("This Device", "SD Card (D:)") | HIGH | V-2015 | P4 428 / 472; X4 408 / 452 |

### 1.10 The Feb-2018 Fast-ring layout (recorded so it is not mistaken for the production form)

| # | Value | Number | Confidence | Build | Derived from |
|---|---|---|---|---|---|
| 1.10.1 | Status bar | **shown** (24 epx) | LOW | 18011 Insider | F1 rows 0-48 px at 2 px/epx |
| 1.10.2 | Top command bar | **48 epx** (24 → 72), #1F1F1F, glyph + text label buttons "Refresh", "Select", "Import", "•••" | LOW | 18011 Insider | F1 rows 48-144 px |
| 1.10.3 | Pivot | mixed-case titles with an **accent underline** under the selected one; thumbnail-size toggles (1 / 4 / 9 squares) at the right | LOW | 18011 Insider | F1 |
| 1.10.4 | Collection layout | justified rows of mixed-aspect thumbnails (not squares); month "March, 2017" in accent, day row "3/15  2 photos" | LOW | 18011 Insider | F1 |
| 1.10.5 | Status | this layout is **not** on the Oct-2018 production capture G1 (no status bar, bottom app bar, no underline, squares); Windows Central said the Fast-ring update "may have been made in error" and could be rolled back | MEDIUM | — | G1 vs F1; WC 2018-02-10 |

### 1.11 Video trim

| # | Value | Number | Confidence | Build | Derived from |
|---|---|---|---|---|---|
| 1.11.1 | Trim screen form | landscape video with a large centred time readout ("01:17.44", ≈mm:ss.cc) and one horizontal track with **two handles** (an accent ring and a filled yellow disc) | LOW | unknown | Y1 (still inside a 16:9 frame, ≈2.5 px per epx lost) |
| 1.11.2 | Geometry, colours, handle sizes, save flow | not measurable | UNMEASURED | — | UNMEASURED-3 |

---

## 2. Strings as shipped

V-2015 (en-GB phones, 10586): "COLLECTION", "ALBUMS", "FOLDERS", "SETTINGS"; month "December 2015"; day row "30/11" … "4
photos"; albums "Camera roll", "Saved pictures", "Screenshots", user album title + "30/11/2015", empty state "Enjoy your albums
here" / "See albums from OneDrive, or select + to create a new album."; folder tiles "Pictures" / "This Device", "Pictures" /
"SD Card (D:)", "OneDrive"; viewer date "Tuesday, 1 December 2015"; expanded labels "Share", "Favourite", "Edit", "Delete";
menu "Slideshow", "Set as", "File information"; Edit sheet "Lumia Creative Studio", "Crop, Rotate, Auto-enhance", "Find more
editors"; Rich Capture "Choose the best lighting"; settings "Viewing and editing", "Linked duplicates", "Exact duplicate files
are shown as a single file. This might include copies backed up to OneDrive and RAW files.", "On", "Tile", "Choose what to
show on the Photos tile.", "Recent photos", "Sources", "This Device", "SD Card (D:)", "Show my cloud-only content from
OneDrive", "Off", "Collection view includes OneDrive content from", "Pictures folder only", "Manage OneDrive upload settings".

V-2016+ production (G1): "Collection", "Albums", "Folders", "October 2018", "30/10", "29 items".

Feb-2018 Fast ring (F1, F2; LOW): "Refresh", "Select", "Import", "March, 2017", "3/15", "2 photos", "Create an album", "Sort
by:", "Newest"; editor "Crop and rotate", "Enhance", "Adjust", "Choose a filter", "Undo all", "Save", "Save a copy". July 2017
viewer (F3, camera photo): "Share", "Zoom", "Draw", "Rotate", date "Wednesday, July 26, 2017".

User guide (en-US, D1): "You can do quick edits, such as rotate and crop, to the photos you have taken. Or use auto-enhance to
let your phone fix your photo with just one simple tap." (l.2877-2878); "If you have downloaded photo editing apps from Store,
they are listed here as editing options." (l.2881-2882).

The shell's strings for its own states (empty / denied / partial access, phase 17 task 4) have no W10M original; W10M's only
empty string captured here is the albums one above.

## 3. Segoe MDL2 glyphs the shell's icon font needs

Codepoints from Microsoft's Segoe MDL2 Assets list (learn.microsoft.com/en-us/windows/apps/design/iconography/segoe-ui-symbol-font,
read 2026-09-23). Glyph identity read from the native captures.

| Use | Glyph name | Codepoint | Seen in |
|---|---|---|---|
| Collection / Folders refresh | Sync | E895 | P1, P3, G1 (two circular arrows) |
| Select | MultiSelect | E762 | P1, G1 |
| Slideshow (V-2016+ collection bar) | Slideshow | E786 | G1 |
| Overflow | More | E712 | all |
| New album | Add | E710 | P2 |
| Share | Share | E72D | P5 |
| Favourite | Heart / HeartFill | EB51 / EB52 | P5 (outline) |
| Edit | Edit | E70F | P5 |
| Delete | Delete | E74D | P5 |
| Accept an edit | CheckMark | E73E | P7 |
| Settings sources | CellPhone, SDCard | E8EA, E7F1 | P4 |
| Video tile | Play | E768 (outline triangle, drawn inside the disc) | X1, G1 |
| Editor (V-2016+) | Crop, Rotate, Undo, Save, SaveCopy, Filter, Color, Brightness, RedEye, InkingTool, EraseTool, ZoomIn, Zoom | E7A8, E7AD, E7A7, E74E, EA35, E71C, E790, E706, E7B3, E76D, E75C, E8A3, E71E | F2 (LOW); set completed from the MDL2 list for Q1 C's tools |
| Trim | Trim | E78A | Q1 C (no capture of the glyph in use) |
| Set as | SetlockScreen, SetTile | E7B5, E97B | phase 17 task 5 targets (no capture of W10M's own glyphs; "Set as" was a text menu item) |

## 4. Motion (RV11)

No 60-fps source of Photos exists in reach (§0.3), so every motion value is UNMEASURED; the proposals below are tagged
approximations derived from measured W10M patterns.

| Motion | Status | Proposed approximation | Derived from |
|---|---|---|---|
| Collection → viewer open, viewer → collection close | UNMEASURED | page fade-in from black, ease-out **200-317 ms** | R7 3.2.2 (Cortana pages, 14393 / 15063, HIGH) |
| Photo swipe (next / previous) | UNMEASURED | finger-tracked 1:1, release settles in **≈290 ms** | R7 4.1.2 / 4.1.4 (finger-tracked panel with a fixed settle, the only measured W10M drag-settle) |
| Viewer chrome hide / show on tap | UNMEASURED | cut, no animation | R7 1.8.2 / 2.6.2 (W10M in-app state changes captured as one-frame cuts) |
| App bar "•••" expand | UNMEASURED for Photos | **317 ms** strong ease-out, 47 % on the first frame | R7 2.1.16 (Messaging's identical app bar, 14393, MEDIUM) |
| Pivot swipe Collection ↔ Albums ↔ Folders | UNMEASURED | the shell's existing pivot settle, **250 ms** (X13) | phase 10 MusicMetrics / phase 17 Y6 |
| Slideshow step | UNMEASURED | **5 s** per photo with a cross-fade of **367 ms** | R3 A7 cycle-tile cross-fade (15063, MEDIUM) for the fade; the interval is phase 17 Y6's agent pick |
| Edit sheet rise | UNMEASURED | slide up with R7 2.1.16's curve | as above |

## UNMEASURED

| # | What | Why not | Proposed tagged approximation (and its source) | What would settle it |
|---|---|---|---|---|
| **1** | V-2016+ viewer, albums, folders and settings on a phone | no native capture exists in any source reached (§0.3) | build them on the V-2015 measured geometry (1.4-1.6, 1.9) with the V-2016+ type ramp from G1 (28-epx mixed-case pivot titles, 15-epx accent month header, #999999 secondary text) — one consistent app, NEEDS-HUMAN accept | a native 14393+ phone screenshot of the viewer and albums |
| **2** | The Q1 C editor on a phone (straighten, light, colour, filters, red-eye) | V-2015's inbox editor had only crop / rotate / auto-enhance (1.7.4); the only phone capture of the Enhance / Adjust editor is the Fast-ring F2, landscape and downscaled | the F2 panel structure (1.8.2) for portrait: tool tiles and Enhance / Adjust tabs in a bottom panel, 60-epx action buttons, accent "Save a copy"; sliders as R3 C1 / R6 §3.4.2; tagged P4 where F2 shows nothing | a native portrait capture of Photos' editor on 14393+ |
| **3** | Video trim geometry | one still inside a 16:9 frame (Y1) | a timeline under the video with two handles: R8 §1.5's scrubber metrics (3-epx track, 18-epx hollow ring thumb) for the handles, the time readout in R3's large-number style; "Save a copy" as 1.8.2 | a screen recording of Photos' trim on W10M |
| **4** | All motion | no 60-fps source | §4 | a Project My Screen recording of Photos |
| **5** | Light theme | every source is dark | follow the system theme with R3 / R7 light-page colours | a light-theme capture |
| **6** | The V-2016+ collection app bar in the expanded state and its menu items | G1 shows the closed bar only | V-2015 1.6.7-1.6.9 metrics; items from phase 17's own scope (Select all, Settings) | a capture with "•••" open |
| **7** | "Set as" sub-menu and File information page | not captured | R7 2.1.15 menu metrics; file information as an R3 C1 settings-style page | captures of both |
| **8** | Pinch-zoom limits and double-tap zoom level | static images only | fit → 1:1 pixel on double-tap (agent pick) | video |

## Gaps for the phase doc

- **Y1 (collection, grid, album tiles):** measured. V-2015: 3 x 111-epx squares at 360 epx with 2-epx gutters and 11 / 12-epx
  margins, 4 columns at 411; V-2016+ (G1): 3 x 127-epx squares with 4-epx gutters at 411. Phase 17's stand-in "a 4-across square
  grid with 2-epx gutters" matches V-2015 on the 950 XL only. **On the S25U canvas (360 epx wide by Q11) V-2015 gives 3
  columns of 111 epx (measured on the 950)**; V-2016+ at 360 epx is not captured (3 x ≈110 epx if it keeps three columns,
  INFERRED). Pick the version (see next point). Album tiles: 1.4 (V-2015 only). App-list rows are not W10M's Photos form.
- **Version ruling needed (NEEDS-HUMAN, like R8's H-M1):** V-2015 (fully measured, caps pivots, grey header band, 20-epx
  month header) vs V-2016+ (the final release's app; only its collection page is measured). Agent lean: **V-2016+ for the
  collection** (it is the governing build and G1 is native) and **V-2015 geometry for the pages G1 does not show** (UNMEASURED-1),
  recorded as an approximation.
- **Y2 (viewer chrome, glyph set):** measured (V-2015): 50-epx #171717 date header, #171717 app bar, Share / Favourite / Edit /
  Delete / More at 286 / 218 / 150 / 82 / 24 epx from the right, overflow menu Slideshow / Set as / File information. Phase 17's
  stand-in (48-epx bar) is right for the bar, wrong for the header (50 epx). The glyphs are MDL2 codepoints (§3), not
  substitutes, if the branding module carries Segoe MDL2 (A10).
- **Y7 (bars):** measured — **status bar hidden in every Photos page, nav bar drawn** (1.1.1). This confirms phase 17's
  candidate for the viewer and extends it to the library pages; the phase doc's "status bar 28 epx (R3 C4)" line does not
  apply to Photos.
- **Y11 (editor tool UI):** LOW only (1.8). Phase 17's stand-in (48-epx bottom tool strip with R6 sliders) conflicts with the
  one capture, which uses a panel of large tiles, tabs, filter thumbnails and 60-epx action buttons with an accent "Save a copy".
  Q1 C's tool list is broader than V-2015's inbox editor (1.7.4) — W10M users got those tools from Lumia Creative Studio or the
  2016+ app, so the editor is partly P4 either way.
- **Y6 (motion):** UNMEASURED; §4 gives tagged approximations. Phase 17's 250-ms opens and 5-s slideshow stand-ins can stay as
  approximations; the viewer open is better modelled on R7 3.2.2's 200-317-ms fade than on the pivot settle.
- **Grid count on the target:** the 360-epx canvas on the S25U gives 3 columns in V-2015 (measured) and very likely in V-2016+
  (it shows 3 even at 411 epx); nothing measured supports 4 at 360.
- **Strings:** use en-US forms ("Favorite", m/d dates) in the shell's default locale; the captures are en-GB.

## Tally

Counted by script from the Confidence column of every row in §1 (a row with two levels counts at the lower): **HIGH 37 ·
MEDIUM 26 · LOW 10 · UNMEASURED 2 · INFERRED 1** (76 rows), plus 8 items in the UNMEASURED table and 7 motion values in §4, all
UNMEASURED with tagged approximations.

## Sources

Provenance (originals unaltered; the `src/` folder is gitignored, third-party images stay local):

| Local file | URL | Fetched | Resolution | sha256 |
|---|---|---|---|---|
| `docs/plan/r11/src/photos/gsm950_039.jpg` | https://st.gsmarena.com/imgroot/reviews/15/microsoft-lumia-950/shots/gsmarena_039.jpg | 2026-09-23 | 1440x2560 | `2e420b1aee3e022f544d279aa4b8c2390aaf6e2858b121c5eb5d442edae1fe87` |
| `docs/plan/r11/src/photos/gsm950_040.jpg` | https://st.gsmarena.com/imgroot/reviews/15/microsoft-lumia-950/shots/gsmarena_040.jpg | 2026-09-23 | 1440x2560 | `8f0574431b618f836a7f35ef4c68d025b0ff489a16699dc88a90e12d255e15e4` |
| `docs/plan/r11/src/photos/gsm950_041.jpg` | https://st.gsmarena.com/imgroot/reviews/15/microsoft-lumia-950/shots/gsmarena_041.jpg | 2026-09-23 | 1440x2560 | `4efbc0f7da2b2b5eb4dd58f61090b6f4a263a0214f9f5636679e1d37e59cbf9b` |
| `docs/plan/r11/src/photos/gsm950_042.jpg` | https://st.gsmarena.com/imgroot/reviews/15/microsoft-lumia-950/shots/gsmarena_042.jpg | 2026-09-23 | 1440x2560 | `5aaa699914ee6ad3336998991cabfea327b2b60ccb7bb02278222a82be53a015` |
| `docs/plan/r11/src/photos/gsm950_043.jpg` | https://st.gsmarena.com/imgroot/reviews/15/microsoft-lumia-950/shots/gsmarena_043.jpg | 2026-09-23 | 1440x2560 | `2400ec528777cbf45fa2459814df8adc8902202d14dfae47e19da6f0b766c378` |
| `docs/plan/r11/src/photos/gsm950_044.jpg` | https://st.gsmarena.com/imgroot/reviews/15/microsoft-lumia-950/shots/gsmarena_044.jpg | 2026-09-23 | 1440x2560 | `aaf1f5cbb29722cbc5982733b088b39487d1634c746e2a6fb6a302741795e7c8` |
| `docs/plan/r11/src/photos/gsm950_111.jpg` | https://st.gsmarena.com/imgroot/reviews/15/microsoft-lumia-950/shots/gsmarena_111.jpg | 2026-09-23 | 1440x2560 | `32bb3d1880455ae9170dd7ff1ba96368f4a2e65c72d2de200784aa7e3e421b0a` |
| `docs/plan/r11/src/photos/gsm950_112.jpg` | https://st.gsmarena.com/imgroot/reviews/15/microsoft-lumia-950/shots/gsmarena_112.jpg | 2026-09-23 | 1440x2560 | `dbe3bb5f7f00cf828f1a5f18ae6f50af55e5fdae0245dbf1a9f4b0b9befadf65` |
| `docs/plan/r11/src/photos/gsm950xl_051.jpg` | https://fdn.gsmarena.com/imgroot/reviews/15/microsoft-lumia-950-xl/shots/gsmarena_051.jpg | 2026-09-23 | 1440x2560 | `7a820b8fcac625c4e975715321c0c9ee2dfbb655579bb1fd0d4b50f86538b5dc` |
| `docs/plan/r11/src/photos/gsm950xl_052.jpg` | https://fdn.gsmarena.com/imgroot/reviews/15/microsoft-lumia-950-xl/shots/gsmarena_052.jpg | 2026-09-23 | 1440x2560 | `a866c55a5649f20f92882e7e9740da997b6d59d2dfce162131cfd96e592b6119` |
| `docs/plan/r11/src/photos/gsm950xl_054.jpg` | https://fdn.gsmarena.com/imgroot/reviews/15/microsoft-lumia-950-xl/shots/gsmarena_054.jpg | 2026-09-23 | 1440x2560 | `123a0b954c6271b5b4028cd4d090c8d966d8be6eb14c63c296cdaec9085b3d9b` |
| `docs/plan/r11/src/photos/gsm950xl_055.jpg` | https://fdn.gsmarena.com/imgroot/reviews/15/microsoft-lumia-950-xl/shots/gsmarena_055.jpg | 2026-09-23 | 1440x2560 | `8b99b0e8070132499e6a9ea46f05726a0bb21ef472d48eff3b1044cec8db1f41` |
| `docs/plan/r11/src/photos/gsm950xl_056.jpg` | https://fdn.gsmarena.com/imgroot/reviews/15/microsoft-lumia-950-xl/shots/gsmarena_056.jpg | 2026-09-23 | 1440x2560 | `a10fe78c812d22281bc794c3de559507ae63393e304ed03977dac1dd7c44b79e` |
| `docs/plan/r11/src/photos/gsm950xl_057.jpg` | https://fdn.gsmarena.com/imgroot/reviews/15/microsoft-lumia-950-xl/shots/gsmarena_057.jpg | 2026-09-23 | 1440x2560 | `340d896e5ed15f9347fc839bcf6bd5e1f33116f50a7fc5074514303695d4a2eb` |
| `docs/plan/r11/src/photos/gsm950xl_058.jpg` | https://fdn.gsmarena.com/imgroot/reviews/15/microsoft-lumia-950-xl/shots/gsmarena_058.jpg | 2026-09-23 | 1440x2560 | `7d5b68ddde756caa543d416e3b79b00adab8c5a69112d5180ba8118a1a7181e4` |
| `docs/plan/r11/src/photos/commons_photos_950xl_2018-10-30.png` | https://upload.wikimedia.org/wikipedia/commons/4/46/Microsoft_Photos_with_display_issued_on_a_Microsoft_Lumia_950_XL_%2830-10-2018%29.png (Commons file page "Microsoft Photos with display issued on a Microsoft Lumia 950 XL (30-10-2018).png", public domain, uploader Donald Trung) | 2026-09-23 | 1440x2560 | `1e97da938bc399513d1a2fad10e9366373133eb4e4d88b45430e594e9e4618e3` |
| `docs/plan/r11/src/photos/twc_photos_18011_2018-02.jpg` | https://thewincentral.com/wp-content/uploads/2018/02/Microsoft-Photos-Windows-10-Mobile.jpg | 2026-09-23 | 1450x1286 | `a4a6ea0473b766c9a19ba91d6552b1e5c466edd6232f0af2d72806eb4120b828` |
| `docs/plan/r11/src/photos/wc_photos_editor_2018-02_a.jpg` | https://cdn.mos.cms.futurecdn.net/imqa7dgjyGCz5s82ynS3jB.jpg | 2026-09-23 | 2048x1152 | `9ff8f736c029be4d05734f16b72313c7df9a1ba1e6ef1f52da2c2ba651699351` |
| `docs/plan/r11/src/photos/wc_photos_editor_2018-02_b.jpg` | https://cdn.mos.cms.futurecdn.net/kwsz76fLSBfx3HuohCcDf6.jpg | 2026-09-23 | 2048x1152 | `d948bf8e3a546476d540f25f4524cba60c24ec5398418cf47d4cb658868f91a5` |
| `docs/plan/r11/src/photos/wl_photos_viewer_2017-07.jpg` | https://www.windowslatest.com/wp-content/uploads/2017/08/Microsoft-Photos-app-for-Windows-10-Mobile.jpg | 2026-09-23 | 1141x638 | `347d3b41672672e7c4e97e0c4a3592ee26f217a9c2bc1039af2259396cf61686` |
| `docs/plan/r11/src/photos/yt_UgcGMP3gJ4g_maxres.jpg` | https://i.ytimg.com/vi/UgcGMP3gJ4g/maxresdefault.jpg | 2026-09-23 | 1280x720 | `ef9ab2261f6df256324ac7f3cb96ba233fe55bc57fb035a8a70a374ae1c06e00` |

Articles and documents that date or version the sources:
- https://www.gsmarena.com/microsoft_lumia_950-review-1347p7.php — GSMArena Lumia 950 review, multimedia page (2015-12-02), P1-P6; camera page p8 for P7-P8
- https://www.gsmarena.com/microsoft_lumia_950_xl-review-1349p7.php — GSMArena Lumia 950 XL review, gallery page, X1-X7
- https://www.thurrott.com/windows/windows-10/82746/microsoft-reworking-photos-app-windows-10 — 2016-10-06, the redesign "is also available on Windows 10 Mobile"
- https://web.archive.org/web/2017id_/https://www.windowslatest.com/2017/02/18/microsoft-photos-app-windows-10-windows-10-mobile-updated-production-ring/ — Photos 17.202.10012.0 to production for PC and Mobile ("New Share Icon", "New Edit Icon")
- https://www.windowscentral.com/photos-app-windows-10-mobile-being-updated-more-editing-features — 2018-02-10, 18011.13438.0 on the Fast ring, "possible that this update was made in error"; source of F2
- https://thewincentral.com/windows-10-mobile-photos-app-gets-redesigned-windows-10-ui-lots-bugs/ — 2018-02-11, source of F1
- https://www.windowslatest.com/2018/02/10/microsoft-photos-app-windows-10-mobile-getting-huge-update-insiders/ — same build; filters, ink, "Select and Settings options" moved to the top
- Lumia with Windows 10 Mobile User Guide, Issue 1.1 (R6 D1; local text copy in R6's scratchpad)
- https://learn.microsoft.com/en-us/windows/apps/design/iconography/segoe-ui-symbol-font — MDL2 codepoints (§3)

Evidence root (scratchpad, this session): `r11pcmf/` — `img/` downloads, `scripts/` (`m.py` helpers, `lines.py` text-line
finder, `appbar.py` glyph centroids, `arcs.py`, `gridcrop.py` gridded zooms, `prov.py` provenance), `sheets/` contact sheets
and zooms. Every value can be re-derived from a fresh download of the URLs above.
