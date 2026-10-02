# R11 §Movies & TV — pass 2 (2026-10-02): the gaps of `movies-tv.md`

An addendum to `docs/plan/r11/movies-tv.md`, written by the lead from a research agent's report (an independent Opus
agent, read-only on tracked files; brief `docs/plan/prompts/r11-pass2-brief.md`). The section itself is unchanged; where
this file corrects it, §4 says so and the correction governs. Sources are local and git-ignored, as R11's are:
`docs/plan/r11/src/movies-tv/pass2/` (with `PROVENANCE.txt`, `frames/`, `scripts/`; 157 MB).

**Summary.** Two of the three row-level gaps are closed from native lossless captures (the film posters, the player's
played colour and right-hand label), the hamburger is confirmed on the later builds, and every motion row but two now
has a bound. The first pass's "video extraction is blocked" no longer holds: extraction worked on 2026-10-02. No true
60-fps footage was found — the one "59 fps" video carries about 12 unique frames per second.

## 1. Gaps closed

Scale factors, all measured: JP = 2.5 px/epx (chrome bottom 180 px = 72 epx; nav bar 120 px = 48 epx; canvas 432×768).
AW1 / AW2 = 1.25 px/epx (nav bar 60 px wide). AW7 = 1.094 px/epx (nav 52.7 px; chrome 79 px). AW3–AW5 show no nav bar,
so their scale is carried from AW7 and cross-checked by the 13-px track inset.

| Row | What | Measured value | Conf. | Source |
|---|---|---|---|---|
| 1.5.5 | Film posters, MOVIES page | Poster tile **112.0 × 160.0 epx** (x 300–579, y 434–833 px), ratio 0.70, not 2:3; includes a 3-px border #1C1C1C. Pitch **120.0 epx** (lefts 300 / 600 / 900 px), 8-epx gutter | MEDIUM | JP2 |
| 1.5.5 | Layout | **One horizontally scrolling strip, not a grid.** Viewport x 8 → W−12 epx (20 → 1050 px); 3 full posters plus a clipped fourth at 432 epx. Strip top 173.6 epx = "In Store" cap top + 24.0. Derived, not measured: 2 full plus a clipped third at 360 epx | MEDIUM | JP2 |
| 1.5.5 | Label lines | Title up to 2 lines, white, cap 27 px = 10.8 epx (15-epx font), first baseline poster bottom + 22.8, pitch 20.0, clipped with no ellipsis at the poster's right edge (579 px). Price line "from $3.99" in #999999, digit height 24 px = 9.6 epx (≈13–14 epx font), baseline 15.6 epx below the last title line | MEDIUM | JP2 |
| 1.6.7 | Played portion colour | **Accent.** Exact (0,120,215), 5 px = 2.0 epx thick, from track left 12 epx to the thumb. The unplayed track is white at 25 % ((64,64,64) over black) | HIGH for app ≥ 3.6.1867 | JP3, JP4; AW5, Y1, Y2 agree |
| 1.6.11 | Right time label | **Remaining time — it counts down.** JP4: thumb centre 545.5 px on track 30→1770, radius 27.5 → 0.2896; 0:17:34 / (0:17:34 + 0:43:06) = 0.2896 (a total reading gives 0.4076). JP3: 0.2611 vs 0.2607. AW5: 0.364 vs 0.3636. Y1 shows 00:01:56 → 00:01:54 → 00:01:52 as elapsed runs 0 → 2 → 4 s | HIGH for app ≥ 3.6.1867 | JP3, JP4, AW5, Y1, Y2 |
| 1.6.8 | Thumb travel rule | Centre runs from track left + r to track right − r (the three fits above only work that way) | HIGH | JP3, JP4, AW5 |
| 1.6.12 | Transport row, skip-button version | Centres: CC **36**, back-10 **W/2 − 48**, play **W/2**, forward-30 **W/2 + 48**, full screen **W − 84**, "•••" **W − 36**. Row centre nav − 39.8; glyphs 18 epx tall. Same offsets on 432, 720 and 592-epx widths. No volume control | HIGH | JP3, JP4, AW2 |
| 1.6.12 | Old row is fixed-epx | Five glyphs at 200.0 / 248.0 / 296.2 / 344.0 / 392.0 epx on a 592-epx width: 48-epx pitch centred on W/2 | MEDIUM | AW1 |
| 1.6.4 / 1.6.5 | Scrim | Top edge at exactly 120.0 epx above the bottom (y 780 of 1080 px). Transmission 0.39 measured losslessly, so ≈60 % black stands. Full content width, not under the nav bar | MEDIUM | JP4; AW1 agrees on 120 |
| 1.6.6 | Track | 2.0 epx, x 12 → W−12, centre 93.0 epx above the bottom, in portrait and landscape | HIGH | JP3, JP4, AW1 |
| 1.6.1 | Bars in the player | Status bar hidden, nav bar shown, on a second device | HIGH | JP3 |
| 1.7.8 | "•••" menu | Flush right, 171.2 epx wide, 150.4 tall for 3 items; item pitch 44.0, 8-epx top and bottom padding; fill #2B2B2B, border #767676; text 15 epx at 39.6 epx inside the left edge; bottom at nav − 60.0. Items: "Cast to device / Aspect ratio / Repeat" | MEDIUM | JP3 |
| UNMEASURED-8 | What cast does | "Conversión a dispositivo" opens the **system Connect pane** ("Conectar", device list, Bluetooth hint) | MEDIUM | Y2 |
| Gap 3 | Later build: hamburger or pivots | **Hamburger confirmed.** Dec 2016 (v10.16112.1015.0): ≡ · "VIDEOS" · search. Apr 2017 Insider: ≡ · "PERSONAL" · search | MEDIUM | AW7, Y1 |
| 1.2.x | Header at a third scale | ≡ 14.0–34.0 epx, title left 61.2, cap 11.2, search centre W − 24.2, band #171717 to 72.0 epx | HIGH | JP2 |

