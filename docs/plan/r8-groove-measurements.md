# R8 — Groove Music (Windows 10 Mobile) now-playing screen measurements

Research task for phase 10 (`phase-10-media-player.md`, Q3: "the NOW-PLAYING screen is measured; the
collection pivots are approximated from geometry this build already has"). Scope is the now-playing
screen only.

## Summary — what was and was not established

The now-playing screen's **structure and every static measurement of it** were established at native
resolution from four independent screenshots across two devices at two different W10M scale factors,
which lets each number be classified as *fixed in epx* or *proportional to screen width*. The screen
is: a 72-epx chrome band (24-epx status bar + 48-epx app header carrying `≡` / "NOW PLAYING" /
search), then album art flush under it, then a two-line metadata block, then a time-labelled
scrubber, then a six-button transport row laid out on an exact **W/6 grid** with every glyph centred
at **536 epx** (= 56 epx above the nav bar), then a centred **`^` chevron at exactly W/2** that
expands the play queue. There is **no bottom app bar**: the "•••" is the sixth cell of the transport
row itself. Artist-art mode makes the art **full-bleed behind the entire content area with no scrim,
no blur and no accent wash**; album-art mode insets it as a square on a black page. Nothing on this
screen is accent-coloured — the scrubber is white at two opacities, and the accent appears only on
the currently-playing row of the expanded queue.

Three things could **not** be established. (1) **The governing build.** Every measurable screenshot
found is from the 10586 era (Dec 2015 and Feb 2016); no measurable 14393/15063/15254 now-playing
screenshot exists in any source reached. A March-2017 screenshot still shows the same header and a
centred chevron, so the *structure* is very likely intact, but it is dimmed by a modal overlay and
yields no numbers. (2) **Motion** — entry/exit transitions,
art cross-fades and the collapse/expand animation all need video, and YouTube extraction was blocked
in this environment (`yt-dlp`: "Sign in to confirm you're not a bot" on every player client, and the
one cookie file on this machine is not readable by an agent). (3) **The "•••" overflow menu contents**
— no screenshot found with it open. A further finding that matters more than any single number: the
Dec-2015 and Feb-2016 Groove versions **differ measurably** in art size, title type size and the
content of the second metadata line, so two columns are given wherever they diverge and neither is
the governing build.

---

## 0. Method, sources and calibration

### 0.1 Unit convention

`epx` below is **the project's unit: 1/360 of screen width**, as phase 10 and R6/R7 use it.

This coincides exactly with the real W10M effective pixel on the **Lumia 950**, which runs its
1440×2560 panel at **400 %** → a 360 × 640 epx canvas. It does *not* coincide on the **Lumia 950 XL**,
which runs the same panel at **350 %** → a 411 × 731 epx canvas. That divergence is what makes the XL
useful: any value that is *fixed in epx* lands on a different pixel column on the two devices, and any
value that is *proportional to screen width* lands on the same one. Every "proportional / fixed" call
in the table below was made that way, not assumed.

Scale factors were not assumed either — they were measured. The `≡` glyph is **80 px** wide on the 950
and **70 px** on the XL; "NOW PLAYING" is **413 px** vs **356 px**; its cap height **43 px** vs **38 px**.
All three ratios are 0.862–0.884, i.e. **3.5/4 = 0.875**. HIGH.

Independent confirmation that the 950 is at 400 %: the chrome band's bottom edge is at **288 px =
72.00 epx**, exactly 24 (status bar) + 48 (header), and the status-bar icons centre at 12.4 epx
(a 24-epx bar centres at 12).

### 0.2 Sources

| ID | Image URL | Pixel size | Device / canvas | Groove version, date | Build |
|---|---|---|---|---|---|
| **G1** | https://st.gsmarena.com/imgroot/reviews/15/microsoft-lumia-950/shots/gsmarena_048.jpg | 1440×2560 | Lumia 950 @400 % → 360×640 epx | GSMArena Lumia 950 review, **Dec 2015** | ≈**10586** (by date) |
| **G2** | https://st.gsmarena.com/imgroot/reviews/15/microsoft-lumia-950/shots/gsmarena_049.jpg | 1440×2560 | as G1 | as G1 — **queue expanded** | ≈10586 |
| **G3** | https://st.gsmarena.com/imgroot/reviews/15/microsoft-lumia-950-xl/shots/gsmarena_061.jpg | 1440×2560 | Lumia 950 XL @**350 %** → 411×731 epx | GSMArena Lumia 950 XL review, **Dec 2015** — artist art, full-bleed | ≈10586 |
| **G4** | https://st.gsmarena.com/imgroot/reviews/15/microsoft-lumia-950-xl/shots/gsmarena_062.jpg | 1440×2560 | as G3 | as G3 — **queue expanded, artist art** | ≈10586 |
| **A1** | https://allaboutwindowsphone.com/images/flow/groove/groovefeb4.jpg | 450×800 | Lumia 950 → 360×640 epx | AAWP, **2016-02-06**, Groove **3.6.1719.0** | ≈10586.x |
| **A2** | https://allaboutwindowsphone.com/images/flow/groove/groovefeb3.jpg | 450×800 | as A1 — **artist art, full-bleed** | as A1 | ≈10586.x |
| **A3** | https://allaboutwindowsphone.com/images/flow/groove/groovefeb5.jpg | 450×800 | as A1 — third instance, artist art | as A1 | ≈10586.x |
| **A4** | https://allaboutwindowsphone.com/images/flow/groove/groovemar3.jpg | 450×800 | as A1 | AAWP, **2017-03-27** ("Your new Groove Music") | Creators-Update era |

**A1–A4 are downscaled 3.2× from 1440-wide**, so 1 epx = 1.25 px and a single-pixel JPEG edge is
±0.8 epx; JPEG bloom systematically *inflates* text bounding boxes there. **G1–G4 are native**
(1 epx = 4 px on the 950, 3.5 px on the XL) and every number below is taken from the native sources
where both exist.

**Two versions, not one.** G1–G4 (Dec 2015) and A1–A3 (Feb 2016) agree *exactly* on the header, the
transport row, the chevron and the nav bar, and disagree measurably on the art rectangle, the title
type size, the progress row's vertical position and the second metadata line's content. The AAWP
article for A1–A3 is specifically about the update that changed artwork handling, which corroborates
the divergence rather than explaining it away. Columns **V-2015** and **V-2016** below.

### 0.3 What was searched without finding

- **Screen recordings** (the R6/R7 method): `yt-dlp` search worked and returned candidates
  (`0FInsacQ_D8`, `RVDQHLNVUbQ`, `3ic43BRi5Tw`, `QWME3tqV5Fc`, `NG7T6H5e5dc`, `utGFO4-jOzA`,
  `lxnASleNhWk` …) but **every** video extraction returned "Sign in to confirm you're not a bot",
  on clients `tv_simply`, `web_embedded`, `android_vr`, `tv`, `mweb` and `ios`. No frames, hence no
  motion values.
- **A 14393/15063/15254 now-playing screenshot**: Microsoft Store listing (Wayback 2017 snapshot) —
  all screenshots are **desktop**, not phone. Windows Central, MSPowerUser, Neowin, WindowsLatest,
  groovyPost, OnMSFT articles — no measurable phone now-playing shot; WindowsLatest's Feb-2018 piece
  (Groove **10.18011.1341.0**) illustrates with an **oblique camera photo of the `≡` pane**, not the
  now-playing screen. GSMArena reviews of the Alcatel Idol 4S and HP Elite x3 pages checked, no hits.
- **The expanded "•••" bar / overflow menu**: not present in any of the 19 Lumia 950, 11 Lumia 950 XL
  or 8 AAWP images pulled.

### 0.4 Reference projects (task asked)

| Project | Licence (read live from `api.github.com`) | Does it document measured values? |
|---|---|---|
| [Diffechento/MangoTile](https://github.com/Diffechento/MangoTile) | **MIT** | **No W10M numbers.** `COMPONENTS.md` documents component *parameters* (`MetroSlider` `trackHeight`/`thumbSize`, `TransportButton` `ringSize`) with no values, no source and no build. It claims one motion is "measured off a Lumia doing it" (`metroSlideIn` — artwork flying in a whole element-width on track change) but gives **no duration, no easing and no citation**, and the kit is explicitly **WP8**-targeted. Not usable as a W10M measurement; recorded here only so it is not mistaken for one later. |
| [Diffechento/MetroMusic](https://github.com/Diffechento/MetroMusic) | **GPL-3.0** | Has `NowPlayingScreen.kt`, but it is **WP8 panorama**, not W10M pivot (R9's finding), and the licence makes it reference-only. **No values taken from it.** Nothing in this document derives from either repo. |

---

## 1. Values

Confidence: **HIGH** = measured at native resolution and agreeing across two or more independent
screenshots (or across two device scales); **MEDIUM** = measured at native resolution on one
screenshot, or agreeing sources with ±1 epx spread; **LOW** = read only from a 450-wide downscale, or
a single ambiguous reading. "INFERRED" = reasoned from the W10M design language, not from an image.

### 1.1 Page frame

| Value | Number | Confidence | Derived from |
|---|---|---|---|
| Status bar height | **24 epx** | HIGH | G1 chrome bottom at 288 px = 72.00 epx = 24 + 48; status icons centre 12.4 epx. Cross-checked on A1 (39.2–57.6 epx header band) |
| App header height | **48 epx** (24 → 72 epx) | HIGH | as above; G1 + G3 + A1 + A3 all put header content at 39–57 epx |
| Header background | **#171717** (23,23,23) | HIGH | G1 y=120 x=700; A1/A2/A3 rows 32–47 exactly (23,23,23), σ=0. Status bar is the same colour — no seam |
| Page background (below the art) | **#000000** | HIGH | G1 y=1580; A1 y≥500 = (0,0,0) |
| On-screen nav bar height | **48 epx**, background **#000000** | HIGH | G3: art ends at 2392 px = 683.4 of 731 real epx → 47.6. A2/A3: art ends at 739 px = 591.2 of 640 → 48.8. Two scales agree |
| Content area (between chrome and nav bar) | **72 → 592 epx = 520 epx tall** on a 16:9 canvas | HIGH | derived from the two rows above |
| Nav-bar glyph row | centres at **606–626 epx** (G1) | MEDIUM | G1 y 2424–2503 |

### 1.2 Header row contents

| Value | Number | Confidence | Derived from |
|---|---|---|---|
| Order, left to right | **`≡` · "NOW PLAYING" · (select, expanded state only) · search** | HIGH | G1, G2, G3, G4, A1–A4 all agree |
| `≡` hamburger centre | **x = 24.00 epx**, glyph 20.0 × 11.25 epx | HIGH | G1 x 56–135, y 172–216 |
| Title left edge | **61.0 epx** (nominal 60 = 48-wide `≡` button + 12 gutter) | HIGH | G1 x=245; A1 x=76 px = 60.8 epx |
| Title text | **"NOW PLAYING", all caps, semibold**, cap height **11.0 epx** (→ ≈15–16 epx font) | HIGH (cap) / MEDIUM (font size, from Segoe UI cap ratio 0.70) | G1 y 174–217 = 44 px. A1 reads 12 epx — JPEG-bloom inflated, native preferred |
| Search glyph centre | **x = 336.00 epx** (= 360 − 24), box 18.0 epx | HIGH | G1 x 1308–1379 |
| Header vertical centre | **cy ≈ 48–49 epx** | HIGH | G1 `≡` cy 48.62, title cy 49.00, search cy 48.00 |
| Extra button in the **expanded** state | a **select/multi-select list glyph** appears between the title and search | HIGH | G2 and G4 both; absent in G1, G3 |
| Header text/glyph colour | white (252–255) | HIGH | G1 samples |

### 1.3 Album art

| Value | V-2015 (G1) | V-2016 (A1) | Confidence | Derived from |
|---|---|---|---|---|
| Position | **flush under the chrome, top edge = 72.00 epx**, horizontally centred | same | **HIGH** | G1 col x=700: (23,23,23) through y=287, art from y=288 = 72.00 epx exactly. A1 art top 72.0 |
| Art rectangle (album-art mode) | **311.25 × 307.25 epx** | **328.00 × ~327.5 epx** | HIGH (each, within its own source) | G1 x 98–1342, y 288–1516 (native). A1 x 20–429, y 90–499 |
| Side margins (album-art mode) | **24.50 / 24.25 epx** | **16.00 / 16.00 epx** | HIGH | same rows |
| Art bottom edge | **379.25 epx** | **399.2 epx** | HIGH | same rows |
| Is it square? | **Not exactly** — 311.25 w × 307.25 h (1.013:1) | **Yes** — 328 × ~327.5 | MEDIUM | See note below |
| Fit behaviour | **Uniform (letterbox-fit), not crop** — the image fills its rectangle edge to edge with no bars, and the rectangle takes the image's aspect | — | MEDIUM | G1's cover is 1.013:1; a height-limited uniform fit of 1.013:1 into a 307.25-tall box gives **311.25** wide, which is what is measured. A1's cover is square and lands at the 328-epx width cap. Consistent with `Stretch="Uniform"`; not proven against a deliberately non-square cover |
| **Artist-art mode** | **FULL-BLEED**: the art fills the whole content area, 0 → 360 epx wide and 72 → 592 epx tall, and the metadata, scrubber, transport row and chevron **overlay it directly** | same | **HIGH** | A2, A3 (450-wide) and **G3** (native): photo pixels sampled at y = 505, 545, 590, 640, 700, 735 px right up to the nav bar top; left/right edge columns are photo, not background |
| Scrim / blur / accent wash behind the overlaid text | **NONE.** No dark gradient, no blur, no tint — plain white text straight onto the photograph | **HIGH** | G3 at native: the photo is fully legible and unmodified behind the title, scrubber and transport row; text is pure white with no shadow. Confirmed on A2 and A3 |
| Art in the **expanded (queue)** state | art is **cropped to the height of the metadata+transport block** and still full-bleed behind it (artist mode); in album mode it is **removed entirely** and the page is black | HIGH | G4: art band 72 → 265.1 real epx, queue list below. G2: no art at all, plain black |

> **Why the art rectangle differs between versions.** The two builds genuinely disagree: 311 epx wide
> with 24.4-epx margins (Dec 2015) vs 328 epx with 16-epx margins (Feb 2016). The AAWP article for the
> Feb-2016 set is *about* the update that changed artwork handling, and the same update changed the
> title size and the second metadata line, so this is a real redesign and not a measurement artefact.
> **Neither is the governing build.** See UNMEASURED-1.

### 1.4 Track metadata block

| Value | V-2015 (G1, native) | V-2016 (A1, 450-wide) | Confidence | Derived from |
|---|---|---|---|---|
| Lines and order | **line 1 = track title; line 2 = artist** | **line 1 = track title; line 2 = "Artist • Album"** | **HIGH** | G1 "Always" / "Bon Jovi"; G3 "You Make It Real (Live At Air" / "James Morrison"; A1 "Revelation Song" / "Kari Jobe • Kari Jobe". The `•` separator with the album is a **V-2016 change** |
| Alignment | **left**, both lines | same | HIGH | all sources |
| Left inset (text origin) | **12 epx** | **12 epx** | HIGH | G1: title "A" left edge 12.00 epx, artist "B" 13.50 epx, elapsed "0" 13.50 epx — the spread is Segoe UI side bearings around a 12-epx origin. A1 title "R" 12.00 epx |
| Title cap height | **22.75 epx** → ≈ **32 epx font** | **≤14.4 epx** → ≈ **20 epx font** | HIGH (V-2015 cap) / LOW (V-2016 cap, bloom-inflated) / MEDIUM (font sizes, Segoe UI cap ratio 0.70) | G1 "A" y 1606–1696 = 91 px. Cross-checked on G3 at the other scale: 22.57 real epx. A1 "R" y 521–538 |
| Title band (ascender→descender) | 400.25 → 431.75 epx | 416.0 → 436.0 epx | HIGH / MEDIUM | G1 y 1601–1726; A1/A3 y 520–544 (two instances agree) |
| Title wrapping | **single line, clipped at the right screen edge — does not wrap** | — | HIGH | G3: "You Make It Real (Live At Air" runs to and is cut by the screen edge |
| Artist line cap height | **13.0 epx** → ≈ **18–19 epx font** | **≤11.2 epx** → ≈ **15–16 epx font** | MEDIUM | G1 "B" y 1767–1818 = 52 px; G3 gives 12.57 real epx. A1 "K" y 555–568 |
| Artist line band | 441.75 → 454.75 epx | 444.0 → 456.0 epx | HIGH / MEDIUM | as above |
| Title baseline → artist baseline | ≈ **23 epx** | **24 epx** | MEDIUM | G1 431.75 → 454.75; A1 431.2 → 455.2 |
| Gap, art bottom → title cap top | **21.0 epx** | **17.6 epx** | MEDIUM | G1 379.25 → 400.25; A1 399.2 → 416.8 (cap top, so the layout gap is smaller by the font's internal leading) |
| Text colour | white (252,252,252) | white (255,255,255) | HIGH | G1 (1790,60); A1 brightest title pixel |
| Second line colour | **same white as the title in the collapsed screen** (the greyed "artist • album" style appears in the *queue list*, not here) | same | MEDIUM | G1/G3 samples; not separately opacity-measured |

### 1.5 Scrubber / progress row

| Value | Number | Confidence | Derived from |
|---|---|---|---|
| Present? | **Yes** — a real draggable slider, not a bare progress bar | HIGH | thumb present in every collapsed shot |
| Layout | **elapsed time (left) · track · total time (right)**, all on one line, vertically centred on each other | HIGH | G1, G3, A1, A3 |
| Right label is **total duration**, not remaining | "5:53" fixed while "0:20"→"0:24" advances between G1 and G2 | **HIGH** | G1 vs G2 (same session, same track): elapsed changes, right label does not |
| Row centre (V-2015) | **cy = 488.0 epx** (= 104 epx above the nav bar top) | HIGH | G1: track rows 1946–1957, thumb 1916–1987, digits 1938–1972 — all centre 488.0 |
| Row centre (V-2016) | **cy = 496.0 epx** (= 96 epx above the nav bar top) | MEDIUM | A1 y 608–631 |
| Track thickness | **3.0 epx** (12 px at 400 %) | **HIGH** | G1 x=700: rows 1946–1957 at lum 64, rows 1945/1958 at 0 — a crisp 12-px band |
| Track extent (V-2015) | **46.00 → 313.75 epx** (length 267.75 epx; right inset 46.25 — symmetric) | HIGH | G1 x 184–1255. Cross-checked on G3: bar left 46.29 real epx |
| Track extent (V-2016) | **50.4 → 308.8 epx** (length 258.4 epx) | LOW | A1 x 63–386 (450-wide) |
| Played portion colour | **white at ≈63 %** (lum 160/255) — **neutral, NOT accent** | **HIGH** | G1 x=200 rows 1946–1957 = (160,160,160); A1 (167,167,167). Two versions, two devices, no hue |
| Unplayed portion colour | **white at ≈25 %** (lum 64/255) | **HIGH** | G1 x=900 = 64; A1 = 66 |
| (Opacity mapping to W10M brushes: 63 % ≈ `BaseMedium` 60 %, 25 % ≈ `BaseLow`/`BaseMediumLow`) | — | INFERRED | not read from any Microsoft document |
| Thumb form | **hollow white ring** — the page/art shows through its centre | **HIGH** | G1: ring lum 255, interior lum 0 (black page). A2: interior = (42,32,30), i.e. the **photograph** shows through |
| Thumb outer diameter | **18.0 epx** | **HIGH** | G1 y 1916–1987 = 72 px. A1 y 608–630 = 23 px = 18.4 epx |
| Thumb ring stroke | **≈2 epx** | MEDIUM | G1 annulus chord at Δy=7.5 px gives R_outer 36 px, R_inner ≈28 px |
| **Thumb travel rule** | the thumb **centre** travels from `trackLeft + r` to `trackRight − r` — it never overhangs the track | **HIGH** | G1: predicted 69.15 epx for 0:20 of 5:53, **measured 69.1 epx**. A1: predicted 11.33 % for 0:40 of 5:59 vs actual 11.14 %. A2 (0:32) predicted left edge 89.8 px, measured 90 |
| Time-label digit cap height | **8.75 epx** → ≈ **12.5 epx font** (Caption class) | MEDIUM | G1 y 1938–1972 = 35 px, identical for both labels. A1 reads 9.6 epx (bloom); the *widths* agree (21.25 vs 20.8 epx for four glyphs), so the two versions use the same size |
| Elapsed label left edge | **13.5 epx** (V-2015) / **17.6 epx** (V-2016) | HIGH / LOW | G1 x=54; cross-checked G3 x=47 → 13.43 real epx. A1 x=22 |
| Total label right edge | **347.25 epx**, i.e. **12.75 epx inset** (V-2015) / 344.0 epx, 16 epx inset (V-2016) | HIGH / LOW | G1 x 1307–1388; G3 gives 12.57 real epx inset. A1 x 404–429 |
| **Fixed or proportional?** | the progress row is **fixed in epx** — the same real-epx insets land on different pixel columns on the 950 (×4) and the XL (×3.5) | **HIGH** | 950 elapsed left x=54 px, XL x=47 px; 54/4 = 13.5, 47/3.5 = 13.43 |

### 1.6 Transport row

| Value | Number | Confidence | Derived from |
|---|---|---|---|
| Controls and order | **`|◀` previous · `‖`/`▷` play-pause · `▶|` next · `↻` repeat · `⤬` shuffle · `•••`** — six cells | **HIGH** | G1, G2, G3, G4, A1, A3 all identical |
| Grid | **six equal cells of W/6**, glyphs centred → centres at **30 · 90 · 150 · 210 · 270 · 330 epx** | **HIGH** | G1 native measured 30.00 / 90.00 / 150.00 / 210.00 / 269.88 / 330.00. A1 30.4 / 90.4 / 150.0 / 210.0 / 269.6 / 329.6. A3 identical to A1 |
| **Proportional, not fixed epx** | the cell pitch is **W/6**, so it is 60 epx on a 360 canvas and 68.5 epx on the XL's 411 canvas | **HIGH** | G3 (350 %) puts the same glyphs at the **same pixel columns** as G1 (400 %) — 120 / 359.5 / 600 / 840 / 1079 / 1320 px — i.e. the same *fraction* of width, not the same epx |
| Row vertical centre | **cy = 536.00 epx**, i.e. **56 epx above the nav bar top** | **HIGH** | G1: all six glyphs return cy = 536.00 exactly. A1 identical. G3 at 350 %: cy 627.43 of a 683.43 nav top = **56.0** |
| Glyph heights | prev / play-pause / next **15.0 epx**; shuffle **14.5 epx**; repeat **20.0 epx**; `•••` dots **2.5 epx** tall, span 17.5 epx | HIGH | G1 per-glyph vertical extents |
| Glyph widths | prev 14.75 · play-pause 7.25 · next 14.75 · shuffle 19.75 · `•••` span 17.5 epx | MEDIUM | G1 column runs |
| Glyph style | **thin line/outline glyphs, not filled** (`|◀` is a bar plus a hollow triangle) | HIGH | visible at native in G1, G3 |
| Glyph colour | white | HIGH | G1 |
| **Toggle "on" state** (repeat / shuffle) | a **filled circular pill, 35 × 35 epx, fill #343434**, centred exactly on the cell (measured cx 210.00, cy 536.00), glyph stays white | **HIGH** | G1 repeat-one active: x 770–909, y 2074–2213, fill (52,52,52). Same pill in G3, G4 |
| Repeat-one sub-state | repeat glyph carries a small **"1"** badge at its top right | HIGH | G1, G3, G4 |
| Is this a W10M `CommandBar`? | **No** — the six controls sit on a W/6 grid spanning the full width, which a CommandBar does not do, and there is no separate app bar strip | MEDIUM | geometry; see §1.8 |

### 1.7 The `^` chevron (queue expander)

| Value | Number | Confidence | Derived from |
|---|---|---|---|
| Present, centred | **cx = 180.00 epx = exactly W/2** | **HIGH** | G1 x 696–743 → cx 180.00; G3 x 699–740 → cx 720 px = W/2 exactly; A1 cx 180.4 |
| Ink box | **12.0 × 6.5 epx** | HIGH | G1 x 696–743, y 2290–2315 |
| Vertical centre | **575.75 epx**, i.e. **16 epx above the nav bar top** | **HIGH** | G1 cy 575.75; G3 cy 667.14 of a 683.43 nav top = **16.3** |
| Pitch, chevron → transport row | **40 epx** (both versions) | HIGH | 575.75 − 536.0 |
| Function | **expands the play queue**; the glyph flips to `v` when expanded | **HIGH** | G2 and G4 show the same screen with a `v` chevron and a queue list below it |
| What it is *not* | not an app-bar "…" — the `•••` is separately present in the transport row above it | HIGH | both visible simultaneously in G1, G3, A1 |

### 1.8 App bar

| Value | Number | Confidence | Derived from |
|---|---|---|---|
| Is there a W10M bottom app bar on this screen? | **No.** The screen's bottom-most element is the `^` chevron, 16 epx above the nav bar. The `•••` lives in the transport row's sixth cell at cy 536 epx | **HIGH** | G1 measured: below the transport row (whose lowest ink is the active-toggle pill, bottom 553.25 epx) the page is black to the chevron at 572.5 epx, and black again from 579.0 to the nav bar top at 592.0 (rows 2316–2367: max luma 14, mean 0.001). No 48-epx app-bar strip exists |
| What `•••` holds | **NOT DETERMINED** | — | see UNMEASURED-3 |

### 1.9 Expanded (queue) state

| Value | Number | Confidence | Derived from |
|---|---|---|---|
| What moves | the metadata + scrubber + transport + chevron block **moves to the top**, flush under the 72-epx chrome; the art is cropped away (artist mode) or removed (album mode) | HIGH | G2 title top = 73.0 epx (vs 400.25 collapsed); G4 art band 72 → 265.1 real epx |
| Internal spacing after the move | unchanged — same title/artist/scrubber/transport/chevron offsets relative to each other | HIGH | G2: title 73.0–104.5, artist 114.0–127.5, scrubber 151.75–169.75, transport 198.75–218.75, chevron 245.75–252.25 epx; all the same deltas as G1 |
| Queue row pitch | **61.5 epx** | **HIGH** | G2 246 px at 400 % = 61.5; G4 216 px at 350 % = 61.7. Two scales agree → fixed in epx |
| Queue row form | **two lines** — track title, then "Artist • Album" in grey — no thumbnail | HIGH | G2, G4 |
| Queue row backgrounds | **alternating**, luma 0 (#000000) and luma 26 (≈#1A1A1A) | MEDIUM | G4 column x=1400 sampled every 4 px across 7 rows |
| Currently-playing row | title drawn in the **accent colour** with a small **accent equalizer-bars glyph** to its left | **HIGH** | G2 (magenta accent) and G4 (blue accent) — two different user accents, same treatment |
| Where the accent appears at all | **only here.** Nothing on the collapsed now-playing screen is accent-coloured | **HIGH** | G1 (magenta accent system) and G3/G4 (blue accent system) both render the whole collapsed screen in neutral white/grey |

### 1.10 Adapting to the build's canvas (derived, not measured)

| Value | Number | Confidence | Derived from |
|---|---|---|---|
| Vertical anchoring | chrome (72 epx) anchors to the **top**; nav bar (48), chevron (−16), transport row (−56) anchor to the **bottom**; the art takes the slack | INFERRED (from the measurements) | the bottom trio holds at 48/16/56 epx above the nav bar on **both** a 640-epx and a 731-epx canvas (G1 vs G3), which is what "bottom-anchored" means |
| On a 19.5:9 canvas (S25U ≈ 360 × 780 epx) | the extra ≈140 epx of height falls **between the art and the metadata block**, because both ends are pinned | INFERRED | do not carry the measured absolute y values (400/444/488/536) across unchanged — only the offsets from each end are transferable |

---

## UNMEASURED

Values that could not be established, and the source that would settle each.

| # | What | Why not | What would settle it |
|---|---|---|---|
| **1** | **The now-playing screen on the governing build (14393 / 15063 / 15254).** Every measurable screenshot is 10586-era. A4 (2017-03-27) shows the screen still has the same header — `≡`, "NOW PLAYING", search — plus a faint centred mark at ≈560–563 epx consistent with the `^` chevron, displaced upward from its 575.75-epx position by the Music-Pass promo banner occupying the bottom of that page. But A4 is dimmed by a modal (background luma 2 vs 23) and **nothing on it can be measured**: at threshold 18 the header text fragments into single-pixel runs, and the centred mark clears the control strip by only 4 rows and ~9 luma levels. Treat A4 as weak structural evidence of continuity, not as a measurement and not as proof the geometry is unchanged. | no measurable late-build capture found; see §0.3 for what was searched | **One native-resolution screenshot of Groove's now-playing screen on 14393+.** Either a screen recording (needs working YouTube extraction) or a phone screenshot posted to a forum/Reddit/imgur. |
| **2** | **Which of V-2015 and V-2016 the governing build shipped**, i.e. whether the art is 311 epx with 24-epx margins or 328 epx with 16-epx margins, whether the title cap is 22.75 or ~14 epx, and whether the second line reads "Artist" or "Artist • Album". | the two versions genuinely differ (§0.2) and neither is the target build | same as UNMEASURED-1. Failing that, an **owner ruling** picking one — V-2016 is the later of the two and the only one whose second line carries the album. |
| **3** | **The `•••` overflow: its menu items and the expanded app-bar form.** | no screenshot found with it open, in 38 images pulled | a screenshot or recording with the now-playing `•••` tapped. |
| **4** | **All motion.** Collection → now-playing entry and its reverse; whether the art transitions on track change (cross-fade, slide, or cut); the collapse/expand animation for the queue; whether the thumb animates or jumps on seek; press feedback on the transport buttons (tilt? none?). | **needs video.** `yt-dlp` was blocked on every player client tried (§0.3), so not a single frame was extracted. MangoTile's `metroSlideIn` claims a Lumia-measured artwork slide but gives no numbers, no build and is **WP8**, not W10M (§0.4) | any W10M Groove **screen recording** (Project My Screen capture preferred, as R3/R6/R7 used), or working YouTube extraction on the candidates listed in §0.3. |
| **5** | **Scrubber interaction**: whether dragging the thumb scrubs live or seeks on release, and whether the track is tappable to seek. | static images only | video, as UNMEASURED-4. |
| **6** | **Light-theme rendering.** Every source is dark theme. | no light-theme Groove now-playing screenshot found | a light-theme capture. (R7 §3.3 found Cortana's Notebook was a *light* page even in dark theme on 15063, so a theme assumption here would not be safe.) |
| **7** | **The second metadata line's exact opacity/colour relative to the title** in the collapsed screen. | the grey "Artist • Album" style is measured in the *queue list*; on the collapsed screen only "white" was sampled, not an opacity ratio | a native dark-background screenshot with a per-channel sample of both lines (G1 would do; not run for this value). |
| **8** | **Whether the art element crops or letterboxes a deliberately non-square cover.** §1.3 infers `Uniform` fit from a 1.013:1 cover, which is too close to square to be decisive. | no source with a markedly non-square cover | a now-playing screenshot with a visibly non-square or very wide cover. |
| **9** | **The "artist art vs album art" toggle itself** — where the setting lives and what the default is. AAWP's Feb-2016 article says the update *added* the choice; both modes are measured, the control is not. | not visible in any screenshot pulled | Groove's settings page on a phone. |
| **10** | **Nav-bar top edge on the Lumia 950 directly.** Taken from where the full-bleed art ends (A2/A3: 591.2 epx; G3: 683.4 of 731) because on G1 the bar is black on a black page and gives no edge. | black-on-black | a light-theme or bright-wallpaper capture. |

---

## Sources

Screenshots measured:
- https://st.gsmarena.com/imgroot/reviews/15/microsoft-lumia-950/shots/gsmarena_048.jpg (G1, 1440×2560)
- https://st.gsmarena.com/imgroot/reviews/15/microsoft-lumia-950/shots/gsmarena_049.jpg (G2, 1440×2560)
- https://st.gsmarena.com/imgroot/reviews/15/microsoft-lumia-950-xl/shots/gsmarena_061.jpg (G3, 1440×2560)
- https://st.gsmarena.com/imgroot/reviews/15/microsoft-lumia-950-xl/shots/gsmarena_062.jpg (G4, 1440×2560)
- https://allaboutwindowsphone.com/images/flow/groove/groovefeb4.jpg (A1, 450×800)
- https://allaboutwindowsphone.com/images/flow/groove/groovefeb3.jpg (A2, 450×800)
- https://allaboutwindowsphone.com/images/flow/groove/groovefeb5.jpg (A3, 450×800)
- https://allaboutwindowsphone.com/images/flow/groove/groovemar3.jpg (A4, 450×800 — structure only, too dimmed to measure)

Articles that date and version the screenshots:
- https://m.gsmarena.com/microsoft_lumia_950-review-1347p7.php — GSMArena Lumia 950 review (Dec 2015), source page for G1/G2
- https://m.gsmarena.com/microsoft_lumia_950_xl-review-1349p7.php — GSMArena Lumia 950 XL review (Dec 2015), source page for G3/G4
- http://allaboutwindowsphone.com/flow/item/21234_Groove_Music_for_Windows_10_Mo.php — AAWP, Steve Litchfield, 2016-02-06, Groove **3.6.1719.0** on a Lumia 950; source page for A1/A2/A3
- https://allaboutwindowsphone.com/flow/item/22096_Your_new_Groove_Music.php — AAWP, 2017-03-27; source page for A4
- https://www.windowscentral.com/groove-music-windows-10-mobile-now-lets-you-view-album-art-now-playing-screen — Windows Central on the artist-art/album-art toggle
- https://www.windowslatest.com/2018/02/18/groove-music-updated-windows-10-mobile-still-no-sign-equalizer/ — Groove **10.18011.1341.0**, Feb 2018; no measurable now-playing shot
- https://www.neowin.net/news/groove-music-for-windows-10-mobile-updated-with-a-bunch-of-new-features/
- https://mspoweruser.com/groove-music-for-windows-10-mobile-being-updated-with-number-of-improvements/
- https://www.groovypost.com/news/groove-music-windows-10-mobile-major-updates-continuum-support/
- http://web.archive.org/web/20170505233113/https://www.microsoft.com/en-us/store/p/groove-music/9wzdncrfj3pt — archived Microsoft Store listing (checked: phone screenshots absent, all listing images are desktop)
- https://en.wikipedia.org/wiki/Windows_10_Mobile_version_history — build/date mapping

Reference projects (licence read live from the GitHub API; **no values taken from either**):
- https://github.com/Diffechento/MangoTile — MIT
- https://github.com/Diffechento/MetroMusic — GPL-3.0

Evidence root (scratchpad, this session):
`/tmp/claude-1000/-home-jeremyking/5bfad911-296d-46d2-9cf5-489a8756adef/scratchpad/r8-work/`
— `img/` source images, `scripts/` the five measurement scripts (`g_an.py` band profiles, `g_cols.py`
column runs, `g_v.py` vertical extents, `g_edge.py` art bbox, `g_scrub.py` scrubber profile),
`sheets/` contact sheets and zoomed crops, `docs/` fetched HTML, `search/` the yt-dlp search logs.
Every source is cited by URL above so any measurement can be re-derived from a fresh download.
