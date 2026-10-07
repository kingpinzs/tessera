# R11 §Files — pass 2 (2026-10-06): the gaps of `files.md`

An addendum to `docs/plan/r11/files.md`, written by the lead from a research agent's report (an independent Opus
agent, read-only on tracked files; brief `docs/plan/prompts/r11-pass2-brief.md` with Files' gaps in the order motion,
dialogs and picker, native capture, Back and landing, the rest). The section itself is unchanged; where this file corrects
it, §4 says so and the correction governs. Sources are local and git-ignored, as R11's are:
`docs/plan/r11/src/files/pass2/` (49 files, 106 MB, with `PROVENANCE.txt`, `frames/`, `scripts/`).

**Headline.** File Explorer's motion is no longer all UNMEASURED: four videos give frame-level evidence for six of the
nine motion rows, and for the Move-to picker, the move progress overlay, the selection geometry, the cold-start entry and
the Recent page. Four of the first pass's PROPOSALS are contradicted by footage (§4): the app starts on **Recent with
the pane open**, not on This Device; entering selection mode is **animated**; a folder change is a **cut to empty, then
rows fade and slide up**; the picker confirms with **two app-bar buttons ✓ ✕** and move progress is a **top box over a
wash**, not a two-button dialog. Still nothing for the sort picker, the ••• expand, the light theme, the rename / delete /
conflict / new-folder dialogs, copy progress, or a native-resolution still. YouTube extraction works (yt-dlp 2026.08.19),
which is where all of it came from.

## 0. Scale factors (measured, not assumed)

| Source | Reading | Factor / canvas |
|---|---|---|
| V1 (Lumia 930 over USB projection, 15254) | the 68-epx app-bar pitch: glyph centres 178 / 259.5 / 343 / 426 px, More 496.5; checks: header 57.5 px = 47.3, app bar 58.5 px = 48.1, pane row 58 px = 47.7; nav bar hidden (hardware keys) | **1.2157 px/epx**, **432 x 768 epx** |
| V2 ("Project My Screen", Vietnamese UI) | header 73 px, app bar 74.5, nav 73, screen 552 x 981 px | **1.533 px/epx**, **360 x 640 epx** |
| V3 (Lumia 550, a CAMERA recording, 10586-era) | horizontal 1.058 px/epx at the header (≡ to ↑ = 330 px for 312 epx) to 1.167 at the app bar (pitch 79.3 px); vertical about 1.02–1.10 | 360-epx canvas, perspective — a range, not one factor |
| V4 (edited tutorial, mouse through projection, 10586-era) | bars 26–27 px; a 3-item menu 136 px wide; phone inset 230.5 px wide | **0.560 px/epx**, about 411 epx |

Frame rates, verified by frame differencing: V1 about 30 unique fps (± 33 ms) in a 60-fps container; V2 15–30 unique
fps (± 67 ms); V3 25 fps (± 40 ms); V4 29.97 fps (± 33 ms).

Caveats. **V1's colours are level-shifted** (#171717 reads (16,18,15); #1F1F1F reads (24,26,23)): no colour from V1 is
usable as-is. **V1's pane roots are registry-modified** between 2:28 and 8:56 (extra rows Documents … This PC); they are
stock at 9:00. **V2 has one tweaked pane row** ("PC này"). V3 is a camera recording: its values are structure and ratios.

## 1. Gaps closed

Every row is LOW by the section's rule (a video frame, a camera, or a projection) unless marked **x2 → MEDIUM
candidate**, where two independent captures at different scales agree.