## 2. New sources

| ID | Local file | Pixels | Device / canvas | Date, app | Build | URL |
|---|---|---|---|---|---|---|
| JP1 | `gsm_jadeprimo_067.png` | 1080×1920 PNG | Acer Jade Primo, 432×768 | 2016-04-01 | 10586.x by date | fdn.gsmarena.com/imgroot/reviews/16/acer-jade-primo/sshots/gsmarena_067.png |
| JP2 | `gsm_jadeprimo_068.png` | as JP1 | as JP1 | MOVIES page | as JP1 | …/gsmarena_068.png |
| JP3 | `gsm_jadeprimo_069.png` | as JP1 | as JP1 | player portrait, 0:15:49, menu open | as JP1 | …/gsmarena_069.png |
| JP4 | `gsm_jadeprimo_070.png` | 1920×1080 PNG | landscape | player, 0:17:34 / 0:43:06 | as JP1 | …/gsmarena_070.png |
| AW1 | `aawp2016-03_nonudge.jpg` | 800×450 | Lumia 950 landscape | old player, before 2016-03-30 | 10586 | allaboutwindowsphone.com/images/flow/misc/nonudge.jpg |
| AW2 | `aawp2016-03_nudge.jpg` | 800×450 | as AW1 | v3.6.1867.0, 2016-03-30 | 10586 | …/flow/misc/nudge.jpg |
| AW3–AW5 | `aawp2016-12_films1..3.jpg` | 800×450 | Lumia 950 XL landscape | v10.16112.1015.0, 2016-12-06 | Fast ring | …/images/news/films/films1..3.jpg |
| AW6 | `aawp2016-12_films4.jpg` | 450×800 | 950 XL | TV title page | as AW3 | …/films4.jpg |
| AW7 | `aawp2016-12_films5.jpg` | 450×800 | 950 XL, 411 epx | VIDEOS page | as AW3 | …/films5.jpg |
| Y1 | `yt_MsuoI3Pex0c.mp4` | 1080p | screen projection, light theme | 2017-04-05 | Fast ring | youtube.com/watch?v=MsuoI3Pex0c |
| Y2 | `yt_INA6BD57v30.mp4` | 720p | camera, Spanish phone | 2016-05-03, skip-button app | 10586-era | youtube.com/watch?v=INA6BD57v30 |
| Y3 | `yt_z2mfHKzT9Ek_555-805.mp4` | 1080p, s 555–805 | camera, Nokia Lumia | 2015-10-18 | **Insider 10549 (the description says 10536) — before 10586** | youtube.com/watch?v=z2mfHKzT9Ek |

