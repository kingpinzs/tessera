# R11 §Movies & TV — Movies & TV (Windows 10 Mobile) measurements

Research section of R11 (`docs/plan/r11-inbox-apps.md`) for phase 17 (`phase-17-inbox-photos-camera-video.md`), the
video half: the shell's Movies & TV hub. Scope measured: the app frame, the ≡ pane, the local **Videos** library page and
the **player**. The store half (Films / TV / Explore / Purchased) is gone and is out of R11's measurement scope; it is
recorded as a **look only** (§1.5, §1.7), because phase 17's Browse pivot (Y8, a P4 design) fills that role with an
online catalogue and can borrow it. Format and rigour follow `r8-groove-measurements.md`; Groove's shared chrome is cited
from R8, not re-derived.

## Summary — what was and was not established

The 10586-era app ("Films & TV" on the en-GB phones GSMArena reviewed, "Movies & TV" in en-US) was measured at native
resolution on a Lumia 950 (400 %) and a Lumia 950 XL (350 %), which separates fixed-epx from width-proportional values the
way R8 did. Its **chrome is Groove's exactly** (R8 §1.1–1.2): a 24-epx status bar and 48-epx header in one seamless
#171717 band, ≡ at 24 epx, the ALL-CAPS title at 60.25 epx, search at W − 24. Everything measured is **fixed in epx** —
nothing on these screens scales with width. The **≡ pane** is 256 epx wide, overlays the page with **no scrim at all**,
has 48-epx rows (glyph at 24, label at 48) and marks the current page with an accent label + glyph and a **4 × 48-epx accent
bar at the left edge**; a bottom-anchored group (account, Settings, Shop for more) sits under a 1-epx #404040 rule. The
**Videos page is a wrap grid of fixed 112 × 112-epx tiles on a 124-epx pitch** with a two-line, clipped caption: two
columns on a 360-epx canvas, three on 411, and the leftover width is left empty, not stretched. The **player** hides the
status bar and header, keeps the nav bar, and puts a flat 120-epx black scrim (≈60 %) at the bottom carrying a 2-epx track
inset 12 epx, an **accent-coloured hollow ring thumb (24 epx)**, time labels **below** the track in HH:MM:SS, and a
**six-button row on a fixed 48-epx pitch centred on W/2**: cast · aspect (crop to fill) · pause/play · CC · full screen
(hide nav bar) · repeat. There is **no previous/next, no ±10 s and no "•••"** on the 10586 player. The CC button opens a
subtitle flyout centred above it.

Three things could not be established. (1) **The governing build.** Every native capture is Dec 2015 (≈10586). The app was
redesigned in 2017 (Insider in March, then production): the pages became **Explore / Purchased / Personal**, the pane went
**acrylic** (May 2017 Insider), and the player gained **skip back 10 / skip forward 30** with cast, zoom and repeat moved
into a "•••" menu. Those 2017 facts come only from downscaled composites and a YouTube thumbnail (LOW); no native 14393 /
15063 / 15254 phone capture was found. (2) **Motion** — no 60-fps source; yt-dlp is blocked and the one storyboard route
went down mid-session. (3) The **played-portion colour** of the 10586 track and whether the right time label is total or
remaining (the only capture is at 0:00:03 with a flyout over the right label).

---

## 0. Method, sources and calibration

### 0.1 Unit convention

`epx` is the project's unit, 1/360 of screen width, as in R8 §0.1: the Lumia 950 runs 1440 × 2560 at **400 %** (360 × 640
epx, 4.0 px/epx) and the Lumia 950 XL runs the same panel at **350 %** (411.43 × 731.43 epx, 3.5 px/epx). A value that lands
on the same epx on both devices is fixed in epx; one that lands on the same pixel column is proportional. The scale factors
are R8's measured ones and are confirmed again here: the chrome ends at 288 px on the 950 and 252 px on the XL, both
**72.00 epx**, and the ≡ glyph is 80 px vs 70 px wide (20.0 epx both).

Downscaled sources are converted with a stated ruler: the March-2017 composite at **1.185 px/epx** (each phone panel
426.67 px = 360 epx; checked by its chrome ending at 85 px = 71.7 epx and its title at 60.7 epx, both matching 10586), and
the May-2017 pane image at **1.5 px/epx** (≡ glyph 30 px = 20 epx; accent bar 72 px = 48 epx; a 720p phone at 225 % =
320-epx canvas shown at 2/3 size). Both are LOW for geometry.

Text sizes: Segoe UI cap height ≈ 0.70 em, as R3 / R8 used, so a measured cap of 10.5 epx is a 15-epx font. JPEG bloom
inflates caps by ≈0.25–0.5 epx at 400 %.

### 0.2 Sources

