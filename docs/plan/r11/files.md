# R11 §Files — File Explorer (Windows 10 Mobile) measurements

Research section for phase 18 (`docs/plan/phase-18-files.md`, which says "r11/files.md settles how W10M drew its root
page" and whose Y1–Y6 approximations wait on this file). Scope: W10M's inbox File Explorer (package
`c5e2524a-ea46-4f67-841f-6a9465d9d515`): the entry points (This Device / SD card / Recent), the folder list, the sort
control, the location bar, selection mode, the app bar and its menus, the tap-and-hold menu and the Properties page, plus
every behaviour a source documents. Format and rigour follow `docs/plan/r8-groove-measurements.md`.

## Summary — what was and was not established

W10M's File Explorer has **no root page**. Its entry points live in a **≡ pane** (an overlay, **256 epx** wide, fill
**#171717**, **48-epx rows**, glyph centred at **24 epx**, label at **60 epx**) listing **Recent**, **This Device** and one
row per removable volume named **"<volume label> (D:)"** ("SD Card (D:)", "WININSTALL (D:)" for a USB OTG stick, "MassStore
(D:)"); the selected row is the accent at 60 % over the pane. Opening an entry shows a single page:
a **48-epx #1F1F1F location bar** holding ≡ (cx 24), a **breadcrumb** ("This Device › … › Music", 15-epx SemiBold, mixed
case, from x 60, middle segments collapsed to "…") and an **↑ up** glyph at 24 epx from the right edge; under it a
**"Sort by: Name ⌄"** line (x 12, cap top 16 epx below the bar); then **two-line rows at a 64-epx pitch** — a **colour
Windows shell icon** (yellow folder, white page, or the file's thumbnail; NOT a monochrome MDL2 glyph), the name, and a grey
detail line ("11/24/2015" for a folder, "817 KB 11/24/2015" for a file). An **Icons** view shows three large icons per row
with the name centred below. The **app bar is the standard W10M one** (48 epx, 68-epx pitch, More at 24 epx from the right,
R7 2.1.14): **Select · New folder · Icons (List in the icons view) · Search · •••**, overflow **Refresh / Select all / Clear
selection / Properties**. **Select** turns rows into a checklist ("2 items selected" replaces the sort line; selected rows
fill with accent) and swaps the bar to **Delete · Move to · Copy to · Share**. Tap-and-hold opens a flyout **Delete / Move
to / Copy to / Share / Rename / Properties** (15063, folder: no Share). **Properties is a full page** (Date modified, File
type, File size, then per-type sections such as Video and Audio). There is **no cut/copy/paste, no zip handling, no recycle
bin, no free-space line** in the app (free space lived in Settings ▸ Storage), and the root folders of This Device are
read-only (New folder is disabled there). The status bar is **shown** (24 epx), unlike the Photos app.

Three things could **not** be established. (1) **Any native-resolution phone capture.** Every phone image is either a
downscale (450x800 of a 950 XL on 15063; 338x600 of a 950 on 10586) or a camera photo; the one exact-pixel source is the
**same UWP app on desktop 1703** at 100 % (Windows Central, 2017: "identical to the version found in Windows 10 Mobile"),
which fixes epx values exactly but is a desktop window. No value here is HIGH. (2) **All motion** — no screen recording of
File Explorer on 10586+ was reachable (YouTube extraction is blocked; storyboards and thumbnails show only structure).
(3) **Progress, conflict, delete-confirmation, rename and Move-to/Copy-to picker screens** — no capture of any of them
exists in the sources reached. One version difference matters: on **10586** the sort value is drawn in the **accent colour
with no chevron**; on **15063** (phone and desktop agree) it is **white with a ChevronDown** and "Sort by:" is grey.

---

## 0. Method, sources and calibration

### 0.1 Unit convention

`epx` = 1/360 of screen width, as R8 §0.1 defines it (the Lumia 950 at 400 % is exactly 360 x 640 epx; the 950 XL at
350 % is 411 x 731 epx). Scale factors were measured, not assumed, from bars the W10M shell draws at 48 epx (nav bar,
header, app bar — R8 §1.1, R7 2.1.14). Desktop 1703 screenshots are at 100 % (title bar 32 px, header 48 px), so 1 px =
1 epx there. Only one phone scale per screen exists, so no fixed-vs-proportional call is made from phone images alone;
where the desktop window (a much wider canvas) puts an element at the same epx offset as the phone, it is called
**fixed epx**.

### 0.2 Sources

| ID | Source (URL) | Pixel size | Device / canvas | Date, app | Build | Local file |
|---|---|---|---|---|---|---|
| **F1** | AAWP "How to: Clear storage space on Windows 10 Mobile", image https://allaboutwindowsphone.com/images/features/space/space19.jpg | 450x800 (3.2x downscale of 1440x2560) | Lumia 950 XL @350 % → 411x731 epx; **1.09375 px/epx** | 2017-07-26 | **15063-era** (date) | `docs/plan/r11/src/files/F1_aawp_2017-07-26_space19.jpg` |
| **F2** | same article, https://allaboutwindowsphone.com/images/features/space/space1-final.jpg (Settings ▸ Storage, not File Explorer) | 450x800 | as F1 | 2017-07-26 | 15063-era | `docs/plan/r11/src/files/F2_aawp_2017-07-26_space1-final.jpg` |
| **F3** | Thurrott "Windows 10 Mobile Tip: Use USB Peripherals", https://www.thurrott.com/wp-content/uploads/2015/11/file-ex.jpg (fetched from https://web.archive.org/web/20170321063719im_/…; the live file is 404) | 338x600 (downscale of 1440x2560) | Lumia 950 @400 % → 360x640 epx; **0.93889 px/epx** | 2015-11-21 | ≈**10586** (date) | `docs/plan/r11/src/files/F3_thurrott_2015-11-21_file-ex.jpg` |
| **F4** | OnMSFT "Diving into the File Explorer on Windows 10 Mobile with the Lumia 950", https://www.onmsft.com/wp-content/uploads/2015/11/DSC_0035.jpg … DSC_0042.jpg (0035/0042 via i0.wp.com, 0037–0041 via web.archive.org/web/2017im_/) | 1152x768 each | **camera photos** of a Lumia 950 (perspective) | 2015-11-27 | ≈10586 | `docs/plan/r11/src/files/F4_onmsft_2015-11-27_DSC_00{35,37,38,39,40,41,42}.jpg` |
| **F5** | AAWP "W10M's File Explorer on your Surface too", https://allaboutwindowsphone.com/images/flow/misc/fileexplorers.jpg | 1000x556 | camera photo, 950 XL in landscape beside a Surface | 2017-05-11 | 15063-era | `docs/plan/r11/src/files/F5_aawp_2017-05-11_fileexplorers.jpg` |
| **F6** | Windows Central "How to enable the hidden, touch-friendly File Explorer in Windows 10", https://cdn.mos.cms.futurecdn.net/aWkVC5TN4acEFfUr6jiED6.jpg | 1322x667 | **desktop window at 100 %** (1 px = 1 epx), dark theme | 2017-05-10, the UWP app in 1703 | 15063 (desktop) | `docs/plan/r11/src/files/F6_wc_2017-05-10_desktop1703_dark.jpg` |
| **F7** | same article, https://cdn.mos.cms.futurecdn.net/BDTWXLSXt2x4aV73p2DxVT.jpg (search + sort flyout) | 1223x667 | desktop 100 %, light theme | 2017-05-10 | 15063 (desktop) | `docs/plan/r11/src/files/F7_wc_2017-05-10_desktop1703_light_sort.jpg` |
| **F8** | Windows Central "Microsoft updates universal File Explorer … 1809", https://cdn.mos.cms.futurecdn.net/8Pnnv25WZrTFjnzLPsCVqj.jpg | 2048x1261 | desktop, dark | 2018-08-21 | 17xxx — **after W10M, not governing** | `docs/plan/r11/src/files/F8_wc_2018-08-21_desktop1809.jpg` |
| **F9** | Windows Central "File Explorer in Windows 10 for phone video tour", https://cdn.mos.cms.futurecdn.net/LLnNJ77iVoSVzxPLuBwD7Y.png | 1295x1150 (two phone screenshots) | phone, Technical Preview | 2015-02-13 | preview (≈9941) — **not governing** | `docs/plan/r11/src/files/F9_wc_2015-02-13_preview_list_menu.png` |
| **F10** | same article, https://cdn.mos.cms.futurecdn.net/VUgkQdwkK7zsyWY7dXtnyB.png | 1295x1150 | as F9 | 2015-02-13 | preview — not governing | `docs/plan/r11/src/files/F10_wc_2015-02-13_preview_appbar_sort.png` |
| **D1** | Microsoft, *User Guide — Lumia with Windows 10 Mobile*, Issue 1.1 (R6 §0.2 D1), "Manage files on your phone", PDF p.125; text in R6's `docs/ug.txt` l.4156–4179 (glyphs identified from a 220-dpi render of p.125) | — | Microsoft document | © 2016 (PDF 2016-02-02) | 10586-era | not copied (cited by page/line) |
| **D3** | Microsoft, "Navigation history and backwards navigation for UWP apps" (R6 §0.2 D3, ms.date 2017-05-19) | — | Microsoft document | 2017 | 15063-era | cited |
| **T1** | OnMSFT article text (page of F4), https://www.onmsft.com/how-to/diving-file-explorer-windows-10-mobile-lumia-950 (read via web.archive.org/web/2017id_/) | — | third-party text | 2015-11-27 | ≈10586 | cited |
| **T2** | Windows Central article text (page of F6) and https://www.windowscentral.com/windows-10-universal-file-explorer (2018-08-21) | — | third-party text | 2017 / 2018 | — | cited |
| **T3** | AAWP "File Cards - universal", http://allaboutwindowsphone.com/flow/item/21150_File_Cards-universal_and_the_f.php | — | third-party text | 2015-12-24 | ≈10586 | cited |
| **G1** | Microsoft Learn, "Segoe MDL2 Assets icons", https://learn.microsoft.com/en-us/windows/apps/design/iconography/segoe-ui-symbol-font (ms.date 2025-09-02) and its glyph images `images/segoe-mdl/<code>.png` | — | Microsoft document | read 2026-09-23 | — | codepoints cited |

F1 and F2 are 3.2x downscales, so 1 px = 0.91 epx and JPEG bloom inflates text boxes by up to 1 px (R8 §0.2's caveat for
the same site). F3 is a 4.26x downscale (1 px = 1.07 epx). F4 and F5 are camera photos: structure, wording and relative
layout only; the few epx figures taken from F4 are perspective-interpolated between the 48-epx header and the 48-epx app
bar and carry ±4 epx. F6/F7 are exact in epx (100 %) but a desktop window: the pane is docked open, the content column
starts at x 257, and the app bar always shows labels (60 epx tall).

### 0.3 Calibration checks

- **F1** (950 XL): status bar 0–26.5 px = **24.2 epx**; header 26.5–79 px = **48.0 epx**; app bar 694.5–747.5 px = 48.5 epx;
  nav bar 748–800 px = 47.5 epx; app-bar glyph centres at 286.2 / 219.0 / 149.9 / 82.3 / 24.2 epx from the right edge, the
  R7 2.1.14 pattern (217.5 / 147.5 / 80.5 / 23.5 at 350 % and 400 %). 450 px / 1.09375 = 411.4 epx = the 950 XL canvas.
- **F3** (950): status bar 0–22.5 px = **24.0 epx**; header 22.5–67.5 px = **48.0 epx**; nav bar 555–600 px = 47.9 epx;
  338 / 0.93889 = 360.0 epx.
- **F6** (desktop): window border at x 0, pane x 1–256, content from x 257; title bar 0–32; header 32–80 (48 px).

### 0.4 Searched without finding

- **A native (1440-wide) phone screenshot of File Explorer on any release build.** GSMArena reviews of the Lumia 950, 950 XL,
  650 and 550 (all pages parsed; the only "file explorer" image is Continuum's desktop mode, 950 XL review p.5 shot 206);
  Windows Central Lumia 950 review (2015-11-20; phone photos only); Windows Central "Full file system access" (2015-08-24;
  a registry editor, not File Explorer); AAWP "How to: Use microSD expansion" (2015-06-23, WP8.1 Storage Sense), "How to:
  take the Lumia 735 up to branch 1709" (2019-07-02, Interop Tools), "Maps edition … 1607" (2020-01-27), "How to: Drag and
  drop files between Windows 10 Mobile and an Apple Mac" (Mac Android File Transfer windows), "How to: clear old photos and
  videos" (2 images, no File Explorer), "Seven reasons for loving W10M in 2022" (no File Explorer image); Thurrott "Windows 10
  Tip: Unlock the UWP File Explorer" (2017-05-06; its images 404 live and in the Wayback Machine); OnMSFT "A closer look at the
  new File Explorer app" (2015 preview, camera photos); MSPowerUser W10M review (Cloudflare challenge, not bypassed); Neowin
  articles (Cloudflare; Wayback copies carry only sidebar images); Wikimedia Commons (categories Windows 10 Mobile, Windows
  Phone software, Windows 10 Mobile settings, Lumia 435/650/950 XL — no File Explorer file; note for the parent: the category
  "Windows 10 Mobile camera settings" holds 16 native 2021 Camera settings screenshots).
- **Web searches** (WebSearch): "File Explorer Windows 10 Mobile app screenshot This Device folders Lumia 950 review 2016",
  "windowscentral File Explorer Windows 10 Mobile update properties select zip 2017", "mspoweruser File Explorer Windows 10
  Mobile update new UI", "Windows 10 Mobile File Explorer app screenshots Sort by This Device SD card folder list phone 2017",
  "windows phone File Explorer Windows 10 Mobile hamburger Recent This Device SD card screenshot forum", "Windows 10 Mobile File
  Explorer properties free space", "allaboutwindowsphone File Explorer Windows 10 Mobile" (3 variants), "thurrott Windows 10
  Mobile File Explorer tip", "Windows 10 Mobile review File Explorer app screenshot mspoweruser OR neowin OR winbeta".
- **Video** (search through the invidious.f5.si JSON API; no stream downloaded): "windows 10 mobile file explorer", "lumia 950
  file explorer", "windows 10 mobile file explorer creators update", "w10m file explorer sd card", "windows 10 mobile file
  explorer tutorial", "file explorer windows 10 mobile lumia", "explorador de archivos windows 10 mobile", "datei explorer windows
  10 mobile", "windows 10 mobile move files to sd card", "windows 10 mobile file explorer zip", "lumia 650 file explorer",
  "windows 10 mobile files app 10586". Candidates found (V81Xf5TCHhg Lumia 550 copy/move to SD, 10586-era; gdj_CmrEPI8 W10M
  Group 2022; h97YanAoU_k, SjTP_gxA4h4, m_KXApSFxQo, 2uKKpxJUwNU, nukqWSDJPRw 2015 previews). Their `maxresdefault` thumbnails
  show File Explorer only as a small inset or a camera shot; the storyboard API returned "Error while communicating with
  Invidious companion" for every id during this session, so no storyboard frame was used. YouTube itself (`yt-dlp`) answers
  "Sign in to confirm you're not a bot".

---

## 1. Values

Confidence: **HIGH** = native resolution agreeing across 2+ independent screenshots or two device scales (none here);
**MEDIUM** = two independent sources of different kinds agreeing within ±1 epx (e.g. the exact desktop 1703 window plus a
phone downscale), or a Microsoft document statement; **LOW** = one downscaled, camera, desktop-only or third-party-text source.
Build column: the build of the source(s); **15063** counts as governing (Q12), 10586 does not.

### 1.1 Page frame

| # | Value | Number | Confidence | Build | Derived from |
|---|---|---|---|---|---|
| 1.1.1 | Status bar | **shown**, **24 epx**, black | MEDIUM | 10586, 15063 | F1 0–26.5 px → 24.2 epx; F3 0–22.5 px → 24.0 epx. Same as R8 §1.1's 24 epx (Groove). Unlike Photos, which hides it (r11/photos.md) |
| 1.1.2 | Location bar (header) | **48 epx**, fill **#1F1F1F** (31,31,31), directly under the status bar (24 → 72 epx) | MEDIUM | 10586, 15063, desktop 15063 | F1 26.5–79 px → 24.2–72.2 epx, (31,31,31); F3 (31,31,31) right of the pane; F6 y 32–80 (48 px), (31,31,31) |
| 1.1.3 | Page background | **#000000** | MEDIUM | 15063, desktop | F1 y 600–650 px (0,0,0); F6 content (1.5,1.5,1.5) |
| 1.1.4 | App bar | **48 epx** (closed), fill **#1F1F1F**, docked on the nav bar | MEDIUM | 10586, 15063 | F1 694.5–747.5 px = 48.5 epx, (31,31,31); F3 top at 510 px = 543.2 epx = 48.0 above the nav top (591.1). R7 2.1.14 (48 epx) |
| 1.1.5 | Nav bar | 48 epx, black | MEDIUM | 10586, 15063 | F1 47.5 epx; F3 47.9 epx; R8 §1.1 |

### 1.2 Location bar contents

| # | Value | Number | Confidence | Build | Derived from |
|---|---|---|---|---|---|
| 1.2.1 | Order | **≡ · breadcrumb · (space) · ↑** | MEDIUM | 10586, 15063 | F1, F3, F4 (0035/37/38/41/42), F5 all agree |
| 1.2.2 | ≡ centre | **x 24 epx** (F3 23.96, F1 24.23), ink ≈ 21–22 epx wide, three bars | MEDIUM | 10586, 15063 | F3 x 12.78–35.15 epx; F1 13.71–34.74 epx; R8 §1.2 (cx 24.00, 20.0 epx) |
| 1.2.3 | Breadcrumb left edge | **60.3 epx** (nominal 60 = 48-epx ≡ button + 12) | LOW | 15063 | F1 "This" x 60.34 epx. R8 §1.2 title at 61.0 |
| 1.2.4 | Breadcrumb type | **mixed case, SemiBold, white**, cap **11.0 epx** (→ 15-epx class); vertically centred in the bar (cap centre 48.9 epx) | MEDIUM | 15063, desktop | F1 "D"/"M" cap 10.97 epx, (255,255,255); F6 title "D" cap 11 px at x 269 (12 px from the content edge, pane open so no ≡). Same cap as R8's "NOW PLAYING" (11.0) but not upper-cased |
| 1.2.5 | Breadcrumb separator | a thin **›** chevron, **4.6 x 8.2 epx**, colour (230,230,230), ≈6 epx gap each side | LOW | 15063 | F1 x 143.54–148.11, y 45.71–53.94 epx. 10586 photos show the same position with a ">"-like glyph (F4 0037, 0041) |
| 1.2.6 | Long paths | middle segments collapse to **"…"** ("This Device > … > clipstudio perf"); the first segment itself truncates with an ellipsis when needed ("This D… > … > lumia 950 unboxing") | LOW | 10586 | F4 0037, 0040, 0041 (camera) |
| 1.2.7 | Breadcrumb segments are tappable (jump to that folder) | documented | MEDIUM | 10586 | D1 l.4168–4169: "To quickly jump to a previous folder, tap the folder you want on the file path at the top of the screen." T1: "tap on the directory name itself" |
| 1.2.8 | Up (↑) | glyph at **24 epx from the right edge** (F1 23.3, F3 22.9, F6 24), ink ≈ 13 x 16 epx; goes to the parent folder | MEDIUM | 10586, 15063, desktop | F1 cx 388.11 of 411.43; F3 cx 337.10 of 360; F6 x 1291–1303, y 48–63. Behaviour: T1 "tap the 'up arrow' button on the top right" |
| 1.2.9 | Up at a volume root | drawn **dimmed**, (123,123,123) ≈ #7B7B7B, when there is no parent | LOW | desktop 15063 | F6 at "DVD Drive (D:)" root. Phone: not determinable (F4 0035 at This Device is a camera photo) |

### 1.3 The ≡ pane (the app's "root")

| # | Value | Number | Confidence | Build | Derived from |
|---|---|---|---|---|---|
| 1.3.1 | Structure | **no root page**: the ≡ pane lists the entry points; choosing one shows its folder page | MEDIUM | 10586, 15063 | F3, F4 0039, F5 (landscape rail); T1 "the hamburger menu … allows you to access a list of recently accessed or downloaded files, the storage hierarchy for this device, and the storage hierarchy for your microSD card"; D1 l.4164 "Tap This Device, and browse to the folder you want." |
| 1.3.2 | Rows, in order | **Recent · This Device · <removable volume>** (one row per volume) | MEDIUM | 10586, 15063 | F3 (Recent / This Device / WININSTALL (D:)); F4 0039 (Recent / This Device / SD Card (D:)); F5 rail (clock / phone / SD) |
| 1.3.3 | Volume row label | **"<volume label> (<letter>:)"** — "SD Card (D:)", "WININSTALL (D:)" (a USB OTG stick, same SD glyph), "MassStore (D:)" | LOW | 10586, 15063 | F4 0039; F3 (Thurrott's USB article: the USB drive is drawn as a volume row); F5 (location bar "MassStore (D:)") |
| 1.3.4 | Pane form (portrait) | **overlay**: slides over the page from the left, covering the location bar and the app bar; only ≡ shows when closed | LOW | 10586 | F3: pane fill runs from 22.5 px (24.0 epx) to the nav bar (555 px), over the header and app bar; the page's app bar is visible only right of the pane |
| 1.3.5 | Pane width | **256 epx** | MEDIUM | 10586, desktop 15063 | F3 right edge 240.5 px = 256.1 epx; F6 pane x 1–256 = 256 px. Same as R7 3.1 Cortana ≡ pane (256 epx) and close to R3 C5 Weather (253) |
| 1.3.6 | Pane fill | **#171717** (23,23,23) | MEDIUM | 10586, desktop | F3 (23,23,23); F6 (23,23,23) |
| 1.3.7 | First row top | directly under the ≡ band: **72 epx** (the pane's top 48 epx holds the ≡) | LOW | 10586 | F3 selected row 67.5–112.5 px = 71.9–119.8 epx |
| 1.3.8 | Row height | **48 epx** | MEDIUM | 10586, desktop | F3 row centres 96.4 / 143.3 / 190.7 epx (pitch 46.9–47.4, ±1.07 per px) and the selected fill 47.9 epx; F6 rows 32–80 and 80–128 px |
| 1.3.9 | Row glyph | centred at **x 24 epx** (under the ≡), ink ≈ 10–15 epx | MEDIUM | 10586, desktop | F3 glyph spans 17.0–32.0, 19.2–28.8, 18.1–29.8 epx (cx 24.0–24.5); F6 x 19–30 (cx 24.5) |
| 1.3.10 | Row label | left edge **60 epx**, 15-epx body, white | MEDIUM | 10586, desktop | F3 59.6–60.7 epx; F6 "T" at x 61, cap 11 px |
| 1.3.11 | Selected row | **accent at 60 % over #171717** | MEDIUM | 10586, desktop | F6 (9,81,139) vs predicted 0.6 x #0078D7 + 0.4 x (23,23,23) = (9,81,138), exact; F3 (11,69,115) = the same rule with the W10 palette's #0063B1. R3 C5 Weather pane (16,68,110) consistent |
| 1.3.12 | Landscape | the pane becomes a **48-epx compact rail** of glyphs only | LOW | 15063 | F5 (camera, landscape) — the shell is portrait-locked, so recorded only |
| 1.3.13 | Default landing entry | **UNMEASURED** — F3 shows Recent selected with the pane open, F4 0039 This Device; neither shows a cold start | UNMEASURED | — | see UNMEASURED-4 |

### 1.4 Sort control

| # | Value | Number | Confidence | Build | Derived from |
|---|---|---|---|---|---|
| 1.4.1 | Form (15063) | **"Sort by: Name ⌄"** — "Sort by:" grey **(160,160,160) ≈ #A0A0A0**, the value **white**, a thin ChevronDown (204,204,204) after it | MEDIUM | 15063 phone + desktop | F1 (159 / 254 / 200); F6 (160 / 255 / 204) |
| 1.4.2 | Form (10586) | "Sort by: **Name**" with the value in the **accent colour** and **no chevron** | LOW | 10586 | F4 0035, 0038, 0042 (camera) |
| 1.4.3 | Position | left **12 epx** (ink 13.7 epx incl. the "S" side bearing); cap top **16 epx below the bar** (88.7 epx on the phone) | MEDIUM | 15063, desktop | F1 "S" x 13.71, cap top 88.69 epx (bar bottom 72.23 → 16.46); F6 "S" x 270 (13 from the content edge), cap top 96 (80 → 16) |
| 1.4.4 | Type size | cap **11 epx** → 15-epx class | MEDIUM | 15063, desktop | F1 "N" 10.97 epx; F6 "S"/"N" 11 px |
| 1.4.5 | Sort keys | **Name, Size, Date** (plus **Relevance** while searching) | MEDIUM | 10586 doc, desktop 15063 | D1 l.4166–4167 "(Name, Size, or Date)"; F7 flyout. There is **no "Type" sort** |
| 1.4.6 | Picker form | desktop 15063: a **menu flyout** under the label, 7 items (Relevance; Name (A on top); Name (Z on top); Size (Smallest on top); Size (Largest on top); Date (Oldest on top); Date (Newest on top)), current item in accent. Phone picker form and item pitch: UNMEASURED (the 2015 preview used a full-screen "SORT BY" page, F10 — not governing) | LOW | desktop 15063 | F7 flyout y 178–425 px, 32-px mouse pitch — desktop-specific, not transferable (phone flyouts are 44 epx, R7 2.2.5) |

### 1.5 Folder list — list view

| # | Value | Number | Confidence | Build | Derived from |
|---|---|---|---|---|---|
| 1.5.1 | Row form | **two lines**: icon · name / detail; single column on the phone | MEDIUM | 10586, 15063 | F4 0035/0037/0042; F5; F6 (three columns only because the desktop window is wide) |
| 1.5.2 | Row pitch | **64 epx** | MEDIUM | desktop 15063 + 10586 | F6 icon tops 135 / 199 / 263 px (64 exact); F4 0035 ≈64 ± 4 epx after perspective interpolation. Equals R3 C1's 64-epx two-line list item |
| 1.5.3 | Icon | a **colour Windows shell icon** (yellow folder, white page, gear page, installer…) or the file's **thumbnail** (photos, videos) — **not** a monochrome MDL2 glyph; box ≈ **32 x 40 epx** | LOW | 10586, desktop | F4 0035 (folders), 0037/0040 (thumbnails); F6 icon bbox 34 x 42 px incl. JPEG bloom |
| 1.5.4 | Icon left / name left | icon **20 epx**, name **72 epx** from the content edge | LOW | desktop 15063 | F6 icon x 277, name x 329 (content edge 257). F4 0035 puts the name ≈61 epx right of the sort text (≈74 epx), consistent within ±4 |
| 1.5.5 | First row | icon top **55 epx** below the location bar; name ascender top 5 epx below the icon top; detail digits 29 epx below | LOW | desktop 15063 | F6 icon top 135, name 140, detail 164 (bar bottom 80) |
| 1.5.6 | Name | 15-epx class (x-height 9 px, ascender 13 px incl. bloom), **white** | LOW | desktop 15063 | F6 "s"/"a" x-height 9, "b" 13 |
| 1.5.7 | Detail line | 15-epx class (digit height 11 px incl. bloom), grey **(165,165,165) ≈ #A5A5A5** | LOW | desktop 15063 | F6 "3/18/2017" top-10 sample (165) |
| 1.5.8 | Detail content | folder: **date only** ("11/24/2015"); file: **size then date** ("817 KB 11/24/2015", "1.21 GB", "128 bytes 2/10/2017"); size to 3 significant figures in bytes / KB / MB / GB; date in the phone's short-date format (en-GB "21/08/2015") | MEDIUM | 10586, 15063 | F4 0037/0040; F5; F6 |
| 1.5.9 | Base folders of This Device | **Documents, Downloads, Music, Pictures, Ringtones, Videos**; they cannot be renamed and **New folder is disabled** at the This Device root | LOW | 10586 | F4 0035 (list), 0038 ("New folder" drawn dim); T1 "doesn't allow you to modify device's base folder; You cannot add folders or change the names of the folders" |

### 1.6 Folder list — icons view

| # | Value | Number | Confidence | Build | Derived from |
|---|---|---|---|---|---|
| 1.6.1 | Form | **three icons per row**, icon ≈ **67 x 84 epx** (large shell icon), name centred **below** the icon | LOW | 15063 | F1 icon bands x 36.6–103.3 / 172.8–240.5 / 309.9–376.7, y 122.5–206.6 epx |
| 1.6.2 | Column centres | 69.9 / 206.6 / 343.3 epx = pitch **136.7 epx ≈ W/3** (137.1 on the 411 canvas) | LOW | 15063 | F1 as above; one scale only, so W/3 vs fixed is not decided |
| 1.6.3 | Label | 12-epx class (ascender 9.1 epx), (237,237,237), top 12.8 epx below the icon | LOW | 15063 | F1 "Audiobooked" y 219.43–228.57, centred at 69.0 (icon centre 69.9) |
| 1.6.4 | Toggle | the app bar's third button switches the view: labelled **Icons** (2x2 glyph) in the list view; shows a **list** glyph in the icons view | MEDIUM | 10586, 15063 | F4 0038 label "Icons"; F6 label "Icons"; F1 (icons view) shows the BulletedList glyph in that slot |

### 1.7 App bar, expanded bar and overflow menu

| # | Value | Number | Confidence | Build | Derived from |
|---|---|---|---|---|---|
| 1.7.1 | Buttons (normal mode) | **Select · New folder · Icons · Search · •••** | MEDIUM | 10586, 15063 | F4 0038 (labels), F6 (labels), F1, F3 (Search, •••) |
| 1.7.2 | Positions | glyph centres **286 / 218 / 150 / 82 epx from the right edge**, More dots at **24**: the fixed-epx W10M pitch (68 epx + a 48-epx More) | MEDIUM | 15063 phone + desktop | F1 286.2 / 219.0 / 149.9 / 82.3 / 24.2; F6 1035 / 1102.5 / 1170.5 / 1239 / 1297 px → 286 / 218.5 / 150.5 / 82 / 24 from x 1321. R7 2.1.14 |
| 1.7.3 | Glyph box | **20 epx**; closed-bar glyph centre **24 epx below the bar top** | MEDIUM | 15063, desktop | F1 glyph heights 14.6–20.1 epx, cy 658.3–659.2 (bar top 635.4 → +23–24); F6 glyphs 20 px, cy 630 (top 606 → +24) |
| 1.7.4 | Expanded bar | **60 epx**, glyph centre **+24**, label centre **+46.5** from its top, labels 12-epx class | LOW | desktop 15063 | F6 bar 606–666, labels 648–657. Same geometry as the Photos expanded bar (r11/photos.md) and R7 2.1.15 |
| 1.7.5 | Overflow (normal mode) | **Refresh · Select all · Clear selection · Properties**; items that cannot apply are dimmed (Clear selection with nothing selected) | MEDIUM | 10586, desktop 15063 | F4 0038; F6 menu, dim item (137,137,137) |
| 1.7.6 | Overflow panel | fill **#2B2B2B** (43,43,43); text inset 12 epx; phone item pitch 44 epx (R7 2.2.5); the desktop's 32-px pitch is a mouse layout | LOW | desktop 15063 | F6 panel (43,43,43), items at 480 / 512 / 544 / 576 px, text x 1174 (panel 1162) |
| 1.7.7 | Disabled text | **(137,137,137) ≈ #898989** on #2B2B2B | LOW | desktop 15063 | F6 "Clear selection" |

### 1.8 Selection mode

| # | Value | Number | Confidence | Build | Derived from |
|---|---|---|---|---|---|
| 1.8.1 | Entry | **Select** in the app bar; exit by unchecking everything or by **Back** | LOW | 10586 | T1 "The Select command converts the view into something of a checklist … hit your device's back button" |
| 1.8.2 | Count line | **"<n> items selected"** replaces the "Sort by" line in the same position | LOW | 10586 | F4 0037 ("2 items selected") |
| 1.8.3 | Rows | a **checkbox** appears at the row's left and the icon and text shift right; **selected rows fill with accent, full width, seamless across adjacent rows**; unselected rows keep the black page | LOW | 10586 | F4 0037 (camera). The measured W10M pattern for the same control is R7 1.3.9 (Phone history: 20.3-epx checkbox at x 22.2, content shifted 32 epx, accent fill) |
| 1.8.4 | App bar in selection | **Delete · Move to · Copy to · Share · •••**; Share dims when a folder is in the selection | LOW | 10586 | F4 0037 (labels shown, Share dim with a folder selected); D1 l.4174–4179 (select, then move / copy / share / delete) |
| 1.8.5 | Overflow in selection | **Select all · Clear selection · Rename · Properties** (Rename and Properties dim with 2 items) | LOW | 10586 | F4 0037 |

### 1.9 Tap-and-hold menu

| # | Value | Number | Confidence | Build | Derived from |
|---|---|---|---|---|---|
| 1.9.1 | Trigger | tap and hold a file or folder (right-click with a mouse) | MEDIUM | 10586 | D1 l.4173 "Tap and hold the file or folder you want, and select what you want to do with it."; T1 |
| 1.9.2 | Items | file (10586): **Delete · Move to · Copy to · Share · Rename · Properties**; folder (15063): **Delete · Move to · Copy to · Rename · Properties** | LOW | 10586, 15063 | F4 0040; F1 |
| 1.9.3 | Box | **240.5 x 236.8 epx** (5 items), fill **#2B2B2B**, 1-px grey border (≈(95–115) after downscale) | LOW | 15063 | F1 x 170.06–410.51, y 215.77–452.57 epx. R7 2.2.5 flyout width 242.6 ± 0.5 (HIGH) agrees within the 1-px downscale error |
| 1.9.4 | Item pitch / inset | **43.9 epx** pitch (R7 2.2.5: 44), text inset **13.7 epx** from the box edge (R7: 14), first cap top 24.7 epx below the box top | LOW | 15063 | F1 cap tops 240.46 / 285.26 / 329.14 / 373.03 / 416.91 epx; text x 183.77 |
| 1.9.5 | Placement | top edge just below the held item's icon; clamped **1 epx inside the screen's right edge** | LOW | 15063 | F1 (held item = second icon, centre 206.6 epx; box right 410.5 of 411.4) |

### 1.10 Properties page

| # | Value | Number | Confidence | Build | Derived from |
|---|---|---|---|---|---|
| 1.10.1 | Form | a **full page**, not a dialog: the location bar shows the breadcrumb ending in the item's name; **no app bar** | LOW | 10586 | F4 0041 (camera); T1 "The properties button invokes a screen showcasing the file's size and metadata" |
| 1.10.2 | Head | thumbnail (≈95 epx square, camera estimate) at the left, name to its right, top-aligned | LOW | 10586 | F4 0041 |
| 1.10.3 | Rows | two columns — grey label ("Date modified:", "File type:", "File size:"), white value starting near mid-width (≈W/2, ±15 epx) | LOW | 10586 | F4 0041 |
| 1.10.4 | Sections | per-type sections with a SemiBold header and a thin full-width rule under it: **Video** (Length, Frame width, Frame height, Data rate, Total bitrate, Frame rate) and **Audio** (Bit rate) for a video file | LOW | 10586 | F4 0041 |
| 1.10.5 | Value formats | "00:07:52", "1920", "20619kbps", "59 frames/second", "MOV File", "1.21 GB", "11/24/2015" | LOW | 10586 | F4 0041 |

### 1.11 Search

| # | Value | Number | Confidence | Build | Derived from |
|---|---|---|---|---|---|
| 1.11.1 | Scope | searches **the folder you are in** (and below) | MEDIUM | 10586 | D1 l.4165: "To search the folder you're currently in, tap [search]." |
| 1.11.2 | Search field | a text box **32 epx** tall, **12 epx below the location bar**, **12-epx side margins**, 2-epx grey border (153) in light theme; the sort line moves below it and reads **"Sort by: Relevance"** | LOW | desktop 15063 | F7 box y 92–123 px, x 269–1209 (content 257–1221). Height equals R3 A14's 32-epx app-list search box |

### 1.12 Behaviour

| # | Value | Number | Confidence | Build | Derived from |
|---|---|---|---|---|---|
| 1.12.1 | Tap a folder | opens it (the page content is replaced; the breadcrumb gains a segment) | LOW | 10586 | T1 "Tapping on a directory opens that directory." |
| 1.12.2 | Tap a file | opens it in its default app; **no "open with" choice** | LOW | 10586 | T1 "there doesn't yet seem to be any way to control which app the file opens with" |
| 1.12.3 | Go to the parent | ↑ in the location bar, or a breadcrumb segment | MEDIUM | 10586 | T1; D1 l.4168–4169 |
| 1.12.4 | Hardware Back | exits selection mode (documented); otherwise the UWP rule — "navigate to the previous location in the app's navigation history", and with an empty in-app stack "the system might navigate to the previous app … or to the Start screen" | LOW | 10586, 15063 doc | T1 (selection); D3 (generic UWP rule). What File Explorer pushes on its stack (each folder visited? each pane choice?) is UNMEASURED-3 |
| 1.12.5 | Move to / Copy to | File Explorer itself serves as the folder picker (Edge downloads open it to choose a location) | LOW | 10586 | T1 "saving files from the Edge browser will also invoke this app, allowing you to choose where you want to store it". Picker visuals UNMEASURED-2 |
| 1.12.6 | Clipboard | **none** (no cut / copy / paste; Move to / Copy to instead). Cut and paste arrived only in the 1809 desktop build (F8), after W10M | LOW | 10586, 1809 | T3 "doesn't even support clipboard functions"; T2 (2018: "New cut option", "Improved copy/paste"; "Microsoft has never updated this app before") |
| 1.12.7 | Zip | **no zip handling** in any source; the user guide mentions zips only as files the phone "doesn't recognize" | LOW | 10586 | D1 l.4115–4117; no capture shows a zip opened |
| 1.12.8 | Recycle bin | **none**; D1's delete is "select … and tap [delete]" with no restore path | MEDIUM | 10586 | D1 l.4178–4179 |
| 1.12.9 | Recent | "recently accessed or downloaded files" | LOW | 10586 | T1; F3 shows the Recent page behind the pane with a one-line empty state ending "…ently." (string UNMEASURED-5) |

### 1.13 Free space

| # | Value | Number | Confidence | Build | Derived from |
|---|---|---|---|---|---|
| 1.13.1 | In File Explorer | **no free-space line** on any page | LOW | 10586, 15063 | F4 0035 (This Device root), F1 (a folder), F3 (pane) — none shows one |
| 1.13.2 | Where W10M showed it | **Settings ▸ Storage ▸ This Device / SD Card (D:)**: "Storage usage", "Total: 29.1 GB", a full-width accent bar, "Used: 18.6 GB" / "Remaining: 10.4 GB", then per-category rows (glyph at x 11–36, bar from 51 to 389 epx, row pitch ≈59.6 epx) | LOW | 15063 | F2 bands y 111.5 (title), 154.5 (refresh line, accent), 183.8 (Total), 207.5 (bar), 233.1 / 261.5 (Used / Remaining), rows 311.8 / 371.2 / 431.5 / 491.0 epx. This is phase 19's page; recorded only as the pattern a P4 free-space line could borrow |

### 1.14 Version differences

| # | Value | Number | Confidence | Build | Derived from |
|---|---|---|---|---|---|
| 1.14.1 | 10586 → 15063 | sort value **accent, no chevron → white with ChevronDown**, "Sort by:" grey; tap-and-hold menu for a **folder** has no Share (15063; 10586 menu shown is for a file). No other visible change in the sources | LOW | 10586, 15063 | F4 vs F1/F6 |
| 1.14.2 | 2015 Technical Preview (not governing) | WP8.1-style circled app-bar glyphs with lowercase labels ("select", "thumbnails", "search"), a docked icon rail, lowercase menus ("delete, move, copy, share, rename, properties"; "refresh, select all, clear selection"), a full-screen "SORT BY" page ("Name (A to Z)" … "Date (old to new)"), detail line "184.62 KB 1/28/15" | LOW | preview | F9, F10 — shows where release wording came from; not to be built |
| 1.14.3 | 1809 desktop (after W10M, not governing) | buttons moved to the top, context menu **Delete · Cut · Copy · Share · Rename · Set as background · Properties**, breadcrumb "This PC › … › Zac › Documents › Work" | LOW | 1809 | F8; T2 |

---

## 2. Strings as shipped

en-US unless noted. "(dim)" = drawn disabled in the capture.

| Where | String | Source |
|---|---|---|
| App name | File Explorer | D1 l.4163; F5 tile "File Explorer" (AAWP fe-screen) |
| Pane rows | Recent · This Device · SD Card (D:) (volume label + drive letter, e.g. WININSTALL (D:), MassStore (D:)) | F3, F4 0039, F5 |
| Location bar | This Device › Music · This Device > … > clipstudio perf · This D… > … > lumia 950 unboxing | F1, F4 |
| Sort line | Sort by: Name (15063: "Sort by:" grey, value white, ⌄) · Sort by: Relevance (while searching) | F1, F6, F7 |
| Sort menu (15063) | Relevance · Name (A on top) · Name (Z on top) · Size (Smallest on top) · Size (Largest on top) · Date (Oldest on top) · Date (Newest on top) | F7 |
| Sort keys (doc) | Name, Size, or Date | D1 l.4166–4167 |
| App bar | Select · New folder · Icons · Search | F4 0038, F6 |
| App bar overflow | Refresh · Select all · Clear selection (dim) · Properties | F4 0038, F6 |
| Selection line | 2 items selected | F4 0037 |
| Selection app bar | Delete · Move to · Copy to · Share (dim with a folder selected) | F4 0037 |
| Selection overflow | Select all · Clear selection · Rename (dim) · Properties (dim) | F4 0037 |
| Tap-and-hold (file, 10586) | Delete · Move to · Copy to · Share · Rename · Properties | F4 0040 |
| Tap-and-hold (folder, 15063) | Delete · Move to · Copy to · Rename · Properties | F1 |
| Properties | Date modified: · File type: · File size: · Video · Length · Frame width · Frame height · Data rate · Total bitrate · Frame rate · Audio · Bit rate | F4 0041 |
| Properties values | 11/24/2015 · MOV File · 1.21 GB · 00:07:52 · 20619kbps · 59 frames/second · 1534kbps | F4 0041 |
| Row details | 11/24/2015 · 817 KB 11/24/2015 · 555 MB 11/24/2015 · 21/08/2015 (en-GB) · 128 bytes 2/10/2017 | F4, F5, F6 |
| Base folders | Documents · Downloads · Music · Pictures · Ringtones · Videos | F4 0035; T1 |
| User guide (D1 l.4156–4179) | "Manage files on your phone" · "1. Tap File Explorer. 2. Tap This Device, and browse to the folder you want. 3. To search the folder you're currently in, tap [search]. 4. To change how the files or folders are arranged, tap the current sorting method (Name, Size, or Date), and select a new method. 5. To quickly jump to a previous folder, tap the folder you want on the file path at the top of the screen." · "Create a new folder: Tap [new folder], and write a name." · "Move or copy a file or folder to another location: Tap and hold the file or folder you want, and select what you want to do with it." · "Tip: To move or copy several files or folders at once, tap [select], select the files or folders you want, and tap [move to] to move or [copy] to copy them." · "Share a file or folder: Tap [select], select what you want to share, tap [share], and select how you want to share." · "Delete a file or folder: Tap [select], select what you want to delete, and tap [delete]." | D1 |
| Recent empty state | "…ently." (only the last word is visible behind the pane) | F3 — full string UNMEASURED-5 |

---

## 3. Segoe MDL2 glyphs the shell's icon font needs

Codepoints read from G1's table; each identity was checked by comparing G1's glyph image with the captured glyph (F1, F3,
F4, F6) and with the icons in D1 p.125 (220-dpi render). File and folder icons are **not** MDL2 (1.5.3); MDL2 Folder E8B7 /
Page E7C3 are listed only as the monochrome fallback if the shell draws no colour type icons.

| # | Use | MDL2 name | Codepoint | Confidence | Evidence |
|---|---|---|---|---|---|
| 3.1 | ≡ in the location bar | GlobalNavigationButton | E700 | MEDIUM | F1, F3 three bars; G1 |
| 3.2 | Up | Up | E74A | MEDIUM | F1, F3, F6 arrow; G1 |
| 3.3 | Pane: Recent | Recent | E823 | LOW | F3 clock glyph; F5 rail |
| 3.4 | Pane: This Device | CellPhone | E8EA | LOW | F3, F4 0039, F6 ("This PC" uses a PC glyph on desktop) |
| 3.5 | Pane: SD card / USB volume | SDCard | E7F1 | LOW | F3 (USB stick drawn with it), F4 0039, F5 |
| 3.6 | Select | MultiSelect | E762 | MEDIUM | F1, F6, D1 p.125 |
| 3.7 | New folder | NewFolder | E8F4 | MEDIUM | F1, F6, D1 p.125 ("Create a new folder: Tap [icon]") — a portrait folder outline with a + at the lower left |
| 3.8 | Icons view toggle | ViewAll | E8A9 | LOW | F4 0038, F6 (2x2 squares) |
| 3.9 | List view toggle (shown in the icons view) | BulletedList | E8FD | LOW | F1 third button |
| 3.10 | Search | Search | E721 | MEDIUM | F1, F3, F6, D1 |
| 3.11 | More | More | E712 | MEDIUM | F1, F3, F6 |
| 3.12 | Delete | Delete | E74D | LOW | F4 0037; D1 p.125 |
| 3.13 | Move to | MoveToFolder | E8DE | LOW | F4 0037 (folder with an arrow); D1 p.125 "tap [icon] to move" |
| 3.14 | Copy to | Copy | E8C8 | LOW | F4 0037; D1 p.125 "[icon] to copy" |
| 3.15 | Share | Share | E72D | LOW | F4 0037; D1 p.125 |
| 3.16 | Selection checkbox (off / on) | Checkbox / CheckboxComposite | E739 / E73A | LOW | F4 0037; R7 1.3.9 |
| 3.17 | Sort chevron | ChevronDown | E70D | LOW | F1, F6 thin chevron |
| 3.18 | Breadcrumb separator | ChevronRight (if not a text "›") | E76C | LOW | F1 4.6 x 8.2-epx chevron; whether it is a glyph or a text character is not determinable |
| 3.19 | P4 rows the phase adds | Refresh E72C, SelectAll E8B3, ClearSelection E8E6, Rename E8AC, Info E946 (Properties), USB E88E (a separate USB glyph, which W10M did **not** use), History E81C | — | — | G1 only; W10M showed these as menu text, not glyphs (not counted) |

---

## 4. Motion (RV11)

No 60-fps (or any) screen recording of File Explorer on 10586+ was reachable (§0.4), so every motion value is UNMEASURED.
Each gets a proposed tagged approximation from a measured W10M pattern for the same control type.

| # | Motion | Status | Proposed tagged approximation | Pattern it derives from |
|---|---|---|---|---|
| 4.1 | ≡ pane open | UNMEASURED | slide in from the left, ease-out, **250 ms** after the first frame | R7 3.1.10 (Cortana ≡ pane, 256 epx, 250 ms ease-out); R3 C5 Weather pane 133 ± 33 ms at 30 fps is the lower bound |
| 4.2 | Pane close on choosing an entry | UNMEASURED | **no slide-out**: pane and page replaced in one frame, then the new page fades in | R7 3.1.11 (HIGH) + 3.2.2 |
| 4.3 | Folder → subfolder, ↑ and breadcrumb jumps | UNMEASURED | content cut, then fade-in **200–317 ms** ease-out (use 250 ms); location bar and app bar do not move | R7 3.2.2 page fade-in (HIGH); phase 18's Y5 stand-in (250 ms, X13) already matches |
| 4.4 | App bar ••• expand / collapse | UNMEASURED | **317 ms** ease-out (47 % in the first frame) | R7 2.1.16 |
| 4.5 | Tap-and-hold menu open | UNMEASURED | pressed-row fill at **300 ms**, menu first frame at **700 ms**; box grows **200–233 ms** ease-out; dismiss fade 67–83 ms | R7 3.6.5, 2.2.6, 3.6.4, 3.6.6 |
| 4.6 | Pressed row fill | UNMEASURED | row box fills (50,50,50) while the finger is down | R7 2.2.1 |
| 4.7 | Enter / leave selection mode (checkbox column, content shift) | UNMEASURED | a one-frame layout change, no slide | R7 1.8.2 (Phone tab switch: one-frame cut) and R7 2.2.7 (delete: one-frame row removal); no measured W10M checkbox animation exists |
| 4.8 | List ↔ Icons toggle | UNMEASURED | one-frame cut | as 4.7 |
| 4.9 | Sort picker open | UNMEASURED | as 4.5's box growth (a flyout) | R7 2.2.6 |

---

## UNMEASURED

| # | What | Why not | Proposed tagged approximation (and its source pattern) |
|---|---|---|---|
| 1 | **Native-resolution geometry of any File Explorer page on the phone** (every phone figure above is from a 3.2x/4.3x downscale or a camera photo) | no 1440-wide phone capture exists in any source reached (§0.4) | use the values above: the desktop 1703 window (F6/F7) is exact in epx for the same UWP app, and every value that the phone downscale also shows agrees within ±1 epx (1.2.4, 1.3.5, 1.4.1, 1.4.3, 1.7.2) |
| 2 | **Move to / Copy to picker, progress, name conflict, delete confirmation, rename, new-folder name entry** | not captured anywhere; D1 describes only the taps | picker = the folder page itself in a picker mode (T1) with a bottom confirm bar in the app-bar geometry (1.7); dialogs = R7 1.3.9's top-anchored W10M dialog (full width, (74,74,74), title / body / two side-by-side buttons) — phase 18's Y4 "phase 03 card idiom" should switch to this measured W10M dialog form |
| 3 | **Hardware Back inside folders** (history vs parent; what leaves the app) | no footage; D3 gives only the generic UWP rule | Back = previous location in history (D3); a straight descent makes that equal to "up one level", which is phase 18's Y3; after a breadcrumb jump Back returns to the folder left, not to its parent. Leaves the app when the stack is empty (D3) |
| 4 | **Which entry the app opens on** (Recent or This Device) and whether it remembers the last folder | F3 and F4 0039 show different selections, neither at a cold start | This Device (D1 l.4164 walks through This Device first); Recent is one tap away in the pane |
| 5 | **Recent page content and empty-state wording** | F3 shows only "…ently." behind the pane | list rows as 1.5 sorted newest first; empty state as a left-aligned line in R7 3.5.9's form (white, subtitle class with cap 14.7 epx, left 11.7 epx, cap top 70.4 epx) with wording written for the shell |
| 6 | **Phone sort picker form and item pitch** | the only release capture of the picker is desktop (F7, 32-px mouse pitch) | a menu flyout under the "Sort by" line, 242.6 epx wide, 44-epx items, 14-epx inset (R7 2.2.5, HIGH), current item in accent (F7) |
| 7 | **Selection-mode geometry on File Explorer** (checkbox size, x, content shift) | F4 0037 is a camera photo | R7 1.3.9 (Phone history): 20.3-epx checkbox centred on the row at x 22.2 epx, content shifted 32 epx, selected rows accent-filled full width |
| 8 | **Icons-view second-row pitch and label wrapping** | F1's menu covers everything below the first row | cells W/3 wide (1.6.2); row pitch = icon 84 + label ≈ 22 + gap 12 ≈ 118 epx (derived from F1's first row only) |
| 9 | **Light-theme rendering on the phone** | every phone source is dark theme | desktop light theme (F7): header (230,230,230), page white, border (153) — a desktop reading only |
| 10 | **All motion** (§4) | no recording | the §4 table |

---

## Gaps for the phase doc

Mapped onto `docs/plan/phase-18-files.md`'s approximation rows (Y1–Y6) and H rows.

- **Y1 (root page) — the stand-in is the wrong shape.** W10M had no root page with two-line rows at 64 epx and a 30-epx
  glyph. The measured form is the **≡ pane** (1.3): 256 epx overlay, #171717, 48-epx rows, glyph at cx 24, label at x 60,
  Recent / This Device / "<label> (D:)" per volume, selected row = accent at 60 %. A USB OTG drive was drawn as a volume row with
  the **SD-card glyph** (F3) — relevant to phase 18's "SD card / USB" naming (W10M used the volume label, not "USB"). The
  Recycle Bin (Q3 C) has no W10M original: a fourth pane row is the least-invented placement (P4, H2). There is **no
  free-space line** in the app (1.13); if phase 18 keeps one it is a P4 design, and Settings ▸ Storage (F2) is the pattern to
  borrow. H4 closes for the pane rows against 1.3; the free-space line moves to an *accept* row.
- **Y2 (folder rows, glyphs, check, app bar) — mostly measured, one conflict.** Rows are **64-epx two-line rows** (1.5), not the
  44-epx app-list rows (R3 C2) the stand-in names. **Type icons were colour Windows shell icons and thumbnails, not MDL2
  glyphs** (1.5.3) — phase 18's scope line "W10M glyphs by type" should read "W10M-style type icons", and the icon art is an
  A10 branding-module asset (the shell needs its own folder / page / archive / audio / video icons; MDL2 Folder E8B7 / Page
  E7C3 are the monochrome fallback). App bar: Select E762 · New folder E8F4 · Icons E8A9 / List E8FD · Search E721 · More E712
  at the fixed 68-epx pitch (1.7.2); selection bar Delete E74D · Move to E8DE · Copy to E8C8 · Share E72D (1.8.4); overflow
  menus as §2. The location bar with breadcrumb and ↑ (1.2) and the "Sort by: Name ⌄" line (1.4) are not in any Y row today and
  should be added as measured values. Selection geometry: R7 1.3.9 (UNMEASURED-7).
- **Sort by type (E3) is an addition.** W10M sorted by Name, Size and Date only (1.4.5); "type" is a P4 extension or should be
  dropped from E3.
- **Zip (Q2 C) and the Recycle Bin (Q3 C) are P4 in full** (1.12.7, 1.12.8): no W10M screen to measure. Zip's virtual root can
  reuse the folder page unchanged; its extract / create flows need the Y4 dialogs.
- **Cut / copy / paste did not exist** (1.12.6): W10M's verbs are "Move to" and "Copy to" (then pick a folder). Phase 18's copy /
  move rows match that wording if they keep it; a clipboard model would be a P4 departure.
- **Y3 (Back) — partly documented.** Back leaves selection mode (T1); ↑ and breadcrumb segments go up; beyond that D3's
  history rule (UNMEASURED-3). Phase 18's "Back goes up one level; leaves at a root" matches only for straight descents; note
  the breadcrumb-jump case in its edge cases.
- **Y4 (progress / conflict / properties) — Properties is measured as a page, not a dialog** (1.10: breadcrumb ending in the name,
  thumbnail + name, grey-label / white-value rows, per-type sections, no app bar). Progress, conflict, delete confirmation and
  rename are UNMEASURED-2; the measured W10M dialog form to derive them from is R7 1.3.9's top-anchored dialog, not phase 03's
  card idiom (H5 stays an *accept* row).
- **Y5 (motion)** — UNMEASURED throughout; §4 gives a source pattern per motion (H3 stays).
- **Status bar.** File Explorer drew the 24-epx status bar (1.1.1), as Groove did (R8 §1.1); the shell's shell-wide 28 epx (R3 C4)
  is a deliberate substitution already recorded for phase 10 — no change, but E11 should compare the header at 28 + 48.
- **E11** can now be written against 1.1–1.10 with LOW / MEDIUM tolerances: ±1 epx for MEDIUM rows (desktop-exact values),
  ±2 epx for LOW phone-downscale rows, structure-only for camera rows.
- **Removable-volume letter.** W10M always showed "(D:)"; Android volumes have no letters. Recording the label plus a letter is
  pure imitation — a naming call for phase 18 (label only, or label + "(D:)").

## Tally

Counted from the Confidence column of §1 and §3 (a row with two levels counts at the lower), plus the §4 motion rows and
row 1.3.13, all UNMEASURED:

| | HIGH | MEDIUM | LOW | UNMEASURED | Rows |
|---|---|---|---|---|---|
| §1 Values | 0 | 34 | 49 | 1 | 84 |
| §3 Glyphs | 0 | 6 | 12 | 0 | 18 |
| §4 Motion | 0 | 0 | 0 | 9 | 9 |
| **Total** | **0** | **40** | **61** | **10** | **111** |

(§3's row 3.19 lists P4 glyphs with no capture and carries no confidence; it is not counted. The UNMEASURED table's ten items
overlap these rows and are not counted twice.)

## Sources

Images (third-party; unaltered originals, kept local — `docs/plan/r11/src/` is gitignored):

| Local file | Source URL | Fetched | Pixels | sha256 |
|---|---|---|---|---|
| `docs/plan/r11/src/files/F1_aawp_2017-07-26_space19.jpg` | https://allaboutwindowsphone.com/images/features/space/space19.jpg | 2026-09-23 | 450x800 | `24a4ab4814b1d47385539109a20524f465195a5fa6dbd6223c905fb8a1a73b8a` |
| `docs/plan/r11/src/files/F2_aawp_2017-07-26_space1-final.jpg` | https://allaboutwindowsphone.com/images/features/space/space1-final.jpg | 2026-09-23 | 450x800 | `96d1813c8d0db7f1380a18c3f533579494701d50abe475030242e84ec84b169f` |
| `docs/plan/r11/src/files/F3_thurrott_2015-11-21_file-ex.jpg` | https://www.thurrott.com/wp-content/uploads/2015/11/file-ex.jpg (via https://web.archive.org/web/20170321063719im_/) | 2026-09-23 | 338x600 | `31a1e07840b1544736500f7c2b7176f7cc0470da790df90eb9fcc47819334144` |
| `docs/plan/r11/src/files/F4_onmsft_2015-11-27_DSC_0035.jpg` | https://www.onmsft.com/wp-content/uploads/2015/11/DSC_0035.jpg (via i0.wp.com) | 2026-09-23 | 1152x768 | `01ef57320e9e02eefbd69c329cec60b4917fbf1fd84fc09def71f01fcd51711b` |
| `docs/plan/r11/src/files/F4_onmsft_2015-11-27_DSC_0037.jpg` | https://www.onmsft.com/wp-content/uploads/2015/11/DSC_0037.jpg (via web.archive.org/web/2017im_/) | 2026-09-23 | 1152x768 | `42584bdd595b4cd81d48d687107812fd1c9e3123e178b07728627a5811a4c4ea` |
| `docs/plan/r11/src/files/F4_onmsft_2015-11-27_DSC_0038.jpg` | https://www.onmsft.com/wp-content/uploads/2015/11/DSC_0038.jpg (via web.archive.org/web/2017im_/) | 2026-09-23 | 1152x768 | `fe908c1edeb2c4a78935ca8211316dd8d3faf8c4e553545ca7ed15e5a9282c2a` |
| `docs/plan/r11/src/files/F4_onmsft_2015-11-27_DSC_0039.jpg` | https://www.onmsft.com/wp-content/uploads/2015/11/DSC_0039.jpg (via web.archive.org/web/2017im_/) | 2026-09-23 | 1152x768 | `9ef287078acff09154a8f4b7c34a58d3b0992b06b9b86abb5914a01a3065c595` |
| `docs/plan/r11/src/files/F4_onmsft_2015-11-27_DSC_0040.jpg` | https://www.onmsft.com/wp-content/uploads/2015/11/DSC_0040.jpg (via web.archive.org/web/2017im_/) | 2026-09-23 | 1152x768 | `cb46078c65e48eef572d954887fa87d21d878d730bb04b183961ba2ea1c81607` |
| `docs/plan/r11/src/files/F4_onmsft_2015-11-27_DSC_0041.jpg` | https://www.onmsft.com/wp-content/uploads/2015/11/DSC_0041.jpg (via web.archive.org/web/2017im_/) | 2026-09-23 | 1152x768 | `be98bb92a3a9a7e693cf508e980044a926a7ba24ddf9fa790b1b4404db141c8c` |
| `docs/plan/r11/src/files/F4_onmsft_2015-11-27_DSC_0042.jpg` | https://www.onmsft.com/wp-content/uploads/2015/11/DSC_0042.jpg (via i0.wp.com) | 2026-09-23 | 1152x768 | `7e849336e747dd71b1b6ae07f5179f4a8b3e30220f44f59c86797bc7c4099405` |
| `docs/plan/r11/src/files/F5_aawp_2017-05-11_fileexplorers.jpg` | https://allaboutwindowsphone.com/images/flow/misc/fileexplorers.jpg | 2026-09-23 | 1000x556 | `19ee5abd068acb92175e0d0e75f3ac5c1b25fb869fee0051a33d87c041563042` |
| `docs/plan/r11/src/files/F6_wc_2017-05-10_desktop1703_dark.jpg` | https://cdn.mos.cms.futurecdn.net/aWkVC5TN4acEFfUr6jiED6.jpg | 2026-09-23 | 1322x667 | `ebb975751b18c3e4daa38092153533d21d64df876e5a8c64ee675040e3ca2460` |
| `docs/plan/r11/src/files/F7_wc_2017-05-10_desktop1703_light_sort.jpg` | https://cdn.mos.cms.futurecdn.net/BDTWXLSXt2x4aV73p2DxVT.jpg | 2026-09-23 | 1223x667 | `3a587844a9d873e279f4039dcf34d8aef6e7525fa407527744ddbe3f21ac48e7` |
| `docs/plan/r11/src/files/F8_wc_2018-08-21_desktop1809.jpg` | https://cdn.mos.cms.futurecdn.net/8Pnnv25WZrTFjnzLPsCVqj.jpg | 2026-09-23 | 2048x1261 | `f47a4d11059b5262429760fe6e5fccf51f6094e5711de12b251e63acfd914287` |
| `docs/plan/r11/src/files/F9_wc_2015-02-13_preview_list_menu.png` | https://cdn.mos.cms.futurecdn.net/LLnNJ77iVoSVzxPLuBwD7Y.png | 2026-09-23 | 1295x1150 | `bf793ec179d4c65931fe75a32e522ea71fbb58df93156548225a683a2fb04ae1` |
| `docs/plan/r11/src/files/F10_wc_2015-02-13_preview_appbar_sort.png` | https://cdn.mos.cms.futurecdn.net/VUgkQdwkK7zsyWY7dXtnyB.png | 2026-09-23 | 1295x1150 | `e4ce9a41a3c0fc11ddb5a8f6737c6a69bd1cc7b072ea507aa2efffe85221dcfc` |

Articles (dating and text):
- https://allaboutwindowsphone.com/features/item/22349_How_to_Clear_storage_space_on_.php — AAWP, 2017-07-26 (F1, F2)
- https://www.thurrott.com/mobile/windows-phone/62499/windows-10-mobile-tip-use-usb-peripherals — Thurrott, 2015-11-21 (F3)
- https://www.onmsft.com/how-to/diving-file-explorer-windows-10-mobile-lumia-950 — OnMSFT, 2015-11-27 (F4, T1; read via web.archive.org/web/2017id_/)
- https://allaboutwindowsphone.com/flow/item/22193_W10Ms_File_Explorer_on_your_Su.php — AAWP, 2017-05-11 (F5)
- https://www.windowscentral.com/how-enable-hidden-modern-file-explorer-app-windows-10 — Windows Central, 2017-05-10 (F6, F7, T2)
- https://www.windowscentral.com/windows-10-universal-file-explorer — Windows Central, 2018-08-21 (F8, T2)
- https://www.windowscentral.com/file-explorer-windows-10-phone-video-tour — Windows Central, 2015-02-13 (F9, F10)
- http://allaboutwindowsphone.com/flow/item/21150_File_Cards-universal_and_the_f.php — AAWP, 2015-12-24 (T3)
- https://learn.microsoft.com/en-us/windows/apps/design/iconography/segoe-ui-symbol-font — Microsoft Learn (G1)
- R6 §0.2 D1 (Lumia W10M user guide) and D3 (UWP back navigation) — cited through R6's evidence copies

Evidence root: the session scratchpad (`r11pcmf/`: `img/files_*` downloads, `sheets/files_*` contact sheets and crops,
`scripts/lines.py`, `scripts/appbar.py`, `scripts/m.py` measurement helpers, `scripts/files_sb.py` storyboard fetcher,
`html/files_*` fetched pages and search results, `html/files_mdl2.json` the parsed G1 table). Every image is cited by URL above so
any number can be re-derived from a fresh download.
