# R12 — What Windows 10 Mobile phones shipped with (the "Windows 10 Mobile (original)" preset)

Research item R12 (INDEX, added 2026-09-23 from phase 12 interview Q5). Gates phase 12's FINAL. Rule: RV9 / Q10 — every
value below is measured from a cited source or recorded as a tagged approximation; nothing is from memory. Format follows
R8 (`r8-groove-measurements.md`): source IDs, per-value confidence, UNMEASURED where no source exists.

Governing build: W10M's final release line (15063 Creators Update / 15254 Fall Creators Update). The out-of-box state
DID differ between the 10586 launch build and 14393+ in exactly one item — the stock Start picture — so every row says
which build its source shows. Fetch date for everything: 2026-09-23.

## Summary — what was and was not established

Established at HIGH confidence from native screenshots of retail / review units: W10M Lumias shipped in the **Dark**
mode, with the accent set to an **OEM colour outside the 48-swatch Windows 10 palette — #3E65FF (62,101,255), the
Windows Phone 8.1 kit's "Cobalt"** — with the Start in **Full screen picture** mode showing the bundled **"Sample images"
img0** through **accent tiles drawn at 40 % opacity**, and the lock screen showing **img5, the portrait crop of the
Windows 10 "Hero"** (the blue light-beam Windows logo). The accent was the same on white and black phones, so it did not
follow the body colour. All 10586-era units (950, 950 XL, 550, 650) show the same look; the 14393 and 15063 release-demo
captures R3 already holds show the same accent, mode and tile opacity with a different img0 (the rotated Hero replaced
the 10586 light-streak picture — Windows Wallpaper Wiki, corroborated by R3's own frames).

Not established: the out-of-box look of the two non-Lumia phones (HP Elite x3, Alcatel Idol 4S — builds known, look
UNMEASURED / LOW); the slider position that produces the 40 % tile opacity (the alpha is measured, the slider-to-alpha
mapping is not); the Lumia 640's WP8.1 default accent beyond one screenshot (LOW); the light-theme keyboard. Video stills
were not usable: `yt-dlp` is blocked here ("Sign in to confirm you're not a bot", every client), as it was for R8, so
"first-boot video + timestamp" evidence was replaced by dated review screenshots, which are native-resolution and better
for colour. Two corrections to R3 fall out of this work (§7).

## 0. Sources

| ID | Source | Date / build shown | Used for |
|---|---|---|---|
| P1 | GSMArena, Microsoft Lumia 950 review, software page: https://www.gsmarena.com/microsoft_lumia_950-review-1347p4.php — shots `https://fdn.gsmarena.com/imgroot/reviews/15/microsoft-lumia-950/shots/gsmarena_001.jpg` (lock), `_004.jpg` (Start), `_005.jpg` (app list); 1440x2560 native | lock shot dated "Wednesday 2 December" (2015) → 10586 | lock picture; Start mode; this unit's accent is reviewer-set (§1) |
| P2 | GSMArena, Lumia 950 XL review: https://www.gsmarena.com/microsoft_lumia_950_xl-review-1349p4.php — `reviews/15/microsoft-lumia-950-xl/shots/gsmarena_001/002/004.jpg` (lock, Hello), `_005.jpg` (Start), `_006.jpg` (app list); 1440x2560 native | "Monday 7 December" (2015) → 10586 | accent, lock, Start picture, tile alpha, columns |
| P3 | GSMArena, Lumia 550 review: https://www.gsmarena.com/microsoft_lumia_550-review-1352p4.php — `reviews/15/microsoft-lumia-550/sshots/-1024x768m/gsmarena_002.jpg` (lock), `_003.jpg` (Start), `_004.jpg` (app list); 432x768 | "Friday 11 December" (2015) → 10586 | lock, Start picture; accent reviewer-set (§1) |
| P4 | GSMArena, Lumia 650 review: https://www.gsmarena.com/microsoft_lumia_650-review-1418p3.php — `reviews/16/microsoft-lumia-650/sshots/gsmarena_003.jpg`, `_004.jpg` (lock), `_005.jpg` (Start), `_006.jpg` (app list), `_007.jpg` (Colours page); 720x1280 native | "Thursday 17 March" (2016) → 10586.x | accent, mode, lock, Start, tile alpha, Colours page |
| A1 | AAWP, "Microsoft Lumia 950 (part 1)", Steve Litchfield: https://allaboutwindowsphone.com/reviews/item/21113_Microsoft_Lumia_950_part_1.php | Nov 2015, "Windows 10 Mobile Build 10586" | 950 default layout ("triple wide"), dark mode |
| A2 | AAWP, "Microsoft Lumia 950 XL (part 1)": http://allaboutwindowsphone.com/reviews/item/21105_Microsoft_Lumia_950_XL.php — `https://allaboutwindowsphone.com/images/reviews/950xl/starttiles.jpg` 450x800 | Nov 2015, Build 10586 | "Show more tiles" behaviour on the XL; Start picture |
| A3 | AAWP, "Microsoft Lumia 650 review": https://allaboutwindowsphone.com/reviews/item/21260_Lumia_650.php — `https://allaboutwindowsphone.com/images/reviews/650/650start.jpg` 450x800, captioned "the default Start screen for new users" | 23 Feb 2016, "production build 10586.107" | the one explicitly-default Start screenshot |
| A4 | AAWP, "Microsoft Lumia 640: no apologies needed": https://allaboutwindowsphone.com/reviews/item/20620_Microsoft_Lumia_6401.php — `https://mediafiles.allaboutwindowsphone.com/640xl/start1.jpg` 450x800 | 20 Apr 2015, WP8.1 Update 2 + Denim | 640 XL pre-upgrade Start |
| W1 | Windows Central, "How to enable four columns for the Start screen on the Lumia 950", Daniel Rubino: https://www.windowscentral.com/enable-four-columns-start-screen-lumia-950 | 23 Nov 2015, 10586 | column default and the scale/toggle mechanism |
| W2 | Windows Central, "11 things you should do first with the Lumia 950 or 950 XL": https://www.windowscentral.com/11-things-you-should-do-first-lumia-950-or-950-xl | Nov 2015 | lock-screen default is a fixed image; Settings paths |
| W3 | Windows Central, Lumia 950 review: https://www.windowscentral.com/lumia-950-review | Nov 2015 | "It ships with Windows 10 Mobile build 10586" |
| W4 | Windows Central, Lumia 550 review, Richard Devine (retail unit "bought from a real store"): https://www.windowscentral.com/microsoft-lumia-550-review — photos `https://cdn.mos.cms.futurecdn.net/<id>-1200-80.jpg`, ids `mrCt7r4r5PQ4pBxEJQHeUi`, `dqLSDosZkCuDmhKQt7VYAW`, `wji5KVhtwJjAwiNDkYaaG5` | 13 Jan 2016 | 550 corroboration (photos, not screenshots) |
| W5 | Windows Central, "Alcatel Idol 4S with Windows 10 unboxing, camera samples, and FAQ": https://www.windowscentral.com/alcatel-idol-4s-unboxing-faq ; review https://www.windowscentral.com/alcatel-idol-4s | 4 / 10 Nov 2016 | "ships with Anniversary Update 14393.189" |
| W6 | Windows Central forum, "Cobalt accent in Windows 10?": https://forums.windowscentral.com/windows-10-mobile/404828-cobalt-accent-windows-10-a.html | 2–5 Jan 2016, 10586.36 | accent survives the 8.1 → 10 upgrade |
| T1 | TechRadar, Microsoft Lumia 950 review: https://www.techradar.com/reviews/phones/mobile-phones/microsoft-lumia-950-1306004/review | 2015 | "dark theme turned on by default" |
| X1 | XDA, "Recovering factory default accent color for Lumia 950 XL": https://xdaforums.com/t/recovering-factory-default-accent-color-for-lumia-950-xl.3545005/ (OP, 25 Jan 2017) | 950 XL, 2017 | factory accent "NOT available for picking in the color palette" |
| M1 | Microsoft Community, "Factory default accent color for Microsoft Lumia 950 XL" (`answers.microsoft.com/.../ae4595d4-7362-4fe6-b481-80936c0eb8fd`; the page now redirects to learn.microsoft.com, text known only from the search-engine snippet) | 950 XL | "the 49th, the sole color in the last row of the color palette" |
| K1 | Windows Wallpaper Wiki, "Windows 10 Mobile" (rev 19465): https://windowswallpaper.miraheze.org/wiki/Windows_10_Mobile and its File: pages | catalogue | names, origins, file copies of img0–img9 |
| K2 | Windows Wallpaper Wiki, "Windows 10": https://windowswallpaper.miraheze.org/wiki/Windows_10 ; "Img103": https://windowswallpaper.miraheze.org/wiki/Img103_(Windows_10) | catalogue | Hero (img0) provenance and PC resolutions |
| D1 | Microsoft, "Themes and accent colors" (WP8.1 adaptation kit, filed under Windows 10 hardware dev): https://learn.microsoft.com/en-us/previous-versions/dn772323(v=vs.85) (already R1 §6.1) | doc | Cobalt = 3E65FF (ID 5); OEM `DefaultBackgroundColor`, `DefaultAccentColor` |
| G1 | GitHub, DrewNaylor/Retiled issue #3: https://github.com/DrewNaylor/Retiled/issues/3 | notes | Cobalt #0050EF vs "Light Cobalt" #3E65FF "according to WindowSpy" |
| PS1 | PhoneScoop, Lumia 650 (Cricket) review, software page: https://www.phonescoop.com/articles/article.php?a=17748&p=6778 — `https://www.phonescoop.com/img/a/p/65624_800.jpg` (Start settings), `65617_800.jpg` (app list), `65614_800.jpg` (Action Center) | 2016, Cricket US unit | "Sample images" background source; a conflicting accent (§1) |
| PS2 | PhoneScoop, Alcatel Idol 4S with Windows 10 review: https://www.phonescoop.com/articles/article.php?a=18390&p=6897 — `img/a/p/68703_500.jpg` (lock), `68707_500.jpg` (Start) | Nov 2016 | Idol 4S (LOW) |
| H1 | Microsoft Lumia 550 user guide, "Personalise the lock screen": https://microsoft-lumia-550.helpdoc.net/en-gb/basics/personalise-your-phone/personalise-the-lock-screen/ | guide | lock Background options |
| S1 / S2 | R3's footage (`w10m-measurements.md` §0.1): S1 https://www.youtube.com/watch?v=I98ENfXJRqA "Windows 10 Mobile Anniversary Update - Official Release Demo" (Windows Central, 2016-08-16, 14393, 950-class at 400 %), frame t=12.0 s; S2 https://www.youtube.com/watch?v=E6vvrz4ozpE "Windows 10 Mobile Creators Update - Official Release Demo" (Windows Central, 2017-03-16, 15063, 950 XL at 350 %), frame t=20.0 s — frames still on disk at R3's `$R3/frames/start/` | 14393 / 15063 | governing-build Start picture, accent rendition, tile alpha |
| R1 / R3 / R6 | `w10m-reference.md`, `w10m-measurements.md`, `r6-measurements.md` | — | palette (R1 §6.1), A3/A4/A10/A16/A18/A22, keyboard colours |

Colour method: flat regions (block std < 3–6 across 8–10 px blocks) were located automatically in each native screenshot
and their median RGB compared with R1 §6.1's two palettes (Euclidean distance in RGB). Screenshots are PNG-derived JPEGs
served by the sites; the exact matches below (distance 0.0–1.4) show the JPEG did not move the colours. Photos of
phones (W4, W5) are used only qualitatively. Tile alpha: for an accent tile in Full screen picture mode,
`tile = a·accent + (1−a)·picture`; `a` was solved per channel using the picture's value in the 13-phys-px left margin
next to the tile at the same row (the margin shows the picture in full-screen mode, R3 §C4 "gutters show the picture, not black" and A18) — the picture varies
slowly, so the margin is the background under the tile edge to within a few units.

## 1. Default accent colour, per device

| Device | Value | RGB / hex | R3 A16 palette position | Confidence | Evidence (source, frame) | Build |
|---|---|---|---|---|---|---|
| Lumia 950 XL | OEM accent "Cobalt" | **(62,101,255) = #3E65FF**, exact | **not in the 48-swatch grid**; = WP8.1 kit ID 5 "Cobalt" (R1 §6.1 table 1). Nearest Win10 swatch is none within 50 units (Purple Shadow #8E8CD8 is the only nearby hue and is far off) | **HIGH** | P2 `gsmarena_006.jpg` app list: 546 flat 10-px blocks of the icon plates read exactly (62,101,255). P2 `_005.jpg` Start: accent tiles are blends of this colour (§5.1). X1 OP: the factory colour is "NOT available for picking in the color palette in Settings / Personalization / Colors"; M1: shown as "the 49th, the sole color in the last row" | 10586 (Dec 2015) |
| Lumia 650 | same | **(62,101,255) = #3E65FF**, exact | as above | **HIGH** | P4 `gsmarena_006.jpg` app list: 296 flat blocks at (62,101,255). P4 `_007.jpg` Colours page: the header gear and the selected "Dark" radio ring are this colour and NO swatch in the two visible rows carries a selection border (evidence file `docs/plan/r12/ev_gsmarena_lumia650_colours_page.jpg`) | 10586.x (Mar 2016) |
| Lumia 950 | same (inferred) | #3E65FF | as above | **MEDIUM** | No untouched 950 screenshot: P1's unit had been set to Plum #BF0077 (191,0,119 exact, app-list plates) and W3's photos cannot resolve hue. Inference: same firmware family and launch as the XL; S1 (a 950-class device, 14393) renders the accent as (48,104,255) in R3's capture, 20 units from #3E65FF and 100+ from #0078D7 — the same rendition R3 saw on the 950 XL S2 | 10586 / 14393 |
| Lumia 550 | same (inferred) | #3E65FF | as above | **LOW** | P3's unit had been set to Rose Bright #EA005E (234,0,94 exact). W4's retail unit (photos) shows blue tiles of the cobalt class over the stock picture, not measurable | 10586 |
| Lumia 640 / 640 XL (WP8.1 as sold) | Cyan | (24,160,224) ≈ **#1BA1E2** WP8.1 "Cyan" (ID 4) | not in the Win10 grid; WP8.1 palette | **LOW** (one unit, reviewer could have changed it) | A4 `640xl/start1.jpg`: 3273 flat 3-px blocks at (24,160,224) on the Phone / Weather / Lumia Camera / Transfer-my-Data tiles, no Start picture, opaque tiles | WP8.1 Update 2, Apr 2015 |
| Lumia 640 / 640 XL after the W10M upgrade | whatever WP8.1 had — the upgrade keeps the accent | (cyan if the row above holds) | — | **MEDIUM** for "kept", LOW for the colour | W6 OP: "upgraded from Windows 8.1 to Windows 10 build 10586.36 … the cobalt accent was still there" — i.e. the 8.1 accent survives the upgrade; the OEM accent is not re-applied | 10586.36 |
| HP Elite x3 | UNMEASURED | — | — | UNMEASURED | No screenshot of an untouched unit found (AAWP part 1/2, Windows Central review + unboxing + FAQ, HotHardware, Tom's Guide, MSPoweruser: no Start/lock screenshot; the unboxing page's image ids resolve to unrelated pictures). Build: "the review X3 is already on W10M build 10.0.14393.321" (AAWP part 1) | 14393.321 |
| Alcatel Idol 4S (T-Mobile) | blue, reviewer-configured units only | — | — | **LOW** | W5 review photos show blue accent tiles over a pale full-screen picture; PS2's unit shows a red/dark picture with an added Instagram tile — both customised. Ships with 14393.189 (W5 FAQ) | 14393.189 |
| Did it follow the body colour? | **No** on W10M Lumias | — | — | **MEDIUM** | P2 (950 XL, colour unknown), P4 (650), W4 (white 550) and W3 (white 950, photos) all show the same cobalt; the 950/950 XL/650/550 were sold only in black and white and no source ties the accent to the shell. On WP8.1 the one 640 XL unit (A4) shows cyan, and Lumia 640s were sold in cyan — a body-colour link there is UNMEASURED (one unit, colour of that unit not stated) | — |
| Did it follow the carrier? | one conflicting unit | — | — | **LOW / CONFLICT** | PS1 (Cricket US Lumia 650): app-list plates and the Action Center's active tile read (27,160,225) = WP8.1 Cyan #1BA1E2, not cobalt. Either Cricket's firmware carried a different default or the reviewer restored a WP8.1 backup (W6 shows accents travel with backups). No second Cricket unit found | 10586 |

Note on the two Cobalts: D1 (the WP8.1 kit table R1 reproduces) lists Cobalt as 3E65FF; G1 records the WP8-era Cobalt as
#0050EF and "#3e65ff instead of #0050ef according to WindowSpy" as a "Light Cobalt" some phones showed. The measured
W10M value is the 3E65FF one. W6 describes the Windows 10 rendering as "a flat purplish blue" compared with WP8 —
consistent with 3E65FF (hue 231°) versus 0050EF (hue 220°).

## 2. Dark or Light by default

| Value | Confidence | Evidence | Build |
|---|---|---|---|
| **Dark** | **HIGH** | T1 (Lumia 950): "Windows 10 Mobile has a dark theme turned on by default, and it really lets the deep blacks and Microsoft's familiar blues shine on this 5.2-inch AMOLED display." P4 `_007.jpg` Colours page (650): "Choose your mode" radio on **Dark**. A1 (950): "for my use in all 'dark' mode". D1: the OEM sets `DefaultBackgroundColor` (0 = light, 1 = dark). Every Start / app-list / Settings screenshot in P1–P4, A1–A3, S1, S2 has black page backgrounds and white text | 10586, 14393, 15063 |

## 3. Start background and column setting out of the box

| Item | Value | Confidence | Evidence | Build |
|---|---|---|---|---|
| Start background mode | **Full screen picture** (R1 §1.5's third mode) — the picture fills the screen; gutters, margins and the app list show it (R3 A18) | **HIGH** | A3 `650start.jpg`, captioned by the reviewer as "the default Start screen for new users": light-streak picture visible in the status-bar band, the gutters and through every accent tile. P2 `_005.jpg`, P3 `_003.jpg`, P4 `_005.jpg`, A2 `starttiles.jpg`, W4 photos, W3 photo: same picture, same mode. S1 t=12 s, S2 t=20 s: same mode with the 14393+ picture | 10586 → 15063 |
| Picture, 10586 (Nov 2015 – mid 2016 phones) | **img0 (versions 1507–1511)** — dark teal/cyan diagonal light streaks on near-black, origin unknown per K1 | **HIGH** that this is the picture (visual match of the wiki file against A3, P2, P3, P4, A2: `docs/plan/r12/ev_img0_comparison.png`, left three panels); K1 for the name | K1 portrait table: "img0 (versions 1507-1511) — unknown / unknown". File: `docs/plan/r12/img0_w10m_1507-1511.jpg` | 10586 |
| Picture, 14393 / 15063 (governing) | **img0 (versions 1607–1709 and Redstone 3)** — the Windows 10 Hero "rotated from a part of the PC counterpart" (K1): the logo's light beams entering from the right in portrait | **MEDIUM** — the two units are Windows Central's release-demo phones (S1, S2), not documented as fresh out of the box, but both carry the default tile layout (Phone, Skype, Messaging, Outlook Calendar, Outlook Mail, People, Cortana, Edge, Office ×4, Store, Continuum/Camera, Photos, Transfer my Data) and K1 independently records the img0 change at 1607 | S1 `I98_start_t12.png`, S2 `E6v_start_t20.png` (`ev_img0_comparison.png`, right three panels). File: `docs/plan/r12/img0_w10m_1607-1709.jpg` | 14393, 15063 |
| Picture source in Settings | Settings > Personalization > Start > Background = **"Sample images"** (the bundled set), Browse picks one; "Choose style": Tile picture / Full screen picture | **HIGH** | PS1 `65624_800.jpg` (evidence file `ev_phonescoop_lumia650_start_settings.jpg`): Preview thumbnail shows the streak picture; "Background: Sample images"; "Browse"; "Choose style" radios | 10586 |
| Column setting, Lumia 950 (400 %) | **3 medium columns** (= 6 small); "Show more tiles" is present but "does nothing on the Lumia 950 by default" | **HIGH** | W1: "By default, the Lumia 950 opts for the three-column layout. … users can also toggle a switch to 'Show more tiles' that adds in a fourth row. Interestingly, this does nothing on the Lumia 950 by default. … By default, the Lumia 950 is set at 400%." A1: "the default layout ('triple wide')" | 10586 |
| Column setting, Lumia 950 XL (350 %) | **3 medium columns** with "Show more tiles" OFF; ON gives **4 medium columns** at 350 % | **HIGH** | W1's mechanism (350 % + toggle → fourth column); A2: "I've taken the default tile layout, flicked the 'Show more tiles' toggle on" and its `starttiles.jpg` shows FOUR medium columns (small pair + medium + wide per row); P2 `_005.jpg` (untouched XL) and S2 (XL frame) show three. This corrects R3 A1's note that the 3-column layout is "Show more tiles ON" on both phones (§7) | 10586, 15063 |
| Column setting, Lumia 650 (5", 720p) | 3 medium columns | **HIGH** | A3 default Start: small pair / medium / medium per row | 10586.107 |
| Column setting, Lumia 550 (4.7", 720p) | 3 medium columns | **MEDIUM** | P3 `_003.jpg` and W4 photos show three; whether the toggle was touched is not stated | 10586 |
| Default tile layout (for reference, not a preset item) | Row 1: Phone, Skype (small) / Messaging, Facebook (small); Outlook Calendar; Outlook Mail. Row 2: People; Cortana (wide). Row 3: Microsoft Edge; Word, OneNote, PowerPoint, Excel (small ×4); Store. Row 4: Photos; regional news app (Daily Mail Online in the UK); Camera. Row 5: Transfer my Data; Lumia Offers (gift); regional app (Amazon UK) | **HIGH** for the UK 650 (A3, explicit default); the 950 XL and 550 units (P2, P3) carry the same rows with Lumia Help+Tips / Continuum / Groove / Films & TV / Photos added on the flagship; region-specific slots vary (A3: "always interesting to see what Microsoft 'pushes' for devices activated in each region") | 10586 |

## 4. Stock lock-screen picture and the bundled Start pictures

### 4.1 Lock screen out of the box

| Item | Value | Confidence | Evidence | Build |
|---|---|---|---|---|
| Lock-screen picture | **img5 (Windows 10 Mobile)** — the portrait crop of the Windows 10 Hero (blue Windows logo with light beams; K1: "Cropped version of the PC counterpart's Hero wallpaper"; PC img0 designed by Bradley G. Munkowitz's team, photo Joe Picard, original file `Gmunk_Final_RGB_2`, K2) | **HIGH** | P1 `_001.jpg` (950, 2 Dec 2015), P2 `_001/_002/_004.jpg` (950 XL, 7 Dec 2015), P3 `_002.jpg` (550, 11 Dec 2015), P4 `_003/_004.jpg` (650, 17 Mar 2016): all four units show it; W4 photo `wji5KVhtwJjAwiNDkYaaG5` (retail 550) shows it; W2: "Get a new lock screen image automatically every day from Bing instead of using the default one" — the default is a fixed image | 10586 |
| Lock Background setting | a fixed picture ("My picture" preselected with the stock image); alternatives "Bing" (daily image) and an app | **MEDIUM** | H1: "Switch Background to My picture, and tap Browse"; "let Bing shuffle photos"; W2 as above. Windows Spotlight on phone exists (AAWP, "How to: Set your lock screen to the (amazing) Bing image of the day", https://allaboutwindowsphone.com/features/item/22382_How_to_Set_your_lockscreen_to_.php: a portrait Spotlight crop "auto-grabbed by the OS if you turn the option on") but was not the default on any unit seen | 10586 |
| Same on 14393 / 15063? | UNMEASURED — no lock-screen frame of a fresh 14393+ device; K1 lists img5 with no version split, unlike img0 | UNMEASURED | — | — |
| Glance | not a preset item (phase 07); R1 §8.2 "time and date in large, white text on the black background" | — | — | — |

### 4.2 The bundled picture set (Microsoft-owned → A10 branding module)

K1's tables for Windows 10 Mobile, with the wiki's own file copies saved into `docs/plan/r12/` (originals untouched;
fetched 2026-09-23 17:13 UTC from `https://static.wikitide.net/windowswallpaperwiki/<path>`; sha256 of the saved file).
The wiki serves 1080x1920 files (864x1536 for the 1607 img0) — the 720p-class phones' native size; whether the
1440x2560 phones carried larger files is UNMEASURED. Licence: every row is "commissioned by Microsoft" or a crop of a
Microsoft PC wallpaper (K1), i.e. Microsoft-owned — ship them only inside the swappable branding module (A10). The
Hero's PC master exists up to 3840x2160 (K2: "1024x768 … 3840x2160"; `C:\Windows\Web\4K\Wallpaper\Windows\img0_*.jpg`
on any Windows 10 1507–1809 install), which is the right source for a 1440x3120 S25 Ultra crop of img5 / img0-1607.

| Role | W10M file | Saved as (docs/plan/r12/) | Size | Origin (K1) | sha256 |
|---|---|---|---|---|---|
| Start "Sample image" 0, 1507–1511 (10586 default) | img0 | `img0_w10m_1507-1511.jpg` | 1080x1920 | unknown | d115eff1605265b9b7eeb614646b43d7a905837818f2035a1e16c8f85f6c2e1c |
| Start "Sample image" 0, 1607–1709 (14393/15063 default) | img0 | `img0_w10m_1607-1709.jpg` | 864x1536 | Hero, rotated part (Munkowitz / Picard) | 228f088bd4b20e4bc001a8d60d34ab8f4151840ef25d82069f6ce9d7180ac9a7 |
| Start "Sample image" 1 | img1 | `img1_w10m.jpg` | 1080x1920 | `beach_2`, JC Carey | 29788eb76a84dab512ead6bd7e26d3de6abfc9d5ff4fee14cec661c6fae4daf8 |
| Start "Sample image" 2 | img2 | `img2_w10m.jpg` | 1080x1920 | `Chad-Copeland-2002_2` | 9331253a39a8d10e9f08d77a2b1aececb4f31a2d83c9a29b510bfcccc2a8d94a |
| Start "Sample image" 3 | img3 | `img3_w10m.jpg` | 1080x1920 | `Chad-Copeland-2001_2` | 27937667fbb82ee50551f74daf95f77141475ea7b39683f8050bd7fda45af8eb |
| Start "Sample image" 4 | img4 | `img4_w10m.jpg` | 1080x1920 | `Chad-Copeland-1000-4_v02_2` | a3329eb0617d8ef158fbde5b974da36ab679bc8933fc98d36be5fb620e5dfbe7 |
| **Lock screen default** | img5 | `img5_w10m_lock.jpg` | 1080x1920 | Hero, portrait crop | c68cec9b549fc78fa6ffe420d2a3213d8ceeebf9ef0c3d05c1d4d9eb2921cad4 |
| Lock (landscape variant, Continuum) | img5 landscape | `img5_landscape_w10m.jpg` | 1920x1200 | Hero | 75598c672eb6b17605c3596821678b8ad4dccf21925ce5fbf0e87739d0d06bd3 |
| Lock alternative | img6 | `img6_w10m_lock.jpg` | 1080x1920 | `DSC7615_FINAL_2`, Steve McCurry (vertical img103) | f4c1007ae1ad0168b743eb947edb27bfdf3a41cfaa3ef89254c16450b02ec4e7 |
| Lock alternative | img7 | `img7_w10m_lock.jpg` | 1080x1920 | `Chad-Copeland-2004_2` (vertical img102) | 80b03291d3aaa4987c8b0767ec6843572625a28ea816446e1b28112fe68d65e9 |
| Lock alternative | img8 | `img8_w10m_lock.jpg` | 1080x1920 | `Chad-Copeland-2007_2` (vertical img101) | 72819380f22d50a640a3e0b0f65d9efbf715c8caf842ad28afb116019ea8d7f6 |
| Lock alternative | img9 | `img9_w10m_lock.jpg` | 1080x1920 | `Chad-Copeland-2008_2` (vertical img104) | 906f35c591a80a6306fbaa5021b564534af346b5a9666f2282836d20221a5df6 |

Whether img1–img4 were also selectable as lock pictures, and img6–img9 as Start pictures, is not stated by K1 (its
tables are "Portrait", "Landscape" and "Lock screen wallpapers"); UNMEASURED. The pre-release Technical Preview
wallpapers (K1 "Background 01", "Wallpaper 01", Getty-licensed) and the nine build-10162 wallpapers (Neowin / NPU packs)
are not the shipping set and are not saved.

Evidence screenshots saved alongside (press screenshots, kept for the measurements only, never shipped):
`ev_aawp_lumia650_default_start_10586.jpg` (A3), `ev_aawp_lumia950xl_start_showmoretiles_10586.jpg` (A2),
`ev_gsmarena_lumia650_colours_page.jpg`, `ev_gsmarena_lumia650_applist.jpg`, `ev_gsmarena_lumia650_lockscreen.jpg` (P4),
`ev_gsmarena_lumia950_lockscreen.jpg` (P1), `ev_gsmarena_lumia950xl_start.jpg`, `ev_gsmarena_lumia950xl_applist.jpg` (P2),
`ev_gsmarena_lumia550_lockscreen.jpg` (P3), `ev_aawp_lumia640xl_start_wp81.jpg` (A4),
`ev_phonescoop_lumia650_start_settings.jpg` (PS1), `ev_img0_comparison.png` (the two img0 files beside A3, P2, S1, S2).

## 5. Other out-of-box visual defaults a preset would set

### 5.1 Tile transparency (R3 A3 was UNMEASURED)

| Item | Value | Confidence | Evidence |
|---|---|---|---|
| Accent-tile opacity in Full screen picture mode, as shipped | **α = 0.40 ± 0.02** (accent tiles are 40 % accent over the picture; app-coloured tiles stay opaque, R3 A4) | **HIGH** for the shipped alpha (three independent units agree; the two GSMArena units are otherwise untouched in this respect and the third is Microsoft's release-demo phone) | P2 `_005.jpg` (950 XL, 1440 wide): Phone tile interior x 40–120 vs left margin x 2–10, rows 120/160/280 → α = (0.40, 0.40–0.44, 0.41–0.42) per R/G/B; rows 1560–1680 (Transfer my Data) → 0.39–0.41 / 0.40–0.49 / 0.40–0.44; rows 2080–2200 (Groove, over the bright streak) → 0.40–0.41 / 0.39–0.40 / 0.38–0.45. Rows landing on glyphs excluded. P4 `_005.jpg` (650, 720 wide): rows 60 / 180 / 1080 → (0.40,0.40,0.41), (0.39,0.40,0.40), (0.40,0.39,0.41). R3 A4's own S1 sample (14393): Cortana tile (19,63,151) over background (0,37,80) with the accent rendered (48,104,255) → (0.40, 0.39, 0.41) |
| Slider position that yields it | UNMEASURED — R1 §1.5 gives the slider's ends (0 % transparent … 100 % opaque) but no source shows a fresh device's slider, and the slider-to-alpha curve is unknown; R3 A3's two user-set observations (22.6 %, 59.7 %) bracket it | UNMEASURED | — |
| Which tiles blend | R3 A4 (MEDIUM): accent-background tiles blend; app-coloured / image tiles are opaque | inherit | A3, P2, P4 confirm: Skype, Facebook, Office, Store-promo, Daily Mail, Amazon tiles opaque; Phone, Messaging, Calendar, Mail, People, Cortana, Edge, Store (logo), Photos, Camera, Transfer my Data, Lumia Offers blend |

### 5.2 Everything else

| Item | Value | Confidence | Evidence |
|---|---|---|---|
| "Show more tiles" | OFF on every phone seen; on the 950 (400 %) it changes nothing, on 350 % devices it adds a fourth medium column | HIGH | §3 (W1, A2, P2, S2) |
| Press / tilt style | **none** — no tilt, depression, scale or highlight on Start tiles at pointer-down | HIGH (R3 A10, two recordings) | not a W10M setting; the preset's "press style" item = the W10M value |
| Keyboard appearance | the dark keyboard R6 measured: panel (22,27,21) capture rendition, keys lum ≈48, white labels, accent cursor-control dot (R6 §2.2.2, §2.5.1; phase 05 Decisions) — measured on S1/S2, which are on the stock Dark mode | HIGH that the shipped keyboard is R6's dark keyboard; **UNMEASURED** whether the Light mode changes the keyboard (irrelevant to this preset, which is Dark) | R6; phase 05 H11 already carries the capture-rendition caveat |
| Cortana's default look | accent-derived two-tone ring (R3 A22: inner darker (28,44,107)-class, outer bright (60–68,97–103,209)-class on S2) — those readings are cobalt shades, consistent with §1 | inherit (R3 A22 MEDIUM) | S2 t=479 s; R1 §9 "animation colour follows the theme/accent colour" |
| Page / chrome colours | Dark mode: black page background, white text (R3 A18 MEDIUM; R1 §6.2 SystemAltHigh #000000 / SystemBaseHigh #FFFFFF) | inherit | — |
| Windows Spotlight on the lock screen | not on by default (§4.1) | MEDIUM | W2, H1 |
| Phase 13 "Transparency effects" (acrylic) | W10M 15063/15254 had no acrylic on phone (R3 A24 UNMEASURED lineage; nothing in S1/S2 shows it) → the preset turns phase 13's switch OFF | approximation (agent reading of phase 12's 2026-09-23 decision that a preset may turn acrylic off) | — |
| Fonts / icons | not preset items (Selawik per Q8; Segoe only on personal builds) | — | — |

## 6. What the "Windows 10 Mobile (original)" preset sets

Each of phase 12's "everything visual" items (Decisions 2026-09-23, Q4), filled from the evidence above. Governing
build = 15063/15254 where the builds differ; the 10586 alternative is given where it does.

| Preset item (phase 12 Q4) | Value | Status |
|---|---|---|
| Accent | **#3E65FF (62,101,255), "Cobalt"** — an OEM colour outside R3 A16's 6 × 8 grid. The shell's accent picker (phase 01, `accent:<name>` from the 48) has no such swatch; W10M itself showed the factory colour as a 49th swatch in a ninth row (M1, X1). Agent recommendation for phase 12: add one named swatch `Cobalt` as the 49th cell (last row, first column) so the preset — and a user re-picking it — can name it; selecting any of the 48 loses it exactly as it did on the phones (X1: "now the default accent color is gone") | HIGH (value); the swatch placement is an approximation, NEEDS-HUMAN |
| Dark / Light | **Dark** | HIGH |
| Background picture | **Full screen picture, img0** — governing build: `img0_w10m_1607-1709.jpg` (rotated Hero); 10586 launch look: `img0_w10m_1507-1511.jpg` (light streaks). Both Microsoft-owned → branding module (A10). Agent recommendation: the preset applies the governing-build picture; bundle the 10586 one as the second "sample image" so Jeremy can choose (he asked for "the original W10M theme that was sent with phones" — the phones themselves were sent with the 10586 picture; the OS he is emulating replaced it; NEEDS-HUMAN which "original" he means) | MEDIUM (14393/15063 picture), HIGH (10586 picture) |
| Tile transparency | accent tiles at **α = 0.40** over the picture (app-coloured tiles opaque); if the shell's slider is linear 0–100 % opaque, set it to 40 | HIGH (alpha); slider mapping UNMEASURED |
| Press style | **none** (no tilt) | HIGH (R3 A10) |
| Tess's look | ring colours derived from #3E65FF by R3 A22's two-tone rule (inner darker, outer bright — the S2 readings are the cobalt case); the HAL lens paint is phase 03's 2026-09-21 decision and is not a W10M value — whether the preset also swaps the lens back to the flat cobalt Cortana disc is a phase 12 decision, NEEDS-HUMAN | MEDIUM (A22) |
| Keyboard colours | R6's dark keyboard with the cobalt cursor dot | HIGH (R6) / rendition caveat H11 |
| Columns ("Show more tiles") | 3 medium columns, toggle OFF (phase 01's default already) | HIGH |
| Lock-screen picture | **img5** (Hero portrait) — Microsoft-owned → branding module; `img5_w10m_lock.jpg` 1080x1920, or a fresh crop from the 4K PC Hero for 1440x3120 | HIGH (10586 units); 14393+ UNMEASURED |
| Windows Spotlight / Bing lock | off | MEDIUM |
| Phase 13 transparency effects (acrylic) | off | approximation |
| Glance, fonts, icons | untouched by the preset | — |

## 7. Open conflicts and notes for other docs (recorded here, not edited there)

1. **R3 §0.2 "colour caveat" is not a capture error.** R3 reports the accent rendering as (48,104,255) "where the nominal
   Win10 default blue is #0078D7" and treats sampled colours as "capture rendition". The stock accent is #3E65FF
   (62,101,255); the S1/S2 demo phones were on it. (48,104,255) is 20 units from the true accent and 100+ from #0078D7,
   so R3's accent-coloured readings (A19 Action Center active tile, A20 volume slider fill, A22 Cortana ring, the keyboard
   dot) are renditions of cobalt, not of Default Blue. Anything phase docs derived as "#0078D7 with a capture offset"
   should be re-read as cobalt when the preset is built. Not edited here (R3 is another doc; INDEX Change Log when phase
   12 lands).
2. **R3 A1's parenthesis "Show more tiles ON, which is the Lumia 950/950 XL default per R1 §1.3" is wrong in both halves**
   (W1, A2): on the 950 at 400 % the toggle has no effect; on the XL at 350 % the default is OFF and ON gives four medium
   columns. The measured 3-column geometry itself is unaffected.
3. **Cricket Lumia 650 accent (PS1) = Cyan #1BA1E2**, against cobalt on the GSMArena 650 (P4). Carrier firmware vs a
   restored backup: undecidable from one unit each. LOW; the preset follows P4 (unbranded).
4. **The "49th swatch"**: M1 (snippet only) says the factory colour appeared as a lone swatch in a ninth row; P4's Colours
   page shows only the first two rows, so the ninth row was not seen. LOW for the swatch's appearance; HIGH that the colour
   is outside the 48 (X1 + measurement).
5. **Which img0 is "original"**: §6. The phones shipped with the streak picture (10586); the governing OS build shipped the
   rotated Hero (14393+, K1 + S1/S2). Both are bundled; Jeremy picks.
6. **Lumia 640 upgraded**: the upgrade keeps the user's WP8.1 accent and (by the same mechanism) the Start background state,
   so an upgraded 640's "shipped" look is WP8.1's — cyan tiles, no Start picture, opaque tiles (A4, one unit, LOW). The
   preset does not model this; recorded because interview Q5 named the 640.
7. **Non-Lumia phones**: builds known (Elite x3 14393.321; Idol 4S 14393.189), looks UNMEASURED / LOW. If a fresh Elite x3
   or Idol 4S screenshot ever surfaces, §1 and §4 gain a row each; nothing in the preset depends on them.
8. **Video evidence**: none of the values above comes from a YouTube frame taken in this run — `yt-dlp` is blocked in this
   environment (as R8 found). R3's existing S1/S2 frames were reused instead. The first-boot videos located
   (Windows Central Lumia 550 unboxing `SS5PNEuAugg`, Lumia 650 `XJXawxcSxbY`, Idol 4S `01jwEQVKlhA` / `I_ZfiscDR8M`,
   Elite x3 `v2RawZjUfqU`) remain unextracted; a session with YouTube cookies could add their first-Start frames.

## 8. Confidence tally

Counting each distinct value in §1–§5 (not the inherited R1/R3/R6 rows):

- **HIGH (14):** accent on the 950 XL; accent on the 650; Dark mode; Full-screen-picture mode; the 10586 picture's identity
  (img0 1507–1511); the "Sample images" source; 950 columns; 950 XL columns and the toggle mechanism; 650 columns; the
  default tile layout (UK 650); the lock picture (img5) on 10586 units; the tile alpha 0.40; "Show more tiles" OFF; the
  shipped keyboard is R6's dark keyboard.
- **MEDIUM (7):** accent on the 950 (inferred); accent survives the WP8.1 → W10M upgrade; accent did not follow the body
  colour; the 14393/15063 picture (img0 1607–1709); 550 columns; the lock Background setting ("My picture" + stock, Bing
  opt-in, Spotlight off); the wiki's file copies as the shipped files (origins per K1).
- **LOW (5):** accent on the 550; the 640 XL's WP8.1 cyan; the Cricket 650 cyan (carrier); the Idol 4S look; the "49th
  swatch" appearance.
- **UNMEASURED (6):** slider position for α = 0.40; the Elite x3's look; the lock picture on a fresh 14393+ device; whether
  the Light mode changes the keyboard; the phone-side file resolution on 1440-class devices; whether img1–img4 / img6–img9
  cross over between Start and lock pickers.
