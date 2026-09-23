# R11 — Settings front (W10M Settings app) measurements

Section of R11 (docs/plan/r11-inbox-apps.md) for phase 19 (docs/plan/phase-19-settings-front.md). Format and confidence
rules follow docs/plan/r8-groove-measurements.md. Cited, not re-measured: R3 C1 (home geometry, 14393/15063 at 720p),
R3 A15 (body line pitch), R7 §3.5.7 / §3.6 (pressed fill, flyout), phase 01 X6 (nav bar).

## Summary

The final-release Settings app is measured directly on the governing build: S2 is a 60-fps Project My Screen capture of
**15063** (350 %, 411-epx canvas), now available at 1080p (R3 used 720p), and S1 is the same kind of capture of **14393**
(400 %, 360 epx). Every geometric value below was taken on both where both show it, so fixed-epx values are confirmed
across two scale factors and two builds. Findings that change phase 19:

1. **W10M 15063 has its own "Apps" category** — "Apps: Uninstall, Apps for websites, Offline maps", pages Apps & features /
   Offline maps / Apps for websites (S2 t=215, t=342.5; Windows Central's 15063 review says the same). It is absent on
   14393 (S1 t=453.75). Phase 19 Scope calls it "desktop Windows' 15063 addition, not W10M's"; that is wrong. The Q2 ruling
   ("W10M categories plus Apps") therefore adds nothing: Apps is already a W10M category.
2. Three page kinds with three looks: **home** and **category** pages sit on the dark chrome colour; **leaf** pages have a
   72.6-epx chrome band (24 status + 48.6 header) over a **black** content area. Category pages are a titled list of
   48-epx one-line rows. They are not the home page's 64-epx two-line rows.
3. **Page navigation**: the old page cuts out with no exit motion. The new page slides up about 10 epx and fades in, 90 % by
   about 200 ms and settled by about 250–300 ms. This was measured three times on two builds at 60 fps. A leaf page loads
   its content afterwards, and the content pops in without animation.
4. **15063 added a "sidebar" at the foot of every leaf page**: "Have a question? / Get help" and "Make Windows better. /
   Give us feedback" (plus "Related settings" links where relevant). 14393 has none. **15254 renamed "Phone update" to
   "Windows Update"** (Windows Central FCU changelog).
5. The status bar is **24 epx**, not 28 epx. Evidence: the 15063 full-screen dialog's top border is at 23.4–27 epx, and the
   leaf chrome band ends at 72.6 epx (video) / 72.0 epx (native 10586). This agrees with R8 (24 + 48) and people.md P0.2.
   R3 C4 said 28 epx, and phase 16/19 cite `SystemBars.STATUS_EPX` = 28 from it (see gaps).

---

## 0. Sources, calibration

| ID | Source | Date / build | Capture | Ruler |
|---|---|---|---|---|
| S2 | https://www.youtube.com/watch?v=E6vvrz4ozpE (Windows Central, "Windows 10 Mobile Creators Update - Official Release Demo") | 2017-03-16, **15063** (title) | PMS, 1920x1080 @ 60 fps (file downloaded by this R11 pass) | screen x 733.5–1182.2 = 448.7 px at t=489.5 (white Notebook page, row 600), top y 142.5 → 1440x2560 at 350 % = 411.43 x 731.43 epx, **1 px = 0.9169 epx** (±1 px = ±0.92 epx) |
| S1 | https://www.youtube.com/watch?v=I98ENfXJRqA (Windows Central, "Windows 10 Mobile Anniversary Update - Official Release Demo") | 2016-08-16, **14393** | PMS, 1920x1080 @ 60 fps | screen x 706.52–1210.72 = 504.2 px at t=1000 (white Edge page, rows 400–700), top y 64.4 → 360x640 epx at 400 %, **1 px = 0.7140 epx** (±0.71 epx) |
| G6 | GSMArena Lumia 650 review screenshots, https://fdn.gsmarena.com/imgroot/reviews/16/microsoft-lumia-650/sshots/gsmarena_0NN.jpg (NN = 007 Colors, 008 Lock screen, 009 Start, 013 task switcher) | review 2016-03, **10586** (by date) | native 720x1280 at 200 % → 360 epx, **1 px = 0.5 epx** | exact |
| X6 | https://www.youtube.com/watch?v=6XzfhogUBt0 (MikeTheTechSavvy, "The Lumia 950 in 2017; does it suck?") | 2017-11-27; **15254 era** (FCU shipped 2017-10; build not on screen) | hand-held camera, 1080p25 | none, structure and strings only |
| D-LS | MicrosoftDocs windows-uwp `launch-settings-app.md` at commit 1b112298 (2017-05-19): https://raw.githubusercontent.com/MicrosoftDocs/windows-uwp/1b112298f46b7151dc457fde38ea09232c38762a/windows-apps-src/launch-resume/launch-settings-app.md | 2017-05 | document; "Supported SKUs" column = Desktop / Mobile / Both | — |
| D-UG | Microsoft, "User Guide — Lumia with Windows 10 Mobile", Issue 1.1 (R6 D1; local text `ug.txt`) | 10586-era | document; "All settings > X > Y" paths | — |
| WC-CU | https://www.windowscentral.com/windows-10-mobile-creators-update-review (2017-04-21): "There's now a new Apps category in the hub of the Settings app … uninstall and 'reset' apps"; the "sidebar … In phone mode, it displays below the content" | 2017-04 | document | — |
| WC-FCU | https://www.windowscentral.com/windows-10-mobile-fall-creators-update-ultimate-changelog (2017-10-08): "changed 'Phone Update' to 'Windows Update' under Settings > Update & security" | 2017-10, 15254 | document | — |

Local copies (gitignored, docs/plan/r11/src/settings-front/). The PNGs are full frames decoded unaltered from the videos,
and the JPGs are unaltered downloads. sha256:

| File | Resolution | Source + time | sha256 |
|---|---|---|---|
| E6vvrz4ozpE_t215.0.png | 1920x1080 | S2 @ 215.0 s (home) | 8c1422edc674ad729800f99592fafcb91a6e7bbb9eec77db62de6dc9fef78bf1 |
| E6vvrz4ozpE_t47.75.png | 1920x1080 | S2 @ 47.75 (System, pressed row) | 93008e2b4a258fa6aa95034560fc4364718c85bd4dac19d4d3b9acc3e5c719e7 |
| E6vvrz4ozpE_t49.75.png | 1920x1080 | S2 @ 49.75 | 8a2d51e5b8bd0d81eed960ef7ae249634f9392a6c49837115bc4194203018c36 |
| E6vvrz4ozpE_t53.0.png | 1920x1080 | S2 @ 53.0 (Notifications & actions) | 67f0474db7952ed1399f255878990f00de1bcc829ff4993d0844dc8a39a67621 |
| E6vvrz4ozpE_t218.417.png | 1920x1080 | S2 @ 218.417 (pressed row before navigation) | 31e73f33370f04d0d666d0618d0539a6398463342033ec4b2daed94b5579b1e7 |
| E6vvrz4ozpE_t218.567.png | 1920x1080 | S2 @ 218.567 (first entrance frame) | 415983db7e4220a3d592f9ce857440ff5c61fcf8b37d605ef1b100817fed98fd |
| E6vvrz4ozpE_t218.8.png | 1920x1080 | S2 @ 218.8 | e0cc584ad484d875fdba864d8a745979aea18922f9867bee08cd25be46fadc29 |
| E6vvrz4ozpE_t219.5.png | 1920x1080 | S2 @ 219.5 (Network & wireless) | 60a89540f18f5f07a2dd882895b0f25f59c342a9ada587e4e6e2c70ae20459de |
| E6vvrz4ozpE_t221.133.png | 1920x1080 | S2 @ 221.133 (leaf entrance) | fbaba9a39c747f7ea9cf823c43b479549ea6ce0ecbb2bc85263925f64d0110c3 |
| E6vvrz4ozpE_t222.75.png | 1920x1080 | S2 @ 222.75 (Wi-Fi) | 8972454aa083927a8e5c0c7a4e5df101f90bcc2aabd6c54d64d93a663a7105e0 |
| E6vvrz4ozpE_t270.0.png | 1920x1080 | S2 @ 270 (Data usage) | 6dd93a4fe77c086c18fb816864cc11705136adc59d29ec02b0c767bf25c63dcc |
| E6vvrz4ozpE_t289.0.png | 1920x1080 | S2 @ 289 (home, scrolled) | f5faa393f4507b34ce705fbf80e616d9689998f0dfdf37dc72dfdca69cf7bdab |
| E6vvrz4ozpE_t310.75.png | 1920x1080 | S2 @ 310.75 (Devices) | e3416ec73ebea3dde6565ae0ad9f264d5d76ec114085283cfddf1248c1d9262b |
| E6vvrz4ozpE_t313.0.png | 1920x1080 | S2 @ 313 (Personalization) | f4418a07dec087daed23ec73070015ef6da10717adc15cd799c3a758097791fe |
| E6vvrz4ozpE_t342.5.png | 1920x1080 | S2 @ 342.5 (Apps) | ff93b74f3da253da0dd15a232119dd27dd7aa6bc44ae75e78d20560fc1d12c25 |
| E6vvrz4ozpE_t347.5.png | 1920x1080 | S2 @ 347.5 (Apps & features) | 3af1546dc9b8b46a2d987b91b0a6fef27ba4316822524c14e74a8f20a22fd227 |
| E6vvrz4ozpE_t406.0.png | 1920x1080 | S2 @ 406 (Active hours dialog) | e27785540dbe5e0684e3874b2fb0fb34d8efb0de2856beff287d1466d45aa8c0 |
| E6vvrz4ozpE_t430.0.png | 1920x1080 | S2 @ 430 (Lock screen, combo open) | 987fa4972488bcc1c2d4e0824b8d7d72a9a69fdd99222ada39b34201356d7c2b |
| E6vvrz4ozpE_t489.5.png | 1920x1080 | S2 @ 489.5 (ruler frame) | c4ee9338dfae049840a12e9e97b71621a1421a7ab3c7519fec44e070529481d5 |
| I98ENfXJRqA_t453.75.png | 1920x1080 | S1 @ 453.75 (home, scrolled) | 2b5afc602c2ca3d68c0f3ef28783f7fd179c951cfa4a7b83ec7a0088cc3275b6 |
| I98ENfXJRqA_t458.0.png | 1920x1080 | S1 @ 458 (home) | 05847421d9b5e9e34ba92da2a38612edb09f8fae1571fed6ba05501ddea965cc |
| I98ENfXJRqA_t460.25.png / _t460.267.png / _t460.583.png | 1920x1080 | S1 entrance frames | de2c6d59…cd3 / 73d8c3f7…d30 / 702ad609…4c5 (full: de2c6d595124f9293687c72456f2ebe318d6f4640f59eab7be3b410118345cd3, 73d8c3f7765709fe0c7e634ccf673ab88118973003931e5db8b5eea55ed7ac30, 702ad60974019e7f50de9ce51d0c59ed42b2cab3e7c26ecf2111775a142a5c88) |
| I98ENfXJRqA_t469.75.png | 1920x1080 | S1 @ 469.75 (Lock screen) | d153edbd77c1718053ae7f8bffbcaca31059f97c066a3b78f3e86bf583bba979 |
| I98ENfXJRqA_t484.0.png | 1920x1080 | S1 @ 484 (Glance screen) | 77fd2b4f33dab4180d30cea5177addca254339a601e3e54ea488c5b5e6e491a1 |
| I98ENfXJRqA_t510.75.png | 1920x1080 | S1 @ 510.75 (Update & security) | bedc846068b1ad4d2608adfc2bda2646e4a7f91df8abfcd4d5e3d7e64c593da7 |
| I98ENfXJRqA_t554.25.png | 1920x1080 | S1 @ 554.25 (System, scrolled) | 7f231fcb9a08ab5aac8b582fb9c40734214fff9dcff0d094142eecd618388d0a |
| I98ENfXJRqA_t601.75.png | 1920x1080 | S1 @ 601.75 (search, no result) | 16212c5fc791bb8d821669c2a1cf49308b79a12004ce9c4d5a65589ba8520ec8 |
| I98ENfXJRqA_t603.25.png | 1920x1080 | S1 @ 603.25 (search suggestions) | 5a6447c497083fcce056ab159c56a0e54d1e5e49059afdfd483d0c92bcf08f3c |
| I98ENfXJRqA_t638.25.png | 1920x1080 | S1 @ 638.25 (Network & wireless) | 1a665f7d9d0a0a42be0374e355eb7cdb846a593f02246dd6b560ee0c7ac62b42 |
| E6vvrz4ozpE_t381.5.png | 1920x1080 | S2 @ 381.5 (Update & security) | fb778436153c6bfbb6816d32ef52fe5d7201d3c225b3c21624f3a6feae55b360 |
| I98ENfXJRqA_t1000.png | 1920x1080 | S1 @ 1000 (ruler frame) | f09df2aa73c50434411b9ab68790d38e7d55cd8e6f4d5b9691b9ab3f56667172 |
| gsmarena_650_007.jpg / _008 / _009 / _013 | 720x1280 | G6 | 7b24576ebac347da7e35d68d094da4d072d5308f5c9d84e67e34cdd71f0e66ba / 078ae71f142f33aafd86122fa5195704ad73da809a9f4aecf2b3d8392dc91b2a / 2b4f48d9705d337f47eb514f5c7af71b10c2d8f7ab749d532eca272c9be454f2 / 67ed0c6aaa03ea8d6a3616842ba15c94b7bdc8be29904bd2b7459d8ceb02982b |
| 6XzfhogUBt0_t296.png / _t310.png | 1920x1080 | X6 @ 296 s (home), 310 s (Privacy) | 53ddf0ad66e463e307602847d9a87978a9e4827f03b87c9cf4def596dbc09cf9 / 9ea3abeb6471a21a47f1406fe8d635ea6058cff14cd475069a95a86a6f7055f5 |

Method: sub-pixel half-level crossings and band detection on the frames in epx (Pillow + numpy; scripts in the session
scratchpad `r11cal/scripts/`: `mm.py` helpers, `set_*.py` per screen, `track.py` shift/fade tracker, `scenes.py` page
finder). Colours sampled from video are **capture rendition** (R3 §0.2). The native 10586 colours are from G6.

Confidence: **HIGH** = S1 and S2 agree, or two sources/scales agree; **MEDIUM** = one clean 60-fps capture or one native
screenshot; **LOW** = camera, a non-governing build only, or a weak reading; **UNMEASURED**.

---

## 1. Category and page set as shipped (for phase 19's per-page table)

Home order and subtitles are read from S2 t=215 / t=289 (15063) and S1 t=453.75 / 458 (14393). Pages come from the
category-page captures, or failing that from D-LS / D-UG. **Build** = where each page list was seen.

| # | Category (home subtitle, 15063) | Pages as shipped | Build / source | Conf |
|---|---|---|---|---|
| 1.1 | **System** — "Display, notifications, battery" | Display · Notifications & actions · Phone · Messaging · Storage · Battery · Driving mode · About | 15063 S2 t=47.75; same list on 15254-era X6 t≈300. 14393 S1 t=554.25 differs: … Messaging · Storage · Battery · **Offline maps** · Driving mode · **Apps for websites** · About. 10586 D-UG: "Battery saver", "Device encryption" were System pages | **HIGH** |
| 1.2 | **Devices** — "Bluetooth, camera" | Default camera · Bluetooth & other devices · NFC · Mouse · USB | 15063 S2 t=310.75 (D-LS: Default camera "Mobile only"; Bluetooth "Both") | MEDIUM |
| 1.3 | **Network & wireless** — "Wi-Fi, airplane mode, cellular" | Data usage · Cellular & SIM · Wi-Fi · Airplane mode · Mobile hotspot · VPN | 15063 S2 t=219.5 and 14393 S1 t=638 identical | **HIGH** |
| 1.4 | **Personalization** — "Start, lock screen, sounds" | Start · Colors · Sounds · Lock screen · Glance screen · Navigation bar | 15063 S2 t=313 and 14393 S1 t=460.75 identical (10586: Glance screen lived under Extras, D-UG) | **HIGH** |
| 1.5 | **Apps** — "Uninstall, Apps for websites, Offline maps" | Apps & features · Offline maps · Apps for websites | 15063 S2 t=342.5; absent on 14393 (S1 t=453.75 goes Personalization → Accounts); WC-CU | **HIGH** |
| 1.6 | **Accounts** — "Your accounts, email, sync" | Your email and accounts · Sign-in options · Access work or school · Kid's Corner · Apps Corner · Sync your settings | not filmed on 14393+. D-UG (10586): Your email and accounts, Sign-in options, Kid's Corner, Apps Corner, Sync your settings; D-LS (2017) "Both": Access work or school, Email & app accounts, Family & other people, Sign-in options, Sync your settings, Your info | LOW (final-build list unverified) |
| 1.7 | **Time & language** — "Speech, region, keyboard" | Date & time · Language · Region · Keyboard · Speech | D-UG (10586); D-LS: Date & time "Both", "Region & language" desktop-only (so the phone keeps Language and Region apart) | LOW |
| 1.8 | **Ease of Access** — "Text size, narrator, high contrast" | Narrator · Magnifier · High contrast · Closed captions · More options (holds text size) | D-UG (Narrator, Magnifier, High contrast, More options); D-LS "Both": Closed captions, Keyboard, Mouse, Other options | LOW |
| 1.9 | **Privacy** — "Location, feedback" | Location · Camera · Microphone · Motion · Notifications · Speech, inking & typing · Account info · Contacts · Calendar · Call history · Email · Messaging · Radios · Background apps · Other devices · Feedback & diagnostics | X6 camera (15254 era) shows Location … Calendar in this order; the rest from D-LS ("Both"). 10586 D-UG also had "Advertising ID" | MEDIUM (order of the tail unverified) |
| 1.10 | **Update & security** — "Backup, Find My Phone" | Phone update (15254: **Windows Update**) · Backup · Device encryption · Find My Phone · For developers · Windows Insider Program | 15063 S2 t=381.5 and 14393 S1 t=510.75 identical; rename in WC-FCU | **HIGH** |
| 1.11 | **Extras** (no subtitle) | OEM "settings apps": 10586 D-UG lists Touch, Glance screen, Smart dual SIM, Network services, Lumia motion data, equalizer | present on S1 and S2 home; D-LS: "Only available if 'settings apps' are installed" | MEDIUM |
| 1.12 | Home category order | System · Devices · Network & wireless · Personalization · **Apps** · Accounts · Time & language · Ease of Access · Privacy · Update & security · Extras | S2 t=215 + t=289; X6 t=296 | **HIGH** |

---

## 2. Home page (Settings landing)

| # | Value | Number | Conf | Source |
|---|---|---|---|---|
| 2.1 | Page background | capture **(14,19,13)** on both builds; the native dark-theme chrome it renders is **#1F1F1F** (31,31,31) (G6 header band) | HIGH | S2 t=215 / S1 t=458 samples; G6 007–009 |
| 2.2 | Status bar | **24 epx**, the same colour as the page | HIGH | see 4.1 |
| 2.3 | Header glyph (gear, accent) | box **24 epx** (S2 14.2–38.1 x 36.2–60.1; S1 12.5–36.8 x 36.8–61.1), centre y ≈ 48.5 | HIGH | blue-dominant bbox |
| 2.4 | Header title "Settings" | left **49 ± 1 epx** (S2 50.0, S1 48.2); cap top 43.4; cap ≈ 11 (R3 C1: 11 ± 1, 15-epx SemiBold class) | HIGH | |
| 2.5 | "Find a setting" box | outer top **80.3 epx**, outer bottom **113.0** → **32.7 epx** tall (R3 C1 at 720p: 31); left edge 11.4–12.4, right edge 10.7–10.9 from the screen edge (≈ 12-epx margins); 2-epx **accent** border; at rest it shows its **focused** style, a white fill (253,255,252), with no keyboard shown | HIGH | S2 x=0.8W column: border 80.2→83.0, 109.6→113.2; S1 79.7→83.3, 109.7→113.2 |
| 2.6 | Placeholder "Find a setting" | grey on white; text left **27 epx** on 15063 (14.6 inside the box); magnifier glyph ≈ 11 epx at the right, right inset ≈ 20 epx | MEDIUM | S2 rows 91.2–105.9 |
| 2.7 | Category row pitch | **64.2 epx**, identical at 350 % and 400 % (fixed epx) | HIGH | S2 glyph tops 136.16, 200.34, 264.53, 456.16, 520.34, 648.71; S1 text tops 176.07, 240.33, 304.59, 368.14 |
| 2.8 | Category glyph | **31 epx** box, left **13.3** (S2) / 12.5 (S1), accent-coloured (capture (48–55, 85–101, 193–218)), vertically centred on the two text lines | HIGH | |
| 2.9 | Row text | title left **56–57.3 epx** (S1 56.0, S2 57.3; R3 55/57); title cap **11.0** (15-epx class), subtitle ≈ 9 (12-epx class); title cap top → subtitle cap top **20.2 epx** (baseline-to-baseline ≈ 18, R3 C1) | HIGH | S2 "Devices" 202.18 / "Bluetooth" 222.35; S1 176.07 / 196.06 |
| 2.10 | Row text colours | title (220,224,220), subtitle **(130,134,129)**, i.e. about 50 % white | HIGH | top-5 % pixels, both builds |
| 2.11 | First row position | box bottom 113 → first glyph top 136.2 (**23 epx**), title cap top 138.9 | MEDIUM | S2 t=215 |
| 2.12 | List bottom | the list scrolls under nothing: it ends at the nav bar top, 683.5 (S2) / 593 (S1) epx, so nav bar ≈ **47–47.4 epx** (X6 says 48) | HIGH | column transitions at x=W−3 |

## 3. Category page (e.g. System, Network & wireless)

| # | Value | Number | Conf | Source |
|---|---|---|---|---|
| 3.1 | Chrome | the home header and "Find a setting" box stay at the top, same geometry as 2.3–2.6; background (14,19,13) | HIGH | S2 t=47.75/219.5/313; S1 t=460.75 |
| 3.2 | Section title | the category name ("System") in **grey (142,145,142)**, left 13.3, cap top **152 epx** (S2), 15-epx class | MEDIUM | S2 t=47.75 |
| 3.3 | Row pitch | **48.0 epx** one-line rows, fixed epx | HIGH | S2 label tops 239.77 … 527.68 over six pitches = 47.99; S1 240.33 … 480.24 over five = 47.98 |
| 3.4 | Row glyph | ≈ 16–20 epx monoline glyph, left **13.3 epx**, white | HIGH | both |
| 3.5 | Row label | left **44.5 epx** (S2 44.47, S1 44.61), white (203–219 capture), 15-epx class. The same 44.5 label x as the Notebook rows (R7 §3.3.6) | HIGH | |
| 3.6 | Pressed row | full-width fill **(63,68,64)**, the row's full 48 epx (band edge 268.4 on S2); the same colour as R7 §3.5.7 | MEDIUM | S2 t=47.75 |

## 4. Leaf page (a setting page: Wi-Fi, Glance screen, Lock screen, Data usage …)

| # | Value | Number | Conf | Source |
|---|---|---|---|---|
| 4.1 | Chrome band | status bar + page header = **72.6 epx** tall (S2 72.56–72.82 on four pages; S1 72.55/72.59); native 10586 **72.0** (G6, three pages). Band colour = the home colour; **content background black (0,0,0)** below it. The 15063 full-screen dialog's accent top border at 23.4–27 epx puts the status bar at **24 epx** | HIGH | x=W−4 edge |
| 4.2 | Header row | gear glyph **≈ 23–24 epx** box (S2 x 14.2–36.2/38.1, y 36.2–60.1; S1 x 12.5–35.3, y 36.8–61.1), i.e. the home header's gear, then the **page name** (not "Settings") at left **48–50 epx** (S2 49.1–50.0, S1 48.2), sentence case, cap ≈ 11 | HIGH | S2 t=222.75/53/270/347.5; S1 t=484/469.75 |
| 4.3 | Header form by build | 14393 / 15063: "[gear] Wi-Fi" sentence case at x ≈ 49. **10586: ALL CAPS page name at x 60** ("[gear] LOCK SCREEN", "[gear] COLOURS", "[gear] START"), gear 23 epx at x 12.5–35.5 | HIGH (difference) | G6 008 cols 12.5–35.5 / 60.0 |
| 4.4 | Page title in content | the page's own heading ("Wi-Fi", "Glance screen", "Overview") in **20-epx Subtitle**, left 13.3, first cap top ≈ 96.7 epx (24 epx under the band) | MEDIUM | S2 t=222.75 rows 96.73; S1 t=484 101.8 |
| 4.5 | Toggle switch | **44.0 x 19.3–20.1 epx** (S2 x 13.0–57.0, y 128.4–147.4; S1 11.8–56.2, 258.9–279.0); thumb **10–10.7 epx**, white; On = accent fill; state label "On"/"Off" starts **56–57 epx** right of the toggle's left edge (S2 69.9, S1 68.9) | HIGH | re-measures R3 C1's 44 x 20 at 1080p |
| 4.6 | Toggle row text | setting description above the toggle in 15-epx body, grey-white (202–210); body line pitch 20 epx (R3 A15) | MEDIUM | S1 t=484 rows 148.9/168.9 |
| 4.7 | Combo box | outer **32.1–33.0 epx** tall; **2-epx grey border (95,97,94)**, black fill; width fits the longest item (S2 12.4–293.9; S1 11.8–192.4); chevron at the right | HIGH | S2 t=270 604.7–637.7; S1 t=469.75 91.8–124.0 |
| 4.8 | Open combo list | the list opens in place over the page, current item accent-filled, one row per item ("30 seconds … Never") | LOW | S2 t=430 (visual) |
| 4.9 | Button | **31.6–31.8 epx** tall, fill capture **(40–48)**, no border, text 15-epx centred, width fits the text + padding (S2 "Set limit" 12.4–103.6) | HIGH | S2 t=270 425.0–456.6; S1 t=469.75 137.5–169.3 |
| 4.10 | Hyperlink | accent text, 15-epx (capture (56–61, 85–88, 166–179)); e.g. "Get help", "Sign-in options", "Hardware properties" | MEDIUM | S2 t=53, S1 t=469.75 |
| 4.11 | 15063 page sidebar | at the foot of each leaf page: "**Have a question?**" (20-epx Subtitle, white) / "**Get help**" (accent link), then "**Make Windows better.**" / "**Give us feedback**"; "Related settings" + links where relevant (Bluetooth page). **Not present on 14393** | HIGH | S2 t=53 rows 471.8/505.7/574.4/608.4; every 15063 leaf page in the capture; WC-CU |
| 4.12 | Wi-Fi network row (15063) | two lines (SSID, "Connected, secured" / "Secured") beside a 28-epx signal glyph; pitch **59.6 epx** | MEDIUM | S2 t=222.75 row tops 243.44/303.04/362.63 |
| 4.13 | App row (Apps & features, 15063) | 40-epx app icon at x 13.3, name + publisher lines, size/date right-aligned; pitch **60 epx** | MEDIUM | S2 t=347.5 283.78/343.38/403.89/463.49 |
| 4.14 | Per-app notification row | 40-epx icon, name + "On: Banners, Sounds, Vibrations", toggle at the right; pitch **80 epx** | MEDIUM | S2 t=53 135.2/215.0/295.7/375.5 |
| 4.15 | Full-screen dialog (Active hours) | covers the page from 24 to 685 epx with a 1–2-epx **accent** border; content inset **27 epx**; Save / Cancel buttons at the bottom | MEDIUM | S2 t=406 |
| 4.16 | Loading state | an empty black content area under the band, with accent marching dots at the top, until data arrives (Wi-Fi: 221.25 → 222.2 s) | MEDIUM | S2 t=221.25–222.2 |

## 5. "Find a setting" search

| # | Value | Number | Conf | Source |
|---|---|---|---|---|
| 5.1 | Behaviour | results appear as you type, in a dropdown directly under the box; the category list stays underneath. Matches are setting titles ("Cont" → Contacts · Continue App Experiences · High contrast) | MEDIUM | S1 t=603.25 |
| 5.2 | Dropdown geometry | starts at the box bottom (113.6 epx), **same width as the box** (x 12.1–348.4), fill **(40,40,40)** with a 1-epx (76,81,75) border; result rows **44.4 epx**, a 13-epx glyph at x 26, text at x **51.8** | MEDIUM | S1 t=603.25: rows 128.2 / 172.5 / 218.2; bottom border 246 |
| 5.3 | No match | a single row "**No results for** <query>", 45 epx tall, text at x 24.6 | MEDIUM | S1 t=601.75 (113.6–158.6) |

## 6. Motion (RV11: 60-fps sources; ±1 frame = ±17 ms)

| # | Value | Number | Conf | Source |
|---|---|---|---|---|
| 6.1 | Row press before navigating | pressed fill (3.6) for about **150 ms** (9 frames), then the page changes | MEDIUM | S2 218.417–218.55 |
| 6.2 | Page exit | **cut**: the old page is gone in one frame with no slide or fade | HIGH | S2 218.567, 221.133; S1 460.25 |
| 6.3 | Page entrance | the new page slides up and fades in. First frame +9.8 / +10.3 epx (S2) and +8.7 epx (S1) below rest, at 29–57 % contrast. Offsets per frame (S2, epx): 9.81, 8.07, 6.79, 5.78, 4.86, 4.03, 3.12, 2.93, 2.84, 2.11, 2.11, 1.38, 1.19, 1.10, 0.73, 0.64. 50 % of travel by ≈ 67 ms, 90 % by ≈ 200 ms, settled by ≈ 250–300 ms. Opacity: 0.31 → 0.90 by ≈ 150 ms, 0.99 by ≈ 233 ms. Strong ease-out, the same form as R7 §2.6.1 but with half the travel (≈ 11 epx) | **HIGH** (3 instances, 2 builds) | `track.py` NCC row-profile shift vs the settled frame: S2 218.567–218.817 and 221.133–221.35; S1 460.267–460.583 |
| 6.4 | Leaf content arrival | content pops in in one frame when loaded, with no animation (S2 222.2) | MEDIUM | S2 frames 402–403 |
| 6.5 | Back navigation, toggle thumb travel, combo open, search dropdown open | UNMEASURED (not isolated in the captures) | — | see §9 |

## 7. Segoe MDL2 glyphs the shell's icon font needs (names and code points from the 2017 MDL2 list, windows-dev-docs d6050b7 `segoe-ui-symbol-font.md`; the match to the on-screen glyph is visual: MEDIUM)

Header: Settings E713. Home categories: System E770 (phone outline), Devices E772, Globe E774 (Network & wireless),
Personalize E771, AllApps E71D (Apps: three bulleted lines), Contact E77B (Accounts), TimeLanguage E775, EaseOfAccess
E776, Lock E72E (Privacy), Sync E895 or UpdateRestore E777 (Update & security: circular arrows), Extras: no exact name
found (a tile/diamond puzzle shape; Puzzle EA86 nearest). Category rows: Display TVMonitor E7F4 (the 15063 glyph is a
phone outline, System E770), Notifications & actions ActionCenter E91C, Phone E717, Message E8BD, Storage SaveLocal
E78C, Battery Battery10 E83F, Driving mode Car E804, About Info E946, Data usage DataSense E791, Wi-Fi Wifi E701,
Airplane E709, VPN E705, Bluetooth E702, Mouse E962, USB E88E, Camera E722, Color E790, Lock screen Lock E72E, Start
Tiles ECA5. Controls: Search E721, ChevronDown E70D, ToggleBorder/ToggleThumb EC12/EC14, RadioBtnOff/On ECCA/ECCB,
Checkbox E739 / CheckMark E73E. Licence: MDL2 is proprietary (R1 §10). The shell draws the Fluent System Icons stand-in,
so this list is the lookup key, not an asset.

## 8. Strings as shipped (15063 unless noted)

Home subtitles: see §1. Box placeholder "Find a setting". Search no-match "No results for <query>". Sidebar: "Have a
question?", "Get help", "Make Windows better.", "Give us feedback", "Related settings". Update & security: "Phone update"
(15254: "Windows Update"), "Update status", "Your device is up to date. Last checked: <date>", "Check for updates",
"Update settings", "Change active hours", "Restart options". Toggle labels "On" / "Off". Lock screen: "Background", "My
picture", "Browse", "Choose an app to show detailed status", "Choose apps to show quick status", "Screen times out after",
"Double-tap to wake up phone", "Sign-in options". Glance screen (14393): "See your clock and your lock screen info at a
glance after your screen has turned off.", "Show Glance screen when your screen is turned off", "On battery, show Glance
screen for", "Show lock screen background picture", "Night mode". Data usage: "Overview", "Total: <n> MB", "From the last
30 days", "View usage details", "Cellular data usage", "Set limit", "Use cellular instead of Wi-Fi", "When Wi-Fi is poor",
"Restrict background data". Wi-Fi (15063): "Wi-Fi", "Connected, secured", "Secured", "Open", "Hardware properties",
"Manage known networks", "Additional settings". Notifications row form: "On: Banners, Sounds, Vibrations".

## 9. UNMEASURED, with proposed tagged approximations

| # | What | Why not | Proposed approximation (from which measured pattern) |
|---|---|---|---|
| U1 | Back navigation (leaf → category → home) | the captures leave pages with the Windows key or new taps; no Back press was isolated | the 6.3 entrance applied to the page being returned to (R7 §3.2 shows the same fade form for Cortana pages); H-row |
| U2 | Toggle thumb travel and colour change | no frame-by-frame toggle flip in S1/S2 | an instant state change with a thumb slide of 2 frames (≈ 33 ms) is **not** sourced; propose a linear 100-ms thumb slide as an [accept] value |
| U3 | Combo box open / close motion | only rest states seen | R7 §2.2.6 flyout open (grows 76 → 100 % in 200 ms, ease-out) |
| U4 | Search dropdown open, and the keyboard | S1 601–636 has a 4-fps scene sampling only; not tracked | the flyout open of U3; keyboard per R6 §2.7 |
| U5 | Accounts, Time & language, Ease of Access page lists on 15063 / 15254 | not filmed | §1 rows 1.6–1.8 (10586 guide + 2017 doc) |
| U6 | Light theme | every capture is dark | UWP light theme (R1 §6.2) with the same geometry |
| U7 | Radio button and checkbox geometry in Settings | radio rows seen (S1 t=100 notifications priority) but not measured | Checkbox 20.6 epx (R7 §3.7.2); radio at the same 20 epx |
| U8 | Category glyphs at native resolution | only 1080p video frames | 2.8 sizes; glyph shapes via §7 names |

## 10. Gaps for the phase doc (phase-19-settings-front.md)

- **Scope "Out: an 'Apps' category (desktop Windows' 15063 addition, not W10M's)" is contradicted.** W10M 15063 shipped an
  Apps category (1.5) with Apps & features / Offline maps / Apps for websites. The Q2 Decision's "plus Apps (desktop
  Windows 10's 2017 addition)" wording should say it is W10M's own. The per-page table must also move **Apps for websites
  out of System**, and add Offline maps and Apps & features there. Default apps is not a W10M Apps page (D-LS lists it
  without an SKU mark; not filmed); keep it as the P4 addition the Q2 ruling allows.
- Per-page table vs as shipped (1.1–1.10). **System** has Battery (not "Battery saver"), Phone, Messaging, Storage and
  Driving mode, and has no Display "more". **Update & security** holds Device encryption, For developers and Windows Insider
  Program (Insider stays out per Scope); on 15254 it is "Windows Update", not "Phone update". **Devices** says "Bluetooth &
  other devices" on 15063 and also lists Mouse and USB. **Personalization** has Navigation bar (a phase 01 / 04 concern:
  there is no shell page for it yet). **Network & wireless** order is Data usage, Cellular & SIM, Wi-Fi, Airplane mode,
  Mobile hotspot, VPN.