| Row | What | Value (epx) | Reading and scale | Source |
|---|---|---|---|---|
| UNMEASURED-4 / 1.3.13 | Cold-start entry | **The first pane entry, which is Recent on stock roots**; on the 360-epx canvas the first app frame after the splash has the **pane open** with Recent selected. The splash is black with a white File Explorer logo | V2 225.2 s, frames 59–131; V1 8:58 → 9:01 (stock roots → Recent); V1 with tweaked roots lands on its first root at 2:30, 5:46, 7:30. Agrees with F3 (Recent selected, pane open) | V2, V1 — x2 → MEDIUM candidate |
| new, 1.3.4 / 1.3.12 | The closed pane's form depends on width | 360 and about 411 epx: no rail, ≡ only. **432 epx portrait: a 48-epx compact rail** of glyphs stays visible (rail edge 57 px = 46.9 epx) and the pane opens over it | V1 152.8 s; V2, V4, V3 for the no-rail form | V1 |
| 1.3.5 | Pane width | 254.5 (309.5 px / 1.2157) and 255.7 (392 px / 1.533) | — | V1, V2 (independent of F3 / F6; 256 stands) |
| UNMEASURED-5 / 1.12.9 | Recent, empty | String: **"You haven't opened any files recently."** Grey, the "Sort by:" label's level (reads 160 vs 165). Left 14.5 from the content edge, cap top 88.8 — the sort line's position. **No "Sort by" line on Recent** | text x 76–376 px, y 108–126 px, content edge 58.4 px | V1 9:01 |
| UNMEASURED-5 | Recent, with content | Rows directly under the bar; a row = icon, name, **date only** ("05-03-2016"). App bar **Select · Icons · Search · •••** (no New folder), dimmed when empty. Tap-and-hold menu: **Remove from recent · Share · Properties** | structure only (0.56 px/epx) | V4 41.5 s; the bar also V1 9:01 |
| UNMEASURED-7 / 1.8.3 | Selection geometry | Checkbox **20 square, centre x 22; content shifts 32** (icon left 20 → 52, name left 72 → 104); checkbox vertically centred on the icon | V3: checkbox 44–66 px, icon 53 → 87, name 109 → 143 at 1.078 px/epx, left edge 31.3. V1 (picker): checkbox 15–38 px, y 169–193, icon left 63 (51.8), name left 126 (103.6) | V3 (10586, camera) + V1 (15254, digital) — x2 → MEDIUM candidate; equals R7 1.3.9 |
| 1.8.2 | Count line | "1 item selected" at the sort line's left edge (46 vs 47 px), same y band | V3 73 s; V1 2:17 | V3, V1 |
| 1.8.3 | Selected row fill | accent, full screen width, full row height (66 px ≈ 64) | y 132–198 px | V3 73 s |
| 1.8.4 | Selection bar | Delete · Move to · Copy to · Share · ••• at the standard right-aligned pitch; **all four dim with nothing selected** | glyph centres 105.5 / 185 / 263.5 / 343.5 / 411 px | V3 67 s, 73 s |
| UNMEASURED-2 | Move-to picker | The folder page in a picker mode. **"Choose a folder"** replaces the sort line (left 46 px, the same x). Breadcrumb, ≡ and ↑ stay; the pane works inside it (Recent / This Device / MEMORY CARD (D:)). The app bar becomes **✓ · ✕ · •••** in the two right-most slots (264 / 343.5 / 411 px = 150 / 82 / 24 from right). Rows carry no checkboxes | V3 83–102 s | V3 |
| UNMEASURED-2 | Move progress | **"Moving files…"** with an indeterminate dots row above the text, in a dark box directly under the status bar: top 24, about 66 tall, inset about 21 each side, text centred. The whole screen, bars included, sits under a light translucent wash (the camera reads about (90,132,172) — colour not reliable). No percentage, no cancel. On completion the app shows the **destination folder** | box x 57–392 of screen 35–414 px, y 46–115 with screen top 21 | V3 105–129 s |
| UNMEASURED-2 (related) | The system file-open picker, 15254 | Entry list Recent / This Device / OneDrive. Folder page with a checkbox per row, title at left 12.3 with no ≡, ↑ dimmed at the root (reads 134 vs 255). Bottom bar: two 30-epx circled ✓ ✕ buttons centred on the screen, 58 apart, ••• at 24 from right | circles 208–245 and 278–316 px | V1 2:11–2:18, 8:44–8:50 |
| 1.5.2 | List row pitch on the phone | **64.2** (78.0 px mean over 4 rows); 64.2 again on the list page | — | V1 (the first digital phone reading; with F6 the MEDIUM stands) |
| 1.5.4–5 | Row geometry on the phone | icon left 20.1, width 33.4–33.7, height 40.3, vertically centred in the row; name left 72.1; first row top 45 below the bar, icon top 56 below | V1 icon y 157–206, fill 143.5–220.5 px; V3 icon 53–89 px | V1, V3 |
| 1.2.2–3, 1.2.8, 1.4.3 | Location bar and sort line | ≡ cx 23.4; breadcrumb left 60.5; ↑ 22.4 from right; sort text 13.3 from the content edge, cap top 88.8 | V1: 29 / 74 / 498 px; sort 75 px with content edge 58.4; y 109 | V1 (agrees with F1 within 1 epx) |
| UNMEASURED-8 / 1.6 | Icons view with thumbnails | 3 columns, pitch about 122 (= W/3 at 360). Thumbnail about 96 square. **The label wraps to two centred lines**, line pitch about 20. **Row pitch about 172 ± 8.** App-bar slot 3 shows the list glyph | thumbs x 8–113, 139–245 px; tops 205 and 388 px; scale about 1.06–1.08 | V3 139.5 s (camera) |
| 1.14.1 | 10586 sort line | accent value, no chevron — confirmed on a second 10586 device | — | V3 |

