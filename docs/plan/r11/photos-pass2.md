# R11 §Photos — pass 2 (2026-10-02): the gaps of `photos.md`

An addendum to `docs/plan/r11/photos.md`, written by the lead from a research agent's report (an independent Opus
agent, read-only on tracked files; brief `docs/plan/prompts/r11-pass2-brief.md`). The section itself is unchanged; where
this file corrects it, §4 says so and the correction governs. Sources are local and git-ignored, as R11's are:
`docs/plan/r11/src/photos/pass2/` (73 files, 80 MB, with `PROVENANCE.txt`).

**Headline.** The Adjust tab (row 1.8.5) is still not captured on a phone, and the evidence says it never shipped to
production phones. The other gaps moved: the V-2016+ viewer, albums, settings, the phone editor, crop and trim now have
measured values (all LOW by the section's rule, because the sources are downscaled or Insider-only), and three motion
rows have real bounds. YouTube extraction works again (yt-dlp 2026.08.19), which is where the motion came from.

## 0. Scale factors (measured, not assumed)

| Source group | Reading | Factor / canvas |
|---|---|---|
| AAWP 450x800, Lumia 950 XL | nav bar y 747.5→800 = 52.5 px; app bar 694.5→747 = 52.5 px | 1.09375 px/epx, 411.43 x 731.43 |
| AAWP 23101 (2018) | app bar 699.5→749.5 = 50 px; nav 749.5→800 = 50.5 px; pivot ink extents equal G1's to ± 0.6 epx | 1.0417 px/epx, 432 x 768 (1080p at 250 %) |
| AAWP 21087 trim, Lumia 830 landscape | closed bar 0→60.5 px; expanded 0→75.5 px | 1.25 px/epx, 640 x 360 |
| AAWP 22261, Lumia 1020 landscape | the 68-epx glyph pitch reads 77 at 1.25, so 1.406 | 1.406 px/epx, 341 epx wide (225 %) |
| Deskmodder native 2560x1440 landscape | nav bar x 2392→2560 = 168 px | 3.5 px/epx (950 XL class) |
| V1 screen recording (zs00Ky8fnpA) | the screen fills the 1080-px frame height; checks: nav top 592.0–592.6, date cap 18.37–29.04, glyphs 286.07 / 217.93 / 149.78 / 82.5 / 24.15 from right | 1.6875 px/epx, **360 x 640** |
| V2 projected screen (UgcGMP3gJ4g) | screen x 706.5→1221.5 = 515 px; app bar 67.5 px = 47.2 epx | 1.4306 px/epx, 360 x 640 |

## 1. Gaps closed

Confidence follows the section's own rule. Every row below is LOW (downscaled, a video frame, or Insider-only) unless
noted; "x2" or "x3" means that many devices at different scales agree.

### Viewer, V-2016+ (UNMEASURED-1)

| Row | What | Value (epx) | Reading | Source |
|---|---|---|---|---|
| 1.6.2 | Date header | 0 → 50, #171717 (23,23,23) | edge 54.5 px / 1.09375 = 49.8; 52 px / 1.0417 = 49.9 | W8, B2 (x2) |
| 1.6.3 | Date text | left 25.0–25.6; cap band 19.2 → 30.2–30.7 | first glyph x 28 px, y 21–33 px (W8); x 26, y 20–32 (B2) | W8, B2 |
| 1.6.5 | Viewer app bar fill | black: (0,0,0) at rest, (5,5,5) expanded | bar samples | B2, W8 |
| 1.6.6 | Glyph set | **Share · Edit · Delete · More at 218 / 150 / 82 / 24 from right**; no Favourite | 218.5 / 149.9 / 82.3–83.2 / 24.2 (W8); 218.4 / 150.2 / 83.0 / 24.5 (B2); 217.3 / 149.1 / 82.3 / 24.0 (L1) | W8, B2, L1 (x3, 2017-06 to 2018-07) |
| 1.6.7 | Expanded bar | 60.3 tall; glyph centre 23.3–25.1 below top; label caps 42.1–51.2 (centre 46.6) | y 734→800 px | W8 |
| 1.6.8 | Labels | "Share", "Edit", "Delete" | — | W8 |
| 1.6.9 | Menu panel | #2B2B2B, 242.7 tall for 5 items + rule; pitch 43.9–44.8; text left 12.8; first cap band 450.7–462.6 with panel top 428.3; rule ≈1 epx from 12.3 to 399.5 | panel 468.5→734 px; caps 493 / 541 / 590 / 637 / 695 px; rule y 673 px | W8 |
| 1.6.10 | Menu items | Slideshow, Print, Set as, Add to album, —, File information | — | W8 |
| 1.6.6 at 360 epx | Older app, Nov 2016 | Share · Favourite · Edit · Delete · More at 286.1 / 217.9 / 149.8 / 82.5 / 24.2; bar centre 24 below top | frame 840 | V1 |
| new | Expanded bar, wrapped label | the bar grows to **76** when a label wraps ("Bearbei-ten"); second label line 623.4–631.1 | bar top 564 → 640 | V1 |

### Albums, album page, collection, settings

| Row | What | Value (epx) | Reading | Source |
|---|---|---|---|---|
| 1.4.1 | System album tiles | 2 x 2 (Camera roll, Favourites / Saved pictures, Screenshots); 60 tall; rows 82.3–142.6 and 154.5–213.9; columns 11.9–199.3 and 210.3–397.7 (187.4 wide) | rows 90–156, 169–234 px | W1 |
| 1.4.2 | Tile label | white, cap band 107.9–118.9, left 25.6, vertically centred | — | W1 |
| 1.4.4 | Separator | ≈1 epx, (31–37) grey, y 226.3, x 13.7 → 397.7 | y 247–248 px | W1 |
| new | Sort row | "Sort by:" white + "Newest photos" accent; band 251.4–266.1; left 12.8 | — | W1 |
| 1.4.5 | User album tile | square 187.4 (rows 274.3–461.7); title centred below (band 473.6–489.1, cloud glyph before); date centred, (156) grey, band 497.4–506.5 | — | W1 |
| new | "New" badge | accent rect 56.7 x 24.7, centred in the tile, top 11.9 below the tile top | 77.7–134.4 x 286.2–310.9 | W1 |
| 1.4.7 | Albums app bar | Sync · Add · Select · More at 218 / 150 / 82 / 24 | 218.1 / 149.9 / 82.3 / 24.2 | W1 |
| new | Album page | grid 3 columns: 11.0–138.1 / 141.7–269.7 / 272.5–399.5 (127–128 wide, 3.7 gutter), first row 307.2–434.3; bar Share · Slideshow · Edit · Select · More at 286 / 218 / 150 / 82 / 24, fill #1F1F1F | — | W2, W7 |
| 1.3.13 | Collection grid at 432 epx | **4 columns of 98.9, gutter 3.84, left 11.5, right 13.4**; row pitch 102.7 | columns 12–115 / 119–222 / 226–329 / 333–436 px | B1 |
| 1.2.7 | Pivot ink extents | 13.4–129.6 / 156.5–244.8 / 273.6–356.2 (B1); 13.7–129.8 / 156.3–245.0 / 272.5–355.7 (W1) | independent of G1, agree to ± 0.6 | B1, W1 |
| 1.3.4–5 | Month header, day row | month cap band 73.9–85.4, left 10.6; day row 330.2–344.6, date left 12.5, count right edge 13.4 from right | — | B1 |
| 1.9.1 | Settings header | "Settings" mixed case, band 0 → ≈52 (± 1), #1F1F1F, text left 12.8 | edge between 56 and 58 px | S1 |
| 1.9.x | Settings contents (Photos 16.1003.10012.0) | link; "You're using … on OneDrive" + ≈4-epx bar; "Viewing and editing"; "Linked duplicates" + toggle; "Tile" / "The photos tile shows" / combo 32 tall, x 12.8 → 187.4; **"Mode": Light / Dark / Use system setting**, 20-epx radios at 44-epx pitch (469.9 / 513.8 / 557.7); "About this app" | — | S1 |

### Unmeasured items 6 and 7, the Edit sheet

| Row | What | Value (epx) | Source |
|---|---|---|---|
| UNMEASURED-6 | Collection bar expanded (July 2016, caps-pivot app) | bar 60 tall (623.5 → 683), (32) fill; glyph centre 23.8 below top; label centre 45.7; labels "Refresh", "Select", "Slideshow"; glyphs at 218.0 / 149.9 / 82.3 / 24.2. Menu panel (41) fill, 517.5 → 623: account row (24.7-epx avatar at x 11.9, name at 61.3) and "Settings" (left 12.8) | C1 |
| UNMEASURED-7 | File information | bottom panel = lower 50 % of the screen (top at 365.7 of 731.4), fill (22,22,22); title cap band 381.3–399.5, left 13.7; accent "Close" ending 15.5 from right; fields at 68.6 pitch, grey label then white value 22 below, left 15.5. In landscape it docks at the left | I1, L1 |
| 1.7.2 | Edit sheet, V-2016+ | the same offsets as V-2015: first icon top +15, rule +64, second icon +80, then 62 pitch; icons 32 x 32 at x 11.9–44.8; label left 57.6. Anchors to the screen bottom when the nav bar is hidden | W9 |
| 1.7.3 | Edit sheet rows, 2017 | Lumia Creative Studio, rule, "Crop, Rotate, Auto-enhance", then third-party editors, "Find more editors" | W9 |

### The editor on a phone (gap 2)

| Row | What | Value (epx) | Source |
|---|---|---|---|
| Y11 / new | Editor page | full screen (nav bar hidden in V1); bar 48 at the bottom, fill (15,15,15); **Crop · Enhance (wand) · Rotate · Save · More at 286 / 218 / 150 / 82 / 24** | W11: 285.7 / 218.1 / 149.9 / 82.3 / 24.2 at 411; V1: 286.1 / 217.9 / 150.1 / 81.9 / 23.9 at 360 (x2) |
| new | Editor expanded bar (de-DE, labels wrap) | 76 tall; labels "Zuschneiden", "Verbessern", "Drehen", "Kopie speichern" (crop, enhance, rotate, save a copy); menu panel 56–58 tall with one item "Speichern" (save), left 12.9 | V1 frame 1320 |
| new | Crop bar | Aspect ratio · Accept (✓) · More at 150 / 82 / 24 | W10, V1 (x2) |
| new | Crop handles | 4 white discs, ≈18 diameter: 18.3 (20 px, W10); 19.0–19.6 including the dark outline (32–33 px, V1); 17.6 native on the Fast-ring build (62 x 61 px, K3) | W10, V1, K3 |
| new | Crop frame and grid | ≈1-epx white frame (0.6 in V1, 0.86 in K3); thirds grid, each white line paired with a dark line | V1, K3 |
| new | Crop rectangle placement | centred on the area above the app bar: centre (180, 295.9) on 360 x 592; (205.3, 317.7) on 411 x 635 | V1, W10 (x2) |
| new | Photo in crop mode at 360 | shrinks from full width (rows 218.7–421.9) to x 33.6–326.4, rows 213.3–378.7; default rectangle 203.9 x 115.3 (70 % of the photo) — one observation | V1 frames 1980–2070 |
| new | Dimming outside the crop | the photo at 34–35 % brightness (pixel ratio 0.343–0.353 between frames 65 and 66 of the 1-fps set) | V1 |
| new | Save flow | the Save glyph = "Save a copy" (dimmed photo, "Saving a copy…", the result is a new file); plain "Save" is in the overflow menu | W11, V1 |
| 1.8.1–2 | Fast-ring panel, native | panel exactly 320.0 wide (1120 px); crop tile #404040, 96.0 tall; tab strip 56; Undo all / Save #454545, 60.0 tall; 2-epx gap; "Save a copy" 60.0 tall | K2 |
| new | Fast-ring crop page, native | buttons "Aspect ratio", "Flip", "Rotate", "Reset" (German in the source); a straighten dial with a 21.7-epx white thumb and "0 °" | K3 |

### Video trim (row 1.11.2) — Nov 2015, Lumia 830, 640 x 360 landscape

| What | Value (epx) | Reading |
|---|---|---|
| Command bar | at the **top**, #1F1F1F; 48 closed, 60 expanded; Save a copy · Cancel · More at 150 / 82 / 24 from right; labels "Save a copy", "Cancel" | 0→60.5 px, 0→75.5 px |
| Scrim band | bottom 100 (260.6 → 360), about 50 % black (wall 145 → 74) | y 326 px |
| Track | centre y 298.4; ≈3 thick (4 px); x 67.2 → 572.8 (67 insets); the selected part white (246), the rest about 135 over 86 | rows 371–374 px |
| Handles | two filled white discs, 18.4 diameter, centred on the track | right handle 534.4–552.8 |
| Ring thumb | a hollow ring, 37.6 outer diameter, under the active handle, centre ≈24 below the track | x 166.4–204.0 |
| Time labels | start left (x 11.2–36.8), end right (602.4–628.8), digits 11.2 tall; they show the range ends ("0:03", "0:14") | — |
| Play button | a dark translucent disc ≈88 ± 4, centred on the screen, white outline triangle | chords 63 and 70 at 30 off centre |
| Saving state | the whole video dimmed to about 45 %; "Saving a copy…"; progress bar x 40 → 600, 6.4 thick, accent fill over grey; the Save glyph disabled | T3 |
| Entry | the video viewer's Edit sheet lists "Save photos from video and get creative", rule, "Trim"; in landscape the sheet docks right, 360 wide | T1 |
| Portrait | the same structure: bar at the top, track near the bottom, ring thumb under the dragged handle (camera footage, not measurable) | V3 |

## 2. New sources

All AAWP images are 450x800 or 800x450 downscales by Steve Litchfield (allaboutwindowsphone.com). Local names are in
`pass2/`.

| ID | Local file | Pixels | Device / canvas | Date, version | Build | URL |
|---|---|---|---|---|---|---|
| W1–W12 | `aawp_22334_album1..12.jpg` | 450x800 | Lumia 950 XL, 411 epx | 2017-07-31 | Creators Update era; app version not shown | allaboutwindowsphone.com/features/item/22334_How_to_edit_and_share_your_Win.php |
| B1, B2 | `aawp_23101_prob1.jpg`, `prob2.jpg` | 450x800 | 1080p phone at 250 %, 432 epx (the article names the Alcatel IDOL 4 Pro; not stated per image) | 2018-07-31 | Fall Creators Update per the article | …/features/item/23101_Windows_10_Mobile_Photos_and_b.php |
| S1, S2 | `aawp_21751_photos-oct1.jpg`, `1photos-oct2.jpg` | 450x800 | 950 XL | 2016-10-06, **16.1003.10012.0** | Fast ring then | …/flow/item/21751_Windows_10_Photos_gains_return.php |
| C1 | `aawp_21549_slideshow.jpg` | 450x800 | 950 XL | 2016-07-08 | Redstone Insider | …/flow/item/21549_Photos_gets_image-opening_anim.php |
| T1–T3 | `aawp_21087_trim1..3.jpg` (+ `albumedit1..2`) | 800x450 | Lumia 830, 640x360 | 2015-11-19 | ≈10586 | …/flow/item/21087_Photos_update_adds_integral_vi.php |
| L1 | `aawp_22261_lc5 / lc8 / lc9.jpg` | 800x480 | Lumia 1020, 341 epx | 2017-06-13 | Creators Update (unofficial on the 1020) | …/features/item/22261_Lumia_Camera_for_the_1020_retu.php |
| I1 | `aawp_21569_photostile5.jpg` (+ `photostile2`) | 450x800 | 950 XL | 2016-07-19 | Redstone | …/features/item/21569_How_to_fix_a_broken_Photos_liv.php |
| M1 | `aawp_21330_photos1–4, 9, 10.jpg` | 450x800 | 950 XL | 2016-03-22, 16.317.14282 per Windows Central | Redstone Insider | …/flow/item/21330_Photos_and_Camera_get_updates-.php |
| — | `aawp_21061_*`, `aawp_21007_favs0..5`, `aawp_20946_photosadd3..5`, `aawp_21609_flickr4-1` | 450x800 / 800x450 | Lumia 930 / 950 class | 2015-09 to 2016-08 | Insider to 10586 | flow items 21061, 21007, 20946; features item 21609 |
| K1–K4 | `deskmodder_wp-ss-20180210-0002..0005.png` | **2560x1440 native** | 3.5 px/epx, landscape | 2018-02-10, **2018.18011.13438.0** | 15254.16 Fast ring (visible in K1) | deskmodder.de/blog/2018/02/10/fotos-app-windows-10-mobile-2018-18011-13438-0-mit-vielen-aenderungen-appx/ |
| V1 | `yt_zs00Ky8fnpA.mp4` + `zs_f*.png` | 1920x1080 | direct screen recording, 360 epx | uploaded 2016-11-10 (WPLive DE); app version not shown; the viewer still has Favourite | unknown | youtube.com/watch?v=zs00Ky8fnpA |
| V2 | `yt_UgcGMP3gJ4g.mp4` + `ugc_f*.png` | 1920x1080 | a projected screen in a phone skin, 360 epx | uploaded 2015-09-14 | **pre-10586 Insider** (status bar shown; only COLLECTION / ALBUMS) | youtube.com/watch?v=UgcGMP3gJ4g |
| V3 | `yt_pXBLVMdoT00.mp4` + `_six_frames.jpg` | 1920x1080 | camera footage, Lumia 1020 | 2015-11-20 (WPXBOX) | ≈10586 | youtube.com/watch?v=pXBLVMdoT00 |
| V4 | `yt_06efj92YO6A_editor_sheet_199-209s.jpg` | sheet | camera footage, Lumia 950 landscape | 2017-08-07; the About page shows 2017.35063.13610.0 | a side-loaded desktop-style build | youtube.com/watch?v=06efj92YO6A |

Text sources that date behaviour: Windows Central 2016-07-07
(windowscentral.com/microsoft-photos-grabs-new-slideshow-button-and-fancy-animation, 16.703.10032.0); WindowsLatest
2018-01-08 (…/2018/01/08/install-new-microsoft-photos-app-windows-10-mobile-trick/ — "No new features since February
2017"; the new app is a side-load); WindowsLatest 2018-02-16
(…/2018/02/16/microsoft-photos-app-update-mobile-reverts-back-old-version/); AAWP 20946 (2015-09-12), the Photos Add-ins
plug-in.

## 3. Gaps still open

- **1.8.5 the Adjust tab on a phone.** K2 shows the panel on the Enhance tab only; Deskmodder's text says Adjust offered
  red-eye, brightness and light. V4 never opens Adjust (frames 199–209 s checked at 2 fps). F2 unchanged. No capture of a
  production phone build with Enhance / Adjust was found.
- **The V-2016+ Folders page.** Not found. Searched: AAWP flow index pages 1–95 and features index pages 1–85 by title;
  the Commons API ("Lumia 950 XL" Microsoft Photos screenshot; "Windows 10 Mobile" Photos app screenshot;
  insource / intitle Photos; intitle "Lumia 950 XL" 2018) — only G1 again; one web search for a 2017–18 Folders-tab
  screenshot returned desktop how-tos.
- **The V-2016+ collection at 360 epx.** Not captured; the rule is now bounded (Corrections 5).
- **The V-2016+ collection bar expanded.** Only the July-2016 caps-pivot app (C1).
- **The "Set as" sub-menu.** Not captured in production. V2 (pre-release) shows flat items "Set as lock screen", "Set as
  background", "Set as Photos tile".
- **Light theme.** No capture, but S1 shows it is an in-app option from 16.1003.10012.0.
- **Trim on V-2016+.** Only the Nov-2015 captures.
- **GSMArena.** The HP Elite x3 has only the MWC hands-on (review-1396, device photos, no screenshots); no Idol 4S
  Windows review found.
- **Windows Central article images.** The two Photos articles carry a camera photo and a cropped old / new strip, no
  full native screenshot.
- **60-fps footage of Photos.** None. Windows Central's 1080p60 screen-capture demos (I98ENfXJRqA Anniversary Update,
  E6vvrz4ozpE Creators Update) showed no Photos at 26-s and 12-s sampling — a coarse scan, not exhaustive. The first
  shows the Camera app (panorama) at about 1055–1085 s.
- **JlF-ze3GXSw** ("Folder View in Windows 10 Mobile Photos App", 2015-09-24) returned HTTP 403 on download.
- Pivot swipe, slideshow step, pinch and double-tap zoom: not in any footage reached.

## 4. Corrections to `photos.md`

1. **Row 1.11.1 is not Trim.** Y1 is the Photos Add-ins frame picker ("Save photos from video" → "Choose best frame");
   V2 at 109–113 s shows the path into it, and AAWP 20946 shows the same screens. The "filled yellow disc" is the
   recorder's touch indicator, present at every tap in the video. Trim has no centred time readout: it has two white
   handles, range times at the track ends, and a top bar (T2).
2. **Editor scope (Summary, 1.7.4, Y11).** The production phone editor was still the "Crop, Rotate, Auto-enhance" tool
   in July 2017 (W9–W11) and in the Nov-2016 recording. Enhance / Adjust reached phones only as a side-load (V4,
   WindowsLatest 2018-01-08) and as the Feb-2018 Fast-ring build, reverted on 2018-02-16. A 48-epx bottom tool strip is
   the production form; F2's panel is not.
3. **Row 1.6.6 for V-2016+.** The section has Share · Favourite · Edit · Delete · More at 286 / 218 / 150 / 82 / 24
   (V-2015, HIGH). From 2017 the bar is Share · Edit · Delete · More at 218 / 150 / 82 / 24 (W8, B2, L1). The older set
   is still on V1 in Nov 2016.
4. **Row 1.6.5.** The section has the viewer bar #171717 (V-2015). W8 and B2 read black; the header stays #171717.
5. **Grid count ("3 even at 411").** B1 shows 4 columns of 98.9 at 432 epx; G1 and W2 show 3 of 127 at 411. Four
   columns at 411 would be 94.2 wide, so the minimum thumbnail lies between 94.2 and 98.9. At 360 that gives 3 columns
   of about 110 (INFERRED, now bounded both ways).
6. **Row 1.8.2.** The section has "Save a copy" accent #0078D7, panel #171717. K2 reads (76,74,72) — read as the system
   accent (it matches the "Storm" swatch), not a fixed blue. The panel is #2B2B2B over the crop tile and tabs, #171717
   in the list.
7. **§4's viewer open / close proposal ("fade-in from black, 200–317 ms").** V-2015 is a hard cut (V2; Windows Central:
   "would just present the photo instantly"). From 16.703.10032.0 (July 2016) the photo expands from its thumbnail;
   from 16.1003.10012.0 (Oct 2016) it shrinks back on return (AAWP 21549, 21751). No duration measured for either.