- GSMArena's review is at gsmarena.com/acer_liquid_jade_primo-review-1419p7.php; the originals are `.png`, and the
  `.jpg` path returns 404.
- JP1 has a pure black header, not #171717, and a title that is not one of the app's pages. It is read as the **Store
  app's** Movies & TV page (inferred), so it is look-only: 2 columns, posters 140 × 192 epx, lefts 16 / 164, row pitch
  248.
- AW1–AW7 are 3.2× downscales, so ± 0.9 epx at best.

## 3. Gaps still open

- **The Dec-2015 six-button version mid-playback.** Its played colour and right-label meaning are still unobserved; AW1
  is at 0:00:07. Everything from v3.6.1867 on is settled.
- **A native-resolution later-build capture, and the pane open on a later build.** Row 1.7.6 (the acrylic pane) stays
  LOW. Searched: AAWP (two articles; directory probes and site search return 404); GSMArena (the Lumia 550 and 650
  reviews have no video section; the HP Elite x3 entry is only an MWC hands-on; the Idol 4S review is the Android phone;
  the 950 XL "time saver" has nothing); Windows Central 2015-09-09 and 2016-03-29 (one camera photo only); OnMSFT (403);
  Deskmodder 2015 (desktop Store); PhoneArena (403 live, 404 on Wayback, review ids guessed); Wikimedia Commons
  (categories and six title searches: only keyboard and settings screenshots); YouTube, 15 queries (nothing shows a 2017
  pane; rejected wdjhbQmMrWg — a cartoon advert on a PC, cslBCzjGlQc — Windows Phone 8.1, U-8Zp0lEl3Y — Edge,
  YEDqFreIr5w — no Movies & TV).
- **The in-app "Show all" films grid and the 10586 title page.** Not captured.
- **Motion still unmeasured:** the subtitle flyout, a thumb drag, the pane on final 10586 or the 2017 acrylic one,
  anything at a real 60 fps.
- **Light theme.** Seen only in Y1, which is too soft to measure.

## 4. Corrections to `movies-tv.md`

