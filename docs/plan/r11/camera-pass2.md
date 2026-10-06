# R11 §Camera — pass 2 (2026-10-02): the gaps of `camera.md`

An addendum to `docs/plan/r11/camera.md`, written by the lead from a research agent's report (an independent Opus
agent, read-only on tracked files; brief `docs/plan/prompts/r11-pass2-brief.md`). The section itself is unchanged; where
this file corrects it, §4 says so and the correction governs. Sources are local and git-ignored, as R11's are:
`docs/plan/r11/src/camera/pass2/` (with `PROVENANCE.txt` and `frames/`; 445 MB, most of it seven videos).

**Summary.** The portrait viewfinder, the later five-ring dial, slow motion, the timer, the zoom slider and four of the
seven motion rows now have measured values. No native-resolution portrait screenshot was found: the portrait values come
from a screen-recording video (H, 2.84 px/epx in its magnified frames) and are cross-checked against native landscape
captures. The chevron's expand / collapse and the dial's value change are still open. The first pass's "video extraction
is blocked" no longer holds: extraction worked on 2026-10-02, which is what made motion measurable.

**The layout is fixed to the device.** Landscape is the portrait layout turned 90° with the glyphs kept upright: the
portrait right edge becomes the landscape top, and the portrait bottom becomes the nav-bar side. This holds for V-2015
(C1 against A8) and for V-2016 / V-2017 (H against K11 and P). So every native landscape number in K11 converts
directly to portrait.

## 0. Scale factors (measured, not assumed)

| Source | Reading | px / epx |
|---|---|---|
| H, magnified frames | screen x 446 → 1467 px over 360 epx; nav bar 137 px = 2.854 | **2.836** |
| H, whole-screen frames | 767 → 1152.5 px over 360 epx | **1.071** |
| P (768 × 432 canvas) | nav bar 100 px | **2.083** |
| N1 (731 × 411) | nav bar 84 px | **1.75** |
| DM1 (411 × 731) | nav bar 67 px | **1.40** |
| A5 (731 × 411) | nav bar 52.5 px | **1.094** |
| A7 (768 × 432) | nav bar 50 px | **1.042** |
| A8 | no nav bar; 800 px taken as 640 epx, which reproduces three native V-2015 values (44.0 pitch, 44.0, 60.0) | 1.25 (carried) |

## 1. Gaps closed