| ID | Image | Pixel size | Device / canvas | App version, date | Build | Local file |
|---|---|---|---|---|---|---|
| **V1** | https://st.gsmarena.com/imgroot/reviews/15/microsoft-lumia-950/shots/gsmarena_054.jpg | 1440×2560 | Lumia 950 @400 % → 360×640 | GSMArena Lumia 950 review, **Dec 2015** — TV page (store, nothing bought) | ≈10586 | `docs/plan/r11/src/movies-tv/gsm950_054.jpg` |
| **V2** | …/microsoft-lumia-950/shots/gsmarena_055.jpg | 1440×2560 | as V1 | as V1 — ≡ pane open, Videos selected | ≈10586 | `…/gsm950_055.jpg` |
| **V3** | …/microsoft-lumia-950/shots/gsmarena_056.jpg | 1440×2560 | as V1 | as V1 — VIDEOS page | ≈10586 | `…/gsm950_056.jpg` |
| **V4** | …/microsoft-lumia-950/shots/gsmarena_059.jpg | 1440×2560 | as V1 | as V1 — player, subtitle flyout open | ≈10586 | `…/gsm950_059.jpg` |
| **X1** | https://fdn.gsmarena.com/imgroot/reviews/15/microsoft-lumia-950-xl/shots/gsmarena_067.jpg | 1440×2560 | Lumia 950 XL @350 % → 411×731 | GSMArena Lumia 950 XL review, **Dec 2015** — VIDEOS page | ≈10586 | `…/gsm950xl_067.jpg` |
| **X2** | …/microsoft-lumia-950-xl/shots/gsmarena_068.jpg | 1440×2560 | as X1 | as X1 — ≡ pane open over the VIDEOS page | ≈10586 | `…/gsm950xl_068.jpg` |
| (X3) | …/microsoft-lumia-950-xl/shots/gsmarena_302.jpg | 1440×2560 | — | XL review's player shot | — | **not a second source**: byte-identical to V4 (same sha256 `0b0c01db…`); GSMArena re-used the 950 capture. Not copied |
| **N1** | https://www.windowslatest.com/wp-content/uploads/2017/03/movies-and-tv-1.jpg | 1280×759 | three phone panels, each 426.67 px = 360 epx (1.185 px/epx) | Neowin capture (watermark), published by Windows Latest **2017-03-08** — EXPLORE / PURCHASED / PERSONAL, "rolling out to Insiders" | 2017-03 Insider (15xxx era) | `…/wl2017-03_movies-and-tv-1.jpg` |
| **N2** | https://www.windowslatest.com/wp-content/uploads/2017/05/fluent-movies.jpg | 960×402 | two phone panels (dark / light), 1.5 px/epx, 320-epx canvas | Windows Latest **2017-05-13**, "Movies & TV app updated with Fluent Design on Windows 10 Mobile" (Insider) | 2017-05 Insider | `…/wl2017-05_fluent-movies.jpg` |
| **N3** | https://i.ytimg.com/vi/MsuoI3Pex0c/maxresdefault.jpg | 1280×720 | YouTube thumbnail: phone player in landscape + phone Store listing | "Movies & Tv, Groove music and Windows store got new features for Windows 10 Mobile", channel "Windows Phone" (≈2017; 360° video support dates it to the Creators Update era) | ≈15063 era | `…/yt_MsuoI3Pex0c_maxres.jpg` |
| **N4** | https://www.windowslatest.com/wp-content/uploads/2017/06/movies-TV-for-windows-10-mobile-e1496309434921.jpg | 1200×741 | camera photo of a Lumia 930, oblique | Windows Latest **2017-06-01** article image (photo date unknown) — TV title page | unknown | `…/wl2017-06_movies-tv-mobile.jpg` |
| **D1** | https://cdn.mos.cms.futurecdn.net/WsMzvLWpaNgfqsb9XpwF8f.jpeg | 2048×757 | **desktop** window (same UWP app) | Windows Central, "How to use the Movies & TV app in Windows 10 Creators Update", **2017-04-11** — Settings page | 15063 desktop | `…/wc2017-04_settings_desktop.jpeg` |
| **D2** | https://cdn.mos.cms.futurecdn.net/9CtE732cpMqcvW2oe8hpBG.jpeg | 2048×776 | desktop | as D1 — Explore tab | 15063 desktop | `…/wc2017-04_explore_desktop.jpeg` |
| **UG** | Microsoft, *User Guide — Lumia with Windows 10 Mobile*, Issue 1.1 EN-US (R6 D1) | — | — | © 2016, "Play a video", text lines 3273–3293, PDF p. 100 | 10586-era document | R6's local copy (`r6-measurements.md` §0.2 D1) |

### 0.3 What was searched without finding

- **A native 14393 / 15063 / 15254 phone capture of the Videos page or the player.** Windows Latest's 2016–2017 Movies & TV
  articles (2016-12, 2017-03-08, 2017-05-13, 2017-06-01, 2017-12-01; the site deleted them, read through the Wayback
  Machine) carry only the composites N1 / N2, a camera photo (N4), a stock phone photo and desktop screenshots. Neowin's
  2017-03 and 2019-02 articles (Cloudflare-blocked live; Wayback copies show no article-body phone image). Thurrott's
  2017-03-08 "Fun UI Refresh" (images refused to a non-browser client). Windows Central 2017-04-11 (desktop only: D1, D2).
- **Microsoft Store listing phone screenshots.** The archived en-US listing (Wayback 2016-10-14 and 2017-01-05) and today's
  catalogue (`displaycatalog.mp.microsoft.com`, product 9WZDNCRFJ3P2) hold one **desktop** image only. N3 shows the phone
  Store listing had a "Screenshots — Mobile" strip in 2017 (a 3-column Videos grid), but those images are not in any
  archive reached.
- **Video.** `yt-dlp` fails with "Sign in to confirm you're not a bot" (as R8 §0.3). Storyboards: signed storyboard URLs
  from `invidious.f5.si /api/v1/videos/<id>` worked earlier in the session for another video, then the instance returned
  "Error while communicating with Invidious companion" for every request (checked twice); other public instances refused
  (403) or were down. Only static YouTube thumbnails (`maxresdefault`, `hq1`–`hq3`) were fetched: for MsuoI3Pex0c (N3),
  wdjhbQmMrWg ("Windows 10 Mobile Films & TV Official video", Nokiapoweruser — thumbnails are animation stills, no UI) and
  JudC10YX3P8 (Creators Update, desktop). No video stream was downloaded.
- **The 10586 Films page** (film posters), the **title page** on 10586, and the player with the flyout closed.

---

## 1. Values

Confidence: **HIGH** = native resolution and agreeing across two independent screenshots or two device scales;
**MEDIUM** = native on one screenshot, a Microsoft document statement, or sources agreeing within ±1 epx; **LOW** =
downscaled, camera photo, thumbnail, or a single ambiguous reading. "INFERRED" marks reasoning, not measurement; it is
kept inside a row's note and never raises the row's level. Build column: 10586 = the Dec 2015 GSMArena captures.

### 1.1 Page frame (library pages)

| # | Value | Number | Confidence | Build | Derived from |
|---|---|---|---|---|---|
| 1.1.1 | Status bar | **shown**, **24 epx**, fill **#171717** (23,23,23), same as the header — one seamless 72-epx band | HIGH | 10586 | V1–V3, X1–X2: fill (23,23,23) at y 2–20 epx and 30–40 epx; = R8 §1.1 |
| 1.1.2 | App header | **48 epx** (24 → 72 epx), #171717 | HIGH | 10586 | chrome bottom at **288 px (950) = 252 px (XL) = 72.00 epx**; = R8 §1.1 |
| 1.1.3 | Page background | **#000000** | HIGH | 10586 | V1, V3, X1 at x 300+ epx, y 80 epx |
| 1.1.4 | Nav bar | **48 epx**, #000000; top at **592.0 epx (950) / 683.43 epx (XL)** | HIGH | 10586 | V2 / X2: the pane fill ends at 2368 px / 2392 px; = R8 §1.1 |
| 1.1.5 | Bottom app bar on the library pages | **none** — black from the content to the nav bar | HIGH | 10586 | V1, V3, X1 |