- **Status bar height**: the measured W10M status bar is 24 epx (4.1, 2.2; R8 §1.1; people.md P0.2). Phase 16 / 19 cite
  "drawn status bar 28 epx (R3 C4, `SystemBars.STATUS_EPX`)". That is a phase 01 value to re-check (R3 C4 read 112 phys at
  720p from Start, whose status bar overlays the wallpaper). Raise it at phase 01, not here.
- Y1 (category glyphs and category page header) is now measured: the category page keeps the home header + box and adds a
  grey section title (3.1–3.2). Y4 (page transition) is now measured (6.2–6.3): replace the hub's `PageTransition` stand-in.
  H5 remains only for toggle / combo motion (U2, U3).
- Leaf pages need the 72.6-epx band + black content form (4.1) and the 15063 sidebar (4.11). The sidebar is a natural home
  for the fallback line (Y2, "Change this in Android settings"): a W10M-native slot, not a new row form.
- The home box renders focused (white, accent border) at rest (2.5). Decide whether the shell copies that (it implies
  programmatic focus without the IME) or shows the unfocused form; this is an [accept] H-row.
- E1 values to update: box height 32.7 epx (R3 C1's 31 was 720p), text x 56–57, category page rows 48 epx at label x 44.5.

## Tally

Each row in §1–§6 and §9 is counted once, at its stated level. §7 (glyph names) and §8 (strings) are not counted. 6.5 is
counted under U1–U4.

- **HIGH 29**: 1.1, 1.3, 1.4, 1.5, 1.10, 1.12, 2.1, 2.2, 2.3, 2.4, 2.5, 2.7, 2.8, 2.9, 2.10, 2.12, 3.1, 3.3, 3.4, 3.5,
  4.1, 4.2, 4.3, 4.5, 4.7, 4.9, 4.11, 6.2, 6.3.
- **MEDIUM 20**: 1.2, 1.9, 1.11, 2.6, 2.11, 3.2, 3.6, 4.4, 4.6, 4.10, 4.12, 4.13, 4.14, 4.15, 4.16, 5.1, 5.2, 5.3, 6.1,
  6.4.
- **LOW 4**: 1.6, 1.7, 1.8, 4.8.
- **UNMEASURED 8**: U1–U8.

Biggest gap: the Accounts / Time & language / Ease of Access page lists on the final build (1.6–1.8) were not filmed, so
they rest on the 10586 guide and the 2017 URI table.