| Section value | New reading | Source |
|---|---|---|
| UNMEASURED-5 proposes right label = **total** | **Remaining** | JP3, JP4, AW5, Y1, Y2 |
| UNMEASURED-4 proposes posters 112 × 168 on a 124 pitch | **112 × 160 on a 120 pitch**, as a strip | JP2 |
| Summary: "no previous/next, no ±10 s, no •••" on 10586; 1.7.7–1.7.9 dated "≈15063 era" | The skip row and "•••" shipped in **v3.6.1867.0 on 2016-03-30 to all W10M phones** (AAWP), so on 10586. Previous / next edge tabs (≈30 × 100 epx) came in Dec 2016 | AW2, JP3, JP4, AW3 |
| 1.6.8 thumb Ø 24.0, stroke ≈2.25 | 24.0 confirmed for the old version (30 px at 1.25). From v3.6.1867 it is **Ø 22.0, stroke 2.0**, sitting 1.2 epx below the track centre | AW1; JP3, JP4, AW5 |
| 1.6.10 format fixed "HH:MM:SS" | Locale-dependent: "0:17:34" on the en-US and Spanish phones, "00:00:29" on en-GB. Baseline nav − 67.6 (was 66.25) | JP4, Y2, AW2 |
| 1.6.13 six buttons including CC | AW1 has **five, no CC**, still centred — CC looks conditional on a subtitle file (inferred) | AW1 vs V4 |
| 1.6.3 video cropped to fill | From v3.6.1867 the default in portrait is **letterbox**; the menu item is "Aspect ratio", later "Zoom to fill" | JP3, AW4 |
| 1.7.8 menu contents | Mar 2016: Cast / Aspect ratio / Repeat. Dec 2016: Cast / Zoom to fill / Repeat / Autoplay. Apr 2017 adds Play as 360° video | JP3, AW4, Y1 |
| 1.1.1 status bar #171717, seamless | True on 10586 (JP2: 0–180 px). In Dec 2016 the status bar is **black** and only the header (24–72 epx) is #171717 | AW7 |
| 1.4.3 pitch 124; 1.4.10 clip at +100; 1.4.11 no metadata | Dec 2016: pitch ≈**120** (lefts 11.9 / 131.7 / 252.3), caption clipped at the tile's right edge, plus a **grey date line**; row pitch 187 epx. The date line is already there in May 2016. LOW | AW7, Y2 |

Y3's pre-release pane has a fourth bottom row, "Downloads"; the section's three rows (Dec 2015) stand.

## 5. Motion

Frame rates: **Y3** is a 30-fps camera video (speed not independently checked). **Y2** is a 30-fps camera video (a
coarse speed check only: the remaining label fell 2 s across 68 frames). **Y1** is a 59-fps container with about 12
unique frames per second, so ± 85 ms; real time is confirmed by the player's own counter (4 ticks in 236 frames).

| Motion | Bound | Source |
|---|---|---|
| Pane open | Slides in from the left, labels moving with it. Edge progress per 33-ms frame: 23 / 46 / 78 / 87 / 93 / 99 / 100 % in two identical events; four events in all. **≈78 % at 100 ms, settled at 167–200 ms (± 33)**, decelerating | Y3 |
| Pane close | Slides out left in **2 frames, 67–100 ms**, three events | Y3 |
| Page change from the pane | The old page is gone within one frame. The new page **fades in while rising**: opacity 26 / 53 / 67 / 80 / 85 / 91 % and 16 / 36 / 58 / 73 / 81 / 87 % per frame. ≈80 % at 133–167 ms, settled 300–400 ms. Offset +18 px at the first frame, then 13 / 9 / 6 / 4 / 3 / 2 / 1 / 0 | Y3 |
| The same, 2017 app (folder drill-in) | +18 px (≈16 epx) at 49 % opacity on the first frame, settled by +322 ms | Y1 |
| Library → player | **A hard cut to black in one frame (≤ 33 ms)**, no connected animation. The title appears at +0.3 s. Y1 agrees: cut within 85 ms, controls at +1.0 s, first video frame at +1.08 s | Y2, Y1 |
| Controls show on tap | Fade-in **≈200 ms** (first visible f1359, full f1365; bound 167–300); the scrim fades with them. Y1: 240–320 ms | Y2, Y1 |
| Controls auto-hide | **Hold ≈3.2 s:** 3.17 s in Y2, 3.25–3.32 s in Y1. Fade-out 367–400 ms in the 2016 app, 0.59–0.69 s in the 2017 app, roughly linear | Y2, Y1 |
| "•••" menu open / close | Opens fully opaque 20 px (≈18 epx) low, settled by +152 ms. Closes within 85 ms | Y1 |

- The section's proposed 133-ms pane open is slightly short, and its 3-s auto-hide is within 0.3 s.
- The epx conversions for the Y1 and Y3 offsets are approximate: Y3 is an oblique camera view, and Y1's nav bar reads
  48 px against header rulers that give 1.10 px/epx.
- Microsoft's page for `EntranceThemeTransition.FromVerticalOffset` states no default, so no documented offset is cited.