### 1.2 Header row

| # | Value | Number | Confidence | Build | Derived from |
|---|---|---|---|---|---|
| 1.2.1 | Order | **≡ · TITLE · search**; nothing else | HIGH | 10586 | V1, V3, X1 |
| 1.2.2 | ≡ glyph | centre **x 24.00**, cy 48.6 epx, **20.0 × 11.25 epx** (XL 20.0 × 10.86) | HIGH | 10586 | V1 x 56–135 px; X1 x 49–118 px; identical to R8 §1.2 |
| 1.2.3 | Title | left **60.25 epx** both scales, ALL CAPS, bold-weight look, cap **11.0 epx** ("VIDEOS" V) / 10.5 ("TV" T), cy **49.0** | HIGH (position, cap) / MEDIUM (weight, visual) | 10586 | V1, V3 x 241 px; X1 x 211 px; = R8 §1.2 ("NOW PLAYING" 61.0, cap 11.0) |
| 1.2.4 | Title while the pane is open | **hidden** — the header row shows only ≡ and search | HIGH | 10586 | V2, X2 header text runs: only x 14–34 and 327–345 (950), 14–34 and 378–397 (XL) |
| 1.2.5 | Search glyph | centre **x = W − 24** (336.00 on 950, 387.57 on XL), cy 48.0, **18.0 × 18.0 epx** | HIGH | 10586 | V1 x 1308–1379 px; X1 x 1325–1387 px |
| 1.2.6 | Fixed or proportional | **fixed epx** — every header value lands on the same epx at 400 % and 350 % | HIGH | 10586 | rows above |
| 1.2.7 | Glyph / text colour | white (253–255) | HIGH | 10586 | V1, X1 |

### 1.3 The ≡ pane

| # | Value | Number | Confidence | Build | Derived from |
|---|---|---|---|---|---|
| 1.3.1 | Kind | **overlay**: the page is neither pushed nor resized; the page pixels right of the pane are unchanged | HIGH | 10586 | X1 vs X2 at x 280–360, y 130–220 epx: mean abs difference **0.0**, caption white 255 in both |
| 1.3.2 | Scrim over the page | **none** (no dimming, no tint) | HIGH | 10586 | as 1.3.1; V2's right strip is the black page, (0,0,0) |
| 1.3.3 | Width | **256.0 epx** on both scales | HIGH | 10586 | V2 edge at 1024 px; X2 at 896 px |
| 1.3.4 | Vertical extent | from the chrome bottom (**72 epx**) to the nav bar top; the header stays above it, and because both are #171717 there is no visible seam | HIGH | 10586 | V2, X2 column profiles |
| 1.3.5 | Fill | **#171717** (23,23,23) | HIGH | 10586 | V2, X2 at x 150–200, y 300–320 epx |
| 1.3.6 | Top group | three rows, **48-epx pitch from 72 epx**: glyph centres at **95.5 / 143.5 / 192.0 epx** | HIGH | 10586 | V2, X2 (identical to ±0.07) |
| 1.3.7 | Row glyph | centre **x 24.0**; boxes 16.0 × 11.0 (Films), 15.0 × 11.0 (TV), 16.0 × 8.0 (Videos) | HIGH | 10586 | V2, X2 |
| 1.3.8 | Row label | origin **x 48** (ink 48.0–49.25), white | HIGH | 10586 | V2, X2 |
| 1.3.9 | Row label type | cap **10.5 epx** ("TV" T) → **15 epx** | HIGH (cap) / MEDIUM (font size) | 10586 | V2 y 138.0–148.25; X2 10.86 |
| 1.3.10 | Current page | label **and** glyph in the **accent colour**; a **4-epx accent bar** at x 0 (3.75 / 3.71 measured) spanning the full **48-epx row** (168 → 216 epx); **no row fill tint** | HIGH | 10586 | V2 bar x 0–15 px, accent (192,0,119); X2 x 0–13 px, (62,101,255); row fill at x 200 epx = (23,23,23) in both. Two different user accents, same treatment |
| 1.3.11 | Separator above the bottom group | **1 epx**, **#404040** (64,64,64), x **12 → 244** (12-epx insets), at **nav top − 159** | HIGH | 10586 | V2 y 433.0 (= 592 − 159.0); X2 y 524.29 (= 683.43 − 159.14) |
| 1.3.12 | Bottom group | **bottom-anchored**, 48-epx pitch: account row centre **nav − 128** (20-epx circular avatar at cx 24), **Settings** centre **nav − 80** (16-epx glyph), **Shop for more** centre **nav − 32** (13.5 × 15-epx bag glyph); labels at x 48.5–48.9 | HIGH | 10586 | V2 and X2 give the same offsets from the nav top to ±0.14 on a 640- and a 731-epx canvas |
| 1.3.13 | Consistency with other measured panes | Cortana's ≡ pane is 256 epx with 48-epx rows (R7 §3.1); MSN Weather's 253 epx (R3 C5, LOW) | — (cross-reference) | — | R7, R3 |

### 1.4 VIDEOS page (the local library)