## 2. New sources

| ID | Local file | Pixels / fps | Device | Date, build | URL |
|---|---|---|---|---|---|
| **V1** | `V1_w10mgroup_2022-06-04_gdj_CmrEPI8_1080p60.mp4` | 1920x1080, 60-fps container, about 30 unique fps | Lumia 930 over USB projection | 2022-06-04; its About page reads "Version: 1709" = **15254** | https://www.youtube.com/watch?v=gdj_CmrEPI8 |
| **V2** | `V2_windowsphonevn_2020-08-25_96l9ZQsMjko_1080p60.mp4` | 1920x1080, 60-fps container, 15–30 unique fps | "Project My Screen", Vietnamese UI | 2020-08-25; build not shown (white sort value + chevron = the 15063-or-later form) | https://www.youtube.com/watch?v=96l9ZQsMjko |
| **V3** | `V3_simplephone2000_2016-02-16_V81Xf5TCHhg_1080p25.mp4` | 1920x1080, 25 fps, **camera** | Lumia 550 | 2016-02-16, **10586-era** (accent sort value, no chevron) | https://www.youtube.com/watch?v=V81Xf5TCHhg |
| **V4** | `V4_w10mreviews_2016-03-05_UKE_ztFQCKY_480p30.mp4` | 854x480, 29.97 fps | an edited tutorial, driven by mouse through projection | 2016-03-05, 10586-era | https://www.youtube.com/watch?v=UKE_ztFQCKY |

About 300 MB was downloaded in all; roughly 25 videos were triaged from 20 search queries.

## 3. Gaps still open

- **#1 A native-resolution still**: not found, and not searched for on the web in this pass (the effort went to video).
  The best new phone-digital sources are V1 at 1.2157 px/epx and V2 at 1.533 px/epx — both below native, V1 with shifted
  colour. No value becomes HIGH.
- **#2, the remainder** (name conflict, delete confirmation, rename, new-folder name entry, COPY progress): the YouTube
  queries "windows 10 mobile file explorer rename folder", "… delete files file explorer lumia" and "… create folder file
  explorer" returned no W10M File Explorer footage. V3 shows only Move. Three other downloaded videos (setq7B1KNoY,
  1F-ypDgxWO0, 81oJpZNNLWk) only browse and tap packages; a fourth (TOM4KQR419c) was only skimmed on a 144p contact sheet.
- **#3 Hardware Back**: every return seen (V1 twice, V3 twice) is a straight descent, where parent equals previous, so
  history-versus-parent is undecided. In V3 at 141 s a finger at the Back key returns from a folder to its parent —
  consistent with both.
- **#4, the remainder**: whether the last folder is remembered across a cold start. V3's launch at 31 s reopens in "This
  Device › Pictures" with no splash, which is a resume, not a cold start.
- **#6 the sort picker, #9 the light theme, 4.4 the ••• expand, 4.9 the sort picker's opening**: no frame in any video.
- **The pane opening on the 360-epx (no-rail) form**: V2 shows the pane already open at launch and closing by a cut; no
  open-by-≡ in motion at 360 epx (4.1's curve is from the 432-epx rail form).
- **Leaving selection mode**: not captured.
- **The view mode per folder**: V3 suggests the Icons choice did not carry to the parent folder (one observation).
- **Not fetched**: HjMWFdxfOdk ("How to View Hidden Files on Windows 10 Mobile", 76 s) and t9Zos5VQcMY returned HTTP 403
  on download and were not retried.

## 4. Corrections to `files.md`

These govern over the first pass. Items 1, 2, 4, 5 and 6 replace first-pass PROPOSALS (the UNMEASURED table's tagged
approximations), not measurements.

1. **§4 row 4.7** proposed "a one-frame layout change, no slide" for entering selection mode. V3 shows an animated shift
   of about 200 ms (10586).
2. **§4 row 4.3** proposed "content cut, then fade-in 200–317 ms". Measured: a cut to an EMPTY list, a load gap of
   230–430 ms, then a fade plus an upward slide of at least 7 epx over about 300 ms, with names, detail lines and icons
   staggered.
3. **Rows 1.3.4 / 1.3.12** recorded the compact rail only for landscape (F5). V1 shows it in portrait at 432 epx; 360
   and 411 epx have none. For a 360-epx shell the overlay form stands.
4. **UNMEASURED-4's proposal** ("This Device") is contradicted: V2 and V1 land on Recent, and at 360 epx with the pane
   open.
5. **UNMEASURED-2's proposal** (a bottom confirm bar; R7's dialog for progress): the picker confirms with two ordinary
   app-bar buttons ✓ ✕, and progress is a top box over a light wash, not a two-button dialog. (The R7 1.3.9 dialog form
   remains the proposal for the dialogs still uncaptured: conflict, delete confirmation, rename, new folder.)