8. **Version dating.** The caps-pivot, grey-band app was still current on 2016-07-08 and 2016-08-02 (C1, AAWP 21609).
   Menu items grew: Print in March 2016 (M1), Add to album by Nov 2016 (V1).
9. **Row 1.7.2 anchoring.** With the nav bar hidden the Edit sheet sits on the screen bottom (W9).

## 5. Motion

Frame rates verified with ffprobe and by frame differencing. The container rate overstates what these recordings
resolve: V1 is a 29.97-fps container with about 6 unique frames per second (± 170 ms); V2 a 29.97-fps container with
about 15 unique frames per second (± 67 ms), and it is a pre-10586 Insider build; V3 is 23.976-fps camera footage.

| Motion | Result | Evidence |
|---|---|---|
| Collection → viewer (V-2015) | **a cut**, ≤ 1 container frame (33 ms; ≤ 67 ms at the capture rate). The viewer opens with its chrome hidden | V2 frames 193 → 194 |
| Viewer → collection (V-2015) | **a cut**, 1 frame; thumbnails refill 5–6 frames later (167–200 ms) through grey placeholders | V2 frames 474 → 475, 480 |
| Chrome show on tap | **a cut**, ≤ 2 frames (≤ 67 ms) in V2; within one capture interval in V1 | V2 frames 210 → 211–212; V1 746 → 748 |
| App bar "•••" expand (viewer) | the panel top travels 311.1 epx (590.7 → 279.6). Progress: 45.2 % at +33 ms, 57.1 % at +67, 81.1 % at +133, 91.0 % at +200, 97.1 % at +300, 99.1 % at +433, 100 % at +534 ms. Strong ease-out, consistent with R7 2.1.16 (317 ms, 47 % on the first frame). V1 agrees within its resolution (≈370 ms across 3 updates) | V2 frames 1779–1795 |
| Photo swipe | a finger-tracked strip with a **20-epx gap** between photos (28.5 px). One whole fling took ≤ 7 frames (234 ms); drag and settle cannot be separated | V2 frames 259–275 |
| Rotate 90° in the editor | animated, between 334 and 500 ms (updates at about 25°, 70°, 90°) | V1 frames 1477 / 1482 / 1487 |
| Crop enter | the photo shrinks first; dim, frame and handles are in place about 770 ms later (may include idle time) | V1 frames 1932 → 1955 |
| Crop apply | the handles vanish, then the result snaps to fit after about 367 ms | V1 frames 2232 → 2243 |
| Edit sheet | appears within one capture interval (≤ 167 ms); a slide is not resolved either way | V1 frames 958 → 963 |
| Editor open | a cut after the row highlight | V1 frame 1133 |
| V-2016+ open / close | qualitative only (Corrections 7) | — |
| Pivot swipe, slideshow step | not captured | — |