| # | Value | Number | Confidence | Build | Derived from |
|---|---|---|---|---|---|
| 1.4.1 | Group header | "**Videos**" in the **accent colour**, x **12.0**, cap **10.5–10.86 epx** → 15 epx, SemiBold look; cap top **91.7 epx** (19.7 below the chrome) | HIGH (position, cap) / MEDIUM (weight, visual) | 10586 | V3 x 48 px; X1 x 42 px |
| 1.4.2 | Tile | **112.0 × 112.0 epx** square | HIGH | 10586 | X1 x 42–434 px, y 420–812 px; V3 row-2 tile 341.0–453.0 epx, x 12–124 |
| 1.4.3 | Horizontal pitch | **124.0 epx** (12-epx gutter); first tile left **12.0** | HIGH | 10586 | X1 tiles at 12 / 136 / 260 epx; V3 at 12 / 136 |
| 1.4.4 | Columns | **as many fixed 112-epx tiles as fit inside 12-epx margins**: **2** at 360 epx (a third would end at 372 > 348), **3** at 411 epx (ends at 372 ≤ 399.4); the grid is not stretched and the remainder is left empty | HIGH | 10586 | same epx tile and pitch on both scales = fixed epx, not proportional |
| 1.4.5 | First row top | **120.0 epx** (chrome + 48) | HIGH | 10586 | X1 direct; V3 from the letterboxed thumbnail centred at 175.4 |
| 1.4.6 | Row pitch | **221 epx** (tile 112 + caption block 109) | MEDIUM | 10586 | V3 rows at 120 and 341 (X1 has one row) |
| 1.4.7 | Thumbnail fit | the frame is **fitted inside the square** (letterboxed, black bars) when it is not square; other tiles show full-square frames | MEDIUM | 10586 | V3 row 1: image 125.5–225.25 inside the 120–232 tile; X1 and V3 row 2 full squares (the system thumbnails differ in shape) |
| 1.4.8 | Caption text | the **file name without extension** | MEDIUM | 10586 | X1 "Hall Pass 720p Xvid" vs the subtitle file "Hall Pass 720p Xvid.srt" in V4; "Ke$ha_-_We_R_Who_W…" |
| 1.4.9 | Caption type and place | **15 epx** (cap 10.5 / 10.57), white, origin x 12 (ink 12.3–13.4), **max 2 lines**, line pitch **20.0**, first baseline **tile bottom + 24.5** | HIGH | 10586 | V3 baselines 256.5 / 276.5 and 477.5 (row 2); X1 256.29 / 276.29 |
| 1.4.10 | Caption overflow | wraps to 2 lines, then **clipped with no ellipsis** at **tile left + 100 epx** (12 inside the tile's right edge) | HIGH | 10586 | V3 "down Trailer [Fu" cut at 112.0; X1 "XVID 720p sam" cut at 236.0 |
| 1.4.11 | Per-tile metadata | none — **no duration, date or size** | HIGH | 10586 | V3, X1 |
| 1.4.12 | Tile placeholder fill | none (black) | HIGH | 10586 | V3 letterbox bars (0,0,0) |

### 1.5 TV page, store half (look only — Browse pivot reference)

| # | Value | Number | Confidence | Build | Derived from |
|---|---|---|---|---|---|
| 1.5.1 | Empty-purchases intro | 2 lines, cap **14.75 epx** → **≈20 epx** (Subtitle class), line pitch **24.0**, x 12, cap top 89.5 | MEDIUM | 10586 | V1 y 89.25–133.0 |
| 1.5.2 | Section row | "**In Store**" white SemiBold look, cap 10.5, x 12, cap top 155.75; "**Show all**" in the accent colour, right-aligned to **346.75** (12-epx margin + side bearing), cap ≈ 9.5–10 | MEDIUM | 10586 | V1 |
| 1.5.3 | Artwork strip | **square 112 × 112 tiles** (TV box art is 1:1), pitch 124, x 12, top **181.25** (section cap top + 25.5); the strip is **clipped at x 348** (third tile cut to 88 epx) — a horizontally scrolling row | MEDIUM | 10586 | V1 |
| 1.5.4 | Artwork caption | 15 epx (cap 10.75), white, 2 lines, first baseline **artwork bottom + 22.0**, pitch 20 | MEDIUM | 10586 | V1 baseline 315.25 |
| 1.5.5 | Film posters (2:3) on 10586 | **UNMEASURED** (Films page not captured) | UNMEASURED | — | see UNMEASURED-4 |

### 1.6 Player

| # | Value | Number | Confidence | Build | Derived from |
|---|---|---|---|---|---|
| 1.6.1 | Bars | **status bar hidden, no app header**; video from y 0; **nav bar shown** (48 epx, black) | MEDIUM | 10586 | V4 top rows are video (55,82,135); nav top 592 |
| 1.6.2 | Nav bar hiding | a player button hides it ("to hide it and make the video wider, tap [↗↙]. To show the navigation bar again, tap [↙↗]"), on phones that allow it | MEDIUM | doc 10586 | UG l.3284–3287 |
| 1.6.3 | Video fit shown | a 16:9 video **fills 360 × 592 epx** (cropped to fill) in portrait — the "full screen" crop state the aspect button toggles | MEDIUM | 10586 | V4; UG l.3281–3283 "If the video is in a different aspect ratio than the phone's screen, it is cropped to fit the screen" |
| 1.6.4 | Control scrim | a **flat band 120 epx tall** from **nav − 120** (472.0) to the nav top; hard top edge, no gradient | MEDIUM | 10586 | V4 x = 4 px: step at y 1888 px |
| 1.6.5 | Scrim strength | black at **≈60 %** (luminance 123.8 → 49.6 across the edge, transmission ≈0.40) | LOW | 10586 | V4 y 471 vs 472 epx; estimated across a continuous cloud texture |
| 1.6.6 | Scrubber track | **2.0 epx** thick, centre **nav − 93** (499.0), x **12 → 348** (12-epx insets); unplayed colour grey, luminance ≈126 over the scrim | MEDIUM | 10586 | V4 rows 1992–1999 px; right end 1392 px |
| 1.6.7 | Played portion colour | **UNMEASURED** on 10586 (0:00:03 of 1:31:31 plays ≈0 px) | UNMEASURED | — | see UNMEASURED-3 |
| 1.6.8 | Thumb | **hollow ring in the accent colour** (192,0,119), outer **Ø 24.0 epx**, stroke ≈**2.25 epx**, video visible through the centre; at t ≈ 0 its centre is at x **24.12** = track left + radius (R8 §1.5's travel rule) | MEDIUM | 10586 | V4 bbox x 12.0–36.0, y 487.0–510.75 |
| 1.6.9 | vs Groove | **differs** from R8 §1.5 (white ring Ø 18, 3-epx track, labels on the track line): Movies & TV's thumb is accent and larger, the track thinner, the labels below | MEDIUM | 10586 | V4 vs R8 §1.5 |
| 1.6.10 | Time labels | **below the track**, elapsed left-aligned at **12.5**, right label ending at **345.5**; digits cap **8.75 epx** (Caption 12, = R8's digit cap); digit band 517.0–525.75 (baseline nav − 66.25); format **HH:MM:SS** with a two-digit hour ("00:00:03", "01:31:31") | MEDIUM | 10586 | V4 |
| 1.6.11 | Right label meaning | **UNMEASURED** (one frame; the flyout covers it) | UNMEASURED | — | see UNMEASURED-5 |
| 1.6.12 | Transport row | **six glyphs on a fixed 48-epx pitch**, centres **60 / 108 / 156 / 204 / 252 / 300 epx** (the group centred on W/2 = 180), row centre **nav − 40** (552.0), glyph box 20 epx | MEDIUM | 10586 | V4 glyph bboxes (pause bars at 152.9 and 159.1 → 156.0). INFERRED fixed epx (48 = the app-bar button width); only one scale exists |
| 1.6.13 | Transport order | **cast to device · aspect (crop to fill) · pause / play · closed captions · full screen (hide nav bar) · repeat (loop)** — **no previous / next, no skip ±10 s, no "•••"** | MEDIUM | 10586 | V4 glyphs; meanings from UG l.3277–3289 ("Tap ‖ or ▷", "Watch a video in full screen: Tap [aspect]", "Hide the navigation bar: tap [↗↙]", "Loop your video: Tap [↻]"); cast from the 2017 menu wording (N3) |
| 1.6.14 | Glyph style | thin outline glyphs, white | MEDIUM | 10586 | V4 |
| 1.6.15 | Seek | "Fast-forward or rewind: Drag the slider left or right." | MEDIUM | doc 10586 | UG l.3279–3280 |
| 1.6.16 | Subtitle flyout | opens **above the CC button, centred on it** (flyout centre x 203.9 vs CC 204.0); rectangle x 83.0–324.75 (**241.75 wide**), y 446.0–523.75 (**77.75 tall** for one item), bottom **20.75 above** the CC glyph top; fill **#2B2B2B** (43,43,43); **1-epx border #767676** (118,118,118); item text 15 epx (cap 10.5), white, left 97.75 (14.75 inside the border), vertically centred | MEDIUM | 10586 | V4 border rows 1784–1787 / 2092–2095 px, columns 332–335 / 1296–1299 px |
| 1.6.17 | Subtitle source | a subtitle **file from the video's own folder** ("choose a file (from the current video folder only)") | MEDIUM | 10586 | GSMArena Lumia 950 review p. 7 text; V4 item "1. Hall Pass 720p Xvid.srt" |
| 1.6.18 | Where the accent appears | the **thumb ring only** on the 10586 player | MEDIUM | 10586 | V4 |

### 1.7 The 2017 redesign (governing-era structure; LOW)

| # | Value | Number | Confidence | Build | Derived from |
|---|---|---|---|---|---|
| 1.7.1 | Pages | the ≡ pane lists **Explore / Purchased / Personal** (replacing Films / TV / Videos); page titles EXPLORE / PURCHASED / PERSONAL | LOW | 2017-03 Insider, then 2017 production | N1, N2 |
| 1.7.2 | Header | unchanged: ≡ · ALL-CAPS title · search, #171717, chrome ≈72 epx (85 px), title left 60.7–61.6, cap ≈11.0 | LOW | 2017-03 | N1 at 1.185 px/epx |
| 1.7.3 | Personal (= the old Videos page) empty state | three lines ≈20 epx at x 11 ("Personal videos from your PC appear here — things you didn't buy or rent in Store."), then two 15-epx lines ("Connect your phone to your PC, then copy files into your phone's Videos folder."), then an (i) glyph with "Learn more" / accent "Learn how" | LOW | 2017-03 | N1 panel 3 |
| 1.7.4 | Purchased | "Refine:" with accent values "Most recent, All, All"; a 2-column poster grid, posters ≈**112 × 157 epx** (≈2:3), pitch ≈120.7 × 228.6, caption = title (2 lines) + year; content inset ≈29.5 epx | LOW | 2017-03 | N1 panel 2 |
| 1.7.5 | Explore | a hero carousel ≈202 epx tall with page dots, category ("Movie"), title ("Passengers + Bonus") and an accent "Buy from $14.99" button; a 2 × 2 grid of W/2 × ≈39-epx buttons with glyphs (Trailers / 360° videos / Movies / TV) on 1-epx grey rules; a "Trailers" section with "Show all" (grid glyph) over 2-up 2:3 tiles | LOW | 2017-03 | N1 panel 1 |
| 1.7.6 | Acrylic pane | the pane became **acrylic** (dark and light themes), starts right under the status bar and **covers the header** (≡ drawn inside it); width ≈256 epx (384 px), accent bar 4 × 48 epx, label x ≈50.7, row pitch ≈51 epx (48 on 10586; may be a scale artefact) | LOW | 2017-05 Insider | N2 at 1.5 px/epx |
| 1.7.7 | 2017 player transport | **captions glyph (left) · skip back 10 · play · skip forward 30 · "•••" (right)** | LOW | ≈15063 era | N3 (landscape) |
| 1.7.8 | 2017 player "•••" menu | "Cast to device", "Zoom to fill", "Repeat", "Autoplay", "Play as 360° video" (check), "Tilt to move" (check) — cast, zoom and repeat moved here from the row | LOW | ≈15063 era | N3 |
| 1.7.9 | 2017 scrubber | played portion **in the accent colour**, accent hollow ring thumb, time labels below the track ("00:00:10" / "00:01:46") | LOW | ≈15063 era | N3 |
| 1.7.10 | Title page (store) | box art left; title ("Heroes Reborn, Season 1"), network, "2015 • Drama • 0 episodes"; "Download season" with a download glyph; description with "More"; an accent "Episodes I own" drop-down; "Episodes" rows (glyph, title, "8/9/2015 • 3 min") | LOW | unknown (photo) | N4 |

---

## 2. Strings as shipped

en-GB phone, 10586 (V1–V4): app **"Films & TV"**; pane **"Films"**, **"TV"**, **"Videos"**, the account's display name,
**"Settings"**, **"Shop for more"**; page titles **"TV"**, **"VIDEOS"**; group header **"Videos"**; TV page **"This is where
you'll see TV programmes you buy in Store."**, **"In Store"**, **"Show all"**; subtitle flyout item **"1. <file name>.srt"**;
time labels **"00:00:03"**, **"01:31:31"**.

en-US, UG (10586-era): app **"Movies & TV"**; "1. Tap Movies & TV > [≡] > Videos. 2. Tap the video you want to play."
(l.3275–3276); "Pause or resume playback — Tap [‖] or [▷]." (l.3277–3278); "Fast-forward or rewind — Drag the slider left
or right." (l.3279–3280); "Watch a video in full screen — Tap [aspect]. If the video is in a different aspect ratio than
the phone's screen, it is cropped to fit the screen." (l.3281–3283); "Hide the navigation bar during playback — If it's
possible to hide the navigation bar in your phone, to hide it and make the video wider, tap [↗↙]. To show the navigation bar
again, tap [↙↗]." (l.3284–3287); "Loop your video — Tap [↻]." (l.3288–3289); "Watch more movies — You can also buy or rent
movies and television shows directly from your phone. Tap Store > Movies & TV …" (l.3290–3293).

2017 phone (N1, N3; LOW): **"Explore"**, **"Purchased"**, **"Personal"**; "Movie", "Buy from $14.99", **"Trailers"**,
**"360° videos"**, **"Movies"**, **"TV"**, **"Show all"**; **"Refine:"** "Most recent, All, All"; **"Personal videos from your
PC appear here — things you didn't buy or rent in Store."**, **"Connect your phone to your PC, then copy files into your
phone's Videos folder."**, **"Learn more"**, **"Learn how"**; player menu **"Cast to device"**, **"Zoom to fill"**,
**"Repeat"**, **"Autoplay"**, **"Play as 360° video"**, **"Tilt to move"**.

2017 Settings page (D1, desktop window of the same UWP app; LOW for the phone): **"Settings"**; **"Download quality"** —
"HD", "SD", "Ask every time"; **"Download location"** — "…age settings" (partly covered); "Show my downloads", "Remove this
device", "Learn more" (under a covered heading); "…ailable video purchases" (partly covered), **"Choose where we look for
videos"**; **"Playback"** — **"Always start videos in full screen"** toggle, "Off"; **"Mode"** — "Light", …; **"Account"** —
"View account", "Payment options", "Order history"; **"App"** — "Help", "Feedback", "About", "What's new".

## 3. Segoe MDL2 glyphs the shell's icon font needs

Codepoints read from Microsoft's "Segoe MDL2 Assets icons" page
(learn.microsoft.com/windows/apps/design/style/segoe-ui-symbol-font, ms.date 2025-09-02, fetched 2026-09-23). The name →
codepoint pairs are Microsoft's; which glyph each W10M control used is matched visually (confidence in the last column).

| Use | Name | Codepoint | Match |
|---|---|---|---|
| ≡ | GlobalNavigationButton | E700 | HIGH (= R8) |
| Search | Search | E721 | HIGH (= R8) |
| Pane: Films / Movies (film strip) | Movies | E8B2 | MEDIUM |
| Pane: TV | TVMonitor | E7F4 | MEDIUM |
| Pane: Videos / Personal (camcorder) | Video | E714 | MEDIUM |
| Pane: Settings | Setting | E713 | MEDIUM |
| Pane: Shop for more (bag) | Shop | E719 | MEDIUM |
| Pane: Explore (2017, compass) | no exact name; nearest MapCompassTop | E812 | LOW |
| Play / Pause | Play / Pause | E768 / E769 | HIGH |
| Aspect (crop to fill) | AspectRatio | E799 | MEDIUM |
| Closed captions | CC | E7F0 | MEDIUM |
| Full screen / back to window (nav bar hide / show) | FullScreen / BackToWindow | E740 / E73F | MEDIUM |
| Repeat (loop) | RepeatAll | E8EE | MEDIUM |
| Cast to device | no "Cast" name on the page; nearest MiracastLogoSmall | EC15 | LOW |
| 2017: captions (speech-bubble glyph) | Subtitles | ED1E | LOW |
| 2017: skip back 10 / skip forward 30 | SkipBack10 / SkipForward30 | ED3C / ED3D | MEDIUM (names match the drawn "10" / "30") |
| 2017: "•••" | More | E712 | HIGH |
| 2017: Learn more (i) | Info | E946 | MEDIUM |
| 2017: menu check | CheckMark | E73E | MEDIUM |
| Title page: Download season | Download | E896 | MEDIUM |
| 360° video (2017) | Video360 | F131 | LOW (menu item has no glyph; for a Browse badge if wanted) |

## 4. Motion (RV11)

No 60-fps source exists for any Movies & TV screen (§0.3), so every motion value is UNMEASURED and carries a proposed
tagged approximation from a pattern this build has already measured:

| Motion | Status | Proposed approximation | From |
|---|---|---|---|
| ≡ pane open / close | UNMEASURED | slide in from the left over **133 ms**, out over 133 ms, no scrim fade (none exists, 1.3.2) | R3 C5 (MSN Weather pane, 4 frames at 30 fps, LOW) |
| Page change from the pane (Films → Videos …) | UNMEASURED | pane slides out 133 ms, then the page **fades in, ease-out, 200–317 ms**, first frame ≈50 % | R3 C5 + R7 §3.2.2 (Cortana page fade-in, HIGH) |
| Library → player | UNMEASURED | the same fade-in; the player's controls appear with the page | R7 §3.2.2 |
| Player controls show / auto-hide | UNMEASURED | fade **200 ms** ease-out in; hide after the phase's **3 s** agent pick (Y6) | R7 §3.2.2 curve; phase 17 Y6 |
| Subtitle flyout open / close | UNMEASURED | rise with R7 §2.1.16's ease-out (47 % first frame, 90 % at 100 ms, settled ≤ 317 ms), scaled to the flyout's 77.75-epx height | R7 §2.1.16 (Messaging app-bar expand, MEDIUM) |
| Thumb drag / seek | UNMEASURED | the thumb follows the finger 1:1, seek on release | R8 UNMEASURED-5 carries the same gap; phase 10 built Groove's |
| Track change / artwork | not applicable (single video) | — | — |

## UNMEASURED

| # | What | Why not | Proposed tagged approximation (and the measured pattern it comes from) |
|---|---|---|---|
| **1** | **The Videos page and player on the governing build (14393 / 15063 / 15254) at native resolution.** The 2017 facts (1.7) are LOW. | no native late-build phone capture found (§0.3) | Build the **10586 geometry (1.2–1.6, HIGH/MEDIUM)** and take the 2017 changes as an owner choice (see Gaps G1), as R8's H-M1 did for Groove's two versions |
| **2** | **All motion** (§4) | no 60-fps source | §4's table (R3 C5, R7 §3.2.2, R7 §2.1.16) |
| **3** | **Played-portion colour of the 10586 track** | 0:00:03 of 1:31:31 plays ≈0 px | **accent**, from the 2017 player (1.7.9, LOW) and consistent with the accent thumb (1.6.8); Groove's neutral white (R8 §1.5) is the counter-pattern and is not used because this app's thumb is already accent |
| **4** | **Film posters and the Films page on 10586**; the 10586 title page | not captured | 2:3 posters **112 epx wide** on the 124-epx pitch of 1.4.3 (tile width = the measured square), height 168 epx; the 2017 Purchased grid (1.7.4, LOW ≈112 × 157) agrees in width |
| **5** | **Right time label = total or remaining** | one frame, flyout over it | **total duration**, from Groove (R8 §1.5, HIGH: the right label held while the elapsed advanced) |
| **6** | **Light-theme rendering** | every capture is dark (N2's light pane is the only light image, LOW) | the dark values; a light theme is not in phase 17's scope |
| **7** | **Player with no flyout, and the controls' idle state** (whether the scrim and controls hide by themselves) | V4 is mid-interaction | controls and scrim always shown while paused; auto-hide per phase 17 Y6 (3 s, H4) |
| **8** | **What the cast button did on the phone** | no capture of it pressed | Android's own cast route (phase 17 decides; W10M's was Miracast / DLNA) |
| **9** | **The ≡ pane's row pitch in the 2017 acrylic version** (≈51 vs 48) | N2 is a 2/3 downscale | **48 epx** (1.3.6, HIGH) |
| **10** | **Aspect button's other states** (letterbox ↔ fill) and portrait / landscape rotation of the player | one frame | fill ↔ fit toggle on AspectRatio (E799); the player rotates with the device (N3 shows landscape) |

---

## Gaps for the phase doc

- **G1 — version choice (new NEEDS-HUMAN, accept).** The 10586 app (Films / TV / Videos; six-button player) is measured
  at native resolution; the 2017 app (Explore / Purchased / Personal; −10 / play / +30 with a "•••" menu; acrylic pane)
  is only LOW. Phase 17 needs one: agent call to put to Jeremy — **the 10586 geometry for everything measured, plus the
  2017 skip buttons** (they are the governing-era behaviour and exactly what Y5 wanted), with the 2017 "•••" menu holding
  cast / zoom to fill / repeat. Record as an H row like phase 10's H-M1.
- **Y5 (player transport and scrubber; My videos rows) — replace the stand-ins.** Y5 says "phase 10's now-playing scrubber
  (R8 §1.5 thumb and track) with play / pause, ±10 s, fullscreen": the thumb and track are **not** Groove's — use 1.6.6–1.6.10
  (2-epx track inset 12, accent ring Ø 24, labels below in HH:MM:SS). The 10586 row is 1.6.12–1.6.13 (48-epx pitch, six
  buttons, no ±10 s); the 2017 row is 1.7.7 (back **10** / forward **30**, not ±10). "App-list rows for the library" is
  wrong: the library is the **112-epx tile grid** of 1.4 (fixed epx; 2 columns at 360 epx → **2 on the S25U's 360-epx canvas
  too**, since the canvas is always 360 wide: Q11), with the 2-line clipped caption of 1.4.9–1.4.10.
- **Y7 (which bars) — closes for the player:** status bar hidden, header hidden, nav bar drawn (1.6.1), with a nav-bar-hide
  button (1.6.2) that the shell cannot honour for Android's own nav bar beyond its own drawn one (phase 01's bar rule). The
  library pages **show** the status bar (1.1.1); the shell keeps its 28-epx status bar (R3 C4), as phase 10 did for Groove's 24.
- **Y6 (motion)** stays an approximation; §4 gives a tagged source for each motion. The H4 row stands.
- **Y8 (Browse pivot, P4)** can borrow measured forms: the section row "title + accent Show all" (1.5.2), the 112-epx
  horizontal artwork strip clipped at the right margin (1.5.3), square TV art vs 2:3 film posters (1.5.3, UNMEASURED-4), the
  2017 hero carousel with an accent "Buy from" button (1.7.5 — reworded "Watch on …"), and the title-page layout (1.7.10).
  Y8's stand-in "3-across 2:3 poster grid with 2-epx gutters" conflicts with every W10M capture, which uses **12-epx
  gutters** (10586) or ≈8.7 (2017) and **2 posters across** at 360 epx for 2:3 art.
- **Navigation form.** Phase 17 draws **pivots** (My videos / Browse / Media server); W10M Movies & TV never used pivots on
  the phone — it used the **≡ pane** (1.3) in both 2015 and 2017. Either keep the pivots as a stated P4 departure, or use the
  measured pane with rows My videos / Browse / Media server (the bottom group — account, Settings — maps to the shell's
  settings page). Needs a line in Decisions either way.
- **H3 (fidelity)** can now be judged against §1.2–1.6; **H7** (the label from the branding module) — W10M's en-US label is
  "Movies & TV", en-GB "Films & TV".
- **Diagnostics / E-rows:** E11's "tap on the scrubber at 70 % of its measured width" should use the measured track
  (x 12 → 348 epx on the 360 canvas, so 70 % = 247.2 epx) and the thumb-travel rule (1.6.8).

## Tally

Counted from the Confidence column of every row in §1 (a row with two levels counts at the lower one; 1.3.13 is a
cross-reference and is not counted). §3's match column and §4 are not counted.

| Section | HIGH | MEDIUM | LOW | UNMEASURED | Rows |
|---|---|---|---|---|---|
| 1.1 Page frame | 5 | 0 | 0 | 0 | 5 |
| 1.2 Header | 6 | 1 | 0 | 0 | 7 |
| 1.3 Pane | 11 | 1 | 0 | 0 | 12 |
| 1.4 Videos page | 8 | 4 | 0 | 0 | 12 |
| 1.5 TV store page | 0 | 4 | 0 | 1 | 5 |
| 1.6 Player | 0 | 15 | 1 | 2 | 18 |
| 1.7 2017 redesign | 0 | 0 | 10 | 0 | 10 |
| **Total** | **30** | **25** | **11** | **3** | **69** |

Counts were produced by parsing the Confidence column of every §1 row. The UNMEASURED table lists 10 open items: the 3
row-level ones plus 7 behaviours no row carries (motion, light theme, idle controls, cast, 2017 pane pitch, aspect states,
the governing-build capture itself).

## Sources

Provenance of every image copied into `docs/plan/r11/src/movies-tv/` (originals unaltered; the folder is gitignored):

| Local file | URL | Fetched | Resolution | sha256 |
|---|---|---|---|---|
| `docs/plan/r11/src/movies-tv/gsm950_054.jpg` | https://st.gsmarena.com/imgroot/reviews/15/microsoft-lumia-950/shots/gsmarena_054.jpg | 2026-09-23 | 1440x2560 | `fbad2092b46ae1fc891c6f295028b2e11eada6e75173ae3dea2b2687fcaeb63f` |
| `docs/plan/r11/src/movies-tv/gsm950_055.jpg` | https://st.gsmarena.com/imgroot/reviews/15/microsoft-lumia-950/shots/gsmarena_055.jpg | 2026-09-23 | 1440x2560 | `46b00d007037313b96d01d52acfae533f46b17fbb2a25e379bba84e9f29dbd91` |
| `docs/plan/r11/src/movies-tv/gsm950_056.jpg` | https://st.gsmarena.com/imgroot/reviews/15/microsoft-lumia-950/shots/gsmarena_056.jpg | 2026-09-23 | 1440x2560 | `b241b8d161dcc7ee0b9e1fc90d91829b53a0a4bc91b3f2b5a3700d2925e7c8cd` |
| `docs/plan/r11/src/movies-tv/gsm950_059.jpg` | https://st.gsmarena.com/imgroot/reviews/15/microsoft-lumia-950/shots/gsmarena_059.jpg | 2026-09-23 | 1440x2560 | `0b0c01dbc47b3236cee6569c9c29651f60c5d1f1cf8414a7c19b87dcbc9f9bb6` |
| `docs/plan/r11/src/movies-tv/gsm950xl_067.jpg` | https://fdn.gsmarena.com/imgroot/reviews/15/microsoft-lumia-950-xl/shots/gsmarena_067.jpg | 2026-09-23 | 1440x2560 | `5c46074a33a20a059b1ce643f82493ad717d0004ca6c5b4c5fe2e0481e40d689` |
| `docs/plan/r11/src/movies-tv/gsm950xl_068.jpg` | https://fdn.gsmarena.com/imgroot/reviews/15/microsoft-lumia-950-xl/shots/gsmarena_068.jpg | 2026-09-23 | 1440x2560 | `498015b6564571d557747b8146bf360122e39beb868ed3e45ba7645b70464a15` |
| `docs/plan/r11/src/movies-tv/wl2017-03_movies-and-tv-1.jpg` | https://www.windowslatest.com/wp-content/uploads/2017/03/movies-and-tv-1.jpg | 2026-09-23 | 1280x759 | `0d06c6cd57f4757f910407b2126afc70d827f47de74bc829199438d53cc3aa21` |
| `docs/plan/r11/src/movies-tv/wl2017-05_fluent-movies.jpg` | https://www.windowslatest.com/wp-content/uploads/2017/05/fluent-movies.jpg | 2026-09-23 | 960x402 | `fe5668eb7b73e3b9fc47367d5003247ccc773c7d06bcb352bed2893371a6c0c7` |
| `docs/plan/r11/src/movies-tv/wl2017-06_movies-tv-mobile.jpg` | https://www.windowslatest.com/wp-content/uploads/2017/06/movies-TV-for-windows-10-mobile-e1496309434921.jpg | 2026-09-23 | 1200x741 | `12e639e9dc319f7da63b0c12fc0eea016c22c474259b89e1cc9e85c13c993599` |
| `docs/plan/r11/src/movies-tv/yt_MsuoI3Pex0c_maxres.jpg` | https://i.ytimg.com/vi/MsuoI3Pex0c/maxresdefault.jpg | 2026-09-23 | 1280x720 | `e03277503b9306c1932043222d9ce78b086625d1355d5444331c2451a445d4f1` |
| `docs/plan/r11/src/movies-tv/wc2017-04_settings_desktop.jpeg` | https://cdn.mos.cms.futurecdn.net/WsMzvLWpaNgfqsb9XpwF8f.jpeg | 2026-09-23 | 2048x757 | `aa7e2bf32f82380a6f4c7edf7fc47be6fa200ea8df8c0f465c71aa5bead6136a` |
| `docs/plan/r11/src/movies-tv/wc2017-04_explore_desktop.jpeg` | https://cdn.mos.cms.futurecdn.net/9CtE732cpMqcvW2oe8hpBG.jpeg | 2026-09-23 | 2048x776 | `a95320f1f2239d4c27317cdbfdf031ad1c3d7d3d9cdb14dc9cd562d613f3b9de` |

Not copied: the XL review's `gsmarena_302.jpg` (https://fdn.gsmarena.com/imgroot/reviews/15/microsoft-lumia-950-xl/shots/gsmarena_302.jpg)
is byte-identical to V4 (`gsm950_059.jpg`), so it is not a second source.

Articles that date and version the images:
- https://www.gsmarena.com/microsoft_lumia_950-review-1347p7.php — GSMArena Lumia 950 review p. 7, 2015-12-02 ("Films and
  TV … You can browse by folders … Subtitles are supported, just hit the subs virtual key and you will be able to choose a
  file (from the current video folder only)")
- https://www.gsmarena.com/microsoft_lumia_950_xl-review-1349p7.php — GSMArena Lumia 950 XL review p. 7, Dec 2015
- https://web.archive.org/web/2018id_/https://www.windowslatest.com/2017/03/08/microsoft-revamps-interface-movies-tv-app-windows-10-pcs-mobiles/ — N1
- https://web.archive.org/web/2018id_/https://www.windowslatest.com/2017/05/13/movies-tv-app-updated-fluent-design-windows-10-mobile/ — N2
- https://web.archive.org/web/2018id_/https://www.windowslatest.com/2017/06/01/movies-tv-app-grabs-major-update-windows-10-mobile-devices/ — N4
- https://www.windowscentral.com/how-use-movies-tv-app-windows-10-creators-update — D1, D2 (2017-04-11)
- https://www.youtube.com/watch?v=MsuoI3Pex0c — N3 (thumbnail only)
- https://www.thurrott.com/music-videos/microsoft-movies-and-tv/106465/movies-tv-app-windows-10-gets-fun-ui-refresh — 2017-03-08, text only (images not retrievable)
- https://learn.microsoft.com/en-us/windows/apps/design/style/segoe-ui-symbol-font — §3 codepoints
- R6 D1 (Lumia W10M user guide) — `r6-measurements.md` §0.2; lines cited in §1.6 and §2

Measurement scripts live in this session's scratchpad (`r11pcmf/scripts/`: `m.py` helpers, `lines.py` text-band finder,
`appbar.py` glyph centroids, `mtv_hdr.py`, `mtv_pane.py`, `mtv_pane2.py`, `mtv_vid.py`); every number can be re-derived from
the cited images with the pixel ranges given in the rows.