6. **UNMEASURED-8's proposal** (row pitch about 118) is contradicted for thumbnail folders: about 172 ± 8 with two-line
   labels (camera).
7. **Row 4.5's pressed-fill timing** (fill at 300 ms, from R7): V4 shows the fill on the press frame. Weak — the input
   was a mouse.
8. **Row 1.12.9**: Recent rows show the date only; Recent has its own hold menu (Remove from recent · Share · Properties)
   and no sort line (V4, V1).

## 5. Motion

| §4 row | Result | Evidence |
|---|---|---|
| 4.1 Pane open | The pane's **right edge grows**; the labels stay put (a reveal, not a slide of content). Edge travel 252 px (207 epx, rail → full). Progress: 34.5 % at +17 ms, 56 % at +33, 70 % at +67, 84 % at +100, 91 % at +133, 95 % at +150, 98 % at +200, 100 % at +283 ms. Strong ease-out, about 250–280 ms. Measured on the rail form (432 epx); the 360-epx open was not captured in motion | V1 152.8 s, frames 24–41 |
| 4.2 Pane close on choosing | **A one-frame cut**: the pane is gone and the list is empty in the same frame. The title changes about 133 ms later; rows arrive at about +233 ms with 4.3's entrance | V2 235.3 s, frames 26 → 27, 35, 41 |
| 4.3 Folder → subfolder | (1) The pressed-row fill while down. (2) **A cut to an empty list** in one frame; the bars do not move. (3) The breadcrumb updates after 230–370 ms (load time, device-dependent). (4) Rows enter with a **fade plus an upward slide**: first captured 7.2 below rest (11 px), then 4.6, 3.3, 2.0, 1.3, 0.7, 0 over about 300 ms, decelerating. Names come first, detail lines about 100 ms later, icons about 130 ms later | V2 239.0 s, frames 11–55; V1 169.4 s, frames 28–65 (the same form) |
| 4.3 Return to the parent | The same form and the same upward direction: a cut, the breadcrumb at about +100 ms, the entrance settled by about +570 ms. Whether ↑ or hardware Back was used is not visible | V1 171.2 s and 178.0 s |
| 4.5 Tap-and-hold | The pressed fill on the press frame. **The menu's first frame 700 ms later** (frames 28 → 49, ± 33 ms). The box is at 76 % of its height on its first frame, 90 % at +100, 95 % at +133, 98 % at +167, settled between +233 and +367 ms. Dismiss on choosing: gone within 1–3 frames (≤ 100 ms). Input was mouse-driven through projection | V4 39.0 s, frames 28 / 49–60 / 89–92 |
| 4.6 Pressed row | The fill covers the full row (63.3 tall), from the content edge to the screen's right edge. Reads (48,48,48) in V1's shifted levels, so about (55 ± 4) true — not a clean reading | V1 169.4 s, frame 20 |
| 4.7 Selection, entering | **Animated, not a cut**: the content slides right 32 while the checkbox column slides in from the left. Icon left edge 53 → 77 → 82 → 86 → 87 px on consecutive frames; checkboxes in place 5 frames after the first motion. **About 200 ms (160–240), ease-out.** Leaving was not captured | V3 65.76 s, frames 3–9 |
| 4.8 List ↔ Icons | A cut to empty with the loading dots under the bar, then labels enter first and thumbnails fill in over the next 0.5–1 s. The same reload form as 4.3; not a morph | V3 135.4 s, frames 14–40 |
| 4.4 ••• expand, 4.9 sort picker | not captured in any source | — |