| Row | What | Measured value (epx) | Pixel reading | Conf. | Source |
|---|---|---|---|---|---|
| UNMEASURED-1 | Portrait shutter | **72-epx disc at W/2, centre 56.1 above the nav bar's top.** White fill to r 31.9, dark ring r 32–34 (#2B2B2B in P1), white ring to r 36.1 | H: white disc x 866–1047, y 403–584; outer r 102.5 px; centre (956.5, 493.5); nav top 652.5 | MEDIUM (video frame at 2.84 px/epx; equals K11 turned) | H |
| 1.6.1 / 1.6.2, lifted | Shutter and mode discs are fixed epx | 72 disc, 56 from the nav bar, modes ± 60 at 36 from the nav bar, on 360, 411 and 432 canvases | N1: shutter x 1035–1161 (72.0), 56.00 from nav, mode + 60.29 / 35.71. P1: 56.2, ± 60, 36.0. A5: 56.2 | MEDIUM → HIGH (native K11 plus three independent captures) | N1, P1, A5 |
| UNMEASURED-1 | Portrait mode buttons | 32-epx discs (black ≈ 30 %), centres W/2 ± 60.3, 35.8 above the nav bar's top | H: glyphs x 769–802 and 1111–1144, y 540–562; disc edge r 45.5 px | MEDIUM | H |
| new | Mode order | Cyclic photo → video → panorama. The right button is the next mode, the left the previous: pano / PHOTO / video; photo / VIDEO / pano; video / PANO / photo | H at 10.5, 12.5, 14.3 s; DM1; O1 | MEDIUM | H, DM1 |
| UNMEASURED-1; 1.6.3 lifted | Settings | 14-epx gear in a 36-epx disc, 24.0 from the right edge, 27.9 above the nav bar's top | H: gear x 1379–1419, y 553–594; disc r 51 px. P1 landscape 28.08 / 24.0; N1 28.0 / 24.6 | MEDIUM (portrait); 1.6.3 → HIGH | H, P1, N1 |
| UNMEASURED-1; 1.6.4 | Camera roll | Square, 36 plus a ≈ 0.7 light border, centre 24.7 from the left, 28.0 above the nav bar's top | H: x 462–570, y 520–626 | MEDIUM | H |
| UNMEASURED-1; 1.6.5 | Quick-settings capsule in portrait | **Vertical on the right edge**, 2 → 45.5 from the edge, y 217 → 423 of 640 (centred on the screen centre, nav bar included). Glyph centres at 320 − 66 / − 22 / + 22 / + 66, 24 from the right | H chevron x 1388–1410, y 64–77 → 23.98 from the right, 386.8 from the top. P1 (432 canvas): − 65.3 / − 21.6 / + 22.3 / + 67.2 from the screen centre; capsule 585 → 1015 px = 206.4 long, 4.5 → 95 px = 43.4 thick | MEDIUM (portrait); form → HIGH | H, P1 |
| 1.6.5, identity | Capsule glyphs | Photo: flash, **HDR** (A = auto, slash = off), timer, chevron. Video: video light, slow motion, chevron (3 items at − 43.7 / + 0.5 / + 45.1). Panorama: no capsule | — | MEDIUM | P1, P6, A5, H, WC1 |
| new | Expanded capsule | Flash − 163.2, timer − 119.5, WB − 73.5, focus − 25.5, ISO + 22.4, shutter + 70.3, exposure + 118.4, chevron + 163.9 from the screen centre. 380 long, 47 thick; captions under the manual controls; HDR hidden | P2: capsule 404 → 1196 px. N1 (411 canvas, earlier build): − 164.3 … + 163.1 | MEDIUM (two canvases) | P2, N1 |
| 1.6.6 | Camera switch | Its own disc; portrait top-right, 24 from the right, 28 from the top | P1 landscape: (58.5, 50.5) px → 28.1 / 24.2. H whole screen: 22.4 / 28.5 | MEDIUM | P1, H |
| new | 4:3 preview, V-2016+ | Centred on the **full screen** (360 × 640: 80 → 560, inferred). V-2015 centred it above the nav bar | P1: 200 → 1400 of 1600 px. M: 88.6 → 636.2 of 731 | MEDIUM | P1, M |
| UNMEASURED-2; 1.4.11 | V-2016 / V-2017 dial | Radii **130.8 / 195.8 / 260.8 / 325.8 / 390.8** — the same as V-2015's 130.5 + 65 k; centre on the nav bar's edge | P4 peaks 272.5 / 407.9 / 543.4 / 678.8 / 814.1 px. A7 (2018-01): 131.0 / 196.0 / 261.1 / 326.1 / 391.0 | MEDIUM (near-native; equals the HIGH V-2015 radii) | P4, A7 |
| UNMEASURED-2 | Dial details | Icons are 31.7-epx black disc badges on their rings. Exposure sits on ring 1 on the axis; the other four sit on one line 154.1 from the axis (432 canvas). Labels at ring radius + ≈ 31. Dark scrim disc r 455, black ≈ 35–40 %. The shutter moves to 76.1 from the nav bar | P4: badges at y 771 px, x 1251 / 1063 / 904 / 755; scrim edge 947 px; shutter centre x 1341.5 | MEDIUM; LOW for the label size | P4 |
| 1.8.5; UNMEASURED-6 | Slow motion | V-2015: turtle glyph 20.0 × 9.6 in slot 3 of the column in video mode, 24.4 from the edge. V-2016+: the middle item of the 3-item video capsule; its off state is drawn with a circle-slash. Toasts "Slow motion on" / "Slow motion off" | A8: x 429–454, y 22–34 | MEDIUM (identity); LOW (size) | A8, P6, H, Y2 |
| 1.2.3; 1.3.2, second capture | V-2015 video mode | Pitch 44.0 / 44.0; video glyph 24.0 × 12.8 at 44.0; small camera glyph at 24.0, offset 60.0 | A8 (Lumia 930) | independent, but downscaled | A8 |
| UNMEASURED-3 | Zoom slider | On the edge opposite the capsule (portrait: left), ≈ 22–25 from the edge, centred on the screen centre. "+" at the top, "−" at the bottom, centres 205.5 apart on 360 and 231.8 on 411 (≈ 0.565 · W). Track 3 wide; accent bar thumb | A5: track 302 → 497 px. H at 25.0 s (3.98 px/epx by the mock-up's bezel): + / − at y 130 / 948, x 772 | LOW | A5, H |
| UNMEASURED-4 | Timer | The toggle steps through "2-second timer", "5-second timer", "Timer off"; the glyph shows the seconds as a subscript. The countdown is an accent ring at the screen centre, outer diameter ≈ 98, stroke ≈ 6.5. The mode buttons hide, the thumbnail fades, the shutter turns grey | H: ring 149 → 254 px at 1.071; colour (255,181,0) | LOW (1.07 px/epx) | H, Y2, T |
| UNMEASURED-4 | Time lapse | The countdown restarts after each capture (2.47 s period on a 2-s timer) until the shutter is pressed | H 48.68 s, 51.15 s | MEDIUM | H |
| UNMEASURED-10 | Toast | Centred at W/2, ≈ 35-epx Light (cap 24.3), baseline 105 above the nav bar's top. V-2015 landscape: cap 24.8 | H: text y 710–736 px. A8: 408–439 px | LOW | H, A8 |
| 1.2.6, second capture | Video timer | "00:00" digits 24.5 tall, V-2016 | P6: y 832–883 px | MEDIUM | P6 |
| 1.5.1 | Panorama bottom row | Active glyph 24.3 × 20.0 at W/2, 52.1 above the nav bar; video W/2 − 60.4, photo W/2 + 60.4, ≈ 32 above the nav bar | DM1: x 271–305, y 870–898; nav top 957 | MEDIUM → HIGH (O1 on 480, DM1 on 411) | DM1 |
| 1.5.3 | Guide band | Full width, centred on the **screen centre**, height ≈ 0.35 · W (126 on 360, 143.6 on 411, ≈ 168 on 480). A 9:16 strip frame at the left (80.7 wide on 411). Arrow 23.6 × 21.4, centre 22.5 past the frame. A thin centre line | DM1: band y 411.5–612.5, frame x 0–113, arrow x 128–161. H: 257 → 383. P5 landscape: band 643 → 957 px | LOW → MEDIUM | DM1, H, P5 |
| 1.5.5 | Roll in panorama (July 2016) | Round 36, centre (W − 24.3, 23.9) | DM1: x 517–567, y 8–59 | MEDIUM | DM1 |
| strings | New | "Camera", "Video", "Panorama", "Slow motion on" / "Slow motion off", "2-second timer", "5-second timer", "Timer off", "Front-facing camera", "Main camera", "Flash auto" / "Flash on" / "Flash off", "Rich capture off", "Rich HDR selects the best settings for a perfect shot." | — | MEDIUM | H, Y2, T, R |

**M** (build 15228, June 2017, 411-epx canvas, 1.10 px/epx) shows the same portrait layout: shutter ≈ 71 at W/2, 55–58
above the nav bar; chevron 24 from the right, + 64.5 from mid-height. It is a third portrait capture, at LOW precision.

## 2. New sources

| ID | Local file (`pass2/`) | Pixels | Device / canvas | Version, date | Build | URL |
|---|---|---|---|---|---|---|
| **H** | `yt_-A8u0vrLVic_1080p30.mp4`, `frames/H_*` | 1920 × 1080, 29.97 fps | Portrait screen recording inside a phone mock-up, 360 × 640 | Camera **2016.1016.11.0** (stated in the description), 2016-10-19 | Fast ring | youtube.com/watch?v=-A8u0vrLVic |
| **P1–P8** | `phonescoop_idol4s_68726…68733_2x.jpg` | 1600 × 900 | Alcatel Idol 4S, 768 × 432 | 2016-11-10 | 14393 | phonescoop.com/articles/article.php?a=18390&p=6897 |
| **N1** | `npu_camera_expanded_2016-10.jpg` | 1280 × 720 | Lumia 950 XL, 731 × 411 | Oct 2016, the build just before 1016.11 | 14393 era | nokiapoweruser.com/wp-content/uploads/2016/10/index-1.jpg |
| **A5** | `aawp_uichange1_2016-10-18.jpg` (and `aawp_timelapse_…`) | 800 × 450 | 950 XL canvas | 2016-10-18 | Fast ring | allaboutwindowsphone.com/flow/item/21779_Windows_10_Camera_gets_UI_make.php |
| **A6, A7** | `aawp_choosing_camera1.jpg`, `…2.jpg` | 800 × 450 | Idol 4S | **2018-01-30** (final-release era) | 15063 / 15254 era | allaboutwindowsphone.com/features/item/22747_Choosing_a_camera_application_.php |
| **A8** | `aawp_slow1.jpg` (and `slow2–4`, `aawp_rcbackcam_…`) | 800 × 450 | Lumia 930, 640 × 360 | 2015-11-06 | 10586 era | allaboutwindowsphone.com/flow/item/21056_120_fps_slow_motion_capture_co.php |
| **DM1** | `deskmodder_pano_1_2016-07-20.jpg` (and `_2`) | 576 × 1024 | 411 × 731 | 2016-07-20 | 14393 era | deskmodder.de/blog/2016/07/20/windows-10-mobile-kamera-app-nun-mit-panoramafunktion/ |
| **M** | `yt_y141zdIfeLU_1080p59.mp4` | 1080p, 59-fps container, **12–15 unique fps** | Projected screen, 411 × 731 | 2017-06-29 | **15228** | youtube.com/watch?v=y141zdIfeLU |
| **R** | `yt_RC7YvHuUdrs_1080p30.mp4` | 1080p, 30 unique fps | Lumia 950 XL hands-on | 2016-04-04 (the dark-disc look is already present) | 10586 era | youtube.com/watch?v=RC7YvHuUdrs |
| **T** | `yt_snuqpES2r_Q_1080p60.mp4` | 1080p, 60-fps container, **30 unique fps** | Lumia 640 XL hands-on | 2016-10-20 | 14946 (the About page is in the video) | youtube.com/watch?v=snuqpES2r_Q |
| **Y2** | `yt_-TZ9Ev-LbOo_720p.mp4` | 720p | A slideshow of portrait screenshots, no motion; Y1 is its thumbnail | 2017-02-13 | — | youtube.com/watch?v=-TZ9Ev-LbOo |
| WC1 | `wc_camera_newui_2016-10.jpg` | 887 × 505 | Annotated landscape; identity only | 2016-10-21 | — | windowscentral.com/microsoft-pushes-updated-camera-app-new-ui-and-features-release-preview-and-slow-rings |
| also | `yt_0eRQ_ZCAIh0…` (panorama hands-on), `yt_ythRGiB4o5Q…` (Lumia 930 slow-motion hands-on) | — | Behaviour only, not measured | — | — | in `PROVENANCE.txt` |

## 3. Gaps still open

- **A native portrait screenshot of V-2016 / V-2017.** None found.
  - GSMArena has no HP Elite x3 review (only the MWC hands-on, review-1396) and no Idol 4S Windows review.
  - Phone Scoop, AAWP, NPU and Windows Central all shot landscape.
  - OnMSFT `?p=39636` and `?p=84650` returned 403; Neowin's Idol 4S review returned 403.
  - WinAero has a desktop image; hoanghamobile and drwindows have none.
  - The AAWP Idol 4S review (22020) has video embeds only; the AAWP Wileyfox Pro part 2 (22681) has one Office image.
  - Four Wikimedia Commons API searches returned only the 16 known files plus two Settings-app pages.
- **Chevron expand / collapse.** Three dead ends: H never taps it; R has an edit cut between the collapsed and the
  expanded state (96.233 → 96.267 s); in T the finger covers it (163.2–164.8 s).
- **Dial value change.** R shows a ring being dragged at 100–103 s; not analysed.
- **Panorama progress.** `yt_0eRQ_ZCAIh0` shows a capture in progress, but hand-held and oblique; not measured.
- **Capture-intent accept / retake, the Living Images indicator, front-camera differences.** Not searched this pass.
  T and R show only the front / back switch (a black preview plus a toast).
- **Camera launch.** M shows it, but at 12–15 unique frames per second; not timed.

## 4. Corrections to `camera.md`

1. **1.6.1, shutter fill #666666.** That is K11's state dimmed by the Lenses hint. The normal fill is white with a
   #2B2B2B 2-epx ring: P1 samples (255,255,255) and (43,43,43); H, N1, A5 and A6 (2018) agree.
2. **UNMEASURED-1's proposal ("settings disc top-right; capsule along the top").** In portrait the capsule is vertical
   on the right edge, settings is bottom-right, the camera switch is top-right and the roll is bottom-left (H, M, and
   Y1 itself).
3. **1.6.5.** The unidentified glyph is HDR.
4. **§4, the mode switch as a cut.** It is a slide of about 300 ms (H, R).
5. **§4, capture feedback as a white flash.** The preview goes black (H, R).
6. **§4, the dial opening as finger-tracked.** No rings appear during the drag; a sweep of about 330 ms follows the
   flick (R). AAWP's wording is "flick out from the shutter button".
7. **1.5.5.** The roll in panorama is 36 at (W − 24.3, 23.9) (DM1), not ≈ 30–32 at (21, 25) (O1, a rescaled copy).
8. **Summary, dating.** The dark-disc and pill look is already on a 950 XL on 2016-04-04 (R), before panorama arrived.
9. **Toast duration.** Measured ≈ 1.1–1.2 s, against the 1.5 s picked in UNMEASURED-10.

## 5. Motion

Frame rates: every source used here carries 30 unique frames per second, so each bound is ± 33 ms. **H** is real time:
its 2-second timer runs 2.0 s.

| Motion | Bound | Source |
|---|---|---|
| Mode switch | The preview slides along the short axis: the old one out, away from the tapped button; a black gap; the new one in from the tapped side. **300 / 267 / 333 ms** in H, 300 ms in R; out and in are each ≈ 133–167 ms. The shutter glyph swaps on the first frame, the side discs fade in at the end, the capsule cross-fades, and the mode-name toast fades in during the slide-in. Only "tap the right-hand button" was observed | H frames 59 → 68, 107 → 115, 152 → 162; R 81.200 → 81.501 s |
| Capture feedback | The preview cuts to black while the chrome stays. Black for **5 frames (167 ms)**, then 4 dim frames, on a capture with no timer (frames 1019–1023); 3–5 frames (≈ 100–167 ms; 1175–1177, 1248–1252) on timer captures. On the 950 XL in R: 13 frames ≈ 433 ms | H, R |
| Toast | Fade in 100–200 ms, hold ≈ 830–870 ms, fade out 167–200 ms | H frames 63 → 100, 1059 → 1093 |
| Timer ring | Erased counter-clockwise from 3 o'clock, linear at 360° / T (264° in 1.468 s); the blackout comes ≈ 0.15 s after it empties | H frames 1114–1174 |
| Settings page | Opens in ≤ 2 frames (≤ 67 ms). Closes in ≈ 3 frames (≈ 100 ms), the content sliding down and fading | H frames 599–601, 995–997 |
| Five-ring dial open | Starts ≈ 100 ms after the finger lifts. A rotational sweep about the dial centre takes **≈ 333 ms** (98.468 → 98.801 s); the labels fade in over the next ≈ 100 ms | R |
| Chevron, dial value change, camera open | Unmeasured | — |

- The light discs under taps in H may be the recorder's touch indicator rather than the app's own, so pressed states
  read from H are unverified. R shows the pressed shutter and the pressed mode button filled with the accent colour.
